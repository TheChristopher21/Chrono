# Enterprise-PMS-Abnahme

Dieses Dokument definiert die technische Freigabe für größere Hotels und Hotelgruppen. Eine Freigabe gilt nur für die konkret geprüfte Infrastruktur, Datenmigration und Anbieterlandschaft.

## Bereits im Produkt abgesichert

- Mandanten- und Hotelzuordnung wird serverseitig geprüft; öffentliche Webhooks verwenden global eindeutige, nicht erratbare Schlüssel.
- Reservierungen sperren den Hotelbestand beim Schreiben. Dadurch können parallele Buchungen das letzte Zimmer nicht doppelt verkaufen.
- Öffentliche Buchungen sind idempotent, rate-limitiert und zunächst nur zeitlich begrenzte Vormerkungen. Die E-Mail-Adresse muss bestätigt werden.
- Verfügbarkeiten werden mit gebündelten Datenbankzugriffen berechnet. Öffentliche Aufenthalte sind standardmäßig auf 30 Nächte begrenzt.
- Kartenreferenzen sind einmalig, Zahlungen und Rückerstattungen werden pessimistisch gesperrt, Übererstattungen werden verhindert.
- Eingehende Webhooks brauchen Zeitstempel, Delivery-ID und HMAC-Signatur; Replay-IDs werden gespeichert.
- Audit-Ereignisse bilden eine HMAC-signierte Sequenzkette. Dokumentfingerprints werden ebenfalls mit einem separaten HMAC-Schlüssel gebildet.
- Das Live-Profil verlangt verifiziertes MySQL-TLS, starke Geheimnisse, echte Zahlungsanbieter und deaktivierte Demo-/Simulationsfunktionen.
- Management- und Prometheus-Endpunkte laufen auf Port 8082 und werden nicht über den öffentlichen Gateway veröffentlicht.
- Backups, Restore-Drills, Outbox-Zustellung, Alertmanager und Grafana sind Bestandteil der Betriebsumgebung.

## Verbindliche Go-live-Gates pro Hotel

1. `ops/preflight.ps1` läuft mit der echten, nicht eingecheckten Produktionsumgebung ohne Fehler.
2. Fresh- und Legacy-Migrationen laufen gegen dieselbe MySQL-Hauptversion wie Produktion.
3. Ein vollständiger Restore-Drill erfüllt die vereinbarten RPO-/RTO-Werte und der Nachweis ist jünger als 30 Tage.
4. Der Lasttest wird mit einer anonymisierten, produktionsähnlichen Zimmer-, Raten- und Reservierungsmenge ausgeführt. P95 bleibt unter 1 Sekunde, Fehlerquote unter 1 Prozent und es entsteht kein Overselling.
5. OTA-, Payment-, Schließsystem-, POS-, E-Mail- und Finanzschnittstellen bestehen ihre Anbieter-Zertifizierung und Sandbox-UAT.
6. Datenschutz, Aufbewahrung, Fiskalisierung, Kurtaxe, Meldewesen und Zahlungsbedingungen werden für Land/Kanton/Gemeinde juristisch abgenommen.
7. Monitoring, Bereitschaftsdienst, Eskalationswege und ein Rollback-Fenster sind für den Einführungstag besetzt.

## Lasttest

Nur gegen eine isolierte Lasttestumgebung ausführen. Schreiblast erzeugt echte Testreservierungen.

```powershell
$env:LOAD_BASE_URL='https://pms-load.example'
$env:LOAD_PROPERTY_CODE='load-hotel'
$env:LOAD_ARRIVAL='2026-10-12'
$env:LOAD_DEPARTURE='2026-10-14'
k6 run ops/load/pms-booking.js
```

Für Buchungskonkurrenz zusätzlich `LOAD_RATE_PLAN_ID` setzen und `LOAD_ALLOW_WRITES=true` verwenden. Danach Reservierungsanzahl, verfügbaren Bestand, Datenbank-Locks, Hikari-Auslastung, P95/P99 und Outbox-Rückstand prüfen.

Die Standardlast steigt bis 75 Anfragen pro Sekunde und dauert fünf Minuten.
Für kurze Pipeline-Smoke-Tests können `LOAD_START_RATE`, `LOAD_PEAK_RATE`,
`LOAD_RAMP_DURATION`, `LOAD_PEAK_DURATION` und `LOAD_COOLDOWN_DURATION`
überschrieben werden. HTTP 429 gilt bewusst als Fehler: Die isolierte
Lasttestumgebung muss ein zur Zielkapazität passendes Rate-Limit besitzen,
damit Drosselung keine langsame oder überlastete Anwendung verdeckt.

## Kapazitätsstartwert

Das Live-Profil startet mit 300 Tomcat-Threads, 40 Datenbankverbindungen und 8.192 maximalen Verbindungen. Das sind sichere Ausgangswerte, keine universelle Kapazitätsgarantie. Sie müssen anhand des Lasttests, der MySQL-Latenz und der Anzahl gleichzeitig aktiver Hotels angepasst werden.

## Architekturgrenze

Die Eigenschaftssperre priorisiert korrekten Bestand gegenüber maximalem Buchungsdurchsatz. Bei sehr hoher Buchungsrate für ein einzelnes Hotel kann sie zum Engpass werden. Vor einem Betrieb mit mehreren hundert Buchungen pro Sekunde pro Hotel ist eine verteilte Bestands-/Kontingentarchitektur mit dediziertem Lasttest erforderlich.
