package com.mdrive.api;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class Keycloak2ProvisioningService {

    private static final String TOKEN_EXCHANGE_GRANT =
        "urn:ietf:params:oauth:grant-type:token-exchange";

    private final RestClient restClient;
    private final String adminBaseUrl;
    private final String provisioningTokenUrl;
    private final String provisionerClientId;
    private final String provisionerClientSecret;
    private final String identityProviderAlias;

    public Keycloak2ProvisioningService(
        RestClient restClient,
        @Value("${app.kc2.admin-base-url}") String adminBaseUrl,
        @Value("${app.kc2.provisioning-token-url}") String provisioningTokenUrl,
        @Value("${app.kc2.provisioner-client-id}") String provisionerClientId,
        @Value("${app.kc2.provisioner-client-secret}") String provisionerClientSecret,
        @Value("${app.kc2.idp-alias}") String identityProviderAlias) {
        this.restClient = restClient;
        this.adminBaseUrl = trimTrailingSlash(adminBaseUrl);
        this.provisioningTokenUrl = provisioningTokenUrl;
        this.provisionerClientId = provisionerClientId;
        this.provisionerClientSecret = provisionerClientSecret;
        this.identityProviderAlias = identityProviderAlias;
    }

    public void ensureFederatedLink(Jwt token) {
        String username = requiredClaim(token, "preferred_username");
        String subject = requiredClaim(token, "sub");
        String adminToken = requestAdminToken();

        List<Map<String, Object>> users = findUsers(adminToken, username);
        if (users.size() != 1) {
            throw new IllegalStateException(
                "KC2 must contain exactly one user with username " + username);
        }

        Map<String, Object> user = users.get(0);
        String userId = requiredValue(user, "id", "KC2 did not return the user identifier");
        List<Map<String, Object>> links = findFederatedLinks(adminToken, userId);

        for (Map<String, Object> link : links) {
            if (!identityProviderAlias.equals(link.get("identityProvider"))) {
                continue;
            }
            String linkedSubject = requiredValue(
                link, "userId", "KC2 returned a federated link without userId");
            String linkedUsername = requiredValue(
                link, "userName", "KC2 returned a federated link without userName");
            if (!subject.equals(linkedSubject) || !username.equals(linkedUsername)) {
                throw new IllegalStateException(
                    "The KC2 federated link does not match the KC1 user");
            }
            return;
        }

        Map<String, String> link = Map.of(
            "identityProvider", identityProviderAlias,
            "userId", subject,
            "userName", username);
        restClient.post()
            .uri(adminUrl("/users/" + userId + "/federated-identity/" + identityProviderAlias))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(link)
            .retrieve()
            .toBodilessEntity();
    }

    private String requestAdminToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", provisionerClientId);
        form.add("client_secret", provisionerClientSecret);
        Map<String, Object> response = restClient.post()
            .uri(provisioningTokenUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
        String accessToken = requiredValue(
            response, "access_token", "KC2 did not return a token for the provisioner");
        return accessToken;
    }

    private List<Map<String, Object>> findUsers(String adminToken, String username) {
        String uri = UriComponentsBuilder.fromUriString(adminUrl("/users"))
            .queryParam("username", username)
            .queryParam("exact", true)
            .build()
            .toUriString();
        return restClient.get()
            .uri(uri)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    }

    private List<Map<String, Object>> findFederatedLinks(String adminToken, String userId) {
        return restClient.get()
            .uri(adminUrl("/users/" + userId + "/federated-identity"))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    }

    private String adminUrl(String suffix) {
        return adminBaseUrl + suffix;
    }

    private static String requiredClaim(Jwt token, String name) {
        String value = token.getClaimAsString(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("The KC1 token does not contain " + name);
        }
        return value;
    }

    private static String requiredValue(
        Map<String, ?> values,
        String name,
        String errorMessage) {
        Object value = values.get(name);
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new IllegalStateException(errorMessage);
        }
        return stringValue;
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
