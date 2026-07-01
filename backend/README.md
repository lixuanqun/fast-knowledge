# Fast Knowledge — 后端子模块

Java 21 + Spring Boot 3.5 单实例轻量知识库 API 服务。

> 仓库结构、启动与部署见根目录 [README.md](../README.md)。

## 功能

- 单租户用户认证（JWT + Spring Security 6）
- 知识库与文档管理（上传 PDF/DOCX/TXT/MD）
- 内置 Lucene HNSW + BM25 混合检索
- 智能问答、多轮对话（SSE）、智能写文档
- MySQL 业务数据 + Redis 缓存/任务锁

## 环境要求

- **JDK 21**（Spring Boot 3.5 官方支持；不再使用 JDK 17）
- MySQL 8.0（库名 `fast_knowledge`）
- Redis 5.0+
- （可选）Ollama 或其他 OpenAI 兼容 LLM 服务

### 安装 JDK 21（Windows）

1. 安装 JDK 21（本机已安装 Microsoft OpenJDK 21，路径见下）
2. 设置用户环境变量：
   - `JAVA_HOME` = `C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot`
   - 将 `%JAVA_HOME%\bin` 置于 `Path` 最前
3. 验证：`java -version` 应显示 `21.x`
4. 确认无误后可卸载旧版 `jdk-17.0.12`（控制面板 → 程序和功能）

> 若仅临时切换，可在 PowerShell 会话内设置：  
> `$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"; $env:Path="$env:JAVA_HOME\bin;$env:Path"`

## 快速启动

### 1. 创建数据库并初始化表结构

```sql
CREATE DATABASE fast_knowledge DEFAULT CHARSET utf8mb4;
USE fast_knowledge;
```

在 MySQL 客户端中执行项目根目录下的 `sql/init.sql`（建表 + 全文索引）。

```powershell
# 示例（按本机 mysql 客户端路径调整）
mysql -u root -p fast_knowledge < sql/init.sql
```

首次启动时 `DataInitializer` 会自动创建默认管理员 **admin / admin123**（若不存在）。

### 2. 修改配置

编辑 `src/main/resources/application.yml` 中的 MySQL、Redis 连接信息。

### 3. 编译运行

```powershell
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn spring-boot:run
```

服务地址：`http://localhost:8088/api`  
Swagger：`http://localhost:8088/api/swagger-ui.html`

默认管理员：**admin / admin123**（首次启动自动创建）

## 技术栈版本

| 组件 | 版本 |
|------|------|
| Spring Boot | 3.5.11 |
| LangChain4j | 1.17.1（BOM 管理） |
| Apache Tika | 3.3.0 |
| Apache Lucene | 9.11.1 |
| SpringDoc OpenAPI | 2.8.6 |
| Spring Security | 6.x（`spring-boot-starter-security`） |

运行时默认启用 **Java 21 虚拟线程**（`spring.threads.virtual.enabled=true`）。

## 认证与安全

- **JWT 签发/校验**：仍使用 `java-jwt`（`JwtUtil`），与现有 Token 格式兼容
- **请求鉴权**：`JwtAuthenticationFilter` + Spring Security `SecurityFilterChain`（替代原 `AuthInterceptor`）
- **角色控制**：`UserController` 管理接口使用 `@PreAuthorize("hasRole('ADMIN')")`；知识库 ACL 仍在 Service 层校验
- **登出黑名单**：Redis/Caffeine 缓存 `kb:token:blacklist:{token}` 逻辑不变
- **公开接口**：`/auth/login`、`/system/config`、Swagger、单 Jar 静态资源

## LLM 配置

通过环境变量或 `application.yml` 配置：

| 变量 | 说明 | 默认 |
|------|------|------|
| `LLM_BASE_URL` | OpenAI 兼容 API 地址 | `http://localhost:11434/v1` |
| `LLM_API_KEY` | API Key | `ollama` |
| `LLM_MODEL` | 模型名称 | `qwen2.5:7b` |

LLM 调用通过 **LangChain4j**（`OpenAiChatModel` / `OpenAiStreamingChatModel`）对接 OpenAI 兼容 API。

## LangChain4j 与内置向量库

- **LLM 层**：LangChain4j `ChatModel` / `StreamingChatModel`（替代原自研 `LlmClient`）
- **向量存储**：`LuceneEmbeddingStore` 实现 LangChain4j `EmbeddingStore`，底层仍为 Apache Lucene
- **RAG 检索**：`KbHybridContentRetriever` 实现 `ContentRetriever`，使用 Lucene HNSW + BM25 混合检索
- **Embedding**：`ProviderEmbeddingModel` 桥接现有 hash / Ollama / ONNX 配置

## Embedding 配置

| `EMBEDDING_PROVIDER` | 说明 |
|----------------------|------|
| `hash` | 仅开发/演示降级模式，启动时会打印 WARN（语义质量有限） |
| `ollama` | 使用 Ollama `/api/embeddings` |
| `onnx` | 本地 ONNX 模型（需放置 `models/bge-small-zh-v1.5.onnx`） |

## 数据目录

- `./data/uploads` — 上传文件
- `./data/lucene` — Lucene 索引
