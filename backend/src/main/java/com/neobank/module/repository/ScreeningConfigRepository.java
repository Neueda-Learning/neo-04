package com.neobank.module.repository;

import com.neobank.module.model.ScreeningConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ScreeningConfigRepository extends JpaRepository<ScreeningConfig, Integer> {

    List<ScreeningConfig> findAllByOrderByVersionDesc();

    @Query("select sc from ScreeningConfig sc where sc.currentVersion = 1")
    Optional<ScreeningConfig> findCurrent();

    @Query("select coalesce(max(sc.version), 0) from ScreeningConfig sc")
    Integer findMaxVersion();

    @Modifying
    @Query("update ScreeningConfig sc set sc.currentVersion = 0 where sc.currentVersion = 1")
    int clearCurrentVersion();
}