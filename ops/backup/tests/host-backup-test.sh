#!/usr/bin/env bash
set -euo pipefail
script_directory="$(cd "$(dirname "$0")/.." && pwd)"
fixture="$(mktemp -d)"
trap 'rm -rf -- "${fixture}"' EXIT
mkdir -p "${fixture}/bin" "${fixture}/data"
export FIXTURE="${fixture}" CHRONO_BACKUP_ROOT="${fixture}/data"
cat > "${fixture}/bin/docker" <<'DOCKER'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "${FIXTURE}/calls"
case "$1" in
    inspect)
        case "$3" in
            *State.Running*) echo true;;
            *Config.Labels*) cat "${FIXTURE}/run-id";;
            *) echo sha256:isolated-source-image;;
        esac;;
    create)
        [[ "$*" == *'--network none'* && "$*" != *'--publish'* && "$*" != *'--privileged'* ]]
        while (($#)); do
            if [[ "$1" == --label ]]; then printf '%s' "${2#*=}" > "${FIXTURE}/run-id"; break; fi
            shift
        done;;
    start) :;;
    rm) [[ "$*" == *'chrono-backup-restore-'* ]];;
    exec)
        query="${!#}"
        if [[ "$*" == *'mysqldump'* ]]; then
            for table in pms_properties pms_reservations pms_audit_events pms_integration_outbox; do
                if [[ -f "${FIXTURE}/incomplete" && "$table" == pms_audit_events ]]; then continue; fi
                printf 'CREATE TABLE `%s` (id bigint);\n' "$table"
            done
        elif [[ "$2" == -i ]]; then
            cat >/dev/null
            [[ ! -f "${FIXTURE}/fail-import" ]]
        elif [[ "$query" == *'SUM(data_length'* ]]; then echo 1000
        elif [[ "$query" == *'SELECT version'* ]]; then echo 48
        elif [[ "$query" == *'COUNT(*)'* ]]; then echo 4
        else echo 1
        fi;;
    *) exit 1;;
esac
DOCKER
chmod +x "${fixture}/bin/docker"
export PATH="${fixture}/bin:${PATH}"

bash "${script_directory}/host-backup.sh" > "${fixture}/success.log"
marker="$(cat "${fixture}/data/backups/latest.ok")"
[[ "${marker}" == /backups/regular-*.sql ]]
grep -q '"status":"OK"' "${fixture}/data/restore-evidence/restore-verification.json"
grep -q '"tableCount":4,"pmsTableCount":4' "${fixture}/data/restore-evidence/restore-verification.json"
grep -q '^rm -fv chrono-backup-restore-' "${fixture}/calls"

touch "${fixture}/fail-import"
if bash "${script_directory}/host-backup.sh" > "${fixture}/failed.log" 2>&1; then echo 'Failed import was accepted' >&2; exit 1; fi
[[ "$(cat "${fixture}/data/backups/latest.ok")" == "${marker}" ]]
grep -q '"status":"FAILED"' "${fixture}/data/restore-evidence/restore-verification.json"
[[ -s "${fixture}/data/backups/${marker##*/}" ]]
rm "${fixture}/fail-import"
touch "${fixture}/incomplete"
if bash "${script_directory}/host-backup.sh" > "${fixture}/incomplete.log" 2>&1; then echo 'Incomplete PMS backup was accepted' >&2; exit 1; fi
[[ "$(cat "${fixture}/data/backups/latest.ok")" == "${marker}" ]]
[[ -z "$(find "${fixture}/data/backups" -name '*.partial' -print -quit)" ]]
echo 'PASS: complete backup/restore, failed import, missing PMS table, retained previous marker and isolated-container cleanup.'
