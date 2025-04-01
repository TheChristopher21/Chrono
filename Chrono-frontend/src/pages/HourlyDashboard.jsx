import { useState, useEffect, useCallback } from 'react';
import { jsPDF } from 'jspdf';
import autoTable from 'jspdf-autotable';
import Navbar from '../components/Navbar';
import api from '../utils/api';
import { useNotification } from '../context/NotificationContext';
import { useTranslation } from '../context/LanguageContext';
import '../styles/HourlyDashboard.css';
import VacationCalendar from '../components/VacationCalendar';

/* =============================
   HELPER FUNCTIONS
============================= */

// NFC-Parsing: Liest aus 32-stelligem Hex-String den Usernamen
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





function getMondayOfWeek(date) {
    const copy = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
    const day = copy.getUTCDay();
    const diff = (day === 0) ? 6 : (day - 1);
    copy.setUTCDate(copy.getUTCDate() - diff);
    copy.setUTCHours(0, 0, 0, 0);

    return copy;
}


function addDays(date, days) {
    const d = new Date(date);
    d.setDate(d.getDate() + days);
    return d;
}

// Liefert Datum im Format yyyy-mm-dd
function formatLocalDate(date) {
    return date.toISOString().slice(0, 10);
}

// Wandelt "YYYY-MM-DD" in "DD-MM-YYYY" um
function formatDate(dateStr) {
    if (!dateStr) return '-';
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return '-';
    const day = String(d.getDate()).padStart(2, '0');
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const year = d.getFullYear();
    return `${day}-${month}-${year}`;
}

// Wandelt einen Datumsstring in "HH:mm" um (für die Eingabefelder)
function toTimeInput(dateStr) {
    if (!dateStr) return "";
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return "";
    const hh = String(d.getHours()).padStart(2, '0');
    const mm = String(d.getMinutes()).padStart(2, '0');
    return `${hh}:${mm}`;
}

// Formatiert einen Zeitstempel zur Anzeige
function formatTime(dateStr) {
    if (!dateStr) return '-';
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return '-';
    return d.toLocaleTimeString('de-DE', {
        hour: '2-digit',
        minute: '2-digit',
        timeZone: 'Europe/Berlin'
    });
}

function parseTimeToMinutes(timeStr) {
    if (!timeStr) return 0;
    const parts = timeStr.split(':');
    const hours = parseInt(parts[0], 10);
    const minutes = parseInt(parts[1], 10);
    return hours * 60 + minutes;
}

function getMinutesSinceMidnight(dateStr) {
    if (!dateStr) return 0;
    const d = new Date(dateStr);
    return d.getHours() * 60 + d.getMinutes();
}

/**
 * Berechnet die Arbeitsminuten eines Tages (Work Start bis Work End abzüglich Pausen).
 */
function computeDayTotalMinutes(dayEntries) {
    const entryStart = dayEntries.find(e => e.punchOrder === 1);
    let entryEnd = dayEntries.find(e => e.punchOrder === 4);
    if (!entryEnd) {
        entryEnd = dayEntries.find(e => e.punchOrder === 2 || e.punchOrder === 3);
    }
    if (!entryStart || !entryEnd) return 0;

    const start = new Date(entryStart.startTime);
    const end = new Date(entryEnd.endTime || entryEnd.startTime);
    let totalMins = 0;

    if (end.toISOString().slice(0, 10) !== start.toISOString().slice(0, 10)) {
        const midnight = new Date(start);
        midnight.setHours(24, 0, 0, 0);
        totalMins = (midnight - start) / 60000;
    } else {
        const startM = getMinutesSinceMidnight(start.toISOString());
        const endM = getMinutesSinceMidnight(end.toISOString());
        totalMins = endM - startM;
    }

    const breakStart = dayEntries.find(e => e.punchOrder === 2);
    const breakEnd = dayEntries.find(e => e.punchOrder === 3);
    if (breakStart && breakEnd) {
        let breakStartMins = breakStart.breakStart
            ? parseTimeToMinutes(breakStart.breakStart)
            : getMinutesSinceMidnight(breakStart.startTime);
        let breakEndMins = breakEnd.breakEnd
            ? parseTimeToMinutes(breakEnd.breakEnd)
            : getMinutesSinceMidnight(breakEnd.startTime);
        if (breakEndMins < breakStartMins) {
            breakEndMins += 24 * 60;
        }
        totalMins -= (breakEndMins - breakStartMins);
    }
    return Math.max(0, totalMins);
}

