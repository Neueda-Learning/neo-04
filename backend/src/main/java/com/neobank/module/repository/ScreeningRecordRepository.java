package com.neobank.module.repository;

import com.neobank.module.model.ScreeningRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreeningRecordRepository extends JpaRepository<ScreeningRecord, Long> {

    long countByConfigVersion(Integer configVersion);
}