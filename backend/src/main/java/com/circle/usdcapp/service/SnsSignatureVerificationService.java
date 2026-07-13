package com.circle.usdcapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Circle Mint (the businessAccount / classic Payments &amp; Payouts API) does
 * NOT use the same X-Circle-Signature scheme as Wallets notifications.
 * Instead it delivers notifications as AWS SNS messages: Circle's dashboard
 * subscribes your HTTPS endpoint directly to an SNS topic, and every POST
 * body is a standard SNS envelope ({@code Type}, {@code Message},
 * {@code Signature}, {@code SigningCertURL}, ...) with the Circle-specific
 * payload JSON-encoded inside the {@code Message} field.
 * <p>
 * This verifies that envelope the standard AWS way: fetch the X.509 cert
 * from {@code SigningCertURL} (restricted to *.amazonaws.com to prevent a
 * forged URL from pointing verification at an attacker-controlled cert),
 * rebuild the canonical "string to sign", and verify it against the
 * envelope's {@code Signature}.
 *
 * See:
 * https://developers.circle.com/circle-mint/references/webhook-notifications
 * and AWS's "Verifying the signatures of Amazon SNS messages" guide.
 */
@Service
@Slf4j
public class SnsSignatureVerificationService {


  // Only ever fetch signing certs from Amazon's own SNS domains.
  private static final Pattern ALLOWED_CERT_HOST = Pattern.compile("^sns\\.[a-z0-9-]+\\.amazonaws\\.com(\\.cn)?$");

  private final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5))
      .build();

  private final Map<String, X509Certificate> certCache = new ConcurrentHashMap<>();

  /**
   * Fetches SubscribeURL to activate a brand-new SNS subscription, as AWS
   * requires. Circle's dashboard triggers this automatically the first time
   * you point a Mint notification subscription at a new endpoint.
   */
  public boolean confirmSubscription(String subscribeUrl) {
    if (!isTrustedAwsHost(subscribeUrl)) {
      log.warn("Refusing to confirm SNS subscription at untrusted host: {}", subscribeUrl);
      return false;
    }
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(subscribeUrl))
          .timeout(Duration.ofSeconds(5))
          .GET()
          .build();
      HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
      return response.statusCode() == 200;
    } catch (Exception e) {
      log.warn("Failed to confirm SNS subscription: {}", e.getMessage());
      return false;
    }
  }

  private boolean isTrustedAwsHost(String url) {
    try {
      URI uri = URI.create(url);
      return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
          && ALLOWED_CERT_HOST.matcher(uri.getHost()).matches();
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * @param envelope the parsed top-level SNS JSON body (Type, Message, Signature,
   *                 SigningCertURL, ...)
   */
  public boolean verify(JsonNode envelope) {
    try {
      String type = envelope.path("Type").asText();
      if (!"Notification".equals(type)) {
        // SubscriptionConfirmation / UnsubscribeConfirmation use a different
        // signed field set; Circle Mint notifications are always type "Notification".
        log.warn("Unexpected SNS message Type '{}', refusing to process", type);
        return false;
      }

      String signingCertUrl = envelope.path("SigningCertURL").asText();
      if (!isTrustedAwsHost(signingCertUrl)) {
        log.warn("Refusing to fetch SNS signing cert from untrusted host: {}", signingCertUrl);
        return false;
      }

      X509Certificate cert = certCache.computeIfAbsent(signingCertUrl, this::fetchCertificate);
      PublicKey publicKey = cert.getPublicKey();

      String signatureVersion = envelope.path("SignatureVersion").asText("1");
      String algorithm = "2".equals(signatureVersion) ? "SHA256withRSA" : "SHA1withRSA";

      String canonicalString = buildCanonicalString(envelope);
      byte[] signatureBytes = Base64.getDecoder().decode(envelope.path("Signature").asText());

      Signature verifier = Signature.getInstance(algorithm);
      verifier.initVerify(publicKey);
      verifier.update(canonicalString.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return verifier.verify(signatureBytes);
    } catch (Exception e) {
      log.warn("Failed to verify SNS signature: {}", e.getMessage());
      return false;
    }
  }

  /** Builds AWS's "string to sign" for a Notification-type SNS message. */
  private String buildCanonicalString(JsonNode envelope) {
    StringBuilder sb = new StringBuilder();
    appendField(sb, envelope, "Message");
    appendField(sb, envelope, "MessageId");
    appendField(sb, envelope, "Subject"); // only included if present in the payload
    appendField(sb, envelope, "Timestamp");
    appendField(sb, envelope, "TopicArn");
    appendField(sb, envelope, "Type");
    return sb.toString();
  }

  private void appendField(StringBuilder sb, JsonNode envelope, String field) {
    if (envelope.has(field) && !envelope.get(field).isNull()) {
      sb.append(field).append('\n').append(envelope.get(field).asText()).append('\n');
    }
  }

  private X509Certificate fetchCertificate(String url) {
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url))
          .timeout(Duration.ofSeconds(5))
          .GET()
          .build();
      HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() != 200) {
        throw new IllegalStateException("Unexpected status " + response.statusCode() + " fetching " + url);
      }
      CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
      return (X509Certificate) certificateFactory.generateCertificate(
          new java.io.ByteArrayInputStream(response.body()));
    } catch (Exception e) {
      throw new IllegalStateException("Unable to fetch SNS signing certificate from " + url, e);
    }
  }
}
