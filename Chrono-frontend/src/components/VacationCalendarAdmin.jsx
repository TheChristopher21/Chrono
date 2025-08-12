import React, { useState, useEffect, useCallback } from 'react';
import ModalOverlay from './ModalOverlay';
import PropTypes from 'prop-types';
import Calendar from 'react-calendar';
import 'react-calendar/dist/Calendar.css';
import '../styles/VacationCalendarAdminScoped.css';
import api from '../utils/api';
import { useAuth } from '../context/AuthContext';
import { useNotification } from '../context/NotificationContext';
import { useTranslation } from '../context/LanguageContext';
import { formatLocalDateYMD } from '../pages/AdminDashboard/adminDashboardUtils.js'; // Passe den Pfad ggf. an

// Annahme: formatLocalDateYMD ist in einer Utility-Datei und wird hier importiert
// import { formatLocalDateYMD } from './adminDashboardUtils'; // Oder der korrekte Pfad

// Falls formatLocalDateYMD nicht extern ist, hier definieren:


function getContrastYIQ(hexcolor) {
    if (!hexcolor) return '#000';
    hexcolor = hexcolor.replace("#", "");
    if (hexcolor.length !== 6) return '#000';
    const r = parseInt(hexcolor.substr(0, 2), 16);
    const g = parseInt(hexcolor.substr(2, 2), 16);
    const b = parseInt(hexcolor.substr(4, 2), 16);
    const yiq = ((r * 299) + (g * 587) + (b * 114)) / 1000;
    return (yiq >= 128) ? '#000' : '#fff';
}

