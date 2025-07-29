// src/pages/AGB.jsx
import React from 'react';
import Navbar from '../components/Navbar';
import '../styles/LegalPages.css';
import { useTranslation } from '../context/LanguageContext';

const AGB = () => {
    const { t } = useTranslation();
    return (
        <>
            <Navbar />
            <div className="legal-wrapper">
                <div className="legal-page">
                    <h1>{t('agbPage.title')}</h1>
                    <p><em>{t('agbPage.stand')}</em></p>

                    <h2>{t('agbPage.scope.title')}</h2>
                    <p>{t('agbPage.scope.content')}</p>

                    <h2>{t('agbPage.services.title')}</h2>
                    <p>{t('agbPage.services.content1')}</p>
                    <p>{t('agbPage.services.content2')}</p>

                    <h2>{t('agbPage.contract.title')}</h2>
                    <p>{t('agbPage.contract.content1')}</p>
                    <p>{t('agbPage.contract.content2')}</p>

                    <h2>{t('agbPage.payment.title')}</h2>
                    <p>{t('agbPage.payment.content1')}</p>
                    <p>{t('agbPage.payment.content2')}</p>

                    <h2>{t('agbPage.obligations.title')}</h2>
                    <p>{t('agbPage.obligations.content')}</p>

                    <h2>{t('agbPage.dataProtection.title')}</h2>
                    <p>{t('agbPage.dataProtection.content1')}</p>
                    <p>{t('agbPage.dataProtection.content2')}</p>

                    <h2>{t('agbPage.dataHandover.title')}</h2>
                    <p>{t('agbPage.dataHandover.content')}</p>

                    <h2>{t('agbPage.liability.title')}</h2>
                    <p>{t('agbPage.liability.content')}</p>

                    <h2>{t('agbPage.changes.title')}</h2>
                    <p>{t('agbPage.changes.content')}</p>

                    <h2>{t('agbPage.finalProvisions.title')}</h2>
                    <p>{t('agbPage.finalProvisions.content')}</p>

                    <br/>
                    <p><em>{t('agbPage.stand')}</em></p>
                </div>
            </div>
        </>
    );
};

export default AGB;