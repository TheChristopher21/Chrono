import React from 'react';
import ModalOverlay from './ModalOverlay';
import ReactMarkdown from 'react-markdown';
import { useTranslation } from '../context/LanguageContext';

const ChangelogModal = ({ changelog, onClose }) => {
    const { t } = useTranslation();
    if (!changelog) return null;

    const handleBackdropClick = (e) => {
        if (e.target === e.currentTarget) {
            onClose();
        }
    };

    return (
        <ModalOverlay visible className="changelog-backdrop" onClick={handleBackdropClick}>
            <div className="changelog-modal">
                <div className="changelog-header">
                    <h2>{t('changelogModal.whatsNew')} {changelog.version}?</h2>
                    <button onClick={onClose} className="changelog-close-btn">&times;</button>
                </div>
                <div className="changelog-content">
                    <h3>{changelog.title}</h3>
                    <p className="changelog-date">
                        {t('changelogModal.published')}: {new Date(changelog.createdAt).toLocaleDateString()}
                    </p>
                    <div className="changelog-body">
                        <ReactMarkdown>{changelog.content}</ReactMarkdown>
                    </div>
                </div>
                <div className="changelog-footer">
                    <button onClick={onClose}>{t('changelogModal.close')}</button>
                </div>
            </div>
        </ModalOverlay>
    );
};

export default ChangelogModal;