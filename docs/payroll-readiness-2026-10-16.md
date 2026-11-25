# Steuer- und Überstundenlogik – Prüfstand 16. Oktober 2026

Historischer Zwischenstand (v2). Die anschließend umgesetzten Jahresmodelle, Teilmonate, 13. Monatslöhne und Rückrechnungen sowie die erneute Prüfung sind im [Prüfstand vom 20. Oktober 2026](payroll-readiness-2026-10-20.md) beschrieben. Die unten aufgeführten offenen Punkte und Testzahlen beziehen sich auf den damaligen Zwischenstand.

Status: lokal umgesetzt und geprüft für den beschriebenen Standardfall; **keine vollständige fachliche Produktivfreigabe für alle Lohnfälle**. Kein Rollout und keine Änderung bestehender Live-Abrechnungen. Dieser Bericht aktualisiert den Stand vom 15. Oktober.

## Umgesetzt

- Versionierte persönliche Lohn-, Steuer- und Überstundenparameter; datierte Firmenversicherungen und individuelle BVG-Beiträge. Fehlend und ausdrücklich null bleiben unterscheidbar. Neue Versionen überschreiben weder alte Parameter noch Belege.
- Deutsche Lohnsteuer nach originalem BMF-PAP 2026, gegen 15 unabhängige amtliche Programmtestfälle geprüft. Reguläre Sozialbeiträge berücksichtigen Jahresgrenzen, Zusatzbeitrag, Sachsen und Elterneigenschaft.
- Schweizer Quellensteuer aus dem amtlichen ESTV-Archiv 2026 für 21 Monatsmodell-Kantone mit Tarif-/Bandprüfung und Fünfrappenrundung. Keine erfundene 10%-Quellensteuer und kein zusätzlicher ordentlicher Einkommensteuerabzug. AHV/ALV, NBU-Grenze, vertragliche KTG-Grenze, FAK, Verwaltungskosten und persönliche BVG-Beträge sind berücksichtigt.
- Die pauschale 160-Stunden-Grenze und separate rohe Stempelpaarung sind entfernt. Brutto und Vorprüfung verwenden kanonische Zeitdaten. Stundenlohn bezahlt Arbeitszeit einmal; Monatslohn zahlt das Zeitkonto ausdrücklich aus. Vertragszuschläge und ganze Minuten ersetzen pauschale Annahmen.
- Auszahlung prüft historischen und aktuell verfügbaren Saldo, kumulierte Sammelfreigaben, offene Stempel und widersprüchliche Arbeitszeitmodelle. Prozentmodelle verwenden dieselbe Wochenrundung wie das Zeitkonto. Regelwechsel innerhalb einer über Monatsgrenzen laufenden Stundenlohnwoche werden abgefangen.
- Neue Entwürfe speichern Berechnungsversion, Parameter, Zeitraum, Zeitdaten, Beiträge, Tarifreferenz und Währung. Freigabe rechnet erneut und vergleicht den Nachweis. Geänderte Grundlagen verlangen einen neuen Entwurf. Alte Entwürfe werden nicht still neu berechnet.
- Genehmigte Belege können nicht zurückgezogen, gelöscht oder durch ein anderes Auszahlungsdatum verändert werden. Parallelfreigaben sperren Personen und aktualisieren vorher geladene Entitäten. Genehmigte Altbeträge werden nicht nachträglich verändert.
- Neue PDFs verwenden gespeicherte Vertragsdaten, Steuermerkmale, Bank-/Sozialversicherungsdaten und Währung. Aktuelle Zeit-/Feriensalden werden nicht als historische Salden ausgegeben. Gespeicherte PDFs bleiben erhalten.
- Zusätzlich behoben: gleichzeitige Erstabfragen im Feiertags-Cache, Seitenrechte der neuen Konfiguration und Fehlerweitergabe bei Benachrichtigung nach bereits gespeichertem Beleg.

## Prüfungen

