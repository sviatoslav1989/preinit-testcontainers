#!/usr/bin/env bash

set -euo pipefail
REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$REPO_ROOT"

sudo apt-get update -qq
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq telnet default-mysql-client ripgrep

mkdir -p "$HOME/.gradle"
sudo chown -R "$(id -u):$(id -g)" "$HOME/.gradle" 2>/dev/null || true
sudo chown -R "$(id -u):$(id -g)" "$REPO_ROOT/.gradle" 2>/dev/null || true
chmod +x gradlew 2>/dev/null || true

