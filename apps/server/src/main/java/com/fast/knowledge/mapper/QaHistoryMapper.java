package com.fast.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fast.knowledge.model.entity.QaHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QaHistoryMapper extends BaseMapper<QaHistory> {

    default IPage<QaHistory> findPage(Page<QaHistory> page, Long kbId, Long userId) {
        return selectPage(page, Wrappers.<QaHistory>lambdaQuery()
                .eq(kbId != null, QaHistory::getKbId, kbId)
                .eq(userId != null, QaHistory::getUserId, userId)
                .orderByDesc(QaHistory::getId));
    }
}
