package com.fast.knowledge.controller;

import com.fast.knowledge.common.ApiResponse;
import com.fast.knowledge.model.dto.LoginRequest;
import com.fast.knowledge.model.vo.LoginVO;
import com.fast.knowledge.security.RateLimit;
import com.fast.knowledge.security.UserContext;
import com.fast.knowledge.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @RateLimit(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/ldap/login")
    @RateLimit(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<LoginVO> ldapLogin(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.loginLdap(request));
    }

    @GetMapping("/oidc/authorize")
    public ApiResponse<Map<String, String>> oidcAuthorize() {
        return ApiResponse.ok(Map.of("authorizationUrl", authService.oidcAuthorizeUrl()));
    }

    @GetMapping("/oidc/callback")
    public void oidcCallback(@RequestParam String code,
                             @RequestParam String state,
                             HttpServletResponse response) throws IOException {
        LoginVO vo = authService.loginOidc(code, state);
        String base = authService.oidcFrontendRedirectBase();
        String redirect = base + (base.contains("?") ? "&" : "?")
                + "token=" + URLEncoder.encode(vo.getToken(), StandardCharsets.UTF_8);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.sendRedirect(redirect);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        authService.logout(request.getHeader("Authorization"), UserContext.currentUserId());
        return ApiResponse.ok();
    }
}
