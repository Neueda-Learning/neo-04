package com.neobank.module.service.matching;

import java.util.List;

/**
 * The evidence panel: every watchlist candidate considered, and the country-risk check.
 * Serialised as JSON into {@code screening_record.evidence}.
 *
 * <p>The normalisedName is recorded for audit (uc-02 analyst review), not for search.
 * Name-based case search must go through the orchestrator (v5 contract: GET /applications?name=),
 * not through local storage, to ensure this module never depends on storing applicant data for search.</p>
 */
public record MatchEvidence(String normalisedName,
                             List<MatchCandidate> candidates,
                             CountryRiskEvidence countryRisk) {
}
