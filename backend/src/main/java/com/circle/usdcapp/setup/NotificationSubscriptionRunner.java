package com.circle.usdcapp.setup;

import com.circle.usdcapp.client.CircleApiClient;
import com.circle.usdcapp.config.CircleProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Convenience runner that registers this app's <em>wallets</em> webhook endpoint
 * with Circle's v2 notification service, so you don't have to click through the
 * Circle Console. v2 (Circle Wallets / W3S) notifications are a direct HTTPS
 * POST from Circle - there is no SNS-style confirmation handshake - so the only
 * requirement is that {@code circle.webhook.endpoint} is publicly reachable over
 * HTTPS and returns 2xx.
 *
 * <p>Run once (after exposing your local server with e.g. ngrok):
 *
 * <pre>
 *   ./mvnw spring-boot:run \
 *     -Dspring-boot.run.arguments="\
 *       --circle.setup.register-webhook=true \
 *       --circle.webhook.endpoint=https://YOUR-ID.ngrok-free.app/api/webhooks/circle/wallets"
 * </pre>
 *
 * <p>It prints the created subscription id and exits. Circle Mint (v1 / SNS)
 * notifications are <strong>not</strong> registered here - those are set up from
 * the Circle Mint side of the Console by subscribing the
 * {@code /api/webhooks/circle/mint} URL to your Mint notification topic, after
 * which this app auto-confirms the SNS SubscriptionConfirmation.
 *
 * See https://developers.circle.com/api-reference/wallets/common/create-subscription
 */
@Component
@ConditionalOnProperty(name = "circle.setup.register-webhook", havingValue = "true")
public class NotificationSubscriptionRunner implements CommandLineRunner {

    private final CircleProperties circleProperties;
    private final CircleApiClient circleApiClient;

    public NotificationSubscriptionRunner(CircleProperties circleProperties, CircleApiClient circleApiClient) {
        this.circleProperties = circleProperties;
        this.circleApiClient = circleApiClient;
    }

    @Override
    public void run(String... args) {
        String endpoint = circleProperties.getWebhook().getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            System.out.println("=================================================================");
            System.out.println(" circle.webhook.endpoint is not set. Pass the public HTTPS URL of");
            System.out.println(" this app's wallets webhook, e.g.:");
            System.out.println("   --circle.webhook.endpoint=https://YOUR-ID.ngrok-free.app/api/webhooks/circle/wallets");
            System.out.println("=================================================================");
            System.exit(1);
            return;
        }

        List<String> types = Arrays.stream(circleProperties.getWebhook().getNotificationTypes().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        System.out.println("=================================================================");
        System.out.println(" Registering v2 (Wallets) notification subscription with Circle");
        System.out.println("   endpoint          : " + endpoint);
        System.out.println("   notificationTypes : " + types);

        JsonNode response = circleApiClient.post(
                "/v2/notifications/subscriptions",
                java.util.Map.of(
                        "endpoint", endpoint,
                        "notificationTypes", types));

        JsonNode data = response.path("data");
        System.out.println(" Created subscription:");
        System.out.println("   id         : " + data.path("id").asText("(none)"));
        System.out.println("   enabled    : " + data.path("enabled").asText("(none)"));
        System.out.println("   restricted : " + data.path("restricted").asText("(none)"));
        System.out.println(" Circle will now POST wallet notifications to your endpoint.");
        System.out.println(" Verify deliveries under Webhook Logs in the Circle Console.");
        System.out.println("=================================================================");

        System.exit(0);
    }
}
