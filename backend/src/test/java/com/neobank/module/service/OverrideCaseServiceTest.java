package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.dto.OverrideCaseRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.Decision;
import com.neobank.module.model.OverrideLog;
import com.neobank.module.model.OverrideOutcome;
import com.neobank.module.model.ScreeningOutcome;
import com.neobank.module.model.ScreeningRecord;
import com.neobank.module.repository.OverrideLogRepository;
import com.neobank.module.repository.ScreeningRecordRepository;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OverrideCaseServiceTest {

    @Mock
    private ScreeningRecordRepository cases;
    @Mock
    private OverrideLogRepository overrides;
    @Mock
    private OrchestratorClient orchestrator;

    private OverrideCaseService service;

    @BeforeEach
    void setUp() {
        service = new OverrideCaseService(cases, overrides, orchestrator, new ObjectMapper());
    }

    @Test
    void changesOnlyTheFinalOutcomeAuditsAndReportsOnceForAnExactReplay() {
        ScreeningRecord record = hit("app-1401");
        var request = new OverrideCaseRequest(OverrideOutcome.CLEAR,
                "  passport verified distinct  ", "  b.dimovski  ");
        when(cases.findByApplicationIdForUpdate("app-1401")).thenReturn(Optional.of(record));

        var first = service.override("app-1401", request);
        ArgumentCaptor<OverrideLog> audit = ArgumentCaptor.forClass(OverrideLog.class);
        verify(overrides).save(audit.capture());
        when(overrides.findFirstByApplicationIdOrderByIdDesc("app-1401"))
                .thenReturn(Optional.of(audit.getValue()));

        var replay = service.override("app-1401", request);

        assertThat(first.machineOutcome()).isEqualTo("HIT");
        assertThat(first.finalOutcome()).isEqualTo("CLEAR");
        assertThat(first.evidence().get("source").asText()).isEqualTo("machine");
        assertThat(first.overrides()).hasSize(1);
        assertThat(first.overrides().getFirst().reason()).isEqualTo("passport verified distinct");
        assertThat(replay.finalOutcome()).isEqualTo("CLEAR");
        verify(overrides, times(1)).save(audit.getValue());
        verify(orchestrator, times(1)).applicationStatusUpdate(
                "app-1401", Decision.ACCEPTED, "SCR_CLEARED_BY_ANALYST");
    }

    @Test
    void mapsHitAndReviewToTheFixedCallbackContract() {
        ScreeningRecord hitTarget = clear("app-hit");
        ScreeningRecord reviewTarget = clear("app-review");
        reviewTarget.claim("first.analyst", Instant.parse("2026-07-29T07:00:00Z"));
        reviewTarget.resolve("CLEAR", "first.analyst", "initial review", ScreeningOutcome.CLEAR,
                "SCR_CLEARED_BY_ANALYST", Instant.parse("2026-07-29T07:05:00Z"));
        when(cases.findByApplicationIdForUpdate("app-hit")).thenReturn(Optional.of(hitTarget));
        when(cases.findByApplicationIdForUpdate("app-review")).thenReturn(Optional.of(reviewTarget));

        service.override("app-hit", new OverrideCaseRequest(OverrideOutcome.HIT, "new evidence", "op"));
        service.override("app-review", new OverrideCaseRequest(OverrideOutcome.REVIEW, "needs review", "op"));

        verify(orchestrator).applicationStatusUpdate(
                "app-hit", Decision.REJECTED, "SCR_CONFIRMED_BY_ANALYST");
        verify(orchestrator).applicationStatusUpdate(
                "app-review", Decision.REFERRED, "SCR_REOPENED_BY_OPERATOR");
        assertThat(reviewTarget.getFinalOutcome()).isEqualTo("REVIEW");
        assertThat(reviewTarget.getClaimedBy()).isNull();
        assertThat(reviewTarget.getResolution()).isNull();
    }

    @Test
    void rejectsAnUnknownCaseWithoutWritingOrCallingBack() {
        when(cases.findByApplicationIdForUpdate("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.override("missing",
                new OverrideCaseRequest(OverrideOutcome.CLEAR, "verified", "op")))
                .isInstanceOf(NoSuchElementException.class);

        verify(overrides, never()).save(org.mockito.ArgumentMatchers.any());
        verify(orchestrator, never()).applicationStatusUpdate(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private static ScreeningRecord hit(String id) {
        ScreeningRecord record = new ScreeningRecord(id);
        record.applyDecision(ScreeningOutcome.HIT, ScreeningOutcome.HIT,
                "SCR_EXACT_MATCH", 1, "{\"source\":\"machine\"}");
        return record;
    }

    private static ScreeningRecord clear(String id) {
        ScreeningRecord record = new ScreeningRecord(id);
        record.applyDecision(ScreeningOutcome.CLEAR, ScreeningOutcome.CLEAR,
                "SCR_NO_MATCH", 1, "{\"source\":\"machine\"}");
        return record;
    }
}
