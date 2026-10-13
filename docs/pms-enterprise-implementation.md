# Chrono PMS: Umsetzung für größere Hotels und Hotelketten

> Historischer erster Umsetzungsstand bis V38. Der [abschließende Code- und Prüfbericht bis V48](pms-code-completion-2026-09-12.md) dokumentiert die danach ergänzte Zahlungs-/Versandautomatik, Live-Aktualisierung, Offline-Bearbeitung, Dokumentversionen, Freigaben und aktuellen Gesamttests. Aussagen über damals noch fehlende Funktionen unten beziehen sich auf diesen früheren Stand.

Stand: 12. September 2026. Dieser Bericht beschreibt den lokalen Implementierungs- und Prüfstand nach dem [Großhotel-Review](pms-large-hotel-review-2026-09-12.md). Die damaligen Befunde bleiben als historische Bestandsaufnahme erhalten; die folgenden Tabellen dokumentieren die anschließend vorgenommenen Änderungen. Eine Bereitstellung auf einem Produktivsystem oder eine weltweite fachliche Zulassung ist damit nicht verbunden.

Die konkreten Fehler F01–F09 wurden im Code bearbeitet und durch gezielte Prüfungen ergänzt. Hinzu kommen Arbeitsabläufe für Gruppen, Debitoren, mehrere Kassen, Housekeeping, Veranstaltungen, Zahlungsvorgänge und die Planung auf Basis gespeicherter Buchungsstände. Für eine Einführung in einer bestimmten Hotelkette bleiben die eingesetzten Länder, Anbieter, Betriebsabläufe und Lastanforderungen einzeln abzunehmen.

## Zuordnung zum Review

