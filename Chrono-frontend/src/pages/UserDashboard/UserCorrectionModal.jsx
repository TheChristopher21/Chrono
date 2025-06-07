// src/pages/UserDashboard/UserCorrectionModal.jsx
import React, { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import { formatLocalDate, formatTime } from './userDashUtils'; // Eigene Utils verwenden

const UserCorrectionModal = ({
                                 visible,
                                 correctionDate, // YYYY-MM-DD String des Tages für die Korrektur
                                 dailySummaryForCorrection, // Das DailyTimeSummaryDTO des Tages
                                 onClose,
                                 onSubmitCorrection, // Neue Funktion, die die Korrekturdaten sendet
                                 t,
                             }) => {
    const [targetEntryId, setTargetEntryId] = useState(''); // ID des zu korrigierenden Eintrags (optional)
    const [desiredTimestampStr, setDesiredTimestampStr] = useState(''); // HH:mm
    const [desiredPunchType, setDesiredPunchType] = useState('START');
    const [reason, setReason] = useState('');

    useEffect(() => {
        if (visible) {
            // Reset form on open
            setTargetEntryId('');
            setDesiredPunchType('START');
            setReason('');
            // Set default time: If existing entries, suggest time after last entry, else 08:00
            if (dailySummaryForCorrection?.entries && dailySummaryForCorrection.entries.length > 0) {
                const lastEntry = dailySummaryForCorrection.entries[dailySummaryForCorrection.entries.length - 1];
                try {
                    const lastEntryDate = new Date(lastEntry.entryTimestamp);
                    lastEntryDate.setHours(lastEntryDate.getHours() + 1); // Suggest 1 hour after last punch
                    setDesiredTimestampStr(formatTime(lastEntryDate));
                    // Suggest opposite punch type of the last entry
                    if (lastEntry.punchType === 'START') setDesiredPunchType('ENDE');
                    else setDesiredPunchType('START');

                } catch (e) {
                    setDesiredTimestampStr('08:00');
                }
            } else {
                setDesiredTimestampStr('08:00');
            }
        }
    }, [visible, dailySummaryForCorrection]);

    if (!visible) return null;

    const handleSubmit = (e) => {
        e.preventDefault();
        if (!desiredTimestampStr || !desiredPunchType || !reason) {
            alert(t('userCorrectionModal.fillAllFields', 'Bitte alle Felder ausfüllen (außer optionaler Originaleintrag).'));
            return;
        }

        // Kombiniere correctionDate (YYYY-MM-DD) und desiredTimestampStr (HH:mm) zu einem ISO DateTime String
        const fullDesiredTimestamp = `${correctionDate}T${desiredTimestampStr}:00`; // Sekunden hinzufügen

        onSubmitCorrection({
            requestDate: correctionDate, // YYYY-MM-DD
            targetEntryId: targetEntryId ? parseInt(targetEntryId, 10) : null,
            desiredTimestamp: fullDesiredTimestamp, // YYYY-MM-DDTHH:mm:ss
            desiredPunchType,
            reason,
        });
    };

    const handleTargetEntryChange = (e) => {
        const selectedId = e.target.value;
        setTargetEntryId(selectedId);
        const selectedEntry = dailySummaryForCorrection?.entries?.find(entry => String(entry.id) === selectedId);
        if (selectedEntry) {
            setDesiredTimestampStr(formatTime(new Date(selectedEntry.entryTimestamp)));
            setDesiredPunchType(selectedEntry.punchType); // Schlägt den aktuellen Typ vor, der Benutzer kann ihn ändern
        } else {
            // Reset if "Neuer Eintrag" gewählt wird
            if (dailySummaryForCorrection?.entries && dailySummaryForCorrection.entries.length > 0) {
                const lastEntry = dailySummaryForCorrection.entries[dailySummaryForCorrection.entries.length - 1];
                const lastEntryDate = new Date(lastEntry.entryTimestamp);
                lastEntryDate.setHours(lastEntryDate.getHours() + 1);
                setDesiredTimestampStr(formatTime(lastEntryDate));
                setDesiredPunchType(lastEntry.punchType === 'START' ? 'ENDE' : 'START');
            } else {
                setDesiredTimestampStr('08:00');
                setDesiredPunchType('START');
            }
        }
    };


    return (
        <div className="modal-overlay user-dashboard scoped-dashboard"> {/* Scope hinzugefügt */}
            <div className="modal-content user-correction-modal-content"> {/* Eigene Klasse für spezifisches Styling */}
                <h3>
                    {t("userCorrectionModal.title", "Korrekturantrag für")} {formatDate(correctionDate)}
                </h3>
                <form onSubmit={handleSubmit}>
                    <div className="form-group">
                        <label htmlFor="targetEntrySelect">{t("userCorrectionModal.selectEntry", "Zu korrigierender Eintrag (optional)")}:</label>
                        <select
                            id="targetEntrySelect"
                            value={targetEntryId}
                            onChange={handleTargetEntryChange}
                        >
                            <option value="">-- {t("userCorrectionModal.newEntry", "Neuer Eintrag erstellen")} --</option>
                            {dailySummaryForCorrection?.entries?.map(entry => (
                                <option key={entry.id} value={entry.id}>
                                    {entry.punchType} @ {formatTime(new Date(entry.entryTimestamp))} (ID: {entry.id})
                                </option>
                            ))}
                        </select>
                    </div>

                    <div className="form-group">
                        <label htmlFor="desiredPunchType">{t("userCorrectionModal.desiredType", "Gewünschter Typ")}:</label>
                        <select
                            id="desiredPunchType"
                            name="desiredPunchType"
                            value={desiredPunchType}
                            onChange={(e) => setDesiredPunchType(e.target.value)}
                            required
                        >
                            <option value="START">START</option>
                            <option value="ENDE">ENDE</option>
                        </select>
                    </div>

                    <div className="form-group">
                        <label htmlFor="desiredTime">{t("userCorrectionModal.desiredTime", "Gewünschte Zeit")}:</label>
                        <input
                            id="desiredTime"
                            type="time"
                            name="desiredTime"
                            value={desiredTimestampStr}
                            onChange={(e) => setDesiredTimestampStr(e.target.value)}
                            required
                        />
                    </div>

                    <div className="form-group">
                        <label htmlFor="reason">{t("reason")}:</label>
                        <textarea
                            id="reason"
                            name="reason"
                            value={reason}
                            onChange={(e) => setReason(e.target.value)}
                            placeholder={t('userCorrectionModal.reasonPlaceholder', 'Begründung für die Korrektur...')}
                            required
                            rows="3"
                        />
                    </div>

                    <div className="modal-buttons">
                        <button type="submit" className="button-primary">
                            {t("submitCorrection", "Antrag senden")}
                        </button>
                        <button type="button" onClick={onClose} className="button-secondary">
                            {t("cancel", "Abbrechen")}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};

UserCorrectionModal.propTypes = {
    visible: PropTypes.bool.isRequired,
    correctionDate: PropTypes.string.isRequired, // YYYY-MM-DD Format
    dailySummaryForCorrection: PropTypes.shape({ // DailyTimeSummaryDTO Struktur
        date: PropTypes.string,
        entries: PropTypes.arrayOf(PropTypes.shape({
            id: PropTypes.number,
            entryTimestamp: PropTypes.string,
            punchType: PropTypes.string,
        })),
        // ... andere Felder aus DailyTimeSummaryDTO falls benötigt
    }),
    onClose: PropTypes.func.isRequired,
    onSubmitCorrection: PropTypes.func.isRequired,
    t: PropTypes.func.isRequired,
};

export default UserCorrectionModal;