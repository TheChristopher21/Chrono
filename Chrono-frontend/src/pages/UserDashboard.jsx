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

/**
 * Ermittelt den Montag der aktuellen Woche.
 */
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

/**
 * Gibt die seit Mitternacht vergangenen Minuten zurück.
 */
const getMinutesSinceMidnight = (datetimeStr) => {
    if (!datetimeStr) return 0;
    const d = new Date(datetimeStr);
    return d.getHours() * 60 + d.getMinutes();
};

/**
 * Rechnet die tatsächlich gearbeiteten Minuten minus die erwarteten Minuten aus.
 * dayEntries = { startTime, endTime, breakStart, breakEnd, punchOrder }
 * expectedWorkHours (z.B. 8.5 => 8 Stunden 30 Min)
 */
const computeDailyDiffValue = (dayEntries, expectedWorkHours) => {
    const entryStart = dayEntries.find(e => e.punchOrder === 1);
    const entryBreakStart = dayEntries.find(e => e.punchOrder === 2);
    const entryBreakEnd = dayEntries.find(e => e.punchOrder === 3);
    const entryEnd = dayEntries.find(e => e.punchOrder === 4);

    if (entryStart && entryBreakStart && entryBreakEnd && entryEnd) {
        // Tatsächlich gestempelte Zeiten
        const workStartMins = getMinutesSinceMidnight(entryStart.startTime);
        const workEndMins = getMinutesSinceMidnight(entryEnd.endTime);
        const breakStartMins = entryBreakStart.breakStart
            ? parseInt(entryBreakStart.breakStart.slice(0, 2), 10) * 60 +
            parseInt(entryBreakStart.breakStart.slice(3, 5), 10)
            : getMinutesSinceMidnight(entryBreakStart.startTime);
        const breakEndMins = entryBreakEnd.breakEnd
            ? parseInt(entryBreakEnd.breakEnd.slice(0, 2), 10) * 60 +
            parseInt(entryBreakEnd.breakEnd.slice(3, 5), 10)
            : getMinutesSinceMidnight(entryBreakEnd.startTime);

        const workDuration = workEndMins - workStartMins;
        const breakDuration = breakEndMins - breakStartMins;
        const actualWorked = workDuration - breakDuration;

        // Erwartete Stunden * 60 => erwartete Minuten (z.B. 8.5 * 60 => 510)
        const expectedMinutes = expectedWorkHours * 60;
        return actualWorked - expectedMinutes;
    }
    return 0;
};

/**
 * Hilfsfunktion, um z.B. +0.50 h oder -1.25 h aus einer Differenz in Minuten zu erhalten.
 */
const formatDiffDecimal = (diffInMinutes) => {
    const sign = diffInMinutes >= 0 ? '+' : '-';
    const absHours = Math.abs(diffInMinutes) / 60; // => Dezimalstunden
    // Wir runden auf 2 Nachkommastellen
    return `${sign}${absHours.toFixed(2)}h`;
};

/**
 * Liest "expectedWorkHours" aus userProfile.schedule, falls hinterlegt (z. B. 8.5).
 * Fallback: defaultExpectedHours = 8.
 */
