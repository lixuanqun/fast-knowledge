# 自动化测试报告

> **版本**：v1.0.0（生产稳定基线）  
> **最近执行**：2026-07-06  
> **环境**：本地 Docker（PostgreSQL / Redis / MinIO）+ Ollama `qwen2.5:7b`  
> **账号**：`admin` / `admin123`

本报告汇总 API 端到端（E2E）与 Browser UI 冒烟测试结果，用于交付验收与回归基线对照。

---

## 汇总

| 测试层 | 通过 | 失败 | 跳过 | 合计 | 通过率 |
|--------|------|------|------|------|--------|
| **API E2E** | 34 | 0 | 4 | 38 | 100%（可执行项） |
| **Browser UI** | 12 | 0 | 3 | 15 | 100%（可执行项） |
| **合计** | **46** | **0** | **7** | **53** | **100%** |

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

## Browser UI（15 项）

原始结果：[`test-assets/ui-results.json`](../../test-assets/ui-results.json)  
重启冒烟：[`test-assets/restart-test-results.json`](../../test-assets/restart-test-results.json)

### 通过（12）

| 用例 ID | 说明 |
|---------|------|
| TC-UI01 | UI 表单登录（`data-testid`） |
| TC-UI02 | 仪表盘概览 |
| TC-UI03 | 侧边栏导航 |
| TC-UI04 | 知识库详情（文档 / 成员 / Wiki / 设置 Tab） |
| TC-UI05 | 设置与隐私页 |
| TC-UI06 | 深色模式切换 |
| TC-UI07 | 改密弹窗 |
| TC-UI08 | 登出确认 |
| TC-UI12～UI15 | 智能问答 / 对话 / 写文档 / 大模型配置入口 |

### 跳过（3）

| 用例 ID | 原因 |
|---------|------|
| TC-UI09 | `/setup` 已完成 |
| TC-UI10 | OIDC callback 未启用 |
| TC-UI11 | LDAP 未启用 |

### 失败

无。

---

## v1.0.0 期间修复的关键问题

| 问题 | 修复 |
|------|------|
| 登出 500 | `TokenBlacklistService` config_key 超长 |
| JWT 过期污染公开接口 | `JwtAuthenticationFilter` 异常降级 |
| Redis 循环依赖 | 独立 `RedisConfig` |
| SSE 流式对话「未认证」 | `UserContext.wrap()` 传播安全上下文 |
| LangChain4j 1.17 `doChat` 未实现 | `DelegatingStreamingChatModel` 委托补全 |
| LLM 不可用返回 500 | `GlobalExceptionHandler` 友好错误 |

---

## 如何复现

```powershell
# 1. 启动基础设施与 Ollama（见 docs/deployment/docker.md）
docker compose -f docker/docker-compose.yml up -d
docker exec fast-knowledge-ollama ollama pull qwen2.5:7b

# 2. 启动后端与前端
.\scripts\dev.ps1

# 3. 运行 API E2E
cd test-assets
.\run-e2e-api.ps1
# 结果写入 e2e-results.json
```

Browser UI 测试使用 Cursor Browser 或手动按上表用例验收。

---

## 后续开发约定

- **v1.0.0** 为 `main` 分支生产稳定基线
- 新功能与 Bug 修复在 `main` 之上开发（建议 feature 分支 → PR → `main`）
- 每次发版前更新本报告与 `e2e-results.json`
