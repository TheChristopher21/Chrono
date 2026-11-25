# PMS hotel access

The company remains the tenant boundary. A PMS master has both `pms: MANAGE` and
`pmsSettings: MANAGE`, and administers only hotels and staff in that company.
Other staff require explicit hotel grants. Existing staff grants start empty;
assign them from the master account before staff resume operations after release.
An overall `pms: VIEW` permission caps every hotel grant at view access.

`GET /api/pms/access/me` returns `userId`, `master`, `permissionKeys`, and
`properties: [{propertyId, propertyName, permissions}]`. Permission keys are
`FRONT_DESK`, `GUESTS`, `HOUSEKEEPING`, `FINANCE`, `REFUNDS`, `RATES`, `REPORTS`,
and `INTEGRATIONS`. Levels are `VIEW` or `MANAGE`; missing means no access.

Masters use `GET /api/pms/access/users` for the tenant's users and hotels, then
`PUT /api/pms/access/users/{userId}` with
`{ "grants": [{ "propertyId": 1, "permissions": { "FRONT_DESK": "MANAGE" } }] }`.
The update replaces all hotel grants atomically, validates tenant membership
before deleting anything and records an audit event in affected hotels. `NONE`
removes a permission. Master accounts cannot be downgraded through this API.

A shared HTTP interceptor and converted-body advice enforce hotel access before
controller mutations. Resource IDs resolve their actual hotel; changing hotels
requires access to both. Composite read responses and portfolio totals are
reduced to permitted hotels/modules. Housekeeping room occupancy hides guest
identity unless reception access is present. Hotel configuration, access
administration and global privacy operations remain master actions.

Local rate creation, edits and day overrides require `RATES: MANAGE` for the
hotel; they do not require master access. Publishing a source rate to selected
other hotels remains a master action with explicit target prices and tax rates.

Organization document list/upload/download/delete requests from staff must
include `propertyId`. `GUESTS` access is required (manage for upload/delete),
and the organization must have a hotel reservation or corporate rate relationship.
A rate-specific document is visible only within its rate's hotel. New companies
can first be linked through a corporate rate or booking; masters can manage
unlinked company records. Frontend visibility is supplementary to these checks.
