# 文档生产流水线设计：Wiki 维护 Agent 与写文档多步编排

> **状态**：评审中（设计文档，未实施）
> **对应规划**：[v2.0.0 §一 M2](../releases/v2.0.0.md)「Wiki 维护 Agent（增量合并 + Lint）」+ 写文档多步升级
> **关联代码**：`apps/server/src/main/java/com/fast/knowledge/service/WikiCompileService.java`、`WriterService.java`
> **日期**：2026-09-04

---

## 1. 背景与目标

### 1.1 现状问题

| # | 问题 | 位置 |
|---|------|------|
| P1 | **全量重生成**：文档每次重索引都把对应 Wiki 页整页重新生成，页面上的人工修订被冲掉 | `WikiCompileService.compileDocument()` |
| P2 | **无质量关卡**：编译结果没有 Lint 检查（文号/日期一致性、结构完整性、内容矛盾） | 同上 |
| P3 | **无变更记录**：Wiki 页 version 递增但没有变更摘要，无法审计「这次改了什么」 | `saveWikiResult()` |
| P4 | **写文档单次生成**：AI 写文档是「RAG 拼上下文 + 单次 LLM 流式输出」，长文档无大纲规划、无分节迭代 | `WriterService.generate()` |

已就绪、无需重建的部分：

- Wiki 审核发布流（`WikiService.publish/reject`，DRAFT→PUBLISHED）、`index.md` 目录重建
- 双路召回路由（`WikiQueryRouter` + `WikiAwareRetrievalService`）
- **写文档沉淀闭环已存在**：`POST /writer/save` → `DocumentService.saveTextDocument()`（落库 + IndexTask + `dispatchIndex`）→ 索引成功后 `IndexTaskProcessor:180` 自动触发 `wikiCompileService.scheduleCompile()`；前端保存按钮与 `saveWriterDocument` API 均已有
- `kb_wiki_link` 表已在 `schema-postgres.sql` 预留（from_page_id → to_page_id），代码尚未引用

### 1.2 目标

1. 文档重索引时，Wiki 页走 **LLM 增量合并**（保留人工修订与既有结构），仅按新文档更新冲突内容
2. 编译/合并后自动 **Lint**（规则 + LLM 两级），发现问题自动修订（封顶 2 轮），未通过且非 autoPublish 时保持 DRAFT 衔接既有审核流
3. 每次变更写入 **变更日志**（from/to version + 摘要），并维护 `kb_wiki_link` 交叉引用
4. AI 写文档升级为 **大纲 → 分节 → 引用 → 润色** 的多步流式编排，向前兼容现有 SSE 契约

### 1.3 非目标

- Error Book（错误知识沉淀库）— 推后
- GraphRAG / Neo4j — 维持 v2.0.0「明确不做」结论
- 通用 Agent 平台、可视化工作流编辑器 — 与产品定位冲突
- 写文档工作流的断点续跑/时间旅行 — 任务均为分钟级短任务，不做 checkpoint 持久化

---

## 2. 业界调研与选型

### 2.1 Java 智能知识库格局（2026 年中）

| 框架 | 版本 | 定位 |
|------|------|------|
| Spring AI | 2.0.0 | Spring 官方生态；Advisor 链、ETL 管道、20+ 向量库抽象 |
| **LangChain4j**（本项目） | 1.17.x | 框架中立、组件粒度细；AI Services、RetrievalAugmentor、agentic 模块 |
| Spring AI Alibaba | 1.0 GA | 国内主流；Spring AI 之上加 Graph 运行时（DAG、持久化、长任务） |

