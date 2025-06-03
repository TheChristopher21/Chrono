// src/pages/AdminDashboard/EditTimeModal.jsx
import React, { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import { formatLocalDateYMD } from './adminDashboardUtils';

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
                    timestampInput: entry.entryTimestamp ? entry.entryTimestamp.substring(0, 16) : '', // YYYY-MM-DDTHH:mm
                    key: entry.id || `new-${Date.now()}-${Math.random()}`
                })).sort((a, b) => new Date(a.timestampInput || 0) - new Date(b.timestampInput || 0))
                : []; // Start with empty if no entries or if creating new day
            setEditableEntries(initialEntries);
        }
    }, [isVisible, dayEntries]);

    if (!isVisible) return null;

    const handleEntryChange = (index, field, value) => {
        const newEntries = [...editableEntries];
        const entryToUpdate = { ...newEntries[index] };

        if (field === 'timestampInput') {
            entryToUpdate[field] = value; // value is YYYY-MM-DDTHH:mm
            entryToUpdate.entryTimestamp = value ? `${value}:00` : null; // Add seconds for backend
        } else if (field === 'punchType') {
            entryToUpdate[field] = value;
        } else if (field === 'systemGeneratedNote') {
            entryToUpdate[field] = value;
        }
        newEntries[index] = entryToUpdate;
        setEditableEntries(newEntries);
    };

    const addEntry = () => {
        let newTimestampStr = '';
        if (targetDate) {
            const datePart = formatLocalDateYMD(targetDate);
            newTimestampStr = `${datePart}T00:00`; // Default to midnight
        }

        // Determine if the next punch should be START or ENDE based on the last entry
        let nextPunchType = 'START';
        if (editableEntries.length > 0) {
            const lastEntry = editableEntries[editableEntries.length - 1];
            if (lastEntry.punchType === 'START') {
                nextPunchType = 'ENDE';
                // Pre-fill time if possible
                if (lastEntry.timestampInput) {
                    try {
                        const lastTime = new Date(lastEntry.entryTimestamp);
                        lastTime.setHours(lastTime.getHours() + 1); // Default to 1 hour later
                        newTimestampStr = lastTime.toISOString().substring(0,16);
                    } catch(e) { /* ignore date parse error */ }
                }
            } else { // Last was ENDE or no entries yet
                if (lastEntry.timestampInput) {
                    try {
                        const lastTime = new Date(lastEntry.entryTimestamp);
                        lastTime.setMinutes(lastTime.getMinutes() + 15); // e.g. 15 mins after last ENDE
                        newTimestampStr = lastTime.toISOString().substring(0,16);
                    } catch(e) { /* ignore */ }
                }
            }
        }


        setEditableEntries([
            ...editableEntries,
            {
                id: null,
                username: targetUsername,
                timestampInput: newTimestampStr,
                entryTimestamp: newTimestampStr ? `${newTimestampStr}:00` : null,
                punchType: nextPunchType,
                source: 'ADMIN_CORRECTION',
                correctedByUser: true,
                systemGeneratedNote: '',
                key: `new-${Date.now()}-${Math.random()}`
            }
        ]);
    };

    const removeEntry = (index) => {
        setEditableEntries(editableEntries.filter((_, i) => i !== index));
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
            if (entry.punchType === lastType) { // Simplified: direct same type after another is invalid
                alert(t('editTimeModal.errors.alternateStartEnd', 'START und ENDE Stempel müssen sich abwechseln.') + ` Problem bei ${entry.punchType} um ${entry.entryTimestamp.substring(11,16)}`);
                isValid = false; break;
            }
            lastType = entry.punchType;
            lastTimestamp = currentEntryTime;
        }

        if (sortedEntries.length > 0 && sortedEntries[0].punchType !== 'START') {
            alert(t('editTimeModal.errors.firstMustBeStart', 'Der erste Stempel des Tages muss ein START sein.'));
            isValid = false;
        }
        if (sortedEntries.length > 0 && sortedEntries.length % 2 !== 0 && sortedEntries[sortedEntries.length -1].punchType !== 'START' ) {
            // Allow odd number if last is START (open day)
            // but if last is ENDE and odd number, that's unusual (e.g. ENDE without preceding START). This is caught by type alternation.
        }


        if (isValid) {
            const entriesToSubmit = sortedEntries.map(entry => ({
                id: entry.id,
                entryTimestamp: entry.entryTimestamp, // Ensure it's YYYY-MM-DDTHH:mm:ss
                punchType: entry.punchType,
                source: entry.id ? entry.source : 'ADMIN_CORRECTION',
                correctedByUser: true,
                systemGeneratedNote: entry.systemGeneratedNote || null // Send null if empty
            }));
            onSubmit(entriesToSubmit);
        }
    };

    const autoFillBreaks = () => {
        const workStartTimeStr = editableEntries.find(e => e.punchType === 'START')?.entryTimestamp;
        let workEndTimeStr = editableEntries.find(e => e.punchType === 'ENDE' && new Date(e.entryTimestamp) > new Date(workStartTimeStr || 0))?.entryTimestamp;

        // Find the last START and the first ENDE after it if multiple pairs exist
        const startEntries = editableEntries.filter(e => e.punchType === 'START').sort((a,b) => new Date(a.entryTimestamp) - new Date(b.entryTimestamp));
        const endEntries = editableEntries.filter(e => e.punchType === 'ENDE').sort((a,b) => new Date(a.entryTimestamp) - new Date(b.entryTimestamp));

        if (!startEntries.length || !endEntries.length) {
            alert(t('editTimeModal.errors.autoBreakCondition', 'Es muss mindestens ein START und ein ENDE Stempel vorhanden sein, um eine Pause einzufügen.'));
            return;
        }
        // Take the earliest START and latest ENDE for the main work block
        const firstWorkStartTime = new Date(startEntries[0].entryTimestamp);
        const lastWorkEndTime = new Date(endEntries[endEntries.length -1].entryTimestamp);

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

        // Place break somewhat in the middle, ensuring it's after the first START and before the last ENDE
        const idealBreakStartTime = new Date(firstWorkStartTime.getTime() + (totalWorkDuration / 2 - breakDurationMinutes / 2) * 60000);
        const idealBreakEndTime = new Date(idealBreakStartTime.getTime() + breakDurationMinutes * 60000);

        if (idealBreakStartTime <= firstWorkStartTime || idealBreakEndTime >= lastWorkEndTime) {
            alert(t('editTimeModal.errors.breakOutsideWork', 'Automatische Pause würde außerhalb der primären Arbeitszeit liegen oder ungültig sein.'));
            return;
        }

        // Remove existing break-like punches (ENDE immediately followed by START)
        const nonBreakEntries = [];
        let i = 0;
        while (i < editableEntries.length) {
            if (i + 1 < editableEntries.length &&
                editableEntries[i].punchType === 'ENDE' &&
                editableEntries[i+1].punchType === 'START') {
                // Check if it's the primary work start/end
                if (editableEntries[i].entryTimestamp !== lastWorkEndTime.toISOString().substring(0,19) &&
                    editableEntries[i+1].entryTimestamp !== firstWorkStartTime.toISOString().substring(0,19)) {
                    // This looks like an existing break, skip both
                    i += 2;
                    continue;
                }
            }
            nonBreakEntries.push(editableEntries[i]);
            i++;
        }


        const newEntriesWithBreak = [
            ...nonBreakEntries.filter(e => new Date(e.entryTimestamp) <= idealBreakStartTime && e.entryTimestamp !== lastWorkEndTime.toISOString().substring(0,19) ), // Keep entries before break, exclude final ENDE if it's too early
            {
                id: null, username: targetUsername,
                timestampInput: idealBreakStartTime.toISOString().substring(0, 16),
                entryTimestamp: idealBreakStartTime.toISOString().substring(0, 19),
                punchType: 'ENDE', source: 'ADMIN_CORRECTION', correctedByUser: true,
                systemGeneratedNote: t('editTimeModal.notes.autoBreakStart', 'Pause Start (Auto)'),
                key: `new-autobreakstart-${Date.now()}`
            },
            {
                id: null, username: targetUsername,
                timestampInput: idealBreakEndTime.toISOString().substring(0, 16),
                entryTimestamp: idealBreakEndTime.toISOString().substring(0, 19),
                punchType: 'START', source: 'ADMIN_CORRECTION', correctedByUser: true,
                systemGeneratedNote: t('editTimeModal.notes.autoBreakEnd', 'Pause Ende (Auto)'),
                key: `new-autobreakend-${Date.now()}`
            },
            ...nonBreakEntries.filter(e => new Date(e.entryTimestamp) >= idealBreakEndTime || e.entryTimestamp === lastWorkEndTime.toISOString().substring(0,19) ) // Keep entries after break
        ].filter((entry, index, self) => // Remove duplicates by key or timestamp+type
                index === self.findIndex((t) => (
                    (t.key && entry.key && t.key === entry.key) ||
                    (t.entryTimestamp === entry.entryTimestamp && t.punchType === entry.punchType)
                ))
        );

        setEditableEntries(newEntriesWithBreak.sort((a, b) => new Date(a.entryTimestamp || 0) - new Date(b.entryTimestamp || 0)));
    };


    return (
        <div className="admin-dashboard scoped-dashboard"> {/* Ensure scope for CSS vars */}
            <div className="modal-overlay">
                <div className="modal-content edit-time-modal-content"> {/* Specific class for wider modal if needed */}
                    <h3>
                        {t("adminDashboard.editTrackingTitle", "Zeiterfassung bearbeiten für")} {targetUsername} <br />
                        {t("date", "Datum")}: {targetDate?.toLocaleDateString("de-DE")}
                    </h3>
                    <form onSubmit={handleSubmitLocal}>
                        {editableEntries.map((entry, index) => (
                            <div key={entry.key} className="time-entry-edit-row">
                                <select
                                    name="punchType"
                                    value={entry.punchType || 'START'} // Default to START if not set
                                    onChange={(e) => handleEntryChange(index, 'punchType', e.target.value)}
                                    className="punch-type-select"
                                >
                                    <option value="START">START</option>
                                    <option value="ENDE">ENDE</option>
                                </select>
                                <input
                                    type="datetime-local"
                                    name="timestampInput"
                                    value={entry.timestampInput}
                                    onChange={(e) => handleEntryChange(index, 'timestampInput', e.target.value)}
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
            </div>
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
            entryTimestamp: PropTypes.string, // ISO String like "2023-10-26T08:00:00"
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