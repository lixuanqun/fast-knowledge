package com.fast.knowledge.mapper;

import com.fast.knowledge.model.entity.KbUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

    KbUser findByUsername(@Param("username") String username);

    KbUser findById(@Param("id") Long id);

    List<KbUser> findAll();

    int insert(KbUser user);

    int update(KbUser user);

    int updatePassword(@Param("id") Long id, @Param("password") String password);

    int deleteById(@Param("id") Long id);

    int countAll();
}
