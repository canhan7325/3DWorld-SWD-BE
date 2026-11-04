package com.project.w_3d_world.features.catalog.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.util.JsonValues;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.auth.service.AccountAuthorization;
import com.project.w_3d_world.features.catalog.dto.request.ProductReviewRequest;
import com.project.w_3d_world.features.catalog.repository.ProductRepository;
import com.project.w_3d_world.features.catalog.repository.ProductReviewRepository;
import com.project.w_3d_world.features.catalog.service.ProductReviewService;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class ProductReviewServiceImpl implements ProductReviewService {
    private final ProductRepository products;
    private final ProductReviewRepository reviews;
    private final StorefrontRepository stores;
    private final AuditLogRepository audit;
    private final AccountAuthorization authorization;

    @Override @Transactional public int review(int buyer,int product,ProductReviewRequest request){
        if(request.rating()<1||request.rating()>5||request.orderItemId()==null)throw ApiException.invalidRequest("Rating must be 1–5 and orderItemId is required.");
        var p=products.lockById(product).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Product does not exist."));
        var shop=stores.findById(p.getStorefrontId()).orElseThrow();
        if(shop.getOwnerUserId().equals(buyer))throw new ApiException(HttpStatus.FORBIDDEN,"Seller cannot review their own product.");
        var purchase=reviews.purchase(buyer,product,request.orderItemId());
        if(purchase.isEmpty())throw new ApiException(HttpStatus.FORBIDDEN,"Only the buyer of a delivered product can review it.");
        if(reviews.exists(request.orderItemId()))throw new ApiException(HttpStatus.CONFLICT,"This purchased item has already been reviewed.");
        int order=((Number)purchase.getFirst().get("physical_order_id")).intValue();
        int id=reviews.create(buyer,product,order,request.orderItemId(),request.rating(),request.comment());
        reviews.refreshRating(product);
        return id;
    }
    @Override @Transactional public void reply(int seller,int product,int review,String reply){
        authorization.requireRole(seller,RoleCode.SELLER);
        if(reply==null||reply.isBlank()||reply.length()>5000)throw ApiException.invalidRequest("Reply is required, at most 5000 characters.");
        var p=products.lockById(product).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Product does not exist."));
        if(stores.findByOwnerUserId(seller).filter(s->s.getId().equals(p.getStorefrontId())).isEmpty())throw new ApiException(HttpStatus.FORBIDDEN,"Only the product's shop owner may reply.");
        if(!reviews.published(product,review))throw new ApiException(HttpStatus.NOT_FOUND,"Published review does not exist for this product.");
        audit.save(new AuditLog(seller,"PRODUCT_REVIEW_REPLIED","REVIEW",review,"Seller response",JsonValues.write(Map.of("reply",reply.trim(),"productId",product))));
    }
}
