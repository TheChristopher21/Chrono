# PMS-Lastprüfung mit 500 zusätzlichen Aufenthalten — optimierter Vergleich

Der optimierte PMS-Stand auf `http://127.0.0.1:18086` bestand den Lastlauf mit zwölf gleichzeitigen virtuellen Mitarbeitern über 45 Sekunden. Alle Latenz- und Fehlergrenzen waren erfüllt; k6 endete mit Code 0. Die ursprünglichen Berichte und Messdateien wurden erhalten.

| Messwert | Dichter Ausgangsstand :18085 | Optimierter Stand :18086 |
| --- | ---: | ---: |
| Hotelzimmer | 1.007 | 1.007 |
| Zusätzliche bestätigte Aufenthalte / eigene Gastprofile | 500 / 500 | 500 / 500 |
| Zusätzliche Zimmernächte / persistierte ROOM-Foliozeilen | 1.500 / 1.500 | 1.500 / 1.500 |
| Sichtbare Aufenthalte und Folios im Testzeitraum | 509 / 509 | 509 / 509 |
| HTTP-Aufrufe einschließlich vier positiver Zugriffsprüfungen | 1.304 | 1.342 |
| Geprüfte Arbeitsaufrufe | 1.300 | 1.338 |
| Abgeschlossene Iterationen | 1.013 | 1.045 |
| HTTP-, Funktions- und unerwartete Berechtigungsfehler | 0 | 0 |
| Schreibaufrufe / Versionskonflikte | 23 / 0 | 29 / 0 |
| Lesezeit p95 / p99 | 101,83 / 130,10 ms | 83,43 / 105,89 ms |
| Schreibzeit p95 / p99 | 2.771,57 / 3.146,31 ms | 179,59 / 201,65 ms |
| Größte gemessene Schreibzeit | 3.251,90 ms | 207,94 ms |
| Rezeption: Lesezeit p95 | 132,30 ms | 105,99 ms |
| Housekeeping: Lesezeit p95 | 10,46 ms | 10,30 ms |
| Finanzen: Lesezeit p95 | 9,66 ms | 10,13 ms |
| Management: Lesezeit p95 | 12,57 ms | 12,79 ms |
| HTTP-Durchsatz | 28,63 Aufrufe/s | 29,45 Aufrufe/s |
| k6-Ergebnis | Code 99: Schreibgrenzen verletzt | Code 0: alle Grenzen erfüllt |

Die Beobachtung spricht für eine deutlich bessere Antwortzeit der schreibenden Rezeptionsabläufe nach der Bündelung von Statushistorien, Rechnungszuordnungen und zugehörigen Datensätzen. Sie ist kein exakt kontrollierter Mikrobenchmark: Der Ausgangsstand verwendete H2 im Arbeitsspeicher, der optimierte Stand eine separate H2-Dateidatenbank. Zusätzlich wurden die Steuersätze der neu initialisierten Demodaten korrigiert. JVM-Aufwärmung und die zufällige Verteilung der wenigen Schreibaufrufe unterscheiden sich ebenfalls. Aus den Einzelmessungen wird deshalb weder ein allgemeiner Beschleunigungsfaktor noch eine SLA abgeleitet.

Beide Bestände wurden über die unterstützte, idempotente Rezeptionsbuchungs-API angelegt: 500 neue synthetische Gäste, jeweils ein zugeordnetes eigenes Testzimmer 10000–10499 und drei Nächte vom 13.09.2026 bis zur Abreise am 16.09.2026. Keine ursprüngliche QA-Reservierung oder deren Folio wurde geändert. Das optimierte Anlegen dauerte etwa 74,8 Sekunden gegenüber etwa 522 Sekunden im Ausgangsstand; auch diese Laufzeiten unterliegen den genannten Umgebungsunterschieden.

Die unabhängige Prüfung las alle 21 Zimmerplanseiten mit je 50 Zimmern für einen 30-Tage-Zeitraum und bestätigte sämtliche 1.007 Zimmer und alle 500 zusätzlichen Aufenthalte. Ein separater API-Lesezugriff bestätigte für jedes neue Folio drei ROOM-Zeilen mit drei unterschiedlichen, passenden Leistungsdaten. Alle 1.500 optimierten ROOM-Zeilen haben einen Steuersatz; beim alten Demostand waren diese 1.500 Steuer-Snapshots leer. Der erfasste Bruttobetrag der zusätzlichen Aufenthalte beträgt in beiden Beständen CHF 238.500. Die Prüfung vorhandener Steuersätze ersetzt keine internationale Steuer- oder Rechnungszertifizierung.

