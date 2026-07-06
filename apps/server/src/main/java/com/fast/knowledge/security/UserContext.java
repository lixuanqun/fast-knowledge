package com.fast.knowledge.security;

import lombok.Data;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@Data
public class UserContext {
    private Long userId;
    private String username;
    private String role;

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    public static void set(UserContext ctx) {
        HOLDER.set(ctx);
    }

    public static UserContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static Long currentUserId() {
        UserContext ctx = get();
        return ctx != null ? ctx.getUserId() : null;
    }

    /** 将当前用户与安全上下文传播到异步线程（SSE 等场景）。 */
    public static Runnable wrap(Runnable task) {
        UserContext captured = get();
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            securityContext.setAuthentication(authentication);
        }
        return () -> {
            try {
                if (captured != null) {
                    set(captured);
                }
                SecurityContextHolder.setContext(securityContext);
                task.run();
            } finally {
                clear();
                SecurityContextHolder.clearContext();
            }
        };
    }
}
