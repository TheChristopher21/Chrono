import React from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { create } from 'zustand';
import { startOfWeek, addDays, formatISO, format, isSameDay, differenceInDays } from 'date-fns';
import { Link } from 'react-router-dom';
import Navbar from '../../components/Navbar';
import api from '../../utils/api';
import '../../styles/AdminSchedulePlannerPageScooped.css';
import { useTranslation } from '../../context/LanguageContext';
import { useNotification } from '../../context/NotificationContext'; // NEU
import { useAuth } from '../../context/AuthContext';

const days = ['Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag'];

const fetchScheduleRules = async () => {
  const { data } = await api.get('/api/admin/shift-definitions');
  return Array.isArray(data) ? data : [];
};

const fetchVacations = async () => {
  const { data } = await api.get('/api/vacation/all');
  return Array.isArray(data) ? data : [];
};


const usePlannerStore = create((set) => ({
  weekStart: startOfWeek(new Date(), { weekStartsOn: 1 }),
  dragUser: null,
  copiedWeek: null,
  setWeekStart: (date) => set({ weekStart: startOfWeek(date, { weekStartsOn: 1 }) }),
  changeWeek: (offset) => set((state) => ({ weekStart: addDays(state.weekStart, offset * 7) })),
  setDragUser: (user) => set({ dragUser: user }),
  setCopiedWeek: (schedule) => set({ copiedWeek: schedule }),
}));

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
    if (!map[dayKey]) {
      map[dayKey] = [];
    }
    if (e.userId) {
      map[dayKey].push({ userId: e.userId, id: e.id, shift: e.shift, date: e.date });
    }
  });
  return map;
};

const saveScheduleEntry = (entry) => api.post('/api/admin/schedule', entry);
const deleteScheduleEntry = (id) => api.delete(`/api/admin/schedule/${id}`);
const autoFillSchedule = (entries) => api.post('/api/admin/schedule/autofill', entries);
const copyScheduleRequest = (entries) => api.post('/api/admin/schedule/copy', entries);

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
    } catch (e) {
      // ignore and fall back
    }
  }
  if (dayName === 'saturday' || dayName === 'sunday') return 0;
  return user.dailyWorkHours ?? 8.5;
};

const WeekNavigator = ({ onAutoFill, onCopyWeek, onPasteWeek }) => {
  const { t } = useTranslation();
  const { weekStart, changeWeek, setWeekStart, copiedWeek } = usePlannerStore();
  const isCurrentWeek = formatISO(weekStart, { representation: 'date' }) === formatISO(startOfWeek(new Date(), { weekStartsOn: 1 }), { representation: 'date' });

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
          <input type="date" value={format(weekStart, 'yyyy-MM-dd')} onChange={(e) => setWeekStart(new Date(e.target.value))} />
          <button onClick={() => window.print()} className="button-print">{t('schedulePlanner.print', 'Drucken')}</button>
          <button onClick={onCopyWeek} className="button-copy">Woche kopieren</button>
          {copiedWeek && <button onClick={onPasteWeek} className="button-paste">Einfügen</button>}
          <button onClick={onAutoFill} className="button-autofill">Automatisch auffüllen</button>
        </div>
      </div>
  );
};

const UserList = () => {
  const { t } = useTranslation();
  const { data: users = [], isLoading: isLoadingUsers } = useQuery({ queryKey: ['users'], queryFn: fetchUsers });
  const { dragUser, setDragUser } = usePlannerStore();
  return (
      <div className="planner-sidebar">
        <h3>{t('schedulePlanner.availableUsers', 'Mitarbeiter')}</h3>
        <div className="user-list">
          {isLoadingUsers ? <p>Lade...</p> : users.map(u => (
              <div key={u.id} className={`user-list-item ${dragUser?.id === u.id ? 'dragging' : ''}`} draggable onDragStart={() => setDragUser(u)} onDragEnd={() => setDragUser(null)}>
                {u.firstName} {u.lastName}
              </div>
          ))}
        </div>
      </div>
  );
};

