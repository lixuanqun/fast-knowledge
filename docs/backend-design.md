# Fast Knowledge — 后端设计与开发指南

> **版本**：v1.0  
> **更新日期**：2026-07-02  
> **接口契约**：[api.md](./api.md)（前后端唯一 API 标准，发生冲突时以该文档为准）  
> **产品需求**：[产品说明.md](./产品说明.md)

---

## 1. 开发原则

后端开发严格遵循以下顺序，**不得跳过设计直接写代码**：

```
① 理解需求（接口契约 + 产品说明）
        ↓
② 设计数据结构（Entity / DTO / VO）
        ↓
③ 设计表结构（Flyway 迁移脚本）
        ↓
④ 分层实现（Controller → Service → Mapper）
        ↓
⑤ 联调验证（对照 api.md 逐接口验收）
```

### 1.1 核心约束

| 约束 | 说明 |
|------|------|
| 接口契约优先 | 所有 Controller 路径、请求/响应字段以 [api.md](./api.md) 为准 |
| 统一响应 | 非 SSE 接口返回 `ApiResponse<T>`，成功 `code=0` |
| 禁止 Mock | 后端提供真实持久化与业务逻辑，前端直接调用 |
| Schema 版本化 | 表结构变更通过 Flyway `V{n}__*.sql` 增量迁移，禁止手改生产库 |
| 权限分层 | 系统角色（ADMIN）在 Controller 注解；知识库 ACL 在 Service 层校验 |

### 1.2 代码分层

```
apps/server/src/main/java/com/fast/knowledge/
├── controller/     # 接口层：路径、参数校验、调用 Service、返回 ApiResponse
├── service/        # 业务层：权限、事务、领域逻辑、审计
├── mapper/         # 数据访问接口（MyBatis）
├── model/
│   ├── entity/     # 与数据库表一一对应
│   ├── dto/        # 请求入参（Request Body / 查询参数封装）
│   └── vo/         # 响应出参（与 api.md 中 TypeScript 类型对齐）
├── security/       # JWT 认证、UserContext
├── common/         # ApiResponse、BusinessException、全局异常处理
└── config/         # Spring 配置、KnowledgeProperties

apps/server/src/main/resources/
├── db/migration/   # Flyway 表结构脚本
└── mapper/         # MyBatis XML
```

---

## 2. 需求理解（按业务域）

以下从 [api.md](./api.md) 与 [产品说明.md](./产品说明.md) 提炼的后端职责，开发前先通读对应模块。

### 2.1 认证与安装

| 需求 | 接口 | 后端职责 |
|------|------|----------|
| 用户登录 | POST /auth/login | 校验账号密码，签发 JWT，返回 `LoginVO` |
| 用户注销 | POST /auth/logout | Token 加入黑名单（Redis/Caffeine） |
| 首次安装 | POST /auth/setup | 修改管理员密码、设置实例名、标记 `setupComplete` |
| 当前用户 | GET /users/me | 从 JWT 解析用户，返回 `UserVO`（不含密码） |
| 自助改密 | POST /users/change-password | 校验旧密码后更新 |

**业务规则**：
- 默认账号 `admin / admin123`，`must_change_password=1`
- 首次登录后 `mustChangePassword=true`，前端引导安装向导
- 密码 BCrypt 加密存储，响应中永不返回 `password` 字段

### 2.2 用户管理（ADMIN）

| 需求 | 接口 | 后端职责 |
|------|------|----------|
| 用户列表 | GET /users | 查询全部用户 |
| 创建用户 | POST /users | 校验用户名唯一性、角色合法性 |
| 更新用户 | PUT /users/{id} | 更新 displayName / role / status |
| 删除用户 | DELETE /users/{id} | 软删或硬删（当前为硬删） |
| 重置密码 | POST /users/{id}/reset-password | 管理员强制重置 |

### 2.3 系统配置

| 需求 | 接口 | 后端职责 |
|------|------|----------|
| 公开配置 | GET /system/config | 实例名、安装状态、向量/Embedding/LLM 提供方（只读） |
| LLM 预设 | GET /system/llm-providers | 返回支持的 LLM 提供商列表 |

**说明**：运行时 AI 配置通过 `application.yml` / 环境变量注入，**无写入 API**。

### 2.4 工作区

