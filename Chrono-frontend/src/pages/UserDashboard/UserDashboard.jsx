// src/pages/UserDashboard/UserDashboard.jsx
import  { useState, useEffect, useRef, useCallback } from 'react';
import Navbar from '../../components/Navbar';
import VacationCalendar from '../../components/VacationCalendar';
import { useAuth } from '../../context/AuthContext';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import api from '../../utils/api';
import { parseISO } from 'date-fns';

import '../../styles/global.css';
// Beide Scoped-Styles werden referenziert, um sicherzustellen, dass alle Designs verfügbar sind.
// Das UserDashboard übernimmt nun primär die Ästhetik des HourlyDashboards.
import '../../styles/UserDashboardScoped.css';

import jsPDF from "jspdf";
import autoTable from "jspdf-autotable";

import { useCustomers } from '../../context/CustomerContext';

import {
    getMondayOfWeek,
    addDays,
    formatLocalDate,
    formatDate,
    formatTime,
    minutesToHHMM,
    getExpectedHoursForDay,
    isLateTime,
    formatPunchedTimeFromEntry,
    parseHex16,
    sortEntries,
} from './userDashUtils';

import CorrectionModal from '../../components/CorrectionModal';
import UserCorrectionsPanel from './UserCorrectionsPanel';
import PrintReportModal from "../../components/PrintReportModal.jsx";
import CustomerTimeAssignModal from '../../components/CustomerTimeAssignModal';

// HINWEIS: HourlyDashboard und PercentageDashboard Imports bleiben für die Routing-Logik bestehen.
import HourlyDashboard from '../../pages/HourlyDashboard/HourlyDashboard.jsx';
import PercentageDashboard from '../../pages/PercentageDashboard/PercentageDashboard.jsx';

