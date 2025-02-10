import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../utils/api';
import Navbar from '../components/Navbar';
import VacationCalendar from '../components/VacationCalendar';
import '../styles/UserDashboard.css';

// Liefert den Montag der Woche des übergebenen Datums
function getMondayOfWeek(date) {
    const copy = new Date(date);
    const day = copy.getDay();
    const diff = day === 0 ? 6 : day - 1;
    copy.setDate(copy.getDate() - diff);
    copy.setHours(0, 0, 0, 0);
    return copy;
}

// Fügt dem Datum eine bestimmte Anzahl Tage hinzu
function addDays(date, days) {
    const d = new Date(date);
    d.setDate(d.getDate() + days);
    return d;
}

// Wandelt einen 32‑stelligen Hex‑String (mit Leerzeichen) in einen ASCII‑String um
function parseHex16(hexString) {
    if (!hexString) return null;
    const clean = hexString.replace(/\s+/g, '');
    if (clean.length !== 32) return null;
    let output = '';
    for (let i = 0; i < 16; i++) {
        const byteHex = clean.slice(i * 2, i * 2 + 2);
        const val = parseInt(byteHex, 16);
        if (val !== 0) {
            output += String.fromCharCode(val);
        }
    }
    return output;
}

// Formatiert eine Uhrzeit (nur Stunden:Minuten)
function formatTime(dateStr) {
    const d = new Date(dateStr);
    return isNaN(d.getTime()) ? '-' : d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

// Gibt den Status-Label basierend auf punchOrder zurück
function getStatusLabel(punchOrder) {
    switch (punchOrder) {
        case 1:
            return "Work Start";
        case 2:
            return "Break Start";
        case 3:
            return "Break End";
        case 4:
            return "Work End";
        default:
            return "";
    }
}

// Wandelt einen Datumsstring in ein Input-kompatibles Format (yyyy-MM-ddTHH:mm)
const UserDashboard = () => {
    const { currentUser } = useAuth();
    const [allEntries, setAllEntries] = useState([]);
    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));
    const [vacationRequests, setVacationRequests] = useState([]);
    const [vacationForm, setVacationForm] = useState({ startDate: '', endDate: '' });
    // State für das Korrektur-Modal
    const [correctionModalVisible, setCorrectionModalVisible] = useState(false);
    const [correctionData, setCorrectionData] = useState({
        date: '',
        workStart: '',
        breakStart: '',
        breakEnd: '',
        workEnd: '',
        reason: ''
    });
    const expectedWorkHours = 8;
    const [punchMessage, setPunchMessage] = useState('');
    const [lastCardUser, setLastCardUser] = useState('');
    const [lastPunchTime, setLastPunchTime] = useState(0);

    useEffect(() => {
        if (currentUser) {
            fetchEntries();
            fetchVacations();
        }
    }, [currentUser]);

    async function fetchEntries() {
        try {
            const res = await api.get(`/api/timetracking/history?username=${currentUser.username}`);
            // Nur Einträge mit punchOrder 1 bis 4 berücksichtigen
            const validEntries = (res.data || []).filter(e => [1, 2, 3, 4].includes(e.punchOrder));
            setAllEntries(validEntries);
        } catch (err) {
            console.error("Error loading time entries", err);
        }
    }

    async function fetchVacations() {
        try {
            const res = await api.get('/api/vacation/my');
            setVacationRequests(res.data || []);
        } catch (err) {
            console.error("Error loading vacation requests", err);
        }
    }

    // NFC-Polling alle 2 Sekunden
    useEffect(() => {
        const interval = setInterval(() => {
            doNfcCheck();
        }, 2000);
        return () => clearInterval(interval);
    }, [allEntries, lastCardUser, lastPunchTime]);

    async function doNfcCheck() {
        try {
            const response = await fetch('http://localhost:8080/api/nfc/read/4');
            if (!response.ok) {
                console.warn("NFC read => HTTP not ok:", response.status);
                return;
            }
            const json = await response.json();
            if (json.status === 'no-card' || json.status === 'error') {
                return;
            } else if (json.status === 'success' && json.data) {
                console.log("NFC Card read (Hex):", json.data);
                const cardUser = parseHex16(json.data);
                if (cardUser) {
                    const now = Date.now();
                    if (cardUser === lastCardUser && (now - lastPunchTime) < 5000) {
                        return;
                    }
                    setLastPunchTime(now);
                    setLastCardUser(cardUser);
                    showPunchMessage(`Eingestempelt: ${cardUser}`);
                    try {
                        await api.post('/api/timetracking/punch', null, {
                            params: { username: cardUser },
                        });
                        console.log("Punch executed for", cardUser);
                    } catch (punchErr) {
                        const errMsg = punchErr.response?.data?.message || punchErr.message;
                        console.error("Punch error:", errMsg);
                        if (errMsg.includes("kompletter Arbeitszyklus")) {
                            showPunchMessage("Tageslimit erreicht");
                        }
                    }
                    if (currentUser && currentUser.username.toLowerCase() === cardUser.toLowerCase()) {
                        fetchEntries();
                    }
                }
            }
        } catch (err) {
            console.error("NFC fetch error:", err);
        }
    }

    function showPunchMessage(msg) {
        setPunchMessage(msg);
        setTimeout(() => {
            setPunchMessage('');
        }, 3000);
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
    const weekDateStrings = weekDates.map(d => d.toLocaleDateString());

    const filteredEntries = allEntries.filter(e => {
        const ds = new Date(e.startTime).toLocaleDateString();
        return weekDateStrings.includes(ds);
    });

    // Gruppiere Einträge pro Tag
    const dayMap = {};
    filteredEntries.forEach(entry => {
        const ds = new Date(entry.startTime).toLocaleDateString();
        if (!dayMap[ds]) dayMap[ds] = [];
        dayMap[ds].push(entry);
    });

    // Öffnet das Korrektur-Modal; ds wird als Datum im Format dd.MM.yyyy erwartet
    function openCorrectionModal(ds) {
        const parts = ds.split('.');
        if (parts.length === 3) {
            const correctDate = `${parts[2]}-${parts[1].padStart(2, '0')}-${parts[0].padStart(2, '0')}`;
            setCorrectionData({
                date: correctDate,
                workStart: '',
                breakStart: '',
                breakEnd: '',
                workEnd: '',
                reason: ''
            });
            setCorrectionModalVisible(true);
        }
    }

    function handleCorrectionChange(e) {
        setCorrectionData({
            ...correctionData,
            [e.target.name]: e.target.value
        });
    }

    async function handleCorrectionSubmit(e) {
        e.preventDefault();
        try {
            const { date, workStart, breakStart, breakEnd, workEnd, reason } = correctionData;
            const desiredStart = `${date}T${workStart || '00:00'}`;
            const desiredEnd = `${date}T${workEnd || '00:00'}`;
            console.log("Sending correction request with:", {
                username: currentUser?.username,
                date,
                workStart,
                breakStart,
                breakEnd,
                workEnd,
                reason,
                desiredStart,
                desiredEnd
            });
            await api.post('/api/correction/create-full', null, {
                params: {
                    username: currentUser?.username,
                    date: date,
                    workStart: workStart,
                    breakStart: breakStart,
                    breakEnd: breakEnd,
                    workEnd: workEnd,
                    reason: reason,
                    desiredStart: desiredStart,
                    desiredEnd: desiredEnd
                }
            });
            alert("Korrektur-Antrag wurde erfolgreich eingereicht.");
            setCorrectionModalVisible(false);
        } catch (err) {
            console.error("Error sending correction request", err);
            alert("Error sending correction request: " + (err.response?.data || err.message));
        }
    }

    // Vacation request submission
    async function handleVacationSubmit(e) {
        e.preventDefault();
        try {
            await api.post('/api/vacation/create', null, {
                params: {
                    username: currentUser.username,
                    startDate: vacationForm.startDate,
                    endDate: vacationForm.endDate
                }
            });
            alert("Urlaubsantrag wurde erfolgreich eingereicht.");
        } catch (err) {
            console.error("Error submitting vacation request:", err);
            alert("Error submitting vacation request.");
        }
    }

    return (
        <div className="user-dashboard">
            <Navbar />
            <header className="dashboard-header">
                <h2>Mein Dashboard (NFC aktiv)</h2>
                <div className="personal-info">
                    <p><strong>Benutzer:</strong> {currentUser?.username || "Nicht eingeloggt"}</p>
                    <p><strong>Erwartete Arbeitszeit/Tag:</strong> {expectedWorkHours}h</p>
                </div>
            </header>

            {punchMessage && (
                <div className="punch-message">
                    {punchMessage}
                </div>
            )}

            {/* Wochenübersicht */}
            <section className="time-tracking-section">
                <h3>Wochenübersicht</h3>
                <div className="week-navigation">
                    <button onClick={handlePrevWeek}>← Vorherige Woche</button>
                    <input
                        type="date"
                        onChange={handleWeekJump}
                        value={selectedMonday.toISOString().slice(0, 10)}
                    />
                    <button onClick={handleNextWeek}>Nächste Woche →</button>
                </div>
                <div className="week-display">
                    {weekDates.map((dayObj, idx) => {
                        const ds = dayObj.toLocaleDateString();
                        const dayEntries = dayMap[ds] || [];
                        return (
                            <div key={idx} className="day-card">
                                <div className="day-card-header">
                                    <h4>
                                        {dayObj.toLocaleDateString('de-DE', { weekday: 'long' })}, {ds}
                                    </h4>
                                </div>
                                {dayEntries.length === 0 ? (
                                    <p className="no-entries">Keine Einträge</p>
                                ) : (
                                    <ul>
                                        {dayEntries
                                            .sort((a, b) => a.punchOrder - b.punchOrder)
                                            .map(e => (
                                                <li key={e.id}>
                                                    <span className="entry-label">{getStatusLabel(e.punchOrder)}:</span> {formatTime(e.startTime)}
                                                </li>
                                            ))}
                                    </ul>
                                )}
                                <div className="correction-button-day">
                                    <button onClick={() => openCorrectionModal(ds)}>Korrektur</button>
                                </div>
                            </div>
                        );
                    })}
                </div>
            </section>

            {/* Urlaubsanträge */}
            <section className="vacation-section">
                <h3>Urlaub beantragen</h3>
                <form onSubmit={handleVacationSubmit} className="form-vacation">
                    <div className="form-group">
                        <label>Startdatum:</label>
                        <input
                            type="date"
                            name="startDate"
                            value={vacationForm.startDate}
                            onChange={(e) => setVacationForm({ ...vacationForm, startDate: e.target.value })}
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label>Enddatum:</label>
                        <input
                            type="date"
                            name="endDate"
                            value={vacationForm.endDate}
                            onChange={(e) => setVacationForm({ ...vacationForm, endDate: e.target.value })}
                            required
                        />
                    </div>
                    <button type="submit">Beantragen</button>
                </form>
                <div className="vacation-history">
                    <h4>Meine Urlaubsanträge</h4>
                    {vacationRequests.length === 0 ? (
                        <p>Keine Urlaubsanträge</p>
                    ) : (
                        <ul>
                            {vacationRequests.map(v => (
                                <li key={v.id}>
                                    {v.startDate} bis {v.endDate}{' '}
                                    {v.approved ? (
                                        <span className="approved">Genehmigt</span>
                                    ) : v.denied ? (
                                        <span className="denied">Abgelehnt</span>
                                    ) : (
                                        <span className="pending">Offen</span>
                                    )}
                                </li>
                            ))}
                        </ul>
                    )}
                </div>
                <div className="calendar-section">
                    <h4>Urlaubskalender</h4>
                    <VacationCalendar vacationRequests={vacationRequests.filter(v => v.approved)} />
                </div>
            </section>

            {/* Korrektur-Modal */}
            {correctionModalVisible && (
                <div className="modal-overlay">
                    <div className="modal-content">
                        <h3>Korrektur für {correctionData.date}</h3>
                        <form onSubmit={handleCorrectionSubmit} className="form-correction">
                            <div className="form-group">
                                <label>Arbeits-Start (HH:MM):</label>
                                <input
                                    type="time"
                                    name="workStart"
                                    value={correctionData.workStart}
                                    onChange={handleCorrectionChange}
                                />
                            </div>
                            <div className="form-group">
                                <label>Pausen-Start (HH:MM):</label>
                                <input
                                    type="time"
                                    name="breakStart"
                                    value={correctionData.breakStart}
                                    onChange={handleCorrectionChange}
                                />
                            </div>
                            <div className="form-group">
                                <label>Pausen-Ende (HH:MM):</label>
                                <input
                                    type="time"
                                    name="breakEnd"
                                    value={correctionData.breakEnd}
                                    onChange={handleCorrectionChange}
                                />
                            </div>
                            <div className="form-group">
                                <label>Arbeits-Ende (HH:MM):</label>
                                <input
                                    type="time"
                                    name="workEnd"
                                    value={correctionData.workEnd}
                                    onChange={handleCorrectionChange}
                                />
                            </div>
                            <div className="form-group">
                                <label>Grund:</label>
                                <input
                                    type="text"
                                    name="reason"
                                    value={correctionData.reason}
                                    onChange={handleCorrectionChange}
                                    required
                                />
                            </div>
                            <div className="modal-buttons">
                                <button type="submit">Einreichen</button>
                                <button type="button" onClick={() => setCorrectionModalVisible(false)}>Abbrechen</button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default UserDashboard;
