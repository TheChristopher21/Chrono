// src/pages/LandingPage.jsx — Landing (final, ohne Mock, fully scoped)
import React, { useState } from "react";
import { Link } from "react-router-dom";
import Navbar from "../components/Navbar";
import "../styles/LandingPageScoped.css";
import { useTranslation } from "../context/LanguageContext";
import { useNotification } from "../context/NotificationContext";
import api from "../utils/api";

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

// Content definitions – keeps component lean and improves readability
const features = [
    {
        icon: "🧾",
        title: "Payroll + Zeiterfassung",
        bullets: [
            "Arbeitszeiten & Projekte",
            "Urlaub und Überstunden",
            "Eine Oberfläche für alles",
        ],
    },
    {
        icon: "🇨🇭🇩🇪",
        title: "CH & DE Lohnabrechnung",
        bullets: ["PDF/CSV Export", "Lohnarten anpassbar", "Beide Länder abgedeckt"],
    },
    {
        icon: "👆",
        title: "NFC-Stempeluhr & Web",
        bullets: ["NFC-Karten", "Web & Mobil", "Offline bereit"],
    },
    {
        icon: "📅",
        title: "Urlaub & Überstunden",
        bullets: ["Digitale Anträge", "Resttage automatisch", "Überstundenfrei rechnen"],
    },
    {
        icon: "🔔",
        title: "Benachrichtigungen",
        bullets: ["Neue Abrechnungen", "Anträge", "Offene Stempelungen"],
    },
    {
        icon: "🛡️",
        title: "Schweizer Server & DSGVO",
        bullets: ["Verschlüsselt", "Rollenbasiert", "2FA-bereit", "Daten in CH"],
    },
];

const steps = [
    { n: "1", title: "Registrieren", text: "Kostenlos starten – ohne Kreditkarte." },
    { n: "2", title: "Team & Projekte anlegen", text: "Mitarbeitende, Projekte und Kunden hinzufügen." },
    { n: "3", title: "Loslegen", text: "Stempeln, Urlaub beantragen, Abrechnung erstellen." },
];

const LandingPage = () => {
    const { t } = useTranslation();
    const { notify } = useNotification();
    const [contact, setContact] = useState({ name: "", email: "", message: "" });
    const [sending, setSending] = useState(false);

    const onChange = (e) => setContact({ ...contact, [e.target.name]: e.target.value });

    const submitContact = async (e) => {
        e.preventDefault();
        if (sending) return;
        setSending(true);
        try {
            await api.post("/api/contact", contact);
            notify(t("landingPage.contactSuccess", "Nachricht gesendet."), "success");
            setContact({ name: "", email: "", message: "" });
        } catch {
            notify(t("landingPage.contactError", "Fehler beim Senden."), "error");
        } finally {
            setSending(false);
        }
    };


    return (
        <div className="landing-page scoped-landing">
            <Navbar />

            <main>
                {/* HERO (einspaltig, ohne Mock) */}
                <header className="lp-hero lp-section lp-section-lg" id="home">
                    <div className="lp-hero-single">
                        <div className="lp-hero-content">
                            <div className="lp-hero-badge">Fair · Klar · Zuverlässig</div>
                            <h1 className="lp-h1">
                                {t("landing.hero.title", "Zeit erfassen, fair abrechnen – einfach, klar, zuverlässig.")}
                            </h1>
                            <br></br>
                            <p className="lp-lead">
                                {t("landing.hero.sub",
                                    "Chrono hilft Teams in der Schweiz & Deutschland, Zeiten korrekt zu erfassen, Urlaub sauber zu managen und Löhne sicher abzurechnen."
                                )}
                            </p>

                            <div className="lp-cta-buttons">
                                <Link className="lp-btn lp-primary" to="/register">
                                    {t("landing.cta.try", "Kostenlos testen")}
                                </Link>
                                <Link className="lp-btn lp-secondary" to="/login">
                                    {t("landing.cta.login", "Anmelden")}
                                </Link>
                            </div>

                            <ul className="lp-usp-chips" role="list">
                                <li>🇨🇭 Schweizer Server</li>
                                <li>🔐 DSGVO-konform</li>
                                <li>🧮 Kein Excel-Chaos</li>
                            </ul>
                        </div>
                    </div>
                </header>

                {/* FEATURES */}
                <section className="lp-section" id="features">
                    <h2 className="lp-h2">{t("landing.features.title", "Alles drin, was du brauchst – ohne Ballast.")}</h2>
                    <p className="lp-section-sub">
                        {t("landing.features.sub", "Fokussiert auf das Wesentliche: Zeiterfassung, Abrechnung, Urlaub und klare Admin-Prozesse.")}
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
                                <label htmlFor="email" className="lp-label">E-Mail</label>
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
                        <Link to="/impressum">Impressum</Link>
                        <Link to="/datenschutz">Datenschutz</Link>
                        <Link to="/agb">AGB</Link>
                    </nav>
                </div>
            </footer>
        </div>
    );
};

export default LandingPage;
