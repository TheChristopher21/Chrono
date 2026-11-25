# Abrechnung und Praxis-Stundenvergleich – 2. November 2026

Lokaler Stand `chrono-payroll-2026-v5`. Kein Deployment, keine Migration und keine Datenänderung auf dem Live-Server. Nach der Klarstellung des Betreibers werden die bestehenden, bisher zum Stundenabbau verwendeten Abrechnungen nicht nachträglich fachlich korrigiert oder als geprüfte Lohnhistorie übernommen.

## Korrektur für neue Abrechnungen

Die Prüfung von Überstundenauszahlungen bei Prozentanstellungen verwendete fälschlich eine 40-Stunden-Basis. Sie verwendet jetzt dieselbe 42,5-Stunden-Basis wie das bestehende Arbeitszeitmodell. 80 % entsprechen vertraglich 34 Stunden, 33 % entsprechen 14,025 Stunden. Vertragliche Monatslohn-Wochenstunden werden nicht auf ganze Minuten oder gerundete Tagessummen verändert. Die bestehende Minutenrundung des Zeitkontos bleibt erhalten. Bei Stundenlohn bleibt die wochenbezogene Zuschlagsgrenze minutengenau.

Das Formular erlaubt vier Nachkommastellen und erklärt die Prozentbasis. Die Bruttoberechnung wendet die ganzzahlige Wochen-Zuschlagsgrenze nur beim Stundenlohn an. Neue Berechnungen tragen v5; genehmigte ältere Belege und ihre Snapshots bleiben erhalten. Für diese Änderungen gibt es keine weitere Schemaänderung.

## Lesender Praxis-Abgleich

- Live-Code laut Server: `f13d7c44`; Datenstichtag 2026-11-02, konsistenter Export um 17:27:17 UTC.
- 18 Konten, 3'870 Stempel, 25 genehmigte Abrechnungen; zusätzlich die für Stunden relevanten Modellhistorien, Abwesenheiten, Feiertagsentscheidungen und Arbeitstagstausche.
- Minimierter Export ohne Namen, Benutzernamen, Löhne, Bankdaten, Patienten, Freitextnotizen oder Zugangsdaten. Einzelwerte bleiben unter dem gitignorierten Verzeichnis `data/audit-practice-20261102/`.
- Ausschließlich SELECT in ausdrücklich schreibgeschützten MySQL-Transaktionen. Die anschließende Neuberechnung erfolgt offline mit den echten Services und ausschließlich lokalen In-Memory-Repository-Mocks. Kein Spring-Kontext, keine Verbindung der neuen Anwendung zur Live-Datenbank.
- Zusätzlich wurde der `TimeTrackingService` des tatsächlichen Live-Commits lokal kompiliert und mit exakt denselben Daten/Stichtag ausgeführt. Die unveränderten Abhängigkeiten bzw. ihre verhaltensgleiche Datumserweiterung wurden im Diff geprüft.
- **Alle 18 neu berechneten Salden, Ist-/Soll-Gesamtsummen und Listen auffälliger Tage sind zwischen Live-Code und v5 identisch.** Der opt-in Replay-Test vergleicht die vollständigen Ergebniszeilen automatisch.
- Sieben nicht gelöschte Konten mit aktuellen Stempeln stimmen zusätzlich minutengenau mit dem gespeicherten Live-Saldo überein. Bei zwei Administrationskonten ohne aktuelle Stempel und sieben gelöschten Konten weicht eine Neuberechnung vom gespeicherten Saldo ab. Beide Codeversionen liefern dieselbe Abweichung; dies ist kein durch v5 eingeführter Unterschied. Ohne verlässlichen früheren Berechnungsstichtag ist eine reine Alterungserklärung nicht abschließend belegt.
- Drei aktive Konten enthalten zusammen sieben als korrekturbedürftig markierte Tage. Ein gleicher Saldo bestätigt diese Stempel nicht fachlich. Vor einer neuen Auszahlung müssen die betreffenden Tage geklärt werden; sie wurden nicht verändert.
- Zweiter lesender Abruf: alle 25 bisherigen Stundenabbuchungen hinsichtlich ID, Personenzuordnung, Zeitraum, Freigabe, Auszahlungsdatum, Auszahlungsflag und Stunden unverändert. Vollständige Lohnbeträge wurden nicht exportiert und nicht erneut geprüft.

Private Ergebnisdatei: `data/audit-practice-20261102/vergleich.md`. Eingabe-SHA-256: `a1f8fafb7f0e6195723f59fb20143240213360ebe923954f6b3a87c2535862a9`.

## Prüfungen

