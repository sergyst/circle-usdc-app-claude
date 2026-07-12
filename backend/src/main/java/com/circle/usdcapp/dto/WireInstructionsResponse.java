package com.circle.usdcapp.dto;

public record WireInstructionsResponse(
        boolean live,
        String beneficiaryName,
        String beneficiaryAddress,
        String bankName,
        String bankAddress,
        String accountNumber,
        String routingNumber,
        String swiftCode,
        String referenceInstructions
) {
}
