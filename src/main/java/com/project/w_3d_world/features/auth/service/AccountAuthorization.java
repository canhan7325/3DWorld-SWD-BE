package com.project.w_3d_world.features.auth.service;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.auth.entity.UserRole;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.user.entity.User;
import com.project.w_3d_world.features.user.repository.UserRepository;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountAuthorization {
    private final UserRepository users;

    @Transactional(readOnly = true)
    public User requireRole(int actorId, RoleCode... allowed) {
        User user = users.findWithRolesById(actorId).filter(User::isActive)
                .orElseThrow(ApiException::invalidCredentials);
        boolean permitted = user.isEmailVerified() && user.getUserRoles().stream()
                .filter(UserRole::isActive)
                .anyMatch(role -> Arrays.asList(allowed).contains(role.getRole().getCode()));
        if (!permitted) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Permission denied.");
        }
        return user;
    }
}
