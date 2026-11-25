# Temporary dependency security exceptions

## React Router RSC advisory

- Advisory: `GHSA-qwww-vcr4-c8h2`
- Accepted version: `react-router` and `react-router-dom` `7.18.2`
- Scope: temporary, until the upstream project publishes a fixed stable
  release

The advisory affects React Router's server-side RSC mode and action
execution. Chrono is a client-rendered Vite application. It uses
`BrowserRouter`, `Routes`, links and client navigation, and does not expose
React Server Components, React Router server actions or an RSC request
handler.

`npm run audit:prod` enforces this exception narrowly. It fails when:

- any other production dependency advisory is present;
- the installed React Router version changes;
- a React Router RSC/server API marker appears in production source; or
- the accepted advisory is replaced by a different finding.

Review and remove the exception as soon as a fixed stable React Router
version is available.

## Removed DOMPurify exception

On 2026-10-12, the transitive DOMPurify override was updated from `3.4.12`
to `3.4.16`, fixing both `GHSA-55q2-fjhq-7xh7` and
`GHSA-6688-9rhm-gjv2`. The former DOMPurify/jsPDF exception and its exact
version check have been removed. New DOMPurify findings fail the audit.

The same update raises Axios from `1.18.1` to `1.20.0`, resolving its reported
production advisories. No Axios exception is granted. Audit execution errors
also fail the build; they are never treated as accepted advisories.
