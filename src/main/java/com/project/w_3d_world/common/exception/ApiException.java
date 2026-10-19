package com.project.w_3d_world.common.exception;

import lombok.Getter;

import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static ApiException emailAlreadyExists() {
        return new ApiException(HttpStatus.CONFLICT, "Email is already registered.");
    }

    public static ApiException invalidCredentials() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED, "Invalid credentials or account is unavailable.");
    }

    public static ApiException invalidSession() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Session is invalid, expired or revoked.");
    }

    public static ApiException invalidRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