const ScheduleTable = ({ schedule, holidays, vacationMap }) => {
  const { data: users = [] } = useQuery({ queryKey: ['users'], queryFn: fetchUsers });
  const { data: shifts = [], isLoading: isLoadingShifts } = useQuery({ queryKey: ['scheduleRules'], queryFn: fetchScheduleRules });
  const { weekStart, dragUser, setDragUser } = usePlannerStore();
  const { notify } = useNotification(); // NEU
  const { t } = useTranslation();
  const [hoveredCell, setHoveredCell] = React.useState(null);
  const queryClient = useQueryClient();
  const mutationOptions = {
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['schedule', weekStart] });
    },
    onError: (err) => {
      const message = err?.response?.data?.message || err?.response?.data || err.message;
      notify(message);
    },
  };
  const saveMutation = useMutation({ mutationFn: saveScheduleEntry, ...mutationOptions });
  const deleteMutation = useMutation({ mutationFn: deleteScheduleEntry, ...mutationOptions });

  const onDrop = (dateKey, shiftKey) => {
    setHoveredCell(null);
    if (!dragUser) return;

    const vacForUser = vacationMap?.[dragUser.username]?.[dateKey];
    if (vacForUser) { // Verhindert das Ablegen, wenn der User im Urlaub ist
      setDragUser(null);
      return;
    }

    const dayEntries = schedule[dateKey] || [];
    const userAlreadyInShift = dayEntries.some(e => e.shift === shiftKey && e.userId === dragUser.id);

    const expectedHours = getExpectedHoursForDate(dragUser, dateKey);
    if (expectedHours <= 0) {
      notify(t('schedulePlanner.userDayOff', 'Der Nutzer hat an diesem Tag frei'));
      setDragUser(null);
      return;
    }

    if (!userAlreadyInShift) {
      saveMutation.mutate({ userId: dragUser.id, date: dateKey, shift: shiftKey });
    }

    setDragUser(null);
  };

  const clearEntry = (entryId) => {
    deleteMutation.mutate(entryId);
  };

  const dateKeys = days.map((_, i) => formatISO(addDays(weekStart, i), { representation: 'date' }));
  if (isLoadingShifts) {
    return <div className="loading-indicator">Lade Schichtinformationen...</div>;
  }
  return (
      <div className="schedule-table-wrapper">
        <table className="schedule-table">
          <thead>
          <tr>{days.map(d => <th key={d}>{d}</th>)}</tr>
          </thead>
          <tbody>
          <tr>
            {dateKeys.map(dateKey => {
              const today = isSameDay(new Date(dateKey), new Date());
              const holidayName = holidays[dateKey];
              // NEU: Prüfen, ob der gezogene User an diesem Tag Urlaub hat
              const isVacationDayForDraggedUser = dragUser && vacationMap?.[dragUser.username]?.[dateKey];

              return (
                  <td key={dateKey}
                      // NEU: Fügt die "on-vacation" Klasse hinzu, wenn der User im Urlaub ist
                      className={`day-cell ${today ? 'day-cell-today' : ''} ${isVacationDayForDraggedUser ? 'on-vacation' : ''}`}
                      title={holidayName || ''} // NEU: Fügt den Tooltip für den ganzen Tag hinzu
                  >
                    <div className="day-header">
                      {holidayName && <span className="holiday-indicator">{holidayName}</span>}
                      <span className="day-date">{format(new Date(dateKey), 'd.')}</span>
                    </div>
                    <div className="day-content-shifts">
                      {/* NEU: Zeigt "Im Urlaub" anstelle der Schichten an */}
                      {isVacationDayForDraggedUser ? (
                          <div className="vacation-blocker">{t('schedulePlanner.onVacationText', 'Im Urlaub')}</div>
                      ) : (<>
                      {shifts.filter(shift => shift.isActive).map(({ shiftKey, label, startTime, endTime }) => {
                        const entriesForShift = (schedule[dateKey] || []).filter(e => e.shift === shiftKey);
                        const isHovered = hoveredCell?.dateKey === dateKey && hoveredCell?.shiftKey === shiftKey;

                        return (
                            <div
                                key={shiftKey}
                                className={`shift-slot ${isHovered ? 'droppable-hover' : ''}`}
                                onDragOver={(e) => { e.preventDefault(); setHoveredCell({ dateKey, shiftKey }); }}
                                onDragLeave={() => setHoveredCell(null)}
                                onDrop={() => onDrop(dateKey, shiftKey)}
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
                                      return (
                                          <div key={entry.id} className="assigned-user" style={{ backgroundColor: user.color || 'var(--ud-c-primary)' }}>
                                            <span>{user.firstName} {user.lastName}</span>
                                            <button className="clear-cell-btn" onClick={() => clearEntry(entry.id)}>×</button>
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
                      </>)}
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


const AdminSchedulePlannerPage = () => {
  const { t } = useTranslation();
  const { currentUser } = useAuth();
  // FIX: Select state primitives individually from the store to prevent unnecessary re-renders.
  const weekStart = usePlannerStore(state => state.weekStart);
  const copiedWeek = usePlannerStore(state => state.copiedWeek);
  const setCopiedWeek = usePlannerStore(state => state.setCopiedWeek);

  const queryClient = useQueryClient();

  const { data: schedule, isLoading } = useQuery({
    queryKey: ['schedule', weekStart],
    queryFn: () => fetchSchedule(weekStart),
    initialData: {},
  });

  const { data: users = [] } = useQuery({ queryKey: ['users'], queryFn: fetchUsers });
  const { data: shifts = [] } = useQuery({ queryKey: ['scheduleRules'], queryFn: fetchScheduleRules });

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


  const autoFillMutation = useMutation({
    mutationFn: autoFillSchedule,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['schedule', weekStart] });
    },
    onError: (err) => alert(`Fehler beim automatischen Füllen: ${err.response?.data?.message || err.message}`),
  });

  const copyWeekMutation = useMutation({
    mutationFn: copyScheduleRequest,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['schedule', weekStart] });
      alert("Woche erfolgreich eingefügt!");
    },
    onError: (err) => alert(`Fehler beim Einfügen der Woche: ${err.response?.data?.message || err.message}`),
  });

  const handleAutoFill = () => {
    const newEntries = [];
    const dateKeys = days.map((_, i) => formatISO(addDays(weekStart, i), { representation: 'date' }));
    let userIndex = 0;

    dateKeys.forEach(dateKey => {
      if (holidays[dateKey]) {
        return;
      }

      const assignedUsersToday = new Set((schedule[dateKey] || []).map(e => e.userId));
      const activeShifts = shifts.filter(s => s.isActive);

      activeShifts.forEach(shift => {
        const alreadyAssignedToShift = (schedule[dateKey] || []).some(e => e.shift === shift.shiftKey);
        if (!alreadyAssignedToShift && users.length > 0) {
          let assigned = false;
          let attempts = 0;
          while (!assigned && attempts < users.length) {
            const userToAssign = users[userIndex % users.length];
            const username = userToAssign.username;
            const onVacation = username && vacationMap[username] && vacationMap[username][dateKey];
            if (!assignedUsersToday.has(userToAssign.id)
                && getExpectedHoursForDate(userToAssign, dateKey) > 0
                && !onVacation) {
              newEntries.push({
                userId: userToAssign.id,
                date: dateKey,
                shift: shift.shiftKey,
              });
              assignedUsersToday.add(userToAssign.id);
              assigned = true;
            }
            userIndex++;
            attempts++;
          }
        }
      });
    });

    if (newEntries.length > 0) {
      autoFillMutation.mutate(newEntries);
    } else {
      alert("Keine leeren Schichten zum Füllen gefunden oder keine Mitarbeiter verfügbar.");
    }
  };

  const handleCopyWeek = () => {
    const allEntries = Object.values(schedule).flat();
    if (allEntries.length === 0) {
      alert("Diese Woche ist leer und kann nicht kopiert werden.");
      return;
    }
    setCopiedWeek({
      sourceWeekStart: weekStart,
      entries: allEntries
    });
    alert("Woche kopiert!");
  };

  const handlePasteWeek = () => {
    if (!copiedWeek) return;

    const dayOffset = differenceInDays(weekStart, copiedWeek.sourceWeekStart);

    const newEntries = copiedWeek.entries.map(entry => {
      const newDate = addDays(new Date(entry.date), dayOffset);
      return {
        ...entry,
        date: formatISO(newDate, { representation: 'date' }),
        id: null
      };
    });

    copyWeekMutation.mutate(newEntries);
  };

  return (
      <>
        <Navbar />
        <div className="schedule-planner-page scoped-dashboard">
          <div className="planner-layout">
            <div className="planner-main">
              <div className="planner-header">
                <h2 className="cmp-title">Dienstplan</h2>
                <Link to="/admin/shift-rules" className="button-settings">
                  <span role="img" aria-label="settings">⚙️</span>
                  Schicht-Einstellungen
                </Link>
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