# Chrono PMS: Zimmerplan, Firmenprofile und internationale Konfiguration

Stand: 8. September 2026. Diese Erweiterung setzt die beschriebenen Betriebsabläufe in Frontend, API und Datenbank um. Sie erweitert das vorhandene mandantengetrennte Mehrhotel-PMS; eine pauschale weltweite steuerliche Zertifizierung ist damit nicht verbunden.

## Zimmerplan und große Hotels

- Fortlaufende Zimmer-/Tagesmatrix mit fixierten Zimmernummern und Datumskopf im Format `Mo. 07.09`, farbigen Zimmerkategorien, Aufenthalten und Sperren.
- Beim Öffnen beginnt der Plan am aktuellen Tag in der Zeitzone des Hotels. Standard sind 30 Tage; wählbar sind 7, 10, 14, 30, 60 und 90 Tage. „Heute“ folgt auch nach Mitternacht dem Hoteldatum.
- Suche nach Zimmernummer, Name, Kategorie und Ausstattung. Filter für Zimmertyp, Bettenart, Etage, Kapazität, Reinigungsbereich, Housekeeping, Betriebsstatus, inaktive Zimmer und vollständige Verfügbarkeit im Zeitraum.
- Mehrere Ausstattungen werden gemeinsam verlangt: beispielsweise Badewanne **und** ruhig. Die auswählbaren Merkmale stammen aus den in der Hoteleinrichtung gepflegten Zimmermerkmalen.
- Abreisetage geben das Zimmer frei. Bestätigte Aufenthalte, Optionen, Gäste im Haus, historische Aufenthalte und technische Sperren sind unterscheidbar. Reservierungen können geöffnet und innerhalb derselben Kategorie verschoben werden; das Backend prüft die Verfügbarkeit erneut.
- Die Zimmerabfrage ist datenbankseitig auf 25/50/100 Zimmer pro Seite und höchstens 90 Tage begrenzt. Reservierungen und Sperren werden nur für die angefragten Zimmer und überlappenden Tage geladen. Der normale Dashboardstart lädt kompakte Hotel-/Kategoriedaten statt sämtlicher Zimmer der Kette.

## Persönliche Masterkonten

Vorhandene Administratorkonten bekommen die separate Berechtigung `pmsSettings` als Standard, sofern das PMS-Modul freigeschaltet ist. Für Änderungen sind sowohl `pms: MANAGE` als auch `pmsSettings: MANAGE` erforderlich. Eine explizite Entziehung bleibt wirksam. Normale Mitarbeiter können sich die Masterberechtigung auch durch ein manipuliertes Berechtigungsfeld nicht verschaffen.

Damit sind Hotel-/Zimmerstammdaten, Raten und Tagesrestriktionen, Buchungsmaschinen- und Kurtaxeneinstellungen, Schnittstellenkonfiguration, Vorlagen sowie Änderungen an Vertragsdokumenten geschützt. Operative Berechtigungen für Reservierungen, Gästebetreuung und Housekeeping bleiben getrennt. Zuweisung erfolgt über die bestehende Benutzerverwaltung; es wird kein gemeinsames Masterpasswort angelegt.

## Raten und Steuern

Raten speichern Netto- oder Bruttoeingabe, Steuerprozent, getrennten Frühstücksanteil mit eigenem Steuersatz, enthaltene Erwachsene, Erwachsenen-/Kinderzuschläge, Aufenthalts- und Verkaufszeiträume, Mindest-/Höchstaufenthalt, Vorausbuchungsgrenzen, Firmenbindung sowie Storno-, Anzahlungs- und Zahlungsbedingungen. Tagespreise, Stop-Sell und An-/Abreisesperren bleiben verfügbar.

Verfügbarkeit, Reservierung und Gastkonto verwenden dieselbe Preisberechnung. Übernachtung, enthaltenes Frühstück und POS-Leistungen übernehmen den jeweils verwendeten Steuerprozentsatz auf die Position. Persönliche Gast- oder Firmenländer ändern diesen Satz nicht automatisch. Unkonfigurierte Altraten bleiben als solche erkennbar; ein Steuersatz wird nicht stillschweigend aus einem anderen Land übernommen.

