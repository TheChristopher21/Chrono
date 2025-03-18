import { useState, useEffect, useRef } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../utils/api';
import Navbar from '../components/Navbar';
import VacationCalendar from '../components/VacationCalendar';
import { jsPDF } from 'jspdf';
import '../styles/UserDashboard.css';
import { useNotification } from '../context/NotificationContext';
import { useTranslation } from '../context/LanguageContext';
import HourlyDashboard from './HourlyDashboard';
import autoTable from "jspdf-autotable";

const getMondayOfWeek = (date) => {
    const copy = new Date(date);
    const day = copy.getDay();
    const diff = day === 0 ? 6 : day - 1;
    copy.setDate(copy.getDate() - diff);
    copy.setHours(0, 0, 0, 0);
    return copy;
};

const addDays = (date, days) => {
    const d = new Date(date);
    d.setDate(d.getDate() + days);
    return d;
};

const parseHex16 = (hexString) => {
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
};

const formatTime = (dateStr) => {
    const d = new Date(dateStr);
    return isNaN(d.getTime())
        ? '-'
        : d.toLocaleTimeString('de-DE', {
            hour: '2-digit',
            minute: '2-digit',
            timeZone: 'Europe/Berlin'
        });
};

const formatLocalDate = (date) => {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
};

const formatDate = (dateStr) => {
    const date = new Date(dateStr);
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();
    return `${day}-${month}-${year}`;
};

const getMinutesSinceMidnight = (datetimeStr) => {
    if (!datetimeStr) return 0;
    const d = new Date(datetimeStr);
    return d.getHours() * 60 + d.getMinutes();
};

const computeDailyDiffValue = (dayEntries, expectedWorkHours) => {
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
};

const computeDailyDiff = (dayEntries, expectedWorkHours) => {
    const diff = computeDailyDiffValue(dayEntries, expectedWorkHours);
    const sign = diff >= 0 ? '+' : '';
    return `${sign}${Math.round(diff)} min`;
};

const getExpectedHoursForDay = (dayObj, userConfig, defaultExpectedHours) => {
    if (userConfig?.isHourly) return 0;
    let expectedForDay = defaultExpectedHours;
    if (userConfig && userConfig.weeklySchedule && userConfig.scheduleCycle) {
        const epoch = new Date(2020, 0, 1);
        const diffWeeks = Math.floor((dayObj - epoch) / (7 * 24 * 60 * 60 * 1000));
        const cycleIndex = diffWeeks % userConfig.scheduleCycle;
        const dayOfWeek = dayObj.toLocaleDateString('en-US', { weekday: 'long' }).toLowerCase();
        if (Array.isArray(userConfig.weeklySchedule) && userConfig.weeklySchedule[cycleIndex]) {
            const scheduleValue = Number(userConfig.weeklySchedule[cycleIndex][dayOfWeek]);
            if (!isNaN(scheduleValue)) {
                expectedForDay = scheduleValue;
            }
        }
    }
    return expectedForDay;
};

const getStatusLabel = (punchOrder) => {
    switch (punchOrder) {
        case 1:
            return 'Work Start';
        case 2:
            return 'Break Start';
        case 3:
            return 'Break End';
        case 4:
            return 'Work End';
        default:
            return '';
    }
};

const formatDiff = (diff, t) => {
    const sign = diff >= 0 ? '+' : '-';
    const abs = Math.abs(diff);
    const hrs = Math.floor(abs / 60);
    const mins = abs % 60;
    return `${sign}${hrs} ${t("hours")} ${mins} ${t("minutes")}`;
};

const groupEntriesByDay = (entries) => {
    const dayMap = {};
    entries.forEach(entry => {
        const ds = new Date(entry.startTime).toLocaleDateString('de-DE');
        if (!dayMap[ds]) {
            dayMap[ds] = [];
        }
        dayMap[ds].push(entry);
    });
    return dayMap;
};

