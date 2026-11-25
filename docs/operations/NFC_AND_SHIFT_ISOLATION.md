# NFC and shift definition ownership rollout

This release does not rewrite existing companies, employees, time entries,
overtime balances, payslips, schedules, shared shift values, or raw NFC command
data. V59 adds nullable ownership and indexes. Legacy shift definitions remain
immutable shared defaults. Legacy NFC commands remain stored with null ownership
and are excluded from every tenant/device poll and status/completion operation.
The migration also tolerates older installations without either optional table.

## Existing live NFC device: Praxis

If no reader is configured yet, the interactive production deploy looks up the
exact company name `Praxis` and displays its ID. Confirm the ID only if the
existing reader belongs to that company. The deploy saves the assignment with
the existing token. For unattended deploys, configure the assignment in advance.

Keep the existing live `NFC_AGENT_TOKEN` and existing agent request shape. Before
deploying with a manual assignment, resolve the actual company ID from the
deployment database:

```sql
SELECT id, name FROM companies WHERE name = 'Praxis';
```

Proceed only if this identifies exactly one operator-confirmed company. A name
does not establish ownership of old commands and is never used by the application
to assign them. Do not guess an ID or use the first company. Configure the backend
environment with that ID and a stable name for the existing physical device:

```dotenv
NFC_AGENT_COMPANY_ID=<confirmed Praxis ID>
NFC_AGENT_DEVICE_ID=praxis-reader
NFC_AGENT_REGISTRY_JSON=[]
```

Ensure these variables reach the backend container/process. The existing opaque
token authenticates precisely that company/device. Its unchanged `X-Agent-Token`
header works for polling, completion and punches. The existing
`X-NFC-Agent-Request: true` convention still selects NFC punch source. Without
ownership configuration, agent authentication fails closed and enqueue fails with
an actionable NFC configuration error; unrelated application operations do not
depend on that configuration. Keep `NFC_AGENT_ALLOW_LOCALHOST_LEGACY=false` in
production. Even an explicitly enabled local development bypass needs ownership.

Old command rows cannot be assigned automatically from usernames or raw data.
Review them separately using operator evidence. For a still-needed PROGRAM
request, create a fresh command through the tenant UI after checking its intended
employee and device. Keep the original ownerless row for review; do not bulk
backfill, replay or delete it. The current UI continues sending
`{"type":"PROGRAM","data":"username"}`. The backend validates that the target
employee belongs to the actor's selected company and derives the destination
device from server configuration. Status lookup is company scoped; polling and
completion additionally require the exact device.

New PROGRAM commands also store the validated employee's immutable ID. Dispatch
rechecks that ID, original username and company immediately before returning a
command. Renamed, transferred, deleted or unbound targets are marked `cancelled`
with a processing timestamp; raw command data stays unchanged. Polling examines
at most 25 pending commands per request and skips cancelled work, so a stale head
cannot indefinitely block later valid requests. A cancelled status is terminal;
the current UI may report its normal programming timeout, and an administrator
can inspect status and create a fresh request after reviewing the employee.

## Optional additional independent agents

The server may supply a JSON array in `NFC_AGENT_REGISTRY_JSON`:

```json
[
  {"companyId":17,"deviceId":"reception-a","tokenEnv":"NFC_RECEPTION_A_TOKEN","defaultForCompany":true,"localHardware":false},
  {"companyId":29,"deviceId":"reception-b","tokenEnv":"NFC_RECEPTION_B_TOKEN","defaultForCompany":true,"localHardware":true}
]
```

The IDs above are illustrative. Supply each referenced secret environment
variable to the backend separately. Do not put literal tokens in JSON or commit
secret values. Tokens and company/device identities must be unique. With several
devices for a company, exactly one must be marked `defaultForCompany` for UI
enqueue. At most one registration may own `localHardware`, the physical reader
attached to this backend. The legacy configured token owns that local reader, so
do not additionally mark a registry device as its owner. Agents on remote
workstations use their own hardware; they may poll and punch without access to the
backend reader. Existing deployment startup secret validation still applies.

The backend's direct read/write-sector0 endpoints require the authenticated local
reader owner. Sector 0 programming must contain a same-company username encoded
as 16 UTF-8 bytes with trailing zero padding. Administrative direct `/write` also
requires an authenticated administrator in that reader's company. Agent punches
check the requested employee's company even if another authenticated principal is
present in the request.

## Shift definitions

Unedited tenants retain each legacy default's existing ID, key, label and times.
Editing a shared default copies the submitted definition into an owned override;
the shared row remains unchanged. Each company may define the same key with
different values. Actor-selected company scope is required, including for a
superadministrator; request bodies cannot choose ownership. Foreign-owned IDs
return not found. Schedule duration/overlap calculations use the target employee's
actual company, including when a superadministrator manages another company.

Deleting an inherited or owned definition creates a company-local suppression.
It disappears from that company's selectable definitions without exposing the
shared default again. Its times remain available when evaluating existing
scheduled entries, so deletion does not erase their duration. Saving that key
again restores it locally. Renaming a key publishes a company-owned definition
under the new key and suppresses the old key locally; existing entries retain
their old key and duration. A rename cannot replace another visible key. Demo
shift seeding writes only the demo company's definitions.

Before manual production release, verify the existing Praxis punch request, an
unauthorized other-company punch, own-device polling/completion, an ownerless
legacy command, and a two-company shift edit in a restored staging database.
Preserve the pre-release backup and confirm time/payroll snapshots remain equal
apart from deliberate new test punches in staging. No deployment or historical
row ownership reassignment is performed by this change.
