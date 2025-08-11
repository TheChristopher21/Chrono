import React from 'react';
import { useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { addDays, format, formatISO, startOfWeek } from 'date-fns';
import api from '../../utils/api';
import { useTranslation } from '../../context/LanguageContext';
import '../../styles/AdminSchedulePlannerPageScooped.css';
import '../../styles/PrintSchedule.css';

const days = ['Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag'];

function useQueryParams() {
  return new URLSearchParams(useLocation().search);
}

const fetchUsers = async () => {
  const { data } = await api.get('/api/admin/users');
  return Array.isArray(data) ? data : [];
};

const fetchScheduleRules = async () => {
  const { data } = await api.get('/api/admin/shift-definitions');
  return Array.isArray(data) ? data : [];
};

const fetchScheduleRange = async (startDate, endDate) => {
  const start = formatISO(startDate, { representation: 'date' });
  const end = formatISO(endDate, { representation: 'date' });
  const { data } = await api.get('/api/admin/schedule', { params: { start, end } });
  const map = {};
  (Array.isArray(data) ? data : []).forEach(e => {
    const dayKey = formatISO(new Date(e.date), { representation: 'date' });
    if (!map[dayKey]) {
      map[dayKey] = [];
    }
    if (e.userId) {
      map[dayKey].push({ userId: e.userId, id: e.id, shift: e.shift });
    }
  });
  return map;
};

export default function PrintSchedule() {
  const { t } = useTranslation();
  const query = useQueryParams();
  const startParam = query.get('start');
  const weeks = parseInt(query.get('weeks') || '1', 10);
  const startDate = startOfWeek(new Date(startParam), { weekStartsOn: 1 });
  const endDate = addDays(startDate, weeks * 7 - 1);

  const { data: users = [] } = useQuery({ queryKey: ['users'], queryFn: fetchUsers });
  const { data: shifts = [] } = useQuery({ queryKey: ['scheduleRules'], queryFn: fetchScheduleRules });
  const { data: scheduleMap = {} } = useQuery({ queryKey: ['printSchedule', startParam, weeks], queryFn: () => fetchScheduleRange(startDate, endDate) });

  const weekStarts = Array.from({ length: weeks }, (_, i) => addDays(startDate, i * 7));

  return (
    <div className="schedule-planner-page schedule-print-page scoped-dashboard">
      <div className="btnRow">
        <button onClick={() => window.print()}>{t('printReport.printButton', 'Drucken')}</button>
      </div>
      {weekStarts.map(weekStart => {
        const weekLabel = `${t('schedulePlanner.weekShort', 'KW')} ${format(weekStart, 'w')} / ${format(weekStart, 'yyyy')}`;
        const dateKeys = days.map((_, i) => formatISO(addDays(weekStart, i), { representation: 'date' }));
        return (
          <div key={weekStart.toISOString()} className="print-week">
            <h2 className="cmp-title">{weekLabel}</h2>
            <table className="schedule-table">
              <thead>
                <tr>{days.map(d => <th key={d}>{d}</th>)}</tr>
              </thead>
              <tbody>
                <tr>
                  {dateKeys.map(dateKey => (
                    <td key={dateKey} className="day-cell">
                      <div className="day-header">
                        <span className="day-date">{format(new Date(dateKey), 'd.')}</span>
                      </div>
                      <div className="day-content-shifts">
                        {shifts.filter(s => s.isActive).map(({ shiftKey, label, startTime, endTime }) => {
                          const entries = (scheduleMap[dateKey] || []).filter(e => e.shift === shiftKey);
                          return (
                            <div key={shiftKey} className="shift-slot">
                              <div className="shift-label">
                                <span>{label}</span>
                                <span className="shift-time">{startTime} - {endTime}</span>
                              </div>
                              <div className="shift-content">
                                {entries.length > 0 ? (
                                  entries.map(entry => {
                                    const user = users.find(u => u.id === entry.userId);
                                    if (!user) return null;
                                    return (
                                      <div key={entry.id} className="assigned-user" style={{ backgroundColor: user.color || 'var(--ud-c-primary)' }}>
                                        <span>{user.firstName} {user.lastName}</span>
                                      </div>
                                    );
                                  })
                                ) : (
                                  <div className="empty-shift-slot">-</div>
                                )}
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    </td>
                  ))}
                </tr>
              </tbody>
            </table>
          </div>
        );
      })}
    </div>
  );
}

