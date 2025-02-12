// src/components/UserDashboard.jsx
import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../utils/api';
import Navbar from '../components/Navbar';
import VacationCalendar from '../components/VacationCalendar';
import { jsPDF } from "jspdf";
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

// Parst einen 32‑stelligen Hex‑String zu einem ASCII‑String
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

// Formatiert einen Zeitstempel (HH:MM) in der deutschen Zeitzone
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

// Gibt den Label für den punchOrder zurück
function getStatusLabel(punchOrder) {
    switch (punchOrder) {
        case 1: return "Work Start";
        case 2: return "Break Start";
        case 3: return "Break End";
        case 4: return "Work End";
        default: return "";
    }
}

// Formatiert ein Datum in das Format "yyyy-MM-dd"
function formatLocalDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

// Formatiert ein Datum für die Anzeige (z.B. "10-02-2025")
function formatDate(dateStr) {
    const date = new Date(dateStr);
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();
    return `${day}-${month}-${year}`;
}

// Gibt die Minuten seit Mitternacht zurück
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
 * Berechnet die Differenz (in Minuten) zwischen der tatsächlich gearbeiteten Zeit
 * (Work End minus Work Start minus Pausendauer) und der erwarteten Arbeitszeit.
 * Gibt einen String zurück, z. B. "+20 min" oder "-15 min".
 */
function computeDailyDiff(dayEntries, expectedWorkHours) {
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
        const diff = actualWorked - expectedMinutes;
        const sign = diff >= 0 ? '+' : '';
        return `${sign}${Math.round(diff)} min`;
    }
    return "";
}

