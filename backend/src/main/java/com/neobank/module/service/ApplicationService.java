package com.neobank.module.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.dto.ScreeningRecordView;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.CallbackStatus;
import com.neobank.module.model.CountryRiskEntry;
import com.neobank.module.model.Decision;
import com.neobank.module.model.ScreeningConfig;
import com.neobank.module.model.ScreeningOutcome;
import com.neobank.module.model.ScreeningRecord;
import com.neobank.module.model.WatchlistEntry;
import com.neobank.module.repository.CountryRiskEntryRepository;
import com.neobank.module.repository.ScreeningConfigRepository;
import com.neobank.module.repository.ScreeningRecordRepository;
import com.neobank.module.repository.WatchlistEntryRepository;
import com.neobank.module.service.matching.MatchVerdict;
import com.neobank.module.service.matching.ScreeningMatcher;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h2>UC-00 — Process Application, plus uc-02's matching engine.</h2>
 *
 * <p>The one durable thing UC-00 owns: exactly one {@link ScreeningRecord} row per
 * {@code applicationId}, written on the calling thread before the {@code 202} goes out, keyed so a
 * repeated {@code /execute} for the same application is a no-op rather than a second row. Deciding
 * the actual outcome then runs off-thread in {@link #decide}: match the watchlist and high-risk
 * countries via {@link ScreeningMatcher}, write the verdict back onto the same row, and report it to
 * the orchestrator.</p>
 */
@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private final Executor executor;
    private final ScreeningRecordRepository screeningRecords;
    private final OrchestratorClient orchestrator;
    private final ScreeningConfigRepository screeningConfigs;
    private final WatchlistEntryRepository watchlistEntries;
    private final CountryRiskEntryRepository countryRiskEntries;
    private final ScreeningMatcher matcher;
    private final ObjectMapper json;

    public ApplicationService(@Qualifier("applicationTaskExecutor") Executor executor,
                              ScreeningRecordRepository screeningRecords,
                              OrchestratorClient orchestrator,
                              ScreeningConfigRepository screeningConfigs,
                              WatchlistEntryRepository watchlistEntries,
                              CountryRiskEntryRepository countryRiskEntries,
                              ScreeningMatcher matcher,
                              ObjectMapper json) {
        this.executor = executor;
        this.screeningRecords = screeningRecords;
        this.orchestrator = orchestrator;
        this.screeningConfigs = screeningConfigs;
        this.watchlistEntries = watchlistEntries;
        this.countryRiskEntries = countryRiskEntries;
        this.matcher = matcher;
        this.json = json;
    }

    /**
     * Open the case synchronously (so the row exists before the {@code 202} ack), then hand the
     * actual decision off to the executor. Nothing here may block on the decision itself — that is
     * the executor's job.
     */
    public void processApplicationAsync(ApplicationRequest request) {
        String applicationId = request.applicationId();
        log.info("RECEIVED {}", request.summary());

        boolean opened;
        try {
            opened = openCase(applicationId);
        } catch (RuntimeException e) {
            log.error("Could not open a screening case for {} — referring", applicationId, e);
            orchestrator.applicationStatusUpdate(applicationId, Decision.REFERRED, "module error: " + e);
            return;
        }

        if (!opened) {
            log.info("{} already has a screening record — ignoring duplicate execute", applicationId);
            return;
        }

        executor.execute(() -> decide(request));
    }

    /**
     * The only write UC-00 makes: one {@link ScreeningRecord} row, {@code PENDING}/{@code IN_PROGRESS}
     * everywhere a real verdict will eventually go. The unique constraint on {@code application_id}
     * is the idempotency guard — a race between two concurrent {@code /execute} calls for the same
     * id collapses to exactly one row via {@link DataIntegrityViolationException}, not an exception
     * that reaches the caller.
     */
    @Transactional
    boolean openCase(String applicationId) {
        if (screeningRecords.existsByApplicationId(applicationId)) {
            return false;
        }
        try {
            screeningRecords.save(new ScreeningRecord(applicationId));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    /**
     * uc-02's matching engine: match against the current {@link ScreeningConfig}'s watchlist and
     * high-risk countries, write the verdict onto this application's row, then report it to the
     * orchestrator. Package-private so a unit test can call it directly on the test thread.
     *
     * <p>Any failure here is reported as {@link Decision#REFERRED} rather than left to the
     * orchestrator's timeout — the row was already opened, so there is always something to
     * report.</p>
     */
    void decide(ApplicationRequest request) {
        String applicationId = request.applicationId();
        try {
            Application.Applicant applicant = request.application() == null
                    ? null : request.application().applicant();
            ScreeningConfig config = screeningConfigs.findCurrent().orElse(null);
            List<CountryRiskEntry> countryRisks = config == null
                    ? List.of() : countryRiskEntries.findAllByVersionOrderByIdAsc(config.getVersion());
            Integer configVersion = config == null ? null : config.getVersion();
            List<WatchlistEntry> watchlist = config == null
                    ? List.of() : watchlistEntries.findAllByVersionOrderByIdAsc(config.getVersion());
            Integer samplingFrequency = config == null ? null : config.getSamplingFrequency();

            MatchVerdict verdict = matcher.match(applicant, watchlist, countryRisks);
            String evidence = writeEvidence(verdict);

            AppliedDecision applied = applyDecision(applicationId, verdict, configVersion, evidence, samplingFrequency);

            Decision decision = toDecision(applied.finalOutcome());
            orchestrator.applicationStatusUpdate(applicationId, decision, applied.reasonCode());
            recordCallback(applicationId, CallbackStatus.SENT);
            log.info("{} decided {} ({})", applicationId, applied.finalOutcome(), applied.reasonCode());
        } catch (RuntimeException e) {
            log.error("Could not decide a screening outcome for {} — referring", applicationId, e);
            orchestrator.applicationStatusUpdate(applicationId, Decision.REFERRED, "module error: " + e);
            recordCallback(applicationId, CallbackStatus.FAILED);
        }
    }

    /** What actually got written to the row — the sampled outcome/reason if uc-02 rule 4 fired, else the matcher's own. */
    private record AppliedDecision(ScreeningOutcome finalOutcome, String reasonCode) {
    }

    /**
     * uc-02 rule 4 — every {@code samplingFrequency}th first-time decision is forced to
     * {@code REVIEW} for a mandatory business/analyst confirmation, whatever rules 1–3 (and the
     * fuzzy tiers) concluded. {@code machineOutcome} keeps the real verdict; only
     * {@code finalOutcome} is overridden, and the reason becomes {@code SCR_SAMPLED_FOR_REVIEW}
     * alone (uc-02 AC7) — not appended to the machine's own reason codes.
     *
     * <p>The row's own auto-increment {@code id}, assigned synchronously when UC-00 opens the
     * case, is the sample position: it is unique and ordered by receipt with no extra counter or
     * race to manage. A {@code null} id only happens in a unit test that never persisted the row
     * for real — treated as "not sampled" rather than throwing.</p>
     */
    @Transactional
    AppliedDecision applyDecision(String applicationId, MatchVerdict verdict, Integer configVersion,
                                  String evidence, Integer samplingFrequency) {
        return screeningRecords.findByApplicationId(applicationId).map(record -> {
            boolean sampled = samplingFrequency != null && samplingFrequency > 0
                    && record.getId() != null && record.getId() % samplingFrequency == 0;
            ScreeningOutcome finalOutcome = sampled ? ScreeningOutcome.REVIEW : verdict.outcome();
            String reasonCode = sampled ? "SCR_SAMPLED_FOR_REVIEW" : verdict.reasonCode();

            record.applyDecision(verdict.outcome(), finalOutcome, reasonCode, configVersion, evidence);
            screeningRecords.save(record);
            return new AppliedDecision(finalOutcome, reasonCode);
        }).orElseGet(() -> new AppliedDecision(verdict.outcome(), verdict.reasonCode()));
    }

    @Transactional
    void recordCallback(String applicationId, CallbackStatus status) {
        screeningRecords.findByApplicationId(applicationId).ifPresent(record -> {
            record.recordCallback(status, Instant.now());
            screeningRecords.save(record);
        });
    }

    private String writeEvidence(MatchVerdict verdict) {
        try {
            return json.writeValueAsString(verdict.evidence());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not serialise match evidence", e);
        }
    }

    private static Decision toDecision(ScreeningOutcome outcome) {
        return switch (outcome) {
            case HIT -> Decision.REJECTED;
            case REVIEW -> Decision.REFERRED;
            case CLEAR, PENDING -> Decision.ACCEPTED;
        };
    }

    /** Everything this module has answered, newest first — what its own UI reads. */
    @Transactional(readOnly = true)
    public List<ScreeningRecordView> findAll() {
        return screeningRecords.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(ScreeningRecordView::of)
                .toList();
    }
}
