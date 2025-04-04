// HourlyWeekOverview.jsx
import React from 'react';
import PropTypes from 'prop-types';

import {
    addDays,
    formatLocalDate,
    formatDate,
    formatTime,
    computeDayTotalMinutes,
    getMondayOfWeek
} from './hourDashUtils';

const HourlyWeekOverview = ({
                                t,
                                userProfile,
                                allEntries,
                                dailyNotes,
                                noteEditVisibility,
                                setDailyNotes,
                                setNoteEditVisibility,
                                handleSaveNote,
                                openCorrectionModal,
                                selectedMonday,
                                setSelectedMonday,
                                weeklyTotalMins,
                                monthlyTotalMins,
                                handleManualPunch,
                                punchMessage
                            }) => {

    // Helper
    const weekDates = Array.from({ length: 7 }, (_, i) => addDays(selectedMonday, i));
    const weekStrs = weekDates.map(d => formatLocalDate(d));
    const weeklyEntries = allEntries.filter(track => {
        const localDate = track.startTime.slice(0, 10);
        return weekStrs.includes(localDate);
    });

    // dayMap => pro Tag Einträge
    const weeklyDayMap = {};
    weeklyEntries.forEach(entry => {
        const ds = entry.startTime.slice(0, 10);
        if (!weeklyDayMap[ds]) weeklyDayMap[ds] = [];
        weeklyDayMap[ds].push(entry);
    });

    // Gesamtsumme (aktuelle Woche)
    const weeklyHrs = Math.floor(weeklyTotalMins / 60);
    const weeklyRemMins = weeklyTotalMins % 60;

    // Gesamtsumme (Monat)
    const monthlyHrs = Math.floor(monthlyTotalMins / 60);
    const monthlyRemMins = monthlyTotalMins % 60;

    function handlePrevWeek() {
        setSelectedMonday(prev => addDays(prev, -7));
    }
    function handleNextWeek() {
        setSelectedMonday(prev => addDays(prev, 7));
    }
    function handleWeekJump(e) {
        const picked = new Date(e.target.value);
        if (!isNaN(picked.getTime())) {
            setSelectedMonday(getMondayOfWeek(picked));
        }
    }

    return (
        <section className="weekly-overview">
            <h3>Wochenübersicht</h3>

            {punchMessage && <div className="punch-message">{punchMessage}</div>}

            {/* Manuelles Stempeln */}
            <div className="punch-section">
                <h4>{t("manualPunchTitle")}</h4>
                <button onClick={handleManualPunch}>{t("manualPunchButton")}</button>
            </div>

            {/* Wochen-Navigation */}
            <div className="week-navigation">
                <button onClick={handlePrevWeek}>← Vorige Woche</button>
                <input
                    type="date"
                    onChange={handleWeekJump}
                    value={formatLocalDate(selectedMonday)}
                />
                <button onClick={handleNextWeek}>Nächste Woche →</button>
            </div>

            <div className="weekly-monthly-totals">
                <p>Gesamtstunden (aktuelle Woche): {weeklyHrs}h {weeklyRemMins}min</p>
                <p>Gesamtstunden (Monat): {monthlyHrs}h {monthlyRemMins}min</p>
            </div>

            {/* Tages-Übersicht */}
            <div className="week-display">
                {weekDates.map((dayDate, index) => {
                    const isoDay = formatLocalDate(dayDate);
                    const dayEntries = weeklyDayMap[isoDay] || [];
                    const dayName = dayDate.toLocaleDateString('de-DE', { weekday: 'long' });
                    const formattedDay = formatDate(isoDay);

                    dayEntries.sort((a, b) => a.punchOrder - b.punchOrder);

                    const workStart = dayEntries.find(e => e.punchOrder === 1);
                    const breakStart = dayEntries.find(e => e.punchOrder === 2);
                    const breakEnd = dayEntries.find(e => e.punchOrder === 3);
                    const workEnd = dayEntries.find(e => e.punchOrder === 4);

                    return (
                        <div key={index} className="week-day-card">
                            <div className="week-day-header">
                                <strong>{dayName}, {formattedDay}</strong>
                            </div>
                            <div className="week-day-content">
                                {dayEntries.length === 0 ? (
                                    <p>Keine Einträge</p>
                                ) : (
                                    <ul>
                                        <li>
                                            <strong>Work Start:</strong>{" "}
                                            {workStart ? formatTime(workStart.startTime) : "-"}
                                        </li>
                                        <li>
                                            <strong>Break Start:</strong>{" "}
                                            {breakStart
                                                ? (breakStart.breakStart
                                                    ? formatTime(breakStart.breakStart)
                                                    : formatTime(breakStart.startTime))
                                                : "-"}
                                        </li>
                                        <li>
                                            <strong>Break End:</strong>{" "}
                                            {breakEnd
                                                ? (breakEnd.breakEnd
                                                    ? formatTime(breakEnd.breakEnd)
                                                    : formatTime(breakEnd.startTime))
                                                : "-"}
                                        </li>
                                        <li>
                                            <strong>Work End:</strong>{" "}
                                            {workEnd
                                                ? formatTime(workEnd.endTime || workEnd.startTime)
                                                : "-"}
                                        </li>
                                    </ul>
                                )}
                            </div>

                            {/* Notizen-Bereich */}
                            <div className="daily-note-section">
                                {noteEditVisibility[isoDay] ? (
                                    <>
                    <textarea
                        value={dailyNotes[isoDay] || ''}
                        onChange={(e) =>
                            setDailyNotes({ ...dailyNotes, [isoDay]: e.target.value })
                        }
                    />
                                        <button onClick={() => {
                                            handleSaveNote(isoDay);
                                            // Schließen
                                            setNoteEditVisibility({ ...noteEditVisibility, [isoDay]: false });
                                        }}>
                                            Speichern
                                        </button>
                                    </>
                                ) : (
                                    <>
                                        <button
                                            onClick={() =>
                                                setNoteEditVisibility({ ...noteEditVisibility, [isoDay]: true })
                                            }
                                        >
                                            {dailyNotes[isoDay] ? "Notizen bearbeiten" : "Notizen hinzufügen"}
                                        </button>
                                        {dailyNotes[isoDay] && (
                                            <div className="note-display">
                                                {dailyNotes[isoDay]}
                                            </div>
                                        )}
                                    </>
                                )}
                            </div>

                            {/* Korrektur-Button */}
                            <div className="correction-button-row">
                                <button onClick={() => openCorrectionModal(dayDate)}>
                                    Korrektur anfragen
                                </button>
                            </div>
                        </div>
                    );
                })}
            </div>
        </section>
    );
};

HourlyWeekOverview.propTypes = {
    t: PropTypes.func.isRequired,
    userProfile: PropTypes.object,
    allEntries: PropTypes.array.isRequired,
    dailyNotes: PropTypes.object.isRequired,
    noteEditVisibility: PropTypes.object.isRequired,
    setDailyNotes: PropTypes.func.isRequired,
    setNoteEditVisibility: PropTypes.func.isRequired,
    handleSaveNote: PropTypes.func.isRequired,
    openCorrectionModal: PropTypes.func.isRequired,
    selectedMonday: PropTypes.instanceOf(Date).isRequired,
    setSelectedMonday: PropTypes.func.isRequired,
    weeklyTotalMins: PropTypes.number.isRequired,
    monthlyTotalMins: PropTypes.number.isRequired,
    handleManualPunch: PropTypes.func.isRequired,
    punchMessage: PropTypes.string
};

export default HourlyWeekOverview;
