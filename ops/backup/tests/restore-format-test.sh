#!/usr/bin/env bash
set -euo pipefail
# Run inside an isolated test container: /backups and /restore-evidence are fixtures.
script_directory="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p /backups /restore-evidence /tmp/fake-mysql
export PATH="/tmp/fake-mysql:${PATH}" MYSQL_HOST=isolated MYSQL_USER=fixture MYSQL_PASSWORD=fixture
export MYSQL_DATABASE=fixture RESTORE_EXPECTED_FLYWAY_VERSION=48
export RESTORE_MINIMUM_TABLES=4 RESTORE_MINIMUM_PMS_TABLES=4
cat > /tmp/fake-mysql/mysql <<'MYSQL'
#!/bin/bash
case "${!#}" in
    *COUNT*) echo 4;;
    *'SELECT version'*) echo 48;;
    *) cat > /tmp/restored.sql;;
esac
MYSQL
chmod +x /tmp/fake-mysql/mysql
printf 'CREATE TABLE `pms_properties` (id bigint);\n' > /backups/regular-format.sql
gzip -c /backups/regular-format.sql > /backups/regular-format.sql.gz
for format in sql sql.gz; do
    name="regular-format.${format}"
    (cd /backups && sha256sum "${name}" > "${name}.sha256")
    printf '/backups/%s\n' "${name}" > /backups/latest.ok
    sh "${script_directory}/check-backup.sh"
    sh "${script_directory}/restore-drill.sh"
    cmp /tmp/restored.sql /backups/regular-format.sql
    grep -q '"status":"OK"' /restore-evidence/restore-verification.json
done
# Even with a matching checksum, truncated gzip must fail and replace old success evidence.
head -c 25 /backups/regular-format.sql.gz > /backups/regular-broken.sql.gz
(cd /backups && sha256sum regular-broken.sql.gz > regular-broken.sql.gz.sha256)
printf '/backups/regular-broken.sql.gz\n' > /backups/latest.ok
if sh "${script_directory}/restore-drill.sh" >/tmp/broken-restore.log 2>&1; then echo 'Corrupt gzip was accepted' >&2; exit 1; fi
grep -q '"status":"FAILED"' /restore-evidence/restore-verification.json
if sh "${script_directory}/check-backup.sh" >/dev/null 2>&1; then echo 'Corrupt gzip health check passed' >&2; exit 1; fi
printf '/backups/../etc/passwd\n' > /backups/latest.ok
if sh "${script_directory}/check-backup.sh" >/dev/null 2>&1; then echo 'Escaping marker accepted' >&2; exit 1; fi
echo 'PASS: plain SQL, gzip SQL, decompressed import bytes, corrupt gzip failure and escaping marker rejection.'
