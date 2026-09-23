# Separate Admin-Arbeitsübersicht

Die neue Oberfläche liegt unter `/admin/dashboard-neu`. Der Einstieg „Neues Dashboard“ erscheint für `ROLE_SUPERADMIN` im Plattformmenü und im Arbeitsbereich-Launcher. Die direkte Route, die Mitarbeiter-Unterroute und wiederhergestellte Arbeitsbereiche prüfen dieselbe Rolle. Eine individuelle Seitenfreigabe ersetzt diese Rollenprüfung nicht.

`/admin/dashboard` bleibt die bisherige Oberfläche und das Standardziel. Die neue Variante verwendet die vorhandenen API-Endpunkte und Fachkomponenten; der Entwurf enthält keine Beispieldaten in der Produktimplementierung.

## Funktionszuordnung

| Bereich | Neue Arbeitsübersicht |
| --- | --- |
| Übersicht | Vier klickbare Kennzahlen: offene Anträge, Personen mit Zeitproblemen, heute Abwesende und negative Zeitkonten. Datenstand und tatsächlicher Prüfzeitraum werden ausgewiesen. Priorisierte Aufgaben, Teamabwesenheiten und Salden sind direkt erreichbar. |
| Offene Aufgaben | Gemeinsame Liste aus Korrekturgruppen, Urlaubsanträgen und den bereits berechneten Zeitproblemen. Je nach Bildschirmhöhe drei bis fünf Vorschauaufgaben, vollständige Liste und direkte Prüfung. Zeitnahe Urlaube zuerst, danach betroffene Tage. Offene Entscheidungsformulare bleiben beim Größenwechsel erhalten. |
| Freigaben | Aufklappbare Änderungen und Entscheidungsnotiz. Korrekturen werden entsprechend dem Backend nach Person und gewünschtem Stempeltag zusammengefasst, mit allen Begründungen und Änderungen. Eine Freigabe ersetzt die bisherigen Tagesstempel durch die beantragten Stempel; Ablehnungen erfolgen pro ID. Bei Teilfehlern bleiben nur gescheiterte Änderungen offen. |
| Team | Abteilungsfilter (`departmentName`), heutige und kommende Abwesenheiten, Zeitkonten; Details öffnen den vorhandenen Kalender. |
| Zeitprüfung | Bestehende Wochen-/Monatsansicht, Zeitraumgrenzen, Suche, Problemfilter, Sortierung, Tageskorrekturen, Feiertagsentscheidungen, Arbeitstagtausch, Krankmeldungen und Druck. |
| Antragscenter | Gemeinsame chronologische Liste mit Detailprüfung daneben. Typ-, Status-, Personen- und Datumsfilter, auf-/absteigende Sortierung, vollständige Korrekturgruppen, Notizen, Historie, Teilfehler-Wiederholung, Urlaubsrestkonto, Teamüberschneidungen und bestätigte Urlaubslöschung. |
| Urlaub und Krankheit | Vorhandener Kalender und echte Formulare; getrennte Urlaubszeiträume, Aktualisierung ohne Seitenreload und fester Mitarbeiter im Profil bleiben erhalten. |
| Mitarbeitende | Personenbezogene Arbeitsliste: Probleme, offene Anträge, nächste/heutige Abwesenheit und Gesamtzeitkonto. Handlungsbedarf zuerst, Suche und Filter, sechs Personen pro Seite. Direkte Aktionen öffnen gefilterte Anträge, Zeiten und Urlaub mit vorausgewählter Person. Das vollständige Profil liegt unter `/admin/dashboard-neu/mitarbeiter/:username`. |
| Module | Vollständiger freigegebener Modulkatalog sowie Schnellzugriff auf Dienstplan, Payroll und Analytics. |
| App-Funktionen | Bestehende Navigation und Arbeitsbereiche, Hell-/Dunkelmodus, Sprache, Profil und Befehlspalette werden weiterverwendet. |
| Anpassung | Neue Übersicht hat eigene Ansichtseinstellungen; Fachbereiche verwenden separate `workspace-*`-Layoutbereiche. Alte Einstellungen bleiben bestehen. |

