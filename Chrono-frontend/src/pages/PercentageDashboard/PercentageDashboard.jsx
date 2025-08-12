// src/pages/PercentageDashboard/PercentageDashboard.jsx
import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate, Link } from "react-router-dom";
import Navbar from '../../components/Navbar';
import api from '../../utils/api';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import { useAuth } from "../../context/AuthContext.jsx";
import { useCustomers } from '../../context/CustomerContext';
import jsPDF from 'jspdf';
import { parseISO } from 'date-fns';
import { useUserData } from '../../hooks/useUserData';
import autoTable from "jspdf-autotable";

import {
    getMondayOfWeek,
    addDays,
    formatLocalDate,
    formatDate,
    formatTime,
    minutesToHHMM,
    computeTotalWorkedMinutesInRange,
    expectedDayMinutesForPercentageUser,
    parseHex16
} from './percentageDashUtils';

import PercentageWeekOverview from './PercentageWeekOverview';
import PercentageVacationSection from './PercentageVacationSection';
import PercentageCorrectionsPanel from './PercentageCorrectionsPanel';
import CorrectionModal from '../../components/CorrectionModal';
import PrintReportModal from "../../components/PrintReportModal.jsx";

// Einheitliche Styles importieren
import '../../styles/PercentageDashboardScoped.css'; // <— NEU: spezifische Fixes für Percentage