/**
 * Summiert alle Arbeitsminuten in einem Datumsbereich.
 */
function computeTotalMinutesInRange(allEntries, startDate, endDate) {
    const filtered = allEntries.filter(e => {
        const d = new Date(e.startTime);
        return d >= startDate && d <= endDate;
    });
    const dayMap = {};
    filtered.forEach(entry => {
        const ds = entry.startTime.slice(0, 10);
        if (!dayMap[ds]) dayMap[ds] = [];
        dayMap[ds].push(entry);
    });
    let total = 0;
    Object.keys(dayMap).forEach(ds => {
        total += computeDayTotalMinutes(dayMap[ds]);
    });
    return total;
}

function HourlyDashboard() {
    const { t } = useTranslation();
    const { notify } = useNotification();

    const [userProfile, setUserProfile] = useState(null);
    const [allEntries, setAllEntries] = useState([]);
    const [punchMessage, setPunchMessage] = useState('');
    const [lastPunchTime, setLastPunchTime] = useState(0);

    // Wochenansicht
    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));

    // Drucken
    const [printModalVisible, setPrintModalVisible] = useState(false);
    const [printStartDate, setPrintStartDate] = useState(formatLocalDate(new Date()));
    const [printEndDate, setPrintEndDate] = useState(formatLocalDate(new Date()));

    // Urlaub
    const [vacationRequests, setVacationRequests] = useState([]);
    const [vacationForm, setVacationForm] = useState({ startDate: '', endDate: '' });

    // Korrekturanträge
    const [correctionRequests, setCorrectionRequests] = useState([]);
    const [showCorrectionsPanel, setShowCorrectionsPanel] = useState(false);
    const [showAllCorrections, setShowAllCorrections] = useState(false);
    const [selectedCorrectionMonday, setSelectedCorrectionMonday] = useState(getMondayOfWeek(new Date()));

    // Korrektur-Modal
    const [showCorrectionModal, setShowCorrectionModal] = useState(false);
    const [correctionDate, setCorrectionDate] = useState("");
    const [correctionData, setCorrectionData] = useState({
        workStart: "",
        breakStart: "",
        breakEnd: "",
        workEnd: "",
        reason: ""
    });
    async function handleSaveNote(isoDate) {
        try {
            await api.post('/api/timetracking/daily-note', null, {
                params: {
                    username: userProfile.username,
                    date: isoDate,
                    note: dailyNotes[isoDate] || ""
                }
            });
            notify("Tagesnotiz gespeichert.");
            fetchEntries();
        } catch (err) {
            console.error("Fehler beim Speichern der Tagesnotiz:", err);
            notify("Fehler beim Speichern der Tagesnotiz.");
        }
    }


    // Zustand für Sichtbarkeit des Notiz-Editors pro Tag (Key: yyyy-mm-dd)
    const [noteEditVisibility, setNoteEditVisibility] = useState({});
    // Tägliche Notizen
    const [dailyNotes, setDailyNotes] = useState({});

    useEffect(() => {
        async function fetchProfile() {
            try {
                const res = await api.get('/api/auth/me');
                const profile = res.data;
                if (!profile.weeklySchedule) {
                    profile.weeklySchedule = [
                        { monday: 1, tuesday: 2, wednesday: 3, thursday: 4, friday: 5, saturday: 0, sunday: 0 }
                    ];
                    profile.scheduleCycle = 1;
                }
                setUserProfile(profile);
            } catch (err) {
                console.error(t('personalData.errorLoading'), err);
            }
        }
        fetchProfile();
    }, [t]);

    useEffect(() => {
        if (userProfile) {
            fetchEntries();
            fetchVacations();
            fetchCorrections();
        }
    }, [userProfile]);

    const fetchEntries = useCallback(async () => {
        if (!userProfile) return;
        try {
            const res = await api.get(`/api/timetracking/history?username=${userProfile.username}`);
            const entries = res.data || [];
            // Filtere die regulären Zeiteinträge (punchOrder 1..4)
            const validEntries = entries.filter(e => [1, 2, 3, 4].includes(e.punchOrder));
            setAllEntries(validEntries);
            // Lade auch die Notiz-Einträge (punchOrder 0) und speichere sie im dailyNotes-State
            const noteEntries = entries.filter(e => e.punchOrder === 0 && e.dailyNote && e.dailyNote.trim().length > 0);
            if (noteEntries.length > 0) {
                setDailyNotes(prev => {
                    const merged = { ...prev };
                    noteEntries.forEach(noteEntry => {
                        const isoDate = noteEntry.startTime.slice(0, 10); // Datum im Format "yyyy-mm-dd"
                        merged[isoDate] = noteEntry.dailyNote;
                    });
                    return merged;
                });
            }
        } catch (err) {
            console.error("Error loading time entries", err);
        }
    }, [userProfile]);

    async function fetchVacations() {
        try {
            const res = await api.get('/api/vacation/my');
            setVacationRequests(res.data || []);
        } catch (err) {
            console.error("Error loading vacation requests", err);
        }
    }

    async function fetchCorrections() {
        try {
            const res = await api.get(`/api/correction/my?username=${userProfile.username}`);
            setCorrectionRequests(res.data || []);
        } catch (err) {
            console.error('Error loading correction requests', err);
        }
    }
