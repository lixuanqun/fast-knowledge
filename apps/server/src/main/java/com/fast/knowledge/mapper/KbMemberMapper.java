package com.fast.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fast.knowledge.model.entity.KbMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KbMemberMapper extends BaseMapper<KbMember> {

    @Select("""
            SELECT m.*, u.username, u.display_name AS displayName
            FROM kb_kb_member m LEFT JOIN kb_user u ON m.user_id = u.id
            WHERE m.kb_id = #{kbId} ORDER BY m.id
            """)
    List<KbMember> findByKbId(@Param("kbId") Long kbId);

    default KbMember findByKbAndUser(Long kbId, Long userId) {
        return selectOne(Wrappers.<KbMember>lambdaQuery()
                .eq(KbMember::getKbId, kbId)
                .eq(KbMember::getUserId, userId));
    }

    @Select("""
            SELECT m.*, u.username, u.display_name AS displayName
            FROM kb_kb_member m LEFT JOIN kb_user u ON m.user_id = u.id
            WHERE m.id = #{id}
            """)
    KbMember findEnrichedById(@Param("id") Long id);

    @Select("""
            SELECT m.*, u.username, u.display_name AS displayName
            FROM kb_kb_member m LEFT JOIN kb_user u ON m.user_id = u.id
            WHERE m.id = #{id} AND m.kb_id = #{kbId}
            """)
    KbMember findByIdAndKbId(@Param("id") Long id, @Param("kbId") Long kbId);

    default int deleteByKbId(Long kbId) {
        return delete(Wrappers.<KbMember>lambdaQuery().eq(KbMember::getKbId, kbId));
    }
}
