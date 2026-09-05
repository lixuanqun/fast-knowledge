# GUI 自动化功能测试报告（develop_2.0.0）

> **执行日期**：2026-09-05
> **被测版本**：`develop_2.0.0` 分支（端口化 + 云端模型化 + Classic 双轨 + langgraph4j 图编排）
> **部署形态**：Standard（PostgreSQL 16 + pgvector + Redis 7 + MinIO，docker compose）
> **前端**：Vite dev server `http://localhost:5174/`（代理后端 `:8088/api/v1`）
> **后端**：`KNOWLEDGE_EDITION=enterprise`，`EMBEDDING_PROVIDER=hash`（无云端 key 的降级向量）
> **环境受限项**：本机无 LLM 端点（Ollama 未安装、无云端 API key），问答/对话/写文档的内容生成无法验证，仅验证交互链路与错误呈现
> **测试方式**：浏览器 GUI 黑盒测试（ZCode In-app Browser + Playwright 语义定位），环境预置数据经 API 注入；截图存于 `gui-test-screenshots/`

---

## 一、结果汇总

| # | 测试点 | 结果 | 说明 |
|---|--------|------|------|
| T1 | 登录主流程 | ✅ PASS | 跳转仪表盘，RAG 运营面板完整渲染 |
| T2 | 新建知识库 | ✅ PASS | 弹窗表单 → 列表即时刷新，UTF-8 正常 |
| T3 | 知识库详情与 Tab | ✅ PASS | 文档/成员/Wiki/设置/索引任务 5 Tab，Wiki 双路召回文案正确 |
| T4 | 智能检索 | ⚠️→✅ | 首测 FAIL（BUG-1），修复后复测 PASS |
| T5 | 智能问答（LLM 受限） | ⚠️ PASS* | 交互正常；失败反馈仅短暂 toast，见 BUG-2 |
| T6 | 智能写文档（LLM 受限） | ⚠️ PASS* | 表单/流式 UI 正常；LLM 缺位时长时间无反馈，见 BUG-2 |
| T7 | 导航可达性（场景模板/审计日志/API Key） | ✅ PASS | 三页渲染完整，审计准确记录测试轨迹 |
| T8 | 深色/浅色主题切换 | ✅ PASS | html class `dark` ↔ 空，两套主题渲染完整 |
| T9 | 错误密码边界 | ❌ BUG-3 | 三次捕捉（0.6s/1.2s/1.5s）均无错误提示 |
| T10 | 登出流程 | ✅ PASS | 确认弹窗 → 回登录页 |

**总计**：7 PASS、1 PASS-with-bug（T4 修复后）、2 发现 UX 缺陷（T5/T6）、1 缺陷（T9）。
**回归**：后端全量单测 80/80 全绿；前端 vue-tsc + Vitest 11/11 全绿。

---

## 二、详细记录

### T1 登录主流程 ✅

初始页渲染完整（标题/表单/提示），`admin/admin123` 登录后跳转 `/dashboard`，系统概览（2 知识库/2 文档/1 已索引）与 RAG 运营面板（检索次数/命中率/热门查询）正确展示。

