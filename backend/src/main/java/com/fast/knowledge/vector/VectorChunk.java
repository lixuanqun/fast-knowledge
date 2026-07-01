package com.fast.knowledge.vector;

import lombok.Data;

@Data
public class VectorChunk {
    private Long docId;
    private Long chunkId;
    private String title;
    private String content;
    private float[] vector;
}
