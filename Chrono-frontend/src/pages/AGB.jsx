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
            <p>{t('agbPage.intro1')}</p>
            <p>{t('agbPage.intro2')}</p>
            <p>{t('agbPage.intro3')}</p>
            <p>{t('agbPage.stand')}</p>
        </div>
        </div>
        </>
    );
};

export default AGB;