| Befund | Umgesetztes Verhalten | Wesentliche Prüfung und Grenze |
| --- | --- | --- |
| **F01: Doppelte Fakturierung** | Rechnungspositionen verweisen dauerhaft auf ihre Leistungsquelle. Eine eindeutige aktive Zuordnung verhindert die erneute Fakturierung desselben Bestands; später hinzugefügte Leistungen können separat abgerechnet werden. Dokumentkorrektur mit Neuausstellung (`REISSUE`) und Leistungsstorno (`CANCEL_SERVICES`) sind getrennt. | Wiederholung, Nachfakturierung, Korrektur und Firmenforderung sind durch Regressionstests abgedeckt. Vorhandene Rechnungen ohne historische Positionszuordnung werden nicht automatisch als frei fakturierbar behandelt. |
| **F02: Doppelte Zimmerleistungen nach Aufteilung** | Die Neuberechnung berücksichtigt erzeugte Leistungen über sämtliche zugehörigen Gastkonten einschließlich Gruppen-Masterkonto hinweg. Eine unveränderliche Ursprungsreservierung bleibt auch nach dem Routing erhalten. | Verlängern, Verkürzen und Ändern eines Gruppenmitglieds verändert keine Leistungen anderer Mitglieder. Fakturierte oder bereits abgeschlossene Positionen werden nicht heimlich ersetzt oder verschoben. |
| **F03: Steuerverlust im Export und fehlende Leistungsgegenbuchung** | Finanzexport und Nettoauswertungen verwenden den gespeicherten Steuersatz der Leistung. Ein Leistungsstorno erzeugt passende negative Leistungspositionen; eine reine Dokumentkorrektur verändert den Umsatz nicht. Hotelbezogene Buchhaltungskonten ersetzen feste Kontonummern. | Steuerpflichtige Leistungen, Stornos, getrennte Firmenübernahme und Bankzahlung werden geprüft. **Unbekannte Alt-Steuersätze führen zu einer Sperre bzw. ausdrücklich unvollständigen Kennzahlen, niemals zu angenommenen null Prozent.** |
| **F04: Unvollständige Tagesabschlusssperre** | Ein persistierter Betriebstag wird unter Hotelsperre fortgeschrieben. Abgeschlossene Leistungs-/Buchungstage sind gegen normale Änderungen geschützt. Rechnungs- und Gutschriftdatum folgen dem offenen Betriebstag. Der Abschluss prüft offene Kassen, fällige Abreisen und ausstehende Anreisen. | Zukünftige Tage lassen sich nicht abschließen. No-show-Verarbeitung verwendet dieselben Reservierungsbedingungen wie die Rezeption. Zukünftige Leistungsdaten bleiben für Vorausabrechnungen möglich; sie sind vom Dokumentdatum getrennt. |
| **F05: Kassen und Firmenabrechnung** | Mehrere Kassen mit Kassen-/Abteilungscode, Schichtzuordnung von Gastkonto- und direkten POS-Barzahlungen sowie vollständiger Bargeldabgleich. Firmenkredit mit Freigabe, Limit, Zahlungsziel, offenen Posten, Bankausgleich, Guthabenerstattung und protokollierten Mahnstufen. Gruppen-Masterkonten übernehmen konfigurierte Leistungsarten. | `DIRECT_BILL` ist eine Forderungsübernahme und kein erfundener Bankeingang. Reguläre Zahlungsendpunkte dürfen diese Zahlungsart nicht eigenständig buchen. Mahnstufen protokollieren Bearbeitung; ein automatischer Mahnversand ist damit nicht zugesagt. |
| **F06: Zimmerwechsel und Mitreisende** | Zeitbezogene Zimmerabschnitte mit Start-/Enddatum und Rate erlauben geplante Wechsel einschließlich anderer Kategorien. Mitreisende erhalten eigene Profile, Aufenthaltsdaten und Anmeldungsinformationen. | Verfügbarkeit wird für die betroffenen Abschnitte geprüft; Belegung und Mitreisendendaten müssen zum Aufenthalt passen. Ein vorhandener Hauptgast wird nicht durch bloße Erwachsenenanzahlen ersetzt. |
| **F07: Hotelrechte und Arbeitsplatzaktualisierung** | Hotelzuweisungen je Mitarbeiter mit getrennten Lese-/Bearbeitungsrechten für acht Bereiche. Gemeinsame API-Prüfungen, gefilterte Sammelantworten und Portfolioauswertungen; Housekeeping ohne Rezeptionsrecht erhält keine Gastidentitäten aus Belegungen. Sichtbare PMS-Ansichten laden regelmäßig im Hintergrund nach. | Masterverwaltung bleibt getrennt. Lokale Raten benötigen `RATES: MANAGE`, Rückerstattungen die eigene Berechtigung. Die Aktualisierung erfolgt standardmäßig im Abstand von 15 Sekunden sowie nach passenden Aktionen/Fokuswechseln; sie ist kein sofortiger Push-Kanal. |
| **F08: Falsche Restore-Version und fehlender Nachweis** | Erwartete Flyway-Version wird aus den Release-Migrationen bestimmt. Das Restore-Skript schreibt einen maschinenlesbaren Nachweis mit Backup-Prüfsumme, tatsächlicher/erwarteter Version, Zeit und Tabellenzahlen; das PMS kann diesen externen Nachweis prüfen. | Echter Backup-/Restore-Lauf auf isoliertem MySQL 8.4.11 bestanden. Ein dabei reproduzierter Fehler wurde behoben: Schon eine fehlgeschlagene Prüfsummenprüfung ersetzt jetzt einen alten Erfolgsnachweis durch `FAILED`. |
| **F09: Gleich hohe Teilrückzahlungen** | Jeder beabsichtigte Erstattungsvorgang besitzt eine eindeutige Vorgangs-ID. Der Auftrag wird vor dem Provideraufruf gespeichert; Wiederholung verwendet dieselbe ID. Anbieter-ID und Status werden ausgewertet; unklare Vorgänge bleiben offen. | Gleiche Beträge in verschiedenen Vorgängen sind voneinander getrennt. Geschlossene Konten benötigen entsprechendes Guthaben aus einer Leistungskorrektur. Offene Erstattungen blockieren Check-out. Eine Zahlung auf geschlossenem Konto kann nicht einfach annulliert werden. |

