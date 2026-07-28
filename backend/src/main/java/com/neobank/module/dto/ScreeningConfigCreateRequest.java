package com.neobank.module.dto;

import com.neobank.module.model.RiskLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;

public record ScreeningConfigCreateRequest(
        @NotNull @Positive Integer samplingFrequency,
        @NotBlank String createdBy,
        Boolean activate,
        @NotNull @Valid List<WatchlistEntryInput> watchlistEntries,
        @NotNull @Valid List<CountryRiskEntryInput> countryRiskEntries) {

    public record WatchlistEntryInput(
            @NotBlank String listId,
            String firstName,
            String lastName,
            LocalDate dateOfBirth,
            String nationality,
            String listType,
            String source) {
    }

    public record CountryRiskEntryInput(
            @NotBlank String countryCode,
            @NotBlank String countryName,
            @NotNull RiskLevel riskLevel) {
    }
}