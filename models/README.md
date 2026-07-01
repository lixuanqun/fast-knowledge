# ONNX 离线 Embedding 模型

将以下文件放入本目录（或挂载到容器 `/data/knowledge/models`）：

| 文件 | 说明 |
|------|------|
| `bge-small-zh-v1.5.onnx` | BGE 中文小模型 ONNX 导出 |
| `tokenizer.json` | HuggingFace tokenizer（与模型配套） |

## 获取方式

1. 从 [HuggingFace BAAI/bge-small-zh-v1.5](https://huggingface.co/BAAI/bge-small-zh-v1.5) 下载 `tokenizer.json`
2. 使用 [optimum](https://github.com/huggingface/optimum) 或官方脚本导出 ONNX

## 配置

```yaml
knowledge:
  embedding:
    provider: onnx
    onnx-model-path: ./models/bge-small-zh-v1.5.onnx
    onnx-tokenizer-path: ./models/tokenizer.json
    dimension: 512
    onnx-max-seq-len: 512
    onnx-batch-size: 16
```

也可将 `onnx-tokenizer-path` 指向包含 `tokenizer.json` 的目录。
