package com.fast.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fast.knowledge.model.entity.DocumentChunk;
import com.fast.knowledge.model.vo.SearchHitVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {

    @Insert("""
            <script>
            INSERT INTO kb_document_chunk (kb_id, document_id, chunk_index, content, section_title, context_prefix, page_no, anchor_type, token_count)
            VALUES
            <foreach collection="chunks" item="c" separator=",">
            (#{c.kbId}, #{c.documentId}, #{c.chunkIndex}, #{c.content}, #{c.sectionTitle}, #{c.contextPrefix}, #{c.pageNo}, #{c.anchorType}, #{c.tokenCount})
            </foreach>
            </script>
            """)
    int batchInsert(@Param("chunks") List<DocumentChunk> chunks);

    /** 混合检索关键词支路：FULLTEXT ngram 自然语言相关度排序（仅 MySQL，H2 profile 关闭该支路） */
    @Select("""
            <script>
            SELECT c.id            AS chunkId,
                   c.document_id   AS documentId,
                   d.title         AS documentTitle,
                   d.doc_type      AS docType,
                   d.doc_no        AS docNo,
                   c.section_title AS section,
                   c.page_no       AS pageNo,
                   c.anchor_type   AS anchorType,
                   c.content       AS content,
                   MATCH(c.content) AGAINST(#{query} IN NATURAL LANGUAGE MODE) AS score
            FROM kb_document_chunk c
            JOIN kb_document d ON d.id = c.document_id
            WHERE c.kb_id = #{kbId}
              AND MATCH(c.content) AGAINST(#{query} IN NATURAL LANGUAGE MODE)
              <if test="docType != null and docType != ''">AND d.doc_type = #{docType}</if>
            ORDER BY score DESC
            LIMIT #{limit}
            </script>
            """)
    List<SearchHitVO> fulltextSearch(@Param("kbId") Long kbId,
                                     @Param("query") String query,
                                     @Param("docType") String docType,
                                     @Param("limit") int limit);

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
