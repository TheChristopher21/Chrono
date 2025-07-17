// src/pages/AGB.jsx
import React from 'react';
import Navbar from '../components/Navbar';
import '../styles/LegalPages.css';

const AGB = () => {
    return (
        <>
        <Navbar />
        <div className="legal-wrapper">
        <div className="legal-page">
            <h1>Allgemeine Geschäftsbedingungen (AGB)</h1>

            <h2>1. Geltungsbereich</h2>
            <p>
                Diese Allgemeinen Geschäftsbedingungen (AGB) gelten für alle Verträge
                zwischen <strong>Chrono-Logisch</strong> (nachfolgend „Anbieter“) und
                dem jeweiligen Vertragspartner (nachfolgend „Kunde“) über die Nutzung
                der Zeiterfassungs-App und aller damit zusammenhängenden Leistungen.
                Abweichende Bedingungen des Kunden werden nur dann Vertragsbestandteil,
                wenn der Anbieter diesen ausdrücklich schriftlich zugestimmt hat.
            </p>

            <h2>2. Vertragsgegenstand</h2>
            <p>
                Der Anbieter stellt dem Kunden eine Zeiterfassungs-App („Chrono-Logisch“)
                zur Verfügung, die u. a. folgende Funktionen beinhaltet:
            </p>
            <ul>
                <li>NFC-Stempel-System (Smart Punch)</li>
                <li>Automatische Erkennung von Arbeitspausen und Arbeitsschritten</li>
                <li>Korrektur- und Admin-Funktionen (z. B. Bearbeitung von Einträgen)</li>
                <li>Berichte &amp; Historienverwaltung</li>
                <li>Backup-Funktion (wöchentliche Datensicherung)</li>
            </ul>
            <p>
                Der genaue Funktionsumfang und die angebotenen Pakete (Small, Basic,
                Professional) ergeben sich aus den Informationen auf der Webseite des
                Anbieters.
            </p>

            <h2>3. Vertragsschluss</h2>
            <p>
                Ein Vertrag kommt zustande, sobald der Kunde das gewünschte Paket über
                das Online-Anmeldeformular bucht und der Anbieter die Buchung per E-Mail
                bestätigt. Alternativ kann der Vertrag auch durch beidseitige
                Unterzeichnung einer schriftlichen Vereinbarung oder per E-Mail-Korrespondenz
                geschlossen werden.
            </p>

            <h2>4. Preise und Zahlungsbedingungen</h2>
            <p>
                Die aktuellen Preise sind auf der Website des Anbieters einsehbar und
                verstehen sich netto, exklusive Mehrwertsteuer (soweit anwendbar).
            </p>
            <ul>
                <li>
                    <strong>Monatliche Zahlung:</strong> Der Kunde zahlt
                    monatlich im Voraus, eine Rückerstattung bei vorzeitiger Kündigung ist
                    ausgeschlossen.
                </li>
                <li>
                    <strong>Jährliche Zahlung:</strong> Der Kunde zahlt
                    jährlich im Voraus und erhält nach Bezahlung keine Rückerstattung,
                    auch wenn das Abo vorzeitig beendet wird.
                </li>
            </ul>
            <p>
                Bei Zahlungsverzug behält sich der Anbieter vor, den Zugang zur App zu
                sperren, bis alle offenen Beträge beglichen sind.
            </p>

            <h2>5. Laufzeit und Kündigung</h2>
            <p>
                <strong>Monatliche Pakete:</strong> Können bis zum Ende eines
                Monats gekündigt werden. Hat der Kunde für den laufenden Monat bereits
                gezahlt, bleibt die Nutzung bis zum Monatsende bestehen.
            </p>
            <p>
                <strong>Jährliche Pakete:</strong> Nach erfolgter Zahlung und
                Nutzung erfolgt keine (anteilige) Rückerstattung. Die Kündigung muss
                mindestens 14 Tage vor Ablauf der jährlichen Laufzeit schriftlich oder
                per E-Mail eingereicht werden. Andernfalls verlängert sich das Abo um
                ein weiteres Jahr.
            </p>

            <h2>6. Haftung und Verfügbarkeit</h2>
            <p>
                Der Anbieter führt wöchentliche Backups durch und übernimmt die
                Verantwortung für eine Wiederherstellung bei serverseitigen
                Datenverlusten. Für Ausfälle des Kartenlesegeräts oder andere technische
                Probleme im Verantwortungsbereich des Kunden (z. B. Strom- oder
                Internetausfall) haftet der Anbieter nicht.
            </p>
            <p>
                Eine Verfügbarkeit von 100 % wird angestrebt. Bei Bugs oder Ausfällen,
                die auf den Anbieter zurückzuführen sind, wird umgehend nach Bekanntwerden
                eine Lösung angestrebt.
            </p>

            <h2>7. Änderungen der Preise und AGB</h2>
            <p>
                Preisänderungen werden dem Kunden jeweils jährlich per E-Mail
                mitgeteilt und gelten nicht rückwirkend für bereits laufende
                Abrechnungszeiträume. Der Anbieter kann diese AGB jederzeit ändern. Die
                Änderungen werden dem Kunden schriftlich oder per E-Mail mitgeteilt.
                Widerspricht der Kunde nicht innerhalb von 14 Tagen nach Mitteilung,
                gelten die geänderten Bedingungen als angenommen.
            </p>

            <h2>8. Datenschutz</h2>
            <p>
                Der Anbieter speichert personenbezogene Daten (Name, E-Mail,
                Firmenangaben) verschlüsselt gemäß den geltenden
                Datenschutzbestimmungen. Die Daten werden ausschließlich zum
                Zweck der Vertragserfüllung und Administration genutzt.
                Passwörter werden nur verschlüsselt abgelegt.
            </p>
            <p>
                Weitere Informationen sind in der
                <em> Datenschutzerklärung</em> (Privacy Policy) des Anbieters
                aufgeführt.
            </p>

            <h2>9. Aufbewahrung und Löschung der Daten</h2>
            <p>
                Auf Wunsch des Kunden werden die Daten bis zu 2 Jahre nach Kündigung
                aufbewahrt und anschließend gelöscht, sofern keine gesetzlichen
                Aufbewahrungsfristen entgegenstehen. Der Kunde kann alternativ ein
                eigenes Backup anfordern und danach eigenverantwortlich aufbewahren.
            </p>

            <h2>10. Schlussbestimmungen</h2>
            <ul>
                <li>
                    <strong>Gerichtsstand und anwendbares Recht:</strong> Es gilt
                    ausschließlich Schweizer Recht. Gerichtsstand ist am Sitz des
                    Anbieters (Mogelsberg), soweit nicht ein zwingender gesetzlicher
                    Gerichtsstand vorgeschrieben ist.
                </li>
                <li>
                    <strong>Salvatorische Klausel:</strong> Sollte eine
                    Bestimmung dieser AGB unwirksam sein oder werden, bleibt die
                    Wirksamkeit der übrigen Bestimmungen unberührt.
                </li>
            </ul>

            <p>
                <em>Stand: Mai 2025</em>
            </p>
            <p>
                Bei Fragen oder Anmerkungen steht Ihnen der Anbieter unter<br />
                <strong>+41 76 546 79 60</strong> oder via E-Mail an{" "}
                <a href="mailto:siefertchristopher@chrono-logisch.ch">
                    siefertchristopher@chrono-logisch.ch
                </a>{" "}
                von Montag bis Freitag (11:00–15:00) zur Verfügung.
            </p>
        </div>
        </div>
        </>
    );
};

export default AGB;
