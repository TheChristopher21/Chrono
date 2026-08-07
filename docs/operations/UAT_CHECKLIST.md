# Hotel user acceptance checklist

Record tester, date, release tag and evidence for every scenario.

## Front office

- Create, edit, confirm, cancel and no-show a reservation.
- Detect overlapping reservations and unavailable rooms.
- Check in only to a ready room and move an in-house guest.
- Check out only after the folio is balanced.
- Create split folios, corrections, invoices and credit notes.
- Verify cash shift opening, payment, refund, closing and variance.
- Verify a real provider-sandbox card payment, refund and failed payment.

## Inventory and channels

- Map every live room type and rate code.
- Send rates, restrictions and availability to the provider sandbox.
- Import new, changed and cancelled OTA bookings idempotently.
- Reconcile availability after retries, downtime and out-of-order webhooks.
- Confirm dead-letter alerts and manual replay.

## Direct sales, POS and local integrations

- Search and book the final room through the public booking page under load.
- Confirm a concurrent front-office/online attempt cannot oversell inventory.
- Verify terms/privacy consent and the configured guarantee status.
- Settle a POS ticket directly and post another ticket to a guest folio.
- Reconcile POS tax and gross totals with an independent calculation.
- Post tourism tax with adult, child and maximum-night variations exactly once.
- Issue and revoke a sandbox room credential and reconcile it with the lock provider.
- Import the accounting CSV into the hotel's staging ledger and reconcile totals.

## Housekeeping and maintenance

- Generate arrival/departure cleaning tasks.
- Change room status and prevent sale of blocked rooms.
- Open, resolve and audit maintenance work.

## Night audit and reporting

- Process arrivals, departures and no-shows.
- Close the business date exactly once.
- Compare occupancy, ADR, RevPAR, revenue, taxes and open balances against
  independently calculated test values.

## Privacy and access

- Verify reception, housekeeping, accounting and administrator permissions.
- Export a guest record and execute the approved anonymization workflow.
- Confirm document numbers are not stored in plaintext.
- Verify expired and reused digital registration links fail.

## Resilience

- Restart backend and database during a controlled test.
- Confirm queued provider events recover without duplicates.
- Restore the latest backup into the isolated restore environment.
- Test a monitoring alarm and the escalation path.
- Retry booking, POS and access-provider requests after timeouts without duplicates.
