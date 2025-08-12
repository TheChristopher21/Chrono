// src/components/CorrectionModal.jsx
import React, { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import ModalOverlay from './ModalOverlay';
import { formatDate } from '../utils/dateUtils';
import { formatTime, sortEntries } from '../utils/timeUtils';

const CorrectionModal = ({
                             visible,
                             correctionDate,
                             dailySummary,
                             onClose,
                             onSubmit,
                             t,
                             dashboard = 'user', // 'user' | 'hourly' | 'percentage'
                         }) => {
    const [entries, setEntries] = useState([]);
    const [reason, setReason] = useState('');

    useEffect(() => {
        if (visible) {
            const initialEntries =
                sortEntries(dailySummary?.entries).map((entry) => ({
                    time: formatTime(new Date(entry.entryTimestamp)),
                    type: entry.punchType,
                })) || [];

            if (initialEntries.length === 0) {
                setEntries([
                    { time: '08:00', type: 'START' },
                    { time: '17:00', type: 'ENDE' },
                ]);
            } else {
                setEntries(initialEntries);
            }
            setReason('');
        }
    }, [visible, dailySummary]);

    if (!visible) return null;

    const handleEntryChange = (index, field, value) => {
        setEntries((prev) => {
            const next = [...prev];
            next[index][field] = value;
            return next;
        });
    };

    const addEntry = () => {
        const last = entries[entries.length - 1];
        const nextType = last?.type === 'START' ? 'ENDE' : 'START';
        setEntries([...entries, { time: '', type: nextType }]);
    };

    const removeEntry = (index) => {
        setEntries((prev) => prev.filter((_, i) => i !== index));
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        if (!reason.trim()) {
            alert(t('correctionModal.reasonRequired', 'Bitte geben Sie einen Grund an.'));
            return;
        }
        if (entries.some((e) => !e.time)) {
            alert(t('correctionModal.timeRequired', 'Bitte füllen Sie alle Zeitfelder aus.'));
            return;
        }
        onSubmit(entries, reason);
    };

    // map dashboard -> data-context expected by the isolated CSS
    const contextMap = {
        user: 'correction-user',
        hourly: 'correction-hourly',
        percentage: 'correction-percentage',
    };
    const dataContext = contextMap[dashboard] || contextMap.user;

    return (
        <ModalOverlay
            visible={visible}
            onClose={onClose}
            className="modal-overlay"
            data-context={dataContext}
        >
            <div className="modal-content" data-context={dataContext}>
                <div className="modal-header">
                    <h3>
                        {t('userCorrectionModal.title', 'Korrekturantrag für')}{' '}
                        {formatDate(new Date(`${correctionDate}T00:00:00`))}
                    </h3>
                </div>

                <div className="modal-body">
                    <form onSubmit={handleSubmit}>
                        <div className="correction-entries">
                            {entries.map((entry, index) => (
                                <div key={index} className="correction-entry entry-row">
                                    <input
                                        type="time"
                                        value={entry.time}
                                        onChange={(e) => handleEntryChange(index, 'time', e.target.value)}
                                        required
                                        aria-label={t('correctionModal.timeLabel', 'Zeit')}
                                    />
                                    <select
                                        value={entry.type}
                                        onChange={(e) => handleEntryChange(index, 'type', e.target.value)}
                                        aria-label={t('correctionModal.typeLabel', 'Typ')}
                                    >
                                        <option value="START">START</option>
                                        <option value="ENDE">ENDE</option>
                                    </select>
                                    <button
                                        type="button"
                                        className="remove-entry-btn"
                                        onClick={() => removeEntry(index)}
                                        aria-label={t('correctionModal.removeEntry', 'Eintrag entfernen')}
                                        title={t('correctionModal.removeEntry', 'Eintrag entfernen')}
                                    >
                                        &times;
                                    </button>
                                </div>
                            ))}
                        </div>

                        <button type="button" className="add-correction-entry" onClick={addEntry}>
                            + {t('correctionModal.addEntry', 'Eintrag hinzufügen')}
                        </button>

                        <div className="form-group" style={{ marginTop: '1rem' }}>
                            <label htmlFor="reason-textarea">{t('reason', 'Grund')}:</label>
                            <textarea
                                id="reason-textarea"
                                value={reason}
                                onChange={(e) => setReason(e.target.value)}
                                placeholder={t(
                                    'userCorrectionModal.reasonPlaceholder',
                                    'Begründung für die Korrektur...'
                                )}
                                required
                                rows={4}
                            />
                        </div>

                        {/* Footer (sticky via CSS) */}
                        <div className="modal-footer">
                            <button type="submit" className="button-primary">
                                {t('submitCorrection', 'Antrag senden')}
                            </button>
                            <button type="button" onClick={onClose} className="button-secondary">
                                {t('cancel', 'Abbrechen')}
                            </button>
                        </div>
                    </form>
                </div>
            </div>
        </ModalOverlay>
    );
};

CorrectionModal.propTypes = {
    visible: PropTypes.bool.isRequired,
    correctionDate: PropTypes.string.isRequired,
    dailySummary: PropTypes.shape({
        entries: PropTypes.arrayOf(
            PropTypes.shape({
                entryTimestamp: PropTypes.string,
                punchType: PropTypes.string,
            })
        ),
    }),
    onClose: PropTypes.func.isRequired,
    onSubmit: PropTypes.func.isRequired,
    t: PropTypes.func.isRequired,
    dashboard: PropTypes.oneOf(['user', 'hourly', 'percentage']),
};

export default CorrectionModal;
