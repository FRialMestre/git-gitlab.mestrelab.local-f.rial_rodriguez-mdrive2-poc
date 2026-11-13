package com.mdrive.api;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
public class MiddlewareController {

    private static final Logger logger = LoggerFactory.getLogger(MiddlewareController.class);

    private final RestClient restClient;
    private final Keycloak2ProvisioningService provisioningService;
    private final String middlewareUrl;
    private final Jackson2ObjectMapperBuilder objectMapperBuilder;
    private final String tokenExchangeUrl;
    private final String tokenExchangeClientId;
    private final String tokenExchangeClientSecret;
    private final String tokenExchangeSubjectIssuer;
    private final String tokenExchangeAudience;

    public MiddlewareController(
        RestClient restClient,
        Keycloak2ProvisioningService provisioningService,
        @Value("${middleware.base-url:http://middleware:8080}") String middlewareUrl,
        Jackson2ObjectMapperBuilder objectMapperBuilder,
        @Value("${app.token-exchange.token-url}") String tokenExchangeUrl,
        @Value("${app.token-exchange.client-id}") String tokenExchangeClientId,
        @Value("${app.token-exchange.client-secret}") String tokenExchangeClientSecret,
        @Value("${app.token-exchange.subject-issuer}") String tokenExchangeSubjectIssuer,
        @Value("${app.token-exchange.audience}") String tokenExchangeAudience) {
        this.restClient = restClient;
        this.provisioningService = provisioningService;
        this.middlewareUrl = middlewareUrl;
        this.objectMapperBuilder = objectMapperBuilder;
        this.tokenExchangeUrl = tokenExchangeUrl;
        this.tokenExchangeClientId = tokenExchangeClientId;
        this.tokenExchangeClientSecret = tokenExchangeClientSecret;
        this.tokenExchangeSubjectIssuer = tokenExchangeSubjectIssuer;
        this.tokenExchangeAudience = tokenExchangeAudience;
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
            String username = token.getClaimAsString("preferred_username");
            if (username == null || username.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "El token KC1 no contiene preferred_username"));
            }

            logger.info("Solicitud {} recibida para el usuario {}", path, username);
            logKeycloak1TokenHeader(token);
            provisioningService.ensureFederatedLink(token);
            logger.info("Provisioning de KC2 completado para el usuario {}", username);
            String keycloak2Token = exchangeToken(token);
            logger.info("Token exchange de KC2 completado para el usuario {}", username);
            logKeycloak2TokenClaims(keycloak2Token);
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
                logger.warn(
                    "KC2 rechazó la petición: status={}, error={}, description={}",
                    status.value(),
                    responseBody.get("error"),
                    responseBody.get("error_description"));
                return ResponseEntity.status(status).body(responseBody);
            } catch (Exception parsingError) {
                logger.warn("KC2 rechazó la petición con status {}", status.value());
                return ResponseEntity.status(status)
                    .body(Map.of("status", "error", "message", "El middleware rechazó la petición"));
            }
        } catch (IllegalStateException error) {
            return ResponseEntity.status(502).body(Map.of(
                "status", "error",
                "message", error.getMessage()));
        }
    }

    private void logKeycloak1TokenHeader(Jwt token) {
        try {
            String[] parts = token.getTokenValue().split("\\.", -1);
            if (parts.length != 3) {
                logger.warn("El token KC1 no tiene formato JWT");
                return;
            }
            String header = new String(
                Base64.getUrlDecoder().decode(parts[0]),
                StandardCharsets.UTF_8);
            Map<String, Object> headerValues = objectMapperBuilder.build()
                .readValue(header, Map.class);
            logger.info(
                "Cabecera del token KC1: alg={}, kid={}, typ={}",
                headerValues.get("alg"),
                headerValues.get("kid"),
                headerValues.get("typ"));
            logger.info(
                "Claims de validación del token KC1: iss={}, aud={}, sub={}, azp={}",
                token.getIssuer(),
                token.getAudience(),
                token.getSubject(),
                token.getClaimAsString("azp"));
        } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException error) {
            logger.warn("No se pudo leer la cabecera del token KC1: {}", error.getMessage());
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
        form.add("subject_issuer", tokenExchangeSubjectIssuer);
        form.add("audience", tokenExchangeAudience);
        Map exchange = restClient.post()
            .uri(tokenExchangeUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(Map.class);
        String accessToken = (String) exchange.get("access_token");
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("KC2 no devolvió un token de acceso");
        }
        return accessToken;
    }

    private void logKeycloak2TokenClaims(String accessToken) {
        try {
            logger.warn("Token KC2 recibido: {}", accessToken);
            String[] parts = accessToken.split("\\.", -1);
            if (parts.length != 3) {
                logger.warn("KC2 devolvió un token que no tiene formato JWT");
                return;
            }
            String payload = new String(
                Base64.getUrlDecoder().decode(parts[1]),
                StandardCharsets.UTF_8);
            Map<String, Object> claims = objectMapperBuilder.build()
                .readValue(payload, Map.class);
            logger.info(
                "Claims del token KC2: iss={}, aud={}, sub={}, azp={}, preferred_username={}, scope={}, exp={}",
                claims.get("iss"),
                claims.get("aud"),
                claims.get("sub"),
                claims.get("azp"),
                claims.get("preferred_username"),
                claims.get("scope"),
                claims.get("exp"));
        } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException error) {
            logger.warn("No se pudieron leer los claims del token KC2: {}", error.getMessage());
        }
    }
}