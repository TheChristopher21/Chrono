import React, { useState } from 'react';
import '../styles/QuickStart.css';

const tasks = [
    { id: 'profile', label: 'Profil ausfüllen' },
    { id: 'punch', label: 'Erste Zeiterfassung' },
    { id: 'vacation', label: 'Urlaub beantragen' }
];

export default function QuickStart() {
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
        <div className="quickstart-widget">
            <h3>Quick Start</h3>
            <ul>
                {tasks.map(t => (
                    <li key={t.id}>
                        <label>
                            <input type="checkbox" checked={!!done[t.id]} onChange={() => toggle(t.id)} />
                            {t.label}
                        </label>
                    </li>
                ))}
            </ul>
            <div className="qs-progress">{progress}% erledigt</div>
        </div>
    );
}
