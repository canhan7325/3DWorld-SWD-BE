package com.project.w_3d_world.features.auth.service;

import com.project.w_3d_world.features.auth.enums.TokenPurpose;
import com.project.w_3d_world.features.user.entity.User;

public interface EmailTokenService {
    void createVerification(User user);

    void requestLink(String email, TokenPurpose purpose);

    void verifyEmail(String email, String token);

    void resetPassword(String email, String token, String newPassword);
}