Kernimplementierungen: [Finanz- und Gruppenabläufe](../Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsAdvancedService.java), [Reservierungs-/Kassenabläufe](../Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsOperationsService.java), [Betriebstag](../Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsFinancialPeriodService.java), [Debitoren](../Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsReceivablesService.java), [Erstattungsverarbeitung](../Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsRefundProcessor.java).

## Ergänzte Arbeitsbereiche

### Gruppen, Kontingente und Veranstaltungen

Gruppenkontingente können ohne bereits benannte Gäste nach Zimmerkategorie und Zeitraum angelegt werden. Abruf, benannte Buchungen und Freigabefristen wirken gemeinsam auf die verfügbare Kapazität; ein abgerufenes Zimmer wird nicht zusätzlich als unbenanntes Kontingent abgezogen. Zimmerlisten erlauben abweichende Reisedaten innerhalb des Gruppenrahmens. Sammel-Check-in/-out ruft die normalen Validierungen auf und liefert je Mitglied ein eigenes Ergebnis.

Das Gruppen-Masterkonto übernimmt ausgewählte Leistungsarten. Die ursprüngliche Reservierung bleibt für Preisänderungen, Storno, Steuern und Berichte gespeichert. Der normale Check-out eines Mitglieds verlangt den Ausgleich seiner persönlichen Konten; das gesonderte Gruppen-Masterkonto bleibt für die Gruppenabrechnung bestehen.

Veranstaltungsaufträge ergänzen Ressourcenbuchungen um Mengen, Nettopreise, Steuersätze, Aufbau-/Abbauzeiten, Ablauf, Raumbestuhlung und Verpflegungshinweise. Die Pufferzeiten belegen die Ressource ebenfalls. Ein Bankettauftrag kann als PDF ausgegeben und genau einmal auf ein ausgewähltes Gastkonto oder Gruppen-Masterkonto gebucht werden. Gebuchte Positionen sind anschließend unveränderlich. Vertragsunterzeichnung, externe Catering-Systeme und ein vollständiger Angebotsvertrieb sind zusätzliche Integrations-/Produktprozesse.

### Housekeeping und Aufenthalt

Reinigung, Inspektion und Abendservice sind eigenständige Aufträge pro Zimmer und Tag. Zustände wie offen, in Bearbeitung, erledigt, „Nicht stören“ und verschoben sowie Zuweisung und Ereignishistorie werden getrennt gespeichert. Versionsprüfung verhindert das unbemerkte Überschreiben fremder Änderungen; bei Konflikten kann die aktuelle Fassung übernommen werden, ohne den eigenen Entwurf sofort zu verlieren.

Zimmerabschnitte, Mitreisende und ihre Anmeldungen sind im Aufenthalt erreichbar. Die Anzeige respektiert die Hotel- und Bereichsrechte. Der fortlaufende Zimmerplan mit fixierten Zimmernummern, Tagesköpfen, Standardzeitraum ab heute und Ausstattungsfiltern bleibt erhalten; Details stehen in der [internationalen Konfiguration](pms-international-expansion.md).

### Raten, Anzahlungen und Kartenzahlungen

Ratenbedingungen umfassen Storno-/No-show-Regeln, Anzahlungsquote und Fälligkeit. Neue Reservierungen speichern die akzeptierten Bedingungen als Snapshot. Die Aufenthaltsansicht zeigt fällige Anzahlungen und mögliche Gebühren; Statusänderungen verbuchen konfigurierte Gebühren mit explizitem Steuersatz. Bereits fakturierte oder abgeschlossene Altleistungen bleiben für eine nachvollziehbare Korrektur erhalten. Ein fehlender Alt-Snapshot wird nicht rückwirkend aus der heute gültigen Rate erfunden.

