package com.fast.knowledge.security;

import com.fast.knowledge.cache.CacheProvider;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CacheProvider cacheProvider;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, CacheProvider cacheProvider) {
        this.jwtUtil = jwtUtil;
        this.cacheProvider = cacheProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                String token = auth.substring(7);
                if (cacheProvider.get("kb:token:blacklist:" + token).isEmpty()) {
                    DecodedJWT jwt = jwtUtil.verify(token);
                    AuthenticatedUser user = new AuthenticatedUser(
                            jwt.getClaim("userId").asLong(),
                            jwt.getClaim("username").asString(),
                            jwt.getClaim("role").asString()
                    );
                    UserContext ctx = new UserContext();
                    ctx.setUserId(user.getUserId());
                    ctx.setUsername(user.getUsername());
                    ctx.setRole(user.getRole());
                    UserContext.set(ctx);

                    String role = user.getRole() != null ? user.getRole() : "USER";
                    JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                            user,
                            token,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    cacheProvider.set("kb:session:" + user.getUserId(), token, Duration.ofHours(24));
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
