# Backup and restore

The protected application deploy in `update.sh` always creates and validates
a fresh pre-deploy SQL backup without starting, stopping or recreating MySQL.
The commands below belong to a separately reviewed infrastructure maintenance
window. They are not part of a normal frontend/backend release and must not be
combined with `--remove-orphans`, `down`, volume pruning or volume removal on
the shared production host.

## Daily backup

`mysql-backup` connects over the internal data network, creates a
single-transaction SQL dump, writes it atomically and stores a SHA-256
checksum. The backend's legacy scheduled backup remains disabled because the
distroless application container intentionally has no MySQL client binaries.

PMS profile documents use database blobs by default and are covered by this SQL
dump. If `PMS_DOCUMENT_STORAGE_ROOT` is configured, document files additionally
require a coordinated private-volume backup and restore drill. Neither this SQL
job nor the current off-site mount includes that optional volume automatically.
See [PMS runtime and document storage](PMS_RUNTIME_CONFIGURATION.md).

Inspect:

```powershell
docker compose ps mysql-backup
docker compose logs --tail 100 mysql-backup
```

## Restore drill

The drill restores the newest checksum-verified dump into an isolated,
ephemeral MySQL instance:

```powershell
docker compose --profile restore-test up --build --abort-on-container-exit backup-restore-test
docker compose --profile restore-test down
```

The drill rejects markers outside `/backups`, verifies the SHA-256 checksum,
restores into the isolated database, and requires the configured total-table,
PMS-table and Flyway-version gates. The defaults are 20 total tables and 41 PMS
tables. `RESTORE_EXPECTED_FLYWAY_VERSION=auto` derives the expected version from
the current release's mounted `/migrations/V*__*.sql` files. Release packaging
includes these files; no version constant needs editing on a schema release.

The successful drill atomically writes `data/restore-evidence/restore-verification.json`
with a UTC verification time, backup filename, SHA-256 hash and release schema
version. Failure after backup selection writes `FAILED` evidence. The backend
reads the evidence volume without write access and requires a successful, fresh
proof for the newest checksum-verified backup and its own release schema. Its
PMS monitoring restore check reports missing, stale or mismatching evidence.
Enable this container-compatible path with `APP_BACKUP_RESTORE_EXTERNAL_ENABLED=true`;
the image does not need MySQL command-line programs. Protect the writable evidence
directory with the same operator access policy as backups. A JSON file is evidence
from the trusted restore job, not independent attestation.

Success requires at least `RESTORE_MINIMUM_TABLES` tables. Afterward, perform
application-level checks in staging: company count, hotel count, reservation
count, open folio balance and newest audit/outbox timestamps.

## Off-site copy

Configure `RESTIC_REPOSITORY` and `RESTIC_PASSWORD`, then run the encrypted
off-site profile:

```powershell
docker compose --profile offsite-backup up -d offsite-backup
```

The repository must be outside the production host, access must be restricted
to backup operators, and restore credentials must be available through the
incident process.
