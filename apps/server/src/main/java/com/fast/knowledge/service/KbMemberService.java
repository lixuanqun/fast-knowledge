package com.fast.knowledge.service;

import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.mapper.KbMemberMapper;
import com.fast.knowledge.mapper.UserMapper;
import com.fast.knowledge.model.dto.KbMemberRequest;
import com.fast.knowledge.model.entity.KbMember;
import com.fast.knowledge.model.entity.KbUser;
import com.fast.knowledge.model.entity.KnowledgeBase;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KbMemberService {

    private final KbMemberMapper kbMemberMapper;
    private final UserMapper userMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final AuditLogService auditLogService;

    public KbMemberService(KbMemberMapper kbMemberMapper, UserMapper userMapper,
                           KnowledgeBaseService knowledgeBaseService,
                           AuditLogService auditLogService) {
        this.kbMemberMapper = kbMemberMapper;
        this.userMapper = userMapper;
        this.knowledgeBaseService = knowledgeBaseService;
        this.auditLogService = auditLogService;
    }

    public List<KbMember> listMembers(Long kbId) {
        knowledgeBaseService.checkReadPermission(knowledgeBaseService.getById(kbId));
        return kbMemberMapper.findByKbId(kbId);
    }

    public KbMember addMember(Long kbId, KbMemberRequest request) {
        KnowledgeBase kb = knowledgeBaseService.getById(kbId);
        knowledgeBaseService.checkKbAdminPermission(kb);
        KbUser user = userMapper.findByUsername(request.getUsername());
        if (user == null) {
            throw new BusinessException("用户不存在: " + request.getUsername());
        }
        if (kb.getOwnerId().equals(user.getId())) {
            throw new BusinessException("所有者无需添加为成员");
        }
        KbMember existing = kbMemberMapper.findByKbAndUser(kbId, user.getId());
        if (existing != null) {
            existing.setPermission(request.getPermission() != null ? request.getPermission() : "READ");
            kbMemberMapper.updateById(existing);
            auditLogService.log("UPDATE_MEMBER", "KB", kbId, "user=" + user.getUsername());
            return requireEnrichedMember(existing.getId());
        }
        KbMember member = new KbMember();
        member.setKbId(kbId);
        member.setUserId(user.getId());
        member.setPermission(request.getPermission() != null ? request.getPermission() : "READ");
        kbMemberMapper.insert(member);
        auditLogService.log("ADD_MEMBER", "KB", kbId, "user=" + user.getUsername());
        return requireEnrichedMember(member.getId());
    }

    public void removeMember(Long kbId, Long memberId) {
        KnowledgeBase kb = knowledgeBaseService.getById(kbId);
        knowledgeBaseService.checkKbAdminPermission(kb);
        KbMember member = kbMemberMapper.findByIdAndKbId(memberId, kbId);
        if (member == null) {
            throw new BusinessException("成员不存在");
        }
        kbMemberMapper.deleteById(memberId);
        auditLogService.log("REMOVE_MEMBER", "KB", kbId, "memberId=" + memberId);
    }

    public String resolvePermission(KnowledgeBase kb, Long userId) {
        if (kb.getOwnerId().equals(userId)) {
            return "ADMIN";
        }
        KbMember member = kbMemberMapper.findByKbAndUser(kb.getId(), userId);
        if (member != null) {
            return member.getPermission();
        }
        if ("PUBLIC".equals(kb.getVisibility())) {
            return "READ";
        }
        return null;
    }

    private KbMember requireEnrichedMember(Long memberId) {
        KbMember enriched = kbMemberMapper.findEnrichedById(memberId);
        if (enriched == null) {
            throw new BusinessException("成员记录不存在");
        }
        return enriched;
    }
}
