# PMS-Design und frei anpassbare Übersicht

Stand: 13. September 2026. Die Änderungen betreffen die PMS-Oberfläche und ihre persönlichen Dashboard-Einstellungen. Bestehende Chrono-Dashboards verwenden weiterhin ihre bisherige Anordnung.

## Übersicht gestalten

Über **Übersicht anpassen** lassen sich Bereiche im Raster frei positionieren und in Breite und Höhe verändern. Der Griff verschiebt eine Karte, die untere rechte Ecke verändert ihre Größe. Eine Vorschau zeigt vor dem Loslassen die neue Position und ausweichende Karten. Nur überlappende Karten rücken nach unten; absichtlich freie Flächen bleiben erhalten. Gesperrte Karten und die Rastergrenzen werden respektiert.

Die Suchpalette bietet die bisherigen neun Bereiche sowie sechs einzelne Kennzahlen und sechs einzelne Schnellaktionen. Dadurch kann beispielsweise die Auslastung unabhängig von den anderen Kennzahlen direkt neben den Anreisen liegen. Die Gruppenblöcke lassen sich ausblenden, wenn ihre Inhalte einzeln angeordnet werden.

- **Genaue Position:** Bereich auswählen und Spalte, Zeile, Breite und Höhe eintragen. Die Oberfläche zählt ab eins; intern werden Positionen ab null gespeichert.
- **Tastatur:** Pfeiltasten am Griff verschieben; Umschalt plus Pfeiltasten oder der Größenänderungsgriff verändern die Abmessungen. Escape verwirft eine laufende Ziehbewegung.
- **Rückgängig:** Die letzten Layoutänderungen einschließlich Ausblenden und Zurücksetzen können während der Bearbeitung rückgängig gemacht werden.
- **Mobil:** Karten werden untereinander angezeigt. Die gespeicherte Desktopgeometrie bleibt dabei erhalten. Über die Positionsfelder kann sie auch am kleinen Bildschirm bearbeitet werden.
- **Speicherung:** Persönliche Anordnung je Benutzer und Hotel über den vorhandenen Einstellungsendpunkt. Alte Einstellungen mit Reihenfolge und Größenklasse werden übernommen. Die Schema-Version bleibt kompatibel; die optionalen Rasterfelder werden serverseitig gemeinsam und auf gültige Grenzen geprüft.
- **Offene Änderungen:** „Wird gespeichert“ bleibt sichtbar, bis die letzte Änderung bestätigt wurde. Bei Verbindungsfehlern bleibt eine lokale Kopie mit Änderungskennung erhalten. Beim Zurückwechseln zum Hotel oder erneuten Öffnen wird diese mit der aktuellen Serverrevision erneut gespeichert. Eine ältere Antwort kann eine neuere Änderung nicht als gespeichert markieren oder überschreiben.

Das Raster hat zwölf Spalten und bis zu 200 Zeilen. Karten können bis zu 24 Rasterzeilen hoch sein. Die Karte scrollt bei umfangreichen Inhalten innerhalb ihrer gewählten Höhe; die Nachbarkarte wird dadurch nicht überdeckt. Breitenabhängige Umbrüche richten sich nach der Karte selbst, nicht nur nach der Fensterbreite.

## Dark- und Light-Mode

Raten, Firmenauswahl, Verläufe, kommerzielle Bereiche und Warnmeldungen verwenden durchgängig die PMS-Farbvariablen. Native graue Such-/Verlaufsbuttons wurden an die übrigen Bedienelemente angepasst. Tastaturfokus, deaktivierte Schaltflächen, Akzentbeschriftungen und Gefahrbestätigungen sind in beiden Modi unterscheidbar.

Der Zimmerplan besitzt eine eigene dunkle Statuspalette. Formulare, Tabellen und schmale Karten erhalten passende Umbrüche. Die Zweispaltenansicht umfangreicher Arbeitsbereiche wird früher gestapelt, damit sie zwischen Sidebar und Seitenrand nicht überläuft. Öffentliche Veranstaltungsangebote erhalten ebenfalls lokale Theme-Farben.

## Prüfnachweise

