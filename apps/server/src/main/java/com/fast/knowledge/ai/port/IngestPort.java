package com.fast.knowledge.ai.port;

import com.fast.knowledge.model.entity.DocumentChunk;
import com.fast.knowledge.model.entity.KbDocument;

import java.util.List;

/**
 * 摄入端口 — 文档分块与向量索引写入面。
 * 块行由调用方持久化后再回调 {@link #embedChunks}，保证 DB 与向量库按块 ID 对齐。
 */
public interface IngestPort {

    /** 文本分块；实现方内部构造文档元数据（kbId/docId/title） */
    List<String> split(String fullText, Long kbId, Long docId, String title);

    /** 块向量化并写入向量库 */
    void embedChunks(KbDocument doc, List<DocumentChunk> chunks);

    void deleteByDocument(Long kbId, Long docId);

    void deleteChunk(Long kbId, Long chunkId);

    void deleteKb(Long kbId);

    /** 知识库级 AI 运行时缓存失效（向量存储 / 检索器 / 通用助手实例）；聊天助手见 ConversationPort */
    void evictKbRuntime(Long kbId);
}
