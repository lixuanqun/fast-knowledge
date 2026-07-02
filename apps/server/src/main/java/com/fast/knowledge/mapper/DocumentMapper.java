package com.fast.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fast.knowledge.model.entity.KbDocument;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DocumentMapper extends BaseMapper<KbDocument> {

    default List<KbDocument> findByKbId(Long kbId) {
        return selectList(Wrappers.<KbDocument>lambdaQuery()
                .eq(KbDocument::getKbId, kbId)
                .orderByDesc(KbDocument::getCreatedAt));
    }

    default int deleteByKbId(Long kbId) {
        return delete(Wrappers.<KbDocument>lambdaQuery().eq(KbDocument::getKbId, kbId));
    }
}
