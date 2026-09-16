# Fast Knowledge 智能化升级开发方案（v2.1 → v3.0）

> 目标：在不偏离"中小企业私有化知识库"定位的前提下，把检索质量做到第一梯队（Contextual Retrieval / Agentic RAG / GraphRAG），用评测闭环证明质量，用审计/离线/权限守住企业交付门槛。
>
> **硬约束：不新增任何中间件。** 全部新能力基于现有 MySQL 5.7 + Redis + 阿里云 OSS/MinIO + ECS 单机构建。

---

## 0. 基础设施利用总表

| 新能力 | 实现方式 | 不引入的东西 |
|--------|----------|--------------|
| 知识图谱（GraphRAG） | MySQL 邻接表（kg_entity / kg_edge），限 1-2 跳查询 | ~~Neo4j / NebulaGraph~~ |
| 异步任务（VLM 解析、KG 抽取、评测执行） | 现有 IndexTask 任务表 + IndexTaskProcessor，扩展 task_type | ~~Kafka / RocketMQ~~ |
| 语义缓存 | L1 Caffeine 内存向量比对 + L2 Redis 结果缓存（扩展现有 SearchCacheService） | ~~Redis Stack 向量检索~~ |
| 解析产物 / 评测导出 / trace 大报文 | OSS（STORAGE_PROVIDER=oss 已支持）与 MinIO 同接口 | ~~新对象存储~~ |
| 定时任务（过期提醒、缺口聚类） | ECS systemd timer 调用管理 API / @Scheduled | ~~XXL-Job~~ |
| 可观测 | 现有 Micrometer + Prometheus + 审计表，新增 llm_trace 表 | ~~Langfuse 独立部署~~ |
| MCP / IM 机器人入口 | 同一 Spring Boot Jar 新增端点，复用 API Key 鉴权 | ~~独立网关服务~~ |
| 向量索引 | 现有本地 Lucene（HNSW）+ ./data/vectors，规模阈值内不换 | ~~Milvus / ES~~ |

## 1. 里程碑总览

| 里程碑 | 主题 | 工作包 | 预估（2 后端 + 1 前端） |
|--------|------|--------|------------------------|
| **v2.1.0** | 检索质量地基 | WP1 上下文化分块、WP2 扫描件 VLM 解析入库、WP3 评测闭环、WP4 本地重排+语义缓存、WP5 引用溯源增强 | 6-8 周 |
| **v2.2.0** | Agentic 与图谱 | WP6 Agentic 检索闭环、WP7 GraphRAG（MySQL 存图）、WP8 MCP Server、WP9 主动知识运营、WP10 LLM 用量可观测 | 8-10 周 |
| **v3.0.0** | 多模态与连接 | WP11 视觉检索（探索）、WP12 Text2SQL 只读问答、WP13 IM 机器人、WP14 Wiki Agent 全自动转正 | 8 周+（WP11 探索性） |

每个 WP 合入前必须通过 WP3 评测集的门禁指标（不回退），这是"最先进"可被证明的方式。

---

## 2. v2.1.0 —— 检索质量地基

### WP1 上下文化与语义分块（Contextual Retrieval + Late-Chunking 思路）

- **目标**：解决"制度/工艺长文档切块后脱离上下文导致检索失败"。业界基准：检索失败率约 -67%（Anthropic Contextual Retrieval）。
- **方案**：`ChunkService` 切分后，用小模型（glm-4-flash / qwen-flash 档，成本可控）为每个 chunk 生成 1-2 句上下文前缀（含文档主题、章节位置、适用部门），前缀与正文**一起**向量化并进 Lucene 索引。
- **落点**：
  - 新增 `ai/port/ChunkContextPort`（沿现有 port 风格），实现走 ChatPort 小模型调用；
  - `kb_chunk` 表加列 `context_prefix VARCHAR(512)`、`index_version INT`；
  - 开关 `KNOWLEDGE_INGEST_CONTEXTUAL_ENABLED`（默认关，按 KB 灰度开启）；已入库文档通过 `IndexRebuildService` 新增"重嵌入"任务类型平滑重建（新旧版本双写，切换后下线旧索引）。
