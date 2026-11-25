# Lohnprüfung und Vertragsparameter – Stand 15. Oktober 2026

Fortschreibung: [Steuer- und Überstundenlogik vom 16. Oktober](payroll-readiness-2026-10-16.md). Die nachfolgende Darstellung dokumentiert den früheren Stand.

Diese Änderung ist noch keine fachliche Freigabe des gesamten Lohnrechners. Sie ist lokal umgesetzt; auf dem Produktivserver wurden weder Code noch Daten verändert. Der breitere Ausbau von Integrationen, Offline-Erfassung, Onboarding und PMS ist damit nicht abgeschlossen.

## Implementiert

- Firmen erfassen BU, NBU, KTG Arbeitnehmer/Arbeitgeber und FAK in Prozent. Persönliche BVG-Beträge werden separat in CHF pro Monat erfasst. Leere Werte bleiben unbekannt, ausdrücklich eingetragene Nullen bleiben Nullen.
- Jede Speicherung erstellt eine zusätzliche Version mit Gültigkeitsdatum, Grundlage, Ersteller-ID und Erstellungszeit in UTC. Bei gleichem Gültigkeitsdatum gilt die Version mit der höchsten ID. Es werden keine undatierten Altwerte mit einem erfundenen Gültigkeitsdatum übernommen.
- Die Schweizer Berechnung verwendet die zum Zeitraum gehörenden Vertragswerte. Fehlende Werte, Änderungen innerhalb des Zeitraums und nicht unterstützte Teilmonate werden abgewiesen. BVG wird nicht mehr aus Altersgutschriften als persönlicher Beitrag geschätzt. Die verwendeten Versions-IDs stehen im GENERATED-Audit-Eintrag.
- Bereits gespeicherte Abrechnungen werden durch Parameteränderungen nicht neu berechnet. Alte genehmigte Abrechnungen werden nicht automatisch korrigiert.
- Die neue Vorprüfung zeigt die täglichen Werte aus der Zeitübersicht, fehlende Zeiten/Stammdaten und genehmigte Überschneidungen. Die pauschale Netto-Vorschau von 86 % wurde entfernt. Die Vorprüfung weist die Steuerberechnung ausdrücklich als nicht verifiziert aus.
- Freigaben prüfen gerundete Beträge, Positionssummen, Nettorechnung, Überschneidungen und den gemeinsam angeforderten Überstundensaldo. Benutzer und Abrechnungen werden unter Datenbanksperren erneut geladen.
- Ausgewählte Sammelfreigaben und firmenweite Freigaben erfolgen innerhalb einer Transaktion. Benachrichtigungen werden erst nach erfolgreichem Commit ausgelöst. Automatische Wiederholung fehlgeschlagener Benachrichtigungen ist noch nicht implementiert.
- Die Summenübersicht trennt CHF und EUR, statt alle Beträge als CHF auszuweisen. Es findet keine implizite Währungsumrechnung statt.
- Migration V51 ergänzt eine eigene Historientabelle ohne Änderung bestehender Lohnbeträge. Das Audit-SQL in `ops/audit/payroll-readonly.sql` verwendet eine konsistente, nur lesende Transaktion.

## Fachliche Freigabe bleibt offen

`TaxCalculationService` enthält weiterhin vereinfachte CH-Steuer-/Quellensteuerregeln und deutsche Lohnsteuer-/Sozialversicherungsregeln. Die Anbindung vertraglicher Beiträge ist keine Bestätigung des gesamten Nettolohns. Vor einer Produktivfreigabe müssen insbesondere amtliche Quellensteuertarife bzw. der deutsche Programmablaufplan, Jahresparameter, Beitragsgrenzen, Beitragsgrundlagen und Sonderfälle implementiert und unabhängig geprüft werden.

Die Bruttoberechnung enthält weiterhin eine pauschale Grenze von 160 Stunden und 25 % Überstundenzuschlag. Vertragsabhängige Zuschläge, historisierte Löhne, Beschäftigungswechsel, Ein-/Austritte, Ferien-/Feiertagszuschläge, 13. Monatslohn sowie Jahressummen und Rückrechnungen sind damit nicht vollständig abgedeckt. Die Vorprüfung nutzt die kanonische Zeitübersicht; die Bruttoberechnung verwendet noch einen eigenen Stempelpaarungsweg. Diese Abweichung muss vor einer Freigabe vereinheitlicht werden.

