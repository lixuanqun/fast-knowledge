package com.fast.knowledge.service;

import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.config.KnowledgeProperties;
import com.fast.knowledge.mapper.DocumentChunkMapper;
import com.fast.knowledge.mapper.DocumentMapper;
import com.fast.knowledge.mapper.KbMemberMapper;
import com.fast.knowledge.mapper.KnowledgeBaseMapper;
import com.fast.knowledge.model.dto.KnowledgeBaseRequest;
import com.fast.knowledge.model.entity.KbMember;
import com.fast.knowledge.model.entity.KnowledgeBase;
import com.fast.knowledge.security.UserContext;
import com.fast.knowledge.langchain4j.store.KbVectorIndexService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final KbMemberMapper kbMemberMapper;
    private final KbVectorIndexService vectorIndexService;
    private final KnowledgeProperties properties;
    private final AuditLogService auditLogService;
    private final WorkspaceService workspaceService;
    private final SearchCacheService searchCacheService;

    public KnowledgeBaseService(KnowledgeBaseMapper knowledgeBaseMapper,
                                DocumentMapper documentMapper,
                                DocumentChunkMapper documentChunkMapper,
                                KbMemberMapper kbMemberMapper,
                                KbVectorIndexService vectorIndexService,
                                KnowledgeProperties properties,
                                AuditLogService auditLogService,
                                WorkspaceService workspaceService,
                                SearchCacheService searchCacheService) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.documentMapper = documentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.kbMemberMapper = kbMemberMapper;
        this.vectorIndexService = vectorIndexService;
        this.properties = properties;
        this.auditLogService = auditLogService;
        this.workspaceService = workspaceService;
        this.searchCacheService = searchCacheService;
    }

    public List<KnowledgeBase> listMine() {
        return knowledgeBaseMapper.findByOwnerOrMember(UserContext.currentUserId());
    }

    public KnowledgeBase getById(Long id) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException("知识库不存在");
        }
        checkReadPermission(kb);
        return kb;
    }

    public KnowledgeBase create(KnowledgeBaseRequest request) {
        var workspace = workspaceService.getDefault();
        if (workspace == null) {
            workspace = workspaceService.createDefaultForUser(UserContext.currentUserId());
        }
        KnowledgeBase kb = new KnowledgeBase();
        kb.setName(request.getName());
        kb.setDescription(request.getDescription() != null ? request.getDescription() : "");
        kb.setWorkspaceId(workspace.getId());
        kb.setOwnerId(UserContext.currentUserId());
        kb.setVisibility(request.getVisibility() != null ? request.getVisibility() : "PRIVATE");
        kb.setSearchAlpha(request.getSearchAlpha() != null ? request.getSearchAlpha() : 0.6);
        kb.setSearchTopK(request.getSearchTopK() != null ? request.getSearchTopK()
                : properties.getSearch().getDefaultTopK());
        kb.setStatus(1);
        knowledgeBaseMapper.insert(kb);
        auditLogService.log("CREATE_KB", "KB", kb.getId(), kb.getName());
        return kb;
    }

    public KnowledgeBase update(Long id, KnowledgeBaseRequest request) {
        KnowledgeBase kb = getById(id);
        checkWritePermission(kb);
        kb.setName(request.getName());
        if (request.getDescription() != null) {
            kb.setDescription(request.getDescription());
        }
        if (request.getVisibility() != null) {
            kb.setVisibility(request.getVisibility());
        }
        if (request.getSearchAlpha() != null) {
            kb.setSearchAlpha(request.getSearchAlpha());
        }
        if (request.getSearchTopK() != null) {
            kb.setSearchTopK(request.getSearchTopK());
        }
        knowledgeBaseMapper.updateById(kb);
        auditLogService.log("UPDATE_KB", "KB", id, kb.getName());
        return kb;
    }

    @Transactional
    public void delete(Long id) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException("知识库不存在");
        }
        checkKbAdminPermission(kb);
        documentChunkMapper.deleteByKbId(id);
        documentMapper.deleteByKbId(id);
        kbMemberMapper.deleteByKbId(id);
        vectorIndexService.deleteKb(id);
        knowledgeBaseMapper.deleteById(id);
        searchCacheService.invalidateForKb(id);
        auditLogService.log("DELETE_KB", "KB", id, kb.getName());
    }

    public void checkReadPermission(KnowledgeBase kb) {
        UserContext ctx = UserContext.get();
        if (ctx == null) {
            throw new BusinessException(401, "未认证");
        }
        if ("ADMIN".equals(ctx.getRole())) {
            return;
        }
        if (kb.getOwnerId().equals(ctx.getUserId())) {
            return;
        }
        if ("PUBLIC".equals(kb.getVisibility())) {
            return;
        }
        KbMember member = kbMemberMapper.findByKbAndUser(kb.getId(), ctx.getUserId());
        if (member != null) {
            return;
        }
        throw new BusinessException(403, "无权限访问该知识库");
    }

    public void checkWritePermission(KnowledgeBase kb) {
        UserContext ctx = UserContext.get();
        if (ctx == null) {
            throw new BusinessException(401, "未认证");
        }
        if ("ADMIN".equals(ctx.getRole())) {
            return;
        }
        if (kb.getOwnerId().equals(ctx.getUserId())) {
            return;
        }
        KbMember member = kbMemberMapper.findByKbAndUser(kb.getId(), ctx.getUserId());
        if (member != null && ("WRITE".equals(member.getPermission()) || "ADMIN".equals(member.getPermission()))) {
            return;
        }
        throw new BusinessException(403, "无权限操作该知识库");
    }

    /** owner、知识库 ADMIN 成员或系统 ADMIN 可管理成员与删除知识库 */
    public void checkKbAdminPermission(KnowledgeBase kb) {
        UserContext ctx = UserContext.get();
        if (ctx == null) {
            throw new BusinessException(401, "未认证");
        }
        if ("ADMIN".equals(ctx.getRole())) {
            return;
        }
        if (kb.getOwnerId().equals(ctx.getUserId())) {
            return;
        }
        KbMember member = kbMemberMapper.findByKbAndUser(kb.getId(), ctx.getUserId());
        if (member != null && "ADMIN".equals(member.getPermission())) {
            return;
        }
        throw new BusinessException(403, "无权限管理该知识库");
    }
}
