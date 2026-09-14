# Chrono PMS: abschließender Code- und Prüfstand

Stand: 12. September 2026. Dieser Bericht ergänzt den [ersten Enterprise-Umsetzungsstand](pms-enterprise-implementation.md) um die anschließend implementierten Funktionen bis Flyway V48. Maßgeblich ist der vom Nutzer präzisierte Auftrag: funktionierender Anwendungscode. Die Prüfungen verwenden ausschließlich isolierte lokale Systeme und synthetische Hoteldaten.

## Bedienung und Design

- **Eigenständiger PMS-Arbeitsbereich:** PMS und Chrono besitzen getrennte interne Tabs. Erneutes Anklicken eines geöffneten Navigationsziels aktiviert den vorhandenen Tab; Mittelklick öffnet eine weitere Instanz. Vor-/Zurücknavigation und unabhängige, noch ungespeicherte Eingaben sind durch Integrationstests abgesichert. Das PMS zeigt dauerhaft die vollständige Oberfläche; der Einfach-/Profi-Umschalter entfällt. Die Ausnahme vom Inaktivitätslogout gilt für das PMS. [Sitzung und Tabs](pms-workspaces-and-session.md).
- **Fortlaufender Zimmerplan:** Fixierte Zimmernummern links, Tage mit Wochentag oben, Start ab dem aktuellen Hoteltag und standardmäßig 30 Tage. Weitere Zeiträume, Suche, Ausstattungsfilter, Zimmerfarben, Tastaturnavigation, Bereichsauswahl, Excel-kompatibles Kopieren und Sammelaufträge sind integriert. Der Plan lädt begrenzte Datenbankseiten und rendert nur den sichtbaren Zimmerbereich; der Raumwechsel ist kein vollständiges Neuladen aller Zimmer.
- **Übersichtliche Finanzen:** Die Arbeitsbereiche Rechnungen, Forderungen, Versand/Mahnungen, Bank/Buchhaltung, Zahlungen, Freigaben und Länder/XML sind getrennt anwählbar. Eingaben bleiben beim Wechsel erhalten. Bankimport, Aktionen, Statusbezeichnungen und mobile Formulare verwenden das gemeinsame Design. Helle und dunkle Ansichten wurden visuell geprüft.
- **Wiederverbindung:** Ein vorübergehender Fehler beim Laden der Hoteleinrichtung wird als Ladefehler mit Wiederholung angezeigt. Er darf kein leeres Hotel vortäuschen. Automatische Wiederverbindung und Hintergrundaktualisierung laden die Einrichtung erneut.

## Ergänzte und durchgängig verbundene Abläufe

