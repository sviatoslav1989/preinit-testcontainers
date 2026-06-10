#!/usr/bin/env bash

set -euo pipefail
REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$REPO_ROOT"

sudo apt-get update -qq
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq telnet default-mysql-client ripgrep

sudo chown -R "$(id -u):$(id -g)" "$REPO_ROOT/.gradle" 2>/dev/null || true
chmod +x gradlew 2>/dev/null || true

configure_testcontainers_host_override() {
    local profile_snippet="/etc/profile.d/testcontainers-host-override.sh"
    local bashrc="$HOME/.bashrc"
    local marker_start="# >>> testcontainers host override >>>"
    local marker_end="# <<< testcontainers host override <<<"

    remove_bashrc_block() {
        if [[ -f "$bashrc" ]]; then
            sed -i "/^${marker_start}\$/,/^${marker_end}\$/d" "$bashrc"
        fi
    }

    if docker info 2>/dev/null | grep -q 'Operating System: Docker Desktop'; then
        echo "Docker Desktop detected; setting TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal"
        export TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal
        printf '%s\n' \
            '# Set by preinit-testcontainers devcontainer setup (Docker Desktop only)' \
            'export TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal' \
            | sudo tee "$profile_snippet" > /dev/null
        remove_bashrc_block
        cat >> "$bashrc" <<EOF
$marker_start
export TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal
$marker_end
EOF
    else
        echo "Native Linux Docker detected; leaving Testcontainers host auto-detection enabled"
        sudo rm -f "$profile_snippet"
        remove_bashrc_block
        unset TESTCONTAINERS_HOST_OVERRIDE
    fi
}

configure_testcontainers_host_override

