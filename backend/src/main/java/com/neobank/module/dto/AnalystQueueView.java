package com.neobank.module.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.model.ScreeningRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One open REVIEW in the analyst queue, including the machine evidence needed to decide. */
public record AnalystQueueView(
        String applicationId,
        String outcome,
        String machineOutcome,
        String reasonCode,
        List<String> causes,
        String claimedBy,
        Instant claimedAt,
        String resolvedBy,
        Instant resolvedAt,
        String resolution,
        String resolutionReason,
        JsonNode evidence,
        Instant openedAt) {

    public static AnalystQueueView of(ScreeningRecord row, ObjectMapper json) {
        JsonNode evidence = parseEvidence(row.getEvidence(), json);
        return new AnalystQueueView(
                row.getApplicationId(), row.getFinalOutcome(), row.getMachineOutcome(), row.getReasonCode(),
                causes(evidence, row.getReasonCode()), row.getClaimedBy(), row.getClaimedAt(),
                row.getResolvedBy(), row.getResolvedAt(), row.getResolution(), row.getResolutionReason(),
                evidence, row.getCreatedAt());
    }

    private static JsonNode parseEvidence(String value, ObjectMapper json) {
        if (value == null || value.isBlank()) {
            return json.createObjectNode();
        }
        try {
            return json.readTree(value);
        } catch (Exception e) {
            throw new IllegalStateException("stored evidence is not valid JSON", e);
        }
    }

    private static List<String> causes(JsonNode evidence, String reasonCode) {
        List<String> result = new ArrayList<>();
        evidence.path("candidates").forEach(candidate -> {
            if ("partial".equalsIgnoreCase(candidate.path("verdict").asText()) && !result.contains("partial")) {
                result.add("partial");
            }
        });
        if (evidence.path("countryRisk").path("highRisk").asBoolean(false)) {
            result.add("country");
        }
        if (evidence.path("sampling").path("sampled").asBoolean(false)) {
            result.add("sampled");
        }
        if (result.isEmpty() && reasonCode != null) {
            result.add(reasonCode.replace("SCR_", "").replace("_FOR_REVIEW", "")
                    .replace("_MATCH", "").replace('_', '-').toLowerCase());
        }
        return List.copyOf(result);
    }
}
