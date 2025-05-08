import  { useState, useEffect } from 'react';
import Navbar from '../../components/Navbar';
import { useAuth } from '../../context/AuthContext';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import api from '../../utils/api';

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
    computeDayTotalMinutes,
    formatTime
} from './adminDashboardUtils';

const AdminDashboard = () => {
    const { currentUser } = useAuth();
    const { notify } = useNotification();
    const { t } = useTranslation();

    // State: Zeiterfassungen, Urlaube, Korrekturen, Nutzerliste
    const [allTracks, setAllTracks]           = useState([]);
    const [allVacations, setAllVacations]     = useState([]);
    const [allCorrections, setAllCorrections] = useState([]);
    const [users, setUsers]                   = useState([]);

    // State: Wochen-Navigation
    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));

    // State: erweiterte Userblöcke
    const [expandedUsers, setExpandedUsers] = useState({});

    // State: EditTimeModal
    const [editModalVisible, setEditModalVisible] = useState(false);
    const [editDate, setEditDate]                 = useState(null);
    const [editTargetUsername, setEditTargetUsername] = useState('');
    const [editData, setEditData] = useState({
        workStart: '',
        breakStart: '',
        breakEnd: '',
        workEnd: '',
        adminPassword: '',
        userPassword: ''
    });

    // State: PrintUserTimesModal
    const [printUserModalVisible, setPrintUserModalVisible] = useState(false);
    const [printUser, setPrintUser]                         = useState('');
    const [printUserStartDate, setPrintUserStartDate]       = useState(formatLocalDateYMD(new Date()));
    const [printUserEndDate,   setPrintUserEndDate]         = useState(formatLocalDateYMD(new Date()));

    // Sonstige
    const [weeklyBalances, setWeeklyBalances] = useState([]);
    const defaultExpectedHours                = 8;

    //
    // 1) Data Loading
    //
    useEffect(() => {
        fetchUsers();
        fetchAllTracks();
        fetchAllVacations();
        fetchAllCorrections();
    }, []);

    useEffect(() => {
        fetchTrackingBalances();
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
            const validEntries = (res.data || []).filter(e => [1,2,3,4].includes(e.punchOrder));
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
    async function fetchTrackingBalances() {
        try {
            const res = await api.get('/api/admin/timetracking/admin/tracking-balances');
            setWeeklyBalances(res.data || []);
        } catch (err) {
            console.error("Fehler beim Laden der Tracking-Bilanzen:", err);
        }
    }

    //
    // 2) Week Navigation
    //
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

    //
    // 3) Vacation Handlers
    //
    async function handleApproveVacation(id) {
        try {
            await api.post(`/api/vacation/approve/${id}`);
            fetchAllVacations();
        } catch (err) {
            console.error('Error approving vacation', err);
            notify('Error approving vacation: ' + err.message);
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

    //
    // 4) Corrections Handlers
    //
    async function handleApproveCorrection(id, comment) {
        try {
            await api.post(`/api/correction/approve/${id}`, null, { params: { comment } });
            fetchAllCorrections();
        } catch (err) {
            console.error('Error approving correction', err);
            notify('Error approving correction: ' + err.message);
        }
    }
    async function handleDenyCorrection(id, comment) {
        try {
            await api.post(`/api/correction/deny/${id}`, null, { params: { comment } });
            fetchAllCorrections();
        } catch (err) {
            console.error('Error denying correction', err);
            notify('Error denying correction: ' + err.message);
        }
    }

    //
    // 5) EditTimeModal (Bearbeiten / Neuen Tag anlegen)
    //
    function openEditModal(targetUsername, dateObj, entries) {
        // Minimales Default
        const defaultTime = '00:00';

        // Versuche existierende Punches zu finden
        const eStart = entries.find(e => e.punchOrder === 1);
        let eEnd     = entries.find(e => e.punchOrder === 4);
        if (!eEnd) {
            // Fallback: falls 4 fehlt, nimm evtl punchOrder=2
            eEnd = entries.find(e => e.punchOrder === 2);
        }
        const workStartVal = eStart ? formatTime(eStart.startTime) : defaultTime;
        const workEndVal   = eEnd
            ? formatTime(eEnd.endTime || eEnd.startTime)
            : defaultTime;

        setEditTargetUsername(targetUsername);
        setEditDate(dateObj);
        setEditData({
            workStart:   workStartVal,
            breakStart:  defaultTime,
            breakEnd:    defaultTime,
            workEnd:     workEndVal,
            adminPassword: '',
            userPassword: ''
        });
        setEditModalVisible(true);
    }

    /** NEU: "Zeiten Eintragen" - Modal öffnen, aber ohne vorhandene Einträge */
    function openNewEntryModal(targetUsername, dateObj) {
        setEditTargetUsername(targetUsername);
        setEditDate(dateObj);
        setEditData({
            workStart:   '',
            breakStart:  '',
            breakEnd:    '',
            workEnd:     '',
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

        // Falls der Admin sein eigener Eintrag => AdminPasswort = userPassword
        const finalAdminPassword =
            (currentUser.username === editTargetUsername)
                ? editData.userPassword
                : editData.adminPassword;

        const params = {
            targetUsername: editTargetUsername,
            date:           formattedDate,
            workStart:      editData.workStart,
            breakStart:     editData.breakStart,
            breakEnd:       editData.breakEnd,
            workEnd:        editData.workEnd,
            adminUsername:  currentUser.username,
            adminPassword:  finalAdminPassword,
            userPassword:   editData.userPassword
        };
        try {
            await api.put('/api/timetracking/editDay', null, { params });
            setEditModalVisible(false);
            fetchAllTracks(); // Daten neu laden
        } catch (err) {
            console.error('Edit failed', err);
            notify(t('adminDashboard.editFailed') + ': ' + err.message);
        }
    }
    function handleEditInputChange(e) {
        const { name, value } = e.target;
        setEditData(prev => ({ ...prev, [name]: value }));
    }

    //
    // 6) PrintUserTimesModal
    //
    function openPrintUserModal(username) {
        setPrintUser(username);
        const nowStr = formatLocalDateYMD(new Date());
        setPrintUserStartDate(nowStr);
        setPrintUserEndDate(nowStr);
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
            const ds = new Date(entry.startTime).toLocaleDateString("de-DE");
            if (!grouped[ds]) grouped[ds] = [];
            grouped[ds].push(entry);
        });
        const sortedDates = Object.keys(grouped).sort(
            (a,b) => new Date(a.split(".").reverse()) - new Date(b.split(".").reverse())
        );

        // PDF
        const doc = new jsPDF("p", "mm", "a4");
        doc.setFontSize(14);
        doc.text(`Zeitenbericht für ${printUser}`, 14, 15);
        doc.setFontSize(11);
        doc.text(`Zeitraum: ${printUserStartDate} – ${printUserEndDate}`, 14, 22);

        const tableBody = sortedDates.map(dateStr => {
            const dayEntries = grouped[dateStr].sort((a, b) => a.punchOrder - b.punchOrder);
            const eStart  = dayEntries.find(e => e.punchOrder === 1);
            const eBreakS = dayEntries.find(e => e.punchOrder === 2);
            const eBreakE = dayEntries.find(e => e.punchOrder === 3);
            const eEnd    = dayEntries.find(e => e.punchOrder === 4);

            const safeTime = (entry, type = "start") => {
                if (!entry) return "-";
                if (type === "end" && entry.endTime) {
                    return formatTime(entry.endTime);
                }
                return formatTime(entry.startTime);
            };
            const workStart  = safeTime(eStart, "start");
            const breakStart = eBreakS ? safeTime(eBreakS) : "-";
            const breakEnd   = eBreakE ? safeTime(eBreakE, "end") : "-";
            const workEnd    = safeTime(eEnd, "end");

            // Kannst hier noch dailyDiff oder Zeit-Spannen berechnen
            const totalMins = computeDayTotalMinutes(dayEntries);
            const totalStr  = `${totalMins} min`;

            return [dateStr, workStart, breakStart, breakEnd, workEnd, totalStr];
        });

        doc.autoTable({
            head: [["Datum", "Work-Start", "Break-Start", "Break-End", "Work-End", "Minuten"]],
            body: tableBody,
            startY: 30,
            margin: { left: 14, right: 14 },
            styles: { fontSize: 9, cellPadding: 3 },
            headStyles: {
                fillColor: [0, 123, 255],
                textColor: 255,
                halign: "center",
                fontStyle: "bold"
            },
            bodyStyles: {
                halign: "center"
            },
            didDrawPage: (data) => {
                doc.text(`Seite ${data.pageNumber}`, doc.internal.pageSize.getWidth() - 14, 10, {
                    align: "right"
                });
            }
        });
        doc.save(`zeiten_${printUser}_${printUserStartDate}_${printUserEndDate}.pdf`);
        setPrintUserModalVisible(false);
    }

    //
    // 7) Template
    //
    return (
        <div className="admin-dashboard scoped-dashboard">
            <Navbar />
            <header className="dashboard-header">
                <h2>{t('adminDashboard.titleWeekly')}</h2>
                {currentUser && (
                    <p>{t('adminDashboard.loggedInAs')} {currentUser.username}</p>
                )}
            </header>

            <div className="dashboard-content">
                {/* Linke Spalte */}
                <div className="left-column">
                    {/* Wochenübersicht */}
                    <AdminWeekSection
                        t={t}
                        weekDates={Array.from({ length: 7 }, (_, i) => addDays(selectedMonday, i))}
                        selectedMonday={selectedMonday}
                        handlePrevWeek={handlePrevWeek}
                        handleNextWeek={handleNextWeek}
                        handleWeekJump={handleWeekJump}
                        allTracks={allTracks}
                        users={users}
                        expandedUsers={expandedUsers}
                        setExpandedUsers={setExpandedUsers}
                        defaultExpectedHours={defaultExpectedHours}
                        openEditModal={openEditModal}
                        openPrintUserModal={openPrintUserModal}
                        weeklyBalances={weeklyBalances}
                        // NEU: die Funktion zum "Zeiten Eintragen" bei leeren Tagen:
                        openNewEntryModal={openNewEntryModal}
                    />

                    {/* VacationRequests */}
                    <AdminVacationRequests
                        t={t}
                        allVacations={allVacations}
                        handleApproveVacation={handleApproveVacation}
                        handleDenyVacation={handleDenyVacation}
                    />
                </div>

                {/* Rechte Spalte */}
                <div className="right-column">
                    {/* Korrekturanträge */}
                    <AdminCorrectionsList
                        t={t}
                        allCorrections={allCorrections}
                        handleApproveCorrection={handleApproveCorrection}
                        handleDenyCorrection={handleDenyCorrection}
                    />
                </div>
            </div>

            {/* Urlaubskalender */}
            <div style={{ marginTop: '2rem' }}>
                <h4>{t('adminDashboard.vacationCalendarTitle')}</h4>
                <VacationCalendarAdmin
                    vacationRequests={allVacations.filter(v => v.approved)}
                />
            </div>

            {/* Modal: Zeiten bearbeiten/eintragen */}
            <EditTimeModal
                t={t}
                editModalVisible={editModalVisible}
                editDate={editDate}
                editData={editData}
                handleEditInputChange={handleEditInputChange}
                handleEditSubmit={handleEditSubmit}
                setEditModalVisible={setEditModalVisible}
            />

            {/* Modal: User-Zeiten drucken */}
            <PrintUserTimesModal
                printUserModalVisible={printUserModalVisible}
                printUser={printUser}
                printUserStartDate={printUserStartDate}
                printUserEndDate={printUserEndDate}
                setPrintUserStartDate={setPrintUserStartDate}
                setPrintUserEndDate={setPrintUserEndDate}
                handlePrintUserTimesPeriodSubmit={handlePrintUserTimesPeriodSubmit}
                setPrintUserModalVisible={setPrintUserModalVisible}
            />
        </div>
    );
};

export default AdminDashboard;
