package com.neobank.module.dto;

import com.neobank.module.model.ScreeningRecord;
import java.time.Instant;

/**
 * What {@code GET /api/v1/applications} returns — this module's own board, not the orchestrator's.
 * Replaces {@code DemoShowcaseView} now that the table behind it is the real screening one.
 *
 * <p>Yours to grow: add {@code reasonCode}, {@code configVersion} or {@code matchedEntryId} when
 * the operator screen needs them. The surrogate {@code id} still does not leak — same reasoning as
 * the view it replaces.</p>
 */
public record ScreeningRecordView(
        String applicationId,
        String status,
        String machineOutcome,
        String finalOutcome,
        String processingStatus,
        String callbackStatus,
        Instant createdAt,
        Instant updatedAt) {

    public static ScreeningRecordView of(ScreeningRecord row) {
        return new ScreeningRecordView(
                row.getApplicationId(),
                outcomeToDecision(row.getFinalOutcome()),
                row.getMachineOutcome(),
                row.getFinalOutcome(),
                row.getProcessingStatus(),
                row.getCallbackStatus(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private static String outcomeToDecision(String screeningOutcome) {
        return switch (screeningOutcome) {
            case "HIT" -> "REJECTED";
            case "REVIEW" -> "REFERRED";
            case "CLEAR", "PENDING" -> "ACCEPTED";
            default -> "ACCEPTED";
        };
    }
}
