# Chrono PMS: Zimmerplan, Firmenprofile und internationale Konfiguration

Stand: 12. September 2026. Dieses Dokument beschreibt die internationale Konfiguration und ergänzt den [aktuellen Enterprise-Implementierungsstand](pms-enterprise-implementation.md). Die Erweiterungen setzen die beschriebenen Betriebsabläufe in Frontend, API und Datenbank um. Sie erweitern das mandantengetrennte Mehrhotel-PMS; eine pauschale weltweite steuerliche Zertifizierung ist damit nicht verbunden.

## Zimmerplan und große Hotels

- Fortlaufende Zimmer-/Tagesmatrix mit fixierten Zimmernummern und Datumskopf im Format `Mo. 07.09`, farbigen Zimmerkategorien, Aufenthalten und Sperren.
- Beim Öffnen beginnt der Plan am aktuellen Tag in der Zeitzone des Hotels. Standard sind 30 Tage; wählbar sind 7, 10, 14, 30, 60 und 90 Tage. „Heute“ folgt auch nach Mitternacht dem Hoteldatum.
- Suche nach Zimmernummer, Name, Kategorie und Ausstattung. Filter für Zimmertyp, Bettenart, Etage, Kapazität, Reinigungsbereich, Housekeeping, Betriebsstatus, inaktive Zimmer und vollständige Verfügbarkeit im Zeitraum.
- Mehrere Ausstattungen werden gemeinsam verlangt: beispielsweise Badewanne **und** ruhig. Die auswählbaren Merkmale stammen aus den in der Hoteleinrichtung gepflegten Zimmermerkmalen.
- Abreisetage geben das Zimmer frei. Bestätigte Aufenthalte, Optionen, Gäste im Haus, historische Aufenthalte und technische Sperren sind unterscheidbar. Reservierungen können geöffnet und innerhalb derselben Kategorie verschoben werden; das Backend prüft die Verfügbarkeit erneut.
- Die Zimmerabfrage ist datenbankseitig auf 25/50/100 Zimmer pro Seite und höchstens 90 Tage begrenzt. Reservierungen und Sperren werden nur für die angefragten Zimmer und überlappenden Tage geladen. Der normale Dashboardstart lädt kompakte Hotel-/Kategoriedaten statt sämtlicher Zimmer der Kette.

## Persönliche Masterkonten

Vorhandene Administratorkonten bekommen die separate Berechtigung `pmsSettings` als Standard, sofern das PMS-Modul freigeschaltet ist. Für Änderungen sind sowohl `pms: MANAGE` als auch `pmsSettings: MANAGE` erforderlich. Eine explizite Entziehung bleibt wirksam. Normale Mitarbeiter können sich die Masterberechtigung auch durch ein manipuliertes Berechtigungsfeld nicht verschaffen.

Hotel-/Zimmerstammdaten, Buchungsmaschinen- und Kurtaxeneinstellungen, Schnittstellenkonfiguration, Vorlagen, Kontenpläne sowie die zentrale Ratenveröffentlichung bleiben Masteraktionen. Lokale Raten und Tagesrestriktionen können Mitarbeiter mit `RATES: MANAGE` für das betreffende Hotel bearbeiten. Firmen-/Ratenvertragsdokumente können Mitarbeiter mit `GUESTS: MANAGE` im ausdrücklich angegebenen Hotel verwalten, wenn die Firma dort über eine Reservierung oder Firmenrate zugeordnet ist. Es wird kein gemeinsames Masterpasswort angelegt.

Hotelzugriff ist innerhalb des Mandanten zusätzlich einzeln zugewiesen. Die Matrix trennt Rezeption, Gäste/Firmen, Housekeeping, Finanzen, Rückerstattungen, Raten, Berichte und Schnittstellen jeweils in Lesen und Bearbeiten. Mitarbeiter ohne Hotelzuweisung erhalten keinen Hotelzugriff; ein globales `pms: VIEW` begrenzt auch weitergehende Hotelzuweisungen auf Lesen. Master verwalten diese Zuweisungen im PMS. Gemeinsame Antworten, Portfolioauswertungen und Suchabfragen werden nach diesen Rechten eingeschränkt; Housekeeping ohne Rezeptionsrecht erhält keine Gastidentitäten aus Belegungen. Details und API-Vertrag stehen in [PMS hotel access](pms-property-access.md).

