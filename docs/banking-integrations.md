# Banking & Zahlungsverkehr Integrationen

Das Banking-Modul der Chrono-Plattform kann nun echte Zahlungs- und Signaturprozesse
anbinden. Die folgenden Abschnitte erläutern die verfügbaren Integrationen und wie
sie konfiguriert werden.

## Konfiguration

Alle Einstellungen erfolgen über `application.properties` bzw. Umgebungsvariablen.

```properties
# Zahlungen (pain.001 Übermittlung)
banking.integrations.payments.enabled=true
banking.integrations.payments.endpoint=https://banking.example.com/api/pain001
banking.integrations.payments.api-key=${BANKING_API_KEY}

# Digitale Signaturen
banking.integrations.signatures.enabled=true
banking.integrations.signatures.base-url=https://sign.example.com
banking.integrations.signatures.api-key=${SIGNATURES_API_KEY}
banking.integrations.signatures.poll-interval=PT2M

# Sichere Nachrichten (EBICS / SWIFT / Secure-Mail)
banking.integrations.messages.enabled=true
banking.integrations.messages.endpoint=https://secure-msg.example.com/api/messages
banking.integrations.messages.api-key=${MESSAGES_API_KEY}
```

Alle Integrationen verwenden einen Bearer-Token für die Authentisierung. Wird eine
Integration als `enabled=false` belassen, verhält sich der Dienst weiterhin wie zuvor
und simuliert den Ablauf – Rückgabewerte zeigen dann `providerStatus=SIMULATED`.

## Zahlungsübermittlung (pain.001)

Der `PaymentGatewayClient` sendet das generierte pain.001 XML (inklusive Idempotency-
Header) an das konfigurierte Endpoint. Die Antwort sollte ein JSON mit mindestens der
`reference` enthalten. Diese Referenz wird auf dem Batch gespeichert und erneut
verwendet, wenn der Idempotency-Key übereinstimmt.

## Digitale Signaturen

* `POST /requests` erwartet die Felder `documentType`, `documentPath`, `signerEmail`.
* `GET /requests/{reference}` liefert den Status (`PENDING`, `IN_PROGRESS`, `COMPLETED`,
  `FAILED`) sowie optional eine `signingUrl`.
* `POST /requests/{reference}/complete` schließt die Signatur ab.

Das System pollt offene Anfragen im Intervall `banking.integrations.signatures.poll-interval`
und aktualisiert den Status automatisch.

## Sichere Nachrichten

Sichere Nachrichten werden als JSON an das konfigurierte Endpoint gesendet. Die
Erwartung an die Antwort ist ein JSON mit

```json
{ "delivered": true, "reference": "abc-123", "status": "DELIVERED", "message": "optional" }
```

Schlägt die Zustellung fehl oder ist die Integration deaktiviert, bleibt der
Nachrichtendatensatz gespeichert, aber `delivered=false` und `providerStatus=SIMULATED`.
