# API CORS behind Nginx Proxy Manager

Spring Security (`SecurityConfig.corsConfigurationSource`) owns the explicit
origin, method and header allowlists, including `X-Chrono-Company` for the
superadmin company view and `Idempotency-Key` for writes.

The NPM host `api.chrono-logisch.ch` must forward OPTIONS to the backend and
pass its CORS response headers through. Do not add `Access-Control-*` headers
or an `if ($request_method = 'OPTIONS') { return 204; }` block in the host's
Advanced configuration. That short-circuits Spring and can cause duplicate
allow-origin headers on actual requests.

## Verification

Run `python3 ops/gateway/check-api-cors.py` after API/proxy deployments. This
checks the public endpoint for both production frontend origins, company
headers, write methods and rejection of unapproved origins/headers. It sends
only OPTIONS and unauthenticated GET requests; no application writes.

## Legacy configuration repair

On 2026-10-12 the API host still returned a hardcoded 204 with only
`Authorization,Content-Type,Origin`. The deployed backend already allowed
`X-Chrono-Company`, but never received the preflight.

`python3 ops/gateway/fix-api-cors.py` checks for that exact legacy block in
both NPM's SQLite host record and its generated file (dry run). First verify
the backend's direct OPTIONS response, then use `--apply` to remove it.
The script refuses unfamiliar configurations/custom locations, saves the
affected host record and config under `data/cors-backups/`, checks `nginx -t`
and reloads Nginx. It restores the original state on failure. No containers
are replaced. The NPM database is updated as well so a later UI save does not
restore the obsolete rules. Ordinary app deployments do not modify NPM.

The live repair succeeded on 2026-10-12; its backup is
`/root/chrono/data/cors-backups/20261012T063841534260Z`.
