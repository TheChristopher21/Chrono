// src/pages/UserDashboard.jsx
import  { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../utils/api';
import Navbar from '../components/Navbar';
import VacationCalendar from '../components/VacationCalendar';
import '../styles/UserDashboard.css';

const UserDashboard = () => {
    const { currentUser, setCurrentUser } = useAuth();
    const [timeHistory, setTimeHistory] = useState([]);
    const [vacationForm, setVacationForm] = useState({ startDate: '', endDate: '' });
    const [vacationRequests, setVacationRequests] = useState([]);
    const [personalData, setPersonalData] = useState({
        firstName: currentUser?.firstName || '',
        lastName: currentUser?.lastName || '',
        email: currentUser?.email || ''
    });

    // Correction Modal State
    const [showCorrectionModal, setShowCorrectionModal] = useState(false);
    const [selectedEntry, setSelectedEntry] = useState(null);
    const [correctionForm, setCorrectionForm] = useState({ desiredStart: '', desiredEnd: '', reason: '' });

    // Hilfsfunktion: Formatierung für datetime-local
    const formatDateTimeLocal = (date) => {
        if (!date) return '';
        const d = new Date(date);
        const pad = (n) => (n < 10 ? '0' + n : n);
        return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
    };

    const fetchTimeHistory = async () => {
        try {
            const res = await api.get(`/api/timetracking/history?username=${currentUser.username}`);
            setTimeHistory(res.data);
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

    const fetchPersonalData = async () => {
        try {
            const res = await api.get('/api/auth/me');
            setPersonalData({
                firstName: res.data.firstName,
                lastName: res.data.lastName,
                email: res.data.email
            });
        } catch (err) {
            console.error("Error fetching personal data", err);
        }
    };

    useEffect(() => {
        if (currentUser) {
            fetchTimeHistory();
            fetchVacationRequests();
            fetchPersonalData();
        }
    }, [currentUser]);

    // Automatischer Reminder: Wenn heute ein genehmigter Vacation Request existiert, soll ein Banner angezeigt werden
    const isOnVacation = () => {
        const today = new Date();
        today.setHours(0,0,0,0);
        return vacationRequests.some(vac => {
            if (vac.approved) {
                const start = new Date(vac.startDate);
                const end = new Date(vac.endDate);
                start.setHours(0,0,0,0);
                end.setHours(0,0,0,0);
                return today >= start && today <= end;
            }
            return false;
        });
    };

    const handlePunchIn = async () => {
        try {
            await api.post(`/api/timetracking/punch-in?username=${currentUser.username}`);
            fetchTimeHistory();
        } catch (err) {
            console.error("Error punching in", err);
        }
    };

    const handlePunchOut = async () => {
        try {
            await api.post(`/api/timetracking/punch-out?username=${currentUser.username}`);
            fetchTimeHistory();
        } catch (err) {
            console.error("Error punching out", err);
        }
    };

    // Öffnet das Correction Modal und füllt Standardwerte
    const openCorrectionModal = (entry) => {
        setSelectedEntry(entry);
        setCorrectionForm({
            desiredStart: formatDateTimeLocal(entry.startTime),
            desiredEnd: entry.endTime ? formatDateTimeLocal(entry.endTime) : '',
            reason: ''
        });
        setShowCorrectionModal(true);
    };

    const closeCorrectionModal = () => {
        setShowCorrectionModal(false);
        setSelectedEntry(null);
        setCorrectionForm({ desiredStart: '', desiredEnd: '', reason: '' });
    };

    const handleCorrectionSubmit = async (e) => {
        e.preventDefault();
        try {
            await api.post('/api/correction/create', null, {
                params: {
                    username: currentUser.username,
                    timeTrackingId: selectedEntry ? selectedEntry.id : null,
                    desiredStart: correctionForm.desiredStart,
                    desiredEnd: correctionForm.desiredEnd,
                    reason: correctionForm.reason
                }
            });
            alert("Correction request submitted");
            closeCorrectionModal();
            fetchTimeHistory();
        } catch (err) {
            console.error("Error submitting correction request", err);
        }
    };

    const handleVacationSubmit = async (e) => {
        e.preventDefault();
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
            fetchVacationRequests();
        } catch (err) {
            console.error("Error submitting vacation request", err);
        }
    };

    const handlePersonalDataUpdate = async (e) => {
        e.preventDefault();
        try {
            const res = await api.put('/api/user/update', {
                username: currentUser.username,
                firstName: personalData.firstName,
                lastName: personalData.lastName,
                email: personalData.email
            });
            alert("Profile updated");
            setCurrentUser(res.data);
        } catch (err) {
            console.error("Error updating personal data", err);
        }
    };

    return (
        <div className="user-dashboard">
            <Navbar />
            <header className="dashboard-header">
                <h2>User Dashboard</h2>
                <div className="personal-info">
                    <p><strong>Username:</strong> {currentUser?.username}</p>
                </div>
            </header>

            {isOnVacation() && (
                <div className="vacation-reminder">
                    <p>You are currently on vacation. Enjoy your break!</p>
                </div>
            )}

            <section className="time-tracking-section">
                <h3>Time Tracking</h3>
                <div className="tracking-buttons">
                    <button onClick={handlePunchIn}>Punch In</button>
                    <button onClick={handlePunchOut}>Punch Out</button>
                </div>
                <div className="time-history">
                    <h4>Your Time Entries:</h4>
                    {timeHistory.length === 0 ? (
                        <p>No time entries found.</p>
                    ) : (
                        <ul>
                            {timeHistory.map(entry => (
                                <li key={entry.id}>
                                    <span className="entry-label">Start:</span> {new Date(entry.startTime).toLocaleString()}<br />
                                    <span className="entry-label">End:</span> {entry.endTime ? new Date(entry.endTime).toLocaleString() : 'Ongoing'}<br />
                                    <span className="entry-label">Status:</span> {entry.color}
                                    {entry.endTime && !entry.corrected && (
                                        <button className="btn-correct" onClick={() => openCorrectionModal(entry)}>Correct</button>
                                    )}
                                </li>
                            ))}
                        </ul>
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
                    <h4>Your Vacation Requests:</h4>
                    {vacationRequests.length === 0 ? (
                        <p>No vacation requests found.</p>
                    ) : (
                        <ul>
                            {vacationRequests.map(vac => (
                                <li key={vac.id}>
                                    <span className="entry-label">From:</span> {vac.startDate}<br />
                                    <span className="entry-label">To:</span> {vac.endDate}<br />
                                    <span className="entry-label">Status:</span> <span className={`status-badge ${vac.approved ? 'approved' : vac.denied ? 'denied' : 'pending'}`}>
                    {vac.approved ? 'Approved' : vac.denied ? 'Denied' : 'Pending'}
                  </span>
                                </li>
                            ))}
                        </ul>
                    )}
                </div>
                {/* Abwesenheitskalender */}
                <div className="calendar-section">
                    <h4>Vacation Calendar</h4>
                    <VacationCalendar vacationRequests={vacationRequests.filter(vac => vac.approved)} />
                </div>
            </section>

            <section className="personal-data-section">
                <h3>Update Personal Data</h3>
                <form onSubmit={handlePersonalDataUpdate} className="form-personal">
                    <div className="form-group">
                        <label>First Name:</label>
                        <input
                            type="text"
                            name="firstName"
                            value={personalData.firstName}
                            onChange={(e) => setPersonalData({ ...personalData, firstName: e.target.value })}
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label>Last Name:</label>
                        <input
                            type="text"
                            name="lastName"
                            value={personalData.lastName}
                            onChange={(e) => setPersonalData({ ...personalData, lastName: e.target.value })}
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label>Email:</label>
                        <input
                            type="email"
                            name="email"
                            value={personalData.email}
                            onChange={(e) => setPersonalData({ ...personalData, email: e.target.value })}
                            required
                        />
                    </div>
                    <button type="submit">Update Profile</button>
                </form>
            </section>

            {/* Correction Request Modal */}
            {showCorrectionModal && (
                <div className="modal-overlay">
                    <div className="modal-content">
                        <h3>Correction Request</h3>
                        <form onSubmit={handleCorrectionSubmit} className="form-correction">
                            <div className="form-group">
                                <label>Desired Start:</label>
                                <input
                                    type="datetime-local"
                                    name="desiredStart"
                                    value={correctionForm.desiredStart}
                                    onChange={(e) => setCorrectionForm({ ...correctionForm, desiredStart: e.target.value })}
                                    required
                                />
                            </div>
                            <div className="form-group">
                                <label>Desired End:</label>
                                <input
                                    type="datetime-local"
                                    name="desiredEnd"
                                    value={correctionForm.desiredEnd}
                                    onChange={(e) => setCorrectionForm({ ...correctionForm, desiredEnd: e.target.value })}
                                    required
                                />
                            </div>
                            <div className="form-group">
                                <label>Reason:</label>
                                <input
                                    type="text"
                                    name="reason"
                                    value={correctionForm.reason}
                                    onChange={(e) => setCorrectionForm({ ...correctionForm, reason: e.target.value })}
                                    placeholder="Enter reason"
                                    required
                                />
                            </div>
                            <div className="modal-buttons">
                                <button type="submit">Submit Correction</button>
                                <button type="button" onClick={closeCorrectionModal}>Cancel</button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default UserDashboard;
