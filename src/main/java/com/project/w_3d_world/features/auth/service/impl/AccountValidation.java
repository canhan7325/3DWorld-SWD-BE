package com.project.w_3d_world.features.auth.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class AccountValidation {
    private AccountValidation() {}

    public static String required(String value, int max, String field) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw ApiException.invalidRequest(field + " is required and must not exceed " + max + " characters.");
        }
        return value.trim();
    }

    public static String email(String value) {
        String email = required(value, 320, "Email").toLowerCase(Locale.ROOT);
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw ApiException.invalidRequest("Email is invalid.");
        }
        return email;
    }

    public static void password(String value) {
        if (value == null || value.length() < 8 || value.getBytes(StandardCharsets.UTF_8).length > 72
                || !value.matches("(?s).*[a-z].*") || !value.matches("(?s).*[A-Z].*")
                || !value.matches("(?s).*[0-9].*") || !value.matches("(?s).*[^A-Za-z0-9].*")) {
            throw ApiException.invalidRequest(
                    "Password must have at least 8 characters, uppercase, lowercase, digit and special character, "
                            + "and must not exceed 72 UTF-8 bytes.");
        }
    }

    public static String phone(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!value.trim().matches("\\+?[0-9]{8,15}")) {
            throw ApiException.invalidRequest("Phone must have 8-15 digits, optionally prefixed with +.");
        }
        return value.trim();
    }

    public static String bankNumber(String value) {
        String number = required(value, 34, "Bank account number");
        if (!number.matches("[0-9]{6,34}")) {
            throw ApiException.invalidRequest("Bank account number must contain 6-34 digits.");
        }
        return number;
    }
}
