package com.project.w_3d_world.features.payment.repository;

import com.project.w_3d_world.features.payment.entity.PaymentAttempt;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Integer> {
    Optional<PaymentAttempt> findByExternalReferenceAndProviderName(String reference, String provider);

    List<PaymentAttempt> findByAttemptStatusAndProviderName(String status, String provider);

    Optional<PaymentAttempt> findByTransactionIdAndProviderName(Integer transactionId, String provider);
}
