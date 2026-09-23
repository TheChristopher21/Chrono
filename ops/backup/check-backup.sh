#!/bin/sh
set -eu

marker="/backups/latest.ok"
max_age_minutes="${BACKUP_MAX_AGE_MINUTES:-1560}"

test -s "${marker}"
backup_file="$(sed -n '1p' "${marker}")"
test -n "${backup_file}"
case "${backup_file}" in /*) ;; *) backup_file="/backups/${backup_file}";; esac
test "$(dirname "${backup_file}")" = /backups
case "$(basename "${backup_file}")" in
  .*|predeploy-*|*[!A-Za-z0-9_.-]*) exit 1;;
  *.sql|*.sql.gz) ;;
  *) exit 1;;
esac
test -f "${backup_file}"
test ! -L "${backup_file}"
test -s "${backup_file}"
test -s "${backup_file}.sha256"
(cd /backups && sha256sum -c "$(basename "${backup_file}").sha256" >/dev/null)
case "${backup_file}" in *.sql.gz) gzip -t "${backup_file}";; esac
find "${marker}" -mmin "-${max_age_minutes}" -print -quit | grep -q .
