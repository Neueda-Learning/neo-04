package com.neobank.module.model;

/**
 * The screening domain's own answer about an applicant — {@code machine_outcome} and
 * {@code final_outcome} on {@link ScreeningRecord}. This is <b>not</b> {@link Decision}: that enum
 * is the wire value this module reports to the orchestrator ({@code ACCEPTED}/{@code REJECTED}/
 * {@code REFERRED}); this one is the screening verdict itself, per UC-00's spec.
 *
 * <p>{@link #CLEAR}, {@link #REVIEW} and {@link #HIT} are the three outcomes the spec names.
 * {@link #PENDING} is this module's own addition: {@code machine_outcome}/{@code final_outcome}
 * are {@code NOT NULL} columns, and UC-00 inserts the row <em>before</em> anything has been
 * decided — deciding is the engine use case's job, which reads this row and replaces
 * {@code PENDING} with a real verdict.</p>
 */
public enum ScreeningOutcome {

    /** Row exists, nothing decided yet — the state every {@code screening_record} starts in. */
    PENDING,

    /** No match, no country risk. */
    CLEAR,

    /** Partial match, high-risk country, or sampled — parked for an analyst. */
    REVIEW,

    /** Exact watchlist match. */
    HIT
}
