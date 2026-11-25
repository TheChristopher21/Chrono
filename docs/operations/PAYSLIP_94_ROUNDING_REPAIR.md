# Abrechnung #94: Rundungskorrektur vom 21. Oktober 2026

Die angeforderte Serverprüfung ordnete den Screenshot der Abrechnung #94 und
Benutzer-ID 62 zu. Die zusätzlich genannte Mitarbeiterin (ID 64) hatte keine
entsprechende offene Abrechnung. Änderungen wurden ausschließlich an #94 und
deren beiden Lohnpositionen vorgenommen; der Beleg bleibt unfreigegeben.

## Ursache der Freigabesperre

Produktiv läuft Commit `f13d7c44fe03`. Die Serverlogs vom 20. Oktober enthalten
zweimal `Validation error: Journal entry not balanced`. Die alte Erstellung
speicherte ungerundete `double`-Beträge:

| Wert | Gespeichert vor Reparatur | Nach Reparatur |
| --- | ---: | ---: |
| Grundlohn | 18.066666666666666 | 18.07 |
| Auszahlung aus Stundensaldo | 0.7083333333333333 | 0.71 |
| Brutto | 18.775 | 18.78 |
| Abzüge | 1.30 | 1.30 |
| Netto | 17.474999999999998 | 17.48 |
| Arbeitgeberbeiträge | 1.82 | 1.82 |

`AccountingService.recordPayrollPosting` rundet mit `BigDecimal` und `HALF_UP`.
Vorher ergaben sich Soll 20.60 und Haben 20.59; jetzt sind beide Seiten 20.60.
Die Buchungsprüfung wurde nicht abgeschwächt. Ein bloßes MySQL `ROUND` auf den
alten DOUBLE-Spalten reproduziert die Java-Rundung nicht zuverlässig; das
Reparaturskript konvertiert die Positionen deshalb zuerst in DECIMAL.

## Herkunft der 17 Minuten

Die vollständigen START/ENDE-Paare von ID 62 seit Eintritt am 1. Oktober 2025
ergeben bis 20. Oktober 2026 genau 11'085 Minuten (184:45). Die genehmigten
Abrechnungen ziehen gemäß bestehender Kontologik 11'068 Minuten (184:28) ab.
Die Differenz beträgt 17 Minuten. Der offene Beleg #94 zieht noch nichts ab.

Bis 23. August waren 9'597 Minuten erfasst und in den bis zu diesem Periodenende
zugeordneten Belegen 10'122 Minuten ausgezahlt: rechnerisch -525 Minuten (-8:45).
Im Zeitraum 24. August bis 23. September kamen 542 Minuten (9:02) hinzu:
3. September 11:04–15:28 (264 Minuten), 17. September 10:53–15:31 (278 Minuten).
Damit verbleiben 17 Minuten. Die darauffolgenden 946 Minuten wurden mit #95
vollständig verrechnet und ändern diesen Rest nicht.

Dies erklärt die bestehende Stundenkonto-Rechnung. Es bestätigt weder die
fachliche Richtigkeit der historischen Auszahlungen noch einen zusätzlichen
Überstundenanspruch: ID 62 arbeitet im Stundenlohn, und die alte Anwendung
führt auch dieses Stundenkonto unter der Bezeichnung Überstunden.

## Ausführung und Nachweis

- Verwendet: `ops/maintenance/repair-payslip-94-rounding.sql`.
- Vorherige Sicherung ausschließlich des Belegs, seiner Positionen und Audits:
  `/root/chrono/data/backups/payslip-94-rounding-20261021/payslip-before-20261021T092329Z.sql.gz`.
  Gzip geprüft, alle fünf erwarteten Tabellen enthalten;
  SHA-256 `80ea5f69e5d3bf15e232f4e9e7a8a4cb41fcdb2471f266c73513beaf92440907`.
- Zuerst erfolgreich mit isolierten temporären MySQL-Tabellen getestet.
  Eine als freigegeben markierte Testkopie wurde durch den CHECK abgewiesen.
- Live in einer Transaktion mit exakten Vorbedingungen und Zeilensperren
  ausgeführt. Version 2 auf 3 erhöht; Audit `ROUNDING_CORRECTED` hinzugefügt.
- SHA-256-Vergleich vor/nach Reparatur: übrige Abrechnungen und Lohnpositionen,
  beide Mitarbeiter-Salden und deren Stempel unverändert.
- 25 gezielte Java-Tests erfolgreich, einschließlich Reproduktion der alten
  Buchungssperre und erfolgreicher Buchung der korrigierten Beträge in H2.
  Log: `output/payroll-rounding-20261021-tests.log`.
- Keine Live-Freigabe, Buchung, Auszahlung oder E-Mail ausgelöst. Kein Deployment
  und keine Änderung am laufenden Programm. Der neuere lokale Lohnrechner
  berechnet bereits gerundete Komponenten; seine umfangreicheren Änderungen
  wurden im Rahmen dieser Einzelreparatur nicht veröffentlicht.

Das Skript ist absichtlich nicht wiederholt auf denselben oder beliebige
andere Belege anwendbar: Abweichungen von der geprüften Ausgangsversion werden
abgewiesen. Eine spätere Freigabe erfolgt über den normalen Anwendungsablauf.
