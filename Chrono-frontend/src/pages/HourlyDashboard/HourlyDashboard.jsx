// src/pages/HourlyDashboard/HourlyDashboard.jsx
//was hat man gemacht / kategorie / pro Stunde? / popup für den tag
//Events im UrlaubsKalender per Admin
import { useState, useEffect, useCallback } from 'react';
import Navbar from '../../components/Navbar';
import api from '../../utils/api';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import { useAuth } from "../../context/AuthContext.jsx";
import 'jspdf-autotable';
import jsPDF from 'jspdf';
import autoTable from "jspdf-autotable";

// HIER DEN LOCALE-IMPORT HINZUFÜGEN
import { de } from 'date-fns/locale';

import {
    getMondayOfWeek,
    addDays,
    formatLocalDate,
    formatDate,
    minutesToHHMM,
    formatTime
} from './hourDashUtils';

import HourlyWeekOverview from './HourlyWeekOverview';
import HourlyVacationSection from './HourlyVacationSection';
import HourlyCorrectionsPanel from './HourlyCorrectionsPanel';
import HourlyCorrectionModal from './HourlyCorrectionModal';
import PrintReportModal from '../../components/PrintReportModal.jsx';

import '../../styles/HourlyDashboardScoped.css';

const HourlyDashboard = () => {
    const { t } = useTranslation();
    const { notify } = useNotification();
    // KORREKTUR: Wir benötigen die 'punch'-Funktion aus dem AuthContext nicht.
    const { currentUser, fetchCurrentUser } = useAuth();

    const [userProfile, setUserProfile] = useState(null);
    const [dailySummaries, setDailySummaries] = useState([]);
    const [customers, setCustomers] = useState([]);
    const [recentCustomers, setRecentCustomers] = useState([]);
    const [projects, setProjects] = useState([]);
    const [selectedCustomerId, setSelectedCustomerId] = useState('');
    const [selectedProjectId, setSelectedProjectId] = useState('');
    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));
    const [vacationRequests, setVacationRequests] = useState([]);
    const [correctionRequests, setCorrectionRequests] = useState([]);
    const [showCorrectionsPanel, setShowCorrectionsPanel] = useState(false);
    const [selectedCorrectionMonday, setSelectedCorrectionMonday] = useState(getMondayOfWeek(new Date()));
    const [showAllCorrections, setShowAllCorrections] = useState(false);
    const [printModalVisible, setPrintModalVisible] = useState(false);
    const [printStartDate, setPrintStartDate] = useState(new Date());
    const [printEndDate, setPrintEndDate] = useState(new Date());
    const [showCorrectionModal, setShowCorrectionModal] = useState(false);
    const [correctionDate, setCorrectionDate] = useState(null);
    const [dailySummaryForCorrection, setDailySummaryForCorrection] = useState(null);
    const [punchMessage, setPunchMessage] = useState('');
    const [monthlyTotalMins, setMonthlyTotalMins] = useState(0);

    // KORREKTE IMPLEMENTIERUNG: Die Funktion ruft die API direkt auf.
    const handleManualPunch = async () => {
        if (!currentUser?.username) {
            notify(t('userNotLoggedIn', 'Benutzer nicht angemeldet.'), 'error');
            return;
        }
        try {
            const params = { username: currentUser.username, source: 'MANUAL_PUNCH' };
            if (selectedCustomerId) params.customerId = selectedCustomerId;
            if (selectedProjectId) params.projectId = selectedProjectId;
            const response = await api.post('/api/timetracking/punch', null, { params });
            const newEntry = response.data;
            setPunchMessage(`${t("manualPunchMessage", "Erfolgreich gestempelt")} ${currentUser.username} (${t('punchTypes.' + newEntry.punchType, newEntry.punchType)} @ ${formatTime(new Date(newEntry.entryTimestamp))})`);
            setTimeout(() => setPunchMessage(''), 3000);
            fetchWeeklyData(selectedMonday);
            fetchDataForUser();
        } catch (error) {
            console.error('Punch Error:', error);
            notify(error.message || t('punchError', 'Fehler beim Stempeln'), 'error');
        }
    };

