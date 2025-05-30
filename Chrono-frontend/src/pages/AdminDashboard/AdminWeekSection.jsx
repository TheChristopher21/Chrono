// src/pages/AdminDashboard/AdminWeekSection.jsx
import React, { useState, useMemo, useEffect, useRef, useCallback } /* NEU: useCallback importiert */ from "react";
import PropTypes from "prop-types";
import "../../styles/AdminDashboardScoped.css";
import api from "../../utils/api";
import { useAuth } from "../../context/AuthContext";
import { useNotification } from "../../context/NotificationContext";

import {
    formatLocalDateYMD,
    formatDate,
    getStatusLabel,
    getExpectedHoursForDay,
    computeDailyDiffValue,
    computeDailyDiff,
    computeDayTotalMinutesFromEntries,
    minutesToHHMM,
    groupTracksByDay,
    calculateWeeklyActualMinutes,
    calculateWeeklyExpectedMinutes,
    isLateTime,
    getDetailedGlobalProblemIndicators,
    getMondayOfWeek
} from "./adminDashboardUtils";

const AdminWeekSection = ({
                              t,
                              weekDates,
                              selectedMonday,
                              handlePrevWeek,
                              handleNextWeek,
                              handleWeekJump,
                              onFocusProblemWeek,
                              allTracks,
                              allVacations,
                              allSickLeaves,
                              allHolidays,
                              users,
                              defaultExpectedHours,
                              openEditModal,
                              openPrintUserModal,
                              weeklyBalances = [],
                              openNewEntryModal,
                              onDataReloadNeeded,
                          }) => {
    const { currentUser } = useAuth();
    const { notify } = useNotification();

    const [searchTerm, setSearchTerm] = useState("");
    const [detailedUser, setDetailedUser] = useState(null);
    const [sortConfig, setSortConfig] = useState({ key: 'username', direction: 'ascending' });
    const [focusedProblem, setFocusedProblem] = useState({ username: null, dateIso: null, type: null }); // NEU: type hinzugefügt

    const HIDDEN_USERS_LOCAL_STORAGE_KEY = 'adminDashboard_hiddenUsers_v1';

    const [hiddenUsers, setHiddenUsers] = useState(() => {
        const savedHiddenUsers = localStorage.getItem(HIDDEN_USERS_LOCAL_STORAGE_KEY);
        if (savedHiddenUsers) {
            try {
                const hiddenUserArray = JSON.parse(savedHiddenUsers);
                return new Set(Array.isArray(hiddenUserArray) ? hiddenUserArray : []);
            } catch (e) {
                console.error("Error parsing hidden users from localStorage:", e);
                return new Set();
            }
        }
        return new Set();
    });
    const [showHiddenUsersManager, setShowHiddenUsersManager] = useState(false);
    const detailSectionRef = useRef(null);

    const [showDeleteSickLeaveModal, setShowDeleteSickLeaveModal] = useState(false);
    const [sickLeaveToDelete, setSickLeaveToDelete] = useState(null);

    const [currentUserHolidayOptions, setCurrentUserHolidayOptions] = useState([]);

    // Stabile Funktion zum Laden der Feiertagsoptionen, um unnötige Neuausführungen des Effekts zu vermeiden
    const fetchHolidayOptionsForUser = useCallback(async (username, mondayDate) => {
        const userConf = users.find(u => u.username === username);
        if (userConf && userConf.isPercentage) {
            try {
                const response = await api.get('/api/admin/user-holiday-options/week', {
                    params: {
                        username: username,
                        mondayInWeek: formatLocalDateYMD(mondayDate)
                    }
                });
                setCurrentUserHolidayOptions(Array.isArray(response.data) ? response.data : []);
            } catch (error) {
                console.error("Error fetching holiday options for user week:", error);
                setCurrentUserHolidayOptions([]);
                notify(t('errors.fetchHolidayOptionsError', 'Fehler beim Laden der Feiertagsoptionen.'), 'error');
            }
        } else {
            setCurrentUserHolidayOptions([]);
        }
    }, [users, notify, t]); // Abhängigkeiten: users, notify, t

    useEffect(() => {
        if (detailedUser) {
            fetchHolidayOptionsForUser(detailedUser, selectedMonday);
        } else {
            setCurrentUserHolidayOptions([]);
        }
    }, [detailedUser, selectedMonday, fetchHolidayOptionsForUser]); // fetchHolidayOptionsForUser ist jetzt stabil


    useEffect(() => {
        localStorage.setItem(HIDDEN_USERS_LOCAL_STORAGE_KEY, JSON.stringify(Array.from(hiddenUsers)));
    }, [hiddenUsers]);

    const processedUserData = useMemo(() => {
        // ... (bestehende Logik für processedUserData, aber currentUserHolidayOptions wird jetzt im Effekt oben gesetzt)
        return users
            .map((user) => {
                const userConfig = user;
                const userAllTracks = allTracks.filter(track => track.username === user.username);
                const allUserDayMapGlobal = groupTracksByDay(userAllTracks);

                const userTracksCurrentWeek = userAllTracks.filter(track => {
                    const trackDate = track.dailyDate || (track.startTime ? track.startTime.slice(0, 10) : null);
                    return trackDate && weekDates.some(d => formatLocalDateYMD(d) === trackDate);
                });
                const userDayMapCurrentWeek = groupTracksByDay(userTracksCurrentWeek);

                const userApprovedVacations = allVacations.filter(
                    (vac) => vac.username === user.username && vac.approved
                );
                const userCurrentSickLeaves = allSickLeaves.filter(
                    (sl) => sl.username === user.username
                );

                const userCantonKey = userConfig.companyCantonAbbreviation || 'GENERAL';
                const holidaysForThisUser = allHolidays[userCantonKey] || allHolidays['GENERAL'] || {};

                // Für die globale Problemübersicht: Verwende die Optionen des aktuell detaillierten Users,
                // falls es sich um diesen User handelt, sonst leeres Array (oder spezifischere Logik falls nötig).
                const optionsForGlobalIndicator = user.username === detailedUser ? currentUserHolidayOptions : [];


                const weeklyActualMinutes = calculateWeeklyActualMinutes(userDayMapCurrentWeek, weekDates);
                const weeklyExpectedMinutes = calculateWeeklyExpectedMinutes(
                    userConfig,
                    weekDates,
                    defaultExpectedHours,
                    userApprovedVacations,
                    userCurrentSickLeaves,
                    holidaysForThisUser,
                    optionsForGlobalIndicator // Optionen für die Berechnung des Wochensolls
                );
                const currentWeekOvertimeMinutes = weeklyActualMinutes - weeklyExpectedMinutes;

                const balanceEntry = weeklyBalances.find(b => b.username === user.username);
                const cumulativeBalanceMinutes = balanceEntry?.trackingBalance !== undefined && balanceEntry?.trackingBalance !== null
                    ? balanceEntry.trackingBalance
                    : userConfig.trackingBalanceInMinutes ?? currentWeekOvertimeMinutes;

                const problemIndicators = getDetailedGlobalProblemIndicators(
                    allUserDayMapGlobal,
                    userApprovedVacations,
                    userConfig,
                    defaultExpectedHours,
                    userCurrentSickLeaves,
                    holidaysForThisUser,
                    optionsForGlobalIndicator // Optionen für die detaillierte Problemanalyse
                );

                return {
                    username: user.username,
                    userColor: /^#[0-9A-F]{6}$/i.test(userConfig.color || "") ? userConfig.color : "#007BFF",
                    department: userConfig.department || '-',
                    weeklyActualMinutes,
                    weeklyExpectedMinutes,
                    currentWeekOvertimeMinutes,
                    cumulativeBalanceMinutes,
                    problemIndicators,
                    userConfig,
                    userDayMap: userDayMapCurrentWeek,
                    userApprovedVacations,
                    userCurrentSickLeaves,
                    allUserDayMapGlobal
                };
            })
            .filter(ud =>
                ud.username.toLowerCase().includes(searchTerm.toLowerCase()) &&
                !hiddenUsers.has(ud.username)
            );
    }, [users, allTracks, allVacations, allSickLeaves, allHolidays, weekDates, defaultExpectedHours, weeklyBalances, searchTerm, hiddenUsers, detailedUser, currentUserHolidayOptions]); // currentUserHolidayOptions als Abhängigkeit

    const sortedUserData = useMemo(() => {
        // ... (bestehende Sortierlogik, aber Problemindikator-Sortierung anpassen)
        let sortableItems = [...processedUserData];
        if (sortConfig !== null) {
            sortableItems.sort((a, b) => {
                let valA = a[sortConfig.key];
                let valB = b[sortConfig.key];

                if (sortConfig.key === 'weeklyBalanceMinutes') {
                    valA = a.currentWeekOvertimeMinutes;
                    valB = b.currentWeekOvertimeMinutes;
                } else if (sortConfig.key === 'cumulativeBalanceMinutes') {
                    valA = a.cumulativeBalanceMinutes;
                    valB = b.cumulativeBalanceMinutes;
                } else if (sortConfig.key === 'problemIndicators') {
                    // NEU: Berücksichtige holidayPendingCount beim Sortieren
                    valA = (a.problemIndicators.missingEntriesCount + a.problemIndicators.incompleteDaysCount + a.problemIndicators.autoCompletedCount + a.problemIndicators.holidayPendingCount);
                    valB = (b.problemIndicators.missingEntriesCount + b.problemIndicators.incompleteDaysCount + b.problemIndicators.autoCompletedCount + b.problemIndicators.holidayPendingCount);
                } else if (typeof valA === 'string') {
                    valA = valA.toLowerCase();
                    valB = valB.toLowerCase();
                }

                if (valA < valB) {
                    return sortConfig.direction === 'ascending' ? -1 : 1;
                }
                if (valA > valB) {
                    return sortConfig.direction === 'ascending' ? 1 : -1;
                }
                return 0;
            });
        }
        return sortableItems;

    }, [processedUserData, sortConfig]);


    const requestSort = (key) => {
        let direction = 'ascending';
        if (sortConfig.key === key && sortConfig.direction === 'ascending') {
            direction = 'descending';
        }
        setSortConfig({ key, direction });
    };

    const getSortIndicator = (key) => {
        if (sortConfig.key === key) {
            return sortConfig.direction === 'ascending' ? ' ▲' : ' ▼';
        }
        return '';
    };

    const toggleDetails = (username) => {
        const newDetailedUser = detailedUser === username ? null : username;
        setDetailedUser(newDetailedUser);
        if (newDetailedUser === null) {
            setFocusedProblem({ username: null, dateIso: null, type: null });
        }
    };

    const daysForUserDetailView = () => {
        return weekDates;
    };

    const handleProblemIndicatorClick = (username, problemType, dateIso = null) => {
        // Wenn kein spezifisches Datum (dateIso) übergeben wird (z.B. Klick auf Gesamtindikator),
        // finde den ersten problematischen Tag dieses Typs für den User.
        let targetDateIso = dateIso;
        if (!targetDateIso) {
            const userProcessedData = processedUserData.find(ud => ud.username === username);
            if (userProcessedData && userProcessedData.problemIndicators.problematicDays.length > 0) {
                const firstProblemOfType = userProcessedData.problemIndicators.problematicDays.find(p => p.type === problemType || (problemType === 'any_incomplete' && p.type.startsWith('incomplete')) || (problemType === 'any_problem' && p.type !== 'holiday_pending_decision' && p.type !== 'auto_completed'));
                if (firstProblemOfType) {
                    targetDateIso = firstProblemOfType.dateIso;
                } else if (problemType === 'holiday_pending_decision') { // Spezifischer Fall für Feiertage
                    const firstHolidayPending = userProcessedData.problemIndicators.problematicDays.find(p => p.type === 'holiday_pending_decision');
                    if(firstHolidayPending) targetDateIso = firstHolidayPending.dateIso;
                } else if (userProcessedData.problemIndicators.problematicDays.length > 0){ // Fallback: erster problematischer Tag
                    targetDateIso = userProcessedData.problemIndicators.problematicDays[0].dateIso;
                }
            }
        }

        if (targetDateIso) {
            const problemDate = new Date(targetDateIso + "T00:00:00");
            const problemWeekMonday = getMondayOfWeek(problemDate);

            if (problemWeekMonday.getTime() !== selectedMonday.getTime()) {
                if (onFocusProblemWeek) {
                    onFocusProblemWeek(problemDate);
                }
            }
            setDetailedUser(username);
            setFocusedProblem({ username, dateIso: targetDateIso, type: problemType });
        } else {
            // Fallback, wenn kein spezifischer Tag gefunden wurde, aber Details geöffnet werden sollen
            setDetailedUser(username);
            setFocusedProblem({ username, dateIso: null, type: problemType });
        }
    };


    const handleHideUser = (usernameToHide) => {
        setHiddenUsers(prev => new Set(prev).add(usernameToHide));
        if (detailedUser === usernameToHide) {
            setDetailedUser(null);
            setFocusedProblem({ username: null, dateIso: null, type: null });
        }
    };

    const handleUnhideUser = (usernameToUnhide) => {
        setHiddenUsers(prev => {
            const next = new Set(prev);
            next.delete(usernameToUnhide);
            return next;
        });
    };

    const handleUnhideAllUsers = () => {
        setHiddenUsers(new Set());
        setShowHiddenUsersManager(false);
    };

    useEffect(() => {
        let highlightTimeoutId = null;

        if (focusedProblem.username && focusedProblem.dateIso && detailSectionRef.current) {
            // Der Typ des Problems ist jetzt in focusedProblem.type verfügbar, z.B. 'missing', 'auto_completed', 'holiday_pending_decision'
            // Für 'holiday_pending_decision' scrollt es zum Tag, das Holiday-Select wird durch die Logik in der DayCard hervorgehoben oder fokussiert.
            const elementId = `day-card-${focusedProblem.username}-${focusedProblem.dateIso}`;
            const elementToScrollTo = detailSectionRef.current.querySelector(`#${elementId}`);
            const scrollContainer = detailSectionRef.current;

            if (elementToScrollTo && scrollContainer) {
                if (scrollContainer.style.overflow === 'hidden') {
                    scrollContainer.style.overflow = '';
                }

                elementToScrollTo.scrollIntoView({ behavior: 'smooth', block: 'center' });

                // Unterschiedliche Highlights je nach Problemtyp
                let highlightClass = 'highlight-problem'; // Standard
                if (focusedProblem.type === 'auto_completed' || focusedProblem.type === 'auto_completed_incomplete') {
                    highlightClass = 'highlight-autocompleted'; // Eigene Klasse für Autocomplete
                } else if (focusedProblem.type === 'holiday_pending_decision') {
                    highlightClass = 'highlight-holiday-pending'; // Eigene Klasse für ausstehende Feiertage
                }
                elementToScrollTo.classList.add(highlightClass);


                highlightTimeoutId = setTimeout(() => {
                    if (elementToScrollTo && elementToScrollTo.classList.contains(highlightClass)) {
                        elementToScrollTo.classList.remove(highlightClass);
                    }
                    if (scrollContainer.style.overflow === 'hidden') {
                        scrollContainer.style.overflow = '';
                    }
                }, 2500); // Etwas längere Highlight-Zeit
            }
        }

        return () => {
            clearTimeout(highlightTimeoutId);
        };
    }, [focusedProblem]);

    const openDeleteSickLeaveConfirmationModal = (sickLeaveEntry) => {
        setSickLeaveToDelete(sickLeaveEntry);
        setShowDeleteSickLeaveModal(true);
    };

    const handleDeleteSickLeave = async () => {
        if (!sickLeaveToDelete || !currentUser?.username) {
            notify(t('errors.genericError', 'Ein Fehler ist aufgetreten.'), 'error');
            return;
        }
        try {
            await api.delete(`/api/sick-leave/${sickLeaveToDelete.id}`);
            notify(t('adminDashboard.sickLeaveDeleteSuccess', 'Krankmeldung erfolgreich gelöscht.'), 'success');
            setShowDeleteSickLeaveModal(false);
            setSickLeaveToDelete(null);
            if (onDataReloadNeeded) {
                onDataReloadNeeded();
            }
            if (detailedUser) { // Lade Feiertagsoptionen neu, da eine Krankmeldung das Soll beeinflussen kann
                fetchHolidayOptionsForUser(detailedUser, selectedMonday);
            }
        } catch (err) {
            console.error('Error deleting sick leave:', err);
            const errorMsg = err.response?.data?.message || err.message || t('errors.unknownError');
            notify(t('adminDashboard.sickLeaveDeleteError', 'Fehler beim Löschen der Krankmeldung:') + ` ${errorMsg}`, 'error');
        }
    };

    const handleHolidayOptionChange = async (username, dateIso, newOptionValue) => {
        try {
            await api.post('/api/admin/user-holiday-options', null, {
                params: {
                    username: username,
                    date: dateIso,
                    option: newOptionValue
                }
            });
            notify(t('adminDashboard.holidayOptionUpdateSuccess', 'Feiertagsoption erfolgreich aktualisiert.'), 'success');

            fetchHolidayOptionsForUser(username, selectedMonday); // Optionen für den betroffenen User neu laden

            if (onDataReloadNeeded) {
                onDataReloadNeeded();
            }
        } catch (error) {
            console.error("Error updating holiday option:", error);
            notify(t('errors.holidayOptionUpdateError', 'Fehler beim Aktualisieren der Feiertagsoption.') + (error.response?.data?.message || error.message), 'error');
        }
    };


    return (
        <div className="admin-dashboard scoped-dashboard">
            <section className="week-section">
                {/* ... (Header, Navigation, Suchfeld, HiddenUsersManager bleiben gleich) ... */}
                <div className="section-header-controls">
                    <h3>{t("adminDashboard.timeTrackingCurrentWeek")}</h3>
                    <div className="week-navigation">
                        <button onClick={handlePrevWeek} aria-label={t('adminDashboard.prevWeek', 'Vorige Woche')}>←</button>
                        <input type="date" value={formatLocalDateYMD(selectedMonday)} onChange={handleWeekJump} aria-label={t('adminDashboard.jumpToDate', 'Datum auswählen')} />
                        <button onClick={handleNextWeek} aria-label={t('adminDashboard.nextWeek', 'Nächste Woche')}>→</button>
                    </div>
                </div>


                <div className="user-search-controls">
                    <input
                        type="text"
                        placeholder={t("adminDashboard.searchUser") || "Benutzer suchen..."}
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        className="user-search-input"
                    />
                    {users.length > 0 && (
                        <button
                            onClick={() => setShowHiddenUsersManager(!showHiddenUsersManager)}
                            className="manage-hidden-users-button"
                            title={t('manageHiddenUsersTooltip', 'Ausgeblendete Benutzer verwalten')}
                        >
                            {showHiddenUsersManager ? t('hideHiddenUsersList', 'Liste verbergen') : t('showHiddenUsersList', 'Ausgeblendete zeigen')} ({hiddenUsers.size})
                        </button>
                    )}
                </div>

                {showHiddenUsersManager && (
                    <div className="hidden-users-manager card-style">
                        <h4>{t('hiddenUsersTitle', 'Ausgeblendete Benutzer')}</h4>
                        {hiddenUsers.size === 0 ? (
                            <p>{t('noHiddenUsers', 'Aktuell sind keine Benutzer ausgeblendet.')}</p>
                        ) : (
                            <>
                                <ul className="hidden-users-list">
                                    {Array.from(hiddenUsers).sort().map(username => (
                                        <li key={username}>
                                            <span>{username}</span>
                                            <button onClick={() => handleUnhideUser(username)} className="action-button unhide-button">
                                                {t('unhideUser', 'Einblenden')}
                                            </button>
                                        </li>
                                    ))}
                                </ul>
                                <button onClick={handleUnhideAllUsers} className="action-button unhide-all-button">
                                    {t('unhideAllUsers', 'Alle einblenden')}
                                </button>
                            </>
                        )}
                    </div>
                )}

                {sortedUserData.length === 0 ? (
                    <p className="no-data-message">
                        {/* ... (Nachrichtentext bleibt gleich) ... */}
                        {hiddenUsers.size > 0 && searchTerm.trim() === ""
                            ? t("adminDashboard.allVisibleUsersHiddenOrNoData", "Keine sichtbaren Benutzer für diese Woche oder alle gefilterten Benutzer sind ausgeblendet.")
                            : (searchTerm.trim() === ""
                                ? t("adminDashboard.noUserDataForWeek")
                                : t("adminDashboard.noMatch"))
                        }
                    </p>
                ) : (
                    <div className="table-responsive-wrapper">
                        <table className="admin-week-table">
                            <thead>
                            {/* ... (Tabellenkopf bleibt gleich) ... */}
                            <tr>
                                <th onClick={() => requestSort('username')} className="sortable-header">{t('user', 'Benutzer')} {getSortIndicator('username')}</th>
                                <th onClick={() => requestSort('weeklyActualMinutes')} className="sortable-header">{t('actualHours', 'Ist (Wo)')} {getSortIndicator('weeklyActualMinutes')}</th>
                                <th onClick={() => requestSort('weeklyExpectedMinutes')} className="sortable-header">{t('expectedHours', 'Soll (Wo)')} {getSortIndicator('weeklyExpectedMinutes')}</th>
                                <th onClick={() => requestSort('weeklyBalanceMinutes')} className="sortable-header">{t('balanceWeek', 'Saldo (diese Woche)')} {getSortIndicator('weeklyBalanceMinutes')}</th>
                                <th onClick={() => requestSort('cumulativeBalanceMinutes')} className="sortable-header">{t('balanceTotal', 'Gesamtsaldo')} {getSortIndicator('cumulativeBalanceMinutes')}</th>
                                <th onClick={() => requestSort('problemIndicators')} className="sortable-header">{t('issues', 'Probleme')} {getSortIndicator('problemIndicators')}</th>
                                <th>{t('actions', 'Aktionen')}</th>
                            </tr>
                            </thead>
                            <tbody>
                            {sortedUserData.map((userData) => (
                                <React.Fragment key={userData.username}>
                                    <tr className={detailedUser === userData.username ? "user-row-detailed" : "user-row"}>
                                        {/* ... (andere TDs bleiben gleich) ... */}
                                        <td data-label={t('user', 'Benutzer')} style={{ borderLeft: `4px solid ${userData.userColor}` }}>{userData.username}</td>
                                        <td data-label={t('actualHours', 'Ist (Wo)')}>{minutesToHHMM(userData.weeklyActualMinutes)}</td>
                                        <td data-label={t('expectedHours', 'Soll (Wo)')}>{minutesToHHMM(userData.weeklyExpectedMinutes)}</td>
                                        <td data-label={t('balanceWeek', 'Saldo (diese Woche)')} className={userData.currentWeekOvertimeMinutes < 0 ? 'negative-balance' : 'positive-balance'}>
                                            {minutesToHHMM(userData.currentWeekOvertimeMinutes)}
                                        </td>
                                        <td data-label={t('balanceTotal', 'Gesamtsaldo')} className={userData.cumulativeBalanceMinutes < 0 ? 'negative-balance' : 'positive-balance'}>
                                            {minutesToHHMM(userData.cumulativeBalanceMinutes)}
                                        </td>
                                        <td data-label={t('issues', 'Probleme')} className="problem-indicators-cell">
                                            {(userData.problemIndicators.missingEntriesCount > 0 || userData.problemIndicators.incompleteDaysCount > 0 || userData.problemIndicators.autoCompletedCount > 0 || userData.problemIndicators.holidayPendingCount > 0) ? (
                                                <>
                                                    {userData.problemIndicators.missingEntriesCount > 0 && (
                                                        <span
                                                            title={`${userData.problemIndicators.missingEntriesCount} ${t('problemTooltips.missingEntries', 'Tag(e) ohne Eintrag')}`}
                                                            onClick={() => handleProblemIndicatorClick(userData.username, "missing")}
                                                            className="problem-icon" role="button" tabIndex={0}
                                                            onKeyPress={(e) => e.key === 'Enter' && handleProblemIndicatorClick(userData.username, "missing")}
                                                        >❗</span>
                                                    )}
                                                    {userData.problemIndicators.incompleteDaysCount > 0 && (
                                                        <span
                                                            title={`${userData.problemIndicators.incompleteDaysCount} ${t('problemTooltips.incompleteDays', 'Tag(e) unvollständig')}`}
                                                            onClick={() => handleProblemIndicatorClick(userData.username, "any_incomplete")}
                                                            className="problem-icon" role="button" tabIndex={0}
                                                            onKeyPress={(e) => e.key === 'Enter' && handleProblemIndicatorClick(userData.username, "any_incomplete")}
                                                        >⚠️</span>
                                                    )}
                                                    {userData.problemIndicators.autoCompletedCount > 0 && (
                                                        <span
                                                            title={`${userData.problemIndicators.autoCompletedCount} ${t('problemTooltips.autoCompletedDays', 'Tag(e) automatisch beendet (23:20)')}`}
                                                            onClick={() => handleProblemIndicatorClick(userData.username, "auto_completed")}
                                                            className="problem-icon auto-completed-icon" role="button" tabIndex={0}
                                                            onKeyPress={(e) => e.key === 'Enter' && handleProblemIndicatorClick(userData.username, "auto_completed")}
                                                        >🤖</span>
                                                    )}
                                                    {/* NEU: Indikator für ausstehende Feiertagsentscheidung */}
                                                    {userData.problemIndicators.holidayPendingCount > 0 && (
                                                        <span
                                                            title={`${userData.problemIndicators.holidayPendingCount} ${t('problemTooltips.holidayPending', 'Feiertagsoption(en) ausstehend')}`}
                                                            onClick={() => handleProblemIndicatorClick(userData.username, "holiday_pending_decision")}
                                                            className="problem-icon holiday-pending-icon" role="button" tabIndex={0} // Eigene Klasse für Styling
                                                            onKeyPress={(e) => e.key === 'Enter' && handleProblemIndicatorClick(userData.username, "holiday_pending_decision")}
                                                        >🎉❓</span>
                                                    )}
                                                </>
                                            ) : (<span role="img" aria-label={t('noIssues', 'Keine Probleme')}>✅</span>)}
                                        </td>
                                        <td data-label={t('actions', 'Aktionen')} className="actions-cell">
                                            {/* ... (Aktionsbuttons bleiben gleich) ... */}
                                            <button
                                                onClick={() => toggleDetails(userData.username)}
                                                className="action-button details-toggle-button"
                                                title={detailedUser === userData.username ? t('hideDetails', 'Details Ausblenden') : t('showDetails', 'Details Anzeigen')}
                                                aria-expanded={detailedUser === userData.username}
                                            >
                                                {detailedUser === userData.username ? '📂' : '📄'}
                                            </button>
                                            <button
                                                onClick={() => openPrintUserModal(userData.username)}
                                                className="action-button print-user-button"
                                                title={t('printButtonUser', 'Zeiten dieses Benutzers drucken')}
                                            >
                                                🖨️
                                            </button>
                                            <button
                                                onClick={() => handleHideUser(userData.username)}
                                                className="action-button hide-user-row-button"
                                                title={t('hideUserInTable', 'Diesen Benutzer in der Tabelle ausblenden')}
                                            >
                                                🙈
                                            </button>
                                        </td>
                                    </tr>
                                    {detailedUser === userData.username && (
                                        <tr className="user-detail-row">
                                            <td colSpan="7" ref={detailSectionRef}>
                                                <div className="admin-week-display-detail">
                                                    {/* ... (Anzeige der Wochensalden bleibt gleich) ... */}
                                                    <div className="user-weekly-balance-detail">
                                                        <strong>{t('balanceTotal', 'Gesamtsaldo')}:</strong> {minutesToHHMM(userData.cumulativeBalanceMinutes)}
                                                        <br />
                                                        <strong>{t('balanceWeek', 'Saldo (diese Woche)')}:</strong> {minutesToHHMM(userData.currentWeekOvertimeMinutes)}
                                                    </div>

                                                    {daysForUserDetailView().map((d) => {
                                                        const iso = formatLocalDateYMD(d);
                                                        const dayEntriesArray = userData.userDayMap[iso] || [];
                                                        const singleDayEntryObject = dayEntriesArray[0];

                                                        const userCantonKeyForDayCard = userData.userConfig.companyCantonAbbreviation || 'GENERAL';
                                                        const holidaysForDayCard = allHolidays[userCantonKeyForDayCard] || allHolidays['GENERAL'] || {};
                                                        const holidayNameOnThisDay = holidaysForDayCard[iso];

                                                        const expected = getExpectedHoursForDay(
                                                            d,
                                                            userData.userConfig,
                                                            defaultExpectedHours,
                                                            holidaysForDayCard,
                                                            userData.userApprovedVacations,
                                                            userData.userCurrentSickLeaves,
                                                            currentUserHolidayOptions // Hier die spezifischen Optionen verwenden
                                                        );

                                                        const currentDayDate = new Date(d);
                                                        currentDayDate.setHours(0, 0, 0, 0);

                                                        const vacationOnThisDay = userData.userApprovedVacations.find(vac => {
                                                            return iso >= vac.startDate && iso <= vac.endDate && vac.approved;
                                                        });

                                                        const sickOnThisDay = userData.userCurrentSickLeaves.find(sick => {
                                                            return iso >= sick.startDate && iso <= sick.endDate;
                                                        });

                                                        // NEU: Highlight-Logik für spezifische Problemtypen
                                                        const isFocused = focusedProblem.username === userData.username && focusedProblem.dateIso === iso;
                                                        let dayCardId = `day-card-${userData.username}-${iso}`;
                                                        let cardClass = `admin-day-card`;
                                                        if (isFocused) {
                                                            if (focusedProblem.type === 'auto_completed' || focusedProblem.type === 'auto_completed_incomplete') {
                                                                cardClass += ' highlight-autocompleted';
                                                                dayCardId += '-autocompleted-focus';
                                                            } else if (focusedProblem.type === 'holiday_pending_decision') {
                                                                cardClass += ' highlight-holiday-pending';
                                                                dayCardId += '-holidaypending-focus';
                                                            } else { // missing, incomplete_work_end_missing, etc.
                                                                cardClass += ' focused-problem';
                                                                dayCardId += '-problem-focus';
                                                            }
                                                        }
                                                        if (singleDayEntryObject?.corrected && singleDayEntryObject?.workEnd && isLateTime(singleDayEntryObject.workEnd) && !isFocused) { // Nur wenn nicht schon durch Fokus gehighlighted
                                                            cardClass += ' auto-completed-day-card';
                                                        }


                                                        let dayCardContent;
                                                        let holidayHandlingSelect = null;
                                                        const currentHolidayOptionDto = currentUserHolidayOptions.find(opt => opt.holidayDate === iso);
                                                        const currentHolidayHandling = currentHolidayOptionDto?.holidayHandlingOption || 'PENDING_DECISION';

                                                        if (userData.userConfig.isPercentage && holidayNameOnThisDay) {
                                                            cardClass += ' admin-day-card-holiday-percentage'; // Beibehaltung für Styling
                                                            // Das Select wird jetzt innerhalb des holidayNameOnThisDay Blocks gerendert
                                                        }
                                                        // ... (Restliche Logik für vacationOnThisDay, sickOnThisDay etc. bleibt gleich)
                                                        // ... bis zur Rendervariante von dayCardContent
                                                        if (holidayNameOnThisDay) {
                                                            cardClass += ' admin-day-card-holiday';
                                                            dayCardContent = (
                                                                <>
                                                                    <p className="holiday-indicator">🎉 {t('holiday', 'Feiertag')}: {holidayNameOnThisDay}</p>
                                                                    {userData.userConfig.isPercentage && (
                                                                        <div className="holiday-handling-select">
                                                                            <label htmlFor={`holiday-opt-${iso}-${userData.username}`}>{t('adminDashboard.holidayOptionLabel', 'Feiertagsoption:')}</label>
                                                                            <select
                                                                                id={`holiday-opt-${iso}-${userData.username}`}
                                                                                value={currentHolidayHandling}
                                                                                onChange={(e) => handleHolidayOptionChange(userData.username, iso, e.target.value)}
                                                                                className={isFocused && focusedProblem.type === 'holiday_pending_decision' ? 'highlight-select' : ''} // Klasse für JS-Fokus/Highlight
                                                                            >
                                                                                <option value="PENDING_DECISION">{t('adminDashboard.holidayOption.pending', 'Ausstehend')}</option>
                                                                                <option value="DEDUCT_FROM_WEEKLY_TARGET">{t('adminDashboard.holidayOption.deduct', 'Soll reduzieren')}</option>
                                                                                <option value="DO_NOT_DEDUCT_FROM_WEEKLY_TARGET">{t('adminDashboard.holidayOption.doNotDeduct', 'Soll nicht reduzieren')}</option>
                                                                            </select>
                                                                        </div>
                                                                    )}
                                                                </>
                                                            );
                                                        } else if (vacationOnThisDay) {
                                                            // ... (wie zuvor)
                                                            cardClass += ' admin-day-card-vacation';
                                                            dayCardContent = <p className="vacation-indicator">🏖️ {t('adminDashboard.onVacation', 'Im Urlaub')}{vacationOnThisDay.halfDay ? ` (${t('adminDashboard.halfDayShort', '½ Tag')})` : ''}{vacationOnThisDay.usesOvertime ? ` (${t('adminDashboard.overtimeVacationShort', 'ÜS')})` : ''}</p>;
                                                        } else if (sickOnThisDay) {
                                                            // ... (wie zuvor)
                                                            cardClass += ' admin-day-card-sick';
                                                            dayCardContent = (
                                                                <div className="sick-leave-details-admin">
                                                                    <p className="sick-indicator">
                                                                        ⚕️ {t('sickLeave.sick', 'Krank')}{sickOnThisDay.halfDay ? ` (${t('adminDashboard.halfDayShort', '½ Tag')})` : ''}
                                                                        {sickOnThisDay.comment && <span className="sick-comment-badge" title={sickOnThisDay.comment}>📝</span>}
                                                                    </p>
                                                                    <button
                                                                        onClick={() => openDeleteSickLeaveConfirmationModal(sickOnThisDay)}
                                                                        className="delete-sick-leave-button-inline"
                                                                        title={t('adminDashboard.deleteSickLeaveTitle', 'Krankmeldung löschen')}
                                                                    >
                                                                        🗑️
                                                                    </button>
                                                                </div>
                                                            );
                                                        } else if (!singleDayEntryObject || (!singleDayEntryObject.workStart && !singleDayEntryObject.workEnd)) {
                                                            // ... (wie zuvor)
                                                            let showNewEntryButton = (expected != null && expected > 0) || (userData.userConfig.isPercentage && (d.getDay() !== 0 && d.getDay() !== 6)); // Sa/So nur wenn explizit Soll
                                                            const relevantEffectiveStartDate = userData.userConfig.scheduleEffectiveDate ? new Date(userData.userConfig.scheduleEffectiveDate + "T00:00:00") : null;
                                                            if (relevantEffectiveStartDate && currentDayDate < relevantEffectiveStartDate) {
                                                                showNewEntryButton = false;
                                                            }
                                                            dayCardContent = (
                                                                <>
                                                                    <p className="no-entries">{t('adminDashboard.noEntries')}</p>
                                                                    {showNewEntryButton ? (
                                                                        <button className="edit-day-button new-entry" onClick={() => openNewEntryModal(userData.username, d)}>{t('adminDashboard.newEntryButton')}</button>
                                                                    ) : null}
                                                                </>
                                                            );
                                                        } else {
                                                            // ... (wie zuvor)
                                                            const dailyDiffStr = computeDailyDiff(
                                                                [singleDayEntryObject],
                                                                expected,
                                                                userData.userConfig.isHourly,
                                                                d,
                                                                userData.userConfig,
                                                                holidaysForDayCard,
                                                                currentUserHolidayOptions
                                                            );
                                                            const isAutoCompleted = singleDayEntryObject?.corrected && singleDayEntryObject?.workEnd && isLateTime(singleDayEntryObject.workEnd);

                                                            dayCardContent = (
                                                                <>
                                                                    <div className="admin-day-card-header">
                                                                        <div>
                                                                            {expected !== null && !userData.userConfig.isHourly && <span className="expected-hours"> (Soll: {expected.toFixed(2)}h)</span>}
                                                                            {!userData.userConfig.isHourly && dailyDiffStr &&
                                                                                <span className={`daily-diff ${computeDailyDiffValue([singleDayEntryObject], expected, false, d, userData.userConfig, holidaysForDayCard, currentUserHolidayOptions) < 0 ? 'negative-balance' : 'positive-balance'}`}> ({dailyDiffStr})</span>
                                                                            }
                                                                            {isAutoCompleted && <span className="auto-completed-tag">AUTO</span>}
                                                                        </div>
                                                                        <button className="edit-day-button" onClick={() => openEditModal(userData.username, d, [singleDayEntryObject])}>{t("adminDashboard.editButton")}</button>
                                                                    </div>
                                                                    <ul className="time-entry-list">
                                                                        {singleDayEntryObject.workStart && <li><span className="entry-label">{getStatusLabel(1)}:</span> <span className={isLateTime(singleDayEntryObject.workStart) ? 'late-time' : ''}>{singleDayEntryObject.workStart.slice(0,5)}</span></li>}
                                                                        {singleDayEntryObject.breakStart && <li><span className="entry-label">{getStatusLabel(2)}:</span> {singleDayEntryObject.breakStart.slice(0,5)}</li>}
                                                                        {singleDayEntryObject.breakEnd && <li><span className="entry-label">{getStatusLabel(3)}:</span> {singleDayEntryObject.breakEnd.slice(0,5)}</li>}
                                                                        {singleDayEntryObject.workEnd && <li><span className="entry-label">{getStatusLabel(4)}:</span> <span className={isLateTime(singleDayEntryObject.workEnd) ? 'late-time' : ''}>{singleDayEntryObject.workEnd.slice(0,5)}</span></li>}
                                                                    </ul>
                                                                    {userData.userConfig.isHourly && <p><strong>{t('totalTime', 'Gesamtzeit')}:</strong> {minutesToHHMM(computeDayTotalMinutesFromEntries([singleDayEntryObject]))}</p>}
                                                                </>
                                                            );
                                                        }


                                                        return (
                                                            <div id={dayCardId} key={dayCardId} className={cardClass}>
                                                                <div className="admin-day-card-header-date">
                                                                    <strong>{d.toLocaleDateString("de-DE", { weekday: "long" })}, {formatDate(d)}</strong>
                                                                    {userData.userConfig.isPercentage && holidayNameOnThisDay && currentHolidayHandling === 'PENDING_DECISION' && (
                                                                        <span className="holiday-pending-icon" title={t('adminDashboard.holidayOptionPendingTooltip', 'Feiertagsoption ausstehend')}>🎉❓</span>
                                                                    )}
                                                                </div>
                                                                <div className="admin-day-content">
                                                                    {dayCardContent}
                                                                </div>
                                                            </div>
                                                        );
                                                    })}
                                                    {/* ... (Print-Button bleibt gleich) ... */}
                                                    <button className="print-times-button" onClick={() => openPrintUserModal(userData.username)}>
                                                        {t("adminDashboard.printButton") || "Zeiten Drucken"}
                                                    </button>
                                                </div>
                                            </td>
                                        </tr>
                                    )}
                                </React.Fragment>
                            ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </section>
            {/* ... (Modals bleiben gleich) ... */}
            {showDeleteSickLeaveModal && sickLeaveToDelete && (
                <div className="modal-overlay">
                    <div className="modal-content delete-confirmation-modal">
                        <h3>{t('adminDashboard.deleteSickLeaveConfirmTitle', 'Krankmeldung löschen bestätigen')}</h3>
                        <p>
                            {t('adminDashboard.deleteSickLeaveConfirmMessage', 'Möchten Sie die Krankmeldung für')}
                            <strong> {sickLeaveToDelete.username || t('adminDashboard.unknownUser', 'Unbekannt')} </strong>
                            ({formatDate(sickLeaveToDelete.startDate)} - {formatDate(sickLeaveToDelete.endDate)})
                            {sickLeaveToDelete.halfDay ? ` (${t('adminDashboard.halfDayShort', '½ Tag')})` : ''}
                            {t('adminDashboard.deleteSickLeaveIrreversible', ' wirklich löschen? Das Tagessoll wird neu berechnet.')}
                        </p>
                        <div className="modal-buttons">
                            <button onClick={handleDeleteSickLeave} className="button-danger">
                                {t('delete', 'Ja, löschen')}
                            </button>
                            <button onClick={() => setShowDeleteSickLeaveModal(false)} className="button-cancel">
                                {t('cancel', 'Abbrechen')}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

AdminWeekSection.propTypes = {
    t: PropTypes.func.isRequired,
    weekDates: PropTypes.arrayOf(PropTypes.instanceOf(Date)).isRequired,
    selectedMonday: PropTypes.instanceOf(Date).isRequired,
    handlePrevWeek: PropTypes.func.isRequired,
    handleNextWeek: PropTypes.func.isRequired,
    handleWeekJump: PropTypes.func.isRequired,
    onFocusProblemWeek: PropTypes.func.isRequired,
    allTracks: PropTypes.array.isRequired,
    allVacations: PropTypes.array.isRequired,
    allSickLeaves: PropTypes.array.isRequired,
    allHolidays: PropTypes.object.isRequired,
    users: PropTypes.array.isRequired,
    defaultExpectedHours: PropTypes.number.isRequired,
    openEditModal: PropTypes.func.isRequired,
    openPrintUserModal: PropTypes.func.isRequired,
    weeklyBalances: PropTypes.array,
    openNewEntryModal: PropTypes.func.isRequired,
    onDataReloadNeeded: PropTypes.func.isRequired,
};

export default AdminWeekSection;