// PercentageWeekOverview.jsx
import React from 'react';
import PropTypes from 'prop-types';
import { useTranslation } from '../../context/LanguageContext';

import {
    addDays,
    formatISO, // Behält formatISO für die interne Logik und Keys
    formatTime,
    computeDayTotalMinutes,
    isLateTime,
    minutesToHours,
    pickTime,
    formatDate, // Hinzugefügt für eine benutzerfreundlichere Datumsanzeige
} from './percentageDashUtils';

const PercentageWeekOverview = ({
                                    entries,
                                    monday,
                                    setMonday,
                                    weeklyWorked,
                                    weeklyExpected, // Wird hier nicht mehr direkt für tägliches Soll verwendet
                                    weeklyDiff,
                                    handleManualPunch,
                                    punchMessage,
                                    openCorrectionModal,
                                }) => {
    const { t } = useTranslation();

    const weekDates = Array.from({ length: 5 }, (_, i) => addDays(monday, i)); // Mo-Fr
    const isoStrings = weekDates.map(formatISO);

    // dayMap: isoString -> Array von Einträgen
    const dayMap = {};
    entries.forEach(e => {
        // Stellt sicher, dass startTime vorhanden ist, bevor slice aufgerufen wird
        const iso = e.startTime ? e.startTime.slice(0, 10) : null;
        if (iso) {
            if (!dayMap[iso]) dayMap[iso] = [];
            dayMap[iso].push(e);
        }
    });

    function prevWeek() {
        setMonday(prev => addDays(prev, -7));
    }
    function nextWeek() {
        setMonday(prev => addDays(prev, 7));
    }
    function jumpWeek(e) {
        const d = new Date(e.target.value);
        // Stelle sicher, dass das Datum gültig ist, bevor es gesetzt wird
        if (!isNaN(d.getTime())) {
            // getMondayOfWeek stellen sicher, dass immer ein Montag ausgewählt wird,
            // auch wenn der Nutzer einen anderen Tag im Datepicker wählt.
            // Hier gehen wir davon aus, dass 'monday' immer ein Montag ist
            // und die Navigation um ganze Wochen erfolgt.
            // Wenn der Datepicker direkt den Montag setzen soll:
            // setMonday(getMondayOfWeek(d)); ansonsten:
            setMonday(d);
        }
    }

    return (
        <section className="weekly-overview">
            <h3>{t('weeklyOverview')}</h3>

            {punchMessage && <div className="punch-message">{punchMessage}</div>}

            {/* Manuelles Stempeln */}
            <div className="punch-section">
                <button onClick={handleManualPunch}>
                    {t('manualPunchButton')}
                </button>
            </div>

            {/* Navigation */}
            <div className="week-navigation">
                <button onClick={prevWeek}>
                    ← {t('prevWeek')}
                </button>
                <input
                    type="date"
                    value={formatISO(monday)} // ISO-Format für den Input-Wert ist oft robuster
                    onChange={jumpWeek}
                />
                <button onClick={nextWeek}>
                    {t('nextWeek')} →
                </button>            </div>

            {/* Die wöchentliche Zusammenfassung (Ist, Soll, Saldo) bleibt hier, da sie das Wochensoll anzeigt */}
            <div className="weekly-summary">
                <p>
                    <strong>{t('weeklyHours')}:</strong> {minutesToHours(weeklyWorked)}
                </p>
                <p>
                    <strong>{t('expected')}:</strong> {minutesToHours(weeklyExpected)}
                </p>
                <p>
                    <strong className={(weeklyDiff ?? 0) < 0 ? 'balance-negative' : 'balance-positive'}>
                        {t('weekBalance')}:
                    </strong>
                    <span className={(weeklyDiff ?? 0) < 0 ? 'balance-negative' : 'balance-positive'}>
                        {minutesToHours(weeklyDiff)}
                    </span>
                </p>
            </div>

            {/* Darstellung der Einträge */}
            <div className="week-display">
                {weekDates.map((dayObj, idx) => {
                    const iso = isoStrings[idx];
                    const dayEntries = (dayMap[iso] || []).sort((a,b) => a.punchOrder - b.punchOrder);
                    const worked = computeDayTotalMinutes(dayEntries);
                    const displayDate = formatDate(dayObj); // Benutzerfreundliches Datumsformat

                    return (
                        <div key={idx} className="week-day-card">
                            <div className="week-day-header">
                                {dayObj.toLocaleDateString('de-DE', {
                                    weekday: 'short', // z.B. "Mo"
                                    day: '2-digit',
                                    month: '2-digit',
                                })}
                                {/* Hier wird KEINE tägliche Sollzeit mehr angezeigt */}
                            </div>

                            <div className="week-day-content">
                                {dayEntries.length === 0 ? (
                                    <p className="no-entries">{t('noEntries')}</p>
                                ) : (
                                    <ul>
                                        {dayEntries.map(e => {
                                            const labelByOrder = {1:t('workStart'),2:t('breakStart'),3:t('breakEnd'),4:t('workEnd')};
                                            let time = "-";
                                            // Stellt sicher, dass die Felder existieren, bevor darauf zugegriffen wird
                                            switch (e.punchOrder) {
                                                case 1:
                                                    time = formatTime(pickTime(e,"workStart","startTime"));
                                                    break;
                                                case 2:
                                                    time = formatTime(pickTime(e,"breakStart","startTime"));
                                                    break;
                                                case 3:
                                                    time = formatTime(pickTime(e,"breakEnd","endTime","startTime"));
                                                    break;
                                                case 4:
                                                    time = formatTime(pickTime(e,"workEnd","endTime","startTime"));
                                                    break;
                                                default:
                                                    time = "-";
                                            }
                                            const label = labelByOrder[e.punchOrder] || 'Unbekannt';

                                            return (
                                                <li key={e.id || `entry-${idx}-${e.punchOrder}`} className={isLateTime(time) ? 'late-time' : ''}>
                                                    <span className="entry-label">{label}:</span>
                                                    <span className="entry-time">{time}</span>
                                                </li>
                                            );
                                        })}
                                    </ul>
                                )}
                            </div>

                            {/* Anzeige der tatsächlich gearbeiteten Stunden pro Tag */}
                            {dayEntries.length > 0 && (
                                <div className="daily-summary">
                                    ⏱ {minutesToHours(worked)}
                                </div>
                            )}

                            <div className="correction-button-row">
                                <button onClick={() => openCorrectionModal(iso)}>
                                    {t('submitCorrectionRequest')}
                                </button>
                            </div>
                        </div>
                    );
                })}
            </div>
        </section>
    );
};

PercentageWeekOverview.propTypes = {
    entries: PropTypes.array.isRequired,
    monday: PropTypes.instanceOf(Date).isRequired,
    setMonday: PropTypes.func.isRequired,
    weeklyWorked: PropTypes.number,
    weeklyExpected: PropTypes.number, // Bleibt als Prop für die Gesamtanzeige
    weeklyDiff: PropTypes.number,
    handleManualPunch: PropTypes.func.isRequired,
    punchMessage: PropTypes.string,
    openCorrectionModal: PropTypes.func.isRequired,
};

export default PercentageWeekOverview;