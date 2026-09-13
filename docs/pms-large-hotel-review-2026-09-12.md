# Prüfung: Eignung von Chrono PMS für große Hotels und Hotelketten

> Historische Bestandsaufnahme vor der anschließenden Umsetzung. Die Änderungen zu F01–F09, ergänzten Module und neuen Prüfnachweise stehen im [Enterprise-Umsetzungsbericht](pms-enterprise-implementation.md). Die ursprünglichen Befunde und das damalige Urteil bleiben unten unverändert dokumentiert.

Stand: 12. September 2026. Geprüft wurde der aktuelle lokale Arbeitsstand einschließlich der noch nicht veröffentlichten Tab- und Sitzungsänderungen. Diese Prüfung bewertet Implementierungen, Datenmodelle, Oberflächen, vorhandene Tests und Betriebsunterlagen. Es wurden keine Produktivdaten verändert und keine neuen Live-, Last- oder Anbieterabnahmen durchgeführt.

## Gesamturteil

**Nein, der derzeitige Stand enthält noch nicht alles für den verlässlichen Betrieb eines großen Hotels oder einer internationalen Hotelkette.** Viele Grundabläufe sind tatsächlich implementiert und getestet. Es bestehen jedoch konkrete Abrechnungsfehler, unvollständige Großhotelprozesse und offene Nachweise für den Betrieb. Eine vorhandene Oberfläche oder Schnittstellenkonfiguration ist dabei nicht gleichbedeutend mit einem vollständig nutzbaren Ablauf.

Maßstab ist ein Hotel mit mehreren Rezeptionsarbeitsplätzen, Schichten, Gruppen- und Firmenkunden sowie mehreren Abteilungen; zusätzlich wird der zuvor gewünschte internationale Kettenbetrieb berücksichtigt. Spa, Bankett, Restaurant und Loyalitätsprogramme sind abhängig vom Hotelkonzept zu ergänzen oder anzubinden.

## Was bereits vorhanden ist

| Bereich | Nachgewiesener Umfang |
| --- | --- |
| Rezeption | Reservierungen, Optionen/Warteliste, Verfügbarkeitsprüfung, atomarer Walk-in mit Gästeanmeldung, Check-in/out, Storno und No-show; Schutz gegen doppelte Buchungsanfragen. |
| Zimmerplan | Zimmer-/Tagesmatrix, Ausstattungsfilter, Farben, Sperren, seitenweises Laden; einfache Zimmerzuweisung und Wechsel. |
| Gäste/Firmen | Profile, mehrere E-Mails, Ernährungshinweise, Firmenkontakte, Verknüpfungen, Rechnungsprofile, Dokumente, Zusammenführung und Datenschutzfunktionen. |
| Raten | Netto/Brutto, Steuer- und Frühstücksaufteilung, Belegungszuschläge, Firmenbindung, Verkaufs-/Aufenthaltszeiträume und Tagesrestriktionen. |
| Finanzen | Gastkonten, manuelle Kontenaufteilung innerhalb einer Reservierung, Zahlungen, Rückerstattungen, Rechnungssnapshots/PDF, Gutschriftdokumente und einfache Kassenschichten. |
| Betrieb | Housekeeping-Status, Inspektion, Wartungsaufträge, Zimmerblockaden, Gruppenanlage und Ressourcenbuchungen. |
| Vertrieb | Öffentliche Buchung mit E-Mail-Bestätigung, Kommunikationsabläufe, signierte Integrationswarteschlange und Kanal-Webhooks; konkrete Stripe-Prüfung bereits eingezogener Zahlungen. |
| Kette/Betriebstechnik | Mehrere Hotels pro Mandant, Portfolioanzeige, Masterberechtigungen, Auditprotokoll, Monitoring, Backup-/Restore-Werkzeuge, Lasttestskript und Einführungs-/Notfallunterlagen. |

## Vor einem Großhotel-Produktivbetrieb zu beheben

### F01 – Derselbe Leistungsbestand kann mehrfach fakturiert werden

