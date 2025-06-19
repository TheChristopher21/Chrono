// src/pages/PercentageDashboard/PercentageCorrectionModal.jsx
import React, { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import { formatTime, formatDate } from './percentageDashUtils';

const PercentageCorrectionModal = ({
                                       visible,
                                       correctionDate,
                                       dailySummaryForCorrection,
                                       onClose,
                                       onSubmitCorrection,
                                       t,
                                   }) => {
    const [entries, setEntries] = useState([]);
    const [reason, setReason] = useState('');

    useEffect(() => {
        if (visible) {
            if (dailySummaryForCorrection?.entries && dailySummaryForCorrection.entries.length > 0) {
                setEntries(dailySummaryForCorrection.entries.map(entry => ({
                    time: formatTime(new Date(entry.entryTimestamp)),
                    type: entry.punchType,
                    key: entry.id
                })));
            } else {
                setEntries([
                    { time: '08:00', type: 'START', key: 'new-1' },
                    { time: '17:00', type: 'ENDE', key: 'new-2' }
                ]);
            }
            setReason('');
        }
    }, [visible, dailySummaryForCorrection]);

    if (!visible) return null;

    const handleEntryChange = (index, field, value) => {
        const newEntries = [...entries];
        newEntries[index][field] = value;
        setEntries(newEntries);
    };

    const handleRemoveEntry = (index) => {
        const newEntries = entries.filter((_, i) => i !== index);
        setEntries(newEntries);
    };

    const handleAddEntry = () => {
        const lastEntry = entries.length > 0 ? entries[entries.length - 1] : null;
        const newType = lastEntry && lastEntry.type === 'START' ? 'ENDE' : 'START';
        setEntries([...entries, { time: '12:00', type: newType, key: `new-${Date.now()}` }]);
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        onSubmitCorrection(entries, reason);
    };

    return (
        <div className="modal-backdrop">
            <div className="modal-content">
                <form onSubmit={handleSubmit}>
                    <h3>{t('correctionFor')} {formatDate(correctionDate)}</h3>
                    {entries.map((entry, index) => (
                        <div key={entry.key || index} className="entry-row">
                            <input type="time" value={entry.time} onChange={(e) => handleEntryChange(index, 'time', e.target.value)} required />
                            <select value={entry.type} onChange={(e) => handleEntryChange(index, 'type', e.target.value)}>
                                <option value="START">{t('start')}</option>
                                <option value="ENDE">{t('end')}</option>
                            </select>
                            <button type="button" onClick={() => handleRemoveEntry(index)} className="button-remove" title={t('remove', 'Entfernen')}>
                                &times;
                            </button>
                        </div>
                    ))}
                    <button type="button" onClick={handleAddEntry} className="button-add-entry">
                        {t('addEntry', 'Eintrag hinzufügen')}
                    </button>
                    <div className="form-group">
                        <label htmlFor="reasonPct">{t("reason")}:</label>
                        <textarea id="reasonPct" value={reason} onChange={(e) => setReason(e.target.value)} placeholder={t('userCorrectionModal.reasonPlaceholder', 'Begründung...')} required rows="3" />
                    </div>
                    <div className="modal-buttons">
                        <button type="submit" className="button-primary">{t("submitCorrection", "Antrag senden")}</button>
                        <button type="button" onClick={onClose} className="button-secondary">{t("cancel", "Abbrechen")}</button>
                    </div>
                </form>
            </div>
        </div>
    );
};
PercentageCorrectionModal.propTypes = {
    visible: PropTypes.bool.isRequired,
    correctionDate: PropTypes.string.isRequired,
    dailySummaryForCorrection: PropTypes.object,
    onClose: PropTypes.func.isRequired,
    onSubmitCorrection: PropTypes.func.isRequired,
    t: PropTypes.func.isRequired,
};

export default PercentageCorrectionModal;