# PMS-Sitzung und getrennte Arbeitsbereiche

Stand: 12. September 2026.

## Bedienung

- Das angemeldete PMS bleibt ohne Maus- oder Tastatureingaben geöffnet. Chrono behält seine automatische Abmeldung nach zehn Minuten Inaktivität. Beim Wechsel vom PMS zu Chrono beginnt dessen Zeitfenster neu.
- Ein inaktiver Chrono-Browser-Tab meldet nur sich selbst ab. Ein anderer Browser-Tab mit offenem PMS bleibt angemeldet. Neuladen eines automatisch abgemeldeten Tabs stellt dessen Sitzung nicht wieder her. Bewusstes Abmelden beendet die gemeinsame Anmeldung weiterhin in allen Browser-Tabs.
- PMS und Chrono zeigen jeweils ihre eigenen internen Tabs und Startmenüs. Beim Bereichswechsel bleiben die geöffneten Tabs des anderen Bereichs erhalten. Im PMS gibt es einen direkten Link „Zu Chrono wechseln“.
- Ein normaler Klick auf einen Navigationspunkt aktiviert dessen vorhandenen Tab und erhält dessen gespeicherte Filter. Ein Mittelklick öffnet einen weiteren internen Tab derselben Seite. Dies gilt für die Hauptnavigation, das Tab-Startmenü, die PMS-Seitenleiste und die mobile Chrono-Navigation.
- Der Browserverlauf unterscheidet Tabs anhand ihrer ID, auch bei identischer Adresse. Vor-/Zurück-Maustasten verwenden diesen Verlauf; in der Electron-App werden die entsprechenden Systembefehle ebenfalls verarbeitet.
- Ein Mittelklick auf einen nicht angehefteten Tab schließt ihn. Schließen, Umordnen und Wiederherstellen beziehen sich auf den sichtbaren Arbeitsbereich. Pro Bereich sind bis zu zwölf Tabs möglich; angeheftete Tabs bleiben beim regulären Öffnen geschützt.
- Bereits besuchte Tab-Inhalte bleiben während des Bereichswechsels im Speicher. Dadurch können beispielsweise zwei Gästeformulare unterschiedliche ungespeicherte Eingaben enthalten. Geschlossene Tabs, Abmeldung und Kontowechsel verwerfen diese Inhalte. Ein vollständiges Neuladen stellt die Tab-Adressen wieder her, nicht ungespeicherte Formulare.
- Das PMS zeigt immer die vollständige bisherige Profi-Ansicht mit allen Bereichen und Tastaturkürzeln. Der Umschalter „Einfach / Profi“ wurde entfernt.

## Sitzungsverhalten

Die Ausnahme gilt nur für den angemeldeten PMS-Arbeitsbereich `/pms` mit entsprechender Modul- und Seitenberechtigung. Öffentliche Buchungs- und Gästeanmeldeseiten erhalten keine Ausnahme.

Ein geöffnetes PMS erneuert sein noch gültiges, zeitlich begrenztes JWT kurz vor Ablauf über `POST /api/pms/session/refresh`. Der Server prüft den Benutzer, seine PMS-Berechtigung, die Firmenzuordnung und einen gegebenenfalls abgelaufenen Demo-Zugang erneut. Das Token wird nicht unbegrenzt gültig gemacht. Bleibt der Rechner länger als die verbleibende Tokenlaufzeit offline oder im Ruhezustand, ist erneut eine Anmeldung erforderlich.

Automatisch abgemeldete Chrono-Tabs blockieren auch das Anhängen gemeinsamer Anmeldedaten an ihre Hintergrundanfragen. Ein externer Kontowechsel wird erkannt; Inhalte eines alten Kontos dürfen nicht mit den Anmeldedaten eines anderen Kontos weiterlaufen.

## Technische Umsetzung

Die vorhandene Tab-Speicherung bleibt kompatibel und unterscheidet die Bereiche anhand der Seite. Der Server erlaubt bereits genügend Einträge und eindeutige IDs für zwölf Chrono- und zwölf PMS-Tabs. Eine Datenbankmigration ist nicht erforderlich.

Die App verwendet [React Activity](https://react.dev/reference/react/Activity) für besuchte Tab-Inhalte: Inaktive Ansichten behalten ihren Zustand, während ihre Effekte aufgeräumt werden. Jede Ansicht bekommt einen eigenen Router-Ort; nur die aktive Ansicht zeigt ihre Navigation. Portale werden zusätzlich an die aktive Ansicht gebunden. Die Mindestversion von React entspricht der bereits installierten und im Lockfile enthaltenen Version 19.2.4.

Die Desktop-App verarbeitet [Electron `app-command`](https://www.electronjs.org/docs/latest/api/browser-window#event-app-command-windows-linux) über die Chromium-Navigationshistorie. Der Webbrowser behält seine native Vor-/Zurück-Behandlung, damit ein Mausklick nicht doppelt navigiert.

Die Änderungen sind lokal implementiert. Frontend und Backend müssen gemeinsam ausgerollt werden, damit die Sitzungserneuerung im Produktivsystem verfügbar ist.

## Prüfung

- Gesamte Frontend-Testsuite: 334 Tests in 60 Dateien bestanden.
- Backend: sechs Tests für die PMS-Sitzungserneuerung und 28 Tests für Prüfung und Speicherung der UI-Einstellungen bestanden.
- Produktionsbuild und `git diff --check` erfolgreich.
- Echte PMS-Komponenten mit Tab-Verwaltung geprüft: Walk-in-Schnellaktionen in neuen und vorhandenen Tabs sowie getrennte, ungespeicherte Gästeformulare.
- Browserprüfung in Edge mit synthetischen API-Daten: normaler Klick, Mittelklick, identische Tab-Adressen im Verlauf, Bereichswechsel mit erhaltenen Tabs und Erhaltung eines ungespeicherten Chrono-Profilentwurfs. Testbrowser und Entwicklungsserver wurden anschließend beendet.
- Die Electron-Maustastenanbindung ist durch gezielte Tests geprüft; ein neu paketiertes Desktop-Installationsprogramm wurde nicht gestartet.
