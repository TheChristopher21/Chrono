import React, { useEffect, useState } from 'react';
import Navbar from '../../components/Navbar';
import api from '../../utils/api';
import '../../styles/AdminSchedulePlannerPage.css';
import { useTranslation } from '../../context/LanguageContext';
import { startOfWeek, addDays, formatISO } from 'date-fns';

const days = ['Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday'];

const AdminSchedulePlannerPage = () => {
  const { t } = useTranslation();
  const [users, setUsers] = useState([]);
  const [schedule, setSchedule] = useState({});
  const [weekStart, setWeekStart] = useState(startOfWeek(new Date(), { weekStartsOn: 1 }));
  const [dragUser, setDragUser] = useState(null);

  useEffect(() => {
    api.get('/api/admin/users').then(res => {
      setUsers(Array.isArray(res.data) ? res.data : []);
    });
  }, []);

  useEffect(() => {
    const start = formatISO(weekStart, { representation: 'date' });
    const end = formatISO(addDays(weekStart, 6), { representation: 'date' });
    api.get('/api/admin/schedule', { params: { start, end } })
      .then(res => {
        const map = {};
        (res.data || []).forEach(e => {
          const dayKey = formatISO(new Date(e.date), { representation: 'date' });
          map[dayKey] = { userId: e.userId, id: e.id };
        });
        setSchedule(map);
      });
  }, [weekStart]);

  const assign = (dateKey, userId) => {
    setSchedule(prev => ({ ...prev, [dateKey]: { ...prev[dateKey], userId } }));
  };

  const onDragStart = (u) => setDragUser(u);
  const onDrop = (dateKey) => {
    if (dragUser) assign(dateKey, dragUser.id);
    setDragUser(null);
  };
  const allowDrop = e => e.preventDefault();

  const autoFill = () => {
    const newSchedule = {};
    days.forEach((_, i) => {
      const dateKey = formatISO(addDays(weekStart, i), { representation: 'date' });
      const user = users[i % users.length];
      if (user) newSchedule[dateKey] = { userId: user.id };
    });
    setSchedule(newSchedule);
  };

  const save = () => {
    Object.entries(schedule).forEach(([dateKey, { userId, id }]) => {
      api.post('/api/admin/schedule', { id, userId, date: dateKey, shift: 'DAY' });
    });
  };

  const weekDates = days.map((_, i) => addDays(weekStart, i));
  const dateKeys = weekDates.map(d => formatISO(d, { representation: 'date' }));

  const conflictMap = Object.values(schedule).reduce((acc, entry) => {
    if (!entry?.userId) return acc;
    acc[entry.userId] = (acc[entry.userId] || 0) + 1;
    return acc;
  }, {});

  return (
    <div className="admin-schedule-planner-page scoped-dashboard">
      <Navbar />
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
        <button onClick={autoFill}>{t('schedulePlanner.auto', 'Auto Fill')}</button>
        <button onClick={save}>{t('schedulePlanner.save', 'Save')}</button>
      </div>
      <table className="schedule-table">
        <thead>
          <tr>
            {days.map((d, idx) => <th key={d}>{d}</th>)}
          </tr>
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
                  className={`droppable ${conflict ? 'conflict' : ''}`}
                  onDragOver={allowDrop}
                  onDrop={() => onDrop(dateKey)}
                >
                  {user ? user.username : '-'}
                </td>
              );
            })}
          </tr>
        </tbody>
      </table>
    </div>
  );
};

export default AdminSchedulePlannerPage;
