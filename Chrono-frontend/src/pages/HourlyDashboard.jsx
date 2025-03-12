// src/pages/HourlyDashboard.jsx
import React, { useState, useEffect, useCallback } from 'react';
import { jsPDF } from 'jspdf';
import Navbar from '../components/Navbar';
import api from '../utils/api';
import { useNotification } from '../context/NotificationContext';
import { useTranslation } from '../context/LanguageContext';
import '../styles/UserDashboard.css';
import VacationCalendar from "../components/VacationCalendar.jsx";

/* =====================================
   HELPER FUNCTIONS
==================================== */

// Returns the Monday of the week for a given date.
const getMondayOfWeek = (date) => {
    const copy = new Date(date);
    const day = copy.getDay();
    const diff = day === 0 ? 6 : day - 1;
    copy.setDate(copy.getDate() - diff);
    copy.setHours(0, 0, 0, 0);
    return copy;
};

// Adds a given number of days to a date.
const addDays = (date, days) => {
    const d = new Date(date);
    d.setDate(d.getDate() + days);
    return d;
};

// Converts a 32-character hex string to ASCII.
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

// Returns the number of minutes since midnight for a given datetime string.
const getMinutesSinceMidnight = (datetimeStr) => {
    if (!datetimeStr) return 0;
    const d = new Date(datetimeStr);
    return d.getHours() * 60 + d.getMinutes();
};

// Formats a date as "YYYY-MM-DD".
const formatLocalDate = (date) => {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
};

// Formats a date string as "DD-MM-YYYY".
const formatDate = (dateStr) => {
    const date = new Date(dateStr);
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();
    return `${day}-${month}-${year}`;
};

// Formats a date object as a short time string "HH:MM".
const formatTimeShort = (dateObj) => {
    return dateObj.toLocaleTimeString('de-DE', {
        hour: '2-digit',
        minute: '2-digit',
        timeZone: 'Europe/Berlin'
    });
};