Die Rechnungserstellung liest jedes Mal sämtliche Positionen des Gastkontos und vergibt eine neue Rechnungsnummer. Eine Zuordnung bereits fakturierter Positionen bzw. eine Sperre gegen erneute Ausstellung desselben Bestands fehlt im geprüften Ablauf. Zwei Aufrufe können so zwei ausgestellte Rechnungen für dieselben Leistungen erzeugen. Die Nummerneindeutigkeit verhindert das nicht.

Beleg: [Rechnungserstellung](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsAdvancedService.java:420), [Oberfläche](C:/Users/siefe/WebstormProjects/Chrono/Chrono-frontend/src/pages/Pms/PmsAdvancedWorkspace.jsx:769).

Abnahme: wiederholte oder parallele Ausstellung derselben Positionen erzeugt keine zweite Forderung; Nachdruck, neue Leistungen und Korrektur sind getrennte Abläufe.

### F02 – Aufgeteilte Leistungen können bei Aufenthaltsänderungen doppelt entstehen

Manuelles Verschieben einer erzeugten Übernachtungsposition in ein zweites Gastkonto ist erlaubt. Eine spätere Reservierungsänderung entfernt automatisch erzeugte Leistungen jedoch nur aus dem Hauptkonto und erzeugt den gesamten Aufenthalt dort neu. Die bereits verschobene Position bleibt im zweiten Konto bestehen.

Beleg: [Positionsverschiebung](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsAdvancedService.java:391), [Neuberechnung](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsOperationsService.java:1665).

Abnahme: Übernachtung einer Firma zuordnen, Aufenthalt verlängern/verkürzen und Preise ändern; jede Leistung darf danach genau einmal mit korrektem Empfänger bestehen.

### F03 – Steuerinformation geht im Finanzexport verloren

Der CSV-Export übergibt für Gastkonto-Leistungen fest den Steuersatz null, obwohl die Position einen Steuersnapshot besitzt. Direkte POS-Positionen verwenden dagegen ihren gespeicherten Satz. Eine steuerpflichtige Übernachtung kann dadurch mit `taxRate=0` ausgegeben werden.

Beleg: [Export](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsExtensionsService.java:553).

Außerdem erzeugt die Rechnungskorrektur ein negatives Dokument, aber keine entsprechende Leistungsgegenbuchung im Gastkonto. Der Export basiert auf Leistungen und Zahlungen, nicht auf Rechnungsdokumenten. Dokumentkorrektur, Leistungskorrektur und Geldrückzahlung müssen deshalb als durchgängiger, abgestimmter Ablauf definiert werden; eine Rückzahlung allein korrigiert keinen Umsatz. [Korrekturablauf](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsAdvancedService.java:508).

### F04 – Tagesabschluss schützt abgeschlossene Geschäftstage nicht vollständig

Der vorhandene Night Audit speichert Kennzahlen und kann No-shows verarbeiten. Es fehlt ein fortgeschriebener, gegen normale Änderungen gesperrter Betriebstag. Manuelle Leistungsdaten und die vollständige Neuerzeugung von Zimmerleistungen sind nicht an einen abgeschlossenen Zeitraum gebunden.

Beleg: [Night Audit](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsAdvancedService.java:590), [Leistungsbuchung](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsOperationsService.java:916).

### F05 – Kassen- und Firmenabrechnung reichen für mehrere Abteilungen nicht aus

Pro Hotel darf nur eine Kassenschicht gleichzeitig geöffnet sein. Der Bargeldabgleich summiert Gastkonto-Zahlungen; direkte POS-Abschlüsse sind nicht derselben Kassenschicht zugeordnet. Für mehrere Rezeptionisten, Restaurantkassen und Schichtübergaben braucht es getrennte Kassen und eine vollständige Abstimmung.

