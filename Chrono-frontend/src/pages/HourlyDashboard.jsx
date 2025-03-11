// src/pages/HourlyDashboard.jsx
import React, { useState, useEffect, useCallback } from 'react';
import { jsPDF } from 'jspdf';
import Navbar from '../components/Navbar';
import api from '../utils/api';
import { useNotification } from '../context/NotificationContext';
import { useTranslation } from '../context/LanguageContext';
import '../styles/UserDashboard.css';

/* =====================================
   HELPER FUNCTIONS
==================================== */

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

// Gibt die Minuten seit Mitternacht für einen Datums-/Zeit-String zurück.
const getMinutesSinceMidnight = (datetimeStr) => {
    if (!datetimeStr) return 0;
    const d = new Date(datetimeStr);
    return d.getHours() * 60 + d.getMinutes();
};

// Formatiert ein Datum als "YYYY-MM-DD".
const formatLocalDate = (date) => {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
};

// Formatiert einen Datums-String als "DD-MM-YYYY".
// Formatiert ein Datum als Uhrzeit "HH:MM" (2-stellig).
const formatTimeShort = (dateObj) => {
    return dateObj.toLocaleTimeString('de-DE', {
        hour: '2-digit',
        minute: '2-digit',
        timeZone: 'Europe/Berlin'
    });
};

/**
 * computeDailyDiffValue:
 * Berechnet die Differenz (in Minuten) eines Tages (gearbeitete Zeit minus Sollzeit).
 * Hier werden die Stempel für Work Start, Break Start, Break End und Work End berücksichtigt.
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

// Gibt die Tages-Differenz als formatierten String zurück.
const computeDailyDiff = (dayEntries, expectedWorkHours) => {
    const diff = computeDailyDiffValue(dayEntries, expectedWorkHours);
    const sign = diff >= 0 ? '+' : '';
    return `${sign}${Math.round(diff)} min`;
};

/**
 * getExpectedHoursForDay:
 * Bestimmt anhand des WeeklySchedule den erwarteten Sollwert für einen Tag.
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

// Gibt einen Text-Label für einen gegebenen Punch-Order zurück.
// Formatiert eine Differenz (in Minuten) als "+hh hrs mm min".
// Gibt alle Einträge des aktuellen Tages (im 'de-DE'-Format) zurück.
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
    // dailyData speichert pro Tag: totalMins, earliestStart, latestEnd
    const [dailyData, setDailyData] = useState({});
    const [printModalVisible, setPrintModalVisible] = useState(false);
    const [printStartDate, setPrintStartDate] = useState(formatLocalDate(new Date()));
    const [printEndDate, setPrintEndDate] = useState(formatLocalDate(new Date()));

    // defaultExpectedHours aus dem Profil, falls vorhanden
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
    }, [t]);

    /* ===== Einträge laden ===== */
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

    useEffect(() => {
        fetchEntries();
    }, [fetchEntries]);

    /* ===== NFC-Polling ===== */
    const doNfcCheck = useCallback(async () => {
        if (!userProfile?.isHourly) return;
        // Prüfe, ob bereits 2 Stempel für heute vorhanden sind.
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
                    // Nochmals prüfen, ob 2 Stempel schon vorhanden sind.
                    if (getTodayEntries(allEntries).length >= 2) {
                        notify(t("maxStampsReached"));
                        return;
                    }
                    try {
                        await api.post('/api/timetracking/punch', null, {
                            params: { username: cardUser, isHourly: true }
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

    /* ===== Manuelles Stempeln ===== */
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
                params: { username: userProfile.username, isHourly: true }
            });
            setPunchMessage(`${t("manualPunchMessage")} ${userProfile.username}`);
            setTimeout(() => setPunchMessage(''), 3000);
            fetchEntries();
        } catch (err) {
            console.error("Punch error:", err);
            notify(t("manualPunchError"));
        }
    };

    /* ===== Monatsübersicht berechnen ===== */
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

    // Gesamtarbeitszeit im aktuellen Monat
    let totalMins = 0;
    Object.values(dailyData).forEach(dayObj => {
        totalMins += dayObj.totalMins;
    });
    const totalHrs = Math.floor(totalMins / 60);
    const totalRemMins = totalMins % 60;

    // Monatsnavigation
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

    /* ===== Rendering ===== */

    if (!userProfile) {
        return (
            <div className="user-dashboard">
                <Navbar />
                <p>Loading...</p>
            </div>
        );
    }

    // Für die Anzeige in der Wochenansicht: Gruppiere Einträge nach Tag
    const weekDates = Array.from({ length: 7 }, (_, i) => addDays(getMondayOfWeek(new Date()), i));
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
        const dayEntriesToday = allEntries.filter(e => new Date(e.startTime).toLocaleDateString('de-DE') === todayStr);
        if (dayEntriesToday.length > 0) {
            const expectedForToday = getExpectedHoursForDay(new Date(), userProfile, defaultExpectedHours);
            dailyDiffDisplay = computeDailyDiff(dayEntriesToday, expectedForToday);
            dailyDiffDisplay = `${dailyDiffDisplay} (${t("expectedWorkHours")}: ${expectedForToday} ${t("hours")})`;
        }
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
        </div>
    );
}

export default HourlyDashboard;
