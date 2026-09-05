# 知识智能层分层重构蓝图（AI Layer Refactor）

> **状态**：M0/M1 已完成；v2 修订实施中
> **背景**：模型与生态发展带来新路径（agentic RAG、编排框架、模型角色分工）；本项目底座（LangChain4j + 混合检索）与业界主流一致，**不换底座，重构上层**
> **约束**：允许破坏性变更（用于包结构/配置命名空间/新契约；无收益的 API/DB 破坏不做）
> **关联**：[wiki-agent-graph.md](./wiki-agent-graph.md)（其设计作为 M2 蓝本）；日期 2026-09-05

---

## 0. v2 约束修订（2026-09-05，面向传统企业下沉部署）

四条新约束及架构响应：

| # | 约束 | 架构响应 |
|---|------|---------|
| 1 | 传统企业基础设施：**MySQL 5.7、Redis 5/6、向量存储本地优先** | **PostgreSQL/pgvector 已整体移除**（含依赖、compose、k8s、schema、脚本），部署收敛为单一形态：MySQL 5.7 + Redis 5/6 + 本地文件向量索引。MyBatis-Plus Wrapper 均为可移植 SQL（已核），Redis 用面仅 SET NX / Pub-Sub / INCR（5.0+ 全支持） |
| 2 | **去掉本地 ONNX，LLM 与 Embedding 全部云端化** | 移除 OnnxEmbeddingProvider / OnnxRerankScoringModel / onnxruntime / DJL 依赖；EmbeddingProvider 新增 `openai` provider（OpenAI 兼容 `/v1/embeddings`，覆盖 DashScope compatible-mode / 硅基流动 / OpenAI）；Rerank 仅保留云端（Cohere/Jina，已就绪）或关闭。云端 LLM 支持（Ollama/DashScope/OpenAI）已在 `LlmProvider` 就绪 |
| 3 | **引入 langgraph4j 实现平台编排** | M2 编排层选型由 langchain4j-agentic 改为 **`org.bsc.langgraph4j:langgraph4j-core:1.8.26`**（稳定线，纯图编排，不绑 LangChain4j 版本；节点调用 `ai/port` 端口，零耦合） |
| 4 | langgraph4j 能否平替 langchain4j | **不能。** langgraph4j 仅是编排层（StateGraph/边/状态机），无模型调用、无 Embedding、无向量存储、无文档解析能力；其 langchain4j 集成模块 POM 中 `dev.langchain4j:langchain4j:1.19.0` 为 compile 依赖。结论：**保留 LangChain4j 作底座**（已压缩至 `langchain4j/` 适配器 + `llm/` + `embedding/` 内），langgraph4j 做编排层，二者为上下层关系 |

部署形态关键差异：

| 维度 | Classic（新增，面向传统企业） | Standard（现有） |
|------|------------------------------|-----------------|





---

## 1. 耦合测绘（重构前基线）

| 现状 | 数据 |
|------|------|
| AI 适配层（自洽，属适配器本职） | `langchain4j/` 25 文件、`llm/` 7、`embedding/` 7 |
| service 层直接 import `dev.langchain4j` | **7 个文件**：`AgenticRetrievalService`、`ChatServiceImpl`、`RagServiceImpl`、`SearchServiceImpl`、`IndexTaskProcessor`、`WriterService`、`WikiCompileService` |
| service 层 import 适配器实现类 | `KbEmbeddingStore(Factory)`、`KbChatAssistantFactory`、`DbChatMemoryStore`、`RetrievedContentMapper`、`SearchHitMapper`、`SearchRerankService`、`KbVectorIndexService`、`KbDocumentSplitter`、`KbEmbeddingIngestor` |
| 已有端口化先例 | `EmbeddingProvider`（embedding/）、`CacheProvider`（cache/）、`StorageProvider`（storage/）、`QueryRewriter`（service/ 接口 + langchain4j/ 实现） |

**结论**：重构 = 把 `EmbeddingProvider` 范式推广到 Chat / 对话 / 检索 / 重排 / 索引五个面，并让 agentic 编排成为一等公民。

## 2. 目标架构（终态）

