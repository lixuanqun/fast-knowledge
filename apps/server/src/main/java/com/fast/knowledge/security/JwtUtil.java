package com.fast.knowledge.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fast.knowledge.config.KnowledgeProperties;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtil {

    private final KnowledgeProperties properties;

    public JwtUtil(KnowledgeProperties properties) {
        this.properties = properties;
    }

    public String createToken(Long userId, String username, String role) {
        long expireMs = properties.getJwt().getExpireSeconds() * 1000;
        return JWT.create()
                .withClaim("userId", userId)
                .withClaim("username", username)
                .withClaim("role", role)
                .withExpiresAt(new Date(System.currentTimeMillis() + expireMs))
                .sign(Algorithm.HMAC256(properties.getJwt().getSecret()));
    }

    public DecodedJWT verify(String token) {
        return JWT.require(Algorithm.HMAC256(properties.getJwt().getSecret())).build().verify(token);
    }

    public Long getUserId(String token) {
        return verify(token).getClaim("userId").asLong();
    }
}
