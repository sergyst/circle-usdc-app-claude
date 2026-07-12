package com.circle.usdcapp.setup;

import com.circle.usdcapp.config.CircleProperties;
import com.circle.usdcapp.service.EntitySecretCipherService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Run once, before the app is used normally, to generate and register a new
 * Circle entity secret:
 *
 *   ./mvnw spring-boot:run -Dspring-boot.run.arguments=--circle.setup.register-entity-secret=true
 *
 * This prints the generated hex entity secret (put it in CIRCLE_ENTITY_SECRET)
 * and writes recovery-file.b64 (store it somewhere safe - Circle Support
 * needs it to recover wallet access if the secret is ever lost). Re-running
 * this against an account that already has an entity secret registered will
 * fail; only do this once per Circle account/environment.
 */
@Component
@ConditionalOnProperty(name = "circle.setup.register-entity-secret", havingValue = "true")
public class EntitySecretRegistrationRunner implements CommandLineRunner {

    private final CircleProperties circleProperties;
    private final EntitySecretCipherService cipherService;

    public EntitySecretRegistrationRunner(CircleProperties circleProperties, EntitySecretCipherService cipherService) {
        this.circleProperties = circleProperties;
        this.cipherService = cipherService;
    }

    @Override
    public void run(String... args) throws Exception {
        String secret = circleProperties.getEntitySecret();
        if (secret == null || secret.isBlank() || secret.contains("REPLACE")) {
            byte[] randomBytes = new byte[32];
            new SecureRandom().nextBytes(randomBytes);
            secret = HexFormat.of().formatHex(randomBytes);
            circleProperties.setEntitySecret(secret);
        }

        System.out.println("=================================================================");
        System.out.println(" Generated entity secret (save this now, it will not be shown again):");
        System.out.println(" " + secret);
        System.out.println(" Put it in your environment as CIRCLE_ENTITY_SECRET before next boot.");
        System.out.println("=================================================================");

        String recoveryFileBase64 = cipherService.registerEntitySecret();
        Path outPath = Path.of("entity-secret-recovery-file.b64");
        Files.writeString(outPath, recoveryFileBase64);

        System.out.println(" Entity secret registered with Circle.");
        System.out.println(" Recovery file written to: " + outPath.toAbsolutePath());
        System.out.println(" Store it somewhere safe and secret - required for account recovery.");
        System.out.println("=================================================================");

        System.exit(0);
    }
}
