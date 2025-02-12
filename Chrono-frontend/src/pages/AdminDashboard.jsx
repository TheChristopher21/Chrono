// src/components/AdminDashboard.jsx
import React, { useState, useEffect } from 'react';
import Navbar from '../components/Navbar';
import api from '../utils/api';
import VacationCalendarAdmin from '../components/VacationCalendarAdmin';
import { useAuth } from '../context/AuthContext';
import '../styles/AdminDashboard.css';

// Liefert den Montag der Woche des übergebenen Datums
function getMondayOfWeek(date) {
    const copy = new Date(date);
    const day = copy.getDay();
    const diff = day === 0 ? 6 : day - 1;
    copy.setDate(copy.getDate() - diff);
    copy.setHours(0, 0, 0, 0);
    return copy;
}

// Formatiert ein Datum (als String oder Objekt) ins Format "DD-MM-YYYY"
function formatDate(dateStrOrObj) {
    const date = new Date(dateStrOrObj);
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();
    return `${day}-${month}-${year}`;
}

// Fügt dem Datum eine bestimmte Anzahl Tage hinzu
function addDays(date, days) {
    const d = new Date(date);
    d.setDate(d.getDate() + days);
    return d;
}

function getStatusLabel(punchOrder) {
    switch (punchOrder) {
        case 1: return "Work Start";
        case 2: return "Break Start";
        case 3: return "Break End";
        case 4: return "Work End";
        default: return "";
    }
}

function formatTime(dateStr) {
    const d = new Date(dateStr);
    return isNaN(d.getTime())
        ? '-'
        : d.toLocaleTimeString("de-DE", {
            hour: '2-digit',
            minute: '2-digit',
            timeZone: 'Europe/Berlin'
        });
}

function formatLocalDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

// Hilfsfunktion: Gibt die Minuten seit Mitternacht eines Zeitstempels zurück
function getMinutesSinceMidnight(datetimeStr) {
    const d = new Date(datetimeStr);
    return d.getHours() * 60 + d.getMinutes();
}

// Wandelt einen Zeitstring ("HH:mm" oder "HH:mm:ss") in Minuten um
function parseTimeToMinutes(timeStr) {
    const parts = timeStr.split(':');
    const hours = parseInt(parts[0], 10);
    const minutes = parseInt(parts[1], 10);
    return hours * 60 + minutes;
}

/**
 * Berechnet den Unterschied (in Minuten) zwischen der tatsächlich gearbeiteten Zeit
 * (Work End minus Work Start minus Pausendauer) und der erwarteten Arbeitszeit.
 * Gibt einen numerischen Wert zurück.
 */
function computeDailyDiffValue(dayEntries, expectedWorkHours) {
    const entryStart = dayEntries.find(e => e.punchOrder === 1);
    const entryBreakStart = dayEntries.find(e => e.punchOrder === 2);
    const entryBreakEnd = dayEntries.find(e => e.punchOrder === 3);
    const entryEnd = dayEntries.find(e => e.punchOrder === 4);
    if (entryStart && entryBreakStart && entryBreakEnd && entryEnd) {
        const workStartMins = getMinutesSinceMidnight(entryStart.startTime);
        const workEndMins = getMinutesSinceMidnight(entryEnd.endTime);
        const workDuration = workEndMins - workStartMins;
        const breakStartMins = entryBreakStart.breakStart
            ? parseTimeToMinutes(entryBreakStart.breakStart)
            : getMinutesSinceMidnight(entryBreakStart.startTime);
        const breakEndMins = entryBreakEnd.breakEnd
            ? parseTimeToMinutes(entryBreakEnd.breakEnd)
            : getMinutesSinceMidnight(entryBreakEnd.startTime);
        const breakDuration = breakEndMins - breakStartMins;
        const actualWorked = workDuration - breakDuration;
        const expectedMinutes = expectedWorkHours * 60;
        return actualWorked - expectedMinutes;
    }
    return 0;
}

/**
 * Gibt den formatierten Differenz-String für einen Tag zurück (z. B. "+20 min")
 */
function computeDailyDiff(dayEntries, expectedWorkHours) {
    const diff = computeDailyDiffValue(dayEntries, expectedWorkHours);
    const sign = diff >= 0 ? '+' : '';
    return `${sign}${Math.round(diff)} min`;
}

