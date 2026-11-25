#!/bin/sh
set -eu

: "${RESTIC_REPOSITORY:?RESTIC_REPOSITORY is required}"
: "${RESTIC_PASSWORD:?RESTIC_PASSWORD is required}"

interval="${OFFSITE_BACKUP_INTERVAL_SECONDS:-86400}"
retention="${OFFSITE_BACKUP_RETENTION_DAYS:-90}"

if ! restic snapshots >/dev/null 2>&1; then
  restic init
fi

while true; do
  restic backup /backups --tag chrono-mysql
  restic forget --tag chrono-mysql --keep-within "${retention}d" --prune
  restic check
  sleep "${interval}"
done
