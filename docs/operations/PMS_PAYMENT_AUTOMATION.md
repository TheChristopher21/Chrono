# Hotelbezogene Kartenzahlungen und Anzahlungsautomatik

Der PMS-Zahlungsbetrieb verwendet pro neuem Zahlungsauftrag einen dauerhaft gespeicherten Händlerkontext. `CONNECT:acct_…` sendet Stripe-Anfragen über das konkrete verbundene Konto; `PLATFORM:acct_…` verwendet das Serverkonto und prüft dessen Identität gegen den gespeicherten Kontowert. Ein Wechsel der Hoteleinstellung verschiebt bestehende Checkout-, Zahlungs- oder Erstattungsvorgänge nicht in ein anderes Händlerkonto. Geheimschlüssel werden ausschließlich auf dem Server konfiguriert.

## Konfiguration

- `APP_PMS_PAYMENTS_STRIPE_ENABLED=true` und `STRIPE_SECRET_KEY`: vorhandener Stripe-Adapter. `APP_PMS_PAYMENTS_SIMULATED_ENABLED=false` außerhalb isolierter Tests.
- `APP_PMS_PAYMENTS_STRIPE_WEBHOOK_SECRET`: Signaturgeheimnis des tatsächlich konfigurierten Stripe-Endpunkts.
- `APP_PMS_PAYMENTS_AUTOMATION_ENABLED=true`: aktiviert den regelmäßigen Zahlungs-/Erstattungs-/Webhook-Abgleich und den Scan optierter Hotels. Standard ist `false`, damit lokale oder ungeprüfte Installationen keine externen Vorgänge starten.
- `APP_PUBLIC_BASE_URL`: öffentliche Rückkehradresse. Die Rückkehrseite bucht keine Zahlungen.
- Im Hotel unter „Händlerkonto & automatische Anzahlungslinks“ das konkrete Konto einstellen und beim Anbieter prüfen lassen. Änderungen benötigen Masterberechtigung, Lesen und Zahlungsvorgänge Finanzrechte.
- Anzahlungslinks müssen zusätzlich **je Hotel** aktiviert werden. Der Hotelabsender und dessen Versand müssen über die Rechnung-/Versandeinstellungen freigegeben sein.

`GET/PUT /api/pms/properties/{id}/payment-settings` verwaltet Konto und Opt-in mit Versionsprüfung. `GET /api/pms/properties/{id}/payment-automation` zeigt die letzten 100 Checkout-Aufträge, Abgleichfehler und den letzten Anzahlungs-Scan. Bei einem Konflikt zunächst die gespeicherten Einstellungen neu laden. Bereits gespeicherte Zahlungsvorgänge behalten ihren eigenen Kontext.

## Stripe-Zustellung

Stripe erhält die Route `POST /api/public/pms/webhooks/stripe`. Der Empfänger prüft die Signatur gegen den unveränderten Body und eine Zeitabweichung von höchstens 300 Sekunden. Die Stripe-Ereignis-ID ist in der Inbox eindeutig. Ein erneut zugestelltes Ereignis erzeugt keinen zweiten Auftrag. Gespeichert werden Ereignis-/Objekt-/Kontoreferenzen, Status und Retry-Zeit, nicht der vollständige Nachrichtenbody.

Empfohlene Ereignisse: `checkout.session.completed`, `checkout.session.expired`, `checkout.session.async_payment_succeeded`, `checkout.session.async_payment_failed`, `payment_intent.succeeded`, `payment_intent.amount_capturable_updated`, `payment_intent.payment_failed`, `payment_intent.canceled`, `refund.created`, `refund.updated`, `refund.failed`. Verbundene Konten benötigen die passende Connect-Zustellung in Stripe.

Der Worker akzeptiert den Händlerbezug nur für das gespeicherte Konto. Er übernimmt keine Beträge oder Zahlungszustände aus dem Webhook in das Gastkonto: Er fragt den ursprünglichen Anbieterauftrag mit dessen gespeichertem Händlerkontext erneut ab und verwendet denselben Finanzablauf wie die manuelle Funktion „Status prüfen“. Dadurch gelten Foliozuordnung, Währung, Betrag, Anbieterreferenz, Wiederholungsschutz und offener Betriebstag auch bei automatischer Zustellung. Ein gesperrter Betriebstag oder ein vorübergehender Anbieterfehler lässt den Vorgang offen und wiederholbar. Veraltete Verarbeitungsclaims werden nach zehn Minuten erneut freigegeben.

