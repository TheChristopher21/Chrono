import React, { useState } from 'react';
import { useProjects } from '../../context/ProjectContext';
import { useTranslation } from '../../context/LanguageContext';
import api from '../../utils/api';

const AdminProjectReportPage = () => {
  const { projects } = useProjects();
  const { t } = useTranslation();
  const [projectId, setProjectId] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(false);

  const loadReport = async () => {
    if (!projectId || !startDate || !endDate) return;
    setLoading(true);
    try {
      const res = await api.get(`/api/report/project/${projectId}`, {
        params: { startDate, endDate }
      });
      setReport(res.data);
    } catch (err) {
      console.error('Error fetching project report', err);
    } finally {
      setLoading(false);
    }
  };

  const minsToHours = (mins) => mins == null ? '-' : (mins / 60).toFixed(1);
  const overallPct = report && report.budgetMinutes ? (report.totalMinutes / report.budgetMinutes) * 100 : 0;

  return (
    <div className="admin-project-report">
      <h2>{t('projectReport', 'Projekt-Auswertung')}</h2>
      <div style={{ marginBottom: '1rem' }}>
        <select value={projectId} onChange={e => setProjectId(e.target.value)}>
          <option value="">{t('selectProject', 'Projekt wählen')}</option>
          {projects.map(p => (
            <option key={p.id} value={p.id}>{p.name}</option>
          ))}
        </select>
        <input type="date" value={startDate} onChange={e => setStartDate(e.target.value)} />
        <input type="date" value={endDate} onChange={e => setEndDate(e.target.value)} />
        <button onClick={loadReport} disabled={loading}>{t('generateReport', 'Bericht erstellen')}</button>
      </div>

      {report && (
        <div>
          {report.budgetMinutes != null && (
            <div style={{ border: '1px solid #ccc', height: '20px', marginBottom: '0.5rem' }}>
              <div style={{ width: `${Math.min(100, overallPct)}%`, height: '100%', backgroundColor: '#4caf50' }} />
            </div>
          )}
          <p>
            {t('budget', 'Budget')}: {minsToHours(report.budgetMinutes)} Std. |
            {t('actual', 'Ist')}: {minsToHours(report.totalMinutes)} Std. |
            {t('remaining', 'Verbleibend')}: {minsToHours(report.remainingMinutes)} Std.
          </p>
          <table>
            <thead>
              <tr>
                <th>{t('task', 'Aufgabe')}</th>
                <th>{t('budget', 'Budget')}</th>
                <th>{t('actual', 'Ist')}</th>
                <th>{t('progress', 'Fortschritt')}</th>
              </tr>
            </thead>
            <tbody>
              {report.tasks && report.tasks.map(task => {
                const pct = task.budgetMinutes ? (task.totalMinutes / task.budgetMinutes) * 100 : null;
                return (
                  <tr key={task.taskId || task.taskName}>
                    <td>{task.taskName}</td>
                    <td>{minsToHours(task.budgetMinutes)}</td>
                    <td>{minsToHours(task.totalMinutes)}</td>
                    <td>{pct != null ? `${pct.toFixed(1)}%` : '-'}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default AdminProjectReportPage;
