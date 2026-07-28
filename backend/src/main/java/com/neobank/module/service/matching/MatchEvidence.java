package com.neobank.module.service.matching;

import java.util.List;

/**
 * The evidence panel: the normalised name as actually compared, every watchlist candidate
 * considered, and the country-risk check. Serialised as JSON into {@code screening_record.evidence}.
 */
public record MatchEvidence(String normalisedName, List<MatchCandidate> candidates,
                             CountryRiskEvidence countryRisk) {
}
