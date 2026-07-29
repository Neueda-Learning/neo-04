package com.neobank.module.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.dto.OverrideCaseRequest;
import com.neobank.module.dto.ScreeningRecordDetailView;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.Decision;
import com.neobank.module.model.OverrideLog;
import com.neobank.module.model.OverrideOutcome;
import com.neobank.module.model.ScreeningRecord;
import com.neobank.module.repository.OverrideLogRepository;
import com.neobank.module.repository.ScreeningRecordRepository;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies UC-08's audited, idempotent manual outcome correction. */
@Service
public class OverrideCaseService {

    private final ScreeningRecordRepository cases;
    private final OverrideLogRepository overrides;
    private final OrchestratorClient orchestrator;
    private final ObjectMapper json;

    public OverrideCaseService(ScreeningRecordRepository cases, OverrideLogRepository overrides,
                               OrchestratorClient orchestrator, ObjectMapper json) {
        this.cases = cases;
        this.overrides = overrides;
        this.orchestrator = orchestrator;
        this.json = json;
    }

    @Transactional
    public ScreeningRecordDetailView override(String applicationId, OverrideCaseRequest request) {
        ScreeningRecord record = cases.findByApplicationIdForUpdate(applicationId)
                .orElseThrow(() -> new NoSuchElementException("no case for " + applicationId));
        String operator = request.operator().trim();
        String reason = request.reason().trim();

        if (isReplay(record, request.newOutcome(), operator, reason)) {
            return ScreeningRecordDetailView.of(record, json);
        }
        if (record.getFinalOutcome().equals(request.newOutcome().name())) {
            throw new IllegalArgumentException("case already has outcome " + request.newOutcome());
        }

        OverrideLog audit = new OverrideLog(record, record.getFinalOutcome(), request.newOutcome(),
                operator, reason);
        record.overrideFinalOutcome(request.newOutcome());
        record.addOverrideLog(audit);
        overrides.save(audit);
        cases.save(record);

        orchestrator.applicationStatusUpdate(applicationId, callbackStatus(request.newOutcome()),
                callbackComment(request.newOutcome()));
        return ScreeningRecordDetailView.of(record, json);
    }

    private boolean isReplay(ScreeningRecord record, OverrideOutcome outcome, String operator, String reason) {
        if (!record.getFinalOutcome().equals(outcome.name())) {
            return false;
        }
        return overrides.findFirstByApplicationIdOrderByIdDesc(record.getApplicationId())
                .filter(last -> last.getNewOutcome().equals(outcome.name()))
                .filter(last -> last.getOperator().equals(operator))
                .filter(last -> last.getReason().equals(reason))
                .isPresent();
    }

    private static Decision callbackStatus(OverrideOutcome outcome) {
        return switch (outcome) {
            case CLEAR -> Decision.ACCEPTED;
            case HIT -> Decision.REJECTED;
            case REVIEW -> Decision.REFERRED;
        };
    }

    private static String callbackComment(OverrideOutcome outcome) {
        return switch (outcome) {
            case CLEAR -> "SCR_CLEARED_BY_ANALYST";
            case HIT -> "SCR_CONFIRMED_BY_ANALYST";
            case REVIEW -> "SCR_REOPENED_BY_OPERATOR";
        };
    }
}
