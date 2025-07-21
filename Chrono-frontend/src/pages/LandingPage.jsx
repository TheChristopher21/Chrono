// src/pages/LandingPage.jsx · Features aktualisiert (2025-07)
import "react";
import { Link } from "react-router-dom";
import Navbar from "../components/Navbar";
import "../styles/LandingPageScoped.css";
import { useTranslation } from "../context/LanguageContext";
import { useState } from "react";
import { useNotification } from "../context/NotificationContext";
import api from "../utils/api";

/* ---------- Sub-Components ------------------------------------------ */
const FeatureCard = ({ icon, title, text }) => (
    <div className="feature-card">
        <div className="feature-icon">{icon}</div>
        <h3>{title}</h3>
        <p>{text}</p>
    </div>
);

const StepCard = ({ n, title, text }) => (
    <div className="step-item">
        <div className="step-number">{n}</div>
        <h4>{title}</h4>
        <p>{text}</p>
    </div>
);

const LandingPage = () => {
    const { t } = useTranslation();
    const { notify } = useNotification();
    const [contact, setContact] = useState({ name: "", email: "", message: "" });
    const [sending, setSending] = useState(false);

    const handleContactChange = (e) => {
        setContact({ ...contact, [e.target.name]: e.target.value });
    };

    const submitContact = async (e) => {
        e.preventDefault();
        if (sending) return;
        setSending(true);
        try {
            await api.post("/api/contact", contact);
            notify(t("landingPage.contactSuccess", "Nachricht gesendet."));
            setContact({ name: "", email: "", message: "" });
        } catch (err) {
            console.error(err);
            notify(t("landingPage.contactError", "Fehler beim Senden."));
        } finally {
            setSending(false);
        }
    };

    // NEUE FUNKTIONSLISTE 2025: Leicht verständlich, echte Alleinstellungsmerkmale!
    const features = [
        {
            icon: "🔄",
            title: "Automatische Lohnabrechnung",
            text: "Erstelle Abrechnungen für alle Mitarbeitenden automatisch – monatlich, pünktlich und ohne Excel-Chaos. Für Schweiz und Deutschland, individuell einstellbar.",
        },
        {
            icon: "🧾",
            title: "All-in-One Payroll & Zeiterfassung",
            text: "Arbeitszeiten, Projekte, Urlaub, Kranktage, Zuschläge und Überstunden werden automatisch in der Abrechnung berücksichtigt. Alles aus einer Hand – kein Toolwechsel!",
        },
        {
            icon: "🇨🇭🇩🇪",
            title: "Abrechnung für Schweiz & Deutschland",
            text: "Unterstützt sowohl Schweizer als auch deutsche Lohnmodelle (inkl. Steuerklasse, Tarifcode, Kanton, Quellensteuer etc.). Perfekt für grenzüberschreitende Teams.",
        },
        {
            icon: "👆",
            title: "NFC-Terminal",
            text: "Zeiterfassung ganz einfach per NFC-Chip oder Web – keine teure Hardware nötig. Auf Wunsch auch Terminal-Modus für das Büro.",
        },
        {
            icon: "📅",
            title: "Urlaubsplanung & Genehmigung",
            text: "Urlaubsanträge, Überstundenfrei und Abwesenheiten digital beantragen, verwalten und direkt genehmigen. Übersichtlicher Kalender und automatische Berechnung der Resttage.",
        },
        {
            icon: "⏰",
            title: "Automatisches Ausstempeln",
            text: "Schutz vor vergessenen Stempeln: Am Tagesende wird automatisch ausgestempelt und die Zeit korrekt berechnet.",
        },
        {
            icon: "🟢",
            title: "Live-Status & Dashboard",
            text: "Immer im Blick: Wer ist heute da? Was wurde schon genehmigt? Automatische Benachrichtigungen bei neuen Abrechnungen, Urlaubsanträgen und mehr.",
        },
        {
            icon: "📄",
            title: "PDF-Lohnabrechnung & CSV-Export",
            text: "Erstelle und exportiere Lohnabrechnungen als PDF oder CSV für Steuerberater, Buchhaltung und die eigene Ablage.",
        },
        {
            icon: "📊",
            title: "Projekt- & Kundenzeiten",
            text: "Ordne Zeiten und Abwesenheiten direkt Projekten und Kunden zu. Perfekt für Agenturen, Dienstleister und projektorientierte Teams.",
        },
        {
            icon: "🔔",
            title: "Erinnerungen & Automatisierung",
            text: "Nie wieder Fristen verpassen: Erinnerungen an offene Stempelungen, Abrechnungen, Anträge oder bevorstehende Monatswechsel.",
        },
        {
            icon: "🛡️",
            title: "Sicher & DSGVO-konform",
            text: "Deine Daten liegen verschlüsselt auf Schweizer Servern. Rechte- und Rollenkonzept, 2FA und DSGVO-Unterstützung inklusive.",
        },
    ];


    const steps = [
        {
            n: "1",
            title: "Account anlegen",
            text: "Jetzt kostenlos registrieren – ganz ohne Risiko, keine Kreditkarte nötig.",
        },
        {
            n: "2",
            title: "Team & Projekte anlegen",
            text: "Mitarbeiter, Projekte und (falls nötig) Kunden hinzufügen. Alles super einfach.",
        },
        {
            n: "3",
            title: "Direkt loslegen",
            text: "Stempeln, Zeiten erfassen, Urlaub beantragen oder direkt die erste Abrechnung erstellen lassen. Fertig!",
        },
    ];

    return (
        <div className="landing-page scoped-landing">
            <Navbar />
            <main>
                {/* HERO *********************************************************** */}
                <header className="landing-hero site-section lg" id="home">
                    <div className="section-inner">
                        <h1>Die smarte Zeiterfassung & Lohnabrechnung für Teams in der Schweiz und Deutschland</h1>
                        <div className="cta-buttons">
                            <Link to="/register" className="btn primary">
                                Jetzt kostenlos starten
                            </Link>
                        </div>
                    </div>
                </header>

                {/* INFO *********************************************************** */}
                <section className="info-section site-section">
                    <div className="section-inner">
                        <h2 className="section-title accent">Warum Chrono-Logisch?</h2>
                        <div className="info-box">
                            <p>
                                Schluss mit komplizierten Excel-Listen, Papier-Stundenzetteln und teuren Insellösungen!<br /><br />
                                Chrono-Logisch digitalisiert deine komplette Zeiterfassung & Abrechnung in einem System – automatisch, rechtssicher und so einfach, dass jeder im Team damit klarkommt.<br /><br />
                                Egal ob Homeoffice, Büro oder unterwegs: Stempeln, Projekte zuordnen, Urlaub oder Überstunden beantragen und die Lohnabrechnung automatisch generieren lassen – alles aus einer App. Perfekt für KMU, Dienstleister, Agenturen und Unternehmen mit flexiblen Arbeitsmodellen.
                            </p>
                        </div>
                    </div>
                </section>

                {/* FEATURES ******************************************************* */}
                <section className="features-section site-section" id="features">
                    <div className="section-inner">
                        <h2>Alle Funktionen im Überblick</h2>
                        <p>
                            Keine Gimmicks – nur Features, die deinen Alltag wirklich erleichtern.<br />
                            <b>Automatische Lohnabrechnung, Projektzeiten, NFC-Terminal, Überstunden, Urlaub, PDF-Export & mehr.</b>
                        </p>

                        <div className="features-grid">
                            {features.map((f, idx) => (
                                <FeatureCard key={idx} icon={f.icon} title={f.title} text={f.text} />
                            ))}
                        </div>
                    </div>
                </section>

                {/* STEPS ********************************************************** */}
                <section className="steps-section site-section">
                    <div className="section-inner">
                        <h2>In 3 Schritten startklar</h2>
                        <div className="steps-grid">
                            {steps.map((s, idx) => (
                                <StepCard key={idx} n={s.n} title={s.title} text={s.text} />
                            ))}
                        </div>
                    </div>
                </section>

                {/* CONTACT ----------------------------------------------------- */}
                <section className="contact-section site-section" id="contact">
                    <div className="section-inner">
                        <h3>Kontakt</h3><br/>
                        <form className="contact-form" onSubmit={submitContact}>
                            <input
                                name="name"
                                type="text"
                                placeholder="Name"
                                value={contact.name}
                                onChange={handleContactChange}
                                required
                            />
                            <input
                                name="email"
                                type="email"
                                placeholder="E-Mail"
                                value={contact.email}
                                onChange={handleContactChange}
                                required
                            />
                            <textarea
                                name="message"
                                placeholder="Nachricht"
                                value={contact.message}
                                onChange={handleContactChange}
                                required
                            />
                            <button className="btn primary" disabled={sending}>
                                Absenden
                            </button>
                        </form>
                    </div>
                </section>

                <section className="newsletter-section site-section">
                    <div className="section-inner">
                        <h3>Bleib informiert!</h3>
                        <p>Updates & Tipps direkt in dein Postfach.</p>
                        <br/>
                        <form className="newsletter-form">
                            <input type="email" placeholder="Deine E-Mail" />
                            <button className="btn primary">Abonnieren</button>
                        </form>
                    </div>
                </section>
            </main>

            {/* FOOTER ********************************************************* */}
            <footer className="landing-footer">
                <div className="footer-content">
                    <p>© {new Date().getFullYear()} Chrono-Logisch. Alle Rechte vorbehalten.</p>
                    <div className="social-icons"></div>
                    <div style={{ marginTop: "1rem" }}>
                        <Link to="/impressum" style={{ marginRight: "1rem" }}>Impressum</Link>
                        <Link to="/agb">AGB</Link>
                    </div>
                </div>
            </footer>
        </div>
    );
};

export default LandingPage;
