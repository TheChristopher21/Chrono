import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import Navbar from '../../components/Navbar';
import ModalOverlay from '../../components/ModalOverlay';
import EditTimeModal from './EditTimeModal';
import { useTranslation } from '../../context/LanguageContext';
import { useNotification } from '../../context/NotificationContext';
import api from '../../utils/api';
import VacationCalendarAdmin from '../../components/VacationCalendarAdmin';
import {
    formatDate,
    formatDateWithWeekday,
    formatTime,
    formatLocalDateYMD,
    getMondayOfWeek,
    addDays,
    minutesToHHMM,
    getDetailedGlobalProblemIndicators,
} from './adminDashboardUtils';
import '../../styles/AdminEmployeeOverviewScoped.css';

const isWorkDay = (dateObj) => {
    const day = dateObj.getDay();
    return day !== 0 && day !== 6;
};

const isDateInRange = (dateString, startDate, endDate) => {
    if (!dateString || !startDate || !endDate) return false;
    return dateString >= startDate && dateString <= endDate;
};

const getRequestStatusLabel = (request, t) => {
    if (request?.approved) return t('approved', 'Genehmigt');
    if (request?.denied) return t('denied', 'Abgelehnt');
    return t('pending', 'Offen');
};

const getRequestStatusClass = (request) => {
    if (request?.approved) return 'ok';
    if (request?.denied) return 'bad';
    return 'pending';
};

const countVacationDays = (vacations) => {
    if (!Array.isArray(vacations) || vacations.length === 0) return 0;
    return vacations.reduce((sum, vacation) => {
        if (!vacation || vacation.usesOvertime) return sum;
        const start = new Date(`${vacation.startDate}T00:00:00`);
        const end = new Date(`${vacation.endDate}T00:00:00`);
        if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) return sum;

        let days = 0;
        for (let cursor = new Date(start); cursor <= end; cursor.setDate(cursor.getDate() + 1)) {
            if (isWorkDay(cursor)) {
                days += vacation.halfDay ? 0.5 : 1;
            }
        }
        return sum + days;
    }, 0);
};

