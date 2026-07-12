package com.circle.usdcapp.service;

import com.circle.usdcapp.client.CircleApiClient;
import com.circle.usdcapp.config.CircleProperties;
import com.circle.usdcapp.dto.*;
import com.circle.usdcapp.exception.NotFoundException;
import com.circle.usdcapp.model.LedgerTransaction;
import com.circle.usdcapp.model.ManagedWallet;
import com.circle.usdcapp.repository.LedgerTransactionRepository;
import com.circle.usdcapp.repository.ManagedWalletRepository;
import com.circle.usdcapp.util.Constants;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class CircleWalletService {

    private static final Logger log = LoggerFactory.getLogger(CircleWalletService.class);

    private final CircleApiClient client;
    private final CircleProperties circleProperties;
    private final EntitySecretCipherService cipherService;
    private final ManagedWalletRepository walletRepository;
    private final LedgerTransactionRepository ledgerRepository;

    private volatile String resolvedWalletSetId;

    public CircleWalletService(CircleApiClient client,
            CircleProperties circleProperties,
            EntitySecretCipherService cipherService,
            ManagedWalletRepository walletRepository,
            LedgerTransactionRepository ledgerRepository) {
        this.client = client;
        this.circleProperties = circleProperties;
        this.cipherService = cipherService;
        this.walletRepository = walletRepository;
        this.ledgerRepository = ledgerRepository;
        this.resolvedWalletSetId = circleProperties.getWalletSetId();
    }

    /** Creates the shared walletSet on first use if none was configured. */
    public String ensureWalletSet() {
        if (resolvedWalletSetId != null && !resolvedWalletSetId.isBlank()) {
            return resolvedWalletSetId;
        }
        Map<String, Object> body = Map.of(
                "idempotencyKey", UUID.randomUUID().toString(),
                "entitySecretCiphertext", cipherService.generateCiphertext(),
                "name", "usdc-app-wallet-set");
        JsonNode response = client.post("/v1/w3s/developer/walletSets", body);
        String id = response.path("data").path("walletSet").path("id").asText();
        log.warn("Created a new Circle walletSet '{}'. Set circle.wallet-set-id (or CIRCLE_WALLET_SET_ID) " +
                "to this value so future restarts reuse the same wallet set.", id);
        this.resolvedWalletSetId = id;
        return id;
    }

    public WalletResponse createWallet(String label) {
        String walletSetId = ensureWalletSet();
        Map<String, Object> body = Map.of(
                "idempotencyKey", UUID.randomUUID().toString(),
                "entitySecretCiphertext", cipherService.generateCiphertext(),
                "blockchains", List.of(circleProperties.getBlockchain()),
                "accountType", "SCA",
                "count", 1,
                "walletSetId", walletSetId,
                "metadata", List.of(Map.of("name", label)));
        JsonNode response = client.post("/v1/w3s/developer/wallets", body);
        JsonNode walletNode = response.path("data").path("wallets").get(0);

        ManagedWallet wallet = new ManagedWallet();
        wallet.setCircleWalletId(walletNode.path("id").asText());
        wallet.setWalletSetId(walletSetId);
        wallet.setBlockchain(walletNode.path("blockchain").asText());
        wallet.setAddress(walletNode.path("address").asText());
        wallet.setAccountType(walletNode.path("accountType").asText());
        wallet.setLabel(label);
        walletRepository.save(wallet);

        return toResponse(wallet);
    }

    public List<WalletResponse> listWallets() {
        return walletRepository.findAll().stream().map(this::toResponse).toList();
    }

    public ManagedWallet requireWallet(String localWalletId) {
        return walletRepository.findById(localWalletId)
                .orElseThrow(() -> new NotFoundException("No wallet found with id " + localWalletId));
    }

    public BalanceResponse getBalances(String localWalletId) {
        ManagedWallet wallet = requireWallet(localWalletId);
        JsonNode response = client.get("/v1/w3s/wallets/" + wallet.getCircleWalletId() + "/balances");
        List<TokenBalance> balances = new ArrayList<>();
        for (JsonNode entry : response.path("data").path("tokenBalances")) {
            JsonNode token = entry.path("token");
            balances.add(new TokenBalance(
                    token.path("symbol").asText(),
                    entry.path("amount").asText("0"),
                    token.path("tokenAddress").asText(null)));
        }
        return new BalanceResponse(wallet.getId(), wallet.getAddress(), balances);
    }

    /**
     * Looks up the Circle-internal tokenId for USDC on this wallet's chain by
     * reading its current balances (USDC must have been received at least once
     * for Circle to have indexed a tokenId against the wallet).
     */
    private String findUsdcTokenId(ManagedWallet wallet) {
        JsonNode response = client.get("/v1/w3s/wallets/" + wallet.getCircleWalletId() + "/balances");
        for (JsonNode entry : response.path("data").path("tokenBalances")) {
            JsonNode token = entry.path("token");
            if (Constants.USDC_SYMBOL.equalsIgnoreCase(token.path("symbol").asText())) {
                return token.path("id").asText();
            }
        }
        return null;
    }

    public TransactionResponse transferUsdc(TransferRequest request) {
        ManagedWallet wallet = requireWallet(request.fromWalletId());
        String tokenId = findUsdcTokenId(wallet);

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("idempotencyKey", UUID.randomUUID().toString());
        body.put("entitySecretCiphertext", cipherService.generateCiphertext());
        body.put("walletId", wallet.getCircleWalletId());
        body.put("destinationAddress", request.destinationAddress());
        body.put("amounts", List.of(request.amountUsdc().toPlainString()));
        body.put("feeLevel", "MEDIUM");
        if (tokenId != null) {
            body.put("tokenId", tokenId);
        } else {
            // Wallet has never held USDC before, so Circle hasn't indexed a tokenId
            // for it yet. Fall back to the known Sepolia USDC contract address.
            body.put("tokenAddress", Constants.USDC_SEPOLIA_CONTRACT_ADDRESS);
            body.put("blockchain", circleProperties.getBlockchain());
        }

        JsonNode response = client.post("/v1/w3s/developer/transactions/transfer", body);
        String circleTxId = response.path("data").path("id").asText();

        LedgerTransaction tx = new LedgerTransaction();
        tx.setCircleTransactionId(circleTxId);
        tx.setWalletId(wallet.getId());
        tx.setType(LedgerTransaction.TransactionType.TRANSFER);
        tx.setDirection(LedgerTransaction.Direction.OUT);
        tx.setAmountUsdc(request.amountUsdc());
        tx.setCounterparty(request.destinationAddress());
        tx.setState(LedgerTransaction.TransactionState.PENDING);
        ledgerRepository.save(tx);

        return toResponse(tx);
    }

    /**
     * Re-fetches a transaction's current state from Circle and updates the local
     * ledger row.
     */
    public TransactionResponse refreshTransaction(String localTransactionId) {
        LedgerTransaction tx = ledgerRepository.findById(localTransactionId)
                .orElseThrow(() -> new NotFoundException("No transaction found with id " + localTransactionId));
        if (tx.getCircleTransactionId() == null) {
            return toResponse(tx);
        }
        JsonNode response = client.get("/v1/w3s/transactions/" + tx.getCircleTransactionId());
        JsonNode data = response.path("data").path("transaction");
        String state = data.path("state").asText();
        tx.setState(mapState(state));
        String txHash = data.path("txHash").asText(null);
        if (txHash != null && !txHash.isBlank()) {
            tx.setTxHash(txHash);
        }
        tx.setUpdatedAt(java.time.Instant.now());
        ledgerRepository.save(tx);
        return toResponse(tx);
    }

    public List<TransactionResponse> listLedger() {
        return ledgerRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    public void recordLedgerEntry(LedgerTransaction tx) {
        ledgerRepository.save(tx);
    }

    public void requestFaucetFunds(String localWalletId) {
        ManagedWallet wallet = requireWallet(localWalletId);
        Map<String, Object> body = Map.of(
                "address", wallet.getAddress(),
                "blockchain", circleProperties.getBlockchain(),
                "native", true,
                "usdc", true);
        client.post("/v1/faucet/drips", body);

        LedgerTransaction tx = new LedgerTransaction();
        tx.setWalletId(wallet.getId());
        tx.setType(LedgerTransaction.TransactionType.FAUCET);
        tx.setDirection(LedgerTransaction.Direction.IN);
        tx.setAmountUsdc(BigDecimal.ZERO);
        tx.setCounterparty("Circle testnet faucet");
        tx.setState(LedgerTransaction.TransactionState.PENDING);
        ledgerRepository.save(tx);
    }

    /**
     * Applies a Wallets (W3S) "transactions.inbound" / "transactions.outbound"
     * webhook notification: updates the matching ledger row if we already have
     * one (e.g. a transfer we initiated ourselves), or creates one if this is
     * the first we've heard of it (e.g. an inbound deposit from outside, or a
     * faucet drip landing on-chain) so it still shows up in the ledger without
     * requiring the user to manually refresh.
     */
    public void applyTransactionNotification(JsonNode notification) {
        String circleTxId = notification.path("id").asText(null);
        if (circleTxId == null || circleTxId.isBlank()) {
            log.warn("Ignoring wallets webhook notification with no transaction id");
            return;
        }

        String stateRaw = notification.hasNonNull("state") ? notification.path("state").asText()
                : notification.path("status").asText(null);
        String txHash = notification.path("txHash").asText(null);

        Optional<LedgerTransaction> existing = ledgerRepository.findByCircleTransactionId(circleTxId);
        if (existing.isPresent()) {
            LedgerTransaction tx = existing.get();
            if (stateRaw != null) {
                tx.setState(mapState(stateRaw));
            }
            if (txHash != null && !txHash.isBlank()) {
                tx.setTxHash(txHash);
            }
            tx.setUpdatedAt(java.time.Instant.now());
            ledgerRepository.save(tx);
            return;
        }

        // First time we've seen this transaction id - only record it if it
        // belongs to one of our wallets.
        String circleWalletId = notification.path("walletId").asText(null);
        if (circleWalletId == null) {
            return;
        }
        walletRepository.findByCircleWalletId(circleWalletId).ifPresent(wallet -> {
            LedgerTransaction tx = new LedgerTransaction();
            tx.setCircleTransactionId(circleTxId);
            tx.setWalletId(wallet.getId());
            tx.setType(LedgerTransaction.TransactionType.TRANSFER);
            String transactionType = notification.path("transactionType").asText("");
            tx.setDirection("INBOUND".equalsIgnoreCase(transactionType)
                    ? LedgerTransaction.Direction.IN
                    : LedgerTransaction.Direction.OUT);
            JsonNode amounts = notification.path("amounts");
            BigDecimal amount = amounts.isArray() && amounts.size() > 0
                    ? new BigDecimal(amounts.get(0).asText("0"))
                    : BigDecimal.ZERO;
            tx.setAmountUsdc(amount);
            tx.setCounterparty(notification.path("destinationAddress").asText(null));
            tx.setState(stateRaw != null ? mapState(stateRaw) : LedgerTransaction.TransactionState.PENDING);
            tx.setTxHash(txHash);
            ledgerRepository.save(tx);
            log.info("Recorded new ledger entry from webhook for wallet {} (tx {})", wallet.getId(), circleTxId);
        });
    }

    private LedgerTransaction.TransactionState mapState(String circleState) {
        return switch (circleState) {
            case "COMPLETE", "CONFIRMED" -> LedgerTransaction.TransactionState.COMPLETE;
            case "FAILED", "CANCELLED", "DENIED" -> LedgerTransaction.TransactionState.FAILED;
            default -> LedgerTransaction.TransactionState.PENDING;
        };
    }

    private WalletResponse toResponse(ManagedWallet wallet) {
        return new WalletResponse(
                wallet.getId(), wallet.getCircleWalletId(), wallet.getAddress(),
                wallet.getBlockchain(), wallet.getAccountType(), wallet.getLabel(), wallet.getCreatedAt());
    }

    private TransactionResponse toResponse(LedgerTransaction tx) {
        return new TransactionResponse(
                tx.getId(), tx.getCircleTransactionId(), tx.getWalletId(), tx.getType().name(),
                tx.getDirection().name(), tx.getAmountUsdc(), tx.getCounterparty(),
                tx.getState().name(), tx.getTxHash(), tx.getCreatedAt());
    }
}
