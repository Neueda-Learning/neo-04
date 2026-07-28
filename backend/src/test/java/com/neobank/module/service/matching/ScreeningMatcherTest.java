package com.neobank.module.service.matching;

import static org.assertj.core.api.Assertions.assertThat;

import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.model.CountryRiskEntry;
import com.neobank.module.model.RiskLevel;
import com.neobank.module.model.ScreeningOutcome;
import com.neobank.module.model.WatchlistEntry;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Table-driven unit tests for {@link ScreeningMatcher}'s rules — no Spring, no database, matches
 * the doc's own "build and unit-test it before any Spring wiring" note.
 */
class ScreeningMatcherTest {

    private final ScreeningMatcher matcher = new ScreeningMatcher();

    private static Application.Applicant applicant(String fullName, String dateOfBirth, String countryOfResidence) {
        return new Application.Applicant(fullName, dateOfBirth, null, null, null, countryOfResidence,
                null, null, null, null, null);
    }

    private static WatchlistEntry entry(String listId, String firstName, String lastName, LocalDate dob) {
        return new WatchlistEntry(1, listId, firstName, lastName, dob, null, "SANCTIONS", "seed");
    }

    @Test
    void exactNameAndDobIsHitRegardlessOfFuzzyRules() {
        MatchVerdict verdict = matcher.match(
                applicant("Marek Nowak", "1961-04-19", null),
                List.of(entry("WL-001", "Marek", "Nowak", LocalDate.of(1961, 4, 19))),
                List.of());

        assertThat(verdict.outcome()).isEqualTo(ScreeningOutcome.HIT);
        assertThat(verdict.reasonCode()).isEqualTo("SCR_EXACT_MATCH");
        assertThat(verdict.evidence().candidates()).singleElement()
                .satisfies(candidate -> assertThat(candidate.weight()).isEqualTo(1.0));
    }

    @Test
    void aOneLetterTypoWithTheSameDobIsAHighConfidenceFuzzyHit() {
        // "marek novak" vs "marek nowak" — one substitution in an 11-char string: 1 - 1/11 = 0.91.
        MatchVerdict verdict = matcher.match(
                applicant("Marek Novak", "1961-04-19", null),
                List.of(entry("WL-001", "Marek", "Nowak", LocalDate.of(1961, 4, 19))),
                List.of());

        assertThat(verdict.outcome()).isEqualTo(ScreeningOutcome.HIT);
        assertThat(verdict.reasonCode()).isEqualTo("SCR_FUZZY_MATCH_HIGH_CONFIDENCE");
        assertThat(verdict.evidence().candidates()).singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.rule()).isEqualTo("fuzzy");
                    assertThat(candidate.verdict()).isEqualTo("hit");
                    assertThat(candidate.weight()).isGreaterThan(0.8);
                });
    }

    @Test
    void aLooseSpellingWithTheSameDobIsAFuzzyMatchThatNeedsAnalystConfirm() {
        // "jon smit" vs "john smith" — 3 edits over a 10-char string: 1 - 3/10 = 0.70.
        MatchVerdict verdict = matcher.match(
                applicant("Jon Smit", "1980-01-01", null),
                List.of(entry("WL-009", "John", "Smith", LocalDate.of(1980, 1, 1))),
                List.of());

        assertThat(verdict.outcome()).isEqualTo(ScreeningOutcome.REVIEW);
        assertThat(verdict.reasonCode()).isEqualTo("SCR_FUZZY_MATCH_NEEDS_CONFIRM");
        assertThat(verdict.evidence().candidates()).singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.verdict()).isEqualTo("needs-confirm");
                    assertThat(candidate.weight()).isGreaterThan(0.5).isLessThanOrEqualTo(0.8);
                });
    }

    @Test
    void aCoincidentalDobMatchWithAnUnrelatedNameIsNoMatch() {
        MatchVerdict verdict = matcher.match(
                applicant("Completely Different", "1980-01-01", null),
                List.of(entry("WL-009", "John", "Smith", LocalDate.of(1980, 1, 1))),
                List.of());

        assertThat(verdict.outcome()).isEqualTo(ScreeningOutcome.CLEAR);
        assertThat(verdict.reasonCode()).isEqualTo("SCR_NO_MATCH");
        assertThat(verdict.evidence().candidates()).singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.verdict()).isEqualTo("no-match");
                    assertThat(candidate.weight()).isLessThanOrEqualTo(0.5);
                });
    }

    @Test
    void aSurnameOnlyCollisionWithADifferentDobNeverUpgradesTheOutcome() {
        // uc-02 AC2 — Maria Nowak vs WL-001 Marek Nowak: surname collision only, different DOB.
        MatchVerdict verdict = matcher.match(
                applicant("Maria Nowak", "1996-04-11", null),
                List.of(entry("WL-001", "Marek", "Nowak", LocalDate.of(1961, 4, 19))),
                List.of());

        assertThat(verdict.outcome()).isEqualTo(ScreeningOutcome.CLEAR);
        assertThat(verdict.reasonCode()).isEqualTo("SCR_NO_MATCH");
        assertThat(verdict.evidence().candidates()).singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.rule()).isEqualTo("surname");
                    assertThat(candidate.weight()).isEqualTo(0.0);
                });
    }

    @Test
    void exactNameWithMismatchedDobIsStillThePlainPartialMatch() {
        // uc-02 AC4 — name exact, DOB different: unaffected by the new fuzzy tiers.
        MatchVerdict verdict = matcher.match(
                applicant("Amara Diallo", "1988-06-02", null),
                List.of(entry("WL-003", "Amara", "Diallo", LocalDate.of(1969, 2, 10))),
                List.of());

        assertThat(verdict.outcome()).isEqualTo(ScreeningOutcome.REVIEW);
        assertThat(verdict.reasonCode()).isEqualTo("SCR_PARTIAL_MATCH");
    }

    @Test
    void aHighConfidenceFuzzyHitStillLosesToAnExactMatchOnAnotherEntry() {
        MatchVerdict verdict = matcher.match(
                applicant("Marek Nowak", "1961-04-19", null),
                List.of(
                        entry("WL-001", "Marek", "Nowak", LocalDate.of(1961, 4, 19)),
                        entry("WL-002", "Marek", "Novak", LocalDate.of(1961, 4, 19))),
                List.of());

        assertThat(verdict.outcome()).isEqualTo(ScreeningOutcome.HIT);
        assertThat(verdict.reasonCode()).isEqualTo("SCR_EXACT_MATCH");
    }

    @Test
    void needsConfirmFuzzyAndHighRiskCountryAreBothReported() {
        MatchVerdict verdict = matcher.match(
                applicant("Jon Smit", "1980-01-01", "BY"),
                List.of(entry("WL-009", "John", "Smith", LocalDate.of(1980, 1, 1))),
                List.of(new CountryRiskEntry(1, "BY", "Belarus", RiskLevel.HIGH)));

        assertThat(verdict.outcome()).isEqualTo(ScreeningOutcome.REVIEW);
        assertThat(verdict.reasonCodes()).containsExactly("SCR_FUZZY_MATCH_NEEDS_CONFIRM", "SCR_HIGH_RISK_COUNTRY");
    }
}
