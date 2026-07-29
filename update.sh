#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$(readlink -f "$0")")"

echo "[INFO] Lade den freigegebenen Git-Stand..."
git pull --ff-only

# Re-execute the freshly pulled wrapper once. This prevents an older Bash
# process from continuing with commands that were removed by the Git update.
if [[ "${CHRONO_UPDATE_REEXECUTED:-0}" != "1" ]]; then
  export CHRONO_UPDATE_REEXECUTED=1
  exec bash ./update.sh "$@"
fi

# Execute the freshly pulled safety-critical deployment implementation.
exec bash ./ops/deploy-production.sh "$@"
