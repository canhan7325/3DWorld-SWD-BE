package com.project.w_3d_world.features.auth.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.features.auth.dto.request.LoginRequest;
import com.project.w_3d_world.features.auth.dto.request.RegisterRequest;
import com.project.w_3d_world.features.auth.dto.response.LoginResponse;
import com.project.w_3d_world.features.auth.dto.response.UserResponse;
import com.project.w_3d_world.features.auth.entity.UserRole;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.auth.repository.RoleRepository;
import com.project.w_3d_world.features.auth.repository.UserRoleRepository;
import com.project.w_3d_world.features.auth.service.AuthService;
import com.project.w_3d_world.features.auth.service.EmailTokenService;
import com.project.w_3d_world.features.user.entity.User;
import com.project.w_3d_world.features.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailTokenService emailTokenService;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.findByNormalizedEmail(email).isPresent()) {
            throw ApiException.emailAlreadyExists();
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.invalidRequest("Password must not exceed 72 UTF-8 bytes.");
        }

        var customerRole =
                roleRepository
                        .findByCode(RoleCode.CUSTOMER)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "The CUSTOMER role is missing from the database"));
        User user =
                new User(
                        email,
                        passwordEncoder.encode(request.password()),
                        request.fullName().trim(),
                        request.phone());
        userRepository.saveAndFlush(user);

        UserRole userRole = new UserRole(user, customerRole);
        userRoleRepository.saveAndFlush(userRole);
        user.getUserRoles().add(userRole);
        emailTokenService.createVerification(user);
        return UserResponse.from(user);
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.invalidCredentials();
        }
        User user =
                userRepository
                        .findByNormalizedEmail(normalizeEmail(request.email()))
                        .orElseThrow(ApiException::invalidCredentials);
        if (!user.isActive()
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.invalidCredentials();
        }

        UserResponse response = UserResponse.from(user);
        var token =
                jwtService.generateToken(
                        response.userId(), response.roles(), response.phone(), response.email());
        return new LoginResponse(token.getTokenValue(), "Bearer", token.getExpiresAt(), response);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse currentUser(int userId) {
        User user =
                userRepository
                        .findWithRolesById(userId)
                        .filter(User::isActive)
                        .orElseThrow(ApiException::invalidCredentials);
        return UserResponse.from(user);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
