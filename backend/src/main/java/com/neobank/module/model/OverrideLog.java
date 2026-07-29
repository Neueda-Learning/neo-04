package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/** Immutable audit row for one effective manual outcome change. */
@Entity
@Table(name = "override_log")
public class OverrideLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", referencedColumnName = "application_id", nullable = false)
    private ScreeningRecord screeningRecord;

    @Column(name = "application_id", insertable = false, updatable = false)
    private String applicationId;

    @Column(name = "old_outcome", nullable = false, length = 32)
    private String oldOutcome;

    @Column(name = "new_outcome", nullable = false, length = 32)
    private String newOutcome;

    @Column(name = "operator", nullable = false, length = 100)
    private String operator;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Column(name = "override_time", nullable = false, updatable = false)
    private Instant overrideTime;

    protected OverrideLog() {
        // JPA
    }

    public OverrideLog(ScreeningRecord screeningRecord, String oldOutcome, OverrideOutcome newOutcome,
                       String operator, String reason) {
        this.screeningRecord = screeningRecord;
        this.applicationId = screeningRecord.getApplicationId();
        this.oldOutcome = oldOutcome;
        this.newOutcome = newOutcome.name();
        this.operator = operator;
        this.reason = reason;
    }

    @PrePersist
    void onCreate() {
        if (overrideTime == null) {
            overrideTime = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getOldOutcome() {
        return oldOutcome;
    }

    public String getNewOutcome() {
        return newOutcome;
    }

    public String getOperator() {
        return operator;
    }

    public String getReason() {
        return reason;
    }

    public Instant getOverrideTime() {
        return overrideTime;
    }
}
