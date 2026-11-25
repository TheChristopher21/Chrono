#!/usr/bin/env bash
# Regular backups for an existing Docker host; never starts or replaces production services.
set -Eeuo pipefail
umask 077

root="${CHRONO_BACKUP_ROOT:-/root/chrono/data}"
source_container="${CHRONO_MYSQL_CONTAINER:-chrono-mysql-1}"
root="$(realpath -e "${root}")"
backup_directory="${root}/backups"
evidence_directory="${root}/restore-evidence"
mkdir -p "${backup_directory}" "${evidence_directory}"
# These two dedicated read-only backend mounts must be traversable by its non-root UID.
chmod 0755 "${backup_directory}" "${evidence_directory}"
exec 9>"${root}/.pms-backup.lock"
flock -n 9 || { echo 'A Chrono backup is already running.'; exit 0; }

started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
started_epoch="$(date +%s)"
run_id="$(date -u +%Y%m%dT%H%M%SZ)-$$"
backup_name="regular-${run_id}.sql"
backup="${backup_directory}/${backup_name}"
partial="${backup_directory}/.${backup_name}.partial"
restore_container="chrono-backup-restore-${run_id,,}"
restore_created=false
backup_sha256=""
source_version=""
restored_version=""
restored_tables=0
restored_pms_tables=0
previous_backup=""
if [[ -f "${backup_directory}/latest.ok" ]]; then
    IFS= read -r previous_marker < "${backup_directory}/latest.ok" || true
    if [[ "${previous_marker:-}" == /backups/regular-*.sql && "${previous_marker#'/backups/'}" != */* ]]; then
        previous_backup="${previous_marker##*/}"
    fi
fi

source_query() {
    docker exec "${source_container}" sh -c '
        MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --batch --skip-column-names \
            -uroot "$MYSQL_DATABASE" -e "$1"
    ' sh "$1"
}
restore_query() {
    docker exec "${restore_container}" mysql --batch --skip-column-names -uroot chrono_restore -e "$1"
}
write_evidence() {
    local status="$1" temporary="${evidence_directory}/.restore-${run_id}.tmp"
    printf '{"schemaVersion":1,"status":"%s","startedAt":"%s","verifiedAt":"%s","durationSeconds":%s,"backupFile":"%s","backupSha256":"%s","flywayVersion":"%s","expectedFlywayVersion":"%s","tableCount":%s,"pmsTableCount":%s}\n' \
        "${status}" "${started_at}" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$(( $(date +%s) - started_epoch ))" \
        "${backup_name}" "${backup_sha256}" "${restored_version}" "${source_version}" \
        "${restored_tables}" "${restored_pms_tables}" > "${temporary}"
    chmod 0644 "${temporary}"
    mv -- "${temporary}" "${evidence_directory}/restore-verification.json"
}
cleanup() {
    local result=$?
    trap - EXIT
    if [[ ${result} -ne 0 ]]; then
        write_evidence FAILED || true
        echo 'Chrono backup/restore verification failed. The previous backup is retained.' >&2
    fi
    if [[ "${restore_created}" == true ]]; then
        # Only this invocation's labelled, network-isolated test container and its anonymous volume.
        local label
        label="$(docker inspect --format '{{index .Config.Labels "io.chrono.backup.run"}}' "${restore_container}" 2>/dev/null || true)"
        if [[ "${label}" == "${run_id}" ]]; then docker rm -fv "${restore_container}" >/dev/null || true; fi
    fi
    rm -f -- "${partial}"
    exit "${result}"
}
trap cleanup EXIT
trap 'exit 143' TERM
trap 'exit 130' INT
trap 'exit 129' HUP

for command in docker flock sha256sum stat df awk; do command -v "${command}" >/dev/null; done
[[ "$(docker inspect --format '{{.State.Running}}' "${source_container}")" == true ]]
source_image="$(docker inspect --format '{{.Image}}' "${source_container}")"
source_version="$(source_query 'SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1')"
[[ "${source_version}" =~ ^[0-9]+([.][0-9]+)*$ ]]
source_tables="$(source_query 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type="BASE TABLE"')"
source_pms_tables="$(source_query 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND LEFT(table_name,4)="pms_" AND table_type="BASE TABLE"')"
database_bytes="$(source_query 'SELECT COALESCE(SUM(data_length+index_length),0) FROM information_schema.tables WHERE table_schema=DATABASE()')"
[[ "${source_tables}" =~ ^[0-9]+$ && "${source_pms_tables}" =~ ^[0-9]+$ && "${database_bytes}" =~ ^[0-9]+$ ]]
(( source_pms_tables >= 4 ))
available_bytes="$(df -B1 --output=avail "${root}" | tail -n 1 | tr -d ' ')"
(( available_bytes > database_bytes * 3 + 2147483648 )) || { echo 'Insufficient disk space for backup and isolated restore.' >&2; exit 1; }

echo "Creating a consistent regular backup; source has ${source_tables} tables, ${source_pms_tables} PMS tables, Flyway ${source_version}."
docker exec "${source_container}" sh -c '
    MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot \
        --single-transaction --quick --routines --triggers --events \
        --hex-blob --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE"
' > "${partial}"
[[ -s "${partial}" ]]
for table in pms_properties pms_reservations pms_audit_events pms_integration_outbox; do
    printf -v create_table_pattern '^CREATE TABLE( IF NOT EXISTS)? \140%s\140' "${table}"
    grep -qE "${create_table_pattern}" "${partial}"
done
[[ "$(source_query 'SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1')" == "${source_version}" ]]
mv -- "${partial}" "${backup}"
backup_sha256="$(sha256sum "${backup}" | cut -d ' ' -f 1)"
# Relative filenames work both on the host and at the backend's /backups mount.
(cd "${backup_directory}" && sha256sum "${backup_name}" > "${backup_name}.sha256")
(cd "${backup_directory}" && sha256sum -c "${backup_name}.sha256")

echo 'Restoring into a temporary MySQL container without network or published ports.'
docker create --name "${restore_container}" --label "io.chrono.backup.run=${run_id}" \
    --network none --cpus 1 --memory 1g --memory-swap 1g \
    --security-opt no-new-privileges:true \
    -e MYSQL_ALLOW_EMPTY_PASSWORD=yes -e MYSQL_DATABASE=chrono_restore \
    "${source_image}" --innodb-buffer-pool-size=128M --event-scheduler=OFF >/dev/null
restore_created=true
docker start "${restore_container}" >/dev/null
ready=false
for ((attempt=0; attempt<90; attempt++)); do
    if docker exec "${restore_container}" mysql --protocol=TCP -h127.0.0.1 -uroot chrono_restore -e 'SELECT 1' >/dev/null 2>&1; then ready=true; break; fi
    sleep 2
done
[[ "${ready}" == true ]] || { echo 'Isolated restore database did not become ready.' >&2; exit 1; }
docker exec -i "${restore_container}" mysql -uroot chrono_restore < "${backup}"
restored_tables="$(restore_query 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type="BASE TABLE"')"
restored_pms_tables="$(restore_query 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND LEFT(table_name,4)="pms_" AND table_type="BASE TABLE"')"
restored_version="$(restore_query 'SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1')"
[[ "${restored_tables}" == "${source_tables}" && "${restored_pms_tables}" == "${source_pms_tables}" && "${restored_version}" == "${source_version}" ]]
core_tables="$(restore_query 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ("pms_properties","pms_reservations","pms_audit_events","pms_integration_outbox")')"
[[ "${core_tables}" == 4 ]]

write_evidence OK
# A failed import never promotes an incomplete or untested backup.
printf '/backups/%s\n' "${backup_name}" > "${backup_directory}/.latest-${run_id}.tmp"
chmod 0644 "${backup_directory}/.latest-${run_id}.tmp" "${backup}" "${backup}.sha256"
mv -- "${backup_directory}/.latest-${run_id}.tmp" "${backup_directory}/latest.ok"
echo "Verified ${backup_name}: ${restored_tables} tables, ${restored_pms_tables} PMS tables, Flyway ${restored_version}."

# Only rotate this service's files; retain the previous confirmed generation as well.
mapfile -t regular_backups < <(find "${backup_directory}" -maxdepth 1 -type f -name 'regular-*.sql' -printf '%f\n' | sort -r)
for old in "${regular_backups[@]:2}"; do
    [[ "${old}" != "${previous_backup}" ]] || continue
    if [[ -n "$(find "${backup_directory}/${old}" -maxdepth 0 -mtime +30 -print)" ]]; then
        rm -f -- "${backup_directory}/${old}" "${backup_directory}/${old}.sha256"
    fi
done
