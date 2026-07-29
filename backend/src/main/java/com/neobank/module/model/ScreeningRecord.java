package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <h2>UC-00's whole job: one durable row per {@code applicationId}, written before the {@code 202}.</h2>
 *
 * <p>Maps to {@code screening_record} (see {@code db/changelog/changes/002-create-screening-case.yaml}
 * — <b>never edit that change set</b>; new columns are a new one). This replaces the template's
 * {@code demo_showcase} placeholder: it is this module's real table, keyed on
 * {@code application_id} the same way, but shaped for the screening domain instead of three throwaway
 * columns.</p>
 *
 * <p><b>Two constructors, two use cases.</b> {@link #ScreeningRecord(String)} is all UC-00 needs —
 * it writes {@link ScreeningOutcome#PENDING} into both outcome columns and
 * {@link ProcessingStatus#IN_PROGRESS}, because deciding anything is explicitly out of scope here:
 * that is the engine use case's job, and it updates this same row rather than inserting a new one.
 * Do not add a "decide" method to this class — keep the entity dumb and put the rules in the
 * service that reads it.</p>
 */
@Entity
@Table(name = "screening_record")
public class ScreeningRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The id from the request envelope. Also the idempotency key: {@code uk_screening_record_application_id}
     * is what turns a repeated {@code /execute} for the same application into a no-op rather than a
     * second row.
     */
    @Column(name = "application_id", nullable = false, length = 50)
    private String applicationId;

    /** What rules 1–3 computed before sampling. {@code PENDING} until the engine use case decides. */
    @Column(name = "machine_outcome", nullable = false, length = 32)
    private String machineOutcome;

    /** The effective outcome after any analyst override. Starts equal to {@code machineOutcome}. */
    @Column(name = "final_outcome", nullable = false, length = 32)
    private String finalOutcome;

    /** Whether {@code PUT /api/v1/applications/{id}} has told the orchestrator this outcome yet. */
    @Column(name = "callback_status", nullable = false, length = 32)
    private String callbackStatus;

    /** Where this case sits in this module's own pipeline — not the orchestrator's business. */
    @Column(name = "processing_status", nullable = false, length = 32)
    private String processingStatus;

    /** Why {@code final_outcome} is what it is, e.g. a high-risk-country code. Set by the engine. */
    @Column(name = "reason_code", length = 50)
    private String reasonCode;

    /** The {@code screening_config} version matched against — pinned forever once set. */
    @Column(name = "config_version")
    private Integer configVersion;

    /**
     * The evidence JSON: normalised name, candidates considered, country risk, sampling.
     *
     * <p>{@code length = 65535} is not a real cap — it is how Hibernate picks the MySQL LOB DDL
     * subtype to validate against. Without it, a {@code @Lob String} defaults to the JPA column
     * length of 255 and Hibernate expects {@code TINYTEXT}, but the changelog declares this column
     * {@code TEXT} (up to 65535) — {@code ddl-auto=validate} then fails on real MySQL even though
     * H2 (used by {@code ./mvnw test}) does not enforce the same distinction.</p>
     */
    @Lob
    @Column(name = "evidence", length = 65535)
    private String evidence;

    /** When the outcome was last reported to the orchestrator. */
    @Column(name = "callback_time")
    private Instant callbackTime;

    @Column(name = "claimed_by", length = 100)
    private String claimedBy;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution", length = 20)
    private String resolution;

    @Column(name = "resolution_reason", length = 500)
    private String resolutionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "screeningRecord")
    @OrderBy("overrideTime DESC, id DESC")
    private List<OverrideLog> overrideLogs = new ArrayList<>();

    protected ScreeningRecord() {
        // JPA
    }

    /**
     * The only row UC-00 ever writes: {@code application_id} plus placeholders everywhere a real
     * verdict will eventually go. See the class doc for why {@code PENDING}/{@code IN_PROGRESS} are
     * correct here rather than a guess at the real outcome.
     */
    public ScreeningRecord(String applicationId) {
        this.applicationId = applicationId;
        this.machineOutcome = ScreeningOutcome.PENDING.name();
        this.finalOutcome = ScreeningOutcome.PENDING.name();
        this.callbackStatus = CallbackStatus.PENDING.name();
        this.processingStatus = ProcessingStatus.IN_PROGRESS.name();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * The engine use case's write: replaces {@code PENDING}/{@code PENDING} with the real verdict.
     * {@code machineOutcome} and {@code finalOutcome} are usually the same value — they only
     * diverge for a sampled decision (uc-02 rule 4), where {@code machineOutcome} keeps what the
     * rules actually computed and {@code finalOutcome} is forced to {@code REVIEW} so an analyst
     * always sees the machine's own answer even though the case is parked. A later analyst
     * override (a different use case) is the only other thing allowed to make them diverge. Kept
     * dumb on purpose: no matching rule lives here, only field assignment — see the class doc.
     */
    public void applyDecision(ScreeningOutcome machineOutcome, ScreeningOutcome finalOutcome,
                              String reasonCode, Integer configVersion, String evidence) {
        this.machineOutcome = machineOutcome.name();
        this.finalOutcome = finalOutcome.name();
        this.reasonCode = reasonCode;
        this.configVersion = configVersion;
        this.evidence = evidence;
        this.processingStatus = ProcessingStatus.COMPLETE.name();
    }

    /** {@code PUT /api/v1/applications/{id}} was attempted; see {@link CallbackStatus} for what SENT/FAILED mean. */
    public void recordCallback(CallbackStatus status, Instant when) {
        this.callbackStatus = status.name();
        this.callbackTime = when;
    }

    public void claim(String analyst, Instant when) {
        this.claimedBy = analyst;
        this.claimedAt = when;
    }

    public void release() {
        this.claimedBy = null;
        this.claimedAt = null;
    }

    public void resolve(String resolution, String analyst, String reason, ScreeningOutcome outcome,
                        String reasonCode, Instant when) {
        this.resolution = resolution;
        this.resolvedBy = analyst;
        this.resolutionReason = reason;
        this.resolvedAt = when;
        this.finalOutcome = outcome.name();
        this.reasonCode = reasonCode;
    }

    /** UC-08's only mutation to the screening record. */
    public void overrideFinalOutcome(OverrideOutcome outcome) {
        this.finalOutcome = outcome.name();
        if (outcome == OverrideOutcome.REVIEW) {
            this.claimedBy = null;
            this.claimedAt = null;
            this.resolvedBy = null;
            this.resolvedAt = null;
            this.resolution = null;
            this.resolutionReason = null;
        }
    }

    public void addOverrideLog(OverrideLog overrideLog) {
        overrideLogs.add(0, overrideLog);
    }

    public Long getId() {
        return id;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getMachineOutcome() {
        return machineOutcome;
    }

    public String getFinalOutcome() {
        return finalOutcome;
    }

    public String getCallbackStatus() {
        return callbackStatus;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public Integer getConfigVersion() {
        return configVersion;
    }

    public String getEvidence() {
        return evidence;
    }

    public Instant getCallbackTime() {
        return callbackTime;
    }

    public String getClaimedBy() {
        return claimedBy;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public String getResolution() {
        return resolution;
    }

    public String getResolutionReason() {
        return resolutionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<OverrideLog> getOverrideLogs() {
        return Collections.unmodifiableList(overrideLogs);
    }
}
