# PMS commercial and hotel integrations

Chrono PMS includes provider-neutral workflows for direct booking, POS,
tourism tax, digital room access, accounting export and source-system
migration. Provider credentials remain outside the PMS and all provider side
effects use the signed integration outbox.

## Direct booking engine

Configure the public slug, HTTPS terms/privacy links and guarantee policy in
`Verkauf & lokale Integrationen`. The public page is then available at
`/book/<public-slug>`.

Public requests are rate-limited through the shared database. Availability is
calculated with bounded, batched inventory reads and public stays are limited
to 30 nights by default. Every booking write requires an `Idempotency-Key`,
repeats the availability check under the property lock and creates guest,
reservation, folio, history and outbox records in one transaction. A public
slug is globally unique and therefore cannot cross tenant boundaries.

New public bookings remain `TENTATIVE` for 15 minutes and receive a one-time
email verification link. An unguaranteed booking becomes `CONFIRMED` only after
verification. When a guarantee is required, verification extends the hold for
24 hours while the certified payment/guarantee workflow completes.

The guarantee flag creates a `DEPOSIT_REQUIRED` reservation. Payment capture
is intentionally not simulated: a live hotel must complete the hosted checkout
or terminal workflow with its certified payment provider before treating the
reservation as financially guaranteed.

## POS

POS tickets can be settled directly using a recorded payment method or posted
to an open guest folio as a service charge. Each ticket stores immutable line,
tax and gross totals. Direct card settlement represents a completed external
terminal transaction; card details must never be entered in Chrono.

## Tourism tax and registration

One property-specific tourism-tax rule defines adult/child nightly rates and
an optional maximum number of nights. Staff explicitly record the number of
chargeable children because age exemptions vary by municipality. A tax can be
posted only once per reservation and is added to the main open folio.

The existing digital registration workflow remains responsible for statutory
guest-registration data. Local authority exports are jurisdiction-specific and
must be approved against the hotel's municipality before go-live.

## Digital room access

Chrono stores only the provider name and an external credential reference. It
never stores a door PIN or mobile-key secret. Issue and revoke operations enter
the signed integration outbox as `access_credential.issue_requested` and
`access_credential.revoke_requested`. The gateway must acknowledge successful
delivery and reconcile active credentials with the lock provider.

## Accounting CSV

The export contains semicolon-separated, balanced debit/credit rows for folio
services, payments and direct POS tickets. Invoices are document snapshots and
are deliberately not exported as an additional revenue posting, which avoids
double-posting the underlying folio services.
Default Swiss-style accounts are:

| Posting | Account | Counter-account |
| --- | --- | --- |
| Room charge | 1100 | 3200 |
| Breakfast charge | 1100 | 3210 |
| Other/POS service | 1100 or payment account | 3220 |
| Cash payment | 1000 | 1100 |
| Card payment | 1020 | 1100 |
| Bank transfer | 1021 | 1100 |
| Tourism tax | 1100 | 3600 |

These are transport defaults, not accounting advice. The pilot hotel's chart
of accounts and import format must be signed off by its fiduciary/accounting
team before automated posting.

## Migration batches

Migration imports are transactional and idempotent per hotel and batch key.
Each reservation row carries an external reference, guest identity, mapped room
type/rate IDs, stay dates, expected gross amount and deposit. Chrono recalculates
the stay from its own rates and marks any mismatch as
`RECONCILIATION_REQUIRED`.

Example `reservations` value:

```json
[
  {
    "externalReference": "LEGACY-4711",
    "firstName": "Alex",
    "lastName": "Meier",
    "email": "alex@example.com",
    "phone": "+41790000000",
    "roomTypeId": 1,
    "ratePlanId": 1,
    "arrivalDate": "2026-09-01",
    "departureDate": "2026-09-03",
    "adults": 2,
    "children": 0,
    "expectedGrossAmount": 360.00,
    "depositAmount": 100.00
  }
]
```

Never proceed with cutover while a batch is marked
`RECONCILIATION_REQUIRED`.
