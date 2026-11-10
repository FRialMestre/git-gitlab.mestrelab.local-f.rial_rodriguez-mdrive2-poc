package com.mdrive.api;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
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

    public MiddlewareController(
        RestClient restClient,
        @Value("${middleware.base-url:http://middleware:8080}") String middlewareUrl,
        Jackson2ObjectMapperBuilder objectMapperBuilder) {
        this.restClient = restClient;
        this.middlewareUrl = middlewareUrl;
        this.objectMapperBuilder = objectMapperBuilder;
    }

    @GetMapping("/api/middleware/status")
    public ResponseEntity<Map> status(@AuthenticationPrincipal Jwt token) {
        try {
            return restClient.get()
                .uri(middlewareUrl + "/api/middleware/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.getTokenValue())
                .retrieve()
                .toEntity(Map.class);
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
}