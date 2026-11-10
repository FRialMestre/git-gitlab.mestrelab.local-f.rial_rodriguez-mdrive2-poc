package com.mdrive.spinpilot;

import java.time.Instant;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {

    @GetMapping("/api/spinpilot/status")
    public Map<String, Object> status(@AuthenticationPrincipal Jwt token) {
        if (!"keycloak2".equals(token.getClaimAsString("auth_source"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El usuario no procede de Keycloak 2");
        }

        return Map.of(
            "application", "spinpilot",
            "status", "ok",
            "message", "Usuario de Keycloak 2 validado en Spinpilot",
            "user", token.getClaimAsString("preferred_username"),
            "subject", token.getSubject(),
            "issuer", token.getIssuer().toString(),
            "checkedAt", Instant.now().toString());
    }
}