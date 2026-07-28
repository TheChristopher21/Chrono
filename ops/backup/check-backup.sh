#!/bin/sh
set -eu

marker="/backups/latest.ok"
max_age_minutes="${BACKUP_MAX_AGE_MINUTES:-1560}"

test -s "${marker}"
backup_file="$(sed -n '1p' "${marker}")"
test -n "${backup_file}"
test -s "${backup_file}"
test -s "${backup_file}.sha256"
(cd /backups && sha256sum -c "$(basename "${backup_file}").sha256" >/dev/null)
find "${marker}" -mmin "-${max_age_minutes}" -print -quit | grep -q .
