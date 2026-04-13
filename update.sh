#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$(readlink -f "$0")")"

FORCE_CLEANUP=0
if [[ "${1:-}" == "--cleanup" ]]; then
  FORCE_CLEANUP=1
fi

should_cleanup() {
  local usage
  usage="$(df -P / | awk 'NR==2 {gsub("%","",$5); print $5}')"
  [[ -n "$usage" && "$usage" -ge 80 ]]
}

safe_docker_cleanup() {
  echo "[CLEANUP] Docker builder cache bereinigen..."
  docker builder prune -a -f || true

  echo "[CLEANUP] Ungenutzte Docker Images bereinigen..."
  docker image prune -a -f || true

  echo "[CLEANUP] Gestoppte Container bereinigen..."
  docker container prune -f || true

  echo "[CLEANUP] Docker Volumes bleiben unberuehrt. MySQL-Daten werden nicht geloescht."
  docker system df || true
}

echo "[INFO] Git pull..."
git pull --ff-only

if [[ "$FORCE_CLEANUP" -eq 1 ]] || should_cleanup; then
  echo "[INFO] Server-Speicher ist knapp oder Cleanup wurde angefordert. Starte sichere Docker-Bereinigung..."
  safe_docker_cleanup
else
  echo "[INFO] Genug Speicher vorhanden. Ueberspringe Docker-Bereinigung."
fi

echo "[INFO] Pull aktuelle Images..."
docker compose pull

echo "[INFO] Starte Container neu..."
docker compose up -d --remove-orphans

echo "[INFO] Finale Docker-Bereinigung nach dem Deploy..."
docker image prune -a -f || true
docker container prune -f || true

echo "[OK] Update fertig."
