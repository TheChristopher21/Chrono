// src/pages/AdminDashboard/EditTimeModal.jsx
import React, { useState, useEffect } from 'react';
import ModalOverlay from '../../components/ModalOverlay';
import PropTypes from 'prop-types';
import { formatLocalDateYMD } from './adminDashboardUtils';

/**
 * Helper to format a Date object into a 'YYYY-MM-DDTHH:mm:ss' string respecting local time.
 * @param {Date} date The date to format.
 * @returns {string} Formatted date string.
 */
const formatToLocalISOString = (date) => {
    if (!(date instanceof Date) || isNaN(date.getTime())) return '';
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');
    return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}`;
};

const EditTimeModal = ({
                           t,
                           isVisible,
                           targetDate,     // Date-Objekt des zu bearbeitenden Tages
                           dayEntries,     // Array von TimeTrackingEntryDTOs für diesen Tag
                           targetUsername,
                           onSubmit,       // Funktion, die die gesamte Liste der Einträge ans Backend sendet
                           onClose,
                           users,          // For break duration logic if still needed for autofill
                       }) => {
    const [editableEntries, setEditableEntries] = useState([]);

    useEffect(() => {
        if (isVisible) {
            const initialEntries = dayEntries && dayEntries.length > 0
                ? dayEntries.map(entry => ({
                    ...entry,
                    timeInput: entry.entryTimestamp ? entry.entryTimestamp.substring(11, 16) : '', // Extrahiere HH:mm
                    key: entry.id || `new-${Date.now()}-${Math.random()}`
                })).sort((a, b) => new Date(a.entryTimestamp || 0) - new Date(b.entryTimestamp || 0))
                : [];
            setEditableEntries(initialEntries);
        }
    }, [isVisible, dayEntries]);

    if (!isVisible) return null;

    const handleEntryChange = (index, field, value) => {
        const newEntries = [...editableEntries];
        const entryToUpdate = { ...newEntries[index] };
        const datePart = formatLocalDateYMD(targetDate);

        if (field === 'timeInput') {
            entryToUpdate.timeInput = value; // value ist 'HH:mm'
            entryToUpdate.entryTimestamp = value ? `${datePart}T${value}:00` : null; // Kombiniere mit Datum
        } else if (field === 'punchType') {
            entryToUpdate[field] = value;
        } else if (field === 'systemGeneratedNote') {
            entryToUpdate[field] = value;
        }
        newEntries[index] = entryToUpdate;
        setEditableEntries(newEntries);
    };

    const addEntry = () => {
        const datePart = formatLocalDateYMD(targetDate);
        let newFullTimestampStr = `${datePart}T08:00`; // Standard für den allerersten Eintrag

        const sortedEntries = [...editableEntries]
            .filter(e => e.entryTimestamp)
            .sort((a, b) => new Date(a.entryTimestamp) - new Date(b.entryTimestamp));

        let nextPunchType = 'START';
        if (sortedEntries.length > 0) {
            const lastEntry = sortedEntries[sortedEntries.length - 1];
            nextPunchType = lastEntry.punchType === 'START' ? 'ENDE' : 'START';

            if (lastEntry.entryTimestamp) {
                try {
                    const lastTime = new Date(lastEntry.entryTimestamp);
                    lastTime.setHours(lastTime.getHours() + 1); // Immer eine Stunde hinzufügen

                    const year = lastTime.getFullYear();
                    const month = String(lastTime.getMonth() + 1).padStart(2, '0');
                    const day = String(lastTime.getDate()).padStart(2, '0');
                    const hours = String(lastTime.getHours()).padStart(2, '0');
                    const minutes = String(lastTime.getMinutes()).padStart(2, '0');

                    newFullTimestampStr = `${year}-${month}-${day}T${hours}:${minutes}`;
                } catch (e) {
                    console.error("Error setting new entry time:", e);
                    newFullTimestampStr = `${datePart}T00:00`;
                }
            }
        }

        const newEntryList = [
            ...editableEntries,
            {
                id: null,
                username: targetUsername,
                timeInput: newFullTimestampStr.substring(11, 16),
                entryTimestamp: `${newFullTimestampStr}:00`,
                punchType: nextPunchType,
                source: 'ADMIN_CORRECTION',
                correctedByUser: true,
                systemGeneratedNote: '',
                key: `new-${Date.now()}-${Math.random()}`
            }
        ];

        newEntryList.sort((a, b) => new Date(a.entryTimestamp || 0) - new Date(b.entryTimestamp || 0));
        setEditableEntries(newEntryList);
    };

    const removeEntry = (indexToRemove) => {
        setEditableEntries(editableEntries.filter((_, index) => index !== indexToRemove));
    };

    const handleSubmitLocal = (e) => {
        e.preventDefault();
        let isValid = true;
        let lastType = null;
        let lastTimestamp = null;

        const sortedEntries = [...editableEntries].sort((a, b) =>
            new Date(a.entryTimestamp || 0) - new Date(b.entryTimestamp || 0)
        );

        for (const entry of sortedEntries) {
            if (!entry.entryTimestamp || !entry.punchType) {
                alert(t('editTimeModal.errors.timestampOrTypeMissing', 'Zeitstempel oder Typ fehlt bei einem Eintrag.'));
                isValid = false; break;
            }
            const currentEntryTime = new Date(entry.entryTimestamp);
            if (lastTimestamp && currentEntryTime < lastTimestamp) {
                alert(t('editTimeModal.errors.chronologicalOrder', 'Stempelungen müssen in chronologischer Reihenfolge sein.'));
                isValid = false; break;
            }
            if (entry.punchType === lastType) {
                alert(t('editTimeModal.errors.alternateStartEnd', 'START und ENDE Stempel müssen sich abwechseln.') + ` Problem bei ${entry.punchType} um ${entry.timeInput}`);
                isValid = false; break;
            }
            lastType = entry.punchType;
            lastTimestamp = currentEntryTime;
        }

        if (sortedEntries.length > 0 && sortedEntries[0].punchType !== 'START') {
            alert(t('editTimeModal.errors.firstMustBeStart', 'Der erste Stempel des Tages muss ein START sein.'));
            isValid = false;
        }

        if (isValid) {
            const entriesToSubmit = sortedEntries.map(entry => ({
                id: entry.id,
                entryTimestamp: entry.entryTimestamp,
                punchType: entry.punchType,
                source: entry.id ? entry.source : 'ADMIN_CORRECTION',
                correctedByUser: true,
                systemGeneratedNote: entry.systemGeneratedNote || null
            }));
            onSubmit(entriesToSubmit);
        }
    };

    const autoFillBreaks = () => {
        const startEntries = editableEntries.filter(e => e.punchType === 'START').sort((a,b) => new Date(a.entryTimestamp) - new Date(b.entryTimestamp));
        const endEntries = editableEntries.filter(e => e.punchType === 'ENDE').sort((a,b) => new Date(a.entryTimestamp) - new Date(b.entryTimestamp));

        if (!startEntries.length || !endEntries.length) {
            alert(t('editTimeModal.errors.autoBreakCondition', 'Es muss mindestens ein START und ein ENDE Stempel vorhanden sein, um eine Pause einzufügen.'));
            return;
        }
        const firstWorkStartTime = new Date(startEntries[0].entryTimestamp);
        const lastWorkEndTime = new Date(endEntries[endEntries.length - 1].entryTimestamp);

        if (firstWorkStartTime >= lastWorkEndTime) {
            alert(t('editTimeModal.errors.startBeforeEndForBreak', 'Arbeitsbeginn muss vor Arbeitsende liegen, um Pause einzufügen.'));
            return;
        }

        const userConf = users.find(u => u.username === targetUsername);
        const breakDurationMinutes = userConf?.breakDuration || 30;
        const totalWorkDuration = (lastWorkEndTime - firstWorkStartTime) / (1000 * 60);

        if (totalWorkDuration <= breakDurationMinutes) {
            alert(t('editTimeModal.errors.workTooShortForBreak', 'Arbeitszeit zu kurz für die Standardpause.'));
            return;
        }

        const idealBreakStartTime = new Date(firstWorkStartTime.getTime() + (totalWorkDuration / 2 - breakDurationMinutes / 2) * 60000);
        const idealBreakEndTime = new Date(idealBreakStartTime.getTime() + breakDurationMinutes * 60000);

        if (idealBreakStartTime <= firstWorkStartTime || idealBreakEndTime >= lastWorkEndTime) {
            alert(t('editTimeModal.errors.breakOutsideWork', 'Automatische Pause würde außerhalb der primären Arbeitszeit liegen oder ungültig sein.'));
            return;
        }

        const nonBreakEntries = [];
        let i = 0;
        while (i < editableEntries.length) {
            if (i + 1 < editableEntries.length &&
                editableEntries[i].punchType === 'ENDE' &&
                editableEntries[i+1].punchType === 'START') {
                if (new Date(editableEntries[i].entryTimestamp).getTime() !== lastWorkEndTime.getTime() &&
                    new Date(editableEntries[i+1].entryTimestamp).getTime() !== firstWorkStartTime.getTime()) {
                    i += 2;
                    continue;
                }
            }
            nonBreakEntries.push(editableEntries[i]);
            i++;
        }

        const idealBreakStartTimeFullStr = formatToLocalISOString(idealBreakStartTime);
        const idealBreakEndTimeFullStr = formatToLocalISOString(idealBreakEndTime);
        const lastWorkEndTimeFullStr = formatToLocalISOString(lastWorkEndTime);

        const newEntriesWithBreak = [
            ...nonBreakEntries.filter(e => new Date(e.entryTimestamp).getTime() <= idealBreakStartTime.getTime() && e.entryTimestamp !== lastWorkEndTimeFullStr),
            {
                id: null, username: targetUsername,
                timeInput: idealBreakStartTimeFullStr.substring(11, 16),
                entryTimestamp: idealBreakStartTimeFullStr,
                punchType: 'ENDE', source: 'ADMIN_CORRECTION', correctedByUser: true,
                systemGeneratedNote: t('editTimeModal.notes.autoBreakStart', 'Pause Start (Auto)'),
                key: `new-autobreakstart-${Date.now()}`
            },
            {
                id: null, username: targetUsername,
                timeInput: idealBreakEndTimeFullStr.substring(11, 16),
                entryTimestamp: idealBreakEndTimeFullStr,
                punchType: 'START', source: 'ADMIN_CORRECTION', correctedByUser: true,
                systemGeneratedNote: t('editTimeModal.notes.autoBreakEnd', 'Pause Ende (Auto)'),
                key: `new-autobreakend-${Date.now()}`
            },
            ...nonBreakEntries.filter(e => new Date(e.entryTimestamp).getTime() >= idealBreakEndTime.getTime() || e.entryTimestamp === lastWorkEndTimeFullStr)
        ].filter((entry, index, self) =>
                index === self.findIndex((t) => (
                    (t.key && entry.key && t.key === entry.key) ||
                    (t.entryTimestamp === entry.entryTimestamp && t.punchType === entry.punchType)
                ))
        );

        setEditableEntries(newEntriesWithBreak.sort((a, b) => new Date(a.entryTimestamp || 0) - new Date(b.entryTimestamp || 0)));
    };


    return (
        <div className="admin-dashboard scoped-dashboard">
            <ModalOverlay visible={isVisible}>
                <div className="modal-content edit-time-modal-content">
                    <h3>
                        {t("adminDashboard.editTrackingTitle", "Zeiterfassung bearbeiten für")} {targetUsername} <br />
                        {t("date", "Datum")}: {targetDate?.toLocaleDateString("de-DE")}
                    </h3>
                    <form onSubmit={handleSubmitLocal}>
                        {editableEntries.map((entry, index) => (
                            <div key={entry.key} className="time-entry-edit-row">
                                <select
                                    name="punchType"
                                    value={entry.punchType || 'START'}
                                    onChange={(e) => handleEntryChange(index, 'punchType', e.target.value)}
                                    className="punch-type-select"
                                >
                                    <option value="START">START</option>
                                    <option value="ENDE">ENDE</option>
                                </select>
                                <input
                                    type="time"
                                    name="timeInput"
                                    value={entry.timeInput}
                                    onChange={(e) => handleEntryChange(index, 'timeInput', e.target.value)}
                                    required
                                    className="timestamp-input"
                                />
                                <input
                                    type="text"
                                    name="systemGeneratedNote"
                                    value={entry.systemGeneratedNote || ""}
                                    onChange={(e) => handleEntryChange(index, 'systemGeneratedNote', e.target.value)}
                                    placeholder={t('editTimeModal.notePlaceholder', 'Notiz (optional)')}
                                    className="note-input"
                                />
                                <button type="button" onClick={() => removeEntry(index)} className="remove-entry-button button-danger-plain">
                                    {t('remove', 'Entfernen')}
                                </button>
                            </div>
                        ))}
                        <div className="modal-action-buttons">
                            <button type="button" onClick={addEntry} className="add-entry-button button-secondary">
                                {t('editTimeModal.addEntryButton', 'Eintrag hinzufügen')}
                            </button>
                            <button type="button" onClick={autoFillBreaks} className="autofill-break-button button-secondary">
                                {t('editTimeModal.autoFillBreakButton', 'Standardpause einfügen')}
                            </button>
                        </div>

                        <div className="modal-buttons main-actions">
                            <button type="submit" className="button-primary">{t("save", "Speichern")}</button>
                            <button type="button" onClick={onClose} className="button-cancel">
                                {t("cancel", "Abbrechen")}
                            </button>
                        </div>
                    </form>
                </div>
            </ModalOverlay>
        </div>
    );
};

EditTimeModal.propTypes = {
    t: PropTypes.func.isRequired,
    isVisible: PropTypes.bool.isRequired,
    targetDate: PropTypes.instanceOf(Date),
    dayEntries: PropTypes.arrayOf(
        PropTypes.shape({
            id: PropTypes.number,
            entryTimestamp: PropTypes.string,
            punchType: PropTypes.oneOf(['START', 'ENDE']),
            source: PropTypes.string,
            correctedByUser: PropTypes.bool,
            systemGeneratedNote: PropTypes.string
        })
    ).isRequired,
    targetUsername: PropTypes.string.isRequired,
    onSubmit: PropTypes.func.isRequired,
    onClose: PropTypes.func.isRequired,
    users: PropTypes.array,
};

export default EditTimeModal;