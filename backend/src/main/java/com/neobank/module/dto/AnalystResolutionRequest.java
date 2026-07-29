package com.neobank.module.dto;

import jakarta.validation.constraints.NotBlank;

public record AnalystResolutionRequest(
        @NotBlank String resolution,
        @NotBlank String reason,
        @NotBlank String analyst) {
}
