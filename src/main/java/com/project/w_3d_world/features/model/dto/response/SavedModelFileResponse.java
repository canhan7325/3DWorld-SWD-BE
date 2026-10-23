package com.project.w_3d_world.features.model.dto.response;

public record SavedModelFileResponse(
        Integer fileId, String fileName, String fileFormat, Long fileSize,
        String downloadPath) {}
