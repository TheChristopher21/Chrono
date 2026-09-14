# Separate Admin-Arbeitsübersicht

Die neue Oberfläche liegt unter `/admin/dashboard-neu`. Der Einstieg „Neues Dashboard“ erscheint für `ROLE_SUPERADMIN` im Plattformmenü und im Arbeitsbereich-Launcher. Die direkte Route, die Mitarbeiter-Unterroute und wiederhergestellte Arbeitsbereiche prüfen dieselbe Rolle. Eine individuelle Seitenfreigabe ersetzt diese Rollenprüfung nicht.

`/admin/dashboard` bleibt die bisherige Oberfläche und das Standardziel. Die neue Variante verwendet die vorhandenen API-Endpunkte und Fachkomponenten; der Entwurf enthält keine Beispieldaten in der Produktimplementierung.

## Funktionszuordnung

| Bereich | Neue Arbeitsübersicht |
| --- | --- |
| Offene Aufgaben | Gemeinsame Liste aus Korrekturgruppen, Urlaubsanträgen und den bereits berechneten Zeitproblemen. Filter, weitere Vorgänge, direkte Prüfung und Mitarbeiterprofil. |
| Freigaben | Aufklappbare Änderungen und Entscheidungsnotiz. Zusammengehörige Korrekturen werden gemeinsam bearbeitet. Bei Teilfehlern bleiben nur gescheiterte Änderungen offen. |
| Team | Abteilungsfilter (`departmentName`), heutige und kommende Abwesenheiten, Zeitkonten; Details öffnen den vorhandenen Kalender. |
| Zeitprüfung | Bestehende Wochen-/Monatsansicht, Zeitraumgrenzen, Suche, Problemfilter, Sortierung, Tageskorrekturen, Feiertagsentscheidungen, Arbeitstagtausch, Krankmeldungen und Druck. |
| Antragscenter | Bestehende vollständige Urlaubs- und Korrekturlisten einschliesslich Historie, Suche, Entscheidungen und Löschung. |
| Urlaub und Krankheit | Vorhandener Kalender und echte Formulare; getrennte Urlaubszeiträume, Aktualisierung ohne Seitenreload und fester Mitarbeiter im Profil bleiben erhalten. |
| Mitarbeitende | Suchbare Teamliste und neue geschützte Unterroute `/admin/dashboard-neu/mitarbeiter/:username`; bestehendes vollständiges Profil mit Rückweg zur neuen Übersicht. |
| Module | Vollständiger freigegebener Modulkatalog sowie Schnellzugriff auf Dienstplan, Payroll und Analytics. |
| App-Funktionen | Bestehende Navigation und Arbeitsbereiche, Hell-/Dunkelmodus, Sprache, Profil und Befehlspalette werden weiterverwendet. |
| Anpassung | Neue Übersicht hat eigene Ansichtseinstellungen; Fachbereiche verwenden separate `workspace-*`-Layoutbereiche. Alte Einstellungen bleiben bestehen. |

## Technische Grenzen und Prüfung

Das neue Layout ist auf `.admin-workspace` begrenzt. Die klassische Variante bleibt der Standardwert von `AdminDashboard`; die Mitarbeiterkomponente erhält nur einen optionalen Rückweg. Backendänderung: additive Freigabe der vier neuen Layoutbereiche in der bestehenden UI-Einstellungsvalidierung.

Automatisierte Prüfungen decken Rollen-/Routensperren, wiederhergestellte Arbeitsbereiche, Datenfilter, Entscheidungsgruppen, Teilfehler, Hintergrundaktualisierung, Kalenderanbindung und die bestehende Mitarbeiterübersicht ab. Browserprüfungen verwenden abgefangene API-Anfragen mit isolierten Testdaten. Build und Tests sind lokale Prüfungen; sie führen kein Deployment aus.

Geprüft am 14.09.2026: 142 Frontendtests im gemeinsamen Lauf, danach 29 Tests für die zuletzt geänderte Übersicht und Arbeitsbereich-Metadaten (einschliesslich eines zusätzlichen Mitarbeiter-Tab-Tests). Die Backend-Validierung besteht 41 Tests. Der Browserdurchlauf umfasst Freigaben, Teamwechsel, zwei Urlaubszeiträume mit erhaltenem Dialog/Monat, Mitarbeiterprofil und Rückweg, Abwesenheitsdetails sowie Zeitprüfung und Antragscenter. Responsive Ansichten wurden zwischen 320 und 1440 Pixeln geprüft.
