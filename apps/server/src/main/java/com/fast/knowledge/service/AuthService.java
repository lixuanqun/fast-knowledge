package com.fast.knowledge.service;

import com.fast.knowledge.audit.AuditActions;
import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.mapper.UserMapper;
import com.fast.knowledge.model.dto.LoginRequest;
import com.fast.knowledge.model.entity.KbUser;
import com.fast.knowledge.model.vo.LoginVO;
import com.fast.knowledge.security.AuthSources;
import com.fast.knowledge.security.JwtUtil;
import com.fast.knowledge.security.TokenBlacklistService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;
    private final AuditLogService auditLogService;
    private final FederatedUserService federatedUserService;
    private final ObjectProvider<LdapAuthService> ldapAuthService;
    private final ObjectProvider<OidcAuthService> oidcAuthService;

    public AuthService(UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       TokenBlacklistService tokenBlacklistService,
                       AuditLogService auditLogService,
                       FederatedUserService federatedUserService,
                       ObjectProvider<LdapAuthService> ldapAuthService,
                       ObjectProvider<OidcAuthService> oidcAuthService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.tokenBlacklistService = tokenBlacklistService;
        this.auditLogService = auditLogService;
        this.federatedUserService = federatedUserService;
        this.ldapAuthService = ldapAuthService;
        this.oidcAuthService = oidcAuthService;
    }

    public LoginVO login(LoginRequest request) {
        KbUser user = userMapper.findByUsername(request.getUsername());
        if (user == null || user.getStatus() != 1) {
            throw new BusinessException("用户名或密码错误");
        }
        String authSource = user.getAuthSource() != null ? user.getAuthSource() : AuthSources.LOCAL;
        if (AuthSources.OIDC.equals(authSource)) {
            throw new BusinessException("该账号请使用 OIDC 登录");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        LoginVO vo = issueToken(user);
        auditLogService.log(user.getId(), AuditActions.LOGIN, "USER", user.getId(), "LOCAL");
        return vo;
    }

    public LoginVO loginLdap(LoginRequest request) {
        LdapAuthService ldap = ldapAuthService.getIfAvailable();
        if (ldap == null) {
            throw new BusinessException("LDAP 登录未启用");
        }
        if (!ldap.authenticate(request.getUsername(), request.getPassword())) {
            throw new BusinessException("LDAP 用户名或密码错误");
        }
        KbUser user = federatedUserService.provision(
                AuthSources.LDAP,
                request.getUsername(),
                request.getUsername(),
                ldap.resolveDisplayName(request.getUsername()));
        if (user.getStatus() != 1) {
            throw new BusinessException("账号已禁用");
        }
        LoginVO vo = issueToken(user);
        auditLogService.log(user.getId(), AuditActions.LOGIN, "USER", user.getId(), "LDAP");
        return vo;
    }

    public LoginVO loginOidc(String code, String state) {
        OidcAuthService oidc = oidcAuthService.getIfAvailable();
        if (oidc == null) {
            throw new BusinessException("OIDC 登录未启用");
        }
        OidcAuthService.OidcUserInfo info = oidc.exchangeCode(code, state);
        KbUser user = federatedUserService.provision(
                AuthSources.OIDC,
                info.subject(),
                info.username(),
                info.displayName());
        if (user.getStatus() != 1) {
            throw new BusinessException("账号已禁用");
        }
        LoginVO vo = issueToken(user);
        auditLogService.log(user.getId(), AuditActions.LOGIN, "USER", user.getId(), "OIDC");
        return vo;
    }

    public String oidcAuthorizeUrl() {
        OidcAuthService oidc = oidcAuthService.getIfAvailable();
        if (oidc == null) {
            throw new BusinessException("OIDC 登录未启用");
        }
        return oidc.buildAuthorizationUrl();
    }

    public String oidcFrontendRedirectBase() {
        OidcAuthService oidc = oidcAuthService.getIfAvailable();
        if (oidc == null) {
            return "/login/callback";
        }
        return oidc.frontendRedirectBase();
    }

    public void logout(String token, Long userId) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        tokenBlacklistService.blacklist(token);
        if (userId != null) {
            auditLogService.log(userId, AuditActions.LOGOUT, "USER", userId, null);
        }
    }

    private LoginVO issueToken(KbUser user) {
        String token = jwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setDisplayName(user.getDisplayName());
        vo.setRole(user.getRole());
        vo.setMustChangePassword(Integer.valueOf(1).equals(user.getMustChangePassword()));
        return vo;
    }
}
