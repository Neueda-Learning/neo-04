package com.neobank.module.dto;

import com.neobank.module.integrations.orchestrator.Application;

/** Applicant fields displayed by UC-03, fetched live and never persisted by this module. */
public record ApplicantView(
        String fullName,
        String dateOfBirth,
        String countryOfResidence,
        String nationality,
        String channel) {

    public static ApplicantView of(Application application) {
        Application.Applicant applicant = application.applicant();
        return new ApplicantView(
                applicant == null ? null : applicant.fullName(),
                applicant == null ? null : applicant.dateOfBirth(),
                applicant == null ? null : applicant.countryOfResidence(),
                applicant == null ? null : applicant.nationality(),
                application.channel());
    }
}
