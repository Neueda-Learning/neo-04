package com.neobank.module.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ScreeningConfigDetailView(
        Integer version,
        Integer samplingFrequency,
        boolean currentVersion,
        String createdBy,
        Instant createdAt,
        List<WatchlistEntryView> watchlistEntries,
        List<CountryRiskEntryView> countryRiskEntries) {

    public record WatchlistEntryView(
            Long id,
            String listId,
            String firstName,
            String lastName,
            LocalDate dateOfBirth,
            String nationality,
            String listType,
            String source,
            Instant createdAt) {
    }

    public record CountryRiskEntryView(
            Long id,
            String countryCode,
            String countryName,
            String riskLevel) {
    }
}