# Chrono: Vergleich von vier Ausschreibungen

Stand: 15. September 2026. Codebasis: lokaler Checkout `c02842d7`. Bewertung anhand von Quellcode, Projektdokumentation, den bereitgestellten Screenshots und öffentlich zugänglichen Ausschreibungen. Keine Implementierung, Laufzeitabnahme oder Lastprüfung durchgeführt. „Vorhanden“ bedeutet im Code umgesetzt, nicht beim jeweiligen Auftraggeber produktiv abgenommen.

## Entscheidung

| Priorität | Projekt | Einschätzung | Geschätzter Zusatzaufwand |
|---|---|---|---|
| 1 | Kirchliche Einrichtung: Gästehaus und Seminarräume | Beste fachliche Passung. Bestehendes PMS gezielt ergänzen. | 35–65 PT bis zum abgegrenzten produktiven Pilot |
| 2 | Waschstraßen: zwei Filialen | Technisch machbar, aber mehrere wesentliche Kassen- und Hardwarefunktionen fehlen. | 75–140 PT; DATEV und Fingerprint zusätzlich |
| 3 | Tessiner Kantonsverwaltung: HR-System | Eignungshürden zuerst prüfen. Vollständiger Funktionskatalog liegt nicht vor. | 5–10 PT Eignungs-/Umfangsprüfung; Gesamtaufwand offen |
| 4 | HOCH PEP4US: Zeitwirtschaft und Personalplanung | Zeitwirtschaftsbasis vorhanden, erheblicher Ausbau zur klinischen Personalplanung nötig. | Szenario 600–1.200 PT inklusive Reserve; Integrationsoptionen zusätzlich |

Die Reihenfolge bewertet die Zweckmässigkeit einer Bewerbung mit dem heutigen Chrono. Sie ist keine Bewertung des Marktvolumens oder der Zuschlagswahrscheinlichkeit; Budgets und Anbieterreferenzen sind nicht bekannt.

### Was die Schätzungen bedeuten

- 1 Personentag (PT) = 8 Arbeitsstunden. Gemeint ist Gesamtarbeit von Entwicklung, Fachkonzeption, Tests und Einführung; mehrere Personen reduzieren nicht jeden Zeitbedarf proportional.
- Annahmen: erfahrenes Team mit Kenntnis des Chrono-Codes, klar begrenzter Erstumfang, zeitnahe fachliche Entscheidungen, verfügbare Testhardware und dokumentierte Schnittstellen. Ein vorhandener, zertifizierter Fiskaldienst wird angebunden; keine eigene TSE entwickelt.
- Enthalten sind die unten genannten Anpassungen, Integrationstests und Pilotabnahme. Nicht enthalten: Einkauf von Geräten, externe Lizenzen, laufender Betrieb/Support, zusätzliche unbekannte Pflichtanforderungen oder aufwendige Bereinigung unbekannter Altdaten.
- Für Kirche und Waschstraßen sind dies frühe Arbeitsbandbreiten. Für PEP4US ist es nur ein Entwicklungsszenario ohne vollständigen Anforderungskatalog. Für Tessin ist eine Gesamtsumme ohne Unterlagen nicht seriös.
- Kalenderorientierung: Kirche mit einer überwiegend verfügbaren Entwicklungskraft plus fachlicher Unterstützung etwa 2–4 Monate, Waschstraßen etwa 4–8 Monate. PEP4US als Teamprojekt mit etwa 5–7 Personen aus Entwicklung, Fachplanung, QA und Einführung eher 12–24 Monate. Abhängigkeiten beim Auftraggeber können darüber hinausgehen.

## 1. Kirchliche Einrichtung – zuerst verfolgen

