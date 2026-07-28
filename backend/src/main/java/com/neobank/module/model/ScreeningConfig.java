package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "screening_config")
public class ScreeningConfig {

    @Id
    private Integer version;

    @Column(name = "sampling_frequency", nullable = false)
    private Integer samplingFrequency;

    @Column(name = "current_version", nullable = false)
    private Byte currentVersion;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ScreeningConfig() {
        // JPA
    }

    public ScreeningConfig(Integer version, Integer samplingFrequency, boolean currentVersion,
                           String createdBy) {
        this.version = version;
        this.samplingFrequency = samplingFrequency;
        this.currentVersion = currentVersion ? (byte) 1 : (byte) 0;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Integer getVersion() {
        return version;
    }

    public Integer getSamplingFrequency() {
        return samplingFrequency;
    }

    public boolean isCurrentVersion() {
        return currentVersion != null && currentVersion == (byte) 1;
    }

    public void setCurrentVersion(boolean currentVersion) {
        this.currentVersion = currentVersion ? (byte) 1 : (byte) 0;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}