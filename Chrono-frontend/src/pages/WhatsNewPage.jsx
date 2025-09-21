import React, { useState, useEffect } from 'react';
import Navbar from '../components/Navbar';
import api from '../utils/api';
import ReactMarkdown from 'react-markdown';
import { useTranslation } from '../context/LanguageContext';
import '../styles/WhatsNewPageScoped.css';

const WhatsNewPage = () => {
    const { t } = useTranslation();
    const [changelogs, setChangelogs] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        const fetchAllChangelogs = async () => {
            try {
                // KORREKTUR: Die korrekte API-Adresse aus Ihrer Originaldatei wird wieder verwendet.
                const response = await api.get('/api/changelog');

                // Sortiert die Einträge, sodass der neueste immer oben ist
                const sortedLogs = response.data.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
                setChangelogs(sortedLogs);
            } catch (err) {
                setError('Fehler beim Laden der Update-Historie.');
                console.error("Fehler beim Laden der Changelogs:", err);
            } finally {
                setLoading(false);
            }
        };
        fetchAllChangelogs();
    }, []);

    const formatDate = (dateString) => {
        if (!dateString) return '';
        const options = { year: 'numeric', month: 'long', day: 'numeric' };
        return new Date(dateString).toLocaleDateString('de-DE', options);
    };

    return (
        <>
            <Navbar />
            <div className="whats-new-page scoped-whats-new">
                <div className="header">
                    <h1>{t('whatsNewPage.title', 'Alle Änderungen und Updates')}</h1>
                    <p>Hier finden Sie den vollständigen Verlauf aller neuen Funktionen und Verbesserungen.</p>
                </div>

                {loading && <div className="loader">{t('whatsNewPage.loading', 'Lade Verlauf...')}</div>}
                {error && <div className="error-message">{error}</div>}

                {!loading && !error && (
                    <div className="timeline">
                        {changelogs.map(log => (
                            <div key={log.id} className="timeline-item">
                                <div className="timeline-dot"></div>
                                <div className="timeline-content">
                                    <span className="version-badge">Version {log.version}</span>
                                    <h2 className="title">{log.title}</h2>
                                    <time className="date">{formatDate(log.createdAt)}</time>
                                    <div className="changes-list">
                                        <ReactMarkdown>{log.content || ''}</ReactMarkdown>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </>
    );
};

export default WhatsNewPage;