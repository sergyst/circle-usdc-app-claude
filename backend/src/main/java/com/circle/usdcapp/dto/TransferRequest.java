package com.circle.usdcapp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TransferRequest(
        @NotBlank(message = "fromWalletId is required") String fromWalletId,
        @NotBlank(message = "destinationAddress is required") String destinationAddress,
        @NotNull(message = "amountUsdc is required")
        @DecimalMin(value = "0.000001", message = "amountUsdc must be greater than 0") BigDecimal amountUsdc
) {
}
