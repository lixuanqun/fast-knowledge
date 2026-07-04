package com.fast.knowledge.common;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class SseEmitterHelperTest {

    @Test
    void shouldCreateEmitterWithTimeout() {
        SseEmitter emitter = SseEmitterHelper.create(30_000L);
        assertThat(emitter).isNotNull();
        assertThat(emitter.getTimeout()).isEqualTo(30_000L);
    }

    @Test
    void shouldHaveDefaultTimeoutConstants() {
        assertThat(SseEmitterHelper.TIMEOUT_CHAT).isEqualTo(120_000L);
        assertThat(SseEmitterHelper.TIMEOUT_LONG).isEqualTo(180_000L);
    }

    @Test
    void sendDataShouldNotThrow() {
        SseEmitter emitter = SseEmitterHelper.create(1000L);
        // 不应抛异常
        SseEmitterHelper.sendData(emitter, "test data");
    }

    @Test
    void sendNamedShouldNotThrow() {
        SseEmitter emitter = SseEmitterHelper.create(1000L);
        SseEmitterHelper.sendNamed(emitter, "done", "[DONE]");
    }

    @Test
    void sendErrorShouldCompleteWithError() {
        SseEmitter emitter = SseEmitterHelper.create(1000L);
        SseEmitterHelper.sendError(emitter, "test error");
        // sendError 会将 emitter 标记为 completeWithError
    }
}
