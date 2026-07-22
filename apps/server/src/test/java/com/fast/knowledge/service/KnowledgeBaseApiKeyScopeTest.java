package com.fast.knowledge.service;

import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.mapper.KbMemberMapper;
import com.fast.knowledge.mapper.KnowledgeBaseMapper;
import com.fast.knowledge.model.entity.KnowledgeBase;
import com.fast.knowledge.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseApiKeyScopeTest {

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;
    @Mock
    private com.fast.knowledge.mapper.DocumentMapper documentMapper;
    @Mock
    private com.fast.knowledge.mapper.DocumentChunkMapper documentChunkMapper;
    @Mock
    private KbMemberMapper kbMemberMapper;
    @Mock
    private com.fast.knowledge.langchain4j.store.KbVectorIndexService vectorIndexService;
    @Mock
    private com.fast.knowledge.config.KnowledgeProperties properties;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private WorkspaceService workspaceService;
    @Mock
    private SearchCacheService searchCacheService;

    @InjectMocks
    private KnowledgeBaseService knowledgeBaseService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void scopedApiKeyCannotReadOtherKbEvenAsAdmin() {
        UserContext ctx = new UserContext();
        ctx.setUserId(1L);
        ctx.setRole("ADMIN");
        ctx.setScopedKbId(10L);
        UserContext.set(ctx);

        KnowledgeBase other = kb(99L, 1L, "PRIVATE");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> knowledgeBaseService.checkReadPermission(other));
        assertEquals(403, ex.getCode());
    }

    @Test
    void scopedApiKeyCanReadBoundKb() {
        UserContext ctx = new UserContext();
        ctx.setUserId(2L);
        ctx.setRole("USER");
        ctx.setScopedKbId(10L);
        UserContext.set(ctx);

        KnowledgeBase bound = kb(10L, 2L, "PRIVATE");
        knowledgeBaseService.checkReadPermission(bound);
    }

    @Test
    void listMineReturnsOnlyScopedKb() {
        UserContext ctx = new UserContext();
        ctx.setUserId(2L);
        ctx.setRole("USER");
        ctx.setScopedKbId(10L);
        UserContext.set(ctx);

        KnowledgeBase bound = kb(10L, 2L, "PRIVATE");
        when(knowledgeBaseMapper.selectById(10L)).thenReturn(bound);

        List<KnowledgeBase> list = knowledgeBaseService.listMine();
        assertEquals(1, list.size());
        assertEquals(10L, list.get(0).getId());
    }

    @Test
    void unscopedJwtUnaffected() {
        UserContext ctx = new UserContext();
        ctx.setUserId(2L);
        ctx.setRole("USER");
        UserContext.set(ctx);

        KnowledgeBase owned = kb(10L, 2L, "PRIVATE");
        knowledgeBaseService.checkReadPermission(owned);
    }

    private static KnowledgeBase kb(Long id, Long ownerId, String visibility) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(id);
        kb.setOwnerId(ownerId);
        kb.setVisibility(visibility);
        return kb;
    }
}
