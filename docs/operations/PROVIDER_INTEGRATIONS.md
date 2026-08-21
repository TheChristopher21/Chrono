# PMS provider integrations

## Outbound provider gateway

Chrono sends versioned JSON envelopes to
`APP_PMS_PROVIDER_GATEWAY_ENDPOINT`. Each request includes:

- `Idempotency-Key: chrono-pms-<eventId>`
- `X-Chrono-Event-Id`
- `X-Chrono-Timestamp`
- `X-Chrono-Signature: v1=<HMAC-SHA256>`

The signature input is `<timestamp>.<raw request body>`. The gateway must:

1. Require HTTPS.
2. Reject timestamps outside the agreed clock window.
3. Verify the HMAC before parsing the event.
4. Store the idempotency key before applying side effects.
5. Return a non-2xx response for retryable or permanent failures.
6. Reconcile reservation, rate and inventory totals at least daily.

Provider-specific credentials stay in the gateway or an external secret
manager. Chrono stores only references such as `env:CHANNEL_PROVIDER_SECRET`.

The same gateway transports provider-neutral digital-lock issue/revoke events.
It must keep the supplied external credential reference opaque and must never
return or log raw room PINs, mobile-key secrets or lock-provider credentials.

## Inbound channel booking webhooks

Each channel connection exposes a random `webhookKey`. Providers submit to
`POST /api/public/pms/webhooks/channels/{webhookKey}/bookings` with:

- `X-Chrono-Timestamp: <unix-seconds>`
- `X-Chrono-Delivery-Id: <provider-unique-id>`
- `X-Chrono-Signature: sha256=<HMAC-SHA256>`

The signature input is `<timestamp>.<delivery-id>.<raw request body>`. Chrono
rejects expired timestamps, invalid signatures and a repeated delivery ID.
Responses contain only the result and reservation identifiers; guest and folio
data are never returned to the public provider endpoint.

## Stripe PMS card payments

Enable `APP_PMS_PAYMENTS_STRIPE_ENABLED=true` only after webhook and sandbox
acceptance. Chrono accepts an already captured PaymentIntent id. The intent
must have:

- status `succeeded`
- exact amount and hotel currency
- metadata `chronoPropertyId`
- metadata `chronoFolioId`

No PAN, CVC or Stripe client secret may be sent to or stored by Chrono.
Refunds and voids use Stripe idempotency keys derived from the Chrono payment.

## Still external

Provider contracts, OTA certification, production API keys, terminal
registration, sender domains/numbers and webhook allow-listing are external
activities. They cannot be completed from the repository and must be recorded
in the go-live evidence.
