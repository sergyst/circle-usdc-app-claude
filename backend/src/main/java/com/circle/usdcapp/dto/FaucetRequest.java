package com.circle.usdcapp.dto;

import jakarta.validation.constraints.NotBlank;

public record FaucetRequest(@NotBlank(message = "walletId is required") String walletId) {
}