Master können Raten in ausgewählte andere Hotels desselben Mandanten veröffentlichen. Zimmerkategorie, Zielrate, lokale Preise, Währung und Steuersätze werden ausdrücklich angegeben; die Veröffentlichung errät keine Wechselkurse oder Ländersteuern und verändert keine akzeptierten Reservierungsbedingungen.

Der Zahlungsbereich umfasst gespeicherte Checkout-Aufträge, Kartenvorautorisierung, Einzug, Freigabe und Statusabgleich über den implementierten Stripe-Adapter. Ein Rücksprung aus dem Browser gilt nicht als Zahlungsbestätigung. Anbieterprüfung und die lokale Buchung bleiben verbunden. Anzahlungsregeln allein lösen keine automatische Abbuchung aus. Produktive Schlüssel, unterstützte Zahlungsmittel, Währungen und konkrete Anbieterabnahmen müssen für die Installation bereitgestellt werden.

Bei einer unklaren Erstattungsantwort hält die Oberfläche Betrag, Grund, Kasse und Vorgangs-ID des begonnenen Auftrags fest. Sie lädt den dazugehörigen gespeicherten Vorgang nach und wiederholt denselben Auftrag. Ein bekannter offener Vorgang sperrt zusätzliche Rückerstattungen bzw. Annullierungen derselben Originalzahlung, bis der Anbieterstatus geklärt ist.

### Buchhaltung, Kennzahlen und Planung

Hotelbezogene Kontenzuordnungen umfassen Gast-/Firmenforderungen, Erlösarten, Zahlungsmittel, Bank und POS. Bestehende Standardwerte sind vorbelegt; Änderungen sind Masteraktionen. Neu erzeugte Exporte verwenden die aktuelle Zuordnung, auch für historische Auswahlzeiträume. Bereits übergebene Buchhaltungsläufe müssen daher mit dem tatsächlichen Finanzsystem abgestimmt bleiben.

Umsatz, ADR und RevPAR verwenden die tatsächlich gespeicherten Netto-Zimmerleistungen nach Leistungsdatum einschließlich entsprechender Gegenbuchungen. Frühstück und andere Leistungen sind eigene Erlösarten. Bei Storno/No-show verschwinden bereits gebuchte Leistungen nicht allein durch den Reservierungsstatus aus der Umsatzsicht; eine nötige Gutschrift ist ein eigener Geschäftsvorgang.

Die neue Revenue-Ansicht zeigt den aktuellen bestätigten/im Haus befindlichen Buchungsstand, manuell gespeicherte Vergleichsstände, Pickup-Differenzen und Monatsbudgets. Teilmonatsbudgets werden nach Kalendertagen anteilig berechnet. Es gibt keine erfundene historische Buchungskurve und kein Prognosemodell für zukünftige Nachfrage. Fehlende Steuerdaten werden als unbekannte Nettoerlöse bzw. unvollständige Vergleichswerte ausgewiesen.

### Rechte, Historien, Währungen und Arbeitsbereiche

Die Hotelmatrix trennt `FRONT_DESK`, `GUESTS`, `HOUSEKEEPING`, `FINANCE`, `REFUNDS`, `RATES`, `REPORTS` und `INTEGRATIONS` jeweils in Lesen und Bearbeiten. Ein globales PMS-Leserecht begrenzt auch weitergehende Hotelzuweisungen. Firmen-/Ratenvertragsdokumente verlangen bei Mitarbeitern einen expliziten Hotelkontext und eine Verbindung der Firma zu diesem Hotel. Details: [Hotelrechte](pms-property-access.md).

Zehn Historienbereiche werden als begrenzte SQL-Projektionen mit Suche und Seitennavigation geladen: Rechnungen, Tagesabschlüsse, Kommunikation, Integrationswarteschlange, Audit, Gästeanmeldungen, Ressourcenbuchungen, POS-Belege, Zutrittsberechtigungen und Migrationsläufe. Debitorenlisten sind ebenfalls paginiert. Binäre Vertragsdokumente werden nicht in Listen mitgeladen. Das bedeutet keine vollständige Pagination sämtlicher Stammdaten; insbesondere explizit geöffnete Einrichtung und Filter-Metadaten bleiben weiter zu betrachten.

