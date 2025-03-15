import React, { useState, useEffect, useCallback } from 'react';
import { jsPDF } from 'jspdf';
import autoTable from 'jspdf-autotable';
import Navbar from '../components/Navbar';
import api from '../utils/api';
import { useNotification } from '../context/NotificationContext';
import { useTranslation } from '../context/LanguageContext';
import '../styles/HourlyDashboard.css';
import VacationCalendar from '../components/VacationCalendar';


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
function getMinutesSinceMidnight(dateStr) {
    if (!dateStr) return 0;
    const d = new Date(dateStr);
    return d.getHours() * 60 + d.getMinutes();
}

function formatTimeShort(dateObj) {
    if (!dateObj) return '-';
    const d = new Date(dateObj);
    return d.toLocaleTimeString('de-DE', {
        hour: '2-digit',
        minute: '2-digit',
        timeZone: 'Europe/Berlin'
    });
}

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



function getTodayEntries(entries) {
    const todayISO = new Date().toISOString().slice(0, 10);
    return entries.filter(e => e.startTime.slice(0, 10) === todayISO);
}

function formatLocalDate(date) {
    return date.toISOString().slice(0, 10);
}

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

    const [dailyData, setDailyData] = useState({});
    const [printModalVisible, setPrintModalVisible] = useState(false);
    const [printStartDate, setPrintStartDate] = useState(formatLocalDate(new Date()));
    const [printEndDate, setPrintEndDate] = useState(formatLocalDate(new Date()));
    const [vacationRequests, setVacationRequests] = useState([]);
    const [vacationForm, setVacationForm] = useState({ startDate: '', endDate: '' });
    const [dailyNotes, setDailyNotes] = useState({});
    const defaultExpectedHours =
        userProfile && userProfile.dailyWorkHours !== undefined
            ? Number(userProfile.dailyWorkHours)
            : 8;

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
        }
    }, [userProfile]);

    const fetchEntries = useCallback(async () => {
        if (!userProfile) return;
        try {
            const res = await api.get(`/api/timetracking/history?username=${userProfile.username}`);
            const entries = res.data || [];
            const validEntries = entries.filter(e => [1, 2, 3, 4].includes(e.punchOrder));
            setAllEntries(validEntries);

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

    const handleSaveNote = async isoDate => {
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
            console.error("Error saving daily note:", err);
            notify("Fehler beim Speichern der Tagesnotiz.");
        }
    };

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

    const doNfcCheck = useCallback(async () => {
        if (!userProfile?.isHourly) return;
        const todayEntries = getTodayEntries(allEntries);
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
        const interval = setInterval(() => doNfcCheck(), 2000);
        return () => clearInterval(interval);
    }, [doNfcCheck]);

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


    let totalMinsCalc = 0;
    Object.values(dailyData).forEach(dayObj => {
        totalMinsCalc += dayObj.totalMins;
    });
    const totalHrs = Math.floor(totalMinsCalc / 60);
    const totalRemMins = totalMinsCalc % 60;

    function handlePrevMonth() {
        setSelectedMonth(new Date(selectedMonth.getFullYear(), selectedMonth.getMonth() - 1, 1));
    }
    function handleNextMonth() {
        setSelectedMonth(new Date(selectedMonth.getFullYear(), selectedMonth.getMonth() + 1, 1));
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
            fetchVacations();
        } catch (err) {
            console.error('Error submitting vacation request:', err);
            notify(t("vacationSubmitError"));
        }
    }

    function formatDate(dateStr) {
        if (!dateStr) return '-';
        const d = new Date(dateStr);
        const day = String(d.getDate()).padStart(2, '0');
        const month = String(d.getMonth() + 1).padStart(2, '0');
        const year = d.getFullYear();
        return `${day}-${month}-${year}`;
    }

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

    if (!userProfile) {
        return (
            <div className="user-dashboard">
                <Navbar />
                <p>Loading...</p>
            </div>
        );
    }
    function formatDisplayDate(dateStr) {
        const d = new Date(dateStr);
        const day = String(d.getDate()).padStart(2, '0');
        const month = String(d.getMonth() + 1).padStart(2, '0');
        const year = d.getFullYear();
        return `${day}-${month}-${year}`;
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
                                <th>Notiz</th>
                            </tr>
                            </thead>
                            <tbody>
                            {Object.entries(dailyData)
                                .sort(([a], [b]) => new Date(a) - new Date(b))
                                .map(([isoDate, info]) => {
                                    const displayDate = formatDisplayDate(isoDate);
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
                            onChange={(e) => setDailyNotes({ ...dailyNotes, [isoDate]: e.target.value })}
                            rows="2"
                        />
                                                <button onClick={() => handleSaveNote(isoDate)}>Speichern</button>
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