const assignCustomerForDay = async (isoDate, customerId) => {
        try {
            await api.put('/api/timetracking/day/customer', null, { params: { username: currentUser.username, date: isoDate, customerId: customerId || '' } });
            fetchWeeklyData(selectedMonday);
            notify(t('customerSaved'), 'success');
        } catch (err) {
            console.error('Error saving customer', err);
            notify(t('customerSaveError'), 'error');
        }
    };

    const assignProjectForDay = async (isoDate, projectId) => {
        try {
            await api.put('/api/timetracking/day/project', null, { params: { username: currentUser.username, date: isoDate, projectId: projectId || '' } });
            fetchWeeklyData(selectedMonday);
            notify(t('customerSaved'), 'success');
        } catch (err) {
            console.error('Error saving project', err);
            notify(t('customerSaveError'), 'error');
        }
    };

    const fetchDataForUser = useCallback(async () => {
        if (!currentUser?.username) return;
        try {
            const profileResponse = await api.get(`/api/users/profile/${currentUser.username}`);
            setUserProfile(profileResponse.data);
            if (profileResponse.data.lastCustomerId && !selectedCustomerId) {
                setSelectedCustomerId(String(profileResponse.data.lastCustomerId));
            }
            const vacationResponse = await api.get(`/api/vacation/user/${currentUser.username}`);
            setVacationRequests(vacationResponse.data || []);
        } catch (error) {
            console.error("Fehler beim Laden der Benutzerdaten:", error);
            notify(t('errors.fetchUserData', 'Fehler beim Laden der Benutzerdaten.'), 'error');
        }
    }, [currentUser, notify, t]);

    const fetchWeeklyData = useCallback(async (monday) => {
        if (!currentUser?.username) return;
        try {
            const startDate = formatLocalDate(monday);
            const endDate = formatLocalDate(addDays(monday, 6));
            const response = await api.get(`/api/dashboard/user/${currentUser.username}/week`, {
                params: { startDate, endDate }
            });
            setDailySummaries(response.data.dailySummaries || []);
        } catch (error) {
            console.error("Fehler beim Abrufen der wöchentlichen Daten:", error);
            notify(t('errors.fetchWeeklyData', 'Fehler beim Abrufen der wöchentlichen Daten.'), 'error');
            setDailySummaries([]);
        }
    }, [currentUser, notify, t]);

    const fetchCorrectionRequests = useCallback(async () => {
        if (!currentUser || !currentUser.username) return;
        try {
            const response = await api.get(`/api/correction/user/${currentUser.username}`);
            setCorrectionRequests(response.data || []);
        } catch (error) {
            console.error("Fehler beim Abrufen der Korrekturanträge:", error);
            notify(t('userManagement.errorLoadingCorrections'), 'error');
        }
    }, [currentUser, notify]);

    useEffect(() => {
        fetchCurrentUser();
    }, [fetchCurrentUser]);

    useEffect(() => {
        if (currentUser?.company?.customerTrackingEnabled) {
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
    }, [currentUser]);

    useEffect(() => {
        if (currentUser) {
            fetchDataForUser();
            fetchWeeklyData(selectedMonday);
            fetchCorrectionRequests();
        }
    }, [currentUser, selectedMonday, fetchDataForUser, fetchWeeklyData, fetchCorrectionRequests]);

    useEffect(() => {
        if (!dailySummaries || dailySummaries.length === 0) {
            setMonthlyTotalMins(0);
            return;
        }
        const currentMonth = selectedMonday.getMonth();
        const currentYear = selectedMonday.getFullYear();
        const monthlyMinutes = dailySummaries
            .filter(s => {
                const summaryDate = new Date(s.date);
                return summaryDate.getMonth() === currentMonth && summaryDate.getFullYear() === currentYear;
            })
            .reduce((acc, curr) => acc + (curr.workedMinutes || 0), 0);
        setMonthlyTotalMins(monthlyMinutes);

    }, [dailySummaries, selectedMonday]);

    const handleOpenCorrectionModal = (date, summary) => {
        setCorrectionDate(formatLocalDate(date));
        setDailySummaryForCorrection(summary);
        setShowCorrectionModal(true);
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
            fetchCorrectionRequests();
        } catch (error) {
            console.error('Fehler beim Absenden der Korrekturanträge:', error);
            const errorMsg = error.response?.data?.message || 'Ein oder mehrere Anträge konnten nicht gesendet werden.';
            notify(errorMsg, 'error');
        }
    };

    const handlePrintReport = async () => {
        if (!currentUser) return;
        try {
            const response = await api.get(`/api/reports/user/${currentUser.username}`, {
                params: {
                    startDate: formatLocalDate(printStartDate),
                    endDate: formatLocalDate(printEndDate),
                },
            });
            const { reportData, totalWork, totalPause, totalOvertime, userName } = response.data;
            const doc = new jsPDF();
            doc.text(`${t("timeReportFor")} ${userName}`, 14, 15);
            doc.text(`${t("period")}: ${formatDate(printStartDate)} - ${formatDate(printEndDate)}`, 14, 22);

            autoTable(doc, {
                startY: 30,
                head: [[t("date"), t("start"), t("end"), t("worked"), t("pause"), t("overtime")]],
                body: reportData.map(d => [
                    formatDate(d.date), d.startTime, d.endTime,
                    minutesToHHMM(d.workedMinutes), minutesToHHMM(d.pauseMinutes), minutesToHHMM(d.overtimeMinutes)
                ]),
                foot: [[
                    t("total"), "", "",
                    minutesToHHMM(totalWork), minutesToHHMM(totalPause), minutesToHHMM(totalOvertime)
                ]],
                showFoot: 'last_page'
            });
            doc.save(`Zeiterfassung_${userName}_${formatLocalDate(printStartDate)}-${formatLocalDate(printEndDate)}.pdf`);
        } catch (error) {
            console.error("Fehler beim Erstellen des Reports:", error);
            notify(t('errors.reportError'), "error");
        }
        setPrintModalVisible(false);
    };

    const weeklyTotalMins = dailySummaries.reduce((acc, curr) => acc + (curr.workedMinutes || 0), 0);

    if (!currentUser || !userProfile) {
        return <div>Wird geladen...</div>;
    }

    return (
        <div className="hourly-dashboard scoped-dashboard">
            <Navbar />
            <header className="dashboard-header">
                <h1>{t('welcome')}, {currentUser.firstName || currentUser.username}</h1>
                <button onClick={() => setPrintModalVisible(true)} className="button-primary">
                    {t('printReportButton')}
                </button>
            </header>

            <HourlyWeekOverview
                t={t}
                dailySummaries={dailySummaries}
                selectedMonday={selectedMonday}
                setSelectedMonday={setSelectedMonday}
                openCorrectionModal={handleOpenCorrectionModal}
                weeklyTotalMins={weeklyTotalMins}
                monthlyTotalMins={monthlyTotalMins}
                handleManualPunch={handleManualPunch}
                punchMessage={punchMessage}
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