# Abrechnungslogik – Prüfstand 20. Oktober 2026

Berechnungsversion `chrono-payroll-2026-v4`; begonnen am 20. Oktober, Abschlussprüfung in der Nacht zum 21. Oktober 2026. Die Arbeiten wurden nach dem Stopp lokal fortgesetzt. Es wurde nichts veröffentlicht und keine Live-Datenbank verändert. Dieser Bericht ersetzt den damaligen v2-Zwischenstand vom 16. Oktober; er ist keine Freigabe sämtlicher denkbarer Lohnfälle.

## Umgesetzter Umfang

- Schweizer Jahresmodell-Quellensteuer für FR, GE, TI, VD und VS mit vollständiger freigegebener Jahresgeschichte, Lohnprognose, historischen Tarifmerkmalen und laufendem Jahresausgleich. Gleiche Tarifbasen werden vor der Rundung zusammengefasst. Die tatsächlichen gerundeten Zahlungen bestimmen den Abschluss bei Austritt/Jahresende.
- Teilmonate bei Ein-/Austritt: ausdrücklich vereinbarte Kalender- oder 30-Tage-Lohnkürzung, eigenständige steuerliche/versicherungsrechtliche Tageszählung und explizite BVG-Kürzung. Ein bekannter zukünftiger Austrittsmonat wird auch in der CH-/DE-Jahresprognose vertragsgemäß gekürzt.
- 13. Monatslohn: kumulierter Anspruch auf ein Zwölftel des berechtigten Grundlohns, monatliche/quartalsweise/halbjährliche/jährliche Auszahlung oder Austrittsabrechnung. Bereits bezahlte Teilbeträge werden abgezogen. Offene Ansprüche dürfen durch einen Wechsel auf keinen 13. Monatslohn nicht verschwinden.
- Rückrechnungen: getrennte, vorzeichenbehaftete Differenzbelege für den Originalmonat und die betroffenen freigegebenen Folgemonate. Die gesamte Kette wird gemeinsam geprüft/freigegeben. Originalbeträge, frühere Korrekturen und gespeicherte PDFs bleiben erhalten. Rückforderungen und die Rücknahme bezahlter Überstunden sind abbildbar.
- Parallele Freigaben: aktuelle gesperrte Abrechnungshistorie auch unter MySQL REPEATABLE READ. Eine Transaktion, die auf die Mitarbeitersperre warten musste, darf keine inzwischen freigegebene Korrektur oder Folgemonatsabrechnung übersehen.
- Überstundenkorrekturen bleiben dem Kontoabschnitt des Originalbelegs zugeordnet. Eine Rücknahme aus einem früheren Abschnitt kann keine neue Auszahlung nach einem Wechsel des Arbeitszeitmodells finanzieren; dies wird auch innerhalb noch nicht freigegebener Korrekturketten geprüft.
- KTG-Jahreslohngrenze wird zusätzlich im Berechnungsnachweis gespeichert. Alte Nachweise bleiben lesbar. Fehlende verlässliche Grundlagen werden nicht durch Standardwerte ersetzt.

## Schutz bestehender Belege

Neue Berechnungen erhalten v4; ältere offene Entwürfe müssen ausdrücklich neu erstellt werden. Genehmigte Belege werden nicht automatisch auf v4 umgerechnet. Eine Korrektur übernimmt die freigegebene vorherige Revision als nachprüfbare Basis und erzeugt einen neuen Differenzbeleg. Die bestehenden additiven Migrationen und nicht kaskadierenden Referenzen bleiben erhalten; für v4 ist keine weitere Schemaänderung nötig.

## Prüfungen

