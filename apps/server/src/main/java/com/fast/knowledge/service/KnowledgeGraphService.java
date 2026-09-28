package com.fast.knowledge.service;

import com.fast.knowledge.ai.port.ChatPort;
import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.mapper.DocumentChunkMapper;
import com.fast.knowledge.mapper.DocumentMapper;
import com.fast.knowledge.model.entity.DocumentChunk;
import com.fast.knowledge.model.entity.KbDocument;
import com.fast.knowledge.model.entity.KgEdge;
import com.fast.knowledge.model.entity.KgEntity;
import com.fast.knowledge.model.vo.SearchHitVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WP7 知识图谱（GraphRAG 轻量版）— 存储为 MySQL 邻接表，检索经 WP7.1 双层链接 + PPR 扩散。
 *
 * <p>构建：索引完成后 LLM 抽取实体/关系（单文档单次调用），实体按 (kbId, name) 幂等合并，
 * 边按 (kbId, src, dst, relation) 幂等合并并记录证据文档。
 * 检索（WP7.1，LightRAG 式双层 + HippoRAG 式 PPR）：LLM 派生低层（实体）/高层（主题）关键词，
 * 与问题原文子串链接合并为种子实体 → 个性化 PageRank 扩散取 top 邻居 → 证据文档 chunk
 * 进入扩展召回，由 RetrievalOrchestrator 与向量/关键词结果融合。
 *
 * <p>升级预留：切换 TuGraph 时仅替换本类存储/查询实现（见 docs/architecture/graph-storage-selection.md）。
 */
@Slf4j
@Service
public class KnowledgeGraphService {

