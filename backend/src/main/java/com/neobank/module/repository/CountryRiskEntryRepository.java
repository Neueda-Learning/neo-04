package com.neobank.module.repository;

import com.neobank.module.model.CountryRiskEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryRiskEntryRepository extends JpaRepository<CountryRiskEntry, Long> {

    List<CountryRiskEntry> findAllByVersionOrderByIdAsc(Integer version);

    void deleteByVersion(Integer version);
}