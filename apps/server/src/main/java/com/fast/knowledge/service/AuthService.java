package com.fast.knowledge.service;

import com.fast.knowledge.cache.CacheProvider;
import com.fast.knowledge.common.BusinessException;
import com.fast.knowledge.mapper.UserMapper;
import com.fast.knowledge.model.dto.LoginRequest;
import com.fast.knowledge.model.entity.KbUser;
import com.fast.knowledge.model.vo.LoginVO;
import com.fast.knowledge.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final CacheProvider cacheProvider;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil, CacheProvider cacheProvider) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.cacheProvider = cacheProvider;
    }

    public LoginVO login(LoginRequest request) {
        KbUser user = userMapper.findByUsername(request.getUsername());
        if (user == null || user.getStatus() != 1) {
            throw new BusinessException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        String token = jwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setDisplayName(user.getDisplayName());
        vo.setRole(user.getRole());
        vo.setMustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()));
        return vo;
    }

    public void logout(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        cacheProvider.set("kb:token:blacklist:" + token, "1", Duration.ofHours(24));
    }
}
