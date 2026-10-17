package com.project.w_3d_world.features.payment.repository;

import com.project.w_3d_world.features.payment.entity.PaymentAttempt;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Integer> {
    Optional<PaymentAttempt> findByExternalReferenceAndProviderName(String reference, String provider);
}
