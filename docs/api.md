# Fast Knowledge — 前后端接口契约文档

> **版本**：v1.0  
> **更新日期**：2026-07-02  
> **用途**：本文档为前后端开发的唯一接口契约。前端直接调用真实 API，禁止使用 Mock 数据；后端按本文档实现并保证兼容。

---

## 1. 通用约定

### 1.1 基础信息

| 项 | 值 |
|----|-----|
| 协议 | HTTP/HTTPS |
| 开发环境 Base URL | `http://localhost:8088/api` |
| 生产环境 Base URL | 由部署决定，前端通过 `VITE_API_BASE` 配置 |
| API 风格 | REST + SSE（流式） |
| 数据格式 | JSON（`Content-Type: application/json`） |
| 字符编码 | UTF-8 |
| 时间格式 | ISO 8601，如 `2026-07-02T10:30:00` |

### 1.2 统一响应包装

除 SSE 流式接口外，所有接口均返回以下结构：

```json
{
  "code": 0,
  "message": "success",
  "data": <业务数据，可为 object / array / null>
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | int | `0` 表示成功；非 `0` 表示业务错误 |
| message | string | 提示信息 |
| data | any | 业务载荷 |

**前端处理规则**（`web/src/utils/request.ts`）：

- `code !== 0`：弹出错误提示，拒绝 Promise
- HTTP `401`：清除登录态，跳转登录页
- 成功时返回整个响应体，业务数据取 `res.data`

### 1.3 认证

| 项 | 说明 |
|----|------|
| 方式 | JWT Bearer Token |
| 请求头 | `Authorization: Bearer <token>` |
| 获取 | `POST /auth/login` 返回 `data.token` |
| 失效 | HTTP 401 或调用 `POST /auth/logout` |

**公开接口**（无需 Token）：

- `POST /auth/login`
- `GET /system/config`
- `GET /system/llm-providers`

其余接口均需登录。

### 1.4 权限

| 级别 | 说明 |
|------|------|
| 已登录 | 普通业务接口 |
| ADMIN | 用户管理相关接口（`@PreAuthorize("hasRole('ADMIN')")`） |
| 知识库 ACL | 文档、成员、检索等按 owner + member permission 在 Service 层校验 |

知识库成员权限枚举：`READ` | `WRITE` | `ADMIN`（默认 `READ`）

用户角色枚举：`ADMIN` | `USER`

### 1.5 错误码

| 场景 | HTTP 状态码 | code | 说明 |
|------|-------------|------|------|
| 成功 | 200 | 0 | 正常 |
| 业务错误 | 200 | 非 0 | message 含错误描述 |
| 未认证 | 401 | — | Token 缺失或无效 |
| 无权限 | 403 | — | 角色或 ACL 不足 |
| 参数校验失败 | 400 | — | 字段校验不通过 |

---

## 2. 公共数据模型

### 2.1 LoginResult

```typescript
interface LoginResult {
  token: string
  userId: number
  username: string
  displayName: string
  role: "ADMIN" | "USER"
  mustChangePassword?: boolean
}
```

### 2.2 UserVO / KbUser

```typescript
interface UserVO {
  id: number
  username: string
  displayName: string
  role: "ADMIN" | "USER"
  status: number          // 1=启用, 0=禁用
  createdAt?: string
  updatedAt?: string
}
```

### 2.3 SystemConfig

```typescript
interface SystemConfig {
  instanceName: string
  setupComplete: boolean
  vectorProvider: string       // 如 "sqlite-vec" / "pgvector"
  embeddingProvider: string    // 如 "onnx" | "ollama" | "hash"
  llmProvider?: string
  llmProviderName?: string
  llmModel: string
  llmAllowExternal: boolean
}
```

### 2.4 LlmProviderPreset

```typescript
interface LlmProviderPreset {
  id: string
  name: string
  defaultBaseUrl: string
  defaultModel: string
  local: boolean
  docsHint?: string
}
```

### 2.5 Workspace

```typescript
interface Workspace {
  id: number
  name: string
  ownerId: number
  createdAt?: string
  updatedAt?: string
}
```

### 2.6 KnowledgeBase

```typescript
interface KnowledgeBase {
  id: number
  name: string
  description: string
  workspaceId?: number
  ownerId: number
  visibility: "PRIVATE" | "PUBLIC"
  searchAlpha: number       // 默认 0.6，混合检索向量权重
  searchTopK: number          // 默认 8
  status?: number
  createdAt?: string
  updatedAt?: string
}
```

### 2.7 KbMember

```typescript
interface KbMember {
  id: number
  kbId: number
  userId: number
  permission: "READ" | "WRITE" | "ADMIN"
  createdAt?: string
  username: string
  displayName: string
}
```

### 2.8 KbDocument

```typescript
interface KbDocument {
  id: number
  kbId: number
  title: string
  fileName: string
  fileType: string
  fileSize: number
  filePath?: string
  indexStatus: "PENDING" | "INDEXING" | "INDEXED" | "FAILED"
  indexError?: string
  chunkCount: number
  enabled?: number
  createdBy?: number
  createdAt?: string
  updatedAt?: string
}
```

### 2.9 DocumentPreview

```typescript
interface DocumentPreview {
  documentId: number
  title: string
  fileType: string
  previewMode: string
  content: string
  truncated: boolean
  contentLength: number
}
```

### 2.10 DocumentChunk

```typescript
interface DocumentChunk {
  id: number
  chunkIndex: number
  content: string
  tokenCount: number
}
```

### 2.11 IndexTask

```typescript
interface IndexTask {
  id: number
  documentId: number
  status: string
  retryCount: number
  errorMsg?: string
  lockedBy?: string
  lockedAt?: string
  createdAt?: string
  updatedAt?: string
}
```

### 2.12 SearchHit

```typescript
interface SearchHit {
  chunkId: number
  documentId: number
  documentTitle: string
  content: string
  score: number
}
```

### 2.13 QaResult

```typescript
interface QaResult {
  answer: string
  sources: SearchHit[]
}
```

### 2.14 ChatSession

```typescript
interface ChatSession {
  id: number
  userId: number
  kbId?: number
  title: string
  createdAt?: string
  updatedAt?: string
}
```

### 2.15 ChatMessage

```typescript
interface ChatMessage {
  id: number
  sessionId: number
  role: "user" | "assistant" | "system"
  content: string
  sources?: string          // JSON 字符串，前端需 JSON.parse
  createdAt?: string
}
```

### 2.16 DashboardStats

```typescript
interface DashboardStats {
  kbCount: number
  documentCount: number
  indexedCount: number
  failedCount: number
  pendingTasks: number
  recentAudits: AuditLog[]
}
```

### 2.17 AuditLog

```typescript
interface AuditLog {
  id: number
  userId: number
  action: string
  targetType: string
  targetId: number
  detail: string
  createdAt?: string
}
```

---

## 3. 接口清单

### 3.1 认证与安装

#### POST /auth/login

首次登录 / 常规登录。

| 项 | 值 |
|----|-----|
| 权限 | 公开 |
| Content-Type | application/json |

**请求体**

```json
{
  "username": "admin",
  "password": "admin123"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | string | 是 | 用户名 |
| password | string | 是 | 密码 |

**响应 data**：`LoginResult`

---

#### POST /auth/logout

注销当前 Token。

| 项 | 值 |
|----|-----|
| 权限 | 已登录 |

**请求体**：无

**响应 data**：`null`

---

#### POST /auth/setup

首次安装向导：设置实例名称并修改管理员密码。

| 项 | 值 |
|----|-----|
| 权限 | 已登录（首次登录后） |

**请求体**

```json
{
  "instanceName": "我的知识库",
  "newPassword": "newpass123"
}
```

| 字段 | 类型 | 必填 | 约束 |
|------|------|------|------|
| instanceName | string | 是 | 最长 128 字符 |
| newPassword | string | 是 | 6–64 字符 |

**响应 data**：`null`

---

### 3.2 用户

#### GET /users/me

获取当前登录用户信息。

| 项 | 值 |
|----|-----|
| 权限 | 已登录 |

**响应 data**：`UserVO`

---

#### POST /users/change-password

用户自助修改密码。

| 项 | 值 |
|----|-----|
| 权限 | 已登录 |

**请求体**

```json
{
  "oldPassword": "oldpass",
  "newPassword": "newpass123"
}
```

| 字段 | 类型 | 必填 | 约束 |
|------|------|------|------|
| oldPassword | string | 是 | — |
| newPassword | string | 是 | 6–64 字符 |

**响应 data**：`null`

---

#### GET /users

用户列表。

| 项 | 值 |
|----|-----|
| 权限 | ADMIN |

**响应 data**：`UserVO[]`

---

#### POST /users

创建用户。

| 项 | 值 |
|----|-----|
| 权限 | ADMIN |

**请求体**

```json
{
  "username": "zhangsan",
  "password": "pass1234",
  "displayName": "张三",
  "role": "USER"
}
```

| 字段 | 类型 | 必填 | 约束 |
|------|------|------|------|
| username | string | 是 | 3–32 字符，仅字母数字下划线 |
| password | string | 是 | 6–64 字符 |
| displayName | string | 否 | — |
| role | string | 是 | `ADMIN` 或 `USER` |

**响应 data**：`UserVO`

---

#### PUT /users/{id}

更新用户。

| 项 | 值 |
|----|-----|
| 权限 | ADMIN |
| 路径参数 | id — 用户 ID |

**请求体**

```json
{
  "displayName": "新名称",
  "role": "USER",
  "status": 1
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| displayName | string | 否 | 显示名 |
| role | string | 否 | `ADMIN` 或 `USER` |
| status | number | 否 | 1=启用, 0=禁用 |

**响应 data**：`UserVO`

---

#### DELETE /users/{id}

删除用户。

| 项 | 值 |
|----|-----|
| 权限 | ADMIN |
| 路径参数 | id — 用户 ID |

**响应 data**：`null`

---

#### POST /users/{id}/reset-password

管理员重置用户密码。

| 项 | 值 |
|----|-----|
| 权限 | ADMIN |
| 路径参数 | id — 用户 ID |

**请求体**

```json
{
  "newPassword": "newpass123"
}
```

**响应 data**：`null`

---

### 3.3 系统配置

#### GET /system/config

获取公开系统配置（安装状态、向量/Embedding/LLM 提供方等）。

| 项 | 值 |
|----|-----|
| 权限 | 公开 |

**响应 data**：`SystemConfig`

---

#### GET /system/llm-providers

获取 LLM 提供商预设列表。

| 项 | 值 |
|----|-----|
| 权限 | 公开 |

**响应 data**：`LlmProviderPreset[]`

---

### 3.4 工作区

> 工作区由系统自动管理，仅提供查询接口，无创建/更新/删除 API。

#### GET /workspaces

获取当前用户的工作区列表。

| 项 | 值 |
|----|-----|
| 权限 | 已登录 |

**响应 data**：`Workspace[]`

---

#### GET /workspaces/{id}

获取工作区详情。

| 项 | 值 |
|----|-----|
| 权限 | 已登录 |
| 路径参数 | id — 工作区 ID |

**响应 data**：`Workspace`

---

### 3.5 知识库

#### GET /kbs

获取当前用户可访问的知识库列表。

| 项 | 值 |
|----|-----|
| 权限 | 已登录 |

**响应 data**：`KnowledgeBase[]`

---

#### GET /kbs/{id}

获取知识库详情。

| 项 | 值 |
|----|-----|
| 权限 | 已登录 |
| 路径参数 | id — 知识库 ID |

**响应 data**：`KnowledgeBase`

---

#### POST /kbs

创建知识库。

| 项 | 值 |
|----|-----|
| 权限 | 已登录 |

**请求体**

```json
{
  "name": "产品文档库",
  "description": "产品相关文档",
  "visibility": "PRIVATE",
  "searchAlpha": 0.6,
  "searchTopK": 8
}
```

| 字段 | 类型 | 必填 | 默认值 |
|------|------|------|--------|
| name | string | 是 | — |
| description | string | 否 | — |
| visibility | string | 否 | `PRIVATE` |
| searchAlpha | number | 否 | 0.6 |
| searchTopK | number | 否 | 8 |

**响应 data**：`KnowledgeBase`

---

#### PUT /kbs/{id}

更新知识库。

| 项 | 值 |
|----|-----|
| 权限 | 已登录（需 WRITE 及以上） |
| 路径参数 | id — 知识库 ID |

**请求体**：同 POST /kbs

**响应 data**：`KnowledgeBase`

---

#### DELETE /kbs/{id}

删除知识库。

| 项 | 值 |
|----|-----|
| 权限 | 已登录（需 owner 或 ADMIN 权限） |
| 路径参数 | id — 知识库 ID |

**响应 data**：`null`

---

### 3.6 知识库成员

#### GET /kbs/{kbId}/members

获取知识库成员列表。

| 项 | 值 |
|----|-----|
| 权限 | 已登录 |
| 路径参数 | kbId — 知识库 ID |

**响应 data**：`KbMember[]`

---

#### POST /kbs/{kbId}/members

添加成员。

| 项 | 值 |
|----|-----|
| 权限 | 已登录（需 ADMIN 权限） |
| 路径参数 | kbId — 知识库 ID |

**请求体**

```json
{
  "username": "zhangsan",
  "permission": "READ"
}
```

| 字段 | 类型 | 必填 | 默认值 |
|------|------|------|--------|
| username | string | 是 | — |
| permission | string | 否 | `READ` |

**响应 data**：`KbMember`

---

#### DELETE /kbs/{kbId}/members/{memberId}

移除成员。

| 项 | 值 |
|----|-----|
| 权限 | 已登录（需 ADMIN 权限） |
| 路径参数 | kbId — 知识库 ID；memberId — 成员记录 ID |

**响应 data**：`null`

---

### 3.7 文档管理

#### GET /kbs/{kbId}/documents

获取知识库文档列表。

| 项 | 值 |
|----|-----|
| 权限 | 已登录（需 READ 及以上） |
| 路径参数 | kbId — 知识库 ID |

**响应 data**：`KbDocument[]`

---

#### GET /kbs/{kbId}/documents/{docId}

获取文档元信息。

| 项 | 值 |
|----|-----|
| 路径参数 | kbId, docId |

**响应 data**：`KbDocument`

---

#### GET /kbs/{kbId}/documents/{docId}/preview

获取文档预览内容。

| 项 | 值 |
|----|-----|
| 路径参数 | kbId, docId |

**响应 data**：`DocumentPreview`

---

#### GET /kbs/{kbId}/documents/{docId}/chunks

获取文档分块列表。

| 项 | 值 |
|----|-----|
| 路径参数 | kbId, docId |

**响应 data**：`DocumentChunk[]`

---

#### POST /kbs/{kbId}/documents/upload

上传文档（异步索引）。

| 项 | 值 |
|----|-----|
| 权限 | 已登录（需 WRITE 及以上） |
| Content-Type | multipart/form-data |
| 文件大小限制 | 50 MB |

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | File | 是 | 上传的文件 |

**响应 data**：`KbDocument`（`indexStatus` 初始为 `PENDING`）

---

#### DELETE /kbs/{kbId}/documents/{docId}

删除文档。

| 项 | 值 |
|----|-----|
| 权限 | 已登录（需 WRITE 及以上） |
| 路径参数 | kbId, docId |

**响应 data**：`null`

---

#### POST /kbs/{kbId}/documents/{docId}/reindex

重建单个文档索引。

| 项 | 值 |
|----|-----|
| 权限 | 已登录（需 WRITE 及以上） |
| 路径参数 | kbId, docId |

**响应 data**：`null`

---

### 3.8 索引任务

#### GET /index-tasks/pending

获取待处理索引任务。

| 项 | 值 |
|----|-----|
| 权限 | 已登录 |

**Query 参数**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| limit | int | 20 | 返回条数 |

**响应 data**：`IndexTask[]`

---

#### GET /index-tasks/recent

获取最近索引任务。

| 项 | 值 |
|----|-----|
| 权限 | 已登录 |

**Query 参数**：同 pending

**响应 data**：`IndexTask[]`

---

#### GET /index-tasks/failed

获取失败索引任务。

| 项 | 值 |
|----|-----|
| 权限 | 已登录 |

**Query 参数**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| limit | int | 20 | 返回条数 |
| kbId | long | — | 可选，按知识库过滤 |

**响应 data**：`IndexTask[]`

---

#### POST /index-tasks/{documentId}/retry

重试指定文档的索引任务。

| 项 | 值 |
|----|-----|
| 权限 | 已登录 |
| 路径参数 | documentId — 文档 ID |

**响应 data**：`null`

---

#### POST /index-tasks/rebuild/{kbId}

异步重建整个知识库索引。

| 项 | 值 |
|----|-----|
| 权限 | 已登录（需 WRITE 及以上） |
| 路径参数 | kbId — 知识库 ID |

**响应 data**

```json
{
  "accepted": true,
  "message": "索引重建已在后台执行"
}
```

---

### 3.9 混合检索

#### POST /search

向量 + 全文混合检索。

| 项 | 值 |
|----|-----|
| 权限 | 已登录（需知识库 READ 及以上） |

**请求体**

```json
{
  "kbId": 1,
  "query": "如何部署",
  "topK": 8,
  "alpha": 0.6
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| kbId | number | 是 | 知识库 ID |
| query | string | 是 | 检索关键词 |
| topK | number | 否 | 返回条数，默认取知识库 searchTopK |
| alpha | number | 否 | 向量权重，默认取知识库 searchAlpha |

**响应 data**：`SearchHit[]`

---

### 3.10 智能问答（RAG）

#### POST /qa

单轮 RAG 问答，同步返回完整答案。

| 项 | 值 |
|----|-----|
| 权限 | 已登录（需知识库 READ 及以上） |

**请求体**

```json
{
  "kbId": 1,
  "question": "产品的核心功能是什么？"
}
```

| 字段 | 类型 | 必填 |
|------|------|------|
| kbId | number | 是 |
| question | string | 是 |

**响应 data**：`QaResult`

---

### 3.11 智能对话

#### GET /chat/sessions

获取当前用户的历史会话列表。

| 项 | 值 |
|----|-----|
| 权限 | 已登录 |

**响应 data**：`ChatSession[]`

---

#### POST /chat/sessions

创建新会话。

| 项 | 值 |
|----|-----|
| 权限 | 已登录 |

**请求体**

```json
{
  "kbId": 1,
  "title": "新对话"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| kbId | number | 否 | 关联知识库 |
| title | string | 否 | 会话标题 |

**响应 data**：`ChatSession`

---

#### GET /chat/sessions/{sessionId}/messages

获取会话消息列表。

| 项 | 值 |
|----|-----|
| 权限 | 已登录 |
| 路径参数 | sessionId — 会话 ID |

**响应 data**：`ChatMessage[]`

---

#### DELETE /chat/sessions/{sessionId}

删除会话及其消息。

| 项 | 值 |
|----|-----|
| 权限 | 已登录 |
| 路径参数 | sessionId — 会话 ID |

**响应 data**：`null`

---

#### POST /chat/messages/stream

流式多轮对话（SSE）。

| 项 | 值 |
|----|-----|
| 权限 | 已登录 |
| Content-Type | application/json |
| 响应类型 | `text/event-stream` |
| 实现方式 | 原生 `fetch`（不走 axios） |

**请求体**

```json
{
  "sessionId": 1,
  "kbId": 1,
  "message": "请介绍一下这个产品"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sessionId | number | 否 | 为空时自动创建新会话 |
| kbId | number | 否 | 关联知识库（RAG 检索范围） |
| message | string | 是 | 用户消息 |

**SSE 事件格式**

| 事件 | data 内容 | 说明 |
|------|-----------|------|
| （默认 message） | 文本片段 | 逐字流式输出 |
| `event: done` | `{"sessionId":1,"sources":[...]}` | 流结束，含会话 ID 和引用来源 |
| `event: error` | 错误信息字符串 | 流中断 |

**sources 结构**：`SearchHit[]`

---

### 3.12 智能写文档

#### POST /writer/generate

AI 生成 Markdown 文档（SSE 流式）。

| 项 | 值 |
|----|-----|
| 权限 | 已登录 |
| Content-Type | application/json |
| 响应类型 | `text/event-stream` |

**请求体**

```json
{
  "kbId": 1,
  "topic": "产品发布说明",
  "outline": "1. 概述\n2. 新功能\n3. 升级指南",
  "style": "正式",
  "wordCount": 2000
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| kbId | number | 否 | 关联知识库（可参考已有文档） |
| topic | string | 是 | 写作主题 |
| outline | string | 否 | 大纲 |
| style | string | 否 | 写作风格 |
| wordCount | number | 否 | 目标字数 |

**SSE 事件格式**

| 事件 | data 内容 | 说明 |
|------|-----------|------|
| （默认 message） | 文本片段 | Markdown 内容流 |
| `event: done` | `[DONE]` | 生成完成 |
| `event: error` | 错误信息 | 生成失败 |

---

#### POST /writer/save

将生成的文档保存到知识库。

| 项 | 值 |
|----|-----|
| 权限 | 已登录（需 WRITE 及以上） |

**请求体**

```json
{
  "kbId": 1,
  "title": "产品发布说明",
  "content": "# 产品发布说明\n\n..."
}
```

| 字段 | 类型 | 必填 |
|------|------|------|
| kbId | number | 是 |
| title | string | 否 |
| content | string | 是 |

**响应 data**：`KbDocument`

---

### 3.13 运营概览

#### GET /dashboard/stats

获取运营统计数据。

| 项 | 值 |
|----|-----|
| 权限 | 已登录 |

**响应 data**：`DashboardStats`

---

#### GET /dashboard/audits

获取审计日志。

| 项 | 值 |
|----|-----|
| 权限 | 已登录 |

**Query 参数**

| 参数 | 类型 | 默认值 |
|------|------|--------|
| limit | int | 50 |

**响应 data**：`AuditLog[]`

---

## 4. 接口总览表

| # | 方法 | 路径 | 权限 | 模块 | 说明 |
|---|------|------|------|------|------|
| 1 | POST | /auth/login | 公开 | 认证 | 登录 |
| 2 | POST | /auth/logout | 登录 | 认证 | 注销 |
| 3 | POST | /auth/setup | 登录 | 安装 | 首次安装向导 |
| 4 | GET | /users/me | 登录 | 用户 | 当前用户 |
| 5 | POST | /users/change-password | 登录 | 用户 | 改密 |
| 6 | GET | /users | ADMIN | 用户 | 用户列表 |
| 7 | POST | /users | ADMIN | 用户 | 创建用户 |
| 8 | PUT | /users/{id} | ADMIN | 用户 | 更新用户 |
| 9 | DELETE | /users/{id} | ADMIN | 用户 | 删除用户 |
| 10 | POST | /users/{id}/reset-password | ADMIN | 用户 | 重置密码 |
| 11 | GET | /system/config | 公开 | 系统 | 系统配置 |
| 12 | GET | /system/llm-providers | 公开 | 系统 | LLM 预设 |
| 13 | GET | /workspaces | 登录 | 工作区 | 工作区列表 |
| 14 | GET | /workspaces/{id} | 登录 | 工作区 | 工作区详情 |
| 15 | GET | /kbs | 登录 | 知识库 | 知识库列表 |
| 16 | GET | /kbs/{id} | 登录 | 知识库 | 知识库详情 |
| 17 | POST | /kbs | 登录 | 知识库 | 创建知识库 |
| 18 | PUT | /kbs/{id} | 登录 | 知识库 | 更新知识库 |
| 19 | DELETE | /kbs/{id} | 登录 | 知识库 | 删除知识库 |
| 20 | GET | /kbs/{kbId}/members | 登录 | 成员 | 成员列表 |
| 21 | POST | /kbs/{kbId}/members | 登录 | 成员 | 添加成员 |
| 22 | DELETE | /kbs/{kbId}/members/{memberId} | 登录 | 成员 | 移除成员 |
| 23 | GET | /kbs/{kbId}/documents | 登录 | 文档 | 文档列表 |
| 24 | GET | /kbs/{kbId}/documents/{docId} | 登录 | 文档 | 文档详情 |
| 25 | GET | /kbs/{kbId}/documents/{docId}/preview | 登录 | 文档 | 文档预览 |
| 26 | GET | /kbs/{kbId}/documents/{docId}/chunks | 登录 | 文档 | 分块列表 |
| 27 | POST | /kbs/{kbId}/documents/upload | 登录 | 文档 | 上传文档 |
| 28 | DELETE | /kbs/{kbId}/documents/{docId} | 登录 | 文档 | 删除文档 |
| 29 | POST | /kbs/{kbId}/documents/{docId}/reindex | 登录 | 文档 | 重建单文档索引 |
| 30 | GET | /index-tasks/pending | 登录 | 索引 | 待处理任务 |
| 31 | GET | /index-tasks/recent | 登录 | 索引 | 最近任务 |
| 32 | GET | /index-tasks/failed | 登录 | 索引 | 失败任务 |
| 33 | POST | /index-tasks/{documentId}/retry | 登录 | 索引 | 重试索引 |
| 34 | POST | /index-tasks/rebuild/{kbId} | 登录 | 索引 | 重建整库索引 |
| 35 | POST | /search | 登录 | 检索 | 混合检索 |
| 36 | POST | /qa | 登录 | 问答 | RAG 问答 |
| 37 | GET | /chat/sessions | 登录 | 对话 | 会话列表 |
| 38 | POST | /chat/sessions | 登录 | 对话 | 创建会话 |
| 39 | GET | /chat/sessions/{sessionId}/messages | 登录 | 对话 | 会话消息 |
| 40 | DELETE | /chat/sessions/{sessionId} | 登录 | 对话 | 删除会话 |
| 41 | POST | /chat/messages/stream | 登录 | 对话 | 流式对话 (SSE) |
| 42 | POST | /writer/generate | 登录 | 写作 | 流式生成 (SSE) |
| 43 | POST | /writer/save | 登录 | 写作 | 保存文档 |
| 44 | GET | /dashboard/stats | 登录 | 概览 | 运营统计 |
| 45 | GET | /dashboard/audits | 登录 | 概览 | 审计日志 |

**共计 45 个 REST 端点 + 2 个 SSE 流式端点。**

---

## 5. 前端 API 模块映射

前端 API 封装位于 `web/src/api/`，各文件与后端模块对应关系：

| 前端文件 | 覆盖接口 |
|----------|----------|
| `auth.ts` | #1–3, #4–5 |
| `users.ts` | #6–10 |
| `config.ts` | #11–12 |
| `workspace.ts` | #13–14 |
| `kb.ts` | #15–22, #23–29, #34 |
| `documents.ts` | #25–26 |
| `index-tasks.ts` | #32–33 |
| `search.ts` | #35 |
| `qa.ts` | #36 |
| `chat.ts` | #37–40 |
| `sse.ts` | #41–42 |
| `writer.ts` | #43 |
| `dashboard.ts` | #44–45 |

---

## 6. 待对齐事项

当前无已知前后端路径不一致问题。整库索引重建已对齐为 `POST /index-tasks/rebuild/{kbId}`（`web/src/api/kb.ts` → `rebuildKbIndex()`）。

---

## 7. Swagger 在线文档

后端集成 springdoc-openapi，启动服务后可访问：

- UI：`http://localhost:8088/api/swagger-ui.html`
- JSON：`http://localhost:8088/api/v3/api-docs`

Swagger 为自动生成的辅助参考；**发生冲突时以本文档为准**。

---

## 8. 变更记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-02 | 初始版本，汇总全部 45+2 个接口 |

## 9. 相关文档

| 文档 | 用途 |
|------|------|
| [backend-design.md](./backend-design.md) | 后端设计指南：需求理解 → 数据结构 → 表结构 → 分层开发 |
| [产品说明.md](./产品说明.md) | 产品功能与业务规则 |
| [fast_knowledge.md](./fast_knowledge.md) | 技术架构与部署 |
