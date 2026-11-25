// src/pages/DemoTour.jsx
import React from 'react';
import { Link } from 'react-router-dom';
import Navbar from '../components/Navbar'; // NEU: Navbar importieren
import { useTranslation } from '../context/LanguageContext';
import '../styles/DemoTourScoped.css';

const DemoTour = () => {
    const { t } = useTranslation();

    return (
        // Die äußere Klasse steuert den Hintergrund, die innere das Layout
        <div className="demo-tour-page scoped-demo-tour">
            <Navbar /> {/* NEU: Navbar-Komponente hier einfügen */}
            <main className="demo-tour-content">
                <div className="demo-tour-card">
                    <h2>{t('demoTour.title', 'Chrono Demo')}</h2>
                    <p>{t('demoTour.intro', 'Willkommen! Diese Kurzführung zeigt dir die wichtigsten Bereiche.')}</p>
                    <ol>
                        <li>{t('demoTour.stepDashboard', 'Im Dashboard findest du bereits angelegte Projekte und Zeiteinträge.')}</li>
                        <li>{t('demoTour.stepMenu', 'Wechsle über das Menü zu Kunden oder Projekten, um weitere Daten zu sehen.')}</li>
                        <li>{t('demoTour.stepReset', 'Teste gern alle Funktionen - Änderungen werden beim nächsten Demo-Login zurückgesetzt.')}</li>
                    </ol>
                    <div className="demo-tour-actions">
                        <Link to="/dashboard" className="demo-tour-btn primary">{t('demoTour.dashboardCta', 'Zum Dashboard')}</Link>
                        <Link to="/admin/dashboard" className="demo-tour-btn">{t('demoTour.adminCta', 'Zum Admin-Dashboard')}</Link>
                    </div>
                </div>
            </main>
        </div>
    );
};

export default DemoTour;
