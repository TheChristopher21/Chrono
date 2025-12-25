// src/pages/LandingPage.jsx — Landing (final, ohne Mock, fully scoped)
import React, { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import Navbar from "../components/Navbar";
import "../styles/LandingPageScoped.css";
import { useTranslation } from "../context/LanguageContext";
import { useNotification } from "../context/NotificationContext";
import { useAuth } from "../context/AuthContext";
import api from "../utils/api";

// Feature block for homepage highlights
const FeatureCard = ({ icon, title, lines }) => (
    <article className="lp-feature-card" role="listitem">
        <div className="lp-feature-icon" aria-hidden="true">{icon}</div>
        <h3 className="lp-h3">{title}</h3>
        <p className="lp-text-muted">
            {lines.map((line, index) => (
                <React.Fragment key={index}>
                    {line}
                    {index !== lines.length - 1 && <br />}
                </React.Fragment>
            ))}
        </p>
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
            icon: "⏱️",
            title: t("landing.features.tracking.title", "Zeiterfassung & Projekte"),
            lines: [
                t("landing.features.tracking.line1", "Mitarbeitende stempeln per Browser, NFC oder App."),
                t("landing.features.tracking.line2", "Zeiten sofort Projekten und Kunden zuordnen."),
            ],
        },
        {
            icon: "🏖️",
            title: t("landing.features.leave.title", "Urlaub & Abwesenheiten"),
            lines: [
                t("landing.features.leave.line1", "Urlaub online beantragen, genehmigen und auswerten."),
                t("landing.features.leave.line2", "Resttage und Überstunden immer im Blick."),
            ],
        },
        {
            icon: "💼",
            title: t("landing.features.payroll.title", "Lohnabrechnung CH & DE"),
            lines: [
                t("landing.features.payroll.line1", "Lohnabrechnungen rechtssicher erstellen."),
                t("landing.features.payroll.line2", "Export für Treuhänder und Buchhaltung."),
            ],
        },
        {
            icon: "📈",
            title: t("landing.features.reporting.title", "Auswertungen & Berichte"),
            lines: [
                t("landing.features.reporting.line1", "Sieh auf einen Blick, wer wann wie viel gearbeitet hat."),
                t("landing.features.reporting.line2", "Praktisch für Steuer, Revision und Planung."),
            ],
        },
        {
            icon: "🤝",
            title: t("landing.features.teamwork.title", "Teamarbeit leicht gemacht"),
            lines: [
                t("landing.features.teamwork.line1", "Alle wissen, wer was übernimmt."),
                t("landing.features.teamwork.line2", "Aufgaben bleiben transparent für das ganze Team."),
            ],
        },
        {
            icon: "🔔",
            title: t("landing.features.reminders.title", "Erinnerungen kommen von selbst"),
            lines: [
                t("landing.features.reminders.line1", "Chrono erinnert an fehlende Zeiten oder Freigaben."),
                t("landing.features.reminders.line2", "So geht nichts Wichtiges verloren."),
            ],
        },
        {
            icon: "🔒",
            title: t("landing.features.security.title", "Deine Daten sind geschützt"),
            lines: [
                t("landing.features.security.line1", "Alle Infos liegen sicher auf Schweizer Servern."),
                t("landing.features.security.line2", "Du bestimmst, wer was sehen darf."),
            ],
        },
        {
            icon: "📲",
            title: t("landing.features.access.title", "Chrono läuft überall"),
            lines: [
                t("landing.features.access.line1", "Nutze Chrono am Computer, Tablet oder Handy."),
                t("landing.features.access.line2", "Auch unterwegs schnell Zeiten eintragen."),
            ],
        },
    ];

    const steps = [
        {
            n: "1",
            title: t("landing.steps.register.title", "Registrieren"),
            text: t(
                "landing.steps.register.text",
                "Konto anlegen – ganz ohne Kreditkarte."
            ),
        },
        {
            n: "2",
            title: t("landing.steps.setup.title", "Team & Projekte hinzufügen"),
            text: t(
                "landing.steps.setup.text",
                "Mitarbeitende, Kunden und Projekte erfassen."
            ),
        },
        {
            n: "3",
            title: t("landing.steps.start.title", "Loslegen"),
            text: t(
                "landing.steps.start.text",
                "Stempeln, Urlaub beantragen, Löhne erstellen."
            ),
        },
    ];

    const logos = [
        t("landing.social.hotel", "Hotel"),
        t("landing.social.construction", "Bauunternehmen"),
        t("landing.social.cleaning", "Reinigungsfirma"),
        t("landing.social.gastro", "Gastronomie"),
        t("landing.social.treuhand", "Treuhand"),
        t("landing.social.fitness", "Fitnessstudio"),
    ];

    return (
        <div className="landing-page scoped-landing">
            <Navbar />
            <main>
                {/* HERO */}
                <header className="lp-hero lp-section lp-section-lg" id="home">
                    <div className="lp-hero-inner">
                        <div className="lp-hero-copy">
                            <span className="lp-hero-badge">
                                {t("landing.hero.badge", "fair · klar · zuverlässig")}
                            </span>
                            <h1 className="lp-h1">
                                {t(
                                    "landing.hero.title",
                                    "Zeiten erfassen. Löhne abrechnen. Urlaub managen."
                                )}
                                <span className="lp-hero-subline">
                                    {t(
                                        "landing.hero.subline",
                                        "Alles in einer Plattform – made in Switzerland."
                                    )}
                                </span>
                            </h1>
                            <p className="lp-lead">
                                {t(
                                    "landing.hero.text",
                                    "Chrono hilft Teams in der Schweiz & Deutschland, Arbeitszeiten sauber zu erfassen, Abwesenheiten zu planen und Löhne rechtssicher abzurechnen – ohne Excel-Chaos."
                                )}
                            </p>
                            <div className="lp-cta-buttons">
                                <Link className="lp-btn lp-primary" to="/register">
                                    {t("landing.cta.try", "Kostenlos testen")}
                                </Link>
                                <button className="lp-btn lp-secondary" type="button" onClick={handleDemo}>
                                    {t("landing.cta.demo", "Live-Demo ansehen")}
                                </button>
                            </div>
                            <p className="lp-hero-note">
                                {t(
                                    "landing.hero.note",
                                    "🕒 Ohne Kreditkarte · jederzeit kündbar · Schweizer Server"
                                )}
                            </p>
                        </div>
                        <div className="lp-hero-media">
                            <div className="lp-hero-media-stack">
                                <img
                                    src="/img/Ich.png"
                                    alt={t("landing.hero.photoAlt", "Portraitfoto der Gründerin/des Gründers")}
                                    className="lp-hero-photo"
                                    loading="lazy"
                                />
                                <div className="lp-hero-about">
                                    <h3 className="lp-h3">{t("landing.hero.aboutTitle", "Über mich")}</h3>
                                    <p className="lp-text-muted">
                                        {t(
                                            "landing.hero.aboutText",
                                            "Ich habe Chrono entwickelt, damit Teams ihre Zeit klar, fair und einfach organisieren können – mit Fokus auf die Schweiz."
                                        )}
                                    </p>
                                </div>
                            </div>
                        </div>
                    </div>
                </header>

                {/* SOCIAL PROOF */}
                <section className="lp-social-proof" aria-label={t("landing.social.title", "Vertrauen von Teams aus CH & DE")}>
                    <div className="lp-container">
                        <h2 className="lp-h2">{t("landing.social.title", "Vertrauen von Teams aus CH & DE")}</h2>
                        <ul className="lp-logo-row" role="list">
                            {logos.map((logo, index) => (
                                <li key={index}>{logo}</li>
                            ))}
                        </ul>
                        <p className="lp-text-muted lp-social-note">
                            {t(
                                "landing.social.note",
                                "Chrono läuft auf sicheren Schweizer Servern und ist vollständig DSGVO-konform."
                            )}
                        </p>
                    </div>
                </section>

                {/* FEATURES */}
                <section className="lp-section" id="features">
                    <div className="lp-container">
                        <h2 className="lp-h2">
                            {t("landing.features.title", "Alles drin, was du brauchst – ohne Ballast.")}
                        </h2>
                        <div className="lp-features-grid" role="list" aria-label="Produktfunktionen">
                            {features.map((feature, index) => (
                                <FeatureCard key={index} icon={feature.icon} title={feature.title} lines={feature.lines} />
                            ))}
                        </div>
                    </div>
                </section>

                {/* STEPS */}
                <section className="lp-section" id="start">
                    <div className="lp-container">
                        <h2 className="lp-h2">{t("landing.steps.title", "So startest du mit Chrono in 3 Schritten")}</h2>
                        <div className="lp-steps-grid">
                            {steps.map((step, index) => (
                                <StepCard key={index} n={step.n} title={step.title} text={step.text} />
                            ))}
                        </div>
                        <Link className="lp-text-link" to="/register">
                            {t("landing.steps.link", "Zum Registrieren →")}
                        </Link>
                    </div>
                </section>

                {/* PRICING */}
                <section className="lp-pricing" id="preise">
                    <div className="lp-container lp-pricing-inner">
                        <div className="lp-pricing-copy">
                            <h2 className="lp-h2">{t("landing.pricing.title", "Baukasten-Preismodell – zahle nur, was du brauchst.")}</h2>
                            <p className="lp-text-muted">
                                {t(
                                    "landing.pricing.text",
                                    "Ab 5 CHF pro Mitarbeitendem im Monat. Module für Urlaub, Lohnabrechnung und mehr einfach dazubuchen."
                                )}
                            </p>
                        </div>
                        <Link className="lp-btn lp-primary" to="/preise">
                            {t("landing.pricing.cta", "Preise ansehen & Konfiguration starten")}
                        </Link>
                    </div>
                </section>

                {/* CONTACT */}
                <section className="lp-contact" id="kontakt" aria-labelledby="kontakt-title">
                    <div className="lp-container lp-contact-inner">
                        <div className="lp-contact-copy">
                            <h2 id="kontakt-title" className="lp-h2">{t("landing.contact.title", "Lass uns über dein Team sprechen")}</h2>
                            <p className="lp-text-muted">
                                {t(
                                    "landing.contact.text",
                                    "Du willst Chrono zuerst sehen oder hast Fragen zur Lohnabrechnung in CH/DE? Schreib uns – wir melden uns in der Regel noch am selben Werktag."
                                )}
                            </p>
                        </div>
                        <details className="lp-contact-accordion" open>
                            <summary className="lp-contact-summary">
                                {t("landing.contact.summary", "Kontaktformular öffnen")}
                                <span aria-hidden="true" className="lp-contact-summary-icon">⌄</span>
                            </summary>
                            <form className="lp-contact-form" onSubmit={submitContact}>
                                <div className="lp-form-row">
                                    <div className="lp-form-group">
                                        <label htmlFor="name" className="lp-label">{t("landing.contact.name", "Name")}</label>
                                        <input
                                            id="name"
                                            name="name"
                                            type="text"
                                            required
                                            value={contact.name}
                                            onChange={onChange}
                                            className="lp-input"
                                        />
                                    </div>
                                    <div className="lp-form-group">
                                        <label htmlFor="email" className="lp-label">{t("landing.contact.email", "E-Mail")}</label>
                                        <input
                                            id="email"
                                            name="email"
                                            type="email"
                                            required
                                            value={contact.email}
                                            onChange={onChange}
                                            className="lp-input"
                                        />
                                    </div>
                                </div>

                                <div className="lp-form-group">
                                    <label htmlFor="message" className="lp-label">{t("landing.contact.msg", "Nachricht")}</label>
                                    <textarea
                                        id="message"
                                        name="message"
                                        rows={4}
                                        required
                                        value={contact.message}
                                        onChange={onChange}
                                        placeholder={t("landing.contact.placeholder", "Wie können wir helfen?")}
                                        className="lp-textarea"
                                    />
                                </div>

                                <div className="lp-form-actions">
                                    <button className="lp-btn lp-primary" type="submit" disabled={sending}>
                                        {sending
                                            ? t("landing.contact.sending", "Wird gesendet…")
                                            : t("landing.contact.send", "Nachricht senden")}
                                    </button>
                                    <p className="lp-form-hint lp-text-muted">
                                        {t("landing.contact.hint", "Antwort in der Regel noch am selben Werktag.")}
                                    </p>
                                </div>
                            </form>
                        </details>
                    </div>
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
