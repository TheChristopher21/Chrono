// src/pages/Datenschutz.jsx
import React from 'react';
import Navbar from '../components/Navbar';
import '../styles/LegalPages.css';
import { useTranslation } from '../context/LanguageContext';

const Datenschutz = () => {
    const { t } = useTranslation();
    return (
        <>
            <Navbar />
            <div className="legal-wrapper">
                <div className="legal-page">
                    <h1>{t('privacyPage.title')}</h1>
                    <p>{t('privacyPage.content')}</p>
                </div>
            </div>
        </>
    );
};

export default Datenschutz;
