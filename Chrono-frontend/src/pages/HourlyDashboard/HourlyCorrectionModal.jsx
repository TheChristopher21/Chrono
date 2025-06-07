// src/pages/HourlyDashboard/HourlyCorrectionModal.jsx
import React, { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import { formatLocalDate, formatTime } from './hourDashUtils'; // Eigene Utils verwenden

const HourlyCorrectionModal = ({
                                   visible,
                                   correctionDate, // YYYY-MM-DD String des Tages für die Korrektur
                                   dailySummaryForCorrection, // Das DailyTimeSummaryDTO des Tages
                                   onClose,
                                   onSubmitCorrection,
                                   t,
                               }) => {
    const [targetEntryId, setTargetEntryId] = useState('');
    const [desiredTimestampStr, setDesiredTimestampStr] = useState(''); // HH:mm
    const [desiredPunchType, setDesiredPunchType] = useState('START');
    const [reason, setReason] = useState('');

    useEffect(() => {
        if (visible) {
            setTargetEntryId('');
            setDesiredPunchType('START');
            setReason('');

            if (dailySummaryForCorrection?.entries && dailySummaryForCorrection.entries.length > 0) {
                const lastEntry = dailySummaryForCorrection.entries[dailySummaryForCorrection.entries.length - 1];
                try {
                    const lastEntryDate = new Date(lastEntry.entryTimestamp);
                    lastEntryDate.setHours(lastEntryDate.getHours() + 1);
                    setDesiredTimestampStr(formatTime(lastEntryDate));
                    setDesiredPunchType(lastEntry.punchType === 'START' ? 'ENDE' : 'START');
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
        const fullDesiredTimestamp = `${correctionDate}T${desiredTimestampStr}:00`;

        onSubmitCorrection({
            requestDate: correctionDate,
            targetEntryId: targetEntryId ? parseInt(targetEntryId, 10) : null,
            desiredTimestamp: fullDesiredTimestamp,
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
            setDesiredPunchType(selectedEntry.punchType);
        } else {
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
        // Scope-Klasse für konsistentes Styling mit anderen Modals
        <div className="modal-overlay hourly-dashboard scoped-dashboard">
            <div className="modal-content user-correction-modal-content"> {/* Wiederverwendung oder Anpassung */}
                <h3>
                    {t("userCorrectionModal.title", "Korrekturantrag für")} {formatDate(correctionDate)}
                </h3>
                <form onSubmit={handleSubmit}>
                    <div className="form-group">
                        <label htmlFor="targetEntrySelectHourly">{t("userCorrectionModal.selectEntry", "Zu korrigierender Eintrag (optional)")}:</label>
                        <select
                            id="targetEntrySelectHourly"
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
                        <label htmlFor="desiredPunchTypeHourly">{t("userCorrectionModal.desiredType", "Gewünschter Typ")}:</label>
                        <select
                            id="desiredPunchTypeHourly"
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
                        <label htmlFor="desiredTimeHourly">{t("userCorrectionModal.desiredTime", "Gewünschte Zeit")}:</label>
                        <input
                            id="desiredTimeHourly"
                            type="time"
                            name="desiredTime"
                            value={desiredTimestampStr}
                            onChange={(e) => setDesiredTimestampStr(e.target.value)}
                            required
                        />
                    </div>

                    <div className="form-group">
                        <label htmlFor="reasonHourly">{t("reason")}:</label>
                        <textarea
                            id="reasonHourly"
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

HourlyCorrectionModal.propTypes = {
    visible: PropTypes.bool.isRequired,
    correctionDate: PropTypes.string.isRequired,
    dailySummaryForCorrection: PropTypes.shape({
        date: PropTypes.string,
        entries: PropTypes.arrayOf(PropTypes.shape({
            id: PropTypes.number,
            entryTimestamp: PropTypes.string,
            punchType: PropTypes.string,
        })),
    }),
    onClose: PropTypes.func.isRequired,
    onSubmitCorrection: PropTypes.func.isRequired,
    t: PropTypes.func.isRequired,
};

export default HourlyCorrectionModal;