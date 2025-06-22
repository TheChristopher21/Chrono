import React, { useState, useEffect } from 'react';
import Navbar from '../components/Navbar';
import api from '../utils/api';
import ReactMarkdown from 'react-markdown';
import '../styles/Changelog.css'; // Wir verwenden dieselbe CSS-Datei

const WhatsNewPage = () => {
    const [changelogs, setChangelogs] = useState([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const fetchAllChangelogs = async () => {
            try {
                const response = await api.get('/api/changelog');
                setChangelogs(response.data);
            } catch (error) {
                console.error("Fehler beim Laden der Changelogs:", error);
            } finally {
                setLoading(false);
            }
        };
        fetchAllChangelogs();
    }, []);

    return (
        <div>
            <Navbar />
            <div className="page-container">
                <h1>Alle Änderungen und Updates</h1>
                {loading ? (
                    <p>Lade Verlauf...</p>
                ) : (
                    <div className="changelog-history">
                        {changelogs.map(log => (
                            <div key={log.id} className="changelog-history-item">
                                <div className="changelog-header">
                                    <h2>Version {log.version} - {log.title}</h2>
                                    <span className="changelog-date">
                                        {new Date(log.createdAt).toLocaleDateString()}
                                    </span>
                                </div>
                                <div className="changelog-body">
                                    <ReactMarkdown>{log.content}</ReactMarkdown>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
};

export default WhatsNewPage;