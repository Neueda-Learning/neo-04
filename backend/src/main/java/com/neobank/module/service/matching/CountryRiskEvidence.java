package com.neobank.module.service.matching;

/** Whether the applicant's country of residence is on the current high-risk list. */
public record CountryRiskEvidence(boolean highRisk, String countryCode) {
}
