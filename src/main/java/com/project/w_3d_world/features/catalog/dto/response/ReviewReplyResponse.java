package com.project.w_3d_world.features.catalog.dto.response;
import java.time.Instant;
public record ReviewReplyResponse(int sellerId,String reply,Instant createdAt) {}
