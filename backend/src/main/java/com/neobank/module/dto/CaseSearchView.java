package com.neobank.module.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.model.ScreeningRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A screening case as the search board shows it (uc-01).
 *
 * <p>Deliberately minimal — no applicant name is stored or returned; the UI hydrates
 * the name column live via the module's application-fetch GET.</p>
 */
public record CaseSearchView(
        String applicationId,
        Instant submittedAt,
        String outcome,
        boolean sampled,
        int matchCount) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static CaseSearchView of(ScreeningRecord row) {
        boolean sampled = !row.getMachineOutcome().equals(row.getFinalOutcome());
        int matchCount = parseMatchCount(row.getEvidence());
        return new CaseSearchView(
                row.getApplicationId(),
                row.getCreatedAt(),
                row.getFinalOutcome(),
                sampled,
                matchCount);
    }

    /** Counts candidates from evidence JSON whose verdict is not "no-match". */
    private static int parseMatchCount(String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return 0;
        }
        try {
            Map<String, Object> parsed = MAPPER.readValue(evidence, new TypeReference<>() {});
            Object candidates = parsed.get("candidates");
            if (candidates instanceof List<?> list) {
                return (int) list.stream()
                        .filter(c -> c instanceof Map<?, ?> m && !"no-match".equals(m.get("verdict")))
                        .count();
            }
        } catch (Exception ignored) {
            // malformed evidence — treat as zero matches
        }
        return 0;
    }
}
