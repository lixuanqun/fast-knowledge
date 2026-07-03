package com.fast.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fast.knowledge.model.entity.WikiPage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WikiPageMapper extends BaseMapper<WikiPage> {

    default WikiPage findByKbAndSlug(Long kbId, String slug) {
        return selectOne(Wrappers.<WikiPage>lambdaQuery()
                .eq(WikiPage::getKbId, kbId)
                .eq(WikiPage::getSlug, slug));
    }
}
