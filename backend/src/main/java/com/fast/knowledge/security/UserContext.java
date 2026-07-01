package com.fast.knowledge.security;

import lombok.Data;

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
}
