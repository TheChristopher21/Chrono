// src/pages/PercentageDashboard/PercentageDashboard.jsx
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
    expectedDayMinutes,
    minutesToHours // Hinzugefügt: Korrekte Funktion importieren
} from './percentageDashUtils'; // Sicherstellen, dass es von hier kommt
import { expandDayRows } from '../UserDashboard/userDashUtils';

import PercentageWeekOverview from './PercentageWeekOverview';
import PercentageVacationSection from './PercentageVacationSection';
import PercentageCorrectionsPanel from './PercentageCorrectionsPanel';
import PercentageCorrectionModal from './PercentageCorrectionModal';
import PrintReportModal from "../../components/PrintReportModal.jsx";

import '../../styles/PercentageDashboardScoped.css';
// Entfernen Sie den falschen Import, falls vorhanden, oder stellen Sie sicher, dass er nicht existiert:
// import {minutesToHours} from "date-fns"; // DIESE ZEILE ENTFERNEN ODER AUSKOMMENTIEREN

const PercentageDashboard = () => {
    const { t } = useTranslation();
    const { notify } = useNotification();
    useNavigate(); // Hook wird aufgerufen, Rückgabewert muss nicht unbedingt verwendet werden, wenn nur für Navigationseffekte

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
    }, [notify]); // notify als Abhängigkeit hinzugefügt

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
    }, []); // Leeres Abhängigkeitsarray, da doNfcCheck keine Props/State verwendet, die sich ändern und einen Neurestart des Intervalls erfordern

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

            setPunchMsg(`Eingestempelt: ${cardUser}`); // Direktes Feedback für den User
            setTimeout(() => setPunchMsg(''), 3000);

            await api.post('/api/timetracking/punch', null, { params: { username: cardUser } });
            // Daten neu laden nach erfolgreichem Punch
            loadEntries();
            loadProfile();
        } catch (err) {
            console.error('NFC‑Fehler', err);
            // Optional: User-Benachrichtigung bei NFC-Fehler
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
        const dayEntries = entries.filter(e => e.startTime && e.startTime.slice(0, 10) === isoDay);
        return computeDayTotalMinutes(dayEntries);
    }).reduce((a, b) => a + b, 0);

    const weeklyExpected = profile ? (5 * expectedDayMinutes(profile)) : 0; // Sicherstellen, dass profile existiert
    const weeklyDiff = weeklyWorked - weeklyExpected;

    // Urlaub laden
    const fetchVacations = useCallback(async () => { // useCallback für Konsistenz
        if (!profile) return;
        try {
            const res = await api.get('/api/vacation/my');
            setVacationRequests(res.data || []);
        } catch (err) {
            console.error("Fehler beim Laden der Urlaube:", err);
            notify("Fehler beim Laden der Urlaubsanträge."); // Notify User
        }
    }, [profile, notify]); // notify als Abhängigkeit

    useEffect(() => {
        fetchVacations();
    }, [fetchVacations]);

    // Korrekturen laden
    const fetchCorrections = useCallback(async () => { // useCallback für Konsistenz
        if (!profile) return;
        try {
            const res = await api.get(`/api/correction/my?username=${profile.username}`);
            setCorrectionRequests(res.data || []);
        } catch (err) {
            console.error('Fehler beim Laden der Korrekturanträge', err);
            notify("Fehler beim Laden der Korrekturanträge."); // Notify User
        }
    }, [profile, notify]); // notify als Abhängigkeit

    useEffect(() => {
        fetchCorrections();
    }, [fetchCorrections]);


    // Korrektur-Modal öffnen
    function openCorrectionModal(dateStr) {
        setCorrectionDate(dateStr);
        // Logik zum Vorbelegen der Zeiten basierend auf 'entries' für 'dateStr'
        const dayEntriesForModal = entries.filter(e => e.startTime && e.startTime.slice(0, 10) === dateStr)
            .sort((a,b) => a.punchOrder - b.punchOrder);

        const workStartEntry = dayEntriesForModal.find(e => e.punchOrder === 1);
        const breakStartEntry = dayEntriesForModal.find(e => e.punchOrder === 2);
        const breakEndEntry = dayEntriesForModal.find(e => e.punchOrder === 3);
        const workEndEntry = dayEntriesForModal.find(e => e.punchOrder === 4);

        setCorrectionData({
            workStart: workStartEntry ? (workStartEntry.workStart || workStartEntry.startTime.slice(11,16)) : "",
            breakStart: breakStartEntry ? (breakStartEntry.breakStart || breakStartEntry.startTime.slice(11,16)) : "",
            breakEnd: breakEndEntry ? (breakEndEntry.breakEnd || breakEndEntry.startTime.slice(11,16)) : "",
            workEnd: workEndEntry ? (workEndEntry.workEnd || workEndEntry.endTime?.slice(11,16) || workEndEntry.startTime.slice(11,16)) : "",
            reason: ""
        });
        setShowCorrectionModal(true);
    }

    async function handleCorrectionSubmit(e) {
        e.preventDefault();
        const { workStart, breakStart, breakEnd, workEnd, reason } = correctionData;
        // Validierung: workStart und workEnd müssen ausgefüllt sein
        if (!workStart || !workEnd) {
            notify("Bitte Arbeitsbeginn und Arbeitsende für die Korrektur angeben.");
            return;
        }
        const desiredStart = `${correctionDate}T${workStart}`;
        const desiredEnd   = `${correctionDate}T${workEnd}`;

        try {
            await api.post('/api/correction/create-full', null, {
                params: {
                    username: profile.username,
                    date: correctionDate,
                    workStart,
                    breakStart: breakStart || null, // Send null if empty
                    breakEnd: breakEnd || null,     // Send null if empty
                    workEnd,
                    reason,
                    desiredStart, // Ist für Backend-Logik, auch wenn es hier redundant wirkt
                    desiredEnd    // Ist für Backend-Logik, auch wenn es hier redundant wirkt
                }
            });
            notify("Korrekturantrag erfolgreich gestellt.");
            fetchCorrections(); // Korrekturen neu laden
            setShowCorrectionModal(false);
        } catch (err) {
            console.error("Fehler beim Absenden des Korrekturantrags:", err);
            const errorMsg = err.response?.data?.message || err.message || "Unbekannter Fehler";
            notify(`Fehler beim Absenden: ${errorMsg}`);
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
                    endDate:   printEndDate,
                },
            });

            const doc = new jsPDF("p", "mm", "a4");
            doc.setFontSize(14);
            doc.text(`Zeitenbericht für ${profile.firstName} ${profile.lastName}`, 14, 15);
            doc.setFontSize(11);
            doc.text(`Zeitraum: ${formatISO(new Date(printStartDate))} – ${formatISO(new Date(printEndDate))}`, 14, 22); // formatISO für konsistente Anzeige

            const rows = (data || []).map(e => [
                formatISO(new Date(e.date)), // Datum formatieren
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
                headStyles: { fillColor: [71, 91, 255], textColor: 255, halign: "center" }, // Primärfarbe für Header
                didDrawPage: (dataHooks) => { // data umbenannt zu dataHooks um Kollision zu vermeiden
                    doc.text(`Seite ${dataHooks.pageNumber}`, doc.internal.pageSize.getWidth() - 14, 10, { align: "right" });
                },
            });
            doc.save(`zeitenbericht_${profile.username}_${printStartDate}_${printEndDate}.pdf`);
        } catch (err) {
            console.error("Fehler beim Generieren des PDF-Berichts:", err);
            notify("Fehler beim Erstellen des PDF-Berichts.");
        }
    }

    // Falls Profil noch nicht geladen:
    if (!profile) {
        return (
            <div className="percentage-dashboard scoped-dashboard"> {/* Scope-Klasse hier hinzugefügt */}
                <Navbar />
                <p style={{textAlign: 'center', marginTop: '2rem'}}>{t("loading", "Lade...")}</p> {/* Einfache Ladeanzeige */}
            </div>
        );
    }

    return (
        <div className="percentage-dashboard scoped-dashboard">
            <Navbar />

            <header className="dashboard-header">
                <h2>{profile.isPercentage ? t("percentageDashboard.title", "Percentage-Dashboard") : t("title", "Mein Dashboard")}</h2>
                <div className="personal-info">
                    <p>
                        <strong>{t('usernameLabel')}:</strong> {profile.username}
                    </p>
                    {profile.isPercentage && (
                        <p>
                            <strong>{t('percentageDashboard.workPercentageLabel', 'Arbeits-%')}:</strong> {profile.workPercentage}%
                        </p>
                    )}
                    <p>

                        <span className={(weeklyDiff ?? 0) < 0 ? 'balance-negative' : 'balance-positive'}>
                            {minutesToHours(weeklyDiff)}
                        </span>
                    </p>
                    {profile.trackingBalanceInMinutes != null && (
                        <p className="overtime-info">
                            <strong>{t('overtimeBalance')}:</strong> {minutesToHours(profile.trackingBalanceInMinutes)}
                            <span className="tooltip-wrapper">
                <span className="tooltip-icon">ℹ️</span>
                <span className="tooltip-box">
                  {t('percentageDashboard.overtimeTooltip', 'Überstunden entstehen, wenn du mehr als dein Tagessoll arbeitest. Du kannst sie später als „Überstundenfrei“ nutzen.')}
                </span>
              </span>
                        </p>
                    )}
                </div>

                <div className="print-report-container">
                    <button onClick={() => setPrintModalVisible(true)} className="button-primary">
                        {t("printReportButton")}
                    </button>
                </div>
            </header>

            {punchMsg && <div className="punch-message">{punchMsg}</div>}

            <PercentageWeekOverview
                // user={profile} // 'user' prop wird nicht verwendet in PercentageWeekOverview
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

            <PercentageVacationSection
                t={t}
                userProfile={profile}
                vacationRequests={vacationRequests}
                onRefreshVacations={fetchVacations}
            />

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

            <PercentageCorrectionModal
                visible={showCorrectionModal}
                correctionDate={correctionDate}
                correctionData={correctionData}
                handleCorrectionInputChange={(e) =>
                    setCorrectionData({ ...correctionData, [e.target.name]: e.target.value })
                }
                handleCorrectionSubmit={handleCorrectionSubmit}
                onClose={() => setShowCorrectionModal(false)}
                t={t}
            />
        </div>
    );
};

export default PercentageDashboard;