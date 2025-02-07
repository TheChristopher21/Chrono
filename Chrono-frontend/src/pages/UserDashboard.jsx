import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../utils/api';
import Navbar from '../components/Navbar';
import VacationCalendar from '../components/VacationCalendar';
import '../styles/UserDashboard.css';

/**
 * Gruppiert Einträge nach Datum
 */
const groupEntriesByDate = (entries) => {
    return entries.reduce((acc, entry) => {
        const dateStr = new Date(entry.startTime).toLocaleDateString();
        if (!acc[dateStr]) {
            acc[dateStr] = [];
        }
        acc[dateStr].push(entry);
        return acc;
    }, {});
};

/**
 * Gruppiert Urlaubseinträge nach "Woche"
 */
const groupVacationsByWeek = (vacations) => {
    return vacations.reduce((acc, vac) => {
        const weekKey = new Date(vac.startDate).toLocaleDateString();
        if (!acc[weekKey]) acc[weekKey] = [];
        acc[weekKey].push(vac);
        return acc;
    }, {});
};

const UserDashboard = () => {
    const { currentUser } = useAuth();

    // Statt timeHistory nutzen wir direkt groupedEntries
    const [groupedEntries, setGroupedEntries] = useState({});

    // Vacation
    const [vacationForm, setVacationForm] = useState({ startDate: '', endDate: '' });
    const [vacationRequests, setVacationRequests] = useState([]);

    // Correction (Modal)
    const [correctionModalVisible, setCorrectionModalVisible] = useState(false);
    const [correctionForm, setCorrectionForm] = useState({
        desiredStart: '',
        desiredEnd: '',
        reason: ''
    });

    // Expand states
    const [expandedDates, setExpandedDates] = useState({});
    const [expandedVacationWeeks, setExpandedVacationWeeks] = useState({});

    // Harte Annahme, z.B. 8h (kein dynamisches WorkConfig)
    const [expectedWorkHours] = useState(8);

    // 1) Time History laden
    const fetchTimeHistory = async () => {
        if (!currentUser || !currentUser.username) return;
        try {
            const res = await api.get(`/api/timetracking/history?username=${currentUser.username}`);
            // Wir setzen nur groupedEntries, kein "timeHistory" mehr.
            setGroupedEntries(groupEntriesByDate(res.data));
        } catch (err) {
            console.error('Error fetching time history', err);
        }
    };

    // 2) Vacation Requests laden
    const fetchVacationRequests = async () => {
        try {
            const res = await api.get('/api/vacation/my');
            setVacationRequests(res.data);
        } catch (err) {
            console.error('Error fetching vacation requests', err);
        }
    };

    // useEffect: Sobald currentUser da ist, lade timeHistory & vacations
    useEffect(() => {
        if (currentUser) {
            fetchTimeHistory();
            fetchVacationRequests();
        }
    }, [currentUser]);

    /*
     * Vier Methoden für das 4-Schritte-Einstempeln
     */
    const handleWorkStart = async () => {
        try {
            await api.post(`/api/timetracking/work-start?username=${currentUser.username}`);
            fetchTimeHistory();
        } catch (err) {
            console.error("Error in Work Start", err);
            alert(err.response?.data?.message || "Work Start failed");
        }
    };

    const handleBreakStart = async () => {
        try {
            await api.post(`/api/timetracking/break-start?username=${currentUser.username}`);
            fetchTimeHistory();
        } catch (err) {
            console.error("Error in Break Start", err);
            alert(err.response?.data?.message || "Break Start failed");
        }
    };

    const handleBreakEnd = async () => {
        try {
            await api.post(`/api/timetracking/break-end?username=${currentUser.username}`);
            fetchTimeHistory();
        } catch (err) {
            console.error("Error in Break End", err);
            alert(err.response?.data?.message || "Break End failed");
        }
    };

    const handleWorkEnd = async () => {
        try {
            await api.post(`/api/timetracking/work-end?username=${currentUser.username}`);
            fetchTimeHistory();
        } catch (err) {
            console.error("Error in Work End", err);
            alert(err.response?.data?.message || "Work End failed");
        }
    };

    // Correction Modal toggeln
    const toggleCorrectionModal = () => {
        setCorrectionModalVisible(!correctionModalVisible);
    };

    const handleCorrectionFormChange = (e) => {
        setCorrectionForm({ ...correctionForm, [e.target.name]: e.target.value });
    };

    const handleCorrectionSubmit = async (e) => {
        e.preventDefault();
        try {
            await api.post('/api/correction/create', null, {
                params: {
                    username: currentUser.username,
                    desiredStart: correctionForm.desiredStart,
                    desiredEnd: correctionForm.desiredEnd,
                    reason: correctionForm.reason
                }
            });
            alert("Correction request submitted");
            setCorrectionForm({ desiredStart: '', desiredEnd: '', reason: '' });
            setCorrectionModalVisible(false);
            await fetchTimeHistory();
        } catch (err) {
            console.error("Error submitting correction request", err);
        }
    };

    // Vacation Submit
    const handleVacationSubmit = async (e) => {
        e.preventDefault();
        if (new Date(vacationForm.startDate) > new Date(vacationForm.endDate)) {
            alert("Start date cannot be after End date.");
            return;
        }
        try {
            await api.post('/api/vacation/create', null, {
                params: {
                    username: currentUser.username,
                    startDate: vacationForm.startDate,
                    endDate: vacationForm.endDate
                }
            });
            alert("Vacation request submitted");
            setVacationForm({ startDate: '', endDate: '' });
            await fetchVacationRequests();
        } catch (err) {
            console.error("Error submitting vacation request", err);
        }
    };

    const toggleDateGroup = (dateStr) => {
        setExpandedDates(prev => ({ ...prev, [dateStr]: !prev[dateStr] }));
    };

    // Arbeitszeit berechnen (Start->End)
    const calculateWorkTime = (entries) => {
        if (entries.length < 2) return null;
        const sorted = [...entries].sort((a, b) => a.punchOrder - b.punchOrder);

        if (!sorted[0].endTime || !sorted[sorted.length - 1].endTime) return null;

        const start = new Date(sorted[0].startTime);
        const end = new Date(sorted[sorted.length - 1].endTime);
        return (end - start) / 60000; // Minuten
    };

    const getWorkTimeIndicator = (totalMinutes) => {
        if (totalMinutes === null) return null;
        const expectedMinutes = expectedWorkHours * 60;
        const diff = totalMinutes - expectedMinutes;
        if (Math.abs(diff) < 10) return null; // 10 Min Toleranz
        return diff > 0
            ? { type: 'over', value: `+${Math.round(diff)} min` }
            : { type: 'under', value: `${Math.round(-diff)} min` };
    };

    // Urlaub gruppieren
    const groupedVacations = groupVacationsByWeek(vacationRequests);

    return (
        <div className="user-dashboard">
            <Navbar />

            <header className="dashboard-header">
                <h2>User Dashboard</h2>
                <div className="personal-info">
                    <p><strong>Username:</strong> {currentUser?.username}</p>
                    <p><strong>Expected Work Hours:</strong> {expectedWorkHours}h</p>
                </div>
            </header>

            {/* =====================
          Time Tracking
         ===================== */}
            <section className="time-tracking-section">
                <h3>Time Tracking</h3>
                <div className="tracking-buttons">
                    {/* Nur die 4-Button-Lösung */}
                    <button onClick={handleWorkStart}>Work Start</button>
                    <button onClick={handleBreakStart}>Break Start</button>
                    <button onClick={handleBreakEnd}>Break End</button>
                    <button onClick={handleWorkEnd}>Work End</button>

                    {/* Correction-Antrag */}
                    <button onClick={toggleCorrectionModal}>Request Correction</button>
                </div>

                <div className="time-history">
                    <h4>Your Time Entries by Day:</h4>
                    {Object.keys(groupedEntries).length === 0 ? (
                        <p>No time entries found.</p>
                    ) : (
                        <div className="date-groups">
                            {Object.keys(groupedEntries).map(dateStr => {
                                const entries = groupedEntries[dateStr];
                                const totalMinutes = calculateWorkTime(entries);
                                const indicator = getWorkTimeIndicator(totalMinutes);
                                return (
                                    <div key={dateStr} className="date-group">
                                        <div
                                            className="date-group-header"
                                            onClick={() => toggleDateGroup(dateStr)}
                                        >
                                            <h5>{dateStr}</h5>
                                            <button>{expandedDates[dateStr] ? '-' : '+'}</button>
                                        </div>
                                        {expandedDates[dateStr] && (
                                            <ul>
                                                {entries.map(entry => (
                                                    <li key={entry.id} className="track-item">
                                                        <span className="entry-label">Start:</span>{' '}
                                                        {new Date(entry.startTime).toLocaleString()}
                                                        <br />
                                                        <span className="entry-label">End:</span>{' '}
                                                        {entry.endTime
                                                            ? new Date(entry.endTime).toLocaleString()
                                                            : 'Ongoing'}
                                                        <br />
                                                        <span className="entry-label">Status:</span>{' '}
                                                        {entry.color}
                                                    </li>
                                                ))}
                                                {indicator && (
                                                    <li className="work-indicator-container">
                            <span className={`work-indicator ${indicator.type}`}>
                              {indicator.value}
                            </span>
                                                    </li>
                                                )}
                                            </ul>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>
            </section>

            {/* =====================
          Vacation
         ===================== */}
            <section className="vacation-section">
                <h3>Vacation Request</h3>
                <form onSubmit={handleVacationSubmit} className="form-vacation">
                    <div className="form-group">
                        <label>Start Date:</label>
                        <input
                            type="date"
                            name="startDate"
                            value={vacationForm.startDate}
                            onChange={(e) =>
                                setVacationForm({ ...vacationForm, startDate: e.target.value })
                            }
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label>End Date:</label>
                        <input
                            type="date"
                            name="endDate"
                            value={vacationForm.endDate}
                            onChange={(e) =>
                                setVacationForm({ ...vacationForm, endDate: e.target.value })
                            }
                            required
                        />
                    </div>
                    <button type="submit">Submit Vacation</button>
                </form>

                <div className="vacation-history">
                    <h4>Your Vacation Requests (grouped by week):</h4>
                    {vacationRequests.length === 0 ? (
                        <p>No vacation requests found.</p>
                    ) : (
                        Object.keys(groupVacationsByWeek(vacationRequests))
                            .sort()
                            .map(weekKey => (
                                <div key={weekKey} className="vacation-week-group">
                                    <div
                                        className="vacation-week-header"
                                        onClick={() =>
                                            setExpandedVacationWeeks(prev => ({
                                                ...prev,
                                                [weekKey]: !prev[weekKey]
                                            }))
                                        }
                                    >
                                        <h5>{weekKey}</h5>
                                        <button>{expandedVacationWeeks[weekKey] ? '-' : '+'}</button>
                                    </div>
                                    {expandedVacationWeeks[weekKey] && (
                                        <ul>
                                            {groupVacationsByWeek(vacationRequests)[weekKey].map(vac => (
                                                <li key={vac.id}>
                                                    <span className="entry-label">From:</span> {vac.startDate}
                                                    <br />
                                                    <span className="entry-label">To:</span> {vac.endDate}
                                                    <br />
                                                    <span className="entry-label">Status:</span>{' '}
                                                    <span
                                                        className={`status-badge ${
                                                            vac.approved
                                                                ? 'approved'
                                                                : vac.denied
                                                                    ? 'denied'
                                                                    : 'pending'
                                                        }`}
                                                    >
                            {vac.approved
                                ? 'Approved'
                                : vac.denied
                                    ? 'Denied'
                                    : 'Pending'}
                          </span>
                                                </li>
                                            ))}
                                        </ul>
                                    )}
                                </div>
                            ))
                    )}
                </div>

                <div className="calendar-section">
                    <h4>Vacation Calendar</h4>
                    <VacationCalendar vacationRequests={vacationRequests.filter(vac => vac.approved)} />
                </div>
            </section>

            {/* =====================
          Correction Modal
         ===================== */}
            {correctionModalVisible && (
                <div className="modal-overlay">
                    <div className="modal-content">
                        <h3>Request Time Correction</h3>
                        <form onSubmit={handleCorrectionSubmit} className="form-correction">
                            <div className="form-group">
                                <label>Desired Start:</label>
                                <input
                                    type="datetime-local"
                                    name="desiredStart"
                                    value={correctionForm.desiredStart}
                                    onChange={handleCorrectionFormChange}
                                    required
                                />
                            </div>
                            <div className="form-group">
                                <label>Desired End:</label>
                                <input
                                    type="datetime-local"
                                    name="desiredEnd"
                                    value={correctionForm.desiredEnd}
                                    onChange={handleCorrectionFormChange}
                                    required
                                />
                            </div>
                            <div className="form-group">
                                <label>Reason:</label>
                                <input
                                    type="text"
                                    name="reason"
                                    value={correctionForm.reason}
                                    onChange={handleCorrectionFormChange}
                                    required
                                />
                            </div>
                            <div className="modal-buttons">
                                <button type="submit">Submit Correction Request</button>
                                <button
                                    type="button"
                                    onClick={() => setCorrectionModalVisible(false)}
                                >
                                    Cancel
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default UserDashboard;