Die Operations-Übersicht bündelt die Abfragen von Reservierungshistorie und Rechnungszuordnungen. Zusammen benötigte Beziehungen werden über gezielte Entity-Graphs geladen. Ein Regressionstest vergleicht die SQL-Abfragezahl bei zwei und zwanzig Aufenthalten, damit beim Vergrößern der Liste nicht erneut einzelne Historien-/Rechnungsabfragen pro Aufenthalt entstehen. Dieser Test prüft das Abfrageverhalten; eine Zusage für beliebig große Antwortmengen folgt daraus nicht.

Geldbeträge werden mit bis zu vier Nachkommastellen gespeichert und nach den kleinsten Einheiten der jeweiligen Währung geprüft bzw. gerundet. JPY ohne Nachkommastellen und KWD mit drei Nachkommastellen sind geprüft. Diese Präzision ist keine automatische Währungsumrechnung. Anbieterformate und deren Währungsunterstützung werden gesondert behandelt.

PMS und Chrono haben getrennte interne Tabs. Reguläres Öffnen aktiviert den vorhandenen Tab; Mittelklick erzeugt einen weiteren. Der Browserverlauf unterscheidet auch gleiche Adressen anhand des Tabs. Nur das PMS bleibt bei Inaktivität angemeldet und erneuert noch gültige Sitzungen. Chrono behält die Inaktivitätsabmeldung; bewusstes Abmelden wirkt weiterhin in allen Browser-Tabs. Längerer Offline-/Ruhezustand kann eine neue Anmeldung erfordern. Einzelheiten: [Arbeitsbereiche und Sitzung](pms-workspaces-and-session.md).

## Migrationen und Einführung

Die Änderungen ergänzen die vorhandenen Migrationen V23–V26. Für den hier geprüften Release ist **V38** der jüngste Stand.

| Migration | Inhalt |
| --- | --- |
| V27 | Rechnungsquellen und aktive Zuordnung, Finanzperioden, Firmenkreditkonten, Debitoren und Ausgleichsbuchungen |
| V28 | Zimmerabschnitte, Mitreisende, Kassen-/Abteilungscodes, Zahlungsdatum und dauerhafte Erstattungsaufträge |
| V29 | Mitarbeiterrechte je Hotel |
| V30 | Verbindung direkter POS-Zahlungen mit der Kassenschicht |
| V31 | Unabhängige Housekeeping-Arbeitsarten, Versionsprüfung und Ereignishistorie |
| V32 | Hosted-Payment-Aufträge und akzeptierte Raten-/Gebührenbedingungen je Reservierung |
| V33 | Gruppenkontingente, Masterkonto, Routing und ursprüngliche Reservierung einer Leistung |
| V34 | Veranstaltungsaufträge/-positionen und gepufferte Ressourcenbelegung |
| V35 | Währungspräzision für Geldbeträge und feinere Steuerpräzision |
| V36 | Konfigurierbare Buchhaltungskonten je Hotel |
| V37 | Gespeicherte Revenue-Vergleichsstände und Monatsbudgets |
| V38 | Datenbankkompatible Zahlungsstatus `PENDING`/`FAILED` und Firmenübernahme |

Migrationen regulär über Flyway ausführen; produktive Hibernate-Schemaänderung bleibt deaktiviert. Frontend und Backend zusammen ausrollen. Der Nachweis auf MySQL umfasst sowohl eine neue Datenbank als auch die Baseline einer vorhandenen Chrono-Struktur. V38 behebt ausdrücklich einen Fehler, den reine Entity-DDL-Tests nicht sahen: Das frühere Datenbank-ENUM erlaubte keine offenen Erstattungsstatus.