const getExpectedHoursForDay = (dayObj, userConfig, defaultExpectedHours) => {
    if (userConfig?.isHourly) return 0;
    let expectedForDay = defaultExpectedHours; // in Stunden
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

/**
 * Zeigt "Work Start", "Break Start", etc. statt reiner Punchnummer.
 */
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

/**
 * Gruppiert Einträge nach Datum => { "01.04.2023": [...], "02.04.2023": [...], ... }
 */
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
    const [printModalVisible, setPrintModalVisible] = useState(false);
    const [printStartDate, setPrintStartDate] = useState(formatLocalDate(new Date()));
    const [printEndDate, setPrintEndDate] = useState(formatLocalDate(new Date()));
    const [punchMessage, setPunchMessage] = useState('');
    const lastPunchTimeRef = useRef(0);
    const [weeklyDiff, setWeeklyDiff] = useState(0);   // in Minuten
    const [monthlyDiff, setMonthlyDiff] = useState(0); // in Minuten
    const [overallDiff, setOverallDiff] = useState(0); // in Minuten
    const [monthlyDiffAll, setMonthlyDiffAll] = useState({});
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

    const [correctionRequests, setCorrectionRequests] = useState([]);
    const [showCorrectionsPanel, setShowCorrectionsPanel] = useState(false);
    const [selectedCorrectionMonday, setSelectedCorrectionMonday] = useState(getMondayOfWeek(new Date()));

    // Falls dailyWorkHours nicht gesetzt => 8
    // Manchmal userProfile.dailyWorkHours = "8.5" => wir parsen Number
    const defaultExpectedHours = userProfile ? Number(userProfile.dailyWorkHours || 8) : 8;

    // ----------------------------
    // 1) Profil laden
    // ----------------------------
    useEffect(() => {
        async function fetchProfile() {
            try {
                const res = await api.get('/api/auth/me');
                const profile = res.data;
                // wandle weeklySchedule in Array um, wenn es keines ist
                if (profile.weeklySchedule && !Array.isArray(profile.weeklySchedule)) {
                    profile.weeklySchedule = [profile.weeklySchedule];
                }
                // falls gar nichts da => defaults
                if (!profile.weeklySchedule) {
                    profile.weeklySchedule = [
                        { monday: 8, tuesday: 8, wednesday: 8, thursday: 8, friday: 8, saturday: 0, sunday: 0 }
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

    // ----------------------------
    // 2) Daten laden
    // ----------------------------
    useEffect(() => {
        if (!userProfile) return;
        fetchEntries();
        fetchVacations();
        fetchCorrections();
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

    async function fetchCorrections() {
        try {
            const res = await api.get(`/api/correction/my?username=${userProfile.username}`);
            setCorrectionRequests(res.data || []);
        } catch (err) {
            console.error('Error loading correction requests', err);
        }
    }

    // ----------------------------
    // 3) NFC-Abfrage (alle 2 Sek.)
    // ----------------------------
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
            if (json.status === 'no-card' || json.status === 'error') return;
            if (json.status === 'success') {
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

    // ----------------------------
    // 4) Berechnungen: Weekly, Monthly, Overall
    //    => In MINUTEN!
    // ----------------------------
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

        let sumMins = 0;
        for (const ds in grouped) {
            const dayEntries = grouped[ds];
            if (dayEntries.length > 0) {
                const dateObj = new Date(dayEntries[0].startTime);
                const expected = getExpectedHoursForDay(dateObj, userProfile, defaultExpectedHours);
                sumMins += computeDailyDiffValue(dayEntries, expected);
            }
        }
        setWeeklyDiff(sumMins);
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

        let sumMins = 0;
        for (const ds in grouped) {
            const dayEntries = grouped[ds];
            if (dayEntries.length > 0) {
                const dateObj = new Date(dayEntries[0].startTime);
                const expected = getExpectedHoursForDay(dateObj, userProfile, defaultExpectedHours);
                sumMins += computeDailyDiffValue(dayEntries, expected);
            }
        }
        setMonthlyDiff(sumMins);
    }, [allEntries, userProfile, selectedMonday]);

    useEffect(() => {
        if (!userProfile || userProfile.isHourly) {
            setOverallDiff(0);
            return;
        }
        const grouped = groupEntriesByDay(allEntries);
        let sumMins = 0;
        for (const ds in grouped) {
            const dayEntries = grouped[ds];
            if (dayEntries.length > 0) {
                const dateObj = new Date(dayEntries[0].startTime);
                const expected = getExpectedHoursForDay(dateObj, userProfile, defaultExpectedHours);
                sumMins += computeDailyDiffValue(dayEntries, expected);
            }
        }
        setOverallDiff(sumMins);
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
            diffs[monthKey] = sumMonth; // in Minuten
        }
        setMonthlyDiffAll(diffs);
    }, [allEntries, userProfile]);

    // ----------------------------
    // 5) Anzeige in der Wochenübersicht
    // ----------------------------
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

    // ----------------------------
    // 6) Tägliche Differenz "heute"
    // ----------------------------
    let dailyDiffDisplay = null;
    if (userProfile && !userProfile.isHourly) {
        const todayStr = new Date().toLocaleDateString('de-DE');
        const groupedToday = groupEntriesByDay(allEntries);
        const entriesToday = groupedToday[todayStr] || [];
        if (entriesToday.length > 0) {
            const expectedForToday = getExpectedHoursForDay(new Date(), userProfile, defaultExpectedHours);
            const diffMins = computeDailyDiffValue(entriesToday, expectedForToday);
            // z.B. +0.50h, -1.25h
            dailyDiffDisplay = `${formatDiffDecimal(diffMins)} (Expected: ${expectedForToday.toFixed(2)}h)`;
        }
    }

    // ----------------------------
    // 7) isHourly
    // ----------------------------
    if (userProfile?.isHourly) {
        return <HourlyDashboard />;
    }

    // ----------------------------
    // 8) Korrekturanträge
    // ----------------------------
    const correctionsForWeek = correctionRequests.filter(req => {
        const reqDate = new Date(req.desiredStart);
        return reqDate >= selectedCorrectionMonday && reqDate < addDays(selectedCorrectionMonday, 7);
    });
    const correctionWeekLabel = `${formatDate(selectedMonday)} - ${formatDate(addDays(selectedMonday, 6))}`;

    // ----------------------------
    // 9) PDF-Export
    // ----------------------------
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
                const diffText = formatDiffDecimal(diffValue); // z.B. +1.50h
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

    // ----------------------------
    // 10) Korrektur-Funktionen
    // ----------------------------
    const openCorrectionModal = (dateObj) => {
        const localDate = dateObj.toLocaleDateString();
        const dayEntries = allEntries.filter(e => {
            const entryDate = new Date(e.startTime).toLocaleDateString();
            return entryDate === localDate;
        });

        const entryStart = dayEntries.find(e => e.punchOrder === 1);
        const entryBreakStart = dayEntries.find(e => e.punchOrder === 2);
        const entryBreakEnd = dayEntries.find(e => e.punchOrder === 3);
        const entryEnd = dayEntries.find(e => e.punchOrder === 4);

        const formatToInputTime = (dateStr) => {
            if (!dateStr) return '';
            const d = new Date(dateStr);
            return isNaN(d.getTime()) ? '' : d.toISOString().slice(11, 16);
        };

        const formatToInputManualTime = (timeStr) => {
            return timeStr && typeof timeStr === 'string' && timeStr.length >= 5
                ? timeStr.slice(0, 5)
                : '';
        };

        setCorrectionDate(formatLocalDate(dateObj));
        setCorrectionData({
            workStart: formatToInputTime(entryStart?.startTime),
            breakStart: entryBreakStart?.breakStart
                ? formatToInputManualTime(entryBreakStart.breakStart)
                : formatToInputTime(entryBreakStart?.startTime),
            breakEnd: entryBreakEnd?.breakEnd
                ? formatToInputManualTime(entryBreakEnd.breakEnd)
                : formatToInputTime(entryBreakEnd?.startTime),
            workEnd: formatToInputTime(entryEnd?.endTime),
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
            fetchCorrections();
            setShowCorrectionModal(false);
        } catch (err) {
            console.error("Fehler beim Absenden des Korrekturantrags:", err);
            notify("Fehler beim Absenden des Korrekturantrags.");
        }
    };

    function formatLocalTime(hmsString) {
        if (!hmsString) return '-';
        return hmsString.slice(0, 5);
    }
    const sortedCorrections = (showAllCorrections ? correctionRequests : correctionsForWeek)
        .slice()
        .sort((a, b) => {
            const dateA = new Date(a.desiredStart);
            const dateB = new Date(b.desiredStart);
            return dateB - dateA;
        });

    // ----------------------------
    // 11) Refresh-Funktion (für VacationCalendar)
    // ----------------------------
    const refreshVacations = () => {
        fetchVacations();
    };

    // ----------------------------
    // 12) Render
    // ----------------------------
    return (
        <div className="user-dashboard">
            <Navbar />
            <header className="dashboard-header">
                <h2>{t("title")}</h2>
                <div className="personal-info">
                    <p>
                        <strong>{t("usernameLabel")}:</strong>{" "}
                        {userProfile?.username || t("notLoggedIn")}
                    </p>
                    {!userProfile?.isHourly && (
                        <>
                            {/* Erwartete Arbeitsstunden heute in Dezimal, falls userProfile z.B. 8.5 hat */}
                            <p>
                                <strong>{t("expectedWorkHours")}:</strong>{" "}
                                {userProfile
                                    ? getExpectedHoursForDay(new Date(), userProfile, defaultExpectedHours).toFixed(2)
                                    : 8
                                } {t("hours")}
                            </p>

                            {/* Tages-Differenz in Dezimalstunden */}
                            {dailyDiffDisplay && (
                                <p>
                                    <strong>{t("diffToday")}:</strong> {dailyDiffDisplay}
                                </p>
                            )}
                        </>
                    )}
                </div>
            </header>

            {/* Punch-Meldung */}
            {punchMessage && (
                <div className="punch-message">{punchMessage}</div>
            )}

            {/* Manueller Punch */}
            <section className="punch-section">
                <h3>{t("manualPunchTitle")}</h3>
                <button onClick={handleManualPunch}>{t("manualPunchButton")}</button>
            </section>

            {/* Wochen-Übersicht */}
            <section className="time-tracking-section">
                <h3>{t("weeklyOverview")}</h3>
                <div className="week-navigation">
                    <button onClick={() => setSelectedMonday(prev => addDays(prev, -7))}>
                        ← {t("prevWeek")}
                    </button>
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
                    <button onClick={() => setSelectedMonday(prev => addDays(prev, 7))}>
                        {t("nextWeek")} →
                    </button>
                </div>

                {/* Weekly-Diff in Dezimalstunden */}
                {!userProfile?.isHourly && (
                    <div className="week-diff">
                        {t("weekDiff")}: <strong>{formatDiffDecimal(weeklyDiff)}</strong>
                    </div>
                )}

                {/* Tages-Karten */}
                <div className="week-display">
                    {weekDates.map((dayObj, idx) => {
                        const ds = dayObj.toLocaleDateString();
                        const dayEntries = dayMapWeek[ds] || [];
                        const expectedForDay = getExpectedHoursForDay(dayObj, userProfile, defaultExpectedHours);
                        const diffMins = dayEntries.length >= 4
                            ? computeDailyDiffValue(dayEntries, expectedForDay)
                            : 0;
                        const dailyDiffStr = dayEntries.length >= 4
                            ? formatDiffDecimal(diffMins)
                            : '';

                        return (
                            <div key={idx} className="day-card">
                                <div className="day-card-header">
                                    <h4>
                                        {dayObj.toLocaleDateString('de-DE', { weekday: 'long' })}, {ds}
                                        <span className="expected-hours">
                                            {` ( ${t("expectedWorkHours")}: ${expectedForDay.toFixed(2)}h )`}
                                        </span>
                                    </h4>
                                    {dailyDiffStr && <span className="daily-diff">({dailyDiffStr})</span>}
                                </div>

                                {dayEntries.length === 0 ? (
                                    <p className="no-entries">{t("noEntries")}</p>
                                ) : (
                                    <ul className="time-entry-list">
                                        {dayEntries.sort((a, b) => a.punchOrder - b.punchOrder).map(e => {
                                            let displayTime = '-';
                                            if (userProfile.isHourly) {
                                                displayTime = e.endTime
                                                    ? formatTime(e.endTime)
                                                    : formatTime(e.startTime);
                                            } else {
                                                if (e.punchOrder === 1) {
                                                    displayTime = formatTime(e.startTime);
                                                } else if (e.punchOrder === 2) {
                                                    displayTime = e.breakStart
                                                        ? formatTime(e.breakStart)
                                                        : formatTime(e.startTime);
                                                } else if (e.punchOrder === 3) {
                                                    displayTime = e.breakEnd
                                                        ? formatTime(e.breakEnd)
                                                        : formatTime(e.startTime);
                                                } else if (e.punchOrder === 4) {
                                                    displayTime = formatTime(e.endTime);
                                                }
                                            }
                                            return (
                                                <li key={e.id}>
                                                    {!userProfile.isHourly && (
                                                        <span className="entry-label">
                                                            {getStatusLabel(e.punchOrder)}:
                                                        </span>
                                                    )}{' '}
                                                    {displayTime}
                                                </li>
                                            );
                                        })}
                                    </ul>
                                )}

                                {/* Korrekturantrag */}
                                <button
                                    className="correction-button"
                                    onClick={() => openCorrectionModal(dayObj)}
                                >
                                    Korrekturantrag stellen
                                </button>
                            </div>
                        );
                    })}
                </div>
            </section>

            {/* Neuer VacationCalendar */}
            <section className="vacation-calendar-section">
                <h3>{t("vacationTitle")}</h3>
                <VacationCalendar
                    vacationRequests={vacationRequests}
                    userProfile={userProfile}
                    onRefreshVacations={refreshVacations}
                />
            </section>

            {/* Korrekturen */}
            <section className="correction-panel">
                <div className="corrections-header" onClick={() => setShowCorrectionsPanel(prev => !prev)}>
                    <h3>{t("correctionRequests") || "Korrekturanträge"}</h3>
                    <span className="toggle-icon">{showCorrectionsPanel ? "▲" : "▼"}</span>
                </div>
                {showCorrectionsPanel && (
                    <div className="corrections-content">
                        <div className="week-navigation corrections-nav">
                            <button onClick={() => setSelectedCorrectionMonday(prev => addDays(prev, -7))}>
                                ← {t("prevWeek")}
                            </button>
                            <span className="week-label">{correctionWeekLabel}</span>
                            <button onClick={() => setSelectedCorrectionMonday(prev => addDays(prev, 7))}>
                                {t("nextWeek")} →
                            </button>
                        </div>
                        <div className="toggle-all-button">
                            <button onClick={() => setShowAllCorrections(prev => !prev)}>
                                {showAllCorrections
                                    ? t("showWeeklyOnly") || "Nur aktuelle Woche"
                                    : t("showAll") || "Alle anzeigen"}
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
                                            <strong>Work Start:</strong> {formatLocalTime(req.workStart)}
                                            <br />
                                            <strong>Break Start:</strong> {formatLocalTime(req.breakStart)}
                                            <br />
                                            <strong>Break End:</strong> {formatLocalTime(req.breakEnd)}
                                            <br />
                                            <strong>Work End:</strong> {formatLocalTime(req.workEnd)}
                                        </li>
                                    );
                                })}
                            </ul>
                        )}
                    </div>
                )}
            </section>

            {/* Zusammenfassung aller Monate (monthlyDiffAll) */}
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
                                        {/* diffVal ist in Minuten => formatDiffDecimal */}
                                        <td>{formatDiffDecimal(diffVal)}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    )}
                </section>
            )}

            {/* PDF-Report */}
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
                            <button onClick={() => setPrintModalVisible(false)}>
                                {t("cancel")}
                            </button>
                        </div>
                    </div>
                </div>
            )}

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
                                />
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
