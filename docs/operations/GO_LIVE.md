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

Create and verify the versioned hand-off described in
`RELEASE_PACKAGE.md`. The image tag in the production `.env` must match the
manifest and the uploaded images.

```powershell
powershell -ExecutionPolicy Bypass -File .\ops\preflight.ps1
docker compose pull --ignore-buildable
docker compose build mysql-backup alertmanager
docker compose up -d mysql
docker compose up -d --remove-orphans --wait --wait-timeout 600
docker compose ps
```

Flyway runs as part of backend startup. Apply every migration to staging
first, start one backend instance, inspect the Flyway log and health result,
then allow staff or providers to send production traffic.

The production Compose project deliberately keeps the existing Nginx Proxy
Manager, Ollama and Open WebUI services in the project. Their established
bind mounts and named volumes must not be replaced during an application
release.

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
3. Revert application images to the previous immutable tag. Never downgrade
   the database automatically.
4. If a database restore is required, preserve the failed database volume,
   restore into a new database and reconcile provider events before reopening.
5. Resume provider delivery only after reservation and payment totals match.
