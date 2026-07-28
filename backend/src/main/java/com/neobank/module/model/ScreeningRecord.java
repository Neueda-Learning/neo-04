package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "screening_record")
public class ScreeningRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_version")
    private Integer configVersion;

    protected ScreeningRecord() {
        // JPA
    }

    public Long getId() {
        return id;
    }

    public Integer getConfigVersion() {
        return configVersion;
    }
}