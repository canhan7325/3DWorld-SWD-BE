package com.project.w_3d_world.features.catalog.dto.response;

import com.project.w_3d_world.features.catalog.dto.response.ReviewReplyResponse;
import java.time.Instant;

public record ReviewResponse(Integer id, int rating, String comment, String images,
                                 Instant createdAt, ReviewReplyResponse sellerReply) {
        public ReviewResponse(Integer id,int rating,String comment,String images,Instant createdAt){this(id,rating,comment,images,createdAt,null);}
    }
