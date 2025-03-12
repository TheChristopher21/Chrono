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
===================================== */

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

// Parses a time string "HH:MM" into minutes.
const parseTimeToMinutes = (timeStr) => {
    if (!timeStr) return 0;
    const parts = timeStr.split(':');
    const hours = parseInt(parts[0], 10);
    const minutes = parseInt(parts[1], 10);
    return hours * 60 + minutes;
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
    // dailyData will store, per day, the calculated total minutes and optional times for display.
    const [dailyData, setDailyData] = useState({});
    const [printModalVisible, setPrintModalVisible] = useState(false);
    const [printStartDate, setPrintStartDate] = useState(formatLocalDate(new Date()));
    const [printEndDate, setPrintEndDate] = useState(formatLocalDate(new Date()));

    // Vacation states (optional)
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
                // Falls kein Wochenplan vorhanden, setze einen Standard
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
                    if (getTodayEntries(allEntries).length >= 2) {
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
        if (!userProfile) {
            setDailyData({});
            return;
        }
        // Für stundenbasierte Nutzer: Gruppiere Einträge pro Tag und berechne pro Tag die Arbeitszeit
        const dayMap = {};
        allEntries.forEach(entry => {
            const dayStr = new Date(entry.startTime).toLocaleDateString('de-DE');
            if (!dayMap[dayStr]) dayMap[dayStr] = [];
            dayMap[dayStr].push(entry);
        });
        const daily = {};
        Object.keys(dayMap).forEach(dayStr => {
            const entries = dayMap[dayStr];
            let totalMins = 0;
            let startEntry = entries.find(e => e.punchOrder === 1);
            // Fallback: Falls kein Eintrag mit punchOrder=1, nimm den frühesten Eintrag
            if (!startEntry) {
                startEntry = entries.reduce((prev, curr) => (new Date(prev.startTime) < new Date(curr.startTime) ? prev : curr), entries[0]);
            }
            let endEntry = entries.find(e => e.punchOrder === 4);
            if (!endEntry) {
                // Fallback: punchOrder=2
                endEntry = entries.find(e => e.punchOrder === 2);
            }
            if (startEntry && endEntry) {
                const start = new Date(startEntry.startTime);
                const end = new Date(endEntry.endTime || endEntry.startTime);
                if (end.toDateString() !== start.toDateString()) {
                    const midnight = new Date(start);
                    midnight.setHours(24, 0, 0, 0);
                    totalMins = (midnight - start) / 60000;
                } else {
                    totalMins = getMinutesSinceMidnight(end.toISOString()) - getMinutesSinceMidnight(start.toISOString());
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
}

export default HourlyDashboard;
