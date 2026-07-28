package com.neobank.module.repository;

import com.neobank.module.model.ScreeningRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * One row per {@code applicationId} — {@link #existsByApplicationId} is what makes UC-00's
 * idempotency criterion cheap to check before ever attempting a save.
 */
public interface ScreeningRecordRepository extends JpaRepository<ScreeningRecord, Long> {

    boolean existsByApplicationId(String applicationId);

    Optional<ScreeningRecord> findByApplicationId(String applicationId);

    /**
     * Newest first, with the {@code id} tiebreak {@code DemoShowcaseRepositoryIT} discovered:
     * MySQL {@code TIMESTAMP} truncates to whole seconds, so several rows written in the same
     * second would otherwise reorder between refreshes.
     */
    List<ScreeningRecord> findAllByOrderByCreatedAtDescIdDesc();

    /**
     * Used by {@code ScreeningConfigService#delete} to refuse deleting a config version that is
     * still pinned by at least one alert's {@code config_version}.
     */
    long countByConfigVersion(Integer configVersion);
}