| Bereich | Implementierter Stand |
| --- | --- |
| Ketten und Stammdaten | Hotel- und Bereichsrechte, Masterverwaltung, paginierte Mitarbeiter-/Firmen-/Gruppensuche und gezieltes Nachladen ausgewählter Ansprechpartner. Gäste/Firmen behalten getrennte Kontaktadressen, verknüpfte Ansprechpartner, individuelle Rechnungsprofile und besondere Gästehinweise. |
| Arbeitsplatzaktualisierung | Authentifizierter Ereignisstrom je Hotel, serverseitige Änderungsabfrage, begrenzte Verbindungen, Wiederverbindung und 15-Sekunden-Rückfallabfrage. Ereignisse transportieren nur Aktualisierungssignale. Hotelberechtigung und Tokenablauf werden erneut geprüft. |
| Housekeeping ohne Netz | Bewusst aktivierbare lokale Vormerkung, befristeter Cache je Nutzer/Hotel, eigene Vorgangs-ID pro Änderung und automatische Synchronisierung. Versionskonflikte verlangen eine ausdrückliche Auflösung. Wiederholungen erzeugen keine doppelten Änderungen; Abmeldung und entzogene Rechte räumen den betroffenen Cache auf. |
| Finanzfreigaben | Konfigurierbares Vier-Augen-Prinzip für Rückzahlungen mit Schwellenwert, kumulierten gebuchten/offenen Beträgen, Ablaufzeit und unveränderlichem Antragsinhalt. Die antragstellende Person kann sich nicht selbst freigeben. Die Freigabe wird atomar für den zugehörigen Vorgang verbraucht. |
| Zahlungsautomatik | Hotelbezogener Stripe-Kontext, dauerhaft gespeicherte Zahlungs-/Erstattungsvorgänge, geprüfte Webhooks, Wiederholung und Statusabgleich. Automatische Anzahlungslinks und ihre Zustellung sind getrennt aktivierbar. [Zahlungen](operations/PMS_PAYMENT_AUTOMATION.md). |
| Rechnung und Versand | Unveränderlich archivierte PDF-Originale, Rechnungs- und Gutschrifttexte in Deutsch, Englisch, Französisch, Italienisch und Spanisch sowie persistente Versandaufträge mit MIME-Anhängen. Mahnregeln prüfen vor dem Versand den aktuellen Forderungsstand. [Rechnung, Versand und Mahnung](pms-billing-automation.md). |
| Bank und Buchhaltung | Definierter CSV-Bankimport mit Vorschau, ausdrücklicher Forderungszuordnung und Wiederholungsschutz. Exportläufe archivieren Datei, Zeitraum und Prüfsumme; eine gespeicherte Übernahmereferenz bestätigt die Übergabe. |
| Revenue | Gespeicherte Vergleichsstände, historisch begründete Nachfrageprognose, sichtbare Datenbasis und begrenzte Preisvorschläge. Fehlende Historie ergibt keine erfundene Prognose. Automatische Übernahme ist konfigurierbar und schützt manuelle Preise. |
| Kettenraten | Dauerhafte Vererbungsbeziehungen, ausdrücklich erfasste Wechselkurse, lokale Steuerzuordnung, Zyklusprüfung und eingefrorene lokale Ausnahmen. Akzeptierte Reservierungsbedingungen bleiben unverändert. |
| Veranstaltungen | Versionierte Angebote aus dem Veranstaltungsauftrag, befristete Kundenlinks, einmalige Entscheidung und unveränderlicher angenommener Leistungsstand. Abweichende spätere Bankettaufträge werden nicht als bereits vereinbart behandelt. |
| Buchungskanäle | Konkreter Beds24-API-v2-Adapter für Kalender-/Bestandsveröffentlichung mit dauerhaften Aufträgen, Statusabgleich und Fehlerbehandlung. Nicht abgeglichene externe Buchungen blockieren eine ungesicherte Veröffentlichung. [Beds24](operations/PMS_BEDS24.md). |
| Internationale Konfiguration | Hotelbezogene Pflichtfelder und versionierte Gästeanmeldungen. UBL-2.1-Rechnung/Gutschrift mit Betragsprüfung, ausdrücklicher Vorauszahlungszuordnung, archiviertem XML und Prüfung gegen die offiziellen mitgelieferten XSDs. [Revenue, Angebote, Raten und UBL](pms-commercial-automation-and-ubl.md). |
| Vertragsdokumente | Versionen, Prüfsummen, Archivierung, Größen-/Mandantenlimits und konfliktgeschütztes Ersetzen. Listen laden keine Dokumentbytes. Datenbankspeicherung bleibt Standard; ein privates Dateisystem ist optional konfigurierbar. [Konfiguration und Speicher](operations/PMS_RUNTIME_CONFIGURATION.md). |

Die bereits im ersten Bericht beschriebenen Korrekturen an Fakturierung, Leistungsrouting, Tagesabschluss, mehreren Kassen, Gruppen-Masterkonten, Firmenforderungen, Zimmerabschnitten, Mitreisenden und Steuerexport bleiben Bestandteil dieses Stands.

## Datenbank und Laufzeit

| Migration | Inhalt |
| --- | --- |
| V39 | Zahlungsautomatik und dauerhafte Verarbeitung von Anbieterereignissen |
| V40 | Versand, Mahnwesen, Bankimporte und archivierte Buchhaltungsexporte |
| V41 | Revenue-Automatik und Vergleichsdaten |
| V42 | Versionierte Veranstaltungsangebote |
| V43 | Ratenvererbung |
| V44 | Dokumentversionen und Speicherverwaltung |
| V45 | Beds24-Veröffentlichungen |
| V46 | Internationale Anmelderegeln und strukturierte Rechnungsdokumente |
| V47 | Wiederholungssichere Offline-Housekeeping-Vorgänge |
| V48 | Finanzfreigaben und Freigaberichtlinien |

