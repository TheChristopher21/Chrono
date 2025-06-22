import React from 'react';
import ReactMarkdown from 'react-markdown';
import '../styles/Changelog.css'; // Wir erstellen diese CSS-Datei als Nächstes

const ChangelogModal = ({ changelog, onClose }) => {
    if (!changelog) return null;

    const handleBackdropClick = (e) => {
        if (e.target === e.currentTarget) {
            onClose();
        }
    };

    return (
        <div className="changelog-backdrop" onClick={handleBackdropClick}>
            <div className="changelog-modal">
                <div className="changelog-header">
                    <h2>Was ist neu in Version {changelog.version}?</h2>
                    <button onClick={onClose} className="changelog-close-btn">&times;</button>
                </div>
                <div className="changelog-content">
                    <h3>{changelog.title}</h3>
                    <p className="changelog-date">
                        Veröffentlicht am: {new Date(changelog.createdAt).toLocaleDateString()}
                    </p>
                    <div className="changelog-body">
                        <ReactMarkdown>{changelog.content}</ReactMarkdown>
                    </div>
                </div>
                <div className="changelog-footer">
                    <button onClick={onClose}>Schließen</button>
                </div>
            </div>
        </div>
    );
};

export default ChangelogModal;