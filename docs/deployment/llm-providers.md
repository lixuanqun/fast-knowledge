# 大模型配置（模型中立）

Fast Knowledge 通过 **OpenAI 兼容 API** 对接各大模型厂商，设置 `LLM_PROVIDER` 即可使用预设，也可用环境变量覆盖 `baseUrl` / `apiKey` / `model`。

## 快速配置

```yaml
knowledge:
  llm:
    provider: dashscope    # 预设 ID
    api-key: ${LLM_API_KEY}
    model: qwen-plus       # 可覆盖预设默认模型
```

或环境变量：

```env
LLM_PROVIDER=deepseek
LLM_API_KEY=sk-xxx
LLM_MODEL=deepseek-chat
```

## 支持的预设

| provider | 厂商 | 默认 baseUrl | 默认 model | 说明 |
|----------|------|--------------|------------|------|
| `ollama` | 本地 Ollama | `http://localhost:11434/v1` | `qwen2.5:7b` | 私有化推荐，`api-key` 填 `ollama` |
| `deepseek` | DeepSeek | `https://api.deepseek.com/v1` | `deepseek-chat` | [API Key](https://platform.deepseek.com/api_keys) |
| `glm` | 智谱 GLM | `https://open.bigmodel.cn/api/paas/v4` | `glm-4-flash` | [API Key](https://open.bigmodel.cn/usercenter/apikeys) |
| `dashscope` | 阿里云百炼 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus` | [百炼控制台](https://bailian.console.aliyun.com/) |
| `volcengine` | 火山引擎方舟 | `https://ark.cn-beijing.volces.com/api/v3` | — | `LLM_MODEL` 填**推理接入点 ID** |
| `openai` | OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` | 国际 OpenAI |
| `custom` | 自定义 | 需设置 `LLM_BASE_URL` | 需设置 `LLM_MODEL` | 任意兼容端点 |

查询预设列表 API：`GET /api/system/llm-providers`（无需登录）

## 配置示例

### DeepSeek

```env
LLM_PROVIDER=deepseek
LLM_API_KEY=sk-your-deepseek-key
LLM_MODEL=deepseek-chat
```

### 智谱 GLM

```env
LLM_PROVIDER=glm
LLM_API_KEY=your-zhipu-api-key
LLM_MODEL=glm-4-flash
```

### 阿里云百炼（DashScope 兼容模式）

```env
LLM_PROVIDER=dashscope
LLM_API_KEY=sk-your-dashscope-key
LLM_MODEL=qwen-plus
```

### 火山引擎方舟

在火山控制台创建推理接入点，将接入点 ID 作为 model：

```env
LLM_PROVIDER=volcengine
LLM_API_KEY=your-volcengine-api-key
LLM_MODEL=ep-20240618001-xxxxx
```

### 本地 Ollama（强隐私）

```env
LLM_PROVIDER=ollama
LLM_BASE_URL=http://localhost:11434/v1
LLM_API_KEY=ollama
LLM_MODEL=qwen2.5:7b
LLM_ALLOW_EXTERNAL=false
```

## 隐私与外连控制

```env
LLM_ALLOW_EXTERNAL=false
```

开启后，仅允许访问 `localhost` / `127.0.0.1` / `ollama` 等本地地址，阻止调用云端 API，适合严格内网部署。

## Embedding 与 LLM 分离

向量化与对话可独立配置：

```env
EMBEDDING_PROVIDER=onnx          # 本地向量
LLM_PROVIDER=dashscope         # 云端对话
```

或全部本地：

```env
EMBEDDING_PROVIDER=ollama
EMBEDDING_MODEL=nomic-embed-text
LLM_PROVIDER=ollama
LLM_MODEL=qwen2.5:7b
LLM_ALLOW_EXTERNAL=false
```

## Docker Compose 示例

```yaml
environment:
  LLM_PROVIDER: dashscope
  LLM_API_KEY: ${LLM_API_KEY}
  LLM_MODEL: qwen-plus
  EMBEDDING_PROVIDER: onnx
```

## 故障排查

| 现象 | 处理 |
|------|------|
| 401 Unauthorized | 检查 `LLM_API_KEY` |
| model not found | 核对厂商文档中的模型名或火山接入点 ID |
| 连接超时 | 检查网络、`LLM_ALLOW_EXTERNAL` 是否误拦本地地址 |
| 火山引擎报错 | `LLM_MODEL` 必须为 Endpoint ID，不是模型展示名 |
