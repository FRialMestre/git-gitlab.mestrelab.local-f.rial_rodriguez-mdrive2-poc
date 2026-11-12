package com.mdrive.api;

import java.util.Map;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@RestController
public class MiddlewareController {

    private final RestClient restClient;
    private final String middlewareUrl;
    private final Jackson2ObjectMapperBuilder objectMapperBuilder;
    private final String tokenExchangeUrl;
    private final String tokenExchangeClientId;
    private final String tokenExchangeClientSecret;
    private final String tokenExchangeRequestedIssuer;

    public MiddlewareController(
        RestClient restClient,
        @Value("${middleware.base-url:http://middleware:8080}") String middlewareUrl,
        Jackson2ObjectMapperBuilder objectMapperBuilder,
        @Value("${app.token-exchange.token-url}") String tokenExchangeUrl,
        @Value("${app.token-exchange.client-id}") String tokenExchangeClientId,
        @Value("${app.token-exchange.client-secret}") String tokenExchangeClientSecret,
        @Value("${app.token-exchange.requested-issuer}") String tokenExchangeRequestedIssuer) {
        this.restClient = restClient;
        this.middlewareUrl = middlewareUrl;
        this.objectMapperBuilder = objectMapperBuilder;
        this.tokenExchangeUrl = tokenExchangeUrl;
        this.tokenExchangeClientId = tokenExchangeClientId;
        this.tokenExchangeClientSecret = tokenExchangeClientSecret;
        this.tokenExchangeRequestedIssuer = tokenExchangeRequestedIssuer;
    }

    @GetMapping("/api/middleware/status")
    public ResponseEntity<Object> status(@AuthenticationPrincipal Jwt token) {
        return callMiddleware(token, "/api/middleware/status");
    }

    @GetMapping("/api/middleware/solvents")
    public ResponseEntity<Object> solvents(@AuthenticationPrincipal Jwt token) {
        return callMiddleware(token, "/api/middleware/solvents");
    }

    private ResponseEntity<Object> callMiddleware(Jwt token, String path) {
        try {
            if (!"topspin".equals(token.getClaimAsString("auth_source"))) {
                return ResponseEntity.ok(Map.of(
                    "status", "skipped",
                    "message", "Usuario local de KC1: no se solicita token para Spinpilot",
                    "user", token.getClaimAsString("preferred_username")));
            }

            String keycloak2Token = exchangeToken(token);
            return restClient.get()
                .uri(middlewareUrl + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + keycloak2Token)
                .retrieve()
                .toEntity(Object.class);
        } catch (RestClientResponseException error) {
            HttpStatusCode status = error.getStatusCode();
            try {
                Map responseBody = objectMapperBuilder.build()
                    .readValue(error.getResponseBodyAsString(), Map.class);
                return ResponseEntity.status(status).body(responseBody);
            } catch (Exception parsingError) {
                return ResponseEntity.status(status)
                    .body(Map.of("status", "error", "message", "El middleware rechazó la petición"));
            }
        }
    }

    private String exchangeToken(Jwt token) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange");
        form.add("client_id", tokenExchangeClientId);
        form.add("client_secret", tokenExchangeClientSecret);
        form.add("subject_token", token.getTokenValue());
        form.add("subject_token_type", "urn:ietf:params:oauth:token-type:access_token");
        form.add("requested_token_type", "urn:ietf:params:oauth:token-type:access_token");
        form.add("requested_issuer", tokenExchangeRequestedIssuer);
        Map exchange = restClient.post()
            .uri(tokenExchangeUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(Map.class);
        String accessToken = (String) exchange.get("access_token");
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("KC1 no devolvió un token externo de KC2");
        }
        return accessToken;
    }
}