开源 Java 同类产品（对标）：[langchain4j-aideepin](https://github.com/moyangzhan/langchain4j-aideepin)（LangChain4j + pgvector/Neo4j + workflow + MCP）、MaxKB4J 等。**本项目技术底座（LangChain4j + pgvector 混合检索 + 本地 ONNX Embedding/Rerank + Ollama + 审计 + 离线交付）与业界 Java 主流路径一致**，企业层完成度更高；差距集中在文档生产的 agentic 编排层。

### 2.2 编排层选型对比

2026 年 Java 生态 agentic 编排三条主流路线：Spring AI Alibaba Graph、LangGraph4j、langchain4j-agentic（[官方文档](https://docs.langchain4j.dev/tutorials/agents/)）。

| 方案 | 新增依赖 | 与本项目兼容性 | 能力匹配 | 主要顾虑 |
|------|---------|---------------|---------|---------|
| **langchain4j-agentic `1.17.1-beta27`** ✅ | 1 个官方模块 | **与 BOM 1.17.1 精确同版本线，零耦合** | sequential / parallel / **loop**（maxIterations + exitCondition）/ **conditional** / HITL 恰好覆盖需求 | 官方标注 experimental，API 可能演进 |
| langgraph4j-core `1.8.26` | 1 个第三方 | core 不绑 LangChain4j | StateGraph、条件边、检查点 | 社区项目；其 langchain4j 集成模块绑定 LC4J **1.19.0** |
| 手写编排 | 0 | 完全兼容 | 自写 | 循环/状态/HITL 代码随需求膨胀（参考 `AgenticRetrievalService` 已到复杂度上限） |
| 升级 LC4J 1.19 + langgraph4j-langchain4j | 2 个 | ❌ 需升 BOM | + 工具调用 | 升级回归风险 + beta 传递依赖 |

**结论（2026-09-05 修订）：采用 `org.bsc.langgraph4j:langgraph4j-core:1.8.26`。** 平台编排层约束确定引入 langgraph4j（见 [ai-layer-refactor.md](./ai-layer-refactor.md) §0）；core 稳定线为纯图编排（StateGraph/条件边/检查点），不绑定 LangChain4j 版本，节点内部调用 `ai/port` 端口（ChatPort 等），与底座零耦合。原 langchain4j-agentic 方案（与 BOM 同线的 sequential/loop/conditional 声明式工作流）作为备选保留：若 langgraph4j 的节点装配代码量超预期，可回到同线 agentic 模块，对外行为不变。决策依据：

1. 编排层独立选型：core 不含任何模型 I/O，不与 LangChain4j 1.17.2 产生版本耦合
2. StateGraph 的 conditional edges 恰好表达 merge/compile 分支与 lint→revise 循环（maxIterations 语义在节点内自管理）
3. 检查点存储（postgres/redis saver）为后续长任务断点续跑预留升级位
4. experimental 风险可控：图装配与节点逻辑隔离在 `ai/orchestration/` 内（见 §9），对外行为不变

> **说明：langgraph4j 不能替代 langchain4j。** 它只是编排层（StateGraph/边/状态通道），自身无模型调用、无向量库、无解析能力；其 langchain4j 集成模块的 POM 中 `dev.langchain4j:langchain4j:1.19.0` 为 compile 依赖。两者是上下层关系，只能叠加。

---

## 3. 总体架构

```
┌──────────────────────────────────────────────────────────┐
│  编排层（新增，flag 默认关闭）                              │
│  WikiAgentService（Wiki 维护图）  WriterGraphService（写文档图） │
│  ↳ langchain4j-agentic: sequence/conditional/loop          │
├──────────────────────────────────────────────────────────┤
│  基础层（保留不动）                                         │
│  ChatModel / StreamingChatModel ← LlmModelRegistry（热刷新） │
│  AI Services · PgVector HYBRID · Rerank · 本地 ONNX         │
├──────────────────────────────────────────────────────────┤
│  Spring Boot 3.5 · PostgreSQL/pgvector · Redis · MinIO     │
└──────────────────────────────────────────────────────────┘
```

- **图内只放 LLM agent**（merge/compile/lint/revise、outline/section/polish）；extract、persist 等确定性 Java 步骤放在 workflow 前后（`invoke()` 返回 `AgenticScope` 后读状态落库），不依赖非 LLM agent 能力
- 所有 agent 通过 `.chatModel(scope -> llmModelRegistry.getChatModel())` **动态取模型**（agentic 在每次调用前求值该函数）——天然兼容管理端 LLM 配置热刷新，无需 Factory evict 模式
- 两个图各自独立开关 + Wiki 图叠加企业版门控（`EditionGuard`，与制造场景模板同款）；**关闭时行为与现状逐字节一致**

---

## 4. Wiki 维护 Agent 详细设计

### 4.1 数据流

```
IndexTaskProcessor:180（索引成功）
  └─ WikiCompileService.scheduleCompile(docId)          ← 现有入口不变
       └─ [agent.enabled && enterprise] ?
            ├─ 是 → WikiAgentService.run(docId)          ← 新增分支
            │    1. extract（Java）: TextExtractionService.extractFullText + 元数据 hint
            │    2. 图:
            │       conditional { 旧页存在 → mergeAgent（增量合并）
            │                    无旧页   → compileAgent（全量，沿用 WIKI_SYSTEM 语义） }
            │       → loop { lintAgent → reviseAgent }
            │            .maxIterations(2)
            │            .exitCondition(lintIssues 为空)
            │            .testExitAtLoopEnd(true)
            │    3. persist（Java，短事务）:
            │       upsert WikiPage(version+1) + kb_wiki_change_log
            │       + kb_wiki_link 交叉引用 + 审计 + 指标
            └─ 否 → 现有单发编译路径（逐字不变）
```

### 4.2 Agent 接口定义（`langchain4j/agent/WikiAgents.java`，新增）

```java
public interface WikiAgents {

    interface WikiMergeAgent {
        @UserMessage("""
            你是 Wiki 维护助手。知识库中已存在该文档对应的 Wiki 页，文档内容发生了更新。
            要求：
            1. 以现有页为基础做增量合并：保留其中仍然有效的内容、人工维护的结构与措辞
            2. 仅按新文档更新冲突或过时的部分（条款号、日期、流程变化）
            3. 不编造原文没有的内容；文末保留来源文档 ID 行
            输出合并后的完整 Markdown。
            """)
        @Agent(description = "增量合并 Wiki 页", outputKey = "draftMd")
        String merge(@V("existingMd") String existingMd, @V("docText") String docText);
    }

    interface WikiLintAgent {
        @UserMessage("""
            检查以下 Wiki 草稿质量，只输出 JSON 字符串数组（无问题输出 []）：
            - 与元数据矛盾：文号/生效日期/部门不一致
            - 缺失来源行、标题层级断裂、空章节
            - 草稿内部前后矛盾
            元数据：{{meta}}
            """)
        @Agent(description = "Lint Wiki 草稿", outputKey = "lintIssues")
        String lint(@V("draftMd") String draftMd, @V("meta") String meta);
    }

    interface WikiReviseAgent {
        @UserMessage("按检查意见修订 Wiki 草稿，只输出修订后全文。意见：{{issues}}")
        @Agent(description = "按 Lint 意见修订", outputKey = "draftMd")
        String revise(@V("draftMd") String draftMd, @V("issues") String issues);
    }
}
```

组装示例（`WikiAgentService` 内，仅示意）：

```java
Agent mergeOrCompile = AgenticServices.conditionalBuilder()
        .subAgents(scope -> scope.readState("existingMd", "") != null, mergeAgent)
        .subAgents(scope -> scope.readState("existingMd", "") == null, compileAgent)
        .build();
Agent lintLoop = AgenticServices.loopBuilder()
        .subAgents(lintAgent, reviseAgent)
        .maxIterations(properties.getWiki().getAgent().getMaxLintIterations())
        .exitCondition(scope -> parseIssues(scope.readState("lintIssues", "[]")).isEmpty())
        .testExitAtLoopEnd(true)
        .build();
// agentBuilder(...) 均指定 .chatModel(scope -> llmModelRegistry.getChatModel())
```

### 4.3 Prompt 设计要点

- 合并 Prompt 强调「保留人工维护内容」，并要求输出完整 Markdown（便于整页覆盖写库与 diff）
- Lint 输出强制 JSON 数组（沿用 `AgenticRetrievalService` 的正则提取 + Jackson 解析容错模式）
- 输入沿用现有 12000 字符截断（`StringUtils.truncate`）控制 token 成本
- **规则 Lint 先行**（确定性 Java 检查：来源行存在、标题层级、文号与 `kb_document` 元数据一致），LLM Lint 仅做语义矛盾检查且可经 `wiki.agent.llm-lint-enabled` 关闭——保护本地小参数模型（qwen2.5:7b）场景

### 4.4 持久化（短事务，LLM 调用在事务外——沿用现有模式）

| 动作 | 说明 |
|------|------|
| upsert `kb_wiki_page` | version+1；status 规则见 §4.5；source_doc_ids 保持累计 |
| insert `kb_wiki_change_log` | **新表**，每次变更一行 |
| 维护 `kb_wiki_link` | 启用预留表：persist 时按「docType/department 相同 + 标题相关性」对已发布页做**保守规则匹配**写入 from→to（可配置关闭，防误报） |
| 审计 | `AuditLogService.log("WIKI_AGENT_MERGE", ...)` 含变更摘要 |
| 指标 | `MetricsService` 新增 `countWikiAgent(type)`（merge/compile/lint-revise 计数） |

`kb_wiki_change_log` DDL（对齐现有建表风格，落 `schema-postgres.sql`）：

```sql
CREATE TABLE IF NOT EXISTS kb_wiki_change_log (
    id            BIGSERIAL PRIMARY KEY,
    kb_id         BIGINT       NOT NULL,
    page_id       BIGINT       NOT NULL,
    from_version  INT,
    to_version    INT          NOT NULL,
    change_type   VARCHAR(32)  NOT NULL,           -- MERGE / COMPILE
    summary       VARCHAR(1000),                   -- 变更摘要（LLM 生成或规则拼接）
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_wiki_change_page ON kb_wiki_change_log (page_id);
```

### 4.5 状态机与审核流衔接

| 条件 | 结果 status |
|------|------------|
| Lint 通过 && `wiki.auto-publish=true` | `PUBLISHED` |
| Lint 通过 && 非 autoPublish | `DRAFT`（人工审核，现状语义） |
| Lint 2 轮后仍未通过 && 非 autoPublish | `DRAFT`，issues 写入 change_log.summary 供审核者参考 |
| Lint 2 轮后仍未通过 && autoPublish | `PUBLISHED`，issues 记录在 change_log（不阻塞） |

### 4.6 降级与回退

| 场景 | 行为 |
|------|------|
| `wiki.agent.enabled=false`（默认）或社区版 | 走现有单发编译路径，逐字不变 |
| 图执行任意异常 | `log.warn` 后回退现有单发编译（`scheduleCompile` 本就是 best-effort catch 语义） |
| 合并结果结构校验失败（空/超长/无来源行） | 回退全量编译 |
| LLM Lint 关闭 | 仅规则 Lint，issues 仍参与修订循环 |

---

## 5. Writer 多步编排设计（`service/WriterGraphService.java`，新增）

```
sequence( planOutline → draftSection（按大纲逐节循环） → cite → polish )
```

- **流式策略**：节点内部沿用现有 `StreamingChatModel` + `StreamingChatResponseHandler` 手工推 SSE（agentic 的 TokenStream 传播仅对最后一个 agent 生效，手工流式更可控、改动最小）；agentic 只负责编排与状态传递
- **SSE 事件契约**：
  - 保留 `done: [DONE]` 与 `error` 事件不变
  - 新增命名事件 `step`，载荷示例：`{"stage":"draftSection","sectionIndex":2,"sectionTotal":5,"title":"第二章 安装前准备"}`
  - 旧前端忽略未知事件即天然向后兼容
- `WriterService.generate()` 按 `knowledge.writer.graph-enabled` 分流；`WriterRequest` 字段不变
- **AI 来源标记（可选小补充）**：`SaveDocumentRequest` 增加可选 `docType`，`/writer/save` 落库时携带，便于在文档列表区分 AI 生成文档（沉淀链路本身已存在，见 §1.1）

---

## 6. 配置项清单（`application-ai.yml`，默认全关）

| 配置项 | 默认 | 说明 |
|--------|------|------|
| `knowledge.wiki.agent.enabled` | `false` | Wiki 维护 Agent 总开关（叠加 `EditionGuard.isEnterprise()`） |
| `knowledge.wiki.agent.max-lint-iterations` | `2` | lint→revise 循环封顶 |
| `knowledge.wiki.agent.llm-lint-enabled` | `true` | LLM 语义 Lint（关闭则仅规则 Lint） |
| `knowledge.wiki.agent.link-enabled` | `true` | 交叉引用写入 `kb_wiki_link` |
| `knowledge.writer.graph-enabled` | `false` | 写文档多步编排开关 |

---

## 7. 兼容性影响

| 维度 | 影响 |
|------|------|
| REST/SSE API | **零契约变更**：`/writer/generate` 请求响应不变（新增 `step` 命名事件向后兼容）；`/writer/save` 仅可选新增 `docType` 字段；Wiki API 不变 |
| 数据库 | 新增 `kb_wiki_change_log`；启用已有 `kb_wiki_link`（结构不变）；`SQL_INIT_MODE` 既有机制覆盖 |
| 依赖 | +1：`dev.langchain4j:langchain4j-agentic:1.17.1-beta27`；LangChain4j 停留 1.17.1 不升级 |
| 社区版 | 两图均不走（flag + EditionGuard），行为与 v1.1.0 基线一致 |
| 索引链路 | `IndexTaskProcessor` 不改；分支在 `WikiCompileService` 内部 |

---

## 8. 测试与验收

**单测**（`chatModel(scope -> mock)` 注入 mock ChatModel，参考现有 service 测试风格）：

| 用例 | 断言 |
|------|------|
| 增量合并 | 旧页人工内容关键词保留；新文档变更点进入结果 |
| 规则 Lint | 文号与元数据不一致被捕获 |
| 循环封顶 | issues 恒不空时迭代 ≤ maxLintIterations |
| persist | version+1、change_log 落行、link 写入、审计调用 |
| 降级 | LLM 抛异常 → 回退现有路径；flag 关闭 → 不进入图 |
| Writer 图 | step 事件顺序与分节计数；`[DONE]` 仅发送一次 |

**回归**：`mvn -pl apps/server -am -B test` 全绿；`cd web && npm run test:run` 全绿；`test-assets/run-e2e-api.ps1` 补「flag 关闭回归 + /writer/save 抽检」用例并更新 `docs/testing/automation-report.md`。

**验收 checklist**

- [ ] 重索引后已有 Wiki 页被增量合并，人工修订保留，`kb_wiki_change_log` 可查
- [ ] Lint 发现矛盾自动修订（≤2 轮）；未通过且非 autoPublish → DRAFT 待审
- [ ] 开关关闭 / 社区版行为与现状完全一致
- [ ] 写文档输出 step 进度，`[DONE]` 契约不变，旧前端无感
- [ ] 全部现有测试 + 新增单测通过

---

## 9. 风险与缓解

| 风险 | 缓解 |
|------|------|
| agentic 模块 experimental，API 可能演进 | API 隔离于 `WikiAgents` / `WikiAgentService` / `WriterGraphService` 三个类；flag 默认关；对外签名不变时可同接口替换 langgraph4j-core |
| 本地小模型（qwen2.5:7b）合并/Lint 质量 | 规则 Lint 先行；Prompt 精简 + 12000 字符截断；合并结果结构校验失败→回退全量编译；`llm-lint-enabled` 可关 |
| 交叉引用误报 | 保守规则匹配（docType/department 相同 + 标题相关）+ `link-enabled` 开关 |
| 每次重索引增加 2~3 次 LLM 调用 | 循环封顶 2 轮；截断控制输入；社区版/关闭态零增量 |
| 事务与阻塞 | LLM 在事务外、persist 短事务（沿用 `WikiCompileService` 现有模式） |

---

## 10. 实施拆分

| PR | 内容 | 预估改动 |
|----|------|---------|
| PR1 | Phase 1（依赖+配置）+ Phase 2（Wiki 维护 Agent + change_log/link + 降级） | 后端 ~10 文件（新增 4：`WikiAgents`、`WikiAgentService`、`WikiChangeLog` 实体+Mapper；修改 4：`WikiCompileService`、`KnowledgeProperties`、`application-ai.yml`、`schema-postgres.sql`；测试 1-2） |
| PR2 | Phase 3+4（Writer 图 + step 事件 + 前端进度 + AI 来源标记） | 后端 ~3 文件、前端 ~2 文件、测试 1-2 |

文档随 PR 更新：本设计文档状态改为「已实施」、`docs/releases/v2.0.0.md` M2 状态、`README.md` 功能表。

---

## 11. 参考资料

- [langchain4j-agentic 官方教程（Agentic Workflows）](https://docs.langchain4j.dev/tutorials/agents/)
- [LangChain4j Agentic Workflows 实战（JavaPro, 2026-07）](https://javapro.io/2026/07/08/langchain4j-agentic-workflows-from-ai-calls-to-multi-agent-systems-in-java/)
- [langgraph4j（LangGraph Java 移植）](https://github.com/langgraph4j/langgraph4j)
- [Spring AI Alibaba（Graph 运行时）](https://github.com/alibaba/spring-ai-alibaba)
- [langchain4j-aideepin（Java 同类开源产品）](https://github.com/moyangzhan/langchain4j-aideepin)
- [Java AI agent frameworks in 2026 横评](https://codewiz.info/blog/java-ai-agent-frameworks-2026/)
- Maven Central：`dev.langchain4j/langchain4j-agentic`（1.17.1-beta27 与本项目 BOM 对齐）
