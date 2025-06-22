// src/pages/UserDashboard/UserDashboard.jsx
import React, { useState, useEffect, useRef, useCallback } from 'react';
import Navbar from '../../components/Navbar';
import VacationCalendar from '../../components/VacationCalendar';
import { useAuth } from '../../context/AuthContext';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import api from '../../utils/api';
import { parseISO } from 'date-fns';

import '../../styles/global.css';
import '../../styles/UserDashboardScoped.css';
import "jspdf-autotable"; // Für PDF Export
import jsPDF from "jspdf"; // Für PDF Export
import autoTable from "jspdf-autotable"; // Importiere autoTable explizit

import HourlyDashboard from '../../pages/HourlyDashboard/HourlyDashboard.jsx'; // Für den Fall, dass ein User stündlich ist

import {
    getMondayOfWeek,
    addDays,
    formatLocalDate,
    formatDate,
    formatTime,
    minutesToHHMM,
    getExpectedHoursForDay,
    isLateTime,
    formatPunchedTimeFromEntry,
} from './userDashUtils';

import UserCorrectionModal from './UserCorrectionModal';
import UserCorrectionsPanel from './UserCorrectionsPanel';
import PrintReportModal from "../../components/PrintReportModal.jsx";

function UserDashboard() {
    const { currentUser, fetchCurrentUser } = useAuth();
    const { notify } = useNotification();
    const { t } = useTranslation();

    const [userProfile, setUserProfile] = useState(null);
    const [dailySummaries, setDailySummaries] = useState([]);
    const [vacationRequests, setVacationRequests] = useState([]);
    const [correctionRequests, setCorrectionRequests] = useState([]);
    const [sickLeaves, setSickLeaves] = useState([]);
    const [holidaysForUserCanton, setHolidaysForUserCanton] = useState({ data: {}, year: null, canton: null });

    const [punchMessage, setPunchMessage] = useState('');
    const lastPunchTimeRef = useRef(0);

    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));

    const [showCorrectionsPanel, setShowCorrectionsPanel] = useState(false);
    const [showAllCorrections, setShowAllCorrections] = useState(false);
    const [selectedCorrectionMonday, setSelectedCorrectionMonday] = useState(getMondayOfWeek(new Date()));

    const [printModalVisible, setPrintModalVisible] = useState(false);
    const [printStartDate, setPrintStartDate] = useState(formatLocalDate(new Date(new Date().getFullYear(), new Date().getMonth(), 1)));
    const [printEndDate, setPrintEndDate] = useState(formatLocalDate(new Date(new Date().getFullYear(), new Date().getMonth() + 1, 0)));

    const [showCorrectionModal, setShowCorrectionModal] = useState(false);
    const [correctionDate, setCorrectionDate] = useState("");
    const [dailySummaryForCorrection, setDailySummaryForCorrection] = useState(null);

    const defaultExpectedHours = userProfile?.dailyWorkHours ?? 8.5;

    const loadProfileAndInitialData = useCallback(async () => {
        try {
            const profile = await fetchCurrentUser();
            if (profile && Object.keys(profile).length > 0) {
                if (profile.weeklySchedule && !Array.isArray(profile.weeklySchedule)) {
                    profile.weeklySchedule = [profile.weeklySchedule];
                }
                if (!profile.weeklySchedule || profile.weeklySchedule.length === 0) {
                    profile.weeklySchedule = [{ monday: 8.5, tuesday: 8.5, wednesday: 8.5, thursday: 8.5, friday: 8.5, saturday: 0, sunday: 0 }];
                    profile.scheduleCycle = 1;
                }
                setUserProfile(profile);
            } else {
                throw new Error("User profile could not be loaded.");
            }
        } catch (err) {
            console.error(t('personalData.errorLoading'), err);
            notify(t('errors.fetchProfileError', 'Fehler beim Laden des Profils.'), 'error');
        }
    }, [fetchCurrentUser, t, notify]);

    useEffect(() => {
        loadProfileAndInitialData();
    }, [loadProfileAndInitialData]);

    const fetchHolidaysForUser = useCallback(async (year, cantonAbbreviation) => {
        const cantonKey = cantonAbbreviation || 'GENERAL';
        if (holidaysForUserCanton.year === year && holidaysForUserCanton.canton === cantonKey) {
            return; // Bereits für dieses Jahr und Kanton geladen
        }
        try {
            const params = { year, cantonAbbreviation: cantonAbbreviation || '', startDate: `${year}-01-01`, endDate: `${year}-12-31` };
            const response = await api.get('/api/holidays/details', { params });
            setHolidaysForUserCanton({ data: response.data || {}, year, canton: cantonKey });
        } catch (error) {
            console.error(t('errors.fetchHolidaysError', 'Fehler beim Laden der Feiertage:'), error);
            setHolidaysForUserCanton({ data: {}, year, canton: cantonKey });
        }
    }, [t, holidaysForUserCanton]);

    const fetchDataForUser = useCallback(async () => {
        if (!userProfile?.username) return;
        try {
            const [resSummaries, resVacation, resCorr, resSick] = await Promise.all([
                api.get(`/api/timetracking/history?username=${userProfile.username}`),
                api.get('/api/vacation/my'),
                api.get(`/api/correction/my?username=${userProfile.username}`),
                api.get('/api/sick-leave/my')
            ]);
            setDailySummaries(Array.isArray(resSummaries.data) ? resSummaries.data : []);
            setVacationRequests(Array.isArray(resVacation.data) ? resVacation.data : []);
            setCorrectionRequests(Array.isArray(resCorr.data) ? resCorr.data : []);
            setSickLeaves(Array.isArray(resSick.data) ? resSick.data : []);
        } catch (err) {
            console.error("Fehler beim Laden der Benutzerdaten (UserDashboard):", err);
            notify(t('errors.fetchUserDataError', 'Fehler beim Laden der Benutzerdaten.'), 'error');
        }
    }, [userProfile, notify, t]);

    useEffect(() => {
        if (userProfile) {
            fetchDataForUser();
            // Feiertage laden basierend auf dem Kanton des Users, falls vorhanden
            const cantonAbbr = userProfile.company?.cantonAbbreviation || userProfile.companyCantonAbbreviation;
            if (cantonAbbr) {
                fetchHolidaysForUser(selectedMonday.getFullYear(), cantonAbbr);
            } else {
                fetchHolidaysForUser(selectedMonday.getFullYear(), ''); // Fallback für allgemeine Feiertage
            }
        }
    }, [userProfile, fetchDataForUser, fetchHolidaysForUser, selectedMonday]);

    // Feiertage neu laden, wenn sich das Jahr des selectedMonday ändert
    useEffect(() => {
        if (userProfile) {
            const cantonAbbr = userProfile.company?.cantonAbbreviation || userProfile.companyCantonAbbreviation;
            fetchHolidaysForUser(selectedMonday.getFullYear(), cantonAbbr || '');
        }
    }, [selectedMonday, userProfile, fetchHolidaysForUser]);


    useEffect(() => {
        const interval = setInterval(doNfcCheck, 2000);
        return () => clearInterval(interval);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    async function doNfcCheck() {
        try {
            const response = await fetch(`${import.meta.env.VITE_API_BASE_URL}/api/nfc/read/1`);
            if (!response.ok) return;
            const json = await response.json();
            if (json.status !== 'success' || !json.data) return;
            const cardUser = json.data;
            if (!cardUser) return;

            if (Date.now() - lastPunchTimeRef.current < 5000) return;
            lastPunchTimeRef.current = Date.now();

            showPunchMessage(`${t('login.stamped', 'Eingestempelt')}: ${cardUser}`);
            await api.post('/api/timetracking/punch', null, { params: { username: cardUser, source: 'NFC_SCAN' } });
            if (userProfile && cardUser === userProfile.username) {
                fetchDataForUser();
                loadProfileAndInitialData();
            }
        } catch (err) {
            console.error('NFC error', err);
        }
    }

    function showPunchMessage(msg) {
        setPunchMessage(msg);
        setTimeout(() => setPunchMessage(''), 3000);
    }

    async function handleManualPunch() {
        if (!userProfile) return;
        try {
            const response = await api.post('/api/timetracking/punch', null, {
                params: { username: userProfile.username, source: 'MANUAL_PUNCH' }
            });
            const newEntry = response.data;
            showPunchMessage(`${t("manualPunchMessage")} ${userProfile.username} (${t('punchTypes.' + newEntry.punchType, newEntry.punchType)} @ ${formatTime(new Date(newEntry.entryTimestamp))})`);
            fetchDataForUser();
            loadProfileAndInitialData();
        } catch (err) {
            console.error('Punch-Fehler:', err);
            notify(t("manualPunchError", "Fehler beim manuellen Stempeln."), 'error');
        }
    }

    const weekDates = Array.from({ length: 7 }, (_, i) => addDays(selectedMonday, i));

    const weeklyExpectedMins = weekDates.reduce((sum, d) => {
        const expHours = getExpectedHoursForDay(
            d,
            userProfile,
            defaultExpectedHours,
            holidaysForUserCanton?.data,
            vacationRequests,
            sickLeaves
        );
        return sum + Math.round((expHours || 0) * 60);
    }, 0);
    const weeklyExpectedStr = minutesToHHMM(weeklyExpectedMins);

    const weeklyActualWorkedMinutes = weekDates.reduce((acc, date) => {
        const isoDate = formatLocalDate(date);
        const summaryForDay = dailySummaries.find(s => s.date === isoDate);
        return acc + (summaryForDay?.workedMinutes || 0);
    },0);
    const weeklyDiffStr = minutesToHHMM(weeklyActualWorkedMinutes - weeklyExpectedMins);
    const overtimeBalanceStr = minutesToHHMM(userProfile?.trackingBalanceInMinutes || 0);

    const sortedCorrections = (showAllCorrections ? correctionRequests : correctionRequests.filter(req => {
        if (!req.requestDate) return false;
        const reqDate = parseISO(req.requestDate);
        return reqDate >= selectedMonday && reqDate < addDays(selectedMonday, 7);
    }))
        .slice()
        .sort((a, b) => {
            const dateA = a.requestDate ? parseISO(a.requestDate) : 0;
            const dateB = b.requestDate ? parseISO(b.requestDate) : 0;
            return dateB - dateA;
        });

    function openCorrectionModalForDay(dateObj) {
        const isoDate = formatLocalDate(dateObj);
        setCorrectionDate(isoDate);
        const summaryForDay = dailySummaries.find(s => s.date === isoDate);
        setDailySummaryForCorrection(summaryForDay || { date: isoDate, entries: [] });
        setShowCorrectionModal(true);
    }

    const fetchCorrectionRequests = useCallback(async () => {
        if (!currentUser || !currentUser.username) {
            return;
        }
        try {
            // Die URL braucht keine Datums-Parameter mehr
            const response = await api.get(`/api/correction/user/${currentUser.username}`);
            setCorrectionRequests(response.data);
        } catch (error) {
            console.error("Fehler beim Abrufen der Korrekturanträge:", error);
            notify("Korrekturanträge konnten nicht geladen werden.", 'error');
        }
    }, [currentUser, notify]); // Abhängigkeiten bleiben gleich

    const handleCorrectionSubmit = async (entries, reason) => {
        // Prüfen, ob alle notwendigen Daten vorhanden sind
        if (!correctionDate || !entries || entries.length === 0) {
            notify(t('hourlyDashboard.addEntryFirst'), 'error');
            return;
        }
        if (!currentUser || !currentUser.username) {
            notify(t('hourlyDashboard.userNotFound'), 'error');
            return;
        }

        // Erstellt eine Liste von Promises für jeden einzelnen Korrekturantrag
        const correctionPromises = entries.map(entry => {
            const desiredTimestamp = `${correctionDate}T${entry.time}:00`;
            const desiredPunchType = entry.type;

            // Alle Parameter für die URL vorbereiten, wie vom Backend jetzt verlangt
            const params = new URLSearchParams({
                username: currentUser.username,
                reason: reason,
                requestDate: correctionDate,
                desiredTimestamp: desiredTimestamp,
                desiredPunchType: desiredPunchType
                // targetEntryId ist optional und wird hier weggelassen
            });

            // API-Aufruf mit leerem Body, da alle Daten in der URL sind
            return api.post(`/api/correction/create?${params.toString()}`, null);
        });

        try {
            // Wartet, bis alle Anfragen parallel gesendet wurden
            await Promise.all(correctionPromises);

            notify(t('userDashboard.correctionSuccess'), 'success');
            setShowCorrectionModal(false);
            fetchCorrectionRequests(selectedCorrectionMonday);

        } catch (error) {
            console.error('Fehler beim Absenden der Korrekturanträge:', error);
            const errorMsg = error.response?.data?.message || 'Ein oder mehrere Anträge konnten nicht gesendet werden.';
            notify(errorMsg, 'error');
        }
    };


    async function handlePrintReport() {
        if (!printStartDate || !printEndDate || !userProfile) {
            notify(t("missingDateRange", "Zeitraum oder Benutzerprofil fehlt."), 'error');
            return;
        }
        setPrintModalVisible(false);

        const summariesToPrint = dailySummaries.filter(summary =>
            summary.date >= printStartDate && summary.date <= printEndDate
        ).sort((a,b) => parseISO(a.date) - parseISO(b.date));

        const doc = new jsPDF("p", "mm", "a4");
        doc.setFontSize(14);
        doc.text(`Zeitenbericht für ${userProfile.firstName} ${userProfile.lastName} (${userProfile.username})`, 14, 15);
        doc.setFontSize(11);
        doc.text(`Zeitraum: ${formatDate(printStartDate)} – ${formatDate(printEndDate)}`, 14, 22);

        const tableBody = summariesToPrint.map(summary => {
            const displayDate = formatDate(summary.date);
            const primary = summary.primaryTimes || { firstStartTime: null, lastEndTime: null, isOpen: false};
            const workStart  = primary.firstStartTime ? primary.firstStartTime.substring(0,5) : "-";
            const workEnd    = primary.lastEndTime ? primary.lastEndTime.substring(0,5) : (primary.isOpen ? "OFFEN" : "-");
            const breakTimeStr = minutesToHHMM(summary.breakMinutes);
            const totalWorkedStr = minutesToHHMM(summary.workedMinutes);
            const punches = summary.entries.map(e => `${t('punchTypes.'+e.punchType, e.punchType).substring(0,1)}:${formatTime(e.entryTimestamp)}${e.source === 'SYSTEM_AUTO_END' && !e.correctedByUser ? '(A)' : ''}`).join(' | ');

            return [displayDate, workStart, workEnd, breakTimeStr, totalWorkedStr, punches, summary.dailyNote || ""];
        });

        autoTable(doc, {
            head: [["Datum", "Start", "Ende", "Pause", "Arbeit", "Stempelungen", "Notiz"]],
            body: tableBody,
            startY: 30,
            margin: { left: 10, right: 10 },
            styles: { fontSize: 7, cellPadding: 1.5, overflow: 'linebreak' },
            headStyles: { fillColor: [71, 91, 255], textColor: 255, fontStyle: "bold", halign: "center" },
            columnStyles: {
                0: { cellWidth: 18 }, 1: { cellWidth: 12 }, 2: { cellWidth: 12 },
                3: { cellWidth: 15 }, 4: { cellWidth: 15 }, 5: { cellWidth: 'auto'},
                6: { cellWidth: 40 }
            },
            didDrawPage: (dataHooks) => {
                doc.setFontSize(8);
                doc.text(`Seite ${dataHooks.pageNumber} von ${doc.internal.getNumberOfPages()}`, doc.internal.pageSize.getWidth() - 10, doc.internal.pageSize.getHeight() - 10, { align: "right" });
            }
        });
        doc.save(`Zeitenbericht_${userProfile.username}_${printStartDate}_bis_${printEndDate}.pdf`);
    }

    if (!userProfile) {
        return (
            <div className="user-dashboard scoped-dashboard">
                <Navbar />
                <p className="loading-message">{t("loading", "Lade Benutzerprofil...")}</p>
            </div>
        );
    }
    if (userProfile.isHourly) {
        return <HourlyDashboard />;
    }
    if (userProfile.isPercentage) {
        // Annahme: Es gibt eine PercentageDashboard Komponente
        // import PercentageDashboard from '../PercentageDashboard/PercentageDashboard';
        // return <PercentageDashboard />;
        // Für dieses Beispiel leite auf eine dedizierte Route weiter oder zeige Nachricht
        // Da das PercentageDashboard eigene Logik hat, ist eine eigene Komponente/Route besser
        // return <Navigate to="/percentage-dashboard" replace />; // Besser wäre, es direkt hier zu rendern
    }

    return (
        <> {/* React Fragment oder ein neutrales <div> als äußeren Container nutzen */}
            <Navbar /> {/* NAVBAR IST JETZT AUSSERHALB DES SCOPED-DASHBOARD DIVS */}
            <div className="user-dashboard scoped-dashboard">
                <header className="dashboard-header">
                <h2>{t("title")}</h2>
                <div className="personal-info">
                    <p><strong>{t("usernameLabel")}:</strong> {userProfile.username}</p>
                    <p><strong>{t('expected')}:</strong> {weeklyExpectedStr}</p>
                    <p><strong>{t('overtimeBalance')}:</strong> <span className={userProfile.trackingBalanceInMinutes < 0 ? 'balance-negative': 'balance-positive'}>{overtimeBalanceStr}</span></p>
                </div>
            </header>

            {punchMessage && <div className="punch-message">{punchMessage}</div>}

            <section className="punch-section content-section">
                <h3>{t("manualPunchTitle")}</h3>
                <button onClick={handleManualPunch} className="button-primary">
                    {t("manualPunchButton")}
                </button>
            </section>

            <section className="time-tracking-section content-section">
                <h3 className="section-title">{t("weeklyOverview")}</h3>
                <div className="week-navigation">
                    <button onClick={() => setSelectedMonday(prev => addDays(prev, -7))} className="button-secondary">
                        ← {t("prevWeek")}
                    </button>
                    <input
                        type="date"
                        value={formatLocalDate(selectedMonday)}
                        onChange={(e) => {
                            const pickedDate = e.target.value ? parseISO(e.target.value) : null;
                            if (pickedDate && !isNaN(pickedDate.getTime())) {
                                setSelectedMonday(getMondayOfWeek(pickedDate));
                            }
                        }}
                    />
                    <button onClick={() => setSelectedMonday(prev => addDays(prev, 7))} className="button-secondary">
                        {t("nextWeek")} →
                    </button>
                </div>
                <div className="weekly-summary">
                    <p><strong>{t('actualTime')}:</strong> {minutesToHHMM(weeklyActualWorkedMinutes)}</p>
                    <p><strong>{t('expected')}:</strong> {weeklyExpectedStr}</p>
                    <p>
                        <strong>{t('weekBalance')}:</strong>
                        <span className={weeklyActualWorkedMinutes - weeklyExpectedMins < 0 ? 'balance-negative' : 'balance-positive'}>
                            {weeklyDiffStr}
                        </span>
                    </p>
                </div>

                <div className="week-display">
                    {weekDates.map((dayObj) => {
                        const isoDate = formatLocalDate(dayObj);
                        const summary = dailySummaries.find(s => s.date === isoDate);
                        const dayName = dayObj.toLocaleDateString('de-DE', { weekday: 'long' });
                        const formattedDisplayDate = formatDate(dayObj);

                        const vacationToday = vacationRequests.find(v => v.approved && isoDate >= v.startDate && isoDate <= v.endDate);
                        const sickToday = sickLeaves.find(sl => isoDate >= sl.startDate && isoDate <= sl.endDate);
                        const isHolidayToday = holidaysForUserCanton?.data && holidaysForUserCanton.data[isoDate];

                        const expectedMinsToday = userProfile.isPercentage ? 0 : Math.round(getExpectedHoursForDay(
                            dayObj,
                            userProfile,
                            defaultExpectedHours,
                            holidaysForUserCanton?.data,
                            vacationRequests,
                            sickLeaves
                        ) * 60);
                        const dailyDiffMinutes = summary ? summary.workedMinutes - expectedMinsToday : (isHolidayToday || vacationToday || sickToday ? 0 : 0 - expectedMinsToday) ;


                        return (
                            <div key={isoDate}
                                 className={`day-card ${summary?.needsCorrection ? 'needs-correction-highlight' : ''} ${vacationToday ? 'vacation-day' : ''} ${sickToday ? 'sick-day' : ''} ${isHolidayToday ? 'holiday-day' : ''}`}>
                                <div className="day-card-header">
                                    <h4>{dayName}, {formattedDisplayDate}</h4>
                                    <div className="day-card-meta">
                                        {!userProfile.isPercentage && <span className="expected-hours">({t("expectedWorkHours")}: {minutesToHHMM(expectedMinsToday)})</span>}
                                        {summary && !userProfile.isPercentage && <span className={`daily-diff ${dailyDiffMinutes < 0 ? 'balance-negative' : 'balance-positive'}`}>({t("diffToday")}: {minutesToHHMM(dailyDiffMinutes)})</span>}
                                    </div>
                                </div>

                                {isHolidayToday ? (
                                    <div className="holiday-indicator day-card-info">
                                        <span role="img" aria-label="Feiertag">🎉</span> {holidaysForUserCanton.data[isoDate]}
                                    </div>
                                ) : vacationToday ? (
                                    <div className="vacation-indicator day-card-info">
                                        <span role="img" aria-label="Urlaub">🏖️</span> {t('onVacation', 'Im Urlaub')}
                                        {vacationToday.halfDay && ` (${t('halfDayShort', '½ Tag')})`}
                                        {vacationToday.usesOvertime && ` (${t('overtimeVacationShort', 'ÜS')})`}
                                    </div>
                                ) : sickToday ? (
                                    <div className="sick-leave-indicator day-card-info">
                                        <span role="img" aria-label="Krank">⚕️</span> {t('sickLeave.sick', 'Krank')}
                                        {sickToday.halfDay && ` (${t('halfDayShort', '½ Tag')})`}
                                        {sickToday.comment && <span className="info-badge" title={sickToday.comment}>📝</span>}
                                    </div>
                                ) : (!summary || summary.entries.length === 0) ? (
                                    <p className="no-entries">{t("noEntries")}</p>
                                ) : (
                                    <>
                                        <ul className="time-entry-list">
                                            {summary.entries.map(entry => (
                                                <li key={entry.id || entry.entryTimestamp}>
                                                    <span className="entry-label">{t(`punchTypes.${entry.punchType}`, entry.punchType)}:</span>
                                                    <span className={`entry-time ${isLateTime(formatTime(entry.entryTimestamp)) ? 'late-time' : ''}`}>
                                                        {formatPunchedTimeFromEntry(entry)}
                                                        {entry.source === 'SYSTEM_AUTO_END' && !entry.correctedByUser && <span className="auto-end-indicator" title={t('messages.autoEndedTooltip', 'Automatisch beendet')}> (A)</span>}
                                                    </span>
                                                </li>
                                            ))}
                                        </ul>
                                        <div className="daily-summary-times">
                                            <p><strong>{t('actualTime')}:</strong> {minutesToHHMM(summary.workedMinutes)}</p>
                                            <p><strong>{t('breakTime')}:</strong> {minutesToHHMM(summary.breakMinutes)}</p>
                                        </div>
                                    </>
                                )}
                                {summary?.needsCorrection && !vacationToday && !sickToday && !isHolidayToday && (
                                    <p className="needs-correction-text">{t('messages.correctionNeeded', 'Bitte korrigieren!')}</p>
                                )}
                                {!vacationToday && !sickToday && !isHolidayToday && (
                                    <div className="correction-button-row">
                                        <button onClick={() => openCorrectionModalForDay(dayObj)} className="button-secondary">
                                            {t("submitCorrectionRequest")}
                                        </button>
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </div>
            </section>

            <VacationCalendar
                vacationRequests={vacationRequests}
                userProfile={userProfile}
                onRefreshVacations={fetchDataForUser}
            />

            <UserCorrectionsPanel
                t={t}
                showCorrectionsPanel={showCorrectionsPanel}
                setShowCorrectionsPanel={setShowCorrectionsPanel}
                selectedCorrectionMonday={selectedCorrectionMonday}
                setSelectedCorrectionMonday={setSelectedCorrectionMonday}
                showAllCorrections={showAllCorrections}
                setShowAllCorrections={setShowAllCorrections}
                sortedCorrections={sortedCorrections}
            />

            <div className="print-report-container">
                <button onClick={() => setPrintModalVisible(true)} className="button-primary">
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
                cssScope="user"
            />

            <UserCorrectionModal
                visible={showCorrectionModal}
                correctionDate={correctionDate}
                dailySummaryForCorrection={dailySummaryForCorrection}
                onSubmitCorrection={handleCorrectionSubmit}
                onClose={() => setShowCorrectionModal(false)}
                t={t}
            />
        </div>
        </>
    );
}

export default UserDashboard;