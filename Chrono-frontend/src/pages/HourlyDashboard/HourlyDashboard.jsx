// HourlyDashboard.jsx
import  { useState, useEffect, useCallback } from 'react';
import Navbar from '../../components/Navbar';
import api from '../../utils/api';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';

import {
    parseHex16,
    getMondayOfWeek,
    addDays,
    formatLocalDate,
    computeTotalMinutesInRange, formatTime
} from './hourDashUtils';

import HourlyWeekOverview from './HourlyWeekOverview';
import HourlyVacationSection from './HourlyVacationSection';
import HourlyCorrectionsPanel from './HourlyCorrectionsPanel';
import HourlyCorrectionModal from './HourlyCorrectionModal';
import HourlyPrintModal from './HourlyPrintModal';

import '../../styles/HourlyDashboard.css'; // Dein Style, falls du hast

const HourlyDashboard = () => {
    const { t } = useTranslation();
    const { notify } = useNotification();

    const [userProfile, setUserProfile] = useState(null);
    const [allEntries, setAllEntries] = useState([]);
    const [punchMessage, setPunchMessage] = useState('');

    // Wochenansicht
    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));

    // Drucken
    const [printModalVisible, setPrintModalVisible] = useState(false);
    const [printStartDate, setPrintStartDate] = useState(formatLocalDate(new Date()));
    const [printEndDate, setPrintEndDate] = useState(formatLocalDate(new Date()));

    // Urlaub
    const [vacationRequests, setVacationRequests] = useState([]);
    const [vacationForm, setVacationForm] = useState({ startDate: '', endDate: '' });

    // Korrekturanträge
    const [correctionRequests, setCorrectionRequests] = useState([]);
    const [showCorrectionsPanel, setShowCorrectionsPanel] = useState(false);
    const [showAllCorrections, setShowAllCorrections] = useState(false);
    const [selectedCorrectionMonday, setSelectedCorrectionMonday] = useState(getMondayOfWeek(new Date()));

    // Correction Modal
    const [showCorrectionModal, setShowCorrectionModal] = useState(false);
    const [correctionDate, setCorrectionDate] = useState("");
    const [correctionData, setCorrectionData] = useState({
        workStart: "",
        breakStart: "",
        breakEnd: "",
        workEnd: "",
        reason: ""
    });

    // Notizen
    const [dailyNotes, setDailyNotes] = useState({});
    const [noteEditVisibility, setNoteEditVisibility] = useState({});

    // ---------------------------
    // 1) Profil laden
    // ---------------------------
    useEffect(() => {
        async function fetchProfile() {
            try {
                const res = await api.get('/api/auth/me');
                const profile = res.data;
                if (!profile.weeklySchedule) {
                    profile.weeklySchedule = [
                        { monday: 1, tuesday: 2, wednesday: 3, thursday: 4, friday: 5, saturday: 0, sunday: 0 }
                    ];
                    profile.scheduleCycle = 1;
                }
                setUserProfile(profile);
            } catch (err) {
                console.error(t('personalData.errorLoading'), err);
            }
        }
        fetchProfile();
    }, [t]);

    // ---------------------------
    // 2) Einträge, Urlaub, Korrekturen
    // ---------------------------
    useEffect(() => {
        if (userProfile) {
            fetchEntries();
            fetchVacations();
            fetchCorrections();
        }
    }, [userProfile]);

    const fetchEntries = useCallback(async () => {
        if (!userProfile) return;
        try {
            const res = await api.get(`/api/timetracking/history?username=${userProfile.username}`);
            const entries = res.data || [];
            // Reguläre Stempel (1..4)
            const validEntries = entries.filter(e => [1, 2, 3, 4].includes(e.punchOrder));
            setAllEntries(validEntries);

            // Notiz-Einträge (punchOrder=0 => dailyNote)
            const noteEntries = entries.filter(e => e.punchOrder === 0 && e.dailyNote && e.dailyNote.trim().length > 0);
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
            console.error("Error loading time entries", err);
        }
    }, [userProfile]);

    async function fetchVacations() {
        try {
            const res = await api.get('/api/vacation/my');
            setVacationRequests(res.data || []);
        } catch (err) {
            console.error("Error loading vacation requests", err);
        }
    }

    async function fetchCorrections() {
        try {
            const res = await api.get(`/api/correction/my?username=${userProfile.username}`);
            setCorrectionRequests(res.data || []);
        } catch (err) {
            console.error('Error loading correction requests', err);
        }
    }

    // ---------------------------
    // 3) NFC-Check (Intervall)
    // ---------------------------
    useEffect(() => {
        const interval = setInterval(() => doNfcCheck(), 2000);
        return () => clearInterval(interval);
    }, []);

    async function doNfcCheck() {
        try {
            const response = await fetch('http://localhost:8080/api/nfc/read/1');
            if (!response.ok) return;
            const json = await response.json();
            if (json.status === 'no-card' || json.status === 'error') return;
            if (json.status === 'success') {
                const cardUser = parseHex16(json.data);
                if (cardUser) {
                    // Stempeln
                    await api.post('/api/timetracking/punch', null, {
                        params: { username: cardUser }
                    });
                }
            }
        } catch (err) {
            console.error("Punch error:", err);
        }
    }

    // ---------------------------
    // 4) Manuelles Stempeln
    // ---------------------------
    async function handleManualPunch() {
        try {
            await api.post('/api/timetracking/punch', null, {
                params: { username: userProfile.username }
            });
            setPunchMessage(`Eingestempelt: ${userProfile.username}`);
            setTimeout(() => setPunchMessage(''), 3000);
            fetchEntries();
        } catch (err) {
            console.error('Punch-Fehler:', err);
            notify(t("manualPunchError") || "Fehler beim Stempeln");
        }
    }

    // ---------------------------
    // 5) Wochen-/Monatssummen
    // ---------------------------
    const endOfWeek = addDays(selectedMonday, 6);
    const weeklyTotalMins = computeTotalMinutesInRange(allEntries, selectedMonday, endOfWeek);

    const year = selectedMonday.getFullYear();
    const month = selectedMonday.getMonth();
    const firstOfMonth = new Date(year, month, 1, 0, 0, 0);
    const lastOfMonth = new Date(year, month + 1, 0, 23, 59, 59);
    const monthlyTotalMins = computeTotalMinutesInRange(allEntries, firstOfMonth, lastOfMonth);

    // ---------------------------
    // 6) Druck-Funktion
    // ---------------------------
    function handlePrintReport() {
        if (!printStartDate || !printEndDate) {
            notify(t("printReportError"));
            return;
        }
        // PDF-Generierung
        import('jspdf').then(({ default: jsPDF }) => {
            import('jspdf-autotable').then(({ default: autoTable }) => {
                const doc = new jsPDF("p", "mm", "a4");
                doc.setFontSize(12);
                doc.text(`${t("printReportTitle")} - ${userProfile?.username}`, 10, 15);

                const filteredEntries = allEntries.filter(e => {
                    const entryDate = new Date(e.startTime);
                    return entryDate >= new Date(printStartDate) && entryDate <= new Date(printEndDate);
                });

                // Gruppieren
                const grouped = {};
                filteredEntries.forEach(entry => {
                    const ds = new Date(entry.startTime).toLocaleDateString("de-DE");
                    if (!grouped[ds]) grouped[ds] = [];
                    grouped[ds].push(entry);
                });

                const tableBody = Object.keys(grouped)
                    .sort((a, b) => new Date(a) - new Date(b))
                    .map(dateStr => {
                        const dayEntries = grouped[dateStr].sort((x, y) => x.punchOrder - y.punchOrder);

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

                        const dayMinutes = computeTotalMinutesInRange(dayEntries, new Date(dateStr), new Date(dateStr));
                        const sign = dayMinutes >= 0 ? "+" : "-";
                        const diffText = `${sign}${Math.abs(dayMinutes)} min`;

                        return [dateStr, workStart, breakStart, breakEnd, workEnd, diffText];
                    });

                autoTable(doc, {
                    head: [["Datum", "Work Start", "Break Start", "Break End", "Work End", "Diff"]],
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

                window.electron?.ipcRenderer.invoke("saveAndOpenPDF", pdfBase64).catch(err => {
                    console.error("Fehler beim Drucken:", err);
                    notify("Fehler beim Öffnen des Berichts.");
                });
                setPrintModalVisible(false);
            });
        });
    }

    // ---------------------------
    // 7) Urlaub
    // ---------------------------
    async function handleVacationSubmit(e) {
        e.preventDefault();
        try {
            await api.post('/api/vacation/create', null, {
                params: {
                    username: userProfile.username,
                    startDate: vacationForm.startDate,
                    endDate: vacationForm.endDate
                }
            });
            notify(t("vacationSubmitSuccess"));
            fetchVacations();
        } catch (err) {
            console.error('Error submitting vacation request:', err);
            notify(t("vacationSubmitError"));
        }
    }

    // ---------------------------
    // 8) Korrekturantrag (Neuen erstellen)
    // ---------------------------
    function openCorrectionModal(dateObj) {
        const isoStr = formatLocalDate(dateObj);
        const dayEntries = allEntries.filter(e => e.startTime.slice(0, 10) === isoStr);

        let workStartVal = "";
        let breakStartVal = "";
        let breakEndVal = "";
        let workEndVal = "";

        const ws = dayEntries.find(e => e.punchOrder === 1);
        if (ws) workStartVal = ws.startTime.slice(11, 16); // "HH:MM"
        const bs = dayEntries.find(e => e.punchOrder === 2);
        if (bs) breakStartVal = bs.breakStart
            ? bs.breakStart.slice(0, 5)
            : bs.startTime.slice(11, 16);
        const be = dayEntries.find(e => e.punchOrder === 3);
        if (be) breakEndVal = be.breakEnd
            ? be.breakEnd.slice(0, 5)
            : be.startTime.slice(11, 16);
        const we = dayEntries.find(e => e.punchOrder === 4);
        if (we) workEndVal = we.endTime
            ? we.endTime.slice(11, 16)
            : we.startTime.slice(11, 16);

        setCorrectionDate(isoStr);
        setCorrectionData({
            workStart: workStartVal,
            breakStart: breakStartVal,
            breakEnd: breakEndVal,
            workEnd: workEndVal,
            reason: ""
        });
        setShowCorrectionModal(true);
    }

    function handleCorrectionInputChange(e) {
        const { name, value } = e.target;
        setCorrectionData(prev => ({ ...prev, [name]: value }));
    }

    async function handleCorrectionSubmit(e) {
        e.preventDefault();
        if (!correctionData.workStart || !correctionData.workEnd) {
            notify("Bitte füllen Sie Work Start und Work End aus.");
            return;
        }
        const desiredStart = `${correctionDate}T${correctionData.workStart}`;
        const desiredEnd = `${correctionDate}T${correctionData.workEnd}`;
        try {
            await api.post('/api/correction/create-full', null, {
                params: {
                    username: userProfile.username,
                    date: correctionDate,
                    workStart: correctionData.workStart,
                    breakStart: correctionData.breakStart,
                    breakEnd: correctionData.breakEnd,
                    workEnd: correctionData.workEnd,
                    reason: correctionData.reason,
                    desiredStart,
                    desiredEnd
                }
            });
            notify("Korrekturantrag erfolgreich gestellt.");
            fetchCorrections();
            setShowCorrectionModal(false);
        } catch (err) {
            console.error("Fehler beim Absenden des Korrekturantrags:", err);
            notify("Fehler beim Absenden des Korrekturantrags.");
        }
    }

    if (!userProfile) {
        return (
            <div className="user-dashboard">
                <Navbar />
                <p>Loading...</p>
            </div>
        );
    }

    return (
        <div className="user-dashboard">
            <Navbar />

            <header className="dashboard-header">
                <h2>{t("title")} (Stundenbasiert)</h2>
                <div className="personal-info">
                    <p>
                        <strong>{t("usernameLabel")}:</strong> {userProfile.username}
                    </p>
                </div>
            </header>

            {/* Wochendarstellung */}
            <HourlyWeekOverview
                t={t}
                userProfile={userProfile}
                allEntries={allEntries}
                dailyNotes={dailyNotes}
                noteEditVisibility={noteEditVisibility}
                setDailyNotes={setDailyNotes}
                setNoteEditVisibility={setNoteEditVisibility}
                handleSaveNote={async (isoDate) => {
                    try {
                        await api.post('/api/timetracking/daily-note', null, {
                            params: {
                                username: userProfile.username,
                                date: isoDate,
                                note: dailyNotes[isoDate] || ""
                            }
                        });
                        notify("Tagesnotiz gespeichert.");
                        fetchEntries();
                    } catch (err) {
                        console.error("Fehler beim Speichern der Tagesnotiz:", err);
                        notify("Fehler beim Speichern der Tagesnotiz.");
                    }
                }}
                openCorrectionModal={openCorrectionModal}
                selectedMonday={selectedMonday}
                setSelectedMonday={setSelectedMonday}
                weeklyTotalMins={weeklyTotalMins}
                monthlyTotalMins={monthlyTotalMins}
                handleManualPunch={handleManualPunch}
                punchMessage={punchMessage}
            />

            {/* Drucken */}
            <div className="print-report-container">
                <button onClick={() => setPrintModalVisible(true)}>
                    {t("printReportButton")}
                </button>
            </div>
            <HourlyPrintModal
                t={t}
                visible={printModalVisible}
                printStartDate={printStartDate}
                setPrintStartDate={setPrintStartDate}
                printEndDate={printEndDate}
                setPrintEndDate={setPrintEndDate}
                handlePrintReport={handlePrintReport}
                onClose={() => setPrintModalVisible(false)}
            />

            {/* Urlaub */}
            <HourlyVacationSection
                t={t}
                userProfile={userProfile}
                vacationRequests={vacationRequests}
                vacationForm={vacationForm}
                setVacationForm={setVacationForm}
                handleVacationSubmit={handleVacationSubmit}
            />

            {/* Korrekturanträge */}
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

            {/* Modal: Neuer Korrekturantrag */}
            <HourlyCorrectionModal
                visible={showCorrectionModal}
                correctionDate={correctionDate}
                correctionData={correctionData}
                handleCorrectionInputChange={handleCorrectionInputChange}
                handleCorrectionSubmit={handleCorrectionSubmit}
                onClose={() => setShowCorrectionModal(false)}
            />
        </div>
    );
};

export default HourlyDashboard;
