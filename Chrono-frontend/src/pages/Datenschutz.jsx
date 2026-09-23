import React, { useContext, useEffect } from 'react';
import Navbar from '../components/Navbar';
import { LanguageContext, useTranslation } from '../context/LanguageContext';
import '../styles/LegalPages.css';

const Datenschutz = () => {
    const { language } = useContext(LanguageContext);
    const { t } = useTranslation();

    useEffect(() => {
        // Script einbinden, sobald die Seite geladen wird
        const script = document.createElement("script");
        script.src = "https://app.privacybee.io/widget.js";
        script.defer = true;
        document.body.appendChild(script);

        return () => {
            // Script beim Verlassen der Seite wieder entfernen
            document.body.removeChild(script);
        };
    }, []);

    return (
        <>
            <Navbar />
            <div className="legal-wrapper">
                <div className="legal-page">
                    <h1>{t('privacyPage.title', 'Datenschutzerklärung')}</h1>

                    <privacybee-widget
                        key={language}
                        website-id="cmg29z1in00v4boymy5yx1ttp"
                        type="dsg"
                        lang={language === 'en' ? 'en' : 'de'}
                    ></privacybee-widget>
                </div>
            </div>
        </>
    );
};

export default Datenschutz;