Die Laufzeitschalter sind zwischen Anwendungseinstellungen, `.env.example` und den Compose-Konfigurationen abgestimmt. Neue Zahlungs-, Versand- und Anbieterautomatik wird bewusst konfiguriert. Der Produktionsmodus erlaubt keine simulierten Zahlungsbuchungen. Ein optionaler privater Dokumentenspeicher muss zusammen mit der Datenbank gesichert werden; der unten nachgewiesene Restore verwendet die Standardspeicherung in der Datenbank.

Beim Ereignisstrom wird der bereits autorisierte asynchrone Abschluss gezielt zugelassen; der initiale Aufruf benötigt weiterhin Anmeldung und Hotelrecht. Ein interner Marker darf keine beliebigen Endpunkte freigeben. Hintergrund ist die erneute Autorisierung von Servlet-Dispatches in [Spring Security](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html). Tests prüfen ausdrücklich anonyme Erstaufrufe, fehlende Marker und fremde Pfade.

## Abschließende Prüfnachweise

| Prüfung | Ergebnis und Nachweis |
| --- | --- |
| Gesamtes Backend | **694 Tests: 693 bestanden, ein optionaler MySQL-Test im Standardlauf übersprungen.** Keine Fehler. [Vollständiges Protokoll](../tmp/pms-final-entire-backend-v2.log). |
| Echte MySQL-Migrationen | Der optionale Test wurde separat tatsächlich gegen MySQL 8.4 ausgeführt: neue und vorhandene/baselinierte Datenbank bis V48. Der zusammengefasste gezielte Lauf bestand **105 von 105 Tests ohne Skip**; diese Zahl ist kein zusätzlicher unabhängiger Gesamtbestand. [Protokoll](../tmp/pms-final-mysql-new-modules.log). |
| Letzte Backendänderung | Nach dem vollständigen Lauf wurde das Schließen unterbrochener Ereignisströme ergänzt. Erneute Kompilierung und **5 von 5 betroffenen Service-/Security-Tests bestanden**. [Protokoll](../tmp/pms-final-live-close.log). |
| Gesamtes Frontend | **456 von 456 Tests in 80 Dateien bestanden.** Einschließlich unabhängiger Tabentwürfe, Wiederverbindung, Offline-Synchronisierung, Freigaben, Rechnungen, Verzeichnisse und neuer Finanznavigation. [Protokoll](../tmp/pms-final-frontend-complete.log). |
| Produktionsbuild | Vite-Build erfolgreich, 2.245 Module, 6,37 Sekunden. [Protokoll](../tmp/pms-final-production-build.log). |
| API-Abläufe gegen MySQL | **11 von 11 Prüfungen bestanden:** Hotel-/Firmenaufenthalt, französische CHF-Rechnung, bytegleicher PDF-Nachdruck nach Profiländerung, unveränderliches UBL, unabhängige XSD-Prüfung, Bankausgleich mit Duplikatvermeidung und archivierter Buchhaltungsexport. [Prüfbericht](../output/playwright/pms-api-billing-qa.md), [Messdaten](../output/playwright/pms-api-billing-qa.json). |
| Großer Zimmerplan | Im Browser **1.007 Zimmer** in einem Hotel sowie 500 zusätzliche Aufenthalte und 1.500 Zimmerleistungspositionen. Sprung in den Bereich um Zimmer 950 bei nur rund 20 gerenderten Zeilen. Zusätzlich prüft die automatisierte Virtualisierungsregression einen Bestand von 20.000 Zimmern. Das ist kein Lastnachweis mit 20.000 real belegten Zimmern. [Testdaten](../output/playwright/pms-staff-dense-seed-final-mysql.json), [Zimmerplan](../output/playwright/pms-final-room-plan-desktop.png). |
| Mobile Gestaltung | Zimmerplan bei 390 × 844 Pixeln ohne horizontales Überlaufen der gesamten Seite; die Tagesspalten bleiben innerhalb des Plans scrollbar. Finanzbereich in heller/dunkler Ansicht geprüft. [Mobil](../output/playwright/pms-final-room-plan-mobile.png), [Bank und Buchhaltung](../output/playwright/pms-final-bank-light.png). |
| Tatsächliche Offline-Bearbeitung | Browser offline geschaltet, Auftrag lokal vorgemerkt, Serverbestand blieb zunächst unverändert. Nach Wiederverbindung genau eine Änderung mit neuer Version und zugehörigem Verlauf; lokale Vormerkung verschwand. [Vorher](../output/playwright/pms-final-offline-before-sync.json), [Nachher](../output/playwright/pms-final-offline-after-sync.json). |
| Tatsächliche Live-Aktualisierung | Authentifizierter Stream und serverseitige Housekeeping-Änderung; Änderungsereignis im ersten Lauf nach 937 ms empfangen, ohne Geschäftsdaten im Ereignis. Nach Neustart der abschließenden Codefassung erneut erfolgreich, diesmal 128 ms. Ein bereits vorhandener Auftrag wurde beim Wiederholen vorher korrekt als Konflikt abgewiesen; der erfolgreiche zweite Lauf verwendet ein anderes synthetisches Zimmer. [Erster Nachweis](../output/playwright/pms-final-live-verification.json), [Abschließender Neustarttest](../output/playwright/pms-final-release-live-verification.json). |
| Backup und Wiederherstellung | Repository-Skripte auf isoliertem MySQL 8.4.11: **152 Tabellen, davon 81 PMS-Tabellen, Flyway V48**, Wiederherstellung in ein neues Testschema erfolgreich. Alle 7.551 Datensätze, Tabellenchecksummen und archivierten PDF-/UBL-/Exportbytes stimmen mit der Quelle überein. Bankausgleich und bestätigter Offlinevorgang sind erhalten. Der Restore dauerte elf Sekunden bei diesem synthetischen Datenbestand. [Restore-Nachweis](../output/pms-final-restore-verification.json), [Inhaltsvergleich](../output/pms-final-restore-content-verification.json). |

