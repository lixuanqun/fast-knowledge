package com.fast.knowledge.langchain4j.rerank;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.embedding.OnnxTensorUtils;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于 ONNX 的本地 Cross-Encoder Reranker（如 BAAI/bge-reranker-base）。
 */
@Slf4j
public class OnnxRerankScoringModel implements ScoringModel {

    private final OrtEnvironment env;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    private final int maxSeqLen;

    public OnnxRerankScoringModel(KnowledgeProperties.Rerank config) {
        try {
            this.env = OrtEnvironment.getEnvironment();
            this.session = env.createSession(config.getOnnxModelPath(), new OrtSession.SessionOptions());
            this.tokenizer = loadTokenizer(config.getOnnxTokenizerPath());
            this.maxSeqLen = Math.max(32, config.getOnnxMaxSeqLen());
            log.info("ONNX Reranker 已加载: model={}, maxSeqLen={}", config.getOnnxModelPath(), maxSeqLen);
        } catch (Exception e) {
            throw new RuntimeException("加载 ONNX Reranker 失败", e);
        }
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        List<Double> scores = new ArrayList<>(segments.size());
        for (TextSegment segment : segments) {
            scores.add(scorePair(query, segment.text()));
        }
        return Response.from(scores);
    }

    private double scorePair(String query, String passage) {
        try {
            Encoding encoding = tokenizer.encode(query, passage);
            long[] inputIds = padToMax(encoding.getIds());
            long[] attentionMask = padMask(encoding.getAttentionMask(), inputIds.length);
            Map<String, OnnxTensor> inputs = OnnxTensorUtils.buildInputs(env, inputIds, attentionMask);
            try (OrtSession.Result output = session.run(inputs)) {
                return extractScore(output.get(0).getValue());
            } finally {
                inputs.values().forEach(OnnxTensor::close);
            }
        } catch (Exception e) {
            throw new RuntimeException("ONNX Reranker 推理失败", e);
        }
    }

    private long[] padToMax(long[] ids) {
        long[] padded = new long[maxSeqLen];
        int len = Math.min(ids.length, maxSeqLen);
        System.arraycopy(ids, 0, padded, 0, len);
        return padded;
    }

    private long[] padMask(long[] mask, int len) {
        long[] padded = new long[maxSeqLen];
        int effective = Math.min(len, maxSeqLen);
        if (mask != null && mask.length >= effective) {
            System.arraycopy(mask, 0, padded, 0, effective);
        } else {
            for (int i = 0; i < effective; i++) {
                padded[i] = 1;
            }
        }
        return padded;
    }

    private static double extractScore(Object value) {
        if (value instanceof float[][] matrix) {
            return matrix[0][0];
        }
        if (value instanceof float[] vector) {
            return vector[0];
        }
        if (value instanceof double[][] matrix) {
            return matrix[0][0];
        }
        if (value instanceof double[] vector) {
            return vector[0];
        }
        throw new IllegalStateException("不支持的 Reranker ONNX 输出类型: " + value.getClass());
    }

    private static HuggingFaceTokenizer loadTokenizer(String path) throws Exception {
        Path tokenizerPath = Path.of(path);
        if (Files.isDirectory(tokenizerPath)) {
            tokenizerPath = tokenizerPath.resolve("tokenizer.json");
        }
        if (!Files.exists(tokenizerPath)) {
            throw new IllegalStateException("未找到 Reranker tokenizer: " + path);
        }
        return HuggingFaceTokenizer.newInstance(tokenizerPath);
    }
}
