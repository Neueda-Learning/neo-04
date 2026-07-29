package com.neobank.module.dto;

import com.neobank.module.model.OverrideLog;
import java.time.Instant;

public record OverrideLogView(
        String oldOutcome,
        String newOutcome,
        String reason,
        String operator,
        Instant overriddenAt) {

    public static OverrideLogView of(OverrideLog row) {
        return new OverrideLogView(row.getOldOutcome(), row.getNewOutcome(), row.getReason(),
                row.getOperator(), row.getOverrideTime());
    }
}