const UserDashboard = () => {
    const { currentUser } = useAuth();
    const { notify } = useNotification();
    const { t } = useTranslation();

    const [userProfile, setUserProfile] = useState(null);
    const [allEntries, setAllEntries] = useState([]);
    const [vacationRequests, setVacationRequests] = useState([]);
    const [vacationForm, setVacationForm] = useState({ startDate: '', endDate: '' });
    const [printModalVisible, setPrintModalVisible] = useState(false);
    const [printStartDate, setPrintStartDate] = useState(formatLocalDate(new Date()));
    const [printEndDate, setPrintEndDate] = useState(formatLocalDate(new Date()));
    const [punchMessage, setPunchMessage] = useState('');
    const [lastPunchTime, setLastPunchTime] = useState(0);
    const [weeklyDiff, setWeeklyDiff] = useState(0);
    const [monthlyDiff, setMonthlyDiff] = useState(0);
    const [overallDiff, setOverallDiff] = useState(0);
    const [monthlyDiffAll, setMonthlyDiffAll] = useState({});
    const lastPunchTimeRef = useRef(0);
    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));
    const [showAllCorrections, setShowAllCorrections] = useState(false);

    const [showCorrectionModal, setShowCorrectionModal] = useState(false);
    const [correctionDate, setCorrectionDate] = useState("");
    const [correctionData, setCorrectionData] = useState({
        workStart: "",
        breakStart: "",
        breakEnd: "",
        workEnd: "",
        reason: ""
    });

    // Neuer State für Korrekturanträge und deren Panel-Steuerung
    const [correctionRequests, setCorrectionRequests] = useState([]);
    const [showCorrectionsPanel, setShowCorrectionsPanel] = useState(false);
    const [selectedCorrectionMonday, setSelectedCorrectionMonday] = useState(getMondayOfWeek(new Date()));

    // Zentrale Berechnung der erwarteten Stunden: Wenn userProfile noch null ist, verwenden wir 8
    const defaultExpectedHours = userProfile ? Number(userProfile.dailyWorkHours || 8) : 8;

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
    }, [currentUser, t]);

    /* ===== Einträge, Urlaubsanträge und Korrekturanträge laden ===== */
    useEffect(() => {
        if (userProfile) {
            fetchEntries();
            fetchVacations();
            fetchCorrections();
        }
    }, [userProfile]);

    async function fetchEntries() {
        try {
            const res = await api.get(`/api/timetracking/history?username=${userProfile.username}`);
            const validEntries = (res.data || []).filter(e => [1, 2, 3, 4].includes(e.punchOrder));
            setAllEntries(validEntries);
        } catch (err) {
            console.error('Error loading time entries', err);
        }
    }

    async function fetchVacations() {
        try {
            const res = await api.get('/api/vacation/my');
            setVacationRequests(res.data || []);
        } catch (err) {
            console.error('Error loading vacation requests', err);
        }
    }

    // Neue Funktion zum Laden der Korrekturanträge des Users
    async function fetchCorrections() {
        try {
            const res = await api.get(`/api/correction/my?username=${userProfile.username}`);
            setCorrectionRequests(res.data || []);
        } catch (err) {
            console.error('Error loading correction requests', err);
        }
    }

    /* ===== NFC-Polling ===== */
    useEffect(() => {
        const interval = setInterval(() => {
            doNfcCheck();
        }, 2000);
        return () => clearInterval(interval);
    }, []);

    async function doNfcCheck() {
        try {
            const response = await fetch('http://localhost:8080/api/nfc/read/1');
            if (!response.ok) return;
            const json = await response.json();
            if (json.status === 'no-card') return;
            else if (json.status === 'error') return;
            else if (json.status === 'success') {
                const cardUser = parseHex16(json.data);
                if (cardUser) {
                    const now = Date.now();
                    if (now - lastPunchTimeRef.current < 5000) return;
                    lastPunchTimeRef.current = now;
                    showPunchMessage(`Eingestempelt: ${cardUser}`);
                    try {
                        await api.post('/api/timetracking/punch', null, {
                            params: { username: cardUser }
                        });
                    } catch (err) {
                        console.error("Punch-Fehler:", err);
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

    async function handleManualPunch() {
        try {
            await api.post('/api/timetracking/punch', null, {
                params: { username: userProfile.username }
            });
            showPunchMessage(`${t("manualPunchMessage")} ${userProfile.username}`);
            fetchEntries();
        } catch (err) {
            console.error('Punch-Fehler:', err);
            notify(t("manualPunchError"));
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
            notify(t("vacationSubmitSuccess"));
        } catch (err) {
            console.error('Error submitting vacation request:', err);
            notify(t("vacationSubmitError"));
        }
    }

    useEffect(() => {
        if (!userProfile || userProfile.isHourly) {
            setWeeklyDiff(0);
            return;
        }
        const weekDates = Array.from({ length: 7 }, (_, i) => addDays(selectedMonday, i));
        const dateStrings = weekDates.map(d => d.toLocaleDateString());
        const weeklyEntries = allEntries.filter(e => {
            const ds = new Date(e.startTime).toLocaleDateString();
            return dateStrings.includes(ds);
        });
        const grouped = {};
        weeklyEntries.forEach(entry => {
            const ds = new Date(entry.startTime).toLocaleDateString('de-DE');
            if (!grouped[ds]) grouped[ds] = [];
            grouped[ds].push(entry);
        });
        let sum = 0;
        for (const ds in grouped) {
            const dayEntries = grouped[ds];
            if (dayEntries.length > 0) {
                const dateObj = new Date(dayEntries[0].startTime);
                const expected = getExpectedHoursForDay(dateObj, userProfile, defaultExpectedHours);
                sum += computeDailyDiffValue(dayEntries, expected);
            }
        }
        setWeeklyDiff(sum);
    }, [allEntries, userProfile, selectedMonday]);

    useEffect(() => {
        if (!userProfile || userProfile.isHourly) {
            setMonthlyDiff(0);
            return;
        }
        const monthlyEntries = allEntries.filter(entry => {
            const d = new Date(entry.startTime);
            return (
                d.getFullYear() === selectedMonday.getFullYear() &&
                d.getMonth() === selectedMonday.getMonth()
            );
        });
        const grouped = {};
        monthlyEntries.forEach(entry => {
            const ds = new Date(entry.startTime).toLocaleDateString('de-DE');
            if (!grouped[ds]) grouped[ds] = [];
            grouped[ds].push(entry);
        });
        let sum = 0;
        for (const ds in grouped) {
            const dayEntries = grouped[ds];
            if (dayEntries.length > 0) {
                const dateObj = new Date(dayEntries[0].startTime);
                const expected = getExpectedHoursForDay(dateObj, userProfile, defaultExpectedHours);
                sum += computeDailyDiffValue(dayEntries, expected);
            }
        }
        setMonthlyDiff(sum);
    }, [allEntries, userProfile, selectedMonday]);

    useEffect(() => {
        if (!userProfile || userProfile.isHourly) {
            setOverallDiff(0);
            return;
        }
        const grouped = groupEntriesByDay(allEntries);
        let sum = 0;
        for (const ds in grouped) {
            const dayEntries = grouped[ds];
            if (dayEntries.length > 0) {
                const dateObj = new Date(dayEntries[0].startTime);
                const expected = getExpectedHoursForDay(dateObj, userProfile, defaultExpectedHours);
                sum += computeDailyDiffValue(dayEntries, expected);
            }
        }
        setOverallDiff(sum);
    }, [allEntries, userProfile]);

    useEffect(() => {
        if (!userProfile || userProfile.isHourly) {
            setMonthlyDiffAll({});
            return;
        }
        const monthlyGroups = {};
        allEntries.forEach(entry => {
            const d = new Date(entry.startTime);
            const monthKey = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
            if (!monthlyGroups[monthKey]) monthlyGroups[monthKey] = {};
            const ds = d.toLocaleDateString('de-DE');
            if (!monthlyGroups[monthKey][ds]) monthlyGroups[monthKey][ds] = [];
            monthlyGroups[monthKey][ds].push(entry);
        });
        const diffs = {};
        for (const monthKey in monthlyGroups) {
            let sumMonth = 0;
            const dayGroup = monthlyGroups[monthKey];
            for (const ds in dayGroup) {
                const dayEntries = dayGroup[ds];
                if (dayEntries.length > 0) {
                    const dateObj = new Date(dayEntries[0].startTime);
                    const expected = getExpectedHoursForDay(dateObj, userProfile, defaultExpectedHours);
                    sumMonth += computeDailyDiffValue(dayEntries, expected);
                }
            }
            diffs[monthKey] = sumMonth;
        }
        setMonthlyDiffAll(diffs);
    }, [allEntries, userProfile]);

    const weekDates = Array.from({ length: 7 }, (_, i) => addDays(selectedMonday, i));
    const dayMapWeek = {};
    weekDates.forEach(d => {
        dayMapWeek[d.toLocaleDateString()] = [];
    });
    allEntries.forEach(entry => {
        const ds = new Date(entry.startTime).toLocaleDateString();
        if (dayMapWeek[ds] !== undefined) {
            dayMapWeek[ds].push(entry);
        }
    });

    let dailyDiffDisplay = null;
    if (userProfile && !userProfile.isHourly) {
        const todayStr = new Date().toLocaleDateString('de-DE');
        const groupedToday = groupEntriesByDay(allEntries);
        const entriesToday = groupedToday[todayStr] || [];
        if (entriesToday.length > 0) {
            const expectedForToday = getExpectedHoursForDay(new Date(), userProfile, defaultExpectedHours);
            dailyDiffDisplay = computeDailyDiff(entriesToday, expectedForToday);
            dailyDiffDisplay = `${dailyDiffDisplay} (${t("expectedWorkHours")}: ${expectedForToday} ${t("hours")})`;
        }
    }

    if (userProfile?.isHourly) {
        return <HourlyDashboard />;
    }

    const correctionsForWeek = correctionRequests.filter(req => {
        const reqDate = new Date(req.desiredStart);
        return reqDate >= selectedCorrectionMonday && reqDate < addDays(selectedCorrectionMonday, 7);
    });


    // Formatierung der Wochenanzeige (z. B. "01.03.2025 - 07.03.2025")
    const correctionWeekLabel = `${formatDate(selectedCorrectionMonday)} - ${formatDate(addDays(selectedCorrectionMonday, 6))}`;

    async function handlePrintReport() {
        if (!printStartDate || !printEndDate) {
            notify(t("printReportError"));
            return;
        }
        const doc = new jsPDF("p", "mm", "a4");
        doc.setFontSize(12);
        doc.text(`${t("printReportTitle")} - ${userProfile.username}`, 10, 15);

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
                const workStart = dayEntries.find(e => e.punchOrder === 1)
                    ? formatTime(dayEntries.find(e => e.punchOrder === 1).startTime)
                    : "-";
                const breakStart = dayEntries.find(e => e.punchOrder === 2)
                    ? (dayEntries.find(e => e.punchOrder === 2).breakStart
                        ? formatTime(dayEntries.find(e => e.punchOrder === 2).breakStart)
                        : formatTime(dayEntries.find(e => e.punchOrder === 2).startTime))
                    : "-";
                const breakEnd = dayEntries.find(e => e.punchOrder === 3)
                    ? (dayEntries.find(e => e.punchOrder === 3).breakEnd
                        ? formatTime(dayEntries.find(e => e.punchOrder === 3).breakEnd)
                        : formatTime(dayEntries.find(e => e.punchOrder === 3).startTime))
                    : "-";
                const workEnd = dayEntries.find(e => e.punchOrder === 4)
                    ? formatTime(dayEntries.find(e => e.punchOrder === 4).endTime)
                    : "-";
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
            await window.electron.ipcRenderer.invoke("saveAndOpenPDF", pdfBase64);
        } catch (err) {
            console.error("Fehler beim Speichern/Öffnen des PDFs:", err);
            notify("Fehler beim Öffnen des Berichts.");
        }
        setPrintModalVisible(false);
    }

    const openCorrectionModal = (dateObj) => {
        setCorrectionDate(formatLocalDate(dateObj));
        setCorrectionData({
            workStart: "",
            breakStart: "",
            breakEnd: "",
            workEnd: "",
            reason: ""
        });
        setShowCorrectionModal(true);
    };

    const handleCorrectionInputChange = (e) => {
        const { name, value } = e.target;
        setCorrectionData(prev => ({ ...prev, [name]: value }));
    };

    const handleCorrectionSubmit = async (e) => {
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
            fetchCorrections();  // Aktualisiere die Liste der Korrekturanträge
            setShowCorrectionModal(false);
        } catch (err) {
            console.error("Fehler beim Absenden des Korrekturantrags:", err);
            notify("Fehler beim Absenden des Korrekturantrags.");
        }
    };

    {/* Hilfsfunktion zum Formatieren von "HH:mm:ss" zu "HH:mm" */}
    function formatLocalTime(hmsString) {
        if (!hmsString) return '-';
        // "HH:mm:ss" -> "HH:mm"
        return hmsString.slice(0, 5);
    }
    const sortedCorrections = (showAllCorrections ? correctionRequests : correctionsForWeek)
        .slice() // Kopie erstellen, damit das Original nicht verändert wird
        .sort((a, b) => {
            // Falls deine Daten in "desiredStart" liegen:
            const dateA = new Date(a.desiredStart);
            const dateB = new Date(b.desiredStart);
            // Absteigend sortieren
            return dateB - dateA;
        });

    return (
        <div className="user-dashboard">
            <Navbar />
            <header className="dashboard-header">
                <h2>{t("title")}</h2>
                <div className="personal-info">
                    <p>
                        <strong>{t("usernameLabel")}:</strong> {userProfile?.username || t("notLoggedIn")}
                    </p>
                    {!userProfile?.isHourly && (
                        <>
                            <p>
                                <strong>{t("expectedWorkHours")}:</strong>{" "}
                                {userProfile ? getExpectedHoursForDay(new Date(), userProfile, defaultExpectedHours) : 8} {t("hours")}
                            </p>
                            {dailyDiffDisplay && (
                                <p>
                                    <strong>{t("diffToday")}:</strong> {dailyDiffDisplay}
                                </p>
                            )}
                        </>
                    )}
                </div>
            </header>

            {punchMessage && <div className="punch-message">{punchMessage}</div>}

            <section className="punch-section">
                <h3>{t("manualPunchTitle")}</h3>
                <button onClick={handleManualPunch}>{t("manualPunchButton")}</button>
            </section>

            <section className="time-tracking-section">
                <h3>{t("weeklyOverview")}</h3>
                <div className="week-navigation">
                    <button onClick={() => setSelectedMonday(prev => addDays(prev, -7))}>← {t("prevWeek")}</button>
                    <input
                        type="date"
                        onChange={(e) => {
                            const picked = new Date(e.target.value);
                            if (!isNaN(picked.getTime())) {
                                setSelectedMonday(getMondayOfWeek(picked));
                            }
                        }}
                        value={selectedMonday.toISOString().slice(0, 10)}
                    />
                    <button onClick={() => setSelectedMonday(prev => addDays(prev, 7))}>{t("nextWeek")} →</button>
                </div>
                {!userProfile?.isHourly && (
                    <div className="week-diff">
                        {t("weekDiff")}: <strong>{formatDiff(weeklyDiff, t)}</strong>
                    </div>
                )}
                <div className="week-display">
                    {weekDates.map((dayObj, idx) => {
                        const ds = dayObj.toLocaleDateString();
                        const dayEntries = dayMapWeek[ds] || [];
                        const expectedForDay = getExpectedHoursForDay(dayObj, userProfile, defaultExpectedHours);
                        const dailyDiff = dayEntries.length >= 4 ? computeDailyDiff(dayEntries, expectedForDay) : '';
                        return (
                            <div key={idx} className="day-card">
                                <div className="day-card-header">
                                    <h4>
                                        {dayObj.toLocaleDateString('de-DE', { weekday: 'long' })}, {ds}{' '}
                                        <span className="expected-hours">
                                            ({t("expectedWorkHours")}: {expectedForDay} {t("hours")})
                                        </span>
                                    </h4>
                                    {dailyDiff && <span className="daily-diff">({dailyDiff})</span>}
                                </div>
                                {dayEntries.length === 0 ? (
                                    <p className="no-entries">{t("noEntries")}</p>
                                ) : (
                                    <ul className="time-entry-list">
                                        {dayEntries.sort((a, b) => a.punchOrder - b.punchOrder).map(e => {
                                            let displayTime = '-';
                                            if (userProfile.isHourly) {
                                                displayTime = e.endTime ? formatTime(e.endTime) : formatTime(e.startTime);
                                            } else {
                                                if (e.punchOrder === 1) displayTime = formatTime(e.startTime);
                                                else if (e.punchOrder === 2)
                                                    displayTime = e.breakStart ? formatTime(e.breakStart) : formatTime(e.startTime);
                                                else if (e.punchOrder === 3)
                                                    displayTime = e.breakEnd ? formatTime(e.breakEnd) : formatTime(e.startTime);
                                                else if (e.punchOrder === 4) displayTime = formatTime(e.endTime);
                                            }
                                            return (
                                                <li key={e.id}>
                                                    {!userProfile.isHourly && (
                                                        <span className="entry-label">{getStatusLabel(e.punchOrder)}:</span>
                                                    )}{' '}
                                                    {displayTime}
                                                </li>
                                            );
                                        })}
                                    </ul>
                                )}
                                <button className="correction-button" onClick={() => openCorrectionModal(dayObj)}>
                                    Korrekturantrag stellen
                                </button>
                            </div>
                        );
                    })}
                </div>
            </section>

            {/* Neuer, einklappbarer Bereich für Korrekturanträge */}
            {/* Neuer, einklappbarer Bereich für Korrekturanträge */}
            <section className="correction-panel">
                <div className="corrections-header" onClick={() => setShowCorrectionsPanel(prev => !prev)}>
                    <h3>{t("correctionRequests") || "Korrekturanträge"}</h3>
                    <span className="toggle-icon">{showCorrectionsPanel ? "▲" : "▼"}</span>
                </div>
                {showCorrectionsPanel && (
                    <div className="corrections-content">
                        {/* Nur anzeigen, wenn wir in der Wochenansicht sind */}
                        {!showAllCorrections && (
                            <div className="week-navigation corrections-nav">
                                <button onClick={() => setSelectedCorrectionMonday(prev => addDays(prev, -7))}>
                                    ← {t("prevWeek")}
                                </button>
                                <span className="week-label">{correctionWeekLabel}</span>
                                <button onClick={() => setSelectedCorrectionMonday(prev => addDays(prev, 7))}>
                                    {t("nextWeek")} →
                                </button>
                            </div>
                        )}
                        {/* Umschaltbutton */}
                        <div className="toggle-all-button">
                            <button onClick={() => setShowAllCorrections(prev => !prev)}>
                                {showAllCorrections ? (t("showWeeklyOnly") || "Nur aktuelle Woche") : (t("showAll") || "Alle anzeigen")}
                            </button>
                        </div>
                        {(showAllCorrections ? correctionRequests : correctionsForWeek).length === 0 ? (
                            <p>{t("noCorrections") || "Keine Korrekturanträge vorhanden"}</p>
                        ) : (
                            <ul className="corrections-list">
                                {sortedCorrections.map(req => {
                                    const desiredDate = new Date(req.desiredStart);
                                    return (
                                        <li key={req.id}>
                                            {formatDate(desiredDate)}{' '}
                                            {req.approved ? (
                                                <span className="approved">Bestätigt</span>
                                            ) : req.denied ? (
                                                <span className="denied">Abgelehnt</span>
                                            ) : (
                                                <span className="pending">Offen</span>
                                            )}
                                            <br />
                                            {req.reason}
                                            <br />
                                            <strong>Work Start:</strong> {formatLocalTime(req.workStart)}<br/>
                                            <strong>Break Start:</strong> {formatLocalTime(req.breakStart)}<br/>
                                            <strong>Break End:</strong> {formatLocalTime(req.breakEnd)}<br/>
                                            <strong>Work End:</strong> {formatLocalTime(req.workEnd)}
                                        </li>
                                    );
                                })}
                            </ul>
                        )}
                    </div>
                )}
            </section>

            {!userProfile?.isHourly && (
                <section className="monthly-all">
                    <h3>{t("allMonths")}</h3>
                    {Object.keys(monthlyDiffAll).length === 0 ? (
                        <p>{t("noEntries")}</p>
                    ) : (
                        <table className="daily-summary-table">
                            <thead>
                            <tr>
                                <th>{t("month")}</th>
                                <th>{t("diffToday")}</th>
                            </tr>
                            </thead>
                            <tbody>
                            {Object.entries(monthlyDiffAll)
                                .sort(([a], [b]) => a.localeCompare(b))
                                .map(([monthKey, diffVal]) => (
                                    <tr key={monthKey}>
                                        <td>{monthKey}</td>
                                        <td>{formatDiff(diffVal, t)}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    )}
                </section>
            )}

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

            {/* Korrekturantrag Modal */}
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
};

export default UserDashboard;
