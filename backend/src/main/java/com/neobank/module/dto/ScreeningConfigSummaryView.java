package com.neobank.module.dto;

import com.neobank.module.model.ScreeningConfig;
import java.time.Instant;

public record ScreeningConfigSummaryView(
        Integer version,
        Integer samplingFrequency,
        boolean currentVersion,
        String createdBy,
        Instant createdAt) {

    public static ScreeningConfigSummaryView of(ScreeningConfig config) {
        return new ScreeningConfigSummaryView(
                config.getVersion(),
                config.getSamplingFrequency(),
                config.isCurrentVersion(),
                config.getCreatedBy(),
                config.getCreatedAt());
    }
}