- Gesamtes Backend: **948 bestanden, 1 übersprungen, 0 Fehler; 949 insgesamt**. Enthalten: Praxis-Replay, drei MySQL-Paralleltests sowie Migrationen auf frischen und Legacy-Schemas mit erhaltenen Altbeträgen. Log: `output/payroll-v5-backend-full-final.log`.
- Gesamtes Frontend: **682 Tests in 99 Dateien bestanden**; Produktionsbuild erfolgreich. Logs: `output/payroll-v5-frontend.log`, `output/payroll-v5-build.log`.
- Neue Fälle: 100/80/33/15-Prozent-Verträge, Ablehnung der alten 40-Stunden-Annahme und eines Nullpensums, keine automatische Auszahlung bei Monatslohn, ganzzahlige Zuschlagsgrenze bei Stundenlohn, unveränderte Speicherung von 14,025 Wochenstunden.
- Browserprüfung mit Playwright/Edge gegen eine wiederhergestellte lokale Testdatenbank: Regel mit 14,025 Stunden gespeichert, Erfolgsmeldung in der Oberfläche, exakter Wert `14.025` in MySQL. Mobile Darstellung bei 390 Pixeln visuell geprüft. Screenshot: `output/playwright/v5-weekly-hours-mobile.png`. Die bekannte lokale Changelog-Route liefert weiterhin 404; die Lohnregel wurde erfolgreich gespeichert.
- Alle **16 vorhandenen lokalen Testabrechnungen samt Verdienst-/Abzugs-/Arbeitgeberpositionen** nach dem Browserlauf exakt unverändert, durch identische deterministische SQL-Exporte bestätigt.
- Lokaler Backup-/Restore-Test des Schemas 53 mit synthetischen Daten: Originaldump und Dump nach Wiederherstellung byteidentisch, SHA-256 `ea071909825782cc2a1dbf474cb580b8137d88afb3bd7a2c32450cb6dde733ec`. Dies ist kein neu angestoßener Live-Restore.
- Vorhandenen Live-Restore-Nachweis nur gelesen: Status OK vom 2026-11-02, 02:28:57 UTC, Live-Schema 50, 156 Tabellen. Dieser Nachweis betrifft die laufende Version, nicht die noch unveröffentlichte neue Version. Der externe Backup-Test der Java-Suite blieb übersprungen.

Die ersten Gesamtversuche waren durch eine temporäre Vergleichsklasse im Spring-Test-Scan und einen unter Windows zerlegten Testaufruf blockiert. Nach Entfernung der temporären Klasse und Korrektur des Aufrufs lief die vollständige Suite erfolgreich. Die Vergleichsklasse gehört nicht zum Produkt oder zu den eingecheckten Testquellen.

## Verbleibende Grenzen und Eingaben

1. **2027 bleibt gesperrt.** Die ESTV kündigt die vollständigen 2027-Tarifdateien voraussichtlich für Dezember 2026 an; das 2027-Recordformat enthält neue Tessiner Tarife. Die BMF-XML-Veröffentlichung bietet am Prüfdatum 2026/2025, noch keinen 2027-PAP. Veröffentlichung, Import, unabhängige Referenzfälle und neue Berechnungsversion sind noch erforderlich. 2026-Werte werden nicht auf 2027 übertragen. Quellen: [ESTV](https://www.estv.admin.ch/de/quellensteuer), [BMF-PAP](https://www.bmf-steuerrechner.de/interface/pseudocodes.xhtml).
2. **Vertragswerte sind firmenspezifische Eingaben.** BU/NBU, KTG, FAK und die persönlichen BVG-Beiträge können bereits mit Gültigkeitsdatum erfasst werden. Fehlende Werte werden nicht geschätzt.
3. **Alte Stundenabbau-Belege bleiben unverändert.** Sie werden nicht als verlässliche steuerliche Jahresgrundlage behandelt. Für eine neue echte Lohnabrechnung mitten im Jahr bleiben vollständige Jahresgrundlagen erforderlich. Eine separate Übernahme geprüfter Eröffnungswerte ist noch nicht implementiert. Die Klarstellung des Betreibers ist keine Erlaubnis, fehlende steuerliche Vorperioden auf null zu setzen.
4. **Sonderfälle sind weiterhin nicht vollständig implementiert:** jahresübergreifende Rückrechnungen, getrennte Zuordnung bei Quellensteuerkantons-/Statuswechseln oder unterjährigem KTG-Cap-Wechsel, Stundenlohn-Abwesenheitsentschädigungen und Mehrfachbeschäftigung. Die bestehenden Sperren/Umfangsbestätigungen bleiben bestehen. Dieser Durchlauf schließt diese fachlichen Erweiterungen nicht ab; siehe [Details des unterstützten Umfangs](../Chrono-backend/src/main/resources/payroll/README.md).

Damit sind Stundenvergleich und die hier beschriebene v5-Korrektur geprüft. Es besteht keine pauschale Freigabe aller Lohnfälle und keine Freigabe zum Deployment.
