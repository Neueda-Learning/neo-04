package com.neobank.module.dto;

import jakarta.validation.constraints.NotBlank;

public record AnalystClaimRequest(@NotBlank String analyst) {
}
