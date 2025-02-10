import React, { useState, useEffect } from 'react';
import Navbar from '../components/Navbar';
import api from '../utils/api';
import VacationCalendarAdmin from '../components/VacationCalendarAdmin';
import { useAuth } from '../context/AuthContext';
import '../styles/AdminDashboard.css';

// Helper Functions
function getMondayOfWeek(date) {
    const copy = new Date(date);
    const day = copy.getDay();
    const diff = day === 0 ? 6 : day - 1;
    copy.setDate(copy.getDate() - diff);
    copy.setHours(0, 0, 0, 0);
    return copy;
}

function addDays(date, days) {
    const d = new Date(date);
    d.setDate(d.getDate() + days);
    return d;
}

function calcDayMinutes(entries) {
    if (entries.length < 2) return null;
    const sorted = [...entries].sort((a, b) => a.punchOrder - b.punchOrder);
    if (!sorted[0].endTime || !sorted[sorted.length - 1].endTime) return null;
    const start = new Date(sorted[0].startTime);
    const end = new Date(sorted[sorted.length - 1].endTime);
    return (end - start) / 60000;
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
    return isNaN(d.getTime()) ? '-' : d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

/**
 * Filtert Correction-Requests für den gegebenen Nutzer und Tag.
 */
function getCorrectionsForDay(username, day, corrections) {
    const dayLocal = day.toLocaleDateString('de-DE');
    return corrections.filter(req => {
        if (!req.desiredStart) {
            console.error("Correction request missing desiredStart", req);
            return false;
        }
        const reqDate = new Date(req.desiredStart);
        if (isNaN(reqDate.getTime())) {
            console.error("Invalid desiredStart in correction request", req);
            return false;
        }
        const reqLocalDate = reqDate.toLocaleDateString('de-DE');
        return (
            req.username.toLowerCase() === username.toLowerCase() &&
            reqLocalDate === dayLocal
        );
    });
}

const AdminDashboard = () => {
    const { currentUser } = useAuth();
    const [adminPassword, setAdminPassword] = useState('');
    const [allTracks, setAllTracks] = useState([]);
    const [allVacations, setAllVacations] = useState([]);
    const [correctionRequests, setCorrectionRequests] = useState([]);
    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));
    const expectedWorkHours = 8;
    const [expandedUsers, setExpandedUsers] = useState({});

    useEffect(() => {
        fetchAllTracks();
        fetchAllVacations();
        fetchAllCorrections();
    }, []);

    async function fetchAllTracks() {
        try {
            const res = await api.get('/api/admin/timetracking/all');
            const validEntries = (res.data || []).filter(e => [1, 2, 3, 4].includes(e.punchOrder));
            console.log("Loaded time entries:", validEntries);
            setAllTracks(validEntries);
        } catch (err) {
            console.error("Error loading time entries", err);
        }
    }

    async function fetchAllVacations() {
        try {
            const res = await api.get('/api/vacation/all');
            console.log("Loaded vacation requests:", res.data);
            setAllVacations(res.data || []);
        } catch (err) {
            console.error("Error loading vacation requests", err);
        }
    }

    async function fetchAllCorrections() {
        try {
            const res = await api.get('/api/correction/open');
            console.log("Loaded correction requests:", res.data);
            setCorrectionRequests(res.data || []);
        } catch (err) {
            console.error("Error loading correction requests", err);
        }
    }

    const sortedVacations = [...allVacations].sort((a, b) => new Date(a.startDate) - new Date(b.startDate));

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

    const userGroups = filteredTracks.reduce((acc, track) => {
        const uname = track.username;
        if (!acc[uname]) acc[uname] = [];
        acc[uname].push(track);
        return acc;
    }, {});

    function toggleUserExpand(username) {
        setExpandedUsers(prev => ({ ...prev, [username]: !prev[username] }));
    }

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

    async function handleApproveCorrection(id) {
        if (!adminPassword) {
            alert("Please enter admin password!");
            return;
        }
        try {
            const response = await api.post(`/api/correction/approve/${id}`, null, { params: { adminPassword } });
            console.log("Approve correction response:", response.data);
            fetchAllCorrections();
            fetchAllTracks();
        } catch (err) {
            console.error("Error approving correction", err);
        }
    }

    async function handleDenyCorrection(id) {
        try {
            const response = await api.post(`/api/correction/deny/${id}`);
            console.log("Deny correction response:", response.data);
            fetchAllCorrections();
        } catch (err) {
            console.error("Error denying correction", err);
        }
    }

    function calcDiffIndicator(totalMin) {
        if (totalMin == null) return null;
        const expected = expectedWorkHours * 60;
        const diff = totalMin - expected;
        if (Math.abs(diff) < 10) return null;
        return diff > 0
            ? { type: 'over', text: `+${diff.toFixed(2)} Min` }
            : { type: 'under', text: `-${Math.abs(diff).toFixed(2)} Min` };
    }

    console.log("User groups:", userGroups);
    console.log("Correction requests:", correctionRequests);

    return (
        <div className="admin-dashboard fancy-bg">
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

            {/* Time Tracking Section */}
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
                            const dayMap = {};
                            userGroups[username].forEach(t => {
                                const ds = new Date(t.startTime).toLocaleDateString();
                                if (!dayMap[ds]) dayMap[ds] = [];
                                dayMap[ds].push(t);
                            });
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
                                            {weekDates.map((wd, i) => {
                                                const ds = wd.toLocaleDateString();
                                                const dayEntries = dayMap[ds] || [];
                                                const totalMin = calcDayMinutes(dayEntries);
                                                const diffInd = calcDiffIndicator(totalMin);
                                                const correctionsForDay = getCorrectionsForDay(username, wd, correctionRequests);
                                                console.log(`User ${username} – ${ds}: Found ${correctionsForDay.length} correction(s).`);
                                                return (
                                                    <div key={i} className="admin-day-card">
                                                        <div className="admin-day-card-header">
                                                            <strong>{wd.toLocaleDateString('de-DE', { weekday: 'long' })}, {ds}</strong>
                                                            {diffInd && (
                                                                <span className={`indicator ${diffInd.type}`}>
                                  {diffInd.text}
                                </span>
                                                            )}
                                                        </div>
                                                        {/* Anzeige der aktuellen (Original) Zeiten */}
                                                        <div className="admin-day-content">
                                                            <div className="time-entries">
                                                                {dayEntries.length === 0 ? (
                                                                    <p className="no-entries">No entries</p>
                                                                ) : (
                                                                    <ul className="time-entry-list">
                                                                        {dayEntries
                                                                            .sort((a, b) => a.punchOrder - b.punchOrder)
                                                                            .map(e => (
                                                                                <li key={e.id}>
                                                                                    <span className="entry-label">{getStatusLabel(e.punchOrder)}:</span> {e.punchOrder === 4 ? formatTime(e.endTime) : formatTime(e.startTime)}
                                                                                </li>
                                                                            ))}
                                                                    </ul>
                                                                )}
                                                            </div>
                                                            {correctionsForDay.length > 0 && (
                                                                <div className="corrections-container">
                                                                    {correctionsForDay.map(corr => (
                                                                        <div key={corr.id} className="correction-item">
                                                                            {/* Wenn die Correction noch nicht genehmigt ist, wird nur die angefragte Zeit angezeigt */}
                                                                            {!corr.approved ? (
                                                                                <div className="correction-details one-column">
                                                                                    <div className="corrected-details">
                                                                                        <p><strong>Work Start:</strong> {corr.workStart ? corr.workStart : '-'}</p>
                                                                                        <p><strong>Break Start:</strong> {corr.breakStart ? corr.breakStart : '-'}</p>
                                                                                        <p><strong>Break End:</strong> {corr.breakEnd ? corr.breakEnd : '-'}</p>
                                                                                        <p><strong>Work End:</strong> {corr.workEnd ? corr.workEnd : '-'}</p>
                                                                                    </div>
                                                                                </div>
                                                                            ) : (
                                                                                // Wenn genehmigt, dann zeige links die angefragten (korrigierten) Zeiten und rechts die Originalzeiten
                                                                                <div className="correction-details two-column">
                                                                                    <div className="corrected-details">
                                                                                        <p><strong>Korrigiert - Work Start:</strong> {corr.workStart ? corr.workStart : '-'}</p>
                                                                                        <p><strong>Korrigiert - Break Start:</strong> {corr.breakStart ? corr.breakStart : '-'}</p>
                                                                                        <p><strong>Korrigiert - Break End:</strong> {corr.breakEnd ? corr.breakEnd : '-'}</p>
                                                                                        <p><strong>Korrigiert - Work End:</strong> {corr.workEnd ? corr.workEnd : '-'}</p>
                                                                                    </div>
                                                                                    <div className="original-details">
                                                                                        <p><strong>Original - Work Start:</strong> {corr.originalWorkStart ? corr.originalWorkStart : (corr.originalStart ? formatTime(corr.originalStart) : '-')}</p>
                                                                                        <p><strong>Original - Break Start:</strong> {corr.originalBreakStart ? corr.originalBreakStart : '-'}</p>
                                                                                        <p><strong>Original - Break End:</strong> {corr.originalBreakEnd ? corr.originalBreakEnd : '-'}</p>
                                                                                        <p><strong>Original - Work End:</strong> {corr.originalWorkEnd ? corr.originalWorkEnd : (corr.originalEnd ? formatTime(corr.originalEnd) : 'running')}</p>
                                                                                    </div>
                                                                                </div>
                                                                            )}
                                                                            <p><strong>Overall Start:</strong> {corr.originalStart ? formatTime(corr.originalStart) : '-'}</p>
                                                                            <p><strong>Overall End:</strong> {corr.originalEnd ? formatTime(corr.originalEnd) : 'running'}</p>
                                                                            <p><strong>Reason:</strong> {corr.reason}</p>
                                                                            <p><strong>Status:</strong> {corr.status ? corr.status : "Open"}</p>
                                                                            {/* Nur anzeigen, wenn noch nicht genehmigt */}
                                                                            {!corr.approved && (
                                                                                <div className="correction-buttons">
                                                                                    <button className="accept-button" onClick={() => handleApproveCorrection(corr.id)}>Approve</button>
                                                                                    <button className="reject-button" onClick={() => handleDenyCorrection(corr.id)}>Reject</button>
                                                                                </div>
                                                                            )}
                                                                        </div>
                                                                    ))}
                                                                </div>
                                                            )}


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

            {/* Vacation Section */}
            <section className="vacation-section">
                <h3>Vacation Requests (All Users)</h3>
                {sortedVacations.length === 0 ? (
                    <p>No vacation requests found</p>
                ) : (
                    <ul className="vacation-list">
                        {sortedVacations.map(v => (
                            <li key={v.id} className="vacation-item">
                                <strong>{v.username}</strong>: {v.startDate} - {v.endDate}{' '}
                                {v.approved ? <span className="approved">Approved</span>
                                    : v.denied ? <span className="denied">Denied</span>
                                        : <span className="pending">Pending</span>}
                                {!v.approved && !v.denied && (
                                    <span className="vacation-buttons">
                    <button onClick={() => handleApproveVacation(v.id)}>Approve</button>
                    <button onClick={() => handleDenyVacation(v.id)}>Reject</button>
                  </span>
                                )}
                            </li>
                        ))}
                    </ul>
                )}
                <div className="calendar-section">
                    <h4>Vacation Calendar</h4>
                    <VacationCalendarAdmin vacationRequests={sortedVacations.filter(v => v.approved)} />
                </div>
            </section>
        </div>
    );
};

export default AdminDashboard;
