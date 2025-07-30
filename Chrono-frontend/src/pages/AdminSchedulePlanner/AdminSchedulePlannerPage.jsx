import React from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { create } from 'zustand';
import { startOfWeek, addDays, formatISO, format, differenceInCalendarWeeks, isSameWeek, isSameDay } from 'date-fns';

import Navbar from '../../components/Navbar';
import ModalOverlay from '../../components/ModalOverlay';
import api from '../../utils/api';
import '../../styles/AdminSchedulePlannerPageScooped.css';
import { useTranslation } from '../../context/LanguageContext';

const days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

// Zustand-Store für den UI-State
const usePlannerStore = create((set) => ({
  weekStart: startOfWeek(new Date(), { weekStartsOn: 1 }),
  dragUser: null,
  copyCount: 1,
  setWeekStart: (date) => set({ weekStart: startOfWeek(date, { weekStartsOn: 1 }) }),
  changeWeek: (offset) => set((state) => ({ weekStart: addDays(state.weekStart, offset * 7) })),
  setDragUser: (user) => set({ dragUser: user }),
  setCopyCount: (count) => set({ copyCount: count }),
}));

// API-Funktionen
const fetchUsers = async () => {
  const res = await api.get('/api/admin/users');
  return Array.isArray(res.data) ? res.data : [];
};

const fetchSchedule = async (weekStart) => {
  const start = formatISO(weekStart, { representation: 'date' });
  const end = formatISO(addDays(weekStart, 6), { representation: 'date' });
  const res = await api.get('/api/admin/schedule', { params: { start, end } });
  const map = {};
  (res.data || []).forEach(e => {
    const dayKey = formatISO(new Date(e.date), { representation: 'date' });
    map[dayKey] = { userId: e.userId, id: e.id, note: e.note };
  });
  return map;
};

const saveScheduleEntry = (entry) => {
  return api.post('/api/admin/schedule', { ...entry, shift: 'DAY' });
};

// --- Sub-Komponenten ---

const WeekNavigator = () => {
  const { t } = useTranslation();
  const { weekStart, changeWeek, setWeekStart, copyCount, setCopyCount } = usePlannerStore();
  const { data: schedule = {} } = useQuery({ queryKey: ['schedule', weekStart], queryFn: () => fetchSchedule(weekStart) });
  const queryClient = useQueryClient();

  const isCurrentWeek = isSameWeek(weekStart, new Date(), { weekStartsOn: 1 });
  const weekLabel = `${t('schedulePlanner.weekShort', 'KW')} ${format(weekStart, 'I')} / ${format(weekStart, 'yyyy')}`;
  const rangeLabel = `${format(weekStart, 'dd.MM.')}–${format(addDays(weekStart, 6), 'dd.MM.yyyy')}`;

  const copyWeeksMutation = useMutation({
    mutationFn: async (weeks) => {
      const promises = [];
      for (let w = 1; w <= weeks; w++) {
        Object.entries(schedule).forEach(([dateKey, { userId }]) => {
          if (!userId) return;
          const date = addDays(new Date(dateKey), w * 7);
          const copyDate = formatISO(date, { representation: 'date' });
          promises.push(api.post('/api/admin/schedule', { userId, date: copyDate, shift: 'DAY' }));
        });
      }
      return Promise.all(promises);
    },
    onSuccess: () => {
      alert('Wochen erfolgreich kopiert!');
      queryClient.invalidateQueries({ queryKey: ['schedule'] });
    },
    onError: () => alert('Fehler beim Kopieren der Wochen.'),
  });

  return (
      <div className="schedule-planner__controls">
        <button onClick={() => changeWeek(-1)}>&laquo;</button>
        <span className={`schedule-planner__week-display${isCurrentWeek ? ' schedule-planner__week-display--current' : ''}`}>{weekLabel} | {rangeLabel}</span>
        <button onClick={() => changeWeek(1)}>&raquo;</button>
        <input type="date" value={format(weekStart, 'yyyy-MM-dd')} onChange={(e) => setWeekStart(new Date(e.target.value))} />
        <div className="copy-weeks">
          <input type="number" min="1" value={copyCount} onChange={(e) => setCopyCount(parseInt(e.target.value, 10) || 1)} />
          <button onClick={() => copyWeeksMutation.mutate(copyCount)} disabled={copyWeeksMutation.isLoading}>
            {copyWeeksMutation.isLoading ? 'Kopiere...' : t('schedulePlanner.copyWeeks', 'Copy')}
          </button>
        </div>
      </div>
  );
};

