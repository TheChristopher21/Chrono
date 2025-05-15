import React from 'react';
import PropTypes from 'prop-types';

import {
    addDays,
    formatLocalDate,
    formatDate,
    formatTime,
    getMondayOfWeek
} from './hourDashUtils';

const HourlyWeekOverview = ({
                                t,
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
    const weekDates = selectedMonday
        ? Array.from({ length: 7 }, (_, i) => addDays(selectedMonday, i))
        : [];

    const weekStrs = weekDates.map(d => formatLocalDate(d));
    const weeklyEntries = allEntries.filter(track => {
        const localDate = track.startTime.slice(0, 10);
        return weekStrs.includes(localDate);
    });

    const weeklyDayMap = {};
    weeklyEntries.forEach(entry => {
        const ds = entry.startTime.slice(0, 10);
        if (!weeklyDayMap[ds]) weeklyDayMap[ds] = [];
        weeklyDayMap[ds].push(entry);
    });

    // Summenanzeige
    const weeklyHrs = Math.floor(weeklyTotalMins / 60);
    const weeklyRemMins = weeklyTotalMins % 60;
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
            <h3>{t("weeklyOverview")}</h3>

            {punchMessage && <div className="punch-message">{punchMessage}</div>}

            <div className="punch-section">
                <h4>{t("manualPunchTitle")}</h4>
                <button onClick={handleManualPunch}>
                    {t("manualPunchButton")}
                </button>
            </div>

            <div className="week-navigation">
                <button onClick={handlePrevWeek}>← {t("prevWeek")}</button>
                <input
                    type="date"
                    onChange={handleWeekJump}
                    value={formatLocalDate(selectedMonday)}
                />
                <button onClick={handleNextWeek}>{t("nextWeek")} →</button>
            </div>

            <div className="weekly-monthly-totals">
                <p>
                    {t("weeklyHours")}: {weeklyHrs}h {weeklyRemMins}min
                </p>
                <p>
                    {t("monthlyHours")}: {monthlyHrs}h {monthlyRemMins}min
                </p>
            </div>

            <div className="week-display">
                {weekDates.map((dayObj, index) => {
                    const isoDay = formatLocalDate(dayObj);
                    const dayEntries = weeklyDayMap[isoDay] || [];
                    const dayName = dayObj.toLocaleDateString('de-DE', { weekday: 'long' });
                    const formattedDay = formatDate(isoDay);

                    // Sortierung nach punchOrder
                    dayEntries.sort((a, b) => a.punchOrder - b.punchOrder);

                    // PunchOrders
                    const workStart  = dayEntries.find(e => e.punchOrder === 1);
                    const breakStart = dayEntries.find(e => e.punchOrder === 2);
                    const breakEnd   = dayEntries.find(e => e.punchOrder === 3);
                    const workEnd    = dayEntries.find(e => e.punchOrder === 4);

                    return (
                        <div key={index} className="week-day-card">
                            <div className="week-day-header">
                                <strong>
                                    {dayName}, {formattedDay}
                                </strong>
                            </div>

                            <div className="week-day-content">
                                {dayEntries.length === 0 ? (
                                    <p>{t("noEntries")}</p>
                                ) : (
                                    <ul>
                                        <li>
                                            <strong>{t("workStart")}:</strong>{" "}
                                            {workStart ? formatTime(workStart.startTime) : "-"}
                                        </li>
                                        <li>
                                            <strong>{t("breakStart")}:</strong>{" "}
                                            {breakStart
                                                ? breakStart.breakStart
                                                    ? formatTime(breakStart.breakStart)
                                                    : formatTime(breakStart.startTime)
                                                : "-"}
                                        </li>
                                        <li>
                                            <strong>{t("breakEnd")}:</strong>{" "}
                                            {breakEnd
                                                ? breakEnd.breakEnd
                                                    ? formatTime(breakEnd.breakEnd)
                                                    : formatTime(breakEnd.startTime)
                                                : "-"}
                                        </li>
                                        <li>
                                            <strong>{t("workEnd")}:</strong>{" "}
                                            {workEnd
                                                ? formatTime(workEnd.endTime || workEnd.startTime)
                                                : "-"}
                                        </li>
                                    </ul>
                                )}
                            </div>

                            <div className="daily-note-section">
                                {noteEditVisibility[isoDay] ? (
                                    <>
                    <textarea
                        value={dailyNotes[isoDay] || ""}
                        onChange={(e) =>
                            setDailyNotes({ ...dailyNotes, [isoDay]: e.target.value })
                        }
                    />
                                        <button
                                            onClick={() => {
                                                handleSaveNote(isoDay);
                                                setNoteEditVisibility({
                                                    ...noteEditVisibility,
                                                    [isoDay]: false
                                                });
                                            }}
                                        >
                                            {t("userManagement.button.save")}
                                        </button>
                                    </>
                                ) : (
                                    <>
                                        <button
                                            onClick={() =>
                                                setNoteEditVisibility({
                                                    ...noteEditVisibility,
                                                    [isoDay]: true
                                                })
                                            }
                                        >
                                            {dailyNotes[isoDay] ? t("editNotes") : t("addNotes")}
                                        </button>
                                        {dailyNotes[isoDay] && (
                                            <div className="note-display">{dailyNotes[isoDay]}</div>
                                        )}
                                    </>
                                )}
                            </div>

                            <div className="correction-button-row">
                                <button onClick={() => openCorrectionModal(dayObj)}>
                                    {t("submitCorrectionRequest")}
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
