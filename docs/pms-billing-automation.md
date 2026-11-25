# PMS: Versand, Mahnwesen und Bankabgleich

Diese Beschreibung betrifft die implementierten Abläufe im Code. Sie setzt keine tatsächlich eingerichteten Anbieterzugänge voraus. Migration `V40__pms_billing_delivery_bank_automation.sql` ergänzt die Tabellen, den unveränderlichen Rechnungs-Sprachstand und das PDF-Archiv. Bestehende Hotels beginnen ohne aktivierten E-Mail-Versand und ohne Mahnautomatik.

## E-Mail-Versand

Ein PMS-Master richtet unter „Versand und Mahnwesen“ je Hotel Absender, Anzeigename, Antwortadresse, Vorlagen und die gewünschten Automatiken ein. SMTP-Host und Zugangsdaten verwenden die vorhandene Spring-Mail-Konfiguration der Installation. Die drei Zeitlimits sind über `SPRING_MAIL_SMTP_CONNECTION_TIMEOUT`, `SPRING_MAIL_SMTP_READ_TIMEOUT` und `SPRING_MAIL_SMTP_WRITE_TIMEOUT` überschreibbar; die Vorgaben sind 5, 30 und 30 Sekunden. Die Verbindung verwendet die vorhandene SMTP-/TLS-Konfiguration. Der Mailserver muss den angegebenen Hotelabsender akzeptieren.

Rechnungen können ausdrücklich an eine ausgewählte Adresse oder die gespeicherte Rechnungsadresse versandt werden. Firmenrechnungen benötigen eine Firmen-Rechnungsadresse oder eine bewusst eingegebene Adresse; das System verwendet dafür nicht stillschweigend die private Adresse des Reisenden. Bei Privatfolien ist die Hauptadresse des Gastes der Rückfall. Der Versandauftrag bewahrt Empfänger, Absender, Betreff, Nachricht, eindeutige Message-ID, Dokumentdatei und SHA-256-Prüfsummen. Derselbe Auftragsschlüssel erzeugt keinen zweiten Auftrag; abweichende Inhalte unter demselben Schlüssel werden abgelehnt.

Die bestehende Gastkommunikation erzeugt bei aktiviertem Hotelversand ebenfalls echte Versandaufträge. Ohne Aktivierung bleibt die ältere Kommunikationswarteschlange erhalten und erzeugt keine externe Zustellung. Bereits gespeicherte ausgehende Nachrichten können über `POST /api/pms/properties/{hotel}/billing/communications/{nachricht}/send` ausdrücklich übernommen werden. Neue freie Nachrichten und Anhänge lassen sich direkt im Versandpanel anlegen. Zugelassen sind bis zu zehn PDF-, PNG-, JPEG-, CSV- oder Textanhänge mit insgesamt höchstens 8 MB.

Der Worker verarbeitet Aufträge nach dem Commit. Er reserviert einen Auftrag mit einer zeitlich begrenzten Sperre, überträgt außerhalb der Datenbanktransaktion und schreibt das Ergebnis anschließend dauerhaft:

| Status | Bedeutung |
| --- | --- |
| QUEUED | Versand eingeplant |
| SENDING | Übertragung vom Worker übernommen |
| SENT | SMTP-Server hat die Nachricht angenommen; keine Lesebestätigung oder garantierte Posteingangszustellung |
| RETRY | Klar erkennbarer vorübergehender Fehler, beispielsweise abgelehnte Verbindung oder SMTP-4xx; bis zu fünf Versuche mit wachsendem Abstand |
| UNKNOWN | Annahme nach Abbruch oder abgelaufener Worker-Sperre unklar; kein blinder automatischer Neuversand |
| FAILED | Dauerhafte Ablehnung oder ausgeschöpfte Wiederholungen |
| PAUSED | Hotelversand nach Einplanung deaktiviert; ausdrücklicher Neustart nach Aktivierung erforderlich |
| CANCELLED | Die zu versendende Rechnung/Forderung ist inzwischen korrigiert oder der Mahnbetrag hat sich verändert |

