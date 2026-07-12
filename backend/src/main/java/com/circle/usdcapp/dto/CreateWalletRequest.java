package com.circle.usdcapp.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateWalletRequest(@NotBlank(message = "label is required") String label) {
}
