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

  echo "[CLEANUP] Verwaiste Docker Image-Layer bereinigen..."
  docker image prune -f || true

  echo "[CLEANUP] Gestoppte Container bereinigen..."
  docker container prune -f || true

  echo "[CLEANUP] Docker Volumes bleiben unberuehrt. MySQL-Daten werden nicht geloescht."
  docker system df || true
}

create_predeploy_backup() {
  local mysql_container="chrono-mysql-1"
  local root_password
  local backup_path

  root_password="$(sed -n 's/^MYSQL_ROOT_PASSWORD=//p' .env | tail -n 1)"
  if [[ -z "${root_password}" ]]; then
    echo "[FAIL] MYSQL_ROOT_PASSWORD fehlt; Pre-Deploy-Backup nicht möglich." >&2
    return 1
  fi
  if ! docker inspect "${mysql_container}" >/dev/null 2>&1; then
    echo "[FAIL] Laufender MySQL-Container ${mysql_container} nicht gefunden." >&2
    return 1
  fi

  mkdir -p ./data/backups
  backup_path="./data/backups/predeploy-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
  echo "[INFO] Erstelle konsistentes Pre-Deploy-Datenbankbackup..."
  docker exec \
    -e MYSQL_PWD="${root_password}" \
    "${mysql_container}" \
    mysqldump -uroot --single-transaction --routines --triggers chrono_db |
    gzip -9 > "${backup_path}"
  test -s "${backup_path}"
  chmod 600 "${backup_path}"
  echo "[OK] Pre-Deploy-Backup erstellt: ${backup_path}"
}

migrate_prometheus_data() {
  local target="./prometheus_data"
  local marker="${target}/.chrono-bind-migration-complete"

  mkdir -p "${target}"
  if [[ -f "${marker}" ]]; then
    return
  fi

  if docker inspect chrono-prometheus-1 >/dev/null 2>&1; then
    echo "[INFO] Übernehme bestehende Prometheus-Historie in den stabilen Datenpfad..."
    docker cp chrono-prometheus-1:/prometheus/. "${target}/"
  fi
  chown -R 65534:65534 "${target}"
  touch "${marker}"
  chown 65534:65534 "${marker}"
}

echo "[INFO] Git pull..."
git pull --ff-only

echo "[INFO] Produktionskonfiguration validieren..."
docker compose --env-file .env config --quiet

create_predeploy_backup
migrate_prometheus_data

if [[ "$FORCE_CLEANUP" -eq 1 ]] || should_cleanup; then
  echo "[INFO] Server-Speicher ist knapp oder Cleanup wurde angefordert. Starte sichere Docker-Bereinigung..."
  safe_docker_cleanup
else
  echo "[INFO] Genug Speicher vorhanden. Ueberspringe Docker-Bereinigung."
fi

echo "[INFO] Pull aktuelle Images..."
docker compose pull --ignore-buildable

echo "[INFO] Lokale Betriebs-Images bauen..."
docker compose build mysql-backup alertmanager

echo "[INFO] Starte Container neu..."
docker compose up -d --remove-orphans --wait --wait-timeout 600

echo "[INFO] Finale Docker-Bereinigung nach dem Deploy..."
docker image prune -f || true
docker container prune -f || true

echo "[INFO] Laufende Dienste..."
docker compose ps

echo "[OK] Update fertig."