Zudem verlangt Check-out ausgeglichene Gastkonten. Ein echter Firmen-Debitorenprozess – Gast reist ab, freigegebene Firma zahlt später, Forderung wird überwacht – ist dadurch nicht vollständig abgebildet. Zahlungsziel und Firmenadresse ersetzen weder Kreditlimit noch Offene-Posten-Verwaltung und Mahnwesen. Das manuelle Leistungsverschieben ist auf dieselbe Reservierung beschränkt; ein Gruppen-Masterkonto fehlt.

Beleg: [Kassenschicht](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsOperationsService.java:1092), [Check-out](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsOperationsService.java:655), [POS](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsExtensionsService.java:210).

### F06 – Zimmerwechsel und Mitreisende benötigen vollständigere Datenmodelle

Der Zimmerwechsel ersetzt das Zimmer für den gesamten Aufenthalt und erlaubt im Wechselablauf nur denselben Zimmertyp. Beispiel: Ein Wechsel an Tag drei kann scheitern, weil das Zielzimmer an Tag eins noch belegt war. Benötigt werden zeitbezogene Zimmerabschnitte einschließlich geplanter Wechsel und Up-/Downgrades.

Eine Reservierung verweist außerdem auf ein Hauptgastprofil; zusätzliche Erwachsene sind nur als Anzahl erfasst. Mehrere namentliche Mitreisende, eigene An-/Abreisedaten und getrennte Anmeldungen sind damit nicht vollständig abgedeckt.

Beleg: [Zimmerwechsel](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsOperationsService.java:856), [Reservierung](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/entities/pms/Reservation.java:38), [Anmeldung](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/entities/pms/GuestRegistration.java:14).

### F07 – Hotelrechte und Aktualisierung zwischen Arbeitsplätzen fehlen in der nötigen Tiefe

Die Prüfung trennt Mandanten und kontrolliert PMS-Seitenrechte. Es gibt jedoch keine ausgewertete Hotelzuweisung je Mitarbeiter innerhalb einer Kette und keine ausreichende Trennung etwa zwischen Housekeeping, Kasse, Preisänderung und Erstattung. Das ist eine Lücke innerhalb eines berechtigten Mandanten, kein Nachweis eines Zugriffs auf fremde Mandanten.

Änderungen werden über eigene Aktionen, lokale Browsernachrichten und Fensterfokus nachgeladen. Eine durchgehende Aktualisierung eines dauerhaft offenen Rezeptions-PCs nach Änderungen auf einem anderen PC ist im PMS-Pfad nicht implementiert. Der serverseitige Bestandsschutz bleibt davon unabhängig vorhanden.

Beleg: [Berechtigungsprüfung](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/controller/pms/PmsOperationsController.java:456), [Aktualisierung](C:/Users/siefe/WebstormProjects/Chrono/Chrono-frontend/src/pages/Pms/PmsDashboard.jsx:426), [Browserkanal](C:/Users/siefe/WebstormProjects/Chrono/Chrono-frontend/src/utils/dataRefresh.js:273).

### F08 – Wiederherstellungsprüfung passt nicht zur aktuellen Datenbankversion

Der Restore-Test erwartet ausdrücklich Flyway-Version 17, während die vorhandenen Migrationen bis Version 26 reichen. CI und Compose übernehmen ebenfalls 17. Damit weist diese Konfiguration eine korrekt auf den aktuellen Stand migrierte Datenbank zurück. Die Versionsprüfung muss zum jeweiligen Release passen und anschließend erfolgreich durchlaufen.

Beleg: [Restore-Prüfung](C:/Users/siefe/WebstormProjects/Chrono/ops/backup/restore-drill.sh:71), [CI](C:/Users/siefe/WebstormProjects/Chrono/.github/workflows/ci.yml:148), [aktuelle Migration](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/resources/db/migration/V26__pms_property_invoice_settings.sql:1).

Zusätzlich sind externer Restore-Drill und der vom PMS erwartete Nachweis `restore-verification.txt` noch nicht durchgängig verbunden. Erfolgreiche Wiederherstellung muss als maschinenlesbarer, aktueller Nachweis im Monitoring ankommen.

