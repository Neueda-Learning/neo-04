package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.CountryRiskEntry;
import com.neobank.module.model.Decision;
import com.neobank.module.model.RiskLevel;
import com.neobank.module.model.ScreeningConfig;
import com.neobank.module.model.ScreeningRecord;
import com.neobank.module.model.WatchlistEntry;
import com.neobank.module.repository.CountryRiskEntryRepository;
import com.neobank.module.repository.ScreeningConfigRepository;
import com.neobank.module.repository.ScreeningRecordRepository;
import com.neobank.module.repository.WatchlistEntryRepository;
import com.neobank.module.service.matching.ScreeningMatcher;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * UC-00: exactly one row per {@code applicationId}, written before the executor ever runs, plus
 * the guard that keeps a failure to open the case reportable rather than silently timing out.
 *
 * <p>No Spring, no database, no HTTP — the service takes a request and calls two collaborators, so
 * the test is a handful of lines.</p>
 */
class ApplicationServiceTest {

    private ScreeningRecordRepository screeningRecords;
    private OrchestratorClient orchestrator;
    private ScreeningConfigRepository screeningConfigs;
    private WatchlistEntryRepository watchlistEntries;
    private CountryRiskEntryRepository countryRiskEntries;
    private ScreeningMatcher matcher;
    private ObjectMapper json;

    @BeforeEach
    void setUp() {
        screeningRecords = mock(ScreeningRecordRepository.class);
        orchestrator = mock(OrchestratorClient.class);
        screeningConfigs = mock(ScreeningConfigRepository.class);
        watchlistEntries = mock(WatchlistEntryRepository.class);
        countryRiskEntries = mock(CountryRiskEntryRepository.class);
        matcher = new ScreeningMatcher();
        json = new ObjectMapper();
        when(screeningRecords.save(any(ScreeningRecord.class))).thenAnswer(call -> call.getArgument(0));
        when(screeningConfigs.findCurrent()).thenReturn(Optional.empty());
    }

    private ApplicationService service() {
        return new ApplicationService(Runnable::run, screeningRecords, orchestrator, screeningConfigs,
                watchlistEntries, countryRiskEntries, matcher, json);
    }

