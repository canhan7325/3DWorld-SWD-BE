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

    @Query("select r from PrintRequest r where r.status = 'SUBMITTED' "
            + "and r.submittedAt is not null and r.cancelledAt is null "
            + "and r.lockedAt is null and r.currentQuoteId is null "
            + "and r.currentFileVersion is not null and r.currentSpecVersion is not null "
            + "and exists (select f.fileVersionId from PrintRequestFileVersion f "
            + "where f.printRequestId = r.printRequestId and f.version = r.currentFileVersion) "
            + "and exists (select s.specVersionId from PrintRequestSpecVersion s "
            + "where s.printRequestId = r.printRequestId and s.version = r.currentSpecVersion) "
            + "and (:assignment = 'ALL' "
            + "or (:assignment = 'UNASSIGNED' and r.handledByUserId is null) "
            + "or (:assignment = 'ASSIGNED' and r.handledByUserId is not null) "
            + "or (:assignment = 'MINE' and r.handledByUserId = :staffId))")
    Page<PrintRequest> findStaffReviewQueue(@Param("assignment") String assignment,
            @Param("staffId") Integer staffId, Pageable pageable);

    @Query("select r from PrintRequest r where r.status = 'IN_QUEUE' "
            + "and r.lockedAt is not null and r.cancelledAt is null "
            + "and r.currentQuoteId is not null "
            + "and exists (select q.printQuoteId from PrintQuote q "
            + "where q.printQuoteId = r.currentQuoteId and q.printRequestId = r.printRequestId "
            + "and q.quoteStatus = 'ACCEPTED') "
            + "and exists (select h.printStatusHistoryId from PrintRequestStatusHistory h "
            + "where h.printRequestId = r.printRequestId and h.toStatus = 'IN_QUEUE' "
            + "and h.sourceEventId like concat('POD_PAYMENT_VERIFIED:', str(r.printRequestId), ':tx:%')) "
            + "and exists (select anyItem.printRequestItemId from PrintRequestItem anyItem "
            + "where anyItem.printRequestId = r.printRequestId) "
            + "and (:itemStatus = 'ALL' or exists (select i.printRequestItemId from PrintRequestItem i "
            + "where i.printRequestId = r.printRequestId and i.productionStatus = :itemStatus))")
    Page<PrintRequest> findProductionBoard(@Param("itemStatus") String itemStatus, Pageable pageable);

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
