#!/bin/sh
set -eu

evidence_directory="${RESTORE_EVIDENCE_DIRECTORY:-/restore-evidence}"
mkdir -p "${evidence_directory}"
started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
started_epoch="$(date +%s)"
backup_name=""
backup_sha256=""
expected_flyway_version=""
flyway_version=""
table_count=0
pms_table_count=0
write_evidence() {
  result="$1"
  umask 077
  temporary="${evidence_directory}/.restore-verification.json.tmp"
  duration_seconds="$(($(date +%s) - started_epoch))"
  printf '{"schemaVersion":1,"status":"%s","startedAt":"%s","verifiedAt":"%s","durationSeconds":%s,"backupFile":"%s","backupSha256":"%s","flywayVersion":"%s","expectedFlywayVersion":"%s","tableCount":%s,"pmsTableCount":%s}\n' \
    "${result}" "${started_at}" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${duration_seconds}" \
    "${backup_name}" "${backup_sha256}" "${flyway_version}" "${expected_flyway_version}" \
    "${table_count:-0}" "${pms_table_count:-0}" > "${temporary}"
  chmod 0644 "${temporary}"
  mv "${temporary}" "${evidence_directory}/restore-verification.json"
}
# Even invalid/missing backups must replace an earlier successful proof.
trap 'write_evidence FAILED' EXIT

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
candidate_backup_name="$(basename "${backup_file}")"
case "${candidate_backup_name}" in *[!A-Za-z0-9_.-]*) echo "Unsafe backup filename" >&2; exit 1;; esac
backup_name="${candidate_backup_name}"
backup_sha256="$(sha256sum "${backup_file}" | cut -d ' ' -f 1)"
(cd /backups && sha256sum -c "${backup_name}.sha256")

export MYSQL_PWD="${MYSQL_PASSWORD}"
candidate_flyway_version="${RESTORE_EXPECTED_FLYWAY_VERSION:-auto}"
if [ "${candidate_flyway_version}" = "auto" ]; then
  migration_directory="${RESTORE_MIGRATIONS_DIRECTORY:-/migrations}"
  test -d "${migration_directory}"
  candidate_flyway_version="$(find "${migration_directory}" -maxdepth 1 -type f -name 'V*__*.sql' \
    | sed 's|.*/||' | sed -n 's/^V\([0-9][0-9._]*\)__.*\.sql$/\1/p' | tr '_' '.' | sort -V | tail -n 1)"
fi
case "${candidate_flyway_version}" in ''|*[!0-9.]*) echo "Release migration version is unavailable or invalid" >&2; exit 1;; esac
expected_flyway_version="${candidate_flyway_version}"
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

observed_flyway_version="$(mysql \
  --batch --skip-column-names \
  --protocol=TCP \
  --host="${MYSQL_HOST}" \
  --port="${MYSQL_PORT:-3306}" \
  --user="${MYSQL_USER}" \
  "${MYSQL_DATABASE}" \
  -e "SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1")"
case "${observed_flyway_version}" in ''|*[!0-9.]*) echo "Restored migration version is unavailable or invalid" >&2; exit 1;; esac
flyway_version="${observed_flyway_version}"

if [ "${flyway_version}" != "${expected_flyway_version}" ]; then
  echo "Restore drill failed: expected Flyway version ${expected_flyway_version}, found ${flyway_version:-none}" >&2
  exit 1
fi

write_evidence OK
trap - EXIT
echo "Restore drill passed with ${table_count} tables, ${pms_table_count} PMS tables and Flyway ${flyway_version}."
