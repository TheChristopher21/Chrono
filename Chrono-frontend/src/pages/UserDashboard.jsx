import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../utils/api';
import Navbar from '../components/Navbar';
import VacationCalendar from '../components/VacationCalendar';
import '../styles/UserDashboard.css';

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
    const [timeHistory, setTimeHistory] = useState([]);
    const [groupedEntries, setGroupedEntries] = useState({});
    const [vacationForm, setVacationForm] = useState({ startDate: '', endDate: '' });
    const [vacationRequests, setVacationRequests] = useState([]);
    const [correctionModalVisible, setCorrectionModalVisible] = useState(false);
    const [correctionForm, setCorrectionForm] = useState({
        desiredStart: '',
        desiredEnd: '',
        reason: ''
    });
    const [expandedDates, setExpandedDates] = useState({});
    const [expandedVacationWeeks, setExpandedVacationWeeks] = useState({});
    const [expectedWorkHours, setExpectedWorkHours] = useState(8);

    const fetchTimeHistory = async () => {
        try {
            const res = await api.get(`/api/timetracking/history?username=${currentUser.username}`);
            setTimeHistory(res.data);
            setGroupedEntries(groupEntriesByDate(res.data));
        } catch (err) {
            console.error("Error fetching time history", err);
        }
    };

    const fetchVacationRequests = async () => {
        try {
            const res = await api.get('/api/vacation/my');
            setVacationRequests(res.data);
        } catch (err) {
            console.error("Error fetching vacation requests", err);
        }
    };

    const fetchWorkConfig = async () => {
        try {
            const res = await api.get(`/api/admin/users/getWorkConfig/${currentUser.id}`);
            if (res.data.dailyWorkHours) {
                setExpectedWorkHours(res.data.dailyWorkHours);
            }
        } catch (err) {
            console.error("Error fetching work config", err);
        }
    };

    useEffect(() => {
        if (currentUser) {
            fetchTimeHistory();
            fetchVacationRequests();
            fetchWorkConfig();
        }
    }, [currentUser]);

    const hasActivePunch = () => {
        return timeHistory.some(entry => !entry.endTime);
    };

    const handlePunchIn = async () => {
        if (hasActivePunch()) {
            alert("You have already punched in. Please punch out first.");
            return;
        }
        try {
            await api.post(`/api/timetracking/punch-in?username=${currentUser.username}`);
            await fetchTimeHistory();
        } catch (err) {
            console.error("Error punching in", err);
        }
    };

    const handlePunchOut = async () => {
        if (!hasActivePunch()) {
            alert("You are not punched in.");
            return;
        }
        try {
            await api.post(`/api/timetracking/punch-out?username=${currentUser.username}`);
            await fetchTimeHistory();
        } catch (err) {
            console.error("Error punching out", err);
        }
    };

    const toggleDateGroup = (dateStr) => {
        setExpandedDates(prev => ({ ...prev, [dateStr]: !prev[dateStr] }));
    };

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

    const calculateWorkTime = (entries) => {
        if (entries.length < 2) return null;
        const sorted = [...entries].sort((a, b) => a.punchOrder - b.punchOrder);
        if (!sorted[0].endTime || !sorted[sorted.length - 1].endTime) return null;
        const start = new Date(sorted[0].startTime);
        const end = new Date(sorted[sorted.length - 1].endTime);
        return (end - start) / 60000; // in Minuten
    };

    const getWorkTimeIndicator = (totalMinutes) => {
        if (totalMinutes === null) return null;
        const expectedMinutes = expectedWorkHours * 60;
        const diff = totalMinutes - expectedMinutes;
        if (Math.abs(diff) < 10) return null;
        return diff > 0
            ? { type: 'over', value: `+${Math.round(diff)} min` }
            : { type: 'under', value: `${Math.round(-diff)} min` };
    };

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
            <section className="time-tracking-section">
                <h3>Time Tracking</h3>
                <div className="tracking-buttons">
                    <button onClick={handlePunchIn}>Punch In</button>
                    <button onClick={handlePunchOut}>Punch Out</button>
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
                                        <div className="date-group-header" onClick={() => toggleDateGroup(dateStr)}>
                                            <h5>{dateStr}</h5>
                                            <button>{expandedDates[dateStr] ? '-' : '+'}</button>
                                        </div>
                                        {expandedDates[dateStr] && (
                                            <ul>
                                                {entries.map(entry => (
                                                    <li key={entry.id} className="track-item">
                                                        <span className="entry-label">Start:</span> {new Date(entry.startTime).toLocaleString()}<br />
                                                        <span className="entry-label">End:</span> {entry.endTime ? new Date(entry.endTime).toLocaleString() : 'Ongoing'}<br />
                                                        <span className="entry-label">Status:</span> {entry.color}
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
            <section className="vacation-section">
                <h3>Vacation Request</h3>
                <form onSubmit={handleVacationSubmit} className="form-vacation">
                    <div className="form-group">
                        <label>Start Date:</label>
                        <input
                            type="date"
                            name="startDate"
                            value={vacationForm.startDate}
                            onChange={(e) => setVacationForm({ ...vacationForm, startDate: e.target.value })}
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label>End Date:</label>
                        <input
                            type="date"
                            name="endDate"
                            value={vacationForm.endDate}
                            onChange={(e) => setVacationForm({ ...vacationForm, endDate: e.target.value })}
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
                        Object.keys(groupedVacations)
                            .sort()
                            .map(weekKey => (
                                <div key={weekKey} className="vacation-week-group">
                                    <div className="vacation-week-header" onClick={() => setExpandedVacationWeeks(prev => ({ ...prev, [weekKey]: !prev[weekKey] }))}>
                                        <h5>{weekKey}</h5>
                                        <button>{expandedVacationWeeks[weekKey] ? '-' : '+'}</button>
                                    </div>
                                    {expandedVacationWeeks[weekKey] && (
                                        <ul>
                                            {groupedVacations[weekKey].map(vac => (
                                                <li key={vac.id}>
                                                    <span className="entry-label">From:</span> {vac.startDate}<br />
                                                    <span className="entry-label">To:</span> {vac.endDate}<br />
                                                    <span className="entry-label">Status:</span>{' '}
                                                    <span className={`status-badge ${vac.approved ? 'approved' : vac.denied ? 'denied' : 'pending'}`}>
                                                        {vac.approved ? 'Approved' : vac.denied ? 'Denied' : 'Pending'}
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
                                <button type="button" onClick={() => setCorrectionModalVisible(false)}>Cancel</button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default UserDashboard;
