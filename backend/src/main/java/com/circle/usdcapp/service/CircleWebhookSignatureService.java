package com.circle.usdcapp.service;

import com.circle.usdcapp.client.CircleApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verifies webhook notifications from Circle's Developer-Controlled Wallets
 * (W3S) notification service - the one that fires transactions.inbound /
 * transactions.outbound / challenges.* / etc, as configured under
 * Notifications in the Circle Dashboard.
 * <p>
 * Every notification is signed with an ECDSA (P-256, SHA-256) key. The
 * signing key is identified by the X-Circle-Key-Id header and rotates
 * occasionally, so keys are fetched from Circle on first use and cached
 * per key id rather than hardcoded.
 * <p>
 * NOTE: this is a completely different signing mechanism from Circle Mint's
 * (businessAccount) payment/payout notifications, which are delivered over
 * AWS SNS - see {@link SnsSignatureVerificationService} for that flow.
 *
 * See:
 * https://developers.circle.com/w3s/docs/web3-services-notifications-quickstart
 */
@Service
public class CircleWebhookSignatureService {

  private static final Logger log = LoggerFactory.getLogger(CircleWebhookSignatureService.class);
  private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

  private final CircleApiClient circleApiClient;
  private final Map<String, PublicKey> publicKeyCache = new ConcurrentHashMap<>();

  public CircleWebhookSignatureService(CircleApiClient circleApiClient) {
    this.circleApiClient = circleApiClient;
  }

  /**
   * @param rawBody   the exact, unmodified request body bytes as received - do
   *                  not
   *                  re-serialize a parsed object, or the signature will not
   *                  match.
   * @param signature base64 contents of the X-Circle-Signature header
   * @param keyId     contents of the X-Circle-Key-Id header
   */
  public boolean verify(byte[] rawBody, String signature, String keyId) {
    if (signature == null || signature.isBlank() || keyId == null || keyId.isBlank()) {
      return false;
    }
    try {
      PublicKey publicKey = publicKeyCache.computeIfAbsent(keyId, this::fetchPublicKey);
      Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
      verifier.initVerify(publicKey);
      verifier.update(rawBody);
      return verifier.verify(Base64.getDecoder().decode(signature));
    } catch (Exception e) {
      log.warn("Failed to verify Circle wallets webhook signature (keyId={}): {}", keyId, e.getMessage());
      return false;
    }
  }

  private PublicKey fetchPublicKey(String keyId) {
    try {
      JsonNode response = circleApiClient.get("/v2/notifications/publicKey/" + keyId);
      String base64Der = response.path("data").path("publicKey").asText();
      byte[] decoded = Base64.getDecoder().decode(base64Der);
      KeyFactory keyFactory = KeyFactory.getInstance("EC");
      return keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
    } catch (Exception e) {
      throw new IllegalStateException("Unable to fetch Circle notification public key " + keyId, e);
    }
  }
}
