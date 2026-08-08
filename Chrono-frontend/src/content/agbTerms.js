const p = (text) => `<p>${text}</p>`;

const list = (items) => `
    <ul>
        ${items.map((item) => `<li>${item}</li>`).join("\n")}
    </ul>
`;

const section = (title, blocks) => `
    <section class="legal-section">
        <h2>${title}</h2>
        ${blocks.join("\n")}
    </section>
`;

const providerBlock = section("Anbieter", [
    p(`
        <strong>Chrono-Logisch</strong><br />
        Einzelunternehmen<br />
        Inhaber: Christopher Siefert<br />
        Lettenstrasse 20<br />
        CH-9122 Mogelsberg, Schweiz<br />
        E-Mail: <a href="mailto:siefertchristopher@chrono-logisch.ch">siefertchristopher@chrono-logisch.ch</a><br />
        Telefon: <a href="tel:+41765467960">+41 76 546 79 60</a><br />
        UID / Handelsregister-Nr. / USt-ID: nicht angegeben
    `),
    p(`Nachfolgend "Chrono", "Anbieter" oder "wir" genannt.`),
]);

const summaryBlock = section("Kurzüberblick", [
    p(`Dieser Kurzüberblick dient nur der besseren Verständlichkeit. Maßgeblich sind ausschließlich die vollständigen AGB.`),
    p(`Chrono ist eine modulare SaaS-Plattform für Unternehmen. Je nach gebuchtem Paket unterstützt Chrono insbesondere bei Zeiterfassung, Personalverwaltung, Urlaub, Abwesenheiten, Dienstplanung, Lohnabrechnung, Finanzprozessen, CRM, Lager, Supply Chain, Reporting und KI-gestützter Assistenz.`),
    p(`Chrono stellt technische Funktionen bereit. Der Kunde bleibt verantwortlich für die Richtigkeit seiner Daten, Eingaben, Lohninformationen, Mitarbeiterdaten, steuerlichen Angaben, Freigaben und gesetzlichen Pflichten.`),
    p(`Chrono richtet sich an Unternehmen und Organisationen, nicht an private Verbraucher.`),
]);

