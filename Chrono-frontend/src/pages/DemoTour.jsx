// src/pages/DemoTour.jsx
import React from 'react';
import { Link } from 'react-router-dom';
import Navbar from '../components/Navbar'; // NEU: Navbar importieren
import '../styles/DemoTourScoped.css';

const DemoTour = () => (
    // Die äußere Klasse steuert den Hintergrund, die innere das Layout
    <div className="demo-tour-page scoped-demo-tour">
        <Navbar /> {/* NEU: Navbar-Komponente hier einfügen */}
        <main className="demo-tour-content">
            <div className="demo-tour-card">
                <h2>Chrono Demo</h2>
                <p>Willkommen! Diese Kurzführung zeigt dir die wichtigsten Bereiche.</p>
                <ol>
                    <li>Im Dashboard findest du bereits angelegte Projekte und Zeiteinträge.</li>
                    <li>Wechsle über das Menü zu Kunden oder Projekten, um weitere Daten zu sehen.</li>
                    <li>Teste gern alle Funktionen – Änderungen werden beim nächsten Demo-Login zurückgesetzt.</li>
                </ol>
                <div className="demo-tour-actions">
                    <Link to="/dashboard" className="demo-tour-btn primary">Zum Dashboard</Link>
                    <Link to="/admin/dashboard" className="demo-tour-btn">Zum Admin-Dashboard</Link>
                </div>
            </div>
        </main>
    </div>
);

export default DemoTour;