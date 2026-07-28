package com.neobank.module.model;

/**
 * {@code screening_record.processing_status} — where a case sits in this module's own pipeline.
 * Not what the orchestrator has been told; see {@link CallbackStatus} for that.
 */
public enum ProcessingStatus {

    /** Row written, off-thread decision not finished yet. Set synchronously, before the {@code 202}. */
    IN_PROGRESS,

    /** The engine has decided — {@code machine_outcome}/{@code final_outcome} are no longer PENDING. */
    COMPLETE
}
