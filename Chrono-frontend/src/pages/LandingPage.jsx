// src/pages/LandingPage.jsx — Landing (final, ohne Mock, fully scoped)
import React, { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import Navbar from "../components/Navbar";
import "../styles/LandingPageScoped.css";
import { useTranslation } from "../context/LanguageContext";
import { useNotification } from "../context/NotificationContext";
import { useAuth } from "../context/AuthContext";
import api from "../utils/api";
import logo from "../assets/logo.svg";

// Feature block supports either a single text or a list of bullet points
const FeatureCard = ({ icon, title, text, bullets }) => (
    <article className="lp-feature-card" role="listitem">
        <div className="lp-feature-icon" aria-hidden="true">{icon}</div>
        <h3 className="lp-h3">{title}</h3>
        {bullets ? (
            <ul className="lp-text-muted">
                {bullets.map((b, i) => (
                    <li key={i}>{b}</li>
                ))}
            </ul>
        ) : (
            <p className="lp-text-muted">{text}</p>
        )}
    </article>
);

const StepCard = ({ n, title, text }) => (
    <div className="lp-step-card">
        <div className="lp-step-number" aria-hidden="true">{n}</div>
        <h4 className="lp-h4">{title}</h4>
        <p className="lp-text">{text}</p>
    </div>
);

// Content definitions are now built inside the component using translations

const LandingPage = () => {
    const { t } = useTranslation();
    const { notify } = useNotification();
    const { loginDemo } = useAuth();
    const navigate = useNavigate();
    const [contact, setContact] = useState({ name: "", email: "", message: "" });
    const [sending, setSending] = useState(false);

    const onChange = (e) => setContact({ ...contact, [e.target.name]: e.target.value });

    const submitContact = async (e) => {
        e.preventDefault();
        if (sending) return;
        setSending(true);
        try {
            await api.post("/api/contact", contact);
            notify(t("landing.contact.success", "Nachricht gesendet."), "success");
            setContact({ name: "", email: "", message: "" });
        } catch {
            notify(t("landing.contact.error", "Fehler beim Senden."), "error");
        } finally {
            setSending(false);
        }
    };

    const handleDemo = async () => {
        const res = await loginDemo();
        if (res.success) {
            navigate("/demo-tour", { replace: true });
        } else {
            notify(res.message || t("landing.demoError", "Demo-Anmeldung fehlgeschlagen"), "error");
        }
    };

    const features = [
        {
            icon: "🧾",
            title: t("landing.features.items.0.title", "Arbeitszeiterfassung & Payroll verbinden"),
            bullets: [
                t("landing.features.items.0.bullets.0", "Arbeitszeiten gesetzeskonform dokumentieren"),
                t("landing.features.items.0.bullets.1", "Lohnarten und Zuschläge automatisch berechnen"),
                t("landing.features.items.0.bullets.2", "Transparente Stundennachweise für Mitarbeitende"),
            ],
        },
        {
            icon: "🇨🇭🇩🇪",
            title: t("landing.features.items.1.title", "Payroll für Schweiz & Deutschland"),
            bullets: [
                t("landing.features.items.1.bullets.0", "Kantonale und deutsche Vorgaben bereits hinterlegt"),
                t("landing.features.items.1.bullets.1", "Lohnabrechnungen als PDF oder Excel exportieren"),
                t("landing.features.items.1.bullets.2", "Brutto- und Nettolöhne mit wenigen Klicks anpassen"),
            ],
        },
        {
            icon: "👆",
            title: t("landing.features.items.2.title", "Stempeln via Terminal oder Web-App"),
            bullets: [
                t("landing.features.items.2.bullets.0", "Terminal, NFC-Karte oder Browser nutzen"),
                t("landing.features.items.2.bullets.1", "Mobile Zeiterfassung für Teams im Außendienst"),
                t("landing.features.items.2.bullets.2", "Offline-Puffer sorgt für verlässliche Buchungen"),
            ],
        },
        {
            icon: "📅",
            title: t("landing.features.items.3.title", "Urlaub, Überstunden & Gleitzeit im Blick"),
            bullets: [
                t("landing.features.items.3.bullets.0", "Abwesenheiten digital beantragen und freigeben"),
                t("landing.features.items.3.bullets.1", "Resturlaub und Salden in Echtzeit einsehen"),
                t(
                    "landing.features.items.3.bullets.2",
                    "Überstunden automatisch in die Lohnabrechnung übernehmen"
                ),
            ],
        },
        {
            icon: "🔔",
            title: t("landing.features.items.4.title", "Automatische Hinweise & Erinnerungen"),
            bullets: [
                t("landing.features.items.4.bullets.0", "Benachrichtigung bei neuen Lohnabrechnungen"),
                t("landing.features.items.4.bullets.1", "Automatische Info zu Urlaubs- und Korrekturanträgen"),
                t("landing.features.items.4.bullets.2", "Erinnerung, wenn du vergisst auszustempeln"),
            ],
        },
        {
            icon: "🛡️",
            title: t("landing.features.items.5.title", "Datenschutzkonforme Cloud in der Schweiz"),
            bullets: [
                t("landing.features.items.5.bullets.0", "Hosting in Schweizer Rechenzentren"),
                t("landing.features.items.5.bullets.1", "DSGVO- und CH-DSG-konforme Datenverarbeitung"),
                t("landing.features.items.5.bullets.2", "Feingranulare Rollen- und Rechteverwaltung"),
                t("landing.features.items.5.bullets.3", "Optionale Zwei-Faktor-Authentifizierung"),
            ],
        },
        {
            icon: "👥",
            title: t("landing.features.items.6.title", "Teams, Kunden & Projekte steuern"),
            bullets: [
                t(
                    "landing.features.items.6.bullets.0",
                    "Mitarbeitende, Kunden und Projekte zentral verwalten"
                ),
                t("landing.features.items.6.bullets.1", "Projektzeiten und Budgets live verfolgen"),
                t("landing.features.items.6.bullets.2", "Rollen & Zugriffe pro Standort definieren"),
            ],
        },
        {
            icon: "📊",
            title: t("landing.features.items.7.title", "Berichte und Auswertungen exportieren"),
            bullets: [
                t("landing.features.items.7.bullets.0", "Übersichtliche Reports für HR und Steuerberater"),
                t("landing.features.items.7.bullets.1", "Projektzeiten, Kosten und Auslastung auf einen Blick"),
                t("landing.features.items.7.bullets.2", "Export als Excel, PDF oder DATEV-kompatible Dateien"),
            ],
        },
        {
            icon: "🤝",
            title: t("landing.features.items.8.title", "Persönlicher Support & Onboarding"),
            bullets: [
                t(
                    "landing.features.items.8.bullets.0",
                    "Direkter Draht zu unseren Zeiterfassungs-Expert:innen"
                ),
                t("landing.features.items.8.bullets.1", "Antwort in der Regel am selben Werktag"),
                t("landing.features.items.8.bullets.2", "Geführtes Onboarding für dein gesamtes Team"),
            ],
        },
    ];

    const steps = [
        {
            n: "1",
            title: t("landing.steps.items.0.title", "Registrieren"),
            text: t(
                "landing.steps.items.0.text",
                "Kostenlos starten – ohne Kreditkarte und ohne Installationsaufwand."
            ),
        },
        {
            n: "2",
            title: t("landing.steps.items.1.title", "Team & Projekte anlegen"),
            text: t(
                "landing.steps.items.1.text",
                "Mitarbeitende, Kunden und Projekte anlegen – optional mit Schichtplänen."
            ),
        },
        {
            n: "3",
            title: t("landing.steps.items.2.title", "Loslegen"),
            text: t(
                "landing.steps.items.2.text",
                "Zeiten erfassen, Urlaub freigeben und die Payroll in wenigen Klicks abschließen."
            ),
        },
    ];

    return (
        <div className="landing-page scoped-landing">
            <Navbar />

            <div className="lp-logo-strip">
                <div className="lp-logo-strip-inner">
                    <img
                        src={logo}
                        alt={t("landing.logo.alt", "Chrono Logo")}
                        className="lp-logo-strip-image"
                        loading="lazy"
                    />
                    <p className="lp-logo-strip-text">
                        {t(
                            "landing.logo.tagline",
                            "Chrono – Zeiterfassung Software und Payroll Plattform für KMU, Agenturen und Handwerk."
                        )}
                    </p>
                </div>
            </div>

            <main>
                {/* HERO (einspaltig, ohne Mock) */}
                <header className="lp-hero lp-section lp-section-lg" id="home">
                    <div className="lp-hero-single">
                        <div className="lp-hero-content">
                            <div className="lp-hero-badge">
                                {t("landing.hero.badge", "Zeiterfassung & Payroll für KMU")}
                            </div>
                            <h1 className="lp-h1">
                                {t(
                                    "landing.hero.title",
                                    "Chrono Zeiterfassung Software für faire Arbeitszeiten und Lohnabrechnung."
                                )}
                            </h1>
                            <br></br>
                            <p className="lp-lead">
                                {t(
                                    "landing.hero.sub",
                                    "Chrono ist die Zeiterfassungssoftware für Unternehmen in der Schweiz und Deutschland. Erfasse Arbeitszeiten gesetzeskonform, plane Projekte und schließe die Lohnabrechnung in wenigen Klicks ab."
                                )}
                            </p>

                            <div className="lp-cta-buttons">
                                <Link className="lp-btn lp-primary" to="/register">
                                    {t("landing.cta.try", "Kostenlos testen")}
                                </Link>
                                <Link className="lp-btn lp-secondary" to="/login">
                                    {t("landing.cta.login", "Anmelden")}
                                </Link>
                                <button className="lp-btn lp-secondary" onClick={handleDemo}>
                                    {t("landing.cta.demo", "Demo ansehen")}
                                </button>
                            </div>

                            <ul className="lp-usp-chips" role="list">
                                <li>{t("landing.hero.chips.server", "🇨🇭 Schweizer Server")}</li>
                                <li>{t("landing.hero.chips.gdpr", "🔐 DSGVO-konform")}</li>
                                <li>{t("landing.hero.chips.noExcel", "🧮 Kein Excel-Chaos")}</li>
                            </ul>
                        </div>
                    </div>
                </header>

                {/* FEATURES */}
                <section className="lp-section" id="features">
                    <h2 className="lp-h2">
                        {t(
                            "landing.features.title",
                            "Alle Module für digitale Arbeitszeiterfassung und Payroll."
                        )}
                    </h2>
                    <p className="lp-section-sub">
                        {t(
                            "landing.features.sub",
                            "Chrono vereint Zeiterfassung, Projektzeiterfassung, Schichtplanung und Lohnabrechnung in einer DSGVO-konformen Plattform."
                        )}
                    </p>
                    <div className="lp-features-grid" role="list" aria-label="Featureliste">
                        {features.map((f, i) => (
                            <FeatureCard key={i} icon={f.icon} title={f.title} bullets={f.bullets} text={f.text} />
                        ))}
                    </div>
                </section>

                {/* STEPS */}
                <section className="lp-section" id="start">
                    <h2 className="lp-h2">{t("landing.steps.title", "So startest du in 3 Schritten")}</h2>
                    <div className="lp-steps-grid">
                        {steps.map((s, i) => (
                            <StepCard key={i} n={s.n} title={s.title} text={s.text} />
                        ))}
                    </div>
                </section>

                {/* CONTACT */}
                <section className="lp-section" id="kontakt" aria-labelledby="kontakt-title">
                    <h2 id="kontakt-title" className="lp-h2">{t("landing.contact.title", "Kontakt aufnehmen")}</h2>
                    <form className="lp-contact-form" onSubmit={submitContact}>
                        <div className="lp-form-row">
                            <div className="lp-form-group">
                                <label htmlFor="name" className="lp-label">{t("landing.contact.name", "Name")}</label>
                                <input id="name" name="name" type="text" required value={contact.name} onChange={onChange} className="lp-input" />
                            </div>
                            <div className="lp-form-group">
                                <label htmlFor="email" className="lp-label">{t("landing.contact.email", "E-Mail")}</label>
                                <input id="email" name="email" type="email" required value={contact.email} onChange={onChange} className="lp-input" />
                            </div>
                        </div>

                        <div className="lp-form-group">
                            <label htmlFor="message" className="lp-label">{t("landing.contact.msg", "Nachricht")}</label>
                            <textarea id="message" name="message" rows={4} required value={contact.message} onChange={onChange} placeholder={t("landing.contact.placeholder", "Wie kann ich helfen?")} className="lp-textarea" />
                        </div>

                        <div className="lp-form-actions">
                            <button className="lp-btn lp-primary" type="submit">{t("landing.contact.send", "Senden")}</button>
                            <p className="lp-form-hint lp-text-muted">{t("landing.contact.hint", "Antwort in der Regel innerhalb von 24h.")}</p>
                        </div>
                    </form>
                </section>
            </main>

            <footer className="lp-footer">
                <div className="lp-foot-inner">
                    <span>© {new Date().getFullYear()} Chrono</span>
                    <nav className="lp-foot-links" aria-label="Footer Navigation">
                        <Link to="/impressum">{t("landing.footer.imprint", "Impressum")}</Link>
                        <Link to="/datenschutz">{t("landing.footer.privacy", "Datenschutz")}</Link>
                        <Link to="/agb">{t("landing.footer.terms", "AGB")}</Link>
                    </nav>
                </div>
            </footer>
        </div>
    );
};

export default LandingPage;
