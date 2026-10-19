package com.project.w_3d_world.common.response;

import java.time.OffsetDateTime;

public record ApiResponse<T>(int status, String message, T data, OffsetDateTime timestamp) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "Success", data, OffsetDateTime.now());
    }

    public static <T> ApiResponse<T> created(String message, T data) {
        return new ApiResponse<>(201, message, data, OffsetDateTime.now());
    }

    public static <T> ApiResponse<T> error(int status, String message) {
        return new ApiResponse<>(status, message, null, OffsetDateTime.now());
    }
}
