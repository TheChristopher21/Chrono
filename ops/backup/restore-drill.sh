#!/bin/sh
set -eu

: "${MYSQL_HOST:?MYSQL_HOST is required}"
: "${MYSQL_DATABASE:?MYSQL_DATABASE is required}"
: "${MYSQL_USER:?MYSQL_USER is required}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}"

marker="/backups/latest.ok"
test -s "${marker}"
backup_file="$(sed -n '1p' "${marker}")"
test -s "${backup_file}"
(cd /backups && sha256sum -c "$(basename "${backup_file}").sha256")

export MYSQL_PWD="${MYSQL_PASSWORD}"
mysql \
  --protocol=TCP \
  --host="${MYSQL_HOST}" \
  --port="${MYSQL_PORT:-3306}" \
  --user="${MYSQL_USER}" \
  "${MYSQL_DATABASE}" < "${backup_file}"

table_count="$(mysql \
  --batch --skip-column-names \
  --protocol=TCP \
  --host="${MYSQL_HOST}" \
  --port="${MYSQL_PORT:-3306}" \
  --user="${MYSQL_USER}" \
  -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}'")"

minimum="${RESTORE_MINIMUM_TABLES:-20}"
if [ "${table_count}" -lt "${minimum}" ]; then
  echo "Restore drill failed: expected at least ${minimum} tables, found ${table_count}" >&2
  exit 1
fi

echo "Restore drill passed with ${table_count} tables."
