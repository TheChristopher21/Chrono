// src/pages/AGB.jsx
import React from 'react';
import Navbar from '../components/Navbar';
import '../styles/LegalPages.css';
import { Link } from 'react-router-dom';

const AGB = () => {
    return (
        <>
            <Navbar />
            <div className="legal-wrapper">
                <div className="legal-page">
                    <h1>Allgemeine Geschäftsbedingungen (AGB) für Chrono</h1>
                    <p><strong>Stand: 10. September 2025</strong></p>

                    <h2>1. Geltungsbereich</h2>
                    <p>
                        Diese Allgemeinen Geschäftsbedingungen (AGB) regeln das Vertragsverhältnis zwischen der Chrono, vertreten durch Christopher Siefert, Lettenstrasse 20, 9122 Mogelsberg, Schweiz (nachfolgend "Anbieter" genannt) und den Nutzern (nachfolgend "Kunde" genannt) der Software-as-a-Service (SaaS) Anwendung "Chrono" (nachfolgend "Software" genannt).
                    </p>
                    <p>
                        Abweichende Bedingungen des Kunden werden nicht anerkannt, es sei denn, der Anbieter stimmt ihrer Geltung ausdrücklich schriftlich zu.
                    </p>

                    <h2>2. Leistungsgegenstand</h2>
                    <p>
                        Der Anbieter stellt dem Kunden die Software "Chrono" zur digitalen Zeiterfassung, Projektverwaltung, Urlaubs- und Abwesenheitsplanung sowie optional zur Lohnabrechnung über das Internet zur Verfügung. Der Funktionsumfang richtet sich nach dem vom Kunden gebuchten Leistungspaket.
                    </p>
                    <p>
                        Die Software wird kontinuierlich weiterentwickelt. Der Anbieter behält sich das Recht vor, Funktionen zu ändern, zu erweitern oder einzuschränken, solange die Kernfunktionalität des gebuchten Pakets erhalten bleibt.
                    </p>

                    <h2>3. Vertragsschluss und Testphase</h2>
                    <p>
                        Der Vertrag kommt durch die Online-Registrierung des Kunden und die Annahme durch den Anbieter zustande. Mit der Registrierung gibt der Kunde ein verbindliches Angebot zum Abschluss eines Nutzungsvertrages ab.
                    </p>
                    <p>
                        Gegebenenfalls angebotene kostenlose Testphasen sind unverbindlich und gehen nicht automatisch in ein kostenpflichtiges Abonnement über, es sei denn, dies wird ausdrücklich anders vereinbart.
                    </p>

                    <h2>4. Preise und Zahlungsbedingungen</h2>
                    <p>
                        Die Vergütung für die Nutzung der Software richtet sich nach der aktuellen Preisliste des Anbieters zum Zeitpunkt des Vertragsschlusses. Die Preise verstehen sich zuzüglich der gesetzlichen Mehrwertsteuer.
                    </p>
                    <p>
                        Die Nutzungsgebühren sind je nach gewähltem Abrechnungszeitraum (z.B. monatlich, jährlich) im Voraus fällig. Die Zahlung erfolgt über die angebotenen Zahlungsmethoden (z.B. Kreditkarte via Stripe). Bei Zahlungsverzug ist der Anbieter berechtigt, den Zugang zur Software zu sperren.
                    </p>

                    <h2>5. Pflichten des Kunden</h2>
                    <p>
                        Der Kunde ist verpflichtet, seine Zugangsdaten (Benutzername, Passwort) geheim zu halten und vor dem Zugriff durch unbefugte Dritte zu schützen. Der Kunde ist für alle Aktivitäten verantwortlich, die über sein Benutzerkonto erfolgen.
                    </p>
                    <p>
                        Der Kunde ist für die Rechtmäßigkeit der von ihm und seinen Nutzern in die Software eingegebenen Daten selbst verantwortlich. Er hat sicherzustellen, dass keine Inhalte hochgeladen werden, die gegen geltendes Recht oder die Rechte Dritter verstoßen.
                    </p>

                    <h2>6. Datenschutz und Datensicherheit</h2>
                    <p>
                        Der Schutz personenbezogener Daten hat höchste Priorität. Der Anbieter verarbeitet die Daten des Kunden ausschließlich im Rahmen der Vertragserfüllung und unter Einhaltung der gesetzlichen Datenschutzbestimmungen.
                    </p>
                    <p>
                        Weitere Informationen zur Datenverarbeitung finden sich in unserer <Link to="/datenschutz">Datenschutzerklärung</Link>.
                    </p>

                    <h2>7. Verfügbarkeit und Wartung</h2>
                    <p>
                        Der Anbieter gewährleistet eine branchenübliche Verfügbarkeit der Software. Der Anbieter ist berechtigt, die Verfügbarkeit für geplante Wartungsarbeiten vorübergehend einzuschränken. Diese werden nach Möglichkeit zu nutzungsarmen Zeiten durchgeführt und rechtzeitig angekündigt.
                    </p>

                    <h2>8. Haftung</h2>
                    <p>
                        Der Anbieter haftet unbeschränkt bei Vorsatz oder grober Fahrlässigkeit sowie bei Verletzung von Leben, Körper oder Gesundheit. Bei leichter Fahrlässigkeit haftet der Anbieter nur bei Verletzung wesentlicher Vertragspflichten (Kardinalpflichten) und begrenzt auf den vertragstypischen, vorhersehbaren Schaden.
                    </p>
                    <p>
                        Die Haftung für Datenverlust wird auf den typischen Wiederherstellungsaufwand beschränkt, der bei regelmäßiger und gefahrentsprechender Anfertigung von Sicherungskopien eingetreten wäre. Der Anbieter ist nicht für die inhaltliche Richtigkeit der vom Kunden erfassten Daten verantwortlich.
                    </p>

                    <h2>9. Vertragslaufzeit und Kündigung</h2>
                    <p>
                        Der Vertrag wird auf unbestimmte Zeit geschlossen und kann vom Kunden jederzeit zum Ende des laufenden Abrechnungszeitraums gekündigt werden. Der Anbieter kann den Vertrag mit einer Frist von 30 Tagen zum Ende des Abrechnungszeitraums kündigen.
                    </p>
                    <p>
                        Das Recht zur außerordentlichen Kündigung aus wichtigem Grund bleibt unberührt.
                    </p>

                    <h2>10. Schlussbestimmungen</h2>
                    <p>
                        Es gilt das Recht der Schweiz unter Ausschluss des UN-Kaufrechts. Gerichtsstand für alle Streitigkeiten aus diesem Vertragsverhältnis ist der Sitz des Anbieters.
                    </p>
                    <p>
                        Sollten einzelne Bestimmungen dieser AGB unwirksam sein oder werden, so wird dadurch die Wirksamkeit der übrigen Bestimmungen nicht berührt.
                    </p>
                </div>
            </div>
        </>
    );
};

export default AGB;