Vor Wiederaufnahme des Mitarbeiterbetriebs müssen Master die Hotelzuweisungen vergeben; Bestandsmitarbeiter bekommen keine pauschalen Rechte auf alle Hotels. Automatisches Testkonto und Demo-Initialisierung bleiben in Produktion deaktiviert. Die lokale Demo verwendet für neue Datensätze ausdrücklich konfigurierte Beispiel-Steuersätze; sie repariert keine bestehenden Hoteldaten durch Schätzung.

**Alt-Steuerdaten müssen fachlich geklärt werden.** Finanzexport und Performancebericht blockieren betroffene Leistungen ohne Steuersatz mit einer verständlichen Fehlermeldung; die Revenue-Planung kennzeichnet unbestimmte Nettowerte. Weder `null` noch eine ausländische Anschrift bedeutet automatisch Steuerfreiheit. Beim bewussten Ausstellen einer bisher nicht zugeordneten Altleistung wird ein ausdrücklich gewählter Steuersatz auf der Quelle und Rechnung festgehalten. Unveränderliche oder bereits abgeschlossene Sachverhalte sind über dokumentierte Korrekturen zu behandeln. Es gibt keinen pauschalen Null-Prozent-Backfill.

Der gespeicherte Betriebstag kann vom Kalenderdatum abweichen. Kassen, Rechnungsausstellung, Forderungsübernahme und Korrekturen müssen mit diesem offenen Tag arbeiten. Vor dem Tagesabschluss sind Kassen und fällige Aufenthalte zu klären. Bei unklarem Kartenstatus denselben gespeicherten Vorgang erneut abgleichen; einen Ersatzvorgang erst nach geklärtem tatsächlichem Geldfluss anlegen.

## Prüfnachweise

### Automatisierte und visuelle Prüfungen

| Prüfung | Nachgewiesener Stand |
| --- | --- |
| Backend, abschließender vollständiger Lauf | **273 Tests: 272 bestanden, ein optionaler MySQL-Test übersprungen.** Keine Fehler/Fehlschläge. Der Demo-Steuernachweis und der zusätzliche Test zur SQL-Bündelung sind enthalten. [Laufprotokoll](../tmp/pms-enterprise-backend-final.log). |
| MySQL 8.4 | Separater, tatsächlich ausgeführter Migrationslauf für frische und bestehende/baselinierte Datenbank bestanden; V27–V38 eingeschlossen. Der Skip im regulären Lauf bedeutet keine fehlende MySQL-Prüfung. |
| Vollständiger Anwendungsstart | Live-Profil gegen Flyway-Schema mit expliziten Test-Fixtures bestanden. Reale Zahlungs-/Rechnungsabläufe und vollständig gesetzte Demo-Steuersnapshots; Performance- und Revenue-Berichte erfolgreich. Nur der externe Kartenanbieter wird im Test ersetzt. Produktionsschutz bleibt aktiv. |
| Frontend | **383 von 383 Tests in 68 Dateien bestanden**, einschließlich der abschließenden Rollen-, Hotelkontext- und Erstattungsvorgangskorrekturen. [Laufprotokoll](../tmp/pms-enterprise-frontend-final.log). |
| Produktionsbuild | Vite-Build erfolgreich in **11,44 Sekunden**. [Buildprotokoll](../tmp/pms-enterprise-build-final.log). |
| Browser | Isolierte Anwendung bei 1.600 × 1.000 und 390 × 900 Pixeln, helle und dunkle Ansicht; Zimmerplan, Housekeeping, Gastkonten, Rechnungen, Berichte, Hotelrechte und Bankettauftrag visuell geprüft. Housekeeping-Auftrag auf „Bitte nicht stören“ geändert, gespeichert und mit Verlauf nachgelesen. [Aufnahmen](../output/playwright/). |