| 需求 | 接口 | 后端职责 |
|------|------|----------|
| 工作区列表 | GET /workspaces | 返回当前用户的工作区 |
| 工作区详情 | GET /workspaces/{id} | 按 ID 查询 |

**业务规则**：
- 单实例产品，工作区由系统自动创建，**无 CRUD API**
- 创建知识库时自动关联默认工作区

### 2.5 知识库

| 需求 | 接口 | 后端职责 |
|------|------|----------|
| 列表/详情 | GET /kbs, GET /kbs/{id} | owner + member + PUBLIC 可见性过滤 |
| CRUD | POST/PUT/DELETE /kbs | 创建时写入 workspaceId、ownerId；删除级联文档/分块/向量/成员 |
| 成员管理 | /kbs/{kbId}/members | READ/WRITE/ADMIN 三级 ACL |
| 检索参数 | searchAlpha, searchTopK | 知识库级覆盖全局默认 |

**权限矩阵**：

| 操作 | owner | ADMIN 成员 | WRITE 成员 | READ 成员 | PUBLIC 非成员 | 系统 ADMIN |
|------|-------|------------|------------|-----------|---------------|------------|
| 读取 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 写入 | ✓ | ✓ | ✓ | ✗ | ✗ | ✓ |
| 成员管理 | ✓ | ✓* | ✗ | ✗ | ✗ | ✓ |

> *成员管理当前由 `checkWritePermission` 控制，owner 和 WRITE/ADMIN 成员可操作。

### 2.6 文档与索引