/**
 * Ermittelt anhand des individuellen Wochenplans (userConfig.weeklySchedule)
 * den erwarteten Stundenwert für einen bestimmten Tag.
 * Falls kein individueller Plan existiert, wird der Standard (defaultExpectedHours) verwendet.
 * Der Plan wird zyklisch anhand eines festen Epochendatums (1. Jan 2020) ermittelt.
 * Wichtig: Wird ein Wert von 0 konfiguriert (z. B. Sonntags 0 Stunden), soll 0 zurückgegeben werden.
 */
function getExpectedHoursForDay(dayObj, userConfig, defaultExpectedHours) {
    let expectedForDay = defaultExpectedHours;
    if (userConfig && userConfig.weeklySchedule && userConfig.scheduleCycle) {
        const epoch = new Date(2020, 0, 1); // 1. Januar 2020
        const diffWeeks = Math.floor((dayObj - epoch) / (7 * 24 * 60 * 60 * 1000));
        const cycleIndex = diffWeeks % userConfig.scheduleCycle; // Index im Zyklus
        const dayOfWeek = dayObj.toLocaleDateString('en-US', { weekday: 'long' }).toLowerCase();
        if (Array.isArray(userConfig.weeklySchedule) && userConfig.weeklySchedule[cycleIndex]) {
            const scheduleValue = Number(userConfig.weeklySchedule[cycleIndex][dayOfWeek]);
            if (!isNaN(scheduleValue)) {
                expectedForDay = scheduleValue;
            }
        }
    }
    return expectedForDay;
}

