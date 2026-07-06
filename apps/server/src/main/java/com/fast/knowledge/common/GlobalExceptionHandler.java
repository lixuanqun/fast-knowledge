package com.fast.knowledge.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CompletionException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusiness(BusinessException e) {
        if (e.getMessage() != null && e.getMessage().contains("LLM")) {
            return ApiResponse.fail("大模型服务不可用，请检查 LLM 配置或确认 Ollama/云端 API 已启动");
        }
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ApiResponse<Void> handleAccessDenied(Exception e) {
        return ApiResponse.fail(403, "无权限访问");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        return ApiResponse.fail(400, message);
    }

    @ExceptionHandler(RuntimeException.class)
    public ApiResponse<Void> handleRuntime(RuntimeException e) {
        if (isLlmConnectivityFailure(e)) {
            log.warn("LLM connectivity failure: {}", e.getMessage());
            return ApiResponse.fail("大模型服务不可用，请检查 LLM 配置或确认 Ollama/云端 API 已启动");
        }
        return handleException(e);
    }

    private static boolean isLlmConnectivityFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConnectException || t instanceof ClosedChannelException) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null && (msg.contains("Connection refused")
                    || msg.contains("connect timed out")
                    || msg.contains("Failed to connect"))) {
                return true;
            }
        }
        return false;
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ApiResponse.fail("服务器内部错误，请稍后再试");
    }
}
