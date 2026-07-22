# 自动化测试报告

> **版本**：v1.1.0（P0/P1 功能回归）  
> **最近执行**：2026-07-22  
> **环境**：本地 Docker（PostgreSQL / Redis / MinIO）+ Ollama `qwen2.5:7b`  
> **后端**：`http://localhost:8088/api/v1`（profiles=`bundle,enterprise`，`KNOWLEDGE_EDITION=enterprise`）  
> **前端**：`http://localhost:5174/`  
> **账号**：`admin` / `admin123`

本报告汇总 API 端到端（E2E）、新增 P0/P1 API 抽检与 Browser UI 冒烟结果。

---

## 汇总

| 测试层 | 通过 | 失败 | 跳过 | 合计 | 通过率 |
|--------|------|------|------|------|--------|
| **API E2E（基线）** | 34 | 0 | 4 | 38 | 100%（可执行项） |
| **API 新功能抽检** | 5 | 0 | 0 | 5 | 100% |
| **Browser UI** | 19 | 0 | 3 | 22 | 100%（可执行项） |
| **合计** | **58** | **0** | **7** | **65** | **100%** |

跳过项均为**环境未启用**（LDAP / OIDC / 已完成 setup / 成员重复添加），非功能缺陷。

---

## API E2E（38 项）

脚本：[`test-assets/run-e2e-api.ps1`](../../test-assets/run-e2e-api.ps1)  
原始结果：[`test-assets/e2e-results.json`](../../test-assets/e2e-results.json)

### 通过（34）

| 分类 | 用例 ID | 说明 |
|------|---------|------|
| 认证 | TC-A01, TC-A02, TC-A06, TC-A07 | 登录、拒绝错误密码、改密、登出 |
| 仪表盘 | TC-D01 | 统计数据 |
| 知识库 | TC-K01～K07, K09～K13 | CRUD、上传、索引、预览、分块、重索引、Wiki、检索、删除 |
| AI | TC-K12, TC-C01～C03, TC-W01, TC-L01, TC-L02 | RAG 问答、流式对话、写文档、LLM 配置与连通性 |
| 用户 | TC-U01～U05 | 用户 CRUD、权限隔离 |
| API Key | TC-P01, TC-P02 | 创建与吊销 |
| 审计 | TC-R01, TC-R02 | 查询与 CSV 导出 |
| 清理 | TC-Z01, TC-Z02 | 测试数据清理 |

### 跳过（4）

| 用例 ID | 原因 |
|---------|------|
| TC-A03 | LDAP 未启用 |
| TC-A04 | OIDC/SSO 未启用 |
| TC-A05 | 已完成首次安装（`setupComplete=true`） |
| TC-K08 | admin 已是知识库所有者，无法重复添加 |

### 失败

无。

---

## API 新功能抽检（5 项，P0/P1）

原始结果：[`test-assets/new-feature-api-results.json`](../../test-assets/new-feature-api-results.json)

| 用例 ID | 说明 | 结果 |
|---------|------|------|
| TC-N01 | `GET /dashboard/rag-ops` RAG 运营指标 | PASS（searchCount=4） |
| TC-N02 | `GET /qa/history` 问答历史 | PASS |
| TC-N03 | `GET /qa/history/export` CSV 导出 | PASS |
| TC-N04 | `GET /scenarios` 企业版场景模板 | PASS（count=3） |
| TC-N05 | 公开配置 `edition=enterprise` | PASS |

---

## Browser UI（22 项）

原始结果：[`test-assets/ui-results.json`](../../test-assets/ui-results.json)  
执行方式：Cursor Browser MCP 自动打开并操作。

### 通过（19）

| 用例 ID | 说明 |
|---------|------|
| TC-UI01 | UI 登录进入仪表盘 |
| TC-UI02 | 仪表盘 + **RAG 运营**（指标与导出 CSV） |
| TC-UI03 | 侧边栏导航（含场景模板） |
| TC-UI04 | 知识库详情（文档 / 成员 / Wiki / 设置） |
| TC-UI05 | 设置页显示 **发行版=企业版** |
| TC-UI06 | 深色模式切换 |
| TC-UI07 | 改密弹窗 |
| TC-UI08 | 登出确认并回到登录页 |
| TC-UI12～UI15 | 智能问答 / 对话 / 写文档 / 大模型配置 |
| TC-UI16 | **场景模板**（设备维保 / 制度汇编 / 工艺） |
| TC-UI17 | RAG 运营导出按钮可见 |
| TC-UI18 | **文档生命周期元数据**（参与检索 / 生效 / 失效） |
| TC-UI19 | **Wiki Tab**（双路召回说明 / 重建目录） |
| TC-UI20～UI22 | 智能检索 / API Key / 审计日志 |

### 跳过（3）

| 用例 ID | 原因 |
|---------|------|
| TC-UI09 | `/setup` 已完成 |
| TC-UI10 | OIDC 未启用 |
| TC-UI11 | LDAP 未启用 |

### 失败

无。

---

## 本次发现并已修复

| 问题 | 影响 | 修复 |
|------|------|------|
| `AdminDashboard.vue` 使用未定义 Sass 变量 `$fk-bg` | Vite 编译失败，仪表盘白屏/错误遮罩 | 改为 `$fk-surface-muted` |

---

## 如何复现

```powershell
# 1. 启动基础设施
docker compose -f docker/docker-compose.yml up -d

# 2. 后端（企业版）+ 前端
# mvn -pl apps/server spring-boot:run -Dspring-boot.run.profiles=bundle,enterprise
# cd web && npm run dev

# 3. API E2E
cd test-assets
.\run-e2e-api.ps1
```

Browser UI：Cursor Browser 打开 `http://localhost:5174/`，按本报告用例验收。

---

## 结论

**P0（生命周期召回、RAG 运营）与 P1（Wiki UI、场景模板、企业版门控）在当前本地企业版环境下 API + UI 冒烟全部通过。** 可作为 v1.1.0 回归基线；发版前请同步更新本报告与结果 JSON。
