// src/pages/Datenschutz.jsx
import React from 'react';
import Navbar from '../components/Navbar';
import '../styles/LegalPages.css';
import { Link } from 'react-router-dom';

const Datenschutz = () => {
    return (
        <>
            <Navbar />
            <div className="legal-wrapper">
                <div className="legal-page">
                    <h1>Datenschutzerklärung für Chrono</h1>
                    <p><strong>Stand: 10. September 2025</strong></p>

                    <p>
                        Wir freuen uns über Ihr Interesse an unserer Zeiterfassungsanwendung Chrono. Der Schutz Ihrer persönlichen Daten ist uns ein wichtiges Anliegen. Nachfolgend informieren wir Sie ausführlich über den Umgang mit Ihren Daten.
                    </p>

                    <h2>1. Verantwortliche Stelle</h2>
                    <p>
                        Verantwortlicher für die Datenerhebung, -verarbeitung und -nutzung im Sinne der Datenschutz-Grundverordnung (DSGVO) ist:
                    </p>
                    <p>
                        <strong>Chrono</strong><br />
                        Lettenstrasse 20<br />
                        1922 Moglesberg<br />
                        Schweiz
                    </p>
                    <p>
                        <strong>E-Mail:</strong> siefertchristopher@chrono-logisch.ch<br />
                        <strong>Telefon:</strong> +41 764699122
                    </p>
                    <p>
                        Weitere Informationen finden Sie in unserem <Link to="/impressum">Impressum</Link>.
                    </p>

                    <h2>2. Art, Zweck und Umfang der Datenverarbeitung</h2>
                    <p>
                        Wir verarbeiten personenbezogene Daten unserer Nutzer grundsätzlich nur, soweit dies zur Bereitstellung einer funktionsfähigen Webseite sowie unserer Inhalte und Leistungen erforderlich ist.
                    </p>

                    <h3>a) Bei Besuch der Webseite</h3>
                    <p>
                        Bei jedem Aufruf unserer Webseite erfasst unser System automatisiert Daten und Informationen vom Computersystem des aufrufenden Rechners (sog. Server-Logfiles), wie z.B. Browsertyp und -version, verwendetes Betriebssystem, Referrer URL, Hostname des zugreifenden Rechners, Uhrzeit der Serveranfrage und IP-Adresse. Diese Daten sind technisch erforderlich, um Ihnen unsere Anwendung anzuzeigen und die Stabilität und Sicherheit zu gewährleisten.
                    </p>

                    <h3>b) Bei Registrierung und Nutzung eines Benutzerkontos</h3>
                    <p>
                        Um die Funktionen von Chrono nutzen zu können, müssen Sie sich registrieren. Hierbei erheben wir folgende Daten:
                    </p>
                    <ul>
                        <li>Benutzername</li>
                        <li>E-Mail-Adresse</li>
                        <li>Passwort (sicher verschlüsselt)</li>
                        <li>Gegebenenfalls weitere persönliche Daten, die Sie in Ihrem Profil hinterlegen (z.B. Name, Adresse, Telefonnummer).</li>
                    </ul>
                    <p>
                        Diese Daten sind erforderlich, um Ihr Benutzerkonto zu verwalten, Ihnen den Zugang zu ermöglichen und unsere vertraglichen Leistungen zu erbringen.
                    </p>

                    <h3>c) Im Rahmen der Zeiterfassung und Arbeitsorganisation</h3>
                    <p>
                        Die Kernfunktion von Chrono ist die Erfassung und Verwaltung von Arbeitszeiten. Dabei werden folgende Daten verarbeitet:
                    </p>
                    <ul>
                        <li>Start-, End- und Pausenzeiten Ihrer Arbeit</li>
                        <li>Zuordnung der Arbeitszeiten zu Projekten, Kunden und Aufgaben</li>
                        <li>Manuelle Korrekturanfragen und deren Genehmigungsstatus</li>
                        <li>Daten von NFC-Karten, sofern diese zur Zeiterfassung genutzt werden</li>
                        <li>Krankmeldungen und Urlaubsanträge, inklusive Start- und Enddatum</li>
                    </ul>
                    <p>
                        Diese Verarbeitung ist zur Erfüllung des Vertragsverhältnisses mit Ihnen bzw. Ihrem Arbeitgeber erforderlich.
                    </p>

                    <h3>d) Im Rahmen der Lohnabrechnung</h3>
                    <p>
                        Sofern das Modul zur Lohnabrechnung genutzt wird, verarbeiten wir sensible Daten, die für die Erstellung der Lohn- und Gehaltsabrechnungen notwendig sind. Dies können z.B. Gehaltsdaten, Steuerinformationen und Bankverbindungen sein. Diese Daten werden streng vertraulich behandelt.
                    </p>

                    <h3>e) Bei Nutzung des KI-Chatbots und der Wissensdatenbank</h3>
                    <p>
                        Wenn Sie den KI-Chatbot nutzen, werden Ihre Anfragen verarbeitet, um Ihnen passende Antworten aus der Wissensdatenbank zu liefern. Diese Anfragen werden zur Verbesserung des Dienstes analysiert, jedoch ohne direkten Personenbezug gespeichert.
                    </p>

                    <h3>f) Bei Kontaktaufnahme</h3>
                    <p>
                        Wenn Sie uns per Kontaktformular oder E-Mail kontaktieren, werden die von Ihnen übermittelten Daten (z.B. Name, E-Mail-Adresse, Inhalt Ihrer Anfrage) zur Bearbeitung Ihres Anliegens gespeichert und verwendet.
                    </p>

                    <h2>3. Cookies und Lokaler Speicher</h2>
                    <p>
                        Unsere Anwendung verwendet an einigen Stellen den lokalen Speicher (Local Storage) Ihres Browsers, um die Benutzerfreundlichkeit zu verbessern. Darin werden beispielsweise Ihre bevorzugte Sprache oder die Theme-Einstellung (Light/Dark Mode) gespeichert. Diese Informationen verbleiben auf Ihrem Endgerät und werden nicht an unsere Server übertragen.
                    </p>

                    <h2>4. Weitergabe von Daten an Dritte</h2>
                    <p>
                        Eine Weitergabe Ihrer persönlichen Daten an Dritte findet grundsätzlich nicht statt, es sei denn, wir sind gesetzlich dazu verpflichtet oder es ist zur Abwicklung des Vertragsverhältnisses erforderlich. Dies betrifft folgende Fälle:
                    </p>
                    <ul>
                        <li><strong>Zahlungsdienstleister:</strong> Wenn Sie kostenpflichtige Dienste nutzen, können Zahlungsdaten an Dienstleister wie Stripe weitergegeben werden, um die Zahlung abzuwickeln.</li>
                        <li><strong>Google Calendar:</strong> Sofern Sie die Kalender-Integration aktivieren, werden relevante Termindaten (z.B. genehmigter Urlaub) mit Ihrem Google-Konto synchronisiert. Dies geschieht nur nach Ihrer ausdrücklichen Zustimmung.</li>
                    </ul>
                    <p>
                        Mit allen Dienstleistern, die in unserem Auftrag personenbezogene Daten verarbeiten, haben wir entsprechende Verträge zur Auftragsverarbeitung (AVV) geschlossen.
                    </p>

                    <h2>5. Ihre Rechte als betroffene Person</h2>
                    <p>
                        Ihnen stehen bezüglich Ihrer bei uns gespeicherten Daten folgende Rechte zu:
                    </p>
                    <ul>
                        <li><strong>Recht auf Auskunft:</strong> Sie können jederzeit Auskunft über Ihre von uns verarbeiteten personenbezogenen Daten verlangen.</li>
                        <li><strong>Recht auf Berichtigung:</strong> Sie können die Berichtigung unrichtiger oder Vervollständigung Ihrer Daten verlangen.</li>
                        <li><strong>Recht auf Löschung:</strong> Sie können die Löschung Ihrer personenbezogenen Daten verlangen, sofern keine gesetzlichen Aufbewahrungspflichten entgegenstehen.</li>
                        <li><strong>Recht auf Einschränkung der Verarbeitung:</strong> Unter bestimmten Voraussetzungen können Sie eine Einschränkung der Verarbeitung Ihrer Daten verlangen.</li>
                        <li><strong>Widerspruchsrecht:</strong> Sie haben das Recht, aus Gründen, die sich aus Ihrer besonderen Situation ergeben, jederzeit gegen die Verarbeitung Sie betreffender personenbezogener Daten Widerspruch einzulegen.</li>
                        <li><strong>Recht auf Datenübertragbarkeit:</strong> Sie haben das Recht, Daten, die wir auf Grundlage Ihrer Einwilligung oder in Erfüllung eines Vertrags automatisiert verarbeiten, an sich oder an einen Dritten in einem gängigen, maschinenlesbaren Format aushändigen zu lassen.</li>
                        <li><strong>Beschwerderecht:</strong> Sie haben das Recht, sich bei einer Datenschutz-Aufsichtsbehörde über die Verarbeitung Ihrer personenbezogenen Daten durch uns zu beschweren.</li>
                    </ul>
                    <p>
                        Zur Ausübung Ihrer Rechte kontaktieren Sie uns bitte über die oben genannten Kontaktdaten.
                    </p>

                    <h2>6. Datensicherheit</h2>
                    <p>
                        Wir treffen umfangreiche technische und organisatorische Sicherheitsvorkehrungen, um Ihre Daten gegen zufällige oder vorsätzliche Manipulationen, teilweisen oder vollständigen Verlust, Zerstörung oder gegen den unbefugten Zugriff Dritter zu schützen. Unsere Sicherheitsmaßnahmen werden entsprechend der technologischen Entwicklung fortlaufend verbessert.
                    </p>

                    <h2>7. Änderung dieser Datenschutzerklärung</h2>
                    <p>
                        Wir behalten uns vor, diese Datenschutzerklärung anzupassen, damit sie stets den aktuellen rechtlichen Anforderungen entspricht oder um Änderungen unserer Leistungen in der Datenschutzerklärung umzusetzen, z. B. bei der Einführung neuer Services. Für Ihren erneuten Besuch gilt dann die neue Datenschutzerklärung.
                    </p>
                </div>
            </div>
        </>
    );
};

export default Datenschutz;