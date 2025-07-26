import React, { useState } from 'react';
import '../styles/QuickStart.css';
import { useTranslation } from '../context/LanguageContext';

const tasks = [
    { id: 'profile', labelKey: 'quickStart.profile' },
    { id: 'punch', labelKey: 'quickStart.punch' },
    { id: 'vacation', labelKey: 'quickStart.vacation' }
];

export default function QuickStart() {
    const { t } = useTranslation();
    const [done, setDone] = useState(() => {
        try { return JSON.parse(localStorage.getItem('qsDone') || '{}'); } catch { return {}; }
    });

    const toggle = (id) => {
        const newDone = { ...done, [id]: !done[id] };
        setDone(newDone);
        localStorage.setItem('qsDone', JSON.stringify(newDone));
    };

    const completed = tasks.filter(t => done[t.id]).length;
    const progress = Math.round(completed / tasks.length * 100);

    return (
        <div className="scoped-quickstart">
            <div className="quickstart-widget">
            <h3>{t('quickStart.title')}</h3>
            <ul>
                {tasks.map(tk => (
                    <li key={tk.id}>
                        <label>
                            <input type="checkbox" checked={!!done[tk.id]} onChange={() => toggle(tk.id)} />
                            {t(tk.labelKey)}
                        </label>
                    </li>
                ))}
            </ul>
            <div className="qs-progress">{progress}% {t('quickStart.progress')}</div>
            </div>
        </div>
    );
}