- Gezielte Backend-Prüfung: 170 fachliche/Service-/Zeitkonto-Tests erfolgreich. Die drei zusätzlichen MySQL-Paralleltests waren zunächst durch einen falsch geerbten H2-Testdialekt blockiert; nach Korrektur der Testkonfiguration ebenfalls **3/3 erfolgreich**, einschließlich konkurrierender Revision, neu freigegebenem Folgemonat und wartender Korrekturerstellung. Logs: `output/chrono-payroll-v4-targeted.log` und `output/chrono-payroll-v4-mysql-concurrency.log`.
- Gesamtes Frontend: **677/677 Tests** in 98 Dateien erfolgreich; Produktionsbuild erfolgreich. Logs: `output/chrono-payroll-v4-frontend.log` und `output/chrono-payroll-v4-build.log`.
- Gesamtes Backend: **939 erfolgreich, 1 übersprungen, 0 Fehler (940 insgesamt)**. Enthalten sind die drei echten MySQL-Paralleltests und Migrationen auf einem leeren sowie einem Legacy-Schema mit unveränderten Altbeträgen. Nur der externe Backup-Prüffall blieb übersprungen. Log: `output/chrono-payroll-v4-backend-full.log`.
- Erneuter Browserlauf unter Edge mit lokalem MySQL: alte Belege und freigegebene v3-Korrekturen geöffnet; v4-Rückrechnung für Januar/Februar erstellt und beide Belege gemeinsam freigegeben (HTTP 200). Bei unveränderten Vertragsdaten erwartungsgemäß kein weiterer Betrag: neue Belege #15/#16 jeweils brutto/netto CHF 0.00, korrekte Vorgänger #13/#14, originale Zuordnung #11/#12. Neuer Gesamtstand Januar CHF 3'250.00 brutto und CHF 2'492.10 netto.
- PDF des neuen Belegs über die Oberfläche geöffnet: HTTP 200, `application/pdf`, 360'604 Bytes. PDF-Inhaltstests waren auch im vollständigen Backend-Lauf erfolgreich. Die bereits bekannte lokale Changelog-Route liefert 404; sie gehört nicht zur Abrechnungslogik. Die während des noch gestoppten Testbackends protokollierten Analytics-Verbindungsfehler waren keine fehlgeschlagenen Payroll-Aufrufe.
- Direkter SQL-Vergleich aller **14 vorher vorhandenen Testbelege**: Beträge, Zeitraum, Freigabestatus, Währung, Berechnungsversion, Snapshot-Hash, PDF-Pfad und Korrekturverweise unverändert. Vergleich: `output/playwright/v4-before.tsv` / `v4-after.tsv`. Browsernachweise: `v4-original.yml`, `v4-correction-review.yml`, `v4-approved.yml` im selben Ordner. Synthetische Testdaten sind keine fachlich geprüften Kundenabrechnungen.

Bereits vor dem Stopp am 16. Oktober wurden die neuen Eingabemasken und vollständige Abläufe lokal mit synthetischen Firmen geprüft: Vertrags-/Versicherungswerte, Teilmonatsabrechnungen, monatlicher 13. Monatslohn, Erstellung/Freigabe einer zweimonatigen Rückrechnung, PDF sowie mobile Ansicht mit 390 Pixeln Breite. API, Oberfläche und direkte SQL-Abfrage stimmten überein; die zuvor vorhandenen Belege blieben unverändert. Diese früheren Browsernachweise liegen unter `output/playwright/v3-*` und werden von den erneuten v4-Prüfungen getrennt betrachtet.

## Bewusste Grenzen

Unterstützt sind die ausdrücklich bestätigten regulären Einzelarbeitsverhältnisse 2026 und eingegebenen Vertragswerte. Gesperrt bleiben fehlende Jahresgrundlagen, überlappende Originalzeiträume, Quellensteuerkantons-/Statuswechsel ohne getrennte Jahreszuordnung, unterjährige KTG-Cap-Wechsel ohne Versicherungsaufteilung und Jahreswechsel bei Rückrechnungen. Deutsche Korrekturen mit geändertem nichtmonatlichem 13. Monatslohn und anderem Auszahlungsmonat benötigen die Grundlagen des tatsächlichen Zuflussmonats und werden ebenfalls gesperrt.

Andere Bonusgrundlagen, Abwesenheits-/Ferienentschädigung bei Stundenlohn, Mehrfachbeschäftigung, Alters-/Sonderversicherungsfälle und Vorjahreszuordnungen sind nicht pauschal abgedeckt. Vertragliche Versicherungswerte müssen jede Firma und die individuellen BVG-Beiträge die zuständige Stelle selbst erfassen. Früher festgestellte Live-Auffälligkeiten wurden nicht automatisch korrigiert.

Amtliche Quellen, Versionshashes und Reproduktion: [Payroll-Dokumentation](../Chrono-backend/src/main/resources/payroll/README.md). Die unabhängigen Beispiele stammen aus ESTV-/Kantonstabellen und dem BMF-Programmtest mit synthetischen Zahlen. Technische Tests ersetzen keine Swissdec-Zertifizierung oder Abnahme aller individuellen Firmenverträge.
