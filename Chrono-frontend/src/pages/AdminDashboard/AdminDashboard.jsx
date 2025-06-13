import  { useState, useEffect, useCallback } from 'react';
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

import {
    getMondayOfWeek,
    formatLocalDateYMD,
    addDays,
    minutesToHHMM,
    formatDate, // utils formatDate
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
            notify(`Antrag #${id} genehmigt.`, "success");
            handleDataReloadNeeded();
        } catch (error) {
            console.error(`Fehler beim Genehmigen von Antrag #${id}:`, error);
            notify(`Fehler bei Antrag #${id}.`, "error");
        }
    };

    const handleDenyCorrection = async (id, comment) => {
        try {
            await api.post(`/api/correction/deny/${id}`, null, { params: { comment } });
            notify(`Antrag #${id} abgelehnt.`, "success");
            handleDataReloadNeeded();
        } catch (error) {
            console.error(`Fehler beim Ablehnen von Antrag #${id}:`, error);
            notify(`Fehler bei Antrag #${id}.`, "error");
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

    async function handlePrintUserTimesPeriodSubmit() {
        const userSummariesForPrint = dailySummaries.filter(summary => {
            return (
                summary.username === printUser &&
                summary.date >= printUserStartDate &&
                summary.date <= printUserEndDate
            );
        });

        const sortedSummaries = userSummariesForPrint.sort(
            (a,b) => new Date(a.date) - new Date(b.date)
        );

        const doc = new jsPDF("p", "mm", "a4");
        const userDetails = users.find(u => u.username === printUser);
        const userNameDisplay = userDetails ? `${userDetails.firstName} ${userDetails.lastName} (${printUser})` : printUser;

        doc.setFontSize(14);
        doc.text(`Zeitenbericht für ${userNameDisplay}`, 14, 15);
        doc.setFontSize(11);
        doc.text(`Zeitraum: ${formatDate(new Date(printUserStartDate + "T00:00:00Z"))} – ${formatDate(new Date(printUserEndDate + "T00:00:00Z"))}`, 14, 22);

        const tableBody = sortedSummaries.map(summary => {
            const displayDate = formatDate(new Date(summary.date + "T00:00:00Z"));
            const primary = summary.primaryTimes;
            const workStart  = primary.firstStartTime ? primary.firstStartTime.substring(0,5) : "-";
            const workEnd    = primary.lastEndTime ? primary.lastEndTime.substring(0,5) : (primary.isOpen ? "OFFEN" : "-");

            let breakTimes = "-";
            if (summary.breakMinutes > 0) {
                breakTimes = minutesToHHMM(summary.breakMinutes);
            }
            const totalStr  = summary.workedMinutes > 0 ? minutesToHHMM(summary.workedMinutes) : "-";

            const stamps = summary.entries.map(e => `${e.punchType.substring(0,1)}:${e.entryTimestamp.substring(11,16)}${e.source === 'SYSTEM_AUTO_END' && !e.correctedByUser ? '(A)' : ''}`).join(' ');

            return [displayDate, workStart, breakTimes, workEnd, totalStr, stamps, summary.dailyNote || ""];
        });

        autoTable(doc, {
            head: [["Datum", "Start", "Pause", "Ende", "Arbeit", "Stempelungen", "Notiz"]],
            body: tableBody,
            startY: 30,
            margin: { left: 14, right: 14 },
            styles: { fontSize: 8, cellPadding: 1.5 },
            headStyles: { fillColor: [71, 91, 255], textColor: 255, fontStyle: "bold", halign: "center" },
            bodyStyles: { halign: "center" },
            alternateRowStyles: { fillColor: [240, 242, 254] },
            didDrawPage: (data) => {
                doc.setFontSize(8);
                doc.text(`Seite ${data.pageNumber} von ${doc.getNumberOfPages()}`, doc.internal.pageSize.getWidth() - 14, doc.internal.pageSize.getHeight() - 10, { align: "right" });
            }
        });

        doc.save(`Zeiten_${printUser}_${printUserStartDate}_bis_${printUserEndDate}.pdf`);
        setPrintUserModalVisible(false);
    }

    return (
        <div className="admin-dashboard scoped-dashboard">
            <Navbar />
            <header className="dashboard-header">
                <h2>{t('adminDashboard.titleWeekly')}</h2>
                {currentUser && ( <p>{t('adminDashboard.loggedInAs')} {currentUser.username}</p> )}
            </header>
            <div className="admin-action-buttons-container">
                <Link to="/admin/import-timetracking" className="admin-action-button button-primary">
                    {t('adminDashboard.importTimeTrackingButton', 'Zeiten importieren')}
                </Link>
                <button onClick={handleDataReloadNeeded} className="admin-action-button button-secondary">
                    {t('adminDashboard.reloadDataButton', 'Daten neu laden')}
                </button>
            </div>
            <div className="dashboard-conten">
                <div className="left-column lg:col-span-2 flex flex-col gap-4">
                    <AdminWeekSection
                        t={t}
                        weekDates={Array.from({ length: 7 }, (_, i) => addDays(selectedMonday, i))}
                        selectedMonday={selectedMonday}
                        handlePrevWeek={handlePrevWeek}
                        handleNextWeek={handleNextWeek}
                        handleWeekJump={handleWeekJump}
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
                    <AdminVacationRequests
                        t={t}
                        allVacations={allVacations}
                        handleApproveVacation={handleApproveVacation}
                        handleDenyVacation={handleDenyVacation}
                        onReloadVacations={handleDataReloadNeeded}
                    />
                    {/* AdminCorrectionsList moved here for full width */}
                    <AdminCorrectionsList
                        t={t}
                        allCorrections={allCorrections}
                        onApprove={handleApproveCorrection}
                        onDeny={handleDenyCorrection}
                    />
                </div>
                <div className="right-column lg:col-span-1 flex flex-col gap-4">
                    {/* Content for the right column can be added here if needed in future, or it can be removed if not used */}
                    {/* For now, it's empty as AdminCorrectionsList was moved */}
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
                users={users} // Pass users for break duration logic
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