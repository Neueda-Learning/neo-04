package com.neobank.module.service.matching;

import java.util.List;

/**
 * One watchlist entry considered during matching, kept as evidence even when it did not
 * contribute to the outcome — a surname-only collision ({@code verdict = "no-match"}) is exactly
 * as much evidence as an exact hit (uc-02 AC2).
 *
 * <p>{@code weight} is the fuzzy-match confidence in {@code [0.0, 1.0]}: {@code 1.0} for an
 * {@code exact}/{@code partial} rule (the name comparison was exact), the
 * {@link FuzzyNameScorer} similarity for a {@code fuzzy} rule, and {@code 0.0} for a plain
 * {@code surname} collision (name comparison never even matched).</p>
 */
public record MatchCandidate(String entryId, List<String> matchedFields, String rule, String verdict,
                             double weight) {
}
