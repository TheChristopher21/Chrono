# Revenue, Veranstaltungsangebote, Ratenvererbung und internationale Konfiguration

Stand: 12. September 2026. Diese Ergänzung dokumentiert die neuen internen Arbeitsabläufe; sie bestätigt weder einen Produktivrollout noch eine nationale Fiskal- oder Peppol-Zertifizierung.

## Revenue und Preisregeln — V41

`PmsRevenueScheduler` arbeitet standardmäßig alle fünf Minuten hotelweise in separaten Transaktionen. Das Hotel wird beim Schreiben gesperrt. Ein Vergleichsstand ist durch den eindeutigen Schlüssel Hotel/Betriebstag geschützt; mehrere Instanzen oder erneute Aufrufe erzeugen keinen zweiten Tagesstand. Rückwirkende Vergleichsstände werden nicht erfunden. Die Einstellung `snapshotsEnabled` kann die automatische Erfassung je Hotel ausschalten. Der technische Scheduler ist über `app.pms.revenue.scheduler-enabled` abschaltbar.

Die Prognose ist ein offengelegtes Vergleichstagsmodell. Sie verwendet tatsächlich abgeschlossene Night-Audit-Tage desselben Wochentags aus den letzten 84 Tagen sowie Tage der Vorjahressaison im Abstand von höchstens 35 Tagen zum Vorjahresdatum. Bei genügend gespeicherten Vergleichsständen fließt der tatsächlich beobachtete Pickup für denselben Wochentag und dieselbe Vorlaufzeit ein. Ohne die konfigurierte Mindestzahl von Vergleichstagen bleiben Prognose und Erlösschätzung unbekannt. Bereits bestätigte Buchungen werden nicht durch eine kleinere Schätzung ersetzt. Die Anzeige nennt historische, saisonale und Pickup-Stichproben einzeln.

Die angezeigte Spanne entspricht beobachteten Vergleichstagswerten; sie ist **kein kalibriertes statistisches Konfidenzintervall**. Kapazitätsbegrenzung verwendet die aktuell aktiven, betriebsbereiten Zimmer. Dies ist kein vollständiges Revenue-Management-System mit Wettbewerberpreisen, externen Veranstaltungen, historischen Kapazitätsänderungen oder einem trainierten Nachfrage-/Stornomodell. Erlösschätzungen verwenden bekannte Nettoleistungen; unbestimmte Steuersätze werden nicht als null Prozent interpretiert.

Preisregeln kombinieren die hotelweite Belegungsprognose mit Unter-/Obergrenzen, Belegungsschwellen und einem Zu-/Abschlag auf die Basisrate. Das Änderungslimit je Lauf und der maximale Zu-/Abschlag sind auf 25 Prozent begrenzt. Gibt es keinen Preis, der beide Grenzen erfüllt, bleibt der Preis stehen. Regeln erzeugen zunächst Vorschläge. Die Preisautomatik ist standardmäßig aus und erfordert eine bewusste Masterkonfiguration. Sie läuft höchstens einmal je offenem Betriebstag; manuelle Tagespreise werden übersprungen. Eine manuelle Bearbeitung entfernt die Kennzeichnung `revenueManaged`. Bestehende Reservierungs- und Preisakzeptanz-Snapshots werden nicht verändert.

## Veranstaltungsangebote — V42

Ein gespeicherter Veranstaltungsauftrag wird zusammen mit Angebotsbedingungen, Gültigkeit, Ressource und Preisen als neue unveränderliche Version gespeichert. Eine neue Version ersetzt die noch offene vorige Version. Ein Kundenlink besteht aus 32 zufälligen Bytes; gespeichert wird ausschließlich sein Hash. Ein neu erzeugter Link ersetzt den bisherigen. Der Link wird angezeigt und **nicht automatisch versendet**.

