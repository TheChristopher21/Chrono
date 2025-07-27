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
import jsPDF from "jspdf"; // Für PDF Export
import autoTable from "jspdf-autotable"; // Importiere autoTable explizit

import HourlyDashboard from '../../pages/HourlyDashboard/HourlyDashboard.jsx'; // Für den Fall, dass ein User stündlich ist
import PercentageDashboard from '../../pages/PercentageDashboard/PercentageDashboard.jsx';
import { useCustomers } from '../../context/CustomerContext';


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

import CorrectionModal from '../../components/CorrectionModal';
import DayCard from '../../components/DayCard';
import TrendChart from '../../components/TrendChart';
import UserCorrectionsPanel from './UserCorrectionsPanel';
import PrintReportModal from "../../components/PrintReportModal.jsx";
import CustomerTimeAssignModal from '../../components/CustomerTimeAssignModal';

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

    const { customers, fetchCustomers } = useCustomers();
    const [recentCustomers, setRecentCustomers] = useState([]);
    const [projects, setProjects] = useState([]);
    const [selectedCustomerId, setSelectedCustomerId] = useState('');
    const [selectedProjectId, setSelectedProjectId] = useState('');
    const [modalInfo, setModalInfo] = useState({ isVisible: false, day: null, summary: null });

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
                if (profile.lastCustomerId && !selectedCustomerId) {
                    setSelectedCustomerId(String(profile.lastCustomerId));
                }
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

    useEffect(() => {
        const trackingEnabled = userProfile?.customerTrackingEnabled ?? currentUser?.customerTrackingEnabled;
        if (trackingEnabled) {
            fetchCustomers();
            api.get('/api/customers/recent')
                .then(res => setRecentCustomers(Array.isArray(res.data) ? res.data : []))
                .catch(err => console.error('Error loading customers', err));
            api.get('/api/projects')
                .then(res => setProjects(Array.isArray(res.data) ? res.data : []))
                .catch(err => console.error('Error loading projects', err));
        } else {
            setRecentCustomers([]);
            setProjects([]);
        }
    }, [userProfile, currentUser, fetchCustomers]);


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
            const params = { username: userProfile.username, source: 'MANUAL_PUNCH' };
            if (selectedCustomerId) params.customerId = selectedCustomerId;
            if (selectedProjectId) params.projectId = selectedProjectId;
            const response = await api.post('/api/timetracking/punch', null, { params });
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

    const chartData = weekDates.map(d => {
        const iso = formatLocalDate(d);
        const summary = dailySummaries.find(s => s.date === iso);
        const expectedMins = Math.round(getExpectedHoursForDay(
            d,
            userProfile,
            defaultExpectedHours,
            holidaysForUserCanton?.data,
            vacationRequests,
            sickLeaves
        ) * 60);
        return { date: iso, workedMinutes: summary ? summary.workedMinutes : 0, expectedMinutes: expectedMins };
    });

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
            notify(t('userManagement.errorLoadingCorrections'), 'error');
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
        doc.text(`${t('printReport.title')} ${t('for')} ${userProfile.firstName} ${userProfile.lastName} (${userProfile.username})`, 14, 15);
        doc.setFontSize(11);
        doc.text(`${t('printReport.periodLabel')}: ${formatDate(printStartDate)} – ${formatDate(printEndDate)}`, 14, 22);

        const tableBody = summariesToPrint.map(summary => {
            const displayDate = formatDate(summary.date);
            const primary = summary.primaryTimes || { firstStartTime: null, lastEndTime: null, isOpen: false};
            const workStart  = primary.firstStartTime ? primary.firstStartTime.substring(0,5) : "-";
            const workEnd    = primary.lastEndTime ? primary.lastEndTime.substring(0,5) : (primary.isOpen ? t('printReport.open') : "-");
            const breakTimeStr = minutesToHHMM(summary.breakMinutes);
            const totalWorkedStr = minutesToHHMM(summary.workedMinutes);
            const punches = summary.entries.map(e => `${t('punchTypes.'+e.punchType, e.punchType).substring(0,1)}:${formatTime(e.entryTimestamp)}${e.source === 'SYSTEM_AUTO_END' && !e.correctedByUser ? '(A)' : ''}`).join(' | ');

            return [displayDate, workStart, workEnd, breakTimeStr, totalWorkedStr, punches, summary.dailyNote || ""];
        });

        autoTable(doc, {
            head: [[t('printReport.date'), t('printReport.workStart'), t('printReport.workEnd'), t('printReport.pause'), t('printReport.total', 'Arbeit'), t('printReport.punches'), t('printReport.note')]],
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
                doc.text(`${t('page')} ${dataHooks.pageNumber} ${t('of')} ${doc.internal.getNumberOfPages()}`, doc.internal.pageSize.getWidth() - 10, doc.internal.pageSize.getHeight() - 10, { align: "right" });
            }
        });
        doc.save(`Zeitenbericht_${userProfile.username}_${printStartDate}_bis_${printEndDate}.pdf`);
    }

    if (!userProfile) {
        return (
            <div className="user-dashboard scoped-dashboard">
                <Navbar />
                <div className="skeleton-card"></div>
                <div className="skeleton-card"></div>
                <div className="skeleton-card"></div>
            </div>
        );
    }
    if (userProfile?.username && currentUser?.username && userProfile.username === currentUser.username) {
        if (userProfile.isHourly) {
            return <HourlyDashboard />;
        }
        if (userProfile.isPercentage) {
            return <PercentageDashboard />;
        }
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
                    <p>
                        <strong>{t('overtimeBalance')}:</strong>
                        <span className={userProfile.trackingBalanceInMinutes < 0 ? 'balance-negative': 'balance-positive'}>{overtimeBalanceStr}</span>
                        <span className="info-badge" title={t('overtimeBalanceInfo')}>ℹ️</span>
                    </p>
                </div>
            </header>

            {punchMessage && <div className="punch-message">{punchMessage}</div>}

            <section className="punch-section content-section">
                <h3>{t("manualPunchTitle")}</h3>
                {(userProfile?.customerTrackingEnabled ?? currentUser?.customerTrackingEnabled) && (
                    <>
                        <select value={selectedCustomerId} onChange={e => setSelectedCustomerId(e.target.value)}>
                            <option value="">{t('noCustomer')}</option>
                            {recentCustomers.length > 0 && (
                                <optgroup label={t('recentCustomers')}>
                                    {recentCustomers.map(c => (
                                        <option key={'r'+c.id} value={c.id}>{c.name}</option>
                                    ))}
                                </optgroup>
                            )}
                            {customers.map(c => (
                                <option key={c.id} value={c.id}>{c.name}</option>
                            ))}
                        </select>
                        <select value={selectedProjectId} onChange={e => setSelectedProjectId(e.target.value)}>
                            <option value="">{t('noProject','Kein Projekt')}</option>
                            {projects.map(p => (
                                <option key={p.id} value={p.id}>{p.name}</option>
                            ))}
                        </select>
                    </>
                )}
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
                    <div className="summary-item">
                        <span className="summary-label">{t('actualTime')}</span>
                        <span className="summary-value">{minutesToHHMM(weeklyActualWorkedMinutes)}</span>
                    </div>
                    <div className="summary-item">
                        <span className="summary-label">{t('expected')}</span>
                        <span className="summary-value">{weeklyExpectedStr}</span>
                    </div>
                    <div className="summary-item">
                        <span className="summary-label">{t('weekBalance')}</span>
                        <span className={`summary-value ${weeklyActualWorkedMinutes - weeklyExpectedMins < 0 ? 'balance-negative' : 'balance-positive'}`}>{weeklyDiffStr}</span>
                    </div>
                </div>

                <TrendChart data={chartData} />

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
                            <DayCard
                                key={isoDate}
                                t={t}
                                dayName={dayName}
                                displayDate={formattedDisplayDate}
                                summary={summary}
                                holidayName={holidaysForUserCanton.data?.[isoDate]}
                                vacationInfo={vacationToday}
                                sickInfo={sickToday}
                                expectedMinutes={!userProfile.isPercentage ? expectedMinsToday : undefined}
                                diffMinutes={!userProfile.isPercentage ? dailyDiffMinutes : undefined}
                                showCorrection
                                onRequestCorrection={() => openCorrectionModalForDay(dayObj)}
                            >
                                {(userProfile?.customerTrackingEnabled ?? currentUser?.customerTrackingEnabled) && summary && summary.entries.length > 0 && (
                                    <div className="day-card-actions">
                                        <button
                                            onClick={() => setModalInfo({ isVisible: true, day: dayObj, summary })}
                                            className="button-primary-outline"
                                        >
                                            {t('assignCustomer.editButton', 'Kunden & Zeiten bearbeiten')}
                                        </button>
                                    </div>
                                )}
                            </DayCard>
                        );
                    })}
                </div>

                {modalInfo.isVisible && (
                    <CustomerTimeAssignModal
                        t={t}
                        day={modalInfo.day}
                        summary={modalInfo.summary}
                        customers={customers}
                        projects={projects}
                        onClose={() => setModalInfo({ isVisible: false, day: null, summary: null })}
                        onSave={() => {
                            fetchDataForUser();
                            setModalInfo({ isVisible: false, day: null, summary: null });
                        }}
                    />
                )}

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

            <CorrectionModal
                visible={showCorrectionModal}
                correctionDate={correctionDate}
                dailySummary={dailySummaryForCorrection}
                onSubmit={handleCorrectionSubmit}
                onClose={() => setShowCorrectionModal(false)}
                t={t}
            />
        </div>
        </>
    );
}

export default UserDashboard;