// src/components/VacationCalendar.jsx
import { useState, useMemo } from 'react';
import PropTypes from 'prop-types';
import Calendar from 'react-calendar';
import 'react-calendar/dist/Calendar.css';
import '../styles/VacationCalendar.css';
import api from '../utils/api';
import { useNotification } from '../context/NotificationContext';
import { useTranslation } from '../context/LanguageContext';

// Gauss-Algorithmus für Ostersonntag
function calculateEasterSunday(year) {
    const a = year % 19;
    const b = Math.floor(year / 100);
    const c = year % 100;
    const d = Math.floor(b / 4);
    const e = b % 4;
    const f = Math.floor((b + 8) / 25);
    const g = Math.floor((b - f + 1) / 3);
    const h = (19 * a + b - d - g + 15) % 30;
    const i = Math.floor(c / 4);
    const k = c % 4;
    const l = (32 + 2 * e + 2 * i - h - k) % 7;
    const m = Math.floor((a + 11 * h + 22 * l) / 451);
    const month = Math.floor((h + l - 7 * m + 114) / 31);
    const day = ((h + l - 7 * m + 114) % 31) + 1;
    return new Date(year, month - 1, day);
}

function formatDate(d) {
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

function getHolidayLabels(year) {
    const holidays = {};
    holidays[`${year}-01-01`] = "Neujahr";
    const easterSunday = calculateEasterSunday(year);
    const karfreitag = new Date(easterSunday);
    karfreitag.setDate(easterSunday.getDate() - 2);
    holidays[formatDate(karfreitag)] = "Karfreitag";
    const ostermontag = new Date(easterSunday);
    ostermontag.setDate(easterSunday.getDate() + 1);
    holidays[formatDate(ostermontag)] = "Ostermontag";
    holidays[`${year}-05-01`] = "1. Mai";
    const auffahrt = new Date(easterSunday);
    auffahrt.setDate(easterSunday.getDate() + 39);
    holidays[formatDate(auffahrt)] = "Auffahrt";
    const pfingstmontag = new Date(easterSunday);
    pfingstmontag.setDate(easterSunday.getDate() + 50);
    holidays[formatDate(pfingstmontag)] = "Pfingstmontag";
    holidays[`${year}-08-01`] = "Nationalfeiertag";
    holidays[`${year}-10-16`] = "Gallustag";
    holidays[`${year}-12-25`] = "Weihnachten";
    holidays[`${year}-12-26`] = "Stephanstag";
    return holidays;
}

const VacationCalendar = ({ vacationRequests, userProfile, onRefreshVacations }) => {
    const { notify } = useNotification();
    const { t } = useTranslation();

    const [selectedRange, setSelectedRange] = useState(null);
    const [showModal, setShowModal] = useState(false);
    const [vacationType, setVacationType] = useState('full');

    const currentYear = new Date().getFullYear();
    const holidayLabels = useMemo(() => getHolidayLabels(currentYear), [currentYear]);

    const approvedVacations = useMemo(() => {
        return vacationRequests.filter(v => v.approved);
    }, [vacationRequests]);

    function computeUsedVacationDays(vacations) {
        let used = 0;
        vacations.forEach((vac) => {
            const start = new Date(vac.startDate);
            const end = new Date(vac.endDate);
            const diffTime = end.getTime() - start.getTime();
            const dayCount = Math.floor(diffTime / (1000 * 3600 * 24)) + 1;
            if (vac.halfDay) {
                used += 0.5;
            } else {
                used += dayCount;
            }
        });
        return used;
    }
    const totalVacationDays = Number(userProfile?.annualVacationDays) || 0;
    const usedDays = computeUsedVacationDays(approvedVacations);
    const remainingDays = totalVacationDays - usedDays;

    const tileContent = ({ date, view }) => {
        if (view === 'month') {
            const dateStr = formatDate(date);
            if (holidayLabels[dateStr]) {
                return (
                    <div className="holiday-label">
                        {holidayLabels[dateStr]}
                    </div>
                );
            }
            const matched = approvedVacations.filter((vac) => {
                const start = new Date(vac.startDate);
                const end = new Date(vac.endDate);
                start.setHours(0, 0, 0, 0);
                end.setHours(0, 0, 0, 0);
                const current = new Date(date);
                current.setHours(0, 0, 0, 0);
                return current >= start && current <= end;
            });
            if (matched.length > 0) {
                return (
                    <div style={{
                        backgroundColor: '#2ecc71',
                        borderRadius: '50%',
                        width: '10px',
                        height: '10px',
                        margin: '0 auto'
                    }}/>
                );
            }
        }
        return null;
    };

    const handleDateChange = (value) => {
        setSelectedRange(value);
        if (Array.isArray(value) && value.length === 2) {
            setShowModal(true);
        }
    };

    async function handleVacationSubmit(e) {
        e.preventDefault();
        if (!Array.isArray(selectedRange) || selectedRange.length < 2) {
            notify('Bitte einen gültigen Zeitraum auswählen.');
            setShowModal(false);
            return;
        }
        const [rawStart, rawEnd] = selectedRange;
        const startDate = formatDate(rawStart);
        const endDate = formatDate(rawEnd);

        if (vacationType === 'half' && startDate !== endDate) {
            notify('Halbtags-Urlaub gilt nur für einen Tag.');
            return;
        }
        try {
            await api.post('/api/vacation/create', null, {
                params: {
                    username: userProfile.username,
                    startDate,
                    endDate,
                    halfDay: vacationType === 'half'
                }
            });
            notify('Urlaubsantrag eingereicht (wartet auf Freigabe).');
            setShowModal(false);
            if (onRefreshVacations) {
                onRefreshVacations();
            }
        } catch (err) {
            console.error('Fehler beim Urlaubsantrag:', err);
            notify('Fehler beim Urlaubsantrag.');
        }
    }

    return (
        <div className="vacation-calendar">
            <h4>{t("vacationCalendarTitle")}: {totalVacationDays.toFixed(1)} {t("daysLabel")}</h4>
            <h4>{t("myVacations")}: {usedDays.toFixed(1)} {t("daysLabel")}</h4>
            <h4>{t("remainingVacation")}: {remainingDays.toFixed(1)} {t("daysLabel")}</h4>
            <Calendar
                onChange={handleDateChange}
                value={selectedRange}
                tileContent={tileContent}
                selectRange={true}
            />
            {showModal && (
                <div className="vacation-modal-overlay">
                    <div className="vacation-modal-content">
                        <h3>Urlaubsantrag</h3>
                        <p>
                            Gewählter Zeitraum:{' '}
                            <strong>
                                {Array.isArray(selectedRange) && selectedRange.length === 2
                                    ? `${formatDate(selectedRange[0])} bis ${formatDate(selectedRange[1])}`
                                    : '–'}
                            </strong>
                        </p>
                        <form onSubmit={handleVacationSubmit}>
                            <label>
                                {t("vacationType")}:
                                <select
                                    value={vacationType}
                                    onChange={(e) => setVacationType(e.target.value)}
                                >
                                    <option value="full">{t("fullDay")}</option>
                                    <option value="half">{t("halfDay")}</option>
                                </select>
                            </label>
                            <div className="modal-buttons">
                                <button type="submit">{t("vacationSubmitButton")}</button>
                                <button type="button" onClick={() => setShowModal(false)}>{t("cancel")}</button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

VacationCalendar.propTypes = {
    vacationRequests: PropTypes.arrayOf(
        PropTypes.shape({
            startDate: PropTypes.string.isRequired,
            endDate: PropTypes.string.isRequired,
            approved: PropTypes.bool,
            denied: PropTypes.bool,
            color: PropTypes.string,
            halfDay: PropTypes.bool
        })
    ).isRequired,
    userProfile: PropTypes.shape({
        username: PropTypes.string.isRequired,
        annualVacationDays: PropTypes.oneOfType([PropTypes.number, PropTypes.string])
    }).isRequired,
    onRefreshVacations: PropTypes.func
};

export default VacationCalendar;
