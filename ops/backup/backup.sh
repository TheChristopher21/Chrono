#!/bin/sh
set -eu

: "${MYSQL_HOST:?MYSQL_HOST is required}"
: "${MYSQL_DATABASE:?MYSQL_DATABASE is required}"
: "${MYSQL_USER:?MYSQL_USER is required}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}"

MYSQL_PORT="${MYSQL_PORT:-3306}"
BACKUP_INTERVAL_SECONDS="${BACKUP_INTERVAL_SECONDS:-86400}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"
BACKUP_DIRECTORY="/backups"

perform_backup() {
  timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
  temporary="${BACKUP_DIRECTORY}/.${MYSQL_DATABASE}_${timestamp}.sql.tmp"
  output="${BACKUP_DIRECTORY}/${MYSQL_DATABASE}_${timestamp}.sql"

  umask 077
  export MYSQL_PWD="${MYSQL_PASSWORD}"
  mysqldump \
    --protocol=TCP \
    --host="${MYSQL_HOST}" \
    --port="${MYSQL_PORT}" \
    --user="${MYSQL_USER}" \
    --single-transaction \
    --quick \
    --no-tablespaces \
    --routines \
    --events \
    --triggers \
    --set-gtid-purged=OFF \
    --result-file="${temporary}" \
    "${MYSQL_DATABASE}"

  test -s "${temporary}"
  mv "${temporary}" "${output}"
  sha256sum "${output}" > "${output}.sha256"
  printf '%s\n' "${output}" > "${BACKUP_DIRECTORY}/latest.ok"
  find "${BACKUP_DIRECTORY}" -type f -name "${MYSQL_DATABASE}_*.sql" -mtime "+${BACKUP_RETENTION_DAYS}" -delete
  find "${BACKUP_DIRECTORY}" -type f -name "${MYSQL_DATABASE}_*.sql.sha256" -mtime "+${BACKUP_RETENTION_DAYS}" -delete
  echo "Backup completed: $(basename "${output}")"
}

while true; do
  perform_backup
  sleep "${BACKUP_INTERVAL_SECONDS}"
done
