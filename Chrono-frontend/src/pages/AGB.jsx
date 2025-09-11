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
                    <div dangerouslySetInnerHTML={{ __html: t('agbPage.content') }} />
                </div>
            </div>
        </>
    );
};

export default AGB;
