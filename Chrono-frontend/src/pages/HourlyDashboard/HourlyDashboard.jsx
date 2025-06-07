// src/pages/HourlyDashboard/HourlyDashboard.jsx
import  { useState, useEffect, useCallback, useRef } from 'react';
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
} from './hourDashUtils';

import HourlyWeekOverview from './HourlyWeekOverview';
import HourlyVacationSection from './HourlyVacationSection';
import HourlyCorrectionsPanel from './HourlyCorrectionsPanel';
import HourlyCorrectionModal from './HourlyCorrectionModal';
import PrintReportModal from '../../components/PrintReportModal.jsx';

import '../../styles/HourlyDashboardScoped.css';
import autoTable from "jspdf-autotable";

const HourlyDashboard = () => {
    const { t } = useTranslation();
    const { notify } = useNotification();
    const {fetchCurrentUser } = useAuth();

    const [userProfile, setUserProfile] = useState(null);
    const [dailySummaries, setDailySummaries] = useState([]); // Hält DailyTimeSummaryDTOs
    const [punchMessage, setPunchMessage] = useState('');
    const lastPunchTimeRef = useRef(0);

    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));
    const [printModalVisible, setPrintModalVisible] = useState(false);
    const [printStartDate, setPrintStartDate] = useState(formatLocalDate(new Date()));
    const [printEndDate, setPrintEndDate] = useState(formatLocalDate(new Date()));
    const [vacationRequests, setVacationRequests] = useState([]);
    const [correctionRequests, setCorrectionRequests] = useState([]);
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
                setUserProfile(profile);
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

    const fetchDataForUser = useCallback(async () => {
        if (!userProfile?.username) return;
        try {
            const [resSummaries, resVacation, resCorr] = await Promise.all([
                api.get(`/api/timetracking/history?username=${userProfile.username}`),
                api.get('/api/vacation/my'), // Nimmt den Username aus dem Token im Backend
                api.get(`/api/correction/my?username=${userProfile.username}`) // Oder /api/correction/my, wenn Backend es unterstützt
            ]);
            setDailySummaries(Array.isArray(resSummaries.data) ? resSummaries.data : []);
            setVacationRequests(Array.isArray(resVacation.data) ? resVacation.data : []);
            setCorrectionRequests(Array.isArray(resCorr.data) ? resCorr.data : []);
        } catch (err) {
            console.error("Fehler beim Laden der Benutzerdaten (Hourly):", err);
            notify(t('errors.fetchUserDataError', 'Fehler beim Laden der Benutzerdaten.'), 'error');
        }
    }, [userProfile, notify, t]);

    useEffect(() => {
        if (userProfile) {
            fetchDataForUser();
        }
    }, [userProfile, fetchDataForUser]);

    useEffect(() => {
        const interval = setInterval(doNfcCheck, 2000); // NFC-Polling bleibt
        return () => clearInterval(interval);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    async function doNfcCheck() {
        try {
            const response = await fetch(`${import.meta.env.VITE_API_BASE_URL}/api/nfc/read/1`);
            if (!response.ok) return;
            const json = await response.json();
            if (json.status !== 'success') return;
            const cardUser = json.data;
            if (!cardUser) return;

            if (Date.now() - lastPunchTimeRef.current < 5000) return;
            lastPunchTimeRef.current = Date.now();

            setPunchMessage(`${t('login.stamped', 'Eingestempelt')}: ${cardUser}`);
            setTimeout(() => setPunchMessage(''), 3000);

            await api.post('/api/timetracking/punch', null, { params: { username: cardUser, source: 'NFC_SCAN' } });
            fetchDataForUser();
            loadProfileAndInitialData();
        } catch (err) {
            console.error('NFC error', err);
        }
    }

    async function handleManualPunch() {
        if (!userProfile) return;
        try {
            const response = await api.post('/api/timetracking/punch', null, {
                params: { username: userProfile.username, source: 'MANUAL_PUNCH' }
            });
            const newEntry = response.data; // TimeTrackingEntryDTO
            setPunchMessage(`${t("manualPunchMessage")} ${userProfile.username} (${t('punchTypes.'+newEntry.punchType, newEntry.punchType)} @ ${formatTime(new Date(newEntry.entryTimestamp))})`);
            setTimeout(() => setPunchMessage(''), 3000);
            fetchDataForUser();
            loadProfileAndInitialData();
        } catch (err) {
            console.error('Punch-Fehler:', err);
            notify(t("manualPunchError", "Fehler beim manuellen Stempeln."), 'error');
        }
    }

    const weeklyTotalMins = computeTotalWorkedMinutesInRange(dailySummaries, selectedMonday, addDays(selectedMonday, 6));
    const firstOfMonth = new Date(selectedMonday.getFullYear(), selectedMonday.getMonth(), 1);
    const lastOfMonth = new Date(selectedMonday.getFullYear(), selectedMonday.getMonth() + 1, 0);
    const monthlyTotalMins = computeTotalWorkedMinutesInRange(dailySummaries, firstOfMonth, lastOfMonth);
    const overtimeBalanceStr = minutesToHHMM(userProfile?.trackingBalanceInMinutes || 0);

    function openCorrectionModalForDay(dateObj, summaryForDay = null) {
        const isoDate = formatLocalDate(dateObj);
        setCorrectionDate(isoDate);
        setDailySummaryForCorrection(summaryForDay || { date: isoDate, entries: [] });
        setShowCorrectionModal(true);
    }

    async function handleCorrectionSubmit(correctionPayload) {
        if (!userProfile?.username) {
            notify("Benutzerprofil nicht geladen.", "error");
            return;
        }
        try {
            await api.post('/api/correction/create', null, {
                params: {
                    username: userProfile.username,
                    ...correctionPayload
                }
            });
            notify(t("correctionSubmitSuccess", "Korrekturantrag erfolgreich gesendet."), 'success');
            fetchDataForUser(); // Daten neu laden, um Korrekturen und ggf. Saldo zu aktualisieren
            setShowCorrectionModal(false);
        } catch (err) {
            console.error("Fehler beim Senden des Korrekturantrags:", err);
            const errorMsg = err.response?.data?.message || err.message || t('errors.unknownError');
            notify(t("correctionSubmitError", "Fehler beim Senden des Korrekturantrags:") + ` ${errorMsg}`, 'error');
        }
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
        doc.text(`Zeitenbericht für ${userProfile.firstName} ${userProfile.lastName} (${userProfile.username})`, 14, 15);
        doc.setFontSize(11);
        doc.text(`Zeitraum: ${formatDate(printStartDate)} – ${formatDate(printEndDate)}`, 14, 22);

        const tableBody = summariesToPrint.map(summary => {
            const displayDate = formatDate(summary.date);
            const primary = summary.primaryTimes || { firstStartTime: null, lastEndTime: null, isOpen: false};
            const workStart  = primary.firstStartTime ? primary.firstStartTime.substring(0,5) : "-";
            const workEnd    = primary.lastEndTime ? primary.lastEndTime.substring(0,5) : (primary.isOpen ? "OFFEN" : "-");
            const breakTimeStr = minutesToHHMM(summary.breakMinutes);
            const totalWorkedStr = minutesToHHMM(summary.workedMinutes);
            const punches = summary.entries.map(e => `${t('punchTypes.'+e.punchType, e.punchType).substring(0,1)}:${formatTime(e.entryTimestamp)}${e.source === 'SYSTEM_AUTO_END' && !e.correctedByUser ? '(A)' : ''}`).join(' | ');

            return [displayDate, workStart, workEnd, breakTimeStr, totalWorkedStr, punches, summary.dailyNote || ""];
        });

        autoTable(doc, {
            head: [["Datum", "Start", "Ende", "Pause", "Arbeit (Std.)", "Stempelungen", "Notiz"]],
            body: tableBody,
            startY: 30,
            margin: { left: 10, right: 10 },
            styles: { fontSize: 7, cellPadding: 1.5, overflow: 'linebreak' },
            headStyles: { fillColor: [71, 91, 255], textColor: 255, fontStyle: "bold", halign: "center" },
            columnStyles: { /* ... Spaltenbreiten anpassen ... */ },
            didDrawPage: (dataHooks) => {
                doc.setFontSize(8);
                doc.text(`Seite ${dataHooks.pageNumber} von ${doc.internal.getNumberOfPages()}`, doc.internal.pageSize.getWidth() - 10, doc.internal.pageSize.getHeight() - 10, { align: "right" });
            }
        });
        doc.save(`Zeitenbericht_Stunden_${userProfile.username}_${printStartDate}_bis_${printEndDate}.pdf`);
    }


    if (!userProfile) {
        return (
            <div className="hourly-dashboard scoped-dashboard">
                <Navbar />
                <p className="loading-message">{t("loading", "Lade Benutzerprofil...")}</p>
            </div>
        );
    }

    return (
        <div className="hourly-dashboard scoped-dashboard">
            <Navbar />
            <header className="dashboard-header">
                <h2>{t("hourlyDashboard.title", "Stundenübersicht")}</h2>
                <div className="personal-info">
                    <p><strong>{t("usernameLabel")}:</strong> {userProfile.username}</p>
                    <p><strong>{t("overtimeBalance")}:</strong> {overtimeBalanceStr}</p>
                </div>
            </header>

            {punchMessage && <div className="punch-message">{punchMessage}</div>}

            <HourlyWeekOverview
                t={t}
                dailySummaries={dailySummaries}
                selectedMonday={selectedMonday}
                setSelectedMonday={setSelectedMonday}
                weeklyTotalMins={weeklyTotalMins}
                monthlyTotalMins={monthlyTotalMins}
                handleManualPunch={handleManualPunch}
                punchMessage={punchMessage}
                openCorrectionModal={openCorrectionModalForDay}
                userProfile={userProfile}
            />

            <div className="print-report-container">
                <button onClick={() => setPrintModalVisible(true)} className="button-primary">
                    {t("printReportButton", "Bericht drucken")}
                </button>
            </div>
            <PrintReportModal
                t={t}
                visible={printModalVisible}
                startDate={printStartDate}
                setStartDate={setPrintStartDate}
                endDate={printEndDate}
                setEndDate={setPrintEndDate}
                onConfirm={handlePrintReport}
                onClose={() => setPrintModalVisible(false)}
                cssScope="hourly"
            />

            <HourlyVacationSection
                t={t}
                userProfile={userProfile}
                vacationRequests={vacationRequests}
                onRefreshVacations={fetchDataForUser}
            />

            <HourlyCorrectionsPanel
                t={t}
                correctionRequests={correctionRequests}
                selectedCorrectionMonday={selectedCorrectionMonday}
                setSelectedCorrectionMonday={setSelectedCorrectionMonday}
                showCorrectionsPanel={showCorrectionsPanel}
                setShowCorrectionsPanel={setShowCorrectionsPanel}
                showAllCorrections={showAllCorrections}
                setShowAllCorrections={setShowAllCorrections}
            />

            <HourlyCorrectionModal
                visible={showCorrectionModal}
                correctionDate={correctionDate}
                dailySummaryForCorrection={dailySummaryForCorrection}
                onSubmitCorrection={handleCorrectionSubmit}
                onClose={() => setShowCorrectionModal(false)}
                t={t}
            />
        </div>
    );
};

export default HourlyDashboard;