const AdminDashboard = () => {
    const { currentUser } = useAuth();
    const [adminPassword, setAdminPassword] = useState('');
    const [allTracks, setAllTracks] = useState([]);
    const [allVacations, setAllVacations] = useState([]);
    const [allCorrections, setAllCorrections] = useState([]);
    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));
    const [expandedUsers, setExpandedUsers] = useState({});
    // Neuer State für alle Benutzer
    const [users, setUsers] = useState([]);

    // State für das Edit‑Modal (Time Tracking)
    const [editModalVisible, setEditModalVisible] = useState(false);
    const [editDate, setEditDate] = useState(null);
    const [editTargetUsername, setEditTargetUsername] = useState('');
    const [editData, setEditData] = useState({
        workStart: '',
        breakStart: '',
        breakEnd: '',
        workEnd: '',
        adminPassword: '',
        userPassword: ''
    });

    // defaultExpectedHours als Fallback (8 Stunden)
    const defaultExpectedHours = 8;

    // Hole alle Benutzer
    async function fetchUsers() {
        try {
            const res = await api.get('/api/admin/users');
            setUsers(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error("Error loading users", err);
        }
    }

    useEffect(() => {
        fetchUsers();
        fetchAllTracks();
        fetchAllVacations();
        fetchAllCorrections();
    }, []);

    async function fetchAllTracks() {
        try {
            const res = await api.get('/api/admin/timetracking/all');
            const validEntries = (res.data || []).filter(e => [1, 2, 3, 4].includes(e.punchOrder));
            console.log("DEBUG: Loaded time entries:", validEntries);
            setAllTracks(validEntries);
        } catch (err) {
            console.error("Error loading time entries", err);
        }
    }

    async function fetchAllVacations() {
        try {
            const res = await api.get('/api/vacation/all');
            console.log("DEBUG: Loaded vacation requests:", res.data);
            setAllVacations(res.data || []);
        } catch (err) {
            console.error("Error loading vacation requests", err);
        }
    }

    async function fetchAllCorrections() {
        try {
            const res = await api.get('/api/correction/all');
            console.log("DEBUG: Loaded correction requests:", res.data);
            setAllCorrections(res.data || []);
        } catch (err) {
            console.error("Error loading correction requests", err);
        }
    }

    // Wochen-Navigation
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
    const weekDates = Array.from({ length: 7 }, (_, i) => addDays(selectedMonday, i));
    const weekStrs = weekDates.map(d => d.toLocaleDateString());

    const filteredTracks = allTracks.filter(t => {
        const ds = new Date(t.startTime).toLocaleDateString();
        return weekStrs.includes(ds);
    });

    // Gruppiere Einträge pro Benutzer
    const userGroups = filteredTracks.reduce((acc, track) => {
        const uname = track.username;
        if (!acc[uname]) acc[uname] = [];
        acc[uname].push(track);
        return acc;
    }, {});

    function toggleUserExpand(username) {
        setExpandedUsers(prev => ({ ...prev, [username]: !prev[username] }));
    }

    // Vacation Actions
    async function handleApproveVacation(id) {
        if (!adminPassword) {
            alert("Please enter admin password!");
            return;
        }
        try {
            await api.post(`/api/vacation/approve/${id}`, null, { params: { adminPassword } });
            fetchAllVacations();
        } catch (err) {
            console.error("Error approving vacation", err);
        }
    }

    async function handleDenyVacation(id) {
        try {
            await api.post(`/api/vacation/deny/${id}`);
            fetchAllVacations();
        } catch (err) {
            console.error("Error denying vacation", err);
        }
    }

    // Correction Request Actions
    async function handleApproveCorrection(id) {
        if (!adminPassword) {
            alert("Please enter admin password!");
            return;
        }
        try {
            await api.post(`/api/correction/approve/${id}`, null, { params: { adminPassword } });
            fetchAllCorrections();
        } catch (err) {
            console.error("Error approving correction", err);
            alert("Error approving correction: " + err.message);
        }
    }

    async function handleDenyCorrection(id) {
        try {
            await api.post(`/api/correction/deny/${id}`);
            fetchAllCorrections();
        } catch (err) {
            console.error("Error denying correction", err);
            alert("Error denying correction: " + err.message);
        }
    }

    // Edit Modal (Time Tracking)
    function openEditModal(targetUsername, date, entries) {
        const defaultTime = "00:00";
        const workStartEntry = entries.find(e => e.punchOrder === 1);
        const breakStartEntry = entries.find(e => e.punchOrder === 2);
        const breakEndEntry = entries.find(e => e.punchOrder === 3);
        const workEndEntry = entries.find(e => e.punchOrder === 4);

        const workStartVal = workStartEntry ? formatTime(workStartEntry.startTime) : defaultTime;
        const breakStartVal = breakStartEntry
            ? (breakStartEntry.breakStart ? formatTime(breakStartEntry.breakStart) : formatTime(breakStartEntry.startTime))
            : defaultTime;
        const breakEndVal = breakEndEntry
            ? (breakEndEntry.breakEnd ? formatTime(breakEndEntry.breakEnd) : formatTime(breakEndEntry.startTime))
            : defaultTime;
        const workEndVal = workEndEntry ? formatTime(workEndEntry.endTime) : defaultTime;

        setEditTargetUsername(targetUsername);
        setEditDate(date);
        setEditData({
            workStart: workStartVal,
            breakStart: breakStartVal,
            breakEnd: breakEndVal,
            workEnd: workEndVal,
            adminPassword: '',
            userPassword: ''
        });
        setEditModalVisible(true);
    }

    async function handleEditSubmit(e) {
        e.preventDefault();
        if (!editDate) {
            alert("No valid date selected for editing.");
            return;
        }
        const formattedDate = formatLocalDate(editDate);
        const params = {
            targetUsername: editTargetUsername,
            date: formattedDate,
            workStart: editData.workStart,
            breakStart: editData.breakStart,
            breakEnd: editData.breakEnd,
            workEnd: editData.workEnd,
            adminUsername: currentUser.username,
            userPassword: editData.userPassword
        };
        if (editTargetUsername !== currentUser.username) {
            params.adminPassword = editData.adminPassword;
        } else {
            // Self‑Edit: Beide Passwörter müssen gleich sein
            params.adminPassword = editData.userPassword;
        }
        console.log("DEBUG: Submitting edit with parameters:", params);
        try {
            const res = await api.put('/api/timetracking/editDay', null, { params });
            console.log("DEBUG: Edit response:", res.data);
            setEditModalVisible(false);
            fetchAllTracks();
        } catch (err) {
            console.error("Error editing time tracking entries", err);
            alert("Edit failed: " + err.message);
        }
    }

    function handleEditInputChange(e) {
        const { name, value } = e.target;
        setEditData(prev => ({ ...prev, [name]: value }));
    }

    console.log("DEBUG: User groups:", userGroups);

    return (
        <div className="admin-dashboard">
            <Navbar />
            <header className="dashboard-header">
                <h2>Admin-Dashboard (Weekly View)</h2>
                <p>Logged in as: {currentUser?.username}</p>
                <div className="admin-password">
                    <label>Admin Password:</label>
                    <input
                        type="password"
                        value={adminPassword}
                        onChange={(e) => setAdminPassword(e.target.value)}
                    />
                </div>
            </header>
            <div className="dashboard-content">
                {/* Linke Spalte: Time Tracking & Vacation */}
                <div className="left-column">
                    <section className="week-section">
                        <h3>Time Tracking - Current Week</h3>
                        <div className="week-navigation">
                            <button onClick={handlePrevWeek}>← Previous Week</button>
                            <input
                                type="date"
                                onChange={handleWeekJump}
                                value={selectedMonday.toISOString().slice(0, 10)}
                            />
                            <button onClick={handleNextWeek}>Next Week →</button>
                        </div>
                        {Object.keys(userGroups).length === 0 ? (
                            <p>No entries in this week</p>
                        ) : (
                            <div className="admin-user-groups">
                                {Object.keys(userGroups).map(username => {
                                    // Hole die Benutzerkonfiguration aus dem State "users"
                                    const userConfig = users.find(u => u.username === username) || {};
                                    // Gruppiere Tracking-Einträge für diesen Benutzer nach Datum
                                    const dayMap = {};
                                    userGroups[username].forEach(t => {
                                        const ds = new Date(t.startTime).toLocaleDateString();
                                        if (!dayMap[ds]) dayMap[ds] = [];
                                        dayMap[ds].push(t);
                                    });
                                    // Berechne die Gesamtdifferenz für diesen Benutzer über die aktuelle Woche:
                                    let userTotalDiff = 0;
                                    weekDates.forEach(wd => {
                                        const ds = wd.toLocaleDateString();
                                        const expectedForDay = getExpectedHoursForDay(wd, userConfig, defaultExpectedHours);
                                        if (dayMap[ds]) {
                                            userTotalDiff += computeDailyDiffValue(dayMap[ds], expectedForDay);
                                        }
                                    });
                                    const absTotal = Math.abs(userTotalDiff);
                                    const totalHours = Math.floor(absTotal / 60);
                                    const totalMinutes = absTotal % 60;
                                    const totalSign = userTotalDiff >= 0 ? '+' : '-';

                                    const isExpanded = !!expandedUsers[username];
                                    const userColor =
                                        (userGroups[username][0].color &&
                                            /^#[0-9A-Fa-f]{6}$/.test(userGroups[username][0].color))
                                            ? userGroups[username][0].color
                                            : '#007BFF';
                                    return (
                                        <div key={username} className="admin-user-block">
                                            <div
                                                className="admin-user-header"
                                                onClick={() => toggleUserExpand(username)}
                                                style={{ backgroundColor: userColor }}
                                            >
                                                <h4 style={{ color: '#fff' }}>{username}</h4>
                                                <button className="edit-button">{isExpanded ? '–' : '+'}</button>
                                            </div>
                                            {isExpanded && (
                                                <div className="admin-week-display">
                                                    <div className="user-total-diff">
                                                        Gesamt: {totalSign}{totalHours} Std {totalMinutes} min
                                                    </div>
                                                    {weekDates.map((wd, i) => {
                                                        const ds = wd.toLocaleDateString();
                                                        const dayEntries = dayMap[ds] || [];
                                                        const expectedForDay = getExpectedHoursForDay(wd, userConfig, defaultExpectedHours);
                                                        const dailyDiff = dayEntries.length >= 4 ? computeDailyDiff(dayEntries, expectedForDay) : "";
                                                        return (
                                                            <div key={i} className="admin-day-card">
                                                                <div className="admin-day-card-header">
                                                                    <strong>
                                                                        {wd.toLocaleDateString('de-DE', { weekday: 'long' })}, {ds}
                                                                        <span className="expected-hours">(Soll: {expectedForDay} Std)</span>
                                                                    </strong>
                                                                    {dailyDiff && <span className="daily-diff">({dailyDiff})</span>}
                                                                    {dayEntries.length > 0 && (
                                                                        <button onClick={() => openEditModal(username, wd, dayEntries)} className="edit-day-button">
                                                                            Edit
                                                                        </button>
                                                                    )}
                                                                </div>
                                                                <div className="admin-day-content">
                                                                    <div className="time-entries">
                                                                        {dayEntries.length === 0 ? (
                                                                            <p className="no-entries">No entries</p>
                                                                        ) : (
                                                                            <ul className="time-entry-list">
                                                                                {dayEntries.sort((a, b) => a.punchOrder - b.punchOrder).map(e => {
                                                                                    let displayTime = "-";
                                                                                    if (e.punchOrder === 1)
                                                                                        displayTime = formatTime(e.startTime);
                                                                                    else if (e.punchOrder === 2)
                                                                                        displayTime = e.breakStart ? formatTime(e.breakStart) : formatTime(e.startTime);
                                                                                    else if (e.punchOrder === 3)
                                                                                        displayTime = e.breakEnd ? formatTime(e.breakEnd) : formatTime(e.startTime);
                                                                                    else if (e.punchOrder === 4)
                                                                                        displayTime = formatTime(e.endTime);
                                                                                    return (
                                                                                        <li key={e.id}>
                                                                                            <span className="entry-label">{getStatusLabel(e.punchOrder)}:</span> {displayTime}
                                                                                        </li>
                                                                                    );
                                                                                })}
                                                                            </ul>
                                                                        )}
                                                                    </div>
                                                                </div>
                                                            </div>
                                                        );
                                                    })}
                                                </div>
                                            )}
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </section>
                    <section className="vacation-section">
                        <h3>Vacation Requests (All Users)</h3>
                        {allVacations.length === 0 ? (
                            <p>No vacation requests found</p>
                        ) : (
                            <ul className="vacation-list">
                                {allVacations.map(v => (
                                    <li key={v.id} className="vacation-item">
                    <span className="vacation-text">
                      <strong>{v.username}</strong>: {formatDate(v.startDate)} - {formatDate(v.endDate)} {v.approved ? <span className="approved">Approved</span> : v.denied ? <span className="denied">Denied</span> : <span className="pending">Pending</span>}
                    </span>
                                        {!v.approved && !v.denied && (
                                            <span className="vacation-buttons">
                        <button className="approve-btn" onClick={() => handleApproveVacation(v.id)}>Approve</button>
                        <button className="reject-btn" onClick={() => handleDenyVacation(v.id)}>Reject</button>
                      </span>
                                        )}
                                    </li>
                                ))}
                            </ul>
                        )}
                    </section>
                </div>
                {/* Rechte Spalte: Correction Requests */}
                <div className="right-column">
                    <section className="correction-section">
                        <h3>Correction Requests for {formatDate(new Date())}</h3>
                        {allCorrections.length === 0 ? (
                            <p>No correction requests found for {formatDate(new Date())}</p>
                        ) : (
                            <ul className="correction-list">
                                {allCorrections.map(corr => (
                                    <li key={corr.id} className="correction-item">
                                        <div className="correction-info">
                                            <strong>{corr.username}</strong> on {new Date(corr.desiredStartTime).toLocaleDateString()}:
                                            <br />Work Start: {corr.workStart}
                                            <br />Break Start: {corr.breakStart}
                                            <br />Break End: {corr.breakEnd}
                                            <br />Work End: {corr.workEnd}
                                            <br />Reason: {corr.reason}
                                            <br />Status: {corr.approved ? "Approved" : corr.denied ? "Denied" : "Pending"}
                                        </div>
                                        <div className="correction-buttons">
                                            <button onClick={() => handleApproveCorrection(corr.id)}>Accept</button>
                                            <button onClick={() => handleDenyCorrection(corr.id)}>Reject</button>
                                        </div>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </section>
                </div>
            </div>
            <div className="full-width-calendar">
                <h4>Vacation Calendar</h4>
                <VacationCalendarAdmin vacationRequests={allVacations.filter(v => v.approved)} />
            </div>
            {/* Edit Modal for Time Tracking */}
            {editModalVisible && (
                <div className="modal-overlay">
                    <div className="modal-content">
                        <h3>Edit Time Tracking for {editDate.toLocaleDateString('de-DE')}</h3>
                        <form onSubmit={handleEditSubmit}>
                            <div className="form-group">
                                <label>Work Start:</label>
                                <input type="time" name="workStart" value={editData.workStart} onChange={handleEditInputChange} required />
                            </div>
                            <div className="form-group">
                                <label>Break Start:</label>
                                <input type="time" name="breakStart" value={editData.breakStart} onChange={handleEditInputChange} required />
                            </div>
                            <div className="form-group">
                                <label>Break End:</label>
                                <input type="time" name="breakEnd" value={editData.breakEnd} onChange={handleEditInputChange} required />
                            </div>
                            <div className="form-group">
                                <label>Work End:</label>
                                <input type="time" name="workEnd" value={editData.workEnd} onChange={handleEditInputChange} required />
                            </div>
                            {editTargetUsername !== currentUser.username && (
                                <div className="form-group">
                                    <label>Admin Password:</label>
                                    <input type="password" name="adminPassword" value={editData.adminPassword || ''} onChange={handleEditInputChange} required />
                                </div>
                            )}
                            <div className="form-group">
                                <label>User Password:</label>
                                <input type="password" name="userPassword" value={editData.userPassword} onChange={handleEditInputChange} required />
                            </div>
                            <div className="modal-buttons">
                                <button type="submit">Confirm Edit</button>
                                <button type="button" onClick={() => setEditModalVisible(false)}>Cancel</button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default AdminDashboard;