Beispielaufnahmen: [Zimmerplan](../output/playwright/pms-room-plan-desktop.png), [Zimmerplan mit 1.007 Zimmern](../output/playwright/pms-room-plan-dense-dark.png), [Housekeeping](../output/playwright/pms-housekeeping-desktop.png), [Gastkonten](../output/playwright/pms-folios-desktop.png), [Veranstaltungsauftrag](../output/playwright/pms-event-order-desktop.png), [Veranstaltungsauftrag im Dark Mode](../output/playwright/pms-event-order-dark.png), [Berichte mit 500 zusätzlichen Aufenthalten](../output/playwright/pms-reports-dark.png), [mobile Berichtsansicht](../output/playwright/pms-reports-mobile-dark.png). Aufnahmen belegen den jeweils geprüften Bildschirmstand, nicht sämtliche Rollen-, Browser- und Gerätevarianten.

### Echter Backup-/Restore-Probelauf

Der [maschinenlesbare Nachweis](../output/pms-restore-verification-2026-09-12.json) dokumentiert den Lauf der vorhandenen Skripte `backup.sh`, `check-backup.sh` und `restore-drill.sh` in getrennten lokalen Docker-Containern. Quelle und Ziel verwendeten MySQL **8.4.11**. Wiederhergestellt wurden **131 Tabellen, davon 60 PMS-Tabellen, 206 Fremdschlüssel und 25 Flyway-Historieneinträge**. Vollständige Struktur- und Migrationsprüfsummen stimmen zwischen Quelle und Ziel überein; erwartete und tatsächliche Release-Version waren **38**.

Der SQL-Dump war **195.706 Byte** groß; die abschließend gemessene Wiederherstellung dauerte **13,001 Sekunden**. Die SHA-256-Prüfsumme ist im Nachweis gespeichert. Ein absichtlich beschädigter Dump und die Simulation eines späteren Releases V39 wurden mit Exit-Code 1 und `FAILED`-Nachweis zurückgewiesen. ShellCheck v0.10.0 war erfolgreich. Die hierfür angelegten Container und das eigene Netzwerk wurden entfernt; die Quelle und Prüfdateien blieben erhalten.

Das war eine Migrations-Testdatenbank, kein vollständig belegtes produktives Hotel mit großen Dokument- und Historienbeständen. Aus dieser Laufzeit werden keine produktiven RTO-/RPO-Werte oder Verfügbarkeitszusagen abgeleitet.

### Gemischter Mitarbeiter-Lasttest

Der erste dokumentierte [k6-Lauf](../output/playwright/pms-staff-load-report.md) lief lokal 45 Sekunden mit zwölf gleichzeitigen virtuellen Mitarbeitern und **1.007 Zimmern**. Er erfasste 1.347 HTTP-Anfragen, 24 tatsächliche Schreibanfragen, keine HTTP-/Arbeitsablauf-/Berechtigungsfehler sowie Lese-p95 **28,66 ms** und Schreib-p95 **50,77 ms**. Rezeption, Housekeeping, Finanzen und Management hatten getrennte normale Mitarbeiterkonten; acht negative Rechteprüfungen lieferten erwartungsgemäß HTTP 403.

Diese erste Variante hatte überwiegend unbelegte Zimmer und wenige Reservierungs-/Rechnungshistorien. Der anschließende Basislauf mit **500 zusätzlich belegten Aufenthalten** und **1.500 Zimmerleistungspositionen** verfehlte die konfigurierten Schreiblatenzziele: **p95 2.771,57 ms** bei einem Ziel unter 1.500 ms und **p99 3.146,31 ms** bei einem Ziel unter 3.000 ms. [Rohmesswerte](../output/playwright/pms-staff-load-dense-summary.json) und [Testaufbau](../output/playwright/pms-staff-load-dense-metadata.json) bleiben erhalten.