const sections = [
    section("1. Geltungsbereich", [
        p(`1.1 Diese Allgemeinen Geschäftsbedingungen regeln die Nutzung der SaaS-Plattform Chrono durch Kunden.`),
        p(`1.2 Diese AGB gelten für alle Verträge zwischen Chrono und dem Kunden über die Bereitstellung der Software, einschließlich Testphasen, kostenpflichtiger Abonnements, Zusatzmodule, Supportleistungen, Einrichtungsleistungen und sonstiger ergänzender Leistungen.`),
        p(`1.3 Chrono richtet sich ausschließlich an Unternehmen, Selbstständige, juristische Personen, Vereine, Behörden und sonstige Organisationen. Eine Nutzung durch Verbraucher zu privaten Zwecken ist nicht vorgesehen. Kunden mit Sitz in Deutschland bestätigen, als Unternehmer im Sinne von § 14 BGB zu handeln.`),
        p(`1.4 Abweichende, entgegenstehende oder ergänzende Bedingungen des Kunden gelten nur, wenn Chrono ihrer Geltung ausdrücklich in Textform zustimmt.`),
        p(`1.5 Individuelle Vereinbarungen, Angebote oder Auftragsformulare haben Vorrang vor diesen AGB. Danach gelten, soweit vorhanden, der Auftragsbearbeitungsvertrag / AVV / DPA, diese AGB, die Leistungsbeschreibung und die Produktdokumentation.`),
    ]),
    section("2. Begriffe", [
        p(`2.1 Kunde ist die natürliche oder juristische Person, die Chrono für ein Unternehmen, eine Organisation oder eine berufliche Tätigkeit nutzt oder bucht.`),
        p(`2.2 Benutzer sind Personen, denen der Kunde Zugriff auf Chrono gewährt, zum Beispiel Mitarbeitende, Admins, Manager, Payroll-Verantwortliche, externe Dienstleister oder Berater.`),
        p(`2.3 Kundendaten sind alle Daten, Inhalte, Dokumente, Dateien, Personalinformationen, Arbeitszeiten, Abwesenheiten, Korrekturen, Lohninformationen, Bankdaten, Kundeninformationen, Produktdaten, Finanzdaten und sonstigen Informationen, die der Kunde oder seine Benutzer in Chrono eingeben, speichern, abrufen oder verarbeiten.`),
        p(`2.4 Module sind einzelne Funktionsbereiche von Chrono, zum Beispiel Zeiterfassung, Urlaub, Payroll, Dienstplan, CRM, Banking, Lager, Supply Chain, Analytics oder KI-Assistenz.`),
        p(`2.5 Paket bezeichnet den vom Kunden gebuchten Funktionsumfang, einschließlich der gewählten Module, Benutzeranzahl, Speichergrenzen, Supportoptionen und Zusatzleistungen.`),
        p(`2.6 Auftragsbearbeitung / Auftragsverarbeitung bezeichnet die Verarbeitung personenbezogener Daten durch Chrono im Auftrag des Kunden.`),
    ]),
    section("3. Vertragsschluss und Registrierung", [
        p(`3.1 Der Vertrag kommt zustande, wenn der Kunde ein Angebot von Chrono annimmt, sich online registriert, eine Bestellung absendet oder ein kostenpflichtiges Paket aktiviert und Chrono diese Bestellung annimmt.`),
        p(`3.2 Chrono kann Registrierungen oder Bestellungen ablehnen, insbesondere wenn Angaben unvollständig oder offensichtlich fehlerhaft sind, Missbrauchsverdacht besteht oder die Bereitstellung technisch, wirtschaftlich oder rechtlich nicht zumutbar ist.`),
        p(`3.3 Vor Abschluss eines kostenpflichtigen Vertrags muss der Kunde die Möglichkeit haben, seine Eingaben zu prüfen und zu korrigieren.`),
        p(`3.4 Die AGB werden Vertragsbestandteil, wenn der Kunde vor Vertragsschluss auf sie hingewiesen wird, sie in zumutbarer Weise abrufen, speichern und ausdrucken kann und ihrer Geltung zustimmt.`),
        p(`3.5 Bei Online-Bestellungen erhält der Kunde nach Abgabe der Bestellung eine elektronische Bestätigung. Diese Bestätigung kann zugleich die Vertragsannahme darstellen, sofern dies aus ihr hervorgeht.`),
    ]),
    section("4. Testphase", [
        p(`4.1 Chrono kann kostenlose oder vergünstigte Testphasen anbieten.`),
        p(`4.2 Eine Testphase dient ausschließlich dazu, Chrono kennenzulernen und zu prüfen.`),
        p(`4.3 Sofern nicht ausdrücklich anders vereinbart, endet eine kostenlose Testphase automatisch. Ein Übergang in ein kostenpflichtiges Abonnement erfolgt nur, wenn der Kunde aktiv ein kostenpflichtiges Paket auswählt oder bestellt.`),
        p(`4.4 Während einer Testphase kann der Funktionsumfang eingeschränkt sein. Chrono kann Testzugänge jederzeit beschränken oder beenden, wenn Missbrauch, Sicherheitsrisiken oder rechtswidrige Nutzung vermutet werden.`),
    ]),
    section("5. Leistungsgegenstand", [
        p(`5.1 Chrono stellt dem Kunden eine webbasierte, modulare Unternehmensplattform als Software-as-a-Service bereit.`),
        p(`5.2 Je nach gebuchtem Paket kann Chrono insbesondere folgende Funktionen umfassen:`),
        list([
            "Zeiterfassung und Arbeitszeitverwaltung",
            "NFC- oder App-gestützte Stempelfunktionen",
            "Urlaubs-, Krankheits-, Abwesenheits- und Korrekturanträge",
            "Personalverwaltung und Benutzerverwaltung",
            "Rollen- und Rechteverwaltung",
            "Dienstplanung und Schichtverwaltung",
            "Lohnlauf, Lohnabrechnung und lohnbezogene Auswertungen",
            "Rechnungs-, Finanz- und Zahlungsfunktionen",
            "CRM-, Kunden- und Vertriebsfunktionen",
            "Produkt-, Lager- und Supply-Chain-Funktionen",
            "Reporting, Analytics und Exporte",
            "Firmenwissen, Dokumente und KI-gestützte Assistenzfunktionen",
            "Schnittstellen, Importe, Exporte und Integrationen",
        ]),
        p(`5.3 Der konkrete Leistungsumfang ergibt sich aus dem gebuchten Paket, dem Angebot, der Preisliste, der Leistungsbeschreibung oder der jeweils aktivierten Modulkonfiguration.`),
        p(`5.4 Nicht gebuchte oder nicht aktivierte Module sind nicht Vertragsbestandteil.`),
        p(`5.5 Chrono schuldet die Bereitstellung der technischen Plattform. Chrono schuldet keine Steuerberatung, Rechtsberatung, Treuhandberatung, arbeitsrechtliche Beratung, medizinische Beratung oder Finanzberatung.`),
    ]),
    section("6. Weiterentwicklung und Änderungen der Plattform", [
        p(`6.1 Chrono wird laufend weiterentwickelt. Der Anbieter darf Funktionen verbessern, erweitern, ändern, ersetzen oder entfernen, sofern die wesentliche Kernfunktionalität des gebuchten Pakets nicht unangemessen beeinträchtigt wird.`),
        p(`6.2 Änderungen können insbesondere erforderlich sein wegen technischer Weiterentwicklung, Sicherheitsanforderungen, rechtlicher Änderungen, Anpassungen von Drittanbietern, Systemstabilität oder Produktverbesserungen.`),
        p(`6.3 Wesentliche Änderungen, die die Nutzung des gebuchten Pakets erheblich einschränken, werden dem Kunden rechtzeitig mitgeteilt. Ist eine solche Änderung für den Kunden unzumutbar, kann der Kunde den Vertrag zum Zeitpunkt des Wirksamwerdens der Änderung kündigen.`),
        p(`6.4 Chrono kann einzelne Funktionen als Beta-, Vorschau- oder Testfunktionen kennzeichnen. Solche Funktionen können unvollständig sein, Fehler enthalten oder wieder entfernt werden.`),
    ]),
    section("7. Benutzerkonten, Rollen und Berechtigungen", [
        p(`7.1 Der Kunde ist verantwortlich für die Verwaltung seiner Benutzer, Rollen, Rechte, Passwörter und Zugänge.`),
        p(`7.2 Der Kunde muss sicherstellen, dass nur berechtigte Personen Zugriff auf Chrono erhalten und dass Berechtigungen dem tatsächlichen Aufgabenbereich der jeweiligen Benutzer entsprechen.`),
        p(`7.3 Zugänge von ausgeschiedenen Mitarbeitenden oder nicht mehr berechtigten Personen sind unverzüglich zu deaktivieren.`),
        p(`7.4 Der Kunde ist verantwortlich für alle Handlungen, die über seine Benutzerkonten erfolgen, soweit diese Handlungen nicht auf eine Pflichtverletzung von Chrono zurückzuführen sind.`),
        p(`7.5 Der Kunde muss Zugangsdaten geheim halten und angemessene Sicherheitsmaßnahmen treffen, insbesondere sichere Passwörter, Rollenbeschränkungen und, soweit verfügbar, Zwei-Faktor-Authentifizierung.`),
        p(`7.6 Chrono darf sicherheitsrelevante Aktionen protokollieren, insbesondere Logins, Rechteänderungen, Exporte, Payroll-Freigaben, Zahlungsfreigaben, Datenänderungen und administrative Vorgänge.`),
    ]),
    section("8. Nutzungsrechte", [
        p(`8.1 Chrono räumt dem Kunden für die Dauer des Vertrags ein einfaches, nicht ausschließliches, nicht übertragbares und nicht unterlizenzierbares Recht ein, die Plattform im Rahmen des gebuchten Pakets zu nutzen.`),
        p(`8.2 Der Kunde erhält kein Eigentum an der Software, dem Quellcode, den Marken, Designs, Datenbanken, Schnittstellen oder sonstigen Bestandteilen von Chrono.`),
        p(`8.3 Der Kunde darf Chrono nicht:`),
        list([
            "kopieren, vermieten, verkaufen, unterlizenzieren oder Dritten unberechtigt bereitstellen,",
            "zurückentwickeln, dekompilieren oder den Quellcode ableiten, soweit dies gesetzlich nicht zwingend erlaubt ist,",
            "technische Schutzmaßnahmen umgehen,",
            "Sicherheitsmechanismen deaktivieren,",
            "unzulässige automatisierte Zugriffe, Scraping oder Lasttests durchführen,",
            "für rechtswidrige, missbräuchliche oder sicherheitsgefährdende Zwecke verwenden,",
            "zur Entwicklung eines konkurrierenden Produkts durch Nachbau wesentlicher Funktionen nutzen.",
        ]),
        p(`8.4 Rechte an Feedback, Vorschlägen oder Verbesserungsideen des Kunden darf Chrono unentgeltlich zur Verbesserung der Plattform verwenden, soweit dadurch keine vertraulichen Informationen oder personenbezogenen Daten offengelegt werden.`),
    ]),
    section("9. Pflichten des Kunden", [
        p(`9.1 Der Kunde ist verantwortlich für die Rechtmäßigkeit, Richtigkeit und Vollständigkeit der Kundendaten.`),
        p(`9.2 Der Kunde verpflichtet sich insbesondere:`),
        list([
            "nur rechtmäßig erhobene und verarbeitbare Daten in Chrono einzutragen,",
            "Mitarbeitende und sonstige betroffene Personen ordnungsgemäß über die Nutzung von Chrono zu informieren,",
            "erforderliche Rechtsgrundlagen, Einwilligungen oder arbeitsvertragliche Grundlagen selbst sicherzustellen,",
            "Rollen und Zugriffsrechte sorgfältig zu vergeben,",
            "Lohn-, Steuer-, Sozialversicherungs-, Bank- und Mitarbeiterdaten zu prüfen,",
            "gesetzliche Aufbewahrungs- und Nachweispflichten eigenständig einzuhalten,",
            "keine Malware, Bots, Spam, rechtswidrige Inhalte oder unzulässige Daten zu übertragen,",
            "Chrono nicht zur heimlichen, unverhältnismäßigen oder rechtswidrigen Überwachung von Mitarbeitenden zu nutzen,",
            "Sicherheitsvorfälle, verdächtige Zugriffe oder Datenpannen unverzüglich an Chrono zu melden.",
        ]),
        p(`9.3 Der Kunde ist verpflichtet, seine Systeme, Geräte, Browser, Netzwerke und Zugangsdaten angemessen zu sichern.`),
        p(`9.4 Der Kunde ist für Internetzugang, Endgeräte, Browser, lokale Software, NFC-Geräte, Drucker, Schnittstellen und sonstige Infrastruktur verantwortlich, sofern diese nicht ausdrücklich von Chrono bereitgestellt werden.`),
    ]),
    section("10. Zeiterfassung, Arbeitszeiten und Auswertungen", [
        p(`10.1 Chrono unterstützt den Kunden bei der technischen Erfassung, Verwaltung und Auswertung von Arbeitszeiten.`),
        p(`10.2 Der Kunde bleibt verantwortlich für die korrekte Einrichtung von Arbeitszeitmodellen, Sollzeiten, Pausenregeln, Überstundenregeln, Abwesenheiten, Feiertagen, Schichten und Genehmigungsprozessen.`),
        p(`10.3 Chrono kann Salden, Überstunden, Fehlzeiten, Abwesenheiten und Reports automatisch berechnen. Diese Berechnungen beruhen auf den vom Kunden hinterlegten Daten und Einstellungen.`),
        p(`10.4 Der Kunde ist verpflichtet, Auswertungen, Salden und Berichte vor Verwendung, Freigabe, Weitergabe oder Abrechnung zu prüfen.`),
        p(`10.5 Chrono übernimmt keine Gewähr dafür, dass eine bestimmte arbeitsrechtliche Bewertung, Betriebsvereinbarung, Gesamtarbeitsvertrag, Tarifvertrag oder interne Regel des Kunden automatisch vollständig abgebildet ist, sofern dies nicht ausdrücklich vereinbart wurde.`),
    ]),
    section("11. Lohnabrechnung und Payroll", [
        p(`11.1 Soweit Chrono Payroll-, Lohnlauf- oder Lohnabrechnungsfunktionen bereitstellt, handelt es sich um technische Unterstützungsfunktionen zur Vorbereitung, Berechnung, Dokumentation und Verwaltung von Lohnabrechnungen.`),
        p(`11.2 Chrono erbringt keine Rechtsberatung, Steuerberatung, Treuhandberatung, Sozialversicherungsberatung oder arbeitsrechtliche Beratung.`),
        p(`11.3 Der Kunde bleibt allein verantwortlich für:`),
        list([
            "die Richtigkeit der Mitarbeiterdaten,",
            "die Richtigkeit von Lohnbestandteilen, Boni, Abzügen, Zuschlägen und Spesen,",
            "die korrekte Einrichtung von Arbeitszeit- und Lohnmodellen,",
            "Steuerdaten, Quellensteuerdaten, Sozialversicherungsdaten, AHV-/IV-/EO-/ALV-Daten, Krankenkassen-, Unfallversicherungs- und Pensionskassendaten,",
            "deutsche Steuer-, Sozialversicherungs-, Krankenkassen- und Abrechnungsdaten,",
            "Bankverbindungen und Zahlungsdaten,",
            "die Prüfung aller Lohnabrechnungen vor Freigabe oder Auszahlung,",
            "die Einhaltung gesetzlicher Melde-, Abrechnungs-, Zahlungs- und Aufbewahrungspflichten.",
        ]),
        p(`11.4 Automatisch erzeugte Lohnabrechnungen, Reports, Exporte oder Zahlungsvorschläge sind vom Kunden vor Verwendung zu prüfen.`),
        p(`11.5 Chrono übernimmt keine Gewähr dafür, dass automatisch erzeugte Lohnabrechnungen, Berechnungen, Abzüge oder Reports ohne fachliche Prüfung für den konkreten Einzelfall des Kunden vollständig rechtlich, steuerlich oder sozialversicherungsrechtlich korrekt sind.`),
        p(`11.6 Änderungen gesetzlicher Vorgaben, Beitragssätze, kantonaler Regelungen, deutscher Abrechnungsregeln oder sonstiger Parameter können Anpassungen erforderlich machen. Der Kunde ist verantwortlich, die für ihn geltenden Regeln zu prüfen und Chrono über notwendige individuelle Einstellungen zu informieren.`),
    ]),
    section("12. Zahlungsverkehr, Banking und Finanzfunktionen", [
        p(`12.1 Soweit Chrono Zahlungsfunktionen, Zahlungsdateien, Bankanbindungen, Freigabeprozesse, Zahlungsvorschläge oder Exportdateien bereitstellt, dienen diese der technischen Unterstützung des Kunden.`),
        p(`12.2 Die tatsächliche Ausführung von Zahlungen erfolgt durch den Kunden, dessen Bank, Zahlungsdienstleister oder sonstige Drittanbieter.`),
        p(`12.3 Der Kunde ist verantwortlich für die Prüfung von Empfänger, IBAN, Betrag, Währung, Fälligkeit, Zahlungszweck, Freigabe und Zahlungsdatei.`),
        p(`12.4 Chrono haftet nicht für fehlerhafte Zahlungen, soweit diese auf falschen Kundendaten, fehlerhaften Freigaben, Bankfehlern, Drittanbieterfehlern oder mangelnder Prüfung durch den Kunden beruhen.`),
        p(`12.5 Chrono ist kein Kreditinstitut, Zahlungsinstitut, Treuhänder oder Finanzberater, sofern nicht ausdrücklich und schriftlich anders vereinbart.`),
    ]),
    section("13. KI-Funktionen", [
        p(`13.1 Chrono kann KI-gestützte Funktionen bereitstellen, zum Beispiel zur Beantwortung von Fragen, Zusammenfassung von Informationen, Unterstützung bei Analysen, Suche im Firmenwissen, Erklärung von Funktionen oder Unterstützung im Arbeitsalltag.`),
        p(`13.2 KI-Ausgaben können fehlerhaft, unvollständig, veraltet, missverständlich oder für den konkreten Einzelfall ungeeignet sein.`),
        p(`13.3 KI-Ausgaben stellen keine Rechtsberatung, Steuerberatung, arbeitsrechtliche Beratung, medizinische Beratung, Finanzberatung oder verbindliche Handlungsempfehlung dar.`),
        p(`13.4 Der Kunde ist verpflichtet, KI-Ausgaben vor geschäftskritischer Verwendung zu prüfen, insbesondere bei Lohnabrechnungen, Personalentscheidungen, Arbeitszeiten, Kündigungen, Krankheitsfällen, Zahlungen, steuerlichen Fragen oder rechtlichen Bewertungen.`),
        p(`13.5 Chrono trifft keine automatisierten rechtlich erheblichen Entscheidungen über Mitarbeitende oder sonstige betroffene Personen, sofern dies nicht ausdrücklich vereinbart und rechtlich zulässig ist.`),
        p(`13.6 Kundendaten werden nicht zum Training allgemein verfügbarer KI-Grundmodelle verwendet, sofern dies nicht ausdrücklich vereinbart oder in der Datenschutzerklärung transparent beschrieben ist.`),
        p(`13.7 Der Kunde ist verantwortlich dafür, keine Daten in KI-Funktionen einzugeben, die er nicht rechtmäßig verarbeiten oder offenlegen darf.`),
    ]),
    section("14. Preise und Zahlungsbedingungen", [
        p(`14.1 Die Preise ergeben sich aus dem Angebot, der Preisliste, der Bestellung oder dem gebuchten Paket.`),
        p(`14.2 Alle Preise verstehen sich, sofern nicht anders angegeben, zuzüglich gesetzlicher Steuern, insbesondere Mehrwertsteuer, Umsatzsteuer oder sonstiger Abgaben.`),
        p(`14.3 Die Abrechnung erfolgt je nach Paket monatlich, jährlich oder nach individueller Vereinbarung im Voraus.`),
        p(`14.4 Die Vergütung kann sich insbesondere nach Anzahl aktiver Benutzer, Mitarbeitender, Mandanten, Modulen, Speicherumfang, Transaktionen, Supportumfang oder Zusatzleistungen richten.`),
        p(`14.5 Rechnungen sind innerhalb der auf der Rechnung angegebenen Zahlungsfrist fällig. Fehlt eine Zahlungsfrist, sind Rechnungen innerhalb von 14 Tagen ab Rechnungsdatum fällig.`),
        p(`14.6 Bei Zahlungsverzug kann Chrono nach Mahnung und angemessener Frist den Zugang ganz oder teilweise sperren. Die Zahlungspflicht bleibt während der Sperrung bestehen.`),
        p(`14.7 Chrono kann für Rücklastschriften, fehlgeschlagene Zahlungen oder manuelle Mahnungen angemessene Kosten verlangen, soweit der Kunde den Grund zu vertreten hat.`),
        p(`14.8 Preisänderungen für laufende Abonnements werden mindestens 30 Tage vor Wirksamwerden mitgeteilt. Der Kunde kann den Vertrag vor Wirksamwerden der Preisänderung zum Ende des laufenden Abrechnungszeitraums kündigen. Erfolgt keine Kündigung und nutzt der Kunde Chrono weiter, gelten die geänderten Preise ab dem angekündigten Zeitpunkt.`),
        p(`14.9 Individuelle Einrichtungs-, Import-, Anpassungs-, Schulungs- oder Beratungsleistungen werden gesondert vergütet, sofern sie nicht ausdrücklich im Paket enthalten sind.`),
    ]),
    section("15. Verfügbarkeit, Wartung und Störungen", [
        p(`15.1 Chrono bemüht sich um eine hohe Verfügbarkeit der Plattform.`),
        p(`15.2 Eine bestimmte Mindestverfügbarkeit ist nur geschuldet, wenn sie in einem gesonderten Service Level Agreement oder Paket ausdrücklich vereinbart wurde.`),
        p(`15.3 Nicht als Ausfallzeiten gelten insbesondere:`),
        list([
            "geplante Wartungsarbeiten,",
            "Notfallwartungen,",
            "Störungen von Internetverbindungen,",
            "Störungen bei Hosting-, E-Mail-, Zahlungs-, Banking-, KI- oder sonstigen Drittanbietern,",
            "Störungen der Systeme des Kunden,",
            "Angriffe, DDoS, Malware oder Sicherheitsvorfälle,",
            "höhere Gewalt,",
            "vom Kunden verursachte Störungen,",
            "Sperrungen wegen Vertragsverstößen oder Sicherheitsrisiken.",
        ]),
        p(`15.4 Geplante Wartungsarbeiten werden nach Möglichkeit vorab angekündigt.`),
        p(`15.5 Chrono darf kurzfristige Notfallwartungen durchführen, wenn dies zur Sicherheit, Integrität oder Stabilität der Plattform erforderlich ist.`),
    ]),
    section("16. Support", [
        p(`16.1 Chrono stellt Support über die angebotenen Supportkanäle bereit, zum Beispiel E-Mail, Ticketsystem, In-App-Support oder Chat.`),
        p(`16.2 Umfang, Zeiten und Priorität des Supports richten sich nach dem gebuchten Paket.`),
        p(`16.3 Sofern kein Service Level Agreement vereinbart ist, erfolgen Antworten nach bestem Bemühen während der üblichen Geschäftszeiten.`),
        p(`16.4 Support umfasst grundsätzlich Hilfe zur Nutzung der Plattform. Nicht umfasst sind, sofern nicht ausdrücklich vereinbart:`),
        list([
            "individuelle Rechts-, Steuer- oder Treuhandberatung,",
            "individuelle Payroll-Prüfung,",
            "Datenbereinigung,",
            "kundenspezifische Entwicklung,",
            "Schulungen,",
            "Migrationen,",
            "Schnittstellenentwicklung,",
            "Einrichtung komplexer Arbeitszeit-, Lohn- oder Rechtekonzepte.",
        ]),
        p(`16.5 Chrono kann für umfangreiche Zusatzleistungen ein gesondertes Angebot erstellen.`),
    ]),
    section("17. Kundendaten, Rechte an Daten und Inhalte", [
        p(`17.1 Alle Rechte an Kundendaten verbleiben beim Kunden oder den jeweiligen Berechtigten.`),
        p(`17.2 Chrono erhält nur die Rechte, die erforderlich sind, um die Plattform bereitzustellen, zu betreiben, zu sichern, zu warten, Support zu leisten und vertragliche Pflichten zu erfüllen.`),
        p(`17.3 Chrono darf anonymisierte oder aggregierte Daten zur Verbesserung, Analyse, Sicherheit und Weiterentwicklung der Plattform verwenden, sofern kein Rückschluss auf den Kunden, Benutzer oder einzelne Personen möglich ist.`),
        p(`17.4 Der Kunde ist verantwortlich dafür, dass Kundendaten keine Rechte Dritter verletzen und rechtmäßig verarbeitet werden dürfen.`),
        p(`17.5 Chrono ist nicht verpflichtet, Kundendaten inhaltlich zu prüfen.`),
    ]),
    section("18. Datenexport, Backups und Löschung", [
        p(`18.1 Der Kunde kann Kundendaten während der Vertragslaufzeit im Rahmen der verfügbaren Funktionen exportieren, zum Beispiel als CSV, Excel, PDF, JSON oder über Schnittstellen, soweit im jeweiligen Modul verfügbar.`),
        p(`18.2 Nach Vertragsende stellt Chrono dem Kunden für 30 Tage einen angemessenen Zugang zum Export der Kundendaten bereit, sofern keine Sperrung aus wichtigem Grund entgegensteht.`),
        p(`18.3 Nach Ablauf dieser Frist kann Chrono Kundendaten löschen oder anonymisieren, soweit keine gesetzlichen Aufbewahrungspflichten, berechtigten Interessen oder vertraglichen Pflichten entgegenstehen.`),
        p(`18.4 Daten in Backups werden nach den üblichen Backup-Zyklen gelöscht oder überschrieben. Eine sofortige Löschung aus allen Backups ist technisch nicht immer möglich.`),
        p(`18.5 Backups dienen primär der Wiederherstellung des Plattformbetriebs im Störungsfall. Sie ersetzen nicht die eigene Archivierung oder gesetzliche Aufbewahrung durch den Kunden.`),
        p(`18.6 Der Kunde ist verantwortlich, rechtzeitig alle Daten zu exportieren und gesetzliche Aufbewahrungspflichten einzuhalten, insbesondere für Buchhaltung, Lohnabrechnung, Personalakten, Steuern, Sozialversicherungen und arbeitsrechtliche Nachweise.`),
        p(`18.7 Abrechnungs-, Vertrags-, Zahlungs- und Geschäftsdaten von Chrono können auch nach Vertragsende gespeichert bleiben, soweit dies zur Erfüllung gesetzlicher Pflichten oder zur Durchsetzung von Ansprüchen erforderlich ist.`),
    ]),
    section("19. Datenschutz und Auftragsbearbeitung", [
        p(`19.1 Soweit Chrono personenbezogene Daten im Auftrag des Kunden verarbeitet, handelt Chrono als Auftragsbearbeiter nach Schweizer Datenschutzrecht und als Auftragsverarbeiter im Sinne der DSGVO, soweit die DSGVO anwendbar ist.`),
        p(`19.2 Der Kunde bleibt Verantwortlicher für die Rechtmäßigkeit der Verarbeitung personenbezogener Daten, insbesondere von Mitarbeiterdaten, Arbeitszeiten, Abwesenheiten, Krankheitsdaten, Lohninformationen, Bankdaten, Steuerdaten, Sozialversicherungsdaten und sonstigen personenbezogenen Daten.`),
        p(`19.3 Der Kunde ist verantwortlich für die Information seiner Mitarbeitenden, Benutzer, Kunden und sonstigen betroffenen Personen sowie für die erforderlichen Rechtsgrundlagen der Datenverarbeitung.`),
        p(`19.4 Die Einzelheiten der Auftragsbearbeitung / Auftragsverarbeitung werden in einem separaten Auftragsbearbeitungsvertrag / AVV / DPA geregelt. Dieser ist Bestandteil des Vertrags, soweit Chrono personenbezogene Daten im Auftrag des Kunden verarbeitet.`),
        p(`19.5 Die Datenschutzerklärung von Chrono informiert über Datenverarbeitungen, bei denen Chrono selbst verantwortlich ist, zum Beispiel Website, Kontaktformular, Registrierung, Abrechnung, Support und eigene Sicherheitsprozesse.`),
        p(`19.6 Der Kunde darf personenbezogene Daten nur in Chrono verarbeiten, wenn er dazu berechtigt ist.`),
    ]),
    section("20. Subprozessoren und Datenübermittlung ins Ausland", [
        p(`20.1 Chrono darf Subprozessoren einsetzen, insbesondere für Hosting, Infrastruktur, E-Mail-Versand, Zahlungsabwicklung, Support, Fehleranalyse, Monitoring, Sicherheit, KI-Funktionen und sonstige technische Dienste.`),
        p(`20.2 Eine aktuelle Liste der Subprozessoren wird dem Kunden online oder auf Anfrage bereitgestellt. Diese Liste sollte mindestens Name, Zweck, Sitz/Land und betroffene Leistung enthalten.`),
        p(`20.3 Chrono informiert Kunden über wesentliche Änderungen bei Subprozessoren, soweit dies rechtlich erforderlich ist. Der Kunde kann aus berechtigten datenschutzrechtlichen Gründen widersprechen.`),
        p(`20.4 Kann ein berechtigter Widerspruch nicht ausgeräumt werden, kann der Kunde den betroffenen Dienst kündigen.`),
        p(`20.5 Erfolgt eine Übermittlung personenbezogener Daten in Staaten ohne angemessenes Datenschutzniveau, trifft Chrono geeignete Garantien, insbesondere anerkannte Standardvertragsklauseln oder gleichwertige Schutzmaßnahmen, soweit gesetzlich erforderlich.`),
        p(`20.6 Weitere Einzelheiten ergeben sich aus dem Auftragsbearbeitungsvertrag und der Datenschutzerklärung.`),
    ]),
    section("21. Vertraulichkeit", [
        p(`21.1 Beide Parteien verpflichten sich, vertrauliche Informationen der jeweils anderen Partei geheim zu halten und nur zur Durchführung des Vertrags zu verwenden.`),
        p(`21.2 Vertrauliche Informationen sind insbesondere:`),
        list([
            "Kundendaten,",
            "Lohn- und Personaldaten,",
            "Geschäftsgeheimnisse,",
            "Zugangsdaten,",
            "technische Informationen,",
            "Vertragsinhalte,",
            "Preise,",
            "nicht öffentliche Produktinformationen,",
            "Sicherheitsinformationen,",
            "interne Dokumente und Unternehmenswissen.",
        ]),
        p(`21.3 Die Vertraulichkeitspflicht gilt nicht für Informationen, die:`),
        list([
            "öffentlich bekannt sind,",
            "der empfangenden Partei bereits rechtmäßig bekannt waren,",
            "rechtmäßig von Dritten erhalten wurden,",
            "unabhängig entwickelt wurden,",
            "aufgrund gesetzlicher Pflichten, gerichtlicher oder behördlicher Anordnung offengelegt werden müssen.",
        ]),
        p(`21.4 Die Vertraulichkeitspflicht gilt auch nach Vertragsende fort.`),
    ]),
    section("22. Sicherheit, Missbrauch und Sperrung", [
        p(`22.1 Chrono trifft angemessene technische und organisatorische Maßnahmen zum Schutz der Plattform.`),
        p(`22.2 Chrono kann den Zugang des Kunden oder einzelner Benutzer ganz oder teilweise sperren, wenn:`),
        list([
            "ein erhebliches Sicherheitsrisiko besteht,",
            "Zugangsdaten kompromittiert wurden,",
            "gesetzliche Pflichten oder behördliche Anordnungen dies erfordern,",
            "der Kunde gegen wesentliche Vertragspflichten verstößt,",
            "Zahlungsverzug trotz Mahnung besteht,",
            "die Nutzung den Betrieb, die Sicherheit oder andere Kunden gefährdet,",
            "rechtswidrige oder missbräuchliche Nutzung vorliegt.",
        ]),
        p(`22.3 Chrono beschränkt eine Sperrung nach Möglichkeit auf den betroffenen Teil der Plattform.`),
        p(`22.4 Chrono informiert den Kunden über die Sperrung, soweit keine rechtlichen, sicherheitsrelevanten oder behördlichen Gründe entgegenstehen.`),
        p(`22.5 Der Kunde bleibt zur Zahlung verpflichtet, soweit die Sperrung auf einem vom Kunden zu vertretenden Umstand beruht.`),
    ]),
    section("23. Drittanbieter, Schnittstellen und Integrationen", [
        p(`23.1 Chrono kann Schnittstellen zu Drittanbietern bereitstellen, zum Beispiel Banken, Zahlungsdiensten, E-Mail-Diensten, Kalendern, KI-Diensten, Buchhaltungssystemen, CRM-Systemen, Hostingdiensten oder sonstigen externen Anwendungen.`),
        p(`23.2 Für Drittanbieter können zusätzliche Bedingungen, Datenschutzinformationen und Gebühren gelten.`),
        p(`23.3 Chrono haftet nicht für Leistungen, Verfügbarkeit, Änderungen, Fehler oder Datenverarbeitungen von Drittanbietern, soweit diese außerhalb des Einflussbereichs von Chrono liegen.`),
        p(`23.4 Der Kunde ist verantwortlich für die Prüfung, Aktivierung, Konfiguration und Berechtigung von Drittanbieter-Integrationen.`),
        p(`23.5 Wird eine Schnittstelle durch einen Drittanbieter geändert, eingeschränkt oder eingestellt, kann Chrono die betroffene Integration anpassen, einschränken oder beenden.`),
    ]),
    section("24. Gewährleistung", [
        p(`24.1 Chrono stellt die Plattform in dem Zustand bereit, der dem gebuchten Paket, der Leistungsbeschreibung und dem aktuellen technischen Stand der Plattform entspricht.`),
        p(`24.2 Chrono gewährleistet nicht, dass die Plattform jederzeit fehlerfrei, unterbrechungsfrei oder für jeden individuellen Zweck des Kunden geeignet ist.`),
        p(`24.3 Der Kunde ist verpflichtet, erkennbare Mängel, Fehler oder Störungen unverzüglich zu melden und Chrono bei der Fehleranalyse angemessen zu unterstützen.`),
        p(`24.4 Chrono kann Mängel durch Fehlerbehebung, Updates, Workarounds, Konfigurationshinweise oder andere geeignete Maßnahmen beheben.`),
        p(`24.5 Chrono übernimmt keine Gewähr für Fehler, die auf falschen Kundendaten, fehlerhaften Einstellungen, unsachgemäßer Nutzung, Drittanbietern, lokalen Systemen, Browsern, Netzwerkproblemen oder unzulässigen Änderungen durch den Kunden beruhen.`),
    ]),
    section("25. Haftung", [
        p(`25.1 Chrono haftet unbeschränkt für Schäden aus Vorsatz und grober Fahrlässigkeit sowie bei Verletzung von Leben, Körper oder Gesundheit.`),
        p(`25.2 Bei leichter Fahrlässigkeit haftet Chrono nur bei Verletzung wesentlicher Vertragspflichten. Wesentliche Vertragspflichten sind Pflichten, deren Erfüllung die ordnungsgemäße Durchführung des Vertrags überhaupt erst ermöglicht und auf deren Einhaltung der Kunde regelmäßig vertrauen darf.`),
        p(`25.3 Bei leichter Fahrlässigkeit ist die Haftung auf den vorhersehbaren, vertragstypischen Schaden begrenzt.`),
        p(`25.4 Soweit gesetzlich zulässig, ist die Haftung für mittelbare Schäden, Folgeschäden, entgangenen Gewinn, ausgebliebene Einsparungen, Betriebsunterbrechungen, Reputationsschäden, Datenverlust aufgrund fehlender Mitwirkung des Kunden und Schäden aus fehlerhaften Eingaben ausgeschlossen.`),
        p(`25.5 Für Datenverlust haftet Chrono nur insoweit, als der Schaden auch bei angemessener Datensicherung, Exportstrategie und Mitwirkung des Kunden entstanden wäre.`),
        p(`25.6 Für fehlerhafte Lohnabrechnungen, Zahlungen, steuerliche Meldungen, arbeitsrechtliche Bewertungen oder Sozialversicherungsangaben haftet Chrono nicht, soweit diese auf falschen oder unvollständigen Kundendaten, Einstellungen, Freigaben oder fehlender Prüfung durch den Kunden beruhen.`),
        p(`25.7 Die Haftungsbeschränkungen gelten nicht, soweit zwingendes Recht entgegensteht.`),
        p(`25.8 Soweit eine betragsmäßige Haftungsbegrenzung vereinbart werden darf und keine unbeschränkte Haftung nach diesen AGB besteht, ist die Haftung von Chrono pro Vertragsjahr auf die vom Kunden in den letzten zwölf Monaten vor Schadenseintritt gezahlten Entgelte begrenzt.`),
    ]),
    section("26. Höhere Gewalt", [
        p(`26.1 Chrono haftet nicht für Verzögerungen, Ausfälle oder Leistungsstörungen, die durch Ereignisse außerhalb des zumutbaren Einflussbereichs von Chrono verursacht werden.`),
        p(`26.2 Dazu gehören insbesondere Naturereignisse, Krieg, Terror, Pandemien, Streiks, behördliche Maßnahmen, Stromausfälle, großflächige Internetstörungen, Ausfälle von Rechenzentren, Cyberangriffe, DDoS-Angriffe, Störungen von Drittanbietern oder sonstige Ereignisse höherer Gewalt.`),
        p(`26.3 Chrono wird den Kunden über wesentliche Auswirkungen informieren, soweit dies möglich und zumutbar ist.`),
    ]),
    section("27. Vertragslaufzeit und Kündigung", [
        p(`27.1 Die Vertragslaufzeit richtet sich nach dem gebuchten Paket, dem Angebot oder der Bestellung.`),
        p(`27.2 Sofern nichts anderes vereinbart ist, läuft der Vertrag für den jeweiligen Abrechnungszeitraum und verlängert sich automatisch um denselben Zeitraum, wenn er nicht vor Ablauf gekündigt wird.`),
        p(`27.3 Der Kunde kann den Vertrag jederzeit mit Wirkung zum Ende des laufenden Abrechnungszeitraums kündigen.`),
        p(`27.4 Chrono kann den Vertrag mit einer Frist von 30 Tagen zum Ende des laufenden Abrechnungszeitraums kündigen.`),
        p(`27.5 Das Recht zur fristlosen Kündigung aus wichtigem Grund bleibt unberührt.`),
        p(`27.6 Ein wichtiger Grund liegt insbesondere vor, wenn:`),
        list([
            "der Kunde trotz Mahnung erheblich in Zahlungsverzug ist,",
            "der Kunde wiederholt oder schwerwiegend gegen diese AGB verstößt,",
            "rechtswidrige oder missbräuchliche Nutzung vorliegt,",
            "der Betrieb oder die Sicherheit der Plattform gefährdet wird,",
            "die Bereitstellung aus rechtlichen Gründen unmöglich oder unzumutbar wird.",
        ]),
        p(`27.7 Nach Vertragsende endet das Nutzungsrecht des Kunden. Die Regelungen zu Datenexport, Löschung, Vertraulichkeit, Datenschutz, Zahlungspflichten, Haftung und Schlussbestimmungen bleiben bestehen, soweit sie ihrem Zweck nach fortgelten.`),
    ]),
    section("28. Änderungen dieser AGB", [
        p(`28.1 Chrono kann diese AGB ändern, wenn dies aufgrund rechtlicher, technischer, wirtschaftlicher oder produktbezogener Entwicklungen erforderlich ist.`),
        p(`28.2 Wesentliche Änderungen werden dem Kunden mindestens 30 Tage vor Inkrafttreten mitgeteilt.`),
        p(`28.3 Ist eine Änderung für den Kunden unzumutbar, kann der Kunde den Vertrag bis zum Inkrafttreten der Änderung zum Ende des laufenden Abrechnungszeitraums kündigen.`),
        p(`28.4 Änderungen, die den Vertragsinhalt nicht wesentlich betreffen oder ausschließlich zugunsten des Kunden erfolgen, können mit Veröffentlichung oder Mitteilung wirksam werden.`),
    ]),
    section("29. Elektronische Kommunikation", [
        p(`29.1 Chrono darf vertragsrelevante Mitteilungen per E-Mail, innerhalb der Plattform oder über andere elektronische Kommunikationswege übermitteln.`),
        p(`29.2 Der Kunde ist verpflichtet, eine aktuelle E-Mail-Adresse zu hinterlegen und regelmäßig zu prüfen.`),
        p(`29.3 Rechnungen können elektronisch bereitgestellt werden.`),
        p(`29.4 Rechtlich erhebliche Mitteilungen gelten als zugegangen, wenn sie an die zuletzt vom Kunden hinterlegte E-Mail-Adresse gesendet oder innerhalb der Plattform bereitgestellt wurden, soweit gesetzlich zulässig.`),
    ]),
    section("30. Referenzen und öffentliche Nennung", [
        p(`30.1 Chrono darf den Namen, das Logo oder sonstige Kennzeichen des Kunden nur dann öffentlich als Referenz verwenden, wenn der Kunde dem ausdrücklich zugestimmt hat.`),
        p(`30.2 Eine einmal erteilte Zustimmung kann der Kunde jederzeit mit Wirkung für die Zukunft widerrufen.`),
    ]),
    section("31. Abtretung und Übertragung", [
        p(`31.1 Der Kunde darf Rechte und Pflichten aus dem Vertrag nur mit vorheriger Zustimmung von Chrono auf Dritte übertragen.`),
        p(`31.2 Chrono darf den Vertrag auf ein verbundenes Unternehmen, einen Rechtsnachfolger oder Erwerber des Geschäftsbereichs übertragen, sofern dem Kunden dadurch keine wesentlichen Nachteile entstehen.`),
        p(`31.3 Chrono darf Subunternehmer und Dienstleister einsetzen, sofern die vertraglichen und datenschutzrechtlichen Anforderungen eingehalten werden.`),
    ]),
    section("32. Anwendbares Recht und Gerichtsstand", [
        p(`32.1 Es gilt Schweizer Recht unter Ausschluss des Kollisionsrechts und des UN-Kaufrechts, soweit keine zwingenden gesetzlichen Vorschriften entgegenstehen.`),
        p(`32.2 Für Kunden mit Sitz in Deutschland oder der Europäischen Union bleiben zwingende datenschutzrechtliche Vorschriften, insbesondere die DSGVO, unberührt, soweit sie anwendbar sind.`),
        p(`32.3 Gerichtsstand ist, soweit gesetzlich zulässig, der Sitz des Anbieters.`),
        p(`32.4 Chrono ist berechtigt, Ansprüche auch am allgemeinen Gerichtsstand des Kunden geltend zu machen.`),
    ]),
    section("33. Schlussbestimmungen", [
        p(`33.1 Änderungen und Ergänzungen des Vertrags bedürfen der Textform, soweit nicht zwingendes Recht eine andere Form vorschreibt.`),
        p(`33.2 Individuelle Vereinbarungen haben Vorrang vor diesen AGB.`),
        p(`33.3 Sollte eine Bestimmung dieser AGB unwirksam oder undurchführbar sein oder werden, bleibt die Wirksamkeit der übrigen Bestimmungen unberührt.`),
        p(`33.4 Anstelle der unwirksamen oder undurchführbaren Bestimmung gelten die gesetzlichen Vorschriften. Die Parteien werden sich bemühen, eine wirksame Regelung zu vereinbaren, die dem wirtschaftlichen Zweck der unwirksamen Regelung möglichst nahekommt, soweit dies rechtlich zulässig ist.`),
        p(`33.5 Bei Widersprüchen zwischen Sprachfassungen ist die deutsche Fassung maßgeblich, sofern nicht ausdrücklich anders vereinbart.`),
    ]),
];