- **基础设施**：MySQL（新列）、LLM 调用走现有 LLM 中立配置；OSS 不涉及。
- **验收**：golden set 上 recall@5 / MRR 提升 ≥10%；单文档索引耗时增幅 ≤40%（并发 + 批量 prompt 控制）。

### WP2 扫描件 VLM 解析入库

- **目标**：制造业大量扫描工艺卡/红头文件目前抽不出文本。把 VisionService 从"问答"扩展到"入库解析"。
- **方案**：`DocumentIngestService` 流程中，`TextExtractionService` 抽出文本过短或置信度低 → 判定扫描件 → 按页转图 → `VisionPort` 调 qwen-vl 抽取 Markdown（表格输出 HTML），产物作为解析文本进入正常分块/索引管道。
- **落点**：
  - 解析产物存 OSS：`knowledge/parsed/{docId}/page-{n}.md`（可追溯、可重跑）；
  - IndexTask 新增 `task_type=OCR_PARSE`，复用现有重试（INDEX_MAX_RETRY）与 Redis 锁防并发重复；
  - 成本护栏：管理界面开关 + 每月页数配额（MySQL 计数 + Redis 原子限流），超配额任务挂起并告警。
- **验收**：样本扫描工艺卡入库后，检索可命中、问答可引用；解析页均成本可在管理界面看到。

### WP3 评测闭环（质量门禁，全方案的地基）

- **目标**：让"检索变好了"从感觉变成数字，并作为后续所有 WP 的合入门禁。
- **方案**：管理界面内置评测：数据集（问题 + 期望 chunk/关键词）→ 一键跑分 → 指标（recall@k、MRR、忠实度——忠实度用当前已配 LLM 做 judge）→ 两次 run 对比。
- **落点**：
  - 新表：`eval_dataset`、`eval_case`、`eval_run`（含配置快照与分数 JSON）、`eval_run_item`；
  - 执行复用任务表（`task_type=EVAL_RUN`）；进行中加 Redis 锁；
  - 前端新增 `/settings/evals` 页面（数据集 CRUD、运行、diff 视图）；
  - 附带 CLI 入口（Spring Runner），ECS 上可挂 systemd timer 跑夜间回归。
- **基础设施**：MySQL（评测数据）、Redis（执行锁）、OSS（run 明细 JSON 导出）。
- **验收**：v2.1 其余 WP 合并前各跑一次基线，指标无回退；从上传样本到出分 ≤5 分钟（百题级）。

### WP4 本地重排 + 语义缓存

- **目标**：纯内网（不出域）模式也有重排；重复问句命中 <50ms。
- **方案**：
  - `RerankPort` 新增 local 实现：ONNX `bge-reranker-v2-m3`（CPU 推理），`RERANK_PROVIDER=local`，`ProductionConfigValidator` 在离线模式放行；
  - `SearchCacheService` 扩展语义缓存：L1 Caffeine 内保存近期问题向量做 top-1 余弦比对（阈值 0.95），命中直接返回缓存结果；L2 Redis 沿用 query-hash → 结果，TTL 可配。
- **基础设施**：Redis（L2，现有）、Caffeine（L1，现有）、本地 ONNX（与现有本地 embedding 同栈，不新增服务）。
- **验收**：离线模式下重排生效；语义缓存命中率可观测（Micrometer 指标），命中延迟 P95 <50ms。

### WP5 引用溯源增强（段落 → 表格/图片级）

- **方案**：`kb_chunk` 增加 `page_no`、`anchor_type`（text/table/image）、`anchor_ref`（OSS 上解析产物锚点）；问答引用返回类型化引用列表；`DocumentPreviewDrawer` 支持滚动定位到具体表格/图（解析时保留锚点）。
- **验收**：对含表格的样本文档，问答引用可精确跳到表格并高亮。

