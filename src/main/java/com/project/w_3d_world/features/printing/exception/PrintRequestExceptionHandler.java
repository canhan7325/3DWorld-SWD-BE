package com.project.w_3d_world.features.printing.exception;

import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.printing.controller.PrintRequestController;
import jakarta.persistence.PersistenceException;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PrintRequestController.class)
@Order(0)
public class PrintRequestExceptionHandler {
    @ExceptionHandler({DataAccessException.class, TransactionException.class, PersistenceException.class})
    public ResponseEntity<ApiResponse<Void>> persistenceFailure(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Print Request could not be completed."));
    }
}
