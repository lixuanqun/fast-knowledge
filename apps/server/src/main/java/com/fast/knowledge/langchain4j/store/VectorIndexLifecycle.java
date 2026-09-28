package com.fast.knowledge.langchain4j.store;

/**
 * 向量索引生命周期 — 工厂逐出/应用关闭时的统一落盘与资源释放契约。
 * Lucene 引擎（IndexWriter/SearcherManager）与内存引擎（JSON 落盘）均实现此接口。
 */
public interface VectorIndexLifecycle {

    /** 强制落盘（Lucene commit / 内存 JSON flush） */
    void flush();

    /** 落盘并释放底层资源，实例此后不可再用 */
    void close();
}
