package com.project.w_3d_world.features.auth.dto.response;

import com.project.w_3d_world.features.auth.entity.UserRole;
import com.project.w_3d_world.features.user.entity.User;

import java.util.Set;
import java.util.TreeSet;

public record UserResponse(
        int userId,
        String email,
        String fullName,
        String phone,
        boolean emailVerified,
        Set<String> roles) {
    public static UserResponse from(User user) {
        Set<String> roles = new TreeSet<>();
        user.getUserRoles().stream()
                .filter(UserRole::isActive)
                .map(grant -> grant.getRole().getCode().name())
                .forEach(roles::add);

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.isEmailVerified(),
                roles);
    }
}
