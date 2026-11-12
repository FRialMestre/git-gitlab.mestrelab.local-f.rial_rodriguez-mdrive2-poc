package com.mdrive.middleware;

import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@RestController
public class StatusController {

    private final RestClient restClient;
    private final String spinpilotUrl;

    public StatusController(
        RestClient restClient,
        @Value("${app.spinpilot.base-url:https://mdrive-topspin50-win-dev.mestrelab.local:8017}") String spinpilotUrl) {
        this.restClient = restClient;
        this.spinpilotUrl = spinpilotUrl;
    }

    @GetMapping("/api/middleware/status")
    public ResponseEntity<Object> status(@AuthenticationPrincipal Jwt token) {
        return callSpinpilot(token, "/api/spinpilot/status");
    }

    @GetMapping("/api/middleware/solvents")
    public ResponseEntity<Object> solvents(@AuthenticationPrincipal Jwt token) {
        return callSpinpilot(token, "/solvents");
    }

    private ResponseEntity<Object> callSpinpilot(Jwt token, String path) {
        try {
            return restClient.get()
                .uri(spinpilotUrl + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.getTokenValue())
                .retrieve().toEntity(Object.class);
        } catch (RestClientResponseException error) {
            HttpStatusCode status = error.getStatusCode();
            return ResponseEntity.status(status)
                .body(Map.of("status", "error", "message", "Spinpilot rechazó el token"));
        }
    }

}