package com.neobank.module.service;

import com.neobank.module.dto.ScreeningConfigCreateRequest;
import com.neobank.module.dto.ScreeningConfigDetailView;
import com.neobank.module.dto.ScreeningConfigSummaryView;
import com.neobank.module.model.CountryRiskEntry;
import com.neobank.module.model.RiskLevel;
import com.neobank.module.model.ScreeningConfig;
import com.neobank.module.model.WatchlistEntry;
import com.neobank.module.repository.CountryRiskEntryRepository;
import com.neobank.module.repository.ScreeningConfigRepository;
import com.neobank.module.repository.ScreeningRecordRepository;
import com.neobank.module.repository.WatchlistEntryRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScreeningConfigService {

    private final ScreeningConfigRepository configs;
    private final WatchlistEntryRepository watchlistEntries;
    private final CountryRiskEntryRepository countryRiskEntries;
    private final ScreeningRecordRepository screeningRecords;

    public ScreeningConfigService(ScreeningConfigRepository configs,
                                  WatchlistEntryRepository watchlistEntries,
                                  CountryRiskEntryRepository countryRiskEntries,
                                  ScreeningRecordRepository screeningRecords) {
        this.configs = configs;
        this.watchlistEntries = watchlistEntries;
        this.countryRiskEntries = countryRiskEntries;
        this.screeningRecords = screeningRecords;
    }

    @Transactional(readOnly = true)
    public List<ScreeningConfigSummaryView> list() {
        return configs.findAllByOrderByVersionDesc().stream()
                .map(ScreeningConfigSummaryView::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public ScreeningConfigDetailView getCurrent() {
                ScreeningConfig current = configs.findCurrent()
                .orElseThrow(() -> new NoSuchElementException(
                        "no screening config version has been activated yet"));
        return toDetail(current);
    }

    @Transactional(readOnly = true)
    public ScreeningConfigDetailView get(Integer version) {
        ScreeningConfig config = configs.findById(version)
                .orElseThrow(() -> unknownVersion(version));
        return toDetail(config);
    }

    @Transactional
    public ScreeningConfigDetailView create(ScreeningConfigCreateRequest request) {
        ensureUniqueCountryCodes(request.countryRiskEntries());

        int nextVersion = configs.findMaxVersion() + 1;
        boolean activate = Boolean.TRUE.equals(request.activate());

        if (activate) {
            configs.clearCurrentVersion();
        }

        ScreeningConfig config = new ScreeningConfig(
                nextVersion,
                request.samplingFrequency(),
                activate,
                request.createdBy().trim());

        configs.save(config);

        List<WatchlistEntry> watchRows = request.watchlistEntries().stream()
                .map(entry -> new WatchlistEntry(
                        nextVersion,
                        entry.listId().trim(),
                        entry.firstName(),
                        entry.lastName(),
                        entry.dateOfBirth(),
                        entry.nationality(),
                        entry.listType(),
                        entry.source()))
                .toList();
        watchlistEntries.saveAll(watchRows);

        List<CountryRiskEntry> riskRows = request.countryRiskEntries().stream()
                .map(entry -> new CountryRiskEntry(
                        nextVersion,
                        normalizeCountryCode(entry.countryCode()),
                        entry.countryName().trim(),
                        entry.riskLevel()))
                .toList();
        countryRiskEntries.saveAll(riskRows);

        return toDetail(config);
    }

    @Transactional
    public ScreeningConfigDetailView activate(Integer version) {
        ScreeningConfig config = configs.findById(version)
                .orElseThrow(() -> unknownVersion(version));

        if (config.isCurrentVersion()) {
            throw new IllegalStateException("version " + version + " is already current");
        }

        configs.clearCurrentVersion();
        config.setCurrentVersion(true);
        configs.save(config);

        return toDetail(config);
    }

    @Transactional
    public void delete(Integer version) {
                if (!configs.existsById(version)) {
                        throw unknownVersion(version);
                }
                throw new IllegalStateException(
                                "screening config versions are immutable and cannot be deleted");
    }

    private ScreeningConfigDetailView toDetail(ScreeningConfig config) {
        List<ScreeningConfigDetailView.WatchlistEntryView> watchlist =
                watchlistEntries.findAllByVersionOrderByIdAsc(config.getVersion()).stream()
                        .map(entry -> new ScreeningConfigDetailView.WatchlistEntryView(
                                entry.getId(),
                                entry.getListId(),
                                entry.getFirstName(),
                                entry.getLastName(),
                                entry.getDateOfBirth(),
                                entry.getNationality(),
                                entry.getListType(),
                                entry.getSource(),
                                entry.getCreatedAt()))
                        .toList();

        List<ScreeningConfigDetailView.CountryRiskEntryView> risks =
                countryRiskEntries.findAllByVersionOrderByIdAsc(config.getVersion()).stream()
                        .map(entry -> new ScreeningConfigDetailView.CountryRiskEntryView(
                                entry.getId(),
                                entry.getCountryCode(),
                                entry.getCountryName(),
                                entry.getRiskLevel().name()))
                        .toList();

        return new ScreeningConfigDetailView(
                config.getVersion(),
                config.getSamplingFrequency(),
                config.isCurrentVersion(),
                config.getCreatedBy(),
                config.getCreatedAt(),
                watchlist,
                risks);
    }

    private void ensureUniqueCountryCodes(
            List<ScreeningConfigCreateRequest.CountryRiskEntryInput> entries) {
        Set<String> seen = new HashSet<>();
        for (ScreeningConfigCreateRequest.CountryRiskEntryInput entry : entries) {
            String code = normalizeCountryCode(entry.countryCode());
            if (!seen.add(code)) {
                throw new IllegalArgumentException(
                        "countryRiskEntries contains duplicate countryCode: " + code);
            }
        }
    }

    private String normalizeCountryCode(String raw) {
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private NoSuchElementException unknownVersion(Integer version) {
        return new NoSuchElementException("unknown screening config version " + version);
    }
}