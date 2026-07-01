package com.fast.knowledge.mapper;

import com.fast.knowledge.model.entity.KbDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocumentMapper {

    KbDocument findById(@Param("id") Long id);

    List<KbDocument> findByKbId(@Param("kbId") Long kbId);

    int insert(KbDocument document);

    int update(KbDocument document);

    int deleteById(@Param("id") Long id);

    int deleteByKbId(@Param("kbId") Long kbId);
}
