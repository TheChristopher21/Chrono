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

// parseHex16: Wird im NFC-Polling genutzt,
// um aus 32-stelligem Hex-String den Usernamen herauszulesen.
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

// getMondayOfWeek: Liefert den Montag der Woche für ein beliebiges Datum
function getMondayOfWeek(date) {
    const copy = new Date(date);
    const day = copy.getDay();
    const diff = day === 0 ? 6 : day - 1;
    copy.setDate(copy.getDate() - diff);
    copy.setHours(0, 0, 0, 0);
    return copy;
}

// addDays: Erhöht oder verringert ein Datum um X Tage
function addDays(date, days) {
    const d = new Date(date);
    d.setDate(d.getDate() + days);
    return d;
}

// Zeitformatierung für Start/EndTimes
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

// Kürzt "HH:mm:ss" auf "HH:mm"
function formatLocalTime(hmsString) {
    if (!hmsString) return '-';
    return hmsString.slice(0, 5);
}

// Konvertiert ein Datum in yyyy-mm-dd
function formatLocalDate(date) {
    return date.toISOString().slice(0, 10);
}

// Formatiert "YYYY-MM-DD" in "DD-MM-YYYY"
function formatDate(dateStr) {
    if (!dateStr) return '-';
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return '-';
    const day = String(d.getDate()).padStart(2, '0');
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const year = d.getFullYear();
    return `${day}-${month}-${year}`;
}

// Hilfsfunktion: Zeit in Minuten seit Mitternacht
function getMinutesSinceMidnight(dateStr) {
    if (!dateStr) return 0;
    const d = new Date(dateStr);
    return d.getHours() * 60 + d.getMinutes();
}

// Stempeldifferenz berechnen (punchOrder 1..4)
function computeDailyDiffValue(dayEntries, expectedWorkHours) {
    const entryStart = dayEntries.find(e => e.punchOrder === 1);
    const entryBreakStart = dayEntries.find(e => e.punchOrder === 2);
    const entryBreakEnd = dayEntries.find(e => e.punchOrder === 3);
    const entryEnd = dayEntries.find(e => e.punchOrder === 4);

    if (entryStart && entryBreakStart && entryBreakEnd && entryEnd) {
        const workStartMins = getMinutesSinceMidnight(entryStart.startTime);
        const workEndMins = getMinutesSinceMidnight(entryEnd.endTime);
        const workDuration = workEndMins - workStartMins;

        const breakStartMins = entryBreakStart.breakStart
            ? parseInt(entryBreakStart.breakStart.slice(0, 2), 10) * 60 +
            parseInt(entryBreakStart.breakStart.slice(3, 5), 10)
            : getMinutesSinceMidnight(entryBreakStart.startTime);

        const breakEndMins = entryBreakEnd.breakEnd
            ? parseInt(entryBreakEnd.breakEnd.slice(0, 2), 10) * 60 +
            parseInt(entryBreakEnd.breakEnd.slice(3, 5), 10)
            : getMinutesSinceMidnight(entryBreakEnd.startTime);

        const breakDuration = breakEndMins - breakStartMins;
        const actualWorked = workDuration - breakDuration;
        const expectedMinutes = expectedWorkHours * 60;
        return actualWorked - expectedMinutes;
    }
    return 0;
}

// Liefert die erwarteten Stunden (defaultExpectedHours oder aus schedule)
function getExpectedHoursForDay(dayObj, userConfig, defaultExpectedHours) {
    if (userConfig?.isHourly) return 0;
    let expectedForDay = defaultExpectedHours;
    if (userConfig && userConfig.weeklySchedule && userConfig.scheduleCycle) {
        const epoch = new Date(2020, 0, 1);
        const diffWeeks = Math.floor((dayObj - epoch) / (7 * 24 * 60 * 60 * 1000));
        const cycleIndex = diffWeeks % userConfig.scheduleCycle;
        const dayOfWeek = dayObj.toLocaleDateString('en-US', { weekday: 'long' }).toLowerCase();

        if (
            Array.isArray(userConfig.weeklySchedule) &&
            userConfig.weeklySchedule[cycleIndex] &&
            userConfig.weeklySchedule[cycleIndex][dayOfWeek] !== undefined
        ) {
            const scheduleValue = Number(userConfig.weeklySchedule[cycleIndex][dayOfWeek]);
            if (!isNaN(scheduleValue)) {
                expectedForDay = scheduleValue;
            }
        }
    }
    return expectedForDay;
}

/* =============================
   COMPONENT
============================= */

