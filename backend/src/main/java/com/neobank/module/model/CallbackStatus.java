package com.neobank.module.model;

/**
 * {@code screening_record.callback_status} — whether the orchestrator has been told the outcome
 * yet. Separate from {@link ProcessingStatus}: a case can be {@code COMPLETE} and still have a
 * callback that failed and needs retrying.
 */
public enum CallbackStatus {

    /** Not reported yet — true for every row UC-00 creates, since deciding is a later step. */
    PENDING,

    /** {@code PUT /api/v1/applications/{id}} succeeded. */
    SENT,

    /** The orchestrator call failed; the orchestrator's own timeout sweeper will notice. */
    FAILED
}
