package com.fast.knowledge.mapper;

import com.fast.knowledge.model.entity.DocumentChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocumentChunkMapper {

    int batchInsert(@Param("chunks") List<DocumentChunk> chunks);

    List<DocumentChunk> findByDocumentId(@Param("documentId") Long documentId);

    int deleteByDocumentId(@Param("documentId") Long documentId);

    int deleteByKbId(@Param("kbId") Long kbId);

    DocumentChunk findById(@Param("id") Long id);

    List<DocumentChunk> findByKbId(@Param("kbId") Long kbId);
}
