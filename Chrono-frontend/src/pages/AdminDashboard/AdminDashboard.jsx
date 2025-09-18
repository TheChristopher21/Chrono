import { useState, useEffect, useCallback } from 'react';
import Navbar from '../../components/Navbar';
import { useAuth } from '../../context/AuthContext';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import api from '../../utils/api';
import { Link } from 'react-router-dom';
import '../../styles/AdminDashboardScoped.css';
import jsPDF from "jspdf";
import autoTable from "jspdf-autotable";

import AdminWeekSection from './AdminWeekSection';
import AdminVacationRequests from './AdminVacationRequests';
import AdminCorrectionsList from './AdminCorrectionsList';
import EditTimeModal from './EditTimeModal';
import PrintUserTimesModal from './PrintUserTimesModal';
import VacationCalendarAdmin from '../../components/VacationCalendarAdmin';
import AdminDashboardKpis from './AdminDashboardKpis';

import {
    getMondayOfWeek,
    formatLocalDateYMD,
    addDays,
    minutesToHHMM,
    formatDate, processEntriesForReport,
} from './adminDashboardUtils';

const AdminDashboard = () => {
    const { currentUser } = useAuth();
    const { notify } = useNotification();
    const { t } = useTranslation();

    const [dailySummaries, setDailySummaries] = useState([]);
    const [allVacations, setAllVacations] = useState([]);
    const [allCorrections, setAllCorrections] = useState([]);
    const [users, setUsers] = useState([]);
    const [allSickLeaves, setAllSickLeaves] = useState([]);
    const [holidaysByCanton, setHolidaysByCanton] = useState({});

    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));
    const [editModalVisible, setEditModalVisible] = useState(false);
    const [editDate, setEditDate] = useState(null);
    const [editTargetUsername, setEditTargetUsername] = useState('');
    const [editDayEntries, setEditDayEntries] = useState([]);

    const [printUserModalVisible, setPrintUserModalVisible] = useState(false);
    const [printUser, setPrintUser] = useState('');
    const [printUserStartDate, setPrintUserStartDate] = useState(formatLocalDateYMD(new Date()));
    const [printUserEndDate, setPrintUserEndDate] = useState(formatLocalDateYMD(new Date()));
    const [weeklyBalances, setWeeklyBalances] = useState([]);
    const defaultExpectedHours = 8.5;

    const fetchUsers = useCallback(async () => {
        try {
            const res = await api.get('/api/admin/users');
            setUsers(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error('Error loading users', err);
            notify(t('errors.fetchUsersError', 'Fehler beim Laden der Benutzer.'), 'error');
        }
    }, [notify, t]);

    const fetchAllDailySummaries = useCallback(async () => {
        try {
            const res = await api.get('/api/admin/timetracking/all-summaries');
            setDailySummaries(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error('Error loading daily summaries', err);
            setDailySummaries([]);
            notify(t('errors.fetchDailySummariesError', 'Fehler beim Laden der Tagesübersichten.'), 'error');
        }
    }, [notify, t]);

    const fetchAllVacations = useCallback(async () => {
        try {
            const res = await api.get('/api/vacation/all');
            setAllVacations(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error('Error loading vacation requests', err);
            setAllVacations([]);
            notify(t('errors.fetchVacationsError', 'Fehler beim Laden der Urlaubsanträge.'), 'error');
        }
    }, [notify, t]);

    const fetchAllCorrections = useCallback(async () => {
        try {
            const res = await api.get('/api/correction/all');
            setAllCorrections(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error('Error loading correction requests', err);
            setAllCorrections([]);
            notify(t('errors.fetchCorrectionsError', 'Fehler beim Laden der Korrekturanträge.'), 'error');
        }
    }, [notify, t]);

    const fetchTrackingBalances = useCallback(async () => {
        try {
            const res = await api.get('/api/admin/timetracking/admin/tracking-balances');
            setWeeklyBalances(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error("Fehler beim Laden der Tracking-Bilanzen:", err);
            setWeeklyBalances([]);
            notify(t('errors.fetchBalancesError', 'Fehler beim Laden der Salden.'), 'error');
        }
    }, [notify, t]);

    const fetchAllSickLeavesForAdmin = useCallback(async () => {
        try {
            const params = {};
            const res = await api.get('/api/sick-leave/company', { params });
            setAllSickLeaves(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error('Error loading sick leaves for admin dashboard', err);
            setAllSickLeaves([]);
            notify(t('errors.fetchSickLeaveErrorAdmin', 'Fehler beim Laden der Krankmeldungen für das Dashboard.'), 'error');
        }
    }, [notify, t]);

    const fetchHolidaysForAllRelevantCantons = useCallback(async () => {
        const currentYear = selectedMonday.getFullYear();
        const yearStartDate = `${currentYear}-01-01`;
        const yearEndDate = `${currentYear}-12-31`;

        const cantonsToFetch = new Set();
        if (currentUser?.companyCantonAbbreviation) {
            cantonsToFetch.add(currentUser.companyCantonAbbreviation);
        }
        users.forEach(user => {
            if (user.companyCantonAbbreviation) {
                cantonsToFetch.add(user.companyCantonAbbreviation);
            }
        });

        if (cantonsToFetch.size === 0) {
            cantonsToFetch.add('');
        }

        const newHolidaysByCantonState = { ...holidaysByCanton };
        let fetchOccurredForNewData = false;

        for (const canton of cantonsToFetch) {
            const cantonKey = canton || 'GENERAL';
            const yearFromState = holidaysByCanton[cantonKey]?.year;
            if (!newHolidaysByCantonState[cantonKey] || yearFromState !== currentYear) {
                try {
                    const params = { year: currentYear, cantonAbbreviation: canton, startDate: yearStartDate, endDate: yearEndDate };
                    const response = await api.get('/api/holidays/details', { params });
                    newHolidaysByCantonState[cantonKey] = { data: response.data || {}, year: currentYear };
                    fetchOccurredForNewData = true;
                } catch (error) {
                    console.error(t('errors.fetchHolidaysErrorForCanton', `Fehler beim Laden der Feiertage für Kanton ${cantonKey}:`), error);
                    if (!newHolidaysByCantonState[cantonKey]) {
                        newHolidaysByCantonState[cantonKey] = { data: {}, year: currentYear };
                    }
                }
            }
        }
        if (fetchOccurredForNewData) {
            setHolidaysByCanton(newHolidaysByCantonState);
        }
    }, [selectedMonday, currentUser, users, t, holidaysByCanton]);

    const handleDataReloadNeeded = useCallback(() => {
        fetchAllDailySummaries();
        fetchAllVacations();
        fetchAllCorrections();
        fetchAllSickLeavesForAdmin();
        fetchTrackingBalances();
        fetchUsers();
    }, [fetchAllDailySummaries, fetchAllVacations, fetchAllCorrections, fetchAllSickLeavesForAdmin, fetchTrackingBalances, fetchUsers]);


    useEffect(() => {
        fetchUsers();
    }, [fetchUsers]);

    useEffect(() => {
        if (currentUser) {
            handleDataReloadNeeded();
        }
    }, [currentUser, handleDataReloadNeeded]);

    useEffect(() => {
        if (currentUser) {
            fetchHolidaysForAllRelevantCantons();
        }
    }, [selectedMonday, users, currentUser, fetchHolidaysForAllRelevantCantons]);


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

    function handleCurrentWeek() {
        setSelectedMonday(getMondayOfWeek(new Date()));
    }

    async function handleApproveVacation(id) {
        try {
            await api.post(`/api/vacation/approve/${id}`);
            notify(t('adminDashboard.vacationApprovedMsg', 'Urlaub genehmigt.'), 'success');
            handleDataReloadNeeded();
        } catch (err) {
            console.error('Error approving vacation', err);
            notify(t('adminDashboard.vacationApproveErrorMsg', 'Fehler beim Genehmigen des Urlaubs: ') + (err.response?.data?.message || err.message), 'error');
        }
    }
    async function handleDenyVacation(id) {
        try {
            await api.post(`/api/vacation/deny/${id}`);
            notify(t('adminDashboard.vacationDeniedMsg', 'Urlaub abgelehnt.'), 'success');
            fetchAllVacations();
        } catch (err) {
            console.error('Error denying vacation', err);
            notify(t('adminDashboard.vacationDenyErrorMsg', 'Fehler beim Ablehnen des Urlaubs: ') + (err.response?.data?.message || err.message), 'error');
        }
    }

    const handleApproveCorrection = async (id, comment) => {
        try {
            await api.post(`/api/correction/approve/${id}`, null, { params: { comment } });
            notify(`${t('adminDashboard.correctionApprovedMsg')} #${id}`, "success");
            handleDataReloadNeeded();
        } catch (error) {
            console.error(`Fehler beim Genehmigen von Antrag #${id}:`, error);
            notify(`${t('adminDashboard.correctionErrorMsg')} #${id}`, "error");
        }
    };

    const handleDenyCorrection = async (id, comment) => {
        try {
            await api.post(`/api/correction/deny/${id}`, null, { params: { comment } });
            notify(`${t('adminDashboard.correctionDeniedMsg')} #${id}`, "success");
            handleDataReloadNeeded();
        } catch (error) {
            console.error(`Fehler beim Ablehnen von Antrag #${id}:`, error);
            notify(`${t('adminDashboard.correctionErrorMsg')} #${id}`, "error");
        }
    };

    function openEditModal(targetUsername, dateObj, dailySummaryForDay) {
        setEditTargetUsername(targetUsername);
        setEditDate(dateObj);
        setEditDayEntries(dailySummaryForDay ? dailySummaryForDay.entries || [] : []);
        setEditModalVisible(true);
    }

    function openNewEntryModal(targetUsername, dateObj) {
        setEditTargetUsername(targetUsername);
        setEditDate(dateObj);
        setEditDayEntries([]);
        setEditModalVisible(true);
    }

    async function handleEditSubmit(updatedEntriesForDay) {
        if (!editDate || !editTargetUsername) {
            notify(t('adminDashboard.noValidDateOrUser', "Kein gültiges Datum oder Benutzer ausgewählt."), 'error');
            return;
        }
        const formattedDate = formatLocalDateYMD(editDate);

        try {
            await api.put(`/api/admin/timetracking/editDay/${editTargetUsername}/${formattedDate}`, updatedEntriesForDay);
            setEditModalVisible(false);
            notify(t('adminDashboard.editSuccessfulMsg', 'Zeiten erfolgreich bearbeitet.'), 'success');
            handleDataReloadNeeded();
        } catch (err) {
            console.error('Edit failed', err);
            const errorMsg = err.response?.data?.message || err.response?.data || err.message || t('errors.unknownError');
            notify(t('adminDashboard.editFailed', "Fehler beim Bearbeiten") + ': ' + errorMsg, 'error');
        }
    }

    const focusWeekForProblem = useCallback((dateForWeek) => {
        if (dateForWeek && !isNaN(new Date(dateForWeek).getTime())) {
            setSelectedMonday(getMondayOfWeek(new Date(dateForWeek)));
        }
    }, []);

    function openPrintUserModal(username) {
        setPrintUser(username);
        const now = new Date();
        const firstDayOfMonth = formatLocalDateYMD(new Date(now.getFullYear(), now.getMonth(), 1));
        const lastDayOfMonth = formatLocalDateYMD(new Date(now.getFullYear(), now.getMonth() + 1, 0));
        setPrintUserStartDate(firstDayOfMonth);
        setPrintUserEndDate(lastDayOfMonth);
        setPrintUserModalVisible(true);
    }
    const calculateCardHeight = (doc, dayData, width) => {
        let height = 25; // Header + initial padding
        let leftHeight = 30;
        let rightHeight = 30;

        if(dayData.note) {
            leftHeight += doc.splitTextToSize(dayData.note, (width/2.5) - 10).length * 5 + 10;
        }

        dayData.blocks.work.forEach(block => {
            const text = `${block.description ? `${block.description}:` : ''} ${block.start} - ${block.end} (${block.duration})`;
            rightHeight += doc.splitTextToSize(text, width - (width/2.5) - 5).length * 5 + 2;
        });
        height += Math.max(leftHeight, rightHeight);
        return height;
    };
    async function handlePrintUserTimesPeriodSubmit() {
        if (!printUser || !printUserStartDate || !printUserEndDate) return;

        const userSummariesForPrint = dailySummaries.filter(summary =>
            summary.username === printUser &&
            summary.date >= printUserStartDate &&
            summary.date <= printUserEndDate
        );

        const sortedSummaries = userSummariesForPrint.sort(
            (a, b) => new Date(a.date) - new Date(b.date)
        );

        const userDetails = users.find(u => u.username === printUser);
        const userNameDisplay = userDetails ? `${userDetails.firstName} ${userDetails.lastName} (${printUser})` : printUser;
        const balanceRecord = weeklyBalances.find(b => b.username === printUser);
        const overtimeStr = minutesToHHMM(balanceRecord?.trackingBalance || 0);

        const doc = new jsPDF("p", "mm", "a4");
        const pageMargin = 15;
        const pageWidth = doc.internal.pageSize.getWidth();
        const pageHeight = doc.internal.pageSize.getHeight();
        const contentWidth = pageWidth - (2 * pageMargin);
        let yPos = 20;

        doc.setFontSize(22);
        doc.setFont("helvetica", "bold");
        doc.text("Zeitenbericht", pageWidth / 2, yPos, { align: "center" });
        yPos += 10;

        doc.setFontSize(12);
        doc.setFont("helvetica", "normal");
        doc.text(`für ${userNameDisplay}`, pageWidth / 2, yPos, { align: "center" });
        yPos += 6;
        doc.text(`Zeitraum: ${formatDate(new Date(printUserStartDate))} - ${formatDate(new Date(printUserEndDate))}`, pageWidth / 2, yPos, { align: "center" });
        yPos += 6;
        doc.text(`${t('overtimeBalance', 'Überstundensaldo')}: ${overtimeStr}`, pageWidth / 2, yPos, { align: "center" });
        yPos += 15;

        const totalWork = sortedSummaries.reduce((sum, day) => sum + day.workedMinutes, 0);
        const totalPause = sortedSummaries.reduce((sum, day) => sum + day.breakMinutes, 0);

        doc.setFillColor(248, 249, 250);
        doc.rect(pageMargin, yPos, contentWidth, 25, 'F');
        const summaryTextY = yPos + 15;
        const summaryCol1 = pageMargin + contentWidth / 4;
        const summaryCol2 = pageMargin + (contentWidth / 4) * 3;

        doc.setFontSize(10);
        doc.setTextColor(108, 117, 125);
        doc.text("Gesamte Arbeitszeit", summaryCol1, summaryTextY - 8, { align: 'center' });
        doc.text("Gesamte Pausenzeit", summaryCol2, summaryTextY - 8, { align: 'center' });

        doc.setFontSize(16);
        doc.setFont("helvetica", "bold");
        doc.setTextColor(41, 128, 185);
        doc.text(minutesToHHMM(totalWork), summaryCol1, summaryTextY, { align: 'center' });
        doc.setTextColor(44, 62, 80);
        doc.text(minutesToHHMM(totalPause), summaryCol2, summaryTextY, { align: 'center' });
        yPos += 35;

        sortedSummaries.forEach(day => {
            const dayData = {
                date: day.date,
                workedMinutes: day.workedMinutes,
                breakMinutes: day.breakMinutes,
                blocks: processEntriesForReport(day.entries),
                note: day.dailyNote || ""
            };

            const cardHeight = calculateCardHeight(doc, dayData, contentWidth);

            if (yPos + cardHeight > pageHeight - pageMargin) {
                doc.addPage();
                yPos = 20;
            }

            const cardStartY = yPos;
            doc.setFillColor(236, 240, 241);
            doc.roundedRect(pageMargin, yPos, contentWidth, 10, 3, 3, 'F');
            doc.setFontSize(14);
            doc.setFont("helvetica", "bold");
            doc.setTextColor(44, 62, 80);
            doc.text(formatDate(new Date(dayData.date)), pageMargin + 5, yPos + 7);
            yPos += 10;

            const bodyYStart = yPos;
            let leftColY = bodyYStart + 10;
            let rightColY = bodyYStart + 10;
            const rightColX = pageMargin + contentWidth / 2.5;

            doc.setFontSize(11);
            doc.setFont("helvetica", "bold");
            doc.text("Übersicht", pageMargin + 5, leftColY);
            leftColY += 7;

            doc.setFontSize(10);
            doc.setFont("helvetica", "normal");
            doc.text(`Gearbeitet: ${minutesToHHMM(dayData.workedMinutes)}`, pageMargin + 5, leftColY);
            leftColY += 6;
            doc.text(`Pause: ${minutesToHHMM(dayData.breakMinutes)}`, pageMargin + 5, leftColY);
            leftColY += 10;

            if (dayData.note) {
                doc.setFont("helvetica", "bold");
                doc.text("Notiz:", pageMargin + 5, leftColY);
                leftColY += 6;
                doc.setFont("helvetica", "italic");
                const noteLines = doc.splitTextToSize(dayData.note, (contentWidth / 2.5) - 10);
                doc.text(noteLines, pageMargin + 5, leftColY);
                leftColY += noteLines.length * 5;
            }

            doc.setFontSize(11);
            doc.setFont("helvetica", "bold");
            doc.text("Arbeitsblöcke", rightColX, rightColY);
            rightColY += 7;

            doc.setFontSize(10);
            doc.setFont("helvetica", "normal");
            dayData.blocks.work.forEach(block => {
                const text = `${block.description ? `${block.description}:` : 'Arbeit:'} ${block.start} - ${block.end} (${block.duration})`;
                const textLines = doc.splitTextToSize(text, contentWidth - rightColX - 5);
                doc.text(textLines, rightColX, rightColY);
                rightColY += textLines.length * 5 + 2;
            });

            const cardEndY = Math.max(leftColY, rightColY) + 5;
            doc.setDrawColor(222, 226, 230);
            doc.roundedRect(pageMargin, cardStartY, contentWidth, cardEndY - cardStartY, 3, 3, 'S');

            yPos = cardEndY + 10;
        });

        doc.save(`Zeitenbericht_${printUser}_${printUserStartDate}_bis_${printUserEndDate}.pdf`);
        setPrintUserModalVisible(false);
    }

    return (
        <div className="admin-dashboard scoped-dashboard">
            <Navbar />
            <header className="dashboard-header">
                <h2>{t('adminDashboard.titleWeekly')}</h2>
                {currentUser && ( <p>{t('adminDashboard.loggedInAs')} {currentUser.username}</p> )}
            </header>

            <AdminDashboardKpis
                t={t}
                allVacations={allVacations}
                allCorrections={allCorrections}
                weeklyBalances={weeklyBalances}
                users={users}
            />

            <div className="admin-action-buttons-container">
                <Link to="/admin/import-times" className="admin-action-button button-primary">
                    {t('adminDashboard.importTimeTrackingButton', 'Zeiten importieren')}
                </Link>
                <Link to="/admin/payslips" className="admin-action-button button-primary">
                    {t('navbar.payslips', 'Abrechnungen')}
                </Link>
                <Link to="/admin/analytics" className="admin-action-button button-secondary admin-analytics-button">
                    {t('adminDashboard.analyticsButton', 'Analytics anzeigen')}
                </Link>
                <button onClick={handleDataReloadNeeded} className="admin-action-button button-secondary">
                    {t('adminDashboard.reloadDataButton', 'Daten neu laden')}
                </button>
            </div>

            <div className="dashboard-layout">
                <div className="main-content">
                    <AdminWeekSection
                        t={t}
                        weekDates={Array.from({ length: 7 }, (_, i) => addDays(selectedMonday, i))}
                        selectedMonday={selectedMonday}
                        handlePrevWeek={handlePrevWeek}
                        handleNextWeek={handleNextWeek}
                        handleWeekJump={handleWeekJump}
                        handleCurrentWeek={handleCurrentWeek}
                        onFocusProblemWeek={focusWeekForProblem}
                        dailySummariesForWeekSection={dailySummaries}
                        allVacations={allVacations}
                        allSickLeaves={allSickLeaves}
                        allHolidays={holidaysByCanton}
                        users={users}
                        defaultExpectedHours={defaultExpectedHours}
                        openEditModal={openEditModal}
                        openPrintUserModal={openPrintUserModal}
                        rawUserTrackingBalances={weeklyBalances}
                        openNewEntryModal={openNewEntryModal}
                        onDataReloadNeeded={handleDataReloadNeeded}
                    />
                </div>
                <div className="right-column">
                    <AdminVacationRequests
                        t={t}
                        allVacations={allVacations}
                        handleApproveVacation={handleApproveVacation}
                        handleDenyVacation={handleDenyVacation}
                        onReloadVacations={handleDataReloadNeeded}
                    />
                    <AdminCorrectionsList
                        t={t}
                        allCorrections={allCorrections}
                        onApprove={handleApproveCorrection}
                        onDeny={handleDenyCorrection}
                    />
                </div>
            </div>

            <div className="mt-8">
                <h4>{t('adminDashboard.vacationCalendarTitle')}</h4>
                <VacationCalendarAdmin
                    vacationRequests={allVacations.filter(v => v.approved)}
                    onReloadVacations={handleDataReloadNeeded}
                    users={users}
                />
            </div>
            <EditTimeModal
                t={t}
                isVisible={editModalVisible}
                targetDate={editDate}
                dayEntries={editDayEntries}
                targetUsername={editTargetUsername}
                onSubmit={handleEditSubmit}
                onClose={() => setEditModalVisible(false)}
                users={users}
            />
            <PrintUserTimesModal
                printUserModalVisible={printUserModalVisible}
                printUser={printUser}
                printUserStartDate={printUserStartDate}
                printUserEndDate={printUserEndDate}
                setPrintUserStartDate={setPrintUserStartDate}
                setPrintUserEndDate={setPrintUserEndDate}
                handlePrintUserTimesPeriodSubmit={handlePrintUserTimesPeriodSubmit}
                setPrintUserModalVisible={setPrintUserModalVisible}
                t={t}
            />
        </div>
    );
};

export default AdminDashboard;