---

## 3. v2.2.0 —— Agentic 与图谱

### WP6 Agentic 检索闭环

- **方案**：`AgenticRetrievalService` + `RetrievalOrchestrator` 从"查询分解"升级为完整循环：**规划（分解/选路）→ 并行多路检索 → LLM 自评充分性 → 不足则重检索（改写/换路，最多 N 轮）**。`QueryComplexityClassifier` 已有：简单问题直走单路，控制成本与延迟。
- **落点**：`AGENTIC_MAX_ROUNDS`、`AGENTIC_SELF_CRITIQUE` 配置；chat SSE 增加"检索步骤"事件，前端对话流展示"第 2 轮检索…"过程（增强可感知智能）。
- **验收**：多跳测试集答对率提升；P95 延迟增幅 ≤30%（并行子查询 + 提前退出）。

### WP7 GraphRAG（MySQL 存图，企业版门控）

> 图存储选型决策与升级路径详见 [architecture/graph-storage-selection.md](architecture/graph-storage-selection.md)。

- **方案**：只对高价值实体建图（制度-部门-流程-设备-岗位），不做全量通用图谱。
  - 构建：索引完成后异步任务 `KG_EXTRACT`，LLM 抽实体/关系 → MySQL 唯一键 + 向量相似度合并去重；
  - 查询：问题实体链接（分词 + 向量辅助匹配 `kg_entity`）→ 1-2 跳邻居 → 邻居关联 chunk 进入召回池 → 与现有向量/BM25 走统一 RRF 融合；
  - 1-2 跳限制 + KB 级隔离保证 MySQL 自连接查询毫秒级。
- **新表**：`kg_entity(kb_id,name,type,desc,source_chunk_id)`、`kg_edge(src,dst,relation,weight,evidence_chunk_id)`、`kg_build_run`。
- **验收**："A 部门的哪份制度规定了 B 设备的检修周期"类多跳问题命中；每文档构建 LLM 调用数有预算上限。

### WP8 MCP Server（知识库即服务）

- **方案**：同一 Jar 暴露 MCP（SSE/Streamable HTTP）端点，工具：`search(kb,query)`、`qa(kb,question)`、`list_kbs()`、`fetch_chunk(id)`；鉴权复用 API Key；审计复用 AuditLogService（actor=api_key）。企业内其他 AI 工具（IDE、IM 助手、自研 Agent）直接接入。
- **基础设施**：ECS 同端口部署，不新增进程；出站零依赖，不出域模式不受影响。
- **验收**：用标准 MCP 客户端完成检索/问答闭环；API Key 权限与审计全链路可查。

### WP9 主动知识运营（差异化重点）

- **知识缺口**：无结果/低分检索写 `kb_gap_query`，定时聚类（embedding 近邻）→ 管理界面"知识缺口"页 → 指导补文档；
- **过期提醒**：基于已有文档生效日期，systemd timer 定时扫描 → 站内通知 + 可插 webhook（企业微信机器人）；
- **冲突检测**：新文档入库时与既有 chunk 高相似（>阈值）→ 冲突候选列表人工裁决。
- **验收**：三项均可从管理界面看到可执行的建议列表（而非原始日志）。

### WP10 LLM 用量可观测

- **方案**：新表 `llm_trace(scene,provider,model,latency,tokens,cost_est,correlation_id)`；大 prompt 快照可选归档 OSS；管理界面"大模型配置"页增加用量统计卡片（按场景/按日）。
- **验收**：任一问答可凭 correlation_id 串起 检索→重排→生成 全链路耗时与费用。

---

## 4. v3.0.0 —— 多模态与连接（P2，含探索项）

