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

function VacationCalendar({ vacationRequests, userProfile, onRefreshVacations }) {
    const { notify } = useNotification();
    const { t } = useTranslation();

    const [showModal, setShowModal] = useState(false);
    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');
    const [vacationType, setVacationType] = useState('full');
    const [useOvertime, setUseOvertime] = useState(false);

    const approvedVacations = useMemo(
        () => vacationRequests.filter((v) => v.approved),
        [vacationRequests]
    );

    function computeUsedVacationDays(vacs) {
        return vacs.reduce((sum, vac) => {
            // Überspringe Überstunden-Urlaub
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

    // Markierungen
    const tileContent = ({ date, view }) => {
        if (view !== 'month') return null;

        const dayVac = approvedVacations.filter(
            (v) => new Date(v.startDate) <= date && date <= new Date(v.endDate)
        );
        if (dayVac.length === 0) return null;

        // Wir nehmen Farbe Blau (#3498db) für usesOvertime, Grün (#2ecc71) für normalen Urlaub
        const first = dayVac[0];
        const color = first.usesOvertime ? '#3498db' : '#2ecc71';

        // Falls mehrere Urlaube an einem Tag
        const tooltip = dayVac
            .map((v) =>
                `${v.halfDay ? '½ ' + t('vacation.halfDay', 'Tag') : t('vacation.fullDay', 'Ganztags')} (${
                    v.usesOvertime ? t('vacation.overtimeVacation', 'Überstundenfrei') : t('vacation.normalVacation', 'Normaler Urlaub')
                })`
            )
            .join(', ');

        return (
            <div
                className="holiday-dot"
                title={tooltip}
                style={{ backgroundColor: color }}
            />
        );
    };

    async function handleSubmit(e) {
        e.preventDefault();
        if (!startDate || !endDate) {
            return notify(t('vacation.missingDates') || 'Bitte Start- und Enddatum angeben.');
        }
        const s = new Date(`${startDate}T00:00`);
        const eDate = new Date(`${endDate}T00:00`);
        if (s > eDate) {
            return notify(t('vacation.invalidDates') || 'Enddatum darf nicht vor Startdatum liegen.');
        }
        if (vacationType === 'half' && startDate !== endDate) {
            return notify(t('vacation.halfDayOneDay') || 'Halbtags gilt nur für einen einzigen Tag.');
        }

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
            notify(t('vacation.requestSuccess') || 'Urlaubsantrag eingereicht.');
            setShowModal(false);
            setStartDate('');
            setEndDate('');
            setUseOvertime(false);
            onRefreshVacations?.();
        } catch (err) {
            console.error(err);
            notify(t('vacation.requestError') || 'Fehler beim Urlaubsantrag.');
        }
    }

    return (
        <div className="scoped-vacation">
            <div className="vacation-calendar">
                <h4>
                    {t('vacationCalendarTitle', 'Urlaub gesamt')}: {totalVacation.toFixed(1)}{' '}
                    {t('daysLabel', 'Tage')}
                </h4>
                <h4>
                    {t('myVacations', 'Bereits genommen')}: {usedDays.toFixed(1)}{' '}
                    {t('daysLabel', 'Tage')}
                </h4>
                <h4>
                    {t('remainingVacation', 'Verbleibend')}: {remainingDays.toFixed(1)}{' '}
                    {t('daysLabel', 'Tage')}
                </h4>

                <Calendar locale="de-DE" tileContent={tileContent} />

                <button style={{ marginTop: '1rem' }} onClick={() => setShowModal(true)}>
                    {t('requestVacationButton') || 'Urlaub beantragen'}
                </button>
            </div>

            {showModal && (
                <div className="vacation-modal-overlay">
                    <div className="vacation-modal-content">
                        <h3>{t('vacationModalTitle') || 'Urlaubsantrag'}</h3>
                        <form onSubmit={handleSubmit}>
                            <label>
                                {t('fromDate', 'Von')}:
                                <input
                                    type="date"
                                    value={startDate}
                                    onChange={(e) => setStartDate(e.target.value)}
                                    required
                                />
                            </label>
                            <label>
                                {t('toDate', 'Bis')}:
                                <input
                                    type="date"
                                    value={endDate}
                                    onChange={(e) => setEndDate(e.target.value)}
                                    required
                                />
                            </label>
                            <label>
                                {t('dayScope', 'Zeitraum')}:
                                <select
                                    value={vacationType}
                                    onChange={(e) => setVacationType(e.target.value)}
                                >
                                    <option value="full">{t('fullDay', 'Ganztags')}</option>
                                    <option value="half">{t('halfDay', 'Halbtags')}</option>
                                </select>
                            </label>
                            <label>
                                {t('vacationType', 'Urlaubsart')}:
                                <select
                                    value={useOvertime ? 'overtime' : 'normal'}
                                    onChange={(e) => setUseOvertime(e.target.value === 'overtime')}
                                >
                                    <option value="normal">
                                        {t('normalVacation', 'Normaler Urlaub')}
                                    </option>
                                    <option value="overtime">
                                        {t('overtimeVacation', 'Überstundenfrei')}
                                    </option>
                                </select>
                            </label>
                            <div className="modal-buttons">
                                <button type="submit">{t('submitButton') || 'Absenden'}</button>
                                <button type="button" onClick={() => setShowModal(false)}>
                                    {t('cancelButton') || 'Abbrechen'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}

VacationCalendar.propTypes = {
    vacationRequests: PropTypes.array.isRequired,
    userProfile: PropTypes.object.isRequired,
    onRefreshVacations: PropTypes.func
};

export default VacationCalendar;