| 需求 | 接口 | 后端职责 |
|------|------|----------|
| 文档列表/详情 | GET documents | 按 kbId 查询 |
| 上传 | POST upload | multipart，存本地/OSS，创建 `PENDING` 索引任务 |
| 预览/分块 | GET preview, GET chunks | 解析文件内容 / 返回 chunk 列表 |
| 删除/重建 | DELETE, POST reindex | 删文件 + DB + 向量；重建触发 IndexTask |
| 索引任务 | /index-tasks/* | 异步消费：PENDING → INDEXING → INDEXED/FAILED |
| 整库重建 | POST /index-tasks/rebuild/{kbId} | 异步批量重建 |

**索引状态机**：

```
PENDING → INDEXING → INDEXED
                  ↘ FAILED（可 retry）
```

### 2.7 检索与 AI

| 需求 | 接口 | 后端职责 |
|------|------|----------|
| 混合检索 | POST /search | Lucene HNSW + BM25，alpha 加权 |
| RAG 问答 | POST /qa | 检索 → LLM 生成，返回 answer + sources |
| 流式对话 | POST /chat/messages/stream | SSE 多轮，自动创建会话，持久化消息 |
| 智能写作 | POST /writer/generate | SSE 生成 Markdown |
| 保存文档 | POST /writer/save | 文本写入知识库并触发索引 |

### 2.8 运营概览

| 需求 | 接口 | 后端职责 |
|------|------|----------|
| 统计数据 | GET /dashboard/stats | 聚合 kb/文档/索引/任务数 |
| 审计日志 | GET /dashboard/audits | 按时间倒序返回 |

---

## 3. 数据结构设计

### 3.1 模型分层约定

| 层次 | 包路径 | 用途 | 命名 |
|------|--------|------|------|
| Entity | `model.entity` | 数据库映射，Mapper 读写 | 与表名对应，如 `KbDocument` |
| DTO | `model.dto` | 请求入参 | `XxxRequest`，带 Jakarta Validation |
| VO | `model.vo` | 响应出参 | `XxxVO`，字段与 api.md TypeScript 类型一致 |

**转换规则**：
- Entity → VO：Service 层手动赋值或 MapStruct（当前为手动）
- DTO → Entity：Service 层构建，设置默认值与审计字段
- **禁止** Entity 直接作为 Controller 入参（除简单 CRUD 可返回 Entity 当 VO 字段一致时）

### 3.2 领域实体一览

| Entity | 对应表 | 主要字段 | 关联 API 响应类型 |
|--------|--------|----------|-------------------|
| `KbUser` | kb_user | id, username, password*, role, status, mustChangePassword | `UserVO`, `LoginVO` |
| `Workspace` | kb_workspace | id, name, ownerId, settings | `Workspace` |
| `KnowledgeBase` | kb_knowledge_base | id, workspaceId, ownerId, visibility, searchAlpha, searchTopK | `KnowledgeBase` |
| `KbMember` | kb_kb_member | id, kbId, userId, permission | `KbMember`（含 username 联表） |
| `KbDocument` | kb_document | id, kbId, title, fileName, indexStatus, chunkCount | `KbDocument` |
| `DocumentChunk` | kb_document_chunk | id, documentId, chunkIndex, content, tokenCount | `DocumentChunk` |
| `IndexTask` | kb_index_task | id, documentId, status, retryCount, errorMsg | `IndexTask` |
| `ChatSession` | kb_chat_session | id, userId, kbId, title | `ChatSession` |
| `ChatMessage` | kb_chat_message | id, sessionId, role, content, sources(JSON) | `ChatMessage` |
| `AuditLog` | kb_audit_log | id, userId, action, targetType, targetId, detail | `AuditLog` |

> `password` 仅 Entity 内部使用，禁止出现在任何 VO 响应中。

### 3.3 请求 DTO 一览

| DTO | 用于接口 | 必填字段 |
|-----|----------|----------|
| `LoginRequest` | POST /auth/login | username, password |
| `SetupRequest` | POST /auth/setup | instanceName, newPassword |
| `ChangePasswordRequest` | POST /users/change-password | oldPassword, newPassword |
| `CreateUserRequest` | POST /users | username, password, role |
| `UpdateUserRequest` | PUT /users/{id} | —（全部可选） |
| `AdminResetPasswordRequest` | POST /users/{id}/reset-password | newPassword |
| `KnowledgeBaseRequest` | POST/PUT /kbs | name |
| `KbMemberRequest` | POST /kbs/{kbId}/members | username |
| `SearchRequest` | POST /search | kbId, query |
| `QaRequest` | POST /qa | kbId, question |
| `ChatMessageRequest` | POST /chat/messages/stream | message |
| `WriterRequest` | POST /writer/generate | topic |
| `SaveDocumentRequest` | POST /writer/save | kbId, content |

### 3.4 响应 VO 一览

| VO | 用于接口 | 与 api.md 章节 |
|----|----------|----------------|
| `LoginVO` | POST /auth/login | §2.1 LoginResult |
| `UserVO` | GET /users/me, /users | §2.2 UserVO |
| `WorkspaceVO` | GET /workspaces | §2.5 Workspace |
| `KnowledgeBase`（Entity 直出） | /kbs | §2.6 KnowledgeBase |
| `KbMember`（Entity + 联表） | /kbs/{kbId}/members | §2.7 KbMember |
| `KbDocument`（Entity 直出） | /documents | §2.8 KbDocument |
| `DocumentPreviewVO` | GET preview | §2.9 DocumentPreview |
| `DocumentChunkVO` | GET chunks | §2.10 DocumentChunk |
| `IndexTask`（Entity 直出） | /index-tasks | §2.11 IndexTask |
| `SearchHitVO` | POST /search, sources | §2.12 SearchHit |
| `QaResponseVO` | POST /qa | §2.13 QaResult |
| `ChatSession`（Entity 直出） | /chat/sessions | §2.14 ChatSession |
| `ChatMessage`（Entity 直出） | /chat/messages | §2.15 ChatMessage |
| `DashboardVO` | GET /dashboard/stats | §2.16 DashboardStats |
| `AuditLog`（Entity 直出） | GET /dashboard/audits | §2.17 AuditLog |

---

## 4. 表结构设计

Schema 由 Flyway 管理，基线脚本：`apps/server/src/main/resources/db/migration/V1__baseline.sql`。

### 4.1 ER 关系图

```
kb_user ─────────┬──────── kb_workspace (owner_id)
                 │
                 ├──────── kb_knowledge_base (owner_id)
                 │              │
                 │              ├── kb_kb_member (user_id)
                 │              ├── kb_document (kb_id)
                 │              │       └── kb_document_chunk (document_id)
                 │              └── (向量索引，非 MySQL 表)
                 │
                 ├──────── kb_chat_session (user_id, kb_id)
                 │              └── kb_chat_message (session_id)
                 │
                 └── kb_audit_log (user_id)

kb_document ──── kb_index_task (document_id)
kb_system_config (KV 存储，实例配置)
```

### 4.2 表字段与 API 字段映射

#### kb_user

| 列名 | 类型 | API 字段 | 说明 |
|------|------|----------|------|
| id | BIGINT PK | id | 自增主键 |
| username | VARCHAR(64) UK | username | 登录名 |
| password | VARCHAR(128) | — | BCrypt，不对外暴露 |
| display_name | VARCHAR(64) | displayName | 显示名 |
| role | VARCHAR(32) | role | ADMIN / USER |
| status | TINYINT | status | 1=启用, 0=禁用 |
| must_change_password | TINYINT | mustChangePassword | 首次改密标记 |
| created_at | DATETIME | createdAt | |
| updated_at | DATETIME | updatedAt | |

#### kb_workspace

| 列名 | 类型 | API 字段 | 说明 |
|------|------|----------|------|
| id | BIGINT PK | id | |
| name | VARCHAR(128) | name | 默认「默认工作区」 |
| owner_id | BIGINT | ownerId | FK → kb_user.id |
| settings | JSON | — | 预留扩展 |
| created_at / updated_at | DATETIME | createdAt / updatedAt | |

#### kb_knowledge_base

| 列名 | 类型 | API 字段 | 说明 |
|------|------|----------|------|
| id | BIGINT PK | id | |
| workspace_id | BIGINT | workspaceId | FK → kb_workspace.id |
| name | VARCHAR(128) | name | |
| description | VARCHAR(512) | description | |
| owner_id | BIGINT | ownerId | |
| visibility | VARCHAR(32) | visibility | PRIVATE / PUBLIC |
| search_alpha | DOUBLE | searchAlpha | 默认 0.6 |
| search_top_k | INT | searchTopK | 默认 8 |
| status | TINYINT | status | 1=正常 |
| created_at / updated_at | DATETIME | createdAt / updatedAt | |

#### kb_kb_member

| 列名 | 类型 | API 字段 | 说明 |
|------|------|----------|------|
| id | BIGINT PK | id | |
| kb_id | BIGINT | kbId | UK(kb_id, user_id) |
| user_id | BIGINT | userId | |
| permission | VARCHAR(32) | permission | READ / WRITE / ADMIN |
| created_at | DATETIME | createdAt | |
| — | — | username, displayName | 联表 kb_user 查询 |

#### kb_document

| 列名 | 类型 | API 字段 | 说明 |
|------|------|----------|------|
| id | BIGINT PK | id | |
| kb_id | BIGINT | kbId | |
| title | VARCHAR(256) | title | |
| file_name | VARCHAR(256) | fileName | |
| file_type | VARCHAR(32) | fileType | pdf/docx/md/txt 等 |
| file_size | BIGINT | fileSize | 字节 |
| file_path | VARCHAR(512) | filePath | 存储路径 |
| index_status | VARCHAR(32) | indexStatus | PENDING/INDEXING/INDEXED/FAILED |
| index_error | VARCHAR(512) | indexError | 失败原因 |
| chunk_count | INT | chunkCount | |
| enabled | TINYINT | enabled | 1=参与检索 |
| created_by | BIGINT | createdBy | |
| created_at / updated_at | DATETIME | createdAt / updatedAt | |

#### kb_document_chunk

| 列名 | 类型 | API 字段 | 说明 |
|------|------|----------|------|
| id | BIGINT PK | id | |
| kb_id | BIGINT | — | 冗余，便于按库清理 |
| document_id | BIGINT | — | |
| chunk_index | INT | chunkIndex | 从 0 起 |
| content | TEXT | content | 分块文本 |
| token_count | INT | tokenCount | |

#### kb_index_task

| 列名 | 类型 | API 字段 | 说明 |
|------|------|----------|------|
| id | BIGINT PK | id | |
| document_id | BIGINT | documentId | |
| status | VARCHAR(32) | status | PENDING/PROCESSING/DONE/FAILED |
| retry_count | INT | retryCount | |
| error_msg | VARCHAR(512) | errorMsg | |
| locked_by | VARCHAR(64) | lockedBy | 分布式锁持有者 |
| locked_at | DATETIME | lockedAt | |
| created_at / updated_at | DATETIME | createdAt / updatedAt | |

#### kb_chat_session

| 列名 | 类型 | API 字段 | 说明 |
|------|------|----------|------|
| id | BIGINT PK | id | |
| user_id | BIGINT | userId | |
| kb_id | BIGINT | kbId | 可空 |
| title | VARCHAR(256) | title | 默认「新对话」 |
| created_at / updated_at | DATETIME | createdAt / updatedAt | |

#### kb_chat_message

| 列名 | 类型 | API 字段 | 说明 |
|------|------|----------|------|
| id | BIGINT PK | id | |
| session_id | BIGINT | sessionId | |
| role | VARCHAR(16) | role | user/assistant/system |
| content | TEXT | content | |
| sources | JSON | sources | SearchHit[] 序列化字符串 |
| created_at | DATETIME | createdAt | |

#### kb_audit_log

| 列名 | 类型 | API 字段 | 说明 |
|------|------|----------|------|
| id | BIGINT PK | id | |
| user_id | BIGINT | userId | |
| action | VARCHAR(64) | action | CREATE_KB, UPLOAD_DOC 等 |
| target_type | VARCHAR(64) | targetType | KB, DOCUMENT 等 |
| target_id | BIGINT | targetId | |
| detail | VARCHAR(1024) | detail | |
| created_at | DATETIME | createdAt | |

#### kb_system_config

| 列名 | 类型 | API 字段 | 说明 |
|------|------|----------|------|
| config_key | VARCHAR(64) PK | — | instance_name, setup_complete |
| config_value | VARCHAR(1024) | — | |
| updated_at | DATETIME | — | |

### 4.3 表结构变更规范

新增或修改表时：

1. 在本文档 §4 补充字段说明与 API 映射
2. 在 [api.md](./api.md) 补充/更新 TypeScript 类型
3. 新增 Flyway 脚本 `V{n}__description.sql`（**禁止修改已发布的 V1**）
4. 同步更新 `model.entity` 与 Mapper XML
5. 运行集成测试验证迁移

---

## 5. 接口实现映射

### 5.1 Controller → Service 对照

| Controller | Service | 已实现 |
|------------|---------|--------|
| `AuthController` | `AuthService` | ✓ |
| `SetupController` | `UserService`, `SystemConfigService` | ✓ |
| `UserController` | `UserService` | ✓ |
| `SystemController` | `SystemConfigService`, `LlmConfigResolver` | ✓ |
| `WorkspaceController` | `WorkspaceService` | ✓ |
| `KnowledgeBaseController` | `KnowledgeBaseService` | ✓ |
| `KbMemberController` | `KbMemberService` | ✓ |
| `DocumentController` | `DocumentService` | ✓ |
| `IndexTaskController` | `IndexTaskService`, `IndexRebuildService` | ✓ |
| `SearchController` | `SearchService` | ✓ |
| `QaController` | `RagService` | ✓ |
| `ChatController` | `ChatService` | ✓ |
| `WriterController` | `WriterService`, `DocumentService` | ✓ |
| `DashboardController` | `DashboardService`, `AuditLogService` | ✓ |

### 5.2 接口实现状态（对照 api.md §4）

全部 **45 REST + 2 SSE** 接口均已实现。开发新功能或修改现有接口时，按下表逐项验收。

| 模块 | 接口编号 | 状态 |
|------|----------|------|
| 认证与安装 | #1–3 | ✓ 已实现 |
| 用户 | #4–10 | ✓ 已实现 |
| 系统配置 | #11–12 | ✓ 已实现 |
| 工作区 | #13–14 | ✓ 已实现 |
| 知识库 | #15–19 | ✓ 已实现 |
| 成员 | #20–22 | ✓ 已实现 |
| 文档 | #23–29 | ✓ 已实现 |
| 索引任务 | #30–34 | ✓ 已实现 |
| 检索 | #35 | ✓ 已实现 |
| 问答 | #36 | ✓ 已实现 |
| 对话 | #37–41 | ✓ 已实现 |
| 写作 | #42–43 | ✓ 已实现 |
| 概览 | #44–45 | ✓ 已实现 |

---

## 6. 新功能开发检查清单

扩展或新增接口时，按此清单执行：

### 阶段一：需求（①）

- [ ] 在 [api.md](./api.md) 中定义/更新接口（方法、路径、请求、响应、权限）
- [ ] 与前端确认 TypeScript 类型字段名与类型
- [ ] 明确业务规则（权限、状态机、级联删除等）

### 阶段二：数据结构（②）

- [ ] 新增/修改 `model.dto.*Request`（Jakarta Validation 注解）
- [ ] 新增/修改 `model.vo.*VO`（与 api.md 类型对齐）
- [ ] 新增/修改 `model.entity.*`（若涉及持久化）

### 阶段三：表结构（③）

- [ ] 评估是否需要新表或改表
- [ ] 编写 `V{n}__*.sql` Flyway 脚本
- [ ] 更新本文档 §4 表结构说明
- [ ] 本地执行迁移验证

### 阶段四：开发（④）

- [ ] `mapper/` 接口 + `resources/mapper/*.xml`
- [ ] `service/` 业务逻辑（含权限校验、事务、审计）
- [ ] `controller/` 接口（路径与 api.md 一致）
- [ ] 集成测试 `src/test/java/.../integration/`

### 阶段五：验收（⑤）

- [ ] 对照 api.md 用 curl / Postman / 前端联调
- [ ] Swagger `/api/swagger-ui.html` 辅助核对
- [ ] 更新 api.md 变更记录
- [ ] 运行 `mvn test` 集成测试（`src/test/java/.../integration/`）

### 5.3 集成测试覆盖

测试辅助类：`integration/support/ApiTestSupport.java`

| 测试类 | 覆盖场景 |
|--------|----------|
| `AuthIntegrationTest` | 登录、未认证 401、认证后列表 |
| `SystemApiIntegrationTest` | 公开系统配置、LLM 预设 |
| `UserAdminIntegrationTest` | ADMIN 用户列表、普通用户 403、/users/me |
| `KnowledgeBaseAclIntegrationTest` | READ/WRITE/ADMIN 权限矩阵、整库重建权限 |
| `KbMemberIntegrationTest` | 跨库删除成员校验、成员响应字段 |
| `ValidationIntegrationTest` | search/qa/users/chat 参数校验 400 |

---

## 7. 关键实现参考

### 7.1 统一响应

```java
// 成功
return ApiResponse.ok(data);

// 业务异常（Service 层抛出）
throw new BusinessException("知识库不存在");
throw new BusinessException(403, "无权限访问该知识库");
```

### 7.2 知识库权限校验

```java
// KnowledgeBaseService
checkReadPermission(kb);      // 读：owner / member / PUBLIC / 系统 ADMIN
checkWritePermission(kb);     // 写：owner / WRITE|ADMIN member / 系统 ADMIN
checkKbAdminPermission(kb);   // 管理：owner / KB ADMIN member / 系统 ADMIN（成员管理、删库）
```

所有文档、索引、检索、问答操作前须先 `getById` + `checkReadPermission` 或 `checkWritePermission`。

### 7.3 SSE 流式接口

- Controller 返回 `SseEmitter`，`produces = TEXT_EVENT_STREAM_VALUE`
- 不走 `ApiResponse` 包装
- 事件约定：`message`（文本片段）→ `event:done` → `event:error`

### 7.4 审计日志

关键写操作调用 `AuditLogService.log(action, targetType, targetId, detail)`：

| action | 触发场景 |
|--------|----------|
| CREATE_KB | 创建知识库 |
| UPDATE_KB | 更新知识库 |
| DELETE_KB | 删除知识库 |
| UPLOAD_DOC | 上传文档 |
| DELETE_DOC | 删除文档 |

---

## 8. 文档索引

| 文档 | 用途 |
|------|------|
| [api.md](./api.md) | 前后端接口契约（路径、字段、权限） |
| [backend-design.md](./backend-design.md) | 本文档：后端设计流程、数据结构、表结构 |
| [产品说明.md](./产品说明.md) | 产品功能与业务规则 |
| [fast_knowledge.md](./fast_knowledge.md) | 技术架构、部署、AI 配置 |
| [design/README.md](./design/README.md) | UI 设计稿 |

---

## 9. 变更记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-02 | 初始版本：需求梳理、数据结构、表结构、实现映射 |
| v1.1 | 2026-07-02 | 对齐 api.md：KB ADMIN 权限、路径校验、DTO 校验、审计日志 |
| v1.2 | 2026-07-02 | 补充 6 个集成测试类（ACL/校验/用户管理），修复 AccessDenied 403 响应 |