### F09 – Gleich hohe Teilrückerstattungen können vom tatsächlichen Geldfluss abweichen

Der Stripe-Schlüssel zur Wiederholungssicherheit besteht nur aus Originalzahlungs-ID und Erstattungsbetrag. Zwei eigenständige Teilrückerstattungen über jeweils 25 EUR auf dieselbe Zahlung erhalten dadurch denselben Schlüssel. Stripe liefert bei identischen Parametern innerhalb der Aufbewahrungsdauer das gespeicherte Ergebnis der ersten Anfrage zurück; Chrono legt nach jedem erfolgreichen Aufruf hingegen eine neue lokale Erstattung mit Status `POSTED` an. So können lokal 50 EUR Erstattung stehen, obwohl beim Anbieter nur 25 EUR erstattet wurden. Bei abweichenden Parametern kann Stripe die zweite Anfrage stattdessen zurückweisen.

Der zurückgegebene Provider-Erstattungsdatensatz einschließlich ID und Status wird nicht gespeichert oder ausgewertet. Erforderlich sind eine eindeutige ID pro beabsichtigtem Erstattungsvorgang, stabile Wiederholungen dieses Vorgangs und ein Abgleich mit dem tatsächlichen Providerstatus. Dieser Befund wurde statisch im Code nachvollzogen, nicht mit Live-Zahlungen reproduziert.

