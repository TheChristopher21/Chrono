import React from "react";
import { Link } from "react-router-dom";
import Navbar from "../components/Navbar";
import "../styles/LandingPageScoped.css";

const AboutChrono = () => {
    const values = [
        "Direkte Betreuung statt anonymer Supportwege",
        "Schnelle Weiterentwicklung aus praktischen Anforderungen",
        "Individuelle Einführung und Hilfe beim Umstieg",
        "Ein System für Zeit, HR, Lohn, CRM, Finanzen, Lager und Reporting",
    ];

    const timeline = [
        {
            step: "01",
            title: "Frühes Interesse",
            text: "Christopher Siefert interessierte sich schon als Kind für Programmierung und dafür, wie Software echte Abläufe einfacher machen kann.",
        },
        {
            step: "02",
            title: "Erstes Chrono",
            text: "Als seine Eltern eine Zeiterfassung brauchten, entstand die erste kleine Version von Chrono aus einem konkreten Bedarf heraus.",
        },
        {
            step: "03",
            title: "Chrono-Logisch",
            text: "Aus diesem ersten Werkzeug wurde mit viel Herz, Feedback und kontinuierlicher Weiterentwicklung die Plattform Chrono-Logisch.",
        },
    ];

    return (
        <div className="landing-page scoped-landing lp-about-page">
            <Navbar />
            <main>
                <section className="lp-about-hero">
                    <div className="lp-container lp-about-hero-layout">
                        <div className="lp-about-hero-copy">
                            <span className="lp-kicker">Über Chrono-Logisch</span>
                            <h1>Abläufe sollen klarer werden.</h1>
                            <p>
                                Viele Firmen arbeiten täglich mit Excel-Listen, Papierformularen, einzelnen Tools und manuellen Abstimmungen. Chrono-Logisch wurde entwickelt, um genau diese Abläufe zentraler, übersichtlicher und nachvollziehbarer zu machen.
                            </p>
                            <div className="lp-about-actions">
                                <Link className="lp-btn lp-primary" to="/#kontakt">Kontakt aufnehmen</Link>
                                <a className="lp-btn lp-secondary" href="mailto:siefertchristopher@chrono-logisch.ch">E-Mail schreiben</a>
                            </div>
                        </div>
                        <aside className="lp-about-founder-panel">
                            <img src="/img/Ich.png" alt="Christopher Siefert" loading="lazy" />
                            <div>
                                <strong>Christopher Siefert</strong>
                                <span>Gründer & Entwickler von Chrono</span>
                                <p>Mogelsberg, St. Gallen</p>
                            </div>
                        </aside>
                    </div>
                </section>

                <section className="lp-section lp-about-story-section">
                    <div className="lp-container lp-about-two-column">
                        <div>
                            <span className="lp-kicker">Die Idee</span>
                            <h2>Mehr als Zeiterfassung.</h2>
                            <p>
                                Chrono verbindet Arbeitszeiten, Urlaub, Korrekturen, HR, Lohnprozesse, Rechnungen, CRM, Lager, Finanzen und Reporting in einem gemeinsamen System.
                            </p>
                            <p>
                                Das Ziel ist nicht, Unternehmen mit noch einem weiteren Tool zu belasten, sondern bestehende Abläufe zu vereinfachen und in eine klare Struktur zu bringen.
                            </p>
                        </div>
                        <div className="lp-about-promise-card">
                            <span>Leitsatz</span>
                            <strong>Chrono bringt zusammen, was Firmen täglich brauchen.</strong>
                            <p>Und wenn im Alltag etwas Entscheidendes fehlt, wird es gezielt weiterentwickelt.</p>
                        </div>
                    </div>
                </section>

                <section className="lp-section lp-about-timeline-section">
                    <div className="lp-container lp-section-heading">
                        <span className="lp-kicker">Entstehung</span>
                        <h2>Aus einem echten Bedarf entstanden.</h2>
                        <p>
                            Chrono-Logisch ist nicht aus einer abstrakten Produktidee entstanden, sondern aus einem konkreten Problem im Alltag eines Unternehmens.
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
                            <span className="lp-kicker">Ihr Vorteil</span>
                            <h2>Direkter Ansprechpartner für Einführung und Weiterentwicklung.</h2>
                            <p>
                                Kunden sprechen bei Chrono-Logisch nicht mit einem anonymen Konzern, sondern mit einem Ansprechpartner, der das System kennt, weiterentwickelt und versteht, wie die einzelnen Module zusammenspielen.
                            </p>
                            <p>
                                Feedback aus der Praxis kann dadurch gezielt aufgenommen werden. Auch beim Wechsel aus bestehenden Programmen unterstützt Chrono-Logisch bei Einführung, Einrichtung und Umstellung.
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
                            <span className="lp-kicker">Kontakt</span>
                            <h2 id="about-contact-title">Demo, Einführung oder Wechsel besprechen.</h2>
                            <p>
                                Sie können Chrono-Logisch per E-Mail, telefonisch oder über das Kontaktformular auf der Chrono-Seite erreichen.
                            </p>
                        </div>
                        <div className="lp-about-contact-actions">
                            <a className="lp-btn lp-primary" href="mailto:siefertchristopher@chrono-logisch.ch">E-Mail schreiben</a>
                            <a className="lp-btn lp-secondary" href="tel:+41765467960">Telefon anrufen</a>
                            <Link className="lp-btn lp-secondary" to="/#kontakt">Zum Kontaktformular</Link>
                        </div>
                    </div>
                </section>
            </main>

            <footer className="lp-footer">
                <div className="lp-container lp-footer-inner">
                    <strong>Chrono</strong>
                    <nav aria-label="Footer Navigation">
                        <Link to="/">Startseite</Link>
                        <Link to="/impressum">Impressum</Link>
                        <Link to="/datenschutz">Datenschutz</Link>
                        <Link to="/agb">AGB</Link>
                    </nav>
                    <span>© {new Date().getFullYear()}</span>
                </div>
            </footer>
        </div>
    );
};

export default AboutChrono;
