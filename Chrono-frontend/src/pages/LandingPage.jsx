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

    const heroStats = [
        {
            value: t("landing.hero.stats.0.value", "< 10 Min."),
            label: t(
                "landing.hero.stats.0.label",
                "Von Registrierung bis zum ersten Zeiteintrag"
            ),
        },
        {
            value: t("landing.hero.stats.1.value", "200+"),
            label: t("landing.hero.stats.1.label", "KMU aus Schweiz & Deutschland"),
        },
        {
            value: t("landing.hero.stats.2.value", "98%"),
            label: t("landing.hero.stats.2.label", "Support-Zufriedenheit im Onboarding"),
        },
    ];

    const explainers = [
        {
            icon: "⚙️",
            text: t(
                "landing.hero.explain.0",
                "Automatisierte Workflows für Lohnläufe, Bewilligungen und Erinnerungen."
            ),
        },
        {
            icon: "📈",
            text: t(
                "landing.hero.explain.1",
                "Live-Dashboards für Führung & Finance mit exportfertigen Reports."
            ),
        },
        {
            icon: "🔐",
            text: t(
                "landing.hero.explain.2",
                "Schweizer Rechenzentren, DSGVO-konform und Audit-ready."
            ),
        },
    ];

    const features = [
        {
            icon: "🧾",
            title: t("landing.features.items.0.title", "Arbeitszeiterfassung & Payroll in einem System"),
            bullets: [
                t("landing.features.items.0.bullets.0", "Arbeitsstunden in Echtzeit protokollieren"),
                t("landing.features.items.0.bullets.1", "Überstunden, Ferien und Abwesenheiten automatisch auswerten"),
                t("landing.features.items.0.bullets.2", "Zentrale Plattform für HR, Payroll und Compliance"),
            ],
        },
        {
            icon: "🇨🇭🇩🇪",
            title: t("landing.features.items.1.title", "Lohnabrechnung für Schweiz & Deutschland"),
            bullets: [
                t("landing.features.items.1.bullets.0", "Exportfertige Lohnabrechnungen inklusive Sozialabgaben"),
                t("landing.features.items.1.bullets.1", "Flexible Anpassung an kantonale & bundesweite Vorgaben"),
                t("landing.features.items.1.bullets.2", "Mehrsprachige Payslips für internationale Teams"),
            ],
        },
        {
            icon: "👆",
            title: t("landing.features.items.2.title", "Zeiterfassung per Badge, Web oder Mobile"),
            bullets: [
                t("landing.features.items.2.bullets.0", "Kiosk-Terminal mit NFC-/QR-Badge für Fertigung & Retail"),
                t("landing.features.items.2.bullets.1", "Browser- und Smartphone-App für hybride Teams"),
                t("landing.features.items.2.bullets.2", "Offline-fähig für Baustelle, Montage & Außendienst"),
            ],
        },
        {
            icon: "📅",
            title: t("landing.features.items.3.title", "Urlaub, Überstunden & Schichtplanung im Blick"),
            bullets: [
                t("landing.features.items.3.bullets.0", "Digitale Abwesenheitsanträge mit Genehmigungs-Workflow"),
                t("landing.features.items.3.bullets.1", "Transparente Ferien- und Gleitzeitkonten für alle Mitarbeitenden"),
                t("landing.features.items.3.bullets.2", "Automatische Überstundenverrechnung in der Lohnabrechnung"),
            ],
        },
        {
            icon: "🔔",
            title: t("landing.features.items.4.title", "Automatische Hinweise & Compliance-Alerts"),
            bullets: [
                t("landing.features.items.4.bullets.0", "Sofortige Benachrichtigung bei neuen Lohnabrechnungen"),
                t("landing.features.items.4.bullets.1", "Workflows und Reminder für Urlaubs- & Zeitausgleichsanträge"),
                t("landing.features.items.4.bullets.2", "Warnungen bei fehlenden Stempelungen oder Verstößen gegen Arbeitszeitgesetz"),
            ],
        },
        {
            icon: "🛡️",
            title: t("landing.features.items.5.title", "Datenschutz & IT-Sicherheit made in Switzerland"),
            bullets: [
                t("landing.features.items.5.bullets.0", "ISO-zertifizierte Rechenzentren in der Schweiz"),
                t("landing.features.items.5.bullets.1", "Verschlüsselung auf Transport- und Speicherebene"),
                t("landing.features.items.5.bullets.2", "Granulare Rollen- & Rechteverwaltung für HR und Payroll"),
                t("landing.features.items.5.bullets.3", "Mehrstufige Authentifizierung & Audit-Logs"),
            ],
        },
        {
            icon: "👥",
            title: t("landing.features.items.6.title", "Teams, Kunden & Projekte strukturiert steuern"),
            bullets: [
                t("landing.features.items.6.bullets.0", "Neue Mitarbeitende in Sekunden onboarden"),
                t("landing.features.items.6.bullets.1", "Kunden, Projekte & Kostenstellen zentral verwalten"),
                t("landing.features.items.6.bullets.2", "Budget- und Ressourcenplanung mit Live-Status"),
            ],
        },
        {
            icon: "📊",
            title: t("landing.features.items.7.title", "Business-Intelligence & Exportberichte"),
            bullets: [
                t("landing.features.items.7.bullets.0", "Dashboards für Produktivität, Kostenstellen & HR-Kennzahlen"),
                t("landing.features.items.7.bullets.1", "Export als Excel, PDF oder DATEV für Steuerberater"),
                t("landing.features.items.7.bullets.2", "Analysen für Forecasting, Compliance und Audit"),
            ],
        },
        {
            icon: "🤝",
            title: t("landing.features.items.8.title", "Persönlicher Onboarding- & Premium-Support"),
            bullets: [
                t("landing.features.items.8.bullets.0", "Direkter Draht zum Chrono-Expertenteam per Mail & Telefon"),
                t("landing.features.items.8.bullets.1", "Reaktionszeiten innerhalb eines Werktags garantiert"),
                t("landing.features.items.8.bullets.2", "Geführtes Onboarding & Schulungen für HR, Payroll und Teamleads"),
            ],
        },
    ];

    const steps = [
        {
            n: "1",
            title: t("landing.steps.items.0.title", "Registrieren & Setup starten"),
            text: t("landing.steps.items.0.text", "In wenigen Minuten Account anlegen, Mitarbeitende importieren und Systeme verbinden."),
        },
        {
            n: "2",
            title: t("landing.steps.items.1.title", "Team, Projekte & Richtlinien definieren"),
            text: t(
                "landing.steps.items.1.text",
                "Arbeitszeitmodelle, Genehmigungswege und Kostenstellen konfigurieren – ganz ohne IT-Aufwand."
            ),
        },
        {
            n: "3",
            title: t("landing.steps.items.2.title", "Live gehen & Ergebnisse sehen"),
            text: t(
                "landing.steps.items.2.text",
                "Zeiten erfassen, Urlaube digital freigeben und Lohnläufe mit einem Klick abschließen."
            ),
        },
    ];

    const proofPoints = [
        {
            value: t("landing.proof.0.value", "35%"),
            label: t(
                "landing.proof.0.label",
                "weniger administrativer Aufwand bei der Lohnabrechnung"
            ),
        },
        {
            value: t("landing.proof.1.value", "4x"),
            label: t(
                "landing.proof.1.label",
                "schnellere Freigaben für Überstunden und Urlaubsanträge"
            ),
        },
        {
            value: t("landing.proof.2.value", "99,9%"),
            label: t("landing.proof.2.label", "Systemverfügbarkeit im letzten Jahr"),
        },
    ];

    const integrations = [
        "Abacus",
        "Bexio",
        "DATEV",
        "Sage",
        "SAP SuccessFactors",
        "Microsoft Teams",
        "Slack",
        "Google Workspace",
    ];

    return (
        <div className="landing-page scoped-landing">
            <Navbar />

            <main>
                {/* HERO */}
                <header className="lp-hero lp-section lp-section-lg" id="home">
                    <div className="lp-hero-grid">
                        <div className="lp-hero-copy">
                            <div className="lp-hero-badge">
                                {t("landing.hero.badge", "Zeiterfassung · Payroll · HR Automation")}
                            </div>
                            <h1 className="lp-h1">
                                {t(
                                    "landing.hero.title",
                                    "Chrono Zeiterfassung & Lohnabrechnung – die smarte HR-Software für KMU"
                                )}
                            </h1>
                            <p className="lp-lead">
                                {t(
                                    "landing.hero.sub",
                                    "Eine begeisternde Plattform für HR, Finance und Teamleads: Zeiten erfassen, Lohnläufe abschließen und Compliance sichern – alles in Minuten statt Stunden."
                                )}
                            </p>

                            <div className="lp-cta-buttons">
                                <Link className="lp-btn lp-primary" to="/register">
                                    {t("landing.cta.try", "Kostenlos testen")}
                                </Link>
                                <button className="lp-btn lp-secondary" onClick={handleDemo} type="button">
                                    {t("landing.cta.demo", "Geführte Demo")}
                                </button>
                                <Link className="lp-btn lp-link" to="/login">
                                    {t("landing.cta.login", "Ich habe bereits einen Account")}
                                </Link>
                            </div>

                            <ul className="lp-usp-chips" role="list">
                                <li>{t("landing.hero.chips.server", "🇨🇭 Datenhosting in der Schweiz")}</li>
                                <li>{t("landing.hero.chips.gdpr", "🔐 DSGVO- & OR-konform")}</li>
                                <li>{t("landing.hero.chips.noExcel", "🧮 Schluss mit Excel und manuellen Stundenzetteln")}</li>
                            </ul>

                            <div className="lp-hero-explain" role="list">
                                {explainers.map((item, index) => (
                                    <div key={index} className="lp-explain-item" role="listitem">
                                        <span className="lp-explain-ico" aria-hidden="true">
                                            {item.icon}
                                        </span>
                                        <p>{item.text}</p>
                                    </div>
                                ))}
                            </div>

                            <ul className="lp-hero-trust" role="list">
                                <li>{t("landing.hero.trust.0", "Trusted by Produktionsbetriebe, Agenturen & Verwaltungen")}</li>
                                <li>{t("landing.hero.trust.1", "ISO 27001 Hosting & Schweizer Datenschutz")}</li>
                            </ul>
                        </div>

                        <div className="lp-hero-visual" aria-hidden="true">
                            <div className="lp-visual-orb" />
                            <div className="lp-visual-card lp-visual-main">
                                <h3>Chrono Control Center</h3>
                                <ul>
                                    <li>{t("landing.visual.0", "Live-Zeiterfassung aller Standorte")}</li>
                                    <li>{t("landing.visual.1", "Genehmigungsstatus auf einen Blick")}</li>
                                    <li>{t("landing.visual.2", "Payroll-ready Exporte in Sekunden")}</li>
                                </ul>
                                <div className="lp-visual-progress">
                                    <span>{t("landing.visual.progress", "Payroll Run %")}</span>
                                    <div className="lp-progress-bar">
                                        <span style={{ width: "82%" }} />
                                    </div>
                                </div>
                            </div>
                            <div className="lp-visual-stats">
                                {heroStats.map((stat, index) => (
                                    <div key={index} className="lp-visual-stat">
                                        <strong>{stat.value}</strong>
                                        <span>{stat.label}</span>
                                    </div>
                                ))}
                            </div>
                        </div>
                    </div>
                </header>

                {/* FEATURES */}
                <section className="lp-section" id="features">
                    <h2 className="lp-h2">{t("landing.features.title", "Alles, was moderne Personalverwaltung heute braucht.")}</h2>
                    <p className="lp-section-sub">
                        {t(
                            "landing.features.sub",
                            "Digitale Zeiterfassung, automatische Lohnabrechnung und transparente HR-Prozesse – modular aufgebaut und sofort einsatzbereit."
                        )}
                    </p>
                    <div className="lp-features-grid" role="list" aria-label="Featureliste">
                        {features.map((f, i) => (
                            <FeatureCard key={i} icon={f.icon} title={f.title} bullets={f.bullets} text={f.text} />
                        ))}
                    </div>
                </section>

                {/* PROOF SECTION */}
                <section className="lp-section lp-proof" aria-label={t("landing.proof.title", "Ergebnisse in Zahlen")}>
                    <div className="lp-proof-inner">
                        <h2 className="lp-h2">{t("landing.proof.heading", "Mit Chrono steigerst du Effizienz & Zufriedenheit.")}</h2>
                        <p className="lp-section-sub">
                            {t(
                                "landing.proof.copy",
                                "Unternehmen setzen auf Chrono, um Zeiterfassung, Payroll und HR-Prozesse zu beschleunigen – messbar vom ersten Monat an."
                            )}
                        </p>
                        <div className="lp-proof-grid">
                            {proofPoints.map((point, index) => (
                                <div key={index} className="lp-proof-item">
                                    <strong>{point.value}</strong>
                                    <span>{point.label}</span>
                                </div>
                            ))}
                        </div>
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

                {/* INTEGRATIONS */}
                <section className="lp-section lp-integrations" aria-label={t("landing.integrations.title", "Integrationen & Add-ons")}
                >
                    <div className="lp-integrations-inner">
                        <h2 className="lp-h2">{t("landing.integrations.heading", "Verbinde Chrono mit euren Lieblings-Tools.")}</h2>
                        <p className="lp-section-sub">
                            {t(
                                "landing.integrations.copy",
                                "Fertige Schnittstellen zu Buchhaltung, Collaboration und HR-Systemen sorgen für nahtlose Übergaben."
                            )}
                        </p>
                        <div className="lp-integrations-grid" role="list">
                            {integrations.map((name, index) => (
                                <span key={index} className="lp-integrations-item" role="listitem">
                                    {name}
                                </span>
                            ))}
                        </div>
                    </div>
                </section>

                {/* CONTACT */}
                <section className="lp-section" id="kontakt" aria-labelledby="kontakt-title">
                    <h2 id="kontakt-title" className="lp-h2">{t("landing.contact.title", "Kostenlose Beratung anfordern")}</h2>
                    <form className="lp-contact-form" onSubmit={submitContact}>
                        <div className="lp-form-row">
                            <div className="lp-form-group">
                                <label htmlFor="name" className="lp-label">{t("landing.contact.name", "Name & Unternehmen")}</label>
                                <input id="name" name="name" type="text" required value={contact.name} onChange={onChange} className="lp-input" />
                            </div>
                            <div className="lp-form-group">
                                <label htmlFor="email" className="lp-label">{t("landing.contact.email", "E-Mail")}</label>
                                <input id="email" name="email" type="email" required value={contact.email} onChange={onChange} className="lp-input" />
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
                                placeholder={t(
                                    "landing.contact.placeholder",
                                    "Wie unterstützt Chrono eure Zeiterfassung, Lohnabrechnung oder HR-Prozesse?"
                                )}
                                className="lp-textarea"
                            />
                        </div>

                        <div className="lp-form-actions">
                            <button className="lp-btn lp-primary" type="submit">{t("landing.contact.send", "Senden")}</button>
                            <p className="lp-form-hint lp-text-muted">{t("landing.contact.hint", "Antwort inklusive Demo-Termin-Vorschlag innerhalb von 24 Stunden.")}</p>
                        </div>
                    </form>
                </section>

                <section className="lp-cta-banner" aria-label={t("landing.banner.title", "Abschluss CTA")}
                >
                    <div className="lp-cta-inner">
                        <div>
                            <h2 className="lp-h2">{t("landing.banner.heading", "Bereit für produktive HR-Prozesse?")}</h2>
                            <p>
                                {t(
                                    "landing.banner.copy",
                                    "Starte heute mit Chrono, erhalte ein persönliches Onboarding und sichere dir Reporting-Vorlagen für deine nächsten Payroll-Läufe."
                                )}
                            </p>
                        </div>
                        <div className="lp-banner-actions">
                            <Link className="lp-btn lp-primary" to="/register">
                                {t("landing.banner.primary", "Jetzt gratis testen")}
                            </Link>
                            <button className="lp-btn lp-secondary" onClick={handleDemo} type="button">
                                {t("landing.banner.secondary", "Demo buchen")}
                            </button>
                        </div>
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
