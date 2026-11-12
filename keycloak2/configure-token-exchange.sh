#!/usr/bin/env bash
set -euo pipefail

KC_BRUKER_URL="${KC_BRUKER_URL:-https://mdrive-topspin50-win-dev.mestrelab.local:8443}"
KC_BRUKER_ADMIN_USER="${KC_BRUKER_ADMIN_USER:-adminkc}"
KC_BRUKER_ADMIN_PASSWORD="${KC_BRUKER_ADMIN_PASSWORD:-p3oqcvSyKQCydvHTu9GX}"
BRUKER_REALM="Bruker"
BROKER_USERNAME="${BROKER_USERNAME:-usuario-kc2}"


KC_MDRIVE_URL="${KC_MDRIVE_URL:-https://mdrive2-poc-keycloak.mestrelab.local}"
KC_MDRIVE_ADMIN_USER="${KC_MDRIVE_ADMIN_USER:-admin}"
KC_MDRIVE_ADMIN_PASSWORD="${KC_MDRIVE_ADMIN_PASSWORD:-admin}"
MDRIVE_REALM="mdrive"

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
cert_dir="$script_dir/../certs"

BRUKER_CA_FILE="${BRUKER_CA_FILE:?Define BRUKER_CA_FILE with the CA that signs the external Bruker Keycloak}"
MDRIVE_CA_FILE="${MDRIVE_CA_FILE:-}"
permission_file=$(mktemp)
trap 'rm -f "$permission_file"' EXIT

mdrive_curl_args=()
if [[ -n "$MDRIVE_CA_FILE" ]]; then
  mdrive_curl_args+=(--cacert "$MDRIVE_CA_FILE")
fi

bruker_admin_token=$(curl -fsS --cacert "$BRUKER_CA_FILE" -X POST "$KC_BRUKER_URL/realms/master/protocol/openid-connect/token" \
  -d "username=$KC_BRUKER_ADMIN_USER" \
  -d "password=$KC_BRUKER_ADMIN_PASSWORD" \
  -d grant_type=password \
  -d client_id=admin-cli | jq -r .access_token)

api() {
  curl -fsS --cacert "$BRUKER_CA_FILE" -H "Authorization: Bearer $bruker_admin_token" "$@"
}

realm_management_id=$(api "$KC_BRUKER_URL/admin/realms/$BRUKER_REALM/clients?clientId=realm-management" | jq -r '.[0].id')
exchange_client_id=$(api "$KC_BRUKER_URL/admin/realms/$BRUKER_REALM/clients?clientId=mdrive-token-exchange" | jq -r '.[0].id')
spinpilot_client_id=$(api "$KC_BRUKER_URL/admin/realms/$BRUKER_REALM/clients?clientId=spinpilot-api" | jq -r '.[0].id')

api -X PUT "$KC_BRUKER_URL/admin/realms/$BRUKER_REALM/identity-provider/instances/keycloak1/management/permissions" \
  -H 'Content-Type: application/json' -d '{"enabled":true}' >/dev/null
api -X PUT "$KC_BRUKER_URL/admin/realms/$BRUKER_REALM/clients/$spinpilot_client_id/management/permissions" \
  -H 'Content-Type: application/json' -d '{"enabled":true}' >/dev/null

policy_id=$(api "$KC_BRUKER_URL/admin/realms/$BRUKER_REALM/clients/$realm_management_id/authz/resource-server/policy" \
  | jq -r '.[] | select(.name == "mdrive-token-exchange") | .id' | head -n 1)

if [[ -z "$policy_id" ]]; then
  policy_id=$(curl -fsS --cacert "$BRUKER_CA_FILE" -X POST "$KC_BRUKER_URL/admin/realms/$BRUKER_REALM/clients/$realm_management_id/authz/resource-server/policy/client" \
    -H "Authorization: Bearer $bruker_admin_token" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"mdrive-token-exchange\",\"description\":\"Allow MDrive token exchange\",\"logic\":\"POSITIVE\",\"decisionStrategy\":\"UNANIMOUS\",\"clients\":[\"$exchange_client_id\"]}" \
    | jq -r .id)
fi

attach_policy() {
  local permission_id="$1"
  local permission
  permission=$(api "$KC_BRUKER_URL/admin/realms/$BRUKER_REALM/clients/$realm_management_id/authz/resource-server/permission/$permission_id")
  printf '%s' "$permission" |
    jq --arg policy "$policy_id" '.policies = ((.policies // []) + [$policy] | unique)' > "$permission_file"
  api -X PUT "$KC_BRUKER_URL/admin/realms/$BRUKER_REALM/clients/$realm_management_id/authz/resource-server/permission/$permission_id" \
    -H 'Content-Type: application/json' -d @"$permission_file" >/dev/null
}

idp_permission=$(api "$KC_BRUKER_URL/admin/realms/$BRUKER_REALM/identity-provider/instances/keycloak1/management/permissions" | jq -r '.scopePermissions["token-exchange"]')
spinpilot_permission=$(api "$KC_BRUKER_URL/admin/realms/$BRUKER_REALM/clients/$spinpilot_client_id/management/permissions" | jq -r '.scopePermissions["token-exchange"]')
attach_policy "$idp_permission"
attach_policy "$spinpilot_permission"

mdrive_admin_token=$(curl -fsS "${mdrive_curl_args[@]}" -X POST "$KC_MDRIVE_URL/realms/master/protocol/openid-connect/token" \
  -d "username=$KC_MDRIVE_ADMIN_USER" \
  -d "password=$KC_MDRIVE_ADMIN_PASSWORD" \
  -d grant_type=password \
  -d client_id=admin-cli | jq -r .access_token)
mdrive_user_id=$(curl -fsS "${mdrive_curl_args[@]}" "$KC_MDRIVE_URL/admin/realms/$MDRIVE_REALM/users?username=$BROKER_USERNAME" \
  -H "Authorization: Bearer $mdrive_admin_token" | jq -r '.[0].id // empty')
kc2_user_id=$(api "$KC_BRUKER_URL/admin/realms/$BRUKER_REALM/users?username=$BROKER_USERNAME" | jq -r '.[0].id // empty')

if [[ -n "$mdrive_user_id" && -n "$kc2_user_id" ]]; then
  federated_links=$(api "$KC_BRUKER_URL/admin/realms/$BRUKER_REALM/users/$kc2_user_id/federated-identity")
  if ! printf '%s' "$federated_links" | jq -e --arg user_id "$mdrive_user_id" \
      '.[] | select(.identityProvider == "keycloak1" and .userId == $user_id)' >/dev/null; then
    curl -fsS --cacert "$BRUKER_CA_FILE" -X POST "$KC_BRUKER_URL/admin/realms/$BRUKER_REALM/users/$kc2_user_id/federated-identity/keycloak1" \
      -H "Authorization: Bearer $bruker_admin_token" \
      -H 'Content-Type: application/json' \
      -d "{\"userId\":\"$mdrive_user_id\",\"userName\":\"$BROKER_USERNAME\"}" >/dev/null
    echo "Linked $BROKER_USERNAME between keycloak1 and keycloak2."
  fi
fi

echo "Token exchange permissions configured for keycloak1 and spinpilot-api."