const PercentageDashboard = () => {
    const { t } = useTranslation();
    const { notify } = useNotification();
    const { currentUser, fetchCurrentUser } = useAuth();
    const navigate = useNavigate();
    const { refreshData } = useUserData();

    const [userProfile, setUserProfile] = useState(null);
    const [dailySummaries, setDailySummaries] = useState([]);
    const { customers, fetchCustomers } = useCustomers();
    const [recentCustomers, setRecentCustomers] = useState([]);
    const [projects, setProjects] = useState([]);
    const [selectedCustomerId, setSelectedCustomerId] = useState('');
    const [selectedProjectId, setSelectedProjectId] = useState('');
    const [tasks, setTasks] = useState([]);
    const [selectedTaskId, setSelectedTaskId] = useState('');
    const [punchMessage, setPunchMessage] = useState('');
    const lastPunchTimeRef = useRef(0);

    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));

    const [printModalVisible, setPrintModalVisible] = useState(false);
    const [printStartDate, setPrintStartDate] = useState(formatLocalDate(new Date(new Date().getFullYear(), new Date().getMonth(), 1)));
    const [printEndDate, setPrintEndDate] = useState(formatLocalDate(new Date(new Date().getFullYear(), new Date().getMonth() + 1, 0)));

    const [vacationRequests, setVacationRequests] = useState([]);
    const [correctionRequests, setCorrectionRequests] = useState([]);
    const [sickLeaves, setSickLeaves] = useState([]);
    const [holidaysForUserCanton, setHolidaysForUserCanton] = useState({ data: {}, year: null, canton: null });

    const [showCorrectionsPanel, setShowCorrectionsPanel] = useState(false);
    const [showAllCorrections, setShowAllCorrections] = useState(false);
    const [selectedCorrectionMonday, setSelectedCorrectionMonday] = useState(getMondayOfWeek(new Date()));

    const [showCorrectionModal, setShowCorrectionModal] = useState(false);
    const [correctionDate, setCorrectionDate] = useState('');
    const [dailySummaryForCorrection, setDailySummaryForCorrection] = useState(null);

    // State für Notizen
    const [editingNote, setEditingNote] = useState(null);
    const [noteContent, setNoteContent] = useState('');

    const loadProfileAndInitialData = useCallback(async () => {
        try {
            const profile = await fetchCurrentUser();
            if (profile && Object.keys(profile).length > 0) {
                if (profile.isPercentage) {
                    profile.workPercentage = profile.workPercentage ?? 100;
                    profile.annualVacationDays = profile.annualVacationDays ?? 25;
                    profile.trackingBalanceInMinutes = profile.trackingBalanceInMinutes ?? 0;
                    profile.expectedWorkDays = profile.expectedWorkDays ?? 5;
                }
                if (!profile.weeklySchedule || !Array.isArray(profile.weeklySchedule) || profile.weeklySchedule.length === 0) {
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
    }, [fetchCurrentUser, t, notify]);

    useEffect(() => {
        loadProfileAndInitialData();
    }, [loadProfileAndInitialData]);

    useEffect(() => {
        const trackingEnabled = userProfile?.customerTrackingEnabled || currentUser?.customerTrackingEnabled;
        if (trackingEnabled) {
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
    }, [userProfile, currentUser, fetchCustomers]);

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
            console.error("Fehler beim Laden der Benutzerdaten (Percentage):", err);
            notify(t('errors.fetchUserDataError', 'Fehler beim Laden der Benutzerdaten.'), 'error');
        }
    }, [userProfile, notify, t]);

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
            fetchHolidaysForUser(selectedMonday.getFullYear(), cantonAbbr || '');
        }
    }, [userProfile, fetchDataForUser, fetchHolidaysForUser, selectedMonday]);

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
            showPunchMessage(`${t("manualPunchMessage")} ${userProfile.username} (${t('punchTypes.'+newEntry.punchType, newEntry.punchType)} @ ${formatTime(new Date(newEntry.entryTimestamp))})`);
            fetchDataForUser();
        } catch (error) {
            console.error('Punch Error:', error);
            notify(error.message || t('punchError', 'Fehler beim Stempeln'), 'error');
        }
    };

    const handleNoteSave = async (isoDate, content) => {
        if (!userProfile?.username) return;
        try {
            await api.post('/api/timetracking/daily-note', { note: content }, {
                params: { username: userProfile.username, date: isoDate }
            });
            notify(t("dailyNoteSaved", "Notiz gespeichert!"), 'success');
            fetchDataForUser();
        } catch (err) {
            console.error('Fehler beim Speichern der Tagesnotiz:', err);
            notify(t("dailyNoteError", "Notiz konnte nicht gespeichert werden."), 'error');
        } finally {
            setEditingNote(null);
        }
    };

    const handleCorrectionSubmit = async (entries, reason) => {
        if (!correctionDate || !entries || entries.length === 0) {
            notify(t('hourlyDashboard.addEntryFirst'), 'error');
            return;
        }
        if (!currentUser || !currentUser.username) {
            notify(t('hourlyDashboard.userNotFound'), 'error');
            return;
        }
        const correctionPromises = entries.map(entry => {
            const params = new URLSearchParams({
                username: currentUser.username,
                reason: reason,
                requestDate: correctionDate,
                desiredTimestamp: `${correctionDate}T${entry.time}:00`,
                desiredPunchType: entry.type
            });
            return api.post(`/api/correction/create?${params.toString()}`, null);
        });
        try {
            await Promise.all(correctionPromises);
            notify(t('userDashboard.correctionSuccess'), 'success');
            setShowCorrectionModal(false);
            fetchDataForUser();
        } catch (error) {
            console.error('Fehler beim Absenden der Korrekturanträge:', error);
            const errorMsg = error.response?.data?.message || 'Ein oder mehrere Anträge konnten nicht gesendet werden.';
            notify(errorMsg, 'error');
        }
    };

    function openCorrectionModalForDay(dateObj, summary) {
        setCorrectionDate(formatLocalDate(dateObj));
        setDailySummaryForCorrection(summary || { date: formatLocalDate(dateObj), entries: [] });
        setShowCorrectionModal(true);
    }

    async function handlePrintReport() {
        if (!printStartDate || !printEndDate || !userProfile) {
            notify(t("missingDateRange", "Zeitraum oder Benutzerprofil fehlt."), 'error');
            return;
        }
        setPrintModalVisible(false);

        const summariesToPrint = dailySummaries.filter(summary =>
            summary.date >= printStartDate && summary.date <= printEndDate
        ).sort((a,b) => parseISO(a.date) - parseISO(b.date));

        const doc = new jsPDF("p", "mm", "a4");
        doc.setFontSize(14);
        doc.text(`${t('printReport.title')} ${t('for')} ${userProfile.firstName} ${userProfile.lastName} (${userProfile.username}) - ${userProfile.workPercentage}%`, 14, 15);
        doc.setFontSize(11);
        doc.text(`${t('printReport.periodLabel')}: ${formatDate(printStartDate)} – ${formatDate(printEndDate)}`, 14, 22);

        const tableBody = summariesToPrint.map(summary => {
            const displayDate = formatDate(summary.date);
            const primary = summary.primaryTimes || { firstStartTime: null, lastEndTime: null, isOpen: false};
            const workStart  = primary.firstStartTime ? primary.firstStartTime.substring(0,5) : "-";
            const workEnd    = primary.lastEndTime ? primary.lastEndTime.substring(0,5) : (primary.isOpen ? t('printReport.open') : "-");
            const breakTimeStr = minutesToHHMM(summary.breakMinutes);
            const totalWorkedStr = minutesToHHMM(summary.workedMinutes);
            const punches = summary.entries.map(e => `${t('punchTypes.'+e.punchType, e.punchType).substring(0,1)}:${formatTime(e.entryTimestamp)}${e.source === 'SYSTEM_AUTO_END' && !e.correctedByUser ? '(A)' : ''}`).join(' | ');
            return [displayDate, workStart, workEnd, breakTimeStr, totalWorkedStr, punches, summary.dailyNote || ""];
        });
        autoTable(doc, {
            head: [[t('printReport.date'), t('printReport.workStart'), t('printReport.workEnd'), t('printReport.pause'), t('printReport.total'), t('printReport.punches'), t('printReport.note')]],
            body: tableBody, startY: 30, margin: { left: 10, right: 10 }, styles: { fontSize: 7, cellPadding: 1.5, overflow: 'linebreak' },
            headStyles: { fillColor: [71, 91, 255], textColor: 255, fontStyle: "bold", halign: "center" },
            columnStyles: { 5: { cellWidth: 'auto'}, 6: { cellWidth: 40 } },
            didDrawPage: (dataHooks) => {
                doc.setFontSize(8);
                doc.text(`${t('page')} ${dataHooks.pageNumber} ${t('of')} ${doc.internal.getNumberOfPages()}`, doc.internal.pageSize.getWidth() - 10, doc.internal.pageSize.getHeight() - 10, { align: "right" });
            }
        });
        doc.save(`Zeitenbericht_Prozent_${userProfile.username}_${printStartDate}_bis_${printEndDate}.pdf`);
    }

    const weekDatesForOverview = Array.from({ length: 5 }, (_, i) => addDays(selectedMonday, i));
    const weeklyWorked = computeTotalWorkedMinutesInRange(dailySummaries, selectedMonday, addDays(selectedMonday, 4));

    const weeklyExpected = weekDatesForOverview.reduce((sum, dayObj) => {
        const isoDate = formatLocalDate(dayObj);
        const vacationToday = vacationRequests.find(v => v.approved && isoDate >= v.startDate && isoDate <= v.endDate);
        const sickToday = sickLeaves.find(sl => isoDate >= sl.startDate && isoDate <= sl.endDate);
        const isHoliday = holidaysForUserCanton?.data && holidaysForUserCanton.data[isoDate];
        let daySoll = expectedDayMinutesForPercentageUser(userProfile);

        if (isHoliday || (vacationToday && !vacationToday.halfDay) || (sickToday && !sickToday.halfDay)) {
            daySoll = 0;
        } else if (vacationToday?.halfDay || sickToday?.halfDay) {
            daySoll = Math.round(daySoll / 2);
        }
        return sum + daySoll;
    }, 0);
    const weeklyDiff = weeklyWorked - weeklyExpected;
    const overtimeBalanceStr = minutesToHHMM(userProfile?.trackingBalanceInMinutes || 0);

    if (!userProfile) {
        return ( <div className="percentage-dashboard scoped-dashboard"><Navbar /><div className="skeleton-card"></div></div> );
    }
    if (!userProfile.isPercentage) {
        navigate("/user", { replace: true });
        return null;
    }

    return (
        <>
            <Navbar />
            <div className="percentage-dashboard scoped-dashboard">
                <header className="dashboard-header">
                    <h1>{t("percentageDashboard.title", "Prozent-Dashboard")}</h1>
                    <div className="personal-info">
                        <p><strong>{t('usernameLabel')}:</strong> {userProfile.username}</p>
                        <p><strong>{t('percentageDashboard.workPercentageLabel', 'Arbeits-%')}:</strong> {userProfile.workPercentage}%</p>
                        <p>
                            <strong>{t('overtimeBalance')}:</strong>
                            <span className={userProfile.trackingBalanceInMinutes < 0 ? 'balance-negative' : 'balance-positive'}>{overtimeBalanceStr}</span>
                        </p>
                    </div>
                    <div className="header-actions">
                        <button onClick={() => setPrintModalVisible(true)} className="button-primary">
                            {t("printReportButton", "Bericht drucken")}
                        </button>
                        <Link to="/payslips" className="button-primary-outline" style={{ marginLeft: '1rem' }}>
                            {t('payslips.title')}
                        </Link>
                    </div>
                </header>

                {punchMessage && <div className="punch-message">{punchMessage}</div>}

                <PercentageWeekOverview
                    t={t}
                    dailySummaries={dailySummaries}
                    monday={selectedMonday}
                    setMonday={setSelectedMonday}
                    weeklyWorked={weeklyWorked}
                    weeklyExpected={weeklyExpected}
                    weeklyDiff={weeklyDiff}
                    handleManualPunch={handleManualPunch}
                    openCorrectionModal={openCorrectionModalForDay}
                    userProfile={userProfile}
                    customers={customers}
                    recentCustomers={recentCustomers}
                    projects={projects}
                    tasks={tasks}
                    selectedCustomerId={selectedCustomerId}
                    setSelectedCustomerId={setSelectedCustomerId}
                    selectedProjectId={selectedProjectId}
                    selectedTaskId={selectedTaskId}
                    setSelectedTaskId={setSelectedTaskId}
                    vacationRequests={vacationRequests}
                    sickLeaves={sickLeaves}
                    holidaysForUserCanton={holidaysForUserCanton?.data}
                    reloadData={fetchDataForUser}
                    editingNote={editingNote}
                    setEditingNote={setEditingNote}
                    noteContent={noteContent}
                    setNoteContent={setNoteContent}
                    handleNoteSave={handleNoteSave}
                />

                <section className="vacation-section content-section">
                    <h3 className="section-title">{t('vacationTitle', 'Urlaub & Abwesenheiten')}</h3>
                    <PercentageVacationSection
                        t={t}
                        userProfile={userProfile}
                        vacationRequests={vacationRequests}
                        onRefreshVacations={fetchDataForUser}
                    />
                </section>

                <PercentageCorrectionsPanel
                    t={t}
                    correctionRequests={correctionRequests}
                    selectedCorrectionMonday={selectedCorrectionMonday}
                    setSelectedCorrectionMonday={setSelectedCorrectionMonday}
                    showCorrectionsPanel={showCorrectionsPanel}
                    setShowCorrectionsPanel={setShowCorrectionsPanel}
                    showAllCorrections={showAllCorrections}
                    setShowAllCorrections={setShowAllCorrections}
                />

                <PrintReportModal
                    t={t}
                    visible={printModalVisible}
                    startDate={printStartDate}
                    setStartDate={setPrintStartDate}
                    endDate={printEndDate}
                    setEndDate={setPrintEndDate}
                    onConfirm={handlePrintReport}
                    onClose={() => setPrintModalVisible(false)}
                    cssScope="percentage"
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
};

export default PercentageDashboard;