Der Kunde sieht die konkrete Version mit Leistungen, Beträgen, Aufbau/Ablauf und Bedingungen. Annahme verlangt Name und ausdrückliche Zustimmung. Annahme oder Ablehnung werden unter Hotelsperre genau einmal gespeichert. Inhalt, Entscheidung, Name und Zeitpunkt ergeben einen Prüfwert; das vorhandene PMS-Audit protokolliert den Vorgang zusätzlich. Das ist ein nachvollziehbarer Annahmeprozess, keine Behauptung einer qualifizierten elektronischen Signatur oder externen Identitätsprüfung.

Eine angenommene Version kann in den BEO übernommen werden. Änderungen an Zeit, Ressource oder Währung erfordern ein neues Angebot. Vor dem Verbuchen prüft der BEO-Abrechnungspfad den angenommenen Snapshot; abweichende Leistungen können nicht unter einem alten angenommenen Angebot fakturiert werden. Bereits verbuchte BEOs bleiben unveränderlich.

## Ratenvererbung — V43

Eine lokale Zielrate kann genau eine Elternrate besitzen. Die Regeln sind mandantenintern; Kreise und Hierarchien über 20 Ebenen werden abgewiesen. Der Master legt Basisbeziehung, prozentuale Anpassung, Preisgrenzen und die optionale Übernahme von Aufenthalts-/Zahlungs-/Stornobedingungen fest. Lokale Steuersätze und die Zielkategorie bleiben erhalten; unterschiedliche Netto-/Brutto-Preisbasen werden nicht unbemerkt vermischt.

Bei verschiedenen Währungen ist ein ausdrücklich erfasster positiver Kurs mit Kursdatum, begrenzter Gültigkeit und Quellen-/Freigabereferenz erforderlich. Chrono ruft dafür keine behaupteten Livekurse ab. Bei gleicher Währung muss der Kurs 1 sein. Abgelaufene Fremdwährungskurse blockieren weitere Übernahmen. Manuelle Änderungen an einer Zielrate frieren ihre Vererbung als lokale Ausnahme ein; Mitarbeiter mit lokalem Ratenbearbeitungsrecht können diese Ausnahme verwalten. Neue Ratenbeziehungen und explizite zentrale Anwendung erfordern einen Master.

Automatische Übernahme ist je Regel einschaltbar. Der Scheduler prüft standardmäßig alle fünf Minuten aktive, nicht eingefrorene Beziehungen und übernimmt nur veränderte Elternwerte. Hierarchische Änderungen werden bei aufeinanderfolgenden Prüfungen weitergereicht; es wird keine kettenweite, zeitgleiche Livepublikation an externe Buchungskanäle behauptet. Konfiguration: `app.pms.rate-inheritance.scheduler-enabled` und `app.pms.rate-inheritance.scheduler-delay-ms`.

## Internationale Pflichtfelder und strukturierte Rechnung — V46

Master konfigurieren je Hotel einen Regelcode, Rechtsraum, fachliche Prüfquelle und die tatsächlich benötigten Gästeanmeldungsfelder. Neue Einladungen speichern Pflichtfelder und Regelversion. Spätere Konfigurationsänderungen verändern bereits versendete Einladungen nicht; eine ausdrücklich neu ausgegebene Einladung verwendet den neuen Stand. Ohne Konfiguration bleiben die vorhandenen Pflichtfelder erhalten. Auch optionale, tatsächlich ausgefüllte Länder-/Ausweisangaben werden validiert. Diese Feldkonfiguration ersetzt keine landesspezifische Meldeschnittstelle oder die fachliche Pflege nationaler Ausnahmen.

Der strukturierte Export erzeugt **OASIS UBL 2.1 Invoice und CreditNote** aus den unveränderlichen Rechnungssnapshots. Er nutzt keine aktuellen Gäste- oder Hoteladressen als Ersatz für fehlende historische Angaben. Dokumentnummer, Datum, Parteien, Positionsdaten, Steuersätze und Summen werden geprüft. Null-Prozent-Positionen verlangen eine explizite Steuerkategorie; Steuerbefreiung/Reverse Charge benötigen einen Grund. Die konkret dieser Rechnung zugeordneten Vorauszahlungen werden ausdrücklich erfasst, da Gastkonto-Zahlungen nicht automatisch einer einzelnen Rechnung gleichgesetzt werden dürfen. `PrepaidAmount` und `PayableAmount` bilden diese Zuordnung ab.

