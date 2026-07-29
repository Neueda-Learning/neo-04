package com.neobank.module.repository;

import com.neobank.module.model.ScreeningRecord;
import java.util.List;
import org.springframework.data.repository.CrudRepository;

public interface CasesRepository extends CrudRepository<ScreeningRecord, Long> {

    /**
     * Search by application ID. Used when the query looks like an ID (no spaces, typically).
     */
    List<ScreeningRecord> findByApplicationIdContainingIgnoreCaseOrderByCreatedAtDesc(String applicationId);

    /**
     * Fetch cases by a list of application IDs (resolved from orchestrator name search).
     * Ordered newest first.
     */
    List<ScreeningRecord> findByApplicationIdInOrderByCreatedAtDesc(List<String> applicationIds);
}
