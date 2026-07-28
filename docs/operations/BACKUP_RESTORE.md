# Backup and restore

## Daily backup

`mysql-backup` connects over the internal data network, creates a
single-transaction SQL dump, writes it atomically and stores a SHA-256
checksum. The backend's legacy scheduled backup remains disabled because the
distroless application container intentionally has no MySQL client binaries.

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
