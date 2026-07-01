package com.fast.knowledge.service;

import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.mapper.UserMapper;
import com.fast.knowledge.model.dto.AdminResetPasswordRequest;
import com.fast.knowledge.model.dto.ChangePasswordRequest;
import com.fast.knowledge.model.dto.CreateUserRequest;
import com.fast.knowledge.model.dto.UpdateUserRequest;
import com.fast.knowledge.model.entity.KbUser;
import com.fast.knowledge.model.vo.UserVO;
import com.fast.knowledge.security.UserContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder,
                       AuditLogService auditLogService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    public UserVO currentUser() {
        Long userId = UserContext.currentUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        KbUser user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return toVO(user);
    }

    public List<UserVO> listAll() {
        requireAdmin();
        return userMapper.findAll().stream().map(this::toVO).toList();
    }

    @Transactional
    public UserVO create(CreateUserRequest request) {
        requireAdmin();
        if (userMapper.findByUsername(request.getUsername()) != null) {
            throw new BusinessException("用户名已存在");
        }
        KbUser user = new KbUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName() != null && !request.getDisplayName().isBlank()
                ? request.getDisplayName() : request.getUsername());
        user.setRole(normalizeRole(request.getRole()));
        user.setStatus(1);
        user.setMustChangePassword(false);
        userMapper.insert(user);
        auditLogService.log("CREATE_USER", "USER", user.getId(), user.getUsername());
        return toVO(userMapper.findById(user.getId()));
    }

    @Transactional
    public UserVO update(Long id, UpdateUserRequest request) {
        requireAdmin();
        KbUser user = requireUser(id);
        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.getRole() != null) {
            user.setRole(normalizeRole(request.getRole()));
        }
        if (request.getStatus() != null) {
            if (user.getId().equals(UserContext.currentUserId()) && request.getStatus() != 1) {
                throw new BusinessException("不能禁用当前登录账号");
            }
            user.setStatus(request.getStatus());
        }
        userMapper.update(user);
        auditLogService.log("UPDATE_USER", "USER", id, user.getUsername());
        return toVO(userMapper.findById(id));
    }

    @Transactional
    public void delete(Long id) {
        requireAdmin();
        if (id.equals(UserContext.currentUserId())) {
            throw new BusinessException("不能删除当前登录账号");
        }
        KbUser user = requireUser(id);
        if ("admin".equalsIgnoreCase(user.getUsername()) && userMapper.countAll() <= 1) {
            throw new BusinessException("不能删除最后一个管理员账号");
        }
        userMapper.deleteById(id);
        auditLogService.log("DELETE_USER", "USER", id, user.getUsername());
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        Long userId = UserContext.currentUserId();
        KbUser user = requireUser(userId);
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        userMapper.updatePassword(userId, passwordEncoder.encode(request.getNewPassword()));
        auditLogService.log("CHANGE_PASSWORD", "USER", userId, user.getUsername());
    }

    @Transactional
    public void completeInitialSetup(Long userId, String newPassword) {
        KbUser user = requireUser(userId);
        userMapper.updatePassword(userId, passwordEncoder.encode(newPassword));
        auditLogService.log("INITIAL_SETUP", "USER", userId, user.getUsername());
    }

    @Transactional
    public void resetPassword(Long id, AdminResetPasswordRequest request) {
        requireAdmin();
        KbUser user = requireUser(id);
        userMapper.updatePassword(id, passwordEncoder.encode(request.getNewPassword()));
        auditLogService.log("RESET_PASSWORD", "USER", id, user.getUsername());
    }

    private KbUser requireUser(Long id) {
        KbUser user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private void requireAdmin() {
        UserContext ctx = UserContext.get();
        if (ctx == null || !"ADMIN".equals(ctx.getRole())) {
            throw new BusinessException(403, "需要管理员权限");
        }
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "USER";
        }
        return "ADMIN".equalsIgnoreCase(role) ? "ADMIN" : "USER";
    }

    private UserVO toVO(KbUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setDisplayName(user.getDisplayName());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedAt(user.getUpdatedAt());
        return vo;
    }
}
