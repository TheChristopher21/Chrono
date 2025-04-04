// src/pages/UserDashboard/UserDashboard.jsx

import  { useState, useEffect, useRef } from 'react';
import Navbar from '../../components/Navbar';
import VacationCalendar from '../../components/VacationCalendar';
import { useAuth } from '../../context/AuthContext';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import api from '../../utils/api';

// Falls du dein HourlyDashboard so liegen hast:
import HourlyDashboard from '../../pages/HourlyDashboard/HourlyDashboard.jsx';

// Hilfsfunktionen + Subkomponenten
import {
    getMondayOfWeek,
    addDays,
    parseHex16,
    formatTime,
    formatLocalDate,
    computeDailyDiffValue,
    formatDiffDecimal,
    getExpectedHoursForDay,
    getStatusLabel,
    groupEntriesByDay
} from './userDashUtils';

import UserPrintModal from './UserPrintModal';
import UserCorrectionModal from './UserCorrectionModal';
import UserCorrectionsPanel from './UserCorrectionsPanel';

// Dein CSS
import '../../styles/UserDashboard.css';

function UserDashboard() {
    const { currentUser } = useAuth();
    const { notify } = useNotification();
    const { t } = useTranslation();

    // ----------------------------
    // States
    // ----------------------------
    const [userProfile, setUserProfile] = useState(null);
    const [allEntries, setAllEntries] = useState([]);
    const [vacationRequests, setVacationRequests] = useState([]);
    const [correctionRequests, setCorrectionRequests] = useState([]);

    // Punch-Message + NFC-Ref
    const [punchMessage, setPunchMessage] = useState('');
    const lastPunchTimeRef = useRef(0);

    // Woche / Korrekturen
    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));
    const [showCorrectionsPanel, setShowCorrectionsPanel] = useState(false);
    const [showAllCorrections, setShowAllCorrections] = useState(false);
    const [selectedCorrectionMonday, setSelectedCorrectionMonday] = useState(getMondayOfWeek(new Date()));

    // PDF-Druck (Zeitraum)
    const [printModalVisible, setPrintModalVisible] = useState(false);
    const [printStartDate, setPrintStartDate] = useState(formatLocalDate(new Date()));
    const [printEndDate, setPrintEndDate] = useState(formatLocalDate(new Date()));

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

    // Summen
    const [weeklyDiff, setWeeklyDiff] = useState(0);    // in Minuten
    const [monthlyDiff, setMonthlyDiff] = useState(0);  // in Minuten
    const [overallDiff, setOverallDiff] = useState(0);  // in Minuten
    const [monthlyDiffAll, setMonthlyDiffAll] = useState({}); // { "2025-04": diffMins }

    // Default Expected Hours
    const defaultExpectedHours = userProfile ? Number(userProfile.dailyWorkHours || 8) : 8;

    // ----------------------------
    // 1) Profil laden
    // ----------------------------
    useEffect(() => {
        async function fetchProfile() {
            try {
                const res = await api.get('/api/auth/me');
                const profile = res.data;

                if (profile.weeklySchedule && !Array.isArray(profile.weeklySchedule)) {
                    profile.weeklySchedule = [profile.weeklySchedule];
                }
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
    }, [t, currentUser]);

    // ----------------------------
    // 2) Daten laden (Entries, Vacations, Corrections)
    // ----------------------------
    useEffect(() => {
        // Falls userProfile nicht geladen => skip
        if (!userProfile) return;

        async function fetchAllData() {
            try {
                // Einträge
                const resEntries = await api.get(`/api/timetracking/history?username=${userProfile.username}`);
                const validEntries = (resEntries.data || []).filter(e => [1, 2, 3, 4].includes(e.punchOrder));
                setAllEntries(validEntries);

                // Vacation
                const resVacation = await api.get('/api/vacation/my');
                setVacationRequests(resVacation.data || []);

                // Corrections
                const resCorr = await api.get(`/api/correction/my?username=${userProfile.username}`);
                setCorrectionRequests(resCorr.data || []);
            } catch (err) {
                console.error("Fehler beim Laden der Daten:", err);
            }
        }

        fetchAllData();
    }, [userProfile]);

    // ----------------------------
    // 3) NFC-Abfrage per Intervall
    // ----------------------------
    useEffect(() => {
        const interval = setInterval(() => doNfcCheck(), 2000);
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
                    // Verhindere mehrfach-Punch in kurzer Zeit
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
        if (!userProfile) return;
        try {
            await api.post('/api/timetracking/punch', null, {
                params: { username: userProfile.username }
            });
            showPunchMessage(`${t("manualPunchMessage")} ${userProfile.username}`);
            // Einträge neu laden
            const resEntries = await api.get(`/api/timetracking/history?username=${userProfile.username}`);
            const validEntries = (resEntries.data || []).filter(e => [1, 2, 3, 4].includes(e.punchOrder));
            setAllEntries(validEntries);
        } catch (err) {
            console.error('Punch-Fehler:', err);
            notify(t("manualPunchError"));
        }
    }

    // ----------------------------
    // 4) Summen-Berechnungen
    //    (Wir rufen sie unconditionally auf, Hooks meckern sonst)
    // ----------------------------
    useEffect(() => {
        if (!userProfile || userProfile.isHourly) {
            setWeeklyDiff(0);
            return;
        }

        // weekly
        const weekDates = Array.from({ length: 7 }, (_, i) => addDays(selectedMonday, i));
        const dateStrings = weekDates.map(d => d.toLocaleDateString());

        // Filter
        const weeklyEntries = allEntries.filter(e => dateStrings.includes(new Date(e.startTime).toLocaleDateString()));

        // gruppieren
        const grouped = {};
        weeklyEntries.forEach(entry => {
            const ds = new Date(entry.startTime).toLocaleDateString('de-DE');
            if (!grouped[ds]) grouped[ds] = [];
            grouped[ds].push(entry);
        });

        let sumMins = 0;
        Object.keys(grouped).forEach(ds => {
            const dayEntries = grouped[ds];
            if (dayEntries.length > 0) {
                const dateObj = new Date(dayEntries[0].startTime);
                const expected = getExpectedHoursForDay(dateObj, userProfile, defaultExpectedHours);
                sumMins += computeDailyDiffValue(dayEntries, expected);
            }
        });
        setWeeklyDiff(sumMins);
    }, [allEntries, userProfile, selectedMonday]);

    useEffect(() => {
        if (!userProfile || userProfile.isHourly) {
            setMonthlyDiff(0);
            return;
        }

        // monthly
        const year = selectedMonday.getFullYear();
        const month = selectedMonday.getMonth();
        // Alle Einträge dieses Monats
        const monthlyEntries = allEntries.filter(e => {
            const d = new Date(e.startTime);
            return d.getFullYear() === year && d.getMonth() === month;
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

        // overall
        const dayMap = groupEntriesByDay(allEntries);
        let sumMins = 0;
        for (const ds in dayMap) {
            const dayEntries = dayMap[ds];
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

        // monthlyDiffAll
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

    // ---------------------------------------
    // 5) Falls isHourly => direkt HourlyDashboard
    // ---------------------------------------
    if (userProfile && userProfile.isHourly) {
        return <HourlyDashboard />;
    }

    // ---------------------------------------
    // 6) Ab hier: Festangestellten-Dashboard
    // ---------------------------------------




    // ---------------------------------------
    // 7) Druck-Funktion
    // ---------------------------------------
    function handlePrintReport() {
        if (!printStartDate || !printEndDate) {
            notify(t("printReportError"));
            return;
        }
        import('jspdf').then(({ default: jsPDF }) => {
            import('jspdf-autotable').then(({ default: autoTable }) => {
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
                        const dayEntries = grouped[dateStr].sort((x, y) => x.punchOrder - y.punchOrder);
                        const wStart = dayEntries.find(e => e.punchOrder === 1);
                        const bStart = dayEntries.find(e => e.punchOrder === 2);
                        const bEnd = dayEntries.find(e => e.punchOrder === 3);
                        const wEnd = dayEntries.find(e => e.punchOrder === 4);

                        const ws = wStart ? formatTime(wStart.startTime) : "-";
                        const bs = bStart
                            ? (bStart.breakStart ? formatTime(bStart.breakStart) : formatTime(bStart.startTime))
                            : "-";
                        const be = bEnd
                            ? (bEnd.breakEnd ? formatTime(bEnd.breakEnd) : formatTime(bEnd.startTime))
                            : "-";
                        const we = wEnd ? formatTime(wEnd.endTime) : "-";

                        const expected = getExpectedHoursForDay(new Date(dayEntries[0].startTime), userProfile, defaultExpectedHours);
                        const diffVal = computeDailyDiffValue(dayEntries, expected);
                        const diffText = formatDiffDecimal(diffVal);

                        return [dateStr, ws, bs, be, we, diffText];
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
                window.electron?.ipcRenderer.invoke("saveAndOpenPDF", pdfBase64).catch(err => {
                    console.error("Fehler beim PDF:", err);
                    notify("Fehler beim Öffnen des Berichts.");
                });
                setPrintModalVisible(false);
            });
        });
    }

    // ---------------------------------------
    // 8) Korrektur anlegen
    // ---------------------------------------
    function openCorrectionModal(dateObj) {
        const localDate = dateObj.toLocaleDateString();
        const dayEntries = allEntries.filter(e =>
            new Date(e.startTime).toLocaleDateString() === localDate
        );

        const formatToInputTime = (dateStr) => {
            if (!dateStr) return '';
            const dd = new Date(dateStr);
            if (isNaN(dd.getTime())) return '';
            return dd.toISOString().slice(11, 16);
        };

        setCorrectionDate(formatLocalDate(dateObj));
        const wStart = dayEntries.find(e => e.punchOrder === 1);
        const bStart = dayEntries.find(e => e.punchOrder === 2);
        const bEnd = dayEntries.find(e => e.punchOrder === 3);
        const wEnd = dayEntries.find(e => e.punchOrder === 4);

        setCorrectionData({
            workStart: formatToInputTime(wStart?.startTime),
            breakStart: bStart
                ? (bStart.breakStart ? bStart.breakStart.slice(0,5) : formatToInputTime(bStart.startTime))
                : "",
            breakEnd: bEnd
                ? (bEnd.breakEnd ? bEnd.breakEnd.slice(0,5) : formatToInputTime(bEnd.startTime))
                : "",
            workEnd: formatToInputTime(wEnd?.endTime),
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
            notify("Bitte Work Start und Work End ausfüllen.");
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
                    desiredStart,
                    desiredEnd
                }
            });
            notify("Korrekturantrag erfolgreich gestellt.");
            // Neue Korrekturen laden
            const resCorr = await api.get(`/api/correction/my?username=${userProfile.username}`);
            setCorrectionRequests(resCorr.data || []);

            setShowCorrectionModal(false);
        } catch (err) {
            console.error("Fehler beim Absenden des Korrekturantrags:", err);
            notify("Fehler beim Absenden des Korrekturantrags.");
        }
    }

    // ---------------------------------------
    // 9) UI
    // ---------------------------------------

    // Summen in Dezimaldarstellung
    const weeklyDiffStr = formatDiffDecimal(weeklyDiff);
    const monthlyDiffStr = formatDiffDecimal(monthlyDiff);
    const overallDiffStr = formatDiffDecimal(overallDiff);

    // 7 Tage ab selectedMonday (Mo..So)
    const weekDates = Array.from({ length: 7 }, (_, i) => addDays(selectedMonday, i));

    // Korrekturen sortieren
    const correctionsForWeek = correctionRequests.filter(req => {
        const reqDate = new Date(req.desiredStart);
        return reqDate >= selectedCorrectionMonday && reqDate < addDays(selectedCorrectionMonday, 7);
    });
    const sortedCorrections = (showAllCorrections ? correctionRequests : correctionsForWeek)
        .slice()
        .sort((a, b) => new Date(b.desiredStart) - new Date(a.desiredStart));

    // VacationCalendar -> Reload
    function refreshVacations() {
        fetchVacations();
    }

    return (
        <div className="user-dashboard">
            <Navbar />

            <header className="dashboard-header">
                <h2>{t("title")}</h2>
                <div className="personal-info">
                    <p>
                        <strong>{t("usernameLabel")}:</strong> {userProfile?.username || t("notLoggedIn")}
                    </p>
                    {/* Falls du Summen global anzeigen willst: */}
                    <p><strong>Wochensaldo:</strong> {weeklyDiffStr}</p>
                    <p><strong>Monatssaldo:</strong> {monthlyDiffStr}</p>
                    <p><strong>Gesamtsaldo:</strong> {overallDiffStr}</p>
                </div>
            </header>

            {punchMessage && (
                <div className="punch-message">{punchMessage}</div>
            )}

            {/* Manuelles Stempeln */}
            <section className="punch-section">
                <h3>{t("manualPunchTitle")}</h3>
                <button onClick={handleManualPunch}>{t("manualPunchButton")}</button>
            </section>

            {/* Wochenübersicht */}
            <section className="time-tracking-section">
                <h3>{t("weeklyOverview")}</h3>
                <div className="week-navigation">
                    <button onClick={() => setSelectedMonday(prev => addDays(prev, -7))}>
                        ← {t("prevWeek")}
                    </button>
                    <input
                        type="date"
                        value={selectedMonday.toISOString().slice(0, 10)}
                        onChange={(e) => {
                            const picked = new Date(e.target.value);
                            if (!isNaN(picked.getTime())) {
                                setSelectedMonday(getMondayOfWeek(picked));
                            }
                        }}
                    />
                    <button onClick={() => setSelectedMonday(prev => addDays(prev, 7))}>
                        {t("nextWeek")} →
                    </button>
                </div>

                {/* Hier Tag-Cards */}
                <div className="week-display">
                    {weekDates.map((dayObj, idx) => {
                        const ds = dayObj.toLocaleDateString();
                        const dayEntries = allEntries.filter(e =>
                            new Date(e.startTime).toLocaleDateString() === ds
                        );

                        const expectedForDay = getExpectedHoursForDay(dayObj, userProfile, defaultExpectedHours);
                        let diffVal = 0;
                        if (dayEntries.length >= 4) {
                            diffVal = computeDailyDiffValue(dayEntries, expectedForDay);
                        }
                        const dailyDiffStr = dayEntries.length >= 4 ? formatDiffDecimal(diffVal) : '';

                        return (
                            <div key={idx} className="day-card">
                                <div className="day-card-header">
                                    <h4>
                                        {dayObj.toLocaleDateString('de-DE', { weekday: 'long' })}, {ds}
                                        <span className="expected-hours">
                      {` (${t("expectedWorkHours")}: ${expectedForDay.toFixed(2)}h)`}
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
                                            return (
                                                <li key={e.id}>
                          <span className="entry-label">
                            {getStatusLabel(e.punchOrder)}:
                          </span>{" "}
                                                    {displayTime}
                                                </li>
                                            );
                                        })}
                                    </ul>
                                )}

                                {/* Korrektur-Button */}
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

            {/* VacationCalendar */}
            <section className="vacation-calendar-section">
                <h3>{t("vacationTitle")}</h3>
                <VacationCalendar
                    vacationRequests={vacationRequests}
                    userProfile={userProfile}
                    onRefreshVacations={refreshVacations}
                />
            </section>

            {/* Korrekturen */}
            <UserCorrectionsPanel
                t={t}
                showCorrectionsPanel={showCorrectionsPanel}
                setShowCorrectionsPanel={setShowCorrectionsPanel}
                selectedCorrectionMonday={selectedCorrectionMonday}
                setSelectedCorrectionMonday={setSelectedCorrectionMonday}
                correctionRequests={correctionRequests}
                showAllCorrections={showAllCorrections}
                setShowAllCorrections={setShowAllCorrections}
                sortedCorrections={sortedCorrections}
            />

            {/* Übersicht monthlyDiffAll */}
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
                                    <td>{formatDiffDecimal(diffVal)}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                )}
            </section>

            {/* Print-Button */}
            <div className="print-report-container">
                <button onClick={() => setPrintModalVisible(true)}>
                    {t("printReportButton")}
                </button>
            </div>

            {/* Print Modal */}
            <UserPrintModal
                t={t}
                visible={printModalVisible}
                printStartDate={printStartDate}
                setPrintStartDate={setPrintStartDate}
                printEndDate={printEndDate}
                setPrintEndDate={setPrintEndDate}
                handlePrintReport={handlePrintReport}
                onClose={() => setPrintModalVisible(false)}
            />

            {/* Correction Modal */}
            <UserCorrectionModal
                visible={showCorrectionModal}
                correctionDate={correctionDate}
                correctionData={correctionData}
                handleCorrectionInputChange={handleCorrectionInputChange}
                handleCorrectionSubmit={handleCorrectionSubmit}
                onClose={() => setShowCorrectionModal(false)}
            />
        </div>
    );
}

export default UserDashboard;