const AdminEmployeeOverviewPage = () => {
    const { username: encodedUsername } = useParams();
    const username = decodeURIComponent(encodedUsername || '');
    const todayYmd = formatLocalDateYMD(new Date());
    const { t } = useTranslation();
    const { notify } = useNotification();

    const [loading, setLoading] = useState(true);
    const [users, setUsers] = useState([]);
    const [dailySummaries, setDailySummaries] = useState([]);
    const [vacations, setVacations] = useState([]);
    const [corrections, setCorrections] = useState([]);
    const [trackingBalances, setTrackingBalances] = useState([]);
    const [sickLeaves, setSickLeaves] = useState([]);

    const [activeRequestTab, setActiveRequestTab] = useState('vacation');
    const [selectedRequest, setSelectedRequest] = useState(null);

    const [quickAction, setQuickAction] = useState(null);
    const [vacationForm, setVacationForm] = useState({ startDate: todayYmd, endDate: todayYmd, halfDay: false });
    const [sickForm, setSickForm] = useState({ startDate: todayYmd, endDate: todayYmd, halfDay: false, comment: '' });

    const [editModalVisible, setEditModalVisible] = useState(false);
    const [editDate, setEditDate] = useState(null);
    const [editDayEntries, setEditDayEntries] = useState([]);
    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));
    const [problemCursor, setProblemCursor] = useState(-1);

    const fetchAllData = useCallback(async () => {
        setLoading(true);
        try {
            const [usersRes, summariesRes, vacationsRes, correctionsRes, balancesRes, sickLeaveRes] = await Promise.all([
                api.get('/api/admin/users'),
                api.get('/api/admin/timetracking/all-summaries'),
                api.get('/api/vacation/all'),
                api.get('/api/correction/all'),
                api.get('/api/admin/timetracking/admin/tracking-balances'),
                api.get('/api/sick-leave/company'),
            ]);

            setUsers(Array.isArray(usersRes.data) ? usersRes.data : []);
            setDailySummaries(Array.isArray(summariesRes.data) ? summariesRes.data : []);
            setVacations(Array.isArray(vacationsRes.data) ? vacationsRes.data : []);
            setCorrections(Array.isArray(correctionsRes.data) ? correctionsRes.data : []);
            setTrackingBalances(Array.isArray(balancesRes.data) ? balancesRes.data : []);
            setSickLeaves(Array.isArray(sickLeaveRes.data) ? sickLeaveRes.data : []);
        } catch (error) {
            console.error('Fehler beim Laden der Mitarbeiter-Übersicht:', error);
            notify(t('adminEmployeeOverview.fetchError', 'Mitarbeiter-Übersicht konnte nicht geladen werden.'), 'error');
        } finally {
            setLoading(false);
        }
    }, [notify, t]);

    useEffect(() => {
        fetchAllData();
    }, [fetchAllData]);

    const employee = useMemo(
        () => users.find((user) => user?.username === username) || null,
        [users, username],
    );

    const employeeSummaries = useMemo(
        () => dailySummaries
            .filter((entry) => entry?.username === username)
            .sort((a, b) => new Date(b.date || 0) - new Date(a.date || 0)),
        [dailySummaries, username],
    );

    const weekDates = useMemo(
        () => Array.from({ length: 7 }, (_, index) => formatLocalDateYMD(addDays(selectedMonday, index))),
        [selectedMonday],
    );

    const weeklySummaries = useMemo(
        () => employeeSummaries.filter((entry) => weekDates.includes(entry?.date)),
        [employeeSummaries, weekDates],
    );

    const weeklyEntriesOverview = useMemo(
        () => weekDates.map((dateString) => {
            const summary = weeklySummaries.find((entry) => entry?.date === dateString);
            const entries = Array.isArray(summary?.entries)
                ? [...summary.entries].sort((a, b) => new Date(a?.entryTimestamp || 0) - new Date(b?.entryTimestamp || 0))
                : [];
            return {
                dateString,
                summary,
                entries,
            };
        }),
        [weekDates, weeklySummaries],
    );

    const employeeBalance = useMemo(
        () => trackingBalances.find((entry) => entry?.username === username) || null,
        [trackingBalances, username],
    );

    const employeeVacations = useMemo(
        () => vacations
            .filter((vacation) => vacation?.username === username)
            .sort((a, b) => new Date(b.startDate || 0) - new Date(a.startDate || 0)),
        [vacations, username],
    );

    const employeeCorrections = useMemo(
        () => corrections
            .filter((correction) => correction?.username === username)
            .sort((a, b) => new Date(b.requestDate || 0) - new Date(a.requestDate || 0)),
        [corrections, username],
    );

    const employeeSickLeaves = useMemo(
        () => sickLeaves
            .filter((entry) => entry?.username === username)
            .sort((a, b) => new Date(b.startDate || 0) - new Date(a.startDate || 0)),
        [sickLeaves, username],
    );

    const todaysSummary = useMemo(
        () => employeeSummaries.find((entry) => entry?.date === todayYmd) || null,
        [employeeSummaries, todayYmd],
    );

    const vacationStats = useMemo(() => {
        const approved = employeeVacations.filter((vacation) => vacation?.approved);
        const planned = employeeVacations.filter((vacation) => !vacation?.approved && !vacation?.denied);
        const annual = Number(employee?.annualVacationDays) || 0;
        const takenDays = countVacationDays(approved);
        const plannedDays = countVacationDays(planned);

        return {
            annual,
            takenDays,
            plannedDays,
            availableDays: Math.max(annual - takenDays - plannedDays, 0),
        };
    }, [employee, employeeVacations]);

    const totalWorkedWeekMinutes = useMemo(
        () => weeklySummaries.reduce((sum, entry) => sum + (entry?.workedMinutes || 0), 0),
        [weeklySummaries],
    );

    const totalBreakWeekMinutes = useMemo(
        () => weeklySummaries.reduce((sum, entry) => sum + (entry?.breakMinutes || 0), 0),
        [weeklySummaries],
    );

    const weeklyTargetMinutes = useMemo(() => {
        const workDays = weekDates.filter((dateString) => {
            const dateObj = new Date(`${dateString}T00:00:00`);
            return isWorkDay(dateObj);
        }).length;
        const perDay = Number(employee?.weeklyHours) > 0 ? (Number(employee.weeklyHours) * 60) / 5 : 0;
        return Math.round(perDay * workDays);
    }, [employee, weekDates]);

    const weeklyDeltaMinutes = totalWorkedWeekMinutes - weeklyTargetMinutes;

    const openItemsCount = useMemo(
        () => employeeVacations.filter((vac) => !vac.approved && !vac.denied).length
            + employeeCorrections.filter((corr) => !corr.approved && !corr.denied).length,
        [employeeVacations, employeeCorrections],
    );

    const currentStatus = useMemo(() => {
        const todayVacation = employeeVacations.find((vac) => !vac.denied && isDateInRange(todayYmd, vac.startDate, vac.endDate));
        if (todayVacation) return { key: 'vacation', label: t('onVacation', 'In Urlaub') };

        const todaySick = employeeSickLeaves.find((entry) => isDateInRange(todayYmd, entry.startDate, entry.endDate));
        if (todaySick) return { key: 'sick', label: t('sickLeave.status.sick', 'Krank') };

        return { key: 'active', label: t('active', 'Aktiv') };
    }, [employeeSickLeaves, employeeVacations, t, todayYmd]);

    const nextAbsence = useMemo(() => {
        const upcoming = [
            ...employeeVacations
                .filter((item) => !item.denied && item.endDate >= todayYmd)
                .map((item) => ({
                    type: 'Urlaub',
                    startDate: item.startDate,
                    endDate: item.endDate,
                })),
            ...employeeSickLeaves
                .filter((item) => item.endDate >= todayYmd)
                .map((item) => ({
                    type: 'Krank',
                    startDate: item.startDate,
                    endDate: item.endDate,
                })),
        ].sort((a, b) => new Date(a.startDate) - new Date(b.startDate));

        return upcoming[0] || null;
    }, [employeeSickLeaves, employeeVacations, todayYmd]);

    const weeklyAbsenceDays = useMemo(() => {
        const coveredDays = new Set();
        weekDates.forEach((dateStr) => {
            const absent = employeeVacations.some((item) => !item.denied && isDateInRange(dateStr, item.startDate, item.endDate))
                || employeeSickLeaves.some((item) => isDateInRange(dateStr, item.startDate, item.endDate));
            if (absent) coveredDays.add(dateStr);
        });
        return coveredDays.size;
    }, [employeeSickLeaves, employeeVacations, weekDates]);

    const problemIndicators = useMemo(() => {
        const approvedVacations = employeeVacations.filter((vacation) => vacation?.approved);
        return getDetailedGlobalProblemIndicators(
            employeeSummaries,
            approvedVacations,
            employee,
            8.5,
            employeeSickLeaves,
            null,
            null,
        );
    }, [employee, employeeSummaries, employeeSickLeaves, employeeVacations]);

    const weeklyProblems = useMemo(() => {
        const datesInWeek = new Set(weekDates);
        return (problemIndicators.problematicDays || []).filter((problem) => datesInWeek.has(problem.dateIso));
    }, [problemIndicators, weekDates]);

    const problemLabelByType = useMemo(() => ({
        missing: t('adminDashboard.issueRibbon.missing', 'Fehlende Zeiten'),
        incomplete_work_end_missing: t('adminDashboard.issueRibbon.incomplete', 'Unvollständig'),
        incomplete_duplicate_punch_times: t('adminDashboard.issueRibbon.incomplete', 'Unvollständig'),
        auto_completed_uncorrected: t('adminDashboard.issueRibbon.autoCompleted', 'Auto beendet'),
        auto_completed_incomplete_uncorrected: t('adminDashboard.issueRibbon.autoCompleted', 'Auto beendet'),
        holiday_pending_decision: t('adminDashboard.issueRibbon.holidayPending', 'Feiertag offen'),
    }), [t]);

    const requestList = activeRequestTab === 'vacation' ? employeeVacations : employeeCorrections;

    const getPunchTypeLabel = useCallback((punchType) => {
        if (!punchType) return t('adminDashboard.unknownType', 'Unbekannt');
        return t(`punchTypes.${punchType}`, punchType);
    }, [t]);

    const handleApproveVacation = async (id) => {
        try {
            await api.post(`/api/vacation/approve/${id}`);
            notify(t('adminDashboard.vacationApprovedMsg', 'Urlaub genehmigt.'), 'success');
            setSelectedRequest(null);
            fetchAllData();
        } catch (err) {
            notify(t('adminDashboard.vacationApproveErrorMsg', 'Fehler beim Genehmigen des Urlaubs: ') + (err.response?.data?.message || err.message), 'error');
        }
    };

    const handleDenyVacation = async (id) => {
        try {
            await api.post(`/api/vacation/deny/${id}`);
            notify(t('adminDashboard.vacationDeniedMsg', 'Urlaub abgelehnt.'), 'success');
            setSelectedRequest(null);
            fetchAllData();
        } catch (err) {
            notify(t('adminDashboard.vacationDenyErrorMsg', 'Fehler beim Ablehnen des Urlaubs: ') + (err.response?.data?.message || err.message), 'error');
        }
    };

    const handleApproveCorrection = async (id) => {
        try {
            await api.post(`/api/correction/approve/${id}`, null, { params: { comment: '' } });
            notify(`${t('adminDashboard.correctionApprovedMsg', 'Korrektur genehmigt')} #${id}`, 'success');
            setSelectedRequest(null);
            fetchAllData();
        } catch (err) {
            notify(`${t('adminDashboard.correctionErrorMsg', 'Fehler bei Korrekturantrag')} #${id}`, 'error');
        }
    };

    const handleDenyCorrection = async (id) => {
        try {
            await api.post(`/api/correction/deny/${id}`, null, { params: { comment: '' } });
            notify(`${t('adminDashboard.correctionDeniedMsg', 'Korrektur abgelehnt')} #${id}`, 'success');
            setSelectedRequest(null);
            fetchAllData();
        } catch (err) {
            notify(`${t('adminDashboard.correctionErrorMsg', 'Fehler bei Korrekturantrag')} #${id}`, 'error');
        }
    };

    const handleQuickVacationSave = async (event) => {
        event.preventDefault();
        try {
            await api.post('/api/vacation/adminCreate', null, {
                params: {
                    username,
                    startDate: vacationForm.startDate,
                    endDate: vacationForm.endDate,
                    halfDay: vacationForm.halfDay,
                },
            });
            notify(t('adminVacation.createdSuccess', 'Urlaub erfolgreich erstellt und direkt genehmigt'), 'success');
            setQuickAction(null);
            fetchAllData();
        } catch (err) {
            notify(t('adminVacation.createError', 'Fehler beim Anlegen des Urlaubs') + ': ' + (err.response?.data?.message || err.message), 'error');
        }
    };

    const handleQuickSickSave = async (event) => {
        event.preventDefault();
        try {
            await api.post('/api/sick-leave/report', null, {
                params: {
                    targetUsername: username,
                    startDate: sickForm.startDate,
                    endDate: sickForm.endDate,
                    halfDay: sickForm.halfDay,
                    comment: sickForm.comment,
                },
            });
            notify(t('adminSickLeave.reportSuccess', 'Krankmeldung erfolgreich für Benutzer eingetragen.'), 'success');
            setQuickAction(null);
            fetchAllData();
        } catch (err) {
            notify(t('adminSickLeave.reportError', 'Fehler beim Eintragen der Krankmeldung:') + ' ' + (err.response?.data?.message || err.message), 'error');
        }
    };

    const openEditModal = (targetDateString) => {
        const dayData = employeeSummaries.find((entry) => entry?.date === targetDateString);
        setEditDate(new Date(`${targetDateString}T00:00:00`));
        setEditDayEntries(dayData?.entries || []);
        setEditModalVisible(true);
    };

    const handleEditSubmit = async (updatedEntriesForDay) => {
        if (!editDate || !username) return;
        const formattedDate = formatLocalDateYMD(editDate);
        try {
            await api.put(`/api/admin/timetracking/editDay/${username}/${formattedDate}`, updatedEntriesForDay);
            notify(t('adminDashboard.editSuccessfulMsg', 'Zeiten erfolgreich bearbeitet.'), 'success');
            setEditModalVisible(false);
            fetchAllData();
        } catch (err) {
            notify(t('adminDashboard.editFailed', 'Fehler beim Bearbeiten') + ': ' + (err.response?.data?.message || err.message), 'error');
        }
    };

    const goToPreviousWeek = () => {
        setSelectedMonday((prev) => addDays(prev, -7));
        setProblemCursor(-1);
    };

    const goToNextWeek = () => {
        setSelectedMonday((prev) => addDays(prev, 7));
        setProblemCursor(-1);
    };

    const goToCurrentWeek = () => {
        setSelectedMonday(getMondayOfWeek(new Date()));
        setProblemCursor(-1);
    };

    const handleCycleProblem = () => {
        if (weeklyProblems.length === 0) return;
        const nextIndex = (problemCursor + 1) % weeklyProblems.length;
        setProblemCursor(nextIndex);
        openEditModal(weeklyProblems[nextIndex].dateIso);
    };

    return (
        <div className="employee-overview-page">
            <Navbar />
            <main className="employee-overview-main">
                <section className="employee-overview-header card-style">
                    <div>
                        <h1>{t('adminEmployeeOverview.title', 'Mitarbeiter-Übersicht')}</h1>
                        <p>
                            <strong>{employee?.firstName || ''} {employee?.lastName || ''} ({username})</strong>
                            {' · '}
                            {employee?.role || t('role', 'Rolle')}
                            {employee?.department ? ` · ${employee.department}` : ''}
                            {employee?.team ? ` · ${employee.team}` : ''}
                        </p>
                    </div>
                    <div className="header-right-actions">
                        <span className={`status-pill ${currentStatus.key}`}>{currentStatus.label}</span>
                        <button className="quick-action-btn" onClick={() => setQuickAction('vacation')}>+ Urlaub eintragen</button>
                        <button className="quick-action-btn" onClick={() => setQuickAction('sick')}>+ Krank eintragen</button>
                        <button className="quick-action-btn" onClick={() => openEditModal(todayYmd)}>+ Korrektur erfassen</button>
                        <Link className="back-to-dashboard-button" to="/admin/dashboard">{t('adminEmployeeOverview.backToDashboard', 'Zurück zum Dashboard')}</Link>
                    </div>
                </section>

                {loading ? (
                    <section className="employee-overview-skeleton-grid">
                        {Array.from({ length: 8 }).map((_, idx) => <div key={idx} className="skeleton-card card-style" />)}
                    </section>
                ) : !employee ? (
                    <section className="card-style employee-overview-state">
                        {t('adminEmployeeOverview.notFound', 'Mitarbeiter nicht gefunden.')}
                    </section>
                ) : (
                    <>
                        <section className="employee-overview-kpis">
                            <article className="card-style kpi-card">
                                <h3>Heute</h3>
                                <p><strong>{todaysSummary ? t('adminDashboard.stamped', 'Gestempelt') : t('adminDashboard.noDataShort', 'Keine Daten')}</strong></p>
                                <p>{todaysSummary ? `${minutesToHHMM(todaysSummary.workedMinutes || 0)} · ${minutesToHHMM(todaysSummary.breakMinutes || 0)}` : t('adminEmployeeOverview.noTrackingData', 'Keine Zeiterfassungen vorhanden.')}</p>
                            </article>
                            <article className="card-style kpi-card">
                                <h3>Woche</h3>
                                <p>Soll {minutesToHHMM(weeklyTargetMinutes)} / Ist <strong>{minutesToHHMM(totalWorkedWeekMinutes)}</strong></p>
                                <p>{t('overtime', 'Überstunden')}: <strong>{minutesToHHMM(weeklyDeltaMinutes)}</strong></p>
                            </article>
                            <article className="card-style kpi-card">
                                <h3>{t('vacationTitle', 'Urlaub')}</h3>
                                <p>{t('remainingVacation', 'Verfügbar')}: <strong>{vacationStats.availableDays.toFixed(1)} {t('daysLabel', 'Tage')}</strong></p>
                                <p>{t('adminEmployeeOverview.plannedVacation', 'Geplant')}: {vacationStats.plannedDays.toFixed(1)} · {t('myVacations', 'Genommen')}: {vacationStats.takenDays.toFixed(1)}</p>
                            </article>
                            <article className="card-style kpi-card">
                                <h3>{t('adminEmployeeOverview.pending', 'Offen')}</h3>
                                <p><strong>{openItemsCount}</strong> {t('adminDashboard.openRequests', 'Anträge')}</p>
                                <p>{t('balanceTotal', 'Gesamtsaldo')}: {minutesToHHMM(employeeBalance?.trackingBalance || 0)}</p>
                            </article>
                        </section>

                        <section className="employee-overview-grid">
                            <div className="left-column">
                                <article className="card-style employee-overview-card">
                                    <div className="card-heading-row">
                                        <h2>Zeiterfassung – diese Woche</h2>
                                        <button className="text-link-btn" onClick={() => openEditModal(todayYmd)}>{t('adminDashboard.details', 'Details anzeigen')}</button>
                                    </div>
                                    <div className="week-navigation-row">
                                        <button type="button" className="text-link-btn" onClick={goToPreviousWeek}>←</button>
                                        <strong>
                                            {formatDate(new Date(`${weekDates[0]}T00:00:00`))} – {formatDate(new Date(`${weekDates[6]}T00:00:00`))}
                                        </strong>
                                        <button type="button" className="text-link-btn" onClick={goToNextWeek}>→</button>
                                        <button type="button" className="text-link-btn" onClick={goToCurrentWeek}>Diese Woche</button>
                                    </div>
                                    <div className="week-compact-grid">
                                        {weekDates.map((dateString) => {
                                            const dayEntry = weeklySummaries.find((entry) => entry.date === dateString);
                                            return (
                                                <button key={dateString} className="week-day-tile" onClick={() => openEditModal(dateString)}>
                                                    <span>{formatDateWithWeekday(new Date(`${dateString}T00:00:00`))}</span>
                                                    <strong>{dayEntry ? minutesToHHMM(dayEntry.workedMinutes || 0) : '--:--'}</strong>
                                                </button>
                                            );
                                        })}
                                    </div>
                                    <div className="mini-stats-row">
                                        <span>Gesamtstunden: <strong>{minutesToHHMM(totalWorkedWeekMinutes)}</strong></span>
                                        <span>Überstunden/Minus: <strong>{minutesToHHMM(weeklyDeltaMinutes)}</strong></span>
                                        <span>Fehlzeiten: <strong>{weeklyAbsenceDays} Tage</strong></span>
                                    </div>
                                    <div className="punch-overview-list">
                                        <div className="card-heading-row">
                                            <h3>Gestempelte Zeiten</h3>
                                        </div>
                                        <div className="compact-list">
                                            {weeklyEntriesOverview.map(({ dateString, summary, entries }) => (
                                                <div className="punch-overview-item" key={`punch-${dateString}`}>
                                                    <div className="punch-overview-header">
                                                        <strong>{formatDateWithWeekday(new Date(`${dateString}T00:00:00`))}</strong>
                                                        <button
                                                            type="button"
                                                            className="text-link-btn"
                                                            onClick={() => openEditModal(dateString)}
                                                        >
                                                            Korrigieren
                                                        </button>
                                                    </div>
                                                    {entries.length > 0 ? (
                                                        <div className="punch-chip-row">
                                                            {entries.map((entry, index) => (
                                                                <span className="punch-chip" key={`${entry.id || entry.entryTimestamp || dateString}-${index}`}>
                                                                    {getPunchTypeLabel(entry?.punchType)} · {formatTime(new Date(entry.entryTimestamp))}
                                                                </span>
                                                            ))}
                                                        </div>
                                                    ) : (
                                                        <p className="empty-state">
                                                            {summary
                                                                ? t('adminEmployeeOverview.noPunchesForDay', 'Keine einzelnen Stempelungen vorhanden.')
                                                                : t('adminDashboard.noDataShort', 'Keine Daten')}
                                                        </p>
                                                    )}
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                    <div className="problem-case-card">
                                        <div className="card-heading-row">
                                            <h3>Problemfälle</h3>
                                            <button type="button" className="text-link-btn" onClick={handleCycleProblem} disabled={weeklyProblems.length === 0}>
                                                {weeklyProblems.length > 0
                                                    ? `Zum Problem (${((problemCursor + 1) % weeklyProblems.length) + 1}/${weeklyProblems.length})`
                                                    : 'Keine Problemfälle'}
                                            </button>
                                        </div>
                                        <div className="compact-list">
                                            {weeklyProblems.map((problem) => (
                                                <button
                                                    key={`${problem.dateIso}-${problem.type}`}
                                                    type="button"
                                                    className="compact-list-item"
                                                    onClick={() => openEditModal(problem.dateIso)}
                                                >
                                                    <span>{formatDateWithWeekday(new Date(`${problem.dateIso}T00:00:00`))}</span>
                                                    <span className="status-pill pending">{problemLabelByType[problem.type] || problem.type}</span>
                                                </button>
                                            ))}
                                            {weeklyProblems.length === 0 && <p className="empty-state">Keine Problemfälle in dieser Woche.</p>}
                                        </div>
                                    </div>
                                </article>

                                <article className="card-style employee-overview-card">
                                    <div className="card-heading-row">
                                        <h2>{t('correctionRequests', 'Anträge')}</h2>
                                        <div className="request-tabs">
                                            <button className={activeRequestTab === 'vacation' ? 'active' : ''} onClick={() => setActiveRequestTab('vacation')}>Urlaub</button>
                                            <button className={activeRequestTab === 'correction' ? 'active' : ''} onClick={() => setActiveRequestTab('correction')}>Korrektur</button>
                                        </div>
                                    </div>
                                    <div className="compact-list">
                                        {requestList.map((item) => (
                                            <button
                                                className="compact-list-item"
                                                key={`${activeRequestTab}-${item.id}`}
                                                onClick={() => setSelectedRequest({ type: activeRequestTab, data: item })}
                                            >
                                                <span>
                                                    {activeRequestTab === 'vacation'
                                                        ? `${formatDate(new Date(`${item.startDate}T00:00:00`))} – ${formatDate(new Date(`${item.endDate}T00:00:00`))}`
                                                        : (item.requestDate ? formatDate(new Date(item.requestDate)) : '-')}
                                                </span>
                                                <span className={`status-pill ${getRequestStatusClass(item)}`}>{getRequestStatusLabel(item, t)}</span>
                                            </button>
                                        ))}
                                        {requestList.length === 0 && <p className="empty-state">{t('adminCorrections.noRequestsFound', 'Keine Anträge vorhanden.')}</p>}
                                    </div>
                                </article>
                            </div>

                            <div className="right-column">
                                <article className="card-style employee-overview-card calendar-card">
                                    <h2>{t('adminEmployeeOverview.calendarTitle', 'Kalender')}</h2>
                                    <p className="card-subtitle">{t('adminEmployeeOverview.calendarSubtitle', 'Urlaub/Krank direkt für diesen Mitarbeiter erfassen.')}</p>
                                    <VacationCalendarAdmin
                                        vacationRequests={employeeVacations}
                                        onReloadVacations={fetchAllData}
                                        companyUsers={users}
                                        focusUsername={username}
                                    />
                                </article>
                                <article className="card-style employee-overview-card absence-summary-card">
                                    <h2>Abwesenheits-Zusammenfassung</h2>
                                    <p>Nächste Abwesenheit: <strong>{nextAbsence ? `${nextAbsence.type} · ${formatDate(new Date(`${nextAbsence.startDate}T00:00:00`))}` : 'Keine geplant'}</strong></p>
                                    <p>Aktueller Status: <strong>{currentStatus.label}</strong></p>
                                    <p>Diese Woche: <strong>{weeklyAbsenceDays} Tage abwesend</strong></p>
                                    <p>{t('breakTime', 'Pause')} (Woche): <strong>{minutesToHHMM(totalBreakWeekMinutes)}</strong></p>
                                </article>
                            </div>
                        </section>
                    </>
                )}
            </main>

            {quickAction === 'vacation' && (
                <ModalOverlay visible>
                    <div className="modal-content employee-quick-modal">
                        <h3>Urlaub eintragen – {username}</h3>
                        <form onSubmit={handleQuickVacationSave} className="quick-form-grid">
                            <label>Von<input type="date" value={vacationForm.startDate} onChange={(e) => setVacationForm((prev) => ({ ...prev, startDate: e.target.value }))} required /></label>
                            <label>Bis<input type="date" value={vacationForm.endDate} onChange={(e) => setVacationForm((prev) => ({ ...prev, endDate: e.target.value }))} required /></label>
                            <label className="checkbox-label"><input type="checkbox" checked={vacationForm.halfDay} onChange={(e) => setVacationForm((prev) => ({ ...prev, halfDay: e.target.checked }))} />Halbtag</label>
                            <div className="modal-actions">
                                <button type="button" onClick={() => setQuickAction(null)}>{t('cancel', 'Abbrechen')}</button>
                                <button type="submit">{t('save', 'Speichern')}</button>
                            </div>
                        </form>
                    </div>
                </ModalOverlay>
            )}

            {quickAction === 'sick' && (
                <ModalOverlay visible>
                    <div className="modal-content employee-quick-modal">
                        <h3>Krankmeldung eintragen – {username}</h3>
                        <form onSubmit={handleQuickSickSave} className="quick-form-grid">
                            <label>Von<input type="date" value={sickForm.startDate} onChange={(e) => setSickForm((prev) => ({ ...prev, startDate: e.target.value }))} required /></label>
                            <label>Bis<input type="date" value={sickForm.endDate} onChange={(e) => setSickForm((prev) => ({ ...prev, endDate: e.target.value }))} required /></label>
                            <label>Notiz<input type="text" value={sickForm.comment} onChange={(e) => setSickForm((prev) => ({ ...prev, comment: e.target.value }))} /></label>
                            <label className="checkbox-label"><input type="checkbox" checked={sickForm.halfDay} onChange={(e) => setSickForm((prev) => ({ ...prev, halfDay: e.target.checked }))} />Halbtag</label>
                            <div className="modal-actions">
                                <button type="button" onClick={() => setQuickAction(null)}>{t('cancel', 'Abbrechen')}</button>
                                <button type="submit">{t('save', 'Speichern')}</button>
                            </div>
                        </form>
                    </div>
                </ModalOverlay>
            )}

            {selectedRequest && (
                <ModalOverlay visible>
                    <div className="modal-content employee-quick-modal">
                        <h3>{selectedRequest.type === 'vacation' ? 'Urlaubsantrag' : 'Korrekturantrag'} #{selectedRequest.data.id}</h3>
                        <p>Status: <span className={`status-pill ${getRequestStatusClass(selectedRequest.data)}`}>{getRequestStatusLabel(selectedRequest.data, t)}</span></p>
                        {selectedRequest.type === 'vacation' ? (
                            <p>{formatDate(new Date(`${selectedRequest.data.startDate}T00:00:00`))} – {formatDate(new Date(`${selectedRequest.data.endDate}T00:00:00`))}</p>
                        ) : (
                            <>
                                <p>Datum: {selectedRequest.data.requestDate ? formatDate(new Date(selectedRequest.data.requestDate)) : '-'}</p>
                                <p>{selectedRequest.data.reason || '-'}</p>
                            </>
                        )}
                        <div className="modal-actions">
                            <button type="button" onClick={() => setSelectedRequest(null)}>{t('close', 'Schließen')}</button>
                            {!selectedRequest.data.approved && !selectedRequest.data.denied && (
                                <>
                                    <button type="button" onClick={() => (selectedRequest.type === 'vacation' ? handleApproveVacation(selectedRequest.data.id) : handleApproveCorrection(selectedRequest.data.id))}>{t('approve', 'Genehmigen')}</button>
                                    <button type="button" className="deny" onClick={() => (selectedRequest.type === 'vacation' ? handleDenyVacation(selectedRequest.data.id) : handleDenyCorrection(selectedRequest.data.id))}>{t('deny', 'Ablehnen')}</button>
                                </>
                            )}
                        </div>
                    </div>
                </ModalOverlay>
            )}

            <EditTimeModal
                t={t}
                isVisible={editModalVisible}
                targetDate={editDate}
                dayEntries={editDayEntries}
                targetUsername={username}
                onSubmit={handleEditSubmit}
                onClose={() => setEditModalVisible(false)}
                users={users}
            />
        </div>
    );
};

export default AdminEmployeeOverviewPage;
