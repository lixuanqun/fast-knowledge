package com.fast.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fast.knowledge.model.entity.IndexTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface IndexTaskMapper extends BaseMapper<IndexTask> {

    @Select("SELECT * FROM kb_index_task WHERE document_id = #{documentId} ORDER BY id DESC LIMIT 1")
    IndexTask findByDocumentId(@Param("documentId") Long documentId);

    @Select("SELECT * FROM kb_index_task WHERE status = 'PENDING' ORDER BY id LIMIT #{limit}")
    List<IndexTask> findPending(@Param("limit") int limit);

    @Select("SELECT * FROM kb_index_task ORDER BY updated_at DESC LIMIT #{limit}")
    List<IndexTask> findRecent(@Param("limit") int limit);

    @Select("SELECT * FROM kb_index_task WHERE status = 'FAILED' ORDER BY updated_at DESC LIMIT #{limit}")
    List<IndexTask> findFailed(@Param("limit") int limit);

    @Select("""
            SELECT t.* FROM kb_index_task t
            INNER JOIN kb_document d ON t.document_id = d.id
            WHERE d.kb_id = #{kbId} AND t.status = 'FAILED'
            ORDER BY t.updated_at DESC LIMIT #{limit}
            """)
    List<IndexTask> findFailedByKbId(@Param("kbId") Long kbId, @Param("limit") int limit);

    @Select("""
            SELECT * FROM kb_index_task WHERE status = 'FAILED' AND retry_count < #{maxRetry}
            ORDER BY updated_at LIMIT #{limit}
            """)
    List<IndexTask> findRetryable(@Param("maxRetry") int maxRetry, @Param("limit") int limit);

    @Update("""
            UPDATE kb_index_task
            SET locked_by = #{lockedBy}, locked_at = #{lockedAt}
            WHERE document_id = #{documentId}
              AND (locked_by IS NULL OR locked_at < #{lockExpiredBefore})
            """)
    int tryAcquireLock(@Param("documentId") Long documentId,
                       @Param("lockedBy") String lockedBy,
                       @Param("lockedAt") LocalDateTime lockedAt,
                       @Param("lockExpiredBefore") LocalDateTime lockExpiredBefore);

    @Update("""
            UPDATE kb_index_task
            SET locked_by = NULL, locked_at = NULL
            WHERE document_id = #{documentId} AND locked_by = #{lockedBy}
            """)
    int releaseLock(@Param("documentId") Long documentId, @Param("lockedBy") String lockedBy);

    /** 查找超过指定时间的 PENDING 任务（Pub/Sub 兜底用） */
    @Select("SELECT * FROM kb_index_task WHERE status = 'PENDING' AND created_at < #{createdBefore} ORDER BY id LIMIT #{limit}")
    List<IndexTask> findStalePending(@Param("limit") int limit, @Param("createdBefore") LocalDateTime createdBefore);

    /** 查找 DEAD 任务 */
    @Select("SELECT * FROM kb_index_task WHERE status = 'DEAD' ORDER BY updated_at DESC LIMIT #{limit}")
    List<IndexTask> findDead(@Param("limit") int limit);
}
