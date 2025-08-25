import React from 'react';
import { Link } from 'react-router-dom';

const DemoTour = () => (
    <div style={{ padding: '2rem' }}>
        <h2>Chrono Demo</h2>
        <p>Willkommen! Diese Kurzführung zeigt dir die wichtigsten Bereiche.</p>
        <ol>
            <li>Im Dashboard findest du bereits angelegte Projekte und Zeiteinträge.</li>
            <li>Wechsle über das Menü zu Kunden oder Projekten, um weitere Daten zu sehen.</li>
            <li>Teste gern alle Funktionen – Änderungen werden beim nächsten Demo-Login zurückgesetzt.</li>
            <li>Über den Admin-Bereich kannst du Verwaltungsmöglichkeiten ausprobieren.</li>
        </ol>
        <Link to="/dashboard">Zum Dashboard</Link>
        <span style={{ margin: '0 1rem' }}>|</span>
        <Link to="/admin/dashboard">Zum Admin-Dashboard</Link>
    </div>
);

export default DemoTour;
