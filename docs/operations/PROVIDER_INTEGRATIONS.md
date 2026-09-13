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

## Beds24 API V2

The concrete Beds24 adapter publishes mapped daily prices, availability and
calendar restrictions through the documented provider endpoints. Hotel-scoped
settings, persistent publication retries, provider booking comparison and verified
links to existing reservations are available in the integration panel. See the
[Beds24 adapter runbook](PMS_BEDS24.md) for configuration, contract tests and the
exact supported scope. Generic gateway events are not substitutes for this
adapter's provider-specific contract or its booking reconciliation.

## Stripe PMS card payments

The implemented Stripe adapter creates hosted Checkout sessions for immediate
payment or a manual-capture card authorization. Card details are entered on
Stripe. Existing captured PaymentIntent references can also be verified and
posted through the original folio-payment workflow.

Configure `APP_PMS_PAYMENTS_STRIPE_ENABLED=true`, `STRIPE_SECRET_KEY` and the
public application URL (`APP_PUBLIC_BASE_URL`). Keep
`APP_PMS_PAYMENTS_SIMULATED_ENABLED=false` in production. The current adapter
stores an explicit platform or Stripe Connect merchant context on every new
payment request, payment and refund. Hotel configuration changes apply to new
requests; existing records retain their original account. Validate the merchant/account and supported
hotel currencies in an isolated Stripe test environment before enabling live
payments. Never put a secret key in browser configuration or runbook evidence.

The hotel-scoped API requires FINANCE permissions (VIEW to list, MANAGE to
create, capture, release or reconcile):

| Request | Purpose |
| --- | --- |
| `GET /api/pms/properties/{propertyId}/folios/{folioId}/payment-requests` | Read locally recorded requests/statuses |
| `POST /api/pms/properties/{propertyId}/folios/{folioId}/payment-requests` | Create `{requestId,kind:"PAYMENT"\|"AUTHORIZATION",amount}` |
| `POST /api/pms/properties/{propertyId}/payment-requests/{id}/reconcile` | Retrieve and verify the current provider state |
| `POST /api/pms/properties/{propertyId}/payment-requests/{id}/capture` | Capture the authorization with `{amount}` |
| `POST /api/pms/properties/{propertyId}/payment-requests/{id}/release` | Release the authorization or expire the open checkout |

A committed local intent precedes each provider operation. Checkout, capture
and release use Stripe idempotency keys `chrono-checkout-<requestId>`,
`chrono-capture-<requestId>` and `chrono-release-<requestId>`. Repeating a create
request requires the same folio, amount, kind and `requestId`; a different
payload is rejected. After an uncertain response, use the existing request and
reconcile it. Do not create a replacement request merely because a network
response was lost. Once capture has started its amount is fixed.

Chrono verifies session ownership, `chronoPropertyId`, `chronoFolioId`, currency
and received/capturable amounts against its stored intent. Only a verified
`succeeded` PaymentIntent creates a posted folio payment. An authorization
records a guarantee, not a payment. A provider transaction ID is reused rather
than posted twice. A closed financial day can prevent local posting even after
the provider succeeded; reconcile that case after resolving the financial-day
block, without initiating another payment.

The public `/pms-payment-return` page is informational. It performs no payment
posting and is not evidence that a guest paid. The UI periodically reloads the
local list; **Status prüfen** explicitly retrieves the Stripe state. This PMS
workflow also supports a signed Stripe webhook inbox and an opt-in server
reconciliation scheduler. Both use the same provider verification and financial
rules as the manual operation. See [payment automation](PMS_PAYMENT_AUTOMATION.md)
for configuration, hotel-owned accounts, idempotent deposit-link delivery and
verified migration of historical merchant references. Provider payout/settlement
totals still require reconciliation with the actual financial system.

Refunds persist a local intent before contacting Stripe. The request ID, amount
and reason must remain stable for retries. Stripe uses
`chrono-pms-refund-<localRefundId>`; if a provider refund ID is already known,
Chrono retrieves it on retry. Provider `pending` remains locally pending,
`succeeded` becomes posted, and `failed`/`canceled` becomes failed. An unknown
provider result with no saved provider identifier is not re-created after the
23-hour safety window: manually reconcile it with Stripe first. Voiding an
already captured card payment follows the refund workflow, not cancellation
of the successful payment.

PMS amounts follow the hotel currency's ISO fraction digits (0–4), and reject
input below its smallest unit. The Stripe adapter separately translates
provider wire amounts: zero-decimal currencies including MGA use integer wire
amounts; ISK/UGX accept whole amounts but retain Stripe's two-decimal wire
representation. This conversion does not imply that every PMS currency or
merchant country is supported by Stripe.

No PAN, CVC or Stripe client secret is accepted or stored by the PMS. Stored
provider session/payment/refund identifiers and checkout URLs remain sensitive
operational metadata and belong only in authorized records and restricted
go-live evidence.

## Still external

Provider contracts, OTA certification, production API keys, terminal
registration, sender domains/numbers and webhook allow-listing are external
activities. They cannot be completed from the repository and must be recorded
in the go-live evidence.
