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

const getMonthStart = (dateObj) => new Date(dateObj.getFullYear(), dateObj.getMonth(), 1);
const getMonthEnd = (dateObj) => new Date(dateObj.getFullYear(), dateObj.getMonth() + 1, 0);
const weekDayLabels = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];
const MAX_CORRECTION_PREVIEW_PUNCHES = 4;

const formatPunchTypeLabel = (type, t) => {
    if (!type) return '';
    return t(`punchTypes.${type}`, type);
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

    const [decisionNotes, setDecisionNotes] = useState({});

    const [quickAction, setQuickAction] = useState(null);
    const [vacationForm, setVacationForm] = useState({ startDate: todayYmd, endDate: todayYmd, halfDay: false });
    const [sickForm, setSickForm] = useState({ startDate: todayYmd, endDate: todayYmd, halfDay: false, comment: '' });

    const [editModalVisible, setEditModalVisible] = useState(false);
    const [editDate, setEditDate] = useState(null);
    const [editDayEntries, setEditDayEntries] = useState([]);
    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));
    const [selectedMonth, setSelectedMonth] = useState(getMonthStart(new Date()));
    const [timeRangeMode, setTimeRangeMode] = useState('week');
    const [problemCursor, setProblemCursor] = useState(-1);
    const [problemCategoryCursor, setProblemCategoryCursor] = useState({});

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

    const monthDates = useMemo(() => {
        const start = getMonthStart(selectedMonth);
        const end = getMonthEnd(selectedMonth);
        const dates = [];
        for (let cursor = new Date(start); cursor <= end; cursor = addDays(cursor, 1)) {
            dates.push(formatLocalDateYMD(cursor));
        }
        return dates;
    }, [selectedMonth]);

    const visibleDates = timeRangeMode === 'month' ? monthDates : weekDates;

    const periodSummaries = useMemo(
        () => employeeSummaries.filter((entry) => visibleDates.includes(entry?.date)),
        [employeeSummaries, visibleDates],
    );

    const periodEntriesOverview = useMemo(
        () => visibleDates.map((dateString) => {
            const summary = periodSummaries.find((entry) => entry?.date === dateString);
            const entries = Array.isArray(summary?.entries)
                ? [...summary.entries].sort((a, b) => new Date(a?.entryTimestamp || 0) - new Date(b?.entryTimestamp || 0))
                : [];
            return {
                dateString,
                summary,
                entries,
            };
        }),
        [visibleDates, periodSummaries],
    );

    const punchCalendarCells = useMemo(() => {
        const baseCells = periodEntriesOverview.map((entry) => ({ ...entry, isPlaceholder: false }));
        if (timeRangeMode !== 'month') return baseCells;

        const firstDayOfMonth = getMonthStart(selectedMonth);
        const dayIndex = firstDayOfMonth.getDay();
        const mondayBasedOffset = dayIndex === 0 ? 6 : dayIndex - 1;
        const placeholderCells = Array.from({ length: mondayBasedOffset }, (_, index) => ({
            dateString: `placeholder-${index}`,
            summary: null,
            entries: [],
            isPlaceholder: true,
        }));

        return [...placeholderCells, ...baseCells];
    }, [periodEntriesOverview, selectedMonth, timeRangeMode]);

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

    const groupedEmployeeCorrections = useMemo(() => {
        const groups = new Map();

        employeeCorrections.forEach((correction) => {
            const key = `${correction.requestDate || ''}|${correction.reason || ''}|${correction.approved}|${correction.denied}`;
            if (!groups.has(key)) {
                groups.set(key, {
                    ...correction,
                    requestIds: [correction.id],
                    groupedEntries: [correction],
                });
                return;
            }

            const existing = groups.get(key);
            existing.requestIds.push(correction.id);
            existing.groupedEntries.push(correction);
        });

        groups.forEach((group) => {
            group.groupedEntries.sort((a, b) => new Date(a.desiredTimestamp || 0) - new Date(b.desiredTimestamp || 0));
        });

        return Array.from(groups.values()).sort((a, b) => {
            const dateDiff = new Date(b.requestDate || 0) - new Date(a.requestDate || 0);
            if (dateDiff !== 0) return dateDiff;
            return (b.id || 0) - (a.id || 0);
        });
    }, [employeeCorrections]);

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
        () => periodSummaries.reduce((sum, entry) => sum + (entry?.workedMinutes || 0), 0),
        [periodSummaries],
    );

    const totalBreakWeekMinutes = useMemo(
        () => periodSummaries.reduce((sum, entry) => sum + (entry?.breakMinutes || 0), 0),
        [periodSummaries],
    );

    const periodTargetMinutes = useMemo(() => {
        const workDays = visibleDates.filter((dateString) => {
            const dateObj = new Date(`${dateString}T00:00:00`);
            return isWorkDay(dateObj);
        }).length;
        const perDay = Number(employee?.weeklyHours) > 0 ? (Number(employee.weeklyHours) * 60) / 5 : 0;
        return Math.round(perDay * workDays);
    }, [employee, visibleDates]);

    const periodDeltaMinutes = totalWorkedWeekMinutes - periodTargetMinutes;

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


    const periodAbsenceDays = useMemo(() => {
        const coveredDays = new Set();
        visibleDates.forEach((dateStr) => {
            const absent = employeeVacations.some((item) => !item.denied && isDateInRange(dateStr, item.startDate, item.endDate))
                || employeeSickLeaves.some((item) => isDateInRange(dateStr, item.startDate, item.endDate));
            if (absent) coveredDays.add(dateStr);
        });
        return coveredDays.size;
    }, [employeeSickLeaves, employeeVacations, visibleDates]);

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

    const visibleRangeProblems = useMemo(() => {
        const datesInRange = new Set(visibleDates);
        return (problemIndicators.problematicDays || []).filter((problem) => datesInRange.has(problem.dateIso));
    }, [problemIndicators, visibleDates]);

    const prioritizedProblems = useMemo(() => {
        if (visibleRangeProblems.length > 0) return visibleRangeProblems;
        return problemIndicators.problematicDays || [];
    }, [problemIndicators.problematicDays, visibleRangeProblems]);

    const problemGroups = useMemo(() => ({
        missing: prioritizedProblems.filter((problem) => problem.type === 'missing'),
        incomplete: prioritizedProblems.filter((problem) => problem.type.startsWith('incomplete_')),
        autoCompleted: prioritizedProblems.filter((problem) => problem.type === 'auto_completed_uncorrected' || problem.type === 'auto_completed_incomplete_uncorrected'),
        holidayPending: prioritizedProblems.filter((problem) => problem.type === 'holiday_pending_decision'),
    }), [prioritizedProblems]);

    const problemLabelByType = useMemo(() => ({
        missing: t('adminDashboard.issueRibbon.missing', 'Fehlende Zeiten'),
        incomplete_work_end_missing: t('adminDashboard.issueRibbon.incomplete', 'Unvollständig'),
        incomplete_duplicate_punch_times: t('adminDashboard.issueRibbon.incomplete', 'Unvollständig'),
        auto_completed_uncorrected: t('adminDashboard.issueRibbon.autoCompleted', 'Auto beendet'),
        auto_completed_incomplete_uncorrected: t('adminDashboard.issueRibbon.autoCompleted', 'Auto beendet'),
        holiday_pending_decision: t('adminDashboard.issueRibbon.holidayPending', 'Feiertag offen'),
    }), [t]);

    const getPunchTypeLabel = useCallback((punchType) => {
        if (!punchType) return t('adminDashboard.unknownType', 'Unbekannt');
        return t(`punchTypes.${punchType}`, punchType);
    }, [t]);

    const renderCorrectionChange = useCallback((correctionGroup) => {
        if (!correctionGroup) return null;

        const originalLabel = t('adminDashboard.originalTimeLabel', 'Gestempelt');
        const requestedLabel = t('adminDashboard.requestedTimeLabel', 'Beantragt');
        const missingLabel = t('adminDashboard.noOriginalTimeLabel', 'Kein ursprünglicher Stempel');

        const correctionsToRender = Array.isArray(correctionGroup.groupedEntries) && correctionGroup.groupedEntries.length > 0
            ? correctionGroup.groupedEntries
            : [correctionGroup];

        const firstCorrection = correctionsToRender[0];

        const correctionDate = firstCorrection?.requestDate
            ? formatLocalDateYMD(new Date(`${firstCorrection.requestDate}T00:00:00`))
            : null;

        const dayEntries = correctionDate
            ? (employeeSummaries.find((summary) => summary?.date === correctionDate)?.entries || [])
            : [];

        const normalizedDayEntries = dayEntries
            .filter((entry) => entry?.entryTimestamp)
            .map((entry, index) => ({
                id: entry?.id ?? `entry-${index}`,
                entryTimestamp: entry.entryTimestamp,
                punchType: entry?.punchType,
            }))
            .sort((a, b) => new Date(a.entryTimestamp || 0) - new Date(b.entryTimestamp || 0));

        const requestedEntries = normalizedDayEntries.map((entry) => ({ ...entry }));
        const changedEntryIds = new Set();

        correctionsToRender.forEach((correction) => {
            const requestTargetId = correction.targetEntryId;
            const requestOriginalTs = correction.originalTimestamp;
            const requestOriginalType = correction.originalPunchType;

            const targetIndex = requestedEntries.findIndex((entry) => {
                if (requestTargetId != null && entry.id === requestTargetId) return true;
                if (!requestOriginalTs) return false;
                const sameTs = entry.entryTimestamp === requestOriginalTs;
                const sameType = !requestOriginalType || entry.punchType === requestOriginalType;
                return sameTs && sameType;
            });

            if (targetIndex >= 0) {
                requestedEntries[targetIndex] = {
                    ...requestedEntries[targetIndex],
                    entryTimestamp: correction.desiredTimestamp,
                    punchType: correction.desiredPunchType,
                };
                changedEntryIds.add(requestedEntries[targetIndex].id);
                return;
            }

            const syntheticId = `requested-${correction.id || correction.desiredTimestamp || Date.now()}`;
            requestedEntries.push({
                id: syntheticId,
                entryTimestamp: correction.desiredTimestamp,
                punchType: correction.desiredPunchType,
            });
            changedEntryIds.add(syntheticId);
        });

        requestedEntries.sort((a, b) => new Date(a.entryTimestamp || 0) - new Date(b.entryTimestamp || 0));

        const toSlots = (entries, changedIds = null) => (
            Array.from({ length: MAX_CORRECTION_PREVIEW_PUNCHES }, (_, index) => {
                const entry = entries[index] || null;
                return {
                    key: `${index}`,
                    time: entry?.entryTimestamp ? formatTime(entry.entryTimestamp) : null,
                    type: entry?.punchType ? formatPunchTypeLabel(entry.punchType, t) : null,
                    changed: Boolean(entry && changedIds?.has(entry.id)),
                };
            })
        );

        const originalSlots = toSlots(normalizedDayEntries);
        const requestedSlots = toSlots(requestedEntries, changedEntryIds);

        const renderSlots = (slots, variant) => (
            <div className="entry-slot-list">
                {slots.map((slot, index) => (
                    <div key={`${variant}-${slot.key}`} className={`entry-slot${slot.changed ? ' entry-slot--changed' : ''}`}>
                        <span className="entry-slot-index">#{index + 1}</span>
                        {slot.time ? (
                            <>
                                <span className="entry-time">{slot.time}</span>
                                {slot.type && <span className="entry-type">{slot.type}</span>}
                            </>
                        ) : (
                            <span className="entry-time entry-time--missing">{missingLabel}</span>
                        )}
                    </div>
                ))}
            </div>
        );

        return (
            <span className="entry-comparison">
                <span className={`entry-block entry-original${normalizedDayEntries.length > 0 ? '' : ' entry-original--missing'}`}>
                    <span className="entry-label">{originalLabel}</span>
                    {renderSlots(originalSlots, 'original')}
                </span>
                <span className="entry-arrow">→</span>
                <span className="entry-block entry-requested">
                    <span className="entry-label">{requestedLabel}</span>
                    {renderSlots(requestedSlots, 'requested')}
                </span>
            </span>
        );
    }, [employeeSummaries, t]);

    const handleApproveVacation = async (id) => {
        try {
            await api.post(`/api/vacation/approve/${id}`);
            notify(t('adminDashboard.vacationApprovedMsg', 'Urlaub genehmigt.'), 'success');
            fetchAllData();
        } catch (err) {
            notify(t('adminDashboard.vacationApproveErrorMsg', 'Fehler beim Genehmigen des Urlaubs: ') + (err.response?.data?.message || err.message), 'error');
        }
    };

    const handleDenyVacation = async (id) => {
        try {
            await api.post(`/api/vacation/deny/${id}`);
            notify(t('adminDashboard.vacationDeniedMsg', 'Urlaub abgelehnt.'), 'success');
            fetchAllData();
        } catch (err) {
            notify(t('adminDashboard.vacationDenyErrorMsg', 'Fehler beim Ablehnen des Urlaubs: ') + (err.response?.data?.message || err.message), 'error');
        }
    };

    const handleApproveCorrection = async (id, comment = '') => {
        try {
            await api.post(`/api/correction/approve/${id}`, null, { params: { comment } });
            notify(`${t('adminDashboard.correctionApprovedMsg', 'Korrektur genehmigt')} #${id}`, 'success');
            fetchAllData();
        } catch (err) {
            notify(`${t('adminDashboard.correctionErrorMsg', 'Fehler bei Korrekturantrag')} #${id}`, 'error');
        }
    };

    const handleDenyCorrection = async (id, comment = '') => {
        try {
            await api.post(`/api/correction/deny/${id}`, null, { params: { comment } });
            notify(`${t('adminDashboard.correctionDeniedMsg', 'Korrektur abgelehnt')} #${id}`, 'success');
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

    const openEditModal = useCallback((targetDateString) => {
        const dayData = employeeSummaries.find((entry) => entry?.date === targetDateString);
        setEditDate(new Date(`${targetDateString}T00:00:00`));
        setEditDayEntries(dayData?.entries || []);
        setEditModalVisible(true);
    }, [employeeSummaries]);

    const getDecisionNoteKey = useCallback((tab, id) => `${tab}-${id}`, []);

    const handleRequestNoteChange = useCallback((tab, id, value) => {
        const key = getDecisionNoteKey(tab, id);
        setDecisionNotes((prev) => ({
            ...prev,
            [key]: value,
        }));
    }, [getDecisionNoteKey]);

    const getRequestAnchorDate = useCallback((tab, item) => {
        if (tab === 'vacation') return item?.startDate || null;
        if (!item?.requestDate) return null;
        return formatLocalDateYMD(new Date(item.requestDate));
    }, []);

    const handleOpenRequestDate = useCallback((tab, item) => {
        const targetDate = getRequestAnchorDate(tab, item);
        if (!targetDate) return;
        setTimeRangeMode('week');
        setSelectedMonday(getMondayOfWeek(new Date(`${targetDate}T00:00:00`)));
        openEditModal(targetDate);
    }, [getRequestAnchorDate, openEditModal]);

    const handleRequestDecision = useCallback(async (tab, item, decision) => {
        if (!item?.id) return;
        const key = getDecisionNoteKey(tab, item.id);
        const note = (decisionNotes[key] || '').trim();

        if (tab === 'vacation') {
            if (decision === 'approve') await handleApproveVacation(item.id);
            if (decision === 'deny') await handleDenyVacation(item.id);
        }

        if (tab === 'correction') {
            const requestIds = Array.isArray(item.requestIds) && item.requestIds.length > 0
                ? item.requestIds
                : [item.id];
            if (decision === 'approve') {
                await Promise.all(requestIds.map((id) => handleApproveCorrection(id, note)));
            }
            if (decision === 'deny') {
                await Promise.all(requestIds.map((id) => handleDenyCorrection(id, note)));
            }
        }

        setDecisionNotes((prev) => {
            const next = { ...prev };
            delete next[key];
            return next;
        });
    }, [decisionNotes, getDecisionNoteKey, handleApproveCorrection, handleApproveVacation, handleDenyCorrection, handleDenyVacation]);

    const renderRequestList = useCallback((tab, items) => (
        <div className="compact-list vacation-requests-scroll">
            {items.map((item) => {
                const noteKey = getDecisionNoteKey(tab, item.id);
                const requestDateLabel = tab === 'vacation'
                    ? `${formatDate(new Date(`${item.startDate}T00:00:00`))} – ${formatDate(new Date(`${item.endDate}T00:00:00`))}`
                    : (item.requestDate ? formatDate(new Date(item.requestDate)) : '-');
                const isPending = !item.approved && !item.denied;

                return (
                    <article className="compact-list-item request-list-card" key={`${tab}-${item.id}`}>
                        <button
                            type="button"
                            className="request-jump-btn"
                            onClick={() => handleOpenRequestDate(tab, item)}
                        >
                            <div className="request-item-content">
                                <span className="request-item-main">{requestDateLabel}</span>
                                {tab === 'correction' && (
                                    <div className="request-correction-preview">
                                        {renderCorrectionChange(item)}
                                    </div>
                                )}
                            </div>
                            <span className={`status-pill ${getRequestStatusClass(item)}`}>{getRequestStatusLabel(item, t)}</span>
                        </button>

                        {isPending && (
                            <div className="request-inline-actions">
                                <input
                                    type="text"
                                    value={decisionNotes[noteKey] || ''}
                                    onChange={(event) => handleRequestNoteChange(tab, item.id, event.target.value)}
                                    placeholder="Notiz (optional)"
                                />
                                <button
                                    type="button"
                                    className="inline-approve"
                                    onClick={() => handleRequestDecision(tab, item, 'approve')}
                                >
                                    {t('approve', 'Genehmigen')}
                                </button>
                                <button
                                    type="button"
                                    className="inline-deny"
                                    onClick={() => handleRequestDecision(tab, item, 'deny')}
                                >
                                    {t('deny', 'Ablehnen')}
                                </button>
                            </div>
                        )}
                    </article>
                );
            })}
            {items.length === 0 && <p className="empty-state">{t('adminCorrections.noRequestsFound', 'Keine Anträge vorhanden.')}</p>}
        </div>
    ), [decisionNotes, getDecisionNoteKey, handleOpenRequestDate, handleRequestDecision, handleRequestNoteChange, renderCorrectionChange, t]);

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
        setProblemCategoryCursor({});
    };

    const goToNextWeek = () => {
        setSelectedMonday((prev) => addDays(prev, 7));
        setProblemCursor(-1);
        setProblemCategoryCursor({});
    };

    const goToCurrentWeek = () => {
        setSelectedMonday(getMondayOfWeek(new Date()));
        setProblemCursor(-1);
        setProblemCategoryCursor({});
    };

    const goToPreviousMonth = () => {
        setSelectedMonth((prev) => new Date(prev.getFullYear(), prev.getMonth() - 1, 1));
    };

    const goToNextMonth = () => {
        setSelectedMonth((prev) => new Date(prev.getFullYear(), prev.getMonth() + 1, 1));
    };

    const goToCurrentMonth = () => {
        setSelectedMonth(getMonthStart(new Date()));
    };

    const handleCycleProblem = () => {
        if (prioritizedProblems.length === 0) return;
        const nextIndex = (problemCursor + 1) % prioritizedProblems.length;
        setProblemCursor(nextIndex);
        openEditModal(prioritizedProblems[nextIndex].dateIso);
    };

    const handleProblemCategoryClick = (categoryKey) => {
        const categoryProblems = problemGroups[categoryKey] || [];
        if (categoryProblems.length === 0) return;

        setProblemCategoryCursor((prev) => {
            const currentIndex = Number.isInteger(prev[categoryKey]) ? prev[categoryKey] : -1;
            const nextIndex = (currentIndex + 1) % categoryProblems.length;
            openEditModal(categoryProblems[nextIndex].dateIso);
            return {
                ...prev,
                [categoryKey]: nextIndex,
            };
        });
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
                        <section className="card-style employee-overview-problem-ribbon">
                            <div className="card-heading-row">
                                <h2>{t('adminDashboard.issueRibbon.title', 'Problemfokus')}</h2>
                                <button type="button" className="text-link-btn" onClick={handleCycleProblem} disabled={prioritizedProblems.length === 0}>
                                    {prioritizedProblems.length > 0
                                        ? `Zum Problem (${((problemCursor + 1) % prioritizedProblems.length) + 1}/${prioritizedProblems.length})`
                                        : 'Keine Problemfälle'}
                                </button>
                            </div>
                            <div className="problem-ribbon-row" role="list" aria-label={t('adminDashboard.issueFilters.groupLabel', 'Problemtypen filtern')}>
                                <button type="button" className="problem-ribbon-chip" onClick={() => handleProblemCategoryClick('missing')} disabled={problemGroups.missing.length === 0}>
                                    {problemLabelByType.missing} ({problemGroups.missing.length})
                                </button>
                                <button type="button" className="problem-ribbon-chip" onClick={() => handleProblemCategoryClick('incomplete')} disabled={problemGroups.incomplete.length === 0}>
                                    {problemLabelByType.incomplete_work_end_missing} ({problemGroups.incomplete.length})
                                </button>
                                <button type="button" className="problem-ribbon-chip" onClick={() => handleProblemCategoryClick('autoCompleted')} disabled={problemGroups.autoCompleted.length === 0}>
                                    {problemLabelByType.auto_completed_uncorrected} ({problemGroups.autoCompleted.length})
                                </button>
                                <button type="button" className="problem-ribbon-chip" onClick={() => handleProblemCategoryClick('holidayPending')} disabled={problemGroups.holidayPending.length === 0}>
                                    {problemLabelByType.holiday_pending_decision} ({problemGroups.holidayPending.length})
                                </button>
                            </div>
                        </section>

                        <section className="employee-overview-kpis">
                            <article className="card-style kpi-card">
                                <h3>Heute</h3>
                                <p><strong>{todaysSummary ? t('adminDashboard.stamped', 'Gestempelt') : t('adminDashboard.noDataShort', 'Keine Daten')}</strong></p>
                                <p>{todaysSummary ? `${minutesToHHMM(todaysSummary.workedMinutes || 0)} · ${minutesToHHMM(todaysSummary.breakMinutes || 0)}` : t('adminEmployeeOverview.noTrackingData', 'Keine Zeiterfassungen vorhanden.')}</p>
                            </article>
                            <article className="card-style kpi-card">
                                <h3>Woche</h3>
                                <p>Soll {minutesToHHMM(periodTargetMinutes)} / Ist <strong>{minutesToHHMM(totalWorkedWeekMinutes)}</strong></p>
                                <p>{t('overtime', 'Überstunden')}: <strong>{minutesToHHMM(periodDeltaMinutes)}</strong></p>
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
                                        <h2>Zeiterfassung</h2>
                                        <div className="request-tabs">
                                            <button className={timeRangeMode === 'week' ? 'active' : ''} onClick={() => setTimeRangeMode('week')}>Woche</button>
                                            <button className={timeRangeMode === 'month' ? 'active' : ''} onClick={() => setTimeRangeMode('month')}>Monat</button>
                                        </div>
                                    </div>
                                    <div className="week-navigation-row">
                                        {timeRangeMode === 'month' ? (
                                            <>
                                                <button type="button" className="text-link-btn" onClick={goToPreviousMonth}>←</button>
                                                <strong>{selectedMonth.toLocaleDateString('de-DE', { month: 'long', year: 'numeric' })}</strong>
                                                <button type="button" className="text-link-btn" onClick={goToNextMonth}>→</button>
                                                <button type="button" className="text-link-btn" onClick={goToCurrentMonth}>Dieser Monat</button>
                                            </>
                                        ) : (
                                            <>
                                                <button type="button" className="text-link-btn" onClick={goToPreviousWeek}>←</button>
                                                <strong>
                                                    {formatDate(new Date(`${weekDates[0]}T00:00:00`))} – {formatDate(new Date(`${weekDates[6]}T00:00:00`))}
                                                </strong>
                                                <button type="button" className="text-link-btn" onClick={goToNextWeek}>→</button>
                                                <button type="button" className="text-link-btn" onClick={goToCurrentWeek}>Diese Woche</button>
                                            </>
                                        )}
                                    </div>
                                    <div className="mini-stats-row">
                                        <span>Gesamtstunden: <strong>{minutesToHHMM(totalWorkedWeekMinutes)}</strong></span>
                                        <span>Überstunden/Minus: <strong>{minutesToHHMM(periodDeltaMinutes)}</strong></span>
                                        <span>Fehlzeiten: <strong>{periodAbsenceDays} Tage</strong></span>
                                    </div>
                                    <div className="punch-overview-list">
                                        <div className="card-heading-row">
                                            <h3>Gestempelte Zeiten</h3>
                                        </div>
                                        <div className="punch-calendar-grid-wrap">
                                            <div className="punch-calendar-weekdays">
                                                {weekDayLabels.map((label) => (
                                                    <span key={label}>{label}</span>
                                                ))}
                                            </div>
                                            <div className={`punch-calendar-grid punch-calendar-grid--${timeRangeMode}`}>
                                                {punchCalendarCells.map(({ dateString, summary, entries, isPlaceholder }) => {
                                                    if (isPlaceholder) {
                                                        return <div key={dateString} className="punch-overview-item punch-overview-item--placeholder" aria-hidden="true" />;
                                                    }

                                                    return (
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
                                                    );
                                                })}
                                            </div>
                                        </div>
                                    </div>
                                </article>

                                <article className="card-style employee-overview-card vacation-requests-card">
                                    <div className="card-heading-row">
                                        <h2>{t('adminDashboard.correctionRequestsTitle', 'Korrekturanträge')}</h2>
                                    </div>
                                    {renderRequestList('correction', groupedEmployeeCorrections)}
                                </article>

                            </div>

                            <div className="right-column">
                                <article className="card-style employee-overview-card absence-summary-card">
                                    <h2>Abwesenheits-Zusammenfassung</h2>
                                    <div className="absence-summary-list">
                                        <p><span>{t('remainingVacation', 'Verfügbar')}</span><strong>{vacationStats.availableDays.toFixed(1)} {t('daysLabel', 'Tage')}</strong></p>
                                        <p><span>{t('adminEmployeeOverview.plannedVacation', 'Geplant')}</span><strong>{vacationStats.plannedDays.toFixed(1)} {t('daysLabel', 'Tage')}</strong></p>
                                        <p><span>Nächste Abwesenheit</span><strong>{nextAbsence ? `${nextAbsence.type} · ${formatDate(new Date(`${nextAbsence.startDate}T00:00:00`))}` : 'Keine geplant'}</strong></p>
                                        <p><span>Aktueller Status</span><strong>{currentStatus.label}</strong></p>
                                        <p><span>Diese Woche</span><strong>{weeklyAbsenceDays} Tage abwesend</strong></p>
                                        <p><span>{t('breakTime', 'Pause')} (Woche)</span><strong>{minutesToHHMM(totalBreakWeekMinutes)}</strong></p>
                                    </div>
                                </article>
                                <section className="calendar-requests-layout">
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

                                    <article className="card-style employee-overview-card vacation-requests-card">
                                        <div className="card-heading-row">
                                            <h2>{t('adminDashboard.vacationRequestsTitle', 'Urlaubsanträge')}</h2>
                                        </div>
                                        {renderRequestList('vacation', employeeVacations)}
                                    </article>
                                </section>
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
