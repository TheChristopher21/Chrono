// src/pages/PercentageDashboard/PercentageDashboard.jsx
import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from "react-router-dom";
import Navbar from '../../components/Navbar';
import api from '../../utils/api';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import { useAuth } from "../../context/AuthContext.jsx";
import 'jspdf-autotable';
import jsPDF from 'jspdf';
import { parseISO } from 'date-fns';

import {
    getMondayOfWeek,
    addDays,
    formatLocalDate,
    formatDate,
    formatTime,
    minutesToHHMM,
    computeTotalWorkedMinutesInRange,
    expectedDayMinutesForPercentageUser, // Spezifisch für Prozent-Nutzer
    // parseHex16 wird im NFC-Check verwendet
    parseHex16
} from './percentageDashUtils';

import PercentageWeekOverview from './PercentageWeekOverview';
import PercentageVacationSection from './PercentageVacationSection';
import PercentageCorrectionsPanel from './PercentageCorrectionsPanel';
import PercentageCorrectionModal from './PercentageCorrectionModal';
import PrintReportModal from "../../components/PrintReportModal.jsx";

import '../../styles/PercentageDashboardScoped.css';
import autoTable from "jspdf-autotable";


const PercentageDashboard = () => {
    const { t } = useTranslation();
    const { notify } = useNotification();
    const { currentUser, fetchCurrentUser } = useAuth();
    const navigate = useNavigate();

    const [userProfile, setUserProfile] = useState(null);
    const [dailySummaries, setDailySummaries] = useState([]);
    const [customers, setCustomers] = useState([]);
    const [recentCustomers, setRecentCustomers] = useState([]);
    const [projects, setProjects] = useState([]);
    const [selectedCustomerId, setSelectedCustomerId] = useState('');
    const [selectedProjectId, setSelectedProjectId] = useState('');
    const [punchMessage, setPunchMessage] = useState('');
    const lastPunchTimeRef = useRef(0);

    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));

    const [printModalVisible, setPrintModalVisible] = useState(false);
    const [printStartDate, setPrintStartDate] = useState(formatLocalDate(new Date(new Date().getFullYear(), new Date().getMonth(), 1)));
    const [printEndDate, setPrintEndDate] = useState(formatLocalDate(new Date(new Date().getFullYear(), new Date().getMonth() + 1, 0)));

    const [vacationRequests, setVacationRequests] = useState([]);
    const [correctionRequests, setCorrectionRequests] = useState([]);
    const [sickLeaves, setSickLeaves] = useState([]); // NEU für Krankmeldungen
    const [holidaysForUserCanton, setHolidaysForUserCanton] = useState({ data: {}, year: null, canton: null }); // NEU für Feiertage


    const [showCorrectionsPanel, setShowCorrectionsPanel] = useState(false);
    const [showAllCorrections, setShowAllCorrections] = useState(false);
    const [selectedCorrectionMonday, setSelectedCorrectionMonday] = useState(getMondayOfWeek(new Date()));

    const [showCorrectionModal, setShowCorrectionModal] = useState(false);
    const [correctionDate, setCorrectionDate] = useState('');
    const [dailySummaryForCorrection, setDailySummaryForCorrection] = useState(null);

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
        if (userProfile?.company?.customerTrackingEnabled) {
            api.get('/api/customers')
                .then(res => setCustomers(Array.isArray(res.data) ? res.data : []))
                .catch(err => console.error('Error loading customers', err));
            api.get('/api/customers/recent')
                .then(res => setRecentCustomers(Array.isArray(res.data) ? res.data : []))
                .catch(err => console.error('Error loading customers', err));
            api.get('/api/projects')
                .then(res => setProjects(Array.isArray(res.data) ? res.data : []))
                .catch(err => console.error('Error loading projects', err));
        } else {
            setCustomers([]);
            setRecentCustomers([]);
            setProjects([]);
        }
    }, [userProfile]);

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

    const fetchDataForUser = useCallback(async () => {
        if (!userProfile?.username) return;
        try {
            const [resSummaries, resVacation, resCorr, resSick] = await Promise.all([
                api.get(`/api/timetracking/history?username=${userProfile.username}`),
                api.get('/api/vacation/my'),
                api.get(`/api/correction/my?username=${userProfile.username}`),
                api.get('/api/sick-leave/my') // Endpunkt für eigene Krankmeldungen
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

    useEffect(() => {
        if (userProfile) {
            fetchDataForUser();
            const cantonAbbr = userProfile.company?.cantonAbbreviation || userProfile.companyCantonAbbreviation;
            fetchHolidaysForUser(selectedMonday.getFullYear(), cantonAbbr || '');
        }
    }, [userProfile, fetchDataForUser, fetchHolidaysForUser, selectedMonday]);

    useEffect(() => {
        if (userProfile) {
            const cantonAbbr = userProfile.company?.cantonAbbreviation || userProfile.companyCantonAbbreviation;
            fetchHolidaysForUser(selectedMonday.getFullYear(), cantonAbbr || '');
        }
    }, [selectedMonday, userProfile, fetchHolidaysForUser]);


    useEffect(() => {
        const interval = setInterval(doNfcCheck, 2000);
        return () => clearInterval(interval);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

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
            if (currentUser && cardUser === currentUser.username) { // Prüfe gegen currentUser
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
            const response = await api.post('/api/timetracking/punch', null, { params });
            const newEntry = response.data;
            showPunchMessage(`${t("manualPunchMessage")} ${userProfile.username} (${t('punchTypes.'+newEntry.punchType, newEntry.punchType)} @ ${formatTime(new Date(newEntry.entryTimestamp))})`);
            fetchDataForUser();
            loadProfileAndInitialData();
        } catch (e) {
            console.error('Punch‑Fehler', e);
            notify(t("manualPunchError", "Fehler beim manuellen Stempeln."), 'error');
        }
    }

    const assignCustomerForDay = async (isoDate, customerId) => {
        try {
            await api.put('/api/timetracking/day/customer', null, { params: { username: userProfile.username, date: isoDate, customerId: customerId || '' } });
            fetchDataForUser();
            notify(t('customerSaved'), 'success');
        } catch (err) {
            console.error('Error saving customer', err);
            notify(t('customerSaveError'), 'error');
        }
    };

    const assignProjectForDay = async (isoDate, projectId) => {
        try {
            await api.put('/api/timetracking/day/project', null, { params: { username: userProfile.username, date: isoDate, projectId: projectId || '' } });
            fetchDataForUser();
            notify(t('customerSaved'), 'success');
        } catch (err) {
            console.error('Error saving project', err);
            notify(t('customerSaveError'), 'error');
        }
    };

    const weekDatesForOverview = Array.from({ length: 5 }, (_, i) => addDays(selectedMonday, i)); // Mo-Fr
    const weeklyWorked = computeTotalWorkedMinutesInRange(dailySummaries, selectedMonday, addDays(selectedMonday, 4)); // Mo-Fr für %

    const weeklyExpected = weekDatesForOverview.reduce((sum, dayObj) => {
        const isoDate = formatLocalDate(dayObj);
        const vacationToday = vacationRequests.find(v => v.approved && isoDate >= v.startDate && isoDate <= v.endDate);
        const sickToday = sickLeaves.find(sl => isoDate >= sl.startDate && isoDate <= sl.endDate);
        const isHoliday = holidaysForUserCanton?.data && holidaysForUserCanton.data[isoDate];
        let daySoll = expectedDayMinutesForPercentageUser(userProfile);

        // Für Prozent-Nutzer: Abwesenheiten reduzieren das Wochensoll anteilig
        if (isHoliday) {
            // Annahme: Feiertage reduzieren das Soll für Prozent-Nutzer (kann konfiguriert werden)
            // Im Backend wird dies über UserHolidayOption genauer gehandhabt
            daySoll = 0;
        }
        if (vacationToday) {
            daySoll = vacationToday.halfDay ? daySoll / 2 : 0;
        }
        if (sickToday) {
            daySoll = sickToday.halfDay ? daySoll / 2 : 0;
        }
        return sum + daySoll;
    }, 0);
    const weeklyDiff = weeklyWorked - weeklyExpected;

    const firstOfMonth = new Date(selectedMonday.getFullYear(), selectedMonday.getMonth(), 1);
    const lastOfMonth = new Date(selectedMonday.getFullYear(), selectedMonday.getMonth() + 1, 0);
    const monthlyTotalMins = computeTotalWorkedMinutesInRange(dailySummaries, firstOfMonth, lastOfMonth);
    const overtimeBalanceStr = minutesToHHMM(userProfile?.trackingBalanceInMinutes || 0);

    const sortedCorrections = (showAllCorrections ? correctionRequests : correctionRequests.filter(req => {
        if (!req.requestDate) return false;
        const reqDate = parseISO(req.requestDate);
        return reqDate >= selectedCorrectionMonday && reqDate < addDays(selectedCorrectionMonday, 7);
    }))
        .slice()
        .sort((a, b) => {
            const dateA = a.requestDate ? parseISO(a.requestDate) : 0;
            const dateB = b.requestDate ? parseISO(b.requestDate) : 0;
            return dateB - dateA;
        });

    function openCorrectionModalForDay(dateObj) {
        const isoDate = formatLocalDate(dateObj);
        setCorrectionDate(isoDate);
        const summaryForDay = dailySummaries.find(s => s.date === isoDate);
        setDailySummaryForCorrection(summaryForDay || { date: isoDate, entries: [] });
        setShowCorrectionModal(true);
    }

    const fetchCorrectionRequests = useCallback(async () => {
        if (!currentUser || !currentUser.username) {
            return;
        }
        try {
            // Die URL braucht keine Datums-Parameter mehr
            const response = await api.get(`/api/correction/user/${currentUser.username}`);
            setCorrectionRequests(response.data);
        } catch (error) {
            console.error("Fehler beim Abrufen der Korrekturanträge:", error);
            notify(t('userManagement.errorLoadingCorrections'), 'error');
        }
    }, [currentUser, notify]); // Abhängigkeiten bleiben gleich

    const handleCorrectionSubmit = async (entries, reason) => {
        // Prüfen, ob alle notwendigen Daten vorhanden sind
        if (!correctionDate || !entries || entries.length === 0) {
            notify(t('hourlyDashboard.addEntryFirst'), 'error');
            return;
        }
        if (!currentUser || !currentUser.username) {
            notify(t('hourlyDashboard.userNotFound'), 'error');
            return;
        }

        // Erstellt eine Liste von Promises für jeden einzelnen Korrekturantrag
        const correctionPromises = entries.map(entry => {
            const desiredTimestamp = `${correctionDate}T${entry.time}:00`;
            const desiredPunchType = entry.type;

            // Alle Parameter für die URL vorbereiten, wie vom Backend jetzt verlangt
            const params = new URLSearchParams({
                username: currentUser.username,
                reason: reason,
                requestDate: correctionDate,
                desiredTimestamp: desiredTimestamp,
                desiredPunchType: desiredPunchType
                // targetEntryId ist optional und wird hier weggelassen
            });

            // API-Aufruf mit leerem Body, da alle Daten in der URL sind
            return api.post(`/api/correction/create?${params.toString()}`, null);
        });

        try {
            // Wartet, bis alle Anfragen parallel gesendet wurden
            await Promise.all(correctionPromises);

            notify(t('userDashboard.correctionSuccess'), 'success');
            setShowCorrectionModal(false);
            fetchCorrectionRequests(selectedCorrectionMonday);

        } catch (error) {
            console.error('Fehler beim Absenden der Korrekturanträge:', error);
            const errorMsg = error.response?.data?.message || 'Ein oder mehrere Anträge konnten nicht gesendet werden.';
            notify(errorMsg, 'error');
        }
    };


    async function handlePrintReport() {
        // ... (Logik bleibt gleich wie im UserDashboard, ggf. Titel anpassen)
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

    if (!userProfile) {
        return ( <div className="percentage-dashboard scoped-dashboard"><Navbar /><p className="loading-message">{t("loading", "Lade Benutzerprofil...")}</p></div> );
    }
    if (userProfile.isHourly) { // Sicherstellen, dass nur Stundenlöhner hier landen
        navigate("/user", { replace: true });
        return null;
    }
    if (!userProfile.isPercentage) { // Wenn kein Prozent-Nutzer, zum Standard-Dashboard
        navigate("/user", { replace: true });
        return null;
    }


    return (
        <> {/* React Fragment oder ein neutrales <div> als äußeren Container nutzen */}
            <Navbar /> {/* NAVBAR IST JETZT AUSSERHALB DES SCOPED-DASHBOARD DIVS */}
            <div className="percentage-dashboard scoped-dashboard">
                <header className="dashboard-header">
                <h2>{t("percentageDashboard.title", "Prozent-Dashboard")}</h2>
                <div className="personal-info">
                    <p><strong>{t('usernameLabel')}:</strong> {userProfile.username}</p>
                    <p><strong>{t('percentageDashboard.workPercentageLabel', 'Arbeits-%')}:</strong> {userProfile.workPercentage}%</p>
                    <p><strong>{t('weekBalance')}:</strong>
                        <span className={(weeklyDiff ?? 0) < 0 ? 'balance-negative' : 'balance-positive'}>
                            {minutesToHHMM(weeklyDiff)}
                        </span>
                    </p>
                    <p className="overtime-info">
                        <strong>{t('overtimeBalance')}:</strong> {overtimeBalanceStr}
                        <span className="tooltip-wrapper">
                            <span className="tooltip-icon">ℹ️</span>
                            <span className="tooltip-box">
                              {t('percentageDashboard.overtimeTooltip', 'Überstunden entstehen, wenn du mehr als dein Wochensoll arbeitest. Du kannst sie später als „Überstundenfrei“ nutzen.')}
                            </span>
                        </span>
                    </p>
                </div>
                <div className="print-report-container">
                    <button onClick={() => setPrintModalVisible(true)} className="button-primary">
                        {t("printReportButton", "Bericht drucken")}
                    </button>
                </div>
            </header>

            {punchMessage && <div className="punch-message">{punchMessage}</div>}

            <PercentageWeekOverview
                t={t}
                dailySummaries={dailySummaries.filter(s => {
                    const isoDate = s.date;
                    return isoDate >= formatLocalDate(selectedMonday) && isoDate <= formatLocalDate(addDays(selectedMonday, 6)); // Mo-So Daten übergeben
                })}
                monday={selectedMonday}
                setMonday={setSelectedMonday}
                weeklyWorked={weeklyWorked}
                weeklyExpected={weeklyExpected}
                weeklyDiff={weeklyDiff}
                handleManualPunch={handleManualPunch}
                punchMessage={punchMessage}
                openCorrectionModal={openCorrectionModalForDay}
                userProfile={userProfile}
                customers={customers}
                recentCustomers={recentCustomers}
                projects={projects}
                selectedCustomerId={selectedCustomerId}
                setSelectedCustomerId={setSelectedCustomerId}
                selectedProjectId={selectedProjectId}
                setSelectedProjectId={setSelectedProjectId}
                assignCustomerForDay={assignCustomerForDay}
                assignProjectForDay={assignProjectForDay}
                vacationRequests={vacationRequests} // NEU
                sickLeaves={sickLeaves} // NEU
                holidaysForUserCanton={holidaysForUserCanton?.data} // NEU
            />

            <PercentageVacationSection
                t={t}
                userProfile={userProfile}
                vacationRequests={vacationRequests}
                onRefreshVacations={fetchDataForUser}
            />

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

            <PercentageCorrectionModal
                visible={showCorrectionModal}
                correctionDate={correctionDate}
                dailySummaryForCorrection={dailySummaryForCorrection}
                onSubmitCorrection={handleCorrectionSubmit}
                onClose={() => setShowCorrectionModal(false)}
                t={t}
            />
        </div>
        </>
    );
};

export default PercentageDashboard;