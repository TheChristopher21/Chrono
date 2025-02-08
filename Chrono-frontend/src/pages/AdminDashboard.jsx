import React, { useState, useEffect } from 'react';
import Navbar from '../components/Navbar';
import api from '../utils/api';
import VacationCalendar from '../components/VacationCalendar';
import { useAuth } from '../context/AuthContext';
import '../styles/AdminDashboard.css';

// Hilfsfunktionen (getMondayOfWeek, addDays, etc.)

function getMondayOfWeek(date) {
    const copy = new Date(date);
    const day = copy.getDay();
    const diff = day === 0 ? 6 : day - 1;
    copy.setDate(copy.getDate() - diff);
    copy.setHours(0,0,0,0);
    return copy;
}
function addDays(date, days) {
    const d = new Date(date);
    d.setDate(d.getDate() + days);
    return d;
}

function calcDayMinutes(entries) {
    if (entries.length < 2) return null;
    const sorted = [...entries].sort((a,b) => a.punchOrder - b.punchOrder);
    if (!sorted[0].endTime || !sorted[sorted.length - 1].endTime) return null;
    const start = new Date(sorted[0].startTime);
    const end   = new Date(sorted[sorted.length - 1].endTime);
    return (end - start) / 60000; // in Minuten
}

function AdminDashboard() {
    const { currentUser } = useAuth();
    const [adminPassword, setAdminPassword] = useState('');

    const [allTracks, setAllTracks] = useState([]);
    const [allVacations, setAllVacations] = useState([]);

    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));
    const expectedWorkHours = 8;

    // Ausklappen pro User
    const [expandedUsers, setExpandedUsers] = useState({});

    // Edit Modal
    const [editingTrack, setEditingTrack] = useState(null);
    const [editForm, setEditForm] = useState({
        newStart: '',
        newEnd: '',
        userPassword: ''
    });

    useEffect(() => {
        fetchAllTracks();
        fetchAllVacations();
    }, []);

    async function fetchAllTracks() {
        try {
            const res = await api.get('/api/admin/timetracking/all');
            setAllTracks(res.data || []);
        } catch (err) {
            console.error("Fehler beim Laden aller Zeiteinträge", err);
        }
    }
    async function fetchAllVacations() {
        try {
            const res = await api.get('/api/vacation/all');
            setAllVacations(res.data || []);
        } catch (err) {
            console.error("Fehler beim Laden aller Urlaubsanträge", err);
        }
    }

    // Woche
    function handlePrevWeek() {
        setSelectedMonday(prev => addDays(prev, -7));
    }
    function handleNextWeek() {
        setSelectedMonday(prev => addDays(prev, +7));
    }
    function handleWeekJump(e) {
        const picked = new Date(e.target.value);
        if (!isNaN(picked.getTime())) {
            setSelectedMonday(getMondayOfWeek(picked));
        }
    }
    const weekDates = Array.from({length:7}, (_,i)=> addDays(selectedMonday,i));
    const weekStrs = weekDates.map(d => d.toLocaleDateString());

    // Filtern
    const filteredTracks = allTracks.filter(t => {
        const ds = new Date(t.startTime).toLocaleDateString();
        return weekStrs.includes(ds);
    });

    // Gruppieren nach username
    const userGroups = filteredTracks.reduce((acc, track) => {
        const uname = track.username;
        if (!acc[uname]) acc[uname] = [];
        acc[uname].push(track);
        return acc;
    }, {});

    function toggleUserExpand(username) {
        setExpandedUsers(prev => ({
            ...prev,
            [username]: !prev[username]
        }));
    }

    // Edit-Funktionen
    function handleEditClick(track) {
        setEditingTrack(track);
        setEditForm({
            newStart: toLocalInputString(track.startTime),
            newEnd: track.endTime ? toLocalInputString(track.endTime) : '',
            userPassword: ''
        });
    }
    function toLocalInputString(dateStr) {
        const d = new Date(dateStr);
        return d.toISOString().slice(0,16);
    }
    function handleEditFormChange(e) {
        setEditForm(prev => ({ ...prev, [e.target.name]: e.target.value }));
    }
    async function handleEditSubmit(e) {
        e.preventDefault();
        if (!editingTrack) return;
        try {
            await api.put(`/api/admin/timetracking/${editingTrack.id}`, null, {
                params: {
                    newStart: editForm.newStart,
                    newEnd: editForm.newEnd,
                    adminPassword: adminPassword,
                    userPassword: editForm.userPassword
                }
            });
            setEditingTrack(null);
            fetchAllTracks();
        } catch (err) {
            console.error("Fehler beim Update eines Eintrags", err);
        }
    }
    function closeEditModal() {
        setEditingTrack(null);
    }

    // Urlaubs-Genehmigung
    async function handleApproveVacation(id) {
        if (!adminPassword) {
            alert("Bitte Admin-Passwort eingeben!");
            return;
        }
        try {
            await api.post(`/api/vacation/approve/${id}`, null, { params: { adminPassword } });
            fetchAllVacations();
        } catch (err) {
            console.error("Fehler beim Genehmigen", err);
        }
    }
    async function handleDenyVacation(id) {
        try {
            await api.post(`/api/vacation/deny/${id}`);
            fetchAllVacations();
        } catch (err) {
            console.error("Fehler beim Ablehnen", err);
        }
    }

    function calcDiffIndicator(totalMin) {
        if (totalMin == null) return null;
        const expected = expectedWorkHours * 60;
        const diff = totalMin - expected;
        if (Math.abs(diff) < 10) return null;
        return diff > 0
            ? { type:'over', text:`+${diff} Min` }
            : { type:'under', text:`-${Math.abs(diff)} Min` };
    }

    // Sortierte Urlaubsanträge
    const sortedVacations = [...allVacations].sort((a,b) =>
        new Date(a.startDate) - new Date(b.startDate)
    );

    return (
        <div className="admin-dashboard fancy-bg">
            <Navbar />
            <header className="dashboard-header">
                <h2>Admin-Dashboard (Wochenansicht)</h2>
                <p>Angemeldet als: {currentUser?.username}</p>
                <div className="admin-password">
                    <label>Admin-Passwort:</label>
                    <input
                        type="password"
                        value={adminPassword}
                        onChange={(e) => setAdminPassword(e.target.value)}
                    />
                </div>
            </header>

            <section className="week-section">
                <h3>Zeiterfassung - aktuelle Woche</h3>
                <div className="week-navigation">
                    <button onClick={handlePrevWeek}>← Vorherige Woche</button>
                    <input
                        type="date"
                        onChange={handleWeekJump}
                        value={selectedMonday.toISOString().slice(0,10)}
                    />
                    <button onClick={handleNextWeek}>Nächste Woche →</button>
                </div>

                {Object.keys(userGroups).length === 0 ? (
                    <p>Keine Einträge in dieser Woche</p>
                ) : (
                    <div className="admin-user-groups">
                        {Object.keys(userGroups).map(username => {
                            const userEntries = userGroups[username];
                            // Pro Tag gruppieren
                            const dayMap = {};
                            for (let t of userEntries) {
                                const ds = new Date(t.startTime).toLocaleDateString();
                                if (!dayMap[ds]) dayMap[ds] = [];
                                dayMap[ds].push(t);
                            }

                            // Ausklappen je user
                            const isExpanded = !!expandedUsers[username];

                            return (
                                <div key={username} className="admin-user-block">
                                    <div className="admin-user-header" onClick={() => toggleUserExpand(username)}>
                                        <h4>{username}</h4>
                                        <button>{isExpanded ? '–' : '+'}</button>
                                    </div>
                                    {isExpanded && (
                                        <div className="admin-week-display">
                                            {weekDates.map((wd, i) => {
                                                const ds = wd.toLocaleDateString();
                                                const dayEntries = dayMap[ds] || [];
                                                const totalMin = calcDayMinutes(dayEntries);
                                                const diffInd = calcDiffIndicator(totalMin);

                                                return (
                                                    <div key={i} className="admin-day-card">
                                                        <div className="admin-day-card-header">
                                                            <strong>{wd.toLocaleDateString('de-DE',{weekday:'long'})}, {ds}</strong>
                                                            {diffInd && (
                                                                <span className={`indicator ${diffInd.type}`}>
                                  {diffInd.text}
                                </span>
                                                            )}
                                                        </div>
                                                        {dayEntries.length === 0 ? (
                                                            <p className="no-entries">Keine Einträge</p>
                                                        ) : (
                                                            <ul>
                                                                {dayEntries.map(tr => (
                                                                    <li key={tr.id}>
                                                                        <span className="entry-label">Status:</span> {tr.color} <br/>
                                                                        <span className="entry-label">Start:</span> {new Date(tr.startTime).toLocaleString()} <br/>
                                                                        <span className="entry-label">Ende:</span> {tr.endTime ? new Date(tr.endTime).toLocaleString() : 'laufend'} <br/>
                                                                        <button onClick={() => handleEditClick(tr)}>Bearbeiten</button>
                                                                    </li>
                                                                ))}
                                                            </ul>
                                                        )}
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
                <h3>Urlaubsanträge (alle Benutzer)</h3>
                {sortedVacations.length === 0 ? (
                    <p>Keine Urlaubsanträge gefunden</p>
                ) : (
                    <ul className="vacation-list">
                        {sortedVacations.map(v => (
                            <li key={v.id} className="vacation-item">
                                <strong>{v.username}</strong>: {v.startDate} - {v.endDate}{' '}
                                {v.approved ? (
                                    <span className="approved">Genehmigt</span>
                                ) : v.denied ? (
                                    <span className="denied">Abgelehnt</span>
                                ) : (
                                    <span className="pending">Offen</span>
                                )}
                                {!v.approved && !v.denied && (
                                    <span>
                    <button onClick={() => handleApproveVacation(v.id)}>Genehmigen</button>
                    <button onClick={() => handleDenyVacation(v.id)}>Ablehnen</button>
                  </span>
                                )}
                            </li>
                        ))}
                    </ul>
                )}
                <div className="calendar-section">
                    <h4>Urlaubskalender</h4>
                    <VacationCalendar vacationRequests={sortedVacations.filter(v => v.approved)} />
                </div>
            </section>

            {editingTrack && (
                <div className="modal-overlay">
                    <div className="modal-content">
                        <h3>Zeit-Eintrag bearbeiten</h3>
                        <form onSubmit={handleEditSubmit}>
                            <div className="form-group">
                                <label>Neuer Start:</label>
                                <input
                                    type="datetime-local"
                                    name="newStart"
                                    value={editForm.newStart}
                                    onChange={handleEditFormChange}
                                    required
                                />
                            </div>
                            <div className="form-group">
                                <label>Neues Ende:</label>
                                <input
                                    type="datetime-local"
                                    name="newEnd"
                                    value={editForm.newEnd}
                                    onChange={handleEditFormChange}
                                    required
                                />
                            </div>
                            <div className="form-group">
                                <label>User-Passwort:</label>
                                <input
                                    type="password"
                                    name="userPassword"
                                    value={editForm.userPassword}
                                    onChange={handleEditFormChange}
                                    required
                                />
                            </div>
                            <p><em>Das Admin-Passwort hast du oben eingegeben.</em></p>
                            <div className="modal-buttons">
                                <button type="submit">Speichern</button>
                                <button type="button" onClick={closeEditModal}>Abbrechen</button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}

export default AdminDashboard;
