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
const deleteScheduleEntry = (id) => api.delete(`/api/admin/schedule/${id}`);
const autoFillSchedule = (entries) => api.post('/api/admin/schedule/autofill', entries);
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

const hoursBetween = (a = '00:00', b = '00:00') => {
    const [ah, am] = a.split(':').map(n => +n || 0);
    const [bh, bm] = b.split(':').map(n => +n || 0);
    return Math.max(0, (bh + bm / 60) - (ah + am / 60));
};

/* ===========================
   Week Navigator (with toggles)
   =========================== */
const WeekNavigator = ({ onAutoFill, onCopyWeek, onPasteWeek }) => {
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
                <button onClick={onCopyWeek} className="button-copy">Woche kopieren</button>
                {copiedWeek && <button onClick={onPasteWeek} className="button-paste">Einfügen</button>}
                <button onClick={onAutoFill} className="button-autofill">Automatisch auffüllen</button>

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
const UserList = () => {
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
                            draggable={!isDisabled}
                            onDragStart={(e) => {
                                if (isDisabled) {
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
const ScheduleTable = ({ schedule, holidays, vacationMap }) => {
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
        deleteMutation.mutate(entryId);
    };

    if (isLoadingShifts) {
        return <div className="loading-indicator">Lade Schichtinformationen...</div>;
    }

    // schnelles Mapping ShiftKey->ShiftInfo
    const byShiftKey = Object.fromEntries((shifts || []).map(s => [s.shiftKey, s]));

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
                                                className={`shift-slot ${isHovered ? 'droppable-hover' : ''}`}
                                                onDragOver={(e) => { e.preventDefault(); e.dataTransfer.dropEffect = 'move'; setHoveredCell({ dateKey, shiftKey }); }}
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

                                                            // Konflikt-Ermittlung
                                                            const dayEntriesAll = (schedule[dateKey] || []).filter(e => e.userId === user.id);
                                                            const isDoubleBooked = dayEntriesAll.length > 1;
                                                            const expected = getExpectedHoursForDate(user, dateKey);
                                                            const isDayOff = expected <= 0;
                                                            const isVacation = user.username && vacationMap?.[user.username]?.[dateKey];
                                                            const isDisabled = disabledUserIds.has(user.id);

                                                            const cls = [
                                                                'assigned-user',
                                                                dragEntry?.id === entry.id ? 'dragging' : '',
                                                                highlightConflicts && isDoubleBooked ? 'conflict-double' : '',
                                                                highlightConflicts && isDayOff ? 'conflict-dayoff' : '',
                                                                highlightConflicts && isVacation ? 'conflict-vac' : '',
                                                                isDisabled ? 'disabled-user' : ''
                                                            ].filter(Boolean).join(' ');

                                                            return (
                                                                <div
                                                                    key={entry.id}
                                                                    className={cls}
                                                                    style={{ backgroundColor: user.color || 'var(--ud-c-primary)' }}
                                                                    draggable
                                                                    onDragStart={(e) => { usePlannerStore.getState().setDragUser(user, entry); beginDragAssigned(user, entry, e); }}
                                                                    onDragEnd={() => clearDrag()}
                                                                >
                                                                    <span>{user.firstName} {user.lastName}</span>
                                                                    <button
                                                                        className="clear-cell-btn"
                                                                        onClick={(ev) => { ev.stopPropagation(); ev.preventDefault(); clearEntry(entry.id); }}
                                                                        title="Entfernen"
                                                                    >
                                                                        ×
                                                                    </button>
                                                                </div>
                                                            );
                                                        })
                                                    ) : (
                                                        <div className="empty-shift-slot">+</div>
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

    const weekStart = usePlannerStore(state => state.weekStart);
    const copiedWeek = usePlannerStore(state => state.copiedWeek);
    const setCopiedWeek = usePlannerStore(state => state.setCopiedWeek);
    const navigate = useNavigate();
    const weekKey = formatISO(weekStart, { representation: 'date' });

    const queryClient = useQueryClient();

    const { data: schedule, isLoading } = useQuery({
        queryKey: ['schedule', weekKey],
        queryFn: () => fetchSchedule(weekStart),
        initialData: {},
    });

    const { data: users = [] } = useQuery({ queryKey: ['users'], queryFn: fetchUsers });
    const { data: shifts = [] } = useQuery({ queryKey: ['scheduleRules'], queryFn: fetchScheduleRules });
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

    const kpi = React.useMemo(() => {
        let emptySlots = 0;
        let double = 0;
        let vacationConflicts = 0;

        allDayKeys.forEach(dk => {
            const entries = schedule[dk] || [];

            // leere Slots
            activeShifts.forEach(sh => {
                if (!entries.some(e => e.shift === sh.shiftKey)) emptySlots++;
            });

            // Doppelbuchungen je Nutzer/Tag
            const byUser = new Map();
            entries.forEach(e => byUser.set(e.userId, (byUser.get(e.userId) || 0) + 1));
            byUser.forEach(v => { if (v > 1) double++; });

            // Urlaubskonflikte
            entries.forEach(e => {
                const u = users.find(x => x.id === e.userId);
                if (u?.username && vacationMap?.[u.username]?.[dk]) vacationConflicts++;
            });
        });

        return { emptySlots, double, vacationConflicts, users: users.length };
    }, [allDayKeys, schedule, activeShifts, users, vacationMap]);

    const autoFillMutation = useMutation({
        mutationFn: autoFillSchedule,
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['schedule', weekKey] });
        },
        onError: (err) => alert(`Fehler beim automatischen Füllen: ${err.response?.data?.message || err.message}`),
    });

    const copyWeekMutation = useMutation({
        mutationFn: copyScheduleRequest,
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['schedule', weekKey] });
            alert('Woche erfolgreich eingefügt!');
        },
        onError: (err) => alert(`Fehler beim Einfügen der Woche: ${err.response?.data?.message || err.message}`),
    });

    const handleAutoFill = () => {
        if (availableUsers.length === 0) {
            alert(t('schedulePlanner.noActiveUsers', 'Keine aktiven Mitarbeiter für das automatische Auffüllen verfügbar.'));
            return;
        }
        const newEntries = [];
        const dateKeys = Array.from({ length: 7 }, (_, i) => formatISO(addDays(weekStart, i), { representation: 'date' }));
        let userIndex = 0;

        dateKeys.forEach(dateKey => {
            if (holidays[dateKey]) return;

            const assignedUsersToday = new Set((schedule[dateKey] || []).map(e => e.userId));
            const active = shifts.filter(s => s.isActive);

            active.forEach(shift => {
                const alreadyAssignedToShift = (schedule[dateKey] || []).some(e => e.shift === shift.shiftKey);
                if (!alreadyAssignedToShift && availableUsers.length > 0) {
                    let assigned = false;
                    let attempts = 0;
                    while (!assigned && attempts < availableUsers.length) {
                        const userToAssign = availableUsers[userIndex % availableUsers.length];
                        const username = userToAssign.username;
                        const onVacation = username && vacationMap[username] && vacationMap[username][dateKey];
                        if (
                            !assignedUsersToday.has(userToAssign.id) &&
                            getExpectedHoursForDate(userToAssign, dateKey) > 0 &&
                            !onVacation
                        ) {
                            newEntries.push({ userId: userToAssign.id, date: dateKey, shift: shift.shiftKey });
                            assignedUsersToday.add(userToAssign.id);
                            assigned = true;
                        }
                        userIndex++;
                        attempts++;
                    }
                }
            });
        });

        if (newEntries.length > 0) autoFillMutation.mutate(newEntries);
        else alert('Keine leeren Schichten zum Füllen gefunden oder keine Mitarbeiter verfügbar.');
    };

    const handleCopyWeek = () => {
        const allEntries = Object.values(schedule).flat();
        if (allEntries.length === 0) {
            alert('Diese Woche ist leer und kann nicht kopiert werden.');
            return;
        }
        setCopiedWeek({ sourceWeekStart: weekStart, entries: allEntries });
        alert('Woche kopiert!');
    };

    const handlePasteWeek = () => {
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
                                <Link to="/admin/shift-rules" className="button-settings">
                                    <span role="img" aria-label="settings">⚙️</span>
                                    Schicht-Einstellungen
                                </Link>
                                <button onClick={handlePrintSchedule} className="button-print">
                                    <span role="img" aria-label="print">🖨️</span>
                                    Drucken
                                </button>
                            </div>
                        </div>

                        {/* KPI Strip */}
                        <div className="planner-kpis">
                            <div className="kpi"><span className="kpi-label">Leere Slots</span><strong className="kpi-value">{kpi.emptySlots}</strong></div>
                            <div className="kpi"><span className="kpi-label">Doppelbuchungen</span><strong className="kpi-value">{kpi.double}</strong></div>
                            <div className="kpi"><span className="kpi-label">Urlaub-Konflikte</span><strong className="kpi-value">{kpi.vacationConflicts}</strong></div>
                            <div className="kpi"><span className="kpi-label">Mitarbeiter</span><strong className="kpi-value">{kpi.users}</strong></div>
                        </div>

                        <WeekNavigator
                            onAutoFill={handleAutoFill}
                            onCopyWeek={handleCopyWeek}
                            onPasteWeek={handlePasteWeek}
                        />

                        {isLoading ? (
                            <div className="loading-indicator">Lade Arbeitsplan...</div>
                        ) : (
                            <ScheduleTable schedule={schedule} holidays={holidays} vacationMap={vacationMap} />
                        )}
                    </div>
                    <UserList />
                </div>
            </div>
        </>
    );
};

export default AdminSchedulePlannerPage;
