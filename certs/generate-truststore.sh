#!/usr/bin/env bash
set -euo pipefail

base_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
BRUKER_CA_FILE="${BRUKER_CA_FILE:?Define BRUKER_CA_FILE with the external Bruker CA certificate}"

if ! command -v keytool >/dev/null 2>&1; then
  echo "keytool is required to generate certs/truststore.p12" >&2
  exit 1
fi

rm -f "$base_dir/truststore.p12"
keytool -importcert -noprompt -trustcacerts \
  -alias bruker-ca \
  -file "$BRUKER_CA_FILE" \
  -keystore "$base_dir/truststore.p12" \
  -storetype PKCS12 \
  -storepass "${TRUSTSTORE_PASSWORD:-changeit}"

echo "Generated truststore for the external Bruker CA."