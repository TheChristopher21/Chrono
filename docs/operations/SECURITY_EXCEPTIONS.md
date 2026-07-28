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
