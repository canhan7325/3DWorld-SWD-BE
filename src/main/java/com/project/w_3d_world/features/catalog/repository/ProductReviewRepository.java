package com.project.w_3d_world.features.catalog.repository;

import java.util.List;
import java.util.Map;

public interface ProductReviewRepository {
    List<Map<String,Object>> purchase(int buyer,int product,int item);
    boolean exists(int item);
    int create(int buyer,int product,int order,int item,int rating,String comment);
    void refreshRating(int product);
    boolean published(int product,int review);
}
