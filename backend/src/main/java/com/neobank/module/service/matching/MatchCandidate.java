package com.neobank.module.service.matching;

import java.util.List;

/**
 * One watchlist entry considered during matching, kept as evidence even when it did not
 * contribute to the outcome — a surname-only collision ({@code verdict = "no-match"}) is exactly
 * as much evidence as an exact hit (uc-02 AC2).
 */
public record MatchCandidate(String entryId, List<String> matchedFields, String rule, String verdict) {
}
