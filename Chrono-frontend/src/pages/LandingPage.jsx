// src/pages/LandingPage.jsx  ·  Features aktualisiert (2025-06)
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

    return (
        <div className="landing-page scoped-landing">
            <Navbar />

            {/* HERO *********************************************************** */}
            <header className="landing-hero site-section lg" id="home">
                <h1>{t("landingPage.headline", "Zeiterfassung, die Projekte vereinfacht")}</h1>

                <div className="cta-buttons">
                    <Link to="/register" className="btn primary">
                        {t("landingPage.ctaPrimary", "Kostenlos registrieren")}
                    </Link>
                </div>
            </header>

            {/* INFO *********************************************************** */}
            <section className="info-section site-section">
                <h2 className="section-title accent">{t("landingPage.whyTitle")}</h2>

                {/* Glass-Card */}
                <div className="info-box">
                    <p>{t('landingPage.infoText')}</p>

                </div>
            </section>


            {/* FEATURES ******************************************************* */}
            <section className="features-section site-section" id="features">
                <h2>{t("landingPage.featuresTitle", "Alle Funktionen im Überblick")}</h2>
                <p>
                    {t(
                        "lp.featuresSub",
                        "Keine Gimmicks – nur Features, die deinen Alltag wirklich erleichtern."
                    )}
                </p>

                <div className="features-grid">
                    <FeatureCard
                        icon="🧠"
                        title={t("landingPage.featureSmartTitle")}
                        text={t("landingPage.featureSmartText")}
                    />
                    <FeatureCard
                        icon="👆"
                        title={t("landingPage.featureDirectTitle")}
                        text={t("landingPage.featureDirectText")}
                    />
                    <FeatureCard
                        icon="🔄"
                        title={t("landingPage.featureDuplicateTitle")}
                        text={t("landingPage.featureDuplicateText")}
                    />
                    <FeatureCard
                        icon="⏰"
                        title={t("landingPage.featureAutoPunchOutTitle")}
                        text={t("landingPage.featureAutoPunchOutText")}
                    />
                    <FeatureCard
                        icon="📈"
                        title={t("landingPage.featurePercentTitle")}
                        text={t("landingPage.featurePercentText")}
                    />
                    <FeatureCard
                        icon="🛠️"
                        title={t("landingPage.featureAdminTitle")}
                        text={t("landingPage.featureAdminText")}
                    />
                    <FeatureCard
                        icon="📊"
                        title={t("landingPage.featureHistoryTitle")}
                        text={t("landingPage.featureHistoryText")}
                    />
                    <FeatureCard
                        icon="➕"
                        title={t("landingPage.featureOvertimeTitle")}
                        text={t("landingPage.featureOvertimeText")}
                    />
                </div>
            </section>

            {/* STEPS ********************************************************** */}
            <section className="steps-section site-section">
                <h2>{t("lp.stepsTitle", "In 3 Schritten startklar")}</h2>
                <div className="steps-grid">
                    <StepCard
                        n="1"
                        title={t("landingPage.step1Title")}
                        text={t("landingPage.step1Text")}
                    />
                    <StepCard
                        n="2"
                        title={t("landingPage.step2Title")}
                        text={t("landingPage.step2Text")}
                    />
                    <StepCard
                        n="3"
                        title={t("landingPage.step3Title")}
                        text={t("landingPage.step3Text")}
                    />
                </div>
            </section>

            {/* ---------------------------------------------------------------
         KOMMENTAR-INTEGRATION (auskommentiert)
         ---------------------------------------------------------------
         <section className="site-section" id="comments">
           <h2>Kommentare</h2>
           <div id="commento" />
         </section>

         //  Script z. B. in index.html oder via react-helmet:
         //  <script defer src="https://cdn.commento.io/js/commento.js"></script>
         ---------------------------------------------------------------- */}

            {/* NEWSLETTER ***************************************************** */}
            <section className="newsletter-section site-section">
                <h2>{t("landingPage.newsTitle", "Bleib informiert!")}</h2>
                <p>{t("landingPage.newsSub", "Updates & Tipps direkt in dein Postfach.")}</p>
                <form className="newsletter-form">
                    <input type="email" placeholder={t("landingPage.newsletterPlaceholder")} />
                    <button className="btn primary">{t("landingPage.newsletterButton")}</button>
                </form>
            </section>

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
