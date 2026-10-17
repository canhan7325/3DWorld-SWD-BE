package com.project.w_3d_world.features.model.controller;

import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.model.storage.ModelStorageException;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import jakarta.persistence.PersistenceException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@RestControllerAdvice(basePackageClasses = ModelUploadController.class)
@Order(0)
public class ModelUploadExceptionHandler {
    @ExceptionHandler(ModelStorageException.class)
    public ResponseEntity<ApiResponse<Void>> storage(ModelStorageException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case INVALID_METADATA, INVALID_KEY, EMPTY_FILE, INVALID_CONTENT -> HttpStatus.BAD_REQUEST;
            case TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
            case CONFIGURATION, UNSAFE_PATH, COLLISION, ATOMIC_MOVE_UNSUPPORTED,
                    IO_FAILURE, CLEANUP_FAILURE, NOT_FOUND -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        String message = status.is5xxServerError() ? "Model storage is unavailable. Please try again later."
                : status == HttpStatus.CONTENT_TOO_LARGE ? "Model file must not exceed 50000000 bytes."
                : "Model file is empty, unsupported or malformed.";
        return ResponseEntity.status(status).body(ApiResponse.error(status.value(), message));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class, PersistenceException.class})
    public ResponseEntity<ApiResponse<Void>> database(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Model upload could not be committed. Please try again later."));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<ApiResponse<Void>> multipart(Exception exception) {
        HttpStatus status = exception instanceof MaxUploadSizeExceededException
                ? HttpStatus.CONTENT_TOO_LARGE : HttpStatus.BAD_REQUEST;
        String message = status == HttpStatus.CONTENT_TOO_LARGE
                ? "Uploaded file exceeds the configured request limit."
                : "Multipart metadata and file parts are required.";
        return ResponseEntity.status(status).body(ApiResponse.error(status.value(), message));
    }
}