Ein grüner Testlauf beweist die beschriebenen technischen Eigenschaften, nicht die Richtigkeit sämtlicher Löhne. Bestehende ungeprüfte Entwürfe werden durch diese Änderung nicht fachlich verifiziert. Eine produktive Auszahlung auf Grundlage der verbliebenen vereinfachten Regeln ist nicht Gegenstand dieser Freigabe.

## Nur lesender Live-Abgleich

In der geprüften Datenbank waren 24 Abrechnungen vorhanden, davon 23 genehmigt. Auffällig waren ein negativer Nettolohn, eine Abweichung der auf Cent gerundeten Nettorechnung, fünf Abweichungen zwischen Lohnpositionen und Bruttosumme sowie zwei Paare überlappender genehmigter Zeiträume. Bei Stempeln gab es zwei Gruppen mit identischem Benutzer, Zeitpunkt und Stempeltyp. Diese Befunde können sich überschneiden und sind keine Addition unabhängiger Fälle.

Die vorhandenen betrieblichen Versicherungssätze waren nicht gepflegt. Es gibt laut Nutzer keine extern geprüften Referenzabrechnungen. Keine dieser Auffälligkeiten wurde live automatisch geändert. Einzelkorrekturen brauchen eine nachvollziehbare Zuordnung zum ursprünglichen Zeitraum und zu den betroffenen Beträgen.

## Prüfungen und Belege

- Vollständige Frontend-Suite: 669 Tests erfolgreich; danach die vier betroffenen Testdateien mit 23 erfolgreichen Tests und zuletzt der zusätzliche Währungstest innerhalb der 14 erfolgreichen Abrechnungsseiten-Tests. Produktionsbuild erfolgreich.
- Vollständige Backend-Suite: 832 Tests, keine Fehler, zwei umgebungsabhängige Tests übersprungen (MySQL-Migration separat ausgeführt; externer Backup-Test nicht ausgeführt). Siehe `output/chrono-full-backend-tests-final.log`.
- Zusätzlich erfolgreicher Datenbanktest mit zwei parallelen Transaktionen: überlappende Abrechnungen können trotz vorher geladener Entitäten nicht beide freigegeben werden. Siehe `output/chrono-payroll-concurrency-test.log`.
- Migrationen unter lokalem MySQL 8.0: frisches Schema und simuliertes Legacy-Schema erfolgreich; wiederholter Migrationsaufruf ohne neue Änderungen. Siehe `output/chrono-mysql-payroll-tests.log`.
- Browser: echte lokale Anmeldung, Navigation über das Menü, Versicherungswerte speichern, persönliche BVG-Beträge speichern, erneut laden, Versionsanzeige und mobile Ansicht. Separat in einer lokalen Demo: Abrechnungsprüfung öffnen und Tageswerte aufklappen. Testdaten wurden ausschliesslich lokal erzeugt.
- Beispiele: 1,25 % BU, 0 % NBU, 0,4 % KTG AN, 0,6 % KTG AG, 2 % FAK; getrennt 240,50 CHF BVG AN und 300,75 CHF BVG AG. Die Werte bleiben nach dem Laden erhalten.
- Screenshots: `output/playwright/configuration-desktop.png`, `configuration-mobile.png`, `payroll-review.png`. Der gesamte Funktionsumfang von Chrono wurde noch nicht manuell durchgeklickt.
- Im lokalen Browser gab es bestehende 404-Antworten für einen nicht vorhandenen Changelog und beim Wechsel zur Demo vorübergehende 403-Antworten von Hintergrundanfragen. Diese Beobachtungen sind nicht als bestandene End-to-End-Prüfung dieser Funktionen gewertet.

## Amtliche Quellen für die weitere fachliche Umsetzung

- [AHV/IV: Lohnbeiträge, Stand 1. Januar 2026](https://www.ahv-iv.ch/p/2.01.d)
- [BSV: Altersvorsorge in der beruflichen Vorsorge](https://www.bsv.admin.ch/de/altersvorsorge-beruflichen-vorsorge)
- [ESTV: Quellensteuer](https://www.estv.admin.ch/de/quellensteuer)
- [BMF: XML-Programmablaufpläne](https://www.bmf-steuerrechner.de/interface/pseudocodes.xhtml)

Diese Quellen ersetzen nicht die unternehmens- und personenbezogenen Vertragsdaten. Die neue Erfassung stellt die dafür erforderlichen Eingabemöglichkeiten bereit.