// Am besten in handleManualPunch oder einem ähnlichen onClick-Handler:
    async function handleManualPunch() {
        try {
            await api.post('/api/timetracking/punch', null, {
                params: { username: userProfile.username } // Nur username!
            });
            setPunchMessage(`Eingestempelt: ${userProfile.username}`);
            setTimeout(() => setPunchMessage(''), 3000);
            // Anschließend aktuelle Einträge neu laden
            fetchEntries();
        } catch (err) {
            console.error('Punch-Fehler:', err);
            notify("Fehler beim Stempeln.");
        }
    }

    // HourlyDashboard.jsx (NFC-Check)
    async function doNfcCheck() {
        try {
            const response = await fetch('http://localhost:8080/api/nfc/read/1');
            if (!response.ok) return;
            const json = await response.json();
            if (json.status === 'no-card' || json.status === 'error') return;
            if (json.status === 'success') {
                const cardUser = parseHex16(json.data);
                if (cardUser) {
                    // ...
                    await api.post('/api/timetracking/punch', null, {
                        params: { username: cardUser } // Nur username
                    });
                    // ...
                }
            }
        } catch (err) {
            console.error("Punch error:", err);
        }
    }


    useEffect(() => {
        const interval = setInterval(() => doNfcCheck(), 2000);
        return () => clearInterval(interval);
    }, [doNfcCheck]);

