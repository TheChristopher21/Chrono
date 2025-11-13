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
                        <div className="lp-hero-mock" aria-hidden="true">
                            <div className="lp-mock-window" role="presentation">
                                <div className="lp-mock-toolbar">
                                    <div className="lp-mock-breadcrumb">
                                        <span>Chrono Admin</span>
                                        <span className="lp-mock-divider">›</span>
                                        <span>Teamübersicht</span>
                                    </div>
                                    <div className="lp-mock-toolbar-meta">
                                        <span className="lp-mock-pill lp-pill-quiet">KW 12 · 18.–24. März</span>
                                        <span className="lp-mock-pill lp-pill-ghost">Letztes Update 08:45</span>
                                    </div>
                                </div>
                                <div className="lp-mock-header">
                                    <div className="lp-mock-heading">
                                        <span className="lp-mock-eyebrow">Chrono Admin</span>
                                        <h4 className="lp-mock-title">Team Dashboard</h4>
                                        <span className="lp-mock-subtitle">Team Zürich · 28 Mitarbeitende</span>
                                    </div>
                                    <div className="lp-mock-header-meta">
                                        <div className="lp-mock-chip-group">
                                            <span className="lp-mock-tag">Team Zürich</span>
                                            <span className="lp-mock-pill lp-pill-quiet">3 Standorte</span>
                                        </div>
                                        <div className="lp-mock-avatar-group">
                                            <span className="lp-mock-avatar">AK</span>
                                            <span className="lp-mock-avatar">DS</span>
                                            <span className="lp-mock-avatar">JN</span>
                                            <span className="lp-mock-avatar more">+8</span>
                                        </div>
                                    </div>
                                </div>
                                <div className="lp-mock-filter-row">
                                    <div className="lp-mock-filter">
                                        <span className="lp-mock-filter-label">Zeitraum</span>
                                        <span className="lp-mock-filter-value">Diese Woche</span>
                                    </div>
                                    <div className="lp-mock-filter">
                                        <span className="lp-mock-filter-label">Standort</span>
                                        <span className="lp-mock-filter-value">Alle Standorte</span>
                                    </div>
                                    <div className="lp-mock-filter">
                                        <span className="lp-mock-filter-label">Ansicht</span>
                                        <span className="lp-mock-filter-value">Verantwortliche</span>
                                    </div>
                                </div>
                                <div className="lp-mock-body">
                                    <div className="lp-mock-kpi-grid">
                                        <div className="lp-mock-kpi">
                                            <span className="lp-mock-kpi-label">Arbeitsstunden</span>
                                            <span className="lp-mock-kpi-value">1’245 h</span>
                                            <span className="lp-mock-kpi-trend positive">+8% vs. Vorwoche</span>
                                        </div>
                                        <div className="lp-mock-kpi">
                                            <span className="lp-mock-kpi-label">Überstunden</span>
                                            <span className="lp-mock-kpi-value">+184 h</span>
                                            <span className="lp-mock-kpi-trend warning">3 Teams über Ziel</span>
                                        </div>
                                        <div className="lp-mock-kpi">
                                            <span className="lp-mock-kpi-label">Urlaub & Absenzen</span>
                                            <span className="lp-mock-kpi-value">12 offen</span>
                                            <span className="lp-mock-kpi-trend neutral">4 neue heute</span>
                                        </div>
                                        <div className="lp-mock-kpi">
                                            <span className="lp-mock-kpi-label">Lohnläufe</span>
                                            <span className="lp-mock-kpi-value">100%</span>
                                            <span className="lp-mock-kpi-trend positive">Abgeschlossen</span>
                                        </div>
                                    </div>
                                    <div className="lp-mock-main-grid">
                                        <div className="lp-mock-table-card">
                                            <div className="lp-mock-table-head">
                                                <span>Mitarbeitende</span>
                                                <span>Saldo</span>
                                                <span>Status</span>
                                            </div>
                                            <div className="lp-mock-table-row">
                                                <div>
                                                    <span className="lp-mock-name">Anna Keller</span>
                                                    <span className="lp-mock-meta">Marketing · 100%</span>
                                                </div>
                                                <span className="lp-mock-hours">+12:30 h</span>
                                                <span className="lp-mock-badge is-positive">Überstunden</span>
                                            </div>
                                            <div className="lp-mock-table-row">
                                                <div>
                                                    <span className="lp-mock-name">David Steiner</span>
                                                    <span className="lp-mock-meta">Produktion · 80%</span>
                                                </div>
                                                <span className="lp-mock-hours">-04:15 h</span>
                                                <span className="lp-mock-badge is-warning">Fehlzeit</span>
                                            </div>
                                            <div className="lp-mock-table-row">
                                                <div>
                                                    <span className="lp-mock-name">J. Nguyen</span>
                                                    <span className="lp-mock-meta">Projekt Zephyr</span>
                                                </div>
                                                <span className="lp-mock-hours">+02:40 h</span>
                                                <span className="lp-mock-badge is-info">Projektarbeit</span>
                                            </div>
                                            <div className="lp-mock-table-foot">
                                                <span>Mehr Details</span>
                                                <span className="lp-mock-pill lp-pill-ghost">Teamzeiten öffnen</span>
                                            </div>
                                        </div>
                                        <div className="lp-mock-sidebar">
                                            <div className="lp-mock-inbox-card">
                                                <div className="lp-mock-inbox-head">
                                                    <span>Anfragen</span>
                                                    <span className="lp-mock-pill lp-pill-quiet">4 offen</span>
                                                </div>
                                                <div className="lp-mock-inbox-list">
                                                    <div className="lp-mock-inbox-item is-pending">
                                                        <span className="lp-mock-inbox-title">Urlaub · Karin Frei</span>
                                                        <span className="lp-mock-inbox-meta">21.–24. März · wartet</span>
                                                    </div>
                                                    <div className="lp-mock-inbox-item is-positive">
                                                        <span className="lp-mock-inbox-title">Korrektur · Luca Meier</span>
                                                        <span className="lp-mock-inbox-meta">Check-in angepasst · genehmigt</span>
                                                    </div>
                                                    <div className="lp-mock-inbox-item is-warning">
                                                        <span className="lp-mock-inbox-title">Sick Leave · Jana Roth</span>
                                                        <span className="lp-mock-inbox-meta">seit 2 Tagen · Arztzeugnis fehlt</span>
                                                    </div>
                                                    <div className="lp-mock-inbox-footer">Alle Anfragen ansehen →</div>
                                                </div>
                                            </div>
                                            <div className="lp-mock-week-card">
                                                <div className="lp-mock-week-head">
                                                    <span>Wochenverlauf</span>
                                                    <span className="lp-mock-pill lp-pill-quiet">Soll 212 h</span>
                                                </div>
                                                <div className="lp-mock-week-days">
                                                    <div className="lp-mock-week-day">
                                                        <div className="lp-mock-week-day-header">
                                                            <span className="lp-mock-week-label">Mo</span>
                                                            <span className="lp-mock-week-hours">9:15 h</span>
                                                        </div>
                                                        <div className="lp-mock-progress">
                                                            <div className="lp-mock-progress-fill is-positive" style={{ width: "92%" }} />
                                                        </div>
                                                        <span className="lp-mock-week-meta positive">+04:30 h</span>
                                                    </div>
                                                    <div className="lp-mock-week-day">
                                                        <div className="lp-mock-week-day-header">
                                                            <span className="lp-mock-week-label">Di</span>
                                                            <span className="lp-mock-week-hours">8:05 h</span>
                                                        </div>
                                                        <div className="lp-mock-progress">
                                                            <div className="lp-mock-progress-fill" style={{ width: "84%" }} />
                                                        </div>
                                                        <span className="lp-mock-week-meta neutral">Im Rahmen</span>
                                                    </div>
                                                    <div className="lp-mock-week-day">
                                                        <div className="lp-mock-week-day-header">
                                                            <span className="lp-mock-week-label">Mi</span>
                                                            <span className="lp-mock-week-hours">6:10 h</span>
                                                        </div>
                                                        <div className="lp-mock-progress">
                                                            <div className="lp-mock-progress-fill is-warning" style={{ width: "58%" }} />
                                                        </div>
                                                        <span className="lp-mock-week-meta warning">-02:15 h</span>
                                                    </div>
                                                    <div className="lp-mock-week-day">
                                                        <div className="lp-mock-week-day-header">
                                                            <span className="lp-mock-week-label">Do</span>
                                                            <span className="lp-mock-week-hours">7:40 h</span>
                                                        </div>
                                                        <div className="lp-mock-progress">
                                                            <div className="lp-mock-progress-fill is-info" style={{ width: "76%" }} />
                                                        </div>
                                                        <span className="lp-mock-week-meta info">Urlaubsspitze</span>
                                                    </div>
                                                    <div className="lp-mock-week-day">
                                                        <div className="lp-mock-week-day-header">
                                                            <span className="lp-mock-week-label">Fr</span>
                                                            <span className="lp-mock-week-hours">4:00 h</span>
                                                        </div>
                                                        <div className="lp-mock-progress">
                                                            <div className="lp-mock-progress-fill is-muted" style={{ width: "44%" }} />
                                                        </div>
                                                        <span className="lp-mock-week-meta muted">Feiertag (ZH)</span>
                                                    </div>
                                                </div>
                                                <div className="lp-mock-week-footer">Teamplanung öffnen →</div>
                                            </div>
                                        </div>
                                    </div>
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
