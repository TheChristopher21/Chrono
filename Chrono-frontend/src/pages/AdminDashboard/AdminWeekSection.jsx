// AdminWeekSection.jsx
import 'react';
import '../../styles/AdminDashboardScoped.css';

import {
    formatLocalDateYMD,
    formatDate,
    formatTime,
    getStatusLabel,
    getExpectedHoursForDay,
    computeDailyDiffValue,
    computeDailyDiff, isLateTime,
} from './adminDashboardUtils';
import PropTypes from "prop-types";

const AdminWeekSection = ({
                              t,
                              weekDates,
                              selectedMonday,
                              handlePrevWeek,
                              handleNextWeek,
                              handleWeekJump,
                              allTracks,
                              users,
                              expandedUsers,
                              setExpandedUsers,
                              defaultExpectedHours,
                              openEditModal,
                              openPrintUserModal,
                              weeklyBalances = []

                          }) => {


    const weekStrs = weekDates.map(d => formatLocalDateYMD(d));

    // Filter Einträge der aktuellen Woche
    const filteredTracks = allTracks.filter(track => {
        const localDate = track.startTime.slice(0, 10);
        return weekStrs.includes(localDate);
    });

    // Gruppiere Einträge nach Username
    const userGroups = filteredTracks.reduce((acc, track) => {
        const uname = track.username;
        if (!acc[uname]) acc[uname] = [];
        acc[uname].push(track);
        return acc;
    }, {});

    function toggleUserExpand(username) {
        setExpandedUsers(prev => ({ ...prev, [username]: !prev[username] }));
    }

    return (
        <div className="admin-dashboard scoped-dashboard">

        <section className="week-section">
            <h3>{t('adminDashboard.timeTrackingCurrentWeek')}</h3>
            <div className="week-navigation">
                <button onClick={handlePrevWeek}>← {t('adminDashboard.prevWeek')}</button>
                <input
                    type="date"
                    onChange={handleWeekJump}
                    value={formatLocalDateYMD(selectedMonday)}
                />
                <button onClick={handleNextWeek}>{t('adminDashboard.nextWeek')} →</button>
            </div>

            {Object.keys(userGroups).length === 0 ? (
                <p>{t('adminDashboard.noEntriesThisWeek')}</p>
            ) : (
                <div className="admin-user-groups">
                    {Object.keys(userGroups).map(username => {
                        const userConfig = users.find(u => u.username === username) || {};
                        const dayMap = {};
                        userGroups[username].forEach(entry => {
                            const ds = formatLocalDateYMD(new Date(entry.startTime));
                            if (!dayMap[ds]) dayMap[ds] = [];
                            dayMap[ds].push(entry);
                        });

                        // Wochen-Diff
                        let userTotalDiff = 0;
                        weekDates.forEach(wd => {
                            const isoDay = formatLocalDateYMD(wd);
                            const dayEntries = dayMap[isoDay] || [];
                            const expectedForDay = getExpectedHoursForDay(wd, userConfig, defaultExpectedHours);
                            if (dayEntries.length > 0) {
                                userTotalDiff += computeDailyDiffValue(dayEntries, expectedForDay, userConfig.isHourly);
                            }
                        });
                        const absTotal = Math.abs(userTotalDiff);
                        const totalHours = Math.floor(absTotal / 60);
                        const totalMinutes = absTotal % 60;
                        const totalSign = userTotalDiff >= 0 ? '+' : '-';
                        const trackingEntry = weeklyBalances.find(wb => wb.username === username);
                        const trackingBalance = trackingEntry?.trackingBalance ?? 0;
                        const trackingSign = trackingBalance >= 0 ? "+" : "-";
                        const absTrack = Math.abs(trackingBalance);
                        const trackingHours = Math.floor(absTrack / 60);
                        const trackingMins = absTrack % 60;

                        // Gesamt-Saldo


                        let userColor = '#007BFF';
                        const isExpanded = !!expandedUsers[username];
                        // Versuche, userConfig.color oder so zu nehmen, falls es existiert
                        if (userConfig.color && /^#[0-9A-Fa-f]{6}$/.test(userConfig.color)) {
                            userColor = userConfig.color;
                        }


                        return (
                            <div key={username} className="admin-user-block">
                                <div
                                    className="admin-user-header"
                                    onClick={() => toggleUserExpand(username)}
                                    style={{ backgroundColor: userColor }}
                                >
                                    <h4 style={{ color: '#fff' }}>{username}</h4>
                                    <button className="edit-button">
                                        {isExpanded ? '–' : '+'}
                                    </button>
                                </div>

                                {isExpanded && (
                                    <div className="admin-week-display">
                                        <div className="user-total-diff">
                                            <strong>{t('adminDashboard.total')} (Woche):</strong>{' '}
                                            {totalSign}{totalHours} {t('adminDashboard.hours')} {totalMinutes} {t('adminDashboard.minutes')}
                                        </div>
                                        <div className="user-weekly-balance">
                                            <strong>Tracking-Bilanz (inkl. Urlaub/Überstunden):</strong>{' '}
                                            {trackingSign}{trackingHours}h {trackingMins}min
                                        </div>



                                        {weekDates.map((wd, i) => {
                                            const isoDay = formatLocalDateYMD(wd);
                                            const dayEntries = dayMap[isoDay] || [];
                                            const expectedForDay = getExpectedHoursForDay(wd, userConfig, defaultExpectedHours);

                                            if (dayEntries.length === 0) {
                                                return (
                                                    <div key={i} className="admin-day-card">
                                                        <div className="admin-day-card-header">
                                                            <strong>
                                                                {wd.toLocaleDateString('de-DE', { weekday: 'long' })},{' '}
                                                                {formatDate(wd)}
                                                            </strong>
                                                        </div>
                                                        <div className="admin-day-content">
                                                            <p className="no-entries">Keine Einträge</p>
                                                        </div>
                                                    </div>
                                                );
                                            }

                                            if (userConfig.isHourly) {
                                                return (
                                                    <div key={i} className="admin-day-card">
                                                        <div className="admin-day-card-header">
                                                            <strong>
                                                                {wd.toLocaleDateString('de-DE', { weekday: 'long' })},{' '}
                                                                {formatDate(wd)}
                                                            </strong>
                                                        </div>
                                                        <div className="admin-day-content">
                                                            <ul className="time-entry-list">
                                                                {dayEntries
                                                                    .sort((a, b) => a.punchOrder - b.punchOrder)
                                                                    .map(e => {
                                                                        let displayTime = "-";
                                                                        if (e.punchOrder === 1) {
                                                                            displayTime = formatTime(e.startTime);
                                                                        } else if (e.punchOrder === 2) {
                                                                            displayTime = e.breakStart
                                                                                ? formatTime(e.breakStart)
                                                                                : formatTime(e.startTime);
                                                                        } else if (e.punchOrder === 3) {
                                                                            displayTime = e.breakEnd
                                                                                ? formatTime(e.breakEnd)
                                                                                : formatTime(e.startTime);
                                                                        } else if (e.punchOrder === 4) {
                                                                            displayTime = formatTime(e.endTime);
                                                                        }
                                                                        return (
                                                                            <li key={e.id} className={isLateTime(displayTime) ? 'late-time' : ''}>
                                                                                <span className="entry-label">{getStatusLabel(e.punchOrder)}:</span> {displayTime}
                                                                            </li>

                                                                        );
                                                                    })}
                                                            </ul>
                                                        </div>
                                                    </div>
                                                );
                                            } else {
                                                // Festangestellte
                                                const dailyDiff = dayEntries.length >= 4
                                                    ? computeDailyDiff(dayEntries, expectedForDay, false)
                                                    : '';

                                                return (
                                                    <div key={i} className="admin-day-card">
                                                        <div className="admin-day-card-header">
                                                            <strong>
                                                                {wd.toLocaleDateString('de-DE', { weekday: 'long' })},{' '}
                                                                {formatDate(wd)}
                                                                <span className="expected-hours">
                                  ({t('adminDashboard.expected')}: {expectedForDay}h)
                                </span>
                                                            </strong>
                                                            {dailyDiff && (
                                                                <span className="daily-diff">({dailyDiff})</span>
                                                            )}
                                                            {dayEntries.length > 0 && (
                                                                <button
                                                                    onClick={() => openEditModal(username, wd, dayEntries)}
                                                                    className="edit-day-button"
                                                                >
                                                                    {t('adminDashboard.editButton')}
                                                                </button>
                                                            )}
                                                        </div>
                                                        <div className="admin-day-content">
                                                            <ul className="time-entry-list">
                                                                {dayEntries
                                                                    .sort((a, b) => a.punchOrder - b.punchOrder)
                                                                    .map(e => {
                                                                        let displayTime = '-';
                                                                        if (e.punchOrder === 1) {
                                                                            displayTime = formatTime(e.startTime);
                                                                        } else if (e.punchOrder === 2) {
                                                                            displayTime = e.breakStart
                                                                                ? formatTime(e.breakStart)
                                                                                : formatTime(e.startTime);
                                                                        } else if (e.punchOrder === 3) {
                                                                            displayTime = e.breakEnd
                                                                                ? formatTime(e.breakEnd)
                                                                                : formatTime(e.startTime);
                                                                        } else if (e.punchOrder === 4) {
                                                                            displayTime = formatTime(e.endTime);
                                                                        }
                                                                        return (
                                                                            <li key={e.id} className={isLateTime(displayTime) ? 'late-time' : ''}>
                                                                                <span className="entry-label">{getStatusLabel(e.punchOrder)}:</span> {displayTime}
                                                                            </li>

                                                                        );
                                                                    })}
                                                            </ul>
                                                        </div>
                                                    </div>
                                                );
                                            }
                                        })}

                                        <button
                                            className="print-times-button"
                                            onClick={() => openPrintUserModal(username)}
                                        >
                                            Zeiten Drucken
                                        </button>
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </div>
            )}
        </section>
        </div>
    );
};
AdminWeekSection.propTypes = {
    t: PropTypes.func.isRequired,                // Übersetzungsfunktion
    currentUser: PropTypes.shape({
        username: PropTypes.string
        // ... weitere Felder, falls nötig
    }),
    weekDates: PropTypes.arrayOf(PropTypes.instanceOf(Date)).isRequired,
    weeklyBalances: PropTypes.array.isRequired,
    selectedMonday: PropTypes.instanceOf(Date).isRequired,
    handlePrevWeek: PropTypes.func.isRequired,
    handleNextWeek: PropTypes.func.isRequired,
    handleWeekJump: PropTypes.func.isRequired,
    allTracks: PropTypes.array.isRequired,
    users: PropTypes.array.isRequired,
    expandedUsers: PropTypes.object.isRequired,
    setExpandedUsers: PropTypes.func.isRequired,
    defaultExpectedHours: PropTypes.number.isRequired,
    openEditModal: PropTypes.func.isRequired,
    openPrintUserModal: PropTypes.func.isRequired
};
export default AdminWeekSection;
