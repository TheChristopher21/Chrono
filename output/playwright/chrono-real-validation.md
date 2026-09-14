# Prüfung der Browserfassung 10

Am 14.09.2026 lokal im Edge-Browser geprüft. Der akzeptierte Entwurf 09 wurde mit originalem Chrono-Logo, durchgehender App-Navigation, Arbeitsbereich-Tabs und ausgearbeiteten Detailansichten verfeinert.

- Übersicht bei 1440, 1024, 736 und 320 Pixeln ohne horizontale Überläufe oder JavaScript-Laufzeitfehler. Helle und dunkle Darstellung visuell geprüft.
- Mitarbeiterprofil, persönlicher Kalender, Antragsdetails und Urlaubserfassung in mehreren Bildschirmbreiten geprüft. Die mobile Kennzahlenanordnung hat lesbare, einzeilige Beschriftungen.
- Originales Logo geladen; Arbeitsbereiche öffnen, wechseln und schliessen. Der doppelte Übersichts-Kopf verschwindet in Detailansichten.
- Teamfilter, Antragsprüfung mit Abwesenheitsüberschneidungen, Kommentarerhalt und aktualisierte Zähler nach Freigabe geprüft. Zeitprobleme öffnen die passende Person und den betroffenen Tag.
- Urlaub aus dem Mitarbeiterprofil hat eine fest gewählte Person. Zwei getrennte Zeiträume werden gemeinsam gespeichert. Die Ansicht und der gewählte Monat bleiben erhalten, Kalendermarker aktualisieren sich; erneutes identisches Speichern ist deaktiviert.
- Lese-Rolle deaktiviert schreibende Hauptaktionen. Antragscenter, Zeitprüfung, Kalender und Modul-Vorschauen erreichbar.

Prüfskripte: chrono-real-layout-qa.cjs, chrono-real-flow-qa.cjs, chrono-real-scenes-qa.cjs und chrono-real-mobile-final.cjs. Screenshots liegen neben dieser Datei.

Grenze: Interaktiver Entwurf mit lokalen Beispieldaten und Vorschauen der Fachmodule. Keine Produktionsänderung und keine Bestätigung für Backendintegration oder vollständige fachliche Gleichwertigkeit einer späteren Produktimplementierung.
