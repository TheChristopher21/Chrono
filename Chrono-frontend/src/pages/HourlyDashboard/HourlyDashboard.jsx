// src/pages/HourlyDashboard/HourlyDashboard.jsx
import { useState, useEffect, useCallback } from 'react';
import Navbar from '../../components/Navbar';
import api from '../../utils/api';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import { useAuth } from "../../context/AuthContext.jsx";
import 'jspdf-autotable';
import jsPDF from 'jspdf';
import autoTable from "jspdf-autotable";

import {
    getMondayOfWeek,
    addDays,
    formatLocalDate,
    formatDate,
    minutesToHHMM,
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
    // KORREKTUR: currentUser wird jetzt korrekt aus dem Auth-Kontext geholt
    const { currentUser, fetchCurrentUser } = useAuth();

    const [userProfile, setUserProfile] = useState(null);
    const [dailySummaries, setDailySummaries] = useState([]);
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

    const fetchDataForUser = useCallback(async () => {
        if (!currentUser?.username) return;
        try {
            const profileResponse = await api.get(`/api/users/profile/${currentUser.username}`);
            setUserProfile(profileResponse.data);
            const vacationResponse = await api.get(`/api/vacations/user/${currentUser.username}`);
            setVacationRequests(vacationResponse.data);
        } catch (error) {
            console.error("Fehler beim Laden der Benutzerdaten:", error);
            notify(t('errors.fetchUserData'), 'error');
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
            setDailySummaries(response.data.dailySummaries);
        } catch (error) {
            console.error("Fehler beim Abrufen der wöchentlichen Daten:", error);
            notify(t('errors.fetchWeeklyData'), 'error');
        }
    }, [currentUser, notify, t]);

    const fetchCorrectionRequests = useCallback(async () => {
        if (!currentUser || !currentUser.username) return;
        try {
            const response = await api.get(`/api/correction/user/${currentUser.username}`);
            setCorrectionRequests(response.data);
        } catch (error) {
            console.error("Fehler beim Abrufen der Korrekturanträge:", error);
            notify("Korrekturanträge konnten nicht geladen werden.", 'error');
        }
    }, [currentUser, notify]);

    useEffect(() => {
        fetchCurrentUser();
    }, [fetchCurrentUser]);

    useEffect(() => {
        if (currentUser) {
            fetchDataForUser();
            fetchWeeklyData(selectedMonday);
            fetchCorrectionRequests();
        }
    }, [currentUser, selectedMonday, fetchDataForUser, fetchWeeklyData, fetchCorrectionRequests]);

    const handleOpenCorrectionModal = (date, summary) => {
        setCorrectionDate(date);
        setDailySummaryForCorrection(summary);
        setShowCorrectionModal(true);
    };

    const handleCorrectionSubmit = async (entries, reason) => {
        if (!correctionDate || !entries || entries.length === 0) {
            notify('Bitte fügen Sie mindestens einen Korrektureintrag hinzu.', 'error');
            return;
        }
        if (!currentUser || !currentUser.username) {
            notify('Benutzer nicht gefunden, bitte neu anmelden.', 'error');
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