#!/usr/bin/env bash
set -euo pipefail

KC_URL="${KC_URL:-http://localhost:8082}"
KC1_URL="${KC1_URL:-http://localhost:8081}"
KC_ADMIN_USER="${KC_ADMIN_USER:-admin}"
KC_ADMIN_PASSWORD="${KC_ADMIN_PASSWORD:-admin}"
BROKER_USERNAME="${BROKER_USERNAME:-usuario-kc2}"
REALM="keycloak2"

admin_token=$(curl -fsS -X POST "$KC_URL/realms/master/protocol/openid-connect/token" \
  -d "username=$KC_ADMIN_USER" \
  -d "password=$KC_ADMIN_PASSWORD" \
  -d grant_type=password \
  -d client_id=admin-cli | jq -r .access_token)

api() {
  curl -fsS -H "Authorization: Bearer $admin_token" "$@"
}

realm_management_id=$(api "$KC_URL/admin/realms/$REALM/clients?clientId=realm-management" | jq -r '.[0].id')
exchange_client_id=$(api "$KC_URL/admin/realms/$REALM/clients?clientId=mdrive-token-exchange" | jq -r '.[0].id')
spinpilot_client_id=$(api "$KC_URL/admin/realms/$REALM/clients?clientId=spinpilot-api" | jq -r '.[0].id')

api -X PUT "$KC_URL/admin/realms/$REALM/identity-provider/instances/keycloak1/management/permissions" \
  -H 'Content-Type: application/json' -d '{"enabled":true}' >/dev/null
api -X PUT "$KC_URL/admin/realms/$REALM/clients/$spinpilot_client_id/management/permissions" \
  -H 'Content-Type: application/json' -d '{"enabled":true}' >/dev/null

policy_id=$(api "$KC_URL/admin/realms/$REALM/clients/$realm_management_id/authz/resource-server/policy" \
  | jq -r '.[] | select(.name == "mdrive-token-exchange") | .id' | head -n 1)

if [[ -z "$policy_id" ]]; then
  policy_id=$(curl -fsS -X POST "$KC_URL/admin/realms/$REALM/clients/$realm_management_id/authz/resource-server/policy/client" \
    -H "Authorization: Bearer $admin_token" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"mdrive-token-exchange\",\"description\":\"Allow MDrive token exchange\",\"logic\":\"POSITIVE\",\"decisionStrategy\":\"UNANIMOUS\",\"clients\":[\"$exchange_client_id\"]}" \
    | jq -r .id)
fi

attach_policy() {
  local permission_id="$1"
  local permission
  permission=$(api "$KC_URL/admin/realms/$REALM/clients/$realm_management_id/authz/resource-server/permission/$permission_id")
  printf '%s' "$permission" | jq --arg policy "$policy_id" '.policies = [$policy]' > /tmp/token-exchange-permission.json
  api -X PUT "$KC_URL/admin/realms/$REALM/clients/$realm_management_id/authz/resource-server/permission/$permission_id" \
    -H 'Content-Type: application/json' -d @/tmp/token-exchange-permission.json >/dev/null
}

idp_permission=$(api "$KC_URL/admin/realms/$REALM/identity-provider/instances/keycloak1/management/permissions" | jq -r '.scopePermissions["token-exchange"]')
spinpilot_permission=$(api "$KC_URL/admin/realms/$REALM/clients/$spinpilot_client_id/management/permissions" | jq -r '.scopePermissions["token-exchange"]')
attach_policy "$idp_permission"
attach_policy "$spinpilot_permission"

kc1_admin_token=$(curl -fsS -X POST "$KC1_URL/realms/master/protocol/openid-connect/token" \
  -d "username=$KC_ADMIN_USER" \
  -d "password=$KC_ADMIN_PASSWORD" \
  -d grant_type=password \
  -d client_id=admin-cli | jq -r .access_token)
kc1_user_id=$(curl -fsS "$KC1_URL/admin/realms/mdrive/users?username=$BROKER_USERNAME" \
  -H "Authorization: Bearer $kc1_admin_token" | jq -r '.[0].id // empty')
kc2_user_id=$(api "$KC_URL/admin/realms/$REALM/users?username=$BROKER_USERNAME" | jq -r '.[0].id // empty')

if [[ -n "$kc1_user_id" && -n "$kc2_user_id" ]]; then
  federated_links=$(api "$KC_URL/admin/realms/$REALM/users/$kc2_user_id/federated-identity")
  if ! printf '%s' "$federated_links" | jq -e --arg user_id "$kc1_user_id" \
      '.[] | select(.identityProvider == "keycloak1" and .userId == $user_id)' >/dev/null; then
    curl -fsS -X POST "$KC_URL/admin/realms/$REALM/users/$kc2_user_id/federated-identity/keycloak1" \
      -H "Authorization: Bearer $admin_token" \
      -H 'Content-Type: application/json' \
      -d "{\"userId\":\"$kc1_user_id\",\"userName\":\"$BROKER_USERNAME\"}" >/dev/null
    echo "Linked $BROKER_USERNAME between keycloak1 and keycloak2."
  fi
fi

echo "Token exchange permissions configured for keycloak1 and spinpilot-api."