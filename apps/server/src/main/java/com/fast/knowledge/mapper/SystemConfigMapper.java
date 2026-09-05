package com.fast.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fast.knowledge.model.entity.SystemConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.dao.DuplicateKeyException;

@Mapper
public interface SystemConfigMapper extends BaseMapper<SystemConfig> {

    @Select("SELECT config_value FROM kb_system_config WHERE config_key = #{key}")
    String getValue(@Param("key") String key);

    /**
     * 跨方言 upsert（Java 层实现）：
     * 先按主键查询，缺失则插入；并发插入撞唯一键时回退为更新。
     */
    default int upsert(String key, String value) {
        SystemConfig existing = findByKey(key);
        if (existing == null) {
            SystemConfig config = new SystemConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            try {
                return insert(config);
            } catch (DuplicateKeyException e) {
                existing = findByKey(key);
            }
        }
        if (existing == null) {
            throw new IllegalStateException("kb_system_config 并发写入异常: " + key);
        }
        existing.setConfigValue(value);
        return updateById(existing);
    }

    default SystemConfig findByKey(String key) {
        return selectOne(Wrappers.<SystemConfig>lambdaQuery().eq(SystemConfig::getConfigKey, key));
    }
}
