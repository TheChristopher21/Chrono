import React, { useState, useEffect, useCallback } from 'react';
import Navbar from '../../components/Navbar';
import api from '../../utils/api';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import 'jspdf-autotable';
import fileDownload from 'js-file-download';
import jsPDF from 'jspdf';

import {
    parseHex16,
    getMondayOfWeek,
    addDays,
    formatLocalDate,
    computeTotalMinutesInRange
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

    const [userProfile, setUserProfile] = useState(null);
    const [allEntries, setAllEntries] = useState([]);
    const [punchMessage, setPunchMessage] = useState('');

    // Wochenansicht
    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));

    // Print
    const [printModalVisible, setPrintModalVisible] = useState(false);
    const [printStartDate, setPrintStartDate] = useState(formatLocalDate(new Date()));
    const [printEndDate, setPrintEndDate] = useState(formatLocalDate(new Date()));

    // Urlaub
    const [vacationRequests, setVacationRequests] = useState([]);

    // Korrekturen
    const [correctionRequests, setCorrectionRequests] = useState([]);
    const [showCorrectionsPanel, setShowCorrectionsPanel] = useState(false);
    const [showAllCorrections, setShowAllCorrections] = useState(false);
    const [selectedCorrectionMonday, setSelectedCorrectionMonday] = useState(getMondayOfWeek(new Date()));

    // Correction Modal
    const [showCorrectionModal, setShowCorrectionModal] = useState(false);
    const [correctionDate, setCorrectionDate] = useState('');
    const [correctionData, setCorrectionData] = useState({
        workStart: '',
        breakStart: '',
        breakEnd: '',
        workEnd: '',
        reason: ''
    });

    // Notizen
    const [dailyNotes, setDailyNotes] = useState({});
    const [noteEditVisibility, setNoteEditVisibility] = useState({});

    // 1) Profil laden
    useEffect(() => {
        async function fetchProfile() {
            try {
                const res = await api.get('/api/auth/me');
                const profile = res.data;
                if (!profile.weeklySchedule) {
                    profile.weeklySchedule = [
                        {
                            monday: 1,
                            tuesday: 2,
                            wednesday: 3,
                            thursday: 4,
                            friday: 5,
                            saturday: 0,
                            sunday: 0
                        }
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

    // 2) Einträge, Urlaub, Korrekturen
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
            const allData = res.data || [];
            const validEntries = allData.filter(e => [1, 2, 3, 4].includes(e.punchOrder));
            setAllEntries(validEntries);

            // Notiz-Einträge
            const noteEntries = allData.filter(
                (e) => e.punchOrder === 0 && e.dailyNote && e.dailyNote.trim().length > 0
            );
            if (noteEntries.length > 0) {
                setDailyNotes((prev) => {
                    const merged = { ...prev };
                    noteEntries.forEach((noteEntry) => {
                        const isoDate = noteEntry.startTime.slice(0, 10);
                        merged[isoDate] = noteEntry.dailyNote;
                    });
                    return merged;
                });
            }
        } catch (err) {
            console.error('Error loading time entries', err);
        }
    }, [userProfile]);

    async function fetchVacations() {
        try {
            const res = await api.get('/api/vacation/my');
            setVacationRequests(res.data || []);
        } catch (err) {
            console.error('Error loading vacation requests', err);
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

    // 3) NFC Poll
    useEffect(() => {
        const interval = setInterval(() => doNfcCheck(), 2000);
        return () => clearInterval(interval);
    }, []);

    async function doNfcCheck() {
        try {
            const response = await fetch(process.env.APIURL + '/api/nfc/read/1');
            if (!response.ok) return;
            const json = await response.json();
            if (json.status === 'no-card' || json.status === 'error') return;
            if (json.status === 'success') {
                const cardUser = parseHex16(json.data);
                if (cardUser) {
                    await api.post('/api/timetracking/punch', null, {
                        params: { username: cardUser }
                    });
                }
            }
        } catch (err) {
            console.error('Punch error:', err);
        }
    }

    // 4) Manuelles Stempeln
    async function handleManualPunch() {
        if (!userProfile) return;
        try {
            await api.post('/api/timetracking/punch', null, {
                params: { username: userProfile.username }
            });
            setPunchMessage(`${t("punchMessage")}: ${userProfile.username}`);
            setTimeout(() => setPunchMessage(''), 3000);
            fetchEntries();
        } catch (err) {
            console.error('Punch-Fehler:', err);
            notify(t('manualPunchError'));
        }
    }

    // 5) Wochen-/Monatssummen
    const endOfWeek = addDays(selectedMonday, 6);
    const weeklyTotalMins = computeTotalMinutesInRange(allEntries, selectedMonday, endOfWeek);

    const year = selectedMonday.getFullYear();
    const month = selectedMonday.getMonth();
    const firstOfMonth = new Date(year, month, 1, 0, 0, 0);
    const lastOfMonth = new Date(year, month + 1, 0, 23, 59, 59);
    const monthlyTotalMins = computeTotalMinutesInRange(allEntries, firstOfMonth, lastOfMonth);

    // 6) Drucken
    async function handlePrintReport() {
        if (!printStartDate || !printEndDate) {
            return notify(t("missingDateRange"));
        }
        setPrintModalVisible(false);

        try {
            const { data } = await api.get('/api/timetracking/report', {
                params: {
                    username: userProfile.username,
                    startDate: printStartDate,
                    endDate: printEndDate
                }
            });

            const doc = new jsPDF('p', 'mm', 'a4');
            doc.setFontSize(14);
            doc.text(`Zeitenbericht für ${userProfile.firstName} ${userProfile.lastName}`, 14, 15);
            doc.setFontSize(11);
            doc.text(`Zeitraum: ${printStartDate} – ${printEndDate}`, 14, 22);

            const rows = (data || []).map((e) => [
                e.date,
                e.workStart || '-',
                e.breakStart || '-',
                e.breakEnd || '-',
                e.workEnd || '-'
            ]);

            doc.autoTable({
                head: [['Datum', 'Work-Start', 'Break-Start', 'Break-End', 'Work-End']],
                body: rows,
                startY: 30,
                margin: { left: 14, right: 14 },
                styles: { fontSize: 9, cellPadding: 2 },
                headStyles: { fillColor: [0, 123, 255], halign: 'center' },
                didDrawPage: (data) => {
                    doc.text(
                        `Seite ${data.pageNumber}`,
                        doc.internal.pageSize.getWidth() - 14,
                        10,
                        { align: 'right' }
                    );
                }
            });

            doc.save(`timesheet_${userProfile.username}_${printStartDate}_${printEndDate}.pdf`);
        } catch (err) {
            console.error('Fehler beim Generieren', err);
            notify(t("printReportError"));
        }
    }

    // 8) Korrekturantrag (Modal öffnen)
    function openCorrectionModal(dateObj) {
        const isoStr = formatLocalDate(dateObj);
        const dayEntries = allEntries.filter(e => e.startTime.slice(0, 10) === isoStr);

        let workStartVal = '';
        let breakStartVal = '';
        let breakEndVal   = '';
        let workEndVal    = '';

        const ws = dayEntries.find(e => e.punchOrder === 1);
        if (ws) workStartVal = ws.startTime.slice(11, 16);

        const bs = dayEntries.find(e => e.punchOrder === 2);
        if (bs) {
            breakStartVal = bs.breakStart
                ? bs.breakStart.slice(0, 5)
                : bs.startTime.slice(11, 16);
        }
        const be = dayEntries.find(e => e.punchOrder === 3);
        if (be) {
            breakEndVal = be.breakEnd
                ? be.breakEnd.slice(0, 5)
                : be.startTime.slice(11, 16);
        }
        const we = dayEntries.find(e => e.punchOrder === 4);
        if (we) {
            workEndVal = we.endTime
                ? we.endTime.slice(11, 16)
                : we.startTime.slice(11, 16);
        }

        setCorrectionDate(isoStr);
        setCorrectionData({
            workStart:  workStartVal,
            breakStart: breakStartVal,
            breakEnd:   breakEndVal,
            workEnd:    workEndVal,
            reason:     ''
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
            notify(t("fillWorkTimesError") || "Bitte Work Start und End ausfüllen");
            return;
        }
        const desiredStart = `${correctionDate}T${correctionData.workStart}`;
        const desiredEnd   = `${correctionDate}T${correctionData.workEnd}`;
        try {
            await api.post('/api/correction/create-full', null, {
                params: {
                    username: userProfile.username,
                    date: correctionDate,
                    workStart:  correctionData.workStart,
                    breakStart: correctionData.breakStart,
                    breakEnd:   correctionData.breakEnd,
                    workEnd:    correctionData.workEnd,
                    reason:     correctionData.reason,
                    desiredStart,
                    desiredEnd
                }
            });
            notify(t("correctionSubmitSuccess"));
            fetchCorrections();
            setShowCorrectionModal(false);
        } catch (err) {
            console.error('Fehler beim Absenden des Korrekturantrags:', err);
            notify(t("correctionSubmitError"));
        }
    }

    if (!userProfile) {
        return (
            <div className="hourly-dashboard scoped-dashboard">
                <h1>{t("hourlyDashboard.title")}</h1>
                <p>{t("loading")}...</p>
            </div>
        );
    }

    return (
        <div className="hourly-dashboard scoped-dashboard">
            <Navbar />

            <header className="dashboard-header">
                <h2>
                    {t("title")} ({t("hourlyDashboard.mode")})
                </h2>
                <div className="personal-info">
                    <p>
                        <strong>{t("usernameLabel")}:</strong> {userProfile.username}
                    </p>
                </div>
            </header>

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
                                note: dailyNotes[isoDate] || ''
                            }
                        });
                        notify(t("dailyNoteSaved"));
                        fetchEntries();
                    } catch (err) {
                        console.error('Fehler beim Speichern der Tagesnotiz:', err);
                        notify(t("dailyNoteError"));
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

            <div className="print-report-container">
                <button onClick={() => setPrintModalVisible(true)}>
                    {t("printReportButton")}
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
                onRefreshVacations={fetchVacations}
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
                correctionData={correctionData}
                handleCorrectionInputChange={handleCorrectionInputChange}
                handleCorrectionSubmit={handleCorrectionSubmit}
                onClose={() => setShowCorrectionModal(false)}
                t={t}
            />
        </div>
    );
};

export default HourlyDashboard;
