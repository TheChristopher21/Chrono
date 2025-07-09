// src/components/VacationCalendar.jsx
import React, { useState, useMemo, useCallback, useEffect } from 'react';
import ModalOverlay from './ModalOverlay';
import PropTypes from 'prop-types';
import Calendar from 'react-calendar';

import api from '../utils/api';
import { useNotification } from '../context/NotificationContext';
import { useTranslation } from '../context/LanguageContext';

// Annahme: formatLocalDateYMD ist in einer Utility-Datei und wird hier importiert
// import { formatLocalDateYMD } from '../utils/dateUtils'; // Beispielhafter Pfad

// Falls formatLocalDateYMD nicht extern ist, hier definieren:
function formatLocalDateYMD(d) {
    if (!(d instanceof Date) || isNaN(d.getTime())) {
        return "";
    }
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}


function VacationCalendar({ vacationRequests, userProfile, onRefreshVacations }) {
    const { notify } = useNotification();
    const { t } = useTranslation();

    const [showVacationModal, setShowVacationModal] = useState(false);
    const [vacationStartDate, setVacationStartDate] = useState('');
    const [vacationEndDate, setVacationEndDate] = useState('');
    const [vacationType, setVacationType] = useState('full');
    const [useOvertime, setUseOvertime] = useState(false);
    const [overtimeDeductionHours, setOvertimeDeductionHours] = useState('');

    const [sickLeaves, setSickLeaves] = useState([]);
    const [showSickLeaveModal, setShowSickLeaveModal] = useState(false);
    const [sickStartDate, setSickStartDate] = useState('');
    const [sickEndDate, setSickEndDate] = useState('');
    const [isSickHalfDay, setIsSickHalfDay] = useState(false);
    const [sickComment, setSickComment] = useState('');

    const [holidays, setHolidays] = useState({});
    const [activeStartDate, setActiveStartDate] = useState(new Date());

    const approvedVacations = useMemo(
        () => vacationRequests.filter((v) => v.approved),
        [vacationRequests]
    );

    const resetVacationForm = useCallback(() => {
        setVacationStartDate('');
        setVacationEndDate('');
        setVacationType('full');
        setUseOvertime(false);
        setOvertimeDeductionHours('');
    }, []);

    const resetSickLeaveForm = useCallback(() => {
        setSickStartDate('');
        setSickEndDate('');
        setIsSickHalfDay(false);
        setSickComment('');
    }, []);

    const fetchHolidays = useCallback(async (year, canton) => {
        try {
            const yearStartDate = `${year}-01-01`;
            const yearEndDate = `${year}-12-31`;
            const params = {
                year: year,
                cantonAbbreviation: canton || '',
                startDate: yearStartDate,
                endDate: yearEndDate,
            };
            const response = await api.get('/api/holidays/details', { params });
            setHolidays(prevHolidays => ({ ...prevHolidays, ...response.data }));
        } catch (error) {
            console.error(t('errors.fetchHolidaysError', 'Fehler beim Laden der Feiertage:'), error);
        }
    }, [t]);

    const fetchSickLeaves = useCallback(async () => {
        if (userProfile?.username) {
            try {
                const response = await api.get(`/api/sick-leave/my`);
                setSickLeaves(Array.isArray(response.data) ? response.data : []);
            } catch (error) {
                console.error(t('errors.fetchSickLeaveError', 'Fehler beim Laden der Krankmeldungen:'), error);
                notify(t('errors.fetchSickLeaveError', 'Fehler beim Laden der Krankmeldungen.'), 'error');
            }
        }
    }, [userProfile, notify, t]);

    useEffect(() => {
        const year = activeStartDate.getFullYear();
        const canton = userProfile?.company?.cantonAbbreviation;
        const firstDayOfYearKey = `${year}-01-01`; // Oder ein anderer zuverlässiger Schlüssel für das Jahr
        let holidaysLoadedForYear = false;
        // Überprüfen, ob *irgendein* Feiertag für dieses Jahr geladen wurde, um unnötige Abfragen zu vermeiden.
        // Dies ist eine einfache Überprüfung. Eine genauere Prüfung könnte spezifische Schlüssel oder eine separate State-Variable verwenden.
        for (const dateKey in holidays) {
            if (dateKey.startsWith(String(year))) {
                holidaysLoadedForYear = true;
                break;
            }
        }

        if (!holidaysLoadedForYear) {
            fetchHolidays(year, canton);
        }
        fetchSickLeaves();
    }, [activeStartDate, userProfile, fetchHolidays, fetchSickLeaves, holidays]);


    function computeUsedVacationDays(vacs) {
        if (!vacs) return 0;
        return vacs.reduce((sum, vac) => {
            if (vac.usesOvertime) return sum;

            const startDt = new Date(vac.startDate + "T00:00:00");
            const endDt = new Date(vac.endDate + "T00:00:00");

            if (isNaN(startDt.getTime()) || isNaN(endDt.getTime())) return sum;

            let daysInRequest = 0;
            for (let d = new Date(startDt); d <= endDt; d.setDate(d.getDate() + 1)) {
                const dateStr = formatLocalDateYMD(d); // Korrekte Formatierung für den Holiday-Check
                const dayOfWeek = d.getDay();
                const isWeekend = dayOfWeek === 0 || dayOfWeek === 6;
                const isHoliday = holidays[dateStr];

                if (!isWeekend && !isHoliday) {
                    daysInRequest += vac.halfDay ? 0.5 : 1.0;
                }
            }
            return sum + daysInRequest;
        }, 0);
    }

    const totalVacation = Number(userProfile?.annualVacationDays) || 0;
    const usedDays = computeUsedVacationDays(approvedVacations.filter(v => !v.usesOvertime));
    const remainingDays = totalVacation - usedDays;

    const tileContent = ({ date, view }) => {
        if (view !== 'month') return null;

        // KORREKTUR: formatLocalDateYMD verwenden, um den lokalen Tag als YYYY-MM-DD String zu bekommen
        const dateString = formatLocalDateYMD(date);

        let content = [];

        if (holidays[dateString]) { // Abgleich mit lokalen Datums-Strings
            content.push(
                <div
                    key={`holiday-${dateString}`}
                    className="holiday-marker"
                    title={`${t('holiday', 'Feiertag')}: ${holidays[dateString]}`}
                >
                    🎉
                </div>
            );
        }

        const vacsToday = approvedVacations.filter((v) => {
            // v.startDate und v.endDate sind bereits YYYY-MM-DD Strings vom Backend
            return dateString >= v.startDate && dateString <= v.endDate;
        });

        if (vacsToday.length > 0) {
            const displayVac = vacsToday.find(v => v.usesOvertime) || vacsToday[0];
            const color = displayVac.usesOvertime ? '#3498db' : (userProfile?.color || '#2ecc71');

            const tooltip = vacsToday
                .map((v) =>
                    `${v.halfDay ? '½ ' + t('vacation.halfDay', 'Tag') : t('vacation.fullDay', 'Ganztags')} (${
                        v.usesOvertime ? t('vacation.overtimeVacation', 'Überstundenfrei') : t('vacation.normalVacation', 'Normaler Urlaub')
                    })`
                )
                .join('; ');
            content.push(
                <div key={`vacation-${dateString}`} className="holiday-dot" title={tooltip} style={{ backgroundColor: color }} />
            );
        }

        const sickToday = sickLeaves.find(sl => dateString >= sl.startDate && dateString <= sl.endDate);
        if (sickToday) {
            content.push(
                <div
                    key={`sick-${dateString}`}
                    className="sick-leave-dot"
                    title={sickToday.halfDay ? t('sickLeave.halfDay', 'Halbtags krank') : t('sickLeave.fullDay', 'Ganztags krank')}
                    style={{ backgroundColor: sickToday.color || '#FF6347' }}
                />
            );
        }

        return content.length > 0 ? <div className="day-markers-container">{content}</div> : null;
    };

    const onActiveStartDateChange = ({ activeStartDate: newActiveStartDate }) => {
        setActiveStartDate(newActiveStartDate);
    };

    async function handleVacationSubmit(e) {
        e.preventDefault();
        if (!userProfile || !userProfile.username) {
            notify(t('errors.userNotLoaded', 'Benutzerprofil nicht geladen.'), 'error');
            return;
        }
        if (!vacationStartDate || !vacationEndDate) {
            return notify(t('vacation.missingDates', 'Bitte Start- und Enddatum angeben.'), 'warning');
        }
        if (new Date(vacationEndDate) < new Date(vacationStartDate)) { // Direkter Vergleich von Datumsstrings ist problematisch
            return notify(t('vacation.invalidDates', 'Enddatum darf nicht vor dem Startdatum liegen.'), 'error');
        }
        if (vacationType === 'half' && vacationStartDate !== vacationEndDate) {
            return notify(t('vacation.halfDayOneDay', 'Halbtags Urlaub kann nur für einen einzelnen Tag beantragt werden.'), 'error');
        }

        let overtimeDeductionMinutesParam = null;
        if (useOvertime && userProfile.isPercentage) {
            if (!overtimeDeductionHours) {
                notify(t('vacation.missingOvertimeHours', 'Bitte die Anzahl der Überstunden für den Abzug angeben.'), 'warning');
                return;
            }
            const hours = parseFloat(overtimeDeductionHours);
            if (isNaN(hours) || hours <= 0) {
                notify(t('vacation.invalidOvertimeHoursValue', 'Ungültige Stundenzahl für Überstundenabzug.'), 'error');
                return;
            }
            overtimeDeductionMinutesParam = Math.round(hours * 60);
        }

        try {
            const params = {
                username: userProfile.username,
                startDate: vacationStartDate,
                endDate: vacationEndDate,
                halfDay: vacationType === 'half',
                usesOvertime: useOvertime
            };
            if (overtimeDeductionMinutesParam !== null) {
                params.overtimeDeductionMinutes = overtimeDeductionMinutesParam;
            }
            await api.post('/api/vacation/create', null, { params });
            notify(t('vacation.requestSuccess', 'Urlaubsantrag erfolgreich eingereicht.'), 'success');
            setShowVacationModal(false);
            resetVacationForm();
            if (onRefreshVacations) onRefreshVacations();
        } catch (err) {
            console.error("Fehler beim Erstellen des Urlaubsantrags:", err);
            const errorMsg = err.response?.data?.message || err.message || t('errors.unknownError');
            notify(t('vacation.requestError', 'Fehler beim Urlaubsantrag:') + ` ${errorMsg}`, 'error');
        }
    }

    async function handleSickLeaveSubmit(e) {
        e.preventDefault();
        if (!userProfile?.username) {
            notify(t('errors.userNotLoaded', 'Benutzerprofil nicht geladen.'), 'error');
            return;
        }
        if (!sickStartDate || !sickEndDate) {
            notify(t('sickLeave.missingDates', 'Bitte Start- und Enddatum angeben.'), 'warning');
            return;
        }
        if (new Date(sickEndDate) < new Date(sickStartDate)) {
            notify(t('sickLeave.invalidDates', 'Enddatum darf nicht vor dem Startdatum liegen.'), 'error');
            return;
        }
        if (isSickHalfDay && sickStartDate !== sickEndDate) {
            notify(t('sickLeave.halfDayOneDay', 'Halbtägige Krankmeldung nur für einen einzelnen Tag.'), 'error');
            return;
        }

        try {
            const params = {
                targetUsername: userProfile.username,
                startDate: sickStartDate,
                endDate: sickEndDate,
                halfDay: isSickHalfDay,
                comment: sickComment,
            };
            await api.post('/api/sick-leave/report', null, { params });
            notify(t('sickLeave.reportSuccess', 'Krankmeldung erfolgreich übermittelt.'), 'success');
            setShowSickLeaveModal(false);
            resetSickLeaveForm();
            fetchSickLeaves();
            if (onRefreshVacations) onRefreshVacations();
        } catch (err) {
            console.error("Fehler bei Krankmeldung:", err);
            const errorMsg = err.response?.data?.message || err.message || t('errors.unknownError');
            notify(t('sickLeave.reportError', 'Fehler bei Krankmeldung:') + ` ${errorMsg}`, 'error');
        }
    }

    const handleOpenVacationModal = () => {
        resetVacationForm();
        setShowVacationModal(true);
    };

    const handleOpenSickLeaveModal = () => {
        resetSickLeaveForm();
        setShowSickLeaveModal(true);
    };

    return (
        <div className="scoped-vacation">
            {/* ... (Rest des JSX bleibt gleich, nur tileContent wurde oben korrigiert) ... */}
            <div className="vacation-calendar-wrapper">
                <div className="vacation-info-header">
                    <h4><strong>{t('vacationCalendarTitle', 'Urlaub gesamt')}:</strong> {totalVacation.toFixed(1)} {t('daysLabel', 'Tage')}</h4>
                    <h4><strong>{t('myVacations', 'Bereits genommen')}:</strong> {usedDays.toFixed(1)} {t('daysLabel', 'Tage')}</h4>
                    <h4><strong>{t('remainingVacation', 'Verbleibend')}:</strong> {remainingDays.toFixed(1)} {t('daysLabel', 'Tage')}</h4>
                </div>

                <Calendar
                    locale={t('calendarLocale', 'de-DE')}
                    tileContent={tileContent}
                    onActiveStartDateChange={onActiveStartDateChange}
                    activeStartDate={activeStartDate}
                />
                <div className="calendar-action-buttons">
                    <button onClick={handleOpenVacationModal} className="button-primary request-vacation-button">
                        {t('requestVacationButton', 'Urlaub beantragen')}
                    </button>
                    <button onClick={handleOpenSickLeaveModal} className="button-secondary report-sick-leave-button">
                        {t('sickLeave.reportButtonShort', 'Krank melden')}
                    </button>
                </div>
            </div>

            {showVacationModal && (
                <ModalOverlay visible className="vacation-modal-overlay">
                    <div className="vacation-modal-content">
                        <h3>{t('vacationModalTitle', 'Urlaubsantrag stellen')}</h3>
                        <form onSubmit={handleVacationSubmit}>
                            <div className="form-group">
                                <label htmlFor="vacStartDate">{t('fromDate', 'Von')}:</label>
                                <input id="vacStartDate" type="date" value={vacationStartDate} onChange={(e) => setVacationStartDate(e.target.value)} required />
                            </div>
                            <div className="form-group">
                                <label htmlFor="vacEndDate">{t('toDate', 'Bis')}:</label>
                                <input id="vacEndDate" type="date" value={vacationEndDate} onChange={(e) => setVacationEndDate(e.target.value)} required />
                            </div>
                            <div className="form-group">
                                <label htmlFor="vacDayScope">{t('dayScope', 'Zeitraum')}:</label>
                                <select id="vacDayScope" value={vacationType} onChange={(e) => setVacationType(e.target.value)}>
                                    <option value="full">{t('fullDay', 'Ganztags')}</option>
                                    <option value="half">{t('halfDay', 'Halbtags')}</option>
                                </select>
                            </div>
                            <div className="form-group">
                                <label htmlFor="vacTypeSelect">{t('vacationType', 'Urlaubsart')}:</label>
                                <select id="vacTypeSelect" value={useOvertime ? 'overtime' : 'normal'}
                                        onChange={(e) => {
                                            const isOvertime = e.target.value === 'overtime';
                                            setUseOvertime(isOvertime);
                                            if (!isOvertime || !userProfile?.isPercentage) {
                                                setOvertimeDeductionHours('');
                                            }
                                        }}>
                                    <option value="normal">{t('normalVacation', 'Normaler Urlaub')}</option>
                                    {!userProfile?.isHourly && (<option value="overtime">{t('overtimeVacation', 'Überstundenfrei')}</option>)}
                                </select>
                            </div>
                            {userProfile?.isPercentage && useOvertime && (
                                <div className="form-group">
                                    <label htmlFor="overtimeHoursInput">{t('vacation.deductOvertimeHoursLabel', 'Abzuziehende Überstunden (Stunden):')}</label>
                                    <input type="number" id="overtimeHoursInput" value={overtimeDeductionHours} onChange={(e) => setOvertimeDeductionHours(e.target.value)} placeholder={t('vacation.hoursPlaceholder', 'z.B. 4 oder 8.5')} step="0.01" min="0.01" required />
                                    <small className="form-text text-muted">
                                        {t('vacation.overtimeInfoPercentage', 'Geben Sie an, wie viele Überstunden für diesen Zeitraum verwendet werden sollen.')}
                                        {vacationType === 'half' && vacationStartDate === vacationEndDate && (<><br />{t('vacation.halfDayOvertimeNotice', 'Für diesen halben Tag geben Sie die Stunden für den halben Tag an.')}</>)}
                                    </small>
                                </div>
                            )}
                            <div className="modal-buttons">
                                <button type="submit" className="button-primary">{t('submitButton', 'Absenden')}</button>
                                <button type="button" onClick={() => setShowVacationModal(false)} className="button-secondary">{t('cancelButton', 'Abbrechen')}</button>
                            </div>
                        </form>
                    </div>
                </ModalOverlay>
            )}

            {showSickLeaveModal && (
                <ModalOverlay visible className="vacation-modal-overlay sick-leave-modal-overlay">
                    <div className="vacation-modal-content sick-leave-modal-content">
                        <h3>{t('sickLeave.modalTitle', 'Krankheit melden')}</h3>
                        <form onSubmit={handleSickLeaveSubmit}>
                            <div className="form-group">
                                <label htmlFor="sickStartDate">{t('fromDate', 'Von')}:</label>
                                <input id="sickStartDate" type="date" value={sickStartDate} onChange={e => setSickStartDate(e.target.value)} required />
                            </div>
                            <div className="form-group">
                                <label htmlFor="sickEndDate">{t('toDate', 'Bis')}:</label>
                                <input id="sickEndDate" type="date" value={sickEndDate} onChange={e => setSickEndDate(e.target.value)} required />
                            </div>
                            <div className="form-group form-group-checkbox">
                                <input id="sickIsHalfDay" type="checkbox" checked={isSickHalfDay} onChange={e => setIsSickHalfDay(e.target.checked)} />
                                <label htmlFor="sickIsHalfDay" className="checkbox-label">{t('sickLeave.halfDayLabel', 'Halbtag')}</label>
                            </div>
                            <div className="form-group">
                                <label htmlFor="sickComment">{t('comment', 'Kommentar (optional)')}:</label>
                                <textarea id="sickComment" value={sickComment} onChange={e => setSickComment(e.target.value)} rows="3" placeholder={t('sickLeave.commentPlaceholder', 'Grund, Arztbesuch etc.')}></textarea>
                            </div>
                            <div className="modal-buttons">
                                <button type="submit" className="button-primary">{t('submitButton', 'Senden')}</button>
                                <button type="button" onClick={() => setShowSickLeaveModal(false)} className="button-secondary">{t('cancelButton', 'Abbrechen')}</button>
                            </div>
                        </form>
                    </div>
                </ModalOverlay>
            )}
        </div>
    );
}

VacationCalendar.propTypes = {
    vacationRequests: PropTypes.array.isRequired,
    userProfile: PropTypes.shape({
        username: PropTypes.string.isRequired,
        annualVacationDays: PropTypes.number,
        isPercentage: PropTypes.bool,
        isHourly: PropTypes.bool,
        company: PropTypes.shape({ cantonAbbreviation: PropTypes.string }),
        color: PropTypes.string
    }).isRequired,
    onRefreshVacations: PropTypes.func
};

export default VacationCalendar;