// HourlyDashboard.jsx
    async function handleManualPunch() {
        try {
            await api.post('/api/timetracking/punch', null, {
                params: { username: userProfile.username } // Nur username
            });
            setPunchMessage(`Eingestempelt: ${userProfile.username}`);
            setTimeout(() => setPunchMessage(''), 3000);
            fetchEntries(); // aktualisiere die Liste
        } catch (err) {
            console.error('Punch-Fehler:', err);
            notify('Fehler beim Stempeln'); // oder t("manualPunchError")
        }
    }


    // Gesamtsumme (aktuelle Woche)
    const endOfWeek = addDays(selectedMonday, 6);
    const weeklyTotalMins = computeTotalMinutesInRange(allEntries, selectedMonday, endOfWeek);
    const weeklyHrs = Math.floor(weeklyTotalMins / 60);
    const weeklyRemMins = weeklyTotalMins % 60;

    // Gesamtsumme (Monat) basierend auf selectedMonday
    const year = selectedMonday.getFullYear();
    const month = selectedMonday.getMonth();
    const firstOfMonth = new Date(year, month, 1, 0, 0, 0);
    const lastOfMonth = new Date(year, month + 1, 0, 23, 59, 59);
    const monthlyTotalMins = computeTotalMinutesInRange(allEntries, firstOfMonth, lastOfMonth);
    const monthlyHrs = Math.floor(monthlyTotalMins / 60);
    const monthlyRemMins = monthlyTotalMins % 60;

    const handlePrintReport = () => {
        if (!printStartDate || !printEndDate) {
            notify(t("printReportError"));
            return;
        }
        const doc = new jsPDF("p", "mm", "a4");
        doc.setFontSize(12);
        doc.text(`${t("printReportTitle")} - ${userProfile?.username}`, 10, 15);

        const filteredEntries = allEntries.filter(e => {
            const entryDate = new Date(e.startTime);
            return entryDate >= new Date(printStartDate) && entryDate <= new Date(printEndDate);
        });

        const grouped = {};
        filteredEntries.forEach(entry => {
            const ds = new Date(entry.startTime).toLocaleDateString("de-DE");
            if (!grouped[ds]) grouped[ds] = [];
            grouped[ds].push(entry);
        });

        const tableBody = Object.keys(grouped)
            .sort((a, b) => new Date(a) - new Date(b))
            .map(dateStr => {
                const dayEntries = grouped[dateStr].sort((a, b) => a.punchOrder - b.punchOrder);

                const eStart = dayEntries.find(e => e.punchOrder === 1);
                const eBreakS = dayEntries.find(e => e.punchOrder === 2);
                const eBreakE = dayEntries.find(e => e.punchOrder === 3);
                const eEnd = dayEntries.find(e => e.punchOrder === 4);

                const workStart = eStart ? formatTime(eStart.startTime) : "-";
                const breakStart = eBreakS
                    ? (eBreakS.breakStart ? formatTime(eBreakS.breakStart) : formatTime(eBreakS.startTime))
                    : "-";
                const breakEnd = eBreakE
                    ? (eBreakE.breakEnd ? formatTime(eBreakE.breakEnd) : formatTime(eBreakE.startTime))
                    : "-";
                const workEnd = eEnd ? formatTime(eEnd.endTime) : "-";

                let dayMinutes = computeDayTotalMinutes(dayEntries);
                const sign = dayMinutes >= 0 ? "+" : "-";
                const diffText = `${sign}${Math.abs(dayMinutes)} min`;

                // Bestimme das ISO-Datum (yyyy-mm-dd) anhand des ersten Eintrags
                const isoDate = new Date(dayEntries[0].startTime).toISOString().slice(0, 10);
                const note = dailyNotes[isoDate] || "";

                // Rückgabe: zusätzlich die Notiz als extra Spalte
                return [dateStr, workStart, breakStart, breakEnd, workEnd, diffText, note];
            });

        autoTable(doc, {
            head: [["Datum", "Work Start", "Break Start", "Break End", "Work End", "Diff", "Notiz"]],
            body: tableBody,
            startY: 25,
            styles: {
                fontSize: 9,
                cellPadding: 3
            },
            headStyles: {
                fillColor: [0, 123, 255],
                textColor: 255,
                fontStyle: "bold"
            },
            theme: "grid"
        });

        const dataUri = doc.output("datauristring");
        const pdfBase64 = dataUri.split(",")[1];

        try {
            window.electron.ipcRenderer.invoke("saveAndOpenPDF", pdfBase64);
        } catch (err) {
            console.error("Fehler beim Speichern/Öffnen des PDFs:", err);
            notify("Fehler beim Öffnen des Berichts.");
        }
        setPrintModalVisible(false);
    };

    // Urlaub
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
            notify(t("vacationSubmitSuccess"));
            fetchVacations();
        } catch (err) {
            console.error('Error submitting vacation request:', err);
            notify(t("vacationSubmitError"));
        }
    }

    // Korrekturanträge
    const sortedCorrections = (showAllCorrections
            ? correctionRequests
            : correctionRequests.filter(req => {
                if (!req.desiredStart) return false;
                const reqDate = new Date(req.desiredStart);
                return reqDate >= selectedCorrectionMonday && reqDate < addDays(selectedCorrectionMonday, 7);
            })
    )
        .slice()
        .sort((a, b) => new Date(b.desiredStart) - new Date(a.desiredStart));

    /**
     * Öffnet das Korrektur-Modal und füllt es automatisch mit den bereits
     * gestempelten Zeiten des gewählten Tages (falls vorhanden).
     */
    function openCorrectionModal(dateObj) {
        const isoStr = formatLocalDate(dateObj);
        const dayEntries = allEntries.filter(e => e.startTime.slice(0, 10) === isoStr);

        let workStartVal = "";
        let breakStartVal = "";
        let breakEndVal = "";
        let workEndVal = "";

        const ws = dayEntries.find(e => e.punchOrder === 1);
        if (ws) {
            workStartVal = toTimeInput(ws.startTime);
        }
        const bs = dayEntries.find(e => e.punchOrder === 2);
        if (bs) {
            breakStartVal = toTimeInput(bs.breakStart || bs.startTime);
        }
        const be = dayEntries.find(e => e.punchOrder === 3);
        if (be) {
            breakEndVal = toTimeInput(be.breakEnd || be.startTime);
        }
        const we = dayEntries.find(e => e.punchOrder === 4);
        if (we) {
            workEndVal = toTimeInput(we.endTime || we.startTime);
        }

        setCorrectionDate(isoStr);
        setCorrectionData({
            workStart: workStartVal,
            breakStart: breakStartVal,
            breakEnd: breakEndVal,
            workEnd: workEndVal,
            reason: ""
        });
        setShowCorrectionModal(true);
    }

    function handleCorrectionInputChange(e) {
        const { name, value } = e.target;
        setCorrectionData(prev => ({ ...prev, [name]: value }));
    }

    async function handleCorrectionSubmit(e) {
        e.preventDefault();
        if (!correctionData.workStart || !correctionData.workEnd) {
            notify("Bitte füllen Sie Work Start und Work End aus.");
            return;
        }
        const desiredStart = `${correctionDate}T${correctionData.workStart}`;
        const desiredEnd = `${correctionDate}T${correctionData.workEnd}`;
        try {
            await api.post('/api/correction/create-full', null, {
                params: {
                    username: userProfile.username,
                    date: correctionDate,
                    workStart: correctionData.workStart,
                    breakStart: correctionData.breakStart,
                    breakEnd: correctionData.breakEnd,
                    workEnd: correctionData.workEnd,
                    reason: correctionData.reason,
                    desiredStart: desiredStart,
                    desiredEnd: desiredEnd
                }
            });
            notify("Korrekturantrag erfolgreich gestellt.");
            fetchCorrections();
            setShowCorrectionModal(false);
        } catch (err) {
            console.error("Fehler beim Absenden des Korrekturantrags:", err);
            notify("Fehler beim Absenden des Korrekturantrags.");
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
    const weekStrs = weekDates.map(d => formatLocalDate(d));
    const weeklyEntries = allEntries.filter(track => {
        const localDate = track.startTime.slice(0, 10);
        return weekStrs.includes(localDate);
    });
    const weeklyDayMap = {};
    weeklyEntries.forEach(entry => {
        const ds = entry.startTime.slice(0, 10);
        if (!weeklyDayMap[ds]) weeklyDayMap[ds] = [];
        weeklyDayMap[ds].push(entry);
    });

    if (!userProfile) {
        return (
            <div className="user-dashboard">
                <Navbar />
                <p>Loading...</p>
            </div>
        );
    }

    return (
        <div className="user-dashboard">
            <Navbar />

            <header className="dashboard-header">
                <h2>{t("title")} (Stundenbasiert)</h2>
                <div className="personal-info">
                    <p>
                        <strong>{t("usernameLabel")}:</strong> {userProfile.username}
                    </p>
                </div>
            </header>

            {punchMessage && <div className="punch-message">{punchMessage}</div>}

            {/* Manuelles Stempeln */}
            <section className="punch-section">
                <h3>{t("manualPunchTitle")}</h3>
                <button onClick={handleManualPunch}>{t("manualPunchButton")}</button>
            </section>

            {/* Wochenübersicht */}
            <section className="weekly-overview">
                <h3>Wochenübersicht</h3>
                <div className="week-navigation">
                    <button onClick={handlePrevWeek}>← Vorige Woche</button>
                    <input
                        type="date"
                        onChange={handleWeekJump}
                        value={formatLocalDate(selectedMonday)}
                    />
                    <button onClick={handleNextWeek}>Nächste Woche →</button>
                </div>

                <div className="weekly-monthly-totals">
                    <p>Gesamtstunden (aktuelle Woche): {weeklyHrs}h {weeklyRemMins}min</p>
                    <p>Gesamtstunden (Monat): {monthlyHrs}h {monthlyRemMins}min</p>
                </div>

                <div className="week-display">
                    {weekDates.map((dayDate, index) => {
                        const isoDay = formatLocalDate(dayDate);
                        const dayEntries = weeklyDayMap[isoDay] || [];
                        const dayName = dayDate.toLocaleDateString('de-DE', { weekday: 'long' });
                        const formattedDay = formatDate(isoDay);

                        dayEntries.sort((a, b) => a.punchOrder - b.punchOrder);

                        const workStart = dayEntries.find(e => e.punchOrder === 1);
                        const breakStart = dayEntries.find(e => e.punchOrder === 2);
                        const breakEnd = dayEntries.find(e => e.punchOrder === 3);
                        const workEnd = dayEntries.find(e => e.punchOrder === 4);

                        return (
                            <div key={index} className="week-day-card">
                                <div className="week-day-header">
                                    <strong>{dayName}, {formattedDay}</strong>
                                </div>
                                <div className="week-day-content">
                                    {dayEntries.length === 0 ? (
                                        <p>Keine Einträge</p>
                                    ) : (
                                        <ul>
                                            <li>
                                                <strong>Work Start:</strong>{" "}
                                                {workStart ? formatTime(workStart.startTime) : "-"}
                                            </li>
                                            <li>
                                                <strong>Break Start:</strong>{" "}
                                                {breakStart
                                                    ? breakStart.breakStart
                                                        ? formatTime(breakStart.breakStart)
                                                        : formatTime(breakStart.startTime)
                                                    : "-"}
                                            </li>
                                            <li>
                                                <strong>Break End:</strong>{" "}
                                                {breakEnd
                                                    ? breakEnd.breakEnd
                                                        ? formatTime(breakEnd.breakEnd)
                                                        : formatTime(breakEnd.startTime)
                                                    : "-"}
                                            </li>
                                            <li>
                                                <strong>Work End:</strong>{" "}
                                                {workEnd
                                                    ? formatTime(workEnd.endTime || workEnd.startTime)
                                                    : "-"}
                                            </li>
                                        </ul>
                                    )}
                                </div>
                                {/* Notizen-Bereich */}
                                <div className="daily-note-section">
                                    {noteEditVisibility[isoDay] ? (
                                        <>
                                            <textarea
                                                value={dailyNotes[isoDay] || ''}
                                                onChange={(e) =>
                                                    setDailyNotes({ ...dailyNotes, [isoDay]: e.target.value })
                                                }
                                            />
                                            <button onClick={() => {
                                                handleSaveNote(isoDay);
                                                setNoteEditVisibility({ ...noteEditVisibility, [isoDay]: false });
                                            }}>
                                                Speichern
                                            </button>
                                        </>
                                    ) : (
                                        <>
                                            <button onClick={() =>
                                                setNoteEditVisibility({ ...noteEditVisibility, [isoDay]: true })
                                            }>
                                                {dailyNotes[isoDay] ? "Notitzen bearbeiten" : "Notitzen hinzufügen"}
                                            </button>
                                            {dailyNotes[isoDay] && (
                                                <div className="note-display">
                                                    {dailyNotes[isoDay]}
                                                </div>
                                            )}
                                        </>
                                    )}
                                </div>
                                {/* Korrektur-Button */}
                                <div className="correction-button-row">
                                    <button onClick={() => openCorrectionModal(dayDate)}>
                                        Korrektur anfragen
                                    </button>
                                </div>
                            </div>
                        );
                    })}
                </div>
            </section>

            {/* Drucken */}
            <div className="print-report-container">
                <button onClick={() => setPrintModalVisible(true)}>{t("printReportButton")}</button>
            </div>
            {printModalVisible && (
                <div className="modal-overlay">
                    <div className="modal-content">
                        <h3>{t("selectPeriod")}</h3>
                        <div className="form-group">
                            <label>{t("startDate")}:</label>
                            <input
                                type="date"
                                value={printStartDate}
                                onChange={(e) => setPrintStartDate(e.target.value)}
                            />
                        </div>
                        <div className="form-group">
                            <label>{t("endDate")}:</label>
                            <input
                                type="date"
                                value={printEndDate}
                                onChange={(e) => setPrintEndDate(e.target.value)}
                            />
                        </div>
                        <div className="modal-buttons">
                            <button onClick={handlePrintReport}>{t("printReportButton")}</button>
                            <button onClick={() => setPrintModalVisible(false)}>{t("cancel")}</button>
                        </div>
                    </div>
                </div>
            )}

            {/* Urlaub */}
            <section className="vacation-section">
                <h3>{t("vacationTitle")}</h3>
                <form onSubmit={handleVacationSubmit} className="form-vacation">
                    <div className="form-group">
                        <label>{t("startDate")}:</label>
                        <input
                            type="date"
                            name="startDate"
                            value={vacationForm.startDate}
                            onChange={(e) => setVacationForm({ ...vacationForm, startDate: e.target.value })}
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label>{t("endDate")}:</label>
                        <input
                            type="date"
                            name="endDate"
                            value={vacationForm.endDate}
                            onChange={(e) => setVacationForm({ ...vacationForm, endDate: e.target.value })}
                            required
                        />
                    </div>
                    <button type="submit">{t("vacationSubmitButton")}</button>
                </form>
                <div className="vacation-history">
                    <h4>{t("myVacations")}</h4>
                    {vacationRequests.length === 0 ? (
                        <p>{t("noVacations")}</p>
                    ) : (
                        <ul>
                            {vacationRequests.map((v) => (
                                <li key={v.id}>
                                    {formatDate(v.startDate)} {t("to")} {formatDate(v.endDate)}{' '}
                                    {v.approved ? (
                                        <span className="approved">{t("approved")}</span>
                                    ) : v.denied ? (
                                        <span className="denied">{t("denied")}</span>
                                    ) : (
                                        <span className="pending">{t("pending")}</span>
                                    )}
                                </li>
                            ))}
                        </ul>
                    )}
                </div>
                <div className="calendar-section">
                    <h4>{t("vacationCalendarTitle")}</h4>
                    <VacationCalendar vacationRequests={vacationRequests.filter(v => v.approved)} />
                </div>
            </section>

            {/* Korrekturanträge (Accordion) */}
            <section className="correction-panel">
                <div className="corrections-header" onClick={() => setShowCorrectionsPanel(prev => !prev)}>
                    <h3>Korrekturanträge</h3>
                    <span className="toggle-icon">{showCorrectionsPanel ? "▲" : "▼"}</span>
                </div>
                {showCorrectionsPanel && (
                    <div className="corrections-content">
                        {!showAllCorrections && (
                            <div className="week-navigation corrections-nav">
                                <button onClick={() => setSelectedCorrectionMonday(prev => addDays(prev, -7))}>← Prev</button>
                                <span className="week-label">
                                    {formatDate(selectedCorrectionMonday)} - {formatDate(addDays(selectedCorrectionMonday, 6))}
                                </span>
                                <button onClick={() => setSelectedCorrectionMonday(prev => addDays(prev, 7))}>Next →</button>
                            </div>
                        )}
                        <div className="toggle-all-button">
                            <button onClick={() => setShowAllCorrections(prev => !prev)}>
                                {showAllCorrections ? "Nur aktuelle Woche" : "Alle anzeigen"}
                            </button>
                        </div>
                        {sortedCorrections.length === 0 ? (
                            <p>Keine Korrekturanträge vorhanden</p>
                        ) : (
                            [...sortedCorrections].map(req => {
                                const d = new Date(req.desiredStart);
                                return (
                                    <div key={req.id} className="single-correction">
                                        {formatDate(d)} –{" "}
                                        {req.approved ? (
                                            <span className="approved">{t("approved") || "Bestätigt"}</span>
                                        ) : req.denied ? (
                                            <span className="denied">{t("denied") || "Abgelehnt"}</span>
                                        ) : (
                                            <span className="pending">{t("pending") || "Offen"}</span>
                                        )}
                                        <br />
                                        {req.reason}
                                        <br />
                                        <strong>Work Start:</strong> {req.workStart || "-"}<br />
                                        {!userProfile.isHourly && (
                                            <>
                                                <strong>Break Start:</strong> {req.breakStart || "-"}<br />
                                                <strong>Break End:</strong> {req.breakEnd || "-"}<br />
                                            </>
                                        )}
                                        <strong>Work End:</strong> {req.workEnd || "-"}
                                    </div>
                                );
                            })
                        )}
                    </div>
                )}
            </section>

            {/* Modal: Korrekturantrag erstellen */}
            {showCorrectionModal && (
                <div className="modal-overlay">
                    <div className="modal-content">
                        <h3>Korrekturantrag für {correctionDate}</h3>
                        <form onSubmit={handleCorrectionSubmit}>
                            <div className="form-group">
                                <label>Work Start:</label>
                                <input
                                    type="time"
                                    name="workStart"
                                    value={correctionData.workStart}
                                    onChange={handleCorrectionInputChange}
                                    required
                                />
                            </div>
                            <div className="form-group">
                                <label>Break Start:</label>
                                <input
                                    type="time"
                                    name="breakStart"
                                    value={correctionData.breakStart}
                                    onChange={handleCorrectionInputChange}
                                />
                            </div>
                            <div className="form-group">
                                <label>Break End:</label>
                                <input
                                    type="time"
                                    name="breakEnd"
                                    value={correctionData.breakEnd}
                                    onChange={handleCorrectionInputChange}
                                />
                            </div>
                            <div className="form-group">
                                <label>Work End:</label>
                                <input
                                    type="time"
                                    name="workEnd"
                                    value={correctionData.workEnd}
                                    onChange={handleCorrectionInputChange}
                                    required
                                />
                            </div>
                            <div className="form-group">
                                <label>Grund (Reason):</label>
                                <textarea
                                    name="reason"
                                    value={correctionData.reason}
                                    onChange={handleCorrectionInputChange}
                                    required
                                ></textarea>
                            </div>
                            <div className="modal-buttons">
                                <button type="submit">Antrag senden</button>
                                <button type="button" onClick={() => setShowCorrectionModal(false)}>
                                    Abbrechen
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}

export default HourlyDashboard;
