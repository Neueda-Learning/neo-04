package com.neobank.module.repository;

import com.neobank.module.model.WatchlistEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchlistEntryRepository extends JpaRepository<WatchlistEntry, Long> {

    List<WatchlistEntry> findAllByVersionOrderByIdAsc(Integer version);

    void deleteByVersion(Integer version);
}