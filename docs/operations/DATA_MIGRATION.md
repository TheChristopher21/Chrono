# PMS data migration and reconciliation

## Inputs

Obtain versioned exports for properties, room types, rooms, rates, guests,
future reservations, deposits, open folios, companies and channel references.
Never edit the only source export.

## Staging import

1. Normalize dates, time zones, currencies and external identifiers.
2. Validate required references before writing any row.
3. Import into an empty staging tenant.
4. Produce counts and money totals for every entity type.
5. Run the UAT checklist with migrated records.

Use the idempotent migration-batch workflow described in `PMS_EXTENSIONS.md`
for future reservations and deposits. Re-running the same hotel/batch key must
not create duplicate guests, reservations or payments. A batch with calculated
price differences is stored as `RECONCILIATION_REQUIRED` and blocks cutover.

## Cutover

1. Agree a booking freeze or delta-export window.
2. Take and verify a source-system export and Chrono backup.
3. Import the final delta idempotently.
4. Reconcile future reservations by arrival date, room type and status.
5. Reconcile every deposit and open folio balance exactly.
6. Enable channel ingestion only after inventory totals match.

Any unexplained reservation, payment or folio difference is a go-live blocker.