Eine Wiederholung von `UNKNOWN` verlangt die ausdrückliche Bestätigung einer möglichen Doppelzustellung. SMTP kann bei einem Verbindungsabbruch nach der Datenübertragung keine allgemeine Garantie „genau einmal im Empfängerpostfach“ geben. Die Oberfläche benennt daher die tatsächliche Serverannahme. Umsetzung von MIME-Anhängen und Versand folgt der [Spring-Mail-Dokumentation](https://docs.spring.io/spring-framework/reference/integration/email.html); das Annahme-/Fehlerprotokoll richtet sich nach [SMTP, RFC 5321](https://www.rfc-editor.org/info/rfc5321/).

Technische Schalter: `app.pms.delivery.worker.enabled` und `app.pms.delivery.worker.interval-ms` (Vorgabe 30 Sekunden). Die Installation startet mit ausgeschaltetem Worker; der Betreiber aktiviert ihn für den tatsächlichen Versand ausdrücklich. Diese Schalter ersetzen nicht die zusätzliche Aktivierung je Hotel. Ein abgeschalteter Worker belässt die Aufträge sichtbar in der Warteschlange.

## Rechnungsdokumente und Sprachen

Beim Ausstellen wird DE, EN, FR, IT oder ES gespeichert: ausdrücklich gewählte Sprache, sonst unterstützte Gastsprache, sonst Hotelstandard. Bestehende Rechnungen erhalten durch die Migration DE, entsprechend dem bisherigen Formular. Eine Gutschrift übernimmt die Sprache und Empfänger-/Lieferantensnapshots des Originals. Bezeichnungen im Rechnungsformular werden übersetzt; ursprünglich eingegebene Leistungsbeschreibungen und Namen bleiben erhalten.

Die erste erzeugte PDF-Datei wird mit Prüfsumme in `pms_invoice_documents` archiviert. Jeder Nachdruck und Versand verwendet danach exakt diese Bytes. Eine spätere Profiländerung, Spracheinstellung oder Korrektur ersetzt das Original nicht. Für Zeichen außerhalb der vorhandenen Rechnungsschrift kann `app.pms.invoice.font-path` auf eine geeignete einbettbare Unicode-Schrift zeigen. Fehlende Glyphen werden ausdrücklich gemeldet. Die Sprachwahl ist keine Behauptung einer fiskalischen Zulassung in sämtlichen Ländern.

## Mahnregeln

Ein Hotel kann erste Mahnfrist, Abstand zwischen Mahnungen und maximale Stufe festlegen. Automatisches Anlegen und tatsächlicher E-Mail-Versand sind getrennte Opt-ins. Ein manueller Lauf nutzt dieselben Regeln. Die Vorschau zeigt die nächsten Stufen und deren früheste Fälligkeit.

Die Prüfung verwendet höchstens den kleineren Wert aus offenem finanziellem Betriebstag und tatsächlichem Hoteldatum. Eine Forderung muss positiv und überfällig sein; zusätzlich gelten die erste Frist und der Abstand zur letzten Mahnung. Der Hotel-Schreiblock und ein eindeutiger Schlüssel aus Forderung und Mahnstufe verhindern parallele doppelte Mahnungen. Jeder Mahnvorgang speichert Rechnung, Fälligkeit, Betrag und Stufe. Direkt vor dem Versand werden Rechnung, offener Betrag und Fälligkeit erneut geprüft. Geänderte, ausgeglichene oder ersetzte Forderungen führen zum Verwerfen des alten Versandauftrags.

Der Lauf arbeitet die überfälligen Forderungen über Datenbankseiten ab. Fehler einzelner Forderungen verhindern nicht die weiteren Prüfungen. Unvollständige Empfängerangaben werden im manuellen Laufergebnis erklärt; es wird dabei keine Mahnstufe ohne den angeforderten Versandauftrag gebucht. Der Scheduler lässt sich über `app.pms.billing.scheduler.enabled` und `app.pms.billing.scheduler.interval-ms` steuern (Vorgabe fünf Minuten). Mahngebühren oder rechtliche Zwangsschritte werden nicht aus einem E-Mail-Versand abgeleitet.

## Kontrollierter Bankimport

Der Import unterstützt ein ausdrücklich definiertes UTF-8-CSV-Format, maximal 5 MB und 2000 Transaktionen. Die Kopfzeile muss genau lauten:

```csv
bankAccount;transactionId;bookingDate;currency;amount;reference;debtorName
MAIN;BANK-2026-000001;2026-09-12;CHF;140.50;INV-2026-00123;Beispiel AG
```

Trennzeichen ist das Semikolon. Doppelte Anführungszeichen begrenzen Felder; ein Anführungszeichen innerhalb eines Feldes wird verdoppelt. Datumswerte sind ISO-Daten, Geldbeträge verwenden einen Dezimalpunkt und die tatsächlich unterstützten Nachkommastellen der Währung. Zukünftige Banktage, fehlerhafte Daten und widersprüchliche Wiederverwendungen einer Transaktionsreferenz werden abgelehnt. Ein identischer Datei-Hash lädt denselben Import erneut. Bereits bekannte, identische Banktransaktionen einer anderen Datei werden nicht erneut übernommen; die Zahl neuer Zeilen und ausgelassener Duplikate bleibt im Import/Audit sichtbar.

Der Schlüssel ist Hotel + Bankkonto + externe Transaktions-ID. Vorschläge beruhen auf einer exakten Rechnungsnummer oder QR-Referenz, identischer Währung und ausreichendem offenem Betrag. Ein Namensähnlichkeitstreffer allein verbucht nichts. Mitarbeitende wählen die Forderungen und bestätigen den Bankeingang. Eine Bestätigung ist atomar: Bei falscher Währung, Überzahlung oder ungültiger Auswahl wird die Auswahl nicht teilweise verbucht. Wiederholte Bestätigung derselben Zuordnung erzeugt keinen zweiten Ausgleich.

Positive Gutschriften lassen sich einer Forderung zuordnen. Negative und Nullbeträge werden als ignoriert aufbewahrt und erzeugen keine automatische Erstattung. Die Bestätigung schreibt den bestehenden Debitorenausgleich auf den aktuellen offenen Betriebstag; das ursprüngliche Bankdatum bleibt als Quellenangabe erhalten. CAMT ist nicht als zusätzlich unterstütztes Format ausgewiesen. Eine Bank kann dieses CSV bereitstellen oder es kann in einem vorgelagerten, kontrollierten Export erzeugt werden.

## Buchhaltungsexporte

Ein Exportlauf speichert den gewählten Zeitraum `[von, bis ausschließlich)`, Auftragsschlüssel, exakt erzeugte CSV-Datei, Prüfsumme, Erstellungszeitpunkt und Bearbeiter. Die vorhandene konten- und steuergenaue Exportlogik bleibt maßgeblich. Unbekannte historische Steuersätze blockieren weiterhin einen fachlich nicht bestätigbaren Export.

Ein erneuter Aufruf mit gleichem Auftragsschlüssel und Zeitraum lädt denselben Lauf. Ein Download verändert den Status nicht. Erst eine ausdrücklich gespeicherte Empfangs-/Buchungsreferenz setzt `ACKNOWLEDGED`; eine widersprüchliche zweite Referenz wird abgelehnt. Dies dokumentiert die Übernahme in eine Buchhaltung und ersetzt keinen anbieterspezifischen Übertragungskanal.

## Rechte und Verzeichnisse

Versand, Mahnungen, Bankimporte und Exportjournale prüfen Hotel- und FINANCE-Rechte; Änderungen benötigen FINANCE MANAGE. Hotel-Versandregeln benötigen zusätzlich den PMS-Master. Firmen- und Gruppensuche sind paginiert und mandantengebunden. Bereits gewählte Firmen außerhalb der Suchseite werden gezielt mitsamt Ansprechpartnern und Rechnungsprofil nachgeladen. Der Masterbereich für Mitarbeiterrechte bleibt davon getrennt.

## Prüfbarkeit

`PmsBillingAutomationIntegrationTest` prüft die dauerhafte Queue, echte MIME-Anhänge, einen ausschließlich auf 127.0.0.1 erreichbaren Test-SMTP-Server, unklare Zustellung, Wiederholung, Hotelpause, Mahnfristen, geänderte Salden, Bankimport ohne vorzeitige Buchung, Deduplizierung, Währungs-/Überzahlungsablehnung und unveränderliche Exportläufe. Die Advanced-Integration prüft PDF-Ausgabe, Sprachstand und Gutschrift in allen fünf Sprachen. Frontendtests prüfen ausdrücklich bestätigte Bankzuordnung, Empfängersnapshot bei unklarer Antwort, Rollen, Mahn-/Versandbedienung sowie die Firmenauswahl über Seitengrenzen. Aktuelle Ausführungsergebnisse werden im zentralen Umsetzungsbericht ergänzt; diese Beschreibung erfindet keinen zusätzlichen Live-Anbieter- oder Produktionsnachweis.
