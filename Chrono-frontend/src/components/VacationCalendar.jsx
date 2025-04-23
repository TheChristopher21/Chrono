import React, { useState, useMemo } from 'react';
import PropTypes from 'prop-types';
import Calendar from 'react-calendar';
import 'react-calendar/dist/Calendar.css';
import '../styles/VacationCalendar.css';
import api from '../utils/api';
import { useNotification } from '../context/NotificationContext';
import { useTranslation } from '../context/LanguageContext';

function formatDateISO(d) {
    return d.toISOString().split('T')[0];
}

const VacationCalendar = ({ vacationRequests, userProfile, onRefreshVacations }) => {
    const { notify } = useNotification();
    const { t } = useTranslation();

    /* ---------------------------- State ---------------------------- */
    const [showModal, setShowModal] = useState(false);
    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');
    const [vacationType, setVacationType] = useState('full');
    const [useOvertime, setUseOvertime] = useState(false);

    const approvedVacations = useMemo(
        () => vacationRequests.filter(v => v.approved),
        [vacationRequests]
    );

    function computeUsedVacationDays(vacs) {
        return vacs.reduce((sum, vac) => {
            if (vac.usesOvertime) return sum;
            const start = new Date(vac.startDate);
            const end = new Date(vac.endDate);
            const days = Math.floor((end - start) / 86400000) + 1;
            return sum + (vac.halfDay ? 0.5 : days);
        }, 0);
    }

    const totalVacation = Number(userProfile?.annualVacationDays) || 0;
    const usedDays = computeUsedVacationDays(approvedVacations);
    const remainingDays = totalVacation - usedDays;

    /* ---------------------------- Calendar tile -------------------- */
    const tileContent = ({ date, view }) => {
        if (view !== 'month') return null;
        const hasVac = approvedVacations.some(v =>
            new Date(v.startDate) <= date && date <= new Date(v.endDate)
        );
        if (!hasVac) return null;

        const first = approvedVacations.find(v =>
            new Date(v.startDate) <= date && date <= new Date(v.endDate)
        );
        const color = first.usesOvertime ? '#3498db' : '#2ecc71';
        const tooltip = approvedVacations
            .filter(v => new Date(v.startDate) <= date && date <= new Date(v.endDate))
            .map(v => `${v.halfDay ? '½ Tag' : 'Ganztag'} (${v.usesOvertime ? 'Überstunden' : 'Urlaub'})`)
            .join(', ');
        return (
            <div
                className="holiday-dot"
                title={tooltip}
                style={{ backgroundColor: color }}
            />
        );
    };

    /* ---------------------------- Submit --------------------------- */
    const handleSubmit = async e => {
        e.preventDefault();
        if (!startDate || !endDate) return notify('Bitte Start- und Enddatum angeben.');

        const s = new Date(`${startDate}T00:00`);
        const eDate = new Date(`${endDate}T00:00`);
        if (s > eDate) return notify('Enddatum darf nicht vor Startdatum liegen.');
        if (vacationType === 'half' && startDate !== endDate)
            return notify('Halbtags gilt nur für einen Tag.');

        try {
            await api.post('/api/vacation/create', null, {
                params: {
                    username: userProfile.username,
                    startDate,
                    endDate,
                    halfDay: vacationType === 'half',
                    usesOvertime
                }
            });
            notify('Urlaubsantrag eingereicht.');
            setShowModal(false);
            setStartDate(''); setEndDate(''); setUseOvertime(false);
            onRefreshVacations?.();
        } catch (err) {
            console.error(err);
            notify('Fehler beim Urlaubsantrag.');
        }
    };

    /* ---------------------------- UI ------------------------------- */
    return (
        <div className="scoped-vacation">
            <div className="vacation-calendar">
                <h4>{t('vacationCalendarTitle')}: {totalVacation.toFixed(1)} {t('daysLabel')}</h4>
                <h4>{t('myVacations')}: {usedDays.toFixed(1)} {t('daysLabel')}</h4>
                <h4>{t('remainingVacation')}: {remainingDays.toFixed(1)} {t('daysLabel')}</h4>

                <Calendar locale="de-DE" tileContent={tileContent} />

                <button style={{ marginTop: '1rem' }} onClick={() => setShowModal(true)}>
                    Urlaub beantragen
                </button>
            </div>

            {showModal && (
                <div className="vacation-modal-overlay">
                    <div className="vacation-modal-content">
                        <h3>Urlaubsantrag</h3>
                        <form onSubmit={handleSubmit}>
                            <label>Von:
                                <input type="date" value={startDate}
                                       onChange={e => setStartDate(e.target.value)} required />
                            </label>
                            <label>Bis:
                                <input type="date" value={endDate}
                                       onChange={e => setEndDate(e.target.value)} required />
                            </label>
                            <label>Zeitraum:
                                <select value={vacationType}
                                        onChange={e => setVacationType(e.target.value)}>
                                    <option value="full">Ganztags</option>
                                    <option value="half">Halbtags</option>
                                </select>
                            </label>
                            <label>Urlaubsart:
                                <select value={useOvertime ? 'overtime' : 'normal'}
                                        onChange={e => setUseOvertime(e.target.value === 'overtime')}>
                                    <option value="normal">Normaler Urlaub</option>
                                    <option value="overtime">Überstundenfrei</option>
                                </select>
                            </label>
                            <div className="modal-buttons">
                                <button type="submit">Absenden</button>
                                <button type="button" onClick={() => setShowModal(false)}>Abbrechen</button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

VacationCalendar.propTypes = {
    vacationRequests: PropTypes.array.isRequired,
    userProfile: PropTypes.object.isRequired,
    onRefreshVacations: PropTypes.func
};

export default VacationCalendar;
