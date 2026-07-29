#!/usr/bin/env bash
set -Eeuo pipefail

REPOSITORY_ROOT="$(cd "$(dirname "$(readlink -f "$0")")/.." && pwd)"
cd "${REPOSITORY_ROOT}"

readonly ENV_FILE=".env"
readonly DEPLOY_COMPOSE="docker-compose.production.yml"
readonly SHARED_NETWORK="chrono_chrono"
readonly MYSQL_CONTAINER="chrono-mysql-1"
readonly PROXY_CONTAINER="chrono-nginx-1"
readonly BACKEND_CONTAINER="chrono-backend-1"
readonly FRONTEND_CONTAINER="chrono-frontend-1"
readonly MINIMUM_FREE_KIB=$((2 * 1024 * 1024))

REQUESTED_IMAGE_TAG=""
ENV_BACKUP=""
ROLLBACK_TAG=""
DEPLOY_STARTED=0
ROLLBACK_RUNNING=0
PROTECTED_SNAPSHOT=""
CORE_COUNT_SNAPSHOT=""

fail() {
  echo "[FAIL] $*" >&2
  return 1
}

usage() {
  cat <<'EOF'
Usage: ./update.sh --image-tag <immutable-tag>

Only the Chrono backend and frontend are deployed. The database, Docker
volumes, proxy, shared network, monitoring, LLM and unrelated projects are
never managed by this deployment.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --image-tag)
      [[ $# -ge 2 ]] || fail "--image-tag benötigt einen Wert."
      REQUESTED_IMAGE_TAG="$2"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      fail "Unbekanntes Argument: $1"
      ;;
  esac
done

for command in awk curl df docker git gzip sha256sum stat; do
  command -v "${command}" >/dev/null 2>&1 ||
    fail "Erforderliches Programm fehlt: ${command}"
done

[[ -f "${ENV_FILE}" ]] || fail "${ENV_FILE} fehlt."
[[ -f "${DEPLOY_COMPOSE}" ]] || fail "${DEPLOY_COMPOSE} fehlt."

read_env_value() {
  local key="$1"
  awk -v requested_key="${key}" '
    index($0, requested_key "=") == 1 {
      value = substr($0, length(requested_key) + 2)
    }
    END { print value }
  ' "${ENV_FILE}"
}

set_env_value() {
  local key="$1"
  local value="$2"
  local temporary

  temporary="$(mktemp "${ENV_FILE}.tmp.XXXXXX")"
  awk -v requested_key="${key}" -v requested_value="${value}" '
    BEGIN { replaced = 0 }
    index($0, requested_key "=") == 1 {
      print requested_key "=" requested_value
      replaced = 1
      next
    }
    { print }
    END {
      if (!replaced) {
        print requested_key "=" requested_value
      }
    }
  ' "${ENV_FILE}" > "${temporary}"
  chmod --reference="${ENV_FILE}" "${temporary}"
  mv "${temporary}" "${ENV_FILE}"
}

container_status() {
  docker inspect --format '{{.State.Status}}' "$1"
}

container_health() {
  docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$1"
}

require_running_container() {
  local container="$1"
  docker inspect "${container}" >/dev/null 2>&1 ||
    fail "Pflichtcontainer fehlt: ${container}"
  [[ "$(container_status "${container}")" == "running" ]] ||
    fail "Pflichtcontainer läuft nicht: ${container}"
}

network_contains() {
  local container="$1"
  docker network inspect "${SHARED_NETWORK}" \
    --format '{{range .Containers}}{{.Name}}{{"\n"}}{{end}}' |
    grep -Fxq "${container}"
}

mysql_query_root() {
  local query="$1"
  docker exec \
    -e MYSQL_PWD="${MYSQL_ROOT_PASSWORD_VALUE}" \
    "${MYSQL_CONTAINER}" \
    mysql --batch --skip-column-names -uroot -e "${query}"
}

snapshot_protected_containers() {
  PROTECTED_SNAPSHOT="$(mktemp)"
  while IFS= read -r container; do
    case "${container}" in
      "${BACKEND_CONTAINER}"|"${FRONTEND_CONTAINER}")
        continue
        ;;
    esac
    docker inspect \
      --format '{{.Name}}|{{.Id}}|{{.State.StartedAt}}' \
      "${container}" |
      sed 's#^/##' >> "${PROTECTED_SNAPSHOT}"
  done < <(docker ps --format '{{.Names}}' | sort)
}

assert_protected_containers_unchanged() {
  local container
  local expected_id
  local expected_started_at
  local actual
  local actual_id
  local actual_started_at

  while IFS='|' read -r container expected_id expected_started_at; do
    docker inspect "${container}" >/dev/null 2>&1 ||
      fail "Geschützter Container fehlt nach dem Deploy: ${container}"
    [[ "$(container_status "${container}")" == "running" ]] ||
      fail "Geschützter Container läuft nicht mehr: ${container}"
    actual="$(docker inspect --format '{{.Id}}|{{.State.StartedAt}}' "${container}")"
    IFS='|' read -r actual_id actual_started_at <<< "${actual}"
    [[ "${actual_id}" == "${expected_id}" ]] ||
      fail "Geschützter Container wurde ersetzt: ${container}"
    [[ "${actual_started_at}" == "${expected_started_at}" ]] ||
      fail "Geschützter Container wurde neu gestartet: ${container}"
  done < "${PROTECTED_SNAPSHOT}"
}

snapshot_core_counts() {
  CORE_COUNT_SNAPSHOT="$(mktemp)"
  mysql_query_root "
    SELECT 'companies', COUNT(*) FROM \`${MYSQL_DATABASE_VALUE}\`.companies
    UNION ALL SELECT 'users', COUNT(*) FROM \`${MYSQL_DATABASE_VALUE}\`.users
    UNION ALL SELECT 'time_tracking', COUNT(*) FROM \`${MYSQL_DATABASE_VALUE}\`.time_tracking
    UNION ALL SELECT 'time_tracking_entries', COUNT(*) FROM \`${MYSQL_DATABASE_VALUE}\`.time_tracking_entries
    UNION ALL SELECT 'vacation_requests', COUNT(*) FROM \`${MYSQL_DATABASE_VALUE}\`.vacation_requests;
  " > "${CORE_COUNT_SNAPSHOT}"
}

assert_core_counts_not_decreased() {
  local table
  local before
  local after

  while IFS=$'\t' read -r table before; do
    after="$(mysql_query_root "SELECT COUNT(*) FROM \`${MYSQL_DATABASE_VALUE}\`.\`${table}\`;")"
    [[ "${after}" =~ ^[0-9]+$ ]] ||
      fail "Ungültige Zeilenzahl für ${table}: ${after}"
    (( after >= before )) ||
      fail "Datenverlust-Prüfung fehlgeschlagen: ${table} vorher=${before}, nachher=${after}"
  done < "${CORE_COUNT_SNAPSHOT}"
}

create_predeploy_backup() {
  local timestamp="$1"
  local backup_path="./data/backups/predeploy-${timestamp}.sql.gz"
  local temporary_path="${backup_path}.partial"
  local backup_bytes

  mkdir -p ./data/backups
  umask 077

  echo "[INFO] Erstelle konsistentes Datenbankbackup..."
  if ! docker exec \
      -e MYSQL_PWD="${MYSQL_ROOT_PASSWORD_VALUE}" \
      "${MYSQL_CONTAINER}" \
      mysqldump \
        -uroot \
        --single-transaction \
        --quick \
        --routines \
        --triggers \
        --events \
        --hex-blob \
        --set-gtid-purged=OFF \
        --databases "${MYSQL_DATABASE_VALUE}" |
      gzip -9 > "${temporary_path}"; then
    rm -f -- "${temporary_path}"
    fail "Datenbankbackup fehlgeschlagen."
  fi

  gzip -t "${temporary_path}" ||
    fail "Das neue Datenbankbackup ist beschädigt."
  backup_bytes="$(stat -c '%s' "${temporary_path}")"
  (( backup_bytes >= 1048576 )) ||
    fail "Das neue Datenbankbackup ist unerwartet klein (${backup_bytes} Bytes)."

  mv "${temporary_path}" "${backup_path}"
  sha256sum "${backup_path}" > "${backup_path}.sha256"
  chmod 600 "${backup_path}" "${backup_path}.sha256"
  echo "[OK] Geprüftes Backup: ${backup_path} (${backup_bytes} Bytes)"
}

wait_for_url() {
  local label="$1"
  local host="$2"
  local url="$3"
  local attempt

  for attempt in $(seq 1 60); do
    if curl \
      --fail \
      --insecure \
      --silent \
      --output /dev/null \
      --resolve "${host}:443:127.0.0.1" \
      --max-time 5 \
      "${url}"; then
      echo "[OK] ${label} ist erreichbar."
      return 0
    fi
    if (( attempt == 1 || attempt % 10 == 0 )); then
      echo "[INFO] Warte auf ${label} (${attempt}/60) ..."
    fi
    sleep 2
  done
  fail "${label} wurde nicht rechtzeitig erreichbar."
}

rollback_application() {
  [[ "${DEPLOY_STARTED}" -eq 1 ]] || return 0
  [[ -n "${ROLLBACK_TAG}" ]] || return 0

  ROLLBACK_RUNNING=1
  echo "[WARN] App-Deploy fehlgeschlagen. Stelle vorherige App-Images wieder her." >&2
  CHRONO_IMAGE_TAG="${ROLLBACK_TAG}" \
    docker compose \
      --file "${DEPLOY_COMPOSE}" \
      --env-file "${ENV_FILE}" \
      up -d --no-deps backend frontend || true
  docker exec "${PROXY_CONTAINER}" nginx -t >/dev/null 2>&1 &&
    docker exec "${PROXY_CONTAINER}" nginx -s reload >/dev/null 2>&1 || true
  ROLLBACK_RUNNING=0
}

handle_error() {
  local exit_code=$?
  trap - ERR
  if [[ "${ROLLBACK_RUNNING}" -eq 0 ]]; then
    rollback_application
  fi
  if [[ -n "${ENV_BACKUP}" && -f "${ENV_BACKUP}" ]]; then
    cp --preserve=mode "${ENV_BACKUP}" "${ENV_FILE}"
    echo "[INFO] Vorherige .env wurde wiederhergestellt." >&2
  fi
  if [[ -n "${PROTECTED_SNAPSHOT}" && -f "${PROTECTED_SNAPSHOT}" ]]; then
    assert_protected_containers_unchanged || true
  fi
  echo "[FAIL] Deployment abgebrochen. MySQL und geschützte Container wurden nicht verwaltet." >&2
  exit "${exit_code}"
}

cleanup() {
  [[ -z "${PROTECTED_SNAPSHOT}" ]] || rm -f -- "${PROTECTED_SNAPSHOT}"
  [[ -z "${CORE_COUNT_SNAPSHOT}" ]] || rm -f -- "${CORE_COUNT_SNAPSHOT}"
}

trap handle_error ERR
trap cleanup EXIT

if [[ -z "${REQUESTED_IMAGE_TAG}" ]]; then
  REQUESTED_IMAGE_TAG="$(read_env_value CHRONO_IMAGE_TAG)"
fi
[[ "${REQUESTED_IMAGE_TAG}" =~ ^[0-9A-Za-z][0-9A-Za-z._-]{0,63}$ ]] ||
  fail "Ungültiger Image-Tag: ${REQUESTED_IMAGE_TAG}"
case "${REQUESTED_IMAGE_TAG,,}" in
  latest|main|local)
    fail "Der Produktions-Image-Tag muss unveränderlich sein."
    ;;
esac

if [[ "${REQUESTED_IMAGE_TAG}" =~ ^[0-9a-f]{12}$ ]]; then
  CURRENT_COMMIT_TAG="$(git rev-parse --short=12 HEAD)"
  [[ "${CURRENT_COMMIT_TAG}" == "${REQUESTED_IMAGE_TAG}" ]] ||
    fail "Image-Tag ${REQUESTED_IMAGE_TAG} passt nicht zum Server-Commit ${CURRENT_COMMIT_TAG}."
fi

echo "[INFO] Validiere strikt begrenzte Produktionskonfiguration..."
mapfile -t DEPLOY_SERVICES < <(
  CHRONO_ENV_FILE="${ENV_FILE}" \
    docker compose \
      --file "${DEPLOY_COMPOSE}" \
      --env-file "${ENV_FILE}" \
      config --services |
    sort
)
[[ "${DEPLOY_SERVICES[*]}" == "backend frontend" ]] ||
  fail "${DEPLOY_COMPOSE} darf ausschließlich backend und frontend enthalten."

docker network inspect "${SHARED_NETWORK}" >/dev/null 2>&1 ||
  fail "Externes Produktionsnetzwerk fehlt: ${SHARED_NETWORK}"

for container in \
  "${MYSQL_CONTAINER}" \
  "${PROXY_CONTAINER}" \
  "chrono-llm-1" \
  "open-webui" \
  "${BACKEND_CONTAINER}" \
  "${FRONTEND_CONTAINER}"; do
  require_running_container "${container}"
  network_contains "${container}" ||
    fail "${container} ist nicht mit ${SHARED_NETWORK} verbunden."
done

[[ "$(container_health "${MYSQL_CONTAINER}")" == "healthy" ]] ||
  fail "MySQL ist nicht healthy."
[[ "$(container_health "chrono-llm-1")" == "healthy" ]] ||
  fail "Chrono LLM ist nicht healthy."
[[ "$(container_health "open-webui")" == "healthy" ]] ||
  fail "Open WebUI ist nicht healthy."

MYSQL_VOLUME="$(
  docker inspect \
    --format '{{range .Mounts}}{{if eq .Destination "/var/lib/mysql"}}{{.Name}}{{end}}{{end}}' \
    "${MYSQL_CONTAINER}"
)"
[[ -n "${MYSQL_VOLUME}" ]] ||
  fail "MySQL besitzt kein benanntes Volume unter /var/lib/mysql."
docker volume inspect "${MYSQL_VOLUME}" >/dev/null 2>&1 ||
  fail "MySQL-Volume ist nicht auffindbar: ${MYSQL_VOLUME}"

MYSQL_CONTAINER_ID="$(docker inspect --format '{{.Id}}' "${MYSQL_CONTAINER}")"
MYSQL_STARTED_AT="$(docker inspect --format '{{.State.StartedAt}}' "${MYSQL_CONTAINER}")"
MYSQL_DATABASE_VALUE="$(read_env_value MYSQL_DATABASE)"
MYSQL_DATABASE_VALUE="${MYSQL_DATABASE_VALUE:-chrono_db}"
MYSQL_ROOT_PASSWORD_VALUE="$(read_env_value MYSQL_ROOT_PASSWORD)"
MYSQL_APP_USER_VALUE="$(read_env_value MYSQL_USER)"
MYSQL_APP_PASSWORD_VALUE="$(read_env_value MYSQL_PASSWORD)"
[[ "${MYSQL_DATABASE_VALUE}" =~ ^[A-Za-z0-9_]+$ ]] ||
  fail "MYSQL_DATABASE enthält unzulässige Zeichen."
[[ -n "${MYSQL_ROOT_PASSWORD_VALUE}" ]] ||
  fail "MYSQL_ROOT_PASSWORD fehlt."
[[ "${MYSQL_APP_USER_VALUE}" =~ ^[A-Za-z0-9_]+$ &&
   "${MYSQL_APP_USER_VALUE,,}" != "root" ]] ||
  fail "MYSQL_USER muss ein vorhandener dedizierter App-Benutzer sein."
[[ -n "${MYSQL_APP_PASSWORD_VALUE}" ]] ||
  fail "MYSQL_PASSWORD fehlt."

mysql_query_root "SELECT 1;" >/dev/null
docker exec \
  -e MYSQL_PWD="${MYSQL_APP_PASSWORD_VALUE}" \
  "${MYSQL_CONTAINER}" \
  mysql --batch --skip-column-names \
    -u"${MYSQL_APP_USER_VALUE}" \
    -e "SELECT 1 FROM \`${MYSQL_DATABASE_VALUE}\`.companies LIMIT 1;" >/dev/null

AVAILABLE_KIB="$(df -Pk . | awk 'NR == 2 {print $4}')"
[[ "${AVAILABLE_KIB}" =~ ^[0-9]+$ ]] ||
  fail "Freier Speicher konnte nicht ermittelt werden."
(( AVAILABLE_KIB >= MINIMUM_FREE_KIB )) ||
  fail "Weniger als 2 GiB frei. Deployment wird vor jeder Änderung abgebrochen."

snapshot_protected_containers
snapshot_core_counts

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
create_predeploy_backup "${TIMESTAMP}"

ENV_BACKUP=".env.before-deploy-${TIMESTAMP}"
cp --preserve=mode "${ENV_FILE}" "${ENV_BACKUP}"
set_env_value CHRONO_IMAGE_TAG "${REQUESTED_IMAGE_TAG}"

BACKEND_IMAGE_REPOSITORY="$(read_env_value CHRONO_BACKEND_IMAGE)"
BACKEND_IMAGE_REPOSITORY="${BACKEND_IMAGE_REPOSITORY:-chrisubuntu1/chrono-backend}"
FRONTEND_IMAGE_REPOSITORY="$(read_env_value CHRONO_FRONTEND_IMAGE)"
FRONTEND_IMAGE_REPOSITORY="${FRONTEND_IMAGE_REPOSITORY:-chrisubuntu1/chrono-frontend}"
ROLLBACK_TAG="rollback-${TIMESTAMP}"

OLD_BACKEND_IMAGE_ID="$(docker inspect --format '{{.Image}}' "${BACKEND_CONTAINER}")"
OLD_FRONTEND_IMAGE_ID="$(docker inspect --format '{{.Image}}' "${FRONTEND_CONTAINER}")"
docker image tag "${OLD_BACKEND_IMAGE_ID}" "${BACKEND_IMAGE_REPOSITORY}:${ROLLBACK_TAG}"
docker image tag "${OLD_FRONTEND_IMAGE_ID}" "${FRONTEND_IMAGE_REPOSITORY}:${ROLLBACK_TAG}"

echo "[INFO] Lade ausschließlich Backend- und Frontend-Image..."
CHRONO_ENV_FILE="${ENV_FILE}" \
  docker compose \
    --file "${DEPLOY_COMPOSE}" \
    --env-file "${ENV_FILE}" \
    pull backend frontend

DEPLOY_STARTED=1
echo "[INFO] Aktualisiere ausschließlich Chrono Backend und Frontend..."
CHRONO_ENV_FILE="${ENV_FILE}" \
  docker compose \
    --file "${DEPLOY_COMPOSE}" \
    --env-file "${ENV_FILE}" \
    up -d --no-deps --wait --wait-timeout 180 backend frontend

docker exec "${PROXY_CONTAINER}" nginx -t
docker exec "${PROXY_CONTAINER}" nginx -s reload

wait_for_url \
  "Chrono Frontend" \
  "chrono-logisch.ch" \
  "https://chrono-logisch.ch/healthz"
wait_for_url \
  "Chrono Backend" \
  "api.chrono-logisch.ch" \
  "https://api.chrono-logisch.ch/actuator/health"

[[ "$(docker inspect --format '{{.Id}}' "${MYSQL_CONTAINER}")" == "${MYSQL_CONTAINER_ID}" ]] ||
  fail "MySQL-Container wurde unerwartet ersetzt."
[[ "$(docker inspect --format '{{.State.StartedAt}}' "${MYSQL_CONTAINER}")" == "${MYSQL_STARTED_AT}" ]] ||
  fail "MySQL-Container wurde unerwartet neu gestartet."
[[ "$(container_health "${MYSQL_CONTAINER}")" == "healthy" ]] ||
  fail "MySQL ist nach dem App-Deploy nicht healthy."
[[ "$(
  docker inspect \
    --format '{{range .Mounts}}{{if eq .Destination "/var/lib/mysql"}}{{.Name}}{{end}}{{end}}' \
    "${MYSQL_CONTAINER}"
)" == "${MYSQL_VOLUME}" ]] ||
  fail "MySQL-Volume hat sich unerwartet geändert."

assert_core_counts_not_decreased
assert_protected_containers_unchanged

DEPLOY_STARTED=0
trap - ERR

echo "[OK] Sicheres App-Deployment abgeschlossen."
echo "[OK] MySQL-Container unverändert: ${MYSQL_CONTAINER_ID:0:12}"
echo "[OK] MySQL-Volume unverändert: ${MYSQL_VOLUME}"
echo "[OK] Andere Projekte und Infrastrukturcontainer wurden weder ersetzt noch neu gestartet."
