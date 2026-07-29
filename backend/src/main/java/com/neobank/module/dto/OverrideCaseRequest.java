package com.neobank.module.dto;

import com.neobank.module.model.OverrideOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OverrideCaseRequest(
        @NotNull OverrideOutcome newOutcome,
        @NotBlank @Size(max = 255) String reason,
        @NotBlank @Size(max = 100) String operator) {
}
