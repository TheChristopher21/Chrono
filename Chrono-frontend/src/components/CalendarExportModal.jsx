import React from 'react';
import PropTypes from 'prop-types';
import ModalOverlay from './ModalOverlay';
import '../styles/CalendarExportModal.css';
import { useTranslation } from '../context/LanguageContext';

const CalendarExportModal = ({ icsUrl, onClose, onCopyLink }) => {
    const { t } = useTranslation();

    const openGoogle = () => {
        const url = 'https://calendar.google.com/calendar/r?cid=' + encodeURIComponent(icsUrl);
        window.open(url, '_blank');
    };

    const openOutlookApple = () => {
        const url = icsUrl.replace(/^https?:/, 'webcal:');
        window.open(url, '_blank');
    };

    const downloadIcs = async () => {
        try {
            const res = await fetch(icsUrl);
            const blob = await res.blob();
            const href = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = href;
            a.download = 'calendar.ics';
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(href);
        } catch (err) {
            console.error('ICS download failed', err);
        }
    };

    const copyLink = () => {
        if (onCopyLink) onCopyLink();
    };

    const handleBackdropClick = (e) => {
        if (e.target === e.currentTarget) onClose();
    };

    return (
        <ModalOverlay visible onClick={handleBackdropClick}>
            <div className="modal-content calendar-export-modal">
                <h3>{t('personalData.exportModalTitle', 'Kalender exportieren')}</h3>
                <div className="export-options">
                    <button type="button" onClick={openGoogle}>
                        {t('personalData.exportGoogle', 'In Google Calendar öffnen')}
                    </button>
                    <button type="button" onClick={openOutlookApple}>
                        {t('personalData.exportOutlookApple', 'In Outlook/Apple abonnieren')}
                    </button>
                    <button type="button" onClick={downloadIcs}>
                        {t('personalData.exportDownload', 'ICS-Datei herunterladen')}
                    </button>
                    <button type="button" onClick={copyLink}>
                        {t('personalData.exportCopyLink', 'Link kopieren')}
                    </button>
                </div>
                <div className="modal-buttons">
                    <button type="button" onClick={onClose}>{t('cancel', 'Abbrechen')}</button>
                </div>
            </div>
        </ModalOverlay>
    );
};

CalendarExportModal.propTypes = {
    icsUrl: PropTypes.string.isRequired,
    onClose: PropTypes.func.isRequired,
    onCopyLink: PropTypes.func,
};

export default CalendarExportModal;
