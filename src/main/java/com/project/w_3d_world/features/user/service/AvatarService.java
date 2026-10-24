package com.project.w_3d_world.features.user.service;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.storage.CloudinaryStorage;
import com.project.w_3d_world.features.auth.dto.response.UserResponse;
import com.project.w_3d_world.features.auth.service.CapabilityService;
import com.project.w_3d_world.features.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class AvatarService {

    private final AvatarImageValidator validator;
    private final UserRepository users;
    private final CloudinaryStorage storage;
    private final CapabilityService capabilities;

    @Transactional
    public UserResponse upload(int userId, MultipartFile file) {
        byte[] image = validator.validateAndNormalize(file);
        var user = users.lockById(userId)
                .filter(value -> value.isActive())
                .orElseThrow(ApiException::invalidCredentials);
        String previousId = user.getAvatarPublicId();
        var uploaded = storage.uploadAvatar(userId, image, "image/png");

        // A failed DB commit must not replace the profile URL or leave the new upload behind.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                String toDelete = status == STATUS_COMMITTED ? previousId : uploaded.publicId();
                if (toDelete != null) {
                    try {
                        storage.delete(toDelete);
                    } catch (RuntimeException exception) {
                        log.warn("Unable to clean up avatar asset for user {}", userId);
                    }
                }
            }
        });
        user.changeAvatar(uploaded.url(), uploaded.publicId());
        users.flush();
        return UserResponse.from(user, capabilities.getCapabilities(user));
    }

}
