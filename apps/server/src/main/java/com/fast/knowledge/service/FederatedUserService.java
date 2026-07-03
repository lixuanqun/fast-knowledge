package com.fast.knowledge.service;

import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.mapper.UserMapper;
import com.fast.knowledge.model.entity.KbUser;
import com.fast.knowledge.security.AuthSources;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class FederatedUserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public FederatedUserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public KbUser provision(String authSource, String externalId, String username, String displayName) {
        if (externalId != null && !externalId.isBlank()) {
            KbUser existing = userMapper.findByAuthSourceAndExternalId(authSource, externalId);
            if (existing != null) {
                return existing;
            }
        }

        String safeUsername = resolveUsername(username, authSource, externalId);
        KbUser byName = userMapper.findByUsername(safeUsername);
        if (byName != null) {
            if (authSource.equals(byName.getAuthSource())
                    || (byName.getAuthSource() == null && AuthSources.LOCAL.equals(authSource))) {
                return byName;
            }
            throw new BusinessException("用户名已存在且认证来源不同: " + safeUsername);
        }

        KbUser user = new KbUser();
        user.setUsername(safeUsername);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setDisplayName(displayName != null && !displayName.isBlank() ? displayName : safeUsername);
        user.setRole("USER");
        user.setStatus(1);
        user.setMustChangePassword(0);
        user.setAuthSource(authSource);
        user.setExternalId(externalId);
        userMapper.insert(user);
        return user;
    }

    private String resolveUsername(String username, String authSource, String externalId) {
        if (username != null && !username.isBlank()) {
            return username.trim();
        }
        if (externalId != null && !externalId.isBlank()) {
            return authSource.toLowerCase() + "_" + externalId.replaceAll("[^a-zA-Z0-9._-]", "_");
        }
        throw new BusinessException("无法确定联邦登录用户名");
    }
}
