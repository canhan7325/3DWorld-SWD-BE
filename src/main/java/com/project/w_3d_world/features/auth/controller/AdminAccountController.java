package com.project.w_3d_world.features.auth.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.auth.service.AdminAccountService;
import com.project.w_3d_world.features.user.enums.AccountStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(
        name = "Admin accounts",
        description = "Internal accounts, role grants, sanctions and manual sandbox phone verification.")
public class AdminAccountController {
    private final AdminAccountService service;

    @GetMapping
    @Operation(
            summary = "List accounts",
            description = "Admin-only paginated account status and role grants; no password or bank tokens.")
    public ApiResponse<Page<AdminAccountService.Account>> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.list(actor.userId(), page, size));
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @Operation(
            summary = "Create internal account",
            description = "Create PRINT_STAFF or DESIGNER plus CUSTOMER. A verification email is queued; no "
                    + "storefront is created.")
    public ApiResponse<AdminAccountService.Account> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody InternalRequest request) {
        return ApiResponse.created("Internal account created.", service.createInternal(actor.userId(),
                request.email(), request.password(), request.fullName(), request.phone(), request.role()));
    }

    @PutMapping("/{id}/roles/{role}")
    @Operation(
            summary = "Grant or revoke role",
            description = "Audited role update with session revocation and last-active-Admin protection. "
                    + "Seller requires paid active storefront eligibility.")
    public ApiResponse<AdminAccountService.Account> role(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable int id, @PathVariable RoleCode role, @Valid @RequestBody RoleRequest request) {
        return ApiResponse.success(service.role(actor.userId(), id, role, request.active(), request.reason()));
    }

    @PatchMapping("/{id}/status")
    @Operation(
            summary = "Lock, ban or reactivate account",
            description = "Revoke sessions and audit the decision. Existing orders, financial records and "
                    + "cases are preserved; no automatic refund.")
    public ApiResponse<AdminAccountService.Account> status(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable int id, @Valid @RequestBody StatusRequest request) {
        return ApiResponse.success(service.status(actor.userId(), id, request.status(), request.reason()));
    }

    @PatchMapping("/{id}/phone-verification")
    @Operation(
            summary = "Review phone in manual sandbox mode",
            description = "Admin checks the current phone against evidence; this is explicitly a "
                    + "mock/manual verification, not an SMS provider integration.")
    public ApiResponse<AdminAccountService.Account> phone(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable int id, @Valid @RequestBody PhoneRequest request) {
        return ApiResponse.success(service.reviewPhone(actor.userId(), id, request.phone(), request.approved(),
                request.evidenceReference(), request.reason()));
    }

    public record InternalRequest(@NotBlank @Email @Size(max=320) String email, @NotBlank String password,
                                  @NotBlank @Size(max=255) String fullName, String phone, @NotNull RoleCode role) {}
    public record RoleRequest(@NotNull Boolean active, @NotBlank @Size(max=1000) String reason) {}
    public record StatusRequest(@NotNull AccountStatus status, @NotBlank @Size(max=1000) String reason) {}
    public record PhoneRequest(@NotBlank String phone, @NotNull Boolean approved,
                               @NotBlank @Size(max=500) String evidenceReference, @NotBlank String reason) {}
}
