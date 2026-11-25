-- Preserve request_key byte-for-byte: it is already a Stripe metadata/idempotency identity.
-- Nullable additions permit old records to remain inspectable without inventing ownership.
ALTER TABLE pms_payment_requests ADD COLUMN client_request_key VARCHAR(80) NULL;
ALTER TABLE pms_payment_requests ADD COLUMN request_source VARCHAR(24) NULL;
ALTER TABLE pms_payment_requests ADD COLUMN automatic_reservation_id BIGINT NULL;

UPDATE pms_payment_requests SET client_request_key = request_key, request_source = 'CLIENT';

-- A legacy deposit belongs only to the reservation reached through its actual folio.
-- A key preempted in another tenant/folio is retained as CLIENT and cannot occupy this namespace.
UPDATE pms_payment_requests
SET automatic_reservation_id = (
    SELECT r.id FROM pms_folios f JOIN pms_reservations r ON r.id = f.reservation_id
    WHERE f.id = pms_payment_requests.folio_id
      AND CAST(pms_payment_requests.request_key AS BINARY(80)) = CAST(CONCAT('deposit-reservation-', r.id) AS BINARY(80))
)
WHERE CAST(created_by AS BINARY(120)) = CAST('deposit-automation' AS BINARY(120))
  AND kind = 'PAYMENT';

UPDATE pms_payment_requests SET request_source = 'AUTOMATIC_DEPOSIT' WHERE automatic_reservation_id IS NOT NULL;

ALTER TABLE pms_payment_requests ADD CONSTRAINT uk_pms_payment_client_scope UNIQUE(folio_id, request_source, client_request_key);
ALTER TABLE pms_payment_requests ADD CONSTRAINT uk_pms_payment_auto_reservation UNIQUE(automatic_reservation_id);
ALTER TABLE pms_payment_requests ADD CONSTRAINT fk_pms_payment_auto_reservation FOREIGN KEY(automatic_reservation_id) REFERENCES pms_reservations(id);
