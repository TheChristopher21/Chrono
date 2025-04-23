import { useState, useEffect, useRef, useCallback } from 'react';
import Navbar from '../../components/Navbar';
import api from '../../utils/api';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import { useNavigate } from "react-router-dom";
import "jspdf-autotable";

import {
    parseHex16, getMondayOfWeek, addDays, formatISO,
    computeDayTotalMinutes, expectedDayMinutes
} from './percentageDashUtils';

import PercentageWeekOverview from './PercentageWeekOverview';
import PercentageVacationSection from './PercentageVacationSection';
import PercentageCorrectionsPanel from './PercentageCorrectionsPanel';
import PercentageCorrectionModal from './PercentageCorrectionModal';
import '../../styles/PercentageDashboardScoped.css';
import PrintReportModal from "../../components/PrintReportModal.jsx";
import jsPDF from "jspdf";

const PercentageDashboard = () => {
    const {t} = useTranslation();
    const {notify} = useNotification();
    useNavigate();

    const [profile, setProfile] = useState(null);
    const [entries, setEntries] = useState([]);
    const [monday, setMonday] = useState(getMondayOfWeek(new Date()));
    const [punchMsg, setPunchMsg] = useState('');
    const lastPunch = useRef(0);

    const [vacationRequests, setVacationRequests] = useState([]);
    const [vacationForm, setVacationForm] = useState({startDate: '', endDate: ''});

    const [correctionRequests, setCorrectionRequests] = useState([]);
    const [showCorrectionsPanel, setShowCorrectionsPanel] = useState(false);
    const [showAllCorrections, setShowAllCorrections] = useState(false);
    const [selectedCorrectionMonday, setSelectedCorrectionMonday] = useState(getMondayOfWeek(new Date()));

    const [showCorrectionModal, setShowCorrectionModal] = useState(false);
    const [correctionDate, setCorrectionDate] = useState("");
    const [correctionData, setCorrectionData] = useState({
        workStart: "", breakStart: "", breakEnd: "", workEnd: "", reason: ""
    });

    const [printModalVisible, setPrintModalVisible] = useState(false);
    const [printStartDate, setPrintStartDate] = useState(formatISO(new Date()));
    const [printEndDate, setPrintEndDate] = useState(formatISO(new Date()));

    // 🛡️ Profil sicher laden & validieren
    useEffect(() => {
        api.get('/api/auth/me')
            .then(res => {
                const profile = res.data;

                if (profile.isPercentage) {
                    if (profile.workPercentage == null) profile.workPercentage = 100;
                    if (profile.annualVacationDays == null) profile.annualVacationDays = 25;
                    if (profile.trackingBalanceInMinutes == null) profile.trackingBalanceInMinutes = 0; // 🆕
                }

                setProfile(profile);
            })
            .catch(err => {
                console.error('Profil-Fehler', err);
                notify("Fehler beim Laden des Nutzerprofils");
            });
    }, []);


    const loadEntries = useCallback(() => {
        if (!profile) return;
        api.get(`/api/timetracking/history?username=${profile.username}`)
            .then(r => setEntries((r.data || []).filter(e => [1, 2, 3, 4].includes(e.punchOrder))))
            .catch(err => console.error('Eintrags‑Fehler', err));
    }, [profile]);

    useEffect(() => {
        loadEntries();
    }, [loadEntries]);

    useEffect(() => {
        const iv = setInterval(() => doNfcCheck(), 2000);
        return () => clearInterval(iv);
    }, []);

    async function doNfcCheck() {
        try {
            const res = await fetch(process.env.APIURL + '/api/nfc/read/1');
            if (!res.ok) return;
            const json = await res.json();
            if (json.status !== 'success') return;

            const cardUser = parseHex16(json.data);
            if (!cardUser) return;

            const now = Date.now();
            if (now - lastPunch.current < 60000) return;
            lastPunch.current = now;

            await api.post('/api/timetracking/punch', null, {params: {username: cardUser}});
            setPunchMsg(`Eingestempelt: ${cardUser}`);
            setTimeout(() => setPunchMsg(''), 3000);
            loadEntries();
        } catch (err) {
            console.error('NFC‑Fehler', err);
        }
    }

    const formatBalance = (min) => {
        if (min == null) return "0h 0min";
        const sign = min >= 0 ? "+" : "-";
        const abs = Math.abs(min);
        const h = Math.floor(abs / 60);
        const m = abs % 60;
        return `${sign}${h}h ${m}min`;
    };


    async function handleManualPunch() {
        if (!profile) return;
        try {
            await api.post('/api/timetracking/punch', null, {params: {username: profile.username}});
            setPunchMsg(`Manuell gestempelt: ${profile.username}`);
            setTimeout(() => setPunchMsg(''), 3000);
            loadEntries();
        } catch (e) {
            console.error('Punch‑Fehler', e);
            notify('Fehler beim Stempeln');
        }
    }

    const weeklyWorked = Array.from({length: 7}, (_, i) =>
        computeDayTotalMinutes(entries.filter(e => e.startTime.slice(0, 10) === formatISO(addDays(monday, i))))
    ).reduce((a, b) => a + b, 0);
    const weeklyExpected = 7 * expectedDayMinutes(profile || {});
    const weeklyDiff = weeklyWorked - weeklyExpected;


    async function fetchUserProfile() {
        try {
            const res = await api.get('/api/auth/me');
            const profile = res.data;

            if (profile.isPercentage) {
                if (profile.workPercentage == null) profile.workPercentage = 100;
                if (profile.annualVacationDays == null) profile.annualVacationDays = 25;
                if (profile.trackingBalanceInMinutes == null) profile.trackingBalanceInMinutes = 0; // 🆕
            }

            setProfile(profile);
        } catch (err) {
            console.error('Profil-Fehler', err);
            notify("Fehler beim Laden des Nutzerprofils");
        }
    }

    // Urlaub
    useEffect(() => {
        if (profile) fetchVacations();
    }, [profile]);

    async function fetchVacations() {
        try {
            const res = await api.get('/api/vacation/my');
            setVacationRequests(res.data || []);
        } catch (err) {
            console.error("Fehler beim Laden der Urlaube:", err);
        }
    }

    async function handleVacationSubmit(e) {
        e.preventDefault();
        try {
            await api.post('/api/vacation/create', null, {
                params: {
                    username: profile.username,
                    startDate: vacationForm.startDate,
                    endDate: vacationForm.endDate
                }
            });
            notify(t("vacationSubmitSuccess"));
            fetchVacations();
            await fetchUserProfile(); // 🆕 Profil neu laden für aktualisierte Überstunden
        } catch (err) {
            console.error('Fehler beim Urlaubsantrag:', err);
            notify(t("vacationSubmitError"));
        }
    }

    // Korrekturen
    useEffect(() => {
        if (profile) fetchCorrections();
    }, [profile]);

    async function fetchCorrections() {
        try {
            const res = await api.get(`/api/correction/my?username=${profile.username}`);
            setCorrectionRequests(res.data || []);
        } catch (err) {
            console.error('Fehler beim Laden der Korrekturanträge', err);
        }
    }

    function openCorrectionModal(dateStr) {
        setCorrectionDate(dateStr);
        setCorrectionData({workStart: "", breakStart: "", breakEnd: "", workEnd: "", reason: ""});
        setShowCorrectionModal(true);
    }

    async function handleCorrectionSubmit(e) {
        e.preventDefault();
        const {workStart, breakStart, breakEnd, workEnd, reason} = correctionData;
        const desiredStart = `${correctionDate}T${workStart}`;
        const desiredEnd = `${correctionDate}T${workEnd}`;
        try {
            await api.post('/api/correction/create-full', null, {
                params: {
                    username: profile.username,
                    date: correctionDate,
                    workStart,
                    breakStart,
                    breakEnd,
                    workEnd,
                    reason,
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

    async function handlePrintReport() {
        if (!printStartDate || !printEndDate) return notify("Zeitraum fehlt");

        setPrintModalVisible(false);

        try {
            const { data } = await api.get("/api/timetracking/report", {
                params: {
                    username: profile.username,
                    startDate: printStartDate,
                    endDate: printEndDate,
                },
            });

            const doc = new jsPDF("p", "mm", "a4");
            doc.setFontSize(14);
            doc.text(`Zeitenbericht für ${profile.firstName} ${profile.lastName}`, 14, 15);
            doc.setFontSize(11);
            doc.text(`Zeitraum: ${printStartDate} – ${printEndDate}`, 14, 22);

            const rows = (data || []).map((e) => [
                e.date,
                e.workStart || "-",
                e.breakStart || "-",
                e.breakEnd || "-",
                e.workEnd || "-",
            ]);

            doc.autoTable({
                head: [["Datum", "Work-Start", "Break-Start", "Break-End", "Work-End"]],
                body: rows,
                startY: 30,
                margin: { left: 14, right: 14 },
                styles: { fontSize: 9, cellPadding: 2 },
                headStyles: { fillColor: [0, 123, 255], halign: "center" },
                didDrawPage: (data) => {
                    const page = `${doc.internal.getNumberOfPages()}`;
                    doc.text(`Seite ${data.pageNumber}`, doc.internal.pageSize.getWidth() - 14, 10, { align: "right" });
                },
            });

            doc.save(`timesheet_${profile.username}_${printStartDate}_${printEndDate}.pdf`);
        } catch (err) {
            console.error("Fehler beim Generieren", err);
            notify("PDF-Fehler");
        }
    }

    if (!profile) {
        return <div className="user-dashboard"><Navbar/><p>Loading…</p></div>;
    }
    return (
        <div className="percentage-dashboard scoped-dashboard">
            <Navbar/>

            <header className="dashboard-header">
                <h2>Percentage‑Dashboard</h2>
                <div className="personal-info">
                    <p><strong>User:</strong> {profile.username}</p>
                    <p><strong>Arbeits‑%:</strong> {profile.workPercentage}%</p>
                    {profile.trackingBalanceInMinutes != null && (
                        <p className="overtime-info">
                            <strong>Überstunden:</strong> {formatBalance(profile.trackingBalanceInMinutes)}
                            <span className="tooltip-wrapper">
              <span className="tooltip-icon">ℹ️</span>
              <span className="tooltip-box">
                Überstunden entstehen, wenn du mehr als dein Tages‑Soll arbeitest.
                <br/>
                Du kannst sie später als „Überstundenfrei“ nutzen.
              </span>
            </span>
                        </p>
                    )}
                </div>
                <div className="print-report-container">
                    <button onClick={() => setPrintModalVisible(true)}>
                        {t("printReportButton")}
                    </button>
                </div>
            </header>

            {/* 🟦 Wochenübersicht direkt unter Header */}
            <PercentageWeekOverview
                user={profile}
                entries={entries}
                monday={monday}
                setMonday={setMonday}
                weeklyWorked={weeklyWorked}
                weeklyExpected={weeklyExpected}
                weeklyDiff={weeklyDiff}
                handleManualPunch={handleManualPunch}
                punchMessage={punchMsg}
                openCorrectionModal={openCorrectionModal}
            />

            {/* 🟩 Urlaub direkt darunter */}
            <PercentageVacationSection
                t={t}
                userProfile={profile}
                vacationRequests={vacationRequests}
                vacationForm={vacationForm}
                setVacationForm={setVacationForm}
                handleVacationSubmit={handleVacationSubmit}
            />

            {/* 🟨 Danach: Korrekturanträge */}
            <PercentageCorrectionsPanel
                t={t}
                correctionRequests={correctionRequests}
                selectedCorrectionMonday={selectedCorrectionMonday}
                setSelectedCorrectionMonday={setSelectedCorrectionMonday}
                showCorrectionsPanel={showCorrectionsPanel}
                setShowCorrectionsPanel={setShowCorrectionsPanel}
                showAllCorrections={showAllCorrections}
                setShowAllCorrections={setShowAllCorrections}
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
                cssScope="percentage"
            />
            {/* 🔒 Modals (unsichtbar bis aktiviert) */}
            <PercentageCorrectionModal
                visible={showCorrectionModal}
                correctionDate={correctionDate}
                correctionData={correctionData}
                handleCorrectionInputChange={(e) =>
                    setCorrectionData({...correctionData, [e.target.name]: e.target.value})}
                handleCorrectionSubmit={handleCorrectionSubmit}
                onClose={() => setShowCorrectionModal(false)}
            />

        </div>
    );
};
export default PercentageDashboard;
