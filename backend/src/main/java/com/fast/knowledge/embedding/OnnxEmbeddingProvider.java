package com.fast.knowledge.embedding;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.fast.knowledge.config.KnowledgeProperties;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class OnnxEmbeddingProvider implements EmbeddingProvider {

    private final OrtEnvironment env;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    private final int dimension;
    private final int maxSeqLen;
    private final int batchSize;

    public OnnxEmbeddingProvider(KnowledgeProperties properties) {
        try {
            KnowledgeProperties.Embedding cfg = properties.getEmbedding();
            this.env = OrtEnvironment.getEnvironment();
            this.session = env.createSession(cfg.getOnnxModelPath(), new OrtSession.SessionOptions());
            this.tokenizer = loadTokenizer(cfg.getOnnxTokenizerPath());
            this.dimension = cfg.getDimension();
            this.maxSeqLen = cfg.getOnnxMaxSeqLen();
            this.batchSize = Math.max(1, cfg.getOnnxBatchSize());
            log.info("ONNX Embedding 已加载: model={}, maxSeqLen={}, batchSize={}, tokenizer={}",
                    cfg.getOnnxModelPath(), maxSeqLen, batchSize, tokenizer != null);
        } catch (Exception e) {
            throw new RuntimeException("加载 ONNX 模型失败", e);
        }
    }

    @Override
    public float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> result = new ArrayList<>(texts.size());
        for (int offset = 0; offset < texts.size(); offset += batchSize) {
            int end = Math.min(offset + batchSize, texts.size());
            List<String> batch = texts.subList(offset, end);
            result.addAll(inferBatch(batch));
        }
        return result;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    private List<float[]> inferBatch(List<String> batch) {
        List<float[]> vectors = new ArrayList<>(batch.size());
        for (String text : batch) {
            vectors.add(inferSingle(text));
        }
        return vectors;
    }

    private float[] inferSingle(String text) {
        try {
            long[] inputIds;
            long[] attentionMask;
            if (tokenizer != null) {
                Encoding encoding = tokenizer.encode(text);
                long[] paddedIds = new long[maxSeqLen];
                long[] paddedMask = new long[maxSeqLen];
                int len = Math.min(encoding.getIds().length, maxSeqLen);
                System.arraycopy(encoding.getIds(), 0, paddedIds, 0, len);
                long[] mask = encoding.getAttentionMask();
                if (mask != null && mask.length >= len) {
                    System.arraycopy(mask, 0, paddedMask, 0, len);
                } else {
                    for (int i = 0; i < len; i++) {
                        paddedMask[i] = 1;
                    }
                }
                inputIds = paddedIds;
                attentionMask = paddedMask;
            } else {
                int len = Math.min(text.length(), maxSeqLen);
                inputIds = new long[maxSeqLen];
                attentionMask = new long[maxSeqLen];
                for (int i = 0; i < len; i++) {
                    inputIds[i] = text.charAt(i);
                    attentionMask[i] = 1;
                }
            }

            Map<String, OnnxTensor> inputs = OnnxTensorUtils.buildInputs(env, inputIds, attentionMask);
            try (OrtSession.Result output = session.run(inputs)) {
                Object value = output.get(0).getValue();
                return OnnxTensorUtils.extractVector(value, attentionMask, dimension);
            } finally {
                inputs.values().forEach(OnnxTensor::close);
            }
        } catch (Exception e) {
            throw new RuntimeException("ONNX 推理失败", e);
        }
    }

    private HuggingFaceTokenizer loadTokenizer(String path) throws Exception {
        Path tokenizerPath = Path.of(path);
        if (Files.isDirectory(tokenizerPath)) {
            tokenizerPath = tokenizerPath.resolve("tokenizer.json");
        }
        if (Files.exists(tokenizerPath)) {
            return HuggingFaceTokenizer.newInstance(tokenizerPath);
        }
        log.warn("未找到 tokenizer，路径: {}，将使用字符级回退", path);
        return null;
    }
}
