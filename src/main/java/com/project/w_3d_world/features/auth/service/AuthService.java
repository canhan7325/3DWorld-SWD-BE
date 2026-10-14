package com.project.w_3d_world.features.auth.service;

import com.project.w_3d_world.features.auth.dto.request.LoginRequest;
import com.project.w_3d_world.features.auth.dto.request.RegisterRequest;
import com.project.w_3d_world.features.auth.dto.response.LoginResponse;
import com.project.w_3d_world.features.auth.dto.response.UserResponse;

public interface AuthService {
    UserResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    UserResponse currentUser(int userId);
}
