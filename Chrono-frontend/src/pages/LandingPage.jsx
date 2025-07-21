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
            title: t('landingPage.featureReportsTitle'),
            text: t('landingPage.featureReportsText'),
        },
        {
            icon: "🧾",
            title: t('landingPage.featureSmartTitle'),
            text: t('landingPage.featureSmartText'),
        },
        {
            icon: "🇨🇭🇩🇪",
            title: t('landingPage.featureVacationTitle'),
            text: t('landingPage.featureVacationText'),
        },
        {
            icon: "👆",
            title: t('landingPage.featureNfcTitle'),
            text: t('landingPage.featureNfcText'),
        },
        {
            icon: "📅",
            title: t('landingPage.featureDuplicateTitle'),
            text: t('landingPage.featureDuplicateText'),
        },
        {
            icon: "⏰",
            title: t('landingPage.featureAutoPunchOutTitle'),
            text: t('landingPage.featureAutoPunchOutText'),
        },
        {
            icon: "🟢",
            title: t('landingPage.featureDirectTitle'),
            text: t('landingPage.featureDirectText'),
        },
        {
            icon: "📄",
            title: t('landingPage.featureHistoryTitle'),
            text: t('landingPage.featureHistoryText'),
        },
        {
            icon: "📊",
            title: t('landingPage.featurePercentTitle'),
            text: t('landingPage.featurePercentText'),
        },
        {
            icon: "🔔",
            title: t('landingPage.featureAdminTitle'),
            text: t('landingPage.featureAdminText'),
        },
        {
            icon: "🛡️",
            title: t('landingPage.featureOvertimeTitle'),
            text: t('landingPage.featureOvertimeText'),
        },
    ];


    const steps = [
        { n: "1", title: t('landingPage.step1Title'), text: t('landingPage.step1Text') },
        { n: "2", title: t('landingPage.step2Title'), text: t('landingPage.step2Text') },
        { n: "3", title: t('landingPage.step3Title'), text: t('landingPage.step3Text') },
    ];

    return (
        <div className="landing-page scoped-landing">
            <Navbar />
            <main>
                {/* HERO *********************************************************** */}
                <header className="landing-hero site-section lg" id="home">
                    <div className="section-inner">
                        <h1>{t('landingPage.headline')}</h1>
                        <div className="cta-buttons">
                            <Link to="/register" className="btn primary">
                                {t('landingPage.ctaPrimary')}
                            </Link>
                        </div>
                    </div>
                </header>

                {/* INFO *********************************************************** */}
                <section className="info-section site-section">
                    <div className="section-inner">
                        <h2 className="section-title accent">{t('landingPage.whyTitle')}</h2>
                        <div className="info-box">
                            <p>
                                {t('landingPage.infoText')}
                            </p>
                        </div>
                    </div>
                </section>

                {/* FEATURES ******************************************************* */}
                <section className="features-section site-section" id="features">
                    <div className="section-inner">
                        <h2>{t('landingPage.featuresTitle')}</h2>
                        <p>
                            {t('landingPage.featuresSub')}<br />
                            <b>{t('landingPage.featureReportsText')}</b>
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
                        <h2>{t('landingPage.stepsTitle')}</h2>
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
                        <h3>{t('landingPage.contactTitle')}</h3><br/>
                        <form className="contact-form" onSubmit={submitContact}>
                            <input
                                name="name"
                                type="text"
                                placeholder={t('landingPage.contactName')}
                                value={contact.name}
                                onChange={handleContactChange}
                                required
                            />
                            <input
                                name="email"
                                type="email"
                                placeholder={t('landingPage.contactEmail')}
                                value={contact.email}
                                onChange={handleContactChange}
                                required
                            />
                            <textarea
                                name="message"
                                placeholder={t('landingPage.contactMessage')}
                                value={contact.message}
                                onChange={handleContactChange}
                                required
                            />
                            <button className="btn primary" disabled={sending}>
                                {t('landingPage.contactButton')}
                            </button>
                        </form>
                    </div>
                </section>

                <section className="newsletter-section site-section">
                    <div className="section-inner">
                        <h3>{t('landingPage.newsletterTitle', 'Bleib informiert!')}</h3>
                        <p>{t('landingPage.newsletterText', 'Updates & Tipps direkt in dein Postfach.')}</p>
                        <br/>
                        <form className="newsletter-form">
                            <input type="email" placeholder={t('landingPage.newsletterPlaceholder')} />
                            <button className="btn primary">{t('landingPage.newsletterButton')}</button>
                        </form>
                    </div>
                </section>
            </main>

            {/* FOOTER ********************************************************* */}
            <footer className="landing-footer">
                <div className="footer-content">
                    <p>© {new Date().getFullYear()} Chrono-Logisch. {t('landingPage.allRights')}</p>
                    <div className="social-icons"></div>
                    <div style={{ marginTop: "1rem" }}>
                        <Link to="/impressum" style={{ marginRight: "1rem" }}>{t('impressum')}</Link>
                        <Link to="/agb">{t('agb')}</Link>
                    </div>
                </div>
            </footer>
        </div>
    );
};

export default LandingPage;
