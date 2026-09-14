import React, { forwardRef, useImperativeHandle, useState, useEffect, useCallback, useRef, useMemo } from 'react';
import ModalOverlay from './ModalOverlay';
import PropTypes from 'prop-types';
import Calendar from 'react-calendar';
import 'react-calendar/dist/Calendar.css';
import '../styles/VacationCalendarAdminScoped.css';
import api from '../utils/api';
import { useAuth } from '../context/AuthContext';
import { useNotification } from '../context/NotificationContext';
import { useTranslation } from '../context/LanguageContext';
import { formatLocalDate } from '../utils/dateUtils.js';
import { getUserDisplayName } from '../utils/userDisplay';



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

function parseDateString(dateString) {
    if (!dateString) return null;
    const [year, month, day] = dateString.split('-').map(Number);
    if (!year || !month || !day) {
        return null;
    }
    return new Date(year, month - 1, day);
}

const MAX_VISIBLE_DAY_MARKERS = 5;

const VacationCalendarAdmin = forwardRef(({ vacationRequests, onReloadVacations, companyUsers: initialCompanyUsers, focusUsername = null, visibleUsernames = null }, ref) => {
    const { t } = useTranslation();
    const { currentUser } = useAuth();
    const { notify } = useNotification();

    const tRef = useRef(t);
    const notifyRef = useRef(notify);

    useEffect(() => {
        tRef.current = t;
    }, [t]);

    useEffect(() => {
        notifyRef.current = notify;
    }, [notify]);

    const translate = useCallback((key, fallback) => tRef.current(key, fallback), []);

    const pushNotification = useCallback((message, type = 'info') => {
        if (!message) return;
        notifyRef.current({ message, type });
    }, []);

    const [showVacationModal, setShowVacationModal] = useState(false);
    const [newVacationUser, setNewVacationUser] = useState('');
    const [newVacationHalfDay, setNewVacationHalfDay] = useState(false);
    const [newVacationUsesOvertime, setNewVacationUsesOvertime] = useState(false);
    const [overtimeDeductionHours, setOvertimeDeductionHours] = useState('');
    const [vacationStartDate, setVacationStartDate] = useState('');
    const [vacationEndDate, setVacationEndDate] = useState('');
    const [isCompanyVacation, setIsCompanyVacation] = useState(false);
    const [editingVacation, setEditingVacation] = useState(null);
    const [pendingVacationPeriods, setPendingVacationPeriods] = useState([]);
    const [savedVacationPeriods, setSavedVacationPeriods] = useState([]);
    const [vacationSaveMessage, setVacationSaveMessage] = useState('');
    const [isSavingVacation, setIsSavingVacation] = useState(false);
    const savingVacationRef = useRef(false);
    const [vacationCalendarMonth, setVacationCalendarMonth] = useState(new Date());
    const [vacationPickerRevision, setVacationPickerRevision] = useState(0);

    const [showSickLeaveModal, setShowSickLeaveModal] = useState(false);
    const [sickLeaveUser, setSickLeaveUser] = useState('');
    const [sickLeaveStartDate, setSickLeaveStartDate] = useState('');
    const [sickLeaveEndDate, setSickLeaveEndDate] = useState('');
    const [isSickLeaveHalfDay, setIsSickLeaveHalfDay] = useState(false);
    const [sickLeaveComment, setSickLeaveComment] = useState('');
    const [allSickLeaves, setAllSickLeaves] = useState([]);
    const [editingSickLeave, setEditingSickLeave] = useState(null);
    const [absenceToDelete, setAbsenceToDelete] = useState(null);
    const [isDeletingAbsence, setIsDeletingAbsence] = useState(false);

    const [users, setUsers] = useState(initialCompanyUsers || []);
    const [holidays, setHolidays] = useState({});
    const loadedHolidayKeysRef = useRef(new Set());
    const [activeStartDate, setActiveStartDate] = useState(new Date());
    const [currentCantonForHolidays, setCurrentCantonForHolidays] = useState(null);
    const [currentCompanyIdForHolidays, setCurrentCompanyIdForHolidays] = useState(null);
    const [selectedDayDetails, setSelectedDayDetails] = useState(null);
    // const [selectedUserForSickLeaveDetails, setSelectedUserForSickLeaveDetails] = useState(null); // Entfernt, da currentCantonForHolidays ausreicht

    const selectedUserDetailsForVacation = users.find(u => u.username === newVacationUser);
    const visibleUsernameSet = useMemo(() => visibleUsernames ? new Set(visibleUsernames) : null, [visibleUsernames]);

    const scopedUsers = useMemo(() => {
        return users.filter(user => (!focusUsername || user?.username === focusUsername)
            && (!visibleUsernameSet || visibleUsernameSet.has(user?.username)));
    }, [focusUsername, users, visibleUsernameSet]);

    const scopedVacationRequests = useMemo(() => {
        const confirmedIds = new Set(vacationRequests.map((vacation) => vacation.id));
        const requests = [...vacationRequests, ...savedVacationPeriods.filter((vacation) => !confirmedIds.has(vacation.id))];
        return requests.filter(vacation => (!focusUsername || vacation?.username === focusUsername)
            && (!visibleUsernameSet || visibleUsernameSet.has(vacation?.username)));
    }, [focusUsername, vacationRequests, savedVacationPeriods, visibleUsernameSet]);

    useEffect(() => {
        const confirmedIds = new Set(vacationRequests.map((vacation) => vacation.id));
        setSavedVacationPeriods((previous) => previous.filter((vacation) => !confirmedIds.has(vacation.id)));
    }, [vacationRequests]);

    const calendarVacationRequests = useMemo(
        () => scopedVacationRequests.filter((vacation) => !vacation?.denied),
        [scopedVacationRequests],
    );

    const scopedSickLeaves = useMemo(() => {
        return allSickLeaves.filter(sickLeave => (!focusUsername || sickLeave?.username === focusUsername)
            && (!visibleUsernameSet || visibleUsernameSet.has(sickLeave?.username)));
    }, [focusUsername, allSickLeaves, visibleUsernameSet]);

    useEffect(() => {
        if (!focusUsername) return;
        setNewVacationUser(focusUsername);
        setSickLeaveUser(focusUsername);
    }, [focusUsername]);

    const fetchAllUsers = useCallback(async () => {
        if (initialCompanyUsers && initialCompanyUsers.length > 0) {
            setUsers(initialCompanyUsers);
            return;
        }

        try {
            const res = await api.get('/api/admin/users');
            const fetchedUsers = Array.isArray(res.data) ? res.data : [];
            setUsers(fetchedUsers);
        } catch (err) {
            console.error('Error fetching users:', err);
            pushNotification(translate('errors.fetchUsersError', 'Fehler beim Laden der Benutzer.'), 'error');
            setUsers([]);
        }
    }, [initialCompanyUsers, pushNotification, translate]);

    useEffect(() => {
        fetchAllUsers();
    }, [fetchAllUsers]);

    useEffect(() => {
        let cantonToUse = null;
        let companyIdToUse = null;
        if (newVacationUser && users.length > 0) {
            const userDetails = users.find(u => u.username === newVacationUser);
            cantonToUse = userDetails?.company?.cantonAbbreviation || null;
            companyIdToUse = userDetails?.companyId || userDetails?.company?.id || null;
        } else if (sickLeaveUser && users.length > 0) {
            const userDetails = users.find(u => u.username === sickLeaveUser);
            cantonToUse = userDetails?.company?.cantonAbbreviation || null;
            companyIdToUse = userDetails?.companyId || userDetails?.company?.id || null;
        } else if (currentUser?.company?.cantonAbbreviation) {
            cantonToUse = currentUser.company.cantonAbbreviation;
            companyIdToUse = currentUser?.companyId || currentUser?.company?.id || null;
        } else if (users.length > 0 && users[0]?.company?.cantonAbbreviation) {
            cantonToUse = users[0].company.cantonAbbreviation;
            companyIdToUse = users[0]?.companyId || users[0]?.company?.id || null;
        }
        setCurrentCantonForHolidays(cantonToUse);
        setCurrentCompanyIdForHolidays(companyIdToUse);
    }, [newVacationUser, sickLeaveUser, users, currentUser]);

    const fetchHolidays = useCallback(async (year, canton, companyId) => {
        const key = `${year}-${companyId || canton || 'ALL'}`;
        if (loadedHolidayKeysRef.current.has(key)) {
            return;
        }
        try {
            const yearStartDate = `${year}-01-01`;
            const yearEndDate = `${year}-12-31`;
            const params = { year, cantonAbbreviation: canton || '', companyId: companyId || undefined, startDate: yearStartDate, endDate: yearEndDate };
            const response = await api.get('/api/holidays/details', { params });
            setHolidays(prevHolidays => ({ ...prevHolidays, ...response.data }));
            loadedHolidayKeysRef.current.add(key);
        } catch (error) {
            console.error(translate('errors.fetchHolidaysError', 'Fehler beim Laden der Feiertage:'), error);
        }
    }, [translate]);

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
            console.error(translate('errors.fetchSickLeaveErrorAdmin', 'Fehler beim Laden der Krankmeldungen (Admin):'), error);
            pushNotification(translate('errors.fetchSickLeaveErrorAdmin', 'Fehler beim Laden der Krankmeldungen (Admin).'), 'error');
        }
    }, [currentUser, pushNotification, translate]);

    useEffect(() => {
        const year = activeStartDate.getFullYear();
        const cantonToLoad = currentCantonForHolidays;
        const companyIdToLoad = currentCompanyIdForHolidays;

        fetchHolidays(year, cantonToLoad, companyIdToLoad);
    }, [activeStartDate, currentCantonForHolidays, currentCompanyIdForHolidays, fetchHolidays]);

    useEffect(() => {
        fetchAllSickLeaves();
    }, [fetchAllSickLeaves]);


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
            const compDateStr = formatLocalDate(start);

            // Vergleiche die Strings.
            return compDateStr >= itemStartStr && compDateStr <= itemEndStr;

        } catch (e) {
            console.error("Error in itemInRange:", item, start, end, e);
            return false;
        }
    }
    const resetVacationForm = useCallback(() => {
        setEditingVacation(null);
        setNewVacationUser(focusUsername || '');
        setVacationStartDate('');
        setVacationEndDate('');
        setNewVacationHalfDay(false);
        setNewVacationUsesOvertime(false);
        setOvertimeDeductionHours('');
        setIsCompanyVacation(false);
        setPendingVacationPeriods([]);
        setVacationSaveMessage('');
    }, [focusUsername]);

    const resetSickLeaveForm = useCallback(() => {
        setEditingSickLeave(null);
        setSickLeaveUser(focusUsername || '');
        setSickLeaveStartDate('');
        setSickLeaveEndDate('');
        setIsSickLeaveHalfDay(false);
        setSickLeaveComment('');
    }, [focusUsername]);

    const handleCloseVacationModal = useCallback(() => {
        if (savingVacationRef.current) return;
        setShowVacationModal(false);
        resetVacationForm();
    }, [resetVacationForm]);

    const handleCloseSickLeaveModal = useCallback(() => {
        setShowSickLeaveModal(false);
        resetSickLeaveForm();
    }, [resetSickLeaveForm]);

    function getVacationDraft() {
        if (!vacationStartDate || !vacationEndDate) {
            pushNotification(translate('adminVacation.datesMissing', 'Bitte Start- und Enddatum angeben'), 'warning');
            return null;
        }
        if (vacationEndDate < vacationStartDate) {
            pushNotification(translate('adminVacation.endDateBeforeStart', 'Das Enddatum darf nicht vor dem Startdatum liegen.'), 'error');
            return null;
        }
        if (newVacationHalfDay && vacationStartDate !== vacationEndDate) {
            pushNotification(translate('adminVacation.halfDayOneDay', 'Halbtags Urlaub ist nur für einen einzelnen Tag möglich.'), 'error');
            return null;
        }
        const usesOvertime = !isCompanyVacation && newVacationUsesOvertime;
        const period = { startDate: vacationStartDate, endDate: vacationEndDate, halfDay: newVacationHalfDay, usesOvertime };
        if (usesOvertime && selectedUserDetailsForVacation?.isPercentage) {
            const hours = Number(overtimeDeductionHours);
            if (!Number.isFinite(hours) || hours <= 0) {
                pushNotification(translate('adminVacation.invalidOvertimeHours', 'Bitte eine gültige positive Stundenzahl für den Überstundenabzug eingeben.'), 'error');
                return null;
            }
            period.overtimeDeductionMinutes = Math.round(hours * 60);
        }
        if (pendingVacationPeriods.some((entry) => period.startDate <= entry.endDate && period.endDate >= entry.startDate)) {
            pushNotification(translate('adminVacation.overlappingPeriods', 'Dieser Zeitraum überschneidet sich mit einem vorgemerkten Urlaub.'), 'warning');
            return null;
        }
        return period;
    }

    function clearVacationDraft() {
        setVacationPickerRevision((revision) => revision + 1);
        setVacationStartDate('');
        setVacationEndDate('');
        setNewVacationHalfDay(false);
        setOvertimeDeductionHours('');
    }

    function addVacationPeriod() {
        if (savingVacationRef.current) return;
        const period = getVacationDraft();
        if (!period) return;
        setPendingVacationPeriods((previous) => [...previous, period]);
        clearVacationDraft();
        setVacationSaveMessage('');
    }

    async function handleCreateVacation() {
        if (savingVacationRef.current) return;
        if (!currentUser || !currentUser.username) {
            pushNotification(translate("errors.notLoggedIn", "Nicht eingeloggt oder Benutzername fehlt."), 'error');
            return;
        }
        if (!isCompanyVacation && !newVacationUser) {
            pushNotification(translate("adminVacation.noUserSelected", "Bitte einen Benutzer auswählen"), 'warning');
            return;
        }

        const periods = [...pendingVacationPeriods];
        if (vacationStartDate || vacationEndDate || periods.length === 0) {
            const draft = getVacationDraft();
            if (!draft) return;
            periods.push(draft);
        }
        savingVacationRef.current = true;
        setIsSavingVacation(true);
        setVacationSaveMessage('');
        let savedCount = 0;
        try {
            // Each successful period is removed from the queue, so retrying a failure cannot duplicate it.
            for (const period of periods) {
                const params = { ...period, adminUsername: currentUser.username };
                if (!isCompanyVacation) params.username = focusUsername || newVacationUser;
                const response = await api.post(isCompanyVacation ? '/api/vacation/companyCreate' : '/api/vacation/adminCreate', null, { params });
                const created = Array.isArray(response?.data) ? response.data : [response?.data];
                setSavedVacationPeriods((previous) => [...previous, ...created.filter((vacation) => vacation?.id && vacation?.startDate)]);
                savedCount += 1;
                setPendingVacationPeriods(periods.slice(savedCount));
                clearVacationDraft();
            }
            setVacationSaveMessage(t('adminVacation.periodsSaved', '{{count}} Zeitraum/Zeiträume gespeichert. Du kannst direkt weitere Urlaube eintragen.', { count: savedCount }));
            pushNotification(translate("adminVacation.createdSuccess", "Urlaub erfolgreich erstellt und direkt genehmigt"), 'success');
        } catch (err) {
            setPendingVacationPeriods(periods.slice(savedCount));
            clearVacationDraft();
            console.error('Error creating vacation (adminCreate)', err);
            const errorMsg = err.response?.data?.message || err.response?.data || err.message || t('errors.unknownError');
            setVacationSaveMessage(t('adminVacation.periodsPartiallySaved', '{{count}} gespeichert. Die übrigen Zeiträume bleiben zum erneuten Speichern vorgemerkt.', { count: savedCount }));
            pushNotification(`${translate("adminVacation.createError", "Fehler beim Anlegen des Urlaubs")}: ${errorMsg}`, 'error');
        } finally {
            if (savedCount > 0 && onReloadVacations) {
                try {
                    await onReloadVacations();
                } catch {
                    pushNotification(translate('adminVacation.refreshError', 'Urlaub gespeichert. Die Übersicht konnte noch nicht aktualisiert werden.'), 'warning');
                }
            }
            savingVacationRef.current = false;
            setIsSavingVacation(false);
        }
    }

    async function handleUpdateVacation() {
        if (!editingVacation) {
            return;
        }
        if (!currentUser || !currentUser.username) {
            pushNotification(translate('errors.notLoggedIn', 'Nicht eingeloggt oder Benutzername fehlt.'), 'error');
            return;
        }
        if (!vacationStartDate || !vacationEndDate) {
            pushNotification(translate('adminVacation.datesMissing', 'Bitte Start- und Enddatum angeben'), 'warning');
            return;
        }
        if (new Date(vacationEndDate) < new Date(vacationStartDate)) {
            pushNotification(translate('adminVacation.endDateBeforeStart', 'Das Enddatum darf nicht vor dem Startdatum liegen.'), 'error');
            return;
        }
        if (newVacationHalfDay && vacationStartDate !== vacationEndDate) {
            pushNotification(translate('adminVacation.halfDayOneDay', 'Halbtags Urlaub ist nur für einen einzelnen Tag möglich.'), 'error');
            return;
        }

        const usernameForLookup = newVacationUser || editingVacation.username || '';
        const targetUserDetails = users.find(u => u.username === usernameForLookup);
        const usesOvertimeEffective = !isCompanyVacation && newVacationUsesOvertime;

        let overtimeDeductionMinutes = null;
        if (usesOvertimeEffective && targetUserDetails?.isPercentage) {
            const hours = parseFloat(overtimeDeductionHours);
            if (isNaN(hours) || hours <= 0) {
                pushNotification(translate('adminVacation.invalidOvertimeHours', 'Bitte eine gültige positive Stundenzahl für den Überstundenabzug eingeben.'), 'error');
                return;
            }
            overtimeDeductionMinutes = Math.round(hours * 60);
        }

        const payload = {
            startDate: vacationStartDate,
            endDate: vacationEndDate,
            halfDay: newVacationHalfDay,
            usesOvertime: usesOvertimeEffective,
            approved: editingVacation?.approved ?? false,
            denied: editingVacation?.denied ?? false,
        };
        if (overtimeDeductionMinutes !== null) {
            payload.overtimeDeductionMinutes = overtimeDeductionMinutes;
        }

        try {
            const response = await api.put(`/api/vacation/${editingVacation.id}`, payload);
            setSavedVacationPeriods((previous) => previous.map((period) => period.id === editingVacation.id
                ? { ...period, ...payload, ...response?.data }
                : period));
            pushNotification(translate('adminVacation.updateSuccess', 'Urlaubseintrag wurde aktualisiert.'), 'success');
            setShowVacationModal(false);
            resetVacationForm();
            if (onReloadVacations) {
                try {
                    await onReloadVacations();
                } catch {
                    pushNotification(translate('adminVacation.refreshError', 'Änderung gespeichert. Die Übersicht konnte noch nicht aktualisiert werden.'), 'warning');
                }
            }
        } catch (err) {
            console.error('Error updating vacation (admin)', err);
            const errorMsg = err.response?.data?.message || err.response?.data || err.message || t('errors.unknownError');
            pushNotification(`${translate('adminVacation.updateError', 'Fehler beim Aktualisieren des Urlaubs')}: ${errorMsg}`, 'error');
        }
    }

    const handleSubmitVacation = async (event) => {
        event.preventDefault();
        if (editingVacation) {
            await handleUpdateVacation();
        } else {
            await handleCreateVacation();
        }
    };

    async function handleCreateSickLeave() {
        // ... (Logik bleibt gleich)
        if (!currentUser?.username) {
            pushNotification(translate("errors.notLoggedIn", "Admin nicht eingeloggt."), 'error');
            return;
        }
        if (!sickLeaveUser) {
            pushNotification(translate("adminSickLeave.noUserSelected", "Bitte einen Benutzer für die Krankmeldung auswählen."), 'warning');
            return;
        }
        if (!sickLeaveStartDate || !sickLeaveEndDate) {
            pushNotification(translate("adminSickLeave.datesMissing", "Bitte Start- und Enddatum für die Krankmeldung angeben."), 'warning');
            return;
        }
        if (new Date(sickLeaveEndDate) < new Date(sickLeaveStartDate)) {
            pushNotification(translate("adminSickLeave.endDateBeforeStart", "Das Enddatum der Krankheit darf nicht vor dem Startdatum liegen."), 'error');
            return;
        }
        if (isSickLeaveHalfDay && sickLeaveStartDate !== sickLeaveEndDate) {
            pushNotification(translate('sickLeave.halfDayOneDay', 'Halbtägige Krankmeldung nur für einen einzelnen Tag.'), 'error');
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
            pushNotification(translate("adminSickLeave.reportSuccess", "Krankmeldung erfolgreich für Benutzer eingetragen."), 'success');
            setShowSickLeaveModal(false);
            resetSickLeaveForm();
            fetchAllSickLeaves();
            if (onReloadVacations) onReloadVacations();
        } catch (err) {
            console.error('Error reporting sick leave (Admin):', err);
            const errorMsg = err.response?.data?.message || err.message || t('errors.unknownError');
            pushNotification(`${translate("adminSickLeave.reportError", "Fehler beim Eintragen der Krankmeldung:")} ${errorMsg}`, 'error');
        }
    }

    async function handleUpdateSickLeave() {
        if (!editingSickLeave) {
            return;
        }
        if (!sickLeaveStartDate || !sickLeaveEndDate) {
            pushNotification(translate('adminSickLeave.datesMissing', 'Bitte Start- und Enddatum für die Krankmeldung angeben.'), 'warning');
            return;
        }
        if (new Date(sickLeaveEndDate) < new Date(sickLeaveStartDate)) {
            pushNotification(translate('adminSickLeave.endDateBeforeStart', 'Das Enddatum der Krankheit darf nicht vor dem Startdatum liegen.'), 'error');
            return;
        }
        if (isSickLeaveHalfDay && sickLeaveStartDate !== sickLeaveEndDate) {
            pushNotification(translate('sickLeave.halfDayOneDay', 'Halbtägige Krankmeldung nur für einen einzelnen Tag.'), 'error');
            return;
        }

        const payload = {
            startDate: sickLeaveStartDate,
            endDate: sickLeaveEndDate,
            halfDay: isSickLeaveHalfDay,
            comment: sickLeaveComment,
        };

        try {
            await api.put(`/api/sick-leave/${editingSickLeave.id}`, payload);
            pushNotification(translate('adminSickLeave.updateSuccess', 'Krankmeldung wurde aktualisiert.'), 'success');
            setShowSickLeaveModal(false);
            resetSickLeaveForm();
            fetchAllSickLeaves();
            if (onReloadVacations) onReloadVacations();
        } catch (err) {
            console.error('Error updating sick leave (Admin):', err);
            const errorMsg = err.response?.data?.message || err.message || t('errors.unknownError');
            pushNotification(`${translate('adminSickLeave.updateError', 'Fehler beim Aktualisieren der Krankmeldung:')} ${errorMsg}`, 'error');
        }
    }

    const handleSubmitSickLeave = async (event) => {
        event.preventDefault();
        if (editingSickLeave) {
            await handleUpdateSickLeave();
        } else {
            await handleCreateSickLeave();
        }
    };

    const openVacationEditModal = (vacation) => {
        if (!vacation) return;
        resetVacationForm();
        setVacationCalendarMonth(startOfMonth(parseDateString(vacation.startDate) || activeStartDate));
        setEditingVacation(vacation);
        setIsCompanyVacation(Boolean(vacation.companyVacation));
        const username = vacation.username || '';
        setNewVacationUser(username);
        setVacationStartDate(vacation.startDate || '');
        setVacationEndDate(vacation.endDate || '');
        setNewVacationHalfDay(Boolean(vacation.halfDay));
        const usesOvertimeValue = Boolean(vacation.usesOvertime) && !vacation.companyVacation;
        setNewVacationUsesOvertime(usesOvertimeValue);
        if (usesOvertimeValue && vacation.overtimeDeductionMinutes) {
            const hours = vacation.overtimeDeductionMinutes / 60;
            const formatted = Number.isInteger(hours) ? String(hours) : hours.toFixed(2);
            setOvertimeDeductionHours(formatted);
        } else {
            setOvertimeDeductionHours('');
        }
        setShowVacationModal(true);
    };

    const openSickLeaveEditModal = (sickLeave) => {
        if (!sickLeave) return;
        setEditingSickLeave(sickLeave);
        setSickLeaveUser(sickLeave.username || '');
        setSickLeaveStartDate(sickLeave.startDate || '');
        setSickLeaveEndDate(sickLeave.endDate || '');
        setIsSickLeaveHalfDay(Boolean(sickLeave.halfDay));
        setSickLeaveComment(sickLeave.comment || '');
        setShowSickLeaveModal(true);
    };

    const formatUserShortName = (username) => {
        const user = users.find(u => u.username === username);
        return user?.firstName || getUserDisplayName(username, users, t('adminVacation.unknownUser', 'Unbekannt'));
    };

    const formatUserLongName = (username) => {
        return getUserDisplayName(username, users, t('adminVacation.unknownUser', 'Unbekannt'));
    };

    const formatDateRange = (entry) => {
        if (!entry?.startDate || !entry?.endDate) return '';
        if (entry.startDate === entry.endDate) return entry.startDate;
        return `${entry.startDate} - ${entry.endDate}`;
    };

    const getDayDetails = (date) => {
        const dateString = formatLocalDate(date);
        const vacationsToday = calendarVacationRequests.filter((vac) => itemInRange(vac, date, date));
        const sickToday = scopedSickLeaves.filter((sickLeave) => itemInRange(sickLeave, date, date));
        const vacationToday = vacationsToday.filter((vacation) => !vacation?.usesOvertime);
        const overtimeToday = vacationsToday.filter((vacation) => vacation?.usesOvertime);

        return {
            date,
            dateString,
            holidayName: holidays[dateString],
            vacationToday,
            sickToday,
            overtimeToday,
            totalAbsences: vacationsToday.length + sickToday.length,
        };
    };

    const openDayDetails = (date) => {
        setSelectedDayDetails(getDayDetails(date));
    };

    const openVacationFromDetails = (vacation) => {
        setSelectedDayDetails(null);
        openVacationEditModal(vacation);
    };

    const openSickLeaveFromDetails = (sickLeave) => {
        setSelectedDayDetails(null);
        openSickLeaveEditModal(sickLeave);
    };

    const openDeleteAbsenceConfirmation = (type, item) => {
        if (!item?.id) {
            pushNotification(translate('adminCalendar.deleteMissingEntry', 'Kein Eintrag zum Loeschen gefunden.'), 'error');
            return;
        }
        setAbsenceToDelete({
            type: type === 'sick' ? 'sick' : 'vacation',
            item,
        });
    };

    const closeDeleteAbsenceConfirmation = () => {
        if (isDeletingAbsence) return;
        setAbsenceToDelete(null);
    };

    const getAbsenceTypeLabel = (target = absenceToDelete) => {
        if (!target) return t('adminCalendar.absence', 'Abwesenheit');
        if (target.type === 'sick') return t('adminSickLeave.shortTitle', 'Krank');
        if (target.item?.usesOvertime) return t('overtimeVacation', 'Ueberstundenfrei');
        return t('vacation.normalVacation', 'Urlaub');
    };

    const handleDeleteAbsence = async () => {
        if (!absenceToDelete?.item?.id) {
            pushNotification(translate('adminCalendar.deleteMissingEntry', 'Kein Eintrag zum Loeschen gefunden.'), 'error');
            return;
        }
        if (!currentUser?.username) {
            pushNotification(translate('errors.notLoggedIn', 'Admin nicht eingeloggt oder Benutzername fehlt.'), 'error');
            return;
        }

        const deleteType = absenceToDelete.type === 'sick' ? 'sick' : 'vacation';
        setIsDeletingAbsence(true);
        try {
            if (deleteType === 'sick') {
                await api.delete(`/api/sick-leave/${absenceToDelete.item.id}`);
            } else {
                await api.delete(`/api/vacation/${absenceToDelete.item.id}`, {
                    params: { adminUsername: currentUser.username },
                });
                setSavedVacationPeriods((previous) => previous.filter((period) => period.id !== absenceToDelete.item.id));
            }

            pushNotification(
                deleteType === 'sick'
                    ? translate('adminSickLeave.deleteSuccess', 'Krankmeldung wurde geloescht.')
                    : translate('adminVacation.delete.success', 'Urlaubseintrag wurde geloescht.'),
                'success',
            );
            setAbsenceToDelete(null);
            setSelectedDayDetails(null);
            setShowVacationModal(false);
            setShowSickLeaveModal(false);
            resetVacationForm();
            resetSickLeaveForm();
            await fetchAllSickLeaves();
            if (onReloadVacations) {
                try {
                    await onReloadVacations();
                } catch {
                    pushNotification(translate('adminVacation.refreshError', 'Änderung gespeichert. Die Übersicht konnte noch nicht aktualisiert werden.'), 'warning');
                }
            }
        } catch (err) {
            console.error('Error deleting admin calendar absence:', err);
            const errorMsg = err.response?.data?.message || err.response?.data || err.message || t('errors.unknownError', 'Unbekannter Fehler');
            const errorPrefix = deleteType === 'sick'
                ? translate('adminSickLeave.deleteError', 'Fehler beim Loeschen der Krankmeldung:')
                : translate('adminVacation.delete.error', 'Fehler beim Loeschen des Urlaubs:');
            pushNotification(`${errorPrefix} ${errorMsg}`, 'error');
        } finally {
            setIsDeletingAbsence(false);
        }
    };

    const renderDayDetailGroup = (title, items, type, emptyText) => (
        <section className={`calendar-day-detail-group ${type}`}>
            <div className="calendar-day-detail-group-title">
                <span>{title}</span>
                <strong>{items.length}</strong>
            </div>
            {items.length > 0 ? (
                <div className="calendar-day-detail-list">
                    {items.map((item, index) => {
                        const key = `${type}-${item.id || item.username || index}`;
                        const isSick = type === 'sick';
                        return (
                            <div key={key} className="calendar-day-detail-item">
                                <button
                                    type="button"
                                    className="calendar-day-detail-main"
                                    onClick={() => isSick ? openSickLeaveFromDetails(item) : openVacationFromDetails(item)}
                                >
                                    <span className="calendar-day-detail-person">{formatUserLongName(item.username)}</span>
                                    <span className="calendar-day-detail-meta">
                                        {formatDateRange(item)}
                                        {item.halfDay ? ` · ${t('adminVacation.halfDayShort', '½ Tag')}` : ''}
                                        {item.companyVacation ? ` · ${t('adminVacation.companyVacationLabel', 'Betriebsurlaub')}` : ''}
                                        {isSick && item.comment ? ` · ${item.comment}` : ''}
                                    </span>
                                </button>
                                <button
                                    type="button"
                                    className="calendar-day-detail-delete"
                                    onClick={() => openDeleteAbsenceConfirmation(isSick ? 'sick' : 'vacation', item)}
                                    title={t('adminCalendar.deleteAbsenceButton', 'Eintrag loeschen')}
                                    aria-label={`${t('adminCalendar.deleteAbsenceButton', 'Eintrag loeschen')}: ${formatUserLongName(item.username)} ${formatDateRange(item)}`}
                                >
                                    {t('delete', 'Loeschen')}
                                </button>
                            </div>
                        );
                    })}
                </div>
            ) : (
                <p className="calendar-day-detail-empty">{emptyText}</p>
            )}
        </section>
    );

    function tileContent({ date, view }) {
        if (view === 'month') {
            const dayDetails = getDayDetails(date);
            const dayEntries = [];

            if (dayDetails.holidayName) {
                dayEntries.push({
                    key: `holiday-${dayDetails.dateString}`,
                    type: 'holiday',
                    label: dayDetails.holidayName,
                    title: `${t('holiday', 'Feiertag')}: ${dayDetails.holidayName}`,
                });
            }

            dayDetails.vacationToday.forEach((vac, index) => {
                const isPendingVacation = !vac?.approved && !vac?.denied;
                const bgColor = isPendingVacation ? '#767676' : (vac.color || '#22c55e');
                const shortName = formatUserShortName(vac.username);
                dayEntries.push({
                    key: `vac-${vac.id || `${dayDetails.dateString}-${index}`}`,
                    type: 'vacation',
                    item: vac,
                    bgColor,
                    textColor: getContrastYIQ(bgColor),
                    className: `vacation-marker editable${isPendingVacation ? ' pending' : ''}`,
                    label: `${shortName}${vac.halfDay ? ` · ${t('adminVacation.halfDayShort', '½')}` : ''}`,
                    title: `${formatUserLongName(vac.username)} · ${t('vacation.normalVacation', 'Urlaub')}`,
                    isStart: dayDetails.dateString === vac.startDate,
                });
            });

            dayDetails.overtimeToday.forEach((vac, index) => {
                const shortName = formatUserShortName(vac.username);
                dayEntries.push({
                    key: `overtime-${vac.id || `${dayDetails.dateString}-${index}`}`,
                    type: 'overtime',
                    item: vac,
                    bgColor: '#2563eb',
                    textColor: '#ffffff',
                    className: 'vacation-marker overtime-marker editable',
                    label: `${shortName} · ${t('overtimeVacation', 'Überstundenfrei')}`,
                    title: `${formatUserLongName(vac.username)} · ${t('overtimeVacation', 'Überstundenfrei')}`,
                    isStart: dayDetails.dateString === vac.startDate,
                });
            });

            dayDetails.sickToday.forEach((sick, index) => {
                const sickColor = sick.color || '#ef4444';
                const shortName = formatUserShortName(sick.username);
                dayEntries.push({
                    key: `sick-${sick.id || `${dayDetails.dateString}-${index}`}`,
                    type: 'sick',
                    item: sick,
                    bgColor: sickColor,
                    textColor: getContrastYIQ(sickColor),
                    className: 'sick-leave-marker-admin editable',
                    label: `${shortName} · ${t('sickLeave.sickShort', 'Krank')}`,
                    title: `${formatUserLongName(sick.username)}: ${sick.halfDay ? t('sickLeave.halfDay', 'Halbtags krank') : t('sickLeave.fullDay', 'Ganztags krank')}${sick.comment ? ` (${sick.comment})` : ''}`,
                    isStart: dayDetails.dateString === sick.startDate,
                });
            });

            if (dayEntries.length === 0) return null;

            const visibleEntries = dayEntries.slice(0, MAX_VISIBLE_DAY_MARKERS);
            const hiddenCount = dayEntries.length - visibleEntries.length;

            return (
                <div className="vacation-markers" data-entry-count={dayEntries.length}>
                    <span className="aw-calendar-count" style={{ display: 'none' }}>{dayEntries.length} {dayEntries.length === 1 ? t('adminCalendar.entry', 'Eintrag') : t('adminCalendar.entries', 'Einträge')}</span>
                    {visibleEntries.map((entry) => {
                        if (entry.type === 'holiday') {
                            return (
                                <div
                                    key={entry.key}
                                    className="holiday-marker-admin"
                                    title={entry.title}
                                    onClick={(event) => { event.stopPropagation(); openDayDetails(date); }}
                                >
                                    <span aria-hidden="true">Feiertag</span>
                                    <strong>{entry.label}</strong>
                                </div>
                            );
                        }

                        const markerAccessibilityProps = entry.isStart
                            ? {
                                role: 'button',
                                tabIndex: 0,
                                'aria-label': `${entry.title} ${formatDateRange(entry.item)}`,
                            }
                            : {
                                tabIndex: -1,
                                'aria-hidden': true,
                            };
                        const handleEntryOpen = entry.type === 'sick' ? openSickLeaveEditModal : openVacationEditModal;

                        return (
                            <div
                                key={entry.key}
                                className={entry.className}
                                style={{ backgroundColor: entry.bgColor, color: entry.textColor, '--aw-marker-color': entry.bgColor }}
                                title={entry.title}
                                {...markerAccessibilityProps}
                                onClick={(event) => { event.stopPropagation(); handleEntryOpen(entry.item); }}
                                onKeyDown={(event) => {
                                    if (!entry.isStart) return;
                                    if (event.key === 'Enter' || event.key === ' ') {
                                        event.preventDefault();
                                        handleEntryOpen(entry.item);
                                    }
                                }}
                            >
                                <span aria-hidden="true">{entry.label}</span>
                            </div>
                        );
                    })}
                    {hiddenCount > 0 && (
                        <div
                            className="calendar-more-marker"
                            role="button"
                            tabIndex={0}
                            title={t('adminCalendar.moreEntriesTitle', 'Alle Einträge dieses Tages anzeigen')}
                            onClick={(event) => { event.stopPropagation(); openDayDetails(date); }}
                            onKeyDown={(event) => {
                                if (event.key === 'Enter' || event.key === ' ') {
                                    event.preventDefault();
                                    openDayDetails(date);
                                }
                            }}
                        >
                            +{hiddenCount} {t('adminCalendar.moreEntries', 'weitere')}
                        </div>
                    )}
                </div>
            );
        }
        return null;
    }

    const onActiveStartDateChange = ({ activeStartDate: newActiveStartDate }) => {
        if (!newActiveStartDate) return;
        setActiveStartDate(startOfMonth(newActiveStartDate));
    };

    const handleVacationUserChange = (e) => {
        const selectedUsername = e.target.value;
        setNewVacationUser(selectedUsername);
        const userDetails = scopedUsers.find(u => u.username === selectedUsername);
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

    const openVacationModalAndReset = (dateClicked = null, username = null) => {
        resetVacationForm();
        if (!focusUsername && scopedUsers.some(user => user.username === username)) setNewVacationUser(username);
        setVacationCalendarMonth(startOfMonth(dateClicked || activeStartDate));
        if (dateClicked) {
            const dateStr = formatLocalDate(dateClicked);
            setVacationStartDate(dateStr);
            setVacationEndDate(dateStr);
        }
        setShowVacationModal(true);
    };

    const openSickLeaveModalAndReset = (dateClicked = null) => {
        resetSickLeaveForm();
        if (dateClicked) {
            const dateStr = formatLocalDate(dateClicked);
            setSickLeaveStartDate(dateStr);
            setSickLeaveEndDate(dateStr);
        }
        setShowSickLeaveModal(true);
    };

    const handleCalendarDayClick = (value) => {
        const dayDetails = getDayDetails(value);
        if (dayDetails.totalAbsences > 0 || dayDetails.holidayName) {
            setSelectedDayDetails(dayDetails);
            return;
        }
        openVacationModalAndReset(value);
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



    const goToPrevMonth = () => {
        setActiveStartDate(prev => startOfMonth(addMonths(prev || new Date(), -1)));
    };
    const goToNextMonth = () => {
        setActiveStartDate(prev => startOfMonth(addMonths(prev || new Date(),  +1)));
    };

    useImperativeHandle(ref, () => ({
        createVacation: options => openVacationModalAndReset(null, options?.username),
        createSickLeave: () => openSickLeaveModalAndReset(),
        openAbsence: (absence) => {
            const dateValue = absence?.startDate || absence?.dateIso || absence?.raw?.startDate || absence?.item?.startDate;
            const date = parseDateString(dateValue?.slice(0, 10));
            if (!date || Number.isNaN(date.getTime())) return;
            setActiveStartDate(startOfMonth(date));
            setSelectedDayDetails(getDayDetails(date));
        },
    }));

    return (
        <div className="vacation-calendar-admin scoped-vacation">
            <div className="calendar-header">
                <h2>{t('adminCalendar.title', 'Admin Kalenderübersicht')}</h2>
                <div className="month-navigation">
                    <button
                        type="button"
                        onClick={goToPrevMonth}
                        aria-label={t('prevMonth', 'Vorheriger Monat')}
                        title={t('prevMonth', 'Vorheriger Monat')}
                    >
                        ‹
                    </button>
                    <span>{formatMonthYear(activeStartDate || new Date(), t('calendarLocale', 'de-DE'))}</span>
                    <button
                        type="button"
                        onClick={goToNextMonth}
                        aria-label={t('nextMonth', 'Nächster Monat')}
                        title={t('nextMonth', 'Nächster Monat')}
                    >
                        ›
                    </button>
                </div>
            </div>

            <Calendar
                className="calendar-lg"
                value={activeStartDate}
                activeStartDate={activeStartDate}
                tileContent={tileContent}
                onActiveStartDateChange={onActiveStartDateChange}
                onClickDay={handleCalendarDayClick}
                locale={t('calendarLocale', 'de-DE')}
                showNavigation={false}
            />
            <div className="admin-calendar-actions">
                <button onClick={() => openVacationModalAndReset()} className="create-vacation-button">
                    {t('adminVacation.createVacationButton', 'Urlaub manuell erstellen')}
                </button>
                <button onClick={() => openSickLeaveModalAndReset()} className="report-sick-leave-button-admin">
                    {t('adminSickLeave.reportButton', 'Krankheit für User melden')}
                </button>
            </div>

            {selectedDayDetails && (
                <ModalOverlay visible onClose={() => setSelectedDayDetails(null)}>
                    <div className="modal-content calendar-day-details-modal">
                        <div className="calendar-day-details-head">
                            <span>{t('adminCalendar.dayDetailsLabel', 'Tagesübersicht')}</span>
                            <h3>
                                {selectedDayDetails.date.toLocaleDateString(t('calendarLocale', 'de-DE'), {
                                    weekday: 'long',
                                    day: '2-digit',
                                    month: 'long',
                                    year: 'numeric',
                                })}
                            </h3>
                            {selectedDayDetails.holidayName && (
                                <p>{t('holiday', 'Feiertag')}: {selectedDayDetails.holidayName}</p>
                            )}
                        </div>
                        <div className="calendar-day-details-summary">
                            <span>{selectedDayDetails.vacationToday.length} {t('vacation.normalVacation', 'Urlaub')}</span>
                            <span>{selectedDayDetails.sickToday.length} {t('adminSickLeave.shortTitle', 'Krank')}</span>
                            <span>{selectedDayDetails.overtimeToday.length} {t('overtimeVacation', 'Überstundenfrei')}</span>
                        </div>
                        <div className="calendar-day-details-grid">
                            {renderDayDetailGroup(
                                t('vacation.normalVacation', 'Urlaub'),
                                selectedDayDetails.vacationToday,
                                'vacation',
                                t('adminCalendar.noVacationToday', 'Niemand ist an diesem Tag im Urlaub.'),
                            )}
                            {renderDayDetailGroup(
                                t('adminSickLeave.shortTitle', 'Krank'),
                                selectedDayDetails.sickToday,
                                'sick',
                                t('adminCalendar.noSickToday', 'Keine Krankmeldungen an diesem Tag.'),
                            )}
                            {renderDayDetailGroup(
                                t('overtimeVacation', 'Überstundenfrei'),
                                selectedDayDetails.overtimeToday,
                                'overtime',
                                t('adminCalendar.noOvertimeToday', 'Niemand hat an diesem Tag Überstunden frei.'),
                            )}
                        </div>
                        <div className="modal-buttons">
                            <button
                                type="button"
                                className="button-confirm"
                                onClick={() => {
                                    const detailDate = selectedDayDetails.date;
                                    setSelectedDayDetails(null);
                                    openVacationModalAndReset(detailDate);
                                }}
                            >
                                {t('adminVacation.createVacationButton', 'Urlaub manuell erstellen')}
                            </button>
                            <button
                                type="button"
                                className="button-cancel"
                                onClick={() => setSelectedDayDetails(null)}
                            >
                                {t('close', 'Schließen')}
                            </button>
                        </div>
                    </div>
                </ModalOverlay>
            )}

            {showVacationModal && (
                <ModalOverlay visible onClose={handleCloseVacationModal} className="vacation-entry-overlay">
                    <div className="modal-content large-calendar-modal vacation-entry-modal" role="dialog" aria-modal="true" aria-labelledby="vacation-entry-title">
                        <h3 id="vacation-entry-title">{editingVacation ? t('adminVacation.editModalTitle', 'Urlaubseintrag bearbeiten') : t('adminVacation.modalTitle', 'Neuen Urlaub für Mitarbeiter anlegen')}</h3>
                        {!editingVacation && <p className="vacation-entry-hint">{t('adminVacation.multiPeriodHint', 'Zeitraum im Kalender wählen. Für Urlaub in weiteren Monaten zusätzliche Zeiträume vormerken und gemeinsam speichern.')}</p>}
                        {vacationSaveMessage && <p className="vacation-save-status" role="status">{vacationSaveMessage}</p>}
                        <form onSubmit={handleSubmitVacation}>
                            <fieldset disabled={isSavingVacation} className="vacation-entry-fields">
                            {(!focusUsername || editingVacation?.companyVacation) && (
                            <div className="form-group form-group-checkbox">
                                <input
                                    type="checkbox"
                                    id="companyVacationCheckbox"
                                    checked={isCompanyVacation}
                                    onChange={(e) => setIsCompanyVacation(e.target.checked)}
                                    disabled={Boolean(editingVacation) || pendingVacationPeriods.length > 0}
                                />
                                <label htmlFor="companyVacationCheckbox">{t('adminVacation.companyVacationLabel', 'Betriebsurlaub')}</label>
                            </div>
                            )}
                            {!isCompanyVacation && (
                                <div className="form-group">
                                    <label htmlFor="vacationUserSelect">{t('adminVacation.userSelection', 'Benutzer Auswahl')}:</label>
                                    <select
                                        id="vacationUserSelect"
                                        value={newVacationUser}
                                        onChange={handleVacationUserChange}
                                        required
                                        disabled={Boolean(editingVacation) || Boolean(focusUsername) || pendingVacationPeriods.length > 0}
                                    >
                                        <option value="">{t('adminVacation.selectUserPlaceholder', 'Bitte Benutzer auswählen')}</option>
                                        {scopedUsers.map((u) => (<option key={u.id} value={u.username}>{getUserDisplayName(u)}</option>))}
                                    </select>
                                </div>
                            )}
                            <Calendar
                                className="vacation-range-calendar"
                                key={vacationPickerRevision}
                                activeStartDate={vacationCalendarMonth}
                                onActiveStartDateChange={({ activeStartDate: month }) => month && setVacationCalendarMonth(month)}
                                selectRange
                                allowPartialRange
                                onChange={(range) => {
                                    if (Array.isArray(range)) {
                                        const [startSel, endSel] = range;
                                        // The range ends at local 23:59:59.999. Keep the selected
                                        // calendar day instead of converting it to another time zone.
                                        if (startSel) setVacationStartDate(formatLocalDate(startSel));
                                        setVacationEndDate(endSel ? formatLocalDate(endSel) : '');
                                    }
                                }}
                                value={[
                                    parseDateString(vacationStartDate),
                                    parseDateString(vacationEndDate)
                                ]}
                                tileClassName={({ date, view }) => {
                                    if (view !== 'month') return null;
                                    const day = formatLocalDate(date);
                                    if (pendingVacationPeriods.some((period) => day >= period.startDate && day <= period.endDate)) return 'vacation-day-queued';
                                    if (calendarVacationRequests.some((period) => (isCompanyVacation || period.username === newVacationUser) && period.id !== editingVacation?.id && day >= period.startDate && day <= period.endDate)) return 'vacation-day-saved';
                                    return null;
                                }}
                                locale={t('calendarLocale', 'de-DE')}
                            />
                            <p className="vacation-calendar-legend"><span className="vacation-legend-saved">{t('adminVacation.legendSaved', 'Bereits eingetragen')}</span><span className="vacation-legend-queued">{t('adminVacation.legendQueued', 'Vorgemerkt')}</span></p>
                            <div className="vacation-date-inputs">
                            <div className="form-group">
                                <label htmlFor="vacStartDateInput">{t('adminVacation.startDateLabel', 'Startdatum')}:</label>
                                <input id="vacStartDateInput" type="date" value={vacationStartDate} onChange={(e) => { setVacationStartDate(e.target.value); setVacationPickerRevision((revision) => revision + 1); const date = parseDateString(e.target.value); if (date) setVacationCalendarMonth(startOfMonth(date)); }} required={Boolean(editingVacation) || pendingVacationPeriods.length === 0 || Boolean(vacationEndDate)} />
                            </div>
                            <div className="form-group">
                                <label htmlFor="vacEndDateInput">{t('adminVacation.endDateLabel', 'Enddatum')}:</label>
                                <input id="vacEndDateInput" type="date" value={vacationEndDate} onChange={(e) => { setVacationEndDate(e.target.value); setVacationPickerRevision((revision) => revision + 1); }} min={vacationStartDate || undefined} required={Boolean(editingVacation) || pendingVacationPeriods.length === 0 || Boolean(vacationStartDate)} />
                            </div>
                            </div>
                            <div className="form-group form-group-checkbox">
                                <input type="checkbox" id="vacHalfDayCheckbox" checked={newVacationHalfDay} onChange={(e) => setNewVacationHalfDay(e.target.checked)} />
                                <label htmlFor="vacHalfDayCheckbox">{t('adminVacation.halfDayLabel', 'Halbtags Urlaub')}</label>
                            </div>
                            {!isCompanyVacation && (
                                <div className="form-group form-group-checkbox">
                                    <input
                                        type="checkbox"
                                        id="vacUsesOvertimeCheckbox"
                                        checked={newVacationUsesOvertime}
                                        onChange={handleUsesOvertimeChange}
                                        disabled={!selectedUserDetailsForVacation}
                                    />
                                    <label htmlFor="vacUsesOvertimeCheckbox">{t('adminVacation.usesOvertimeLabel', 'Überstunden nutzen')}</label>
                                </div>
                            )}
                            {!isCompanyVacation && newVacationUsesOvertime && selectedUserDetailsForVacation?.isPercentage && (
                                <div className="form-group">
                                    <label htmlFor="vacOvertimeDeductionHoursInput">{t('adminVacation.overtimeDeductionHoursLabel', 'Abzuziehende Überstunden Insgesamt (in Stunden):')}</label>
                                    <input type="number" id="vacOvertimeDeductionHoursInput" value={overtimeDeductionHours} onChange={(e) => setOvertimeDeductionHours(e.target.value)} placeholder={t('adminVacation.hoursPlaceholder', 'z.B. 4 oder 8.5')} step="0.01" min="0.01" required={Boolean(editingVacation) || Boolean(vacationStartDate) || pendingVacationPeriods.length === 0} />
                                    {newVacationHalfDay && vacationStartDate && vacationEndDate && vacationStartDate === vacationEndDate && (<small className="form-text text-muted">{t('adminVacation.halfDayDeductionNotice', 'Hinweis: Für diesen halben Tag die entsprechenden Stunden für den halben Tag eintragen.')}</small>)}
                                </div>
                            )}
                            {!editingVacation && (
                                <div className="vacation-period-planner">
                                    <button type="button" className="vacation-add-period" onClick={addVacationPeriod} disabled={!vacationStartDate || !vacationEndDate || (!isCompanyVacation && !newVacationUser)}>{t('adminVacation.addPeriod', '+ Weiteren Zeitraum vormerken')}</button>
                                    {pendingVacationPeriods.length > 0 && (
                                        <ul className="vacation-period-list" aria-label={t('adminVacation.queuedPeriods', 'Vorgemerkte Zeiträume')}>
                                            {pendingVacationPeriods.map((period, index) => (
                                                <li key={`${period.startDate}-${period.endDate}`}>
                                                    <span><strong>{parseDateString(period.startDate).toLocaleDateString(t('calendarLocale', 'de-DE'))} – {parseDateString(period.endDate).toLocaleDateString(t('calendarLocale', 'de-DE'))}</strong><small>{period.halfDay ? t('halfDay', 'Halbtags') : t('fullDay', 'Ganztags')} · {period.usesOvertime ? t('overtimeVacation', 'Überstundenfrei') : t('vacation.normalVacation', 'Urlaub')}{period.overtimeDeductionMinutes ? ` · ${period.overtimeDeductionMinutes / 60} h` : ''}</small></span>
                                                    <button type="button" aria-label={t('adminVacation.removePeriod', 'Zeitraum entfernen') + ` ${index + 1}`} onClick={() => setPendingVacationPeriods((previous) => previous.filter((_, i) => i !== index))}>×</button>
                                                </li>
                                            ))}
                                        </ul>
                                    )}
                                </div>
                            )}
                            <div className="modal-buttons">
                                {editingVacation && (
                                    <button
                                        type="button"
                                        className="button-danger"
                                        onClick={() => openDeleteAbsenceConfirmation('vacation', editingVacation)}
                                    >
                                        {t('adminVacation.delete.buttonTitle', 'Urlaub loeschen')}
                                    </button>
                                )}
                                <button type="submit" className="button-confirm" disabled={isSavingVacation || (!editingVacation && pendingVacationPeriods.length === 0 && (!vacationStartDate || !vacationEndDate))}>
                                    {isSavingVacation ? t('saving', 'Wird gespeichert …') : editingVacation ? t('adminVacation.updateButton', 'Urlaub aktualisieren') : pendingVacationPeriods.length > 0 ? t('adminVacation.savePeriods', 'Zeiträume speichern') : t('adminVacation.confirmButton', 'Urlaub erstellen')}
                                </button>
                                <button type="button" onClick={handleCloseVacationModal} className="button-cancel">{t('close', 'Schließen')}</button>
                            </div>
                            </fieldset>
                        </form>
                    </div>
                </ModalOverlay>
            )}

            {showSickLeaveModal && (
                <ModalOverlay visible onClose={handleCloseSickLeaveModal}>
                    <div className="modal-content">
                        <h3>{editingSickLeave ? t('adminSickLeave.editModalTitle', 'Krankmeldung bearbeiten') : t('adminSickLeave.modalTitle', 'Krankheit für Benutzer melden')}</h3>
                        <form onSubmit={handleSubmitSickLeave}>
                            <div className="form-group">
                                <label htmlFor="sickLeaveUserSelect">{t('adminSickLeave.userSelection', 'Benutzer Auswahl')}:</label>
                                <select
                                    id="sickLeaveUserSelect"
                                    value={sickLeaveUser}
                                    onChange={handleSickLeaveUserChange}
                                    required
                                    disabled={Boolean(editingSickLeave) || Boolean(focusUsername)}
                                >
                                    <option value="">{t('adminSickLeave.selectUserPlaceholder', 'Bitte Benutzer auswählen')}</option>
                                    {scopedUsers.map((u) => (<option key={`sick-${u.id}`} value={u.username}>{getUserDisplayName(u)}</option>))}
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
                                {editingSickLeave && (
                                    <button
                                        type="button"
                                        className="button-danger"
                                        onClick={() => openDeleteAbsenceConfirmation('sick', editingSickLeave)}
                                    >
                                        {t('adminSickLeave.deleteButton', 'Krankmeldung loeschen')}
                                    </button>
                                )}
                                <button type="submit" className="button-confirm">
                                    {editingSickLeave ? t('adminSickLeave.updateButtonModal', 'Krankmeldung aktualisieren') : t('adminSickLeave.reportButtonModal', 'Krankmeldung speichern')}
                                </button>
                                <button type="button" onClick={handleCloseSickLeaveModal} className="button-cancel">{t('cancel', 'Abbrechen')}</button>
                            </div>
                        </form>
                    </div>
                </ModalOverlay>
            )}

            {absenceToDelete && (
                <ModalOverlay visible onClose={closeDeleteAbsenceConfirmation}>
                    <div className="modal-content delete-confirmation-modal absence-delete-modal">
                        <h3>{t('adminCalendar.deleteAbsenceTitle', 'Abwesenheit loeschen bestaetigen')}</h3>
                        <p>
                            {t('adminCalendar.deleteAbsenceQuestion', 'Moechten Sie diesen Eintrag wirklich loeschen?')}{' '}
                            <strong>{formatUserLongName(absenceToDelete.item.username)}</strong>{' '}
                            <span>
                                ({getAbsenceTypeLabel()} · {formatDateRange(absenceToDelete.item)}
                                {absenceToDelete.item.halfDay ? ` · ${t('adminVacation.halfDayShort', '1/2 Tag')}` : ''})
                            </span>
                        </p>
                        <p className="info-text">
                            {absenceToDelete.type === 'sick'
                                ? t('adminCalendar.deleteSickRecalculationInfo', 'Die Krankmeldung wird entfernt und der Zeitsaldo fuer den Benutzer neu berechnet.')
                                : t('adminCalendar.deleteVacationRecalculationInfo', 'Der Urlaub wird entfernt; Resturlaub und Zeitsaldo werden danach wieder aus den aktuellen Daten berechnet.')}
                        </p>
                        <div className="modal-buttons">
                            <button
                                type="button"
                                className="button-danger"
                                onClick={handleDeleteAbsence}
                                disabled={isDeletingAbsence}
                            >
                                {isDeletingAbsence
                                    ? t('adminCalendar.deletingAbsence', 'Wird geloescht...')
                                    : t('adminVacation.delete.confirmDeleteButton', 'Ja, loeschen')}
                            </button>
                            <button
                                type="button"
                                onClick={closeDeleteAbsenceConfirmation}
                                className="button-cancel"
                                disabled={isDeletingAbsence}
                            >
                                {t('cancel', 'Abbrechen')}
                            </button>
                        </div>
                    </div>
                </ModalOverlay>
            )}
        </div>
    );
});

VacationCalendarAdmin.displayName = 'VacationCalendarAdmin';
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
            approved: PropTypes.bool,
            denied: PropTypes.bool,
            companyVacation: PropTypes.bool,
            overtimeDeductionMinutes: PropTypes.number,
        })
    ).isRequired,
    onReloadVacations: PropTypes.func,
    companyUsers: PropTypes.array,
    focusUsername: PropTypes.string,
    visibleUsernames: PropTypes.arrayOf(PropTypes.string),
};

export default VacationCalendarAdmin;
