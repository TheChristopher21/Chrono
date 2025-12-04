// src/pages/AGB.jsx
import React from 'react';
import Navbar from '../components/Navbar';
import '../styles/LegalPages.css';
import { useTranslation } from '../context/LanguageContext';

const AGB = () => {
    const { t } = useTranslation();
    const highlights = [
        t('agbPage.highlight1'),
        t('agbPage.highlight2'),
        t('agbPage.highlight3'),
        t('agbPage.highlight4'),
    ];

    return (
        <>
            <Navbar />
            <div className="legal-wrapper">
                <section className="legal-hero">
                    <p className="legal-kicker">{t('agbPage.kicker')}</p>
                    <h1>{t('agbPage.title')}</h1>
                    <p className="legal-lead">{t('agbPage.lead')}</p>
                    <div className="legal-meta">
                        <span className="legal-pill">{t('agbPage.updated')}</span>
                        <span className="legal-pill outline">{t('agbPage.summaryHint')}</span>
                    </div>
                </section>

                <div className="legal-grid">
                    <article className="legal-page">
                        <div className="legal-content" dangerouslySetInnerHTML={{ __html: t('agbPage.content') }} />
                    </article>
                    <aside className="legal-aside">
                        <div className="legal-aside-card">
                            <h2>{t('agbPage.highlightTitle')}</h2>
                            <ul>
                                {highlights.map((item, index) => (
                                    <li key={index}>{item}</li>
                                ))}
                            </ul>
                        </div>
                        <div className="legal-aside-card subtle">
                            <h3>{t('agbPage.contactTitle')}</h3>
                            <p>{t('agbPage.contactText')}</p>
                        </div>
                    </aside>
                </div>
            </div>
        </>
    );
};

export default AGB;