```
com.fast.knowledge
├── ai/
│   ├── port/          领域端口（零 dev.langchain4j import）     ← M1
│   │   ├── ChatPort            单轮生成（complete / stream）
│   │   ├── ConversationPort    多轮对话（记忆+检索+溯源+缓存失效）
│   │   ├── VectorSearchPort    知识库级混合向量检索
│   │   ├── RerankPort          重排
│   │   └── IngestPort          分块 / 向量化 / 向量索引增删
│   ├── orchestration/ agentic 编排（唯一允许 import agentic 的包）  ← M2
│   │   ├── WikiAgentService / WriterGraphService
│   │   └── retrieval/  RetrievalOrchestrator（Rewriter → WikiRouter → MultiHop 策略链）
│   └── （langchain4j/ 物理迁移为 ai/langchain4j/）                  ← M4（纯机械移动）
├── llm/               配置解析+热刷新（适配侧）；M3 增加模型角色路由
└── embedding/         EmbeddingProvider 即 EmbeddingPort（沿用）
```

分层规则：
- `service/` 只依赖 `ai/port` + 领域实体/VO，**零 `dev.langchain4j` import，零适配器类 import**
- `langchain4j/`（适配器）实现端口、组装 langchain4j 类型；`llm/`、`embedding/` 同属适配侧
- `ai/orchestration/` 依赖端口 + `langchain4j-agentic`；编排框架可替换（备选 langgraph4j-core），对外行为不变

## 3. 端口契约（M1 落地签名）

```java
package com.fast.knowledge.ai.port;

public interface ChatPort {
    /** 单轮生成；systemPrompt 可为 null */
    String complete(String systemPrompt, String userPrompt);
    /** 流式生成（写文档等场景） */
    void stream(String systemPrompt, String userPrompt, StreamHandler handler);
    interface StreamHandler {
        void onPartial(String token);
        void onComplete(String fullText);
        void onError(Throwable error);
    }
}

public interface ConversationPort {
    /** 多轮对话：内部含记忆、KB 检索、溯源回调；调用即启动流 */
    void streamConversation(Long kbId, Long sessionId, String message, ConversationHandler handler);
    /** 索引/配置变更后失效按 KB 缓存的助手实例 */
    void evictAssistant(Long kbId);
    /** 删除会话记忆 */
    void deleteMemory(Long sessionId);
    /** 会话溯源附加信息 */
    void attachSources(Long sessionId, String sourcesJson);
    interface ConversationHandler {
        void onRetrieved(List<SearchHitVO> sources);
        void onPartial(String token);
        void onComplete();
        void onError(Throwable error);
    }
}

public interface VectorSearchPort {
    /** 混合检索：向量 + 全文（HYBRID），kb 级隔离 */
    List<SearchHitVO> search(Long kbId, float[] queryVector, String query, int fetchK, String docType);
}

public interface RerankPort {
    boolean isActive();
    int candidateCount(int topK);
    List<SearchHitVO> rerank(String query, List<SearchHitVO> hits, int topK);
}

public interface IngestPort {
    /** 文本分块（适配器内构造文档元数据） */
    List<String> split(String fullText, Long kbId, Long docId, String title);
    /** 块向量化写入向量库（DB 块行已持久化后调用） */
    void embedChunks(KbDocument doc, List<DocumentChunk> chunks);
    void deleteByDocument(Long kbId, Long docId);
    void deleteChunk(Long kbId, Long chunkId);
    void deleteKb(Long kbId);
}
```

`EmbeddingPort` = 现有 `embedding/EmbeddingProvider`（`float[] embed(String)` / `embedBatch` / `dimension`），不改名不搬移，直接作为端口使用。

## 4. 适配器映射（新增 `langchain4j/adapter/`）

| 端口 | 适配器 | 封装的现有组件 |
|------|--------|---------------|
| `ChatPort` | `LangChain4jChatAdapter` | `LlmModelRegistry.getChatModel()/getStreamingChatModel()` |
| `ConversationPort` | `LangChain4jConversationAdapter` | `KbChatAssistantFactory`（TokenStream 装配）+ `RetrievedContentMapper` + `DbChatMemoryStore` |
| `VectorSearchPort` | `LangChain4jVectorSearchAdapter` | `KbEmbeddingStoreFactory` + `EmbeddingSearchRequest` + `SearchHitMapper` |
| `RerankPort` | `LangChain4jRerankAdapter` | `SearchRerankService` |
| `IngestPort` | `LangChain4jIngestAdapter` | `KbDocumentSplitter`（Document/Metadata 构造）+ `KbEmbeddingIngestor` + `KbVectorIndexService` |

## 5. 服务层改造清单（M1）

