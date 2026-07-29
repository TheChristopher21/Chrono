# Chrono PMS go-live runbook

## Release gate

A release is eligible for production only when all of the following are true:

1. Backend, frontend and browser tests pass for the exact commit.
2. Images are built from that commit and use one immutable release tag.
3. `ops/preflight.ps1` passes against the production environment file.
4. A fresh backup exists, its checksum passes and the restore-test profile
   completes successfully.
5. Monitoring is green and a test alarm has reached the responsible person.
6. The hotel has signed off every scenario in `UAT_CHECKLIST.md`.
7. Migration counts and financial opening balances have been reconciled.
8. A named go/no-go owner and rollback owner are present.

## Deployment

The active production host shares `chrono_chrono` with other company
projects. An application release must therefore use only
`docker-compose.production.yml`. That file contains exactly `backend` and
`frontend`, treats `chrono_chrono` as external, and cannot manage MySQL,
volumes, Nginx Proxy Manager, monitoring, LLM or unrelated containers.

```powershell
powershell -ExecutionPolicy Bypass -File .\ops\preflight.ps1
.\update.sh --image-tag <immutable-release-tag>
```

`update.sh` verifies the existing MySQL container and named volume, verifies
the dedicated application database account, records all other running
containers, creates and checksum-validates a fresh SQL backup, tags the
previous app images for rollback, and then replaces only backend and
frontend. It aborts if MySQL, the shared network, proxy, LLM, Open WebUI,
free disk space or the protected topology do not match expectations.

Flyway may add a tested schema migration during backend startup. Every
migration must be additive, pass staging first and preserve the core row
counts checked by the deployment. The script never performs an automatic
database rollback. If application verification fails, only the previous
application images are restored while the verified SQL backup is retained.

Never use the infrastructure Compose file on the active shared host. In
particular, never run `docker compose down`, `docker compose down -v`,
`docker compose up --remove-orphans`, `docker volume prune`,
`docker system prune --volumes` or `docker volume rm`.

## Alert delivery

Prometheus evaluates the rules in `ops/monitoring/alerts.yml`. Alertmanager
groups and de-duplicates matching alerts and sends them to
`ALERT_EMAIL_TO` through the configured `SPRING_MAIL_*` SMTP account. A
successful test message is part of the release gate; Alertmanager must not
be configured with a placeholder recipient.

## Verification

- `/actuator/health` returns healthy through the internal network.
- Login succeeds for one administrator and one front-office user.
- A test reservation can be created, changed and cancelled.
- The outbox has no unexpected dead-letter events.
- Card payment verification is tested with the provider sandbox before live
  keys are enabled.
- Channel inventory and reservation reconciliation returns zero unexplained
  differences.
- Grafana receives metrics and alert delivery is tested.

## Rollback

1. Stop new staff activity and provider ingestion.
2. Record the last successful outbox event and business date.
3. Let the protected deploy script restore the locally tagged previous
   application images. Never downgrade the database automatically.
4. If a database restore is required, preserve the failed database volume,
   restore into a new database and reconcile provider events before reopening.
5. Resume provider delivery only after reservation and payment totals match.