const VacationCalendarAdmin = ({ vacationRequests, onReloadVacations, companyUsers: initialCompanyUsers }) => {
    const { t } = useTranslation();
    const { currentUser } = useAuth();
    const { notify } = useNotification();

    const [showVacationModal, setShowVacationModal] = useState(false);
    const [newVacationUser, setNewVacationUser] = useState('');
    const [newVacationHalfDay, setNewVacationHalfDay] = useState(false);
    const [newVacationUsesOvertime, setNewVacationUsesOvertime] = useState(false);
    const [overtimeDeductionHours, setOvertimeDeductionHours] = useState('');
    const [vacationStartDate, setVacationStartDate] = useState('');
    const [vacationEndDate, setVacationEndDate] = useState('');
    const [isCompanyVacation, setIsCompanyVacation] = useState(false);

    const [showSickLeaveModal, setShowSickLeaveModal] = useState(false);
    const [sickLeaveUser, setSickLeaveUser] = useState('');
    const [sickLeaveStartDate, setSickLeaveStartDate] = useState('');
    const [sickLeaveEndDate, setSickLeaveEndDate] = useState('');
    const [isSickLeaveHalfDay, setIsSickLeaveHalfDay] = useState(false);
    const [sickLeaveComment, setSickLeaveComment] = useState('');
    const [allSickLeaves, setAllSickLeaves] = useState([]);

    const [users, setUsers] = useState(initialCompanyUsers || []);
    const [holidays, setHolidays] = useState({});
    const [activeStartDate, setActiveStartDate] = useState(new Date());
    const [currentCantonForHolidays, setCurrentCantonForHolidays] = useState(null);
    // const [selectedUserForSickLeaveDetails, setSelectedUserForSickLeaveDetails] = useState(null); // Entfernt, da currentCantonForHolidays ausreicht

    const selectedUserDetailsForVacation = users.find(u => u.username === newVacationUser);

    const fetchAllUsers = useCallback(async () => {
        if (initialCompanyUsers && initialCompanyUsers.length > 0) {
            setUsers(initialCompanyUsers);
        } else {
            try {
                const res = await api.get('/api/admin/users');
                const fetchedUsers = Array.isArray(res.data) ? res.data : [];
                setUsers(fetchedUsers);
            } catch (err) {
                console.error('Error fetching users:', err);
                notify(t('errors.fetchUsersError', 'Fehler beim Laden der Benutzer.'), 'error');
                setUsers([]);
            }
        }
    }, [notify, t, initialCompanyUsers]);

    useEffect(() => {
        fetchAllUsers();
    }, [fetchAllUsers]);

    useEffect(() => {
        let cantonToUse = null;
        if (newVacationUser && users.length > 0) {
            const userDetails = users.find(u => u.username === newVacationUser);
            cantonToUse = userDetails?.company?.cantonAbbreviation || null;
        } else if (sickLeaveUser && users.length > 0) {
            const userDetails = users.find(u => u.username === sickLeaveUser);
            cantonToUse = userDetails?.company?.cantonAbbreviation || null;
        } else if (currentUser?.company?.cantonAbbreviation) {
            cantonToUse = currentUser.company.cantonAbbreviation;
        } else if (users.length > 0 && users[0]?.company?.cantonAbbreviation) {
            cantonToUse = users[0].company.cantonAbbreviation;
        }
        setCurrentCantonForHolidays(cantonToUse);
    }, [newVacationUser, sickLeaveUser, users, currentUser]);

    const fetchHolidays = useCallback(async (year, canton) => {
        try {
            const yearStartDate = `${year}-01-01`;
            const yearEndDate = `${year}-12-31`;
            const params = { year, cantonAbbreviation: canton || '', startDate: yearStartDate, endDate: yearEndDate };
            const response = await api.get('/api/holidays/details', { params });
            setHolidays(prevHolidays => ({ ...prevHolidays, ...response.data }));
        } catch (error) {
            console.error(t('errors.fetchHolidaysError', 'Fehler beim Laden der Feiertage:'), error);
        }
    }, [t]);

    const fetchAllSickLeaves = useCallback(async () => {
        try {
            let endpoint = '/api/sick-leave/company';
            const params = {};
            if (currentUser?.roles?.includes('ROLE_SUPERADMIN') && !currentUser.companyId) {
                // SuperAdmin ohne eigene Firma sieht alle -> keine CompanyId im Params
            } else if (currentUser?.companyId) {
                params.companyId = currentUser.companyId; // Admin oder SuperAdmin mit Firma
            }
            const response = await api.get(endpoint, { params });
            setAllSickLeaves(Array.isArray(response.data) ? response.data : []);
        } catch (error) {
            console.error(t('errors.fetchSickLeaveErrorAdmin', 'Fehler beim Laden der Krankmeldungen (Admin):'), error);
            notify(t('errors.fetchSickLeaveErrorAdmin', 'Fehler beim Laden der Krankmeldungen (Admin).'), 'error');
        }
    }, [currentUser, notify, t]);

    useEffect(() => {
        const year = activeStartDate.getFullYear();
        const cantonToLoad = currentCantonForHolidays;

        let holidaysLoadedForYearAndCanton = false;
        for (const dateKey in holidays) {
            if (dateKey.startsWith(String(year))) { // Grobe Prüfung fürs Jahr
                // Hier könnte man eine genauere Prüfung einbauen, ob es für *diesen Kanton* geladen wurde,
                // falls die Kantonsauswahl dynamischer ist und nicht nur beim User-Wechsel im Modal.
                // Für den Moment reicht die Jahresprüfung, da der Kanton oben gesetzt wird.
                holidaysLoadedForYearAndCanton = true;
                break;
            }
        }
        if (!holidaysLoadedForYearAndCanton) {
            fetchHolidays(year, cantonToLoad);
        }
        fetchAllSickLeaves();
    }, [activeStartDate, currentCantonForHolidays, fetchHolidays, holidays, fetchAllSickLeaves]);


    function itemInRange(item, start, end, dateFieldPrefix = '') {
        const itemStartDateField = `${dateFieldPrefix}startDate`;
        const itemEndDateField = `${dateFieldPrefix}endDate`;

        if (!item || !item[itemStartDateField] || !item[itemEndDateField] || !start || !end) return false;
        try {
            // item.startDate und item.endDate sind Strings im Format "YYYY-MM-DD" vom Backend
            const itemStartStr = item[itemStartDateField];
            const itemEndStr = item[itemEndDateField];

            // 'start' ist das Date-Objekt vom Kalender für den aktuellen Tag, den wir prüfen.
            // Wandle es in "YYYY-MM-DD" um für den String-Vergleich.
            const compDateStr = formatLocalDateYMD(start);

            // Vergleiche die Strings.
            return compDateStr >= itemStartStr && compDateStr <= itemEndStr;

        } catch (e) {
            console.error("Error in itemInRange:", item, start, end, e);
            return false;
        }
    }
    const resetVacationForm = useCallback(() => {
        setNewVacationUser('');
        setVacationStartDate('');
        setVacationEndDate('');
        setNewVacationHalfDay(false);
        setNewVacationUsesOvertime(false);
        setOvertimeDeductionHours('');
        setIsCompanyVacation(false);
    }, []);

    const resetSickLeaveForm = useCallback(() => {
        setSickLeaveUser('');
        setSickLeaveStartDate('');
        setSickLeaveEndDate('');
        setIsSickLeaveHalfDay(false);
        setSickLeaveComment('');
    }, []);

    async function handleCreateVacation() {
        if (!currentUser || !currentUser.username) {
            notify(t("errors.notLoggedIn", "Nicht eingeloggt oder Benutzername fehlt."), 'error');
            return;
        }
        if (!vacationStartDate || !vacationEndDate) {
            notify(t("adminVacation.datesMissing", "Bitte Start- und Enddatum angeben"), 'warning');
            return;
        }
        if (new Date(vacationEndDate) < new Date(vacationStartDate)) {
            notify(t("adminVacation.endDateBeforeStart", "Das Enddatum darf nicht vor dem Startdatum liegen."), 'error');
            return;
        }

        if (isCompanyVacation) {
            try {
                const params = {
                    adminUsername: currentUser.username,
                    startDate: vacationStartDate,
                    endDate: vacationEndDate,
                    halfDay: newVacationHalfDay,
                };
                await api.post('/api/vacation/companyCreate', null, { params });
                notify(t("adminVacation.createdSuccess", "Urlaub erfolgreich erstellt und direkt genehmigt"), 'success');
                if (onReloadVacations) onReloadVacations();
                setShowVacationModal(false);
                resetVacationForm();
            } catch (err) {
                console.error('Error creating company vacation', err);
                const errorMsg = err.response?.data?.message || err.response?.data || err.message || t('errors.unknownError');
                notify(t("adminVacation.createError", "Fehler beim Anlegen des Urlaubs") + `: ${errorMsg}`, 'error');
            }
            return;
        }

        if (!newVacationUser) {
            notify(t("adminVacation.noUserSelected", "Bitte einen Benutzer auswählen"), 'warning');
            return;
        }

        let overtimeDeductionMinutes = null;
        if (newVacationUsesOvertime && selectedUserDetailsForVacation?.isPercentage) {
            const hours = parseFloat(overtimeDeductionHours);
            if (isNaN(hours) || hours <= 0) {
                notify(t("adminVacation.invalidOvertimeHours", "Bitte eine gültige positive Stundenzahl für den Überstundenabzug eingeben."), 'error');
                return;
            }
            overtimeDeductionMinutes = Math.round(hours * 60);
        }

        const params = {
            adminUsername: currentUser.username,
            username: newVacationUser,
            startDate: vacationStartDate,
            endDate: vacationEndDate,
            halfDay: newVacationHalfDay,
            usesOvertime: newVacationUsesOvertime,
        };
        if (overtimeDeductionMinutes !== null) params.overtimeDeductionMinutes = overtimeDeductionMinutes;

        try {
            await api.post('/api/vacation/adminCreate', null, { params });
            notify(t("adminVacation.createdSuccess", "Urlaub erfolgreich erstellt und direkt genehmigt"), 'success');
            if (onReloadVacations) onReloadVacations();
            setShowVacationModal(false);
            resetVacationForm();
        } catch (err) {
            console.error('Error creating vacation (adminCreate)', err);
            const errorMsg = err.response?.data?.message || err.response?.data || err.message || t('errors.unknownError');
            notify(t("adminVacation.createError", "Fehler beim Anlegen des Urlaubs") + `: ${errorMsg}`, 'error');
        }
    }

    async function handleReportSickLeave() {
        // ... (Logik bleibt gleich)
        if (!currentUser?.username) {
            notify(t("errors.notLoggedIn", "Admin nicht eingeloggt."), 'error');
            return;
        }
        if (!sickLeaveUser) {
            notify(t("adminSickLeave.noUserSelected", "Bitte einen Benutzer für die Krankmeldung auswählen."), 'warning');
            return;
        }
        if (!sickLeaveStartDate || !sickLeaveEndDate) {
            notify(t("adminSickLeave.datesMissing", "Bitte Start- und Enddatum für die Krankmeldung angeben."), 'warning');
            return;
        }
        if (new Date(sickLeaveEndDate) < new Date(sickLeaveStartDate)) {
            notify(t("adminSickLeave.endDateBeforeStart", "Das Enddatum der Krankheit darf nicht vor dem Startdatum liegen."), 'error');
            return;
        }
        if (isSickLeaveHalfDay && sickLeaveStartDate !== sickLeaveEndDate) {
            notify(t('sickLeave.halfDayOneDay', 'Halbtägige Krankmeldung nur für einen einzelnen Tag.'), 'error');
            return;
        }

        try {
            const params = {
                targetUsername: sickLeaveUser,
                startDate: sickLeaveStartDate,
                endDate: sickLeaveEndDate,
                halfDay: isSickLeaveHalfDay,
                comment: sickLeaveComment,
            };
            await api.post('/api/sick-leave/report', null, { params });
            notify(t("adminSickLeave.reportSuccess", "Krankmeldung erfolgreich für Benutzer eingetragen."), 'success');
            setShowSickLeaveModal(false);
            resetSickLeaveForm();
            fetchAllSickLeaves();
            if (onReloadVacations) onReloadVacations();
        } catch (err) {
            console.error('Error reporting sick leave (Admin):', err);
            const errorMsg = err.response?.data?.message || err.message || t('errors.unknownError');
            notify(t("adminSickLeave.reportError", "Fehler beim Eintragen der Krankmeldung:") + ` ${errorMsg}`, 'error');
        }
    }

    function tileContent({ date, view }) {
        if (view === 'month') {
            // 'date' ist das Date-Objekt, das vom Kalender für die Kachel bereitgestellt wird.
            // Es repräsentiert den lokalen Tag.
            // Wandle es in den String-Format "YYYY-MM-DD" um, den du für den Abgleich mit 'holidays' verwendest.
            const dateString = formatLocalDateYMD(date); // KORREKTE VERWENDUNG

            let dayMarkers = [];

            // Feiertags-Marker
            if (holidays[dateString]) { // Abgleich mit dem lokalen Datums-String
                dayMarkers.push(
                    <div
                        key={`holiday-${dateString}`}
                        className="holiday-marker-admin"
                        title={`${t('holiday', 'Feiertag')}: ${holidays[dateString]}`}
                    >
                        <span role="img" aria-label="holiday">🎉</span>
                    </div>
                );
            }

            // Urlaubs-Marker
            // Die 'itemInRange'-Funktion sollte ebenfalls mit dem lokalen 'date'-Objekt und den YYYY-MM-DD Strings aus 'vac' arbeiten.
            const vacsToday = vacationRequests.filter((vac) => itemInRange(vac, date, date));
            if (vacsToday.length > 0) {
                vacsToday.forEach((vac, index) => {
                    const bgColor = vac.color || '#767676';
                    const textColor = getContrastYIQ(bgColor);
                    let displayName = vac.username || t('adminVacation.unknownUser', 'Unbekannt');
                    if (vac.halfDay) displayName += ` (${t('adminVacation.halfDayShort', '½')})`;
                    if (vac.usesOvertime) displayName += " 🌙";

                    dayMarkers.push(
                        <div
                            key={vac.id || `vac-${dateString}-${index}`}
                            className="vacation-marker"
                            style={{ backgroundColor: bgColor, color: textColor }}
                            title={`${vac.username || ''}${vac.halfDay ? ` (${t('adminVacation.halfDayShort', '½')})` : ""}${vac.usesOvertime ? ` (${t('adminVacation.overtimeVacationShort', 'ÜS')})` : ""}`}
                        >
                            {displayName}
                        </div>
                    );
                });
            }

            // Krankheitsmarker
            const sickTodayList = allSickLeaves.filter(sl => itemInRange(sl, date, date));
            sickTodayList.forEach((sick, index) => {
                const sickColor = sick.color || '#FF6347';
                const sickTextColor = getContrastYIQ(sickColor);
                let sickDisplayName = sick.username || t('adminSickLeave.unknownUser', 'Unbekannt');
                if (sick.halfDay) sickDisplayName += ` (${t('adminSickLeave.halfDayShort', '½')} ${t('sickLeave.sickShort', 'K')})`;
                else sickDisplayName += ` (${t('sickLeave.sickShort', 'K')})`;

                dayMarkers.push(
                    <div key={`sick-${sick.id || `sick-${dateString}-${index}`}`} className="sick-leave-marker-admin"
                         style={{ backgroundColor: sickColor, color: sickTextColor}}
                         title={`${sick.username}: ${sick.halfDay ? t('sickLeave.halfDay', 'Halbtags krank') : t('sickLeave.fullDay', 'Ganztags krank')}${sick.comment ? ` (${sick.comment})` : ''}`}>
                        {sickDisplayName}
                    </div>
                );
            });

            return dayMarkers.length > 0 ? <div className="vacation-markers">{dayMarkers}</div> : null;
        }
        return null;
    }

    const onActiveStartDateChange = ({ activeStartDate: newActiveStartDate }) => {
        setActiveStartDate(newActiveStartDate);
    };

    const handleVacationUserChange = (e) => {
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

    const handleSickLeaveUserChange = (e) => {
        setSickLeaveUser(e.target.value);
        const userDetails = users.find(u => u.username === e.target.value);
        if (userDetails?.company?.cantonAbbreviation) {
            setCurrentCantonForHolidays(userDetails.company.cantonAbbreviation);
        }
    };

    const handleUsesOvertimeChange = (e) => {
        const isChecked = e.target.checked;
        setNewVacationUsesOvertime(isChecked);
        if (!isChecked || !selectedUserDetailsForVacation?.isPercentage) {
            setOvertimeDeductionHours('');
        }
    };

    const openVacationModalAndReset = (dateClicked = null) => {
        resetVacationForm();
        if (dateClicked) {
            const dateStr = formatLocalDateYMD(dateClicked);
            setVacationStartDate(dateStr);
            setVacationEndDate(dateStr);
        }
        setShowVacationModal(true);
    };

    const openSickLeaveModalAndReset = (dateClicked = null) => {
        resetSickLeaveForm();
        if (dateClicked) {
            const dateStr = formatYMD(dateClicked);
            setSickLeaveStartDate(dateStr);
            setSickLeaveEndDate(dateStr);
        }
        setShowSickLeaveModal(true);
    };



// Monatshilfen
    function startOfMonth(d) {
        const date = new Date(d);
        date.setDate(1);
        date.setHours(0, 0, 0, 0);
        return date;
    }
    function addMonths(d, m) {
        const date = new Date(d);
        date.setMonth(date.getMonth() + m, 1); // springt sicher zum 1. des Zielmonats
        date.setHours(0, 0, 0, 0);
        return date;
    }
    function formatMonthYear(d, locale = 'de-DE') {
        return new Date(d).toLocaleDateString(locale, { month: 'long', year: 'numeric' });
    }



    const goToPrevMonth = () => setActiveStartDate(startOfMonth(addMonths(activeStartDate, -1)));
    const goToNextMonth = () => setActiveStartDate(startOfMonth(addMonths(activeStartDate,  +1)));

    return (
        <div className="vacation-calendar-admin scoped-vacation">
            <h2>{t('adminCalendar.title', 'Admin Kalenderübersicht')}</h2>
            <div className="calendar-toolbar">
                {/* Monats-Navigation */}
                <div className="toolbar-group">
                    <button
                        type="button"
                        className="btn-arrow"
                        onClick={goToPrevMonth}
                        aria-label={t('prevMonth', 'Vorheriger Monat')}
                        title={t('prevMonth', 'Vorheriger Monat')}
                    >
                        «
                    </button>

                    <div className="toolbar-label month-label">
                        {formatMonthYear(activeStartDate, t('calendarLocale','de-DE'))}
                    </div>

                    <button
                        type="button"
                        className="btn-arrow"
                        onClick={goToNextMonth}
                        aria-label={t('nextMonth', 'Nächster Monat')}
                        title={t('nextMonth', 'Nächster Monat')}
                    >
                        »
                    </button>
                </div>
            </div>

            <Calendar
                className="calendar-lg"
                value={activeStartDate}
                tileContent={tileContent}
                onActiveStartDateChange={onActiveStartDateChange}
                onClickDay={(value) => { openVacationModalAndReset(value); }}
                locale={t('calendarLocale', 'de-DE')}
            />
            <div className="admin-calendar-actions">
                <button onClick={() => openVacationModalAndReset()} className="create-vacation-button">
                    {t('adminVacation.createVacationButton', 'Urlaub manuell erstellen')}
                </button>
                <button onClick={() => openSickLeaveModalAndReset()} className="report-sick-leave-button-admin">
                    {t('adminSickLeave.reportButton', 'Krankheit für User melden')}
                </button>
            </div>

            {showVacationModal && (
                <ModalOverlay visible>
                    <div className="modal-content large-calendar-modal">
                        <h3>{t('adminVacation.modalTitle', 'Neuen Urlaub für Mitarbeiter anlegen')}</h3>
                        <form onSubmit={(e) => { e.preventDefault(); handleCreateVacation(); }}>
                            <div className="form-group form-group-checkbox">
                                <input type="checkbox" id="companyVacationCheckbox" checked={isCompanyVacation} onChange={(e) => setIsCompanyVacation(e.target.checked)} />
                                <label htmlFor="companyVacationCheckbox">{t('adminVacation.companyVacationLabel', 'Betriebsurlaub')}</label>
                            </div>
                            {!isCompanyVacation && (
                                <div className="form-group">
                                    <label htmlFor="vacationUserSelect">{t('adminVacation.userSelection', 'Benutzer Auswahl')}:</label>
                                    <select id="vacationUserSelect" value={newVacationUser} onChange={handleVacationUserChange} required>
                                        <option value="">{t('adminVacation.selectUserPlaceholder', 'Bitte Benutzer auswählen')}</option>
                                        {users.map((u) => (<option key={u.id} value={u.username}>{u.firstName} {u.lastName} ({u.username})</option>))}
                                    </select>
                                </div>
                            )}
                            <Calendar
                                className="calendar-lg"
                                activeStartDate={activeStartDate}
                                selectRange
                                onChange={(range) => {
                                    if (Array.isArray(range)) {
                                        const [startSel, endSel] = range;
                                        if (startSel) setVacationStartDate(formatLocalDateYMD(startSel));
                                        if (endSel) setVacationEndDate(formatLocalDateYMD(endSel));
                                    }
                                }}
                                value={[
                                    vacationStartDate ? new Date(vacationStartDate) : new Date(),
                                    vacationEndDate ? new Date(vacationEndDate) : new Date()
                                ]}
                                locale={t('calendarLocale', 'de-DE')}
                            />
                            <div className="form-group">
                                <label htmlFor="vacStartDateInput">{t('adminVacation.startDateLabel', 'Startdatum')}:</label>
                                <input id="vacStartDateInput" type="date" value={vacationStartDate} onChange={(e) => setVacationStartDate(e.target.value)} required />
                            </div>
                            <div className="form-group">
                                <label htmlFor="vacEndDateInput">{t('adminVacation.endDateLabel', 'Enddatum')}:</label>
                                <input id="vacEndDateInput" type="date" value={vacationEndDate} onChange={(e) => setVacationEndDate(e.target.value)} required />
                            </div>
                            <div className="form-group form-group-checkbox">
                                <input type="checkbox" id="vacHalfDayCheckbox" checked={newVacationHalfDay} onChange={(e) => setNewVacationHalfDay(e.target.checked)} />
                                <label htmlFor="vacHalfDayCheckbox">{t('adminVacation.halfDayLabel', 'Halbtags Urlaub')}</label>
                            </div>
                            {!isCompanyVacation && (
                                <div className="form-group form-group-checkbox">
                                    <input type="checkbox" id="vacUsesOvertimeCheckbox" checked={newVacationUsesOvertime} onChange={handleUsesOvertimeChange} disabled={!selectedUserDetailsForVacation} />
                                    <label htmlFor="vacUsesOvertimeCheckbox">{t('adminVacation.usesOvertimeLabel', 'Überstunden nutzen')}</label>
                                </div>
                            )}
                            {!isCompanyVacation && newVacationUsesOvertime && selectedUserDetailsForVacation?.isPercentage && (
                                <div className="form-group">
                                    <label htmlFor="vacOvertimeDeductionHoursInput">{t('adminVacation.overtimeDeductionHoursLabel', 'Abzuziehende Überstunden Insgesamt (in Stunden):')}</label>
                                    <input type="number" id="vacOvertimeDeductionHoursInput" value={overtimeDeductionHours} onChange={(e) => setOvertimeDeductionHours(e.target.value)} placeholder={t('adminVacation.hoursPlaceholder', 'z.B. 4 oder 8.5')} step="0.01" min="0.01" required />
                                    {newVacationHalfDay && vacationStartDate && vacationEndDate && vacationStartDate === vacationEndDate && (<small className="form-text text-muted">{t('adminVacation.halfDayDeductionNotice', 'Hinweis: Für diesen halben Tag die entsprechenden Stunden für den halben Tag eintragen.')}</small>)}
                                </div>
                            )}
                            <div className="modal-buttons">
                                <button type="submit" className="button-confirm">{t('adminVacation.confirmButton', 'Urlaub erstellen')}</button>
                                <button type="button" onClick={() => {setShowVacationModal(false); resetVacationForm();}} className="button-cancel">{t('cancel', 'Abbrechen')}</button>
                            </div>
                        </form>
                    </div>
                </ModalOverlay>
            )}

            {showSickLeaveModal && (
                <ModalOverlay visible>
                    <div className="modal-content">
                        <h3>{t('adminSickLeave.modalTitle', 'Krankheit für Benutzer melden')}</h3>
                        <form onSubmit={(e) => { e.preventDefault(); handleReportSickLeave(); }}>
                            <div className="form-group">
                                <label htmlFor="sickLeaveUserSelect">{t('adminSickLeave.userSelection', 'Benutzer Auswahl')}:</label>
                                <select id="sickLeaveUserSelect" value={sickLeaveUser} onChange={handleSickLeaveUserChange} required>
                                    <option value="">{t('adminSickLeave.selectUserPlaceholder', 'Bitte Benutzer auswählen')}</option>
                                    {users.map((u) => (<option key={`sick-${u.id}`} value={u.username}>{u.firstName} {u.lastName} ({u.username})</option>))}
                                </select>
                            </div>
                            <div className="form-group">
                                <label htmlFor="sickLeaveStartDateInput">{t('adminSickLeave.startDateLabel', 'Startdatum')}:</label>
                                <input id="sickLeaveStartDateInput" type="date" value={sickLeaveStartDate} onChange={(e) => setSickLeaveStartDate(e.target.value)} required />
                            </div>
                            <div className="form-group">
                                <label htmlFor="sickLeaveEndDateInput">{t('adminSickLeave.endDateLabel', 'Enddatum')}:</label>
                                <input id="sickLeaveEndDateInput" type="date" value={sickLeaveEndDate} onChange={(e) => setSickLeaveEndDate(e.target.value)} required />
                            </div>
                            <div className="form-group form-group-checkbox">
                                <input type="checkbox" id="isSickLeaveHalfDayCheckbox" checked={isSickLeaveHalfDay} onChange={(e) => setIsSickLeaveHalfDay(e.target.checked)} />
                                <label htmlFor="isSickLeaveHalfDayCheckbox">{t('adminSickLeave.halfDayLabel', 'Halbtags krank')}</label>
                            </div>
                            <div className="form-group">
                                <label htmlFor="sickLeaveCommentInput">{t('comment', 'Kommentar (optional)')}:</label>
                                <textarea id="sickLeaveCommentInput" value={sickLeaveComment} onChange={e => setSickLeaveComment(e.target.value)} rows="3" placeholder={t('sickLeave.commentPlaceholder', 'Grund, Arztbesuch etc.')}></textarea>
                            </div>
                            <div className="modal-buttons">
                                <button type="submit" className="button-confirm">{t('adminSickLeave.reportButtonModal', 'Krankmeldung speichern')}</button>
                                <button type="button" onClick={() => {setShowSickLeaveModal(false); resetSickLeaveForm();}} className="button-cancel">{t('cancel', 'Abbrechen')}</button>
                            </div>
                        </form>
                    </div>
                </ModalOverlay>
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
    onReloadVacations: PropTypes.func,
    companyUsers: PropTypes.array
};

export default VacationCalendarAdmin;