## Raten und Steuern

Raten speichern Netto- oder Bruttoeingabe, Steuerprozent, getrennten Frühstücksanteil mit eigenem Steuersatz, enthaltene Erwachsene, Erwachsenen-/Kinderzuschläge, Aufenthalts- und Verkaufszeiträume, Mindest-/Höchstaufenthalt, Vorausbuchungsgrenzen, Firmenbindung sowie Storno-, No-Show-, Anzahlungs- und Zahlungsbedingungen. Gebühren benötigen einen ausdrücklich konfigurierten Steuersatz, auch wenn dieser null Prozent beträgt. Tagespreise, Stop-Sell und An-/Abreisesperren bleiben verfügbar.

Verfügbarkeit, Reservierung und Gastkonto verwenden dieselbe Preisberechnung. Übernachtung, enthaltenes Frühstück und POS-Leistungen übernehmen den jeweils verwendeten Steuerprozentsatz auf die Position. Persönliche Gast- oder Firmenländer ändern diesen Satz nicht automatisch. Unkonfigurierte Altraten bleiben als solche erkennbar; ein Steuersatz wird nicht stillschweigend aus einem anderen Land übernommen.

Firmentarife werden intern anhand des verknüpften Gastes bzw. für eine Angebotsabfrage anhand der ausgewählten Firma geprüft. Die abschließende Buchung prüft die tatsächliche Gästefirma erneut. Auf der öffentlichen Buchungsseite werden Firmenraten nicht ausgegeben. Belegungsabhängige Zuschläge fließen dort bereits in die angezeigte Gesamtsumme ein.

Neue Reservierungen speichern die akzeptierten Fristen, Gebühren, Anzahlungsquote und Fälligkeit als Snapshot. Die Aufenthaltsansicht zeigt fällige/offene Anzahlungen sowie die aktuell vereinbarte Storno-/No-Show-Gebühr. Bei der entsprechenden Statusänderung verarbeitet das System diese Gebühr auf dem Gastkonto. Bereits fakturierte oder abgeschlossene Leistungen bleiben erhalten und verlangen gegebenenfalls eine Gutschrift; fehlende Alt-Snapshots führen nicht zur rückwirkenden Anwendung aktueller Ratenregeln. Die Anzahlungsquote löst allein keinen Kartenabbuchungsauftrag aus.

PMS-Geldbeträge folgen den kleinsten Einheiten der Hotelwährung (ISO-Nachkommastellen von null bis vier); Migration V35 erweitert die Speicherung entsprechend. Eingaben unterhalb der kleinsten Einheit werden abgewiesen, berechnete Beträge passend gerundet. Beispiele sind JPY ohne Nachkommastellen und KWD mit drei. Gästeherkunft und Firmenanschrift ändern die Abrechnungswährung nicht. Anbieterspezifische Karten-Wire-Formate werden separat übersetzt; die Verfügbarkeit einer Währung beim Zahlungsanbieter bleibt gesondert zu prüfen.

Master können eine gespeicherte Rate gezielt an andere Hotels desselben Mandanten veröffentlichen. Pro Ziel werden Kategorie, Zielcode, Hotelwährung, lokale Preise und Steuersätze ausdrücklich angegeben. Bestehende Zielraten werden nur bei bewusster Auswahl ihrer ID überschrieben. Die Veröffentlichung ist atomar; sie schätzt weder Wechselkurse noch lokale Steuern und verändert keine akzeptierten Reservierungssnapshots.

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

Migrationen V23–V26 installieren Zimmerplanindex, erweiterte Raten-/Steuerdaten, Profile und Dokumentablage sowie Rechnungsstammdaten. Die darauf aufbauenden Enterprise-Migrationen ab V27 ergänzen unter anderem Finanzperioden, Zahlungs- und Reservierungssnapshots, Hotelzugriffsrechte, unabhängige Housekeeping-Aufträge, Gruppen-/Veranstaltungsabrechnung, Währungspräzision und Hotelkontenpläne. Sie werden regulär über Flyway angewendet. Bestandsdaten werden ergänzt; bestehende Migrationen bleiben unverändert. Der Restore-Test leitet die erwartete Schema-Version aus den im Release gelieferten Migrationen ab.

