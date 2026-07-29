package com.neobank.module.service.matching;

/**
 * uc-02 rule 4's outcome: whether this decision was forced to {@code REVIEW} regardless of what
 * the matcher itself found, and — when it was — the row's own position (its sample-boundary
 * multiple), e.g. {@code position = 14} for the fixture's 14th first-time decision (uc-02 AC7).
 *
 * <p>Computed after {@link ScreeningMatcher#match} returns (it needs the row's persisted id, which
 * the matcher never sees), then folded into the evidence via {@link MatchEvidence#withSampling}
 * before the evidence is serialised — never before, or the stored blob would contradict the
 * outcome actually reported.</p>
 */
public record SamplingEvidence(boolean sampled, Long position) {
}
