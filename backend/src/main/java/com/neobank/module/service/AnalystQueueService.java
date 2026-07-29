package com.neobank.module.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.dto.AnalystQueueView;
import com.neobank.module.dto.AnalystResolutionRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.CallbackStatus;
import com.neobank.module.model.Decision;
import com.neobank.module.model.ScreeningOutcome;
import com.neobank.module.model.ScreeningRecord;
import com.neobank.module.repository.ScreeningRecordRepository;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-04 claim, release and resolve workflow for open REVIEW cases. */
@Service
public class AnalystQueueService {

    private static final int QUEUE_LIMIT = 10;

    private final ScreeningRecordRepository cases;
    private final OrchestratorClient orchestrator;
    private final ObjectMapper json;

    public AnalystQueueService(ScreeningRecordRepository cases, OrchestratorClient orchestrator, ObjectMapper json) {
        this.cases = cases;
        this.orchestrator = orchestrator;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<AnalystQueueView> openQueue(boolean unclaimedFirst) {
        var page = PageRequest.of(0, QUEUE_LIMIT);
        List<ScreeningRecord> rows = unclaimedFirst
                ? cases.findOpenAnalystQueue(page)
                : cases.findByFinalOutcomeAndResolutionIsNullOrderByCreatedAtAscIdAsc("REVIEW", page);
        return rows.stream().map(row -> AnalystQueueView.of(row, json)).toList();
    }

    @Transactional
    public AnalystQueueView claim(String applicationId, String analyst) {
        String owner = required(analyst, "analyst");
        if (cases.claimIfAvailable(applicationId, owner, Instant.now()) == 1) {
            return view(applicationId);
        }

        ScreeningRecord row = find(applicationId);
        ensureOpenReview(row);
        if (owner.equals(row.getClaimedBy())) {
            return AnalystQueueView.of(row, json);
        }
        throw new IllegalStateException("case " + applicationId + " is already claimed by " + row.getClaimedBy());
    }

    @Transactional
    public AnalystQueueView release(String applicationId, String analyst) {
        String owner = required(analyst, "analyst");
        ScreeningRecord row = findForUpdate(applicationId);
        ensureOpenReview(row);
        if (row.getClaimedBy() == null) {
            return AnalystQueueView.of(row, json);
        }
        if (!owner.equals(row.getClaimedBy())) {
            throw new IllegalStateException("case " + applicationId + " is claimed by " + row.getClaimedBy());
        }
        row.release();
        return AnalystQueueView.of(cases.save(row), json);
    }

    @Transactional
    public AnalystQueueView resolve(String applicationId, AnalystResolutionRequest request) {
        String analyst = required(request.analyst(), "analyst");
        String reason = required(request.reason(), "reason");
        String resolution = required(request.resolution(), "resolution").toUpperCase();
        Resolution decision = Resolution.parse(resolution);

        ScreeningRecord row = findForUpdate(applicationId);
        if (row.getResolution() != null) {
            if (resolution.equals(row.getResolution()) && analyst.equals(row.getResolvedBy())
                    && reason.equals(row.getResolutionReason())) {
                return AnalystQueueView.of(row, json);
            }
            throw new IllegalStateException("case " + applicationId + " is already resolved");
        }
        ensureOpenReview(row);
        if (!analyst.equals(row.getClaimedBy())) {
            throw new IllegalStateException(row.getClaimedBy() == null
                    ? "case " + applicationId + " must be claimed before resolution"
                    : "case " + applicationId + " is claimed by " + row.getClaimedBy());
        }

        row.resolve(resolution, analyst, reason, decision.outcome, decision.reasonCode, Instant.now());
        cases.saveAndFlush(row);
        orchestrator.applicationStatusUpdate(applicationId, decision.callbackDecision, decision.reasonCode);
        row.recordCallback(CallbackStatus.SENT, Instant.now());
        return AnalystQueueView.of(cases.save(row), json);
    }

    private AnalystQueueView view(String applicationId) {
        return AnalystQueueView.of(find(applicationId), json);
    }

    private ScreeningRecord find(String applicationId) {
        return cases.findByApplicationId(applicationId)
                .orElseThrow(() -> new NoSuchElementException("no case for " + applicationId));
    }

    private ScreeningRecord findForUpdate(String applicationId) {
        return cases.findByApplicationIdForUpdate(applicationId)
                .orElseThrow(() -> new NoSuchElementException("no case for " + applicationId));
    }

    private static void ensureOpenReview(ScreeningRecord row) {
        if (!"REVIEW".equals(row.getFinalOutcome()) || row.getResolution() != null) {
            throw new IllegalStateException("case " + row.getApplicationId() + " is not an open REVIEW");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private enum Resolution {
        CLEAR(ScreeningOutcome.CLEAR, Decision.ACCEPTED, "SCR_CLEARED_BY_ANALYST"),
        CONFIRM(ScreeningOutcome.HIT, Decision.REJECTED, "SCR_CONFIRMED_BY_ANALYST");

        private final ScreeningOutcome outcome;
        private final Decision callbackDecision;
        private final String reasonCode;

        Resolution(ScreeningOutcome outcome, Decision callbackDecision, String reasonCode) {
            this.outcome = outcome;
            this.callbackDecision = callbackDecision;
            this.reasonCode = reasonCode;
        }

        private static Resolution parse(String value) {
            try {
                return valueOf(value);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("resolution must be CLEAR or CONFIRM");
            }
        }
    }
}
