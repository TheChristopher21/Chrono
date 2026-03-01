// src/pages/Impressum.jsx
import React from 'react';
import Navbar from '../components/Navbar';
import '../styles/LegalPages.css';
import { useTranslation } from '../context/LanguageContext';

const Impressum = () => {
    const { t } = useTranslation();
    return (
        <>
        <Navbar />
        <div className="legal-wrapper">
        <div className="legal-page">
            <h1>{t('impressumPage.title')}</h1>

            <p dangerouslySetInnerHTML={{ __html: t('impressumPage.address') }} />
            <p dangerouslySetInnerHTML={{ __html: t('impressumPage.contact') }} />
            <p dangerouslySetInnerHTML={{ __html: t('impressumPage.responsible') }} />
            <p dangerouslySetInnerHTML={{ __html: t('impressumPage.liability') }} />
            <p dangerouslySetInnerHTML={{ __html: t('impressumPage.copyright') }} />
            <p dangerouslySetInnerHTML={{ __html: t('impressumPage.stand') }} />
        </div>
        </div>
        </>
    );
};

export default Impressum;
