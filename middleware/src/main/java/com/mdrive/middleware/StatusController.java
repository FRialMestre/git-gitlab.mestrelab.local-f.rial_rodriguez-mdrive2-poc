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
        @Value("${app.spinpilot.base-url:http://spinpilot:8080}") String spinpilotUrl) {
        this.restClient = restClient;
        this.spinpilotUrl = spinpilotUrl;
    }

    @GetMapping("/api/middleware/status")
    public ResponseEntity<Map> status(@AuthenticationPrincipal Jwt token) {
        try {
            return restClient.get()
                .uri(spinpilotUrl + "/api/spinpilot/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.getTokenValue())
                .retrieve()
                .toEntity(Map.class);
        } catch (RestClientResponseException error) {
            HttpStatusCode status = error.getStatusCode();
            return ResponseEntity.status(status)
                .body(Map.of("status", "error", "message", "Spinpilot rechazó el token"));
        }
    }

}