function HourlyDashboard() {
    const { t } = useTranslation();
    const { notify } = useNotification();

    const [userProfile, setUserProfile] = useState(null);
    const [allEntries, setAllEntries] = useState([]);
    const [punchMessage, setPunchMessage] = useState('');
    const [lastPunchTime, setLastPunchTime] = useState(0);

    // Für die Monatsanzeige
    const [selectedMonth, setSelectedMonth] = useState(
        new Date(new Date().getFullYear(), new Date().getMonth(), 1)
    );
    const [dailyData, setDailyData] = useState({});

    // Für das Drucken
    const [printModalVisible, setPrintModalVisible] = useState(false);
    const [printStartDate, setPrintStartDate] = useState(formatLocalDate(new Date()));
    const [printEndDate, setPrintEndDate] = useState(formatLocalDate(new Date()));

    // Urlaubsanträge
    const [vacationRequests, setVacationRequests] = useState([]);
    const [vacationForm, setVacationForm] = useState({ startDate: '', endDate: '' });
    const [dailyNotes, setDailyNotes] = useState({});

    // Korrekturanträge
    const [correctionRequests, setCorrectionRequests] = useState([]);
    const [showCorrectionsPanel, setShowCorrectionsPanel] = useState(false);
    const [showAllCorrections, setShowAllCorrections] = useState(false);
    const [selectedCorrectionMonday, setSelectedCorrectionMonday] = useState(getMondayOfWeek(new Date()));

    // Modal zum Erstellen einer Korrektur
    const [showCorrectionModal, setShowCorrectionModal] = useState(false);
    const [correctionDate, setCorrectionDate] = useState("");
    const [correctionData, setCorrectionData] = useState({
        workStart: "",
        breakStart: "",
        breakEnd: "",
        workEnd: "",
        reason: ""
    });

    // Standard-Arbeitszeit
    const defaultExpectedHours = userProfile ? Number(userProfile.dailyWorkHours || 8) : 8;

    /* =============================
       LOAD USER PROFILE
    ============================= */
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

    /* =============================
       LOAD ENTRIES, VACATIONS, CORRECTIONS
    ============================= */
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
            const validEntries = entries.filter(e => [1, 2, 3, 4].includes(e.punchOrder));
            setAllEntries(validEntries);

            // Tagesnotizen
            const noteEntries = entries.filter(e => e.punchOrder === 0 && e.dailyNote);
            if (noteEntries.length > 0) {
                setDailyNotes(prev => {
                    const merged = { ...prev };
                    noteEntries.forEach(noteEntry => {
                        const isoDate = noteEntry.startTime.slice(0, 10);
                        if (noteEntry.dailyNote && noteEntry.dailyNote.trim().length > 0) {
                            merged[isoDate] = noteEntry.dailyNote;
                        }
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

    /* =============================
       NFC Polling (stundenbasiert)
    ============================= */
    const doNfcCheck = useCallback(async () => {
        if (!userProfile?.isHourly) return;
        // Logik, um Punches zu begrenzen
        const todayISO = new Date().toISOString().slice(0, 10);
        const todayEntries = allEntries.filter(e => e.startTime.slice(0, 10) === todayISO);
        if (todayEntries.length >= 2) return;

        try {
            const response = await fetch('http://localhost:8080/api/nfc/read/4');
            if (!response.ok) return;
            const json = await response.json();
            if (json.status === 'no-card' || json.status === 'error') return;
            if (json.status === 'success' && json.data) {
                const cardUser = parseHex16(json.data);
                if (cardUser) {
                    const now = Date.now();
                    if (now - lastPunchTime < 5000) return;
                    setLastPunchTime(now);

                    if (todayEntries.length >= 2) {
                        notify(t("maxStampsReached"));
                        return;
                    }

                    try {
                        await api.post('/api/timetracking/punch', null, {
                            params: { username: cardUser, isHourly: true, currentDate: formatLocalDate(new Date()) }
                        });
                        setPunchMessage(`${t("punchMessage")}: ${cardUser}`);
                        setTimeout(() => setPunchMessage(''), 3000);

                        if (userProfile.username.toLowerCase() === cardUser.toLowerCase()) {
                            fetchEntries();
                        }
                    } catch (err) {
                        console.error("Punch error:", err);
                    }
                }
            }
        } catch (err) {
            console.error("NFC fetch error:", err);
        }
    }, [allEntries, fetchEntries, lastPunchTime, notify, t, userProfile]);

    useEffect(() => {
        const interval = setInterval(() => doNfcCheck(), 2000);
        return () => clearInterval(interval);
    }, [doNfcCheck]);

    /* =============================
       BUILD DAILY DATA
    ============================= */
    useEffect(() => {
        if (!userProfile) {
            setDailyData({});
            return;
        }
        // Filtere Einträge, die zum ausgewählten Monat gehören
        const filteredEntries = allEntries.filter(entry => {
            const d = new Date(entry.startTime);
            return (
                d.getFullYear() === selectedMonth.getFullYear() &&
                d.getMonth() === selectedMonth.getMonth()
            );
        });

        const dayMap = {};
        filteredEntries.forEach(entry => {
            const dayStr = entry.startTime.slice(0, 10);
            if (!dayMap[dayStr]) dayMap[dayStr] = [];
            dayMap[dayStr].push(entry);
        });

        const daily = {};
        Object.keys(dayMap).forEach(dayStr => {
            const entries = dayMap[dayStr];
            let totalMins = 0;
            let startEntry = entries.find(e => e.punchOrder === 1);
            if (!startEntry) {
                startEntry = entries.reduce((prev, curr) =>
                    new Date(prev.startTime) < new Date(curr.startTime) ? prev : curr, entries[0]);
            }
            let endEntry = entries.find(e => e.punchOrder === 4);
            if (!endEntry) {
                endEntry = entries.find(e => e.punchOrder === 2 || e.punchOrder === 3);
            }
            if (startEntry && endEntry) {
                const start = new Date(startEntry.startTime);
                const end = new Date(endEntry.endTime || endEntry.startTime);
                if (end.toISOString().slice(0, 10) !== start.toISOString().slice(0, 10)) {
                    // Falls "Work End" auf den nächsten Tag rutscht
                    const midnight = new Date(start);
                    midnight.setHours(24, 0, 0, 0);
                    totalMins = (midnight - start) / 60000;
                } else {
                    const startMins = getMinutesSinceMidnight(start.toISOString());
                    const endMins = getMinutesSinceMidnight(end.toISOString());
                    totalMins = endMins - startMins;
                }
            }
            daily[dayStr] = {
                totalMins,
                earliestStart: startEntry ? new Date(startEntry.startTime) : null,
                latestEnd: endEntry ? new Date(endEntry.endTime || endEntry.startTime) : null
            };
        });
        setDailyData(daily);
    }, [allEntries, selectedMonth, userProfile]);

    /* =============================
       SUM TOTAL HOURS
    ============================= */
    let totalMinsCalc = 0;
    Object.values(dailyData).forEach(dayObj => {
        totalMinsCalc += dayObj.totalMins;
    });
    const totalHrs = Math.floor(totalMinsCalc / 60);
    const totalRemMins = totalMinsCalc % 60;

    /* =============================
       MANUAL PUNCH
    ============================= */
    async function handleManualPunch() {
        try {
            await api.post('/api/timetracking/punch', null, {
                params: { username: userProfile.username, isHourly: true, currentDate: formatLocalDate(new Date()) }
            });
            setPunchMessage(`${t("manualPunchMessage")} ${userProfile.username}`);
            setTimeout(() => setPunchMessage(''), 3000);
            fetchEntries();
        } catch (err) {
            console.error('Punch-Fehler:', err);
            notify(t("manualPunchError"));
        }
    }

    /* =============================
       VACATIONS
    ============================= */
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

    /* =============================
       PRINT
    ============================= */
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

                const expected = getExpectedHoursForDay(new Date(dayEntries[0].startTime), userProfile, defaultExpectedHours);
                const diffValue = computeDailyDiffValue(dayEntries, expected);
                const diffText = `${diffValue >= 0 ? '+' : '-'}${Math.abs(diffValue)} min`;

                return [dateStr, workStart, breakStart, breakEnd, workEnd, diffText];
            });

        autoTable(doc, {
            head: [["Datum", "Work Start", "Break Start", "Break End", "Work End", "Diff"]],
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

    /* =============================
       CORRECTION REQUESTS
    ============================= */
    // Erstelle eine sortierte Liste (neueste oben)
    const sortedCorrections = (showAllCorrections
            ? correctionRequests
            : correctionRequests.filter(req => {
                // Filter auf die aktuelle Woche
                if (!req.desiredStart) return false;
                const reqDate = new Date(req.desiredStart);
                return reqDate >= selectedCorrectionMonday && reqDate < addDays(selectedCorrectionMonday, 7);
            })
    )
        .slice() // Kopie erstellen
        .sort((a, b) => new Date(b.desiredStart) - new Date(a.desiredStart));

    // z. B. "13-03-2025 - 19-03-2025"
    const correctionWeekLabel = `${formatDate(selectedCorrectionMonday)} - ${formatDate(addDays(selectedCorrectionMonday, 6))}`;

    // Modal öffnen, um Korrektur für ein bestimmtes Datum zu beantragen
    function openCorrectionModal(dateObj) {
        const isoStr = formatLocalDate(dateObj); // "yyyy-mm-dd"
        setCorrectionDate(isoStr);
        setCorrectionData({
            workStart: "",
            breakStart: "",
            breakEnd: "",
            workEnd: "",
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

    function formatTimeShort(dateObj) {
        if (!dateObj) return '-';
        const d = new Date(dateObj);
        if (isNaN(d.getTime())) return '-';
        return d.toLocaleTimeString('de-DE', {
            hour: '2-digit',
            minute: '2-digit',
            timeZone: 'Europe/Berlin'
        });
    }

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
            fetchEntries(); // oder was immer du brauchst, um die Ansicht zu aktualisieren
        } catch (err) {
            console.error("Error saving daily note:", err);
            notify("Fehler beim Speichern der Tagesnotiz.");
        }
    }



    /* =============================
       RENDER
    ============================= */
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

            {/* Punch per Button */}
            <section className="punch-section">
                <h3>{t("manualPunchTitle")}</h3>
                <button onClick={handleManualPunch}>{t("manualPunchButton")}</button>
            </section>

            {/* Monatsübersicht */}
            <section className="monthly-overview">
                <h3>{t("monthlyOverview")}</h3>
                <div className="month-navigation">
                    <button onClick={() => setSelectedMonth(prev => addDays(prev, -30))}>
                        ← {t("prevMonth")}
                    </button>
                    <span>
          {selectedMonth.toLocaleDateString('de-DE', { month: 'long', year: 'numeric' })}
        </span>
                    <button onClick={() => setSelectedMonth(prev => addDays(prev, 30))}>
                        {t("nextMonth")} →
                    </button>
                </div>
                {Object.keys(dailyData).length === 0 ? (
                    <p>{t("noEntries")}</p>
                ) : (
                    <>
                        <div className="month-total">
                            {t("totalHours")}: <strong>{totalHrs} {t("hours")} {totalRemMins} {t("minutes")}</strong>
                        </div>
                        <table className="daily-summary-table">
                            <thead>
                            <tr>
                                <th>{t("weekday")}</th>
                                <th>{t("date")}</th>
                                <th>{t("workTime")}</th>
                                <th>Notiz</th>
                                <th>Korrektur</th>
                            </tr>
                            </thead>
                            <tbody>
                            {Object.entries(dailyData)
                                .sort(([a], [b]) => new Date(a) - new Date(b))
                                .map(([isoDate, info]) => {
                                    const displayDate = formatDate(isoDate);
                                    const dateObj = new Date(isoDate);
                                    const weekday = dateObj.toLocaleDateString('de-DE', { weekday: 'long' });
                                    const earliest = info.earliestStart ? formatTimeShort(info.earliestStart) : '-';
                                    const latest = info.latestEnd ? formatTimeShort(info.latestEnd) : '-';
                                    const hrs = Math.floor(info.totalMins / 60);
                                    const remMins = info.totalMins % 60;
                                    return (
                                        <tr key={isoDate}>
                                            <td>{weekday}</td>
                                            <td>{displayDate}</td>
                                            <td>
                                                {earliest} {t("to")} {latest} ({hrs} {t("hours")} {remMins} {t("minutes")})
                                            </td>
                                            <td>
                        <textarea
                            placeholder="Tagesnotiz"
                            value={dailyNotes[isoDate] || ''}
                            onChange={(e) =>
                                setDailyNotes({ ...dailyNotes, [isoDate]: e.target.value })
                            }
                            rows="2"
                        />
                                                <button onClick={() => handleSaveNote(isoDate)}>Speichern</button>
                                            </td>
                                            <td>
                                                {/* Button zum Öffnen des Korrektur-Moduls */}
                                                <button onClick={() => openCorrectionModal(dateObj)}>Korrektur</button>
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </>
                )}
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

            {/* Korrektur-Accordion */}
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
                                        <strong>Work Start:</strong> {formatLocalTime(req.workStart)}<br />
                                        {/* Für stundenlöhner keine Pausenfelder */}
                                        {!userProfile.isHourly && (
                                            <>
                                                <strong>Break Start:</strong> {formatLocalTime(req.breakStart)}<br />
                                                <strong>Break End:</strong> {formatLocalTime(req.breakEnd)}<br />
                                            </>
                                        )}
                                        <strong>Work End:</strong> {formatLocalTime(req.workEnd)}
                                    </div>
                                );
                            })
                        )}
                    </div>
                )}
            </section>

            {/* Modal für neue Korrekturanträge */}
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
                            {/* Nur anzeigen, wenn es kein Stundenlöhner ist */}
                            {!userProfile.isHourly && (
                                <>
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
                                </>
                            )}
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
