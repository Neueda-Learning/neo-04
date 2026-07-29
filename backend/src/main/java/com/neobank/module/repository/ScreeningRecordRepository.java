package com.neobank.module.repository;

import com.neobank.module.model.ScreeningRecord;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * One row per {@code applicationId} — {@link #existsByApplicationId} is what makes UC-00's
 * idempotency criterion cheap to check before ever attempting a save.
 */
public interface ScreeningRecordRepository extends JpaRepository<ScreeningRecord, Long> {

    boolean existsByApplicationId(String applicationId);

    Optional<ScreeningRecord> findByApplicationId(String applicationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select row from ScreeningRecord row where row.applicationId = :applicationId")
    Optional<ScreeningRecord> findByApplicationIdForUpdate(@Param("applicationId") String applicationId);

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

    /**
     * uc-01: Search by application ID (partial match, case-insensitive).
     * Used for case board ID search.
     */
    List<ScreeningRecord> findByApplicationIdContainingIgnoreCaseOrderByCreatedAtDesc(String applicationId);

    /**
     * uc-01: Fetch cases by a list of application IDs (resolved from orchestrator name search).
     * Ordered newest first.
     */
    List<ScreeningRecord> findByApplicationIdInOrderByCreatedAtDesc(List<String> applicationIds);

    @Query("""
            select s from ScreeningRecord s
            where s.finalOutcome = 'REVIEW' and s.resolution is null
            order by case when s.claimedBy is null then 0 else 1 end, s.createdAt asc, s.id asc
            """)
    List<ScreeningRecord> findOpenAnalystQueue(Pageable pageable);

    List<ScreeningRecord> findByFinalOutcomeAndResolutionIsNullOrderByCreatedAtAscIdAsc(
            String finalOutcome, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ScreeningRecord s set s.claimedBy = :analyst, s.claimedAt = :claimedAt
            where s.applicationId = :applicationId and s.finalOutcome = 'REVIEW'
              and s.resolution is null and s.claimedBy is null
            """)
    int claimIfAvailable(@Param("applicationId") String applicationId,
                         @Param("analyst") String analyst,
                         @Param("claimedAt") java.time.Instant claimedAt);

}
