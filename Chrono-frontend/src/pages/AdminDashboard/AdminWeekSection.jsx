// src/pages/AdminDashboard/AdminWeekSection.jsx
import React, { useState } from "react";
import PropTypes from "prop-types";
import "../../styles/AdminDashboardScoped.css";

import {
    formatLocalDateYMD,
    formatDate,
    formatTime,
    getStatusLabel,
    getExpectedHoursForDay,
    computeDailyDiffValue,
    computeDailyDiff,
    computeDayTotalMinutes,
    computeTotalMinutesInRange,
    isLateTime,
} from "./adminDashboardUtils";

const AdminWeekSection = ({
                              t,
                              weekDates,
                              selectedMonday,
                              handlePrevWeek,
                              handleNextWeek,
                              handleWeekJump,
                              allTracks,
                              users,
                              expandedUsers,
                              setExpandedUsers,
                              defaultExpectedHours,
                              openEditModal,
                              openPrintUserModal,
                              weeklyBalances = [],
                              openNewEntryModal,
                          }) => {
    const [searchTerm, setSearchTerm] = useState("");

    /* ------------------------------------------------------------------ */
    /* 1) Woche als ISO-Strings vorbereiten                               */
    /* ------------------------------------------------------------------ */
    const weekISO = weekDates.map((d) => formatLocalDateYMD(d));

    /* ------------------------------------------------------------------ */
    /* 2) Tracks auf genau diese Woche einschränken                       */
    /* ------------------------------------------------------------------ */
    const filteredTracks = allTracks.filter((tt) =>
        weekISO.includes(tt.startTime.slice(0, 10))
    );

    /* ------------------------------------------------------------------ */
    /* 3) Mapping Username → Track-Liste                                  */
    /*     Zunächst NUR aus vorhandenen Tracks …                          */
    /* ------------------------------------------------------------------ */
    const tracksByUser = filteredTracks.reduce((acc, tt) => {
        if (!acc[tt.username]) acc[tt.username] = [];
        acc[tt.username].push(tt);
        return acc;
    }, {});

    /* ------------------------------------------------------------------ */
    /* 4) …und jetzt JEDEN bekannten User ergänzen                        */
    /* ------------------------------------------------------------------ */
    const userGroups = {};
    users.forEach((u) => {
        userGroups[u.username] = tracksByUser[u.username] || [];
    });

    /* ------------------------------------------------------------------ */
    /* 5) Alphabetische Sortierung + Suche                                */
    /* ------------------------------------------------------------------ */
    let userKeys = Object.keys(userGroups).sort((a, b) =>
        a.localeCompare(b)
    );

    if (searchTerm.trim() !== "") {
        userKeys = userKeys.filter((uname) =>
            uname.toLowerCase().includes(searchTerm.toLowerCase())
        );
    }

    /* ------------------------------------------------------------------ */
    /* 6) Expand / Collapse                                               */
    /* ------------------------------------------------------------------ */
    const toggleUserExpand = (uname) =>
        setExpandedUsers((p) => ({ ...p, [uname]: !p[uname] }));

    const allExpanded = userKeys.length > 0 && userKeys.every((u) => expandedUsers[u]);
    const toggleAll = () => {
        if (allExpanded) {
            setExpandedUsers({});
        } else {
            const obj = {};
            userKeys.forEach((u) => { obj[u] = true; });
            setExpandedUsers(obj);
        }
    };

    /* ------------------------------------------------------------------ */
    /* 7) Render                                                          */
    /* ------------------------------------------------------------------ */
    return (
        <div className="admin-dashboard scoped-dashboard">
            <section className="week-section">
                <h3>{t("adminDashboard.timeTrackingCurrentWeek")}</h3>

                {/* Navigation */}
                <div className="week-navigation">
                    <button onClick={handlePrevWeek}>
                        ← {t("adminDashboard.prevWeek")}
                    </button>
                    <input
                        type="date"
                        value={formatLocalDateYMD(selectedMonday)}
                        onChange={handleWeekJump}
                    />
                    <button onClick={handleNextWeek}>
                        {t("adminDashboard.nextWeek")} →
                    </button>
                </div>

                {/* Suche */}
                <div
                    className="user-search-bar mb-4 text-center"
                    style={{ marginBottom: "1rem", textAlign: "center" }}
                >
                    <input
                        type="text"
                        placeholder={t("adminDashboard.searchUser") || "Benutzer suchen…"}
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        className="max-w-xs p-2 border rounded"
                    />
                    <button
                        className="ml-2 px-3 py-2"
                        onClick={toggleAll}
                    >
                        {allExpanded
                            ? t('adminDashboard.collapseAll')
                            : t('adminDashboard.expandAll')}
                    </button>
                </div>

                {/* Leerer Zustand */}
                {userKeys.length === 0 ? (
                    <p>
                        {searchTerm.trim() === ""
                            ? t("adminDashboard.noEntriesThisWeek")
                            : t("adminDashboard.noMatch") || "Keine passenden Benutzer."}
                    </p>
                ) : (
                    <div
                        className="admin-user-groups scrollable-user-list overflow-y-auto p-2"
                        style={{
                            maxHeight: userKeys.length > 10 ? "500px" : "auto",
                        }}
                    >
                        {userKeys.map((username) => {
                            /* ---------------------------------------------------------- */
                            /* User-bezogene Konstanten                                   */
                            /* ---------------------------------------------------------- */
                            const userConfig =
                                users.find((u) => u.username === username) || {};
                            const userColor =
                                /^#[0-9A-F]{6}$/i.test(userConfig.color || "")
                                    ? userConfig.color
                                    : "#007BFF";
                            const tracks = userGroups[username]; // kann leer sein

                            /* Tag-Weise gruppieren */
                            const dayMap = {};
                            tracks.forEach((tt) => {
                                const iso = tt.startTime.slice(0, 10);
                                if (!dayMap[iso]) dayMap[iso] = [];
                                dayMap[iso].push(tt);
                            });

                            /* Wochen-Saldo */
                            let weekDiff = 0;
                            weekDates.forEach((d) => {
                                const iso = formatLocalDateYMD(d);
                                const expected = getExpectedHoursForDay(
                                    d,
                                    userConfig,
                                    defaultExpectedHours
                                );
                                if (dayMap[iso]?.length) {
                                    weekDiff += computeDailyDiffValue(
                                        dayMap[iso],
                                        expected,
                                        userConfig.isHourly
                                    );
                                }
                            });
                            const weekSign = weekDiff >= 0 ? "+" : "-";
                            const weekAbs = Math.abs(weekDiff);
                            const weekH = Math.floor(weekAbs / 60);
                            const weekM = weekAbs % 60;

                            // Arbeitszeit-Summen für Stundenlöhner
                            let weekWorked = 0;
                            let monthWorked = 0;
                            if (userConfig.isHourly) {
                                weekDates.forEach((d) => {
                                    const iso = formatLocalDateYMD(d);
                                    const dayEntries = dayMap[iso] || [];
                                    weekWorked += computeDayTotalMinutes(dayEntries);
                                });

                                const monthStart = new Date(selectedMonday.getFullYear(), selectedMonday.getMonth(), 1);
                                const monthEnd = new Date(selectedMonday.getFullYear(), selectedMonday.getMonth() + 1, 0, 23, 59, 59);
                                const userEntries = allTracks.filter(tt => tt.username === username);
                                monthWorked = computeTotalMinutesInRange(userEntries, monthStart, monthEnd);
                            }

                            /* Globale Tracking-Bilanz */
                            const tb =
                                weeklyBalances.find((wb) => wb.username === username)
                                    ?.trackingBalance ?? 0;
                            const tbSign = tb >= 0 ? "+" : "-";
                            const tbAbs = Math.abs(tb);
                            const tbH = Math.floor(tbAbs / 60);
                            const tbM = tbAbs % 60;

                            const expanded = !!expandedUsers[username];

                            return (
                                <div key={username} className="admin-user-block">
                                    {/* Kopf */}
                                    <div
                                        className="admin-user-header"
                                        style={{ backgroundColor: userColor }}
                                        onClick={() => toggleUserExpand(username)}
                                    >
                                        <h4 style={{ color: "#fff", margin: 0 }}>{username}</h4>
                                        <button className="edit-button">
                                            {expanded ? "–" : "+"}
                                        </button>
                                    </div>

                                    {/* Inhalt – kann leer sein */}
                                    {expanded && (
                                        <div className="admin-week-display">
                                            {/* Summen */}
                                            <div className="user-total-diff">
                                                <strong>{t("adminDashboard.total")} (Woche):</strong>{" "}
                                                {weekSign}
                                                {weekH}h {weekM}m
                                            </div>
                                            <div className="user-weekly-balance">
                                                <strong>{t('overtimeBalance')}:</strong>{" "}
                                                {tbSign}
                                                {tbH}h {tbM}m
                                            </div>

                                            {userConfig.isHourly && (
                                                <div className="hourly-summary">
                                                    <p>
                                                        <strong>{t('weeklyHours')}:</strong>{' '}
                                                        {Math.floor(weekWorked / 60)}h {weekWorked % 60}m
                                                    </p>
                                                    <p>
                                                        <strong>{t('monthlyHours')}:</strong>{' '}
                                                        {Math.floor(monthWorked / 60)}h {monthWorked % 60}m
                                                    </p>
                                                </div>
                                            )}

                                            {/* Tage der Woche */}
                                            {weekDates.map((d, idx) => {
                                                const iso = formatLocalDateYMD(d);
                                                const dayEntries = dayMap[iso] || [];
                                                const expected = getExpectedHoursForDay(
                                                    d,
                                                    userConfig,
                                                    defaultExpectedHours
                                                );

                                                /* -------- Keine Einträge – Button “Zeiten eintragen” */
                                                if (dayEntries.length === 0) {
                                                    return (
                                                        <div key={idx} className="admin-day-card">
                                                            <div className="admin-day-card-header">
                                                                <strong>
                                                                    {d.toLocaleDateString("de-DE", {
                                                                        weekday: "long",
                                                                    })}
                                                                    , {formatDate(d)}
                                                                </strong>
                                                            </div>
                                                            <div className="admin-day-content">
                                                                <p className="no-entries">
                                                                    {t('adminDashboard.noEntries')}
                                                                </p>
                                                                <button
                                                                    className="edit-day-button"
                                                                    style={{ background: "#2ecc71" }}
                                                                    onClick={() => openNewEntryModal(username, d)}
                                                                >
                                                                    {t('adminDashboard.newEntryButton')}
                                                                </button>
                                                            </div>
                                                        </div>
                                                    );
                                                }

                                                /* -------- Stempelliste (isHourly) */
                                                if (userConfig.isHourly) {
                                                    return (
                                                        <div key={idx} className="admin-day-card">
                                                            <div className="admin-day-card-header">
                                                                <strong>
                                                                    {d.toLocaleDateString("de-DE", {
                                                                        weekday: "long",
                                                                    })}
                                                                    , {formatDate(d)}
                                                                </strong>
                                                            </div>
                                                            <div className="admin-day-content">
                                                                <ul className="time-entry-list">
                                                                    {dayEntries
                                                                        .sort((a, b) => a.punchOrder - b.punchOrder)
                                                                        .map((e) => {
                                                                            let disp = "-";
                                                                            if (e.punchOrder === 1) {
                                                                                disp = formatTime(e.startTime);
                                                                            } else if (e.punchOrder === 2) {
                                                                                disp = e.breakStart
                                                                                    ? formatTime(e.breakStart)
                                                                                    : formatTime(e.startTime);
                                                                            } else if (e.punchOrder === 3) {
                                                                                disp = e.breakEnd
                                                                                    ? formatTime(e.breakEnd)
                                                                                    : formatTime(e.startTime);
                                                                            } else if (e.punchOrder === 4) {
                                                                                disp = formatTime(e.endTime);
                                                                            }
                                                                            return (
                                                                                <li
                                                                                    key={e.id}
                                                                                    className={
                                                                                        isLateTime(disp) ? "late-time" : ""
                                                                                    }
                                                                                >
                                          <span className="entry-label">
                                            {getStatusLabel(e.punchOrder)}:
                                          </span>{" "}
                                                                                    {disp}
                                                                                </li>
                                                                            );
                                                                        })}
                                                                </ul>
                                                            </div>
                                                        </div>
                                                    );
                                                }

                                                /* -------- Festangestellte mit Tagesdiff */
                                                const dailyDiff =
                                                    dayEntries.length >= 4
                                                        ? computeDailyDiff(
                                                            dayEntries,
                                                            expected,
                                                            false /* isHourly */
                                                        )
                                                        : "";

                                                return (
                                                    <div key={idx} className="admin-day-card">
                                                        <div className="admin-day-card-header">
                                                            <strong>
                                                                {d.toLocaleDateString("de-DE", {
                                                                    weekday: "long",
                                                                })}
                                                                , {formatDate(d)}
                                                                <span className="expected-hours">
                                  (
                                                                    {t("adminDashboard.expected") ||
                                                                        "Soll"}: {expected}h)
                                </span>
                                                            </strong>
                                                            {dailyDiff && (
                                                                <span className="daily-diff">
                                  ({dailyDiff})
                                </span>
                                                            )}
                                                            <button
                                                                className="edit-day-button"
                                                                onClick={() =>
                                                                    openEditModal(username, d, dayEntries)
                                                                }
                                                            >
                                                                {t("adminDashboard.editButton")}
                                                            </button>
                                                        </div>
                                                        <div className="admin-day-content">
                                                            <ul className="time-entry-list">
                                                                {dayEntries
                                                                    .sort(
                                                                        (a, b) => a.punchOrder - b.punchOrder
                                                                    )
                                                                    .map((e) => {
                                                                        let disp = "-";
                                                                        if (e.punchOrder === 1) {
                                                                            disp = formatTime(e.startTime);
                                                                        } else if (e.punchOrder === 2) {
                                                                            disp = e.breakStart
                                                                                ? formatTime(e.breakStart)
                                                                                : formatTime(e.startTime);
                                                                        } else if (e.punchOrder === 3) {
                                                                            disp = e.breakEnd
                                                                                ? formatTime(e.breakEnd)
                                                                                : formatTime(e.startTime);
                                                                        } else if (e.punchOrder === 4) {
                                                                            disp = formatTime(e.endTime);
                                                                        }
                                                                        return (
                                                                            <li
                                                                                key={e.id}
                                                                                className={
                                                                                    isLateTime(disp) ? "late-time" : ""
                                                                                }
                                                                            >
                                        <span className="entry-label">
                                          {getStatusLabel(e.punchOrder)}:
                                        </span>{" "}
                                                                                {disp}
                                                                            </li>
                                                                        );
                                                                    })}
                                                            </ul>
                                                        </div>
                                                    </div>
                                                );
                                            })}

                                            {/* Drucken */}
                                            <button
                                                className="print-times-button"
                                                onClick={() => openPrintUserModal(username)}
                                            >
                                                {t("adminDashboard.printButton") || "Zeiten Drucken"}
                                            </button>
                                        </div>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                )}
            </section>
        </div>
    );
};

AdminWeekSection.propTypes = {
    t: PropTypes.func.isRequired,
    weekDates: PropTypes.arrayOf(PropTypes.instanceOf(Date)).isRequired,
    weeklyBalances: PropTypes.array.isRequired,
    selectedMonday: PropTypes.instanceOf(Date).isRequired,
    handlePrevWeek: PropTypes.func.isRequired,
    handleNextWeek: PropTypes.func.isRequired,
    handleWeekJump: PropTypes.func.isRequired,
    allTracks: PropTypes.array.isRequired,
    users: PropTypes.array.isRequired,
    expandedUsers: PropTypes.object.isRequired,
    setExpandedUsers: PropTypes.func.isRequired,
    defaultExpectedHours: PropTypes.number.isRequired,
    openEditModal: PropTypes.func.isRequired,
    openPrintUserModal: PropTypes.func.isRequired,
    openNewEntryModal: PropTypes.func.isRequired,
};

export default AdminWeekSection;
