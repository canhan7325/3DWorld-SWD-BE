package com.project.w_3d_world.features.printing.exception;

import com.project.w_3d_world.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class QuoteExpiredException extends ApiException {
    public QuoteExpiredException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