// Hauptkomponente: UserDashboard
const UserDashboard = () => {
    const { currentUser } = useAuth();
    const [userProfile, setUserProfile] = useState(currentUser);
    const [allEntries, setAllEntries] = useState([]);
    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));
    const [vacationRequests, setVacationRequests] = useState([]);
    const [vacationForm, setVacationForm] = useState({ startDate: '', endDate: '' });
    const [printModalVisible, setPrintModalVisible] = useState(false);
    const [printStartDate, setPrintStartDate] = useState(formatLocalDate(new Date()));
    const [printEndDate, setPrintEndDate] = useState(formatLocalDate(new Date()));
    const [correctionModalVisible, setCorrectionModalVisible] = useState(false);
    const [correctionData, setCorrectionData] = useState({
        date: '',
        workStart: '',
        breakStart: '',
        breakEnd: '',
        workEnd: '',
        reason: ''
    });
    // Erwartete Arbeitszeit (aus Profil; falls nicht gesetzt, Standard: 8 Stunden)
    const expectedWorkHours =
        userProfile && userProfile.dailyWorkHours ? Number(userProfile.dailyWorkHours) : 8;
    const [punchMessage, setPunchMessage] = useState('');
    const [lastCardUser, setLastCardUser] = useState('');
    const [lastPunchTime, setLastPunchTime] = useState(0);

    // Profil vom Backend laden (z.B. /api/auth/me)
    useEffect(() => {
        async function fetchProfile() {
            try {
                const res = await api.get('/api/auth/me');
                setUserProfile(res.data);
            } catch (err) {
                console.error("Fehler beim Laden des Benutzerprofils", err);
            }
        }
        fetchProfile();
    }, [currentUser]);

    // Sobald das Profil geladen ist, lade Zeiteinträge und Urlaubsanträge
    useEffect(() => {
        if (userProfile) {
            fetchEntries();
            fetchVacations();
        }
    }, [userProfile]);

    async function fetchEntries() {
        try {
            const res = await api.get(`/api/timetracking/history?username=${userProfile.username}`);
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
                const cardUser = parseHex16(json.data);
                if (cardUser) {
                    const now = Date.now();
                    if (cardUser === lastCardUser && (now - lastPunchTime) < 5000) return;
                    setLastPunchTime(now);
                    setLastCardUser(cardUser);
                    showPunchMessage(`Eingestempelt: ${cardUser}`);
                    try {
                        await api.post('/api/timetracking/punch', null, { params: { username: cardUser } });
                        console.log("Punch executed for", cardUser);
                    } catch (punchErr) {
                        const errMsg = punchErr.response?.data?.message || punchErr.message;
                        console.error("Punch error:", errMsg);
                        if (errMsg.includes("kompletter Arbeitszyklus")) {
                            showPunchMessage("Tageslimit erreicht");
                        }
                    }
                    if (userProfile && userProfile.username.toLowerCase() === cardUser.toLowerCase()) {
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
        setTimeout(() => setPunchMessage(''), 3000);
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

    // Gruppiere Einträge nach Datum
    const dayMap = {};
    filteredEntries.forEach(entry => {
        const ds = new Date(entry.startTime).toLocaleDateString();
        if (!dayMap[ds]) dayMap[ds] = [];
        dayMap[ds].push(entry);
    });

    // Öffnet das Korrektur-Modal für einen bestimmten Tag
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
                username: userProfile.username,
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
                    username: userProfile.username,
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

    async function handleVacationSubmit(e) {
        e.preventDefault();
        try {
            await api.post('/api/vacation/create', null, {
                params: {
                    username: userProfile.username,
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

    // PDF-Erstellung mittels jsPDF und html2canvas
    function handlePrintReport() {
        if (!printStartDate || !printEndDate) {
            alert("Bitte wählen Sie sowohl ein Start- als auch ein Enddatum.");
            return;
        }
        // Gruppiere Einträge nach Datum (z. B. "Dienstag, 11.2.2025")
        const groupedByDate = {};
        filteredEntries.forEach(entry => {
            const dayString = new Date(entry.startTime).toLocaleDateString("de-DE", {
                weekday: 'long',
                day: 'numeric',
                month: 'numeric',
                year: 'numeric'
            });
            if (!groupedByDate[dayString]) {
                groupedByDate[dayString] = [];
            }
            groupedByDate[dayString].push(entry);
        });
        const doc = new jsPDF();
        let yPos = 10;
        doc.setFontSize(16);
        // Zeige den Benutzernamen oben im Bericht
        doc.text(`Zeiterfassung für ${userProfile.username}`, 10, yPos);
        yPos += 10;
        doc.setFontSize(12);
        Object.keys(groupedByDate).forEach(day => {
            doc.text(day, 10, yPos);
            const diffText = groupedByDate[day].length >= 4 ? computeDailyDiff(groupedByDate[day], expectedWorkHours) : "";
            if (diffText) {
                doc.text(`Differenz: ${diffText}`, 150, yPos);
            }
            yPos += 7;
            groupedByDate[day]
                .sort((a, b) => a.punchOrder - b.punchOrder)
                .forEach(entry => {
                    let timeText = "";
                    if (entry.punchOrder === 1) {
                        timeText = `Work Start: ${formatTime(entry.startTime)}`;
                    } else if (entry.punchOrder === 2) {
                        timeText = `Break Start: ${entry.breakStart ? formatTime(entry.breakStart) : formatTime(entry.startTime)}`;
                    } else if (entry.punchOrder === 3) {
                        timeText = `Break End: ${entry.breakEnd ? formatTime(entry.breakEnd) : formatTime(entry.startTime)}`;
                    } else if (entry.punchOrder === 4) {
                        timeText = `Work End: ${formatTime(entry.endTime)}`;
                    }
                    doc.text(timeText, 20, yPos);
                    yPos += 7;
                    if (yPos > 280) {
                        doc.addPage();
                        yPos = 10;
                    }
                });
            yPos += 5;
        });
        window.open(doc.output('bloburl'), '_blank');
        setPrintModalVisible(false);
    }

    return (
        <div className="user-dashboard">
            <Navbar />
            <header className="dashboard-header">
                <h2>Mein Dashboard (NFC aktiv)</h2>
                <div className="personal-info">
                    <p>
                        <strong>Benutzer:</strong> {userProfile?.username || "Nicht eingeloggt"}
                    </p>
                    <p>
                        <strong>Erwartete Arbeitszeit/Tag:</strong> {expectedWorkHours} Stunden
                    </p>
                </div>
            </header>
            {punchMessage && (
                <div className="punch-message">{punchMessage}</div>
            )}
            <section className="time-tracking-section">
                <h3>Wochenübersicht</h3>
                <div className="week-navigation">
                    <button onClick={handlePrevWeek}>← Vorherige Woche</button>
                    <input type="date" onChange={handleWeekJump} value={selectedMonday.toISOString().slice(0, 10)} />
                    <button onClick={handleNextWeek}>Nächste Woche →</button>
                </div>
                <div className="week-display">
                    {weekDates.map((dayObj, idx) => {
                        const ds = dayObj.toLocaleDateString();
                        const dayEntries = dayMap[ds] || [];
                        const diff = dayEntries.length >= 4 ? computeDailyDiff(dayEntries, expectedWorkHours) : "";
                        return (
                            <div key={idx} className="day-card">
                                <div className="day-card-header">
                                    <h4>
                                        {dayObj.toLocaleDateString('de-DE', { weekday: 'long' })}, {ds}
                                    </h4>
                                    {diff && <span className="daily-diff">({diff})</span>}
                                </div>
                                {dayEntries.length === 0 ? (
                                    <p className="no-entries">Keine Einträge</p>
                                ) : (
                                    <ul>
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
                                <div className="correction-button-day">
                                    <button onClick={() => openCorrectionModal(ds)}>Korrektur</button>
                                </div>
                            </div>
                        );
                    })}
                </div>
            </section>
            <div className="print-report-container">
                <button onClick={() => setPrintModalVisible(true)}>Zeiten drucken</button>
            </div>
            {printModalVisible && (
                <div className="modal-overlay">
                    <div className="modal-content">
                        <h3>Zeitraum auswählen</h3>
                        <div className="form-group">
                            <label>Startdatum:</label>
                            <input type="date" value={printStartDate} onChange={(e) => setPrintStartDate(e.target.value)} />
                        </div>
                        <div className="form-group">
                            <label>Enddatum:</label>
                            <input type="date" value={printEndDate} onChange={(e) => setPrintEndDate(e.target.value)} />
                        </div>
                        <div className="modal-buttons">
                            <button onClick={handlePrintReport}>Bericht drucken</button>
                            <button onClick={() => setPrintModalVisible(false)}>Abbrechen</button>
                        </div>
                    </div>
                </div>
            )}
            <section className="vacation-section">
                <h3>Urlaub beantragen</h3>
                <form onSubmit={handleVacationSubmit} className="form-vacation">
                    <div className="form-group">
                        <label>Startdatum:</label>
                        <input type="date" name="startDate" value={vacationForm.startDate} onChange={(e) => setVacationForm({ ...vacationForm, startDate: e.target.value })} required />
                    </div>
                    <div className="form-group">
                        <label>Enddatum:</label>
                        <input type="date" name="endDate" value={vacationForm.endDate} onChange={(e) => setVacationForm({ ...vacationForm, endDate: e.target.value })} required />
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
                                    {formatDate(v.startDate)} bis {formatDate(v.endDate)}{" "}
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
            {correctionModalVisible && (
                <div className="modal-overlay">
                    <div className="modal-content">
                        <h3>Korrektur für {correctionData.date}</h3>
                        <form onSubmit={handleCorrectionSubmit} className="form-correction">
                            <div className="form-group">
                                <label>Arbeits-Start (HH:MM):</label>
                                <input type="time" name="workStart" value={correctionData.workStart} onChange={handleCorrectionChange} />
                            </div>
                            <div className="form-group">
                                <label>Pausen-Start (HH:MM):</label>
                                <input type="time" name="breakStart" value={correctionData.breakStart} onChange={handleCorrectionChange} />
                            </div>
                            <div className="form-group">
                                <label>Pausen-Ende (HH:MM):</label>
                                <input type="time" name="breakEnd" value={correctionData.breakEnd} onChange={handleCorrectionChange} />
                            </div>
                            <div className="form-group">
                                <label>Arbeits-Ende (HH:MM):</label>
                                <input type="time" name="workEnd" value={correctionData.workEnd} onChange={handleCorrectionChange} />
                            </div>
                            <div className="form-group">
                                <label>Grund:</label>
                                <input type="text" name="reason" value={correctionData.reason} onChange={handleCorrectionChange} required />
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
