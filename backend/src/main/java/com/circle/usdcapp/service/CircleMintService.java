package com.circle.usdcapp.service;

import com.circle.usdcapp.client.CircleApiClient;
import com.circle.usdcapp.config.CircleProperties;
import com.circle.usdcapp.dto.BuyRequest;
import com.circle.usdcapp.dto.MintOrderResponse;
import com.circle.usdcapp.dto.SellRequest;
import com.circle.usdcapp.dto.WireInstructionsResponse;
import com.circle.usdcapp.exception.CircleApiException;
import com.circle.usdcapp.model.MintOrder;
import com.circle.usdcapp.repository.MintOrderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Circle Mint (https://www.circle.com/mint) is the institutional on/off-ramp
 * that lets an approved, KYB'd business wire USD in and receive minted USDC
 * (buy), or send USDC and receive a USD payout (sell).
 * <p>
 * A "buy" has no single create-order API call: the flow is (1) fetch your
 * account's wire instructions, (2) the buyer wires USD with a reference code
 * in the memo, (3) Circle auto-mints USDC to your Mint wallet on receipt and
 * notifies you by webhook. A "sell" moves USDC into your Mint wallet and then
 * calls the Payouts API to wire USD back out.
 * <p>
 * Until {@code circle.mint.live=true} is set on an account with Mint access,
 * this service processes orders in a clearly-labeled simulation mode so the
 * full buy/sell UX can be exercised without a production Circle Mint
 * relationship.
 */
@Service
@Slf4j
public class CircleMintService {

    private static final String SIMULATED_NOTE = "SIMULATED - this account is not connected to a live, KYB-approved Circle Mint business "
            +
            "account (circle.mint.live=false). No real funds moved. Set CIRCLE_MINT_LIVE=true and " +
            "configure bank linkage in the Circle Dashboard to process real fiat on/off-ramp orders.";

    private final CircleApiClient client;
    private final CircleProperties circleProperties;
    private final MintOrderRepository mintOrderRepository;
    private final CircleWalletService walletService;

    public CircleMintService(CircleApiClient client,
            CircleProperties circleProperties,
            MintOrderRepository mintOrderRepository,
            CircleWalletService walletService) {
        this.client = client;
        this.circleProperties = circleProperties;
        this.mintOrderRepository = mintOrderRepository;
        this.walletService = walletService;
    }

    public WireInstructionsResponse getWireInstructions() {
        if (!circleProperties.getMint().isLive()) {
            return new WireInstructionsResponse(
                    false, "Your Company LLC (sandbox)", "1 Main St, Boston, MA 02110",
                    "Sample Bank, N.A.", "123 Bank Plaza, New York, NY 10001",
                    "0000000000 (simulated)", "021000021 (simulated)", "SAMPUS33",
                    "Include your buy order's reference code in the wire memo so Circle can match the deposit.");
        }
        try {
            JsonNode response = client.get("/v1/businessAccount/banks/wires");
            JsonNode wire = response.path("data").get(0);
            return new WireInstructionsResponse(
                    true,
                    wire.path("beneficiary").path("name").asText(),
                    wire.path("beneficiary").path("address1").asText(),
                    wire.path("bankAddress").path("bankName").asText(),
                    wire.path("bankAddress").path("line1").asText(),
                    wire.path("accountNumber").asText(),
                    wire.path("routingNumber").asText(),
                    wire.path("bankAddress").path("swiftCode").asText(null),
                    "Include your buy order's reference code in the wire memo so Circle can match the deposit.");
        } catch (CircleApiException e) {
            log.warn("Falling back to simulated wire instructions - live Circle Mint call failed: {}", e.getMessage());
            return getWireInstructionsSimulated(e.getMessage());
        }
    }

    private WireInstructionsResponse getWireInstructionsSimulated(String reason) {
        return new WireInstructionsResponse(
                false, "Your Company LLC (sandbox)", "1 Main St, Boston, MA 02110",
                "Sample Bank, N.A.", "123 Bank Plaza, New York, NY 10001",
                "0000000000 (simulated)", "021000021 (simulated)", "SAMPUS33",
                "Live Mint call failed (" + reason + "); showing simulated instructions instead.");
    }

    public MintOrderResponse initiateBuy(BuyRequest request) {
        walletService.requireWallet(request.walletId()); // validates wallet exists

        MintOrder order = new MintOrder();
        order.setType(MintOrder.OrderType.BUY);
        order.setWalletId(request.walletId());
        order.setAmountUsd(request.amountUsd());
        order.setReferenceId("BUY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setLive(circleProperties.getMint().isLive());

        if (circleProperties.getMint().isLive()) {
            // Real buys settle asynchronously: the wire arrives, Circle detects the
            // deposit, and fires a "deposits" notification over the Mint (SNS)
            // webhook - see WebhookController.mintWebhook() and
            // applyDepositNotification() above, which auto-completes this order.
            order.setStatus(MintOrder.OrderStatus.AWAITING_FUNDS);
            order.setNotes("Wire USD using the reference code above. Circle will mint USDC to your Mint " +
                    "wallet automatically once the wire clears (usually same/next business day).");
        } else {
            order.setStatus(MintOrder.OrderStatus.COMPLETE);
            order.setNotes(SIMULATED_NOTE);
        }

        mintOrderRepository.save(order);
        return toResponse(order);
    }

    public MintOrderResponse initiateSell(SellRequest request) {
        walletService.requireWallet(request.walletId()); // validates wallet exists

        MintOrder order = new MintOrder();
        order.setType(MintOrder.OrderType.SELL);
        order.setWalletId(request.walletId());
        order.setAmountUsd(request.amountUsd());
        order.setBankAccountId(request.bankAccountId());
        order.setReferenceId("SELL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setLive(circleProperties.getMint().isLive());

        if (circleProperties.getMint().isLive()) {
            try {
                JsonNode payout = client.post("/v1/businessAccount/payouts", Map.of(
                        "idempotencyKey", UUID.randomUUID().toString(),
                        "destination", Map.of("type", "wire", "id", request.bankAccountId()),
                        "amount", Map.of("amount", request.amountUsd().toPlainString(), "currency", "USD")));
                String payoutId = payout.path("data").path("id").asText();
                order.setStatus(MintOrder.OrderStatus.PROCESSING);
                order.setCircleReferenceId(payoutId);
                order.setNotes("Payout " + payoutId + " submitted to Circle Mint.");
            } catch (CircleApiException e) {
                order.setStatus(MintOrder.OrderStatus.FAILED);
                order.setNotes("Live Circle Mint payout failed: " + e.getMessage() +
                        ". This usually means the connected account isn't KYB-approved for payouts yet.");
            }
        } else {
            order.setStatus(MintOrder.OrderStatus.COMPLETE);
            order.setNotes(SIMULATED_NOTE);
        }

        mintOrderRepository.save(order);
        return toResponse(order);
    }

    public List<MintOrderResponse> listOrders() {
        return mintOrderRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    /**
     * Applies a Circle Mint "deposits" notification (fired when an incoming
     * wire is detected/settled) to complete the matching BUY order.
     * <p>
     * Unlike sells, Circle doesn't hand back one of our reference codes for
     * deposits, so this uses a best-effort match: the oldest still-open BUY
     * order for the exact deposited amount. If your deposit payload includes
     * a remittance/OBI field carrying the wire memo, prefer matching on that
     * instead once you've confirmed its exact field name in your account.
     */
    public void applyDepositNotification(JsonNode deposit) {
        java.math.BigDecimal amount = parseAmount(deposit);
        if (amount == null) {
            log.warn("Ignoring Mint deposit notification with no readable amount");
            return;
        }
        Optional<MintOrder> match = mintOrderRepository.findFirstByTypeAndStatusAndAmountUsdOrderByCreatedAtAsc(
                MintOrder.OrderType.BUY, MintOrder.OrderStatus.AWAITING_FUNDS, amount);
        if (match.isEmpty()) {
            log.info("No open BUY order found matching a ${} deposit - ignoring", amount);
            return;
        }
        MintOrder order = match.get();
        order.setStatus(MintOrder.OrderStatus.COMPLETE);
        order.setCircleReferenceId(deposit.path("id").asText(order.getCircleReferenceId()));
        order.setNotes("Wire deposit received and USDC minted to your Mint wallet.");
        order.setUpdatedAt(java.time.Instant.now());
        mintOrderRepository.save(order);
        log.info("Completed BUY order {} from deposit notification", order.getId());
    }

    /**
     * Applies a Circle Mint "payouts" notification to update the matching
     * SELL order's status. Matched by the Circle payout id we stored when the
     * payout was created (see initiateSell), falling back to trackingRef.
     */
    public void applyPayoutNotification(JsonNode payout) {
        String payoutId = payout.path("id").asText(null);
        String trackingRef = payout.path("trackingRef").asText(null);

        Optional<MintOrder> match = Optional.ofNullable(payoutId)
                .flatMap(mintOrderRepository::findByCircleReferenceId)
                .or(() -> Optional.ofNullable(trackingRef).flatMap(mintOrderRepository::findByCircleReferenceId));

        if (match.isEmpty()) {
            log.info("No SELL order found matching payout notification {} - ignoring", payoutId);
            return;
        }

        MintOrder order = match.get();
        String status = payout.path("status").asText("");
        order.setStatus(switch (status) {
            case "complete", "paid" -> MintOrder.OrderStatus.COMPLETE;
            case "failed", "returned" -> MintOrder.OrderStatus.FAILED;
            default -> MintOrder.OrderStatus.PROCESSING;
        });
        order.setNotes("Payout " + payoutId + " status: " + status);
        order.setUpdatedAt(java.time.Instant.now());
        mintOrderRepository.save(order);
        log.info("Updated SELL order {} to {} from payout notification", order.getId(), order.getStatus());
    }

    private java.math.BigDecimal parseAmount(JsonNode deposit) {
        // Circle Mint deposit payloads have varied slightly across API versions;
        // check the couple of shapes documented for "deposits"/"paymentIntents".
        JsonNode amountNode = deposit.has("amount") ? deposit.path("amount") : deposit.path("amountPaid");
        String raw = amountNode.isObject() ? amountNode.path("amount").asText(null) : amountNode.asText(null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new java.math.BigDecimal(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private MintOrderResponse toResponse(MintOrder order) {
        return new MintOrderResponse(
                order.getId(), order.getType().name(), order.getWalletId(), order.getAmountUsd(),
                order.getStatus().name(), order.getReferenceId(), order.isLive(), order.getNotes(),
                order.getCreatedAt());
    }
}
