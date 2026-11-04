package com.project.w_3d_world.features.catalog;
import com.project.w_3d_world.features.catalog.service.impl.ProductReviewServiceImpl;
import com.project.w_3d_world.features.catalog.repository.ProductRepository;
import com.project.w_3d_world.features.catalog.repository.ProductReviewRepository;
import com.project.w_3d_world.features.catalog.entity.Product;
import com.project.w_3d_world.features.catalog.dto.request.ProductReviewRequest;
import com.project.w_3d_world.features.storefront.entity.Storefront;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.auth.service.AccountAuthorization;
import com.project.w_3d_world.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class ProductReviewServiceTest {
 @Test void purchaseRequiredDuplicateBlockedAndSellerOwnershipEnforced(){
  var products=mock(ProductRepository.class);var reviews=mock(ProductReviewRepository.class);var stores=mock(StorefrontRepository.class);var audit=mock(AuditLogRepository.class);
  var service=new ProductReviewServiceImpl(products,reviews,stores,audit,mock(AccountAuthorization.class));
  var product=new Product(2,9,"Product");when(products.lockById(1)).thenReturn(Optional.of(product));
  var shop=new Storefront(9,"Shop",null);ReflectionTestUtils.setField(shop,"id",2);when(stores.findById(2)).thenReturn(Optional.of(shop));
  var req=new ProductReviewRequest(3,5,"Good");
  when(reviews.purchase(8,1,3)).thenReturn(List.of());assertThrows(ApiException.class,()->service.review(8,1,req));verify(reviews,never()).refreshRating(anyInt());
  when(reviews.purchase(8,1,3)).thenReturn(List.of(Map.of("physical_order_id",4)));when(reviews.create(8,1,4,3,5,"Good")).thenReturn(7);
  assertEquals(7,service.review(8,1,req));verify(reviews).refreshRating(1);
  when(reviews.exists(3)).thenReturn(true);assertThrows(ApiException.class,()->service.review(8,1,req));
  assertThrows(ApiException.class,()->service.review(8,1,new ProductReviewRequest(3,6,"bad")));
  when(stores.findByOwnerUserId(10)).thenReturn(Optional.empty());assertThrows(ApiException.class,()->service.reply(10,1,7,"Reply"));
  when(stores.findByOwnerUserId(9)).thenReturn(Optional.of(shop));when(reviews.published(1,7)).thenReturn(true);
  service.reply(9,1,7,"Thanks");verify(audit).save(any());
 }
}