const content = [
    `<p class="legal-section legal-updated"><strong>Stand: 30. April 2026</strong><br />Version: 1.0</p>`,
    providerBlock,
    summaryBlock,
    ...sections,
].join("\n");

export const deAgbPage = {
    title: "Allgemeine Geschäftsbedingungen für Chrono",
    kicker: "Rechtliches & Vertrauen",
    lead: "Verbindliche Rahmenbedingungen für die Nutzung der modularen Chrono SaaS-Plattform.",
    updated: "Stand: 30. April 2026",
    summaryHint: "Version 1.0",
    highlightTitle: "Wichtig auf einen Blick",
    highlight1: "Chrono richtet sich an Unternehmen und Organisationen, nicht an private Verbraucher.",
    highlight2: "Chrono stellt technische Funktionen bereit; Kundendaten und Freigaben bleiben Verantwortung des Kunden.",
    highlight3: "Datenexport ist während der Laufzeit möglich; nach Vertragsende gibt es grundsätzlich 30 Tage Exportzugang.",
    highlight4: "Schweizer Recht gilt; zwingender Datenschutz, insbesondere DSGVO, bleibt unberührt.",
    contactTitle: "Fragen?",
    contactText: "Schreiben Sie uns per E-Mail oder nutzen Sie die angebotenen Supportkanäle.",
    content,
};

export const enAgbPage = {
    ...deAgbPage,
    title: "Terms and Conditions for Chrono",
    kicker: "Legal & Trust",
    lead: "The German terms below are authoritative for the modular Chrono SaaS platform.",
    updated: "Updated: 30 April 2026",
    summaryHint: "Version 1.0",
    highlightTitle: "Key points",
    highlight1: "Chrono is intended for businesses and organisations, not private consumers.",
    highlight2: "Chrono provides technical features; customer data, settings and approvals remain the customer's responsibility.",
    highlight3: "Data can be exported during the contract; after termination there is generally a 30-day export window.",
    highlight4: "Swiss law applies; mandatory data protection law, especially GDPR where applicable, remains unaffected.",
    contactTitle: "Questions?",
    contactText: "Contact us by email or through the available support channels.",
};
