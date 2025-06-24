// src/pages/AdminDashboard/AdminWeekSection.jsx
import React, { useState, useMemo, useEffect, useRef, useCallback } from "react";
import ModalOverlay from '../../components/ModalOverlay';
import PropTypes from "prop-types";
import "../../styles/AdminDashboardScoped.css";
import api from "../../utils/api"; // Assuming api is configured
import { useAuth } from "../../context/AuthContext"; // Assuming useAuth is available
import { useNotification } from "../../context/NotificationContext"; // Assuming useNotification is available

import {
    formatLocalDateYMD,
    formatDate, // Your existing formatDate
    formatTime, // <--- HIER HINZUFÜGEN
    getExpectedHoursForDay,
    computeDailyDiff,
    minutesToHHMM,
    calculateWeeklyActualMinutes,
    calculateWeeklyExpectedMinutes,
    // isLateTime, // isLateTime might be used internally by formatTimeEntryForDisplay or similar
    getDetailedGlobalProblemIndicators,
    getMondayOfWeek,
} from "./adminDashboardUtils"; // Ensure this path is correct
import {parseISO} from "date-fns"; // Make sure date-fns is installed


const AdminWeekSection = ({
                              t,
                              weekDates,
                              selectedMonday,
                              handlePrevWeek,
                              handleNextWeek,
                              handleWeekJump,
                              onFocusProblemWeek,
                              dailySummariesForWeekSection, // This will be DailyTimeSummaryDTO[]
                              allVacations,
                              allSickLeaves,
                              allHolidays, // This is now holidaysByCanton
                              users,
                              defaultExpectedHours,
                              openEditModal, // This will pass the list of entries now
                              openPrintUserModal,
                              rawUserTrackingBalances, // Consider renaming if structure changed
                              openNewEntryModal, // For creating entries for a day from scratch
                              onDataReloadNeeded,
                          }) => {
    const { notify } = useNotification();
    const { currentUser } = useAuth();

    const [searchTerm, setSearchTerm] = useState("");
    const [detailedUser, setDetailedUser] = useState(null); // Username of the user whose details are expanded
    const [sortConfig, setSortConfig] = useState({ key: 'username', direction: 'ascending' });
    const [focusedProblem, setFocusedProblem] = useState({ username: null, dateIso: null, type: null });

    const HIDDEN_USERS_LOCAL_STORAGE_KEY = 'adminDashboard_hiddenUsers_v2';
    const [hiddenUsers, setHiddenUsers] = useState(() => {
        const saved = localStorage.getItem(HIDDEN_USERS_LOCAL_STORAGE_KEY);
        try { return saved ? new Set(JSON.parse(saved)) : new Set(); }
        catch (e) { console.error("Error parsing hidden users from localStorage:", e); return new Set(); }
    });
    const [showHiddenUsersManager, setShowHiddenUsersManager] = useState(false);
    const detailSectionRef = useRef(null); // For scrolling to problem

    // State for holiday options for the currently detailed percentage user
    const [currentUserHolidayOptions, setCurrentUserHolidayOptions] = useState([]);

    // Fetch holiday options when a percentage user's details are expanded for the current week
    const fetchHolidayOptionsForUser = useCallback(async (username, mondayDate) => {
        const userConf = users.find(u => u.username === username);
        if (userConf && userConf.isPercentage) {
            try {
                const response = await api.get('/api/admin/user-holiday-options/week', {
                    params: { username: username, mondayInWeek: formatLocalDateYMD(mondayDate) }
                });
                setCurrentUserHolidayOptions(Array.isArray(response.data) ? response.data : []);
            } catch (error) {
                console.error("Error fetching holiday options for user's week:", error);
                setCurrentUserHolidayOptions([]); // Reset on error
            }
        } else {
            setCurrentUserHolidayOptions([]); // Not a percentage user or no user
        }
    }, [users, formatLocalDateYMD]); // formatLocalDateYMD is stable

    useEffect(() => {
        if (detailedUser) {
            fetchHolidayOptionsForUser(detailedUser, selectedMonday);
        } else {
            setCurrentUserHolidayOptions([]); // Clear when no user details are open
        }
    }, [detailedUser, selectedMonday, fetchHolidayOptionsForUser]);


    useEffect(() => {
        localStorage.setItem(HIDDEN_USERS_LOCAL_STORAGE_KEY, JSON.stringify(Array.from(hiddenUsers)));
    }, [hiddenUsers]);

    const processedUserData = useMemo(() => {
        // dailySummariesForWeekSection is now a list of DailyTimeSummaryDTO
        return users
            .map((user) => {
                const userConfig = user; // UserDTO from backend
                // Get all DailyTimeSummaryDTOs for this user (can be for multiple days/weeks if AdminDashboard fetches more)
                const allUserSummariesList = dailySummariesForWeekSection.filter(s => s.username === user.username);

                // Create a map of summaries for the current display week for easy lookup
                const userDayMapCurrentWeek = {};
                weekDates.forEach(date => {
                    const isoDate = formatLocalDateYMD(date);
                    const summaryForDay = allUserSummariesList.find(s => s.date === isoDate);
                    userDayMapCurrentWeek[isoDate] = summaryForDay ||
                        { date: isoDate, username: user.username, entries: [], workedMinutes: 0, breakMinutes: 0, needsCorrection: false, primaryTimes: {isOpen: false, firstStartTime: null, lastEndTime: null}, dailyNote: null };
                });

                const userApprovedVacations = allVacations.filter(vac => vac.username === user.username && vac.approved);
                const userCurrentSickLeaves = allSickLeaves.filter(sl => sl.username === user.username);

                // Get holidays for the user's canton, or general if not specified
                const userCantonKey = userConfig.companyCantonAbbreviation || 'GENERAL';
                const holidaysForThisUserYearObj = allHolidays[userCantonKey] || allHolidays['GENERAL']; // allHolidays is now holidaysByCanton
                const holidaysForThisUserYear = holidaysForThisUserYearObj?.data || {}; // Ensure data field is accessed

                const weeklyActualMinutes = calculateWeeklyActualMinutes(Object.values(userDayMapCurrentWeek));

                // Pass holiday options specific to this user for this week if available
                // This assumes currentUserHolidayOptions is correctly populated for the detailedUser
                const holidayOptionsForThisUserInThisWeek = user.username === detailedUser ? currentUserHolidayOptions : (userConfig.isPercentage ? [] : null);


                const weeklyExpectedMinutes = calculateWeeklyExpectedMinutes(
                    userConfig, weekDates, defaultExpectedHours,
                    userApprovedVacations, userCurrentSickLeaves, holidaysForThisUserYear,
                    holidayOptionsForThisUserInThisWeek // Pass options here
                );

                const currentWeekOvertimeMinutes = weeklyActualMinutes - weeklyExpectedMinutes;
                // Use rawUserTrackingBalances or fetch/calculate cumulativeBalance differently if needed
                const cumulativeBalanceRecord = rawUserTrackingBalances.find(b => b.username === user.username);
                const cumulativeBalanceMinutes = cumulativeBalanceRecord ? cumulativeBalanceRecord.trackingBalance : (userConfig.trackingBalanceInMinutes ?? 0);


                // Use all summaries of the user for global problem indicators
                const problemIndicators = getDetailedGlobalProblemIndicators(
                    allUserSummariesList, // Pass all summaries for this user
                    userApprovedVacations, userConfig, defaultExpectedHours,
                    userCurrentSickLeaves, holidaysForThisUserYear,
                    user.username === detailedUser ? currentUserHolidayOptions : (userConfig.isPercentage ? [] : null) // Pass relevant holiday options
                );

                return {
                    username: user.username,
                    userColor: /^#[0-9A-F]{6}$/i.test(userConfig.color || "") ? userConfig.color : "#007BFF", // Default color
                    weeklyActualMinutes,
                    weeklyExpectedMinutes,
                    currentWeekOvertimeMinutes,
                    cumulativeBalanceMinutes,
                    problemIndicators,
                    userConfig, // Pass the full UserDTO
                    userDayMap: userDayMapCurrentWeek, // Summaries for the currently displayed week
                    userApprovedVacations,
                    userCurrentSickLeaves, // Pass sick leaves for display
                };
            })
            .filter(ud => // Filter by search term and hidden status
                ud.username.toLowerCase().includes(searchTerm.toLowerCase()) &&
                !hiddenUsers.has(ud.username)
            );
    }, [users, dailySummariesForWeekSection, allVacations, allSickLeaves, allHolidays, weekDates, defaultExpectedHours, searchTerm, hiddenUsers, rawUserTrackingBalances, detailedUser, currentUserHolidayOptions]);


    const sortedUserData = useMemo(() => {
        // Sorting logic (remains largely the same, adjust keys if needed)
        let sortableItems = [...processedUserData];
        if (sortConfig.key) {
            sortableItems.sort((a, b) => {
                let valA = a[sortConfig.key];
                let valB = b[sortConfig.key];

                // Special handling for problemIndicators sorting (sum of problems)
                if (sortConfig.key === 'problemIndicators') {
                    valA = (a.problemIndicators.missingEntriesCount + a.problemIndicators.incompleteDaysCount + a.problemIndicators.autoCompletedUncorrectedCount + a.problemIndicators.holidayPendingCount);
                    valB = (b.problemIndicators.missingEntriesCount + b.problemIndicators.incompleteDaysCount + b.problemIndicators.autoCompletedUncorrectedCount + b.problemIndicators.holidayPendingCount);
                } else if (typeof valA === 'string' && typeof valB === 'string') {
                    valA = valA.toLowerCase();
                    valB = valB.toLowerCase();
                }

                if (valA < valB) return sortConfig.direction === 'ascending' ? -1 : 1;
                if (valA > valB) return sortConfig.direction === 'ascending' ? 1 : -1;
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
        if (!newDetailedUser) { // If closing details, reset focused problem
            setFocusedProblem({ username: null, dateIso: null, type: null });
        }
    };

    const handleProblemIndicatorClick = (username, problemType, dateIso = null) => {
        let targetDateIso = dateIso;
        // If no specific date is provided, find the first problematic day of that type for the user
        if (!targetDateIso) {
            const userProcData = processedUserData.find(ud => ud.username === username);
            if (userProcData?.problemIndicators?.problematicDays?.length > 0) {
                const problemDays = userProcData.problemIndicators.problematicDays;
                const firstProblemOfType = problemDays.find(p =>
                    p.type === problemType ||
                    (problemType === 'any_incomplete' && p.type.startsWith('incomplete_')) ||
                    (problemType === 'auto_completed' && (p.type === 'auto_completed_uncorrected' || p.type === 'auto_completed_incomplete_uncorrected')) ||
                    (problemType === 'any_problem' && !['holiday_pending_decision'].includes(p.type)) // Exclude pending holiday for generic 'any_problem'
                );
                if (firstProblemOfType) {
                    targetDateIso = firstProblemOfType.dateIso;
                } else if (problemType === 'holiday_pending_decision') {
                    // Specifically look for holiday_pending_decision if that was clicked
                    const firstHolidayPending = problemDays.find(p => p.type === 'holiday_pending_decision');
                    if (firstHolidayPending) targetDateIso = firstHolidayPending.dateIso;
                } else if (problemDays.length > 0) {
                    // Fallback to first problem if specific type not found but problems exist
                    targetDateIso = problemDays[0].dateIso;
                }
            }
        }

        if (targetDateIso) {
            const problemDate = parseISO(targetDateIso); // Use date-fns parseISO
            const problemWeekMonday = getMondayOfWeek(problemDate);

            // Check if the week needs to change
            if (problemWeekMonday.getTime() !== selectedMonday.getTime()) {
                if (onFocusProblemWeek) { // Call the prop function to change the week
                    onFocusProblemWeek(problemDate);
                }
            }
            // Set user and problem details
            setDetailedUser(username);
            setFocusedProblem({ username, dateIso: targetDateIso, type: problemType });
        } else {
            // If no specific problem day found, just open details for the user
            setDetailedUser(username);
            setFocusedProblem({ username, dateIso: null, type: problemType });
        }
    };

    useEffect(() => {
        let highlightTimeoutId = null;
        if (focusedProblem.username && focusedProblem.dateIso && detailSectionRef.current) {
            const elementId = `day-card-${focusedProblem.username}-${focusedProblem.dateIso}`;
            const elementToScrollTo = detailSectionRef.current.querySelector(`#${elementId}`);

            if (elementToScrollTo) {
                elementToScrollTo.scrollIntoView({ behavior: 'smooth', block: 'center' }); // 'center' might be better

                let highlightClass = 'highlight-problem-generic'; // Default or general problem
                if (focusedProblem.type === 'auto_completed_uncorrected' || focusedProblem.type === 'auto_completed_incomplete_uncorrected') {
                    highlightClass = 'highlight-autocompleted';
                } else if (focusedProblem.type === 'holiday_pending_decision') {
                    highlightClass = 'highlight-holiday-pending';
                } else if (focusedProblem.type === 'missing') {
                    highlightClass = 'highlight-missing-entry';
                } else if (focusedProblem.type.startsWith('incomplete_')) {
                    highlightClass = 'highlight-incomplete-day';
                }

                elementToScrollTo.classList.add(highlightClass);
                highlightTimeoutId = setTimeout(() => {
                    elementToScrollTo.classList.remove(highlightClass);
                }, 3000); // Highlight for 3 seconds
            }
        }
        return () => clearTimeout(highlightTimeoutId); // Cleanup timeout on unmount or if focusedProblem changes
    }, [focusedProblem]); // Re-run when focusedProblem changes


    const handleHideUser = (usernameToHide) => {
        setHiddenUsers(prev => new Set(prev).add(usernameToHide));
        if(detailedUser === usernameToHide) setDetailedUser(null); // Close details if hidden
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

    // State for sick leave deletion modal
    const [showDeleteSickLeaveModal, setShowDeleteSickLeaveModal] = useState(false);
    const [sickLeaveToDelete, setSickLeaveToDelete] = useState(null);

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
            // Assuming adminUsername is required for deletion by the backend
            await api.delete(`/api/sick-leave/${sickLeaveToDelete.id}`, {
                // params: { adminUsername: currentUser.username } // If backend needs it
            });
            notify(t('adminDashboard.sickLeaveDeleteSuccess', 'Krankmeldung erfolgreich gelöscht.'), 'success');
            setShowDeleteSickLeaveModal(false);
            setSickLeaveToDelete(null);
            if (onDataReloadNeeded) onDataReloadNeeded(); // Reload data
            // If detailedUser is affected, re-fetch their holiday options as sick leave impacts expected hours
            if (detailedUser === sickLeaveToDelete.username) {
                fetchHolidayOptionsForUser(detailedUser, selectedMonday);
            }
        } catch (err) {
            console.error("Error deleting sick leave:", err);
            notify(t('errors.deleteError', 'Fehler beim Löschen der Krankmeldung:') + (err.response?.data?.message || err.message), 'error');
        }
    };

    const handleHolidayOptionChange = async (username, dateIso, newOptionValue) => {
        try {
            await api.post('/api/admin/user-holiday-options', null, {
                params: { username, date: dateIso, option: newOptionValue }
            });
            notify(t('adminDashboard.holidayOptionUpdateSuccess', 'Feiertagsoption erfolgreich aktualisiert.'), 'success');
            // Re-fetch options for the current detailed user if it matches
            if (detailedUser === username) {
                fetchHolidayOptionsForUser(username, selectedMonday);
            }
            // Reload all data as this might affect balances
            if (onDataReloadNeeded) {
                onDataReloadNeeded();
            }
        } catch (error) {
            console.error("Error updating holiday option:", error);
            notify(t('errors.holidayOptionUpdateError', 'Fehler beim Aktualisieren der Feiertagsoption:') + (error.response?.data?.message || error.message), 'error');
        }
    };


    return (
        <div className="admin-dashboard scoped-dashboard"> {/* Added scoped-dashboard here */}
            <section className="week-section content-section">
                <div className="section-header-controls"> {/* Wrapper for H3 and Navigation */}
                    <h3>{t("adminDashboard.timeTrackingCurrentWeek", "Zeiterfassung Aktuelle Woche")}</h3>
                    <div className="week-navigation">
                        <button onClick={handlePrevWeek} aria-label={t('adminDashboard.prevWeek', 'Vorige Woche')}>←</button>
                        <input
                            type="date"
                            value={formatLocalDateYMD(selectedMonday)}
                            onChange={handleWeekJump}
                            aria-label={t('adminDashboard.jumpToDate', 'Datum auswählen')}
                        />
                        <button onClick={handleNextWeek} aria-label={t('adminDashboard.nextWeek', 'Nächste Woche')}>→</button>
                    </div>
                </div>

                <div className="user-search-controls">
                    <input
                        type="text"
                        placeholder={t("adminDashboard.searchUser", "Benutzer suchen...")}
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        className="user-search-input"
                    />
                    {users.length > 0 && ( // Only show if there are users to manage
                        <button
                            onClick={() => setShowHiddenUsersManager(!showHiddenUsersManager)}
                            className="manage-hidden-users-button text-sm"
                            title={t('manageHiddenUsersTooltip', 'Ausgeblendete Benutzer verwalten')}
                        >
                            {showHiddenUsersManager ? t('hideHiddenUsersList', 'Liste verbergen') : t('showHiddenUsersList', 'Ausgeblendete zeigen')} ({hiddenUsers.size})
                        </button>
                    )}
                </div>

                {showHiddenUsersManager && (
                    <div className="hidden-users-manager card-style p-3 my-2 bg-gray-50 rounded shadow">
                        <h4 className="text-sm font-semibold mb-1">{t('hiddenUsersTitle', 'Ausgeblendete Benutzer')}</h4>
                        {hiddenUsers.size === 0 ? (
                            <p className="text-xs italic">{t('noHiddenUsers', 'Aktuell sind keine Benutzer ausgeblendet.')}</p>
                        ) : (
                            <>
                                <ul className="hidden-users-list list-disc list-inside ml-1 text-xs">
                                    {Array.from(hiddenUsers).sort().map(username => (
                                        <li key={username} className="flex justify-between items-center py-0.5">
                                            <span>{username}</span>
                                            <button onClick={() => handleUnhideUser(username)} className="action-button unhide-button text-xs p-0.5 bg-blue-100 hover:bg-blue-200 rounded">
                                                {t('unhideUser', 'Einblenden')}
                                            </button>
                                        </li>
                                    ))}
                                </ul>
                                <button onClick={handleUnhideAllUsers} className="action-button unhide-all-button mt-2 text-xs p-1 bg-gray-200 hover:bg-gray-300 rounded">
                                    {t('unhideAllUsers', 'Alle einblenden')}
                                </button>
                            </>
                        )}
                    </div>
                )}


                {sortedUserData.length === 0 && !showHiddenUsersManager ? (
                    <p className="no-data-message italic text-gray-600 p-4 text-center">
                        {hiddenUsers.size > 0 && searchTerm.trim() === ""
                            ? t("adminDashboard.allVisibleUsersHiddenOrNoData", "Alle sichtbaren Benutzer sind ausgeblendet oder es sind keine Daten für die aktuelle Woche vorhanden.")
                            : (searchTerm.trim() === "" ? t("adminDashboard.noUserDataForWeek", "Keine Benutzerdaten für diese Woche.") : t("adminDashboard.noMatch", "Keine Benutzer entsprechen der Suche."))
                        }
                    </p>
                ) : (
                    <div className="table-responsive-wrapper">
                        <table className="admin-week-table">
                            <thead>
                            <tr>
                                <th onClick={() => requestSort('username')} className="sortable-header th-user">
                                    {t('user', 'Benutzer')} {getSortIndicator('username')}
                                </th>
                                <th onClick={() => requestSort('weeklyActualMinutes')} className="sortable-header th-numeric">
                                    {t('actualHours', 'Ist (Wo)')} {getSortIndicator('weeklyActualMinutes')}
                                </th>
                                <th onClick={() => requestSort('weeklyExpectedMinutes')} className="sortable-header th-numeric">
                                    {t('expectedHours', 'Soll (Wo)')} {getSortIndicator('weeklyExpectedMinutes')}
                                </th>
                                <th onClick={() => requestSort('currentWeekOvertimeMinutes')} className="sortable-header th-numeric">
                                    {t('balanceWeek', 'Saldo (Wo)')} {getSortIndicator('currentWeekOvertimeMinutes')}
                                </th>
                                <th onClick={() => requestSort('cumulativeBalanceMinutes')} className="sortable-header th-numeric">
                                    {t('balanceTotal', 'Gesamtsaldo')} {getSortIndicator('cumulativeBalanceMinutes')}
                                </th>
                                <th onClick={() => requestSort('problemIndicators')} className="sortable-header th-center">
                                    {t('issues', 'Probleme')} {getSortIndicator('problemIndicators')}
                                </th>
                                <th className="th-actions">{t('actions', 'Aktionen')}</th>
                            </tr>
                            </thead>
                            <tbody>
                            {sortedUserData.map((userData) => (
                                <React.Fragment key={userData.username}>
                                    <tr className={`user-row ${detailedUser === userData.username ? "user-row-detailed" : ""} ${hiddenUsers.has(userData.username) ? "user-row-hidden" : ""}`}>
                                        <td data-label={t('user', 'Benutzer')} className="td-user" style={{ borderLeft: `4px solid ${userData.userColor}` }}>{userData.username}</td>
                                        <td data-label={t('actualHours', 'Ist (Wo)')} className="td-numeric">{minutesToHHMM(userData.weeklyActualMinutes)}</td>
                                        <td data-label={t('expectedHours', 'Soll (Wo)')} className="td-numeric">{minutesToHHMM(userData.weeklyExpectedMinutes)}</td>
                                        <td data-label={t('balanceWeek', 'Saldo (Wo)')} className={`td-numeric ${userData.currentWeekOvertimeMinutes < 0 ? 'negative-balance' : 'positive-balance'}`}>{minutesToHHMM(userData.currentWeekOvertimeMinutes)}</td>
                                        <td data-label={t('balanceTotal', 'Gesamtsaldo')} className={`td-numeric ${userData.cumulativeBalanceMinutes < 0 ? 'negative-balance' : 'positive-balance'}`}>{minutesToHHMM(userData.cumulativeBalanceMinutes)}</td>
                                        <td data-label={t('issues', 'Probleme')} className="problem-indicators-cell td-center">
                                            {/* Problem Indicators Logic */}
                                            {(userData.problemIndicators.missingEntriesCount > 0 || userData.problemIndicators.incompleteDaysCount > 0 || userData.problemIndicators.autoCompletedUncorrectedCount > 0 || userData.problemIndicators.holidayPendingCount > 0) ? (
                                                <div className="flex gap-1 items-center justify-center">
                                                    {userData.problemIndicators.missingEntriesCount > 0 && (
                                                        <span title={`${userData.problemIndicators.missingEntriesCount} ${t('problemTooltips.missingEntries', 'Tag(e) ohne Eintrag')}`} onClick={() => handleProblemIndicatorClick(userData.username, "missing")} className="problem-icon cursor-pointer" role="button" tabIndex={0} onKeyPress={(e) => e.key === 'Enter' && handleProblemIndicatorClick(userData.username, "missing")}>❗</span>
                                                    )}
                                                    {userData.problemIndicators.incompleteDaysCount > 0 && (
                                                        <span title={`${userData.problemIndicators.incompleteDaysCount} ${t('problemTooltips.incompleteDays', 'Tag(e) unvollständig (z.B. fehlendes Ende)')}`} onClick={() => handleProblemIndicatorClick(userData.username, "any_incomplete")} className="problem-icon cursor-pointer" role="button" tabIndex={0} onKeyPress={(e) => e.key === 'Enter' && handleProblemIndicatorClick(userData.username, "any_incomplete")}>⚠️</span>
                                                    )}
                                                    {userData.problemIndicators.autoCompletedUncorrectedCount > 0 && (
                                                        <span title={`${userData.problemIndicators.autoCompletedUncorrectedCount} ${t('problemTooltips.autoCompletedDaysUncorrected', 'Tag(e) automatisch beendet & unkorrigiert')}`} onClick={() => handleProblemIndicatorClick(userData.username, "auto_completed")} className="problem-icon auto-completed-icon cursor-pointer" role="button" tabIndex={0} onKeyPress={(e) => e.key === 'Enter' && handleProblemIndicatorClick(userData.username, "auto_completed")}>🤖</span>
                                                    )}
                                                    {userData.problemIndicators.holidayPendingCount > 0 && (
                                                        <span title={`${userData.problemIndicators.holidayPendingCount} ${t('problemTooltips.holidayPending', 'Feiertagsoption(en) ausstehend')}`} onClick={() => handleProblemIndicatorClick(userData.username, "holiday_pending_decision")} className="problem-icon holiday-pending-icon cursor-pointer" role="button" tabIndex={0} onKeyPress={(e) => e.key === 'Enter' && handleProblemIndicatorClick(userData.username, "holiday_pending_decision")}>🎉❓</span>
                                                    )}
                                                </div>
                                            ) : (<span role="img" aria-label={t('noIssues', 'Keine Probleme')} className="text-green-500">✅</span>)}
                                        </td>
                                        <td data-label={t('actions', 'Aktionen')} className="actions-cell">
                                            <button onClick={() => toggleDetails(userData.username)} className="action-button details-toggle-button" title={detailedUser === userData.username ? t('hideDetails', 'Details Ausblenden') : t('showDetails', 'Details Anzeigen')} aria-expanded={detailedUser === userData.username}>
                                                {detailedUser === userData.username ? '📂' : '📄'}
                                            </button>
                                            <button onClick={() => openPrintUserModal(userData.username)} className="action-button print-user-button" title={t('printButtonUser', 'Zeiten dieses Benutzers drucken')}>
                                                🖨️
                                            </button>
                                            <button onClick={() => handleHideUser(userData.username)} className="action-button hide-user-row-button" title={t('hideUserInTable', 'Diesen Benutzer in der Tabelle ausblenden')}>
                                                🙈
                                            </button>
                                        </td>
                                    </tr>
                                    {detailedUser === userData.username && (
                                        <tr className="user-detail-row">
                                            <td colSpan="7" ref={detailSectionRef}>
                                                <div className="admin-week-display-detail p-2 bg-slate-50 rounded-b-md shadow-inner">
                                                    <div className="user-weekly-balance-detail text-xs mb-2 font-medium">
                                                        <span>{t('balanceTotal', 'Gesamtsaldo')}: {minutesToHHMM(userData.cumulativeBalanceMinutes)}</span>
                                                        <span className="mx-2">|</span>
                                                        <span>{t('balanceWeek', 'Saldo (akt. Woche)')}: {minutesToHHMM(userData.currentWeekOvertimeMinutes)}</span>
                                                    </div>
                                                    <div className="admin-days-grid">
                                                        {weekDates.map((d) => {
                                                            const isoDate = formatLocalDateYMD(d);
                                                            const dailySummary = userData.userDayMap[isoDate]; // This is DailyTimeSummaryDTO
                                                            const userCantonKeyForDay = userData.userConfig.companyCantonAbbreviation || 'GENERAL';
                                                            const holidaysDataForDay = allHolidays[userCantonKeyForDay]?.data || allHolidays['GENERAL']?.data || {};
                                                            const holidayNameOnThisDay = holidaysDataForDay[isoDate];
                                                            const holidayOptionForThisDay = currentUserHolidayOptions.find(opt => opt.holidayDate === isoDate);


                                                            const expectedMinsToday = Math.round(getExpectedHoursForDay(d, userData.userConfig, defaultExpectedHours, holidaysDataForDay, userData.userApprovedVacations, userData.userCurrentSickLeaves, holidayOptionForThisDay) * 60);
                                                            const actualMinsToday = dailySummary?.workedMinutes || 0;
                                                            const diffMinsToday = actualMinsToday - expectedMinsToday;

                                                            const isFocused = focusedProblem.username === userData.username && focusedProblem.dateIso === isoDate;
                                                            let cardClass = `admin-day-card ${isFocused ? (focusedProblem.type.includes('auto_completed') ? 'highlight-autocompleted' : (focusedProblem.type === 'holiday_pending_decision' ? 'highlight-holiday-pending' : 'focused-problem')) : ''}`;
                                                            if (dailySummary?.needsCorrection && !isFocused) cardClass += ' auto-completed-day-card';


                                                            const vacationOnThisDay = userData.userApprovedVacations.find(vac => isoDate >= vac.startDate && isoDate <= vac.endDate);
                                                            const sickOnThisDay = userData.userCurrentSickLeaves.find(sick => isoDate >= sick.startDate && isoDate <= sick.endDate);

                                                            let dayCardContent;
                                                            if (holidayNameOnThisDay) {
                                                                cardClass += ' admin-day-card-holiday';
                                                                const currentHolidayHandling = holidayOptionForThisDay?.holidayHandlingOption || 'PENDING_DECISION';
                                                                dayCardContent = (
                                                                    <>
                                                                        <p className="holiday-indicator text-xs">🎉 {t('holiday', 'Feiertag')}: {holidayNameOnThisDay}</p>
                                                                        {userData.userConfig.isPercentage && (
                                                                            <div className="holiday-handling-select mt-1">
                                                                                <label htmlFor={`holiday-opt-${isoDate}-${userData.username}`} className="text-xs block mb-0.5">{t('adminDashboard.holidayOptionLabel', 'Option:')}</label>
                                                                                <select
                                                                                    id={`holiday-opt-${isoDate}-${userData.username}`}
                                                                                    value={currentHolidayHandling}
                                                                                    onChange={(e) => handleHolidayOptionChange(userData.username, isoDate, e.target.value)}
                                                                                    className={`text-xs p-1 border rounded ${isFocused && focusedProblem.type === 'holiday_pending_decision' ? 'highlight-select' : ''}`}
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
                                                                cardClass += ' admin-day-card-vacation';
                                                                dayCardContent = <p className="vacation-indicator text-xs">🏖️ {t('adminDashboard.onVacation', 'Im Urlaub')}{vacationOnThisDay.halfDay ? ` (${t('adminDashboard.halfDayShort', '½ Tag')})` : ''}{vacationOnThisDay.usesOvertime ? ` (${t('adminDashboard.overtimeVacationShort', 'ÜS')})` : ''}</p>;
                                                            } else if (sickOnThisDay) {
                                                                cardClass += ' admin-day-card-sick';
                                                                dayCardContent = (
                                                                    <div className="sick-leave-details-admin text-xs">
                                                                        <p className="sick-indicator">
                                                                            ⚕️ {t('sickLeave.sick', 'Krank')}
                                                                            {sickOnThisDay.halfDay ? ` (${t('adminDashboard.halfDayShort', '½ Tag')})` : ''}
                                                                            {sickOnThisDay.comment && <span className="sick-comment-badge" title={sickOnThisDay.comment}>📝</span>}
                                                                        </p>
                                                                        <button onClick={() => openDeleteSickLeaveConfirmationModal(sickOnThisDay)} className="delete-sick-leave-button-inline text-xs p-0.5" title={t('adminDashboard.deleteSickLeaveTitle', 'Krankmeldung löschen')}>
                                                                            🗑️
                                                                        </button>
                                                                    </div>
                                                                );
                                                            } else if (!dailySummary || !dailySummary.entries || dailySummary.entries.length === 0) {
                                                                let showNewEntryButton = expectedMinsToday > 0 || (userData.userConfig.isPercentage && (d.getDay() >= 1 && d.getDay() <= (userData.userConfig.expectedWorkDays || 5) )); // Zeige Button, wenn Soll oder potenzieller Arbeitstag für %
                                                                // Check against scheduleEffectiveDate
                                                                const effectiveDate = userData.userConfig.scheduleEffectiveDate ? parseISO(userData.userConfig.scheduleEffectiveDate) : null;
                                                                if (effectiveDate && d < effectiveDate) {
                                                                    showNewEntryButton = false;
                                                                }
                                                                dayCardContent = (
                                                                    <>
                                                                        <p className="no-entries text-xs italic">{t('adminDashboard.noEntries')}</p>
                                                                        {showNewEntryButton && (
                                                                            <button className="edit-day-button new-entry text-xs py-0.5 px-1 mt-1 bg-blue-500 hover:bg-blue-600 text-white rounded" onClick={() => openNewEntryModal(userData.username, d)}>
                                                                                {t('adminDashboard.newEntryButton', 'Neuer Eintrag')}
                                                                            </button>
                                                                        )}
                                                                    </>
                                                                );
                                                            } else { // Day with punches
                                                                dayCardContent = (
                                                                    <>
                                                                        <div className="admin-day-card-header justify-between items-start mb-1">
                                                                            <div className="text-xs">
                                                                                {!userData.userConfig.isHourly && <span className="expected-hours">({t('expectedTimeShort', 'Soll')}: {minutesToHHMM(expectedMinsToday)})</span>}
                                                                                {!userData.userConfig.isHourly && <span className={`daily-diff ml-1 ${diffMinsToday < 0 ? 'text-red-600' : 'text-green-600'}`}>({t('diffTimeShort', 'Diff')}: {minutesToHHMM(diffMinsToday)})</span>}
                                                                                {dailySummary.needsCorrection && <span className="auto-completed-tag ml-1 text-red-600 font-bold" title={t('adminDashboard.needsCorrectionTooltip', 'Automatisch beendet und unkorrigiert')}>KORR?</span>}
                                                                            </div>
                                                                            <button className="edit-day-button text-xs py-0.5 px-1 bg-gray-200 hover:bg-gray-300 rounded" onClick={() => openEditModal(userData.username, d, dailySummary)}>
                                                                                {t("adminDashboard.editButton", "Bearb.")}
                                                                            </button>
                                                                        </div>
                                                                        <ul className="time-entry-list-condensed text-xs">
                                                                            {dailySummary.entries.map(entry => {
                                                                                let typeLabel = entry.punchType;
                                                                                try {
                                                                                    typeLabel = t(`punchTypes.${entry.punchType}`, entry.punchType);
                                                                                } catch (e) { /* Fallback */ }

                                                                                let sourceIndicator = '';
                                                                                if (entry.source === 'SYSTEM_AUTO_END' && !entry.correctedByUser) {
                                                                                    sourceIndicator = t('adminDashboard.entrySource.autoSuffix', ' (Auto)');
                                                                                } else if (entry.source === 'ADMIN_CORRECTION') {
                                                                                    sourceIndicator = t('adminDashboard.entrySource.adminSuffix', ' (AdmK)');
                                                                                } else if (entry.source === 'USER_CORRECTION') {
                                                                                    sourceIndicator = t('adminDashboard.entrySource.userSuffix', ' (UsrK)');
                                                                                } else if (entry.source === 'MANUAL_IMPORT') {
                                                                                    sourceIndicator = t('adminDashboard.entrySource.importSuffix', ' (Imp)');
                                                                                }

                                                                                return (
                                                                                    <li key={entry.id || entry.key} className="py-0.5">
                                                                                        {/* Hier wird die korrekte formatTime-Funktion aufgerufen */}
                                                                                        {`${typeLabel}: ${formatTime(entry.entryTimestamp)}${sourceIndicator}`}
                                                                                    </li>
                                                                                );
                                                                            })}
                                                                        </ul>
                                                                        <p className="text-xs mt-1">
                                                                            <strong>{t('actualTime', 'Ist')}:</strong> {minutesToHHMM(actualMinsToday)} | <strong>{t('breakTime', 'Pause')}:</strong> {minutesToHHMM(dailySummary.breakMinutes)}
                                                                        </p>
                                                                        {dailySummary.dailyNote && <p className="text-xs mt-1 italic">📝 {dailySummary.dailyNote}</p>}
                                                                    </>
                                                                );
                                                            }

                                                            return (
                                                                <div id={`day-card-${userData.username}-${isoDate}`} key={isoDate} className={`${cardClass} p-2 border rounded shadow-sm bg-white`}>
                                                                    <div className="admin-day-card-header-date font-semibold text-sm mb-1 text-gray-700">
                                                                        {d.toLocaleDateString("de-DE", { weekday: "short" }).toUpperCase()}, {formatDate(d)}
                                                                        {/* Indikator für ausstehende Feiertagsoption für Prozent-Nutzer */}
                                                                        {userData.userConfig.isPercentage && holidayNameOnThisDay && holidayOptionForThisDay?.holidayHandlingOption === 'PENDING_DECISION' && (
                                                                            <span className="holiday-pending-icon-small ml-1 text-orange-500 animate-pulse" title={t('adminDashboard.holidayOptionPendingTooltip', 'Feiertagsoption ausstehend')}>❓</span>
                                                                        )}
                                                                    </div>
                                                                    <div className="admin-day-content">
                                                                        {dayCardContent}
                                                                    </div>
                                                                </div>
                                                            );
                                                        })}
                                                    </div>
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
            {/* Modal for deleting sick leave */}
            {showDeleteSickLeaveModal && sickLeaveToDelete && (
                <ModalOverlay visible className="bg-black bg-opacity-50">
                    <div className="modal-content delete-confirmation-modal bg-white p-6 rounded-lg shadow-xl max-w-md w-full">
                        <h3 className="text-lg font-semibold mb-4">{t('adminDashboard.deleteSickLeaveConfirmTitle', 'Krankmeldung löschen bestätigen')}</h3>
                        <p className="mb-4 text-sm">
                            {t('adminDashboard.deleteSickLeaveConfirmMessage', 'Möchten Sie die Krankmeldung für')}
                            <strong> {sickLeaveToDelete.username} </strong>
                            ({formatDate(parseISO(sickLeaveToDelete.startDate))} - {formatDate(parseISO(sickLeaveToDelete.endDate))})
                            {sickLeaveToDelete.halfDay ? ` (${t('adminDashboard.halfDayShort', '½ Tag')})` : ''}
                            {t('adminDashboard.deleteSickLeaveIrreversible', ' wirklich löschen? Das Tagessoll und der Saldo werden neu berechnet.')}
                        </p>
                        <div className="modal-buttons flex justify-end gap-3">
                            <button onClick={handleDeleteSickLeave} className="button-danger bg-red-500 hover:bg-red-600 text-white">
                                {t('delete', 'Ja, löschen')}
                            </button>
                            <button onClick={() => setShowDeleteSickLeaveModal(false)} className="button-cancel bg-gray-300 hover:bg-gray-400">
                                {t('cancel', 'Abbrechen')}
                            </button>
                        </div>
                    </div>
                </ModalOverlay>
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
    dailySummariesForWeekSection: PropTypes.arrayOf(
        PropTypes.shape({ // This should match DailyTimeSummaryDTO
            username: PropTypes.string.isRequired,
            date: PropTypes.string.isRequired, // YYYY-MM-DD
            workedMinutes: PropTypes.number.isRequired,
            breakMinutes: PropTypes.number.isRequired,
            entries: PropTypes.arrayOf( // This should match TimeTrackingEntryDTO
                PropTypes.shape({
                    id: PropTypes.number, // Can be null for new entries before saving
                    entryTimestamp: PropTypes.string.isRequired, // ISO String like "2023-10-26T08:00:00"
                    punchType: PropTypes.oneOf(['START', 'ENDE']).isRequired,
                    source: PropTypes.string, // e.g., 'NFC_SCAN', 'MANUAL_PUNCH', 'SYSTEM_AUTO_END', 'ADMIN_CORRECTION'
                    correctedByUser: PropTypes.bool, // Indicates if an admin or system correction was then user-corrected
                    systemGeneratedNote: PropTypes.string,
                    key: PropTypes.string // Client-side key for React lists, optional
                })
            ).isRequired,
            needsCorrection: PropTypes.bool, // If SYSTEM_AUTO_END happened and not user corrected
            primaryTimes: PropTypes.shape({ // For quick access to main start/end
                firstStartTime: PropTypes.string, // HH:mm or null
                lastEndTime: PropTypes.string,    // HH:mm or null
                isOpen: PropTypes.bool.isRequired // True if the last punch of the day was START
            }),
            dailyNote: PropTypes.string
        })
    ).isRequired,
    allVacations: PropTypes.arrayOf(
        PropTypes.shape({
            username: PropTypes.string,
            startDate: PropTypes.string.isRequired, // YYYY-MM-DD
            endDate: PropTypes.string.isRequired,   // YYYY-MM-DD
            approved: PropTypes.bool,
            halfDay: PropTypes.bool,
            usesOvertime: PropTypes.bool
        })
    ).isRequired,
    allSickLeaves: PropTypes.arrayOf(
        PropTypes.shape({
            username: PropTypes.string.isRequired,
            startDate: PropTypes.string.isRequired, // YYYY-MM-DD
            endDate: PropTypes.string.isRequired,   // YYYY-MM-DD
            halfDay: PropTypes.bool,
            comment: PropTypes.string
        })
    ).isRequired,
    allHolidays: PropTypes.objectOf( // Keyed by canton (e.g., "SG", "GENERAL")
        PropTypes.shape({
            data: PropTypes.objectOf(PropTypes.string).isRequired, // Key: "YYYY-MM-DD", Value: "Holiday Name"
            year: PropTypes.number.isRequired
        })
    ).isRequired,
    users: PropTypes.arrayOf(
        PropTypes.shape({ // Matches UserDTO more closely
            username: PropTypes.string.isRequired,
            trackingBalanceInMinutes: PropTypes.number,
            isPercentage: PropTypes.bool,
            isHourly: PropTypes.bool,
            expectedWorkDays: PropTypes.number,
            workPercentage: PropTypes.number,
            dailyWorkHours: PropTypes.number,
            companyCantonAbbreviation: PropTypes.string,
            color: PropTypes.string,
            scheduleEffectiveDate: PropTypes.string, // YYYY-MM-DD
            // other user fields...
        })
    ).isRequired,
    defaultExpectedHours: PropTypes.number.isRequired,
    openEditModal: PropTypes.func.isRequired,
    openPrintUserModal: PropTypes.func.isRequired,
    rawUserTrackingBalances: PropTypes.arrayOf(PropTypes.shape({
        username: PropTypes.string.isRequired,
        trackingBalance: PropTypes.number.isRequired, // Assuming this structure
    })),
    openNewEntryModal: PropTypes.func.isRequired,
    onDataReloadNeeded: PropTypes.func.isRequired,
};

export default AdminWeekSection;