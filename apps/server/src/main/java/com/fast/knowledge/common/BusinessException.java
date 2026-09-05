package com.fast.knowledge.common;

public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        this(-1, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = -1;
    }

    public int getCode() {
        return code;
    }
}
