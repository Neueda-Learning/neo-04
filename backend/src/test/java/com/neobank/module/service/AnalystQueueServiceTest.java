package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.dto.AnalystResolutionRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.Decision;
import com.neobank.module.model.ScreeningOutcome;
import com.neobank.module.model.ScreeningRecord;
import com.neobank.module.repository.ScreeningRecordRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnalystQueueServiceTest {

    private ScreeningRecordRepository cases;
    private OrchestratorClient orchestrator;
    private AnalystQueueService service;

    @BeforeEach
    void setUp() {
        cases = mock(ScreeningRecordRepository.class);
        orchestrator = mock(OrchestratorClient.class);
        service = new AnalystQueueService(cases, orchestrator, new ObjectMapper());
        when(cases.save(any(ScreeningRecord.class))).thenAnswer(call -> call.getArgument(0));
        when(cases.saveAndFlush(any(ScreeningRecord.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void anotherAnalystCannotClaimAnOwnedCase() {
        ScreeningRecord row = review("app-1360");
        row.claim("r.iqbal", java.time.Instant.now());
        when(cases.findByApplicationId("app-1360")).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.claim("app-1360", "b.dimovski"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("r.iqbal");
    }

    @Test
    void clearingWritesHumanResolutionAndOneAcceptedCallback() {
        ScreeningRecord row = review("app-1360");
        row.claim("r.iqbal", java.time.Instant.now());
        when(cases.findByApplicationIdForUpdate("app-1360")).thenReturn(Optional.of(row));

        var result = service.resolve("app-1360",
                new AnalystResolutionRequest("CLEAR", "jurisdiction only", "r.iqbal"));

        assertThat(result.outcome()).isEqualTo("CLEAR");
        assertThat(result.machineOutcome()).isEqualTo("REVIEW");
        assertThat(result.resolvedBy()).isEqualTo("r.iqbal");
        assertThat(result.resolutionReason()).isEqualTo("jurisdiction only");
        verify(orchestrator).applicationStatusUpdate(
                "app-1360", Decision.ACCEPTED, "SCR_CLEARED_BY_ANALYST");
    }

    @Test
    void replayingTheSameResolutionDoesNotSendASecondCallback() {
        ScreeningRecord row = review("app-1360");
        row.claim("r.iqbal", java.time.Instant.now());
        row.resolve("CLEAR", "r.iqbal", "jurisdiction only", ScreeningOutcome.CLEAR,
                "SCR_CLEARED_BY_ANALYST", java.time.Instant.now());
        when(cases.findByApplicationIdForUpdate("app-1360")).thenReturn(Optional.of(row));

        var result = service.resolve("app-1360",
                new AnalystResolutionRequest("CLEAR", "jurisdiction only", "r.iqbal"));

        assertThat(result.outcome()).isEqualTo("CLEAR");
        verify(orchestrator, never()).applicationStatusUpdate(any(), any(), any());
    }

    @Test
    void resolutionRequiresTheClaimOwner() {
        ScreeningRecord row = review("app-1372");
        when(cases.findByApplicationIdForUpdate("app-1372")).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.resolve("app-1372",
                new AnalystResolutionRequest("CONFIRM", "true match", "r.iqbal")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be claimed");
        verify(orchestrator, never()).applicationStatusUpdate(any(), any(), any());
    }

    private static ScreeningRecord review(String applicationId) {
        ScreeningRecord row = new ScreeningRecord(applicationId);
        row.applyDecision(ScreeningOutcome.REVIEW, ScreeningOutcome.REVIEW,
                "SCR_HIGH_RISK_COUNTRY", 1,
                "{\"normalisedName\":\"elena petrova\",\"candidates\":[],"
                        + "\"countryRisk\":{\"highRisk\":true,\"countryCode\":\"BY\"},"
                        + "\"sampling\":{\"sampled\":false}}");
        return row;
    }
}
