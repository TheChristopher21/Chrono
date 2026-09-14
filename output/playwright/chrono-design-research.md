# Chrono: Grundlage für die neue Arbeitsübersicht

Recherche vom 14. September 2026. Öffentliche Einzelbewertungen wurden gezielt nach den Aufgaben von Führungskräften und Administration gelesen. Sie geben Hinweise auf Bedürfnisse; sie sind keine repräsentative Befragung und keine Messung des Chrono-Nutzerverhaltens. Teilweise wurden Bewertungen incentiviert. Ältere Bewertungen beschreiben damalige Erfahrungen.

## Beobachtungen und Designentscheidungen

| Beobachtung | Quelle | Konsequenz im Entwurf |
|---|---|---|
| Sidiqa, Ausbildungsbegleiterin, wünscht präsentere offene Genehmigungen und kürzere Wege zum Zeitkonto. Oliver, COO, lobt zentrale Freigaben und den Teamüberblick. | [absence.io, OMR Reviews](https://omr.com/de/reviews/product/absence-io), Bewertungen innerhalb der letzten drei Monate bzw. 30 Tage laut Plattform | Konkrete offene Vorgänge bilden die Hauptfläche. Anträge und Zeitprüfung bleiben getrennt filterbar. Zeitkonten sind direkt erreichbar. |
| Alissa, Product Manager, lobt den sichtbaren Resturlaub beim Freigeben. Michael, Strategy Manager, vermisst die gemeinsame Erledigung mehrerer Aufgaben. Rafael, Geschäftsführer, berichtet von bewusst reduziertem Funktionsumfang. | [Personio, OMR Reviews](https://omr.com/de/reviews/product/personio), jeweils älter als zwölf Monate | Kontext beim aufklappbaren Antrag. Vollständige Module behalten klare Zugänge. Sammelaktionen sind ein möglicher nächster Ausbau des Antragscenters, keine zusätzliche permanente Startseitenleiste. |
| Nadia, Verwaltungsassistentin, nutzt die Abwesenheitsübersicht für die Abstimmung gleichzeitiger Urlaube; Thomas, Bereichsleitung, schätzt direkte Zeitkorrekturen und wünscht verständliche Stundenangaben. | [Papershift, OMR Reviews](https://omr.com/de/reviews/product/papershift), älter als zwölf Monate | Gleichzeitig abwesende Personen des Teams im Freigabekontext nennen. Datierte Zeitprobleme führen direkt zum passenden Editor. Zeitkonten zeigen Stunden und Minuten. |
| Ein Praxismanager beschreibt wiederholtes Umstellen der Datumsfelder und umständliche Urlaubsfreigaben. | [Deputy, TrustRadius](https://www.trustradius.com/reviews/deputy-2021-12-05-16-28-45), 31.12.2021, incentiviert | Übersicht startet mit heute und den kommenden Abwesenheiten. Bearbeiten soll Person, Datum und bisherigen Arbeitskontext übernehmen. |
| Bei komplexen Anwendungen empfiehlt NN/g sichtbare wichtige Informationen, erhaltene Fähigkeiten und Zugriff auf ergänzenden Kontext ohne Verlassen der Hauptaufgabe. | [8 Design Guidelines for Complex Applications](https://www.nngroup.com/articles/complex-application-design/), 08.11.2020 | Zwei Hauptbereiche, aufklappbare Entscheidungen und wenige dauerhafte Bedienelemente. Der vollständige Kalender und Detailauswertungen bleiben eigene Arbeitsansichten. |

## Aufbau

1. **Arbeitsübersicht:** offene Anträge und ungeklärte Zeiten, ein gemeinsamer Teamfilter für die Übersicht, heutige Abwesenheiten, nächste Abwesenheiten und kompakte Zeitkonten.
2. **Kontextuelles Prüfen:** Vergleich alter und beantragter Zeiten, Antragsgrund, Kommentar und Entscheidung. Urlaub zeigt den bekannten Kontostand und tatsächliche zeitgleiche Abwesenheiten aus den Beispieldaten. Eine Überschneidung allein wird nicht als Unterbesetzung ausgegeben.
3. **Kalender und Mitarbeitende:** voller Kalender für Planung; Personenübersicht mit fest gewähltem Mitarbeiter bei der Urlaubseingabe. Mehrere getrennte Zeiträume können vorgemerkt und gemeinsam gespeichert werden.
4. **Weitere Arbeitsbereiche:** Antragscenter, Zeitprüfung, alle freigegebenen Module, Dienstplan, Payroll, Analytics, Druck, Arbeitsbereiche, Konto, Sprache, Darstellung und Anpassung bleiben erreichbar.

## Grenzen und Abnahme

Dies ist ein lokaler interaktiver Entwurf mit Beispieldaten. Er schreibt keine echten Personal-, Zeit- oder Urlaubsdaten. Die Modulziele sind Vorschauen, keine vollständig nachgebauten Fachmodule. Die vollständige Funktionserhaltung einer späteren Implementierung ist gegen die separate Quellcode-Inventur zu prüfen.

Öffentliche Bewertungen erklären Bedürfnisse; die gewählte Anordnung ist unsere Designableitung. Vor Produktfreigabe sollte der Entwurf mit echten Chrono-Admins an fünf Aufgaben geprüft werden: überfälligen Zeitfehler finden, Urlaub unter Berücksichtigung des Teams entscheiden, mehrere Urlaube erfassen, bestehenden Eintrag ändern und Zeitbericht für eine Person öffnen. Erfolgskriterien: ohne Erklärung auffindbar, eindeutiger Datum-/Personenkontext, sichtbare Bestätigung und kein Verlust des Arbeitsstands.