    private static final String EXTRACT_PROMPT = """
            你是知识图谱构建助手。阅读一份企业文档，抽取其中的关键实体与实体间关系。
            实体 type 只能取：制度、部门、设备、岗位、流程、指标、其他。
            规则：
            1. 只抽有明确业务意义的实体（制度名、部门名、设备名、岗位名、关键指标等），≤ %d 个；
            2. description 一句话概括，≤ 40 字；
            3. relation 用短词（如：发文部门、负责、适用于、规定了、隶属于）；
            4. 关系的 source/target 必须是 entities 中已列出的实体名；
            5. 严格输出 JSON：{"entities":[{"name":"...","type":"...","description":"..."}],"relations":[{"source":"...","target":"...","relation":"..."}]}
            6. 不要输出任何解释或代码块标记。""";

    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*}", Pattern.DOTALL);

    private final KnowledgeProperties properties;
    private final ChatPort chatPort;
    private final ObjectMapper objectMapper;
    private final com.fast.knowledge.graph.GraphStore graphStore;
    private final DocumentChunkMapper documentChunkMapper;
    private final DocumentMapper documentMapper;

    /** WP7.1 双层关键词派生缓存（kbId:query → 关键词对，10 分钟） */
    private final com.github.benmanes.caffeine.cache.Cache<String, KeywordPair> keywordCache =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .maximumSize(512)
                    .expireAfterWrite(java.time.Duration.ofMinutes(10))
                    .build();

    public KnowledgeGraphService(KnowledgeProperties properties, ChatPort chatPort, ObjectMapper objectMapper,
                                 com.fast.knowledge.graph.GraphStore graphStore,
                                 DocumentChunkMapper documentChunkMapper, DocumentMapper documentMapper) {
        this.properties = properties;
        this.chatPort = chatPort;
        this.objectMapper = objectMapper;
        this.graphStore = graphStore;
        this.documentChunkMapper = documentChunkMapper;
        this.documentMapper = documentMapper;
    }

    public boolean isEnabled() {
        return properties.getKg().isEnabled();
    }

    // ---- 构建 ----

    /** 索引完成后异步构建（LLM 抽取），失败仅记日志不影响文档状态 */
    @Async("indexExecutor")
    public void scheduleBuild(Long kbId, Long docId, String title, String fullText) {
        try {
            int built = build(kbId, docId, title, fullText);
            log.info("KG 构建完成 kbId={} docId={} 实体/边合并 {} 条", kbId, docId, built);
        } catch (Exception e) {
            log.warn("KG 构建失败 kbId={} docId={}: {}", kbId, docId, e.getMessage());
        }
    }

    /** 抽取并幂等入库，返回新增/涉及的实体+边数量 */
    public int build(Long kbId, Long docId, String title, String fullText) throws Exception {
        int maxEntities = Math.max(3, properties.getKg().getMaxEntitiesPerDoc());
        String preview = fullText == null ? "" : fullText.substring(0, Math.min(fullText.length(),
                properties.getKg().getDocPreviewChars()));
        if (preview.isBlank()) {
            return 0;
        }
        String userPrompt = "文档标题：" + (title == null ? "（无标题）" : title) + "\n\n文档内容：\n"
                + preview + "\n\n请输出 JSON（entities 最多 " + maxEntities + " 个）：";
        chatPort.withContext("kg_extract", String.valueOf(docId));
        String raw = chatPort.complete(String.format(EXTRACT_PROMPT, maxEntities), userPrompt);

        Matcher m = JSON_OBJECT(raw);
        if (m == null) {
            log.warn("KG 抽取无 JSON 输出 docId={}", docId);
            return 0;
        }
        Map<String, Object> parsed = objectMapper.readValue(m.group(),
                new TypeReference<Map<String, Object>>() {
                });

        // 实体幂等入库：(kbId, name) 唯一
        Map<String, Long> nameToId = new LinkedHashMap<>();
        int count = 0;
        Object rawEntities = parsed.get("entities");
        if (rawEntities instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> rawMap)) {
                    continue;
                }
                Map<String, Object> e = castMap(rawMap);
                String name = trim(str(e.get("name")), 128);
                if (name == null) {
                    continue;
                }
                KgEntity existing = graphStore.findEntity(kbId, name);
                if (existing != null) {
                    nameToId.put(name, existing.getId());
                    continue;
                }
                KgEntity entity = new KgEntity();
                entity.setKbId(kbId);
                entity.setName(name);
                entity.setType(trim(str(e.get("type")) == null ? "其他" : str(e.get("type")), 32));
                entity.setDescription(trim(str(e.get("description")) == null ? "" : str(e.get("description")), 512));
                graphStore.insertEntity(entity);
                nameToId.put(name, entity.getId());
                count++;
                if (nameToId.size() >= maxEntities) {
                    break;
                }
            }
        }

        // 边幂等入库：source/target 需能解析到实体
        Object rawRelations = parsed.get("relations");
        if (rawRelations instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> rawMap)) {
                    continue;
                }
                Map<String, Object> r = castMap(rawMap);
                Long srcId = nameToId.get(trim(str(r.get("source")), 128));
                Long dstId = nameToId.get(trim(str(r.get("target")), 128));
                if (srcId == null || dstId == null || srcId.equals(dstId)) {
                    continue;
                }
                String relation = trim(str(r.get("relation")) == null ? "相关" : str(r.get("relation")), 128);
                if (graphStore.edgeExists(kbId, srcId, dstId, relation)) {
                    continue;
                }
                KgEdge edge = new KgEdge();
                edge.setKbId(kbId);
                edge.setSrcId(srcId);
                edge.setDstId(dstId);
                edge.setRelation(relation);
                edge.setEvidenceDocId(docId);
                graphStore.insertEdge(edge);
                count++;
            }
        }
        return count;
    }

    // ---- 检索扩展 ----

    /**
     * 问题实体链接 → PPR 邻居的证据文档 chunk 构造扩展召回（低权重分数）。
     * 命中实体为空或扩展超出上限时返回空列表。
     */
    public List<SearchHitVO> expandForQuery(Long kbId, String query, int limit) {
        Set<Long> seeds = new LinkedHashSet<>();
        linkEntities(kbId, query).forEach(e -> seeds.add(e.getId()));
        return expandFromSeeds(kbId, seeds, query, limit);
    }

    /**
     * WP7.1 双层检索（LightRAG 式）：LLM 单次调用派生低层（实体词）/高层（主题词）关键词，
     * 与问题原文子串链接合并为种子实体，再经 PPR 扩散召回证据 chunk。
     * 关键词派生失败/关闭时自动退化为纯子串链接（expandForQuery 同路径）。
     */
    public List<SearchHitVO> expandDualLevel(Long kbId, String query, int limit) {
        Set<Long> seeds = new LinkedHashSet<>();
        linkEntities(kbId, query).forEach(e -> seeds.add(e.getId()));
        KeywordPair keywords = properties.getKg().isDualLevelEnabled() ? deriveKeywords(kbId, query) : null;
        if (keywords != null) {
            seeds.addAll(linkByKeywords(kbId, keywords));
        }
        return expandFromSeeds(kbId, seeds, query, limit);
    }

    /** 种子实体 → PPR 扩散 → top 邻居实体的证据文档 chunk（score = 0.15 + 0.15·(ppr/max)，保持低权重语义） */
    List<SearchHitVO> expandFromSeeds(Long kbId, Set<Long> seedIds, String query, int limit) {
        if (seedIds.isEmpty()) {
            return List.of();
        }
        List<KgEdge> allEdges = graphStore.edges(kbId);

        // 无向邻接表：节点 → 相邻节点；边引用按端点索引，供证据文档回溯
        Map<Long, LinkedHashSet<Long>> adjacency = new LinkedHashMap<>();
        for (KgEdge edge : allEdges) {
            if (edge.getSrcId() == null || edge.getDstId() == null) {
                continue;
            }
            adjacency.computeIfAbsent(edge.getSrcId(), k -> new LinkedHashSet<>()).add(edge.getDstId());
            adjacency.computeIfAbsent(edge.getDstId(), k -> new LinkedHashSet<>()).add(edge.getSrcId());
        }
        Map<Long, List<Long>> adj = new LinkedHashMap<>();
        adjacency.forEach((k, v) -> adj.put(k, List.copyOf(v)));

        Map<Long, Double> ppr = PersonalizedPageRank.compute(adj, seedIds,
                properties.getKg().getPprTeleport(), 30);
        // top 邻居（排除种子自身）
        List<Long> topNeighbors = ppr.entrySet().stream()
                .filter(e -> !seedIds.contains(e.getKey()))
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(Math.max(1, properties.getKg().getPprTopNeighbors()))
                .map(Map.Entry::getKey)
                .toList();
        if (topNeighbors.isEmpty()) {
            return List.of();
        }
        double maxNeighborPpr = topNeighbors.stream().mapToDouble(id -> ppr.getOrDefault(id, 0.0)).max().orElse(1);

        // top 邻居相关边的证据文档
        Set<Long> relevantNodes = new LinkedHashSet<>(seedIds);
        relevantNodes.addAll(topNeighbors);
        Set<Long> evidenceDocIds = new LinkedHashSet<>();
        for (KgEdge edge : allEdges) {
            if (edge.getEvidenceDocId() != null
                    && (relevantNodes.contains(edge.getSrcId()) && relevantNodes.contains(edge.getDstId()))) {
                evidenceDocIds.add(edge.getEvidenceDocId());
            }
        }
        if (evidenceDocIds.isEmpty()) {
            return List.of();
        }

        int chunkLimit = Math.max(1, properties.getKg().getNeighborChunkLimit());
        List<SearchHitVO> out = new ArrayList<>();
        for (Long evidenceDocId : evidenceDocIds) {
            if (out.size() >= chunkLimit) {
                break;
            }
            KbDocument doc = documentMapper.selectById(evidenceDocId);
            String docTitle = doc != null && doc.getTitle() != null ? doc.getTitle() : "";
            List<DocumentChunk> chunks = documentChunkMapper.findByDocumentId(evidenceDocId);
            for (DocumentChunk chunk : chunks) {
                if (out.size() >= chunkLimit) {
                    break;
                }
                SearchHitVO vo = new SearchHitVO();
                vo.setChunkId(chunk.getId());
                vo.setDocumentId(evidenceDocId);
                vo.setDocumentTitle(docTitle);
                vo.setSection(chunk.getSectionTitle());
                vo.setContent(chunk.getContent());
                vo.setScore(0.15 + 0.15 * (pprOf(topNeighbors, ppr) / maxNeighborPpr));
                out.add(vo);
            }
        }
        if (!out.isEmpty()) {
            log.info("KG 扩展召回 kbId={} 种子={} pprTop={} 扩展chunk={}", kbId,
                    entityNames(seedIds), topNeighbors.size(), out.size());
        }
        return out;
    }

    private double pprOf(List<Long> ids, Map<Long, Double> ppr) {
        return ids.stream().mapToDouble(id -> ppr.getOrDefault(id, 0.0)).max().orElse(0);
    }

    private List<String> entityNames(Set<Long> ids) {
        return ids.stream().map(id -> {
            KgEntity e = graphStore.entityById(id);
            return e != null ? e.getName() : String.valueOf(id);
        }).toList();
    }

    /** 实体链接：问题文本包含实体名（≥2 字）即命中 */
    List<KgEntity> linkEntities(Long kbId, String query) {
        if (query == null || query.length() < 2) {
            return List.of();
        }
        List<KgEntity> linked = new ArrayList<>();
        for (KgEntity e : graphStore.entities(kbId)) {
            if (e.getName() != null && e.getName().length() >= 2 && query.contains(e.getName())) {
                linked.add(e);
            }
        }
        return linked;
    }

    // ---- WP7.1 双层关键词派生 ----

    private static final String KEYWORD_PROMPT = """
            你是检索查询分析器。针对用户问题派生两组检索关键词：
            1. low：问题中的具体实体词（制度名、部门、设备、岗位、文号等，≤4 个，每个 ≥2 字）；
            2. high：问题的主题与概念词（≤4 个，每个 ≥2 字）。
            严格输出 JSON：{"low":["..."],"high":["..."]}，不要输出任何解释或代码块标记。""";

    /** 低层/高层关键词对 */
    record KeywordPair(List<String> low, List<String> high) {
    }

    /**
     * LLM 派生双层关键词（带 10 分钟查询级缓存，一次调用覆盖缓存命中的重复问法）。
     * 失败返回 null（调用方退化为纯子串链接）。
     */
    KeywordPair deriveKeywords(Long kbId, String query) {
        if (query == null || query.length() < 2) {
            return null;
        }
        KeywordPair cached = keywordCache.getIfPresent(kbId + ":" + query);
        if (cached != null) {
            return cached;
        }
        try {
            chatPort.withContext("kg_keywords", String.valueOf(kbId));
            String raw = chatPort.complete(KEYWORD_PROMPT, query);
            KeywordPair pair = parseKeywordJson(raw);
            if (pair != null) {
                keywordCache.put(kbId + ":" + query, pair);
            }
            return pair;
        } catch (Exception e) {
            log.warn("KG 双层关键词派生失败 kbId={}（退化为子串链接）: {}", kbId, e.getMessage());
            return null;
        } finally {
            chatPort.clearContext();
        }
    }

    /** 解析 LLM 输出的 {"low":[...],"high":[...]}，字段缺失/非法时为 null */
    static KeywordPair parseKeywordJson(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher m = JSON_OBJECT_PATTERN.matcher(raw);
        if (!m.find()) {
            return null;
        }
        try {
            Map<String, Object> parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(m.group(), new TypeReference<Map<String, Object>>() {
                    });
            List<String> low = normalizeKeywords(parsed.get("low"));
            List<String> high = normalizeKeywords(parsed.get("high"));
            if (low.isEmpty() && high.isEmpty()) {
                return null;
            }
            return new KeywordPair(low, high);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> normalizeKeywords(Object rawList) {
        List<String> out = new ArrayList<>();
        if (rawList instanceof List<?> list) {
            for (Object o : list) {
                String word = o == null ? null : str(o);
                if (word != null && word.length() >= 2) {
                    out.add(word.length() > 32 ? word.substring(0, 32) : word);
                }
                if (out.size() >= 4) {
                    break;
                }
            }
        }
        return out;
    }

    /** 关键词扩展链接：low 词与实体名双向子串匹配；high 词匹配实体 description（子串任一方向） */
    Set<Long> linkByKeywords(Long kbId, KeywordPair keywords) {
        Set<Long> hits = new LinkedHashSet<>();
        for (KgEntity e : graphStore.entities(kbId)) {
            String name = e.getName();
            if (name == null || name.length() < 2) {
                continue;
            }
            for (String word : keywords.low()) {
                if (name.contains(word) || word.contains(name)) {
                    hits.add(e.getId());
                    break;
                }
            }
            if (!hits.contains(e.getId()) && e.getDescription() != null) {
                for (String word : keywords.high()) {
                    if (e.getDescription().contains(word)) {
                        hits.add(e.getId());
                        break;
                    }
                }
            }
        }
        return hits;
    }

    // ---- 生命周期 ----

    public void deleteByKb(Long kbId) {
        graphStore.deleteByKb(kbId);
    }

    public void deleteEdgesByDocument(Long kbId, Long docId) {
        graphStore.deleteEdgesByDocument(kbId, docId);
    }

    public List<KgEntity> listEntities(Long kbId, int limit) {
        List<KgEntity> all = graphStore.entities(kbId);
        return all.subList(0, Math.min(Math.max(limit, 1), all.size()));
    }

    public List<KgEdge> listEdges(Long kbId, int limit) {
        List<KgEdge> all = graphStore.edges(kbId);
        return all.subList(0, Math.min(Math.max(limit, 1), all.size()));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        return (Map<String, Object>) o;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private Matcher JSON_OBJECT(String raw) {
        Matcher m = JSON_OBJECT_PATTERN.matcher(raw == null ? "" : raw.trim());
        return m.find() ? m : null;
    }

    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{.*}", Pattern.DOTALL);

    private String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        String v = value.strip();
        if (v.isEmpty() || "null".equals(v)) {
            return null;
        }
        return v.length() <= max ? v : v.substring(0, max);
    }
}
