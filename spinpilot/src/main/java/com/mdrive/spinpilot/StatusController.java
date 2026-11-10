package com.mdrive.spinpilot;

import java.time.Instant;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
public class StatusController {

    private static final Logger logger = LoggerFactory.getLogger(StatusController.class);

    @GetMapping("/api/spinpilot/status")
    public Map<String, Object> status(@AuthenticationPrincipal Jwt token) {
        String user = token.getClaimAsString("preferred_username");
        String authSource = token.getClaimAsString("auth_source");
        String issuer = token.getIssuer().toString();

        logger.info("Peticion recibida en Spinpilot: user={}, issuer={}, auth_source={}",
            user, issuer, authSource);

        if (!"keycloak2".equals(token.getClaimAsString("auth_source"))) {
            logger.warn("Peticion rechazada por Spinpilot: user={}, auth_source={}, reason=origen no permitido",
                user, authSource);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El usuario no procede de Keycloak 2");
        }

        logger.info("Peticion aceptada por Spinpilot: user={}, auth_source={}", user, authSource);

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