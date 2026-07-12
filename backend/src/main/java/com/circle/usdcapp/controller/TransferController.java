package com.circle.usdcapp.controller;

import com.circle.usdcapp.dto.TransactionResponse;
import com.circle.usdcapp.dto.TransferRequest;
import com.circle.usdcapp.service.CircleWalletService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final CircleWalletService walletService;

    public TransferController(CircleWalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse transfer(@Valid @RequestBody TransferRequest request) {
        return walletService.transferUsdc(request);
    }

    @PostMapping("/{id}/refresh")
    public TransactionResponse refresh(@PathVariable String id) {
        return walletService.refreshTransaction(id);
    }
}
