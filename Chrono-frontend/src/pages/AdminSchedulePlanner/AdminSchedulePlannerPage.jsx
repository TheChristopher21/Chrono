import React from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { create } from 'zustand';
import { startOfWeek, addDays, formatISO, format, isSameDay, differenceInDays } from 'date-fns';
import { Link, useNavigate } from 'react-router-dom';
import Navbar from '../../components/Navbar';
import api from '../../utils/api';
import '../../styles/AdminSchedulePlannerPageScoped.css';
import { useTranslation } from '../../context/LanguageContext';
import { useNotification } from '../../context/NotificationContext';
import { useAuth } from '../../context/AuthContext';
import { ACCESS_MANAGE, ACCESS_VIEW, hasPageAccess, isAdminUser } from '../../utils/pageAccess';

const days = ['Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag'];

/* ===========================
   Data fetchers
   =========================== */
const fetchScheduleRules = async () => {
    const { data } = await api.get('/api/admin/shift-definitions');
    return Array.isArray(data) ? data : [];
};

const fetchVacations = async () => {
    const { data } = await api.get('/api/vacation/all');
    return Array.isArray(data) ? data : [];
};

const fetchUsers = async () => {
    const { data } = await api.get('/api/admin/users');
    return Array.isArray(data) ? data : [];
};

const fetchSchedule = async (weekStart) => {
    const start = formatISO(weekStart, { representation: 'date' });
    const end = formatISO(addDays(weekStart, 6), { representation: 'date' });
    const { data } = await api.get('/api/admin/schedule', { params: { start, end } });
    const map = {};
    (Array.isArray(data) ? data : []).forEach(e => {
        const dayKey = formatISO(new Date(e.date), { representation: 'date' });
        if (!map[dayKey]) map[dayKey] = [];
        if (e.userId) map[dayKey].push({ userId: e.userId, id: e.id, shift: e.shift, date: e.date });
    });
    return map;
};

const saveScheduleEntry = (entry) => {
    if (entry?.id) return api.put(`/api/admin/schedule/${entry.id}`, entry);
    return api.post('/api/admin/schedule', entry);
};

const fetchScheduleLogs = async (weekStart) => {
    const start = formatISO(weekStart, { representation: 'date' });
    const end = formatISO(addDays(weekStart, 6), { representation: 'date' });
    const { data } = await api.get('/api/admin/schedule/logs', { params: { start, end } });
    return Array.isArray(data) ? data : [];
};
const deleteScheduleEntry = (id) => api.delete(`/api/admin/schedule/${id}`);
const autoFillSchedule = (entries) => api.post('/api/admin/schedule/autofill', entries);
const deleteScheduleEntries = (ids) => api.post('/api/admin/schedule/bulk-delete', ids);
const copyScheduleRequest = (entries) => api.post('/api/admin/schedule/copy', entries);

/* ===========================
   Store (Week + UI toggles)
   =========================== */
const usePlannerStore = create((set) => ({
    weekStart: startOfWeek(new Date(), { weekStartsOn: 1 }),
    dragUser: null,
    dragEntry: null,
    copiedWeek: null,

    showWeekends: true,
    highlightConflicts: true,
    disabledUserIds: new Set(),

    setWeekStart: (date) => set({ weekStart: startOfWeek(date, { weekStartsOn: 1 }) }),
    changeWeek: (offset) => set((state) => ({ weekStart: addDays(state.weekStart, offset * 7) })),
    setDragUser: (user, entry = null) => set({ dragUser: user, dragEntry: entry }),
    clearDrag: () => set({ dragUser: null, dragEntry: null }),
    setCopiedWeek: (schedule) => set({ copiedWeek: schedule }),

    setShowWeekends: (v) => set({ showWeekends: v }),
    setHighlightConflicts: (v) => set({ highlightConflicts: v }),
    toggleUserDisabled: (userId) => set((state) => {
        const next = new Set(state.disabledUserIds);
        if (next.has(userId)) next.delete(userId);
        else next.add(userId);
        return { disabledUserIds: next };
    }),
}));

/* ===========================
   Helpers
   =========================== */
const getExpectedHoursForDate = (user, dateKey) => {
    if (Boolean(user.isHourly)) return 0;

    const dateObj = new Date(dateKey);
    const dayNames = ['sunday', 'monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday'];
    const dayName = dayNames[dateObj.getDay()];
    if (user.weeklySchedule && Array.isArray(user.weeklySchedule) && user.weeklySchedule.length > 0 && user.scheduleCycle > 0) {
        try {
            const epochMonday = startOfWeek(new Date('2020-01-06'), { weekStartsOn: 1 });
            const currentMonday = startOfWeek(dateObj, { weekStartsOn: 1 });
            const weeksSinceEpoch = Math.floor(differenceInDays(currentMonday, epochMonday) / 7);
            let cycleIndex = weeksSinceEpoch % user.scheduleCycle;
            if (cycleIndex < 0) cycleIndex += user.scheduleCycle;
            const scheduleWeek = user.weeklySchedule[cycleIndex];
            if (scheduleWeek && typeof scheduleWeek[dayName] === 'number') {
                return scheduleWeek[dayName];
            }
        } catch {
            /* fallback below */
        }
    }
    if (dayName === 'saturday' || dayName === 'sunday') return 0;
    return user.dailyWorkHours ?? 8.5;
};

const getExpectedMinutesForDate = (user, dateKey) => Math.max(0, Math.round(getExpectedHoursForDate(user, dateKey) * 60));

