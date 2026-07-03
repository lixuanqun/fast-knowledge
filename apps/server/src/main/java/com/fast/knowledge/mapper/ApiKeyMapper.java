package com.fast.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fast.knowledge.model.entity.ApiKey;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ApiKeyMapper extends BaseMapper<ApiKey> {

    default ApiKey findByPrefix(String prefix) {
        return selectOne(Wrappers.<ApiKey>lambdaQuery()
                .eq(ApiKey::getKeyPrefix, prefix)
                .eq(ApiKey::getStatus, 1));
    }

    default List<ApiKey> findAll() {
        return selectList(Wrappers.<ApiKey>lambdaQuery().orderByDesc(ApiKey::getId));
    }
}
