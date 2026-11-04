package com.project.w_3d_world.features.catalog.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record ReviewReplyRequest(@NotBlank @Size(max=5000) String reply) {}
