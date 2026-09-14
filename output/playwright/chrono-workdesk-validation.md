# Prüfung des Entwurfs 09

Am 14.09.2026 im lokalen Edge-Browser mit sandboxed Preview geprüft.

- Übersicht bei 1440, 1024, 736 und 320 Pixeln: keine Überläufe und keine JavaScript-Laufzeitfehler. Helle und dunkle Darstellung visuell geprüft.
- Teamfilter betrifft Vorgänge, Abwesenheiten und Zeitkonten. Saldo-Details und negative Konten behalten das ausgewählte Team.
- Urlaubsfreigabe zeigt verfügbaren Beispielurlaub und die zeitgleiche Abwesenheit von Mirjam. Kommentar bleibt nach Zu-/Aufklappen erhalten. Freigabe aktualisiert Zähler, Rückmeldung und die kommenden Abwesenheiten.
- Zeitproblem führt zum Editor der richtigen Person und des angegebenen Tags.
- Urlaub aus dem Mitarbeiterkontext hat eine fest gewählte Person. Zwei Zeiträume in verschiedenen Monaten werden gespeichert; Dialog und Planungsmonat bleiben erhalten, Kalendermarker aktualisieren sich.
- Bei mehr als drei kommenden Abwesenheiten wird die Begrenzung des Ausschnitts kenntlich gemacht.
- Abwesenheitsauskunft öffnet zunächst lesbare Details, darin ein eigener Bearbeiten-Knopf. Lese-Rolle kann Details sehen; Bearbeiten und Genehmigen sind deaktiviert.
- Antragscenter, Zeitprüfung, Kalender und Module erreichbar. Anpassung der Zeitkontenanzeige wirksam. Aufgeklappte Entscheidung bei 320 Pixeln ohne Überlauf.
- Kontextbaustein zusätzlich durch 25 isolierte Assertions des Teilagenten zu Datumsintervallen, Überschneidungen, fehlenden Daten, Urlaubskonto und Escaping geprüft.

Grenze: Lokale Beispieldaten und Fachmodul-Vorschauen. Diese Prüfung ist keine Bestätigung für Backendintegration oder vollständige fachliche Gleichwertigkeit einer späteren Produktimplementierung.
