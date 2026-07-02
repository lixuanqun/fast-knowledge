package com.fast.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fast.knowledge.model.entity.KbUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<KbUser> {

    default KbUser findByUsername(String username) {
        return selectOne(Wrappers.<KbUser>lambdaQuery().eq(KbUser::getUsername, username));
    }

    default List<KbUser> findAll() {
        return selectList(Wrappers.<KbUser>lambdaQuery().orderByAsc(KbUser::getId));
    }

    default int countAll() {
        return Math.toIntExact(selectCount(null));
    }

    @Update("UPDATE kb_user SET password = #{password}, must_change_password = 0 WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("password") String password);
}
