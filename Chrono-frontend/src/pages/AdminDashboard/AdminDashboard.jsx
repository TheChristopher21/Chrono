// AdminDashboard.jsx
import  { useState, useEffect } from 'react';
import Navbar from '../../components/Navbar';
import { useAuth } from '../../context/AuthContext';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import api from '../../utils/api';
import '../../styles/AdminDashboardScoped.css'; // ggf. Pfad anpassen

// Importiere unsere eigenen Sub-Komponenten
import AdminWeekSection from './AdminWeekSection';
import AdminVacationRequests from './AdminVacationRequests';
import AdminCorrectionsList from './AdminCorrectionsList';
import EditTimeModal from './EditTimeModal';
import PrintUserTimesModal from './PrintUserTimesModal';

// Importiere VacationCalendarAdmin, falls die in /components liegt
import VacationCalendarAdmin from '../../components/VacationCalendarAdmin';

// Importiere alle benötigten Funktionen aus unserem Utils-File
import {
    getMondayOfWeek,
    formatLocalDateYMD,
    addDays,
    computeDayTotalMinutes, formatTime, isLateTime
} from './adminDashboardUtils';

const AdminDashboard = () => {
    const { currentUser } = useAuth();
    const { notify } = useNotification();
    const { t } = useTranslation();
// Falls du das irgendwo brauchst
    const [allTracks, setAllTracks] = useState([]);
    const [allVacations, setAllVacations] = useState([]);
    const [allCorrections, setAllCorrections] = useState([]);
    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));
    const [expandedUsers, setExpandedUsers] = useState({});
    const [users, setUsers] = useState([]);
    const [, setDailyNotes] = useState({});

    const [editModalVisible, setEditModalVisible] = useState(false);
    const [editDate, setEditDate] = useState(null);
    const [editTargetUsername, setEditTargetUsername] = useState('');
    const [editData, setEditData] = useState({
        workStart: '',
        breakStart: '',
        breakEnd: '',
        workEnd: '',
        adminPassword: '',
        userPassword: ''
    });
    const [weeklyBalances, setWeeklyBalances] = useState([]);

    const [printUserModalVisible, setPrintUserModalVisible] = useState(false);
    const [printUser, setPrintUser] = useState('');
    const [printUserStartDate, setPrintUserStartDate] = useState(formatLocalDateYMD(new Date()));
    const [printUserEndDate, setPrintUserEndDate] = useState(formatLocalDateYMD(new Date()));

    const defaultExpectedHours = 8;

    // ---------------------------
    // Daten laden (Users, Tracks, Vacations, Corrections)
    // ---------------------------
    useEffect(() => {
        fetchUsers();
        fetchAllTracks();
        fetchAllVacations();
        fetchAllCorrections();
    }, []);
    useEffect(() => {
        async function fetchTrackingBalances() {
            try {
                const res = await api.get(`/api/admin/timetracking/admin/tracking-balances`);
                setWeeklyBalances(res.data || []);
            } catch (err) {
                console.error("Fehler beim Laden der Tracking-Bilanzen:", err);
            }
        }

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
            const validEntries = (res.data || []).filter(e => [1, 2, 3, 4].includes(e.punchOrder));
            setAllTracks(validEntries);

            // Notizen
            const noteEntries = (res.data || []).filter(
                e => e.punchOrder === 0 && e.dailyNote && e.dailyNote.trim().length > 0
            );
            if (noteEntries.length > 0) {
                setDailyNotes(prev => {
                    const merged = { ...prev };
                    noteEntries.forEach(noteEntry => {
                        const isoDate = noteEntry.startTime.slice(0, 10);
                        merged[isoDate] = noteEntry.dailyNote;
                    });
                    return merged;
                });
            }
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

    // ---------------------------
    // Navigation für Kalenderwoche
    // ---------------------------
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

    // ---------------------------
    // Vacation-Handler
    // ---------------------------
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

    // ---------------------------
    // Corrections-Handler
    // ---------------------------
    async function handleApproveCorrection(id) {
        try {
            await api.post(`/api/correction/approve/${id}`);
            fetchAllCorrections();
        } catch (err) {
            console.error('Error approving correction', err);
            notify('Error approving correction: ' + err.message);
        }
    }

    async function handleDenyCorrection(id) {
        try {
            await api.post(`/api/correction/deny/${id}`);
            fetchAllCorrections();
        } catch (err) {
            console.error('Error denying correction', err);
            notify('Error denying correction: ' + err.message);
        }
    }

    // ---------------------------
    // Edit-Time-Modal
    // ---------------------------
    function openEditModal(targetUsername, dateObj, entries) {
        const defaultTime = '00:00';
        const workStartEntry = entries.find(e => e.punchOrder === 1);
        let workEndEntry = entries.find(e => e.punchOrder === 4);
        if (!workEndEntry) {
            workEndEntry = entries.find(e => e.punchOrder === 2);
        }
        const workStartVal = workStartEntry ? formatTime(workStartEntry.startTime) : defaultTime;
        const workEndVal = workEndEntry
            ? formatTime(workEndEntry.endTime || workEndEntry.startTime)
            : defaultTime;

        setEditTargetUsername(targetUsername);
        setEditDate(dateObj);
        setEditData({
            workStart: workStartVal,
            breakStart: defaultTime,
            breakEnd: defaultTime,
            workEnd: workEndVal,
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
        const params = {
            targetUsername: editTargetUsername,
            date: formattedDate,
            workStart: editData.workStart,
            breakStart: editData.breakStart,
            breakEnd: editData.breakEnd,
            workEnd: editData.workEnd,
            adminUsername: currentUser.username,
            adminPassword:
                currentUser.username !== editTargetUsername
                    ? editData.adminPassword
                    : editData.userPassword,
            userPassword: editData.userPassword
        };
        try {
            await api.put('/api/timetracking/editDay', null, { params });
            setEditModalVisible(false);
            fetchAllTracks();
        } catch (err) {
            console.error('Edit failed', err);
            notify(t('adminDashboard.editFailed') + ': ' + err.message);
        }
    }

    function handleEditInputChange(e) {
        const { name, value } = e.target;
        setEditData(prev => ({ ...prev, [name]: value }));
    }

    // ---------------------------
    // Print-Modal
    // ---------------------------
    function openPrintUserModal(username) {
        setPrintUser(username);
        setPrintUserStartDate(formatLocalDateYMD(new Date()));
        setPrintUserEndDate(formatLocalDateYMD(new Date()));
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

        // Gruppieren
        const grouped = {};
        userEntries.forEach(entry => {
            const entryDate = new Date(entry.startTime);
            const ds = entryDate.toLocaleDateString("de-DE");
            if (!grouped[ds]) grouped[ds] = [];
            grouped[ds].push(entry);
        });

        const sortedDates = Object.keys(grouped).sort((a, b) => new Date(a) - new Date(b));

        // PDF erstellen
        // (Genau wie in deinem Code)
        import('jspdf').then(({ default: jsPDF }) => {
            import('jspdf-autotable').then(({ default: autoTable }) => {
                const doc = new jsPDF("p", "mm", "a4");
                doc.setFontSize(12);
                doc.text(`Zeiten für ${printUser}`, 14, 15);

                const tableBody = sortedDates.map(dateStr => {
                    const dayEntries = grouped[dateStr].sort((a, b) => a.punchOrder - b.punchOrder);
                    const eStart = dayEntries.find(e => e.punchOrder === 1);
                    const eBreakS = dayEntries.find(e => e.punchOrder === 2);
                    const eBreakE = dayEntries.find(e => e.punchOrder === 3);
                    const eEnd = dayEntries.find(e => e.punchOrder === 4);

                    function safeFormatTime(entry, which = 'start') {
                        if (!entry) return '-';
                        if (which === 'end' && entry.endTime) return formatTime(entry.endTime);
                        if (which === 'start' && entry.startTime) return formatTime(entry.startTime);
                        return '-';
                    }

                    const workStart = safeFormatTime(eStart, 'start');
                    const breakStart = eBreakS
                        ? (eBreakS.breakStart ? formatTime(eBreakS.breakStart) : formatTime(eBreakS.startTime))
                        : '-';
                    const breakEnd = eBreakE
                        ? (eBreakE.breakEnd ? formatTime(eBreakE.breakEnd) : formatTime(eBreakE.startTime))
                        : '-';
                    const workEnd = safeFormatTime(eEnd, 'end');

                    const dayMinutes = computeDayTotalMinutes(dayEntries);
                    const sign = dayMinutes >= 0 ? "+" : "-";
                    const diffText = `${sign}${Math.abs(dayMinutes)} min`;

                    return [dateStr, workStart, breakStart, breakEnd, workEnd, diffText];
                });

                const head = [["Datum", "Work Start", "Break Start", "Break End", "Work End", "Diff"]];
                autoTable(doc, {
                    head,
                    body: tableBody,
                    startY: 25,
                    styles: {
                        fontSize: 9,
                        cellPadding: 3
                    },
                    headStyles: {
                        fillColor: [0, 123, 255],
                        textColor: 255,
                        fontStyle: "bold"
                    },
                    theme: "grid"
                });

                const dataUri = doc.output("datauristring");
                const pdfBase64 = dataUri.split(",")[1];

                // Versuche, PDF abzuspeichern (ElektroMain)
                window.electron?.ipcRenderer.invoke("saveAndOpenPDF", pdfBase64).catch(err => {
                    console.error("Fehler beim Drucken der Zeiten für", printUser, ":", err);
                    notify("Fehler beim Drucken der Zeiten für " + printUser);
                });
                setPrintUserModalVisible(false);
            });
        });
    }
    const allEntries = allTracks
        .filter(e => e.username === currentUser?.username)
        .filter(e => {
            const entryDate = new Date(e.startTime).toLocaleDateString("de-DE");
            const selectedDate = selectedMonday.toLocaleDateString("de-DE");
            return entryDate === selectedDate;
        })
        .map(e => ({
            label: e.label || `Eintrag ${e.punchOrder}`,
            time: e.endTime
                ? `${formatTime(e.startTime)}–${formatTime(e.endTime)}`
                : formatTime(e.startTime)
        }));

    return (
        <div className="admin-dashboard scoped-dashboard">
            <Navbar />
            <header className="dashboard-header">
                <h2>{t('adminDashboard.titleWeekly')}</h2>
                <p>{t('adminDashboard.loggedInAs')}: {currentUser?.username}</p>
                <ul className="time-entry-list">
                    {allEntries
                        .filter(e => new Date(e.startTime).toLocaleDateString() === selectedMonday.toLocaleDateString())
                        .map((e, idx) => (
                            <li
                                key={idx}
                                className={`time-entry ${isLateTime(e.time) ? 'late-time' : ''}`}
                            >
                                <span className="entry-label">{e.label}</span>: {e.time}
                            </li>
                        ))}
                </ul>


            </header>

            <div className="dashboard-content">
                {/* Linke Spalte: Wochen-Ansicht + Urlaubsanträge */}
                <div className="left-column">
                    <AdminWeekSection
                        t={t}
                        currentUser={currentUser}
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
                        weeklyBalances={weeklyBalances}  // ✅ DAS HAT DIR GEFELHT
                    />


                    <AdminVacationRequests
                        t={t}
                        allVacations={allVacations}
                        handleApproveVacation={handleApproveVacation}
                        handleDenyVacation={handleDenyVacation}
                    />
                </div>

                {/* Rechte Spalte: Korrekturanträge */}
                <div className="right-column">
                    <AdminCorrectionsList
                        t={t}
                        allCorrections={allCorrections}
                        handleApproveCorrection={handleApproveCorrection}
                        handleDenyCorrection={handleDenyCorrection}
                    />
                </div>
            </div>

            {/* Kalender am Seitenende */}
            <div className="full-width-calendar">
                <h4>{t('adminDashboard.vacationCalendarTitle')}</h4>
                <VacationCalendarAdmin
                    vacationRequests={allVacations.filter(v => v.approved)}
                />
            </div>

            {/* Edit-Modal (Zeiten anpassen) */}
            <EditTimeModal
                t={t}
                editModalVisible={editModalVisible}
                editDate={editDate}
                editData={editData}
                handleEditInputChange={handleEditInputChange}
                handleEditSubmit={handleEditSubmit}
                setEditModalVisible={setEditModalVisible}
            />

            {/* PrintUser-Modal */}
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
