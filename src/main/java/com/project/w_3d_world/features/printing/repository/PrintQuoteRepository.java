package com.project.w_3d_world.features.printing.repository;

import com.project.w_3d_world.features.printing.entity.PrintQuote;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface PrintQuoteRepository extends Repository<PrintQuote, Integer> {
    PrintQuote saveAndFlush(PrintQuote quote);

    Optional<PrintQuote> findById(Integer printQuoteId);

    Optional<PrintQuote> findByPrintQuoteIdAndPrintRequestId(
            Integer printQuoteId, Integer printRequestId);

    List<PrintQuote> findByPrintRequestIdOrderByVersionNumberAsc(Integer printRequestId);

    List<PrintQuote> findByPrintRequestIdOrderByVersionNumberDesc(Integer printRequestId);
}
