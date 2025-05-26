// src/components/VacationCalendar.jsx
import React, { useState, useMemo, useCallback } from 'react'; // useCallback hinzugefügt
import PropTypes from 'prop-types';
import Calendar from 'react-calendar';
import 'react-calendar/dist/Calendar.css';
import '../styles/VacationCalendar.css'; // Stellt sicher, dass diese Datei existiert und korrekt ist

import api from '../utils/api';
import { useNotification } from '../context/NotificationContext';
import { useTranslation } from '../context/LanguageContext';

// formatDateISO wird nicht mehr explizit benötigt, da wir direkt mit State-Strings arbeiten
// function formatDateISO(d) {
//     return d.toISOString().split('T')[0];
// }

function VacationCalendar({ vacationRequests, userProfile, onRefreshVacations }) {
    const { notify } = useNotification();
    const { t } = useTranslation();

    const [showModal, setShowModal] = useState(false);
    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');
    const [vacationType, setVacationType] = useState('full'); // 'full' oder 'half'
    const [useOvertime, setUseOvertime] = useState(false);
    // NEU: State für die Eingabe der Überstunden
    const [overtimeDeductionHours, setOvertimeDeductionHours] = useState('');

    const approvedVacations = useMemo(
        () => vacationRequests.filter((v) => v.approved),
        [vacationRequests]
    );

    // Hilfsfunktion zum Zurücksetzen des Formulars
    const resetForm = useCallback(() => {
        setStartDate('');
        setEndDate('');
        setVacationType('full');
        setUseOvertime(false);
        setOvertimeDeductionHours('');
    }, []);


    function computeUsedVacationDays(vacs) {
        if (!vacs) return 0;
        return vacs.reduce((sum, vac) => {
            if (vac.usesOvertime) return sum; // Überstunden-Urlaub zählt nicht zum regulären Kontingent

            const startDt = new Date(vac.startDate + "T00:00:00"); // Zeitkomponente für korrekte Differenzbildung
            const endDt = new Date(vac.endDate + "T00:00:00");

            if (isNaN(startDt.getTime()) || isNaN(endDt.getTime())) return sum;

            let daysInRequest = 0;
            // Iteriere über jeden Tag im Urlaubszeitraum, um Wochenenden/Feiertage später ggf. auszuschließen
            // Diese einfache Zählung hier zählt alle Kalendertage.
            // Eine genauere Zählung würde Wochentage / Feiertage berücksichtigen (Backend macht das bei Bedarf).
            for (let d = new Date(startDt); d <= endDt; d.setDate(d.getDate() + 1)) {
                daysInRequest += vac.halfDay ? 0.5 : 1.0;
            }
            // Korrektur, falls Halbtag über mehrere Tage beantragt wurde (was nicht erlaubt ist)
            // Die UI verhindert dies bereits, aber zur Sicherheit:
            if (vac.halfDay && !vac.startDate.isEqual(vac.endDate)) { // Annahme: Datumsvergleichslogik
                // Dieser Fall sollte durch die UI verhindert werden. Zähle als 1 Tag zur Sicherheit.
                return sum + 0.5; // Oder eine andere Logik
            }

            return sum + daysInRequest;
        }, 0);
    }

    const totalVacation = Number(userProfile?.annualVacationDays) || 0;
    // computeUsedVacationDays muss die korrekten Daten bekommen.
    // Die Filterung auf approved ist schon in approvedVacations.
    const usedDays = computeUsedVacationDays(approvedVacations.filter(v => !v.usesOvertime)); // Nur regulären Urlaub zählen
    const remainingDays = totalVacation - usedDays;


    const tileContent = ({ date, view }) => {
        if (view !== 'month') return null;

        const calendarDayStart = new Date(date);
        calendarDayStart.setHours(0, 0, 0, 0);

        // Nur genehmigte Urlaube im Kalender farblich markieren
        const vacsToday = approvedVacations.filter((v) => {
            const vacStart = new Date(v.startDate + "T00:00:00");
            const vacEnd = new Date(v.endDate + "T23:59:59"); // Ende des Tages
            return calendarDayStart >= vacStart && calendarDayStart <= vacEnd;
        });

        if (vacsToday.length === 0) return null;

        // Priorisiere ggf. Überstundenurlaub für die Farbe, falls Überschneidungen
        vacsToday.some(v => v.usesOvertime);
        const displayVac = vacsToday.find(v => v.usesOvertime) || vacsToday[0]; // Nimm ersten oder Überstundenurlaub

        const color = displayVac.usesOvertime ? '#3498db' : '#2ecc71'; // Blau für Überstunden, Grün für Normal

        const tooltip = vacsToday
            .map((v) =>
                `${v.halfDay ? '½ ' + t('vacation.halfDay', 'Tag') : t('vacation.fullDay', 'Ganztags')} (${
                    v.usesOvertime ? t('vacation.overtimeVacation', 'Überstundenfrei') : t('vacation.normalVacation', 'Normaler Urlaub')
                })`
            )
            .join('; ');

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
        if (!userProfile || !userProfile.username) {
            notify(t('errors.userNotLoaded', 'Benutzerprofil nicht geladen.'), 'error');
            return;
        }
        if (!startDate || !endDate) {
            return notify(t('vacation.missingDates', 'Bitte Start- und Enddatum angeben.'), 'warning');
        }

        const s = new Date(startDate + "T00:00:00Z"); // Als UTC interpretieren, um Zeitzonenprobleme zu minimieren
        const eDate = new Date(endDate + "T00:00:00Z");

        if (s > eDate) {
            return notify(t('vacation.invalidDates', 'Enddatum darf nicht vor dem Startdatum liegen.'), 'error');
        }
        if (vacationType === 'half' && startDate !== endDate) {
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
                startDate: startDate, // Format YYYY-MM-DD
                endDate: endDate,     // Format YYYY-MM-DD
                halfDay: vacationType === 'half',
                usesOvertime: useOvertime
            };

            if (overtimeDeductionMinutesParam !== null) {
                params.overtimeDeductionMinutes = overtimeDeductionMinutesParam;
            }

            await api.post('/api/vacation/create', null, { params });
            notify(t('vacation.requestSuccess', 'Urlaubsantrag erfolgreich eingereicht.'), 'success');
            setShowModal(false);
            resetForm(); // Formular zurücksetzen
            if (onRefreshVacations) {
                onRefreshVacations(); // Callback aufrufen, um Daten im Dashboard neu zu laden
            }
        } catch (err) {
            console.error("Fehler beim Erstellen des Urlaubsantrags:", err);
            const errorMsg = err.response?.data?.message || err.message || t('errors.unknownError');
            notify(t('vacation.requestError', 'Fehler beim Urlaubsantrag:') + ` ${errorMsg}`, 'error');
        }
    }

    const handleOpenModal = () => {
        resetForm(); // Formular zurücksetzen, wenn Modal geöffnet wird
        setShowModal(true);
    };


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

                <button style={{ marginTop: '1rem' }} onClick={handleOpenModal} className="button-primary">
                    {t('requestVacationButton', 'Urlaub beantragen')}
                </button>
            </div>

            {showModal && (
                <div className="vacation-modal-overlay"> {/* Stellt sicher, dass die Klasse für das Overlay korrekt ist */}
                    <div className="vacation-modal-content"> {/* Stellt sicher, dass die Klasse für den Inhalt korrekt ist */}
                        <h3>{t('vacationModalTitle', 'Urlaubsantrag stellen')}</h3>
                        <form onSubmit={handleSubmit}>
                            <div className="form-group">
                                <label htmlFor="vacStartDate">{t('fromDate', 'Von')}:</label>
                                <input
                                    id="vacStartDate"
                                    type="date"
                                    value={startDate}
                                    onChange={(e) => setStartDate(e.target.value)}
                                    required
                                />
                            </div>
                            <div className="form-group">
                                <label htmlFor="vacEndDate">{t('toDate', 'Bis')}:</label>
                                <input
                                    id="vacEndDate"
                                    type="date"
                                    value={endDate}
                                    onChange={(e) => setEndDate(e.target.value)}
                                    required
                                />
                            </div>
                            <div className="form-group">
                                <label htmlFor="vacDayScope">{t('dayScope', 'Zeitraum')}:</label>
                                <select
                                    id="vacDayScope"
                                    value={vacationType}
                                    onChange={(e) => setVacationType(e.target.value)}
                                >
                                    <option value="full">{t('fullDay', 'Ganztags')}</option>
                                    <option value="half">{t('halfDay', 'Halbtags')}</option>
                                </select>
                            </div>
                            <div className="form-group">
                                <label htmlFor="vacTypeSelect">{t('vacationType', 'Urlaubsart')}:</label>
                                <select
                                    id="vacTypeSelect"
                                    value={useOvertime ? 'overtime' : 'normal'}
                                    onChange={(e) => {
                                        const isOvertime = e.target.value === 'overtime';
                                        setUseOvertime(isOvertime);
                                        if (!isOvertime || !userProfile?.isPercentage) {
                                            setOvertimeDeductionHours(''); // Zurücksetzen, wenn nicht benötigt
                                        }
                                    }}
                                >
                                    <option value="normal">
                                        {t('normalVacation', 'Normaler Urlaub')}
                                    </option>
                                    {/* Überstundenfrei nur anbieten, wenn User kein Stundenlöhner ist */}
                                    {!userProfile?.isHourly && (
                                        <option value="overtime">
                                            {t('overtimeVacation', 'Überstundenfrei')}
                                        </option>
                                    )}
                                </select>
                            </div>

                            {/* NEU: Bedingtes Eingabefeld für Überstundenabzug */}
                            {userProfile?.isPercentage && useOvertime && (
                                <div className="form-group">
                                    <label htmlFor="overtimeHoursInput">
                                        {t('vacation.deductOvertimeHoursLabel', 'Abzuziehende Überstunden (Stunden):')}
                                    </label>
                                    <input
                                        type="number"
                                        id="overtimeHoursInput"
                                        value={overtimeDeductionHours}
                                        onChange={(e) => setOvertimeDeductionHours(e.target.value)}
                                        placeholder={t('vacation.hoursPlaceholder', 'z.B. 4 oder 8.5')}
                                        step="0.01" // Erlaubt Dezimalzahlen
                                        min="0.01"   // Muss positiv sein
                                        required // Feld ist erforderlich, wenn Überstundenfrei gewählt wurde
                                    />
                                    <small className="form-text text-muted">
                                        {t('vacation.overtimeInfoPercentage', 'Geben Sie an, wie viele Überstunden für diesen Zeitraum verwendet werden sollen.')}
                                        {vacationType === 'half' && startDate === endDate && (
                                            <React.Fragment><br/>{t('vacation.halfDayOvertimeNotice', 'Für diesen halben Tag geben Sie die Stunden für den halben Tag an.')}</React.Fragment>
                                        )}
                                    </small>
                                </div>
                            )}

                            <div className="modal-buttons">
                                <button type="submit" className="button-primary">{t('submitButton', 'Absenden')}</button>
                                <button type="button" onClick={() => setShowModal(false)} className="button-secondary">
                                    {t('cancelButton', 'Abbrechen')}
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
    userProfile: PropTypes.shape({
        username: PropTypes.string.isRequired,
        annualVacationDays: PropTypes.number,
        isPercentage: PropTypes.bool, // Wichtig für die neue Logik
        isHourly: PropTypes.bool,     // Wichtig für die Anzeige der Urlaubsart "Überstundenfrei"
    }).isRequired,
    onRefreshVacations: PropTypes.func
};

export default VacationCalendar;