- Gesamtes Backend: **884 erfolgreich, 2 übersprungen (886 insgesamt), 0 Fehler**. Danach 18 erfolgreiche Guard-/Nebenläufigkeitstests mit zwei neuen Grenzfällen, 18 erfolgreiche PDF-/Service-/Berechnungstests mit einem neuen PDF-Test und ein erfolgreicher PDF-Test nach der letzten Layoutkorrektur. Logs: `output/chrono-tax-full-backend-final.log`, `output/chrono-tax-final-guards.log`, `output/chrono-tax-pdf-final.log`, `output/chrono-tax-pdf-layout-final.log`.
- Der ausgelassene MySQL-Test wurde separat unter **MySQL 8.0.44** erfolgreich ausgeführt: leeres Schema, Legacy-Schema, unveränderte Altbeträge/Freigaben, wiederholter Migrationslauf. Log: `output/chrono-tax-mysql-migration-final.log`. Der externe Backup-Test bleibt ausgelassen.
- Gesamtes Frontend: **673/673 erfolgreich**. Nach der letzten Textkorrektur nochmals **21/21 Tests** der betroffenen Formular-/Abrechnungsdateien erfolgreich (`output/chrono-tax-ui-last.log`). Produktionsbuild erfolgreich (`output/chrono-tax-ui-final.log`); dieser frühere Lauf enthält auch eine inzwischen korrigierte Testtext-Erwartung. Gesamtlauf: `output/chrono-tax-full-frontend-final.log`.
- Browser mit eigener lokaler MySQL-Testfirma: Versicherungen, BVG, persönliche Quellensteuerregeln speichern; Januar vorprüfen, erstellen, freigeben und PDF öffnen. Synthetischer Sollfall: **CHF 5'000.00 brutto, CHF 906.50 Abzüge, CHF 4'093.50 netto, CHF 818.55 Arbeitgeberbeiträge**. API, Anzeige und direkte SQL-Abfrage stimmen überein.
- Nachträgliche Versicherungsänderung: Februarentwurf wird bei Freigabe mit HTTP 409 und verständlicher Meldung zurückgewiesen; Detailansicht bleibt offen. Alte Entwürfe und Wiederöffnung genehmigter Altbelege ebenfalls mit HTTP 409 gesperrt.
- Acht lokale Altbelege vor/nach neuen Buchungen verglichen: Beträge, Zeiträume, Freigabestatus, Währung und Snapshot-Hash unverändert. Synthetische Demobelege sind keine fachlich geprüften Kundenreferenzen.
- PDF gerendert/visuell geprüft, einschließlich letzter Layoutkorrektur (`output/playwright/payroll-layout-final-1.png`); PDF-Texttest prüft gespeicherte Daten trotz abweichender aktueller Benutzerfelder. Browserartefakte unter `output/playwright/`. Kein Versand echter Lohndaten an BMF oder andere Testdienste. Alle übrigen Chrono-Module wurden nicht vollständig manuell durchgeklickt.
- Deutsches Steuerformular auf 390 Pixeln Breite geprüft: alle Felder bedienbar, kein horizontaler Seitenüberlauf. Zwei Fehler der Testumgebung (gesperrte öffentliche Demo und gleichzeitige Neukompilierung während eines Browseraufrufs) wurden durch eine eigene lokale Testfirma und einen stabilen Neustart beseitigt; die betroffenen Abläufe wurden erfolgreich wiederholt. Der fehlende lokale Changelog liefert weiterhin 404 und gehört nicht zu dieser Abrechnungsprüfung.

## Noch vor einer vollständigen Freigabe erforderlich

Die Automatik ist auf volle Kalendermonate 2026, reguläre monatliche Einzelarbeitsverhältnisse und die ausdrücklich erfassten Vertragswerte begrenzt. Offen sind insbesondere Jahresmodell-Quellensteuer (FR/GE/TI/VD/VS), kumulative Jahresgrundlagen bei schwankendem Lohn über Versicherungsgrenzen, Teilmonate/Ein- und Austritte, 13. Monatslohn/Einmalzahlungen, Stundenlohn-Ferien-/Feiertags- und sonstige Abwesenheitsentschädigung, Mehrfachbeschäftigung, Alters-/Sonderversicherungsfälle sowie Storno und Rückrechnung. Mini-/Midijobs werden gesperrt; besondere Kirchensteuerregeln sind nicht implementiert. Die Maske benennt den Standardfall; unbekannte Vertragswerte werden nicht ergänzt.

Die zuvor nur lesend festgestellten Live-Auffälligkeiten bleiben offen: negativer Nettolohn, Summenabweichungen und überlappende genehmigte Zeiträume. Sie wurden nicht automatisch korrigiert. Neue Prüfungen machen alte Beträge nicht nachträglich richtig. Korrekturen müssen ursprünglichen Zeitraum, Auszahlung und Differenz separat nachvollziehbar zuordnen.

Quellen, Hashes, Reproduktion und Grenzen: [payroll/README.md](../Chrono-backend/src/main/resources/payroll/README.md). Technische Tests sind keine Swissdec-Zertifizierung und keine vollständige rechtliche Bestätigung sämtlicher Lohnfälle.