Die Sichtprüfung begann in der vom Nutzer gestarteten Anwendung `localhost:5173`. Änderungen an Hotel-, Gäste-, Reservierungs- oder Finanzdaten wurden dort nicht vorgenommen. Die vollständige Bearbeitungs- und Speicherprobe verwendet das bereits vorhandene synthetische Hotel in der isolierten MySQL-Testumgebung.

- [Echte Mausbedienung und gespeicherter Rundlauf](../Chrono-frontend/output/playwright/pms-design-canvas-verification.json): genaue Platzierung, Ziehen, Vergrößern, Kollisionsausgleich, Rückgängig und identische Position nach Neuladen.
- [Desktopprüfung](../Chrono-frontend/output/playwright/pms-design-final-browser-scan.json) und [übrige Arbeitsbereiche](../Chrono-frontend/output/playwright/pms-design-remaining-verification.json): alle 17 Arbeitsbereiche neben der Übersicht in beiden Farbmodi, jeweils ohne Überlaufen der gesamten Seite. Die Standardübersicht und ihr Editor wurden zusätzlich am Desktop und mobil angesehen.
- [Responsive-Prüfung](../Chrono-frontend/output/playwright/pms-design-responsive-verification.json): Übersicht, Zimmerplan, Raten und Rechnungen bei 390, 768 und 1200 Pixel Breite, jeweils hell und dunkel; 24 Ansichten ohne horizontales Überlaufen der Seite. Der Zimmerplan behält seine eigene horizontale Tabellenbewegung.
- [Frontend](../tmp/pms-design-frontend-final.log): 480 Tests in 82 Dateien bestanden, einschließlich Kollisionsregeln, Tastaturbedienung, einzelner Dashboard-Bausteine, verzögerter Speicherantworten, Hotelwechsel und Benutzertrennung.
- [Produktionsbuild](../tmp/pms-design-production-build.log): erfolgreich.
- [Backend](../tmp/pms-design-backend-final.log): 60 gezielte Tests bestanden, einschließlich Datenbank-Speichern und erneutem Lesen nach geleertem Persistence-Kontext, Zugriffskontrolle und der unten beschriebenen Live-Verbindungen.

Ansichten der fertigen Übersicht: [Light Mode](../Chrono-frontend/output/playwright/pms-design-overview-final-light.png), [Dark Mode](../Chrono-frontend/output/playwright/pms-design-overview-final-dark.png). Der [mobile Editor](../Chrono-frontend/output/playwright/pms-design-editor-mobile-light.png) bietet dieselben Positionsfelder.

## Beim Browserlauf gefundene Live-Verbindungsfehler

Lange geöffnete PMS-Live-Streams hielten über Spring Open EntityManager in View Datenbankverbindungen fest. In der isolierten MySQL-Umgebung waren schließlich alle zehn Verbindungen belegt; nachfolgende Benutzerabfragen scheiterten. Nur der PMS-Live-Endpunkt ist jetzt von diesem Mechanismus ausgenommen. Andere HTTP-Seiten behalten ihr bisheriges Verhalten.

Der Regressionstest verwendet einen echten HTTP-Server mit produktivem JWT-/Security-Filter und einem Pool mit nur zwei Datenbankverbindungen: Zwölf gleichzeitig offene Streams lassen den Pool frei. Der dreizehnte Stream erhält bei `Accept: text/event-stream` korrekt 429, einen leeren Body und `Retry-After: 15`; eine anschließende authentifizierte Datenbankabfrage funktioniert weiterhin. Dies verhindert auch die zuvor beobachtete falsche 403-Folgeantwort durch die JSON-Fehlerverhandlung. Die Verbindungslimits und Anmelderegeln wurden nicht gelockert.

Der erneute MySQL-Browserlauf nach dem Pool-Fix zeigte keine Pool-Zeitüberschreitungen. Die Testumgebung hat keinen Changelog-Eintrag; entsprechende 404-Antworten des allgemeinen Chrono-Changelog-Endpunkts gehören nicht zu den PMS-Designprüfungen. Die letzte Korrektur der 429-Antwort ist durch den echten HTTP-Integrationstest geprüft.

Das bereits laufende Nutzer-Backend wurde nicht neu gestartet. Für die neue serverseitige Positionsprüfung und Live-Korrektur muss das lokale Backend einmal aus dem aktuellen Code neu gestartet werden; danach die PMS-Seite neu laden. Es ist keine zusätzliche Datenbankmigration notwendig.
