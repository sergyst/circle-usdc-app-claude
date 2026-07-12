package com.circle.usdcapp.service;

import com.circle.usdcapp.client.CircleApiClient;
import com.circle.usdcapp.config.CircleProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Circle's Developer-Controlled Wallets API requires every mutating request
 * (create wallet, transfer, contract execution, ...) to carry a fresh
 * "entitySecretCiphertext": the raw 32-byte entity secret encrypted with
 * Circle's current RSA public key using OAEP/SHA-256. The ciphertext must be
 * unique per request (Circle rejects reuse) which is why this is computed
 * on demand rather than cached.
 * <p>
 * See: https://developers.circle.com/w3s/entity-secret-management
 */
@Service
public class EntitySecretCipherService {

    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    private final CircleApiClient circleApiClient;
    private final CircleProperties circleProperties;

    private volatile PublicKey cachedPublicKey;

    public EntitySecretCipherService(CircleApiClient circleApiClient, CircleProperties circleProperties) {
        this.circleApiClient = circleApiClient;
        this.circleProperties = circleProperties;
    }

    /** Returns a fresh, single-use entitySecretCiphertext (base64). */
    public String generateCiphertext() {
        try {
            PublicKey publicKey = getPublicKey();
            byte[] entitySecretBytes = HexFormat.of().parseHex(circleProperties.getEntitySecret());

            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                    "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, javax.crypto.spec.PSource.PSpecified.DEFAULT);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepParams);

            byte[] encrypted = cipher.doFinal(entitySecretBytes);
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate entitySecretCiphertext. Check circle.entity-secret " +
                    "is a valid 64-character hex string and that the entity's public key has been registered with " +
                    "Circle. Cause: " + e.getMessage(), e);
        }
    }

    private PublicKey getPublicKey() throws Exception {
        if (cachedPublicKey != null) {
            return cachedPublicKey;
        }
        JsonNode response = circleApiClient.get("/v1/w3s/config/entity/publicKey");
        String pem = response.path("data").path("publicKey").asText();
        cachedPublicKey = parsePem(pem);
        return cachedPublicKey;
    }

    private PublicKey parsePem(String pem) throws Exception {
        String cleaned = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(cleaned);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
    }

    /**
     * One-time bootstrap step: registers circleProperties.getEntitySecret() as
     * this API account's entity secret and returns the base64 recovery file
     * contents Circle issues in exchange. Save that file somewhere safe -
     * Circle Support needs it to recover wallet access if the entity secret
     * is ever lost. Run via EntitySecretRegistrationRunner, not at normal boot.
     */
    public String registerEntitySecret() {
        String ciphertext = generateCiphertext();
        JsonNode response = circleApiClient.post("/v1/w3s/config/entity/entitySecret", java.util.Map.of(
                "entitySecretCiphertext", ciphertext
        ));
        return response.path("data").path("recoveryFile").asText();
    }
}