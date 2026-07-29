package com.neobank.module.integrations.orchestrator;

/** Signals a transient failure while hydrating applicant data from the orchestrator. */
public class ApplicantUnavailableException extends RuntimeException {

    public ApplicantUnavailableException(String message) {
        super(message);
    }
}
