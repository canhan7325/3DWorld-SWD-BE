package com.project.w_3d_world.features.model.service;

import com.project.w_3d_world.features.model.dto.request.CreateModelRequest;
import com.project.w_3d_world.features.model.dto.response.ModelUploadResponse;

import org.springframework.web.multipart.MultipartFile;

public interface ModelUploadService {
    ModelUploadResponse uploadModel(
            Integer authenticatedUserId, CreateModelRequest request, MultipartFile file);
}
