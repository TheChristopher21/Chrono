# Beds24 API V2 adapter

Chrono contains a concrete Beds24 client, hotel configuration, calendar publication
queue, reconciliation workflow and PMS integration panel. It does not require a
custom gateway translating Chrono envelopes into Beds24 requests.

## Implemented capabilities

- Hotel-owned Beds24 property and server refresh-token reference, with external
  property, currency and room ownership verification before activation.
- Explicit mapping of a Chrono public rate to a Beds24 room and daily price slot
  1–16. One Chrono category corresponds to one external room category, preventing
  the same capacity from being published into several external inventories.
- Gross nightly prices for the rate's included adult occupancy; local override
  prices and breakfast/tax allocation use the normal PMS pricing rules.
- Remaining inventory from sellable rooms, reservations including room moves,
  active room blocks and group holds. Min/max stay, arrival/departure closures and
  blackout dates are published alongside prices. Closed prices use blackout,
  since removing a daily price could leave another provider price available.
- Persisted publication snapshots with hotel, currency, token reference, request
  ID, date range and exact JSON payload. Retries reuse the saved payload. An
  uncertain browser response retains the same request ID and date range.
- Full paginated booking comparison, including provider cancellations. Staff can
  link an existing Chrono reservation after the provider is queried again and
  dates, occupancy, status, rate/room mapping, currency and price match. Existing
  provider references cannot be reassigned through this workflow.

## Configuration and operator workflow

1. In Beds24, obtain a refresh token with access to the hotel's properties,
   bookings and inventory. Configure the intended daily-price occupancy, taxes,
   price rules and OTA/channel assignments in Beds24.
2. Store the refresh token in a server environment variable such as
   `HOTEL_42_BEDS24`. In the PMS master settings enter only
   `env:HOTEL_42_BEDS24`, the external property ID and room/rate mappings.
3. Enable and save the connection. This performs a read-only provider check.
4. Set `APP_PMS_BEDS24_WORKER_ENABLED=true` when the publication queue should be
   processed. The default is false. The fixed official endpoint is
   `https://beds24.com/api/v2/`; `APP_PMS_BEDS24_BASE_URL` may select another
   supported official Beds24 HTTPS base. Browser-supplied endpoints are absent.
5. Run **Buchungen abgleichen**. Create/correct missing reservations through the
   PMS reservation workflow, preserving the actual accepted OTA price and tax
   basis, then use **Prüfen & verknüpfen** with the Chrono reservation ID.
6. Choose a period of at most 31 nights, starting today or later in the hotel
   timezone, and queue the current calendar. The end date is exclusive in Chrono;
   each provider row represents one calendar night.
7. Check the transfer journal. Only a successful response for every room marks
   a publication complete. A partial HTTP 201 response is not sufficient.

The worker rechecks external property ownership, active external bookings and
the current PMS snapshot before each provider write. Changed local prices or
stock, a date range now in the past, or unresolved active OTA bookings stop the
job with an actionable error. Discard the old job and queue a fresh snapshot
after resolving the issue. Configuration changes and newer publications are
blocked while an older pending, processing or failed job remains unresolved.
Provider failures retry with a bounded delay, respecting Beds24's credit reset
header, and become failed after eight attempts. An in-flight claim has a
ten-minute recovery lease; ordinary HTTP requests time out after twenty seconds.

## Boundaries requiring explicit setup or further adapters

Publishing is an explicit operator action; the worker processes queued jobs. It
does not continuously regenerate inventory after every reservation mutation.
The reconciliation/linking workflow does not automatically create bookings,
overwrite an accepted reservation price, post invoices or apply cancellations.
The snapshot check cannot make the remote API and the local database one atomic
transaction. Inventory still needs ongoing operational reconciliation.

Calendar limits apply per external room, so contradictory restrictions across
mapped price slots are rejected. Rates with advance-booking constraints are
blocked until the corresponding Beds24 price-rule configuration is supported;
corporate rates are excluded. Occupancy supplements, policies and channel-specific
offers are not inferred from a price slot. No direct OTA certification, channel
activation, settlement feed, terminal or door-lock vendor implementation is
claimed by this adapter.

## Verification and references

`PmsBeds24ClientTest` runs against an in-process loopback HTTP server. It checks
the concrete URLs/headers, JSON calendar request, per-item confirmation, token
renewal, rate limits, pagination and property/currency isolation.
`PmsBeds24ServiceTest` covers pricing, inventory, restricted mappings, stable
publication identities, failed/stale jobs and verified booking links. Frontend
tests cover retained drafts, uncertain-request retries, read-only access and
rejected/successful links. These tests do not call Beds24 or transmit guest data.

Contracts were checked against the official
[Beds24 OpenAPI specification](https://beds24.com/api/v2/apiV2.yaml),
[interactive API reference](https://beds24.com/api/v2/) and
[API V2 guide](https://wiki.beds24.com/index.php/API_V2.0).
Provider-side activation and verification with a hotel's credentials remain
deployment tasks; no production connection was opened during implementation.
