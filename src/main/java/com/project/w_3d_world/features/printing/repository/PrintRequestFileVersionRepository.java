package com.project.w_3d_world.features.printing.repository;

import com.project.w_3d_world.features.printing.entity.PrintRequestFileVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface PrintRequestFileVersionRepository extends Repository<PrintRequestFileVersion, Integer> {
    PrintRequestFileVersion saveAndFlush(PrintRequestFileVersion version);

    Optional<PrintRequestFileVersion> findByPrintRequestIdAndVersion(
            Integer printRequestId, Integer version);

    List<PrintRequestFileVersion> findByPrintRequestIdOrderByVersionDesc(Integer printRequestId);
}
