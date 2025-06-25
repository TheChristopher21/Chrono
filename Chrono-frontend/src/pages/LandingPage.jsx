// src/pages/LandingPage.jsx · Features aktualisiert (2025-06)
import "react";
import { Link } from "react-router-dom";
import Navbar from "../components/Navbar";
import "../styles/LandingPageScoped.css";
import { useTranslation } from "../context/LanguageContext";

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

    const features = [
        {
            icon: "🧠",
            title: t("landingPage.featureSmartTitle"),
            text: t("landingPage.featureSmartText"),
        },
        {
            icon: "👆",
            title: t("landingPage.featureDirectTitle"),
            text: t("landingPage.featureDirectText"),
        },
        {
            icon: "🔄",
            title: t("landingPage.featureDuplicateTitle"),
            text: t("landingPage.featureDuplicateText"),
        },
        {
            icon: "⏰",
            title: t("landingPage.featureAutoPunchOutTitle"),
            text: t("landingPage.featureAutoPunchOutText"),
        },
        {
            icon: "📈",
            title: t("landingPage.featurePercentTitle"),
            text: t("landingPage.featurePercentText"),
        },
        {
            icon: "🛠️",
            title: t("landingPage.featureAdminTitle"),
            text: t("landingPage.featureAdminText"),
        },
        {
            icon: "📊",
            title: t("landingPage.featureHistoryTitle"),
            text: t("landingPage.featureHistoryText"),
        },
        {
            icon: "➕",
            title: t("landingPage.featureOvertimeTitle"),
            text: t("landingPage.featureOvertimeText"),
        },
        {
            icon: "💳",
            title: t("landingPage.featureNfcTitle"),
            text: t("landingPage.featureNfcText"),
        },
        {
            icon: "📄",
            title: t("landingPage.featureReportsTitle"),
            text: t("landingPage.featureReportsText"),
        },
        {
            icon: "🌴",
            title: t("landingPage.featureVacationTitle"),
            text: t("landingPage.featureVacationText"),
        },
    ];

    const steps = [
        {
            n: "1",
            title: t("landingPage.step1Title"),
            text: t("landingPage.step1Text"),
        },
        {
            n: "2",
            title: t("landingPage.step2Title"),
            text: t("landingPage.step2Text"),
        },
        {
            n: "3",
            title: t("landingPage.step3Title"),
            text: t("landingPage.step3Text"),
        },
    ];

    return (
        <div className="landing-page scoped-landing">
            <Navbar />
            <main>

                {/* HERO *********************************************************** */}
                <header className="landing-hero site-section lg" id="home">
                    <div className="section-inner">
                        <h1>{t("landingPage.headline", "Zeiterfassung, die Projekte vereinfacht")}</h1>

                        <div className="cta-buttons">
                            <Link to="/register" className="btn primary">
                                {t("landingPage.ctaPrimary", "Kostenlos registrieren")}
                            </Link>
                        </div>
                    </div>
                </header>

                {/* INFO *********************************************************** */}
                <section className="info-section site-section">
                    <div className="section-inner">
                        <h2 className="section-title accent">{t("landingPage.whyTitle")}</h2>

                        {/* Glass-Card */}
                        <div className="info-box">
                            <p>{t('landingPage.infoText')}</p>

                        </div>
                    </div>
                </section>


                {/* FEATURES ******************************************************* */}
                <section className="features-section site-section" id="features">
                    <div className="section-inner">
                        <h2>{t("landingPage.featuresTitle", "Alle Funktionen im Überblick")}</h2>
                        <p>
                            {t(
                                "lp.featuresSub",
                                "Keine Gimmicks – nur Features, die deinen Alltag wirklich erleichtern."
                            )}
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
                        <h2>{t("lp.stepsTitle", "In 3 Schritten startklar")}</h2>
                        <div className="steps-grid">
                            {steps.map((s, idx) => (
                                <StepCard key={idx} n={s.n} title={s.title} text={s.text} />
                            ))}
                        </div>
                    </div>
                </section>


                <section className="newsletter-section site-section">
                    <div className="section-inner">
                        <h3>{t("landingPage.newsTitle", "Bleib informiert!")}</h3>
                        <p>{t("landingPage.newsSub", "Updates & Tipps direkt in dein Postfach.")}</p>
                        <br/>
                        <form className="newsletter-form">
                            <input type="email" placeholder={t("landingPage.newsletterPlaceholder")} />
                            <button className="btn primary">{t("landingPage.newsletterButton")}</button>
                        </form>
                    </div>
                </section>
            </main>

            {/* FOOTER ********************************************************* */}
            <footer className="landing-footer">
                <div className="footer-content">
                    <p>{t("landingPage.allRights")}</p>
                    <div className="social-icons">
                    </div>
                    <div style={{ marginTop: "1rem" }}>
                        <Link to="/impressum" style={{ marginRight: "1rem" }}>{t("impressum")}</Link>
                        <Link to="/agb">{t("agb")}</Link>
                    </div>
                </div>
            </footer>
        </div>
    );
};

export default LandingPage;