Pro Arbeitsbereich lief eine eigene ROLE_USER-Identität mit `master=false` und drei virtuellen Mitarbeitern: Rezeption mit FRONT_DESK/GUESTS MANAGE, Housekeeping mit HOUSEKEEPING MANAGE, Finanzen mit FINANCE MANAGE und Management mit REPORTS VIEW. Alle acht getrennten Negativprüfungen lieferten HTTP 403 für die Masterverwaltung beziehungsweise einen nicht freigegebenen Hotelbereich. Die Lastprüfungen selbst hatten keine unerwarteten 401/403-Antworten. Feldweise Vertraulichkeitsprüfungen aller erlaubten Antwortdaten sind damit nicht abgedeckt.

Im optimierten Lauf wurden elf zusätzliche Testgastprofile angelegt und 18 Updates an einem eigenen Housekeeping-Prüfauftrag vorgenommen. Der konfigurierte Wert von 5 % gilt als Schreibwahrscheinlichkeit je geeigneter Rezeptions-/Housekeeping-Iteration; tatsächlich waren 29 von 1.338 Arbeitsaufrufen Schreibzugriffe, also 2,17 %. Beim Ausgangsstand waren es sechs Gastanlagen und 17 Housekeeping-Updates. Die gemessenen Schreibperzentile beruhen somit auf kleinen und unterschiedlich zusammengesetzten Stichproben.

Rechnungsverlauf und offene Debitoren wurden zusätzlich über ihre begrenzten API-Seiten gelesen: eine Rechnung, null offene Debitoren. Der Nachweis vergrößert Aufenthalte, Zimmerplan und Foliozeilen, nicht die finanzielle Langzeithistorie. Die sichtbare Gästeliste ist auf 200 Einträge begrenzt und darf nicht als Gesamtzahl der Gastprofile gelesen werden. Unterstützte Buchungen erzeugten interne Outbox-Ereignisse; Versand und Zustellung waren auf beiden isolierten Servern deaktiviert. Der Test rief keine externen Zahlungsanbieter, Versand-, Zustell- oder Wiederholungsendpunkte auf.

Während der beiden 45-Sekunden-Lastphasen pausierten andere CPU-intensive Prüfungen; während des optimierten Laufs gab es außerdem keine parallelen Browser-API-Aktionen des Parent-Tasks. Grenzen des Nachweises bleiben: kurze lokale Laufzeit, ein Hotel, keine WAN-/TLS-Latenz, keine Ausfallumschaltung, keine gleichzeitige Zimmerzuteilung oder Zahlungserfassung und keine große Rechnungs-/Debitorenhistorie. Die Testdaten bleiben ausschließlich in den isolierten QA-Datenbanken.

Die optimierte Einrichtung wurde vor dem Datenseeding einmal mit einem frischen separaten QA-Datenbankstand wiederholt: Das Benutzer-Update behandelt das erste Element der Rollenliste als alleinige ausgewählte Rolle. Danach wurde der Testadministrator ausdrücklich als ROLE_ADMIN mit PMS-Masterrechten eingerichtet. Der verworfene leere QA-Stand wurde nicht in die Messung einbezogen. Für Wiederholungen wählen die Hilfsskripte die Administratorrolle ausdrücklich und halten Datenbankziel und Artefakt-Suffix getrennt.

Messbelege im selben Verzeichnis: `pms-staff-load-dense-optimized-summary.json`, `pms-staff-load-dense-optimized-metadata.json`, `pms-staff-dense-seed-optimized.json`, `pms-staff-dense-verification-optimized.json`; Protokoll: `../../tmp/pms-staff-load-dense-optimized.log`. Ausgangsvergleich: `pms-staff-load-dense-report.md`. Alle Mess- und Seedartefakte wurden auf JWTs und Bearer-Tokens geprüft; es wurden keine gefunden. Token wurden nur im Prozessspeicher beziehungsweise in kurzlebigen Prozessumgebungsvariablen gehalten.