| 文件 | 改造 |
|------|------|
| `RagServiceImpl` | `ChatModel` → `ChatPort.complete()` |
| `WikiCompileService` | `ChatModel` → `ChatPort.complete()` |
| `AgenticRetrievalService` | `LlmModelRegistry` → `ChatPort.complete()` |
| `WriterService` | `StreamingChatModel` → `ChatPort.stream()` |
| `ChatServiceImpl` | `KbChatAssistantFactory`/`DbChatMemoryStore`/`TokenStream`/`Content` → `ConversationPort` |
| `SearchServiceImpl` | `EmbeddingModel`/`KbEmbeddingStoreFactory`/`SearchHitMapper`/`SearchRerankService` → `EmbeddingProvider` + `VectorSearchPort` + `RerankPort`（分段计时指标保持） |
| `IndexTaskProcessor` | `KbDocumentSplitter`/`KbEmbeddingIngestor`/`KbVectorIndexService`/`KbChatAssistantFactory` + `Document`/`Metadata`/`TextSegment` → `IngestPort` + `ConversationPort.evictAssistant()` |
| `DocumentService`/`KnowledgeBaseService`/`IndexRebuildService` | `KbVectorIndexService` → `IngestPort` |

## 6. 里程碑

| 里程碑 | 内容 | 状态 |
|--------|------|------|
| **M0 基线加固** | `langchain4j.version` 1.17.1→1.17.2（agentic 同线 1.17.2-beta27 可用）；全量测试回归冻结基线 | ✅ 完成（73 单测 + 11 前端全绿） |
| **M1 端口抽取** | §3-§5 全部内容；验收：service 层 grep 零 `dev.langchain4j`、测试全绿 | ✅ 完成 |
| **M2 编排一等公民** | `ai/orchestration/`：**langgraph4j-core 1.8.26** StateGraph 落位（WikiAgentService + WriterGraphService，单测 8 个全绿）；检索三组件收编为策略链（行为不变）仍待做 | 核心已实施 |
| **M2.5 云端模型化**（v2 新增） | 移除 ONNX/DJL；EmbeddingProvider 增 `openai` provider；Rerank 仅云端/关闭；`application-ai.yml` 默认值切换 | ✅ 完成 |
| **M2.6 Classic 部署形态**（v2 新增） | `schema-mysql.sql`（MySQL 5.7）；`LocalEmbeddingStore`（本地文件向量索引，双轨切换）；Redis 5/6 兼容验证；`mysql` profile。**实测记录**（MySQL 5.7.44 容器 + hash embedding + enterprise）：自动建表 17 张、登录/建库/保存文档/索引/中文检索全链路通过、`data/vectors/kb-1.json` 落盘、无 LLM 时 Wiki 编译任务 best-effort FAILED 不影响主链路。实测修复 2 个可移植性缺口：`SystemConfigMapper.upsert` 的 PG 专属 `ON CONFLICT` 改为 Java 层跨方言实现；`MinioStorageProvider` 增加 bucket 缺失自动创建（首跑不再依赖 compose init） | ✅ 完成 |
| **M3 模型角色化** | `llm/` 角色绑定：`main`（生成）/`light`（rewrite/lint/拆解），管理端可配，缺省回退 main | 待 M2 |
| **M4 契约与迁移** | `langchain4j/` 物理迁移 `ai/langchain4j/`；配置命名空间 `knowledge.ai.*`；LC4J 升级评估（独立 PR） | 待 M3 |
| **M5 文档收尾** | architecture/README、迁移指南（含 Classic 形态部署指南）、automation-report | 收尾 |

## 7. 风险与缓解

| 风险 | 缓解 |
|------|------|
| 包移动与逻辑变更混合致评审困难 | M1 只动依赖不改逻辑；物理移动（M4）独立 commit |
| 行为回归 | M0 基线冻结；每里程碑全量 `mvn test` + 前端 Vitest |
| 指标分段（timeEmbedding/timeVectorSearch）跨端口丢失 | 端口粒度按指标分段设计（embed 与 search 分离） |
| 编排框架锁定 | agentic 仅出现在 `ai/orchestration/`；对外行为不变可换 langgraph4j-core |

## 8. 验收标准

- [ ] `grep -r "dev.langchain4j" service/` 零命中
- [ ] service 层零适配器实现类 import（只依赖 `ai/port`）
- [ ] 全量测试（后端 + 前端）与 M0 基线一致全绿
- [ ] 每里程碑独立可发布，flag 语义不变
