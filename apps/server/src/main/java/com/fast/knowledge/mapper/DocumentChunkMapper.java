package com.fast.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fast.knowledge.model.entity.DocumentChunk;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {

    @Insert("""
            <script>
            INSERT INTO kb_document_chunk (kb_id, document_id, chunk_index, content, token_count)
            VALUES
            <foreach collection="chunks" item="c" separator=",">
            (#{c.kbId}, #{c.documentId}, #{c.chunkIndex}, #{c.content}, #{c.tokenCount})
            </foreach>
            </script>
            """)
    int batchInsert(@Param("chunks") List<DocumentChunk> chunks);

    default List<DocumentChunk> findByDocumentId(Long documentId) {
        return selectList(Wrappers.<DocumentChunk>lambdaQuery()
                .eq(DocumentChunk::getDocumentId, documentId)
                .orderByAsc(DocumentChunk::getChunkIndex));
    }

    default List<DocumentChunk> findByKbId(Long kbId) {
        return selectList(Wrappers.<DocumentChunk>lambdaQuery()
                .eq(DocumentChunk::getKbId, kbId)
                .orderByAsc(DocumentChunk::getDocumentId)
                .orderByAsc(DocumentChunk::getChunkIndex));
    }

    default int deleteByDocumentId(Long documentId) {
        return delete(Wrappers.<DocumentChunk>lambdaQuery().eq(DocumentChunk::getDocumentId, documentId));
    }

    default int deleteByKbId(Long kbId) {
        return delete(Wrappers.<DocumentChunk>lambdaQuery().eq(DocumentChunk::getKbId, kbId));
    }
}
