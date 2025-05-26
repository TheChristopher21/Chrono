// src/pages/AdminDashboard/AdminWeekSection.jsx
import React, { useState, useMemo, useEffect, useRef } from "react";
import PropTypes from "prop-types";
import "../../styles/AdminDashboardScoped.css";

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
    getMondayOfWeek // Hinzufügen, falls nicht schon importiert
} from "./adminDashboardUtils";

const AdminWeekSection = ({
                              t,
                              weekDates,
                              selectedMonday, // Das aktuell im Dashboard ausgewählte Montag-Datum
                              handlePrevWeek,
                              handleNextWeek,
                              handleWeekJump,
                              onFocusProblemWeek, // NEUE Prop von AdminDashboard
                              allTracks,
                              allVacations,
                              users,
                              defaultExpectedHours,
                              openEditModal,
                              openPrintUserModal,
                              weeklyBalances = [],
                              openNewEntryModal,
                          }) => {
    // ... (bestehende States: searchTerm, detailedUser, sortConfig, focusedProblem, etc.) ...
    const [searchTerm, setSearchTerm] = useState("");
    const [detailedUser, setDetailedUser] = useState(null);
    const [sortConfig, setSortConfig] = useState({ key: 'username', direction: 'ascending' });
    const [focusedProblem, setFocusedProblem] = useState({ username: null, dateIso: null });

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

    useEffect(() => {
        localStorage.setItem(HIDDEN_USERS_LOCAL_STORAGE_KEY, JSON.stringify(Array.from(hiddenUsers)));
    }, [hiddenUsers]);

    // processedUserData und sortedUserData bleiben wie in der vorherigen Antwort

    const processedUserData = useMemo(() => {
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

                const weeklyActualMinutes = calculateWeeklyActualMinutes(userDayMapCurrentWeek, weekDates);
                const weeklyExpectedMinutes = calculateWeeklyExpectedMinutes(userConfig, weekDates, defaultExpectedHours, userApprovedVacations);
                const currentWeekOvertimeMinutes = weeklyActualMinutes - weeklyExpectedMinutes;

                const balanceEntry = weeklyBalances.find(b => b.username === user.username);
                const cumulativeBalanceMinutes = balanceEntry?.trackingBalance !== undefined && balanceEntry?.trackingBalance !== null
                    ? balanceEntry.trackingBalance
                    : userConfig.trackingBalanceInMinutes ?? currentWeekOvertimeMinutes;

                const problemIndicators = getDetailedGlobalProblemIndicators(
                    allUserDayMapGlobal,
                    userApprovedVacations,
                    userConfig,
                    defaultExpectedHours
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
                    allUserDayMapGlobal
                };
            })
            .filter(ud =>
                ud.username.toLowerCase().includes(searchTerm.toLowerCase()) &&
                !hiddenUsers.has(ud.username)
            );

    }, [users, allTracks, allVacations, weekDates, defaultExpectedHours, weeklyBalances, searchTerm, hiddenUsers]);

    const sortedUserData = useMemo(() => {
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
                    valA = (a.problemIndicators.missingEntriesCount + a.problemIndicators.incompleteDaysCount + a.problemIndicators.autoCompletedCount);
                    valB = (b.problemIndicators.missingEntriesCount + b.problemIndicators.incompleteDaysCount + b.problemIndicators.autoCompletedCount);
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
            setFocusedProblem({ username: null, dateIso: null });
        }
    };

    const daysForUserDetailView = () => {
        return weekDates;
    };

    const handleProblemIndicatorClick = (username, globalProblematicDays) => {
        if (globalProblematicDays && globalProblematicDays.length > 0) {
            const firstProblemDateIso = globalProblematicDays[0].dateIso;
            const problemDate = new Date(firstProblemDateIso + "T00:00:00"); // Für Datumsvergleiche
            const problemWeekMonday = getMondayOfWeek(problemDate);

            // Prüfen, ob die Woche des Problems von der aktuell ausgewählten Woche abweicht
            // Wichtig: selectedMonday ist ein Date-Objekt, problemWeekMonday auch. Zeitkomponenten müssen ignoriert werden.
            if (problemWeekMonday.getTime() !== selectedMonday.getTime()) {
                if (onFocusProblemWeek) {
                    onFocusProblemWeek(problemDate); // Übergibt das genaue Datum, AdminDashboard setzt den Montag dieser Woche
                }
            }
            setDetailedUser(username); // Details für den User auf jeden Fall öffnen/beibehalten
            setFocusedProblem({ username, dateIso: firstProblemDateIso }); // Fokus auf das Problem setzen
        }
    };

    const handleHideUser = (usernameToHide) => {
        setHiddenUsers(prev => new Set(prev).add(usernameToHide));
        if (detailedUser === usernameToHide) {
            setDetailedUser(null);
            setFocusedProblem({ username: null, dateIso: null });
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
        if (focusedProblem.username && focusedProblem.dateIso && detailSectionRef.current) {
            const elementId = `day-card-${focusedProblem.username}-${focusedProblem.dateIso}`;
            // console.log("Attempting to scroll to:", elementId, "in ref:", detailSectionRef.current);
            const element = detailSectionRef.current.querySelector(`#${elementId}`);
            // console.log("Element found for scrolling:", element);
            if (element) {
                element.scrollIntoView({ behavior: 'smooth', block: 'center' });
            } else {
                // console.log(`Day card with ID ${elementId} not found in the current detail view.`);
            }
        }
    }, [focusedProblem, detailedUser, weekDates]); // weekDates hinzugefügt, damit es neu versucht zu scrollen, wenn sich die Woche ändert

    return (
        <div className="admin-dashboard scoped-dashboard">
            <section className="week-section">
                <h3>{t("adminDashboard.timeTrackingCurrentWeek")}</h3>
                <div className="week-navigation">
                    <button onClick={handlePrevWeek}>← {t("adminDashboard.prevWeek")}</button>
                    <input type="date" value={formatLocalDateYMD(selectedMonday)} onChange={handleWeekJump} />
                    <button onClick={handleNextWeek}>{t("adminDashboard.nextWeek")} →</button>
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
                    <p>
                        {hiddenUsers.size > 0 && searchTerm.trim() === ""
                            ? t("adminDashboard.allVisibleUsersHiddenOrNoData", "Keine sichtbaren Benutzer für diese Woche oder alle gefilterten Benutzer sind ausgeblendet.")
                            : (searchTerm.trim() === ""
                                ? t("adminDashboard.noUserDataForWeek")
                                : t("adminDashboard.noMatch"))
                        }
                    </p>
                ) : (
                    <table className="admin-week-table">
                        <thead>
                        <tr>
                            <th onClick={() => requestSort('username')}>{t('user', 'Benutzer')} {getSortIndicator('username')}</th>
                            <th onClick={() => requestSort('weeklyActualMinutes')}>{t('actualHours', 'Ist (Wo)')} {getSortIndicator('weeklyActualMinutes')}</th>
                            <th onClick={() => requestSort('weeklyExpectedMinutes')}>{t('expectedHours', 'Soll (Wo)')} {getSortIndicator('weeklyExpectedMinutes')}</th>
                            <th onClick={() => requestSort('weeklyBalanceMinutes')}>{t('balanceWeek', 'Saldo (diese Woche)')} {getSortIndicator('weeklyBalanceMinutes')}</th>
                            <th onClick={() => requestSort('cumulativeBalanceMinutes')}>{t('balanceTotal', 'Gesamtsaldo')} {getSortIndicator('cumulativeBalanceMinutes')}</th>
                            <th onClick={() => requestSort('problemIndicators')}>{t('issues', 'Probleme')} {getSortIndicator('problemIndicators')}</th>
                            <th>{t('actions', 'Aktionen')}</th>
                        </tr>
                        </thead>
                        <tbody>
                        {sortedUserData.map((userData) => (
                            <React.Fragment key={userData.username}>
                                <tr className={detailedUser === userData.username ? "user-row-detailed" : ""}>
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
                                        {(userData.problemIndicators.missingEntriesCount > 0 || userData.problemIndicators.incompleteDaysCount > 0 || userData.problemIndicators.autoCompletedCount > 0) ? (
                                            <>
                                                {userData.problemIndicators.missingEntriesCount > 0 && (
                                                    <span
                                                        title={`${userData.problemIndicators.missingEntriesCount} ${t('problemTooltips.missingEntries', 'Tag(e) ohne Eintrag')}`}
                                                        onClick={() => handleProblemIndicatorClick(userData.username, userData.problemIndicators.problematicDays)}
                                                        className="problem-icon"
                                                    >❗</span>
                                                )}
                                                {userData.problemIndicators.incompleteDaysCount > 0 && (
                                                    <span
                                                        title={`${userData.problemIndicators.incompleteDaysCount} ${t('problemTooltips.incompleteDays', 'Tag(e) unvollständig')}`}
                                                        onClick={() => handleProblemIndicatorClick(userData.username, userData.problemIndicators.problematicDays)}
                                                        className="problem-icon"
                                                    >⚠️</span>
                                                )}
                                                {userData.problemIndicators.autoCompletedCount > 0 && (
                                                    <span
                                                        title={`${userData.problemIndicators.autoCompletedCount} ${t('problemTooltips.autoCompletedDays', 'Tag(e) automatisch beendet (23:20)')}`}
                                                        onClick={() => handleProblemIndicatorClick(userData.username, userData.problemIndicators.problematicDays)}
                                                        className="problem-icon auto-completed-icon"
                                                    >🤖</span>
                                                )}
                                            </>
                                        ) : '✅'}
                                    </td>
                                    <td data-label={t('actions', 'Aktionen')} className="actions-cell">
                                        <button
                                            onClick={() => toggleDetails(userData.username)}
                                            className="action-button details-toggle-button"
                                            title={detailedUser === userData.username ? t('hideDetails', 'Details Ausblenden') : t('showDetails', 'Details Anzeigen')}
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
                                                <div className="user-weekly-balance-detail">
                                                    <strong>{t('balanceTotal', 'Gesamtsaldo')}:</strong> {minutesToHHMM(userData.cumulativeBalanceMinutes)}
                                                    <br />
                                                    <strong>{t('balanceWeek', 'Saldo (diese Woche)')}:</strong> {minutesToHHMM(userData.currentWeekOvertimeMinutes)}
                                                </div>

                                                {daysForUserDetailView().map((d) => {
                                                    const iso = formatLocalDateYMD(d);
                                                    const dayEntriesArray = userData.userDayMap[iso] || [];
                                                    const singleDayEntryObject = dayEntriesArray[0];

                                                    const expected = getExpectedHoursForDay(d, userData.userConfig, defaultExpectedHours);

                                                    let vacationOnThisDay = null;
                                                    const currentDayDate = new Date(d);
                                                    currentDayDate.setHours(0, 0, 0, 0);

                                                    for (const vac of userData.userApprovedVacations) {
                                                        const vacStart = new Date(vac.startDate + "T00:00:00");
                                                        const vacEnd = new Date(vac.endDate + "T00:00:00"); // Korrigiert, um nur Startdatum zu vergleichen
                                                        if (currentDayDate >= vacStart && currentDayDate <= vacEnd) {
                                                            vacationOnThisDay = vac;
                                                            break;
                                                        }
                                                    }

                                                    const dayCardId = `day-card-${userData.username}-${iso}`;
                                                    const isFocused = focusedProblem.username === userData.username && focusedProblem.dateIso === iso;
                                                    const isAutoCompleted = singleDayEntryObject?.corrected && singleDayEntryObject?.workEnd?.startsWith("23:20");
                                                    const dayOfWeek = d.getDay();

                                                    if (vacationOnThisDay) {
                                                        return (
                                                            <div id={dayCardId} key={dayCardId} className={`admin-day-card admin-day-card-vacation ${isFocused ? 'focused-problem' : ''}`}>
                                                                <div className="admin-day-card-header"><strong>{d.toLocaleDateString("de-DE", { weekday: "long" })}, {formatDate(d)}</strong></div>
                                                                <div className="admin-day-content"><p className="vacation-indicator"><span role="img" aria-label="vacation">🏖️</span> {t('adminDashboard.onVacation', 'Im Urlaub')}{vacationOnThisDay.halfDay ? ` (${t('adminDashboard.halfDayShort', '½ Tag')})` : ''}{vacationOnThisDay.usesOvertime ? ` (${t('adminDashboard.overtimeVacationShort', 'ÜS')})` : ''}</p></div>
                                                            </div>
                                                        );
                                                    } else if (!singleDayEntryObject || (!singleDayEntryObject.workStart && !singleDayEntryObject.workEnd)) {
                                                        let showNewEntryButton = (expected != null && expected > 0) || (userData.userConfig.isPercentage && (dayOfWeek !== 0 && dayOfWeek !== 6));
                                                        const relevantEffectiveStartDate = userData.userConfig.scheduleEffectiveDate ? new Date(userData.userConfig.scheduleEffectiveDate + "T00:00:00") : null;
                                                        if (relevantEffectiveStartDate && currentDayDate < relevantEffectiveStartDate) {
                                                            showNewEntryButton = false;
                                                        }
                                                        return (
                                                            <div id={dayCardId} key={dayCardId} className={`admin-day-card ${isFocused ? 'focused-problem' : ''} ${isAutoCompleted ? 'auto-completed-day-card' : ''}`}>
                                                                <div className="admin-day-card-header"><strong>{d.toLocaleDateString("de-DE", { weekday: "long" })}, {formatDate(d)}</strong></div>
                                                                <div className="admin-day-content">
                                                                    <p className="no-entries">{t('adminDashboard.noEntries')}</p>
                                                                    {showNewEntryButton ? (
                                                                        <button className="edit-day-button new-entry" onClick={() => openNewEntryModal(userData.username, d)}>{t('adminDashboard.newEntryButton')}</button>
                                                                    ) : null}
                                                                </div>
                                                            </div>
                                                        );
                                                    } else {
                                                        const dailyDiffStr = computeDailyDiff([singleDayEntryObject], expected, userData.userConfig.isHourly);
                                                        return (
                                                            <div id={dayCardId} key={dayCardId} className={`admin-day-card ${isFocused ? 'focused-problem' : ''} ${isAutoCompleted ? 'auto-completed-day-card' : ''}`}>
                                                                <div className="admin-day-card-header">
                                                                    <strong>{d.toLocaleDateString("de-DE", { weekday: "long" })}, {formatDate(d)}{expected !== null && !userData.userConfig.isHourly && <span className="expected-hours"> (Soll: {expected}h)</span>}</strong>
                                                                    {!userData.userConfig.isHourly && dailyDiffStr && <span className={`daily-diff ${computeDailyDiffValue([singleDayEntryObject], expected, false) < 0 ? 'negative-balance' : 'positive-balance'}`}> ({dailyDiffStr})</span>}
                                                                    {isAutoCompleted && <span className="auto-completed-tag">AUTO</span>}
                                                                    <button className="edit-day-button" onClick={() => openEditModal(userData.username, d, [singleDayEntryObject])}>{t("adminDashboard.editButton")}</button>
                                                                </div>
                                                                <div className="admin-day-content">
                                                                    <ul className="time-entry-list">
                                                                        {singleDayEntryObject.workStart && <li><span className="entry-label">{getStatusLabel(1)}:</span> <span className={isLateTime(singleDayEntryObject.workStart) ? 'late-time' : ''}>{singleDayEntryObject.workStart}</span></li>}
                                                                        {singleDayEntryObject.breakStart && <li><span className="entry-label">{getStatusLabel(2)}:</span> {singleDayEntryObject.breakStart}</li>}
                                                                        {singleDayEntryObject.breakEnd && <li><span className="entry-label">{getStatusLabel(3)}:</span> {singleDayEntryObject.breakEnd}</li>}
                                                                        {singleDayEntryObject.workEnd && <li><span className="entry-label">{getStatusLabel(4)}:</span> <span className={isLateTime(singleDayEntryObject.workEnd) ? 'late-time' : ''}>{singleDayEntryObject.workEnd}</span></li>}
                                                                    </ul>
                                                                    {userData.userConfig.isHourly && <p><strong>{t('totalTime', 'Gesamtzeit')}:</strong> {minutesToHHMM(computeDayTotalMinutesFromEntries([singleDayEntryObject]))}</p>}
                                                                </div>
                                                            </div>
                                                        );
                                                    }
                                                })}
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
                )}
            </section>
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
    onFocusProblemWeek: PropTypes.func.isRequired, // NEUE PropType-Validierung
    allTracks: PropTypes.array.isRequired,
    allVacations: PropTypes.arrayOf(
        PropTypes.shape({
            // ... (bestehende Vacation-Props)
        })
    ).isRequired,
    users: PropTypes.array.isRequired,
    defaultExpectedHours: PropTypes.number.isRequired,
    openEditModal: PropTypes.func.isRequired,
    openPrintUserModal: PropTypes.func.isRequired,
    weeklyBalances: PropTypes.array,
    openNewEntryModal: PropTypes.func.isRequired,
};

export default AdminWeekSection;