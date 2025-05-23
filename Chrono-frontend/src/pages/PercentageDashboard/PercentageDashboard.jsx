// PercentageDashboard.jsx
import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from "react-router-dom";
import Navbar from '../../components/Navbar';
import api from '../../utils/api';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import "jspdf-autotable";
import jsPDF from "jspdf";

import {
    parseHex16,
    getMondayOfWeek,
    addDays,
    formatISO,
    computeDayTotalMinutes,
    expectedDayMinutes
} from './percentageDashUtils';
import { expandDayRows } from '../UserDashboard/userDashUtils';

import PercentageWeekOverview from './PercentageWeekOverview';
import PercentageVacationSection from './PercentageVacationSection';
import PercentageCorrectionsPanel from './PercentageCorrectionsPanel';
import PercentageCorrectionModal from './PercentageCorrectionModal';
import PrintReportModal from "../../components/PrintReportModal.jsx";

import '../../styles/PercentageDashboardScoped.css';
import {minutesToHours} from "date-fns";

const PercentageDashboard = () => {
    const { t } = useTranslation();
    const { notify } = useNotification();
    useNavigate();

    const [profile, setProfile] = useState(null);
    const [entries, setEntries] = useState([]);
    const [monday, setMonday] = useState(getMondayOfWeek(new Date()));
    const [punchMsg, setPunchMsg] = useState('');
    const lastPunch = useRef(0);

    // Urlaub
    const [vacationRequests, setVacationRequests] = useState([]);

    // Korrekturen
    const [correctionRequests, setCorrectionRequests] = useState([]);
    const [showCorrectionsPanel, setShowCorrectionsPanel] = useState(false);
    const [showAllCorrections, setShowAllCorrections] = useState(false);
    const [selectedCorrectionMonday, setSelectedCorrectionMonday] = useState(getMondayOfWeek(new Date()));

    // CorrectionModal
    const [showCorrectionModal, setShowCorrectionModal] = useState(false);
    const [correctionDate, setCorrectionDate] = useState("");
    const [correctionData, setCorrectionData] = useState({
        workStart: "",
        breakStart: "",
        breakEnd: "",
        workEnd: "",
        reason: ""
    });

    // Print
    const [printModalVisible, setPrintModalVisible] = useState(false);
    const [printStartDate, setPrintStartDate] = useState(formatISO(new Date()));
    const [printEndDate, setPrintEndDate] = useState(formatISO(new Date()));

    const loadProfile = useCallback(() => {
        api.get('/api/auth/me')
            .then(res => {
                const p = res.data;
                if (p.isPercentage) {
                    if (p.workPercentage == null) p.workPercentage = 100;
                    if (p.annualVacationDays == null) p.annualVacationDays = 25;
                    if (p.trackingBalanceInMinutes == null) p.trackingBalanceInMinutes = 0;
                }
                setProfile(p);
            })
            .catch(err => {
                console.error('Profil-Fehler', err);
                notify("Fehler beim Laden des Nutzerprofils");
            });
    }, [notify]);

    // Profil laden
    useEffect(() => {
        loadProfile();
    }, [loadProfile]);

    // Einträge laden
    const loadEntries = useCallback(() => {
        if (!profile) return;
        api.get(`/api/timetracking/history?username=${profile.username}`)
            .then(r => {
                const expanded = expandDayRows(r.data || []);
                const data = expanded.filter(e => [1,2,3,4].includes(e.punchOrder));
                setEntries(data);
            })
            .catch(err => console.error('Eintrags‑Fehler', err));
    }, [profile]);

    useEffect(() => {
        loadEntries();
    }, [loadEntries]);

    // NFC Polling
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

            await api.post('/api/timetracking/punch', null, { params: { username: cardUser } });
            setPunchMsg(`Eingestempelt: ${cardUser}`);
            setTimeout(() => setPunchMsg(''), 3000);
            loadEntries();
            loadProfile();
        } catch (err) {
            console.error('NFC‑Fehler', err);
        }
    }

    // Manuelles Stempeln
    async function handleManualPunch() {
        if (!profile) return;
        try {
            await api.post('/api/timetracking/punch', null, { params: { username: profile.username } });
            setPunchMsg(`Manuell gestempelt: ${profile.username}`);
            setTimeout(() => setPunchMsg(''), 3000);
            loadEntries();
            loadProfile();
        } catch (e) {
            console.error('Punch‑Fehler', e);
            notify('Fehler beim Stempeln');
        }
    }

    // Summen-Logik
    const weeklyWorked = Array.from({ length: 5 }, (_, i) => {
        const isoDay = formatISO(addDays(monday, i));
        const dayEntries = entries.filter(e => e.startTime.slice(0, 10) === isoDay);
        return computeDayTotalMinutes(dayEntries);
    }).reduce((a, b) => a + b, 0);

    const weeklyExpected = 5 * expectedDayMinutes(profile || {});
    const weeklyDiff = weeklyWorked - weeklyExpected;

    // Urlaub laden
    useEffect(() => {
        if (!profile) return;
        fetchVacations();
    }, [profile]);

    async function fetchVacations() {
        try {
            const res = await api.get('/api/vacation/my');
            setVacationRequests(res.data || []);
        } catch (err) {
            console.error("Fehler beim Laden der Urlaube:", err);
        }
    }

    // Korrekturen laden
    useEffect(() => {
        if (!profile) return;
        fetchCorrections();
    }, [profile]);

    async function fetchCorrections() {
        try {
            const res = await api.get(`/api/correction/my?username=${profile.username}`);
            setCorrectionRequests(res.data || []);
        } catch (err) {
            console.error('Fehler beim Laden der Korrekturanträge', err);
        }
    }

    // Korrektur-Modal öffnen
    function openCorrectionModal(dateStr) {
        setCorrectionDate(dateStr);
        setCorrectionData({
            workStart: "",
            breakStart: "",
            breakEnd: "",
            workEnd: "",
            reason: ""
        });
        setShowCorrectionModal(true);
    }

    async function handleCorrectionSubmit(e) {
        e.preventDefault();
        const { workStart, breakStart, breakEnd, workEnd, reason } = correctionData;
        const desiredStart = `${correctionDate}T${workStart}`;
        const desiredEnd   = `${correctionDate}T${workEnd}`;

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

    // Druck / PDF
    async function handlePrintReport() {
        if (!printStartDate || !printEndDate) {
            notify("Zeitraum fehlt");
            return;
        }
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

            const rows = (data || []).map(e => [
                e.date,
                e.workStart || "-",
                e.breakStart || "-",
                e.breakEnd || "-",
                e.workEnd || "-"
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

    // Falls Profil noch nicht geladen:
    if (!profile) {
        return (
            <div className="user-dashboard">
                <Navbar />
                <p>Loading…</p>
            </div>
        );
    }

    return (
        <div className="percentage-dashboard scoped-dashboard">
            <Navbar />

            <header className="dashboard-header">
                <h2>Percentage‑Dashboard</h2>
                <div className="personal-info">
                    <p>
                        <strong>{t('usernameLabel')}:</strong> {profile.username}
                    </p>
                    <p>
                        <strong>Arbeits‑%:</strong> {profile.workPercentage}%
                    </p>
                    <p>
                        <strong>{t('expected')}:</strong> {minutesToHours(weeklyExpected)}
                    </p>
                    <p>
                        <strong>{t('weekBalance')}:</strong> {minutesToHours(weeklyDiff)}
                    </p>
                    {profile.trackingBalanceInMinutes != null && (
                        <p className="overtime-info">
                            <strong>{t('overtimeBalance')}:</strong> {minutesToHours(profile.trackingBalanceInMinutes)}
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

            {/* Wochenübersicht */}
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

            {/* Urlaub */}
            <PercentageVacationSection
                t={t}
                userProfile={profile}
                vacationRequests={vacationRequests}
                onRefreshVacations={fetchVacations}
            />

            {/* Korrekturen */}
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

            {/* Print-Modal */}
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

            {/* Correction-Modal */}
            <PercentageCorrectionModal
                visible={showCorrectionModal}
                correctionDate={correctionDate}
                correctionData={correctionData}
                handleCorrectionInputChange={(e) =>
                    setCorrectionData({ ...correctionData, [e.target.name]: e.target.value })
                }
                handleCorrectionSubmit={handleCorrectionSubmit}
                onClose={() => setShowCorrectionModal(false)}
                t={t} // <-- falls Übersetzung gewünscht
            />
        </div>
    );
};

export default PercentageDashboard;
