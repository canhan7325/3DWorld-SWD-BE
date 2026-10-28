package com.project.w_3d_world.features.ai.service;

import com.project.w_3d_world.features.ai.dto.response.AiUploadResponse;
import com.project.w_3d_world.features.ai.model.StagedUpload;
import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AiUploadService {

    AiUploadResponse uploadSourceImage(int userId, MultipartFile file);

    List<AiUploadResponse> listUploads(int userId);

    AuthorizedModelDownload downloadUploadFile(int userId, String uploadId);

    StagedUpload getStagedImage(String uploadId);

    void stageImage(String uploadId, StagedUpload upload);

    void evictStagedImage(String uploadId);
}
