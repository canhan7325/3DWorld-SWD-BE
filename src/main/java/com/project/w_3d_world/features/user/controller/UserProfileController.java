package com.project.w_3d_world.features.user.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.auth.dto.response.UserResponse;
import com.project.w_3d_world.features.user.dto.ProfileRequests.AddressRequest;
import com.project.w_3d_world.features.user.dto.ProfileRequests.BankRequest;
import com.project.w_3d_world.features.user.dto.ProfileRequests.UpdateProfileRequest;
import com.project.w_3d_world.features.user.service.AvatarService;
import com.project.w_3d_world.features.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("isAuthenticated()")
@Tag(
        name = "User profile",
        description = "Manage the signed-in profile, addresses, and bank accounts.")
public class UserProfileController {
    private final UserProfileService service;
    private final AvatarService avatars;

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload avatar",
            description = "Upload a JPEG or PNG image (maximum 5 MB and 16 million pixels) "
                    + "to the Cloudinary Avatar folder. Only the signed-in profile is updated. "
                    + "Corrupt images are rejected and storage failures preserve the previous avatar.")
    public ApiResponse<UserResponse> uploadAvatar(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(avatars.upload(user.userId(), file));
    }

    @GetMapping
    @Operation(
            summary = "Read own profile",
            description = "Return the signed-in account profile, avatar, roles and current capabilities.")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(service.profile(user.userId()));
    }

    @PatchMapping
    @Operation(
            summary = "Update profile",
            description = "Update the signed-in user's name and phone number.")
    public ApiResponse<?> profile(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(service.updateProfile(user.userId(), request));
    }

    @GetMapping("/addresses")
    @Operation(
            summary = "List addresses",
            description = "List delivery addresses owned by the signed-in user.")
    public ApiResponse<?> addresses(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(service.addresses(user.userId()));
    }

    @PostMapping("/addresses")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @Operation(
            summary = "Add address",
            description = "Create a delivery address; the first address becomes default.")
    public ApiResponse<?> addAddress(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AddressRequest request) {
        return ApiResponse.created("Address added successfully.", service.addAddress(user.userId(), request));
    }

    @PatchMapping("/addresses/{id}")
    @Operation(
            summary = "Update address",
            description = "Update an address owned by the signed-in user.")
    public ApiResponse<?> updateAddress(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable int id,
            @Valid @RequestBody AddressRequest request) {
        return ApiResponse.success(service.updateAddress(user.userId(), id, request));
    }

    @DeleteMapping("/addresses/{id}")
    @Operation(
            summary = "Delete address",
            description = "Delete an address owned by the signed-in user.")
    public ApiResponse<Void> deleteAddress(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable int id) {
        service.deleteAddress(user.userId(), id);
        return new ApiResponse<>(200, "Address deleted successfully.", null, OffsetDateTime.now());
    }

    @PostMapping("/addresses/{id}/default")
    @Operation(
            summary = "Set default address",
            description = "Set one owned address as default.")
    public ApiResponse<?> defaultAddress(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable int id) {
        return ApiResponse.success(service.setDefaultAddress(user.userId(), id));
    }

    @GetMapping("/bank-accounts")
    @Operation(
            summary = "List bank accounts",
            description = "List active bank accounts with masked account numbers.")
    public ApiResponse<?> banks(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(service.banks(user.userId()));
    }

    @PostMapping("/bank-accounts")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @Operation(
            summary = "Add bank account",
            description = "Add a bank account; the account number is never returned in full.")
    public ApiResponse<?> addBank(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody BankRequest request) {
        return ApiResponse.created(
                "Bank account added successfully.",
                service.addBank(user.userId(), request));
    }

    @PatchMapping("/bank-accounts/{id}")
    @Operation(
            summary = "Update bank account",
            description = "Update an owned account and reset verification to PENDING.")
    public ApiResponse<?> updateBank(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable int id,
            @Valid @RequestBody BankRequest request) {
        return ApiResponse.success(
                service.updateBank(user.userId(), id, request));
    }

    @DeleteMapping("/bank-accounts/{id}")
    @Operation(
            summary = "Deactivate bank account",
            description = "Soft-delete an owned bank account.")
    public ApiResponse<Void> deleteBank(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable int id) {
        service.deleteBank(user.userId(), id);
        return new ApiResponse<>(200, "Bank account deactivated successfully.", null, OffsetDateTime.now());
    }

    @PostMapping("/bank-accounts/{id}/default")
    @Operation(
            summary = "Set default bank account",
            description = "Set an owned account as the default payout account.")
    public ApiResponse<?> defaultBank(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable int id) {
        return ApiResponse.success(service.setDefaultBank(user.userId(), id));
    }
}