Nach der Bündelung der Operations-Abfragen bestand der [optimierte Vergleichslauf](../output/playwright/pms-staff-load-dense-optimized-report.md) alle gesetzten Grenzen: **1.342 HTTP-Anfragen, 29 Schreibanfragen, keine HTTP-/Funktions-/Berechtigungsfehler, Lese-p95 83,43 ms und Schreib-p95 179,59 ms**. Wieder waren zwölf virtuelle Mitarbeiter 45 Sekunden aktiv. Alle 1.007 Zimmer wurden über 21 Zimmerplanseiten geprüft; alle 500 zusätzlichen Aufenthalte hatten die erwarteten drei Leistungsdaten und alle 1.500 zugehörigen Zimmerpositionen einen bestätigten Steuersatz. Acht negative Berechtigungsprüfungen wurden korrekt abgelehnt.

Das ist ein lokaler Vergleich mit gleicher Zimmer-/Aufenthaltsmenge, kein exakt kontrollierter Mikrobenchmark: Die optimierte Umgebung nutzte eine H2-Dateidatenbank statt H2 im Arbeitsspeicher und korrigierte neue Demo-Steuersnapshots. Aufwärmung und zufällige Schreibverteilung unterschieden sich. Der Bestand enthielt nur eine Rechnung und keine offenen Firmenforderungen. Ein allgemeiner Beschleunigungsfaktor, Verhalten bei jahrelanger Finanzhistorie oder eine Produktions-SLA wird daraus nicht abgeleitet.

Die lokalen Kurzläufe ersetzen keinen Dauertest der Zielinfrastruktur mit paralleler Zimmervergabe, OTA-Verkehr, echten Zahlungsanbietern, Failover und hoher Schreiblast. Das [Lasttest-Runbook](operations/PMS_STAFF_LOAD.md) beschreibt Profil und Grenzen.

## Verbleibende Abnahmen und Integrationen

- **Anbieter:** Konkrete OTA-/Channel-Manager-, Schließsystem-, POS-, Finanzsystem- und Kartenanbieter benötigen ihre tatsächlichen Protokolle, Zugangsdaten, Sandboxfälle und Freigaben. Eine funktionierende Warteschlange oder konfigurierbare Anbieteradresse ist keine allgemeine Anbieterabnahme. Siehe [Integrationen](operations/PROVIDER_INTEGRATIONS.md).
- **Länder:** Fiskalisierung, verpflichtende E-Rechnungen, Meldeadapter, besondere Abgaben und nationale Ausnahmen müssen für die tatsächlichen Hotelstandorte umgesetzt bzw. angebunden und fachlich abgenommen werden. Internationale Gästeprofile und Währungspräzision ersetzen diese Arbeiten nicht.
- **Dokumente:** Adressformate sind konfigurierbar, bilden aber keinen vollständigen weltweiten Poststandard-Katalog. Nichtlateinische PDFs benötigen eine passende lizenzierte Schrift; fehlende Zeichen werden als Fehler gemeldet. Der gespeicherte Rechnungsempfänger führt nicht automatisch zu Rechnungsversand.
- **Betrieb:** Hotelweite Schreibsperren schützen gemeinsame Bestände, können aber bei sehr hoher Parallelität begrenzen. Große Datenhistorien, Infrastruktur, Laufzeiten, Datensicherungsaufbewahrung, Wiederanlauf und Betriebsverantwortung sind pro Installation zu prüfen.
- **Fachliche Einführung:** Eine Hotelkette muss mindestens einen vollständigen Betriebstag mit Rollenwechseln, Gruppenanreise, Zimmerwechsel, Firmenabrechnung, Zahlungskorrektur, Tagesabschluss und Wiederherstellung abnehmen. Die vorhandenen [Enterprise-Kriterien](operations/ENTERPRISE_READINESS.md) und die [UAT-Checkliste](operations/UAT_CHECKLIST.md) unterstützen diese Abnahme.

Der Implementierungsstand ist deutlich umfangreicher und gezielt auf die dokumentierten Fehler geprüft. Eine pauschale Aussage „für jedes Hotel weltweit vollständig zertifiziert“ oder eine Produktions-SLA lässt sich daraus nicht ableiten.
