import React, { useEffect, useState } from 'react';
import Navbar from '../../components/Navbar';
import api from '../../utils/api';
import '../../styles/AdminSchedulePlannerPage.css';
import { useTranslation } from '../../context/LanguageContext';
import { startOfWeek, addDays, formatISO, format, differenceInCalendarWeeks } from 'date-fns';

const days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

const AdminSchedulePlannerPage = () => {
  const { t } = useTranslation();
  const [users, setUsers] = useState([]);
  const [schedule, setSchedule] = useState({});
  const [weekStart, setWeekStart] = useState(startOfWeek(new Date(), { weekStartsOn: 1 }));
  const [dragUser, setDragUser] = useState(null);

  const changeWeek = offset => {
    setWeekStart(prev => addDays(prev, offset * 7));
  };

  const onWeekInput = e => {
    const date = new Date(e.target.value);
    if (!isNaN(date)) {
      setWeekStart(startOfWeek(date, { weekStartsOn: 1 }));
    }
  };

  /* ---------------------------------- Daten laden ---------------------------------- */
  useEffect(() => {
    api.get('/api/admin/users').then(res => {
      setUsers(Array.isArray(res.data) ? res.data : []);
    });
  }, []);

  useEffect(() => {
    const start = formatISO(weekStart, { representation: 'date' });
    const end = formatISO(addDays(weekStart, 6), { representation: 'date' });
    api
        .get('/api/admin/schedule', { params: { start, end } })
        .then(res => {
          const map = {};
          (res.data || []).forEach(e => {
            const dayKey = formatISO(new Date(e.date), { representation: 'date' });
            map[dayKey] = { userId: e.userId, id: e.id };
          });
          setSchedule(map);
        });
  }, [weekStart]);

  /* -------------------------------- Drag & Drop ------------------------------------ */
  const onDragStart = user => setDragUser(user);
  const allowDrop = e => e.preventDefault();
  const onDrop = dateKey => {
    if (dragUser) setSchedule(prev => ({ ...prev, [dateKey]: { ...prev[dateKey], userId: dragUser.id } }));
    setDragUser(null);
  };

  /* ----------------------------- Auto‑Fill & Speichern ----------------------------- */
  const autoFill = () => {
    if (users.length === 0) return;
    const epochMonday = new Date(2020, 0, 6);
    const weekIndex = Math.abs(differenceInCalendarWeeks(weekStart, epochMonday));
    const startIndex = weekIndex % users.length;
    const newSchedule = {};
    days.forEach((_, i) => {
      const dateKey = formatISO(addDays(weekStart, i), { representation: 'date' });
      const user = users[(startIndex + i) % users.length];
      if (user) newSchedule[dateKey] = { userId: user.id };
    });
    setSchedule(newSchedule);
  };

  const save = () => {
    Object.entries(schedule).forEach(([dateKey, { userId, id }]) => {
      api.post('/api/admin/schedule', { id, userId, date: dateKey, shift: 'DAY' });
    });
  };

  /* --------------------------- Hilfs‑Berechnungen ---------------------------------- */
  const dateKeys = days.map((_, i) => formatISO(addDays(weekStart, i), { representation: 'date' }));
  const conflictMap = Object.values(schedule).reduce((acc, { userId }) => {
    if (!userId) return acc;
    acc[userId] = (acc[userId] || 0) + 1;
    return acc;
  }, {});

  /* ----------------------------------- Render -------------------------------------- */
  return (
      <>
        <Navbar />

        <div className="admin-schedule-planner-page scoped-dashboard">
          <div className="week-controls">
            <button onClick={() => changeWeek(-1)}>{t('schedulePlanner.prevWeek', 'Prev')}</button>
            <input type="date" value={format(weekStart, 'yyyy-MM-dd')} onChange={onWeekInput} />
            <button onClick={() => changeWeek(1)}>{t('schedulePlanner.nextWeek', 'Next')}</button>
          </div>
          {/* Users Liste */}
          <div className="users-list">
            {users.map(u => (
                <div
                    key={u.id}
                    className="user-item"
                    draggable
                    onDragStart={() => onDragStart(u)}
                >
                  {u.username}
                </div>
            ))}
            <button onClick={autoFill}>{t('schedulePlanner.auto', 'Auto&nbsp;Fill')}</button>
            <button onClick={save}>{t('schedulePlanner.save', 'Save')}</button>
          </div>

          {/* Wochen‑Tabelle */}
          <table className="schedule-table">
            <thead>
            <tr>{days.map(d => <th key={d}>{d}</th>)}</tr>
            </thead>
            <tbody>
            <tr>
              {dateKeys.map(dateKey => {
                const entry = schedule[dateKey] || {};
                const conflict = entry.userId && conflictMap[entry.userId] > 1;
                const user = users.find(u => u.id === entry.userId);
                return (
                    <td
                        key={dateKey}
                        className={`droppable${conflict ? ' conflict' : ''}`}
                        onDragOver={allowDrop}
                        onDrop={() => onDrop(dateKey)}
                    >
                      {user ? user.username : '—'}
                    </td>
                );
              })}
            </tr>
            </tbody>
          </table>
        </div>
      </>
  );
};

export default AdminSchedulePlannerPage;
