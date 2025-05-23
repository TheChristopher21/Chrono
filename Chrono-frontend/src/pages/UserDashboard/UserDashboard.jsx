// src/pages/UserDashboard/UserDashboard.jsx
import React, { useState, useEffect, useRef } from 'react';
import Navbar from '../../components/Navbar';
import VacationCalendar from '../../components/VacationCalendar';
import { useAuth } from '../../context/AuthContext';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import api from '../../utils/api';

import '../../styles/global.css';
import '../../styles/UserDashboardScoped.css';  // Wichtig: Gescoptes Dashboard-CSS
import "jspdf-autotable";
import fileDownload from "js-file-download";
import jsPDF from "jspdf";

import HourlyDashboard from '../../pages/HourlyDashboard/HourlyDashboard.jsx';

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
    groupEntriesByDay,
    expandDayRows,
    isLateTime
} from './userDashUtils';
import { minutesToHours } from '../PercentageDashboard/percentageDashUtils';

import UserCorrectionModal from './UserCorrectionModal';
import UserCorrectionsPanel from './UserCorrectionsPanel';
import PrintReportModal from "../../components/PrintReportModal.jsx";

function UserDashboard() {
    const { currentUser } = useAuth();
    const { notify } = useNotification();
    const { t } = useTranslation();

    const [userProfile, setUserProfile] = useState(null);
    const [allEntries, setAllEntries] = useState([]);
    const [vacationRequests, setVacationRequests] = useState([]);
    const [correctionRequests, setCorrectionRequests] = useState([]);

    // Punch
    const [punchMessage, setPunchMessage] = useState('');
    const lastPunchTimeRef = useRef(0);

    // Weekly
    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));

    // Corrections
    const [showCorrectionsPanel, setShowCorrectionsPanel] = useState(false);
    const [showAllCorrections, setShowAllCorrections] = useState(false);
    const [selectedCorrectionMonday, setSelectedCorrectionMonday] = useState(getMondayOfWeek(new Date()));

    // Print
    const [printModalVisible, setPrintModalVisible] = useState(false);
    const [printStartDate, setPrintStartDate] = useState(formatLocalDate(new Date()));
    const [printEndDate, setPrintEndDate] = useState(formatLocalDate(new Date()));

    // CorrectionModal
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
    const [weeklyDiff, setWeeklyDiff] = useState(0);
    const [monthlyDiff, setMonthlyDiff] = useState(0);
    const [overallDiff, setOverallDiff] = useState(0);
    const [monthlyDiffAll, setMonthlyDiffAll] = useState({});

    const defaultExpectedHours = userProfile
        ? Number(userProfile.dailyWorkHours || 8)
        : 8;

    async function loadProfile() {
        try {
            const res = await api.get('/api/auth/me');
            const profile = res.data;

            // WeeklySchedule fix
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

    // 1) Profil laden
    useEffect(() => {
        loadProfile();
    }, [t, currentUser]);

    // 2) Daten laden
    useEffect(() => {
        if (!userProfile) return;

        async function fetchAllData() {
            try {
                // Einträge
                const resEntries = await api.get(`/api/timetracking/history?username=${userProfile.username}`);
                const expanded = expandDayRows(resEntries.data || []);
                const validEntries = expanded.filter(e => [1, 2, 3, 4].includes(e.punchOrder));
                setAllEntries(validEntries);

                // Urlaub
                const resVacation = await api.get('/api/vacation/my');
                setVacationRequests(resVacation.data || []);

                // Korrekturen
                const resCorr = await api.get(`/api/correction/my?username=${userProfile.username}`);
                setCorrectionRequests(resCorr.data || []);
            } catch (err) {
                console.error("Fehler beim Laden der Daten:", err);
            }
        }
        fetchAllData();
    }, [userProfile]);

    // 3) NFC-Abfrage per Intervall
    useEffect(() => {
        const interval = setInterval(() => doNfcCheck(), 2000);
        return () => clearInterval(interval);
    }, []);

    async function doNfcCheck() {
        try {
            const response = await fetch(process.env.APIURL + '/api/nfc/read/1');
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
                        loadProfile();
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

            const resEntries = await api.get(`/api/timetracking/history?username=${userProfile.username}`);
            const expanded = expandDayRows(resEntries.data || []);
            const validEntries = expanded.filter(e => [1, 2, 3, 4].includes(e.punchOrder));
            setAllEntries(validEntries);
            loadProfile();
        } catch (err) {
            console.error('Punch-Fehler:', err);
            notify(t("manualPunchError"));
        }
    }

    // 4) Summenberechnungen
    useEffect(() => {
        if (!userProfile || userProfile.isHourly) {
            setWeeklyDiff(0);
            return;
        }
        const weekDates = Array.from({ length: 7 }, (_, i) => addDays(selectedMonday, i));
        const dateStrings = weekDates.map(d => d.toLocaleDateString());

        const weeklyEntries = allEntries.filter(e =>
            dateStrings.includes(new Date(e.startTime).toLocaleDateString())
        );

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
        const year = selectedMonday.getFullYear();
        const month = selectedMonday.getMonth();

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

    // 5) Falls isHourly
    if (userProfile && userProfile.isHourly) {
        return <HourlyDashboard />;
    }

    // 7) Print
    async function handlePrintReport() {
        if (!printStartDate || !printEndDate) {
            notify(t("missingDateRange") || "Zeitraum fehlt");
            return;
        }
        setPrintModalVisible(false);

        try {
            const { data } = await api.get("/api/timetracking/report", {
                params: {
                    username: userProfile.username,
                    startDate: printStartDate,
                    endDate:   printEndDate,
                },
            });

            const doc = new jsPDF("p", "mm", "a4");
            doc.setFontSize(14);
            doc.text(`Zeitenbericht für ${userProfile.firstName} ${userProfile.lastName}`, 14, 15);
            doc.setFontSize(11);
            doc.text(`Zeitraum: ${printStartDate} – ${printEndDate}`, 14, 22);

            const rows = (data || []).map(e => [
                e.date,
                e.workStart  || "-",
                e.breakStart || "-",
                e.breakEnd   || "-",
                e.workEnd    || "-"
            ]);

            doc.autoTable({
                head: [["Datum", "Work-Start", "Break-Start", "Break-End", "Work-End"]],
                body: rows,
                startY: 30,
                margin: { left: 14, right: 14 },
                styles: { fontSize: 9, cellPadding: 2 },
                headStyles: { fillColor: [0, 123, 255], halign: "center" },
                didDrawPage: (data) => {
                    const page = `${doc.internal.getNumberOfPages()}`;
                    doc.text(`Seite ${data.pageNumber}`, doc.internal.pageSize.getWidth() - 14, 10, { align: "right" });
                },
            });
            doc.save(`timesheet_${userProfile.username}_${printStartDate}_${printEndDate}.pdf`);
        } catch (err) {
            console.error("Fehler beim Generieren", err);
            notify(t("printReportError") || "PDF-Fehler");
        }
    }

    // 8) Correction Modal
    function openCorrectionModal(dateObj) {
        const localDate = dateObj.toLocaleDateString();
        const dayEntries = allEntries.filter(e =>
            new Date(e.startTime).toLocaleDateString() === localDate
        );

        const formatToInputTime = dateStr => {
            if (!dateStr) return '';
            const dd = new Date(dateStr);
            if (isNaN(dd.getTime())) return '';
            return dd.toISOString().slice(11, 16);
        };
        setCorrectionDate(formatLocalDate(dateObj));

        const wStart = dayEntries.find(e => e.punchOrder === 1);
        const bStart = dayEntries.find(e => e.punchOrder === 2);
        const bEnd   = dayEntries.find(e => e.punchOrder === 3);
        const wEnd   = dayEntries.find(e => e.punchOrder === 4);

        setCorrectionData({
            workStart:  formatToInputTime(wStart?.startTime),
            breakStart: bStart
                ? (bStart.breakStart ? bStart.breakStart.slice(0, 5) : formatToInputTime(bStart.startTime))
                : "",
            breakEnd:   bEnd
                ? (bEnd.breakEnd ? bEnd.breakEnd.slice(0, 5) : formatToInputTime(bEnd.startTime))
                : "",
            workEnd:    formatToInputTime(wEnd?.endTime),
            reason:     ""
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
        const desiredEnd   = `${correctionDate}T${correctionData.workEnd}`;

        try {
            await api.post('/api/correction/create-full', null, {
                params: {
                    username: userProfile.username,
                    date: correctionDate,
                    workStart:  correctionData.workStart,
                    breakStart: correctionData.breakStart,
                    breakEnd:   correctionData.breakEnd,
                    workEnd:    correctionData.workEnd,
                    reason:     correctionData.reason,
                    desiredStart,
                    desiredEnd
                }
            });
            notify(t("correctionSubmitSuccess") || "Korrekturantrag erfolgreich gestellt.");

            const resCorr = await api.get(`/api/correction/my?username=${userProfile.username}`);
            setCorrectionRequests(resCorr.data || []);

            setShowCorrectionModal(false);
        } catch (err) {
            console.error("Fehler beim Absenden des Korrekturantrags:", err);
            notify(t("correctionSubmitError") || "Fehler beim Absenden des Korrekturantrags.");
        }
    }

    // 9) UI
    const weeklyDiffStr   = formatDiffDecimal(weeklyDiff);
    const monthlyDiffStr  = formatDiffDecimal(monthlyDiff);
    const overallDiffStr  = formatDiffDecimal(overallDiff);
    const overtimeBalanceStr = formatDiffDecimal(userProfile?.trackingBalanceInMinutes || 0);

    const weekDates = Array.from({ length: 7 }, (_, i) => addDays(selectedMonday, i));
    const weeklyExpectedMins = weekDates.reduce((sum, d) => {
        const exp = getExpectedHoursForDay(d, userProfile, defaultExpectedHours);
        return sum + (exp ?? 0) * 60;
    }, 0);
    const weeklyExpectedStr = minutesToHours(weeklyExpectedMins);

    // Korrekturen
    const correctionsForWeek = correctionRequests.filter(req => {
        const reqDate = new Date(req.desiredStart);
        return reqDate >= selectedCorrectionMonday && reqDate < addDays(selectedCorrectionMonday, 7);
    });
    const sortedCorrections = (showAllCorrections ? correctionRequests : correctionsForWeek)
        .slice()
        .sort((a, b) => new Date(b.desiredStart) - new Date(a.desiredStart));

    async function fetchVacations() {
        try {
            const resVacation = await api.get('/api/vacation/my');
            setVacationRequests(resVacation.data || []);
        } catch (err) {
            console.error("Fehler beim Nachladen der Urlaube:", err);
            notify("Fehler beim Laden der Urlaube.");
        }
    }
    function refreshVacations() {
        fetchVacations();
    }

    // Falls userProfile nicht existiert -> Laden
    if (!userProfile) {
        return (
            <div className="user-dashboard scoped-dashboard">
                <Navbar />
                <p>Lädt Benutzerprofil…</p>
            </div>
        );
    }

    return (
        <div className="user-dashboard scoped-dashboard">
            <Navbar />

            <header className="dashboard-header">
                <h2>{t("title")}</h2>
                <div className="personal-info">
                    <p><strong>{t("usernameLabel")}:</strong> {userProfile?.username || t("notLoggedIn")}</p>
                    <p>
                        <strong>{t('expected')}:</strong> {weeklyExpectedStr}
                    </p>
                    <p>
                        <strong>{t('overtimeBalance')}:</strong> {overtimeBalanceStr}
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

                {/* (Evtl. Alt-Liste, kann man entfernen, falls nicht benötigt) */}
                <ul className="time-entry-list" style={{ display: 'none' }}>
                    {allEntries
                        .filter(e =>
                            new Date(e.startTime).toLocaleDateString() === selectedMonday.toLocaleDateString()
                        )
                        .map((e, idx) => (
                            <li
                                key={idx}
                                className={`time-entry ${isLateTime(e.time) ? 'late-time' : ''}`}
                            >
                                <span className="entry-label">{e.label}</span>: {e.time}
                            </li>
                        ))}
                </ul>

                {/* Day-Cards */}
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
  {` (${t("expectedWorkHours")}: ${(expectedForDay ?? 0).toFixed(2)}h)`}
</span>

                                    </h4>
                                    {dailyDiffStr && (
                                        <span className="daily-diff">({dailyDiffStr})</span>
                                    )}
                                </div>

                                {dayEntries.length === 0 ? (
                                    <p className="no-entries">{t("noEntries")}</p>
                                ) : (
                                    <ul className="time-entry-list">
                                        {dayEntries
                                            .sort((a, b) => a.punchOrder - b.punchOrder)
                                            .map(e => {
                                                let displayTime = '-';
                                                if (e.punchOrder === 1) {
                                                    displayTime = e.workStart ? formatTime(e.workStart) : '-';
                                                } else if (e.punchOrder === 2) {
                                                    displayTime = e.breakStart ? formatTime(e.breakStart) : '-';
                                                } else if (e.punchOrder === 3) {
                                                    displayTime = e.breakEnd ? formatTime(e.breakEnd) : '-';
                                                } else if (e.punchOrder === 4) {
                                                    displayTime = e.workEnd   ? formatTime(e.workEnd)   : '-';
                                                }
                                                return (
                                                    <li key={`${e.dailyDate}-${e.punchOrder}`}>
        <span className="entry-label">
          {getStatusLabel(e.punchOrder)}:
        </span>{" "}
                                                        <span className={isLateTime(displayTime) ? 'late-time' : ''}>
          {displayTime}
        </span>
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

            {/* Urlaub */}
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
                showAllCorrections={showAllCorrections}
                setShowAllCorrections={setShowAllCorrections}
                sortedCorrections={sortedCorrections}
            />

            {/* Monatliche Übersicht */}
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

            <div className="print-report-container">
                <button onClick={() => setPrintModalVisible(true)}>
                    {t("printReportButton")}
                </button>
            </div>

            {/* Print Modal */}
            <PrintReportModal
                t={t}
                visible={printModalVisible}
                startDate={printStartDate}
                setStartDate={setPrintStartDate}
                endDate={printEndDate}
                setEndDate={setPrintEndDate}
                onConfirm={handlePrintReport}
                onClose={() => setPrintModalVisible(false)}
                cssScope="user"
            />

            {/* Correction Modal */}
            <UserCorrectionModal
                visible={showCorrectionModal}
                correctionDate={correctionDate}
                correctionData={correctionData}
                handleCorrectionInputChange={handleCorrectionInputChange}
                handleCorrectionSubmit={handleCorrectionSubmit}
                onClose={() => setShowCorrectionModal(false)}
                t={t} // <-- HIER ergänzen!
            />
        </div>
    );
}

export default UserDashboard;
