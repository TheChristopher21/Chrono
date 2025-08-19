import React from 'react';
import ModalOverlay from './ModalOverlay';
import ReactMarkdown from 'react-markdown';
import '../styles/Changelog.css';
import { useTranslation } from '../context/LanguageContext';

const ChangelogModal = ({ changelog, onClose }) => {
    const { t } = useTranslation();
    if (!changelog) return null;

    const handleBackdropClick = (e) => {
        if (e.target === e.currentTarget) onClose();
    };

    const titleId = 'changelog-title';

    return (
        <ModalOverlay
            visible
            onClick={handleBackdropClick}
            className="changelog-backdrop scoped-changelog"
            role="dialog"
            aria-modal="true"
            aria-labelledby={titleId}
        >
            {/* Wichtig: .scoped-changelog sitzt am Backdrop-Element; Selektoren decken beide Varianten ab */}
            <div className="changelog-modal">
                <div className="changelog-header">
                    <h2 id={titleId}>
                        {t('changelogModal.whatsNew', 'Was ist neu in')} {changelog.version}?
                    </h2>
                    <button onClick={onClose} className="changelog-close-btn" aria-label={t('changelogModal.close', 'Schließen')}>
                        &times;
                    </button>
                </div>

                <div className="changelog-content">
                    <h3>{changelog.title}</h3>
                    <p className="changelog-date">
                        {t('changelogModal.published', 'Veröffentlicht')}:{" "}
                        {new Date(changelog.createdAt).toLocaleDateString()}
                    </p>
                    <div className="changelog-body">
                        <ReactMarkdown>{changelog.content}</ReactMarkdown>
                    </div>
                </div>

                <div className="changelog-footer">
                    <button onClick={onClose}>{t('changelogModal.close', 'Schließen')}</button>
                </div>
            </div>
        </ModalOverlay>
    );
};

export default ChangelogModal;