/**
 * computeDailyDiffValue:
 * Calculates the difference (in minutes) for a day (worked time minus expected time).
 * Considers Work Start, Break Start, Break End, and Work End.
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
 * Determines the expected work hours for a day based on the user's weekly schedule.
 * For hourly users, returns 0.
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

// Groups entries by day ("DD.MM.YYYY")
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

// Returns all entries for today (formatted in 'de-DE')
const getTodayEntries = (entries) => {
    const todayStr = new Date().toLocaleDateString('de-DE');
    return entries.filter(e => new Date(e.startTime).toLocaleDateString('de-DE') === todayStr);
};

/* =====================================
   COMPONENT: HourlyDashboard
===================================== */
function HourlyDashboard() {
    const { t } = useTranslation();
    const { notify } = useNotification();

    const [userProfile, setUserProfile] = useState(null);
    const [allEntries, setAllEntries] = useState([]);
    const [punchMessage, setPunchMessage] = useState('');
    const [lastPunchTime, setLastPunchTime] = useState(0);
    const [selectedMonth, setSelectedMonth] = useState(
        new Date(new Date().getFullYear(), new Date().getMonth(), 1)
    );
    // dailyData stores for each day: totalMins, earliestStart, latestEnd
    const [dailyData, setDailyData] = useState({});
    const [printModalVisible, setPrintModalVisible] = useState(false);
    const [printStartDate, setPrintStartDate] = useState(formatLocalDate(new Date()));
    const [printEndDate, setPrintEndDate] = useState(formatLocalDate(new Date()));

    // Vacation states (optional, falls benötigt)
    const [vacationRequests, setVacationRequests] = useState([]);
    const [vacationForm, setVacationForm] = useState({ startDate: '', endDate: '' });

    // Use defaultExpectedHours from profile if available
    const defaultExpectedHours =
        userProfile && userProfile.dailyWorkHours !== undefined
            ? Number(userProfile.dailyWorkHours)
            : 8;

    /* ===== Load Profile ===== */
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

    /* ===== Load Entries and Vacations ===== */
    useEffect(() => {
        if (userProfile) {
            fetchEntries();
            fetchVacations();
        }
    }, [userProfile]);

    const fetchEntries = useCallback(async () => {
        if (!userProfile) return;
        try {
            const res = await api.get(`/api/timetracking/history?username=${userProfile.username}`);
            const validEntries = (res.data || []).filter(e => [1, 2, 3, 4].includes(e.punchOrder));
            setAllEntries(validEntries);
        } catch (err) {
            console.error("Error loading time entries", err);
        }
    }, [userProfile]);

    const fetchVacations = async () => {
        try {
            const res = await api.get('/api/vacation/my');
            setVacationRequests(res.data || []);
        } catch (err) {
            console.error("Error loading vacation requests", err);
        }
    };

    /* ===== NFC-Polling ===== */
    const doNfcCheck = useCallback(async () => {
        if (!userProfile?.isHourly) return;
        // Allow a maximum of 2 stamps per day.
        if (getTodayEntries(allEntries).length >= 2) return;
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
                    // Re-check if already 2 stamps exist
                    if (getTodayEntries(allEntries).length >= 2) {
                        notify(t("maxStampsReached"));
                        return;
                    }
                    try {
                        // Pass currentDate to force creation of a new entry for a new day.
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
        const interval = setInterval(() => {
            doNfcCheck();
        }, 2000);
        return () => clearInterval(interval);
    }, [doNfcCheck]);

    /* ===== Manual Punch ===== */
    const handleManualPunch = async () => {
        if (!userProfile?.isHourly) {
            notify("User is not hourly!");
            return;
        }
        if (getTodayEntries(allEntries).length >= 2) {
            notify(t("maxStampsReached"));
            return;
        }
        try {
            await api.post('/api/timetracking/punch', null, {
                params: { username: userProfile.username, isHourly: true, currentDate: formatLocalDate(new Date()) }
            });
            setPunchMessage(`${t("manualPunchMessage")} ${userProfile.username}`);
            setTimeout(() => setPunchMessage(''), 3000);
            fetchEntries();
        } catch (err) {
            console.error("Punch error:", err);
            notify(t("manualPunchError"));
        }
    };

    /* ===== Monthly Overview Calculation ===== */
    useEffect(() => {
        if (!userProfile?.isHourly) {
            setDailyData({});
            return;
        }
        const filtered = allEntries.filter(entry => {
            const d = new Date(entry.startTime);
            return (
                d.getFullYear() === selectedMonth.getFullYear() &&
                d.getMonth() === selectedMonth.getMonth()
            );
        });
        const daily = {};
        filtered.forEach(entry => {
            const dayStr = new Date(entry.startTime).toLocaleDateString('de-DE');
            const endTimeStr = entry.endTime || entry.startTime;
            const minutesWorked = getMinutesSinceMidnight(endTimeStr) - getMinutesSinceMidnight(entry.startTime);
            if (!daily[dayStr]) {
                daily[dayStr] = { totalMins: 0, earliestStart: null, latestEnd: null };
            }
            daily[dayStr].totalMins += minutesWorked;
            const st = new Date(entry.startTime);
            if (!daily[dayStr].earliestStart || st < daily[dayStr].earliestStart) {
                daily[dayStr].earliestStart = st;
            }
            const en = new Date(endTimeStr);
            if (!daily[dayStr].latestEnd || en > daily[dayStr].latestEnd) {
                daily[dayStr].latestEnd = en;
            }
        });
        setDailyData(daily);
    }, [allEntries, selectedMonth, userProfile]);

    // Total work time in current month
    let totalMins = 0;
    Object.values(dailyData).forEach(dayObj => {
        totalMins += dayObj.totalMins;
    });
    const totalHrs = Math.floor(totalMins / 60);
    const totalRemMins = totalMins % 60;

    // Monthly navigation
    const handlePrevMonth = () => {
        setSelectedMonth(new Date(selectedMonth.getFullYear(), selectedMonth.getMonth() - 1, 1));
    };
    const handleNextMonth = () => {
        setSelectedMonth(new Date(selectedMonth.getFullYear(), selectedMonth.getMonth() + 1, 1));
    };

    /* ===== Print Report ===== */
    const handlePrintReport = () => {
        if (!printStartDate || !printEndDate) {
            notify(t("printReportError"));
            return;
        }
        const doc = new jsPDF();
        doc.text(`${t("printReportTitle")} - ${userProfile?.username}`, 10, 10);
        window.open(doc.output('bloburl'), '_blank');
        setPrintModalVisible(false);
    };

    /* ===== Vacation Submission ===== */
    const handleVacationSubmit = async (e) => {
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
    };

    /* ===== Rendering ===== */
    if (!userProfile) {
        return (
            <div className="user-dashboard">
                <Navbar />
                <p>Loading...</p>
            </div>
        );
    }

    // For weekly grouping (not used in hourly view; we display only the monthly overview)
    // Optionally you could add a weekly view as well.

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

            <section className="punch-section">
                <h3>{t("manualPunchTitle")}</h3>
                <button onClick={handleManualPunch}>{t("manualPunchButton")}</button>
            </section>

            <section className="monthly-overview">
                <h3>{t("monthlyOverview")}</h3>
                <div className="month-navigation">
                    <button onClick={handlePrevMonth}>← {t("prevMonth")}</button>
                    <span>
            {selectedMonth.toLocaleDateString('de-DE', { month: 'long', year: 'numeric' })}
          </span>
                    <button onClick={handleNextMonth}>{t("nextMonth")} →</button>
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
                            </tr>
                            </thead>
                            <tbody>
                            {Object.entries(dailyData)
                                .sort(([a], [b]) => {
                                    const [dayA, monA, yrA] = a.split('.');
                                    const [dayB, monB, yrB] = b.split('.');
                                    return new Date(yrA, monA - 1, dayA) - new Date(yrB, monB - 1, dayB);
                                })
                                .map(([dayStr, info]) => {
                                    const [dd, mm, yyyy] = dayStr.split('.');
                                    const dateObj = new Date(yyyy, mm - 1, dd);
                                    const weekday = dateObj.toLocaleDateString('de-DE', { weekday: 'long' });
                                    const earliest = info.earliestStart ? formatTimeShort(info.earliestStart) : '-';
                                    const latest = info.latestEnd ? formatTimeShort(info.latestEnd) : '-';
                                    const hrs = Math.floor(info.totalMins / 60);
                                    const remMins = info.totalMins % 60;
                                    return (
                                        <tr key={dayStr}>
                                            <td>{weekday}</td>
                                            <td>{dayStr}</td>
                                            <td>
                                                {earliest} {t("to")} {latest} ({hrs} {t("hours")} {remMins} {t("minutes")})
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </>
                )}
            </section>

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
                            onChange={(e) =>
                                setVacationForm({ ...vacationForm, startDate: e.target.value })
                            }
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label>{t("endDate")}:</label>
                        <input
                            type="date"
                            name="endDate"
                            value={vacationForm.endDate}
                            onChange={(e) =>
                                setVacationForm({ ...vacationForm, endDate: e.target.value })
                            }
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
                    {/* Hier wird angenommen, dass VacationCalendar eine eigene Komponente ist */}
                    {/* und dass sie alle genehmigten Urlaubsanträge erhält */}
                    <VacationCalendar vacationRequests={vacationRequests.filter(v => v.approved)} />
                </div>
            </section>
        </div>
    );
}

export default HourlyDashboard;