Vor dem dauerhaften Speichern wird XML gegen die **unveränderten offiziellen OASIS-XSDs** validiert. Alle 15 benötigten Schemata liegen im Release; die Validierung lädt weder externe Schemas noch beliebige DTDs. Nach erfolgreicher Prüfung werden XML, SHA-256 und Erstellungsdaten einmalig gespeichert. Wiederholung und Download liefern denselben Inhalt. Ein ungültiger Altdatenbestand wird mit einem konkreten Fehler abgewiesen und nicht still repariert.

Der Export verwendet absichtlich keine Peppol- oder Landes-Konformitätskennung, für die nicht sämtliche Zusatzregeln geprüft wurden. UBL-Syntaxprüfung ist nicht gleichbedeutend mit Peppol BIS, EN16931, XRechnung, nationaler Freigabe, Netztransport oder einem signierten Fiskalbeleg. Grundlagen: [OASIS UBL 2.1](https://docs.oasis-open.org/ubl/os-UBL-2.1/UBL-2.1.html), [Peppol BIS Billing: UBL-Syntax und zusätzliche Geschäftsregeln](https://docs.peppol.eu/poacc/billing/3.0/syntax/ubl-invoice/). Originalhinweise und Lizenzen der mitgelieferten Schemata bleiben erhalten.

## Gezielte Regressionstests

Die Mitarbeiterrechte laden eine durchsuchbare Serverseite mit standardmäßig 50, maximal 100 Konten. Die Datenbank begrenzt zuerst die geordneten IDs; Rollen und Hotelzuweisungen werden anschließend gesammelt geladen. Ein größerer Mitarbeiterbestand führt deshalb nicht zu einer eigenen Berechtigungsabfrage für jedes Konto. Suchzeichen `%` und `_` werden als normale Zeichen behandelt. Seiten- und Benutzerwechsel sind bei einem ungespeicherten Rechteentwurf gesperrt; Speichern oder bewusstes Verwerfen löst diese Sperre.

- `PmsDemandForecastTest`: fehlende Historie, keine Zukunftsdaten im Training, nachvollziehbare Spanne/Pickup, Preisgrenzen und nicht aufeinander aufbauende Basisanpassung.
- `PmsCommercialExpansionTest`: Tagesstand-Idempotenz, Schutz manueller Preise und einmaliger Automatiklauf, lokale Steuern/Freeze, Vererbungskreise und FX-Prüfung, unveränderliche Angebote, verbrauchter Entscheidungsvorgang und BEO-Vertragsabweichung.
- `PmsInternationalSettingsServiceTest`: unveränderliche Pflichtfeldversionen und weiterhin geprüfte optionale Eingaben.
- `PmsStructuredInvoiceServiceTest`: echte UBL-Schema-Prüfung für Rechnung/Gutschrift, escaping, fehlende Alt-Parteien, Nullsteuergründe, Betragsabweichungen und externe DTD-Abweisung.
- `PmsAccessAdministrationTest`: begrenzte, stabile Seiten; Mandanten- und Löschfilter; literale Suche; gleichbleibende Zahl der SQL-Abfragen bei wachsender Seitengröße. `PmsAccessWorkspace.test.jsx`: serverseitige Suche/Seiten, erhaltene Rechteentwürfe und bestätigter Speicherstand.
- Frontend: Angebotszustimmung, ausdrückliche Linkerstellung, Versionierung lokaler Ausnahmen, erhaltene Entwürfe bei Konflikten, verpflichtende Vorauszahlungszuordnung und öffentliche Anmeldung.

Die abschließenden Laufprotokolle und die vollständige integrierte Testzahl stehen im übergeordneten Umsetzungsbericht. Teiltests allein ersetzen keine fachliche Abnahme einer gesamten Hotelinstallation.
