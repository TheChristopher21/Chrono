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
                <h1>{t("lp.headline", "Zeiterfassung, die Projekte vereinfacht")}</h1>

                <div className="cta-buttons">
                    <Link to="/register" className="btn primary">
                        {t("lp.ctaPrimary", "Kostenlos registrieren")}
                    </Link>
                </div>
            </header>

            {/* INFO *********************************************************** */}
            <section className="info-section site-section">
                <h2 className="section-title accent">Warum Chrono-Logic?</h2>

                {/* Glass-Card */}
                <div className="info-box">
                    <p>
                        Chrono-Logic erkennt automatisch den richtigen Stempel, verhindert
                        Dubletten in Millisekunden und füllt vergessene Punch-Outs nachts von
                        selbst. Von Teilzeit-Prozent-Punch bis Überstunden-Management – die
                        Engine läuft 24/7 transaktionssicher und audit-fest.
                    </p>
                </div>
            </section>


            {/* FEATURES ******************************************************* */}
            <section className="features-section site-section" id="features">
                <h2>{t("lp.featuresTitle", "Alle Funktionen im Überblick")}</h2>
                <p>
                    {t(
                        "lp.featuresSub",
                        "Keine Gimmicks – nur Features, die deinen Alltag wirklich erleichtern."
                    )}
                </p>

                <div className="features-grid">
                    <FeatureCard
                        icon="🧠"
                        title="Smart Punch"
                        text="Erkennt automatisch Work Start, Break Start/End & Work End."
                    />
                    <FeatureCard
                        icon="👆"
                        title="Direkter Punch"
                        text="Vier feste Status gezielt wählbar – Übergangs-Logik inklusive."
                    />
                    <FeatureCard
                        icon="🔄"
                        title="Duplicate-Schutz"
                        text="Einzigartiger DB-Index + Catch-Up-Logik verhindern Doppel-Clicks."
                    />
                    <FeatureCard
                        icon="⏰"
                        title="Auto Punch Out"
                        text="Cron-Job 23:20 Uhr beendet vergessene Stempel automatisch."
                    />
                    <FeatureCard
                        icon="📈"
                        title="Prozent-Punch"
                        text="Teilzeit gibt Tages-% an – Ist/Soll-Delta sofort berechnet."
                    />
                    <FeatureCard
                        icon="🛠️"
                        title="Korrekturen & Admin"
                        text="Einträge editieren, ganzen Tag neu schreiben oder Notizen setzen."
                    />
                    <FeatureCard
                        icon="📊"
                        title="Berichte & Historie"
                        text="Tages-, Wochen-, Bereichs-Reports + vollständige Nutzer-History."
                    />
                    <FeatureCard
                        icon="➕"
                        title="Überstunden-Mgmt"
                        text="Persönliche Minuten-Balance auto-aktualisiert, Urlaub einbezogen."
                    />
                </div>
            </section>

            {/* STEPS ********************************************************** */}
            <section className="steps-section site-section">
                <h2>{t("lp.stepsTitle", "In 3 Schritten startklar")}</h2>
                <div className="steps-grid">
                    <StepCard
                        n="1"
                        title="Account anlegen"
                        text="Firmen-Profil & Teams in wenigen Minuten."
                    />
                    <StepCard
                        n="2"
                        title="NFC-Badges koppeln"
                        text="Einmalig scannen, fertig."
                    />
                    <StepCard
                        n="3"
                        title="Dashboard nutzen"
                        text="Echtzeit-Insights & Abwesenheiten verwalten."
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
                <h2>{t("lp.newsTitle", "Bleib informiert!")}</h2>
                <p>{t("lp.newsSub", "Updates & Tipps direkt in dein Postfach.")}</p>
                <form className="newsletter-form">
                    <input type="email" placeholder="Deine E-Mail" />
                    <button className="btn primary">Abonnieren</button>
                </form>
            </section>

            {/* FOOTER ********************************************************* */}
            <footer className="landing-footer">
                <div className="footer-content">
                    <p>© 2025 Chrono-Logic · Alle Rechte vorbehalten</p>
                    <div className="social-icons">
                    </div>
                    <div style={{ marginTop: "1rem" }}>
                        <Link to="/impressum" style={{ marginRight: "1rem" }}>Impressum</Link>
                        <Link to="/agb">AGB</Link>
                    </div>
                </div>
            </footer>
        </div>
    );
};

export default LandingPage;