function UserDashboard() {
    const { currentUser, fetchCurrentUser } = useAuth();
    const { notify } = useNotification();
    const { t } = useTranslation();

    const [userProfile, setUserProfile] = useState(null);
    const [dailySummaries, setDailySummaries] = useState([]);
    const [vacationRequests, setVacationRequests] = useState([]);
    const [correctionRequests, setCorrectionRequests] = useState([]);
    const [sickLeaves, setSickLeaves] = useState([]);
    const [holidaysForUserCanton, setHolidaysForUserCanton] = useState({ data: {}, year: null, canton: null });

    const [punchMessage, setPunchMessage] = useState('');
    const lastPunchTimeRef = useRef(0);

    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));

    const [showCorrectionsPanel, setShowCorrectionsPanel] = useState(false);
    const [showAllCorrections, setShowAllCorrections] = useState(false);
    const [selectedCorrectionMonday, setSelectedCorrectionMonday] = useState(getMondayOfWeek(new Date()));

    const { customers, fetchCustomers } = useCustomers();
    const [recentCustomers, setRecentCustomers] = useState([]);
    const [projects, setProjects] = useState([]);
    const [selectedCustomerId, setSelectedCustomerId] = useState('');
    const [selectedProjectId, setSelectedProjectId] = useState('');
    const [tasks, setTasks] = useState([]);
    const [selectedTaskId, setSelectedTaskId] = useState('');

    const [printModalVisible, setPrintModalVisible] = useState(false);
    const [printStartDate, setPrintStartDate] = useState(formatLocalDate(new Date(new Date().getFullYear(), new Date().getMonth(), 1)));
    const [printEndDate, setPrintEndDate] = useState(formatLocalDate(new Date(new Date().getFullYear(), new Date().getMonth() + 1, 0)));

    const [showCorrectionModal, setShowCorrectionModal] = useState(false);
    const [correctionDate, setCorrectionDate] = useState("");
    const [dailySummaryForCorrection, setDailySummaryForCorrection] = useState(null);

    // State für Notizbearbeitung (übernommen von HourlyWeekOverview)
    const [editingNote, setEditingNote] = useState(null);
    const [noteContent, setNoteContent] = useState('');
    const [modalInfo, setModalInfo] = useState({ isVisible: false, day: null, summary: null });
    const isCustomerTrackingEnabled = userProfile?.customerTrackingEnabled || currentUser?.customerTrackingEnabled;


    const defaultExpectedHours = userProfile?.dailyWorkHours ?? 8.5;

    const loadProfileAndInitialData = useCallback(async () => {
        try {
            const profile = await fetchCurrentUser();
            if (profile && Object.keys(profile).length > 0) {
                if (profile.weeklySchedule && !Array.isArray(profile.weeklySchedule)) {
                    profile.weeklySchedule = [profile.weeklySchedule];
                }
                if (!profile.weeklySchedule || profile.weeklySchedule.length === 0) {
                    profile.weeklySchedule = [{ monday: 8.5, tuesday: 8.5, wednesday: 8.5, thursday: 8.5, friday: 8.5, saturday: 0, sunday: 0 }];
                    profile.scheduleCycle = 1;
                }
                setUserProfile(profile);
                if (profile.lastCustomerId && !selectedCustomerId) {
                    setSelectedCustomerId(String(profile.lastCustomerId));
                }
            } else {
                throw new Error("User profile could not be loaded.");
            }
        } catch (err) {
            console.error(t('personalData.errorLoading'), err);
            notify(t('errors.fetchProfileError', 'Fehler beim Laden des Profils.'), 'error');
        }
    }, [fetchCurrentUser, t, notify, selectedCustomerId]);

    const fetchDataForUser = useCallback(async () => {
        if (!userProfile?.username) return;
        try {
            const [resSummaries, resVacation, resCorr, resSick] = await Promise.all([
                api.get(`/api/timetracking/history?username=${userProfile.username}`),
                api.get('/api/vacation/my'),
                api.get(`/api/correction/my?username=${userProfile.username}`),
                api.get('/api/sick-leave/my')
            ]);
            setDailySummaries(Array.isArray(resSummaries.data) ? resSummaries.data : []);
            setVacationRequests(Array.isArray(resVacation.data) ? resVacation.data : []);
            setCorrectionRequests(Array.isArray(resCorr.data) ? resCorr.data : []);
            setSickLeaves(Array.isArray(resSick.data) ? resSick.data : []);
        } catch (err) {
            console.error("Fehler beim Laden der Benutzerdaten (UserDashboard):", err);
            notify(t('errors.fetchUserDataError', 'Fehler beim Laden der Benutzerdaten.'), 'error');
        }
    }, [userProfile, notify, t]);


    useEffect(() => {
        loadProfileAndInitialData();
    }, [loadProfileAndInitialData]);

    useEffect(() => {
        if (isCustomerTrackingEnabled) {
            fetchCustomers();
            api.get('/api/customers/recent')
                .then(res => setRecentCustomers(Array.isArray(res.data) ? res.data : []))
                .catch(err => console.error('Error loading customers', err));
            api.get('/api/projects')
                .then(res => setProjects(Array.isArray(res.data) ? res.data : []))
                .catch(err => console.error('Error loading projects', err));
        } else {
            setRecentCustomers([]);
            setProjects([]);
        }
    }, [userProfile, currentUser, fetchCustomers, isCustomerTrackingEnabled]);

    useEffect(() => {
        if (selectedProjectId) {
            api.get('/api/tasks', { params: { projectId: selectedProjectId } })
                .then(res => setTasks(Array.isArray(res.data) ? res.data : []))
                .catch(err => { console.error('Error loading tasks', err); setTasks([]); });
        } else {
            setTasks([]);
            setSelectedTaskId('');
        }
    }, [selectedProjectId]);

    const fetchHolidaysForUser = useCallback(async (year, cantonAbbreviation) => {
        const cantonKey = cantonAbbreviation || 'GENERAL';
        if (holidaysForUserCanton.year === year && holidaysForUserCanton.canton === cantonKey) {
            return;
        }
        try {
            const params = { year, cantonAbbreviation: cantonAbbreviation || '', startDate: `${year}-01-01`, endDate: `${year}-12-31` };
            const response = await api.get('/api/holidays/details', { params });
            setHolidaysForUserCanton({ data: response.data || {}, year, canton: cantonKey });
        } catch (error) {
            console.error(t('errors.fetchHolidaysError', 'Fehler beim Laden der Feiertage:'), error);
            setHolidaysForUserCanton({ data: {}, year, canton: cantonKey });
        }
    }, [t, holidaysForUserCanton]);


    useEffect(() => {
        if (userProfile) {
            fetchDataForUser();
            const cantonAbbr = userProfile.company?.cantonAbbreviation || userProfile.companyCantonAbbreviation;
            if (cantonAbbr) {
                fetchHolidaysForUser(selectedMonday.getFullYear(), cantonAbbr);
            } else {
                fetchHolidaysForUser(selectedMonday.getFullYear(), '');
            }
        }
    }, [userProfile, fetchDataForUser, fetchHolidaysForUser, selectedMonday]);


    // Logik für NFC-Check und manuelles Stempeln
    useEffect(() => {
        const interval = setInterval(doNfcCheck, 2000);
        return () => clearInterval(interval);
    }, []); // eslint-disable-line react-hooks/exhaustive-deps

    async function doNfcCheck() {
        try {
            const response = await fetch(`${import.meta.env.VITE_API_BASE_URL}/api/nfc/read/1`);
            if (!response.ok) return;
            const json = await response.json();
            if (json.status !== 'success' || !json.data) return;
            const cardUser = parseHex16(json.data);
            if (!cardUser) return;
            if (Date.now() - lastPunchTimeRef.current < 5000) return;
            lastPunchTimeRef.current = Date.now();
            showPunchMessage(`${t('login.stamped', 'Eingestempelt')}: ${cardUser}`);
            await api.post('/api/timetracking/punch', null, { params: { username: cardUser, source: 'NFC_SCAN' } });
            if (currentUser && cardUser === currentUser.username) {
                fetchDataForUser();
                loadProfileAndInitialData();
            }
        } catch (err) {
            console.error('NFC error', err);
        }
    }

    function showPunchMessage(msg) {
        setPunchMessage(msg);
        setTimeout(() => setPunchMessage(''), 3000);
    }

    async function handleManualPunch() {
        if (!userProfile) return;
        try {
            const params = { username: userProfile.username, source: 'MANUAL_PUNCH' };
            if (selectedCustomerId) params.customerId = selectedCustomerId;
            if (selectedProjectId) params.projectId = selectedProjectId;
            if (selectedTaskId) params.taskId = selectedTaskId;
            const response = await api.post('/api/timetracking/punch', null, { params });
            const newEntry = response.data;
            showPunchMessage(`${t("manualPunchMessage")} ${userProfile.username} (${t('punchTypes.' + newEntry.punchType, newEntry.punchType)} @ ${formatTime(new Date(newEntry.entryTimestamp))})`);
            fetchDataForUser();
            loadProfileAndInitialData();
        } catch (err) {
            console.error('Punch-Fehler:', err);
            notify(t("manualPunchError", "Fehler beim manuellen Stempeln."), 'error');
        }
    }

    // Funktion zum Speichern von Notizen (übernommen von HourlyWeekOverview)
    const handleNoteSave = async (isoDate, content) => {
        if (!userProfile?.username) return;
        try {
            await api.post('/api/timetracking/daily-note', { note: content }, {
                params: { username: userProfile.username, date: isoDate }
            });
            notify(t("dailyNoteSaved", "Notiz gespeichert!"), 'success');
            fetchDataForUser(); // Daten neu laden, um die Notiz anzuzeigen
        } catch (err) {
            console.error('Fehler beim Speichern der Tagesnotiz:', err);
            notify(t("dailyNoteError", "Notiz konnte nicht gespeichert werden."), 'error');
        } finally {
            setEditingNote(null);
        }
    };


    const weekDates = Array.from({ length: 7 }, (_, i) => addDays(selectedMonday, i));

    const weeklyExpectedMins = weekDates.reduce((sum, d) => {
        const expHours = getExpectedHoursForDay(d, userProfile, defaultExpectedHours, holidaysForUserCanton?.data, vacationRequests, sickLeaves);
        return sum + Math.round((expHours || 0) * 60);
    }, 0);

    const weeklyActualWorkedMinutes = weekDates.reduce((acc, date) => {
        const isoDate = formatLocalDate(date);
        const summaryForDay = dailySummaries.find(s => s.date === isoDate);
        return acc + (summaryForDay?.workedMinutes || 0);
    }, 0);

    const weeklyDiffMins = weeklyActualWorkedMinutes - weeklyExpectedMins;
    const overtimeBalanceStr = minutesToHHMM(userProfile?.trackingBalanceInMinutes || 0);

    const chartData = weekDates.map(d => {
        const iso = formatLocalDate(d);
        const summary = dailySummaries.find(s => s.date === iso);
        const expectedMins = Math.round(getExpectedHoursForDay(d, userProfile, defaultExpectedHours, holidaysForUserCanton?.data, vacationRequests, sickLeaves) * 60);
        return { date: iso, workedMinutes: summary ? summary.workedMinutes : 0, expectedMinutes: expectedMins };
    });

    const sortedCorrections = (showAllCorrections ? correctionRequests : correctionRequests.filter(req => {
        if (!req.requestDate) return false;
        const reqDate = parseISO(req.requestDate);
        return reqDate >= selectedMonday && reqDate < addDays(selectedMonday, 7);
    })).slice().sort((a, b) => (parseISO(b.requestDate) || 0) - (parseISO(a.requestDate) || 0));

    function openCorrectionModalForDay(dateObj) {
        const isoDate = formatLocalDate(dateObj);
        setCorrectionDate(isoDate);
        const summaryForDay = dailySummaries.find(s => s.date === isoDate);
        setDailySummaryForCorrection(summaryForDay || { date: isoDate, entries: [] });
        setShowCorrectionModal(true);
    }

    // Logik für Korrektur-Einreichung und PDF-Druck
    const fetchCorrectionRequests = useCallback(async () => {
        if (!userProfile || !userProfile.username) return;
        try {
            const response = await api.get(`/api/correction/user/${userProfile.username}`);
            setCorrectionRequests(response.data || []);
        } catch (error) {
            console.error('Fehler beim Abrufen der Korrekturanträge:', error);
            notify(t('userManagement.errorLoadingCorrections'), 'error');
        }
    }, [userProfile, notify, t]);

    const handleCorrectionSubmit = async (entries, reason) => {
        if (!correctionDate || !entries || entries.length === 0) {
            notify(t('hourlyDashboard.addEntryFirst'), 'error');
            return;
        }
        if (!userProfile || !userProfile.username) {
            notify(t('hourlyDashboard.userNotFound'), 'error');
            return;
        }
        const correctionPromises = entries.map(entry => {
            const params = new URLSearchParams({
                username: userProfile.username,
                reason,
                requestDate: correctionDate,
                desiredTimestamp: `${correctionDate}T${entry.time}:00`,
                desiredPunchType: entry.type,
            });
            return api.post(`/api/correction/create?${params.toString()}`, null);
        });
        try {
            await Promise.all(correctionPromises);
            notify(t('userDashboard.correctionSuccess'), 'success');
            setShowCorrectionModal(false);
            fetchCorrectionRequests();
        } catch (error) {
            console.error('Fehler beim Absenden der Korrekturanträge:', error);
            const errorMsg = error.response?.data?.message || 'Ein oder mehrere Anträge konnten nicht gesendet werden.';
            notify(errorMsg, 'error');
        }
    };

    async function handlePrintReport() {
        if (!printStartDate || !printEndDate || !userProfile) {
            notify(t('missingDateRange', 'Zeitraum oder Benutzerprofil fehlt.'), 'error');
            return;
        }
        setPrintModalVisible(false);

        const summariesToPrint = dailySummaries
            .filter(s => s.date >= printStartDate && s.date <= printEndDate)
            .sort((a, b) => parseISO(a.date) - parseISO(b.date));

        const doc = new jsPDF('p', 'mm', 'a4');
        doc.setFontSize(14);
        doc.text(
            `${t('printReport.title')} ${t('for')} ${userProfile.firstName} ${userProfile.lastName} (${userProfile.username})`,
            14,
            15
        );
        doc.setFontSize(11);
        doc.text(
            `${t('printReport.periodLabel')}: ${formatDate(printStartDate)} – ${formatDate(printEndDate)}`,
            14,
            22
        );
        const overtimeStr = minutesToHHMM(userProfile.trackingBalanceInMinutes || 0);
        doc.text(
            `${t('overtimeBalance')}: ${overtimeStr}`,
            14,
            28
        );

        const tableBody = summariesToPrint.map(summary => {
            const displayDate = formatDate(summary.date);
            const primary = summary.primaryTimes || { firstStartTime: null, lastEndTime: null, isOpen: false };
            const workStart = primary.firstStartTime ? primary.firstStartTime.substring(0, 5) : '-';
            const workEnd = primary.lastEndTime ? primary.lastEndTime.substring(0, 5) : (primary.isOpen ? t('printReport.open') : '-');
            const breakTimeStr = minutesToHHMM(summary.breakMinutes);
            const totalWorkedStr = minutesToHHMM(summary.workedMinutes);
            const punches = sortEntries(summary.entries)
                .map(e => `${t('punchTypes.' + e.punchType, e.punchType).substring(0,1)}:${formatTime(e.entryTimestamp)}${e.source === 'SYSTEM_AUTO_END' && !e.correctedByUser ? '(A)' : ''}`)
                .join(' | ');
            return [displayDate, workStart, workEnd, breakTimeStr, totalWorkedStr, punches, summary.dailyNote || ''];
        });

        autoTable(doc, {
            head: [[
                t('printReport.date'),
                t('printReport.workStart'),
                t('printReport.workEnd'),
                t('printReport.pause'),
                t('printReport.total'),
                t('printReport.punches'),
                t('printReport.note'),
            ]],
            body: tableBody,
            startY: 36,
            margin: { left: 10, right: 10 },
            styles: { fontSize: 7, cellPadding: 1.5, overflow: 'linebreak' },
            headStyles: { fillColor: [71, 91, 255], textColor: 255, fontStyle: 'bold', halign: 'center' },
            columnStyles: { 5: { cellWidth: 'auto' }, 6: { cellWidth: 40 } },
            didDrawPage: dataHooks => {
                doc.setFontSize(8);
                doc.text(
                    `${t('page')} ${dataHooks.pageNumber} ${t('of')} ${doc.internal.getNumberOfPages()}`,
                    doc.internal.pageSize.getWidth() - 10,
                    doc.internal.pageSize.getHeight() - 10,
                    { align: 'right' }
                );
            },
        });
        doc.save(`Zeitenbericht_${userProfile.username}_${printStartDate}_bis_${printEndDate}.pdf`);
    }

    // Lade- und Routing-Logik
    if (!userProfile) {
        return (
            <div className="user-dashboard scoped-dashboard">
                <Navbar />
                <div className="skeleton-card"></div>
            </div>
        );
    }
    if (userProfile?.username && currentUser?.username && userProfile.username === currentUser.username) {
        if (userProfile.isHourly) return <HourlyDashboard />;
        if (userProfile.isPercentage) return <PercentageDashboard />;
    }

    return (
        <>
            <Navbar />
            <div className="user-dashboard scoped-dashboard">
                <header className="dashboard-header">
                    <h1>{t("welcome")}, {userProfile.firstName || userProfile.username}</h1>
                    <div className="personal-info">
                        <p><strong>{t('usernameLabel')}:</strong> {userProfile.username}</p>
                        <p>
                            <strong>{t('overtimeBalance')}:</strong>
                            <span className={userProfile.trackingBalanceInMinutes < 0 ? 'balance-negative' : 'balance-positive'}>
                                {overtimeBalanceStr}
                            </span>
                        </p>
                    </div>
                    <button onClick={() => setPrintModalVisible(true)} className="button-primary">
                        {t("printReportButton")}
                    </button>
                </header>

                {punchMessage && <div className="punch-message">{punchMessage}</div>}

                <section className="weekly-overview content-section">
                    <h3 className="section-title">{t("weeklyOverview")}</h3>

                    <div className="punch-section">
                        <h4>{t("manualPunchTitle")}</h4>
                        {isCustomerTrackingEnabled && (
                            <>
                                <select value={selectedCustomerId} onChange={e => setSelectedCustomerId(e.target.value)}>
                                    <option value="">{t('noCustomer')}</option>
                                    {recentCustomers.length > 0 && (
                                        <optgroup label={t('recentCustomers')}>
                                            {recentCustomers.map(c => <option key={'r'+c.id} value={c.id}>{c.name}</option>)}
                                        </optgroup>
                                    )}
                                    {customers.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                                </select>
                                <select value={selectedProjectId} onChange={e => setSelectedProjectId(e.target.value)}>
                                    <option value="">{t('noProject','Kein Projekt')}</option>
                                    {projects.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
                                </select>
                                <select value={selectedTaskId} onChange={e => setSelectedTaskId(e.target.value)}>
                                    <option value="">{t('noTask','Keine Aufgabe')}</option>
                                    {tasks.map(task => <option key={task.id} value={task.id}>{task.name}</option>)}
                                </select>
                            </>
                        )}
                        <button onClick={handleManualPunch} className="button-primary" id="punch-button">
                            {t("manualPunchButton")}
                        </button>
                    </div>

                    <div className="week-navigation">
                        <button onClick={() => setSelectedMonday(prev => addDays(prev, -7))} className="button-secondary">
                            ← {t("prevWeek")}
                        </button>
                        <input
                            type="date"
                            value={formatLocalDate(selectedMonday)}
                            onChange={(e) => {
                                const pickedDate = e.target.value ? parseISO(e.target.value) : null;
                                if (pickedDate && !isNaN(pickedDate.getTime())) {
                                    setSelectedMonday(getMondayOfWeek(pickedDate));
                                }
                            }}
                        />
                        <button onClick={() => setSelectedMonday(prev => addDays(prev, 7))} className="button-secondary">
                            {t("nextWeek")} →
                        </button>
                        <button onClick={() => setSelectedMonday(getMondayOfWeek(new Date()))} className="button-secondary">
                            {t('currentWeek', 'Aktuelle Woche')}
                        </button>
                    </div>

                    <div className="weekly-monthly-totals">
                        <div className="summary-item">
                            <span className="summary-label">{t('actualTime')}</span>
                            <span className="summary-value">{minutesToHHMM(weeklyActualWorkedMinutes)}</span>
                        </div>
                        <div className="summary-item">
                            <span className="summary-label">{t('expected')}</span>
                            <span className="summary-value">{minutesToHHMM(weeklyExpectedMins)}</span>
                        </div>
                        <div className="summary-item">
                            <span className="summary-label">{t('weekBalance')}</span>
                            <span className={`summary-value ${weeklyDiffMins < 0 ? 'balance-negative' : 'balance-positive'}`}>{minutesToHHMM(weeklyDiffMins)}</span>
                        </div>
                    </div>



                    <div className="week-display">
                        {weekDates.map((dayObj) => {
                            const isoDate = formatLocalDate(dayObj);
                            const summary = dailySummaries.find(s => s.date === isoDate);
                            const dayName = dayObj.toLocaleDateString('de-DE', { weekday: 'long' });
                            const formattedDisplayDate = formatDate(dayObj);

                            const vacationToday = vacationRequests.find(v => v.approved && isoDate >= v.startDate && isoDate <= v.endDate);
                            const sickToday = sickLeaves.find(sl => isoDate >= sl.startDate && isoDate <= sl.endDate);
                            const holidayName = holidaysForUserCanton.data?.[isoDate];

                            const projectNames = Array.from(
                                new Set(
                                    (summary?.entries || [])
                                        .map(entry => entry.projectName || (projects || []).find(p => String(p.id) === String(entry.projectId))?.name || '')
                                        .filter(Boolean)
                                )
                            );
                            const hasProjects = projectNames.length > 0;
                            const showProjectBadge = isCustomerTrackingEnabled && hasProjects;
                            const projectBadgeBaseLabel = t('assignCustomer.projectTag', 'Projektzeit');
                            const projectBadgeLabel = projectNames.length === 1
                                ? projectNames[0]
                                : `${projectBadgeBaseLabel}${projectNames.length > 1 ? ` (${projectNames.length})` : ''}`;
                            const projectBadgeTitle = projectNames.join(', ');

                            let dayClass = 'week-day-card';
                            if (vacationToday) dayClass += ' vacation-day';
                            if (sickToday) dayClass += ' sick-day';
                            if (holidayName) dayClass += ' holiday-day';

                            let expectedMinsToday = Math.round(getExpectedHoursForDay(
                                dayObj, userProfile, defaultExpectedHours, holidaysForUserCanton?.data, vacationRequests, sickLeaves
                            ) * 60);
                            if (holidayName) {
                                expectedMinsToday = 0;
                            } else if (vacationToday) {
                                expectedMinsToday = vacationToday.halfDay ? expectedMinsToday / 2 : 0;
                            } else if (sickToday) {
                                expectedMinsToday = sickToday.halfDay ? expectedMinsToday / 2 : 0;
                            }

                            const dailyDiffMinutes = summary ? summary.workedMinutes - expectedMinsToday : (holidayName || vacationToday || sickToday ? 0 : -expectedMinsToday);

                            return (
                                <div key={isoDate} className={dayClass}>
                                    <div className="week-day-header day-card-header">
                                        <div className="day-card-header-main">
                                            <h4>{dayName}, {formattedDisplayDate}</h4>
                                            {(holidayName || vacationToday || sickToday || showProjectBadge) && (
                                                <div className="day-card-badges">
                                                    {holidayName && <span className="day-card-badge holiday-badge">{holidayName}</span>}
                                                    {vacationToday && <span className="day-card-badge vacation-badge">{t('onVacation', 'Im Urlaub')}</span>}
                                                    {sickToday && <span className="day-card-badge sick-badge">{t('sickLeave.sick', 'Krank')}</span>}
                                                    {showProjectBadge && (
                                                        <span
                                                            className="day-card-badge project-badge"
                                                            title={projectBadgeTitle || undefined}
                                                        >
                                                            {projectBadgeLabel}
                                                        </span>
                                                    )}
                                                </div>
                                            )}
                                        </div>
                                        {isCustomerTrackingEnabled && summary && summary.entries?.length > 0 && (
                                            <div className="day-card-actions">
                                                <button
                                                    onClick={() => setModalInfo({ isVisible: true, day: dayObj, summary })}
                                                    className="button-primary-outline"
                                                >
                                                    {t('assignCustomer.editButton', 'Kunden & Zeiten bearbeiten')}
                                                </button>
                                            </div>
                                        )}
                                    </div>

                                    <div className="week-day-content day-card-content">
                                        {vacationToday?.companyVacation && (
                                            <div className="day-card-info vacation-indicator">🏖️ {t('onVacation', 'Im Urlaub')} {vacationToday.halfDay && `(${t('halfDayShort', '½ Tag')})`}</div>
                                        )}
                                        {(!summary || summary.entries.length === 0) && !vacationToday && !sickToday && !holidayName ? (
                                            <p className="no-entries">{t("noEntries")}</p>
                                        ) : (
                                            <>
                                                <ul className="time-entry-list">
                                                    {sortEntries(summary?.entries).map(entry => (
                                                        <li key={entry.id || entry.entryTimestamp} style={{backgroundColor: entry.customerId ? `hsl(${(entry.customerId * 57) % 360}, var(--customer-color-saturation), var(--customer-color-lightness))` : 'transparent'}}>
                                                            <span className="entry-label">{t(`punchTypes.${entry.punchType}`, entry.punchType)}:</span>
                                                            <span className={`entry-time ${isLateTime(formatTime(new Date(entry.entryTimestamp))) ? 'late-time' : ''}`}>
                                                                {formatPunchedTimeFromEntry(entry)}
                                                            </span>
                                                        </li>
                                                    ))}
                                                </ul>
                                                <div className="daily-summary-times">
                                                    <p><strong>{t('actualTime')}:</strong> {minutesToHHMM(summary?.workedMinutes ?? 0)}</p>
                                                    <p><strong>{t('expected')}:</strong> {minutesToHHMM(expectedMinsToday)}</p>
                                                    <p>
                                                        <strong>{t('dayBalance')}:</strong>
                                                        <span className={dailyDiffMinutes < 0 ? 'balance-negative' : 'balance-positive'}>
                                                            {minutesToHHMM(dailyDiffMinutes)}
                                                        </span>
                                                    </p>
                                                </div>
                                                <div className="daily-note-container">
                                                    {editingNote === isoDate ? (
                                                        <>
                                                            <textarea
                                                                className="daily-note-editor"
                                                                value={noteContent}
                                                                onChange={(e) => setNoteContent(e.target.value)}
                                                                rows="4"
                                                                placeholder={t('enterNotePlaceholder', "Notiz eingeben...")}
                                                            />
                                                            <div className="note-buttons">
                                                                <button className="button-primary" onClick={() => handleNoteSave(isoDate, noteContent)}>{t('save')}</button>
                                                                <button className="button-secondary" onClick={() => setEditingNote(null)}>{t('cancel')}</button>
                                                            </div>
                                                        </>
                                                    ) : (
                                                        <div className="daily-note-display">
                                                            <div className="note-content">
                                                                <strong>{t('dailyNoteTitle', 'Notiz')}:</strong>
                                                                <p>{summary?.dailyNote || t('noNotePlaceholder', 'Keine Notiz.')}</p>
                                                            </div>
                                                            <button className="button-edit-note" title={t('editNote', 'Notiz bearbeiten')} onClick={() => {
                                                                setEditingNote(isoDate);
                                                                setNoteContent(summary?.dailyNote || '');
                                                            }}>✏️
                                                            </button>
                                                        </div>
                                                    )}
                                                </div>
                                            </>
                                        )}
                                        {vacationToday?.companyVacation && (!summary || summary.entries.length === 0) && (
                                            <p className="no-entries">{t('noEntries')}</p>
                                        )}
                                    </div>
                                    <div className="correction-button-row">
                                        <button onClick={() => openCorrectionModalForDay(dayObj)} className="button-secondary">
                                            {t("submitCorrectionRequest")}
                                        </button>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </section>

                <section className="vacation-section content-section">
                    <h3 className="section-title">{t('vacationTitle', 'Urlaub & Abwesenheiten')}</h3>
                    <VacationCalendar
                        vacationRequests={vacationRequests}
                        userProfile={userProfile}
                        onRefreshVacations={fetchDataForUser}
                    />
                </section>

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

                {modalInfo.isVisible && (
                    <CustomerTimeAssignModal
                        t={t}
                        day={modalInfo.day}
                        summary={modalInfo.summary}
                        customers={customers}
                        projects={projects}
                        onClose={() => setModalInfo({ isVisible: false, day: null, summary: null })}
                        onSave={() => {
                            fetchDataForUser();
                            setModalInfo({ isVisible: false, day: null, summary: null });
                        }}
                    />
                )}

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

                <CorrectionModal
                    visible={showCorrectionModal}
                    correctionDate={correctionDate}
                    dailySummary={dailySummaryForCorrection}
                    onSubmit={handleCorrectionSubmit}
                    onClose={() => setShowCorrectionModal(false)}
                    t={t}
                />
            </div>
        </>
    );
}

export default UserDashboard;