| WP | 内容 | 关键点 |
|----|------|--------|
| WP11 视觉检索 | 页面图存 OSS + 视觉向量/说明文混合检索（ColPali 思路降级实现：VLM caption + 页级向量，避免引入 late-interaction 专用索引） | **探索性**，先做报告类文档场景验证 |
| WP12 Text2SQL 只读问答 | 客户业务库只读账号，表/列白名单元数据入 MySQL，LLM 生成 SQL → 校验器（只读+LIMIT+白名单+超时）→ 执行；与文档问答混合编排 | 审计留痕；默认关闭，按客户开通 |
| WP13 IM 机器人 | 企业微信/钉钉回调入口，复用 API Key + RagService，适配被动回复分段/限流 | 不新增服务，同 Jar 新 Controller |
| WP14 Wiki Agent 转正 | `WIKI_AGENT_ENABLED` 企业版默认开，增量合并 + Lint 循环（langgraph4j 图已有） | 人工审核队列保留 |

---

## 5. 数据模型与配置增量汇总

**新表**：`eval_dataset` / `eval_case` / `eval_run` / `eval_run_item`、`kg_entity` / `kg_edge` / `kg_build_run`、`kb_gap_query`、`llm_trace`。
**改表**：`kb_chunk` +（`context_prefix`、`index_version`、`page_no`、`anchor_type`、`anchor_ref`）；IndexTask 扩展 `task_type`（OCR_PARSE / KG_EXTRACT / EVAL_RUN / REEMBED）。
**新配置**（全部环境变量可覆盖，沿用现有前缀风格）：`KNOWLEDGE_INGEST_CONTEXTUAL_ENABLED`、`OCR_PARSE_ENABLED` / `OCR_PARSE_MONTHLY_QUOTA`、`RERANK_PROVIDER=local`、`SEMANTIC_CACHE_THRESHOLD` / `SEMANTIC_CACHE_TTL`、`AGENTIC_MAX_ROUNDS` / `AGENTIC_SELF_CRITIQUE`、`KG_ENABLED`（企业版）、`MCP_ENABLED`。
**升级兼容**：所有重索引类变更走 `index_version` 双写灰度；schema 变更进 `db/migration`（SQL 脚本 + `install.sh update` 钩子执行），保持一键升级与失败回滚能力。

## 6. 风险与对策

| 风险 | 对策 |
|------|------|
| LLM 成本失控（分块上下文、KG 抽取、评测 judge） | 小模型档 + 批量 prompt + WP10 用量看板 + 月度配额熔断 |
| MySQL 图查询慢 | 限 1-2 跳、KB 级分表查询、实体表 (kb_id,name) 索引；超规模再评估，仍不引入图库 |
| 扫描解析质量不稳 | 解析产物存 OSS 可重跑 + 抽样人审界面 + 置信度阈值分流 |
| 升级破坏已入库数据 | index_version 灰度双写；`install.sh update` 健康检查失败自动回滚（现有机制） |
| 评测集代表性不足 | 按场景模板建集（已有 ScenarioTemplateService 可复用），运营中持续从真实查询补题（WP9 数据反哺） |

## 7. 不做清单（守住定位）

- 不自研训练/微调通用模型；
- 不引入 Milvus / Elasticsearch / Neo4j / Kafka / 独立向量库；
- 不引入 Python 侧车服务（GraphRAG 按技术模式用 Java 实现，不复用微软 Python 项目）；
- 不做通用 Agent 工作流平台；
- GPU 不作为依赖（本地 ONNX 均 CPU 可跑）。

---

## 8. 建议实施顺序

```
WP3 评测闭环 ──► WP1 上下文化分块 ──► WP4 本地重排+语义缓存 ──► v2.1.0 发布
        │              │
        │              └──► WP2 扫描件解析（可并行）
        └──► 每个后续 WP 的合入门禁

v2.2: WP6 Agentic ──► WP7 GraphRAG ──► WP8 MCP ──► WP9 运营 ──► WP10 观测
v3.0: WP12/WP13（确定性收益）先行，WP11 探索验证后再决定投入
```

> 评测先行是本方案的关键纪律：没有 WP3，后面每个"先进"都只是感觉；有了 WP3，每一步都可证明。
