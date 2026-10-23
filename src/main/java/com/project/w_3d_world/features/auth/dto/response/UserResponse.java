package com.project.w_3d_world.features.auth.dto.response;

import com.project.w_3d_world.features.auth.entity.UserRole;
import com.project.w_3d_world.features.auth.enums.Capability;
import com.project.w_3d_world.features.user.entity.User;
import java.time.LocalDate;
import java.util.Set;
import java.util.TreeSet;

public record UserResponse(
        int userId,
        String email,
        String fullName,
        String avatarUrl,
        LocalDate dateOfBirth,
        String phone,
        boolean emailVerified,
        Set<String> roles,
        Set<Capability> capabilities) {
    public static UserResponse from(User user, Set<Capability> capabilities) {
        Set<String> roles = new TreeSet<>();
        user.getUserRoles().stream()
                .filter(UserRole::isActive)
                .map(grant -> grant.getRole().getCode().name())
                .forEach(roles::add);

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getDateOfBirth(),
                user.getPhone(),
                user.isEmailVerified(),
                roles,
                capabilities);
    }
}
