# Chrono PMS release package

The release package is the hand-off between a tested source state and a
production deployment. It does not deploy anything by itself.

## Build a release

Log in to the Docker registry first when the application images should be
uploaded:

```powershell
docker login
powershell -ExecutionPolicy Bypass -File .\ops\package-release.ps1 `
  -Version 0.1.0 `
  -Registry chrisubuntu1 `
  -PushImages
```

Without `-PushImages`, the command still runs all tests, builds both
production images and creates a deployment ZIP below `release/`. Use
`-ExportImages` when the target server has no registry access and the images
must travel inside the ZIP.

The release command also runs the production dependency audit. Any
unapproved advisory stops the package. The one temporary, browser-only
React Router exception is documented in `SECURITY_EXCEPTIONS.md` and is
checked against both the exact package version and the production source.

`-SkipTests` and `-SkipImageBuild` are intended only when the exact source
state and exact immutable images have already passed those gates. The
manifest records when either step was skipped.

## Package contents

- `docker-compose.yml` and the complete `ops/` runtime configuration
- `.env.production.template` with the selected immutable image tag
- monitoring configuration and production runbooks
- `RELEASE_MANIFEST.json` with commit, dirty-state and image information
- `SHA256SUMS.txt` for every packaged file
- optional `chrono-pms-images.tar`

The ZIP itself is printed with its SHA-256 checksum. Store that checksum
separately from the uploaded file.

## Deploy the package

1. Extract it into a new versioned directory on the server.
2. Copy `.env.production.template` to `.env` and replace every placeholder
   with a unique production secret or endpoint.
3. Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\ops\preflight.ps1
docker compose pull
docker compose up -d
docker compose ps
```

For an exported image archive, run
`docker load --input .\chrono-pms-images.tar` before `docker compose up`.
Follow `GO_LIVE.md` for backup, restore drill, UAT, verification and rollback.
