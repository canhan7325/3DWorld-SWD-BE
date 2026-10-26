package com.project.w_3d_world.features.printing.repository;

import com.project.w_3d_world.features.printing.entity.PrintQuoteItem;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface PrintQuoteItemRepository extends Repository<PrintQuoteItem, Integer> {
    PrintQuoteItem saveAndFlush(PrintQuoteItem item);

    List<PrintQuoteItem> findByPrintQuoteIdOrderByPrintQuoteItemIdAsc(Integer printQuoteId);
}
