// src/pages/UserDashboard.jsx
import { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../utils/api';
import Navbar from '../components/Navbar';
import VacationCalendar from '../components/VacationCalendar';
import { jsPDF } from 'jspdf';
import '../styles/UserDashboard.css';
import { useNotification } from '../context/NotificationContext';
import { useTranslation } from '../context/LanguageContext';
import HourlyDashboard from './HourlyDashboard';

/* ================================
   HELPER FUNCTIONS
================================ */

// Gibt den Montag der Woche des übergebenen Datums zurück.
const getMondayOfWeek = (date) => {
    const copy = new Date(date);
    const day = copy.getDay();
    const diff = day === 0 ? 6 : day - 1;
    copy.setDate(copy.getDate() - diff);
    copy.setHours(0, 0, 0, 0);
    return copy;
};

// Fügt einem Datum eine bestimmte Anzahl an Tagen hinzu.
const addDays = (date, days) => {
    const d = new Date(date);
    d.setDate(d.getDate() + days);
    return d;
};

// Konvertiert einen 32-stelligen Hex-String in ASCII.
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

// Formatiert einen Datums-/Zeit-String als Uhrzeit (deutsch, 2-stellig).
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

// Formatiert ein Datum als "YYYY-MM-DD".
const formatLocalDate = (date) => {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
};

// Formatiert einen Datums-String als "DD-MM-YYYY".
const formatDate = (dateStr) => {
    const date = new Date(dateStr);
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();
    return `${day}-${month}-${year}`;
};

// Gibt die Minuten seit Mitternacht zurück.
const getMinutesSinceMidnight = (datetimeStr) => {
    if (!datetimeStr) return 0;
    const d = new Date(datetimeStr);
    return d.getHours() * 60 + d.getMinutes();
};

/**
 * computeDailyDiffValue:
 * Berechnet (in Minuten) die Differenz eines Tages (gearbeitete Zeit minus Sollzeit).
 */
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

/**
 * getExpectedHoursForDay:
 * Ermittelt anhand des WeeklySchedule den erwarteten Sollwert für einen Tag.
 * Für stundenbasierte Nutzer (isHourly === true) wird 0 zurückgegeben.
 */
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

// Formatiert eine Differenz (in Minuten) als +/- hh mm.
const formatDiff = (diff, t) => {
    const sign = diff >= 0 ? '+' : '-';
    const abs = Math.abs(diff);
    const hrs = Math.floor(abs / 60);
    const mins = abs % 60;
    return `${sign}${hrs} ${t("hours")} ${mins} ${t("minutes")}`;
};

// Gruppiert Einträge nach Tag ("DD.MM.YYYY").
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

/* ================================
   COMPONENT: UserDashboard
================================ */
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

    // Für nicht-stundenbasierte Nutzer: Differenzen
    const [weeklyDiff, setWeeklyDiff] = useState(0);
    const [monthlyDiff, setMonthlyDiff] = useState(0);
    const [overallDiff, setOverallDiff] = useState(0);
    const [monthlyDiffAll, setMonthlyDiffAll] = useState({}); // { "YYYY-MM": diff }

    // Für Wochenansicht
    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));

    const defaultExpectedHours =
        userProfile && userProfile.dailyWorkHours !== undefined
            ? Number(userProfile.dailyWorkHours)
            : 8;

    /* ===== Profil laden ===== */
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

    /* ===== Einträge und Urlaubsanträge laden ===== */
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

    /* ===== NFC-Polling ===== */
    useEffect(() => {
        const interval = setInterval(() => {
            doNfcCheck();
        }, 2000);
        return () => clearInterval(interval);
    }, [userProfile, lastPunchTime]);

    async function doNfcCheck() {
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
                    try {
                        const params = { username: cardUser };
                        if (userProfile && userProfile.isHourly) params.isHourly = true;
                        await api.post('/api/timetracking/punch', null, { params });
                        setPunchMessage(`${t("punchMessage")}: ${cardUser}`);
                        setTimeout(() => setPunchMessage(''), 3000);
                        if (userProfile && userProfile.username.toLowerCase() === cardUser.toLowerCase()) {
                            fetchEntries();
                        }
                    } catch (punchErr) {
                        const errMsg = punchErr.response?.data?.message || punchErr.message;
                        console.error('Punch error:', errMsg);
                    }
                }
            }
        } catch (err) {
            console.error('NFC fetch error:', err);
        }
    }

    function showPunchMessage(msg) {
        setPunchMessage(msg);
        setTimeout(() => setPunchMessage(''), 3000);
    }

    async function handleManualPunch() {
        try {
            const params = { username: userProfile.username };
            if (userProfile.isHourly) params.isHourly = true;
            await api.post('/api/timetracking/punch', null, { params });
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

    /* ===== Differenzen berechnen (nur für nicht-stundenbasierte Nutzer) ===== */
    // Wöchentliche Differenz
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
    }, [allEntries, userProfile, selectedMonday, defaultExpectedHours]);

    // Monatliche Differenz (aktueller Monat)
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
    }, [allEntries, userProfile, selectedMonday, defaultExpectedHours]);

    // Gesamtdifferenz über alle Zeit
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
    }, [allEntries, userProfile, defaultExpectedHours]);

    // Differenz für alle Monate (als Objekt { "YYYY-MM": diff })
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
    }, [allEntries, userProfile, defaultExpectedHours]);

    // Wochenanzeige: Gruppierung nach Tag
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

    // Diff für heute (nur für nicht-stundenbasierte Nutzer)
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

    // Falls der User stundenbasiert arbeitet, leite zur HourlyDashboard-Seite weiter
    if (userProfile?.isHourly) {
        return <HourlyDashboard />;
    }

    // Druck-Funktion (als Beispiel – hier kann noch erweitert werden)
    function handlePrintReport() {
        if (!printStartDate || !printEndDate) {
            notify(t("printReportError"));
            return;
        }
        const doc = new jsPDF();
        doc.text(t("printReportTitle"), 10, 10);
        window.open(doc.output('bloburl'), '_blank');
        setPrintModalVisible(false);
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
                    {!userProfile?.isHourly && (
                        <>
                            <p>
                                <strong>{t("expectedWorkHours")}:</strong> {getExpectedHoursForDay(new Date(), userProfile, defaultExpectedHours)} {t("hours")}
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
                            </div>
                        );
                    })}
                </div>
            </section>

            {!userProfile?.isHourly && (
                <section className="monthly-all">
                    <h3>{t("monthlyOverview")} - {t("allMonths")}</h3>
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
        </div>
    );
};

export default UserDashboard;