const UserList = ({ setSchedule }) => {
  const { t } = useTranslation();
  const { data: users = [], isLoading: isLoadingUsers } = useQuery({ queryKey: ['users'], queryFn: fetchUsers });
  const { setDragUser } = usePlannerStore();
  const weekStart = usePlannerStore(state => state.weekStart);

  const autoFill = () => {
    if (users.length === 0) return;
    setSchedule(prevSchedule => {
      const epochMonday = new Date(2020, 0, 6);
      const weekIndex = Math.abs(differenceInCalendarWeeks(weekStart, epochMonday));
      const startIndex = weekIndex % users.length;
      const newSchedule = { ...prevSchedule };
      const assigned = new Set(Object.values(newSchedule).map(e => e.userId).filter(Boolean));
      let userIdx = 0;
      days.forEach((_, i) => {
        const dateKey = formatISO(addDays(weekStart, i), { representation: 'date' });
        if (newSchedule[dateKey] && newSchedule[dateKey].userId) return;
        let user = users[(startIndex + userIdx) % users.length];
        if (assigned.has(user.id)) {
          let attempts = 0;
          while (attempts < users.length) {
            userIdx++;
            user = users[(startIndex + userIdx) % users.length];
            if (!assigned.has(user.id)) break;
            attempts++;
          }
        }
        newSchedule[dateKey] = { ...(newSchedule[dateKey] || {}), userId: user.id };
        assigned.add(user.id);
        userIdx++;
      });
      return newSchedule;
    });
  };

  return (
      <div className="schedule-planner__users">
        {isLoadingUsers ? <p>Lade Benutzer...</p> : users.map(u => (
            <div key={u.id} className="schedule-planner__user-item" draggable onDragStart={() => setDragUser(u)}>
              {u.username}
            </div>
        ))}
        <div className="user-list-buttons">
          <button onClick={autoFill}>{t('schedulePlanner.auto', 'Auto Fill')}</button>
        </div>
      </div>
  );
};

const ScheduleTable = ({ schedule, setSchedule, onEditNote }) => {
  const { data: users = [] } = useQuery({ queryKey: ['users'], queryFn: fetchUsers });
  const { weekStart, dragUser, setDragUser } = usePlannerStore();
  const queryClient = useQueryClient();

  const saveMutation = useMutation({
    mutationFn: saveScheduleEntry,
    onSuccess: (data, variables) => {
      // Optional: Update schedule with the ID from the server response
      setSchedule(prev => ({...prev, [variables.date]: { ...prev[variables.date], id: data.id }}));
      queryClient.invalidateQueries({ queryKey: ['schedule', weekStart] });
    },
    onError: (err, variables) => {
      alert('Fehler beim Speichern!');
      queryClient.invalidateQueries({ queryKey: ['schedule', weekStart] }); // Zurücksetzen auf Server-Stand
    }
  });

  const onDrop = (dateKey) => {
    if (dragUser) {
      const currentEntry = schedule[dateKey] || {};
      if (currentEntry.userId === dragUser.id) { // Nicht speichern, wenn der User derselbe ist
        setDragUser(null);
        return;
      }
      const newEntry = { id: currentEntry.id, userId: dragUser.id, date: dateKey };
      setSchedule(prev => ({ ...prev, [dateKey]: newEntry }));
      saveMutation.mutate(newEntry);
      setDragUser(null);
    }
  };

  const dateKeys = days.map((_, i) => formatISO(addDays(weekStart, i), { representation: 'date' }));
  const conflictMap = Object.values(schedule).reduce((acc, { userId }) => {
    if (userId) acc[userId] = (acc[userId] || 0) + 1;
    return acc;
  }, {});


  return (
      <table className="schedule-planner__table">
        <thead>
        <tr>{days.map(d => <th key={d}>{d}</th>)}</tr>
        </thead>
        <tbody>
        <tr>
          {dateKeys.map(dateKey => {
            const entry = schedule[dateKey] || {};
            const user = users.find(u => u.id === entry.userId);
            const conflict = entry.userId && conflictMap[entry.userId] > 1;
            const today = isSameDay(new Date(dateKey), new Date());
            const note = schedule[dateKey]?.note;
            return (
                <td key={dateKey}
                    className={`schedule-planner__cell--droppable ${conflict ? 'schedule-planner__cell--conflict' : ''} ${today ? 'schedule-planner__cell--today' : ''}`}
                    onDragOver={(e) => e.preventDefault()}
                    onDrop={() => onDrop(dateKey)}
                    onDoubleClick={() => onEditNote(dateKey)}>
                  {user ? user.username : '—'} {note && <span className="note-icon" title={note}>📝</span>}
                </td>
            );
          })}
        </tr>
        </tbody>
      </table>
  );
};

