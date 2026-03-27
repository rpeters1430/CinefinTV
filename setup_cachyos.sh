#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "setup_cachyos.sh is deprecated. Running setup_linux.sh instead."
exec "$SCRIPT_DIR/setup_linux.sh" "$@"
