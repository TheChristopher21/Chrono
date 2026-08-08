#!/bin/sh
set -eu

: "${MYSQL_HOST:?MYSQL_HOST is required}"
: "${MYSQL_DATABASE:?MYSQL_DATABASE is required}"
: "${MYSQL_USER:?MYSQL_USER is required}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}"

marker="/backups/latest.ok"
test -s "${marker}"
backup_file="$(sed -n '1p' "${marker}")"
case "${backup_file}" in
  /backups/*.sql) ;;
  *)
    echo "Restore drill failed: backup marker points outside /backups or not to a SQL dump." >&2
    exit 1
    ;;
esac
test -f "${backup_file}"
test ! -L "${backup_file}"
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
  "${MYSQL_DATABASE}" \
  -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")"

minimum="${RESTORE_MINIMUM_TABLES:-20}"
if [ "${table_count}" -lt "${minimum}" ]; then
  echo "Restore drill failed: expected at least ${minimum} tables, found ${table_count}" >&2
  exit 1
fi

pms_table_count="$(mysql \
  --batch --skip-column-names \
  --protocol=TCP \
  --host="${MYSQL_HOST}" \
  --port="${MYSQL_PORT:-3306}" \
  --user="${MYSQL_USER}" \
  "${MYSQL_DATABASE}" \
  -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND LEFT(table_name, 4)='pms_'")"

minimum_pms="${RESTORE_MINIMUM_PMS_TABLES:-41}"
if [ "${pms_table_count}" -lt "${minimum_pms}" ]; then
  echo "Restore drill failed: expected at least ${minimum_pms} PMS tables, found ${pms_table_count}" >&2
  exit 1
fi

flyway_version="$(mysql \
  --batch --skip-column-names \
  --protocol=TCP \
  --host="${MYSQL_HOST}" \
  --port="${MYSQL_PORT:-3306}" \
  --user="${MYSQL_USER}" \
  "${MYSQL_DATABASE}" \
  -e "SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1")"

expected_flyway_version="${RESTORE_EXPECTED_FLYWAY_VERSION:-17}"
if [ "${flyway_version}" != "${expected_flyway_version}" ]; then
  echo "Restore drill failed: expected Flyway version ${expected_flyway_version}, found ${flyway_version:-none}" >&2
  exit 1
fi

echo "Restore drill passed with ${table_count} tables, ${pms_table_count} PMS tables and Flyway ${flyway_version}."