Beleg: [Erstattungsaufruf und lokale Buchung](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsOperationsService.java:1026), [Stripe-Aufruf](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/StripePmsPaymentGateway.java:80), [Stripe: Wiederholungssicherheit](https://docs.stripe.com/api/idempotent_requests).

Zusätzlich verlangt die Erstattung ein offenes Gastkonto. Eine Reklamation nach abgeschlossenem Check-out benötigt daher einen ergänzenden, nachvollziehbaren Korrektur- und Erstattungsablauf. [Kontoprüfung](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsOperationsService.java:1002).

## Weitere funktionale Ausbaustufen

| Bereich | Verbleibende Arbeit |
| --- | --- |
| Gruppen/Kontingente | Anlage umfasst maximal 100 benannte Zimmerbuchungen mit gemeinsamen Reisedaten. Es fehlen Kontingente ohne bereits benannte Gäste, Abruf/Pickup, Freigabefristen, abweichende Reisedaten und vollständige gemeinsame Bearbeitung/Abrechnung. [Gruppenvertrag](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/dto/pms/CreateGroupBookingRequest.java:18). |
| Housekeeping | Ein Auftrag pro Zimmer/Tag kann eine weitere Aufgabenart überschreiben. Reinigung, Inspektion und Abendservice brauchen getrennte Aufgaben, verlässliche Zuweisung und Kontrollhistorie. [Modell](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/entities/pms/HousekeepingTask.java:14). |
| Zahlungen/Raten | Stripe prüft bereits eingezogene Zahlungen und unterstützt Rückerstattungen. Integrierte Kartengarantie/Vorautorisierung, Terminal-/Checkout-Ablauf und automatische Anzahlungs-, Storno- und No-show-Regeln fehlen als vollständige Abläufe. Zentral gepflegte Raten über mehrere Hotels sind ebenfalls weiter auszubauen. |
| Managementberichte | Umsatz/ADR/RevPAR verteilen derzeit den gebuchten Gesamtbetrag gleichmäßig auf Nächte. Darin können Steuer und Frühstück enthalten sein. Das ist keine Auswertung tatsächlich gebuchter Netto-Zimmerumsätze. Forecast, Pickup/Pace, Budgetvergleich und Debitorenberichte sind weiter auszubauen. [Berechnung](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsReportingService.java:217). |
| Veranstaltungen | Ressourcen, Kapazitäten und Terminkonflikte sind vorhanden. Für ein Tagungs-/Banketthotel fehlen vollständige Veranstaltungsangebote, Leistungspositionen, Ablauf-/Bankettaufträge und deren Abrechnung. [Ressourcenbuchung](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsAdvancedService.java:178). |
| Internationalität | Länderabhängige Fiskalisierung/E-Rechnung, Abgaben und Meldesysteme benötigen konkrete Länderanbindungen. Geldbeträge sind auf zwei Nachkommastellen ausgelegt; Schriftabdeckung und Adressvorlagen sind noch kein vollständiger weltweiter Katalog. [Bestehende Grenzen](C:/Users/siefe/WebstormProjects/Chrono/docs/pms-international-expansion.md). |
| Anbieter | Die signierte Integrationswarteschlange ist real. Konkrete, abgenommene Anbindungen für OTA/Channel Manager, Schließsystem, POS und Finanzsystem bleiben zusätzlich erforderlich. [Anbietergrenze](C:/Users/siefe/WebstormProjects/Chrono/docs/operations/PROVIDER_INTEGRATIONS.md). |
| Datenmenge/Betrieb | Außerhalb der Zimmermatrix laden Operations/Advanced umfangreiche Listen und Historien. Hotelweite Schreibsperren schützen den Bestand, begrenzen aber Parallelität. Gemischte Lasttests für Rezeption, Housekeeping, OTA und Reporting sowie vereinbarte Verfügbarkeits-/Wiederherstellungsziele fehlen als nachgewiesene Abnahme. [Operations](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsOperationsService.java:127), [Historien](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsAdvancedService.java:1187). |

Ein Hotel muss nicht jedes Zusatzprodukt selbst im PMS implementieren. Restaurant, Spa, Loyalität, Revenue Management und Vertrieb können über geeignete Integrationen abgedeckt werden. Diese müssen jedoch für die tatsächlich eingesetzten Anbieter funktionieren und abgestimmt sein.

## Vergleichsgrundlage und nächste Reihenfolge

Die Herstellerquellen wurden als Funktionsmaßstab genutzt, nicht als universelle Pflichtliste: [Oracle: Leistungsrouting und Firmen-Debitoren](https://docs.oracle.com/en/industries/hospitality/opera-cloud/25.2/ocsuh/c_managing_reservations_routing_instructions.htm), [Oracle: Kassenschichtabschluss](https://docs.oracle.com/en/industries/hospitality/opera-cloud/25.4/ocsuh/t_cashiers_closure.htm), [Oracle: Gruppen/Kontingente](https://docs.oracle.com/en/industries/hospitality/opera-cloud/24.3/ocsuh/t_managing_blocks.htm), [Mews: Hotelzuweisungen für Mitarbeiter](https://help.mews.com/s/article/Set-up-and-customize-users-in-Mews-Multi-Property), [Mews: zentrale Raten](https://help.mews.com/s/article/How-to-create-rate-groups-in-Mews-Multi-Property?language=en_US).

Empfohlene Reihenfolge: zuerst F01–F04, F09 und die Kassenabstimmung korrigieren; anschließend Zimmerabschnitte, Mitreisende, Hotelrechte, Geräteaktualisierung und Gruppen-/Firmenabrechnung vervollständigen. Parallel den Restore-Nachweis reparieren und die ersten konkreten Länder/Anbieter festlegen. Danach einen vollständigen Betriebstag mit parallelen Arbeitsplätzen, Gruppenanreise, Zimmerwechsel, Rechnungskorrektur, Tagesabschluss und Wiederherstellung abnehmen.

Bereits existierende [Enterprise-Abnahmekriterien](C:/Users/siefe/WebstormProjects/Chrono/docs/operations/ENTERPRISE_READINESS.md) und [Einführungsprüfungen](C:/Users/siefe/WebstormProjects/Chrono/docs/operations/UAT_CHECKLIST.md) können dafür verwendet werden. Vorhandene grüne Softwaretests belegen ihre getesteten Szenarien; sie ersetzen diese fachliche und betriebliche Abnahme nicht. Die hier gefundenen Lücken wurden in dieser Prüfung dokumentiert, noch nicht behoben.