// drag helper – mark as "move"
const beginDragAssigned = (user, entry, e) => {
    if (e?.dataTransfer) {
        e.dataTransfer.effectAllowed = 'move';
        try {
            e.dataTransfer.setData(
                'application/x-chrono-schedule',
                JSON.stringify({ userId: user.id, entryId: entry?.id, date: entry?.date, shift: entry?.shift })
            );
        } catch {}
    }
};

const timeToMinutes = (time = '00:00') => {
    const [hours, minutes] = String(time || '00:00').split(':').map(n => +n || 0);
    return (hours * 60) + minutes;
};

const shiftDurationMinutes = (shift) => {
    if (!shift) return 0;
    const start = timeToMinutes(shift.startTime);
    const end = timeToMinutes(shift.endTime);
    const duration = end - start;
    return duration > 0 ? duration : duration + (24 * 60);
};

const getShiftIntervals = (shift) => {
    if (!shift) return [];
    const start = timeToMinutes(shift.startTime);
    const end = timeToMinutes(shift.endTime);
    if (end > start) return [[start, end]];
    if (end < start) return [[start, 24 * 60], [0, end]];
    return [];
};

const shiftsOverlap = (leftShift, rightShift) => {
    const leftIntervals = getShiftIntervals(leftShift);
    const rightIntervals = getShiftIntervals(rightShift);
    return leftIntervals.some(([leftStart, leftEnd]) =>
        rightIntervals.some(([rightStart, rightEnd]) => Math.max(leftStart, rightStart) < Math.min(leftEnd, rightEnd))
    );
};

const sumEntryMinutes = (entries, shiftByKey) => (entries || []).reduce((sum, entry) => {
    const shift = shiftByKey.get(entry.shift);
    return sum + shiftDurationMinutes(shift);
}, 0);

const buildScheduleAnalysis = ({ entries, user, dateKey, shiftByKey }) => {
    const dayEntries = entries || [];
    const expectedMinutes = getExpectedMinutesForDate(user, dateKey);
    const scheduledMinutes = sumEntryMinutes(dayEntries, shiftByKey);
    const hasDuplicateShift = dayEntries.some((entry, index) =>
        dayEntries.findIndex(other => other.shift === entry.shift) !== index
    );
    const hasOverlap = dayEntries.some((entry, index) => {
        const shift = shiftByKey.get(entry.shift);
        if (!shift) return false;
        return dayEntries.slice(index + 1).some(other => shiftsOverlap(shift, shiftByKey.get(other.shift)));
    });

    return {
        expectedMinutes,
        scheduledMinutes,
        hasDuplicateShift,
        hasOverlap,
        isOverTarget: expectedMinutes > 0 && scheduledMinutes > expectedMinutes,
    };
};

const isSameUserEntryConflict = ({ existingEntries, nextShift, shiftByKey, ignoredEntryId = null }) => {
    return (existingEntries || [])
        .filter(entry => !ignoredEntryId || entry.id !== ignoredEntryId)
        .some(entry => entry.shift === nextShift.shiftKey || shiftsOverlap(nextShift, shiftByKey.get(entry.shift)));
};

const buildAutoFillEntries = ({ availableUsers, shifts, schedule, weekStart, holidays, vacationMap }) => {
    const dateKeys = Array.from({ length: 7 }, (_, i) => formatISO(addDays(weekStart, i), { representation: 'date' }));
    const activeShifts = (shifts || [])
        .filter(shift => shift.isActive)
        .slice()
        .sort((a, b) => timeToMinutes(a.startTime) - timeToMinutes(b.startTime));
    const shiftByKey = new Map((shifts || []).map(shift => [shift.shiftKey, shift]));

    const statsByUserId = new Map(availableUsers.map(user => {
        const weeklyTargetMinutes = dateKeys.reduce((sum, dateKey) => {
            const isUnavailable = holidays?.[dateKey] || (user.username && vacationMap?.[user.username]?.[dateKey]);
            return sum + (isUnavailable ? 0 : getExpectedMinutesForDate(user, dateKey));
        }, 0);

        return [user.id, {
            user,
            weeklyTargetMinutes,
            weeklyMinutes: 0,
            dailyMinutes: new Map(),
            shiftCounts: new Map(),
            assignments: 0,
        }];
    }));

    dateKeys.forEach(dateKey => {
        (schedule?.[dateKey] || []).forEach(entry => {
            const stats = statsByUserId.get(entry.userId);
            if (!stats) return;
            const duration = shiftDurationMinutes(shiftByKey.get(entry.shift));
            stats.weeklyMinutes += duration;
            stats.dailyMinutes.set(dateKey, (stats.dailyMinutes.get(dateKey) || 0) + duration);
            stats.shiftCounts.set(entry.shift, (stats.shiftCounts.get(entry.shift) || 0) + 1);
            stats.assignments += 1;
        });
    });

    const plannedSchedule = Object.fromEntries(dateKeys.map(dateKey => [dateKey, [...(schedule?.[dateKey] || [])]]));
    const newEntries = [];

    dateKeys.forEach(dateKey => {
        if (holidays?.[dateKey]) return;

        activeShifts.forEach(shift => {
            const shiftAlreadyFilled = (plannedSchedule[dateKey] || []).some(entry => entry.shift === shift.shiftKey);
            if (shiftAlreadyFilled) return;

            const duration = shiftDurationMinutes(shift);
            if (duration <= 0) return;

            const candidates = availableUsers
                .map(user => {
                    const stats = statsByUserId.get(user.id);
                    if (!stats) return null;
                    if (user.username && vacationMap?.[user.username]?.[dateKey]) return null;

                    const dayTarget = getExpectedMinutesForDate(user, dateKey);
                    if (dayTarget <= 0) return null;

                    const existingUserEntriesToday = (plannedSchedule[dateKey] || []).filter(entry => entry.userId === user.id);
                    if (isSameUserEntryConflict({ existingEntries: existingUserEntriesToday, nextShift: shift, shiftByKey })) return null;

                    const dayMinutes = stats.dailyMinutes.get(dateKey) || 0;
                    const nextDayMinutes = dayMinutes + duration;
                    const nextWeekMinutes = stats.weeklyMinutes + duration;
                    if (nextDayMinutes > dayTarget) return null;
                    if (stats.weeklyTargetMinutes > 0 && nextWeekMinutes > stats.weeklyTargetMinutes) return null;

                    const dayRemainingAfter = dayTarget - nextDayMinutes;
                    const weekRemainingAfter = Math.max(0, stats.weeklyTargetMinutes - nextWeekMinutes);
                    const shiftLoad = stats.shiftCounts.get(shift.shiftKey) || 0;
                    const weeklyLoadPercent = stats.weeklyTargetMinutes > 0
                        ? nextWeekMinutes / stats.weeklyTargetMinutes
                        : 1;

                    return {
                        user,
                        stats,
                        score: (dayRemainingAfter * 2) + (weekRemainingAfter * 0.05) + (weeklyLoadPercent * 35) + (shiftLoad * 20) + (stats.assignments * 4),
                    };
                })
                .filter(Boolean)
                .sort((left, right) => left.score - right.score || String(left.user.lastName || '').localeCompare(String(right.user.lastName || '')));

            const selected = candidates[0];
            if (!selected) return;

            const entry = { userId: selected.user.id, date: dateKey, shift: shift.shiftKey };
            newEntries.push(entry);
            plannedSchedule[dateKey].push(entry);
            selected.stats.weeklyMinutes += duration;
            selected.stats.dailyMinutes.set(dateKey, (selected.stats.dailyMinutes.get(dateKey) || 0) + duration);
            selected.stats.shiftCounts.set(shift.shiftKey, (selected.stats.shiftCounts.get(shift.shiftKey) || 0) + 1);
            selected.stats.assignments += 1;
        });
    });

    return newEntries;
};

