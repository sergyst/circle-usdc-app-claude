package com.circle.usdcapp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SellRequest(
        @NotBlank(message = "walletId is required") String walletId,
        @NotNull(message = "amountUsd is required")
        @DecimalMin(value = "1", message = "amountUsd must be at least 1") BigDecimal amountUsd,
        @NotBlank(message = "bankAccountId is required") String bankAccountId
) {
}
