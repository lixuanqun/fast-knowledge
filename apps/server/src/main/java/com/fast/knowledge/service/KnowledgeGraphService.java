package com.fast.knowledge.service;

import com.fast.knowledge.ai.port.ChatPort;
import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.mapper.DocumentChunkMapper;
import com.fast.knowledge.mapper.DocumentMapper;
import com.fast.knowledge.mapper.KgEdgeMapper;
import com.fast.knowledge.mapper.KgEntityMapper;
import com.fast.knowledge.model.entity.DocumentChunk;
import com.fast.knowledge.model.entity.KbDocument;
import com.fast.knowledge.model.entity.KgEdge;
import com.fast.knowledge.model.entity.KgEntity;
import com.fast.knowledge.model.vo.SearchHitVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
 * WP7 知识图谱（GraphRAG 轻量版）— 存储为 MySQL 邻接表，查询限 1 跳扩展。
 *
 * <p>构建：索引完成后 LLM 抽取实体/关系（单文档单次调用），实体按 (kbId, name) 幂等合并，
 * 边按 (kbId, src, dst, relation) 幂等合并并记录证据文档。
 * 检索：问题做实体链接（名称子串匹配）→ 1 跳邻居的证据文档 chunk 进入扩展召回，
 * 由 RetrievalOrchestrator 与向量/关键词结果融合。
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
    private final KgEntityMapper entityMapper;
    private final KgEdgeMapper edgeMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final DocumentMapper documentMapper;

    public KnowledgeGraphService(KnowledgeProperties properties, ChatPort chatPort, ObjectMapper objectMapper,
                                 KgEntityMapper entityMapper, KgEdgeMapper edgeMapper,
                                 DocumentChunkMapper documentChunkMapper, DocumentMapper documentMapper) {
        this.properties = properties;
        this.chatPort = chatPort;
        this.objectMapper = objectMapper;
        this.entityMapper = entityMapper;
        this.edgeMapper = edgeMapper;
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
                KgEntity existing = entityMapper.selectOne(Wrappers.<KgEntity>lambdaQuery()
                        .eq(KgEntity::getKbId, kbId)
                        .eq(KgEntity::getName, name)
                        .last("LIMIT 1"));
                if (existing != null) {
                    nameToId.put(name, existing.getId());
                    continue;
                }
                KgEntity entity = new KgEntity();
                entity.setKbId(kbId);
                entity.setName(name);
                entity.setType(trim(str(e.get("type")) == null ? "其他" : str(e.get("type")), 32));
                entity.setDescription(trim(str(e.get("description")) == null ? "" : str(e.get("description")), 512));
                entityMapper.insert(entity);
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
                Long exists = edgeMapper.selectCount(Wrappers.<KgEdge>lambdaQuery()
                        .eq(KgEdge::getKbId, kbId)
                        .eq(KgEdge::getSrcId, srcId)
                        .eq(KgEdge::getDstId, dstId)
                        .eq(KgEdge::getRelation, relation));
                if (exists != null && exists > 0) {
                    continue;
                }
                KgEdge edge = new KgEdge();
                edge.setKbId(kbId);
                edge.setSrcId(srcId);
                edge.setDstId(dstId);
                edge.setRelation(relation);
                edge.setEvidenceDocId(docId);
                edgeMapper.insert(edge);
                count++;
            }
        }
        return count;
    }

    // ---- 检索扩展 ----

    /**
     * 问题实体链接 → 1 跳邻居的证据文档 chunk 构造扩展召回（score 固定低权重 0.3）。
     * 命中实体为空或扩展超出上限时返回空列表。
     */
    public List<SearchHitVO> expandForQuery(Long kbId, String query, int limit) {
        List<KgEntity> linked = linkEntities(kbId, query);
        if (linked.isEmpty()) {
            return List.of();
        }
        Set<Long> entityIds = new LinkedHashSet<>();
        linked.forEach(e -> entityIds.add(e.getId()));

        // 1 跳邻居
        List<KgEdge> edges = edgeMapper.selectList(Wrappers.<KgEdge>lambdaQuery()
                .eq(KgEdge::getKbId, kbId)
                .and(w -> w.in(KgEdge::getSrcId, entityIds).or().in(KgEdge::getDstId, entityIds)));
        Set<Long> neighborIds = new LinkedHashSet<>();
        Set<Long> evidenceDocIds = new LinkedHashSet<>();
        for (KgEdge edge : edges) {
            boolean linkedSrc = entityIds.contains(edge.getSrcId());
            neighborIds.add(linkedSrc ? edge.getDstId() : edge.getSrcId());
            if (edge.getEvidenceDocId() != null) {
                evidenceDocIds.add(edge.getEvidenceDocId());
            }
        }

        int chunkLimit = Math.max(1, properties.getKg().getNeighborChunkLimit());
        List<SearchHitVO> out = new ArrayList<>();
        if (evidenceDocIds.isEmpty()) {
            return out;
        }
        // 邻居实体的证据文档 chunks → 扩展候选（去重由调用方 merge 保证）
        Map<Long, String> docTitles = new HashMap<>();
        for (Long evidenceDocId : evidenceDocIds) {
            if (out.size() >= chunkLimit) {
                break;
            }
            KbDocument doc = documentMapper.selectById(evidenceDocId);
            String docTitle = doc != null && doc.getTitle() != null ? doc.getTitle() : "";
            docTitles.put(evidenceDocId, docTitle);
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
                vo.setScore(0.3);
                out.add(vo);
            }
        }
        if (!out.isEmpty()) {
            log.info("KG 扩展召回 kbId={} 命中实体={} 邻居={} 扩展chunk={}", kbId,
                    linked.stream().map(KgEntity::getName).toList(),
                    neighborIds.stream().map(id -> {
                        KgEntity e = entityMapper.selectById(id);
                        return e != null ? e.getName() : String.valueOf(id);
                    }).toList(),
                    out.size());
        }
        return out;
    }

    /** 实体链接：问题文本包含实体名（≥2 字）即命中 */
    List<KgEntity> linkEntities(Long kbId, String query) {
        if (query == null || query.length() < 2) {
            return List.of();
        }
        List<KgEntity> all = entityMapper.selectList(Wrappers.<KgEntity>lambdaQuery()
                .eq(KgEntity::getKbId, kbId));
        List<KgEntity> linked = new ArrayList<>();
        for (KgEntity e : all) {
            if (e.getName() != null && e.getName().length() >= 2 && query.contains(e.getName())) {
                linked.add(e);
            }
        }
        return linked;
    }

    // ---- 生命周期 ----

    public void deleteByKb(Long kbId) {
        edgeMapper.delete(Wrappers.<KgEdge>lambdaQuery().eq(KgEdge::getKbId, kbId));
        entityMapper.delete(Wrappers.<KgEntity>lambdaQuery().eq(KgEntity::getKbId, kbId));
    }

    public void deleteEdgesByDocument(Long kbId, Long docId) {
        edgeMapper.delete(Wrappers.<KgEdge>lambdaQuery()
                .eq(KgEdge::getKbId, kbId)
                .eq(KgEdge::getEvidenceDocId, docId));
    }

    public List<KgEntity> listEntities(Long kbId, int limit) {
        return entityMapper.selectList(Wrappers.<KgEntity>lambdaQuery()
                .eq(KgEntity::getKbId, kbId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 500)));
    }

    public List<KgEdge> listEdges(Long kbId, int limit) {
        return edgeMapper.selectList(Wrappers.<KgEdge>lambdaQuery()
                .eq(KgEdge::getKbId, kbId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 500)));
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
