package com.project.w_3d_world.features.payment.repository;

import com.project.w_3d_world.features.payment.entity.PaymentAttempt;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentAttempt> findByExternalReferenceAndProviderName(String reference, String provider);

    List<PaymentAttempt> findByAttemptStatusAndProviderName(String status, String provider);

    Optional<PaymentAttempt> findByTransactionIdAndProviderName(Integer transactionId, String provider);
    Optional<PaymentAttempt> findFirstByTransactionIdAndProviderNameOrderByAttemptNumberDesc(
            Integer transactionId, String provider);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PaymentAttempt a where a.id = :id")
    Optional<PaymentAttempt> lockById(@Param("id") int id);
}
