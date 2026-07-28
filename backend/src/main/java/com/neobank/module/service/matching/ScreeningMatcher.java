package com.neobank.module.service.matching;

import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.model.CountryRiskEntry;
import com.neobank.module.model.RiskLevel;
import com.neobank.module.model.ScreeningOutcome;
import com.neobank.module.model.WatchlistEntry;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * <h2>The matching rules, uc-02's "engine" — plain functions over the application plus the
 * current {@code ScreeningConfig}, no Spring, no persistence.</h2>
 *
 * <p>Precedence: exact match wins outright (HIT, {@code SCR_EXACT_MATCH}); otherwise every partial
 * match and high-risk-country signal is collected and, if either fired, the outcome is REVIEW with
 * all contributing codes; otherwise CLEAR ({@code SCR_NO_MATCH}). A surname-only collision is
 * recorded as evidence but never upgrades the outcome by itself (uc-02 AC2).</p>
 */
@Component
public class ScreeningMatcher {

    public MatchVerdict match(Application.Applicant applicant, List<WatchlistEntry> watchlist,
                              List<CountryRiskEntry> countryRiskEntries) {
        String normalisedApplicantName = NameNormalizer.normalize(applicant == null ? null : applicant.fullName());
        LocalDate applicantDob = parseDate(applicant == null ? null : applicant.dateOfBirth());

        List<MatchCandidate> candidates = new ArrayList<>();
        String exactEntryId = null;
        boolean anyPartial = false;

        for (WatchlistEntry entry : watchlist) {
            String candidateName = NameNormalizer.normalize(join(entry.getFirstName(), entry.getLastName()));
            String candidateSurname = NameNormalizer.normalize(entry.getLastName());
            boolean fullNameMatches = !normalisedApplicantName.isEmpty() && normalisedApplicantName.equals(candidateName);
            boolean dobMatches = applicantDob != null && applicantDob.equals(entry.getDateOfBirth());

            if (fullNameMatches && dobMatches) {
                candidates.add(new MatchCandidate(entry.getListId(), List.of("fullName", "dateOfBirth"), "exact", "hit"));
                exactEntryId = entry.getListId();
            } else if (fullNameMatches) {
                candidates.add(new MatchCandidate(entry.getListId(), List.of("fullName"), "partial", "partial"));
                anyPartial = true;
            } else if (!candidateSurname.isEmpty() && containsWord(normalisedApplicantName, candidateSurname)) {
                candidates.add(new MatchCandidate(entry.getListId(), List.of("surname"), "surname", "no-match"));
            }
        }

        String applicantCountry = applicant == null ? null : applicant.countryOfResidence();
        boolean highRiskCountry = applicantCountry != null && countryRiskEntries.stream()
                .anyMatch(risk -> risk.getRiskLevel() == RiskLevel.HIGH
                        && risk.getCountryCode().equalsIgnoreCase(applicantCountry));

        List<String> reasonCodes = new ArrayList<>();
        ScreeningOutcome outcome;
        if (exactEntryId != null) {
            outcome = ScreeningOutcome.HIT;
            reasonCodes.add("SCR_EXACT_MATCH");
        } else {
            if (anyPartial) {
                reasonCodes.add("SCR_PARTIAL_MATCH");
            }
            if (highRiskCountry) {
                reasonCodes.add("SCR_HIGH_RISK_COUNTRY");
            }
            if (reasonCodes.isEmpty()) {
                outcome = ScreeningOutcome.CLEAR;
                reasonCodes.add("SCR_NO_MATCH");
            } else {
                outcome = ScreeningOutcome.REVIEW;
            }
        }

        MatchEvidence evidence = new MatchEvidence(normalisedApplicantName, candidates,
                new CountryRiskEvidence(highRiskCountry, applicantCountry));
        return new MatchVerdict(outcome, reasonCodes, evidence);
    }

    private static String join(String firstName, String lastName) {
        if (firstName == null) {
            return lastName;
        }
        if (lastName == null) {
            return firstName;
        }
        return firstName + " " + lastName;
    }

    private static boolean containsWord(String haystack, String word) {
        for (String token : haystack.split(" ")) {
            if (token.equals(word)) {
                return true;
            }
        }
        return false;
    }

    /** A date the orchestrator sent as a string, not guaranteed parseable — see {@link Application}'s rule 1. */
    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
