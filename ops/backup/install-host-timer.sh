#!/usr/bin/env bash
set -euo pipefail
[[ "$(id -u)" == 0 ]] || { echo 'Run as root on the Chrono Docker host.' >&2; exit 1; }
script_directory="$(cd "$(dirname "$0")" && pwd)"
install -d -m 0755 /opt/chrono-backup
install -m 0755 "${script_directory}/host-backup.sh" /opt/chrono-backup/host-backup.sh
install -m 0644 "${script_directory}/chrono-backup.service" /etc/systemd/system/chrono-backup.service
install -m 0644 "${script_directory}/chrono-backup.timer" /etc/systemd/system/chrono-backup.timer
systemctl daemon-reload
systemctl enable --now chrono-backup.timer
echo 'Daily backup and isolated restore enabled. Start the first run with: systemctl start --no-block chrono-backup.service'
