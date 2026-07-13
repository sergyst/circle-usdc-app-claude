package com.circle.usdcapp.controller;

import com.circle.usdcapp.service.CircleMintService;
import com.circle.usdcapp.service.CircleWalletService;
import com.circle.usdcapp.service.CircleWebhookSignatureService;
import com.circle.usdcapp.service.SnsSignatureVerificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Receiving endpoints for Circle's two, separately-signed notification
 * systems (see https://developers.circle.com/api-reference/webhooks):
 * <p>
 * 1. {@code /api/webhooks/circle/wallets} - Developer-Controlled Wallets
 * (W3S) "v2" notifications: transactions.inbound / transactions.outbound /
 * challenges.* / etc. Delivered as a direct HTTPS POST from Circle and
 * signed with X-Circle-Signature + X-Circle-Key-Id (ECDSA_SHA_256, verified
 * against the raw body). Configure under Notifications in the Circle Console,
 * or via POST /v2/notifications/subscriptions. There is NO SNS-style
 * confirmation handshake for v2 - the endpoint only needs to return 2xx.
 * <p>
 * 2. {@code /api/webhooks/circle/mint} - Circle Mint (businessAccount)
 * "v1" payment/payout notifications, delivered via AWS SNS ("deposits",
 * "payouts", "paymentIntents", ...). Configure by subscribing this URL to
 * your Mint notification topic in the Circle Console; the first request
 * AWS sends will be a SubscriptionConfirmation, which this endpoint
 * auto-confirms.
 * <p>
 * Both endpoints are excluded from the app's X-App-Api-Key check (see
 * ApiKeyAuthFilter) since Circle/AWS won't send that header - signature
 * verification is what protects them instead.
 */
@RestController
@RequestMapping("/api/webhooks/circle")
@Slf4j
public class WebhookController {


  private final CircleWebhookSignatureService walletsSignatureService;
  private final SnsSignatureVerificationService snsSignatureService;
  private final CircleWalletService walletService;
  private final CircleMintService mintService;
  private final ObjectMapper objectMapper;

  public WebhookController(CircleWebhookSignatureService walletsSignatureService,
      SnsSignatureVerificationService snsSignatureService,
      CircleWalletService walletService,
      CircleMintService mintService,
      ObjectMapper objectMapper) {
    this.walletsSignatureService = walletsSignatureService;
    this.snsSignatureService = snsSignatureService;
    this.walletService = walletService;
    this.mintService = mintService;
    this.objectMapper = objectMapper;
  }

  // --- Wallets (W3S) notifications ------------------------------------

  /**
   * Some webhook health checks probe with HEAD before Circle sends real POSTs.
   */
  @RequestMapping(value = "/wallets", method = RequestMethod.HEAD)
  public ResponseEntity<Void> walletsHealthCheck() {
    return ResponseEntity.ok().build();
  }

  @PostMapping(value = "/wallets")
  public ResponseEntity<Map<String, String>> walletsWebhook(
      @RequestHeader(value = "X-Circle-Signature", required = false) String signature,
      @RequestHeader(value = "X-Circle-Key-Id", required = false) String keyId,
      @RequestBody String rawBody) {

    byte[] rawBytes = rawBody.getBytes(StandardCharsets.UTF_8);
    if (!walletsSignatureService.verify(rawBytes, signature, keyId)) {
      log.warn("Rejected wallets webhook - signature verification failed");
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid_signature"));
    }

    JsonNode envelope = parse(rawBody);
    // v2 envelope: subscriptionId / notificationId / notificationType /
    // notification / timestamp / version. Circle delivers "at least once" and
    // reuses notificationId on retries, so downstream handlers must be
    // idempotent (ours upsert on the Circle resource id).
    String notificationId = envelope.path("notificationId").asText("");
    String notificationType = envelope.path("notificationType").asText("");
    JsonNode notification = envelope.path("notification");

    if (notificationType.startsWith("transactions.")) {
      log.info("Wallets webhook notificationId={} type={}", notificationId, notificationType);
      walletService.applyTransactionNotification(notification);
    } else if (!notificationType.equals("webhooks.test")) {
      log.info("Ignoring wallets webhook notificationId={} type={}", notificationId, notificationType);
    }

    return ResponseEntity.ok(Map.of("status", "ok"));
  }

  // --- Circle Mint (businessAccount) notifications, via AWS SNS -------

  // Note: AWS SNS delivers messages with Content-Type: text/plain (not
  // application/json), even though the body is JSON - so this deliberately
  // has no `consumes` restriction, and the body is bound as a raw String.
  @PostMapping(value = "/mint")
  public ResponseEntity<Map<String, String>> mintWebhook(@RequestBody String rawBody) {
    JsonNode envelope = parse(rawBody);
    String type = envelope.path("Type").asText("");

    if ("SubscriptionConfirmation".equals(type)) {
      String subscribeUrl = envelope.path("SubscribeURL").asText();
      boolean confirmed = snsSignatureService.confirmSubscription(subscribeUrl);
      log.info("SNS subscription confirmation for Mint webhook: {}", confirmed ? "confirmed" : "failed");
      return ResponseEntity.ok(Map.of("status", confirmed ? "confirmed" : "confirmation_failed"));
    }

    if (!snsSignatureService.verify(envelope)) {
      log.warn("Rejected Mint webhook - SNS signature verification failed");
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid_signature"));
    }

    JsonNode message = parse(envelope.path("Message").asText("{}"));
    String notificationType = message.path("notificationType").asText("");

    switch (notificationType) {
      case "deposits" -> mintService.applyDepositNotification(message.path("deposit"));
      case "payouts" -> mintService.applyPayoutNotification(message.path("payout"));
      default -> log.info("Ignoring Mint webhook notificationType={}", notificationType);
    }

    return ResponseEntity.ok(Map.of("status", "ok"));
  }

  private JsonNode parse(String rawBody) {
    try {
      return objectMapper.readTree(rawBody);
    } catch (Exception e) {
      throw new IllegalArgumentException("Malformed webhook JSON body: " + e.getMessage(), e);
    }
  }
}