## Technische Grenzen und Prüfung

Das neue Layout ist auf `.admin-workspace` begrenzt. Die klassische Variante bleibt der Standardwert von `AdminDashboard`; die Mitarbeiterkomponente erhält nur einen optionalen Rückweg. Backendänderung: additive Freigabe der vier neuen Layoutbereiche in der bestehenden UI-Einstellungsvalidierung.

Automatisierte Prüfungen decken Rollen-/Routensperren, wiederhergestellte Arbeitsbereiche, Datenfilter, Entscheidungsgruppen, Teilfehler, Hintergrundaktualisierung, Kalenderanbindung und die bestehende Mitarbeiterübersicht ab. Browserprüfungen verwenden abgefangene API-Anfragen mit isolierten Testdaten. Build und Tests sind lokale Prüfungen; sie führen kein Deployment aus.

Kalender und Zeitprüfung nutzen auf Desktopbildschirmen die verbleibende Höhe unter der gemessenen App-Navigation. Der ganze Kalendermonat bleibt sichtbar, auch bei sechs Wochen. Tage zeigen den ersten Eintrag und die Gesamtanzahl; alle Einträge bleiben über die Tagesdetails erreichbar. Lange Zeittabellen scrollen innerhalb ihres Ergebnisbereichs. Mobile Ansichten, starke Vergrößerung und der Layouteditor bleiben im normalen Seitenfluss, damit keine Aktionen abgeschnitten werden.

Datumssemantik: `CorrectionRequest.requestDate` ist der gewählte Korrektur-/Arbeitstag, kein Eingangszeitstempel. Urlaubsanträge liefern keinen verlässlichen Erstellungszeitpunkt. Die Standardsortierung verwendet deshalb Urlaubsbeginn bzw. betroffenen Korrekturtag; fehlende Datumswerte stehen unabhängig von der Sortierrichtung am Ende. Ladefehler werden sichtbar angezeigt; unvollständige Daten sperren schreibende Aktionen.

Bereits anderweitig entschiedene Korrekturen werden anhand der Antwortdaten erkannt, auch wenn das Backend HTTP 200 zurückgibt. Sie werden neu geladen und nicht als erfolgreiche eigene Genehmigung quittiert. Bei einem fehlenden gültigen gewünschten Stempeltag ist eine Genehmigung gesperrt, weil der betroffene Freigabeumfang nicht bestimmbar ist.

Geprüft am 14.09.2026: 142 Frontendtests im gemeinsamen Lauf, danach 29 Tests für die zuletzt geänderte Übersicht und Arbeitsbereich-Metadaten (einschliesslich eines zusätzlichen Mitarbeiter-Tab-Tests). Die Backend-Validierung besteht 41 Tests. Der Browserdurchlauf umfasst Freigaben, Teamwechsel, zwei Urlaubszeiträume mit erhaltenem Dialog/Monat, Mitarbeiterprofil und Rückweg, Abwesenheitsdetails sowie Zeitprüfung und Antragscenter. Responsive Ansichten wurden zwischen 320 und 1440 Pixeln geprüft.

Überarbeitung vom 15.09.2026: Vollständiger Linux-Dockerbuild `chrono-frontend:local-dashboard-v2` erfolgreich, einschliesslich Produktionsaudit, 604 Frontendtests in 89 Dateien und `build:prod`. Browserprüfung mit isolierten API-Daten: alle fünf Hauptbereiche bei 1366×768, 1440×900, 1920×1080 und 390×844; Desktop auch im Dunkelmodus. Bei 1366×768 bleiben die fünf Standardansichten innerhalb des Viewports. Zusätzlich geprüft: Kalender mit sechs Wochen, Monatszeitprüfung, Mitarbeiter-Vorauswahl, zwei getrennte Urlaubszeiträume ohne Dialog-/Monatsverlust, gemischte Datumsreihenfolge, Urlaubs-Zeitraumfilter, Personenfilter, Freigabe ohne Seitenreload, Kommentar in der Historie und Abbruch des Löschdialogs. Kein Deployment ausgeführt.
