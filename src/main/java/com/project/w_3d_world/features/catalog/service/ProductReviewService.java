package com.project.w_3d_world.features.catalog.service;
import com.project.w_3d_world.features.catalog.dto.request.ProductReviewRequest;
public interface ProductReviewService {
    int review(int buyer,int product,ProductReviewRequest request);
    void reply(int seller,int product,int review,String reply);
}
