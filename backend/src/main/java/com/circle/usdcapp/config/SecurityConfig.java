package com.circle.usdcapp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Central HTTP security. There are two mutually-exclusive filter chains,
 * selected by whether an OIDC issuer is configured:
 *
 * <ul>
 *   <li><b>Production ({@code jwtSecurityFilterChain})</b> - active when
 *       {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} is set
 *       (see the {@code prod} profile). Validates JWT bearer tokens from your
 *       identity provider (Auth0 / Cognito / Okta / Entra / Keycloak). Health
 *       and webhook endpoints stay open (webhooks are protected by Circle/AWS
 *       signature verification instead); everything else requires a valid
 *       token.</li>
 *   <li><b>Local dev ({@code devSecurityFilterChain})</b> - active when no
 *       issuer is configured. Preserves the original lightweight behavior: the
 *       shared {@code X-App-Api-Key} header guards the API (via
 *       {@link ApiKeyAuthFilter}), and the H2 console works.</li>
 * </ul>
 *
 * The two conditions are exclusive as long as the issuer-uri is never the
 * literal string "false".
 */
@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfig {

    private static final String ISSUER_URI_PROPERTY = "spring.security.oauth2.resourceserver.jwt.issuer-uri";

    /** Paths that must stay public in every environment. */
    private static final String[] PUBLIC_PATHS = {"/api/health/**", "/api/webhooks/**"};

    // ---- Production: JWT resource server -----------------------------------

    @Bean
    @ConditionalOnProperty(name = ISSUER_URI_PROPERTY)
    public SecurityFilterChain jwtSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Security: OIDC issuer configured - enabling JWT resource-server authentication.");
        http
                // Uses the bean named "corsConfigurationSource" (below).
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    // ---- Local dev: shared X-App-Api-Key -----------------------------------

    @Bean
    @ConditionalOnProperty(name = ISSUER_URI_PROPERTY, havingValue = "false", matchIfMissing = true)
    public SecurityFilterChain devSecurityFilterChain(HttpSecurity http, AppProperties appProperties)
            throws Exception {
        log.warn("Security: no OIDC issuer configured - using DEV shared-key (X-App-Api-Key) auth. "
                + "Set OAUTH2_ISSUER_URI and run with the 'prod' profile before deploying.");
        http
                // Uses the bean named "corsConfigurationSource" (below).
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                // Allow the H2 console to render in a frame (dev only).
                .headers(h -> h.frameOptions(f -> f.disable()))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                // ApiKeyAuthFilter does the actual gating; it self-skips
                // /api/health, /api/webhooks and OPTIONS.
                .addFilterBefore(new ApiKeyAuthFilter(appProperties.getApiKey()),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // ---- Shared CORS -------------------------------------------------------

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties appProperties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(appProperties.getCors().getAllowedOrigins().split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
