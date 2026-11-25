# Lokaler API-Abnahmelauf: Rechnung, UBL und Buchhaltung

12. September 2026 · Backend `http://127.0.0.1:18086` · MySQL 8.4 · isolierte Datenbank `chrono_migration_fresh_20260912_v48`. Ausschließlich synthetische Daten, keine Zugangsdaten in diesem Bericht.

**Ergebnis: Der geprüfte Ablauf ist erfolgreich.** Eigenes Testhotel 2, Firma 2, Gast 509, Aufenthalt/Gastkonto 510, Rechnung 2 (`QABILL-2026-00001`). Rechnungsbetrag CHF 216.20 = CHF 200.00 netto + CHF 16.20 Steuer.

| Prüfung | Ergebnis |
| --- | --- |
| Firma → Gast → Reservierung → Gastkonto → Rechnung über echte API | Erfolgreich |
| Französischsprachiger Rechnungsstand | `Facture` und `Échéance` im ausgegebenen PDF bestätigt |
| Gast nach Rechnungsstellung auf Englisch und anderen Empfängernamen geändert | PDF weiterhin bytegleich und ursprünglicher Empfänger erhalten |
| Wiederholter PDF-Download | Identische 1.720 Bytes, SHA-256 `33465210a7ad45725a1a3ffbba88e563137ee8d9f307f1a0931b243a79ee580f` |
| UBL mit ausdrücklich zugeordneter Vorauszahlung 0 | Gespeichert; erneute Erstellung und Download identisch |
| Unabhängige XML-Prüfung | `lxml.XMLSchema` gegen unveränderte offizielle OASIS-UBL-2.1-XSDs, ohne Netzwerkzugriff |
| Firmenforderung und synthetischer Bankimport | Exakte Rechnungsreferenz vorgeschlagen; Vorschau verändert den offenen Betrag nicht |
| Bankabgleich, erneute Bestätigung und doppelte Dateieinfuhr | Genau ein Ausgleich, Restforderung 0, derselbe Import wiederverwendet |
| Persistierter Buchhaltungsexport und lokales Ack | Exportlauf 1, 535 Bytes, vier ausgeglichene Buchungszeilen; wiederholter Abruf unverändert |
| PDF-Sichtprüfung | Eine gerenderte Seite geprüft; keine überlappenden oder abgeschnittenen Inhalte beobachtet |

Maschinenlesbare Ergebnisse: `pms-api-billing-qa.json`. Prüfartefakte: `pms-api-billing-invoice-first.pdf`, `pms-api-billing-invoice-repeat.pdf`, `pms-api-billing-invoice.xml`, `pms-api-billing-bank-input.csv`, `pms-api-billing-accounting-export.csv`.

Das Ack bestätigt ausschließlich diesen lokalen QA-Vorgang. Es wurde kein Versand, externer Buchhaltungsimport, Stripe-/Beds24-Aufruf oder öffentlicher Link ausgelöst. Die UBL-Syntaxprüfung bestätigt keine Peppol- oder nationale Fiskalzertifizierung. Die PDF-Prüfung betrifft dieses einseitige französische Muster; gespeicherte Leistungsbeschreibungen werden unverändert übernommen.
