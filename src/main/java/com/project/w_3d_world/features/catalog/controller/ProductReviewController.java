package com.project.w_3d_world.features.catalog.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.catalog.dto.request.ProductReviewRequest;
import com.project.w_3d_world.features.catalog.dto.request.ReviewReplyRequest;
import com.project.w_3d_world.features.catalog.service.ProductReviewService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequiredArgsConstructor
public class ProductReviewController {
    private final ProductReviewService service;
    @PostMapping("/api/products/{productId}/reviews") @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String,Integer>> review(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable int productId,@Valid @RequestBody ProductReviewRequest request){return ApiResponse.success(Map.of("reviewId",service.review(user.userId(),productId,request)));}
    @PutMapping("/api/seller/products/{productId}/reviews/{reviewId}/reply") @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<Void> reply(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable int productId,@PathVariable int reviewId,@Valid @RequestBody ReviewReplyRequest request){service.reply(user.userId(),productId,reviewId,request.reply());return ApiResponse.success(null);}
}
