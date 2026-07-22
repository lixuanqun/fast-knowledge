package com.fast.knowledge.service;

import com.fast.knowledge.audit.AuditActions;
import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.mapper.ApiKeyMapper;
import com.fast.knowledge.mapper.KbMemberMapper;
import com.fast.knowledge.mapper.KnowledgeBaseMapper;
import com.fast.knowledge.mapper.UserMapper;
import com.fast.knowledge.model.dto.CreateApiKeyRequest;
import com.fast.knowledge.model.entity.ApiKey;
import com.fast.knowledge.model.entity.KbMember;
import com.fast.knowledge.model.entity.KbUser;
import com.fast.knowledge.model.entity.KnowledgeBase;
import com.fast.knowledge.model.vo.ApiKeyCreatedVO;
import com.fast.knowledge.model.vo.ApiKeyVO;
import com.fast.knowledge.security.UserContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class ApiKeyService {

    private static final String KEY_PREFIX_LABEL = "fk_";
    private final SecureRandom secureRandom = new SecureRandom();

    private final ApiKeyMapper apiKeyMapper;
    private final UserMapper userMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KbMemberMapper kbMemberMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public ApiKeyService(ApiKeyMapper apiKeyMapper,
                         UserMapper userMapper,
                         KnowledgeBaseMapper knowledgeBaseMapper,
                         KbMemberMapper kbMemberMapper,
                         PasswordEncoder passwordEncoder,
                         AuditLogService auditLogService) {
        this.apiKeyMapper = apiKeyMapper;
        this.userMapper = userMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.kbMemberMapper = kbMemberMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    public List<ApiKeyVO> list() {
        requireAdmin();
        return apiKeyMapper.findAll().stream().map(this::toVo).toList();
    }

    @Transactional
    public ApiKeyCreatedVO create(CreateApiKeyRequest request) {
        requireAdmin();
        Long userId = request.getUserId();
        if (userId == null) {
            throw new BusinessException("请指定绑定用户");
        }
        KbUser user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != 1) {
            throw new BusinessException("绑定用户不存在或已禁用");
        }
        if (request.getKbId() != null) {
            assertBoundUserCanAccessKb(user, request.getKbId());
        }
        String secret = randomSecret();
        String prefix = KEY_PREFIX_LABEL + secret.substring(0, 8);
        String plainKey = prefix + "." + secret.substring(8);

        ApiKey entity = new ApiKey();
        String name = request.getName();
        if (name == null || name.isBlank()) {
            throw new BusinessException("API Key 名称不能为空");
        }
        entity.setName(name.trim());
        entity.setKeyPrefix(prefix);
        entity.setKeyHash(passwordEncoder.encode(plainKey));
        entity.setUserId(userId);
        entity.setKbId(request.getKbId());
        entity.setStatus(1);
        apiKeyMapper.insert(entity);

        auditLogService.log(AuditActions.CREATE_API_KEY, "API_KEY", entity.getId(), entity.getName());

        ApiKeyCreatedVO vo = new ApiKeyCreatedVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setKbId(entity.getKbId());
        vo.setUserId(entity.getUserId());
        vo.setApiKey(plainKey);
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    @Transactional
    public void revoke(Long id) {
        requireAdmin();
        ApiKey key = apiKeyMapper.selectById(id);
        if (key == null) {
            throw new BusinessException("API Key 不存在");
        }
        key.setStatus(0);
        apiKeyMapper.updateById(key);
    }

    public Optional<ApiKey> authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank() || !rawKey.startsWith(KEY_PREFIX_LABEL)) {
            return Optional.empty();
        }
        int dot = rawKey.indexOf('.');
        if (dot <= 0) {
            return Optional.empty();
        }
        String prefix = rawKey.substring(0, dot);
        ApiKey entity = apiKeyMapper.findByPrefix(prefix);
        if (entity == null || entity.getStatus() != 1) {
            return Optional.empty();
        }
        if (!passwordEncoder.matches(rawKey, entity.getKeyHash())) {
            return Optional.empty();
        }
        entity.setLastUsedAt(LocalDateTime.now());
        apiKeyMapper.updateById(entity);
        return Optional.of(entity);
    }

    private void requireAdmin() {
        if (!"ADMIN".equals(UserContext.get() != null ? UserContext.get().getRole() : null)) {
            throw new BusinessException(403, "需要管理员权限");
        }
    }

    /** 创建限定范围 Key 时，校验绑定用户对该 KB 至少有读权限。 */
    private void assertBoundUserCanAccessKb(KbUser user, Long kbId) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            throw new BusinessException("限定知识库不存在");
        }
        if ("ADMIN".equals(user.getRole())) {
            return;
        }
        if (kb.getOwnerId() != null && kb.getOwnerId().equals(user.getId())) {
            return;
        }
        if ("PUBLIC".equals(kb.getVisibility())) {
            return;
        }
        KbMember member = kbMemberMapper.findByKbAndUser(kbId, user.getId());
        if (member != null) {
            return;
        }
        throw new BusinessException("绑定用户无权访问限定知识库");
    }

    private ApiKeyVO toVo(ApiKey key) {
        ApiKeyVO vo = new ApiKeyVO();
        vo.setId(key.getId());
        vo.setName(key.getName());
        vo.setKeyPrefix(key.getKeyPrefix());
        vo.setKbId(key.getKbId());
        vo.setUserId(key.getUserId());
        vo.setStatus(key.getStatus());
        vo.setCreatedAt(key.getCreatedAt());
        vo.setLastUsedAt(key.getLastUsedAt());
        return vo;
    }

    private String randomSecret() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
