package com.neobank.module.service.matching;

import java.util.List;

/**
 * The evidence panel: the normalised name as actually compared, every watchlist candidate
 * considered, the country-risk check, and whether sampling forced the outcome (uc-02 AC1's
 * {@code matchResults}). Serialised as JSON into {@code screening_record.evidence}.
 *
 * <p>{@code sampling} is {@code null} as {@link ScreeningMatcher#match} builds this record — the
 * matcher has no idea whether this row will land on a sample boundary, only {@code ApplicationService}
 * does, once the row's own id is known. {@link #withSampling} is how the caller fills it in before
 * serialising, never the matcher itself.</p>
 */
public record MatchEvidence(String normalisedName, List<MatchCandidate> candidates,
                             CountryRiskEvidence countryRisk, SamplingEvidence sampling) {

    public MatchEvidence(String normalisedName, List<MatchCandidate> candidates, CountryRiskEvidence countryRisk) {
        this(normalisedName, candidates, countryRisk, null);
    }

    public MatchEvidence withSampling(SamplingEvidence sampling) {
        return new MatchEvidence(normalisedName, candidates, countryRisk, sampling);
    }
}
