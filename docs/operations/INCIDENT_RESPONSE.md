# Incident response

## Severity

- **Critical:** login compromise, guest-data exposure, payment mismatch,
  database loss or hotel unable to operate.
- **High:** provider/channel outage causing inventory risk, repeated 5xx
  errors or backup failure.
- **Normal:** isolated workflow defect with a documented workaround.

## First response

1. Name an incident owner and start a timestamped log.
2. Preserve application, proxy, database and provider logs.
3. Stop the smallest unsafe operation: provider ingestion, card posting or
   the whole application.
4. Rotate exposed credentials; never paste them into tickets or chat.
5. Reconcile reservations and payments before reopening.

## Recovery evidence

Record the release tag, database backup, restore/checksum result, affected
outbox event ids, payment provider references, reconciliation totals and the
person authorizing reopening.