Eine frühere parallele Frontendprüfung überschritt bei einem mehrstufigen Tabtest die standardmäßigen fünf Sekunden. Der isolierte Test bestand; für diese Integrationstestgruppe gilt nun eine begrenzte längere Laufzeit. Der anschließend erneut ausgeführte vollständige Lauf bestand. Die fachlichen Assertions wurden beibehalten.

Die abschließende Anwendung startet gegen das migrierte MySQL-Schema; Anmeldung und authentifizierte PMS-Aufrufe funktionieren. Der allgemeine Actuator-Gesundheitscheck meldet im isolierten Browser-Testaufbau den bewusst nicht gestarteten lokalen SMTP-Dienst als nicht erreichbar. Dies wird nicht als erfolgreicher externer Mailbetrieb gewertet. Der eigentliche SMTP-Ablauf wurde separat im Integrationstest mit einem lokalen Testserver geprüft.

## Konkrete Grenzen dieses Stands

Die Prüfungen belegen die genannten Codepfade und lokalen Abläufe. Sie sind keine pauschale Aussage, dass jede mögliche Hotelkonfiguration oder jedes Land vollständig abgenommen ist.

- Stripe und Beds24 besitzen konkrete Adapter. Andere Türschloss-, Kassen-, Channel-Manager- und Buchhaltungsprodukte benötigen ihren jeweiligen Adapter beziehungsweise ein passendes unterstütztes Austauschformat. Konfiguration allein implementiert kein fremdes Protokoll.
- UBL-XSD-Prüfung deckt die unterstützte XML-Syntax ab. Peppol-/EN16931-Geschäftsregeln, XRechnung, nationale Fiskalisierung und Behördenübermittlung sind keine dadurch automatisch unterstützten Funktionen. Internationale Gästeadressen bestimmen nicht automatisch die Hotelsteuer.
- Bankimport unterstützt das dokumentierte CSV-Format; CAMT und beliebige Bankdateien sind kein zugesagter Importpfad. Buchhaltungsexporte archivieren den definierten CSV-Vertrag, ohne eine automatische Verbindung zu jedem Finanzsystem zu behaupten.
- E-Mail-Übergabe an einen SMTP-Server ist implementiert und lokal mit einem echten Test-SMTP-Server geprüft. Tatsächliche Zustellung im Postfach und externe Anbieterfreigaben wurden nicht vorgetäuscht.
- Der Browser- und Restore-Nachweis ersetzt keinen Dauertest einer konkreten weltweiten Infrastruktur. Der aktuelle Frontend-Hauptchunk umfasst rund 3,97 MB vor und 1,13 MB nach gzip; weitere Aufteilung kann die Erstladezeit auf langsamen Verbindungen verbessern.

Für konkrete Anbieter und Betriebseinstellungen gelten die verlinkten Runbooks. Für die getesteten internen Abläufe werden keine Hoteladressen, Produktionsschlüssel oder Livekonten benötigt.
