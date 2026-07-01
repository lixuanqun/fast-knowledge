package com.fast.knowledge.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SystemConfigMapper {
    String getValue(@Param("key") String key);

    int upsert(@Param("key") String key, @Param("value") String value);
}
