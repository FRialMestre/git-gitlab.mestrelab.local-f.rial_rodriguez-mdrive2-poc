#!/usr/bin/env bash
set -euo pipefail

remote_user="${REMOTE_USER:-developer}"
remote_host="${REMOTE_HOST:-10.109.128.75}"
remote_dir="${REMOTE_DIR:-/home/developer/mdrive-frontend+keycloak1+keycloak2}"
script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ssh_target="$remote_user@$remote_host"

echo "Creating remote directory: $ssh_target:$remote_dir"
ssh -o BatchMode=yes "$ssh_target" "mkdir -p '$remote_dir'"

echo "Synchronizing project files"
rsync -az --human-readable \
	--exclude='.git/' \
	--exclude='backend/target/' \
	--exclude='middleware/target/' \
	--exclude='spinpilot/target/' \
	"$script_dir/" "$ssh_target:$remote_dir/"

echo "Validating remote Docker Compose configuration"
ssh -o BatchMode=yes "$ssh_target" \
	"cd '$remote_dir' && docker compose config --services && docker compose config >/dev/null"

echo "Synchronization completed successfully"
