package com.project.w_3d_world.features.printing.repository;

import com.project.w_3d_world.features.printing.entity.PrintRequestSpecVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface PrintRequestSpecVersionRepository extends Repository<PrintRequestSpecVersion, Integer> {
    PrintRequestSpecVersion saveAndFlush(PrintRequestSpecVersion version);

    Optional<PrintRequestSpecVersion> findByPrintRequestIdAndVersion(Integer printRequestId, Integer version);

    List<PrintRequestSpecVersion> findByPrintRequestIdOrderByVersionDesc(Integer printRequestId);
}
