package com.project.w_3d_world.features.printing.repository;

import com.project.w_3d_world.features.printing.entity.PrintRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.time.Instant;
import java.util.List;

public interface PrintRequestRepository extends Repository<PrintRequest, Integer> {
    PrintRequest saveAndFlush(PrintRequest request);

    Optional<PrintRequest> findById(Integer printRequestId);

    @Query("select r.printRequestId from PrintRequest r where r.cancelledAt is null "
            + "and r.printRequestId > :afterId "
            + "and r.lockedAt is null and r.status in ('SUBMITTED', 'REQUIRES_REVISION', 'QUOTED') "
            + "and exists (select q.printQuoteId from PrintQuote q where q.printRequestId = r.printRequestId "
            + "and q.versionNumber = 1 and q.expiresAt <= :now) "
            + "and not exists (select accepted.printQuoteId from PrintQuote accepted "
            + "where accepted.printRequestId = r.printRequestId and accepted.quoteStatus = 'ACCEPTED') "
            + "order by r.printRequestId")
    List<Integer> findUnacceptedQuoteExpirationCandidates(
            @Param("now") Instant now, @Param("afterId") Integer afterId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from PrintRequest request where request.printRequestId = :requestId")
    Optional<PrintRequest> findByIdForUpdate(@Param("requestId") Integer requestId);

    Page<PrintRequest> findByCustomerUserId(Integer customerUserId, Pageable pageable);

    Optional<PrintRequest> findByPrintRequestIdAndCustomerUserId(
            Integer printRequestId, Integer customerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from PrintRequest request where request.printRequestId = :requestId "
            + "and request.customerUserId = :customerId")
    Optional<PrintRequest> findOwnedByIdForUpdate(
            @Param("requestId") Integer requestId, @Param("customerId") Integer customerId);
}
