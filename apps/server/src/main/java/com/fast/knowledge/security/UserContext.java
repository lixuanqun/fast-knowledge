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
    /**
     * API Key 限定的知识库 ID；null 表示不限定（JWT 用户或不限范围的 Key）。
     * 有值时，所有 KB 读/写/管理校验必须匹配该 ID。
     */
    private Long scopedKbId;

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

    /** 当前请求是否为「限定知识库」的 API Key 调用。 */
    public static Long currentScopedKbId() {
        UserContext ctx = get();
        return ctx != null ? ctx.getScopedKbId() : null;
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
