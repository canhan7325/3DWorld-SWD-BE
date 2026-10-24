package com.project.w_3d_world.features.catalog.service;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.storage.CloudinaryStorage;
import com.project.w_3d_world.common.util.JsonValues;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.auth.service.AccountAuthorization;
import com.project.w_3d_world.features.catalog.dto.*;
import com.project.w_3d_world.features.catalog.dto.SellerProductDtos.*;
import com.project.w_3d_world.features.catalog.entity.*;
import com.project.w_3d_world.features.catalog.repository.*;
import com.project.w_3d_world.features.storefront.entity.Storefront;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import com.project.w_3d_world.features.user.service.AvatarImageValidator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerProductService {
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final ProductMediaRepository media;
    private final StorefrontRepository stores;
    private final AccountAuthorization authorization;
    private final AuditLogRepository audit;
    private final CloudinaryStorage storage;
    private final AvatarImageValidator images;

    public MarketplacePage<ProductView> list(int userId, int page, int size) {
        var store = ownedStore(userId, false);
        return page(products.findByStorefrontId(store.getId(), MarketplacePage.pageable(page, size)));
    }

    public ProductView get(int userId, int id) {
        return view(owned(userId, id, false, false));
    }

    @Transactional
    public ProductView create(int userId, ProductRequest request) {
        var store = ownedStore(userId, true);
        Product product = new Product(store.getId(), userId, request.title().trim());
        product.edit(request.title().trim(), request.description(), request.category().trim(),
                request.specifications(), request.shippingClass());
        products.saveAndFlush(product);
        if (request.variants() != null) {
            for (var variant : request.variants()) addVariant(product, variant);
        }
        audit(userId, product, "PRODUCT_CREATED", "Draft created");
        return view(product);
    }

    @Transactional
    public ProductView update(int userId, int id, ProductPatch request) {
        Product product = owned(userId, id, true, true);
        if ((request.title() != null && request.title().isBlank())
                || (request.category() != null && request.category().isBlank())) {
            throw ApiException.invalidRequest("Title and category cannot be blank.");
        }
        product.edit(request.title() == null ? null : request.title().trim(), request.description(),
                request.category() == null ? null : request.category().trim(), request.specifications(), request.shippingClass());
        invalidate(product);
        audit(userId, product, "PRODUCT_UPDATED", "Content changed; resubmission required");
        return view(product);
    }

    @Transactional
    public ProductView addVariant(int userId, int id, CreateVariantRequest request) {
        Product product = owned(userId, id, true, true);
        addVariant(product, request);
        invalidate(product);
        audit(userId, product, "VARIANT_CREATED", "Variant created");
        return view(product);
    }

    private void addVariant(Product product, CreateVariantRequest request) {
        uniqueSku(request.sku().trim(), null);
        var variant = new ProductVariant(product.getId(), request.sku().trim(), request.price(), request.stock());
        variant.edit(null, request.attributes() == null ? null : JsonValues.write(request.attributes()), null,
                request.active(), request.fragile(), request.lowStockThreshold(), request.packingGroup(),
                request.weightGrams(), request.lengthCm(), request.widthCm(), request.heightCm());
        variants.saveAndFlush(variant);
    }

    @Transactional
    public ProductView updateVariant(int userId, int id, int variantId, VariantPatch request) {
        Product product = owned(userId, id, true, true);
        var variant = ownedVariant(id, variantId);
        if (request.sku() != null) {
            uniqueSku(request.sku().trim(), variantId);
        }
        variant.edit(request.sku() == null ? null : request.sku().trim(),
                request.attributes() == null ? null : JsonValues.write(request.attributes()), request.price(),
                request.active(), request.fragile(), request.lowStockThreshold(), request.packingGroup(),
                request.weightGrams(), request.lengthCm(), request.widthCm(), request.heightCm());
        invalidate(product);
        audit(userId, product, "VARIANT_UPDATED", "Variant content changed");
        return view(product);
    }

    @Transactional
    public ProductView stock(int userId, int id, int variantId, StockRequest request) {
        Product product = owned(userId, id, true, true);
        var variant = ownedVariant(id, variantId);
        int before = variant.getStockQuantity();
        variant.changeStock(request.stock());
        audit(userId, product, "VARIANT_STOCK_UPDATED", request.reason() + " [" + before + " -> " + request.stock() + "]");
        return view(product);
    }

    @Transactional
    public ProductView removeVariant(int userId, int id, int variantId) {
        Product product = owned(userId, id, true, true);
        var variant = ownedVariant(id, variantId);
        if (variant.getReservedQuantity() != 0) {
            throw new ApiException(HttpStatus.CONFLICT, "A reserved variant cannot be removed.");
        }
        // Soft removal preserves order/cart/package foreign keys and historical snapshots.
        variant.deactivate();
        invalidate(product);
        audit(userId, product, "VARIANT_DEACTIVATED", "Variant removed from sale");
        return view(product);
    }

    @Transactional
    public ProductView addMedia(int userId, int id, Integer variantId, int sortOrder, MultipartFile file) {
        Product product = owned(userId, id, true, true);
        if (sortOrder < 0) {
            throw ApiException.invalidRequest("Media sortOrder cannot be negative.");
        }
        if (variantId != null) {
            ownedVariant(id, variantId);
        }
        if (media.findByProductIdOrderBySortOrderAscIdAsc(id).size() >= 20) {
            throw ApiException.invalidRequest("A product supports at most 20 images.");
        }
        byte[] normalized = images.validateAndNormalize(file);
        var uploaded = storage.uploadProduct(id, normalized, "image/png");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try { storage.delete(uploaded.publicId()); } catch (RuntimeException ignored) { /* Best effort cleanup. */ }
                }
            }
        });
        media.saveAndFlush(new ProductMedia(id, variantId, uploaded.url(), sortOrder));
        invalidate(product);
        audit(userId, product, "PRODUCT_MEDIA_ADDED", "Product image uploaded");
        return view(product);
    }

    @Transactional
    public ProductView removeMedia(int userId, int id, int mediaId) {
        Product product = owned(userId, id, true, true);
        var item = media.findById(mediaId).filter(value -> value.getProductId().equals(id))
                .orElseThrow(() -> missing("Product image"));
        media.delete(item);
        media.flush();
        invalidate(product);
        audit(userId, product, "PRODUCT_MEDIA_REMOVED", "Product image detached");
        return view(product);
    }

    @Transactional
    public ProductView action(int userId, int id, String action) {
        Product product = owned(userId, id, true, "submit".equals(action));
        switch (action) {
            case "submit" -> {
                if (!Set.of("DRAFT", "REJECTED", "UNPUBLISHED").contains(product.getStatus())) {
                    throw ApiException.invalidRequest("Only draft, rejected or unpublished products can be submitted.");
                }
                validateReady(product);
                product.changeStatus("PENDING_REVIEW", "Awaiting manual moderation");
            }
            case "unpublish" -> {
                if (!Set.of("PUBLISHED", "PENDING_REVIEW", "UNPUBLISHED").contains(product.getStatus())) {
                    throw ApiException.invalidRequest("Product cannot be unpublished in its current state.");
                }
                product.changeStatus("UNPUBLISHED", "Unpublished by seller");
            }
            case "archive" -> product.changeStatus("ARCHIVED", "Archived by seller");
            default -> throw ApiException.invalidRequest("Unsupported product action.");
        }
        audit(userId, product, "PRODUCT_" + action.toUpperCase(Locale.ROOT), product.getModerationReason());
        return view(product);
    }

    @Transactional
    public ProductView moderate(int reviewerId, int id, DecisionRequest request, boolean approved) {
        authorization.requireRole(reviewerId, RoleCode.ADMIN);
        var product = products.lockById(id).orElseThrow(() -> missing("Product"));
        var store = stores.findById(product.getStorefrontId()).orElseThrow();
        if (store.getOwnerUserId().equals(reviewerId)) {
            throw ApiException.invalidRequest("You cannot moderate your own product.");
        }
        if (!"PENDING_REVIEW".equals(product.getStatus()) || !revision(product).equals(request.revision())) {
            throw new ApiException(HttpStatus.CONFLICT, "Product changed. Refresh the moderation detail.");
        }
        if (approved) {
            if (!store.canPublish()) {
                throw ApiException.invalidRequest("Storefront is not eligible to publish.");
            }
            validateReady(product);
        }
        product.changeStatus(approved ? "PUBLISHED" : "REJECTED", request.reason());
        audit(reviewerId, product, approved ? "PRODUCT_APPROVED" : "PRODUCT_REJECTED", request.reason());
        return view(product);
    }

    public ProductView moderationDetail(int reviewerId, int id) {
        authorization.requireRole(reviewerId, RoleCode.ADMIN);
        return view(products.findById(id).orElseThrow(() -> missing("Product")));
    }

    public MarketplacePage<ProductView> pending(int reviewerId, int page, int size) {
        authorization.requireRole(reviewerId, RoleCode.ADMIN);
        return page(products.findByStatus("PENDING_REVIEW", MarketplacePage.pageable(page, size)));
    }

    private Storefront ownedStore(int userId, boolean trading) {
        authorization.requireRole(userId, RoleCode.SELLER);
        var store = stores.findByOwnerUserId(userId).orElseThrow(() -> missing("Storefront"));
        if (trading && !store.canPublish()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "An active verified storefront subscription is required.");
        }
        return store;
    }

    private Product owned(int userId, int id, boolean lock, boolean trading) {
        var store = ownedStore(userId, trading);
        Product product = (lock ? products.lockById(id) : products.findById(id))
                .filter(value -> value.getStorefrontId().equals(store.getId())).orElseThrow(() -> missing("Product"));
        if (trading && "ARCHIVED".equals(product.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Archived products cannot be edited.");
        }
        return product;
    }

    private ProductVariant ownedVariant(int productId, int variantId) {
        return variants.findByIdForUpdate(variantId).filter(value -> value.getProductId().equals(productId))
                .orElseThrow(() -> missing("Variant"));
    }

    private void uniqueSku(String sku, Integer exceptId) {
        if (sku.isBlank()) {
            throw ApiException.invalidRequest("SKU is required.");
        }
        if (variants.findBySku(sku).filter(value -> !Objects.equals(value.getId(), exceptId)).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "SKU already exists.");
        }
    }

    private void validateReady(Product product) {
        boolean valid = variants.findByProductIdOrderByIdAsc(product.getId()).stream()
                .anyMatch(value -> value.isActive() && "ACTIVE".equals(value.getStatus()));
        if (product.getTitle().isBlank() || product.getCategory() == null || product.getCategory().isBlank()
                || !valid || media.findByProductIdOrderBySortOrderAscIdAsc(product.getId()).isEmpty()) {
            throw ApiException.invalidRequest("Submission requires a title, category, image and active variant.");
        }
    }

    private void invalidate(Product product) {
        product.changeStatus("DRAFT", "Content changed; submit for moderation again");
    }

    private MarketplacePage<ProductView> page(Page<Product> page) {
        var ids = page.getContent().stream().map(Product::getId).toList();
        if (ids.isEmpty()) {
            return MarketplacePage.from(page.map(this::view));
        }
        var imagesByProduct = media.findByProductIdInOrderBySortOrderAscIdAsc(ids).stream()
                .collect(Collectors.groupingBy(ProductMedia::getProductId));
        var variantsByProduct = variants.findByProductIdInOrderByIdAsc(ids).stream()
                .collect(Collectors.groupingBy(ProductVariant::getProductId));
        return MarketplacePage.from(page.map(product -> view(product,
                imagesByProduct.getOrDefault(product.getId(), List.of()),
                variantsByProduct.getOrDefault(product.getId(), List.of()))));
    }

    private ProductView view(Product product) {
        return view(product, media.findByProductIdOrderBySortOrderAscIdAsc(product.getId()),
                variants.findByProductIdOrderByIdAsc(product.getId()));
    }

    private ProductView view(Product product, List<ProductMedia> media, List<ProductVariant> variants) {
        return new ProductView(product.getId(), product.getStorefrontId(), product.getTitle(), product.getDescription(),
                product.getCategory(), product.getSpecifications(), product.getShippingClass(), product.getStatus(),
                product.getModerationReason(), revision(product), product.getUpdatedAt(),
                media.stream().map(MediaResponse::from).toList(), variants.stream().map(VariantView::from).toList());
    }

    private String revision(Product product) {
        try {
            String value = product.getId() + "|" + product.getStatus() + "|" + product.getUpdatedAt();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void audit(int actor, Product product, String action, String reason) {
        audit.save(new AuditLog(actor, action, "PRODUCT", product.getId(), reason,
                JsonValues.write(Map.of("revision", revision(product), "provider", "MANUAL_REVIEW"))));
    }

    private ApiException missing(String name) {
        return new ApiException(HttpStatus.NOT_FOUND, name + " does not exist.");
    }
}
