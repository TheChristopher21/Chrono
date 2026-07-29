#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$(readlink -f "$0")")"

echo "[INFO] Lade den freigegebenen Git-Stand..."
git pull --ff-only

# Execute the freshly pulled safety-critical deployment implementation.
exec bash ./ops/deploy-production.sh "$@"
