package com.fast.knowledge.embedding;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;

import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Map;

final class OnnxTensorUtils {

    private OnnxTensorUtils() {
    }

    static long[] padIds(long[] ids, long[] attentionMask, int maxLen) {
        long[] paddedIds = new long[maxLen];
        long[] paddedMask = new long[maxLen];
        int len = Math.min(ids.length, maxLen);
        System.arraycopy(ids, 0, paddedIds, 0, len);
        if (attentionMask != null && attentionMask.length >= len) {
            System.arraycopy(attentionMask, 0, paddedMask, 0, len);
        } else {
            for (int i = 0; i < len; i++) {
                paddedMask[i] = 1;
            }
        }
        return paddedIds;
    }

    static long[] defaultMask(int len) {
        long[] mask = new long[len];
        for (int i = 0; i < len; i++) {
            mask[i] = 1;
        }
        return mask;
    }

    static Map<String, OnnxTensor> buildInputs(OrtEnvironment env, long[] inputIds, long[] attentionMask) throws Exception {
        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put("input_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), new long[]{1, inputIds.length}));
        inputs.put("attention_mask", OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), new long[]{1, attentionMask.length}));
        return inputs;
    }

    /**
     * 对 last_hidden_state 做 attention mask 加权平均池化（BGE 标准做法）。
     */
    static float[] meanPool(float[][][] hiddenStates, long[] attentionMask, int dimension) {
        int seqLen = hiddenStates[0].length;
        int hiddenSize = hiddenStates[0][0].length;
        float[] sum = new float[hiddenSize];
        float count = 0;
        int effectiveLen = Math.min(seqLen, attentionMask.length);
        for (int i = 0; i < effectiveLen; i++) {
            if (attentionMask[i] == 0) {
                continue;
            }
            for (int j = 0; j < hiddenSize; j++) {
                sum[j] += hiddenStates[0][i][j];
            }
            count += 1;
        }
        if (count == 0) {
            count = 1;
        }
        float[] vec = new float[Math.min(hiddenSize, dimension)];
        for (int j = 0; j < vec.length; j++) {
            vec[j] = sum[j] / count;
        }
        return normalize(vec);
    }

    static float[] extractVector(Object value, long[] attentionMask, int dimension) {
        if (value instanceof float[][] pooled) {
            return trimAndNormalize(pooled[0], dimension);
        }
        if (value instanceof float[][][] hidden) {
            return meanPool(hidden, attentionMask, dimension);
        }
        throw new IllegalStateException("不支持的 ONNX 输出类型: " + value.getClass());
    }

    static float[] trimAndNormalize(float[] vec, int dimension) {
        if (vec.length > dimension) {
            float[] trimmed = new float[dimension];
            System.arraycopy(vec, 0, trimmed, 0, dimension);
            vec = trimmed;
        }
        return normalize(vec);
    }

    static float[] normalize(float[] vec) {
        float norm = 0;
        for (float v : vec) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < vec.length; i++) {
                vec[i] /= norm;
            }
        }
        return vec;
    }
}
