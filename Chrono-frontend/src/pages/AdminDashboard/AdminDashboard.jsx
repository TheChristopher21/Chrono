// AdminDashboard.jsx
import React, { useState, useEffect, useCallback } from 'react';
import Navbar from '../../components/Navbar';
import { useAuth } from '../../context/AuthContext';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import api from '../../utils/api';
import { Link } from 'react-router-dom';
import '../../styles/AdminDashboardScoped.css';
import jsPDF from "jspdf";
import "jspdf-autotable";

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
    computeDayTotalMinutes
} from './adminDashboardUtils';

import { formatDate as formatDateExternal } from "date-fns";
import autoTable from "jspdf-autotable";


const AdminDashboard = () => {
    const { currentUser } = useAuth();
    const { notify } = useNotification();
    const { t } = useTranslation();

    const [allTracks, setAllTracks] = useState([]);
    const [allVacations, setAllVacations] = useState([]);
    const [allCorrections, setAllCorrections] = useState([]);
    const [users, setUsers] = useState([]);
    const [allSickLeaves, setAllSickLeaves] = useState([]);
    const [holidaysByCanton, setHolidaysByCanton] = useState({});


    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));
    const [editModalVisible, setEditModalVisible] = useState(false);
    const [editDate, setEditDate] = useState(null);
    const [editTargetUsername, setEditTargetUsername] = useState('');
    const [editData, setEditData] = useState({
        workStart: '', breakStart: '', breakEnd: '', workEnd: '', adminPassword: '', userPassword: ''
    });
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

    const fetchAllTracks = useCallback(async () => {
        try {
            const res = await api.get('/api/admin/timetracking/all');
            setAllTracks(res.data || []);
        } catch (err) {
            console.error('Error loading time entries', err);
            notify(t('errors.fetchTimeEntriesError', 'Fehler beim Laden der Zeiteinträge.'), 'error');
        }
    }, [notify, t]);

    const fetchAllVacations = useCallback(async () => {
        try {
            const res = await api.get('/api/vacation/all');
            setAllVacations(res.data || []);
        } catch (err) {
            console.error('Error loading vacation requests', err);
            notify(t('errors.fetchVacationsError', 'Fehler beim Laden der Urlaubsanträge.'), 'error');
        }
    }, [notify, t]);

    const fetchAllCorrections = useCallback(async () => {
        try {
            const res = await api.get('/api/correction/all');
            setAllCorrections(res.data || []);
        } catch (err) {
            console.error('Error loading correction requests', err);
            notify(t('errors.fetchCorrectionsError', 'Fehler beim Laden der Korrekturanträge.'), 'error');
        }
    }, [notify, t]);

    const fetchTrackingBalances = useCallback(async () => {
        if (users.length > 0) {
            try {
                const res = await api.get('/api/admin/timetracking/admin/tracking-balances');
                setWeeklyBalances(res.data || []);
            } catch (err) {
                console.error("Fehler beim Laden der Tracking-Bilanzen:", err);
                notify(t('errors.fetchBalancesError', 'Fehler beim Laden der Salden.'), 'error');
            }
        }
    }, [users, notify, t]);

    const fetchAllSickLeavesForAdmin = useCallback(async () => {
        try {
            const params = {};
            if (currentUser?.roles?.includes('ROLE_SUPERADMIN') && currentUser.companyId) {
                // params.companyId = currentUser.companyId;
            } else if (!currentUser?.roles?.includes('ROLE_SUPERADMIN') && currentUser?.companyId) {
                params.companyId = currentUser.companyId;
            }

            const res = await api.get('/api/sick-leave/company', { params });
            setAllSickLeaves(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error('Error loading sick leaves for admin dashboard', err);
            notify(t('errors.fetchSickLeaveErrorAdmin', 'Fehler beim Laden der Krankmeldungen für das Dashboard.'), 'error');
        }
    }, [currentUser, notify, t]);

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
            try {
                const params = { year: currentYear, cantonAbbreviation: canton, startDate: yearStartDate, endDate: yearEndDate };
                const response = await api.get('/api/holidays/details', { params });
                newHolidaysByCantonState[cantonKey] = response.data || {};
                fetchOccurredForNewData = true;
            } catch (error) {
                console.error(t('errors.fetchHolidaysErrorForCanton', `Fehler beim Laden der Feiertage für Kanton ${cantonKey}:`), error);
                if (!newHolidaysByCantonState[cantonKey]) {
                    newHolidaysByCantonState[cantonKey] = {};
                }
            }
        }
        if (fetchOccurredForNewData || Object.keys(newHolidaysByCantonState).length !== Object.keys(holidaysByCanton).length) {
            setHolidaysByCanton(newHolidaysByCantonState);
        }

    }, [selectedMonday, currentUser, users, t, holidaysByCanton]);

    const handleDataReloadNeeded = useCallback(() => {
        fetchAllTracks();
        fetchAllVacations();
        fetchAllCorrections();
        fetchAllSickLeavesForAdmin();
        fetchTrackingBalances();
    }, [fetchAllTracks, fetchAllVacations, fetchAllCorrections, fetchAllSickLeavesForAdmin, fetchTrackingBalances]);


    useEffect(() => {
        fetchUsers();
    }, [fetchUsers]);

    useEffect(() => {
        if (users.length > 0 || currentUser) {
            handleDataReloadNeeded();
        }
    }, [users, currentUser, handleDataReloadNeeded]);

    useEffect(() => {
        if (users.length > 0 || currentUser) {
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


    async function handleApproveCorrection(id, comment) {
        try {
            await api.post(`/api/correction/approve/${id}`, null, { params: { comment } });
            notify(t('adminDashboard.correctionApprovedMsg', 'Korrekturantrag genehmigt.'), 'success');
            handleDataReloadNeeded();
        } catch (err) {
            console.error('Error approving correction', err);
            notify(t('adminDashboard.correctionApproveErrorMsg', 'Fehler beim Genehmigen der Korrektur: ') + (err.response?.data?.message || err.message), 'error');
        }
    }
    async function handleDenyCorrection(id, comment) {
        try {
            await api.post(`/api/correction/deny/${id}`, null, { params: { comment } });
            notify(t('adminDashboard.correctionDeniedMsg', 'Korrekturantrag abgelehnt.'), 'success');
            fetchAllCorrections();
        } catch (err) {
            console.error('Error denying correction', err);
            notify(t('adminDashboard.correctionDenyErrorMsg', 'Fehler beim Ablehnen der Korrektur: ') + (err.response?.data?.message || err.message), 'error');
        }
    }

    function openEditModal(targetUsername, dateObj, entries) {
        const entry = entries[0] || {};
        setEditTargetUsername(targetUsername);
        setEditDate(dateObj);
        setEditData({
            workStart:   entry.workStart || '',
            breakStart:  entry.breakStart || '',
            breakEnd:    entry.breakEnd || '',
            workEnd:     entry.workEnd || '',
            adminPassword: '',
            userPassword: ''
        });
        setEditModalVisible(true);
    }

    function openNewEntryModal(targetUsername, dateObj) {
        setEditTargetUsername(targetUsername);
        setEditDate(dateObj);
        setEditData({
            workStart:   '', breakStart:  '', breakEnd:    '', workEnd:     '', adminPassword: '', userPassword: ''
        });
        setEditModalVisible(true);
    }

    async function handleEditSubmit(e) {
        e.preventDefault();
        if (!editDate) {
            notify(t('adminDashboard.noValidDate', "Kein gültiges Datum ausgewählt."), 'error');
            return;
        }
        const formattedDate = formatLocalDateYMD(editDate);

        const params = {
            targetUsername: editTargetUsername,
            date:           formattedDate,
            workStart:      editData.workStart || null,
            breakStart:     editData.breakStart || null,
            breakEnd:       editData.breakEnd || null,
            workEnd:        editData.workEnd || null,
            adminUsername:  currentUser.username,
        };
        try {
            await api.put('/api/admin/timetracking/editDay', null, { params });
            setEditModalVisible(false);
            notify(t('adminDashboard.editSuccessfulMsg', 'Zeiten erfolgreich bearbeitet.'), 'success');
            handleDataReloadNeeded();
        } catch (err) {
            console.error('Edit failed', err);
            notify(t('adminDashboard.editFailed', "Fehler beim Bearbeiten") + ': ' + (err.response?.data?.message || err.message), 'error');
        }
    }

    const focusWeekForProblem = useCallback((dateForWeek) => {
        if (dateForWeek && !isNaN(new Date(dateForWeek).getTime())) {
            setSelectedMonday(getMondayOfWeek(new Date(dateForWeek)));
        }
    }, []);

    function handleEditInputChange(e) {
        const { name, value } = e.target;
        setEditData(prev => ({ ...prev, [name]: value }));
    }


    function openPrintUserModal(username) {
        setPrintUser(username);
        const nowStr = formatLocalDateYMD(new Date());
        setPrintUserStartDate(nowStr);
        setPrintUserEndDate(nowStr);
        setPrintUserModalVisible(true);
    }

    async function handlePrintUserTimesPeriodSubmit() {
        const userTracksForPrint = allTracks.filter(e => {
            const entryDateStr = e.dailyDate;
            if (!entryDateStr) return false;
            return (
                e.username === printUser &&
                entryDateStr >= printUserStartDate &&
                entryDateStr <= printUserEndDate
            );
        });

        const sortedEntries = userTracksForPrint.sort(
            (a,b) => new Date(a.dailyDate) - new Date(b.dailyDate)
        );

        const doc = new jsPDF("p", "mm", "a4");
        doc.setFontSize(14);
        doc.text(`Zeitenbericht für ${printUser}`, 14, 15);
        doc.setFontSize(11);
        doc.text(`Zeitraum: ${formatDateExternal(new Date(printUserStartDate + "T00:00:00"), 'dd.MM.yyyy')} – ${formatDateExternal(new Date(printUserEndDate + "T00:00:00"), 'dd.MM.yyyy')}`, 14, 22);

        const tableBody = sortedEntries.map(dayData => {
            const displayDate = formatDateExternal(new Date(dayData.dailyDate + "T00:00:00"), 'dd.MM.yyyy');
            const workStart  = dayData.workStart  ? dayData.workStart.slice(0,5)  : "-";
            const breakStart = dayData.breakStart ? dayData.breakStart.slice(0,5) : "-";
            const breakEnd   = dayData.breakEnd   ? dayData.breakEnd.slice(0,5)   : "-";
            const workEnd    = dayData.workEnd    ? dayData.workEnd.slice(0,5)    : "-";

            const totalMins = computeDayTotalMinutes(dayData);
            const totalStr  = totalMins > 0 ? minutesToHHMM(totalMins) : "-";

            return [displayDate, workStart, breakStart, breakEnd, workEnd, totalStr];
        });

        autoTable(doc, {
            head: [["Datum", "Arbeitsbeginn", "Pausenbeginn", "Pausenende", "Arbeitsende", "Gesamt"]],
            body: tableBody,
            startY: 30,
            margin: { left: 14, right: 14 },
            styles: { fontSize: 9, cellPadding: 2.5 },
            headStyles: {
                fillColor: [71, 91, 255],
                textColor: 255,
                halign: "center",
                fontStyle: "bold"
            },
            bodyStyles: {
                halign: "center"
            },
            alternateRowStyles: { fillColor: [240, 242, 254] },
            didDrawPage: (data) => {
                doc.setFontSize(8);
                doc.text(`Seite ${data.pageNumber} von ${doc.getNumberOfPages()}`, doc.internal.pageSize.getWidth() - 14, doc.internal.pageSize.getHeight() - 10, {
                    align: "right"
                });
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
            <div style={{ marginBottom: '1.5rem', textAlign: 'center' }}>
                <Link to="/admin/import-timetracking" className="button-primary" style={{ textDecoration: 'none', padding: '0.75rem 1.5rem', display: 'inline-block' }}>
                    {t('adminDashboard.importTimeTrackingButton', 'Zeiten importieren')}
                </Link>
            </div>
            <div className="dashboard-content grid lg:grid-cols-3 gap-4">
                <div className="left-column lg:col-span-2 flex flex-col gap-4">
                    <AdminWeekSection
                        t={t}
                        weekDates={Array.from({ length: 7 }, (_, i) => addDays(selectedMonday, i))}
                        selectedMonday={selectedMonday}
                        handlePrevWeek={handlePrevWeek}
                        handleNextWeek={handleNextWeek}
                        handleWeekJump={handleWeekJump}
                        onFocusProblemWeek={focusWeekForProblem}
                        allTracks={allTracks}
                        allVacations={allVacations}
                        allSickLeaves={allSickLeaves}
                        allHolidays={holidaysByCanton} // Geändert
                        users={users}
                        defaultExpectedHours={defaultExpectedHours}
                        openEditModal={openEditModal}
                        openPrintUserModal={openPrintUserModal}
                        weeklyBalances={weeklyBalances}
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
                </div>
                <div className="right-column flex flex-col gap-4">
                    <AdminCorrectionsList
                        t={t}
                        allCorrections={allCorrections}
                        handleApproveCorrection={handleApproveCorrection}
                        handleDenyCorrection={handleDenyCorrection}
                    />
                </div>
            </div>
            <div className="mt-8">
                <h4>{t('adminDashboard.vacationCalendarTitle')}</h4>
                <VacationCalendarAdmin
                    vacationRequests={allVacations.filter(v => v.approved)}
                    onReloadVacations={handleDataReloadNeeded}
                />
            </div>
            <EditTimeModal
                t={t}
                editModalVisible={editModalVisible}
                editDate={editDate}
                editData={editData}
                handleEditInputChange={handleEditInputChange}
                handleEditSubmit={handleEditSubmit}
                setEditModalVisible={setEditModalVisible}
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