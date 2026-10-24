package com.project.w_3d_world.features.printing.exception;

import com.project.w_3d_world.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class PrintingErrors {
    private PrintingErrors() {}

    public static ApiException requestNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Print Request was not found.");
    }

    public static ApiException requestFileNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Print Request file was not found.");
    }

    public static ApiException modelVersionNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Model Version was not found.");
    }

    public static ApiException fileChangeConflict() {
        return new ApiException(HttpStatus.CONFLICT,
                "Print Request file cannot be changed in its current state.");
    }

    public static ApiException specificationChangeConflict() {
        return new ApiException(HttpStatus.CONFLICT,
                "Print Request specification cannot be changed in its current state.");
    }

    public static ApiException duplicateActiveMaterial() {
        return new ApiException(HttpStatus.CONFLICT,
                "An active Material with this name and technology already exists.");
    }
}
