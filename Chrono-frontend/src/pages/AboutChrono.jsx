import React from "react";
import { Link } from "react-router-dom";
import Navbar from "../components/Navbar";
import { useTranslation } from "../context/LanguageContext";
import "../styles/LandingPageScoped.css";

const AboutChrono = () => {
    const { t } = useTranslation();
    const values = [
        t("aboutChrono.values.directSupport", "Direkte Betreuung statt anonymer Supportwege"),
        t("aboutChrono.values.fastDevelopment", "Schnelle Weiterentwicklung aus praktischen Anforderungen"),
        t("aboutChrono.values.onboarding", "Individuelle Einführung und Hilfe beim Umstieg"),
        t("aboutChrono.values.platform", "Ein System für Zeit, HR, Lohn, CRM, Finanzen, Lager und Reporting"),
    ];

    const timeline = [
        {
            step: "01",
            title: t("aboutChrono.timeline.interest.title", "Frühes Interesse"),
            text: t("aboutChrono.timeline.interest.text", "Christopher Siefert interessierte sich schon als Kind für Programmierung und dafür, wie Software echte Abläufe einfacher machen kann."),
        },
        {
            step: "02",
            title: t("aboutChrono.timeline.firstChrono.title", "Erstes Chrono"),
            text: t("aboutChrono.timeline.firstChrono.text", "Als seine Eltern eine Zeiterfassung brauchten, entstand die erste kleine Version von Chrono aus einem konkreten Bedarf heraus."),
        },
        {
            step: "03",
            title: "Chrono-Logisch",
            text: t("aboutChrono.timeline.platform.text", "Aus diesem ersten Werkzeug wurde mit viel Herz, Feedback und kontinuierlicher Weiterentwicklung die Plattform Chrono-Logisch."),
        },
    ];

    return (
        <div className="landing-page scoped-landing lp-about-page">
            <Navbar />
            <main>
                <section className="lp-about-hero">
                    <div className="lp-container lp-about-hero-layout">
                        <div className="lp-about-hero-copy">
                            <span className="lp-kicker">{t("aboutChrono.hero.kicker", "Über Chrono-Logisch")}</span>
                            <h1>{t("aboutChrono.hero.title", "Abläufe sollen klarer werden.")}</h1>
                            <p>
                                {t("aboutChrono.hero.text", "Viele Firmen arbeiten täglich mit Excel-Listen, Papierformularen, einzelnen Tools und manuellen Abstimmungen. Chrono-Logisch wurde entwickelt, um genau diese Abläufe zentraler, übersichtlicher und nachvollziehbarer zu machen.")}
                            </p>
                            <div className="lp-about-actions">
                                <Link className="lp-btn lp-primary" to="/#kontakt">{t("aboutChrono.actions.contact", "Kontakt aufnehmen")}</Link>
                                <a className="lp-btn lp-secondary" href="mailto:siefertchristopher@chrono-logisch.ch">{t("aboutChrono.actions.email", "E-Mail schreiben")}</a>
                            </div>
                        </div>
                        <aside className="lp-about-founder-panel">
                            <img src="/img/Ich.png" alt="Christopher Siefert" loading="lazy" />
                            <div>
                                <strong>Christopher Siefert</strong>
                                <span>{t("aboutChrono.founder.role", "Gründer & Entwickler von Chrono")}</span>
                                <p>Mogelsberg, St. Gallen</p>
                            </div>
                        </aside>
                    </div>
                </section>

                <section className="lp-section lp-about-story-section">
                    <div className="lp-container lp-about-two-column">
                        <div>
                            <span className="lp-kicker">{t("aboutChrono.idea.kicker", "Die Idee")}</span>
                            <h2>{t("aboutChrono.idea.title", "Mehr als Zeiterfassung.")}</h2>
                            <p>
                                {t("aboutChrono.idea.text1", "Chrono verbindet Arbeitszeiten, Urlaub, Korrekturen, HR, Lohnprozesse, Rechnungen, CRM, Lager, Finanzen und Reporting in einem gemeinsamen System.")}
                            </p>
                            <p>
                                {t("aboutChrono.idea.text2", "Das Ziel ist nicht, Unternehmen mit noch einem weiteren Tool zu belasten, sondern bestehende Abläufe zu vereinfachen und in eine klare Struktur zu bringen.")}
                            </p>
                        </div>
                        <div className="lp-about-promise-card">
                            <span>{t("aboutChrono.promise.kicker", "Leitsatz")}</span>
                            <strong>{t("aboutChrono.promise.title", "Chrono bringt zusammen, was Firmen täglich brauchen.")}</strong>
                            <p>{t("aboutChrono.promise.text", "Und wenn im Alltag etwas Entscheidendes fehlt, wird es gezielt weiterentwickelt.")}</p>
                        </div>
                    </div>
                </section>

                <section className="lp-section lp-about-timeline-section">
                    <div className="lp-container lp-section-heading">
                        <span className="lp-kicker">{t("aboutChrono.timeline.kicker", "Entstehung")}</span>
                        <h2>{t("aboutChrono.timeline.title", "Aus einem echten Bedarf entstanden.")}</h2>
                        <p>
                            {t("aboutChrono.timeline.lead", "Chrono-Logisch ist nicht aus einer abstrakten Produktidee entstanden, sondern aus einem konkreten Problem im Alltag eines Unternehmens.")}
                        </p>
                    </div>
                    <div className="lp-container lp-about-timeline">
                        {timeline.map((item) => (
                            <article className="lp-about-timeline-card" key={item.step}>
                                <span>{item.step}</span>
                                <h3>{item.title}</h3>
                                <p>{item.text}</p>
                            </article>
                        ))}
                    </div>
                </section>

                <section className="lp-section lp-about-advantage-section">
                    <div className="lp-container lp-about-advantage-layout">
                        <div className="lp-about-advantage-copy">
                            <span className="lp-kicker">{t("aboutChrono.advantage.kicker", "Ihr Vorteil")}</span>
                            <h2>{t("aboutChrono.advantage.title", "Direkter Ansprechpartner für Einführung und Weiterentwicklung.")}</h2>
                            <p>
                                {t("aboutChrono.advantage.text1", "Kunden sprechen bei Chrono-Logisch nicht mit einem anonymen Konzern, sondern mit einem Ansprechpartner, der das System kennt, weiterentwickelt und versteht, wie die einzelnen Module zusammenspielen.")}
                            </p>
                            <p>
                                {t("aboutChrono.advantage.text2", "Feedback aus der Praxis kann dadurch gezielt aufgenommen werden. Auch beim Wechsel aus bestehenden Programmen unterstützt Chrono-Logisch bei Einführung, Einrichtung und Umstellung.")}
                            </p>
                        </div>
                        <ul className="lp-about-value-list" role="list">
                            {values.map((value) => (
                                <li key={value}>{value}</li>
                            ))}
                        </ul>
                    </div>
                </section>

                <section className="lp-contact-section lp-about-contact-section" aria-labelledby="about-contact-title">
                    <div className="lp-container lp-about-contact-layout">
                        <div>
                            <span className="lp-kicker">{t("aboutChrono.contact.kicker", "Kontakt")}</span>
                            <h2 id="about-contact-title">{t("aboutChrono.contact.title", "Demo, Einführung oder Wechsel besprechen.")}</h2>
                            <p>
                                {t("aboutChrono.contact.text", "Sie können Chrono-Logisch per E-Mail, telefonisch oder über das Kontaktformular auf der Chrono-Seite erreichen.")}
                            </p>
                        </div>
                        <div className="lp-about-contact-actions">
                            <a className="lp-btn lp-primary" href="mailto:siefertchristopher@chrono-logisch.ch">{t("aboutChrono.actions.email", "E-Mail schreiben")}</a>
                            <a className="lp-btn lp-secondary" href="tel:+41765467960">{t("aboutChrono.actions.phone", "Telefon anrufen")}</a>
                            <Link className="lp-btn lp-secondary" to="/#kontakt">{t("aboutChrono.actions.contactForm", "Zum Kontaktformular")}</Link>
                        </div>
                    </div>
                </section>
            </main>

            <footer className="lp-footer">
                <div className="lp-container lp-footer-inner">
                    <img className="lp-footer-logo" src="/img/komplettesLogo.png" alt="Chrono" loading="lazy" />
                    <nav aria-label="Footer Navigation">
                        <Link to="/">{t("aboutChrono.footer.home", "Startseite")}</Link>
                        <Link to="/impressum">{t("aboutChrono.footer.imprint", "Impressum")}</Link>
                        <Link to="/datenschutz">{t("aboutChrono.footer.privacy", "Datenschutz")}</Link>
                        <Link to="/agb">{t("aboutChrono.footer.terms", "AGB")}</Link>
                    </nav>
                    <span>© {new Date().getFullYear()}</span>
                </div>
            </footer>
        </div>
    );
};

export default AboutChrono;
