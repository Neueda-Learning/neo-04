package com.neobank.module.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.model.ScreeningRecord;
import java.time.Instant;
import java.util.List;

/**
 * What {@code GET /api/v1/applications/{applicationId}} returns — uc-02's "why" behind the
 * outcome. Everything {@link ScreeningRecordView} has, plus the reason code and the evidence
 * panel ({@code normalisedName}, every {@code candidates[]} entry considered, {@code countryRisk},
 * {@code sampling}) parsed back into a real nested object, not a re-escaped string — see the
 * design discussion in {@code docs/uc-docs/uc-02-implementation-plan.md} §3.2 on why a stringified
 * blob would make the frontend parse JSON twice.
 */
public record ScreeningRecordDetailView(
        String applicationId,
        String machineOutcome,
        String finalOutcome,
        String processingStatus,
        String callbackStatus,
        String reasonCode,
        Integer configVersion,
        JsonNode evidence,
        String claimedBy,
        Instant claimedAt,
        String resolvedBy,
        Instant resolvedAt,
        String resolution,
        String resolutionReason,
        List<OverrideLogView> overrides,
        Instant createdAt,
        Instant updatedAt) {

    public static ScreeningRecordDetailView of(ScreeningRecord row, ObjectMapper json) {
        return new ScreeningRecordDetailView(
                row.getApplicationId(),
                row.getMachineOutcome(),
                row.getFinalOutcome(),
                row.getProcessingStatus(),
                row.getCallbackStatus(),
                row.getReasonCode(),
                row.getConfigVersion(),
                parseEvidence(row.getEvidence(), json),
                row.getClaimedBy(),
                row.getClaimedAt(),
                row.getResolvedBy(),
                row.getResolvedAt(),
                row.getResolution(),
                row.getResolutionReason(),
                row.getOverrideLogs().stream().map(OverrideLogView::of).toList(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private static JsonNode parseEvidence(String evidence, ObjectMapper json) {
        if (evidence == null) {
            return null;
        }
        try {
            return json.readTree(evidence);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stored evidence is not valid JSON", e);
        }
    }
}
