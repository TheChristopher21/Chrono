import { useState, useEffect, useCallback } from 'react';
import PropTypes from 'prop-types';
import Calendar from 'react-calendar';
import 'react-calendar/dist/Calendar.css';
import '../styles/VacationCalendarAdminScoped.css'; // Dein Admin-Kalender Scope CSS
import api from '../utils/api';
import { useAuth } from '../context/AuthContext';
import { useNotification } from '../context/NotificationContext';
import { useTranslation } from '../context/LanguageContext';

// Hilfsfunktion für Kontrastfarbe (kann außerhalb der Komponente bleiben)
function getContrastYIQ(hexcolor) {
    if (!hexcolor) return '#000'; // Fallback für undefined oder leeren String
    hexcolor = hexcolor.replace("#", "");
    if (hexcolor.length !== 6) return '#000'; // Fallback bei ungültigem Hex
    const r = parseInt(hexcolor.substr(0, 2), 16);
    const g = parseInt(hexcolor.substr(2, 2), 16);
    const b = parseInt(hexcolor.substr(4, 2), 16);
    const yiq = ((r * 299) + (g * 587) + (b * 114)) / 1000;
    return (yiq >= 128) ? '#000' : '#fff';
}

const VacationCalendarAdmin = ({ vacationRequests, onReloadVacations }) => {
    const { t } = useTranslation();
    const { currentUser } = useAuth();
    const { notify } = useNotification();

    // Modal & Formular-States
    const [showModal, setShowModal] = useState(false);
    const [newVacationUser, setNewVacationUser] = useState('');
    const [newVacationHalfDay, setNewVacationHalfDay] = useState(false);
    const [newVacationUsesOvertime, setNewVacationUsesOvertime] = useState(false);
    const [overtimeDeductionHours, setOvertimeDeductionHours] = useState(''); // Für die Eingabe in Stunden

    const [adminPassword, setAdminPassword] = useState('');
    const [users, setUsers] = useState([]);
    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');

    // Details des aktuell ausgewählten Benutzers
    const selectedUserDetails = users.find(u => u.username === newVacationUser);

    const fetchAllUsers = useCallback(async () => {
        try {
            // Sicherstellen, dass der API-Aufruf gemacht wird, um 'isPercentage' zu bekommen
            // Der Endpunkt /api/admin/users sollte UserDTOs liefern, die isPercentage enthalten.
            const res = await api.get('/api/admin/users'); // Korrekter Endpunkt
            setUsers(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error('Error fetching users:', err);
            notify(t('errors.fetchUsersError', 'Fehler beim Laden der Benutzer.'), 'error');
            setUsers([]); // Fallback auf leeres Array
        }
    }, [notify, t]);

    useEffect(() => {
        fetchAllUsers();
    }, [fetchAllUsers]);

    function formatYMD(d) {
        if (!d) return '';
        const dateObj = (d instanceof Date) ? d : new Date(d);
        if (isNaN(dateObj.getTime())) return ''; // Ungültiges Datum abfangen

        const year = dateObj.getFullYear();
        const month = String(dateObj.getMonth() + 1).padStart(2, '0');
        const day = String(dateObj.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
    }

    function vacationInRange(vac, start, end) {
        if (!vac || !vac.startDate || !vac.endDate || !start || !end) return false;
        try {
            const vacStart = new Date(vac.startDate);
            const vacEnd = new Date(vac.endDate);
            vacStart.setHours(0, 0, 0, 0); // Zeitkomponente für Vergleich entfernen
            vacEnd.setHours(0, 0, 0, 0);   // Zeitkomponente für Vergleich entfernen

            const compStart = (start instanceof Date) ? new Date(start.getTime()) : new Date(start);
            compStart.setHours(0,0,0,0);
            const compEnd = (end instanceof Date) ? new Date(end.getTime()) : new Date(end);
            compEnd.setHours(0,0,0,0);

            return vacEnd >= compStart && vacStart <= compEnd;
        } catch (e) {
            console.error("Error parsing date in vacationInRange:", vac, start, end, e);
            return false;
        }
    }

    const resetForm = useCallback(() => {
        setNewVacationUser('');
        setStartDate('');
        setEndDate('');
        setNewVacationHalfDay(false);
        setNewVacationUsesOvertime(false);
        setOvertimeDeductionHours('');
        setAdminPassword('');
    }, []);

    async function handleCreateVacation() {
        if (!currentUser || !currentUser.username) {
            notify(t("errors.notLoggedIn", "Nicht eingeloggt oder Benutzername fehlt."), 'error');
            return;
        }
        if (!newVacationUser) {
            notify(t("adminVacation.noUserSelected", "Bitte einen Benutzer auswählen"), 'warning');
            return;
        }
        if (!startDate || !endDate) {
            notify(t("adminVacation.datesMissing", "Bitte Start- und Enddatum angeben"), 'warning');
            return;
        }
        if (!adminPassword) {
            notify(t("adminVacation.adminPassMissing", "Bitte Admin-Passwort eingeben"), 'warning');
            return;
        }

        let startD, endD;
        try {
            startD = new Date(startDate);
            endD = new Date(endDate);
            startD.setHours(0,0,0,0);
            endD.setHours(0,0,0,0);
        } catch (e) {
            notify(t("adminVacation.invalidDate", "Ungültiges Datumsformat."), 'error');
            return;
        }

        if (endD < startD) {
            notify(t("adminVacation.endDateBeforeStart", "Das Enddatum darf nicht vor dem Startdatum liegen."), 'error');
            return;
        }

        let overtimeDeductionMinutes = null;
        if (newVacationUsesOvertime && selectedUserDetails?.isPercentage) {
            const hours = parseFloat(overtimeDeductionHours);
            if (isNaN(hours) || hours <= 0) {
                notify(t("adminVacation.invalidOvertimeHours", "Bitte eine gültige positive Stundenzahl für den Überstundenabzug eingeben."), 'error');
                return;
            }
            // Das Backend (VacationService.adminCreateVacation) halbiert die `overtimeDeductionMinutesParam`,
            // wenn `halfDay` true ist UND es sich um einen *einzelnen* Tag handelt.
            // Das Frontend sendet daher immer die vom Admin eingegebenen "vollen" Stunden, umgerechnet in Minuten.
            overtimeDeductionMinutes = Math.round(hours * 60);
        }

        const params = {
            adminUsername: currentUser.username,
            adminPassword,
            username: newVacationUser,
            startDate: formatYMD(startD),
            endDate: formatYMD(endD),
            halfDay: newVacationHalfDay,
            usesOvertime: newVacationUsesOvertime,
        };

        if (overtimeDeductionMinutes !== null) {
            params.overtimeDeductionMinutes = overtimeDeductionMinutes;
        }

        try {
            await api.post('/api/vacation/adminCreate', null, { params });
            notify(t("adminVacation.createdSuccess", "Urlaub erfolgreich erstellt und direkt genehmigt"), 'success');

            if (onReloadVacations) {
                onReloadVacations();
            }
            setShowModal(false);
            resetForm();

        } catch (err) {
            console.error('Error creating vacation (adminCreate)', err);
            const errorMsg = err.response?.data?.message || err.response?.data || err.message || t('errors.unknownError', "Unbekannter Fehler");
            notify(t("adminVacation.createError", "Fehler beim Anlegen des Urlaubs") + `: ${errorMsg}`, 'error');
        }
    }

    function tileContent({ date, view }) {
        if (view === 'month') {
            const day = new Date(date);
            day.setHours(0, 0, 0, 0);

            const vacsToday = vacationRequests.filter((vac) => vacationInRange(vac, day, day));
            if (vacsToday.length > 0) {
                return (
                    <div className="vacation-markers">
                        {vacsToday.map((vac) => {
                            const bgColor = vac.color || '#767676';
                            const textColor = getContrastYIQ(bgColor);
                            let displayName = vac.username || t('adminVacation.unknownUser', 'Unbekannt');
                            if (vac.halfDay) displayName += ` (${t('adminVacation.halfDayShort', '½')})`;
                            if (vac.usesOvertime) displayName += " 🌙";

                            return (
                                <div
                                    key={vac.id}
                                    className="vacation-marker"
                                    style={{ backgroundColor: bgColor, color: textColor }}
                                    title={`${vac.username || ''}${vac.halfDay ? ` (${t('adminVacation.halfDayShort', '½')})` : ""}${vac.usesOvertime ? ` (${t('adminVacation.overtimeVacationShort', 'ÜS')})` : ""}`}
                                >
                                    {displayName}
                                </div>
                            );
                        })}
                    </div>
                );
            }
        }
        return null;
    }

    const handleUserChange = (e) => {
        const selectedUsername = e.target.value;
        setNewVacationUser(selectedUsername);
        const userDetails = users.find(u => u.username === selectedUsername);

        if (!userDetails || !userDetails.isPercentage) {
            setNewVacationUsesOvertime(false);
            setOvertimeDeductionHours('');
        } else if (!newVacationUsesOvertime) {
            setOvertimeDeductionHours('');
        }
    };

    const handleUsesOvertimeChange = (e) => {
        const isChecked = e.target.checked;
        setNewVacationUsesOvertime(isChecked);
        if (!isChecked || !selectedUserDetails?.isPercentage) {
            setOvertimeDeductionHours('');
        }
    };

    const openModalAndReset = () => {
        resetForm();
        setShowModal(true);
    };

    return (
        <div className="vacation-calendar-admin scoped-vacation">
            <h2>{t('adminVacation.title', 'Admin Urlaubs Kalender')}</h2>

            <Calendar
                value={new Date()}
                tileContent={tileContent}
                onClickDay={(value) => {
                    const dateStr = formatYMD(value);
                    setStartDate(dateStr);
                    setEndDate(dateStr);
                    setNewVacationUser('');
                    setNewVacationHalfDay(false);
                    setNewVacationUsesOvertime(false);
                    setOvertimeDeductionHours('');
                    setAdminPassword('');
                    setShowModal(true);
                }}
            />

            <button onClick={openModalAndReset} className="create-vacation-button">
                {t('adminVacation.createVacationButton', 'Urlaub manuell erstellen')}
            </button>

            {showModal && (
                <div className="modal-overlay">
                    <div className="modal-content">
                        <h3>{t('adminVacation.modalTitle', 'Neuen Urlaub für Mitarbeiter anlegen')}</h3>

                        <form onSubmit={(e) => { e.preventDefault(); handleCreateVacation(); }}>
                            <div className="form-group">
                                <label htmlFor="vacationUserSelect">
                                    {t('adminVacation.userSelection', 'Benutzer Auswahl')}:
                                </label>
                                <select
                                    id="vacationUserSelect"
                                    value={newVacationUser}
                                    onChange={handleUserChange}
                                    required
                                >
                                    <option value="">
                                        {t('adminVacation.selectUserPlaceholder', 'Bitte Benutzer auswählen')}
                                    </option>
                                    {users.map((u) => (
                                        <option key={u.id} value={u.username}>
                                            {u.firstName} {u.lastName} ({u.username})
                                        </option>
                                    ))}
                                </select>
                            </div>

                            <div className="form-group">
                                <label htmlFor="startDateInput">
                                    {t('adminVacation.startDateLabel', 'Startdatum')}:
                                </label>
                                <input
                                    id="startDateInput"
                                    type="date"
                                    value={startDate}
                                    onChange={(e) => setStartDate(e.target.value)}
                                    required
                                />
                            </div>

                            <div className="form-group">
                                <label htmlFor="endDateInput">
                                    {t('adminVacation.endDateLabel', 'Enddatum')}:
                                </label>
                                <input
                                    id="endDateInput"
                                    type="date"
                                    value={endDate}
                                    onChange={(e) => setEndDate(e.target.value)}
                                    required
                                />
                            </div>

                            <div className="form-group form-group-checkbox">
                                <input
                                    type="checkbox"
                                    id="halfDayCheckbox"
                                    checked={newVacationHalfDay}
                                    onChange={(e) => setNewVacationHalfDay(e.target.checked)}
                                />
                                <label htmlFor="halfDayCheckbox">
                                    {t('adminVacation.halfDayLabel', 'Halbtags Urlaub')}
                                </label>
                            </div>

                            <div className="form-group form-group-checkbox">
                                <input
                                    type="checkbox"
                                    id="usesOvertimeCheckbox"
                                    checked={newVacationUsesOvertime}
                                    onChange={handleUsesOvertimeChange}
                                    disabled={!selectedUserDetails}
                                />
                                <label htmlFor="usesOvertimeCheckbox">
                                    {t('adminVacation.usesOvertimeLabel', 'Überstunden nutzen')}
                                </label>
                            </div>

                            {newVacationUsesOvertime && selectedUserDetails?.isPercentage && (
                                <div className="form-group">
                                    <label htmlFor="overtimeDeductionHoursInput">
                                        {t('adminVacation.overtimeDeductionHoursLabel', 'Abzuziehende Überstunden (in Stunden):')}
                                    </label>
                                    <input
                                        type="number"
                                        id="overtimeDeductionHoursInput"
                                        value={overtimeDeductionHours}
                                        onChange={(e) => setOvertimeDeductionHours(e.target.value)}
                                        placeholder={t('adminVacation.hoursPlaceholder', 'z.B. 4 oder 8.5')}
                                        step="0.01"
                                        min="0.01"
                                        required
                                    />
                                    {newVacationHalfDay && startDate && endDate && startDate === endDate && (
                                        <small className="form-text text-muted">
                                            {t('adminVacation.halfDayDeductionNotice', 'Hinweis: Für diesen halben Tag werden die oben angegebenen Stunden vom Backend halbiert.')}
                                        </small>
                                    )}
                                </div>
                            )}

                            <div className="form-group">
                                <label htmlFor="adminPasswordInput">
                                    {t('adminVacation.adminPasswordLabel', 'Admin-Passwort')}:
                                </label>
                                <input
                                    type="password"
                                    id="adminPasswordInput"
                                    value={adminPassword}
                                    onChange={(e) => setAdminPassword(e.target.value)}
                                    required
                                />
                            </div>

                            <div className="modal-buttons">
                                <button type="submit" className="button-confirm">
                                    {t('adminVacation.confirmButton', 'Urlaub erstellen')}
                                </button>
                                <button type="button" onClick={() => {setShowModal(false); resetForm();}} className="button-cancel">
                                    {t('cancel', 'Abbrechen')}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

VacationCalendarAdmin.propTypes = {
    vacationRequests: PropTypes.arrayOf(
        PropTypes.shape({
            id: PropTypes.number.isRequired,
            startDate: PropTypes.string.isRequired,
            endDate: PropTypes.string.isRequired,
            username: PropTypes.string,
            color: PropTypes.string,
            halfDay: PropTypes.bool,
            usesOvertime: PropTypes.bool,
        })
    ).isRequired,
    onReloadVacations: PropTypes.func
};

export default VacationCalendarAdmin;