Erstattungsereignisse können auch einen bereits beim Anbieter ausgeführten, lokal nach einem Verbindungsabbruch noch unbekannten Erstattungsauftrag zuordnen. Vor der Übernahme werden Händler, ursprüngliche PaymentIntent-ID, Betrag und Währung beim Anbieter geprüft. Es wird hierfür keine neue Erstattung angefordert. Eine unbekannte Antwort ohne gespeicherte Anbieter-ID wird nach dem bestehenden 23-Stunden-Limit nicht erneut als neuer Geldfluss erzeugt.

## Automatische Anzahlungslinks

Die Automatik berücksichtigt bestätigte Reservierungen mit gespeicherten Anzahlungsbedingungen. Fälligkeit und tatsächlicher offener Anzahlungsbetrag werden erneut ermittelt. Unmittelbar vor Anlage des Auftrags prüft der Finanzdienst unter Hotelsperre erneut Opt-in, Reservierungsstatus und Betrag. Stornierte Aufenthalte oder abgeschaltete Automatik erzeugen keinen neuen automatischen Auftrag.

Die stabile Vorgangs-ID lautet `deposit-reservation-<Reservierungs-ID>`. Wiederholung verwendet denselben Checkout-Auftrag und dieselbe Versand-ID. Die Versandwarteschlange verschickt den Zahlungslink nach ihrer eigenen Freigabe-/Retry-Logik; das wiederholte Einreihen derselben ID verschickt keine zweite Nachricht. Die Automatik speichert **keine Karten für spätere Belastungen** und zieht **keine Zahlung ohne Bestätigung des Gastes** ein. Autorisierungen/Einzüge bleiben explizite Finanzaktionen.

Pro Hotel wird ein begrenzter, fortgesetzter Scan ausgeführt, damit große Bestände nicht in einer einzigen unbeschränkten Abfrage geladen werden. Nach einem vollständigen Durchlauf beginnt der nächste bei der ersten Reservierung. Der Status zeigt blockierte Link-/Versandfälle. Eine nachträgliche Änderung eines bereits versandten Anzahlungsauftrags oder ein abgelaufener Link wird nicht durch einen zweiten automatischen Geldauftrag ersetzt; der gespeicherte Vorgang bleibt zur bewussten weiteren Bearbeitung verfügbar.

## Historische Händlerzuordnung

V39 erfindet keine Händlerkonten für ältere Zahlungen. Solange der ursprüngliche Kontext fehlt, bleiben Stripe-Aktionen für diese Datensätze gesperrt. Master können im selben Panel einen historischen Vorgang auswählen und das tatsächlich ursprüngliche Konto prüfen lassen. `POST /api/pms/properties/{id}/payment-settings/legacy-merchant` bindet eine alte Kartenzahlung oder einen alten Checkout-Auftrag erst nach Anbieterprüfung. Ein vorhandener Kontext lässt sich damit nicht überschreiben. Bei Originalzahlungen werden deren Erstattungsvorgänge ebenfalls konsistent zugeordnet. Fehlende historische Checkout-/Anbieterreferenzen müssen zuerst anhand der vorhandenen Anbieterunterlagen aufgeklärt werden.

## Nachweise und Grenzen

Regressionstests prüfen Merchant-Erhalt bei Hotelkonfigurationswechsel, signierte und manipulierte Zustellungen, Wiederholung, Händler-Fremdereignisse, Finanzsperren, falsche Erstattungsbeträge/-quellen sowie Anzahlungs-Opt-in und Storno. Diese Prüfungen ersetzen weder Merchant-Onboarding noch Tests der konkret ausgewählten Stripe-Konten, Zahlungsmittel und Länder. Der Adapter verwendet aktuell Hosted Checkout für Karten; andere lokale Zahlungsmethoden und physische Kartenterminals sind eigenständige Providerfunktionen.

Primärquellen: [Stripe Connect: kontobezogene API-Aufrufe](https://docs.stripe.com/connect/authentication), [Stripe: Webhooks, Signaturen und wiederholte Zustellungen](https://docs.stripe.com/webhooks), [Stripe: Signaturprüfung](https://docs.stripe.com/webhooks/signature).
