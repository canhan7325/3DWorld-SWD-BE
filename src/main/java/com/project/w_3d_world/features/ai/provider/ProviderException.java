package com.project.w_3d_world.features.ai.provider;

public class ProviderException extends RuntimeException {
    private final String code;
    private final int httpStatus;
    private final boolean retryable;

    public ProviderException(String code, String message) {
        this(code, message, 502, false);
    }

    public ProviderException(String code, String message, int httpStatus, boolean retryable) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
