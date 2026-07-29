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
 * <p>Precedence, highest first:</p>
 * <ol>
 *   <li><b>Exact</b> — normalised full name AND date of birth both match a watchlist entry ->
 *   {@code HIT}/{@code SCR_EXACT_MATCH}. Wins over everything else.</li>
 *   <li><b>Fuzzy, high confidence</b> — date of birth matches but the name does not (a
 *   typo/alias/transliteration); {@link FuzzyNameScorer} scores the name similarity in
 *   {@code [0,1]} and a weight {@code > 0.8} is treated the same as an exact hit ->
 *   {@code HIT}/{@code SCR_FUZZY_MATCH_HIGH_CONFIDENCE}, no analyst confirmation required.</li>
 *   <li><b>REVIEW-level signals, all collected (uc-02 AC8)</b> — a {@code SCR_PARTIAL_MATCH}
 *   (name exact, DOB does not), a {@code SCR_FUZZY_MATCH_NEEDS_CONFIRM} (DOB matches, name
 *   similarity {@code > 0.5} but {@code <= 0.8} — too uncertain to auto-accept, an analyst
 *   must confirm), and/or {@code SCR_HIGH_RISK_COUNTRY}. Any of these -> {@code REVIEW}.</li>
 *   <li>Otherwise {@code CLEAR}/{@code SCR_NO_MATCH}.</li>
 * </ol>
 *
 * <p>A surname-only collision, or a DOB match whose name similarity falls below the fuzzy floor,
 * is recorded as evidence ({@code verdict = "no-match"}) but never upgrades the outcome by itself
 * (uc-02 AC2) — the same "near-misses are evidence too" rule the fuzzy tiers also follow.</p>
 */
@Component
public class ScreeningMatcher {

    /** Fuzzy name-similarity weight above which a DOB-matched candidate auto-resolves to HIT. */
    static final double FUZZY_HIGH_CONFIDENCE_THRESHOLD = 0.8;

    /** Below this, a DOB match with a dissimilar name is just noise — not worth flagging at all. */
    static final double FUZZY_CANDIDATE_FLOOR = 0.5;

    public MatchVerdict match(Application.Applicant applicant, List<WatchlistEntry> watchlist,
                              List<CountryRiskEntry> countryRiskEntries) {
        String normalisedApplicantName = NameNormalizer.normalize(applicant == null ? null : applicant.fullName());
        LocalDate applicantDob = parseDate(applicant == null ? null : applicant.dateOfBirth());

        List<MatchCandidate> candidates = new ArrayList<>();
        String exactEntryId = null;
        String fuzzyHighConfidenceEntryId = null;
        boolean anyPartial = false;
        boolean anyFuzzyNeedsConfirm = false;

        for (WatchlistEntry entry : watchlist) {
            String candidateName = NameNormalizer.normalize(join(entry.getFirstName(), entry.getLastName()));
            String candidateSurname = NameNormalizer.normalize(entry.getLastName());
            boolean fullNameMatches = !normalisedApplicantName.isEmpty() && normalisedApplicantName.equals(candidateName);
            boolean dobMatches = applicantDob != null && applicantDob.equals(entry.getDateOfBirth());

            if (fullNameMatches && dobMatches) {
                candidates.add(new MatchCandidate(entry.getListId(), List.of("fullName", "dateOfBirth"), "exact", "hit", 1.0));
                exactEntryId = entry.getListId();
            } else if (fullNameMatches) {
                candidates.add(new MatchCandidate(entry.getListId(), List.of("fullName"), "partial", "partial", 1.0));
                anyPartial = true;
            } else if (dobMatches && !normalisedApplicantName.isEmpty() && !candidateName.isEmpty()) {
                double weight = FuzzyNameScorer.round(FuzzyNameScorer.similarity(normalisedApplicantName, candidateName));
                if (weight > FUZZY_HIGH_CONFIDENCE_THRESHOLD) {
                    candidates.add(new MatchCandidate(entry.getListId(), List.of("fullName", "dateOfBirth"), "fuzzy", "hit", weight));
                    if (fuzzyHighConfidenceEntryId == null) {
                        fuzzyHighConfidenceEntryId = entry.getListId();
                    }
                } else if (weight > FUZZY_CANDIDATE_FLOOR) {
                    candidates.add(new MatchCandidate(entry.getListId(), List.of("fullName", "dateOfBirth"), "fuzzy", "needs-confirm", weight));
                    anyFuzzyNeedsConfirm = true;
                } else {
                    candidates.add(new MatchCandidate(entry.getListId(), List.of("dateOfBirth"), "fuzzy", "no-match", weight));
                }
            } else if (!candidateSurname.isEmpty() && containsWord(normalisedApplicantName, candidateSurname)) {
                candidates.add(new MatchCandidate(entry.getListId(), List.of("surname"), "surname", "no-match", 0.0));
            }
        }

        String applicantCountry = applicant == null ? null : applicant.nationality();
        boolean highRiskCountry = applicantCountry != null && countryRiskEntries.stream()
                .anyMatch(risk -> risk.getRiskLevel() == RiskLevel.HIGH
                        && risk.getCountryCode().equalsIgnoreCase(applicantCountry));

        List<String> reasonCodes = new ArrayList<>();
        ScreeningOutcome outcome;
        if (exactEntryId != null) {
            outcome = ScreeningOutcome.HIT;
            reasonCodes.add("SCR_EXACT_MATCH");
        } else if (fuzzyHighConfidenceEntryId != null) {
            outcome = ScreeningOutcome.HIT;
            reasonCodes.add("SCR_FUZZY_MATCH_HIGH_CONFIDENCE");
        } else {
            if (anyPartial) {
                reasonCodes.add("SCR_PARTIAL_MATCH");
            }
            if (anyFuzzyNeedsConfirm) {
                reasonCodes.add("SCR_FUZZY_MATCH_NEEDS_CONFIRM");
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