const scheduleLogActionLabels = {
    CREATE: 'Erstellt',
    UPDATE: 'Geaendert',
    DELETE: 'Geloescht',
    AUTOFILL_CREATE: 'Automatik',
    BULK_DELETE: 'Entfernt',
    COPY_DELETE: 'Ersetzt',
    COPY_CREATE: 'Eingefuegt',
};

const formatLogTimestamp = (value) => {
    if (!value) return '-';
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) return String(value);
    return parsed.toLocaleString('de-CH', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
    });
};

const formatLogDate = (value) => {
    if (!value) return '-';
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) return String(value);
    return parsed.toLocaleDateString('de-CH', { day: '2-digit', month: '2-digit', year: 'numeric' });
};

const ScheduleChangeLogModal = ({ logs, isLoading, isRefreshing, onClose, onRefresh, weekStart }) => {
    const weekEnd = addDays(weekStart, 6);

    return (
        <div className="schedule-log-backdrop" role="presentation" onMouseDown={onClose}>
            <section
                className="schedule-log-dialog"
                role="dialog"
                aria-modal="true"
                aria-labelledby="schedule-log-title"
                onMouseDown={(event) => event.stopPropagation()}
            >
                <div className="schedule-log-header">
                    <div>
                        <h3 id="schedule-log-title">Dienstplan-Log</h3>
                        <p>{formatLogDate(weekStart)} bis {formatLogDate(weekEnd)}</p>
                    </div>
                    <div className="schedule-log-actions">
                        <button type="button" onClick={onRefresh} disabled={isRefreshing}>
                            {isRefreshing ? 'Aktualisiere...' : 'Aktualisieren'}
                        </button>
                        <button type="button" onClick={onClose} className="schedule-log-close" aria-label="Log schliessen">
                            x
                        </button>
                    </div>
                </div>

                {isLoading ? (
                    <div className="schedule-log-empty">Log wird geladen...</div>
                ) : logs.length === 0 ? (
                    <div className="schedule-log-empty">Keine Aenderungen in dieser Woche.</div>
                ) : (
                    <div className="schedule-log-table-wrap">
                        <table className="schedule-log-table">
                            <thead>
                            <tr>
                                <th>Zeitpunkt</th>
                                <th>Geaendert von</th>
                                <th>Aktion</th>
                                <th>Mitarbeiter</th>
                                <th>Datum</th>
                                <th>Schicht</th>
                                <th>Details</th>
                            </tr>
                            </thead>
                            <tbody>
                            {logs.map(log => (
                                <tr key={log.id}>
                                    <td>{formatLogTimestamp(log.createdAt)}</td>
                                    <td>{log.actorName || log.actorUsername || '-'}</td>
                                    <td>
                                        <span className={`schedule-log-badge action-${String(log.action || '').toLowerCase()}`}>
                                            {scheduleLogActionLabels[log.action] || log.action || '-'}
                                        </span>
                                    </td>
                                    <td>{log.targetName || log.targetUsername || '-'}</td>
                                    <td>{formatLogDate(log.scheduleDate)}</td>
                                    <td>{log.shift || '-'}</td>
                                    <td>{log.details || '-'}</td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </section>
        </div>
    );
};

/* ===========================
   Week Navigator (with toggles)
   =========================== */
const WeekNavigator = ({ onAutoFill, onUndoAutoFill, canUndoAutoFill, isUndoingAutoFill, onCopyWeek, onPasteWeek, canManageSchedule }) => {
    const { t } = useTranslation();
    const {
        weekStart, changeWeek, setWeekStart, copiedWeek,
        showWeekends, setShowWeekends,
        highlightConflicts, setHighlightConflicts
    } = usePlannerStore();
    const isCurrentWeek =
        formatISO(weekStart, { representation: 'date' }) ===
        formatISO(startOfWeek(new Date(), { weekStartsOn: 1 }), { representation: 'date' });

    return (
        <div className="week-controls">
            <div className="navigation-group">
                <button onClick={() => changeWeek(-1)}>&laquo; {t('schedulePlanner.prevWeek', 'Vorige')}</button>
                <span className={`week-label ${isCurrentWeek ? 'current-week' : ''}`}>
          {`${t('schedulePlanner.weekShort', 'KW')} ${format(weekStart, 'w')} / ${format(weekStart, 'yyyy')}`}
        </span>
                <button onClick={() => changeWeek(1)}>{t('schedulePlanner.nextWeek', 'Nächste')} &raquo;</button>
            </div>
            <div className="tools-group">
                <input
                    type="date"
                    value={format(weekStart, 'yyyy-MM-dd')}
                    onChange={(e) => setWeekStart(new Date(e.target.value))}
                />
                {canManageSchedule && (
                    <>
                        <button onClick={onCopyWeek} className="button-copy">Woche kopieren</button>
                        {copiedWeek && <button onClick={onPasteWeek} className="button-paste">Einfuegen</button>}
                        <button onClick={onAutoFill} className="button-autofill">Automatisch auffuellen</button>
                        {canUndoAutoFill && (
                            <button
                                type="button"
                                onClick={onUndoAutoFill}
                                className="button-autofill-undo"
                                disabled={isUndoingAutoFill}
                            >
                                {isUndoingAutoFill ? 'Wird rueckgaengig gemacht...' : 'Automatik rueckgaengig'}
                            </button>
                        )}
                    </>
                )}

                {/* Quick-Filters */}
                <label className="mini-toggle">
                    <input type="checkbox" checked={showWeekends} onChange={e => setShowWeekends(e.target.checked)} />
                    Wochenenden
                </label>
                <label className="mini-toggle">
                    <input type="checkbox" checked={highlightConflicts} onChange={e => setHighlightConflicts(e.target.checked)} />
                    Konflikte hervorheben
                </label>
            </div>
        </div>
    );
};

/* ===========================
   Sidebar: User list
   =========================== */
const UserList = ({ canManageSchedule }) => {
    const { t } = useTranslation();
    const { data: users = [], isLoading: isLoadingUsers } = useQuery({ queryKey: ['users'], queryFn: fetchUsers });
    const dragUser = usePlannerStore(state => state.dragUser);
    const setDragUser = usePlannerStore(state => state.setDragUser);
    const clearDrag = usePlannerStore(state => state.clearDrag);
    const disabledUserIds = usePlannerStore(state => state.disabledUserIds);
    const toggleUserDisabled = usePlannerStore(state => state.toggleUserDisabled);

    return (
        <div className="planner-sidebar">
            <h3>{t('schedulePlanner.availableUsers', 'Mitarbeiter')}</h3>
            <div className="user-list">
                {isLoadingUsers ? <p>Lade...</p> : users.map(u => {
                    const isDisabled = disabledUserIds.has(u.id);
                    return (
                        <div
                            key={u.id}
                            className={`user-list-item ${dragUser?.id === u.id ? 'dragging' : ''} ${isDisabled ? 'disabled' : ''}`}
                            draggable={canManageSchedule && !isDisabled}
                            onDragStart={(e) => {
                                if (!canManageSchedule || isDisabled) {
                                    e.preventDefault();
                                    return;
                                }
                                try {
                                    e.dataTransfer.effectAllowed = 'copyMove';
                                    e.dataTransfer.setData('text/plain', String(u.id ?? 'user'));
                                } catch {}
                                setDragUser(u);
                            }}
                            onDragEnd={() => clearDrag()}
                        >
                            <div className="user-list-item-info">
                                <span>{u.firstName} {u.lastName}</span>
                                {isDisabled && <span className="user-status-tag">{t('schedulePlanner.userDisabled', 'Deaktiviert')}</span>}
                            </div>
                            {canManageSchedule && (
                                <button
                                    type="button"
                                    className="user-disable-toggle"
                                    onClick={(event) => {
                                        event.stopPropagation();
                                        toggleUserDisabled(u.id);
                                    }}
                                    onMouseDown={(event) => event.stopPropagation()}
                                >
                                    {isDisabled ? t('schedulePlanner.enableUser', 'Aktivieren') : t('schedulePlanner.disableUser', 'Deaktivieren')}
                                </button>
                            )}
                        </div>
                    );
                })}
            </div>
        </div>
    );
};

/* ===========================
   Schedule table
   =========================== */
const ScheduleTable = ({ schedule, holidays, vacationMap, canManageSchedule }) => {
    const { data: users = [] } = useQuery({ queryKey: ['users'], queryFn: fetchUsers });
    const { data: shifts = [], isLoading: isLoadingShifts } = useQuery({ queryKey: ['scheduleRules'], queryFn: fetchScheduleRules });

    const weekStart = usePlannerStore(state => state.weekStart);
    const showWeekends = usePlannerStore(state => state.showWeekends);
    const highlightConflicts = usePlannerStore(state => state.highlightConflicts);
    const disabledUserIds = usePlannerStore(state => state.disabledUserIds);

    const setDragUser = usePlannerStore(state => state.setDragUser);
    const dragUser = usePlannerStore(state => state.dragUser);
    const dragEntry = usePlannerStore(state => state.dragEntry);
    const clearDrag = usePlannerStore(state => state.clearDrag);
    const { notify } = useNotification();
    const { t } = useTranslation();
    const [hoveredCell, setHoveredCell] = React.useState(null);

    const queryClient = useQueryClient();
    const weekKey = formatISO(weekStart, { representation: 'date' });

    const mutationOptions = {
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['schedule', weekKey] });
            queryClient.invalidateQueries({ queryKey: ['scheduleLogs', weekKey] });
        },
        onError: (err) => {
            const message = err?.response?.data?.message || err?.response?.data || err.message;
            notify(message);
        },
    };
    const saveMutation = useMutation({ mutationFn: saveScheduleEntry, ...mutationOptions });
    const deleteMutation = useMutation({ mutationFn: deleteScheduleEntry, ...mutationOptions });

    const indices = showWeekends ? [0,1,2,3,4,5,6] : [0,1,2,3,4];
    const visibleDays = indices.map(i => days[i]);
    const dateKeys = indices.map(i => formatISO(addDays(weekStart, i), { representation: 'date' }));

    const onDrop = (e, dateKey, shiftKey) => {
        e.preventDefault();
        e.stopPropagation();
        setHoveredCell(null);
        if (!canManageSchedule) {
            clearDrag();
            return;
        }
        if (!dragUser) return;
        if (disabledUserIds.has(dragUser.id)) {
            notify(t('schedulePlanner.userDisabledDrop', 'Der Nutzer ist deaktiviert und kann nicht zugewiesen werden.'));
            clearDrag();
            return;
        }

        // Urlaub blockiert Drop
        const vacForUser = vacationMap?.[dragUser.username]?.[dateKey];
        if (vacForUser) {
            clearDrag();
            return;
        }

        const dayEntries = schedule[dateKey] || [];
        const userAlreadyInShift = dayEntries.some(e => e.shift === shiftKey && e.userId === dragUser.id);

        const expectedHours = getExpectedHoursForDate(dragUser, dateKey);
        if (expectedHours <= 0) {
            notify(t('schedulePlanner.userDayOff', 'Der Nutzer hat an diesem Tag frei'));
            clearDrag();
            return;
        }

        const targetShift = byShiftKey.get(shiftKey);
        if (!targetShift) {
            notify(t('schedulePlanner.unknownShift', 'Diese Schicht ist nicht mehr gültig.'));
            clearDrag();
            return;
        }

        const existingUserEntriesToday = dayEntries.filter(entry => entry.userId === dragUser.id);
        if (isSameUserEntryConflict({
            existingEntries: existingUserEntriesToday,
            nextShift: targetShift,
            shiftByKey: byShiftKey,
            ignoredEntryId: dragEntry?.id,
        })) {
            notify(t('schedulePlanner.shiftOverlap', 'Diese Schicht überschneidet sich mit einer bestehenden Schicht des Mitarbeiters.'));
            clearDrag();
            return;
        }

        const existingMinutesToday = sumEntryMinutes(
            existingUserEntriesToday.filter(entry => !dragEntry?.id || entry.id !== dragEntry.id),
            byShiftKey
        );
        const nextMinutesToday = existingMinutesToday + shiftDurationMinutes(targetShift);
        const expectedMinutesToday = getExpectedMinutesForDate(dragUser, dateKey);
        if (nextMinutesToday > expectedMinutesToday) {
            notify(t('schedulePlanner.dailyLimitExceeded', 'Diese Schicht passt nicht mehr in die Sollzeit dieses Tages.'));
            clearDrag();
            return;
        }

        if (!userAlreadyInShift) {
            const payload = { userId: dragUser.id, date: dateKey, shift: shiftKey };

            // Move: vorhandenen Eintrag verschieben (optimistic)
            if (dragEntry?.id) {
                payload.id = dragEntry.id;
                queryClient.setQueryData(['schedule', weekKey], old => {
                    const newSchedule = { ...(old || {}) };
                    if (dragEntry.date) {
                        const from = (newSchedule[dragEntry.date] || []).filter(e => e.id !== dragEntry.id);
                        newSchedule[dragEntry.date] = from;
                    }
                    const to = (newSchedule[dateKey] || []).filter(e => e.id !== dragEntry.id);
                    to.push({ ...dragEntry, date: dateKey, shift: shiftKey });
                    newSchedule[dateKey] = to;
                    return newSchedule;
                });
            }

            saveMutation.mutate(payload);
        }
        clearDrag();
    };

    const clearEntry = (entryId) => {
        if (!canManageSchedule) return;
        deleteMutation.mutate(entryId);
    };

    if (isLoadingShifts) {
        return <div className="loading-indicator">Lade Schichtinformationen...</div>;
    }

    // schnelles Mapping ShiftKey->ShiftInfo
    const byShiftKey = new Map((shifts || []).map(s => [s.shiftKey, s]));

    return (
        <div className="schedule-table-wrapper">
            <table className="schedule-table">
                <thead>
                <tr>{visibleDays.map(d => <th key={d}>{d}</th>)}</tr>
                </thead>
                <tbody>
                <tr>
                    {dateKeys.map(dateKey => {
                        const today = isSameDay(new Date(dateKey), new Date());
                        const holidayName = holidays[dateKey];

                        return (
                            <td
                                key={dateKey}
                                className={`day-cell ${today ? 'day-cell-today' : ''}`}
                                title={holidayName || ''}
                            >
                                <div className="day-header">
                                    {holidayName && <span className="holiday-indicator">{holidayName}</span>}
                                    <span className="day-date">{format(new Date(dateKey), 'd.')}</span>
                                </div>

                                <div className="day-content-shifts">
                                    {shifts.filter(shift => shift.isActive).map(({ shiftKey, label, startTime, endTime }) => {
                                        const entriesForShift = (schedule[dateKey] || []).filter(e => e.shift === shiftKey);
                                        const isHovered = hoveredCell?.dateKey === dateKey && hoveredCell?.shiftKey === shiftKey;

                                        return (
                                            <div
                                                key={shiftKey}
                                                className={`shift-slot ${isHovered ? 'droppable-hover' : ''} ${!canManageSchedule ? 'read-only' : ''}`}
                                                onDragOver={(e) => {
                                                    if (!canManageSchedule) return;
                                                    e.preventDefault();
                                                    e.dataTransfer.dropEffect = 'move';
                                                    setHoveredCell({ dateKey, shiftKey });
                                                }}
                                                onDragLeave={() => setHoveredCell(null)}
                                                onDrop={(e) => onDrop(e, dateKey, shiftKey)}
                                            >
                                                <div className="shift-label">
                                                    <span>{label}</span>
                                                    <span className="shift-time">{startTime} - {endTime}</span>
                                                </div>

                                                <div className="shift-content">
                                                    {entriesForShift.length > 0 ? (
                                                        entriesForShift.map(entry => {
                                                            const user = users.find(u => u.id === entry.userId);
                                                            if (!user) return null;

                                                            const dayEntriesAll = (schedule[dateKey] || []).filter(e => e.userId === user.id);
                                                            const analysis = buildScheduleAnalysis({ entries: dayEntriesAll, user, dateKey, shiftByKey: byShiftKey });
                                                            const hasPlanConflict = analysis.hasDuplicateShift || analysis.hasOverlap || analysis.isOverTarget;
                                                            const isDayOff = analysis.expectedMinutes <= 0;
                                                            const isVacation = user.username && vacationMap?.[user.username]?.[dateKey];
                                                            const isDisabled = disabledUserIds.has(user.id);
                                                            const titleParts = [
                                                                `${analysis.scheduledMinutes} / ${analysis.expectedMinutes} Min. geplant`,
                                                                analysis.hasOverlap ? t('schedulePlanner.overlapConflict', 'Schichten überschneiden sich') : '',
                                                                analysis.isOverTarget ? t('schedulePlanner.overTargetConflict', 'Mehr geplant als Sollzeit') : '',
                                                            ].filter(Boolean);

                                                            const cls = [
                                                                'assigned-user',
                                                                dragEntry?.id === entry.id ? 'dragging' : '',
                                                                highlightConflicts && hasPlanConflict ? 'conflict-double' : '',
                                                                highlightConflicts && isDayOff ? 'conflict-dayoff' : '',
                                                                highlightConflicts && isVacation ? 'conflict-vac' : '',
                                                                isDisabled ? 'disabled-user' : ''
                                                            ].filter(Boolean).join(' ');

                                                            return (
                                                                <div
                                                                    key={entry.id}
                                                                    className={cls}
                                                                    style={{ backgroundColor: user.color || 'var(--ud-c-primary)' }}
                                                                    title={titleParts.join(' | ')}
                                                                    draggable={canManageSchedule}
                                                                    onDragStart={(e) => {
                                                                        if (!canManageSchedule) {
                                                                            e.preventDefault();
                                                                            return;
                                                                        }
                                                                        usePlannerStore.getState().setDragUser(user, entry);
                                                                        beginDragAssigned(user, entry, e);
                                                                    }}
                                                                    onDragEnd={() => clearDrag()}
                                                                >
                                                                    <span>{user.firstName} {user.lastName}</span>
                                                                    {canManageSchedule && (
                                                                        <button
                                                                            className="clear-cell-btn"
                                                                            onClick={(ev) => { ev.stopPropagation(); ev.preventDefault(); clearEntry(entry.id); }}
                                                                            title="Entfernen"
                                                                        >
                                                                            x
                                                                        </button>
                                                                    )}
                                                                </div>
                                                            );
                                                        })
                                                    ) : (
                                                        <div className="empty-shift-slot">{canManageSchedule ? '+' : ''}</div>
                                                    )}
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            </td>
                        );
                    })}
                </tr>
                </tbody>
            </table>
        </div>
    );
};

/* ===========================
   Page
   =========================== */
const AdminSchedulePlannerPage = () => {
    const { t } = useTranslation();
    const { currentUser } = useAuth();
    const { notify } = useNotification();

    const weekStart = usePlannerStore(state => state.weekStart);
    const copiedWeek = usePlannerStore(state => state.copiedWeek);
    const setCopiedWeek = usePlannerStore(state => state.setCopiedWeek);
    const navigate = useNavigate();
    const weekKey = formatISO(weekStart, { representation: 'date' });
    const [lastAutoFillBatch, setLastAutoFillBatch] = React.useState({ weekKey: null, entryIds: [] });
    const [showChangeLog, setShowChangeLog] = React.useState(false);
    const canManageSchedule = hasPageAccess(currentUser, 'adminSchedule', ACCESS_MANAGE);
    const canViewScheduleLog = isAdminUser(currentUser);
    const canOpenShiftSettings = hasPageAccess(currentUser, 'adminShiftRules', ACCESS_MANAGE);
    const canPrintSchedule = hasPageAccess(currentUser, 'adminPrintSchedule', ACCESS_VIEW);

    const queryClient = useQueryClient();

    const { data: schedule, isLoading } = useQuery({
        queryKey: ['schedule', weekKey],
        queryFn: () => fetchSchedule(weekStart),
        initialData: {},
    });

    const { data: users = [] } = useQuery({ queryKey: ['users'], queryFn: fetchUsers });
    const { data: shifts = [] } = useQuery({ queryKey: ['scheduleRules'], queryFn: fetchScheduleRules });
    const {
        data: scheduleLogs = [],
        isLoading: isLoadingScheduleLogs,
        isFetching: isFetchingScheduleLogs,
        refetch: refetchScheduleLogs,
    } = useQuery({
        queryKey: ['scheduleLogs', weekKey],
        queryFn: () => fetchScheduleLogs(weekStart),
        enabled: canViewScheduleLog && showChangeLog,
        initialData: [],
    });
    const disabledUserIds = usePlannerStore(state => state.disabledUserIds);
    const availableUsers = React.useMemo(
        () => users.filter(u => !disabledUserIds.has(u.id)),
        [users, disabledUserIds]
    );

    const { data: holidays = {} } = useQuery({
        queryKey: ['holidays', weekStart.getFullYear(), currentUser?.company?.cantonAbbreviation],
        queryFn: async () => {
            const year = weekStart.getFullYear();
            const canton = currentUser?.company?.cantonAbbreviation || 'ZH';
            const startDate = `${year}-01-01`;
            const endDate = `${year}-12-31`;
            const { data } = await api.get('/api/holidays/details', {
                params: { year, cantonAbbreviation: canton, startDate, endDate }
            });
            return data || {};
        },
        enabled: !!currentUser,
    });

    const { data: vacations = [] } = useQuery({ queryKey: ['vacations'], queryFn: fetchVacations });

    const vacationMap = React.useMemo(() => {
        const map = {};
        (Array.isArray(vacations) ? vacations : []).filter(v => v.approved).forEach(v => {
            if (!map[v.username]) map[v.username] = {};
            let d = new Date(v.startDate);
            const end = new Date(v.endDate);
            while (d <= end) {
                const key = formatISO(d, { representation: 'date' });
                map[v.username][key] = v;
                d = addDays(d, 1);
            }
        });
        return map;
    }, [vacations]);

    // KPIs
    const allDayKeys = Array.from({ length: 7 }, (_, i) => formatISO(addDays(weekStart, i), { representation: 'date' }));
    const activeShifts = (shifts || []).filter(s => s.isActive);
    const shiftByKey = React.useMemo(() => new Map((shifts || []).map(shift => [shift.shiftKey, shift])), [shifts]);

    const kpi = React.useMemo(() => {
        let emptySlots = 0;
        let planConflicts = 0;
        let vacationConflicts = 0;

        allDayKeys.forEach(dk => {
            const entries = schedule[dk] || [];

            // leere Slots
            activeShifts.forEach(sh => {
                if (!entries.some(e => e.shift === sh.shiftKey)) emptySlots++;
            });

            const byUser = new Map();
            entries.forEach(entry => {
                if (!byUser.has(entry.userId)) byUser.set(entry.userId, []);
                byUser.get(entry.userId).push(entry);
            });
            byUser.forEach((userEntries, userId) => {
                const user = users.find(x => x.id === userId);
                if (!user) return;
                const analysis = buildScheduleAnalysis({ entries: userEntries, user, dateKey: dk, shiftByKey });
                if (analysis.hasDuplicateShift || analysis.hasOverlap || analysis.isOverTarget) planConflicts++;
            });

            // Urlaubskonflikte
            entries.forEach(e => {
                const u = users.find(x => x.id === e.userId);
                if (u?.username && vacationMap?.[u.username]?.[dk]) vacationConflicts++;
            });
        });

        return { emptySlots, planConflicts, vacationConflicts, users: users.length };
    }, [allDayKeys, schedule, activeShifts, users, vacationMap, shiftByKey]);

    const autoFillMutation = useMutation({
        mutationFn: autoFillSchedule,
        onSuccess: (response) => {
            queryClient.invalidateQueries({ queryKey: ['schedule', weekKey] });
            queryClient.invalidateQueries({ queryKey: ['scheduleLogs', weekKey] });
            const created = response?.data?.created;
            const entryIds = Array.isArray(response?.data?.entries)
                ? response.data.entries.map(entry => entry.id).filter(Boolean)
                : [];
            setLastAutoFillBatch({ weekKey, entryIds });
            if (typeof created === 'number') {
                notify({
                    message: t('schedulePlanner.autoFillCreated', `${created} Schichten automatisch eingetragen.`),
                    type: created > 0 ? 'success' : 'info',
                });
            }
        },
        onError: (err) => alert(`Fehler beim automatischen Füllen: ${err.response?.data?.message || err.message}`),
    });

    const undoAutoFillMutation = useMutation({
        mutationFn: deleteScheduleEntries,
        onSuccess: (response) => {
            queryClient.invalidateQueries({ queryKey: ['schedule', weekKey] });
            queryClient.invalidateQueries({ queryKey: ['scheduleLogs', weekKey] });
            const deleted = response?.data?.deleted;
            const deletedCount = typeof deleted === 'number' ? deleted : lastAutoFillBatch.entryIds.length;
            setLastAutoFillBatch({ weekKey: null, entryIds: [] });
            notify({
                message: t('schedulePlanner.autoFillUndone', `${deletedCount} automatisch eingetragene Schichten entfernt.`),
                type: 'success',
            });
        },
        onError: (err) => {
            const message = err?.response?.data?.message || err?.response?.data || err.message;
            notify({ message, type: 'error' });
        },
    });

    const copyWeekMutation = useMutation({
        mutationFn: copyScheduleRequest,
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['schedule', weekKey] });
            queryClient.invalidateQueries({ queryKey: ['scheduleLogs', weekKey] });
            alert('Woche erfolgreich eingefügt!');
        },
        onError: (err) => alert(`Fehler beim Einfügen der Woche: ${err.response?.data?.message || err.message}`),
    });

    const handleAutoFill = () => {
        if (!canManageSchedule) return;
        if (availableUsers.length === 0) {
            alert(t('schedulePlanner.noActiveUsers', 'Keine aktiven Mitarbeiter für das automatische Auffüllen verfügbar.'));
            return;
        }
        const newEntries = buildAutoFillEntries({
            availableUsers,
            shifts,
            schedule,
            weekStart,
            holidays,
            vacationMap,
        });

        if (newEntries.length > 0) autoFillMutation.mutate(newEntries);
        else {
            notify({
                message: t('schedulePlanner.noAutoFillCandidates', 'Keine passenden freien Schichten gefunden. Mitarbeiter, Sollzeiten, Urlaub und bestehende Planung sind bereits berücksichtigt.'),
                type: 'info',
            });
        }
    };

    const canUndoAutoFill = lastAutoFillBatch.weekKey === weekKey && lastAutoFillBatch.entryIds.length > 0;

    const handleUndoAutoFill = () => {
        if (!canManageSchedule) return;
        if (!canUndoAutoFill) return;
        undoAutoFillMutation.mutate(lastAutoFillBatch.entryIds);
    };

    const handleCopyWeek = () => {
        if (!canManageSchedule) return;
        const allEntries = Object.values(schedule).flat();
        if (allEntries.length === 0) {
            alert('Diese Woche ist leer und kann nicht kopiert werden.');
            return;
        }
        setCopiedWeek({ sourceWeekStart: weekStart, entries: allEntries });
        alert('Woche kopiert!');
    };

    const handlePasteWeek = () => {
        if (!canManageSchedule) return;
        if (!copiedWeek) return;
        const dayOffset = differenceInDays(weekStart, copiedWeek.sourceWeekStart);
        const newEntries = copiedWeek.entries.map(entry => {
            const newDate = addDays(new Date(entry.date), dayOffset);
            return { ...entry, date: formatISO(newDate, { representation: 'date' }), id: null };
        });
        copyWeekMutation.mutate(newEntries);
    };

    const handlePrintSchedule = () => {
        const weeks = parseInt(prompt(t('schedulePlanner.printWeeksPrompt', 'Wie viele Wochen möchten Sie drucken?'), '1'), 10);
        if (!weeks || weeks < 1) return;
        const start = formatISO(weekStart, { representation: 'date' });
        navigate(`/admin/print-schedule?start=${start}&weeks=${weeks}`);
    };

    return (
        <>
            <Navbar />
            <div className="schedule-planner-page scoped-dashboard">
                <div className="planner-layout">
                    <div className="planner-main">
                        <div className="planner-header">
                            <h2 className="cmp-title">Dienstplan</h2>
                            <div className="header-actions">
                                {canViewScheduleLog && (
                                    <button type="button" onClick={() => setShowChangeLog(true)} className="button-log">
                                        Aenderungslog
                                    </button>
                                )}
                                {canOpenShiftSettings && (
                                    <Link to="/admin/shift-rules" className="button-settings">
                                        Schicht-Einstellungen
                                    </Link>
                                )}
                                {canPrintSchedule && (
                                    <button onClick={handlePrintSchedule} className="button-print">
                                        Drucken
                                    </button>
                                )}
                            </div>
                        </div>

                        {/* KPI Strip */}
                        <div className="planner-kpis">
                            <div className="kpi"><span className="kpi-label">Leere Slots</span><strong className="kpi-value">{kpi.emptySlots}</strong></div>
                            <div className="kpi"><span className="kpi-label">Plan-Konflikte</span><strong className="kpi-value">{kpi.planConflicts}</strong></div>
                            <div className="kpi"><span className="kpi-label">Urlaub-Konflikte</span><strong className="kpi-value">{kpi.vacationConflicts}</strong></div>
                            <div className="kpi"><span className="kpi-label">Mitarbeiter</span><strong className="kpi-value">{kpi.users}</strong></div>
                        </div>

                        <WeekNavigator
                            onAutoFill={handleAutoFill}
                            onUndoAutoFill={handleUndoAutoFill}
                            canUndoAutoFill={canUndoAutoFill}
                            isUndoingAutoFill={undoAutoFillMutation.isPending}
                            onCopyWeek={handleCopyWeek}
                            onPasteWeek={handlePasteWeek}
                            canManageSchedule={canManageSchedule}
                        />

                        {isLoading ? (
                            <div className="loading-indicator">Lade Arbeitsplan...</div>
                        ) : (
                            <ScheduleTable
                                schedule={schedule}
                                holidays={holidays}
                                vacationMap={vacationMap}
                                canManageSchedule={canManageSchedule}
                            />
                        )}
                    </div>
                    <UserList canManageSchedule={canManageSchedule} />
                </div>
                {canViewScheduleLog && showChangeLog && (
                    <ScheduleChangeLogModal
                        logs={scheduleLogs}
                        isLoading={isLoadingScheduleLogs}
                        isRefreshing={isFetchingScheduleLogs}
                        onClose={() => setShowChangeLog(false)}
                        onRefresh={() => refetchScheduleLogs()}
                        weekStart={weekStart}
                    />
                )}
            </div>
        </>
    );
};

export default AdminSchedulePlannerPage;
