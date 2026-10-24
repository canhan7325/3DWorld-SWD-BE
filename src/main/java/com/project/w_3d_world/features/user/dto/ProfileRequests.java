package com.project.w_3d_world.features.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import java.time.LocalDate;

public final class ProfileRequests {
    private ProfileRequests() {}

    public record UpdateProfileRequest(
            @NotBlank @Schema(description = "Display name") String fullName,
            @Schema(description = "Phone number") String phone,
            @Past LocalDate dateOfBirth) {}

    public record AddressRequest(
            @NotBlank @Schema(description = "Recipient name") String recipientName,
            String phone,
            @NotBlank @Schema(description = "Detailed address") String addressLine,
            @Schema(description = "City or province") String city,
            String province,
            String district,
            String ward) {}

    public record BankRequest(
            @NotBlank @Schema(description = "Bank code") String bankCode,
            @NotBlank @Schema(description = "Bank name") String bankName,
            @NotBlank @Schema(description = "Account holder name") String accountHolder,
            @NotBlank @Schema(description = "Full account number; never returned in full")
                    String accountNumber) {}
}
