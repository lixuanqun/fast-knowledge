package com.fast.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fast.knowledge.model.entity.SystemConfig;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SystemConfigMapper extends BaseMapper<SystemConfig> {

    @Select("SELECT config_value FROM kb_system_config WHERE config_key = #{key}")
    String getValue(@Param("key") String key);

    @Insert("""
            INSERT INTO kb_system_config (config_key, config_value)
            VALUES (#{key}, #{value})
            ON CONFLICT (config_key) DO UPDATE SET config_value = excluded.config_value
            """)
    int upsert(@Param("key") String key, @Param("value") String value);

    default SystemConfig findByKey(String key) {
        return selectOne(Wrappers.<SystemConfig>lambdaQuery().eq(SystemConfig::getConfigKey, key));
    }
}
