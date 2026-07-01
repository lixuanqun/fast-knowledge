package com.fast.knowledge.mapper;

import com.fast.knowledge.model.entity.IndexTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IndexTaskMapper {

    int insert(IndexTask task);

    int update(IndexTask task);

    IndexTask findByDocumentId(@Param("documentId") Long documentId);

    List<IndexTask> findPending(@Param("limit") int limit);

    List<IndexTask> findRecent(@Param("limit") int limit);

    List<IndexTask> findFailed(@Param("limit") int limit);

    List<IndexTask> findFailedByKbId(@Param("kbId") Long kbId, @Param("limit") int limit);

    List<IndexTask> findRetryable(@Param("maxRetry") int maxRetry, @Param("limit") int limit);

    int tryAcquireLock(@Param("documentId") Long documentId, @Param("lockedBy") String lockedBy);

    int releaseLock(@Param("documentId") Long documentId, @Param("lockedBy") String lockedBy);
}