    private static ApplicationRequest request(String id) {
        Application application = new Application(
                id, "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant("Maria Nowak", "1996-04-11", null, null, null, null,
                        null, null, null, null, null),
                null, null, null,
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                null, null);
        return new ApplicationRequest(id, "corr-1", "process-application", application);
    }

    @Test
    void writesExactlyOneInProgressRowKeyedByApplicationId() {
        ApplicationService service = service();

        service.processApplicationAsync(request("SIM-01"));

        ArgumentCaptor<ScreeningRecord> saved = ArgumentCaptor.forClass(ScreeningRecord.class);
        verify(screeningRecords).save(saved.capture());
        assertThat(saved.getValue().getApplicationId()).isEqualTo("SIM-01");
        assertThat(saved.getValue().getMachineOutcome()).isEqualTo("PENDING");
        assertThat(saved.getValue().getFinalOutcome()).isEqualTo("PENDING");
        assertThat(saved.getValue().getProcessingStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void aRepeatedExecuteForTheSameIdIsANoOpNotASecondRow() {
        when(screeningRecords.existsByApplicationId("SIM-02")).thenReturn(true);
        ApplicationService service = service();

        service.processApplicationAsync(request("SIM-02"));

        verify(screeningRecords, never()).save(any(ScreeningRecord.class));
        verifyNoInteractions(orchestrator);
    }

    @Test
    void aRaceWithAConcurrentExecuteIsAbsorbedByTheUniqueConstraintGuard() {
        when(screeningRecords.existsByApplicationId("SIM-05")).thenReturn(false);
        when(screeningRecords.save(any(ScreeningRecord.class)))
                .thenThrow(new DataIntegrityViolationException("uk_screening_record_application_id"));
        ApplicationService service = service();

        service.processApplicationAsync(request("SIM-05"));

        verifyNoInteractions(orchestrator);
    }

    @Test
    void theRowIsWrittenOnTheCallingThreadNotTheExecutor() {
        Executor neverRuns = task -> { /* the task is never invoked */ };
        ApplicationService service = new ApplicationService(neverRuns, screeningRecords, orchestrator, screeningConfigs,
                watchlistEntries, countryRiskEntries, matcher, json);

        service.processApplicationAsync(request("SIM-06"));

        // openCase happened before the (never-executing) executor was even handed anything.
        verify(screeningRecords).save(any(ScreeningRecord.class));
    }

    @Test
    void aFailureOpeningTheCaseIsStillReportedRatherThanLeavingTheJourneyToTimeOut() {
        when(screeningRecords.existsByApplicationId("SIM-03")).thenReturn(false);
        when(screeningRecords.save(any(ScreeningRecord.class)))
                .thenThrow(new IllegalStateException("database on fire"));
        ApplicationService service = service();

        service.processApplicationAsync(request("SIM-03"));

        ArgumentCaptor<String> comment = ArgumentCaptor.forClass(String.class);
        verify(orchestrator).applicationStatusUpdate(eq("SIM-03"), eq(Decision.REFERRED),
                comment.capture());
        assertThat(comment.getValue()).contains("database on fire");
        verifyNoMoreInteractions(orchestrator);
    }

    @Test
    void theBoardShowsWhatWasOpened() {
        when(screeningRecords.findAllByOrderByCreatedAtDescIdDesc())
                .thenReturn(java.util.List.of(new ScreeningRecord("SIM-01")));
        ApplicationService service = service();

        assertThat(service.findAll())
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.applicationId()).isEqualTo("SIM-01");
                    assertThat(view.machineOutcome()).isEqualTo("PENDING");
                    assertThat(view.processingStatus()).isEqualTo("IN_PROGRESS");
                });
    }

    private static ApplicationRequest requestFor(String id, String fullName, String dateOfBirth, String countryOfResidence) {
        Application application = new Application(
                id, "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant(fullName, dateOfBirth, null, null, null, countryOfResidence,
                        null, null, null, null, null),
                null, null, null,
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                null, null);
        return new ApplicationRequest(id, "corr-1", "process-application", application);
    }

    @Test
    void anExactWatchlistMatchIsAnHitReportedAsRejected() {
        when(screeningConfigs.findCurrent()).thenReturn(Optional.of(new ScreeningConfig(1, 7, true, "seed")));
        when(watchlistEntries.findAllByVersionOrderByIdAsc(1)).thenReturn(java.util.List.of(
                new WatchlistEntry(1, "WL-001", "Marek", "Nowak", java.time.LocalDate.of(1961, 4, 19),
                        "PL", "SANCTIONS", "seed")));
        ScreeningRecord row = new ScreeningRecord("SIM-10");
        when(screeningRecords.findByApplicationId("SIM-10")).thenReturn(Optional.of(row));
        ApplicationService service = service();

        service.decide(requestFor("SIM-10", "Marek Nowak", "1961-04-19", null));

        assertThat(row.getMachineOutcome()).isEqualTo("HIT");
        assertThat(row.getReasonCode()).isEqualTo("SCR_EXACT_MATCH");
        assertThat(row.getProcessingStatus()).isEqualTo("COMPLETE");
        assertThat(row.getCallbackStatus()).isEqualTo("SENT");
        verify(orchestrator).applicationStatusUpdate("SIM-10", Decision.REJECTED, "SCR_EXACT_MATCH");
    }

    @Test
    void noMatchAndNoRiskIsClearReportedAsAccepted() {
        ScreeningRecord row = new ScreeningRecord("SIM-11");
        when(screeningRecords.findByApplicationId("SIM-11")).thenReturn(Optional.of(row));
        ApplicationService service = service();

        service.decide(requestFor("SIM-11", "Nobody Special", "1990-01-01", null));

        assertThat(row.getMachineOutcome()).isEqualTo("CLEAR");
        assertThat(row.getReasonCode()).isEqualTo("SCR_NO_MATCH");
        verify(orchestrator).applicationStatusUpdate("SIM-11", Decision.ACCEPTED, "SCR_NO_MATCH");
    }

    @Test
    void aPartialNameMatchWithMismatchedDobIsReviewReportedAsReferred() {
        when(screeningConfigs.findCurrent()).thenReturn(Optional.of(new ScreeningConfig(1, 7, true, "seed")));
        when(watchlistEntries.findAllByVersionOrderByIdAsc(1)).thenReturn(java.util.List.of(
                new WatchlistEntry(1, "WL-003", "Amara", "Diallo", java.time.LocalDate.of(1969, 2, 10),
                        null, "SANCTIONS", "seed")));
        ScreeningRecord row = new ScreeningRecord("SIM-12");
        when(screeningRecords.findByApplicationId("SIM-12")).thenReturn(Optional.of(row));
        ApplicationService service = service();

        service.decide(requestFor("SIM-12", "Amara Diallo", "1988-06-02", null));

        assertThat(row.getMachineOutcome()).isEqualTo("REVIEW");
        assertThat(row.getReasonCode()).isEqualTo("SCR_PARTIAL_MATCH");
        verify(orchestrator).applicationStatusUpdate("SIM-12", Decision.REFERRED, "SCR_PARTIAL_MATCH");
    }

    @Test
    void aHighRiskCountryOfResidenceIsReviewReportedAsReferred() {
        when(screeningConfigs.findCurrent()).thenReturn(Optional.of(new ScreeningConfig(1, 7, true, "seed")));
        when(countryRiskEntries.findAllByVersionOrderByIdAsc(1)).thenReturn(java.util.List.of(
                new CountryRiskEntry(1, "BY", "Belarus", RiskLevel.HIGH)));
        ScreeningRecord row = new ScreeningRecord("SIM-13");
        when(screeningRecords.findByApplicationId("SIM-13")).thenReturn(Optional.of(row));
        ApplicationService service = service();

        service.decide(requestFor("SIM-13", "Elena Petrova", "1990-01-01", "BY"));

        assertThat(row.getMachineOutcome()).isEqualTo("REVIEW");
        assertThat(row.getReasonCode()).isEqualTo("SCR_HIGH_RISK_COUNTRY");
        verify(orchestrator).applicationStatusUpdate("SIM-13", Decision.REFERRED, "SCR_HIGH_RISK_COUNTRY");
    }

    @Test
    void anExactMatchWinsOverAHighRiskCountry() {
        when(screeningConfigs.findCurrent()).thenReturn(Optional.of(new ScreeningConfig(1, 7, true, "seed")));
        when(watchlistEntries.findAllByVersionOrderByIdAsc(1)).thenReturn(java.util.List.of(
                new WatchlistEntry(1, "WL-001", "Marek", "Nowak", java.time.LocalDate.of(1961, 4, 19),
                        "PL", "SANCTIONS", "seed")));
        when(countryRiskEntries.findAllByVersionOrderByIdAsc(1)).thenReturn(java.util.List.of(
                new CountryRiskEntry(1, "BY", "Belarus", RiskLevel.HIGH)));
        ScreeningRecord row = new ScreeningRecord("SIM-14");
        when(screeningRecords.findByApplicationId("SIM-14")).thenReturn(Optional.of(row));
        ApplicationService service = service();

        service.decide(requestFor("SIM-14", "Marek Nowak", "1961-04-19", "BY"));

        assertThat(row.getMachineOutcome()).isEqualTo("HIT");
        assertThat(row.getReasonCode()).isEqualTo("SCR_EXACT_MATCH");
        verify(orchestrator).applicationStatusUpdate("SIM-14", Decision.REJECTED, "SCR_EXACT_MATCH");
    }

    @Test
    void aFailureDecidingIsReportedAsReferredAndCallbackMarkedFailed() {
        when(screeningConfigs.findCurrent()).thenThrow(new IllegalStateException("database on fire"));
        ScreeningRecord row = new ScreeningRecord("SIM-15");
        when(screeningRecords.findByApplicationId("SIM-15")).thenReturn(Optional.of(row));
        ApplicationService service = service();

        service.decide(requestFor("SIM-15", "Nobody Special", "1990-01-01", null));

        verify(orchestrator).applicationStatusUpdate(eq("SIM-15"), eq(Decision.REFERRED), any());
        assertThat(row.getCallbackStatus()).isEqualTo("FAILED");
    }

    @Test
    void aHighConfidenceFuzzyNameMatchWithTheSameDobIsAnHitReportedAsRejected() {
        when(screeningConfigs.findCurrent()).thenReturn(Optional.of(new ScreeningConfig(1, 7, true, "seed")));
        when(watchlistEntries.findAllByVersionOrderByIdAsc(1)).thenReturn(java.util.List.of(
                new WatchlistEntry(1, "WL-001", "Marek", "Nowak", java.time.LocalDate.of(1961, 4, 19),
                        "PL", "SANCTIONS", "seed")));
        ScreeningRecord row = new ScreeningRecord("SIM-16");
        when(screeningRecords.findByApplicationId("SIM-16")).thenReturn(Optional.of(row));
        ApplicationService service = service();

        // "marek novak" vs "marek nowak" — a one-letter typo, same DOB: similarity ~0.91 > 0.8.
        service.decide(requestFor("SIM-16", "Marek Novak", "1961-04-19", null));

        assertThat(row.getMachineOutcome()).isEqualTo("HIT");
        assertThat(row.getReasonCode()).isEqualTo("SCR_FUZZY_MATCH_HIGH_CONFIDENCE");
        verify(orchestrator).applicationStatusUpdate("SIM-16", Decision.REJECTED, "SCR_FUZZY_MATCH_HIGH_CONFIDENCE");
    }

    @Test
    void aLowerConfidenceFuzzyNameMatchWithTheSameDobIsReviewNeedingAnalystConfirm() {
        when(screeningConfigs.findCurrent()).thenReturn(Optional.of(new ScreeningConfig(1, 7, true, "seed")));
        when(watchlistEntries.findAllByVersionOrderByIdAsc(1)).thenReturn(java.util.List.of(
                new WatchlistEntry(1, "WL-009", "John", "Smith", java.time.LocalDate.of(1980, 1, 1),
                        "US", "SANCTIONS", "seed")));
        ScreeningRecord row = new ScreeningRecord("SIM-17");
        when(screeningRecords.findByApplicationId("SIM-17")).thenReturn(Optional.of(row));
        ApplicationService service = service();

        // "jon smit" vs "john smith" — same DOB, a much looser spelling: similarity ~0.70.
        service.decide(requestFor("SIM-17", "Jon Smit", "1980-01-01", null));

        assertThat(row.getMachineOutcome()).isEqualTo("REVIEW");
        assertThat(row.getReasonCode()).isEqualTo("SCR_FUZZY_MATCH_NEEDS_CONFIRM");
        verify(orchestrator).applicationStatusUpdate("SIM-17", Decision.REFERRED, "SCR_FUZZY_MATCH_NEEDS_CONFIRM");
    }

    @Test
    void everySamplingFrequencyThRowIsForcedToReviewButKeepsTheMachineOutcome() {
        when(screeningConfigs.findCurrent()).thenReturn(Optional.of(new ScreeningConfig(1, 7, true, "seed")));
        ScreeningRecord row = new ScreeningRecord("SIM-18");
        ReflectionTestUtils.setField(row, "id", 14L); // the fixture's own 14th-decision checkpoint (uc-02 AC7)
        when(screeningRecords.findByApplicationId("SIM-18")).thenReturn(Optional.of(row));
        ApplicationService service = service();

        service.decide(requestFor("SIM-18", "Nobody Special", "1990-01-01", null));

        assertThat(row.getMachineOutcome()).isEqualTo("CLEAR"); // what the rules actually decided
        assertThat(row.getFinalOutcome()).isEqualTo("REVIEW");  // parked for a mandatory sample confirm
        assertThat(row.getReasonCode()).isEqualTo("SCR_SAMPLED_FOR_REVIEW");
        verify(orchestrator).applicationStatusUpdate("SIM-18", Decision.REFERRED, "SCR_SAMPLED_FOR_REVIEW");
    }

    @Test
    void aRowNotOnASamplingBoundaryIsUnaffectedBySampling() {
        when(screeningConfigs.findCurrent()).thenReturn(Optional.of(new ScreeningConfig(1, 7, true, "seed")));
        ScreeningRecord row = new ScreeningRecord("SIM-19");
        ReflectionTestUtils.setField(row, "id", 15L);
        when(screeningRecords.findByApplicationId("SIM-19")).thenReturn(Optional.of(row));
        ApplicationService service = service();

        service.decide(requestFor("SIM-19", "Nobody Special", "1990-01-01", null));

        assertThat(row.getMachineOutcome()).isEqualTo("CLEAR");
        assertThat(row.getFinalOutcome()).isEqualTo("CLEAR");
        assertThat(row.getReasonCode()).isEqualTo("SCR_NO_MATCH");
        verify(orchestrator).applicationStatusUpdate("SIM-19", Decision.ACCEPTED, "SCR_NO_MATCH");
    }

    @Test
    void evidenceJsonRecordsTheSamplingDecisionOnceKnown() throws Exception {
        // uc-02 AC1/AC7 — matchResults.sampling must agree with what was actually reported, which
        // only exists once the row's id (the sample position) is known.
        when(screeningConfigs.findCurrent()).thenReturn(Optional.of(new ScreeningConfig(1, 7, true, "seed")));
        ScreeningRecord row = new ScreeningRecord("SIM-20");
        ReflectionTestUtils.setField(row, "id", 14L);
        when(screeningRecords.findByApplicationId("SIM-20")).thenReturn(Optional.of(row));
        ApplicationService service = service();

        service.decide(requestFor("SIM-20", "Nobody Special", "1990-01-01", null));

        var sampling = json.readTree(row.getEvidence()).get("sampling");
        assertThat(sampling.get("sampled").asBoolean()).isTrue();
        assertThat(sampling.get("position").asLong()).isEqualTo(14L);
    }

    @Test
    void evidenceJsonRecordsNotSampledWhenOffTheBoundary() throws Exception {
        when(screeningConfigs.findCurrent()).thenReturn(Optional.of(new ScreeningConfig(1, 7, true, "seed")));
        ScreeningRecord row = new ScreeningRecord("SIM-21");
        ReflectionTestUtils.setField(row, "id", 15L);
        when(screeningRecords.findByApplicationId("SIM-21")).thenReturn(Optional.of(row));
        ApplicationService service = service();

        service.decide(requestFor("SIM-21", "Nobody Special", "1990-01-01", null));

        var sampling = json.readTree(row.getEvidence()).get("sampling");
        assertThat(sampling.get("sampled").asBoolean()).isFalse();
        assertThat(sampling.get("position").isNull()).isTrue();
    }

    @Test
    void findOneReturnsTheDetailViewWithParsedEvidence() {
        ScreeningRecord row = new ScreeningRecord("SIM-22");
        when(screeningRecords.findByApplicationId("SIM-22")).thenReturn(Optional.of(row));
        ApplicationService service = service();
        service.decide(requestFor("SIM-22", "Nobody Special", "1990-01-01", null));

        var detail = service.findOne("SIM-22");

        assertThat(detail.applicationId()).isEqualTo("SIM-22");
        assertThat(detail.finalOutcome()).isEqualTo("CLEAR");
        assertThat(detail.reasonCode()).isEqualTo("SCR_NO_MATCH");
        // A real nested object, not a re-escaped string — see ScreeningRecordDetailView's javadoc.
        assertThat(detail.evidence().get("normalisedName").asText()).isEqualTo("nobody special");
        assertThat(detail.evidence().get("sampling").get("sampled").asBoolean()).isFalse();
    }

    @Test
    void findOneThrowsForAnUnknownApplicationIdSoTheControllerCanAnswer404() {
        when(screeningRecords.findByApplicationId("SIM-404")).thenReturn(Optional.empty());
        ApplicationService service = service();

        assertThatThrownBy(() -> service.findOne("SIM-404"))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("SIM-404");
    }
}
