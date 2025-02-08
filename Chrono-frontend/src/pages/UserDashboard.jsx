import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../utils/api';
import Navbar from '../components/Navbar';
import VacationCalendar from '../components/VacationCalendar';
import '../styles/UserDashboard.css';

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

const UserDashboard = () => {
    const { currentUser } = useAuth();
    const [allEntries, setAllEntries] = useState([]);
    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));

    // Erwartete Arbeitszeit (hart kodiert)
    const expectedWorkHours = 8;

    // Urlaubsanträge
    const [vacationRequests, setVacationRequests] = useState([]);
    const [vacationForm, setVacationForm] = useState({ startDate: '', endDate: '' });

    // Korrektur (pro Tag)
    const [correctionModalVisible, setCorrectionModalVisible] = useState(false);
    const [correctionData, setCorrectionData] = useState({
        date: '',             // z. B. "2023-10-15"
        workStart: '',        // "HH:MM"
        breakStart: '',
        breakEnd: '',
        workEnd: '',
        reason: ''
    });

    useEffect(() => {
        if (!currentUser) return;
        fetchEntries();
        fetchVacations();
    }, [currentUser]);

    async function fetchEntries() {
        try {
            const res = await api.get(`/api/timetracking/history?username=${currentUser.username}`);
            setAllEntries(res.data || []);
        } catch (err) {
            console.error("Fehler beim Laden der Zeiteinträge", err);
        }
    }
    async function fetchVacations() {
        try {
            const res = await api.get('/api/vacation/my');
            setVacationRequests(res.data || []);
        } catch (err) {
            console.error("Fehler beim Laden der Urlaubsanträge", err);
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
    const weekDateStrings = weekDates.map(d => d.toLocaleDateString());

    // Filtern
    const filteredEntries = allEntries.filter(e => {
        const ds = new Date(e.startTime).toLocaleDateString();
        return weekDateStrings.includes(ds);
    });

    // Gruppieren pro Tag
    const dayMap = {};
    for (let e of filteredEntries) {
        const ds = new Date(e.startTime).toLocaleDateString();
        if (!dayMap[ds]) dayMap[ds] = [];
        dayMap[ds].push(e);
    }

    // 4-Button Einstempeln
    async function handleWorkStart() {
        try {
            await api.post(`/api/timetracking/work-start?username=${currentUser.username}`);
            fetchEntries();
        } catch (err) {
            alert("Fehler bei Arbeits-Start");
            console.error(err);
        }
    }
    async function handleBreakStart() {
        try {
            await api.post(`/api/timetracking/break-start?username=${currentUser.username}`);
            fetchEntries();
        } catch (err) {
            alert("Fehler bei Pausen-Start");
            console.error(err);
        }
    }
    async function handleBreakEnd() {
        try {
            await api.post(`/api/timetracking/break-end?username=${currentUser.username}`);
            fetchEntries();
        } catch (err) {
            alert("Fehler bei Pausen-Ende");
            console.error(err);
        }
    }
    async function handleWorkEnd() {
        try {
            await api.post(`/api/timetracking/work-end?username=${currentUser.username}`);
            fetchEntries();
        } catch (err) {
            alert("Fehler bei Arbeits-Ende");
            console.error(err);
        }
    }

    // Korrektur-Modal
    function openCorrectionModal(dateStr) {
        // dateStr in Format dd.mm.yyyy oder so
        // Wir wollen "YYYY-MM-DD" abspeichern
        const dateParts = dateStr.split('.');
        // Falls "14.10.2023" → [ '14', '10', '2023' ]
        if (dateParts.length === 3) {
            const correctDate = `${dateParts[2]}-${dateParts[1].padStart(2,'0')}-${dateParts[0].padStart(2,'0')}`;
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
            // Korrektur anlegen
            // Du kannst im Backend `desiredStart` und `desiredEnd` oder
            // 4 Felder extra anlegen. Hier verknüpfen wir's z. B. in Start/End.
            // Wir basteln einen Start/Ende aus Korrektur:
            //   date + "T" + workStart
            const day = correctionData.date;  // "2023-10-14"
            function comb(field) {
                // z. B. "2023-10-14T08:30"
                return `${day}T${field || '00:00'}`;
            }
            const desiredStart = comb(correctionData.workStart);
            const desiredEnd = comb(correctionData.workEnd);

            await api.post('/api/correction/create-full', null, {
                params: {
                    username: currentUser.username,
                    date: correctionData.date,
                    workStart: correctionData.workStart,
                    breakStart: correctionData.breakStart,
                    breakEnd: correctionData.breakEnd,
                    workEnd: correctionData.workEnd,
                    reason: correctionData.reason,
                    desiredStart,
                    desiredEnd
                }
            });
            alert("Korrektur-Antrag eingereicht.");
            setCorrectionModalVisible(false);
        } catch (err) {
            console.error("Fehler bei Korrektur-Antrag", err);
        }
    }

    // Urlaub
    function handleVacationChange(e) {
        setVacationForm({ ...vacationForm, [e.target.name]: e.target.value });
    }
    async function handleVacationSubmit(e) {
        e.preventDefault();
        // ...
    }

    // Arbeitszeit berechnen
    function calcDayMinutes(entries) {
        if (entries.length < 2) return null;
        const sorted = [...entries].sort((a,b) => a.punchOrder - b.punchOrder);
        if (!sorted[0].endTime || !sorted[sorted.length-1].endTime) return null;
        const start = new Date(sorted[0].startTime);
        const end = new Date(sorted[sorted.length-1].endTime);
        return (end - start)/60000;
    }
    function getIndicator(totalMin) {
        if (totalMin == null) return null;
        const soll = expectedWorkHours * 60;
        const diff = totalMin - soll;
        if (Math.abs(diff) < 10) return null;
        return diff > 0
            ? { type:'over', text:`+${diff} Min` }
            : { type:'under', text:`-${Math.abs(diff)} Min` };
    }

    // Filter + Render
    return (
        <div className="user-dashboard">
            <Navbar />

            <header className="dashboard-header">
                <h2>Mein Dashboard</h2>
                <div className="personal-info">
                    <p><strong>Benutzer:</strong> {currentUser?.username}</p>
                    <p><strong>Erwartete Arbeitszeit/Tag:</strong> {expectedWorkHours}h</p>
                </div>
            </header>

            {/* Wochenübersicht */}
            <section className="time-tracking-section">
                <h3>Wochenübersicht</h3>
                <div className="week-navigation">
                    <button onClick={handlePrevWeek}>← Vorherige Woche</button>
                    <input
                        type="date"
                        onChange={handleWeekJump}
                        value={selectedMonday.toISOString().slice(0,10)}
                    />
                    <button onClick={handleNextWeek}>Nächste Woche →</button>
                </div>

                <div className="tracking-buttons">
                    <button onClick={handleWorkStart}>Arbeits-Start</button>
                    <button onClick={handleBreakStart}>Pausen-Start</button>
                    <button onClick={handleBreakEnd}>Pausen-Ende</button>
                    <button onClick={handleWorkEnd}>Arbeits-Ende</button>
                </div>

                <div className="week-display">
                    {weekDates.map((dayObj, idx) => {
                        const ds = dayObj.toLocaleDateString(); // z. B. "14.10.2023"
                        const dayEntries = dayMap[ds] || [];
                        const totalMin = calcDayMinutes(dayEntries);
                        const indicator = getIndicator(totalMin);

                        return (
                            <div key={idx} className="day-card">
                                <div className="day-card-header">
                                    <h4>
                                        {dayObj.toLocaleDateString('de-DE',{weekday:'long'})}, {ds}
                                    </h4>
                                    {indicator && (
                                        <span className={`indicator-badge ${indicator.type}`}>
                      {indicator.text}
                    </span>
                                    )}
                                </div>
                                {dayEntries.length === 0 ? (
                                    <p className="no-entries">Keine Einträge</p>
                                ) : (
                                    <ul>
                                        {dayEntries.map(e => (
                                            <li key={e.id}>
                                                <span className="entry-label">Status:</span> {e.color}<br/>
                                                <span className="entry-label">Start:</span> {new Date(e.startTime).toLocaleString()}<br/>
                                                <span className="entry-label">Ende:</span> {e.endTime ? new Date(e.endTime).toLocaleString() : 'laufend'}
                                            </li>
                                        ))}
                                    </ul>
                                )}
                                {/* NEU: Korrektur-Button pro Tag */}
                                <div className="correction-button-day">
                                    <button onClick={() => openCorrectionModal(ds)}>
                                        Korrektur
                                    </button>
                                </div>
                            </div>
                        );
                    })}
                </div>
            </section>

            {/* Urlaubsantrag */}
            <section className="vacation-section">
                <h3>Urlaub beantragen</h3>
                <form onSubmit={handleVacationSubmit} className="form-vacation">
                    <div className="form-group">
                        <label>Startdatum:</label>
                        <input
                            type="date"
                            name="startDate"
                            value={vacationForm.startDate}
                            onChange={handleVacationChange}
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label>Enddatum:</label>
                        <input
                            type="date"
                            name="endDate"
                            value={vacationForm.endDate}
                            onChange={handleVacationChange}
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
                                    {v.approved ? <span className="approved">Genehmigt</span>
                                        : v.denied ? <span className="denied">Abgelehnt</span>
                                            : <span className="pending">Offen</span>}
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
