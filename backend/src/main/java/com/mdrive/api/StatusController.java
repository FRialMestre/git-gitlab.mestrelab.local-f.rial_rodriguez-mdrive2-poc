package com.mdrive.api;

import java.time.Instant;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {

    @GetMapping("/api/status")
    public Map<String, Object> status(@AuthenticationPrincipal Jwt token) {
        return Map.of(
            "status", "ok",
            "message", "Token validated by the backend",
            "user", token.getClaimAsString("preferred_username"),
            "subject", token.getSubject(),
            "issuer", token.getIssuer().toString(),
            "checkedAt", Instant.now().toString());
    }
}