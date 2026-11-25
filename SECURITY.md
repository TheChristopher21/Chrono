# Chrono security policy

## Reporting

Do not open public issues containing credentials, guest data, payment
references or exploit details. Report suspected vulnerabilities privately to
the Chrono operator and include the affected version, reproduction steps and
potential impact.

## Production security invariants

- Demo login, PMS test accounts, demo data and admin bootstrap are disabled.
- Every deployed image uses an immutable release tag and has passed CI.
- The application uses a dedicated MySQL account; root is never used by the
  backend.
- Production CORS origins are explicit public HTTPS URLs.
- JWT, NFC, ICS, database, Grafana and provider secrets are unique and stored
  outside Git.
- Card payments are rejected unless a configured gateway verifies a captured
  provider payment. Chrono never accepts PAN, CVC or card client secrets.
- Live PMS outbox traffic uses HTTPS, HMAC signatures and stable idempotency
  keys.
- Backup integrity and restore drills are verified before every release.

Run `powershell -ExecutionPolicy Bypass -File .\ops\preflight.ps1` before a
production deployment. Temporary dependency exceptions must be narrowly
scoped, enforced by CI and documented in
`docs/operations/SECURITY_EXCEPTIONS.md`.

## Supported versions

Only the currently deployed release is supported. Security fixes must be
released as a new immutable image tag; production containers must not track
`latest` or a mutable branch tag.