Firmentarife werden intern anhand des verknüpften Gastes bzw. für eine Angebotsabfrage anhand der ausgewählten Firma geprüft. Die abschließende Buchung prüft die tatsächliche Gästefirma erneut. Auf der öffentlichen Buchungsseite werden Firmenraten nicht ausgegeben. Belegungsabhängige Zuschläge fließen dort bereits in die angezeigte Gesamtsumme ein.

Storno-/Anzahlungsbedingungen dokumentieren die Vereinbarung. Automatische Abbuchungen oder Stornogebühren werden durch diese Felder allein nicht ausgelöst.

## Gäste, Firmen, Rechnungsadressen und Dokumente

- Private, geschäftliche und zusätzliche E-Mail-Adressen; eigene Ernährungshinweise für etwa laktosefreie Kost; optionale VAT-/Tax-ID für geschäftlich handelnde Gäste.
- Mehrere Firmenkontakte mit Funktion, Kontaktinformationen, Hauptkontakt und optionaler Verknüpfung zu einer Gästekartei. Im Gastprofil ist der gewünschte Firmenkontakt auswählbar.
- Strukturierte Rechnungsprofile mit rechtlichem Empfänger, zu Händen, Abteilung/Adresszusatz, Straße, PLZ, Ort, Region, Land, Steuer-ID, Rechnungs-E-Mail, Bestellreferenz, Kostenstelle und Zusatztext.
- Firma vor Person oder Person vor Firma sowie `PLZ Ort / Region` oder `Ort Region PLZ` sind einstellbar und werden als Adressvorschau gezeigt. Individuelle Mitarbeiterabweichungen erben nicht ausgefüllte Angaben aus dem passenden Firmenprofil.
- Das Hotel pflegt eigene Steuer-/Registerangaben, Adresszusatz und Region, Rechnungsnummernpräfix, Zahlungsziel und Fußtext. Ausgestellte Rechnungen speichern Empfänger- und Hotelangaben, Leistungsdaten sowie Steuerbeträge je Position dauerhaft als Snapshot; spätere Profiländerungen verändern ausgestellte Dokumente nicht. Gutschriften übernehmen diese Werte.
- Firmenverträge und Ratenvereinbarungen können als PDF, PNG oder JPEG hinterlegt und einem Ratenplan zugeordnet werden. Uploads prüfen Endung und Dateisignatur. Inhalte bleiben mandantengetrennt und sind nur über authentifizierte Downloads abrufbar. Listen laden nur Metadaten. Aktuelle Grenzen: 4 MB pro Datei, 50 Dateien pro Firma, 250 MB pro Mandant.
- Neue Gästefelder werden beim Datenschutzexport berücksichtigt und bei Anonymisierung entfernt.

Die gespeicherte Rechnungs-E-Mail ist ein Empfängermerkmal. Dieses Update führt keinen automatischen Rechnungsversand ein.

## Recherchegrundlage

