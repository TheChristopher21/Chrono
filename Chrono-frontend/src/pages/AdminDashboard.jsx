import { useState, useEffect } from 'react';
import Navbar from '../components/Navbar';
import api from '../utils/api';
import VacationCalendarAdmin from '../components/VacationCalendarAdmin';
import { useAuth } from '../context/AuthContext';
import '../styles/AdminDashboard.css';
import { useNotification } from '../context/NotificationContext';
import { useTranslation } from '../context/LanguageContext';
import { jsPDF } from 'jspdf';
import autoTable from "jspdf-autotable";

/* =============================
   HELPER FUNCTIONS
============================= */

function getMondayOfWeek(date) {
    const copy = new Date(date);
    const day = copy.getDay();
    const diff = day === 0 ? 6 : day - 1;
    copy.setDate(copy.getDate() - diff);
    copy.setHours(0, 0, 0, 0);
    return copy;
}

function formatDate(dateStrOrObj) {
    const date = new Date(dateStrOrObj);
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();
    return `${day}-${month}-${year}`;
}

function addDays(date, days) {
    const d = new Date(date);
    d.setDate(d.getDate() + days);
    return d;
}

function formatTime(dateStr) {
    const d = new Date(dateStr);
    return isNaN(d.getTime())
        ? '-'
        : d.toLocaleTimeString('de-DE', {
            hour: '2-digit',
            minute: '2-digit',
            timeZone: 'Europe/Berlin'
        });
}

