package com.fast.knowledge.embedding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OnnxTensorUtilsTest {

    @Test
    void meanPoolWithAttentionMask() {
        float[][][] hidden = new float[1][3][4];
        hidden[0][0] = new float[]{1, 0, 0, 0};
        hidden[0][1] = new float[]{0, 1, 0, 0};
        hidden[0][2] = new float[]{0, 0, 1, 0};
        long[] mask = {1, 1, 0};

        float[] vec = OnnxTensorUtils.extractVector(hidden, mask, 4);
        assertEquals(4, vec.length);
        // 归一化后范数约为 1
        double norm = 0;
        for (float v : vec) {
            norm += v * v;
        }
        assertEquals(1.0, norm, 0.01);
    }
}
