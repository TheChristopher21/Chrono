// src/pages/Impressum.jsx
import React from 'react';
import Navbar from '../components/Navbar';
import '../styles/LegalPages.css';
import { useTranslation } from '../context/LanguageContext';

const Impressum = () => {
    const { t } = useTranslation();
    const sections = [
        {
            key: 'provider',
            eyebrow: t('impressumPage.providerEyebrow'),
            title: t('impressumPage.providerTitle'),
            html: t('impressumPage.address'),
        },
        {
            key: 'contact',
            eyebrow: t('impressumPage.contactEyebrow'),
            title: t('impressumPage.contactTitle'),
            html: t('impressumPage.contact'),
        },
        {
            key: 'responsible',
            eyebrow: t('impressumPage.responsibleEyebrow'),
            title: t('impressumPage.responsibleTitle'),
            html: t('impressumPage.responsible'),
        },
        {
            key: 'liability',
            eyebrow: t('impressumPage.liabilityEyebrow'),
            title: t('impressumPage.liabilityTitle'),
            html: t('impressumPage.liability'),
        },
        {
            key: 'copyright',
            eyebrow: t('impressumPage.copyrightEyebrow'),
            title: t('impressumPage.copyrightTitle'),
            html: t('impressumPage.copyright'),
        },
    ];

    return (
        <>
            <Navbar />
            <div className="legal-wrapper legal-wrapper--impressum">
                <section className="legal-hero legal-hero--impressum">
                    <p className="legal-kicker">{t('impressumPage.kicker')}</p>
                    <h1>{t('impressumPage.title')}</h1>
                    <p className="legal-lead">{t('impressumPage.lead')}</p>
                    <div className="legal-meta">
                        <span className="legal-pill">{t('impressumPage.updatedTag')}</span>
                        <span className="legal-pill">{t('impressumPage.contactTag')}</span>
                        <span className="legal-pill outline">{t('impressumPage.locationTag')}</span>
                    </div>
                </section>

                <div className="legal-grid legal-grid--impressum">
                    <article className="legal-page legal-page--impressum">
                        <div className="legal-intro-banner">
                            <p className="legal-info-eyebrow">{t('impressumPage.introLabel')}</p>
                            <p className="legal-intro-copy">{t('impressumPage.introText')}</p>
                        </div>

                        <div className="legal-info-grid">
                            {sections.map((section) => (
                                <section className="legal-info-card" key={section.key}>
                                    <p className="legal-info-eyebrow">{section.eyebrow}</p>
                                    <h2>{section.title}</h2>
                                    <div
                                        className="legal-info-content"
                                        dangerouslySetInnerHTML={{ __html: section.html }}
                                    />
                                </section>
                            ))}
                        </div>
                    </article>

                    <aside className="legal-aside legal-aside--impressum">
                        <div className="legal-aside-card subtle legal-aside-card--highlight">
                            <p className="legal-kicker legal-kicker--small">
                                {t('impressumPage.quickContactKicker')}
                            </p>
                            <h2>{t('impressumPage.quickContactTitle')}</h2>
                            <p>{t('impressumPage.quickContactText')}</p>

                            <div className="legal-contact-actions">
                                <a className="legal-contact-link" href="mailto:siefertchristopher@chrono-logisch.ch">
                                    {t('impressumPage.emailCta')}
                                </a>
                                <a className="legal-contact-link" href="tel:+41765467960">
                                    {t('impressumPage.phoneCta')}
                                </a>
                            </div>
                        </div>

                        <div className="legal-aside-card">
                            <h3>{t('impressumPage.responseTitle')}</h3>
                            <p>{t('impressumPage.responseText')}</p>
                        </div>

                        <div className="legal-aside-card">
                            <h3>{t('impressumPage.noteTitle')}</h3>
                            <p>{t('impressumPage.noteText')}</p>
                            <p
                                className="legal-updated"
                                dangerouslySetInnerHTML={{ __html: t('impressumPage.stand') }}
                            />
                        </div>
                    </aside>
                </div>
            </div>
        </>
    );
};

export default Impressum;