function formatLocalDateYMD(d) {
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

function getMinutesSinceMidnight(datetimeStr) {
    if (!datetimeStr) return 0;
    const d = new Date(datetimeStr);
    return d.getHours() * 60 + d.getMinutes();
}

function parseTimeToMinutes(timeStr) {
    if (!timeStr) return 0;
    const parts = timeStr.split(':');
    const hours = parseInt(parts[0], 10);
    const minutes = parseInt(parts[1], 10);
    return hours * 60 + minutes;
}

function computeDailyDiffValue(dayEntries, expectedWorkHours, isHourly) {
    if (isHourly) {
        const entryStart = dayEntries.find(e => e.punchOrder === 1);
        let entryEnd = dayEntries.find(e => e.punchOrder === 4);
        if (!entryEnd) {
            entryEnd = dayEntries.find(e => e.punchOrder === 2);
        }
        if (entryStart && entryEnd) {
            const startTime = new Date(entryStart.startTime);
            const endTime = new Date(entryEnd.endTime ? entryEnd.endTime : entryEnd.startTime);
            if (endTime.toDateString() !== startTime.toDateString()) {
                const midnight = new Date(startTime);
                midnight.setHours(24, 0, 0, 0);
                const minutesWorked = (midnight - startTime) / 60000;
                const expectedMinutes = expectedWorkHours * 60;
                return minutesWorked - expectedMinutes;
            } else {
                const workStartMins = getMinutesSinceMidnight(entryStart.startTime);
                const workEndMins = getMinutesSinceMidnight(entryEnd.endTime ? entryEnd.endTime : entryEnd.startTime);
                const minutesWorked = workEndMins - workStartMins;
                const expectedMinutes = expectedWorkHours * 60;
                return minutesWorked - expectedMinutes;
            }
        }
        return 0;
    }

    const entryStart = dayEntries.find(e => e.punchOrder === 1);
    const entryBreakStart = dayEntries.find(e => e.punchOrder === 2);
    const entryBreakEnd = dayEntries.find(e => e.punchOrder === 3);
    const entryEnd = dayEntries.find(e => e.punchOrder === 4);
    if (entryStart && entryBreakStart && entryBreakEnd && entryEnd) {
        const workStartMins = getMinutesSinceMidnight(entryStart.startTime);
        let workEndMins = getMinutesSinceMidnight(entryEnd.endTime);
        if (workEndMins < workStartMins) {
            workEndMins += 24 * 60;
        }
        const workDuration = workEndMins - workStartMins;
        let breakStartMins = entryBreakStart.breakStart
            ? parseTimeToMinutes(entryBreakStart.breakStart)
            : getMinutesSinceMidnight(entryBreakStart.startTime);
        let breakEndMins = entryBreakEnd.breakEnd
            ? parseTimeToMinutes(entryBreakEnd.breakEnd)
            : getMinutesSinceMidnight(entryBreakEnd.startTime);
        if (breakEndMins < breakStartMins) {
            breakEndMins += 24 * 60;
        }
        const breakDuration = breakEndMins - breakStartMins;
        const actualWorked = workDuration - breakDuration;
        const expectedMinutes = expectedWorkHours * 60;
        return actualWorked - expectedMinutes;
    }
    return 0;
}

function computeDailyDiff(dayEntries, expectedWorkHours, isHourly) {
    const diff = computeDailyDiffValue(dayEntries, expectedWorkHours, isHourly);
    const sign = diff >= 0 ? '+' : '';
    return `${sign}${Math.round(diff)} min`;
}

function getExpectedHoursForDay(dayObj, userConfig, defaultExpectedHours) {
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
}

function getStatusLabel(punchOrder) {
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
}




const AdminDashboard = () => {
    const { currentUser } = useAuth();
    const { notify } = useNotification();
    const { t } = useTranslation();

    const [adminPassword, setAdminPassword] = useState('');
    const [allTracks, setAllTracks] = useState([]);
    const [allVacations, setAllVacations] = useState([]);
    const [allCorrections, setAllCorrections] = useState([]);
    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));
    const [expandedUsers, setExpandedUsers] = useState({});
    const [users, setUsers] = useState([]);

    const [editModalVisible, setEditModalVisible] = useState(false);
    const [editDate, setEditDate] = useState(null);
    const [editTargetUsername, setEditTargetUsername] = useState('');
    const [editData, setEditData] = useState({
        workStart: '',
        breakStart: '',
        breakEnd: '',
        workEnd: '',
        adminPassword: '',
        userPassword: ''
    });

    const [printUserModalVisible, setPrintUserModalVisible] = useState(false);
    const [printUser, setPrintUser] = useState('');
    const [printUserStartDate, setPrintUserStartDate] = useState(formatLocalDateYMD(new Date()));
    const [printUserEndDate, setPrintUserEndDate] = useState(formatLocalDateYMD(new Date()));

    const defaultExpectedHours = 8;

    useEffect(() => {
        fetchUsers();
        fetchAllTracks();
        fetchAllVacations();
        fetchAllCorrections();
    }, []);

    async function fetchUsers() {
        try {
            const res = await api.get('/api/admin/users');
            setUsers(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error('Error loading users', err);
        }
    }

    async function fetchAllTracks() {
        try {
            const res = await api.get('/api/admin/timetracking/all');
            const validEntries = (res.data || []).filter(e => [1, 2, 3, 4].includes(e.punchOrder));
            setAllTracks(validEntries);
        } catch (err) {
            console.error('Error loading time entries', err);
        }
    }

    async function fetchAllVacations() {
        try {
            const res = await api.get('/api/vacation/all');
            setAllVacations(res.data || []);
        } catch (err) {
            console.error('Error loading vacation requests', err);
        }
    }

    async function fetchAllCorrections() {
        try {
            const res = await api.get('/api/correction/all');
            setAllCorrections(res.data || []);
        } catch (err) {
            console.error('Error loading correction requests', err);
        }
    }

    function handlePrevWeek() {
        setSelectedMonday(prev => addDays(prev, -7));
    }
    function handleNextWeek() {
        setSelectedMonday(prev => addDays(prev, 7));
    }
    function handleWeekJump(e) {
        const picked = new Date(e.target.value);
        if (!isNaN(picked.getTime())) {
            setSelectedMonday(getMondayOfWeek(picked));
        }
    }

    const weekDates = Array.from({ length: 7 }, (_, i) => addDays(selectedMonday, i));
    const weekStrs = weekDates.map(d => formatLocalDateYMD(d));

    const filteredTracks = allTracks.filter(track => {
        const localDate = formatLocalDateYMD(new Date(track.startTime));
        return weekStrs.includes(localDate);
    });

    const userGroups = filteredTracks.reduce((acc, track) => {
        const uname = track.username;
        if (!acc[uname]) acc[uname] = [];
        acc[uname].push(track);
        return acc;
    }, {});

    function toggleUserExpand(username) {
        setExpandedUsers(prev => ({ ...prev, [username]: !prev[username] }));
    }

    async function handleApproveVacation(id) {
        if (!adminPassword) {
            notify(t('adminDashboard.adminPassword') + ' ' + t('adminDashboard.pleaseEnter'));
            return;
        }
        try {
            await api.post(`/api/correction/approve/${id}`, null, { params: { adminPassword } });
            fetchAllVacations();
        } catch (err) {
            console.error('Error approving vacation', err);
        }
    }

    async function handleDenyVacation(id) {
        try {
            await api.post(`/api/vacation/deny/${id}`);
            fetchAllVacations();
        } catch (err) {
            console.error('Error denying vacation', err);
        }
    }

    async function handleApproveCorrection(id) {
        if (!adminPassword) {
            notify(t('adminDashboard.adminPassword') + ' ' + t('adminDashboard.pleaseEnter'));
            return;
        }
        try {
            await api.post(`/api/correction/approve/${id}`, null, { params: { adminPassword } });
            fetchAllCorrections();
        } catch (err) {
            console.error('Error approving correction', err);
            if (err.response && err.response.status === 403) {
                // Zeige deine gewünschte Fehlermeldung
                notify("Falsches Admin Passwort");
            } else {
                // Falls ein anderer Fehler auftritt, kannst du den Standardtext ausgeben
                notify('Error approving correction: ' + err.message);
            }
        }
    }


    async function handleDenyCorrection(id) {
        try {
            await api.post(`/api/correction/deny/${id}`);
            fetchAllCorrections();
        } catch (err) {
            console.error('Error denying correction', err);
            notify('Error denying correction: ' + err.message);
        }
    }

    function openEditModal(targetUsername, dateObj, entries) {
        const defaultTime = '00:00';
        const workStartEntry = entries.find(e => e.punchOrder === 1);
        let workEndEntry = entries.find(e => e.punchOrder === 4);
        if (!workEndEntry) {
            workEndEntry = entries.find(e => e.punchOrder === 2);
        }
        const workStartVal = workStartEntry ? formatTime(workStartEntry.startTime) : defaultTime;
        const workEndVal = workEndEntry ? formatTime(workEndEntry.endTime || workEndEntry.startTime) : defaultTime;

        setEditTargetUsername(targetUsername);
        setEditDate(dateObj);
        setEditData({
            workStart: workStartVal,
            breakStart: defaultTime,
            breakEnd: defaultTime,
            workEnd: workEndVal,
            adminPassword: '',
            userPassword: ''
        });
        setEditModalVisible(true);
    }

    async function handleEditSubmit(e) {
        e.preventDefault();
        if (!editDate) {
            notify(t('adminDashboard.noValidDate'));
            return;
        }
        const formattedDate = formatLocalDateYMD(editDate);
        const params = {
            targetUsername: editTargetUsername,
            date: formattedDate,
            workStart: editData.workStart,
            breakStart: editData.breakStart,
            breakEnd: editData.breakEnd,
            workEnd: editData.workEnd,
            adminUsername: currentUser.username,
            userPassword: editData.userPassword
        };
        if (editTargetUsername !== currentUser.username) {
            params.adminPassword = editData.adminPassword;
        } else {
            params.adminPassword = editData.userPassword;
        }
        try {
            await api.put('/api/timetracking/editDay', null, { params });
            setEditModalVisible(false);
            fetchAllTracks();
        } catch (err) {
            console.error('Edit failed', err);
            notify(t('adminDashboard.editFailed') + ': ' + err.message);
        }
    }

    function handleEditInputChange(e) {
        const { name, value } = e.target;
        setEditData(prev => ({ ...prev, [name]: value }));
    }

    function openPrintUserModal(username) {
        setPrintUser(username);
        setPrintUserStartDate(formatLocalDateYMD(new Date()));
        setPrintUserEndDate(formatLocalDateYMD(new Date()));
        setPrintUserModalVisible(true);
    }

    async function handlePrintUserTimesPeriodSubmit() {
        const userEntries = allTracks.filter(e => {
            const entryDate = new Date(e.startTime);
            return (
                e.username === printUser &&
                entryDate >= new Date(printUserStartDate) &&
                entryDate <= new Date(printUserEndDate)
            );
        });

        const grouped = {};
        userEntries.forEach(entry => {
            const entryDate = new Date(entry.startTime);
            const ds = entryDate.toLocaleDateString("de-DE");
            if (!grouped[ds]) grouped[ds] = [];
            grouped[ds].push(entry);
        });

        const sortedDates = Object.keys(grouped).sort((a, b) => {
            return new Date(a) - new Date(b);
        });
        const tableBody = sortedDates.map(dateStr => {
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

            const userConfig = users.find(u => u.username === printUser) || {};
            const expected = getExpectedHoursForDay(new Date(dayEntries[0].startTime), userConfig, defaultExpectedHours);
            const diffValue = computeDailyDiffValue(dayEntries, expected, userConfig.isHourly);
            const diffText = `${diffValue >= 0 ? '+' : '-'}${Math.abs(diffValue)} min`;

            return [dateStr, workStart, breakStart, breakEnd, workEnd, diffText];
        });

        const doc = new jsPDF("p", "mm", "a4");
        doc.setFontSize(12);
        doc.text(`Zeiten für ${printUser}`, 14, 15);
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
            console.error("Fehler beim Drucken der Zeiten für", printUser, ":", err);
            notify("Fehler beim Drucken der Zeiten für " + printUser);
        }
        setPrintUserModalVisible(false);
    }

    return (
        <div className="admin-dashboard">
            <Navbar />
            <header className="dashboard-header">
                <h2>{t('adminDashboard.titleWeekly')}</h2>
                <p>
                    {t('adminDashboard.loggedInAs')}: {currentUser?.username}
                </p>
                <div className="admin-password">
                    <label>{t('adminDashboard.adminPassword')}: </label>
                    <input
                        type="password"
                        value={adminPassword}
                        onChange={e => setAdminPassword(e.target.value)}
                    />
                </div>
            </header>
            <div className="dashboard-content">
                {/* Linke Spalte: Time Tracking & Vacation */}
                <div className="left-column">
                    <section className="week-section">
                        <h3>{t('adminDashboard.timeTrackingCurrentWeek')}</h3>
                        <div className="week-navigation">
                            <button onClick={handlePrevWeek}>← {t('adminDashboard.prevWeek')}</button>
                            <input
                                type="date"
                                onChange={handleWeekJump}
                                value={formatLocalDateYMD(selectedMonday)}
                            />
                            <button onClick={handleNextWeek}>{t('adminDashboard.nextWeek')} →</button>
                        </div>
                        {Object.keys(userGroups).length === 0 ? (
                            <p>{t('adminDashboard.noEntriesThisWeek')}</p>
                        ) : (
                            <div className="admin-user-groups">
                                {Object.keys(userGroups).map(username => {
                                    const userConfig = users.find(u => u.username === username) || {};
                                    const dayMap = {};
                                    userGroups[username].forEach(entry => {
                                        const ds = formatLocalDateYMD(new Date(entry.startTime));
                                        if (!dayMap[ds]) dayMap[ds] = [];
                                        dayMap[ds].push(entry);
                                    });
                                    let userTotalDiff = 0;
                                    weekDates.forEach(wd => {
                                        const isoDay = formatLocalDateYMD(wd);
                                        const dayEntries = dayMap[isoDay] || [];
                                        const expectedForDay = getExpectedHoursForDay(wd, userConfig, defaultExpectedHours);
                                        if (dayEntries.length > 0) {
                                            userTotalDiff += computeDailyDiffValue(dayEntries, expectedForDay, userConfig.isHourly);
                                        }
                                    });
                                    const absTotal = Math.abs(userTotalDiff);
                                    const totalHours = Math.floor(absTotal / 60);
                                    const totalMinutes = absTotal % 60;
                                    const totalSign = userTotalDiff >= 0 ? '+' : '-';
                                    const isExpanded = !!expandedUsers[username];
                                    let userColor = '#007BFF';
                                    if (userGroups[username].length > 0 && userGroups[username][0].color) {
                                        const c = userGroups[username][0].color;
                                        if (/^#[0-9A-Fa-f]{6}$/.test(c)) {
                                            userColor = c;
                                        }
                                    }
                                    return (
                                        <div key={username} className="admin-user-block">
                                            <div
                                                className="admin-user-header"
                                                onClick={() => toggleUserExpand(username)}
                                                style={{ backgroundColor: userColor }}
                                            >
                                                <h4 style={{ color: '#fff' }}>{username}</h4>
                                                <button className="edit-button">{isExpanded ? '–' : '+'}</button>
                                            </div>
                                            {isExpanded && (
                                                <div className="admin-week-display">
                                                    <div className="user-total-diff">
                                                        {t('adminDashboard.total')}: {totalSign}
                                                        {totalHours} {t('adminDashboard.hours')} {totalMinutes} {t('adminDashboard.minutes')}
                                                    </div>
                                                    {weekDates.map((wd, i) => {
                                                        const isoDay = formatLocalDateYMD(wd);
                                                        const dayEntries = dayMap[isoDay] || [];
                                                        const expectedForDay = getExpectedHoursForDay(wd, userConfig, defaultExpectedHours);
                                                        if (userConfig.isHourly) {
                                                            const workStartEntry = dayEntries.find(e => e.punchOrder === 1);
                                                            let workEndEntry = dayEntries.find(e => e.punchOrder === 4);
                                                            if (!workEndEntry) {
                                                                workEndEntry = dayEntries.find(e => e.punchOrder === 2);
                                                            }
                                                            if (dayEntries.length === 0) {
                                                                return (
                                                                    <div key={i} className="admin-day-card">
                                                                        <div className="admin-day-card-header">
                                                                            <strong>
                                                                                {wd.toLocaleDateString('de-DE', { weekday: 'long' })},{' '}
                                                                                {formatDate(wd)}
                                                                            </strong>
                                                                        </div>
                                                                        <div className="admin-day-content">
                                                                            <p className="no-entries">Keine Einträge</p>
                                                                        </div>
                                                                    </div>
                                                                );
                                                            } else {
                                                                const startTime = workStartEntry ? formatTime(workStartEntry.startTime) : '-';
                                                                const endTime = workEndEntry ? formatTime(workEndEntry.endTime || workEndEntry.startTime) : '-';
                                                                let totalH = 0;
                                                                let totalM = 0;
                                                                if (workStartEntry && workEndEntry) {
                                                                    const start = new Date(workStartEntry.startTime);
                                                                    const end = new Date(workEndEntry.endTime || workEndEntry.startTime);
                                                                    let minutesWorked;
                                                                    if (end.toDateString() !== start.toDateString()) {
                                                                        const midnight = new Date(start);
                                                                        midnight.setHours(24, 0, 0, 0);
                                                                        minutesWorked = (midnight - start) / 60000;
                                                                    } else {
                                                                        minutesWorked =
                                                                            getMinutesSinceMidnight(workEndEntry.endTime || workEndEntry.startTime) -
                                                                            getMinutesSinceMidnight(workStartEntry.startTime);
                                                                    }
                                                                    totalH = Math.floor(minutesWorked / 60);
                                                                    totalM = minutesWorked % 60;
                                                                }
                                                                return (
                                                                    <div key={i} className="admin-day-card">
                                                                        <div className="admin-day-card-header">
                                                                            <strong>
                                                                                {wd.toLocaleDateString('de-DE', { weekday: 'long' })},{' '}
                                                                                {formatDate(wd)}
                                                                            </strong>
                                                                        </div>
                                                                        <div className="admin-day-content">
                                                                            <ul className="time-entry-list">
                                                                                <li>
                                                                                    <span className="entry-label">Work Start:</span> {startTime}
                                                                                </li>
                                                                                <li>
                                                                                    <span className="entry-label">Work End:</span> {endTime}
                                                                                </li>
                                                                                <li>
                                                                                    <span className="entry-label">Total:</span> {totalH} Std {totalM} min
                                                                                </li>
                                                                            </ul>
                                                                        </div>
                                                                    </div>
                                                                );
                                                            }
                                                        } else {
                                                            const dailyDiff =
                                                                dayEntries.length >= 4
                                                                    ? computeDailyDiff(dayEntries, expectedForDay, false)
                                                                    : '';
                                                            return (
                                                                <div key={i} className="admin-day-card">
                                                                    <div className="admin-day-card-header">
                                                                        <strong>
                                                                            {wd.toLocaleDateString('de-DE', { weekday: 'long' })},{' '}
                                                                            {formatDate(wd)}
                                                                            <span className="expected-hours">
                                                                                ({t('adminDashboard.expected')}: {expectedForDay}{' '}
                                                                                {t('adminDashboard.hours')})
                                                                            </span>
                                                                        </strong>
                                                                        {dailyDiff && (
                                                                            <span className="daily-diff">({dailyDiff})</span>
                                                                        )}
                                                                        {dayEntries.length > 0 && (
                                                                            <button
                                                                                onClick={() => openEditModal(username, wd, dayEntries)}
                                                                                className="edit-day-button"
                                                                            >
                                                                                {t('adminDashboard.editButton')}
                                                                            </button>
                                                                        )}
                                                                    </div>
                                                                    <div className="admin-day-content">
                                                                        {dayEntries.length === 0 ? (
                                                                            <p className="no-entries">
                                                                                {t('adminDashboard.noEntriesThisWeek')}
                                                                            </p>
                                                                        ) : (
                                                                            <ul className="time-entry-list">
                                                                                {dayEntries
                                                                                    .sort((a, b) => a.punchOrder - b.punchOrder)
                                                                                    .map(e => {
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
                                                                                                </span>{' '}
                                                                                                {displayTime}
                                                                                            </li>
                                                                                        );
                                                                                    })}
                                                                            </ul>
                                                                        )}
                                                                    </div>
                                                                </div>
                                                            );
                                                        }
                                                    })}
                                                    <button
                                                        className="print-times-button"
                                                        onClick={() => openPrintUserModal(username)}
                                                    >
                                                        {t('Zeiten Drucken')}
                                                    </button>
                                                </div>
                                            )}
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </section>

                    <section className="vacation-section">
                        <h3>{t('adminDashboard.vacationRequestsTitle')}</h3>
                        {allVacations.length === 0 ? (
                            <p>{t('adminDashboard.noVacations')}</p>
                        ) : (
                            <ul className="vacation-list">
                                {allVacations.map(v => (
                                    <li key={v.id} className="vacation-item">
                                        <span className="vacation-text">
                                            <strong>{v.username}</strong>: {formatDate(v.startDate)} - {formatDate(v.endDate)}{' '}
                                            {v.approved ? (
                                                <span className="approved">{t('adminDashboard.approved')}</span>
                                            ) : v.denied ? (
                                                <span className="denied">{t('adminDashboard.denied')}</span>
                                            ) : (
                                                <span className="pending">{t('adminDashboard.pending')}</span>
                                            )}
                                        </span>
                                        {!v.approved && !v.denied && (
                                            <span className="vacation-buttons">
                                                <button
                                                    className="approve-btn"
                                                    onClick={() => handleApproveVacation(v.id)}
                                                >
                                                    {t('adminDashboard.acceptButton')}
                                                </button>
                                                <button
                                                    className="reject-btn"
                                                    onClick={() => handleDenyVacation(v.id)}
                                                >
                                                    {t('adminDashboard.rejectButton')}
                                                </button>
                                            </span>
                                        )}
                                    </li>
                                ))}
                            </ul>
                        )}
                    </section>
                </div>

                {/* Rechte Spalte: Correction Requests */}
                <div className="right-column">
                    <section className="correction-section">
                        <h3>
                            {t('adminDashboard.correctionRequestsTitle')} {t('')}{' '}
                        </h3>
                        {allCorrections.length === 0 ? (
                            <p>
                                {t('adminDashboard.noEntriesThisWeek')}{' '}
                            </p>
                        ) : (
                            <ul className="correction-list">
                                {allCorrections.map(corr => {
                                    const dateStr = new Date(corr.desiredStartTime).toLocaleDateString();
                                    return (
                                        <li key={corr.id} className="correction-item">
                                            <h4>Korrektur vom {dateStr}</h4>
                                            <div className="correction-info">
                                                <p><strong>User:</strong> {corr.username}</p>
                                                <p><strong>Work Start:</strong> {corr.workStart || "-"}</p>
                                                <p><strong>Break Start:</strong> {corr.breakStart || "-"}</p>
                                                <p><strong>Break End:</strong> {corr.breakEnd || "-"}</p>
                                                <p><strong>Work End:</strong> {corr.workEnd || "-"}</p>
                                                <p><strong>Reason:</strong> {corr.reason}</p>
                                                <p>
                                                    <strong>Status:</strong>{" "}
                                                    {corr.approved
                                                        ? t("adminDashboard.approved")
                                                        : corr.denied
                                                            ? t("adminDashboard.denied")
                                                            : t("adminDashboard.pending")}
                                                </p>
                                            </div>
                                            <div className="correction-buttons">
                                                {!corr.approved && !corr.denied && (
                                                    <>
                                                        <button onClick={() => handleApproveCorrection(corr.id)}>
                                                            {t('adminDashboard.acceptButton')}
                                                        </button>
                                                        <button onClick={() => handleDenyCorrection(corr.id)}>
                                                            {t('adminDashboard.rejectButton')}
                                                        </button>
                                                    </>
                                                )}
                                            </div>
                                        </li>
                                    );
                                })}
                            </ul>

                        )}
                    </section>
                </div>
            </div>

            <div className="full-width-calendar">
                <h4>{t('adminDashboard.vacationCalendarTitle')}</h4>
                <VacationCalendarAdmin vacationRequests={allVacations.filter(v => v.approved)} />
            </div>

            {/* Edit Modal */}
            {editModalVisible && (
                <div className="modal-overlay">
                    <div className="modal-content">
                        <h3>{t('adminDashboard.editTrackingTitle')} {editDate?.toLocaleDateString('de-DE')}</h3>
                        <form onSubmit={handleEditSubmit}>
                            <div className="form-group">
                                <label>Work Start:</label>
                                <input type="time" name="workStart" value={editData.workStart} onChange={handleEditInputChange} required />
                            </div>
                            <div className="form-group">
                                <label>Break Start:</label>
                                <input type="time" name="breakStart" value={editData.breakStart} onChange={handleEditInputChange} />
                            </div>
                            <div className="form-group">
                                <label>Break End:</label>
                                <input type="time" name="breakEnd" value={editData.breakEnd} onChange={handleEditInputChange} />
                            </div>
                            <div className="form-group">
                                <label>Work End:</label>
                                <input type="time" name="workEnd" value={editData.workEnd} onChange={handleEditInputChange} required />
                            </div>
                            <div className="form-group">
                                <label>{t('adminPassword')}:</label>
                                <input
                                    type="password"
                                    name="adminPassword"
                                    value={editData.adminPassword}
                                    onChange={handleEditInputChange}
                                    required
                                />
                            </div>
                            <div className="form-group">
                                <label>{t('userPassword')}:</label>
                                <input
                                    type="password"
                                    name="userPassword"
                                    value={editData.userPassword}
                                    onChange={handleEditInputChange}
                                    required
                                />
                            </div>
                            <button type="submit">{t('save')}</button>
                            <button type="button" onClick={() => setEditModalVisible(false)}>
                                {t('cancel')}
                            </button>
                        </form>
                    </div>
                </div>
            )}

            {printUserModalVisible && (
                <div className="modal-overlay">
                    <div className="modal-content">
                        <h3>Zeiten für {printUser} drucken</h3>
                        <div className="form-group">
                            <label>Startdatum:</label>
                            <input
                                type="date"
                                value={printUserStartDate}
                                onChange={(e) => setPrintUserStartDate(e.target.value)}
                            />
                        </div>
                        <div className="form-group">
                            <label>Enddatum:</label>
                            <input
                                type="date"
                                value={printUserEndDate}
                                onChange={(e) => setPrintUserEndDate(e.target.value)}
                            />
                        </div>
                        <div className="modal-buttons">
                            <button onClick={handlePrintUserTimesPeriodSubmit}>Drucken</button>
                            <button onClick={() => setPrintUserModalVisible(false)}>Abbrechen</button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default AdminDashboard;