const AdminSchedulePlannerPage = () => {
  const weekStart = usePlannerStore(state => state.weekStart);
  const { data: initialSchedule, isLoading } = useQuery({
    queryKey: ['schedule', weekStart],
    queryFn: () => fetchSchedule(weekStart),
  });

  const [schedule, setSchedule] = React.useState({});
  const [noteModal, setNoteModal] = React.useState({ open: false, dateKey: null, text: '' });
  React.useEffect(() => {
    if (initialSchedule) {
      setSchedule(initialSchedule);
    }
  }, [initialSchedule]);

  const queryClient = useQueryClient();
  const handleEditNote = (dateKey) => {
    setNoteModal({ open: true, dateKey, text: schedule[dateKey]?.note || '' });
  };

  const closeModal = () => setNoteModal({ open: false, dateKey: null, text: '' });

  const saveNote = () => {
    const { dateKey, text } = noteModal;
    const entry = schedule[dateKey] || { date: dateKey };
    const payload = { id: entry.id, userId: entry.userId, date: dateKey, note: text };
    setSchedule(prev => ({ ...prev, [dateKey]: { ...entry, note: text } }));
    saveScheduleEntry(payload).then(() => {
      queryClient.invalidateQueries({ queryKey: ['schedule', weekStart] });
    });
    closeModal();
  };
  const saveAllMutation = useMutation({
    mutationFn: (scheduleToSave) => {
      const promises = Object.entries(scheduleToSave).map(([dateKey, { userId, id, note }]) =>
          saveScheduleEntry({ id, userId, date: dateKey, note })
      );
      return Promise.all(promises);
    },
    onSuccess: () => {
      alert('Plan erfolgreich gespeichert!');
      queryClient.invalidateQueries({ queryKey: ['schedule', weekStart] });
    },
    onError: () => alert('Ein Fehler ist beim Speichern aufgetreten.'),
  });

  return (
      <>
        <Navbar />
        <div className="schedule-planner scoped-dashboard">
          <div className="schedule-planner__main">
            <WeekNavigator />
            {isLoading ? (
                <div className="loading-indicator">Lade Arbeitsplan...</div>
            ) : (
                <ScheduleTable schedule={schedule} setSchedule={setSchedule} onEditNote={handleEditNote} />
            )}
            <div className="save-panel">
              <button onClick={() => saveAllMutation.mutate(schedule)} disabled={saveAllMutation.isLoading}>
                {saveAllMutation.isLoading ? 'Speichert...' : 'Kompletten Plan Speichern'}
              </button>
            </div>
          </div>
          <UserList setSchedule={setSchedule} />
        </div>
        {noteModal.open && (
          <ModalOverlay visible onClose={closeModal} className="scoped-dashboard">
            <div className="modal-content">
              <h3>Notiz</h3>
              <textarea
                value={noteModal.text}
                onChange={e => setNoteModal({ ...noteModal, text: e.target.value })}
                rows="4"
                style={{ width: '100%' }}
              />
              <div className="modal-buttons">
                <button onClick={closeModal} className="button-secondary">Abbrechen</button>
                <button onClick={saveNote} className="button-primary">Speichern</button>
              </div>
            </div>
          </ModalOverlay>
        )}
      </>
  );
};

export default AdminSchedulePlannerPage;

