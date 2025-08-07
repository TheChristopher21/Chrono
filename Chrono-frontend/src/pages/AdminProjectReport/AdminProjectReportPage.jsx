import React, { useState } from 'react';
import Navbar from '../../components/Navbar'; // Navbar importieren
import { useProjects } from '../../context/ProjectContext';
import { useTranslation } from '../../context/LanguageContext';
import api from '../../utils/api';
import '../../styles/AdminProjectReportPageScoped.css'; // Das neue CSS importieren

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
        setReport(null); // Alten Report zurücksetzen
        try {
            const res = await api.get(`/api/report/project/${projectId}`, {
                params: { startDate, endDate }
            });
            setReport(res.data);
        } catch (err) {
            console.error('Error fetching project report', err);
            // Hier könnte eine Benachrichtigung für den Benutzer stehen
        } finally {
            setLoading(false);
        }
    };

    const minsToHours = (mins) => (mins == null ? '-' : (mins / 60).toFixed(1));

    const overallPct = report && report.budgetMinutes > 0 ? (report.totalMinutes / report.budgetMinutes) * 100 : 0;
    const isOverBudget = overallPct > 100;

    return (
        <>
            <Navbar />
            <div className="admin-project-report-page scoped-dashboard">
                <header className="dashboard-header">
                    <h1>{t('projectReport', 'Projekt-Auswertung')}</h1>
                </header>

                <section className="content-section">
                    <div className="report-filters">
                        <select value={projectId} onChange={e => setProjectId(e.target.value)}>
                            <option value="">{t('selectProject', 'Projekt wählen')}</option>
                            {projects.map(p => (
                                <option key={p.id} value={p.id}>{p.name}</option>
                            ))}
                        </select>
                        <input type="date" value={startDate} onChange={e => setStartDate(e.target.value)} />
                        <input type="date" value={endDate} onChange={e => setEndDate(e.target.value)} />
                        <button onClick={loadReport} disabled={loading} className="button-primary">
                            {loading ? t('loading', 'Lädt...') : t('generateReport', 'Bericht erstellen')}
                        </button>
                    </div>

                    {report && (
                        <div className="report-content">
                            <h2>{report.projectName}</h2>

                            {/* Zusammenfassungs-Karten */}
                            <div className="report-summary-cards">
                                <div className="summary-card">
                                    <span className="label">{t('budget', 'Budget')}</span>
                                    <span className="value">{minsToHours(report.budgetMinutes)} h</span>
                                </div>
                                <div className="summary-card">
                                    <span className="label">{t('actual', 'Ist')}</span>
                                    <span className="value">{minsToHours(report.totalMinutes)} h</span>
                                </div>
                                <div className="summary-card">
                                    <span className="label">{t('remaining', 'Verbleibend')}</span>
                                    <span className={`value ${report.remainingMinutes < 0 ? 'remaining-negative' : ''}`}>
                                {minsToHours(report.remainingMinutes)} h
                            </span>
                                </div>
                            </div>

                            {/* Fortschrittsbalken */}
                            {report.budgetMinutes != null && report.budgetMinutes > 0 && (
                                <div>
                                    <div className="progress-bar-container">
                                        <div
                                            className={`progress-bar ${isOverBudget ? 'over-budget' : ''}`}
                                            style={{ width: `${Math.min(100, overallPct)}%` }}
                                        >
                                            {overallPct.toFixed(1)}%
                                        </div>
                                    </div>
                                </div>
                            )}

                            {/* Detail-Tabelle */}
                            <div className="report-table-container">
                                <table className="report-table">
                                    <thead>
                                    <tr>
                                        <th>{t('task', 'Aufgabe')}</th>
                                        <th>{t('budget', 'Budget (h)')}</th>
                                        <th>{t('actual', 'Ist (h)')}</th>
                                        <th>{t('remaining', 'Verbleibend (h)')}</th>
                                        <th>{t('progress', 'Fortschritt')}</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    {report.tasks && report.tasks.map(task => {
                                        const pct = task.budgetMinutes && task.budgetMinutes > 0 ? (task.totalMinutes / task.budgetMinutes) * 100 : null;
                                        const remaining = task.budgetMinutes != null ? task.budgetMinutes - task.totalMinutes : null;
                                        return (
                                            <tr key={task.taskId || task.taskName}>
                                                <td>{task.taskName}</td>
                                                <td>{minsToHours(task.budgetMinutes)}</td>
                                                <td>{minsToHours(task.totalMinutes)}</td>
                                                <td className={remaining < 0 ? 'remaining-negative' : ''}>{minsToHours(remaining)}</td>
                                                <td>
                                                    {pct != null ? (
                                                        <div className="progress-bar-container" style={{height: '18px', marginBottom: '0'}}>
                                                            <div
                                                                className={`progress-bar ${pct > 100 ? 'over-budget' : ''}`}
                                                                style={{ width: `${Math.min(100, pct)}%`, lineHeight: '18px' }}
                                                            >
                                                                {pct.toFixed(0)}%
                                                            </div>
                                                        </div>
                                                    ) : '-'}
                                                </td>
                                            </tr>
                                        );
                                    })}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    )}
                </section>
            </div>
        </>
    );
};

export default AdminProjectReportPage;