Für nichtlateinische PDF-Texte kann eine geeignete, lizenzierte Schrift über `app.pms.invoice.font-path` eingebettet werden. Die Standard-PDF-Schrift deckt nicht alle Schriftsysteme ab. Ungültige konfigurierte Schriften oder fehlende Zeichen führen zu einer verständlichen Fehlermeldung statt unbemerkt verlorenen Zeichen.

Vor einem weltweiten produktiven Rollout bleiben Länderanbindungen erforderlich: insbesondere verpflichtende elektronische Rechnungsformate/Fiskalisierung, komplexe oder zusammengesetzte Abgaben, nationale Ausnahmen und Meldescheine. Die Währungspräzision ist implementiert; sie ersetzt keine lokale steuerliche oder anbieterbezogene Abnahme. Die zwei Adressformate bilden keinen vollständigen UPU-S42-Länderkatalog ab.

Die Zimmermatrix, Hotelzugriffsabfragen, Debitorenlisten und historischen Ansichten sind datenbankseitig begrenzt oder paginiert. Ein gemischtes Mitarbeiter-Lastprofil liegt unter `ops/load/pms-staff.js` vor; dessen [Runbook](operations/PMS_STAFF_LOAD.md) beschreibt das isolierte Testverfahren und die Messgrößen. Ein erfolgreicher Lauf gegen die tatsächliche Produktionsgröße und Infrastruktur ist damit nicht belegt. Filter-Metadaten und die explizit geöffnete Hoteleinrichtung sind noch nicht vollständig seitenweise geladen. Stripe-Checkout, Vorautorisierung, Einzug, Freigabe und Erstattungsabgleich sind implementiert; konkrete Anbieterzulassungen, produktive Schlüssel und länderspezifische Meldungen bleiben externe Integrationsarbeit. Siehe [Provider integrations](operations/PROVIDER_INTEGRATIONS.md).

## Historischer Prüfstand der Ausgangserweiterung vom 8. September

Die folgenden Zahlen gehören zur ursprünglichen Erweiterung V23–V26 und beschreiben nicht den aktuellen Enterprise-Prüflauf. Aktuelle Prüfungen und noch offene Betriebsnachweise werden im [Enterprise-Implementierungsstand](pms-enterprise-implementation.md) festgehalten.

- Vollständiger PMS-Backendlauf: 136 Tests bestanden, ein MySQL-Integrationstest wegen fehlender separater MySQL-Testdatenbank übersprungen. Die neuen Migrationen wurden mit H2 und beim Start der lokalen Testanwendung geprüft.
- Nach der abschließenden Erweiterung der Buchungsprüfung: alle 25 Frontdesk-Service- und Controller-Tests bestanden. Darin enthalten sind zwölf zusätzliche Fälle für die Übernahme privater/geschäftlicher/zusätzlicher E-Mail-Adressen und die Wiederholung von Buchungsanfragen mit erweiterten Profildaten.
- Vollständige PMS-Frontendtests: 87 Tests bestanden. Nach den letzten Änderungen an E-Mail-Auswahl und lokalem Datenschutzstatus wurden alle 27 Tests der drei betroffenen Testdateien erfolgreich erneut ausgeführt; darin ist ein zusätzlicher Test enthalten.
- Abschließender Frontend-Produktionsbuild erfolgreich; `git diff --check` ohne Fehler.
- Browserprüfung mit isolierten Testdaten: Zimmermatrix auf Desktop und Mobilgerät, Wechsel auf zehn Tage, erneutes Öffnen mit 30 Tagen sowie Speichern der erweiterten Gästedaten. Kein horizontaler Überlauf der Gesamtseite auf dem geprüften mobilen Bildschirm.

Die Änderungen liegen im lokalen Projekt. Eine Bereitstellung auf einem Produktivsystem wurde nicht vorgenommen.
