package com.project.w_3d_world.features.catalog.repository;

import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.catalog.entity.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Integer> {
    List<Product> findByStorefrontId(Integer storefrontId);

    @Query("""
            select p from Product p
            where p.status = 'PUBLISHED'
              and exists (select s.id from Storefront s where s.id = p.storefrontId
                          and s.status = com.project.w_3d_world.features.storefront.enums.StorefrontStatus.ACTIVE
                          and s.verificationStatus =
                              com.project.w_3d_world.features.storefront.enums.VerificationStatus.VERIFIED
                          and s.expiresAt > CURRENT_TIMESTAMP
                          and s.owner.accountStatus =
                              com.project.w_3d_world.features.user.enums.AccountStatus.ACTIVE
                          and s.owner.emailVerified=true and s.owner.phoneVerified=true
                          and exists (select ur.id from UserRole ur where ur.user.id=s.ownerUserId
                                      and ur.approvalStatus = com.project.w_3d_world.features.auth.enums.ApprovalStatus.ACTIVE
                                      and ur.role.code = RoleCode.SELLER))
              and (cast(:keyword as string) is null
                   or lower(p.title) like lower(concat('%', cast(:keyword as string), '%')))
              and (cast(:category as string) is null
                   or lower(p.category) = lower(cast(:category as string)))
            """)
    Page<Product> searchPublic(
            @Param("keyword") String keyword,
            @Param("category") String category,
            Pageable pageable);

    Optional<Product> findByIdAndStatus(Integer id, String status);
}
