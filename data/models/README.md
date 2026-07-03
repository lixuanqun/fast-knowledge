# ONNX 离线模型

本地 ONNX 模型支撑 **Privacy by Default**：Embedding 与 Rerank 可在无外网 API 的情况下运行，适合中小企业私有化部署。产品定位见 [docs/产品说明.md](../../docs/产品说明.md)。

将以下文件放入本目录（或挂载到容器 `/data/knowledge/models`）：

## Embedding（向量化）

| 文件 | 说明 |
|------|------|
| `bge-small-zh-v1.5.onnx` | BGE 中文小模型 ONNX 导出 |
| `tokenizer.json` | Embedding 配套 tokenizer |

## Reranker（检索重排序，可选）

| 文件 | 说明 |
|------|------|
| `bge-reranker-base.onnx` | BGE Cross-Encoder Reranker ONNX |
| `bge-reranker-tokenizer.json` | Reranker 配套 tokenizer |

## 获取方式

### Embedding

1. 从 [HuggingFace BAAI/bge-small-zh-v1.5](https://huggingface.co/BAAI/bge-small-zh-v1.5) 下载 `tokenizer.json`
2. 使用 [optimum](https://github.com/huggingface/optimum) 导出 ONNX

### Reranker

1. 从 [BAAI/bge-reranker-base](https://huggingface.co/BAAI/bge-reranker-base) 下载 tokenizer，保存为 `bge-reranker-tokenizer.json`
2. 使用 optimum 导出 `onnx/model.onnx`，重命名为 `bge-reranker-base.onnx`

```bash
# 示例（需安装 optimum[onnxruntime]）
optimum-cli export onnx -m BAAI/bge-reranker-base --task text-classification ./bge-reranker-onnx
```

## 配置

```yaml
knowledge:
  embedding:
    provider: onnx
    onnx-model-path: ./data/models/bge-small-zh-v1.5.onnx
    onnx-tokenizer-path: ./data/models/tokenizer.json
  search:
    rerank:
      enabled: true
      provider: onnx
      onnx-model-path: ./data/models/bge-reranker-base.onnx
      onnx-tokenizer-path: ./data/models/bge-reranker-tokenizer.json
```

也可将 `onnx-tokenizer-path` 指向包含 `tokenizer.json` 的目录。
