package com.fast.knowledge.mapper;

import com.fast.knowledge.model.entity.KbMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbMemberMapper {

    List<KbMember> findByKbId(@Param("kbId") Long kbId);

    KbMember findByKbAndUser(@Param("kbId") Long kbId, @Param("userId") Long userId);

    KbMember findById(@Param("id") Long id);

    KbMember findByIdAndKbId(@Param("id") Long id, @Param("kbId") Long kbId);

    int insert(KbMember member);

    int update(KbMember member);

    int deleteById(@Param("id") Long id);

    int deleteByKbId(@Param("kbId") Long kbId);
}
