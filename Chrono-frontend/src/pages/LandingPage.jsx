// src/pages/LandingPage.jsx
import React from 'react';
import { Link } from 'react-router-dom';
import Navbar from '../components/Navbar';
import '../styles/LandingPageScoped.css';
import { useTranslation } from '../context/LanguageContext';

const LandingPage = () => {
    const { t } = useTranslation();

    return (
        <div className="landing-page scoped-landing">
            {/* Navigation */}
            <Navbar />

            {/* -------------------- HERO-SECTION -------------------- */}
            <header className="landing-hero">
                <div className="hero-text">
                    <h1>{t("landingPage.headline", "Chrono-Logic")}</h1>
                    <p>{t("landingPage.subHeadline", "Zeiterfassung und NFC-Stempeln – ganz einfach.")}</p>
                    <div className="cta-buttons">
                    </div>
                </div>
                <div className="hero-media">
                    <img src="/images/hero-demo.png" alt="Zeiterfassungs-Demo" />
                </div>
            </header>

            {/* -------------------- FEATURES -------------------- */}
            <section className="features-section flow-section">
                <h2>{t("landingPage.whyTitle", "Warum Chrono-Logic?")}</h2>
                <div className="feature-list">
                    <div className="feature-item">
                        <img src="/images/nfc-icon.png" alt="NFC Icon" />
                        <h3>{t("landingPage.featureNfcTitle", "NFC-Stempeln")}</h3>
                        <p>{t("landingPage.featureNfcText", "Einfaches Ein- und Ausstempeln per NFC-Karte.")}</p>
                    </div>
                    <div className="feature-item">
                        <img src="/images/report-icon.png" alt="Report Icon" />
                        <h3>{t("landingPage.featureReportsTitle", "Automatische Berichte")}</h3>
                        <p>{t("landingPage.featureReportsText", "Übersichtliche Auswertungen und PDF-Exports.")}</p>
                    </div>
                    <div className="feature-item">
                        <img src="/images/calendar-icon.png" alt="Calendar Icon" />
                        <h3>{t("landingPage.featureVacationTitle", "Urlaubsverwaltung")}</h3>
                        <p>{t("landingPage.featureVacationText", "Urlaubstage im Blick, inkl. Genehmigungsprozess.")}</p>
                    </div>
                </div>
            </section>

            {/* -------------------- STEPS: So fängst du an -------------------- */}
            <section className="steps-section flow-section">
                <h2>{t("landingPage.stepsTitle", "So startest du in 3 Schritten")}</h2>
                <div className="steps-grid">
                    <div className="step-item">
                        <div className="step-number">1</div>
                        <h4>{t("landingPage.step1Title", "Firma Registrieren")}</h4>
                        <p>
                            {t("landingPage.step1Text", "Registriere dich in wenigen Sekunden und bekomme deinen Zugang.")}
                        </p>
                    </div>
                    <div className="step-item">
                        <div className="step-number">2</div>
                        <h4>{t("landingPage.step2Title", "Karte programmieren")}</h4>
                        <p>
                            {t("landingPage.step2Text", "Halte deine NFC-Karte an den Reader, um sie deinem Account zuzuweisen.")}
                        </p>
                    </div>
                    <div className="step-item">
                        <div className="step-number">3</div>
                        <h4>{t("landingPage.step3Title", "Losstempeln")}</h4>
                        <p>
                            {t("landingPage.step3Text", "Stemple ein und aus, verwalte Urlaub, erstelle Berichte – fertig!")}
                        </p>
                    </div>
                </div>

                {/* Hier die neue Zeile (Firma-Registrierung) */}
                <div className="firma-register-callout">
                    <h3>{t("landingPage.registerCompanyHeadline", "Registriere jetzt deine Firma")}</h3>
                    <Link to="/register" className="btn primary">
                        {t("landingPage.registerCompanyButton", "Firma registrieren")}
                    </Link>
                </div>
            </section>

            {/* -------------------- VIDEO / HOW-IT-WORKS -------------------- */}
            <section className="video-section flow-section">
                <h2>{t("landingPage.howItWorks", "So funktioniert's")}</h2>
                <div className="video-container">
                    <iframe
                        width="560"
                        height="315"
                        src="https://www.youtube.com/embed/DEIN_VIDEO_ID"
                        title="YouTube video player"
                        frameBorder="0"
                        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                        allowFullScreen
                    />
                </div>
            </section>

            {/* -------------------- FOOTER -------------------- */}
            <footer className="landing-footer">
                <div className="footer-content">
                    <p>© 2025 Chrono-Logic. {t("landingPage.allRights", "Alle Rechte vorbehalten.")}</p>

                    <div className="social-icons">
                        <a href="https://github.com/" target="_blank" rel="noopener noreferrer">
                            <img src="/images/github-icon.png" alt="GitHub" />
                        </a>
                        <a href="https://twitter.com/" target="_blank" rel="noopener noreferrer">
                            <img src="/images/twitter-icon.png" alt="Twitter" />
                        </a>
                        {/* Ggf. mehr Icons: LinkedIn, etc. */}
                    </div>
                </div>
            </footer>
        </div>
    );
};

export default LandingPage;
