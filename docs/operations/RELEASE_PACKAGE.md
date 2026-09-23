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

- `docker-compose.production.yml`, the protected `update.sh` and complete
  `ops/` runtime configuration
- `docker-compose.yml` only as an explicitly profiled infrastructure
  reference; it is not an application deployment file
- `.env.production.template` with the selected immutable image tag
- monitoring configuration and production runbooks
- `RELEASE_MANIFEST.json` with commit, dirty-state and image information
- `SHA256SUMS.txt` for every packaged file
- optional `chrono-pms-images.tar`

The ZIP itself is printed with its SHA-256 checksum. Store that checksum
separately from the uploaded file.

## Deploy the package

1. Verify the package checksum and image tag.
2. Copy only the reviewed release files into the existing production
   checkout. Preserve the existing `.env`, data paths and Docker volumes.
3. Run the protected preflight and application deploy:

```powershell
powershell -ExecutionPolicy Bypass -File .\ops\preflight.ps1
.\update.sh --image-tag <immutable-release-tag>
```

For an exported image archive, run
`docker load --input .\chrono-pms-images.tar` before `update.sh`.
Follow `GO_LIVE.md` for backup, restore drill, UAT, verification and rollback.

Do not run the full infrastructure Compose file on a host where
`chrono_chrono` is shared with other projects. The production application
compose deliberately has no MySQL, proxy, monitoring or LLM service.
