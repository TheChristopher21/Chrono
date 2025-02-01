// src/pages/AdminDashboard.jsx
import React, { useState, useEffect } from 'react';
import api from '../utils/api';
import Navbar from '../components/Navbar';
import '../styles/AdminDashboard.css';
import { useAuth } from '../context/AuthContext';

const AdminDashboard = () => {
    const { currentUser } = useAuth();

    // Gruppierung der Time-Tracking-Einträge nach Benutzer
    const [userGroups, setUserGroups] = useState({});
    // Offene Correction Requests
    const [correctionRequests, setCorrectionRequests] = useState([]);
    // Alle Vacation Requests
    const [vacationRequests, setVacationRequests] = useState([]);
    // Admin-Passwort (wird in mehreren Abschnitten genutzt)
    const [adminPassword, setAdminPassword] = useState('');
    // Steuerung, welche Benutzergruppe aufgeklappt ist
    const [expandedUsers, setExpandedUsers] = useState({});
    // weekOffset für die Navigation zwischen Wochen (0 = aktuelle Woche)
    const [weekOffset, setWeekOffset] = useState(0);

    // Holt alle Time-Tracking-Einträge und gruppiert sie nach Benutzer
    const fetchTimeTracks = async () => {
        try {
            const res = await api.get('/api/admin/timetracking/all');
            const groups = res.data.reduce((acc, track) => {
                const username = track.username;
                if (!acc[username]) {
                    acc[username] = [];
                }
                acc[username].push(track);
                return acc;
            }, {});
            setUserGroups(groups);
        } catch (err) {
            console.error("Error fetching time tracking overview", err);
        }
    };

    // Holt alle offenen Correction Requests
    const fetchCorrectionRequests = async () => {
        try {
            const res = await api.get('/api/correction/open');
            setCorrectionRequests(res.data);
        } catch (err) {
            console.error("Error fetching correction requests", err);
        }
    };

    // Holt alle Vacation Requests
    const fetchVacationRequests = async () => {
        try {
            const res = await api.get('/api/vacation/all');
            setVacationRequests(res.data);
        } catch (err) {
            console.error("Error fetching vacation requests", err);
        }
    };

    useEffect(() => {
        // Wir warten auf den Mount, dann rufen wir alle Daten ab
        fetchTimeTracks();
        fetchCorrectionRequests();
        fetchVacationRequests();
    }, []);

    // Handler für Correction Requests
    const handleApproveCorrection = async (id) => {
        if (!adminPassword) {
            alert("Please enter your admin password");
            return;
        }
        try {
            await api.post(`/api/correction/approve/${id}`, null, { params: { adminPassword } });
            await fetchCorrectionRequests();
            await fetchTimeTracks();
        } catch (err) {
            console.error("Error approving correction request", err);
        }
    };

    const handleDenyCorrection = async (id) => {
        try {
            await api.post(`/api/correction/deny/${id}`);
            await fetchCorrectionRequests();
        } catch (err) {
            console.error("Error denying correction request", err);
        }
    };

    // Handler für Vacation Requests
    const handleApproveVacation = async (id) => {
        if (!adminPassword) {
            alert("Please enter your admin password");
            return;
        }
        try {
            await api.post(`/api/vacation/approve/${id}`, null, { params: { adminPassword } });
            await fetchVacationRequests();
        } catch (err) {
            console.error("Error approving vacation request", err);
        }
    };

    const handleDenyVacation = async (id) => {
        try {
            await api.post(`/api/vacation/deny/${id}`);
            await fetchVacationRequests();
        } catch (err) {
            console.error("Error denying vacation request", err);
        }
    };

    // Zum Auf- und Zuklappen der Benutzergruppe
    const toggleUserGroup = (username) => {
        setExpandedUsers(prev => ({ ...prev, [username]: !prev[username] }));
    };

    // Berechnet die Start- und Enddaten der Woche basierend auf weekOffset
    const getWeekRange = (offset = 0) => {
        const today = new Date();
        const currentDay = today.getDay();
        const diffToMonday = (currentDay === 0 ? -6 : 1 - currentDay);
        const monday = new Date(today);
        monday.setDate(today.getDate() + diffToMonday + offset * 7);
        const sunday = new Date(monday);
        sunday.setDate(monday.getDate() + 6);
        return { monday, sunday };
    };

    // Filtert die Time-Tracking-Einträge für die jeweilige Woche
    const filterEntriesByWeek = (entries, offset = 0) => {
        const { monday, sunday } = getWeekRange(offset);
        return entries.filter(entry => {
            const entryDate = new Date(entry.startTime);
            return entryDate >= monday && entryDate <= sunday;
        });
    };

    return (
        <div className="admin-dashboard">
            <Navbar />
            <header className="dashboard-header">
                <h2>Admin Dashboard</h2>
                <p>Welcome, {currentUser?.username}</p>
            </header>

            <section className="time-tracking-section">
                <h3>Time Tracking Overview</h3>
                {Object.keys(userGroups).length === 0 ? (
                    <p>No time tracks found.</p>
                ) : (
                    <div className="user-groups">
                        {Object.keys(userGroups).map(username => (
                            <div key={username} className="user-group">
                                <div className="user-group-header" onClick={() => toggleUserGroup(username)}>
                                    <h4>{username}</h4>
                                    <button>{expandedUsers[username] ? '-' : '+'}</button>
                                </div>
                                {expandedUsers[username] && (
                                    <>
                                        <div className="week-navigation">
                                            <button onClick={() => setWeekOffset(prev => prev - 1)}>← Prev Week</button>
                                            <span>
                        {getWeekRange(weekOffset).monday.toLocaleDateString()} - {getWeekRange(weekOffset).sunday.toLocaleDateString()}
                      </span>
                                            <button onClick={() => setWeekOffset(prev => prev + 1)}>Next Week →</button>
                                        </div>
                                        <ul>
                                            {filterEntriesByWeek(userGroups[username], weekOffset).map(entry => (
                                                <li key={entry.id} className="track-item">
                                                    <span className="entry-label">Start:</span> {new Date(entry.startTime).toLocaleString()}<br />
                                                    <span className="entry-label">End:</span> {entry.endTime ? new Date(entry.endTime).toLocaleString() : 'Ongoing'}<br />
                                                    <span className="entry-label">Status:</span> {entry.color}
                                                </li>
                                            ))}
                                        </ul>
                                    </>
                                )}
                            </div>
                        ))}
                    </div>
                )}
            </section>

            <section className="correction-section">
                <h3>Open Correction Requests</h3>
                <div className="admin-password">
                    <label>Admin Password:</label>
                    <input
                        type="password"
                        value={adminPassword || ''}
                        onChange={(e) => setAdminPassword(e.target.value)}
                    />
                </div>
                {correctionRequests.length === 0 ? (
                    <p>No correction requests found.</p>
                ) : (
                    <ul>
                        {correctionRequests.map(req => (
                            <li key={req.id} className="correction-item">
                                <span className="entry-label">User:</span> {req.username}<br />
                                <span className="entry-label">Original Start:</span> {req.originalStart ? new Date(req.originalStart).toLocaleString() : 'N/A'}<br />
                                <span className="entry-label">Original End:</span> {req.originalEnd ? new Date(req.originalEnd).toLocaleString() : 'N/A'}<br />
                                <span className="entry-label">Desired Start:</span> {new Date(req.desiredStart).toLocaleString()}<br />
                                <span className="entry-label">Desired End:</span> {new Date(req.desiredEnd).toLocaleString()}<br />
                                <span className="entry-label">Reason:</span> {req.reason}<br />
                                <div className="actions">
                                    <button onClick={() => handleApproveCorrection(req.id)}>Approve</button>
                                    <button onClick={() => handleDenyCorrection(req.id)}>Deny</button>
                                </div>
                            </li>
                        ))}
                    </ul>
                )}
            </section>

            <section className="vacation-section">
                <h3>Vacation Requests</h3>
                <div className="admin-password">
                    <label>Admin Password:</label>
                    <input
                        type="password"
                        value={adminPassword || ''}
                        onChange={(e) => setAdminPassword(e.target.value)}
                    />
                </div>
                {vacationRequests.length === 0 ? (
                    <p>No vacation requests found.</p>
                ) : (
                    <ul>
                        {vacationRequests.map(vac => (
                            <li key={vac.id} className="vacation-item">
                                <span className="entry-label">User:</span> {vac.username}<br />
                                <span className="entry-label">From:</span> {vac.startDate}<br />
                                <span className="entry-label">To:</span> {vac.endDate}<br />
                                <span className="entry-label">Status:</span>{' '}
                                <span className={`status-badge ${vac.approved ? 'approved' : vac.denied ? 'denied' : 'pending'}`}>
                  {vac.approved ? 'Approved' : vac.denied ? 'Denied' : 'Pending'}
                </span>
                                <div className="actions">
                                    <button onClick={() => handleApproveVacation(vac.id)}>Approve</button>
                                    <button onClick={() => handleDenyVacation(vac.id)}>Deny</button>
                                </div>
                            </li>
                        ))}
                    </ul>
                )}
            </section>
        </div>
    );
};

export default AdminDashboard;