- [Mews Timeline](https://help.mews.com/s/article/timeline): Zimmer-/Tagesdarstellung, Merkmalsfilter sowie Belegungs- und Housekeepingfarben.
- [Oracle OPERA: Raten konfigurieren](https://docs.oracle.com/en/industries/hospitality/opera-cloud/26.2/ocsuh/t_creating_a_new_rate_code.htm): Buchungs-/Aufenthaltszeiträume, Belegungssteuerung, Restriktionen und Pakete.
- [Oracle OPERA: Profile](https://docs.oracle.com/en/industries/hospitality/opera-cloud/22.5/ocsuh/t_managing_profiles.htm): Firmenkontakte, verknüpfte Profile, Präferenzen, vereinbarte Raten und Anhänge.
- [EU-Kommission: Rechnungspflichtangaben](https://taxation-customs.ec.europa.eu/taxation/vat/vat-businesses/invoicing_en): unter anderem Identität und Anschrift, Rechnungs-/Leistungsdatum, Nummer, Leistungsbeschreibung, Bemessungsgrundlage, Steuerangaben und besondere Hinweise je Fall.
- [SECO/Schweizer KMU-Portal: Rechnungsstellung](https://www.kmu.admin.ch/de/rechnungsstellung): Lieferanten-/Empfängerdaten, MWST-Nummer, Leistung und Preis-/Steuerangaben.
- [EU-Kommission: Ort der Besteuerung](https://taxation-customs.ec.europa.eu/taxation/vat/vat-directive/place-taxation_en): grundstücksbezogene Leistungen werden im einschlägigen EU-Fall am Ort des Grundstücks besteuert; eine ausländische Firmenadresse führt bei Hotelaufenthalten deshalb nicht pauschal zu Reverse Charge.
- [UPU: internationale Adressierung](https://www.upu.int/en/Postal-Solutions/Programmes-Services/Addressing-Solutions): strukturierte Adressbestandteile und länderspezifische Vorlagen; es gibt keine weltweit identische postalische Reihenfolge.

## Auslieferung und konkrete Grenzen

Migrationen V23–V26 installieren Zimmerplanindex, Raten-/Steuersnapshots, erweiterte Profile und Dokumentablage sowie Rechnungsstammdaten. Sie werden regulär über Flyway angewendet. Bestandsdaten werden ergänzt; bestehende Migrationen bleiben unverändert.

Für nichtlateinische PDF-Texte kann eine geeignete, lizenzierte Schrift über `app.pms.invoice.font-path` eingebettet werden. Die Standard-PDF-Schrift deckt nicht alle Schriftsysteme ab. Ungültige konfigurierte Schriften oder fehlende Zeichen führen zu einer verständlichen Fehlermeldung statt unbemerkt verlorenen Zeichen.

Vor einem weltweiten produktiven Rollout bleiben Länderanbindungen erforderlich: insbesondere verpflichtende elektronische Rechnungsformate/Fiskalisierung, komplexe oder zusammengesetzte Abgaben, nationale Ausnahmen und Meldescheine. Die bestehende Geldspeicherung verwendet zwei Nachkommastellen; vollständige Unterstützung von Währungen mit abweichenden kleinsten Einheiten benötigt eine gesonderte Umstellung des gesamten PMS-Finanzbereichs. Die zwei Adressformate bilden keinen vollständigen UPU-S42-Länderkatalog ab.

Die Zimmermatrix ist begrenzt und kompakte Hotelketten-Daten sind geprüft. Ein Lasttest mit realistischen parallelen Arbeitsplätzen, sehr großen Datenbeständen und der produktiven MySQL-Infrastruktur wurde nicht durchgeführt. Die bestehenden Berechtigungen gelten auf Mandantenebene; getrennte Zugriffszuweisungen je einzelnes Hotel innerhalb desselben Mandanten sind eine weitere Ausbaustufe. Filter-Metadaten und die explizit geöffnete Hoteleinrichtung sind noch nicht vollständig seitenweise geladen.

## Durchgeführte Prüfung

- Vollständiger PMS-Backendlauf: 136 Tests bestanden, ein MySQL-Integrationstest wegen fehlender separater MySQL-Testdatenbank übersprungen. Die neuen Migrationen wurden mit H2 und beim Start der lokalen Testanwendung geprüft.
- Nach der abschließenden Erweiterung der Buchungsprüfung: alle 25 Frontdesk-Service- und Controller-Tests bestanden. Darin enthalten sind zwölf zusätzliche Fälle für die Übernahme privater/geschäftlicher/zusätzlicher E-Mail-Adressen und die Wiederholung von Buchungsanfragen mit erweiterten Profildaten.
- Vollständige PMS-Frontendtests: 87 Tests bestanden. Nach den letzten Änderungen an E-Mail-Auswahl und lokalem Datenschutzstatus wurden alle 27 Tests der drei betroffenen Testdateien erfolgreich erneut ausgeführt; darin ist ein zusätzlicher Test enthalten.
- Abschließender Frontend-Produktionsbuild erfolgreich; `git diff --check` ohne Fehler.
- Browserprüfung mit isolierten Testdaten: Zimmermatrix auf Desktop und Mobilgerät, Wechsel auf zehn Tage, erneutes Öffnen mit 30 Tagen sowie Speichern der erweiterten Gästedaten. Kein horizontaler Überlauf der Gesamtseite auf dem geprüften mobilen Bildschirm.

Die Änderungen liegen im lokalen Projekt. Eine Bereitstellung auf einem Produktivsystem wurde nicht vorgenommen.
