package com.fast.knowledge.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解 — 标注在 Controller 方法上，基于 Redis 固定窗口计数。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 时间窗口内允许的最大请求数，默认 30 */
    int maxRequests() default 30;

    /** 时间窗口秒数，默认 60 */
    int windowSeconds() default 60;
}
