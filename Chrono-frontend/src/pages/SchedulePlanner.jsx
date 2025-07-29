import React, { useEffect, useState } from 'react';
import Navbar from '../components/Navbar';
import { useNotification } from '../context/NotificationContext';
import { useTranslation } from '../context/LanguageContext';
import api from '../utils/api';
import '../styles/SchedulePlanner.css';

const days = ['monday','tuesday','wednesday','thursday','friday','saturday','sunday'];

const defaultSchedule = {
    monday: 8.0,
    tuesday: 8.0,
    wednesday: 8.0,
    thursday: 8.0,
    friday: 8.0,
    saturday: 0.0,
    sunday: 0.0
};

const SchedulePlanner = () => {
    const { notify } = useNotification();
    const { t } = useTranslation();
    const [scheduleCycle, setScheduleCycle] = useState(1);
    const [weekSchedule, setWeekSchedule] = useState(defaultSchedule);
    const [effectiveDate, setEffectiveDate] = useState('');
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        api.get('/api/user/schedule')
            .then(res => {
                const data = res.data;
                if (data.scheduleCycle) setScheduleCycle(data.scheduleCycle);
                if (Array.isArray(data.weeklySchedule) && data.weeklySchedule.length > 0) {
                    setWeekSchedule({ ...defaultSchedule, ...data.weeklySchedule[0] });
                }
                if (data.scheduleEffectiveDate) setEffectiveDate(data.scheduleEffectiveDate);
            })
            .catch(err => {
                const msg = err.response?.data?.message || err.message;
                notify(t('schedulePlanner.loadError', 'Fehler beim Laden: ') + msg, 'error');
            })
            .finally(() => setLoading(false));
    }, []);

    const handleChange = (day, value) => {
        setWeekSchedule(prev => ({ ...prev, [day]: parseFloat(value) }));
    };

    const saveSchedule = () => {
        const payload = {
            scheduleCycle,
            weeklySchedule: [weekSchedule],
            scheduleEffectiveDate: effectiveDate || null
        };
        api.post('/api/user/schedule', payload)
            .then(() => notify(t('schedulePlanner.saveSuccess', 'Plan gespeichert'), 'success'))
            .catch(err => {
                const msg = err.response?.data?.message || err.message;
                notify(t('schedulePlanner.saveError', 'Fehler beim Speichern: ') + msg, 'error');
            });
    };

    if (loading) return <div>Loading...</div>;

    return (
        <div className="schedule-planner scoped">
            <Navbar />
            <div className="page-content">
                <h2>{t('schedulePlanner.title', 'Arbeitsplan')}</h2>
                <div className="schedule-form">
                    <label>{t('schedulePlanner.cycle', 'Zyklus in Wochen')}</label>
                    <input type="number" min="1" value={scheduleCycle} onChange={e => setScheduleCycle(parseInt(e.target.value, 10) || 1)} />
                    <label>{t('schedulePlanner.effective', 'Gültig ab')}</label>
                    <input type="date" value={effectiveDate} onChange={e => setEffectiveDate(e.target.value)} />
                    <table className="schedule-table">
                        <thead>
                            <tr>{days.map(d => <th key={d}>{d}</th>)}</tr>
                        </thead>
                        <tbody>
                            <tr>
                                {days.map(d => (
                                    <td key={d}>
                                        <input type="number" step="0.25" value={weekSchedule[d]} onChange={e => handleChange(d, e.target.value)} />
                                    </td>
                                ))}
                            </tr>
                        </tbody>
                    </table>
                    <button className="button-primary" onClick={saveSchedule}>{t('save', 'Speichern')}</button>
                </div>
            </div>
        </div>
    );
};

export default SchedulePlanner;
