# PMS runtime switches and document storage

The defaults in `application.properties`, `.env.example` and both Compose backend
services keep the new provider and mail workers disabled. Configuring a hotel
or queuing a job does not activate a disabled server worker. Existing PMS outbox
and monitoring settings retain their previous behavior.

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `APP_PMS_PAYMENTS_STRIPE_ENABLED` | `false` | Select the real Stripe payment adapter |
| `STRIPE_SECRET_KEY` | empty | Server-side Stripe API credential |
| `APP_PMS_PAYMENTS_STRIPE_WEBHOOK_SECRET` | empty | Signing secret for `/api/public/pms/webhooks/stripe` |
| `APP_PMS_PAYMENTS_AUTOMATION_ENABLED` | `false` | Consume signed events, reconcile payments/refunds, queue opted-in deposit links |
| `APP_PMS_PAYMENTS_AUTOMATION_INTERVAL_MS` | `60000` | Payment reconciliation interval |
| `APP_PMS_PAYMENTS_AUTOMATION_INITIAL_DELAY_MS` | `30000` | Payment worker startup delay |
| `APP_PMS_BEDS24_WORKER_ENABLED` | `false` | Deliver queued Beds24 calendar publications |
| `APP_PMS_BEDS24_BASE_URL` | `https://beds24.com/api/v2/` | Official provider API base |
| `APP_PMS_BEDS24_WORKER_INTERVAL_MS` | `30000` | Publication interval |
| `APP_PMS_BEDS24_WORKER_INITIAL_DELAY_MS` | `30000` | Publication worker startup delay |
| `APP_PMS_BILLING_SCHEDULER_ENABLED` | `false` | Schedule eligible hotel-configured reminders |
| `APP_PMS_BILLING_SCHEDULER_INTERVAL_MS` | `300000` | Reminder scan interval |
| `APP_PMS_DELIVERY_WORKER_ENABLED` | `false` | Deliver persisted jobs through configured SMTP |
| `APP_PMS_DELIVERY_WORKER_INTERVAL_MS` | `30000` | SMTP queue scan interval |

SMTP still uses the existing `SPRING_MAIL_*` configuration. Each hotel additionally
needs its approved sender and mail opt-in; deposit links and reminder rules have
their own hotel opt-ins. Enabling the billing scheduler alone does not send mail.
Use a deliberate test configuration and mock SMTP for local verification.
The production Compose files force simulated PMS payments off.

Hotel-specific Beds24 refresh tokens are supplied as additional server environment
variables named by the hotel's `env:...` reference. Do not put their values in a
hotel form, source control or a rendered configuration report.

Compose interpolation and container `env_file` loading are separate. When using
a custom environment file, provide that same file through both Compose's
`--env-file` option and `CHRONO_ENV_FILE`; otherwise explicit backend environment
defaults can override values found only through `env_file`. To validate the
checked-in examples without reading an actual `.env` or contacting a daemon:

```powershell
$env:CHRONO_ENV_FILE = '.env.example'
docker compose --env-file .env.example -f docker-compose.yml --profile infrastructure config --quiet
docker compose --env-file .env.example -f docker-compose.production.yml config --quiet
```

## Document limits and default database storage

`PMS_DOCUMENT_MAX_FILE_BYTES=26214400` permits 25 MiB per profile/contract upload.
`PMS_DOCUMENT_TENANT_QUOTA_BYTES=10737418240` permits 10 GiB of stored document
versions per tenant. `PMS_DOCUMENT_ORGANIZATION_LIMIT=5000` limits stored versions
per company profile. Archived versions still occupy storage and count toward
these quotas. The multipart limits `APP_UPLOAD_MAX_FILE_SIZE=32MB` and
`APP_UPLOAD_MAX_REQUEST_SIZE=34MB` leave room for request metadata; reverse proxy
limits must also permit the chosen upload size.

Leave `PMS_DOCUMENT_STORAGE_ROOT` empty for database blob storage. The checked-in
Compose files do not create a writable document volume. Database blobs and
metadata are then included in the existing SQL backup process.

## Optional private persistent document volume

Only configure a storage root after provisioning a persistent private volume
with a documented backup and restore owner. The backend filesystem remains
read-only; the dedicated mount must be writable by the image's `nonroot` user,
and must not be shared with the frontend, web server, public upload directory or
untrusted processes. Do not use `/tmp`. All backend replicas must see the same
storage contents at the same configured path.

An operator-maintained Compose override may, after the volume is provisioned,
contain this example. It is documentation, not an enabled deployment override:

```yaml
services:
  backend:
    environment:
      PMS_DOCUMENT_STORAGE_ROOT: /var/lib/chrono/pms-documents
    volumes:
      - type: bind
        source: /srv/chrono/private/pms-documents
        target: /var/lib/chrono/pms-documents
        bind:
          create_host_path: false
```

The setting affects new uploads. Existing database blobs remain readable; file
records require the configured volume for later downloads. Changing the path or
clearing the setting does not migrate blobs back to the database. Preserve the
relative tenant/UUID keys when moving or restoring a volume.

Back up both MySQL metadata and the private blob volume as a coordinated recovery
set. The existing SQL backup, `update.sh` pre-deploy SQL dump and database restore
drill alone do not prove recovery of file-backed documents. The current off-site
backup mount also does not include this optional volume automatically. Add it to
the approved encrypted backup process and retention policy before activation.
Quiesce document writes for the backup window or use a coordinated storage
snapshot, and record the matching database backup and blob snapshot identifiers.

In an isolated restore drill, restore the matching database and volume, retain
all relative storage keys and verify a selection of downloaded files, sizes and
stored SHA-256 values, including older and archived versions. Test reads through
the authenticated application and verify write permissions before reopening
uploads. Do not delete old blobs merely because they are absent from a newer
backup; older retained database recovery points may still reference them.

See [payment automation](PMS_PAYMENT_AUTOMATION.md), [Beds24](PMS_BEDS24.md),
[billing automation](../pms-billing-automation.md) and
[backup and restore](BACKUP_RESTORE.md) for the corresponding workflows.
