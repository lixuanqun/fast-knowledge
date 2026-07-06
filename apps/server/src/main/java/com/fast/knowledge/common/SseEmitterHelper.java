package com.fast.knowledge.common;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * SSE 流式响应工具类 — 封装 SseEmitter 的创建与事件发送，消除各 Service 中重复的 try-catch 模板代码。
 */
public final class SseEmitterHelper {

    /** 默认短超时（毫秒），适用于对话流 */
    public static final long TIMEOUT_CHAT = 120_000L;
    /** 默认长超时（毫秒），适用于文档生成等耗时场景 */
    public static final long TIMEOUT_LONG = 180_000L;

    private SseEmitterHelper() {
    }

    /** 创建一个带有指定超时的 SseEmitter */
    public static SseEmitter create(long timeoutMs) {
        return new SseEmitter(timeoutMs);
    }

    /** 发送 data 事件，IOException 时静默忽略（连接断开） */
    public static void sendData(SseEmitter emitter, Object data) {
        try {
            emitter.send(SseEmitter.event().data(data));
        } catch (IOException ignored) {
        }
    }

    /** 发送命名事件，IOException 时静默忽略 */
    public static void sendNamed(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException ignored) {
        }
    }

    /** 发送 error 事件并完成（带错误）。所有 IOException 均静默（客户端已断开）。 */
    public static void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(message));
        } catch (IOException ignored) {
        }
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }
}
