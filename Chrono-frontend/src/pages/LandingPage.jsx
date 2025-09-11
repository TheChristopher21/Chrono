// src/pages/LandingPage.jsx — Landing (final, ohne Mock, fully scoped)
import React, { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import Navbar from "../components/Navbar";
import "../styles/LandingPageScoped.css";
import { useTranslation } from "../context/LanguageContext";
import { useNotification } from "../context/NotificationContext";
import { useAuth } from "../context/AuthContext";
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
            title: t("landing.features.items.0.title", "Arbeitszeit & Lohn in einem"),
            bullets: [
                t("landing.features.items.0.bullets.0", "Arbeitsstunden festhalten"),
                t("landing.features.items.0.bullets.1", "Urlaub und Extra-Zeit sehen"),
                t("landing.features.items.0.bullets.2", "Alles an einem Ort"),
            ],
        },
        {
            icon: "🇨🇭🇩🇪",
            title: t("landing.features.items.1.title", "Lohnzettel für CH & DE"),
            bullets: [
                t("landing.features.items.1.bullets.0", "Als Datei speichern"),
                t("landing.features.items.1.bullets.1", "Angaben leicht ändern"),
                t("landing.features.items.1.bullets.2", "Schweiz und Deutschland abgedeckt"),
            ],
        },
        {
            icon: "👆",
            title: t("landing.features.items.2.title", "Stempeln mit Karte oder Web"),
            bullets: [
                t("landing.features.items.2.bullets.0", "Mit Karte ein- und ausstempeln"),
                t("landing.features.items.2.bullets.1", "Auch im Browser oder Handy"),
                t("landing.features.items.2.bullets.2", "Geht sogar ohne Internet"),
            ],
        },
        {
            icon: "📅",
            title: t("landing.features.items.3.title", "Urlaub & Überstunden im Blick"),
            bullets: [
                t("landing.features.items.3.bullets.0", "Urlaub online beantragen"),
                t("landing.features.items.3.bullets.1", "Resttage sofort sehen"),
                t("landing.features.items.3.bullets.2", "Überstunden automatisch verrechnet"),
            ],
        },
        {
            icon: "🔔",
            title: t("landing.features.items.4.title", "Hinweise & Erinnerungen"),
            bullets: [
                t("landing.features.items.4.bullets.0", "Info bei neuem Lohnzettel"),
                t("landing.features.items.4.bullets.1", "Hinweis bei Anträgen"),
                t("landing.features.items.4.bullets.2", "Erinnerung, wenn du vergisst auszustempeln"),
            ],
        },
        {
            icon: "🛡️",
            title: t("landing.features.items.5.title", "Sichere Daten in der Schweiz"),
            bullets: [
                t("landing.features.items.5.bullets.0", "Daten bleiben in der Schweiz"),
                t("landing.features.items.5.bullets.1", "Alles ist geschützt"),
                t("landing.features.items.5.bullets.2", "Nur Berechtigte sehen deine Daten"),
                t("landing.features.items.5.bullets.3", "Zusätzlicher Login-Schutz möglich"),
            ],
        },
        {
            icon: "👥",
            title: t("landing.features.items.6.title", "Team & Projekte verwalten"),
            bullets: [
                t("landing.features.items.6.bullets.0", "Mitarbeitende hinzufügen"),
                t("landing.features.items.6.bullets.1", "Kunden und Projekte anlegen"),
                t("landing.features.items.6.bullets.2", "Alles im Blick behalten"),
            ],
        },
        {
            icon: "📊",
            title: t("landing.features.items.7.title", "Berichte zum Mitnehmen"),
            bullets: [
                t("landing.features.items.7.bullets.0", "Übersichtliche Dateien herunterladen"),
                t("landing.features.items.7.bullets.1", "Schnell sehen, wer wie viel gearbeitet hat"),
                t("landing.features.items.7.bullets.2", "Praktisch für Steuer und Abrechnung"),
            ],
        },
    ];

    const steps = [
        {
            n: "1",
            title: t("landing.steps.items.0.title", "Registrieren"),
            text: t("landing.steps.items.0.text", "Kostenlos starten – ohne Kreditkarte."),
        },
        {
            n: "2",
            title: t("landing.steps.items.1.title", "Team & Projekte anlegen"),
            text: t("landing.steps.items.1.text", "Mitarbeitende, Projekte und Kunden hinzufügen."),
        },
        {
            n: "3",
            title: t("landing.steps.items.2.title", "Loslegen"),
            text: t(
                "landing.steps.items.2.text",
                "Stempeln, Urlaub beantragen, Abrechnung erstellen."
            ),
        },
    ];

    return (
        <div className="landing-page scoped-landing">
            <Navbar />

            <main>
                {/* HERO (einspaltig, ohne Mock) */}
                <header className="lp-hero lp-section lp-section-lg" id="home">
                    <div className="lp-hero-single">
                        <div className="lp-hero-content">
                            <div className="lp-hero-badge">{t("landing.hero.badge", "Fair · Klar · Zuverlässig")}</div>
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