Quelle: [SoftGuide-Projekt 26/3587](https://www.softguide.de/ausschreibungen/kirchliche-einrichtung-sucht-software-fuer-raum-und-zimmerverwaltung). Genannt sind ein kleines Gästehaus mit Frühstück, vermietete Seminarräume, 4–8 Windows-Arbeitsplätze und die Plattform-Frist 10.10.2026. Onlinebuchung gehört zu den notwendigen Funktionen; Umbuchungen sind optional.

### Vorhandene Basis und Lücken

| Anforderung | Chrono heute | Noch erforderlich |
|---|---|---|
| Gästezimmer, mehrtägige Aufenthalte, Frühstück | Zimmerkategorien, Reservierungen, Preise und Frühstücksverbuchung vorhanden. | Stammdaten, Hausregeln und Rechnungsdarstellung einrichten. Eigenes Bad/Etagenbad zunächst über Kategorien/Beschreibungen abbilden. |
| Seminarraum- und Kalenderverwaltung | Interne Ressourcenbuchung mit Beginn/Ende, Kapazität und Kollisionsprüfung. | Bedienung und Vermietungsregeln für diese Einrichtung abstimmen. |
| Onlinebuchung | Öffentliche Übernachtungsbuchung vorhanden. | Öffentliche Seminarraum-Zeitfenster, Auswahl und Buchungsabschluss ergänzen. Annahme dieser Schätzung: Auch Seminarräume sollen online buchbar sein. |
| Rechnungen ohne Übernachtung | PMS-Rechnungen und archivierte PDFs vorhanden. Veranstaltungsabrechnung verlangt derzeit ein Gast-/Gruppenkonto mit Zimmerreservierung. | Eigenständige Seminarvermietung und Rechnung ohne künstliche Zimmerbuchung ermöglichen. |
| Reinigung, Aufgaben, Einsatzpläne | Housekeeping mit Zuweisung, Datum, Priorität und Status; separate Dienstplanung vorhanden. | Housekeeping von Zimmern auf Seminarräume und Gemeinschaftsflächen erweitern; Personalbedarf und Aufgaben mit Veranstaltungen verbinden. |
| Verträge | Versionierte Veranstaltungsangebote mit Bedingungen, öffentlicher Annahme und Nachweis vorhanden. | Mietvertragsvorlagen und gewünschte Änderungs-/Stornoprozesse einrichten. Eine qualifizierte elektronische Signatur ist daraus nicht abzuleiten. |
| E-Rechnung | UBL 2.1 und XSD-Prüfung vorhanden. | Empfängerformat vereinbaren und dessen fachliche Validierung/Übermittlung ergänzen. EN16931, XRechnung oder Peppol sind dadurch noch nicht nachgewiesen. |
| Umbuchung | Änderung von Reservierungen und Zimmerwechsel vorhanden. | Konkrete Abläufe mit dem Betreiber abnehmen. |

Die Ausschreibung nennt kein bestimmtes E-Rechnungsformat. XRechnung ist deshalb eine mögliche Umsetzung, keine nachgewiesene einzelne Mussvorgabe. Zur Abgrenzung von strukturierten Rechnungsformaten siehe die [BMF-Informationen zur E-Rechnung](https://www.bundesfinanzministerium.de/Content/DE/FAQ/e-rechnung.html).

### Aufwand

| Arbeitspaket | PT |
|---|---:|
| Einrichtung, Stammdaten und begrenzte Migration | 4–7 |
| Seminarbuchung online und eigenständige Veranstaltungsabrechnung | 10–18 |
| Reinigung gemeinsamer Flächen und Aufgaben-/Dienstplanverknüpfung | 5–9 |
| Mietvertragsvorlagen und Annahmeprozess | 3–5 |
| Ein vereinbartes E-Rechnungsformat, Validator und Versandweg | 6–12 |
| Integrationstest, fachliche Abnahme und Einführung | 7–14 |
| **Summe** | **35–65** |

Empfehlung: Als konkrete Produktanpassung verfolgen. Zuerst den Ablauf „Seminarraum ohne Übernachtung buchen, Vertrag annehmen, Reinigung zuweisen, Rechnung erzeugen“ als Vorführ- und Abnahmeszenario festlegen.

### Codebelege

- [Zimmerreservierung](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsOperationsService.java:591) und [Frühstück](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsOperationsService.java:1416).
- [Interne Raumbuchung](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsAdvancedService.java:206), [öffentliche Booking Engine](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/controller/pms/PmsPublicBookingController.java:27).
- [Veranstaltungsabrechnung und Gastkontobindung](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsEventOrderService.java:86).
- [Veranstaltungsangebote](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsEventOfferService.java:22), [Housekeeping](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsHousekeepingService.java:34).
- [UBL-Ausgabe](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsStructuredInvoiceService.java:31) und [dokumentierte Formatgrenzen](C:/Users/siefe/WebstormProjects/Chrono/docs/pms-commercial-automation-and-ubl.md:39).

## 2. Waschstraßen – machbar, aber kein fertiges Branchenprodukt

Quelle: [SoftGuide-Projekt 26/3602](https://www.softguide.de/ausschreibungen/fahrzeugdienstleister-waschstrassen-sucht-branchen-bzw-kassenloesung). Zwei Filialen zum Start, Windows, Waschkartenleser, Zeiterfassung und Kassenfunktionen; DATEV optional, Fingerprint bevorzugt. Plattform-Frist: 30.11.2026.

### Vorhandene Basis und Lücken

| Anforderung | Chrono heute | Noch erforderlich |
|---|---|---|
| Kunden, Rechnungen, Zahlungseingänge | CRM, Rechnungen/PDFs und Zahlungsbuchungen vorhanden. | Für den Waschbetrieb zusammenhängenden Ablauf herstellen. |
| Kassensoftware | Echte POS-Belege mit Positionen, Steuern, Barzahlung und externer Kartentransaktionsreferenz im PMS. | Hotelbindung lösen, Waschleistungen/Kundenbezug, Rabatte und belastbare Storno-/Erstattungsabläufe ergänzen. Eine gespeicherte Kartenreferenz steuert kein Zahlungsterminal. |
| Kundenbon und Quittungsdruck | POS-Historie vorhanden, kein fertiger kundenbezogener Bon-/Quittungsdruck gefunden. | Bonlayout, Druckeradapter und Zuordnung zum Kunden. |
| Kassenschublade | Kein Geräteadapter gefunden. | Unterstützte Geräte auswählen und integrieren. |
| Tagesabschluss und Statistik | Kassenschichten mit Kassenkennung, Soll-/Ist-Bestand und POS-Barumsätzen vorhanden; hotelbezogener Tagesabschluss. | Waschstraßenabschluss, Korrekturwege und Umsatzhitlisten nach Leistung/Kunde/Filiale ausarbeiten. |
| Waschkarten | NFC-Lese-/Schreibgrundlage für Zeiterfassung vorhanden. | Eigenständige Kundenkartenlogik. Schätzannahme: Guthaben oder Waschkontingente inklusive Aufladung/Verbrauch/Sperre; konkreter Kartentyp und Herstellerprotokoll offen. |
| Personalzeit und Anwesenheit | Stempeln, Zeitkorrekturen, Absenzen und Salden vorhanden. Offene Stempel sind im Backend erkennbar. | Aktuelles Anwesenheitsboard, Filial-/Terminalzuordnung und Standortrechte ergänzen. |
| Zentrale Daten, Mandanten, Windows | Firmenmodell, Rollen/Passwortschutz und zentraler Browserzugriff vorhanden. | Zwei konkrete Standorte konfigurieren und Hardware-/Netzwerkbetrieb testen. Keine unbegrenzte Skalierung aus dem Architekturtyp ableiten. |
| Fingerprint / DATEV | Keine entsprechende Implementierung gefunden. | Zusätzliche Hardwareintegration bzw. ein vereinbarter Export/Adapter. |

Für die beschriebene deutsche Kasse sind Fiskalisierung und Prüfexport ein zentraler offener Punkt: Im geprüften Code wurden keine TSE-/DSFinV-K-Adapter gefunden. Die [BMF-FAQ zu Kassensystemen](https://www.bundesfinanzministerium.de/Content/DE/FAQ/FAQ-steuergerechtigkeit-belegpflicht.html) erläutert die Anforderungen und grenzt auch Dienstleistungsautomaten ab. Der tatsächliche Kassenaufbau muss feststehen; nicht jede Waschautomaten-Konstellation ist gleich zu behandeln.

### Aufwand

| Arbeitspaket | PT |
|---|---:|
| POS für Waschleistungen: Kunden, Rabatte, Beleg-/Korrekturabläufe, Statistiken | 18–30 |
| Waschkartenlogik und ein dokumentierter Lesertyp | 12–25 |
| Anbindung eines Fiskaldienstes, DSFinV-K und Belegprüfung | 15–30 |
| Bondrucker, Kassenschublade, ein Zahlungsterminal-/Zahlungsablauf | 8–18 |
| Filialzuordnung, Standortrechte und Anwesenheitsübersicht | 7–12 |
| Migration, Geräte-/Integrationstests, Pilot in zwei Filialen und Einführung | 15–25 |
| **Summe** | **75–140** |
| Optional: ein DATEV-Exportformat mit fachlicher Abstimmung | +5–15 |
| Bevorzugte Variante: ein Fingerprint-Gerät mit dokumentiertem SDK | +5–12 |

Eine direkte DATEV-Onlineanbindung kann über dem einfachen Exportpaket liegen. Unbekannte proprietäre Waschkarten, zusätzliche Gerätemodelle oder geforderter Offline-Kassenbetrieb ändern den Umfang deutlich. Offlinebetrieb ist in der zugänglichen Beschreibung nicht als Muss genannt und nicht im Korridor enthalten.

Empfehlung: Nur verfolgen, wenn der Betreiber ein Anpassungsprojekt akzeptiert und das Budget dazu passt. Bei lediglich zwei Startarbeitsplätzen ist der wirtschaftliche Nutzen der Entwicklung ohne bekannte Budgetzusage offen.

### Codebelege

- [CRM](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/controller/crm/CrmController.java:61), [POS-Verarbeitung](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsExtensionsService.java:225).
- [POS-Eingabemodell ohne allgemeinen Kunden-/Rabattbezug](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/dto/pms/PmsExtensionsRequests.java:33), [Kassenschichten](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/pms/PmsOperationsService.java:1246).
- [Zeiterfassung](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/TimeTrackingService.java:160), [NFC-Hardwarezugriff](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/NfcService.java:12).
- [Stempelmodell ohne Filial-/Terminalfeld](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/entities/TimeTrackingEntry.java:23).

## 3. Tessiner Kantonsverwaltung – zuerst Teilnahmefähigkeit klären

Die [TenderLift-Seite](https://tenderlift.ch/de/ausschreibungen/tessin/concorso-283-per-l-acquisizione-e-la-personalizzazione-di-un-231087ae/) beschreibt Beschaffung, Einführung, Anpassung und Betrieb eines HR-Informationssystems. Die entscheidenden Bedingungen konnten zusätzlich in der öffentlichen [amtlichen SIMAP-Publikation, Projekt 42252](https://www.simap.ch/it/project-detail/231087ae-c0dd-4581-861f-baa85b0637b0) gelesen werden.

### Materielle Teilnahmehürden

- Handelsregistereintrag im Informatikbereich oder passenden Tätigkeitsgebiet seit mindestens drei Jahren.
- Verantwortliche Person mit Beschäftigungsgrad über 50 Prozent beim Anbieter, passender Informatikausbildung oder gleichwertiger/höherer Qualifikation und mindestens drei Jahren Umsetzungserfahrung.
- Die angebotene Lösung muss bereits am Markt und bei mindestens zwei Schweizer Kunden im Einsatz sein, die jeweils mindestens 500 Mitarbeitende damit verwalten (CI-03).
- Keine Bietergemeinschaften und keine Teilangebote. Subunternehmen sind nach den Bedingungen möglich. Verfahren/Angebot auf Italienisch; physische Einreichung.

**Falls Chrono die geforderten Produktreferenzen nicht bereits besitzt, ist das eine Hürde für ein eigenes Hauptangebot, die zusätzliche Entwicklung vor Fristablauf nicht beseitigt.** Ein Partner macht fehlende Referenzen der angebotenen Lösung nicht automatisch gültig. Eine Unterauftragnehmerrolle für abgegrenzte Arbeiten an einer qualifizierten Hauptlösung ist gesondert zu betrachten.

### Technischer Aufwand

Chrono besitzt Personalstammdaten, Zeitwirtschaft, Absenzen, Arbeitsmodelle und Dienstplanung. Daraus folgt keine vollständige Abdeckung der kantonalen HR-Prozesse. Der vollständige Leistungskatalog und die konkreten Schnittstellen liegen dieser Prüfung nicht vor.

Vor einem Implementierungsangebot wären zu prüfen bzw. voraussichtlich zu bearbeiten:

1. Pflichtprozessliste und Abgleich jedes Musskriteriums gegen eine nachweisbare Chrono-Funktion.
2. Zielorganisation, Personal-/Berechtigungsmodelle und Freigabeabläufe.
3. Italienische Bedienung, Dokumente und Support, soweit im Leistungskatalog gefordert; die allgemeine Chrono-Oberfläche enthält derzeit DE/EN-Übersetzungen.
4. Vorgesehene Personal-, Lohn-, Identitäts- und Archivschnittstellen; vorhandene Chrono-Endpunkte belegen keine Kompatibilität.
5. Migration, Performance mit den tatsächlichen Beständen, Betriebs-/Supportorganisation und Abnahme.

**5–10 PT** für Eignungsprüfung, vollständige Anforderungsmatrix, Schnittstelleninventar und ein belastbareres Umsetzungsszenario. **Eine Gesamtschätzung bleibt offen.** Ob es um begrenzte HR-Workflows oder eine weitreichende Personalsuite geht, ist aus der veröffentlichten Kurzbeschreibung nicht erkennbar. Die Softwarekategorie „Zeiterfassung und Personalverwaltung“ ersetzt den Funktionskatalog nicht.

Terminabweichung: TenderLift nennt 27.10.2026, 15:00; die amtliche SIMAP-Ansicht zeigt 16:00. Vor einer Einreichung den verbindlichen Zeitpunkt aus den vollständigen Unterlagen klären. Die öffentliche Fragerunde endet laut SIMAP am 25.09.2026. Keine Unterlagen angefordert oder Interessensanmeldung ausgelöst.

## 4. HOCH PEP4US – strategisch verwandt, heute grosser Produktabstand

Quelle: [öffentliche Cobrief-Beschreibung](https://app.cobrief.com/procurements/pep4us-strategische-weiterentwicklung-der-zeitwi-2026-08-04-529199) und [amtliche SIMAP-Publikation, Projekt 40315, Stand 04.08.2026](https://www.simap.ch/de/project-detail/a0b30852-0a0b-4591-b419-a0f822f27135). Gesucht wird eine organisationsübergreifende Zeitwirtschaft für rund 9.700 Mitarbeitende; etwa 6.400 davon benötigen Dienstplanung. Die amtliche Beschreibung verlangt auch eine separate Testumgebung.

### Vorhandene Basis und Lücken

| Thema | Chrono heute | Fehlender wesentlicher Ausbau |
|---|---|---|
| Zeiterfassung/Absenzen/Korrekturen | Browser und Android, Zeitkonten, Anträge und Standardreports. | Abnahme sämtlicher relevanter Spitalregeln und Anbindung vorhandener AZE-Terminals; NFC allein ist kein Kompatibilitätsnachweis. |
| Automatische Dienstplanung | Einfache automatische Belegung freier Schichttypen mit je einer Person; Urlaub, Sollzeiten und Überschneidungen berücksichtigt. | Personalbedarf pro Station/Qualifikation, Regeln für Schichtfolgen/Ruhezeiten, Optimierer und nachvollziehbare Konfliktbehandlung. Die aktuelle Automatik überspringt Feiertage. |
| Mitarbeiterbeteiligung | Absenzen und administrative Planungsregeln. | Dienstwünsche, Verfügbarkeiten, transparente Planung und Freigaben. |
| Springer-/Poolmanagement | Firmen-/Abteilungsgrundlage. | Organisationshierarchie, Qualifikationen, Poolmitgliedschaften und bereichsübergreifende Disposition. |
| Mehrfachanstellungen | Historie von Beschäftigungsmodellen. | Gleichzeitig gültige Verträge/Funktionen mit getrennten Pensen, Konten und konsolidierter Sicht. Historische Änderungen sind keine parallelen Anstellungen. |
| Integration und Reporting | APIs und feste Berichte. | Konkrete HR-/Identitäts-/Terminaladapter, Migration und bedarfsgerechte individuelle Auswertungen. |
| Betrieb | Backup-/Monitoring- und Deploymentgrundlagen. | Repräsentative Last-/Wiederanlauftests und Betriebsabnahme für diesen Personalbestand; Paging und Datenzugriffe im Zeitbereich überarbeiten. |

### Vorläufiges Aufwandsmodell

| Arbeitspaket | PT |
|---|---:|
| Fachkonzept, Prozessaufnahme, Regelkatalog und Abnahmeszenarien | 40–70 |
| Organisation, Qualifikationen und parallele Verträge/Zeitkonten | 60–120 |
| Bedarf, Optimierer, klinische Planungsregeln und Konflikterklärung | 130–250 |
| Mitarbeiterwünsche, Springerpool, Freigaben und App-Abläufe | 70–140 |
| Grundlegende Stammdaten-/Identitäts- und AZE-Anbindungen | 50–100 |
| Reports, Datenmigration und Abstimmung der Salden | 40–80 |
| Leistung, Berechtigungen und Betriebs-/Wiederanlaufnachweise | 70–140 |
| Pilot, Parallelbetrieb, Schulung und gestaffelter Rollout | 40–80 |
| Zwischensumme | 500–980 |
| **Planungskorridor mit rund 20 Prozent Reserve, gerundet** | **600–1.200** |

Zusätzlich als eigene Optionen kalkulieren: **SAP-HCM-OM-Import und IKM-Anbindung zusammen vorläufig 25–70 PT**, bei dokumentierten Schnittstellen. Die amtliche Bekanntmachung bezeichnet diese als separat zu offerierende Optionen; Umsetzung nur bei Beauftragung. Das öffentliche Zielbild erwähnt IKM zwar, daraus darf man es nicht als bereits beauftragten Grundumfang zählen. Ferner nennt die Bekanntmachung zwei optionale Stundenpools von je 200 Stunden für Projekt und jährliche zusätzliche Arbeiten im Betrieb. Diese entsprechen jeweils 25 PT und sind keine vorhandene Chrono-Funktion; den Betriebsstundenpool nicht nur einmal über die Vertragslaufzeit rechnen.

Unbekannte Pflichtanforderungen aus den nicht gelesenen Lastenheften können den Korridor wesentlich erhöhen. Laufender Betrieb und Betreuung sind separat zu kalkulieren. SIMAP nennt für die Ausführung 01.03.2027–30.04.2029 und für die Kalkulation der Lizenzen/Gebühren eine Laufzeit bis 31.12.2033. Der Ausführungsbeginn ist kein nachgewiesener Termin für einen vollständigen Go-live.

### Vergabe- und Referenzlage

Die aktuelle öffentliche SIMAP-Ansicht nennt **25.09.2026, 11:00** und digitale Einreichung über SIMAP; die Fragenfrist 13.08.2026 ist bereits abgelaufen. Sie lässt Subunternehmen zu, aber keine Bietergemeinschaften und keine Teilangebote. Sub-Subunternehmen sind nicht zugelassen. Die [Berichtigung vom 21.07.2026](https://www.simap.ch/de/project-detail/a0b30852-0a0b-4591-b419-a0f822f27135/738c8698-4af1-4a13-a34b-a60dfedcdacd/past) verweist auf ein angepasstes Kriterium EK2 „Schweizer Referenz“; dessen konkrete Schwellen wurden ohne vollständiges A102/Frageforum nicht verifiziert. Aus „keine Kriterien gefunden“ auf einer Drittplattform darf deshalb keine freie Teilnahmefähigkeit abgeleitet werden.

Empfehlung: Heute kein vollständig passendes Standardprodukt zusagen. Als eigenständige Gesamtlieferung ist der Umfang sehr gross; eine klar definierte Unterauftragnehmerrolle bei einer erfahrenen Hauptanbieterin wäre eher prüfbar. Das wäre eine andere Lieferrolle und ersetzt keine vollständige Chrono-Abdeckung.

### Codebelege

- [Aktuelle Planungsautomatik](C:/Users/siefe/WebstormProjects/Chrono/Chrono-frontend/src/pages/AdminSchedulePlanner/AdminSchedulePlannerPage.jsx:198), [serverseitige Prüfungen](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/controller/AdminScheduleEntryController.java:179).
- [Einfaches Schichtmodell](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/entities/ScheduleRule.java:13), [Beschäftigungshistorie](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/services/EmploymentModelHistoryService.java:36).
- [Android-Stempeln](C:/Users/siefe/WebstormProjects/Chrono/Chrono-android/app/src/main/java/ch/chronologisch/chrono/data/TimeTrackingRepository.kt:61), [Standardreports](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/controller/ReportController.java:131).
- [Zeitübersicht mit Laden vollständiger Historien](C:/Users/siefe/WebstormProjects/Chrono/Chrono-backend/src/main/java/com/chrono/chrono/controller/AdminTimeTrackingController.java:61), [Grenzen bestehender Hotel-Lastnachweise](C:/Users/siefe/WebstormProjects/Chrono/docs/operations/PMS_STAFF_LOAD.md:30).

## Gemeinsame Arbeiten und nächste Entscheidung

Bei jedem Projekt bleiben konkrete Stammdaten/Migration, Rollen, benötigte Schnittstellen, Installation, Backup/Wiederherstellung, fachliche Abnahme und Einführung zu leisten. Die dafür veranschlagte Arbeit ist in den jeweiligen Projektpaketen enthalten und nicht ein zweites Mal pauschal zu addieren. Bei mehreren Umsetzungen lassen sich z.B. E-Rechnung, Standortmodelle oder Betriebsverbesserungen wiederverwenden; die vier Gesamtsummen sind deshalb keine addierbare Produkt-Roadmap.

Sinnvolle Reihenfolge:

1. Kirchliche Einrichtung: Anforderungen an Online-Seminarräume, eigenständige Rechnungen und E-Rechnungsformat konkretisieren; darauf Pilot und Angebot begrenzen.
2. Waschstraßen: Budgetbereitschaft, Karten-/Gerätetypen und bestehende Kassenpartner klären, bevor eine grössere Entwicklung zugesagt wird.
3. Tessin: Produktreferenzen und Anbieterbedingungen prüfen, erst danach die vollständigen Unterlagen auswerten und Aufwand kalkulieren.
4. PEP4US: Nur bei passender Anbieter-/Referenzlage und ausreichendem Team oder in einer zulässigen Unterauftragnehmerrolle weiterverfolgen.

Die genannten SoftGuide-Termine sind Plattform-/Recherchefristen. Angebotsfristen und Vertragsbeginn bei öffentlichen Vergaben sind keine Zusage, dass sämtliche Entwicklung bis dahin abgeschlossen werden muss oder darf; massgebend ist der konkrete Projektplan in den Vergabeunterlagen.