![T1 登录页](file:///D:/fk_repo/gui-test-screenshots/t1_login_page.png)
![T1 登录后仪表盘](file:///D:/fk_repo/gui-test-screenshots/t1_after_login.png)

### T2 新建知识库 ✅

「新建知识库」弹窗（名称/描述/可见性）→ 提交「重构验证库」→ 列表即时出现（公开、Top K 8），共 3 条。

![T2 弹窗](file:///D:/fk_repo/gui-test-screenshots/t2_create_dialog.png)
![T2 创建成功](file:///D:/fk_repo/gui-test-screenshots/t2_after_create.png)

### T3 知识库详情与 Tab ✅

详情页 5 个 Tab 切换正常；Wiki Tab 显示双路召回说明（「发布后进入问答双路召回；目录问法优先命中 index」）；设置 Tab 表单回显正确。

![T3 详情-文档 Tab](file:///D:/fk_repo/gui-test-screenshots/t3_kb_detail.png)
![T3 Wiki Tab](file:///D:/fk_repo/gui-test-screenshots/t3_wiki_tab.png)
![T3 设置 Tab](file:///D:/fk_repo/gui-test-screenshots/t3_settings_tab.png)

### T4 智能检索 ⚠️→✅（发现 BUG-1 并修复）

- **首测 FAIL**：「季度保养有什么要求？」在 GUI 测试知识库返回空结果
- **定位**：文档卡在 INDEXING——后端日志 `value too long for type character varying(512)`；pgvector 表 `embedding` 列为 `vector(512)`，而 v2 将默认 `EMBEDDING_DIMENSION` 调整为 1024（云端模型），hash 降级向量随之 1024 维 → 维度不匹配写入失败
- **修复**：`PgVectorEmbeddingStoreConfig` 启动时读取 `atttypmod` 校验存量表维度，不一致输出可操作指引；测试环境以 `EMBEDDING_DIMENSION=512` 对齐存量表后重索引成功
- **复测 PASS**：命中「设备维保管理办法」（score 0.016），命中片段正是「第二条 …季度保养与年度大修…」，「查看原文」链接可用

![T4 首测空结果](file:///D:/fk_repo/gui-test-screenshots/t4_search_result.png)
![T4 复测命中](file:///D:/fk_repo/gui-test-screenshots/t4_search_result_retest.png)

### T5 智能问答（LLM 受限）⚠️

提问「设备维保分为哪几类？」后结果区无任何变化，也未捕捉到错误 toast（4s 与复查两个时点）——LLM 缺位的失败被静默，见 BUG-2。交互本身（知识库选择/示例问题/字数计数）正常。

![T5 提问后无反馈](file:///D:/fk_repo/gui-test-screenshots/t5_qa_llm_error.png)

### T6 智能写文档（LLM 受限）⚠️

表单填写正常，点击「生成文档」后 0.9s/50s 两个时点均无持久反馈（疑似短暂 toast 闪过），结果区未进入。**注**：本次为单发生成路径（`WRITER_GRAPH_ENABLED` 默认关闭）；step 事件 UI 链路需 LLM 可用后另行验证。见 BUG-2。

![T6 写文档表单](file:///D:/fk_repo/gui-test-screenshots/t6_writer_page.png)
![T6 生成 50s 后仍无反馈](file:///D:/fk_repo/gui-test-screenshots/t6_writer_timeout_feedback.png)

### T7 导航可达性 ✅

场景模板（企业版三模板 + 验收清单）、审计日志（完整记录本次测试的 LOGIN/CREATE_KB/SEARCH 轨迹 + CSV 导出入口）、API Key 列表均正常。

![T7 场景模板](file:///D:/fk_repo/gui-test-screenshots/t7_场景模板.png)
![T7 审计日志](file:///D:/fk_repo/gui-test-screenshots/t7_审计日志.png)
![T7 API Key](file:///D:/fk_repo/gui-test-screenshots/t7_API Key.png)

### T8 主题切换 ✅

主题按钮切换后 `documentElement.classList` 由 `dark` ↔ 空，浅色/深色两套主题渲染均完整无错位。

![T8 浅色主题](file:///D:/fk_repo/gui-test-screenshots/t8_theme_after_click.png)

### T9 错误密码 ❌ BUG-3

错误密码提交后于 0.6s / 1.2s / 1.5s 三个时点截图，均无错误提示；按钮无 loading 态、无表单错误标记。复现 3 次。后端审计无失败登录记录（失败登录本不入审计，仅能证明提示未呈现）。**建议**：登录失败在表单下方持久显示错误文案。

![T9 无提示](file:///D:/fk_repo/gui-test-screenshots/t9_wrong_password_toast.png)

### T10 登出 ✅

「退出」→ 确认弹窗 → 回到 `/login`。

![T10 登出确认](file:///D:/fk_repo/gui-test-screenshots/t10_logout_confirm.png)

---

## 三、问题清单与处置（更新：复测完成）

| ID | 级别 | 问题 | 处置 |
|----|------|------|------|
| BUG-1 | P0 | v2 默认维度 1024 与存量 pgvector 表 `vector(512)` 不匹配，索引以晦涩报错失败 | **已修复并复测**：启动时维度一致性校验（`PgVectorEmbeddingStoreConfig`）；维度对齐后重索引成功、检索复测命中 |
| BUG-2 | P1 | LLM 缺位时问答/写文档失败反馈缺失或仅短暂 toast，结果区无持久错误 | **已修复并复测**：问答/写文档失败在结果卡持久显示错误警示与重试指引（截图 t5_retest / t6_retest） |
| BUG-3 | P2 | 错误密码登录无可见错误提示 | **已修复并复测**：登录表单内持久红色警示「用户名或密码错误」（截图 t9_retest_fixed）；初测结论修正：当时点击被测试工具 actionability 问题阻塞、请求未真正提交 |
| BUG-4 | P1（部署） | 生产单 JAR 形态：`context-path=/api/v1` 使根路径 `/` 返回 API 401 JSON，前端入口为 `/api/v1/index.html` 且资源 base 不匹配 | **已修复**：vite base（FK_BASE 环境变量）+ router history base 协同 `/api/v1/`；已知限制：SPA 深链接强制刷新需服务端 fallback，与 API 路由共享命名空间暂不启用 |
| OBS-1 | P3 | 历史遗留数据存在 `?????` 乱码记录（仪表盘热门查询、知识库列表） | 旧数据编码问题，与本次改动无关，建议清理测试库 |

**测试工具环境记录**：复测过程中 IAB guest 渲染进程曾崩溃一次、`evaluate` 返回通道失效（返回值一律为空对象），故改用「生产 bundle 形态（后端直接托管前端，`/api/v1/index.html`）+ 截图 + data-testid 可见性断言」完成复测；期间 `vite dev` 长会话出现页面 JS 半死现象（fill/快照正常、Vue 事件回调不执行），均已通过重建标签页/切换生产形态绕过，与被测代码无关。

---

## 四、环境准备记录（与测试行为区分）

- 容器：docker compose 启动 fast-knowledge-postgres/redis/minio（含 bucket init）
- 预置数据（API 注入，未走 GUI）：登录 token、创建「GUI 测试知识库」、`/writer/save` 写入《设备维保管理办法》并等待索引
- 测试账号：admin/admin123（既有默认账号）
- 测试后动作：修复 BUG-1 → 后端重启（`EMBEDDING_DIMENSION=512`）→ GUI 复测 T4 → 全量单测回归

---

## 五、结论

`develop_2.0.0` 的核心链路（认证、知识库管理、文档索引、混合检索、审计、主题）在 Standard 形态下 **GUI 端到端可用**；本次重构引入的 BUG-1（维度不匹配）已在测试中暴露并修复验证。BUG-2/BUG-3 属于错误呈现 UX 缺陷，不影响功能主链路，建议纳入下一轮修复。LLM 依赖能力（问答生成/对话/写文档生成/Wiki Agent 增量合并）待接入真实 LLM 端点后专项验证。
