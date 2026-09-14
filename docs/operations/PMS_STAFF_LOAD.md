# Mixed hotel staff load

`ops/load/pms-staff.js` exercises simultaneous reception, housekeeping, finance
and management users across multiple hotels. Provide separately authenticated
staff tokens and hotel grants in `LOAD_STAFF_JSON`; do not store them in a file
that is committed or include them in command output. Each record contains
`token`, `propertyId`, `role`, and optionally a dedicated housekeeping `taskId`.
Use the selected hotel's date in `LOAD_BUSINESS_DATE`. Keep the run shorter than
token expiry. Default concurrency is five users per supplied role for five minutes.

Run with `k6 run --summary-export staff-load-summary.json ops/load/pms-staff.js`.
Set `LOAD_BASE_URL` for the staging target. Environment variables
`LOAD_RECEPTION_VUS`, `LOAD_HOUSEKEEPING_VUS`, `LOAD_FINANCE_VUS`,
`LOAD_MANAGEMENT_VUS` and `LOAD_DURATION` control concurrency and duration.

Reads cover paged 30-day room plans, operational data, housekeeper assignments,
invoice history, receivables and chain portfolio totals. To exercise writes,
both `LOAD_ALLOW_WRITES=true` and `LOAD_ENVIRONMENT=isolated` are required.
The default write fraction is 5%, configurable through `LOAD_WRITE_FRACTION`.
Reception users then need `GUESTS: MANAGE` and create synthetic guest profiles;
housekeepers need `HOUSEKEEPING: MANAGE` and toggle only the explicitly selected
fixture task. These writes remain in the isolated database. Restore the fixture
database after the run through the normal staging procedure. Use the existing
`pms-booking.js` workload in parallel for public reservation allocation pressure.

Candidate acceptance gates: read p95 below 750 ms, write p95 below 1500 ms,
unexpected failures below 1%, no authentication failures, controlled update
conflicts below 10%. The p99 gates are 2 s for reads and 3 s for writes. Capture
the summary, app/DB resource metrics, pool waits, audit/outbox lag and business
invariants together. Tune thresholds to the hotel's SLA and load representative
years of data before evaluating capacity. A passing run is evidence only for
the measured dataset, workload and infrastructure, not general chain capacity.

The default composite response contains the newest 50 historical entries.
`GET /api/pms/properties/{id}/history/{section}?page=0&size=50&query=...` retrieves
older pages with total count and `hasNext`; page sizes are capped at 100 and
search text at 120 characters. Sections are invoices, night-audits,
communications, outbox, audit, guest-registrations, resource-bookings,
pos-tickets, access-credentials and migration-batches. Each section applies its
hotel module permission before SQL reads. Summary projections omit message
bodies, outbox payloads and registration secrets.
