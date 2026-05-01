import React, { useCallback, useEffect, useMemo, useState } from "react";
import Navbar from "../../components/Navbar.jsx";
import api from "../../utils/api.js";
import { useAuth } from "../../context/AuthContext.jsx";
import { useNotification } from "../../context/NotificationContext.jsx";
import {
    DAY_KEYS,
    DAY_LABELS,
    DEFAULT_WEEK_PATTERN,
    addDays,
    buildHolidayMap,
    calculateWorkTime,
    clampRange,
    formatDateDisplay,
    formatDateISO,
    getYearsBetween,
    roundHours,
    toDate,
} from "../../utils/workTimeCalculator.js";
import "../../styles/WorkTimeCalculator.css";

const CANTONS = [
    { value: "", label: "Allgemein Schweiz" },
    { value: "AG", label: "Aargau" },
    { value: "AI", label: "Appenzell I.Rh." },
    { value: "AR", label: "Appenzell A.Rh." },
    { value: "BE", label: "Bern" },
    { value: "BL", label: "Basel-Landschaft" },
    { value: "BS", label: "Basel-Stadt" },
    { value: "FR", label: "Freiburg" },
    { value: "GE", label: "Genf" },
    { value: "GL", label: "Glarus" },
    { value: "GR", label: "Graubuenden" },
    { value: "JU", label: "Jura" },
    { value: "LU", label: "Luzern" },
    { value: "NE", label: "Neuenburg" },
    { value: "NW", label: "Nidwalden" },
    { value: "OW", label: "Obwalden" },
    { value: "SG", label: "St. Gallen" },
    { value: "SH", label: "Schaffhausen" },
    { value: "SO", label: "Solothurn" },
    { value: "SZ", label: "Schwyz" },
    { value: "TG", label: "Thurgau" },
    { value: "TI", label: "Tessin" },
    { value: "UR", label: "Uri" },
    { value: "VD", label: "Waadt" },
    { value: "VS", label: "Wallis" },
    { value: "ZG", label: "Zug" },
    { value: "ZH", label: "Zuerich" },
];

const getCurrentYearRange = () => {
    const year = new Date().getFullYear();
    return {
        startDate: `${year}-01-01`,
        endDate: `${year}-12-31`,
    };
};

const getPresetRange = (preset) => {
    const today = new Date();
    const year = today.getFullYear();
    if (preset === "nextYear") {
        return { startDate: `${year + 1}-01-01`, endDate: `${year + 1}-12-31` };
    }
    if (preset === "quarter") {
        const quarterStartMonth = Math.floor(today.getMonth() / 3) * 3;
        const start = new Date(year, quarterStartMonth, 1);
        const end = new Date(year, quarterStartMonth + 3, 0);
        return { startDate: formatDateISO(start), endDate: formatDateISO(end) };
    }
    if (preset === "month") {
        const start = new Date(year, today.getMonth(), 1);
        const end = new Date(year, today.getMonth() + 1, 0);
        return { startDate: formatDateISO(start), endDate: formatDateISO(end) };
    }
    return getCurrentYearRange();
};

const sanitizeHours = (value) => {
    const number = Number(value);
    return Number.isFinite(number) ? Math.max(0, Math.min(24, number)) : 0;
};

const buildPatternFromUser = (currentUser) => {
    const firstSchedule = Array.isArray(currentUser?.weeklySchedule)
        ? currentUser.weeklySchedule[0]
        : null;
    const dailyWorkHours = sanitizeHours(currentUser?.dailyWorkHours ?? 8.5);

    return DAY_KEYS.reduce((pattern, dayKey, index) => {
        const fallbackActive = index < 5;
        const rawHours = firstSchedule?.[dayKey];
        const hours = rawHours !== undefined ? sanitizeHours(rawHours) : (fallbackActive ? dailyWorkHours : 0);
        pattern[dayKey] = {
            active: hours > 0,
            hours,
        };
        return pattern;
    }, {});
};

const getRangeForYearFetch = (year, startDate, endDate) => {
    const start = toDate(startDate);
    const end = toDate(endDate);
    const yearStart = toDate(`${year}-01-01`);
    const yearEnd = toDate(`${year}-12-31`);
    return {
        startDate: formatDateISO(start > yearStart ? start : yearStart),
        endDate: formatDateISO(end < yearEnd ? end : yearEnd),
    };
};

const formatHours = (value) => roundHours(value).toLocaleString("de-CH", {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
});

const csvCell = (value) => `"${String(value ?? "").replaceAll('"', '""')}"`;

const WorkTimeCalculatorPage = () => {
    const { currentUser } = useAuth();
    const { notify } = useNotification();
    const initialRange = getCurrentYearRange();
    const defaultCanton = currentUser?.companyCantonAbbreviation || currentUser?.canton || "";

    const [preset, setPreset] = useState("year");
    const [startDate, setStartDate] = useState(initialRange.startDate);
    const [endDate, setEndDate] = useState(initialRange.endDate);
    const [canton, setCanton] = useState(defaultCanton);
    const [weekPattern, setWeekPattern] = useState(DEFAULT_WEEK_PATTERN);
    const [workloadPercent, setWorkloadPercent] = useState(100);
    const [includeHolidays, setIncludeHolidays] = useState(true);
    const [preHolidayReductionHours, setPreHolidayReductionHours] = useState(0);
    const [apiHolidays, setApiHolidays] = useState({});
    const [customHolidays, setCustomHolidays] = useState([]);
    const [disabledHolidayDates, setDisabledHolidayDates] = useState([]);
    const [customHolidayDraft, setCustomHolidayDraft] = useState({ date: "", name: "" });
    const [loadingHolidays, setLoadingHolidays] = useState(false);
    const effectiveStartDate = startDate || initialRange.startDate;
    const effectiveEndDate = endDate || effectiveStartDate;
    const normalizedRange = useMemo(
        () => clampRange(effectiveStartDate, effectiveEndDate),
        [effectiveEndDate, effectiveStartDate]
    );
    const normalizedStartDate = formatDateISO(normalizedRange.start);
    const normalizedEndDate = formatDateISO(normalizedRange.end);

    useEffect(() => {
        if (defaultCanton) {
            setCanton(defaultCanton);
        }
    }, [defaultCanton]);

    const applyPreset = (nextPreset) => {
        setPreset(nextPreset);
        if (nextPreset !== "custom") {
            const nextRange = getPresetRange(nextPreset);
            setStartDate(nextRange.startDate);
            setEndDate(nextRange.endDate);
        }
    };

    const fetchHolidays = useCallback(async () => {
        if (!startDate || !endDate) {
            setApiHolidays({});
            return;
        }
        setLoadingHolidays(true);
        try {
            const years = getYearsBetween(normalizedStartDate, normalizedEndDate);
            const responses = await Promise.all(years.map((year) => {
                const range = getRangeForYearFetch(year, normalizedStartDate, normalizedEndDate);
                return api.get("/api/holidays/details", {
                    params: {
                        year,
                        cantonAbbreviation: canton || undefined,
                        startDate: range.startDate,
                        endDate: range.endDate,
                    },
                });
            }));

            const merged = {};
            responses.forEach((response) => {
                Object.entries(response.data ?? {}).forEach(([date, name]) => {
                    merged[date] = name;
                });
            });
            setApiHolidays(merged);
        } catch (error) {
            console.error("Feiertage konnten nicht geladen werden", error);
            setApiHolidays({});
            notify({ message: "Feiertage konnten nicht geladen werden.", type: "error" });
        } finally {
            setLoadingHolidays(false);
        }
    }, [canton, endDate, normalizedEndDate, normalizedStartDate, notify, startDate]);

    useEffect(() => {
        fetchHolidays();
    }, [fetchHolidays]);

    const holidayMap = useMemo(() => {
        if (!includeHolidays) {
            return new Map();
        }
        return buildHolidayMap(apiHolidays, customHolidays, disabledHolidayDates);
    }, [apiHolidays, customHolidays, disabledHolidayDates, includeHolidays]);

    const calculation = useMemo(() => calculateWorkTime({
        startDate: normalizedStartDate,
        endDate: normalizedEndDate,
        weekPattern,
        workloadPercent,
        holidays: holidayMap,
        preHolidayReductionHours,
    }), [holidayMap, normalizedEndDate, normalizedStartDate, preHolidayReductionHours, weekPattern, workloadPercent]);

    const allHolidayRows = useMemo(() => {
        const rows = new Map();
        Object.entries(apiHolidays).forEach(([date, name]) => {
            rows.set(date, { date, name, source: "Chrono" });
        });
        customHolidays.forEach((holiday) => {
            rows.set(holiday.date, { ...holiday, source: "Manuell" });
        });
        return Array.from(rows.values())
            .filter((holiday) => holiday.date >= normalizedStartDate && holiday.date <= normalizedEndDate)
            .sort((left, right) => left.date.localeCompare(right.date))
            .map((holiday) => ({
                ...holiday,
                enabled: includeHolidays && !disabledHolidayDates.includes(holiday.date),
                impact: calculation.days.find((day) => day.date === holiday.date)?.holidayDeduction ?? 0,
            }));
    }, [apiHolidays, calculation.days, customHolidays, disabledHolidayDates, includeHolidays, normalizedEndDate, normalizedStartDate]);

    const highlightedDays = useMemo(() => calculation.days
        .filter((day) => day.holiday || day.preHolidayReduction > 0)
        .slice(0, 10), [calculation.days]);

    const applyUserDefaults = () => {
        setWeekPattern(buildPatternFromUser(currentUser));
        setWorkloadPercent(currentUser?.workPercentage ?? 100);
        setCanton(currentUser?.companyCantonAbbreviation || currentUser?.canton || "");
    };

    const updateDay = (dayKey, patch) => {
        setWeekPattern((current) => ({
            ...current,
            [dayKey]: {
                ...current[dayKey],
                ...patch,
            },
        }));
    };

    const toggleHoliday = (date) => {
        setDisabledHolidayDates((current) => current.includes(date)
            ? current.filter((item) => item !== date)
            : [...current, date]);
    };

    const addCustomHoliday = () => {
        if (!customHolidayDraft.date) {
            notify({ message: "Bitte ein Datum fuer den eigenen Feiertag auswaehlen.", type: "warn" });
            return;
        }
        setCustomHolidays((current) => [
            ...current.filter((holiday) => holiday.date !== customHolidayDraft.date),
            {
                date: customHolidayDraft.date,
                name: customHolidayDraft.name || "Eigener Feiertag",
            },
        ]);
        setDisabledHolidayDates((current) => current.filter((date) => date !== customHolidayDraft.date));
        setCustomHolidayDraft({ date: "", name: "" });
    };

    const removeCustomHoliday = (date) => {
        setCustomHolidays((current) => current.filter((holiday) => holiday.date !== date));
    };

    const exportCsv = () => {
        const rows = [
            ["Monat", "Kalendertage", "Arbeitstage brutto", "Feiertage an Arbeitstagen", "Sollstunden brutto", "Feiertagsabzug", "Vortagsreduktion", "Sollstunden netto"],
            ...calculation.months.map((month) => [
                month.label,
                month.calendarDays,
                month.grossWorkdays,
                month.holidayWorkdays,
                formatHours(month.grossHours),
                formatHours(month.holidayHours),
                formatHours(month.preHolidayReductionHours),
                formatHours(month.targetHours),
            ]),
        ];
        const csv = rows.map((row) => row.map(csvCell).join(";")).join("\n");
        const blob = new Blob([`\ufeff${csv}`], { type: "text/csv;charset=utf-8" });
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = `chrono-arbeitszeit-${normalizedStartDate}-${normalizedEndDate}.csv`;
        link.click();
        URL.revokeObjectURL(url);
    };

    const summary = calculation.summary;
    const maxMonthHours = Math.max(...calculation.months.map((month) => month.targetHours), 1);

    return (
        <>
            <Navbar />
            <main className="worktime-calculator-page">
                <header className="worktime-calculator-hero">
                    <div>
                        <p className="worktime-kicker">Chrono Rechner</p>
                        <h1>Arbeitszeit-Rechner</h1>
                        <p>
                            Sollzeit, Arbeitstage, Feiertage, Pensum und Vortagsreduktionen in einer Ansicht.
                        </p>
                    </div>
                    <div className="worktime-hero-actions">
                        <button type="button" className="button-secondary" onClick={applyUserDefaults}>
                            Profilwerte uebernehmen
                        </button>
                        <button type="button" className="button-secondary" onClick={() => window.print()}>
                            Drucken
                        </button>
                        <button type="button" className="button-primary" onClick={exportCsv}>
                            CSV exportieren
                        </button>
                    </div>
                </header>

                <section className="worktime-layout" aria-label="Arbeitszeit-Rechner Eingaben und Ergebnis">
                    <aside className="worktime-settings-panel">
                        <div className="worktime-panel-header">
                            <h2>Einstellungen</h2>
                            <span>{loadingHolidays ? "Feiertage laden..." : "Bereit"}</span>
                        </div>

                        <div className="worktime-field">
                            <label htmlFor="worktimePreset">Zeitraum</label>
                            <select id="worktimePreset" value={preset} onChange={(event) => applyPreset(event.target.value)}>
                                <option value="year">Aktuelles Jahr</option>
                                <option value="nextYear">Naechstes Jahr</option>
                                <option value="quarter">Aktuelles Quartal</option>
                                <option value="month">Aktueller Monat</option>
                                <option value="custom">Eigener Zeitraum</option>
                            </select>
                        </div>

                        <div className="worktime-field-grid">
                            <div className="worktime-field">
                                <label htmlFor="worktimeStart">Von</label>
                                <input
                                    id="worktimeStart"
                                    type="date"
                                    value={startDate}
                                    onChange={(event) => {
                                        setPreset("custom");
                                        setStartDate(event.target.value);
                                    }}
                                />
                            </div>
                            <div className="worktime-field">
                                <label htmlFor="worktimeEnd">Bis</label>
                                <input
                                    id="worktimeEnd"
                                    type="date"
                                    value={endDate}
                                    onChange={(event) => {
                                        setPreset("custom");
                                        setEndDate(event.target.value);
                                    }}
                                />
                            </div>
                        </div>

                        <div className="worktime-field-grid">
                            <div className="worktime-field">
                                <label htmlFor="worktimeCanton">Kanton</label>
                                <select id="worktimeCanton" value={canton} onChange={(event) => setCanton(event.target.value)}>
                                    {CANTONS.map((option) => (
                                        <option key={option.value || "general"} value={option.value}>{option.label}</option>
                                    ))}
                                </select>
                            </div>
                            <div className="worktime-field">
                                <label htmlFor="workloadPercent">Pensum</label>
                                <div className="worktime-input-with-unit">
                                    <input
                                        id="workloadPercent"
                                        type="number"
                                        min="0"
                                        max="200"
                                        step="5"
                                        value={workloadPercent}
                                        onChange={(event) => setWorkloadPercent(event.target.value)}
                                    />
                                    <span>%</span>
                                </div>
                            </div>
                        </div>

                        <div className="worktime-switch-row">
                            <label htmlFor="includeHolidays">Feiertage beruecksichtigen</label>
                            <input
                                id="includeHolidays"
                                type="checkbox"
                                checked={includeHolidays}
                                onChange={(event) => setIncludeHolidays(event.target.checked)}
                            />
                        </div>

                        <div className="worktime-field">
                            <label htmlFor="preHolidayReduction">Zeitreduktion am Vortag</label>
                            <div className="worktime-input-with-unit">
                                <input
                                    id="preHolidayReduction"
                                    type="number"
                                    min="0"
                                    max="8.5"
                                    step="0.25"
                                    value={preHolidayReductionHours}
                                    onChange={(event) => setPreHolidayReductionHours(event.target.value)}
                                />
                                <span>Std.</span>
                            </div>
                        </div>

                        <div className="worktime-week-pattern">
                            <div className="worktime-subhead">
                                <h3>Arbeitswoche</h3>
                                <button
                                    type="button"
                                    className="worktime-link-button"
                                    onClick={() => setWeekPattern(DEFAULT_WEEK_PATTERN)}
                                >
                                    Standard
                                </button>
                            </div>
                            {DAY_KEYS.map((dayKey) => (
                                <div className="worktime-day-row" key={dayKey}>
                                    <label>
                                        <input
                                            type="checkbox"
                                            checked={weekPattern[dayKey]?.active ?? false}
                                            onChange={(event) => updateDay(dayKey, { active: event.target.checked })}
                                        />
                                        <span>{DAY_LABELS[dayKey]}</span>
                                    </label>
                                    <input
                                        type="number"
                                        min="0"
                                        max="24"
                                        step="0.25"
                                        value={weekPattern[dayKey]?.hours ?? 0}
                                        disabled={!weekPattern[dayKey]?.active}
                                        onChange={(event) => updateDay(dayKey, { hours: sanitizeHours(event.target.value) })}
                                    />
                                </div>
                            ))}
                        </div>
                    </aside>

                    <section className="worktime-results">
                        <div className="worktime-kpi-grid">
                            <article className="worktime-kpi primary">
                                <span>Sollstunden netto</span>
                                <strong>{formatHours(summary.targetHours)}</strong>
                                <small>{formatDateDisplay(normalizedStartDate)} - {formatDateDisplay(normalizedEndDate)}</small>
                            </article>
                            <article className="worktime-kpi">
                                <span>Arbeitstage</span>
                                <strong>{summary.netWorkdays}</strong>
                                <small>{summary.grossWorkdays} brutto</small>
                            </article>
                            <article className="worktime-kpi">
                                <span>Feiertagsabzug</span>
                                <strong>{formatHours(summary.holidayHours)}</strong>
                                <small>{summary.holidayWorkdays} auf Arbeitstagen</small>
                            </article>
                            <article className="worktime-kpi">
                                <span>Wochen-Soll</span>
                                <strong>{formatHours(summary.weeklyContractHours)}</strong>
                                <small>Ø netto im Zeitraum {formatHours(summary.averageNetWeeklyHours)} Std.</small>
                            </article>
                        </div>

                        <section className="worktime-month-section">
                            <div className="worktime-section-title">
                                <div>
                                    <h2>Monatsuebersicht</h2>
                                    <p>Brutto-Soll, Feiertagsabzug und Netto-Soll auf einen Blick.</p>
                                </div>
                            </div>
                            <div className="worktime-month-table-wrap">
                                <table className="worktime-month-table">
                                    <thead>
                                    <tr>
                                        <th>Monat</th>
                                        <th>Arbeitstage</th>
                                        <th>Feiertage</th>
                                        <th>Brutto</th>
                                        <th>Abzug</th>
                                        <th>Netto</th>
                                        <th aria-label="Netto-Balken" />
                                    </tr>
                                    </thead>
                                    <tbody>
                                    {calculation.months.map((month) => (
                                        <tr key={month.key}>
                                            <td>{month.label}</td>
                                            <td>{month.netWorkdays} / {month.grossWorkdays}</td>
                                            <td>{month.holidayWorkdays}</td>
                                            <td>{formatHours(month.grossHours)}</td>
                                            <td>{formatHours(month.holidayHours + month.preHolidayReductionHours)}</td>
                                            <td><strong>{formatHours(month.targetHours)}</strong></td>
                                            <td>
                                                <span
                                                    className="worktime-bar"
                                                    style={{ "--bar-width": `${Math.max(4, (month.targetHours / maxMonthHours) * 100)}%` }}
                                                />
                                            </td>
                                        </tr>
                                    ))}
                                    </tbody>
                                </table>
                            </div>
                        </section>

                        <section className="worktime-detail-grid">
                            <div className="worktime-holiday-panel">
                                <div className="worktime-section-title compact">
                                    <div>
                                        <h2>Feiertage</h2>
                                        <p>Einzelne Feiertage koennen fuer diese Berechnung abgewählt werden.</p>
                                    </div>
                                </div>
                                <div className="worktime-custom-holiday">
                                    <input
                                        type="date"
                                        value={customHolidayDraft.date}
                                        onChange={(event) => setCustomHolidayDraft((draft) => ({ ...draft, date: event.target.value }))}
                                    />
                                    <input
                                        type="text"
                                        value={customHolidayDraft.name}
                                        placeholder="Eigener Feiertag"
                                        onChange={(event) => setCustomHolidayDraft((draft) => ({ ...draft, name: event.target.value }))}
                                    />
                                    <button type="button" className="button-secondary" onClick={addCustomHoliday}>Hinzufuegen</button>
                                </div>
                                <div className="worktime-holiday-list">
                                    {allHolidayRows.length > 0 ? allHolidayRows.map((holiday) => (
                                        <div className="worktime-holiday-row" key={`${holiday.source}-${holiday.date}`}>
                                            <label>
                                                <input
                                                    type="checkbox"
                                                    checked={holiday.enabled}
                                                    disabled={!includeHolidays}
                                                    onChange={() => toggleHoliday(holiday.date)}
                                                />
                                                <span>
                                                    <strong>{holiday.name}</strong>
                                                    <small>{formatDateDisplay(holiday.date)} · {holiday.source}</small>
                                                </span>
                                            </label>
                                            <div className="worktime-holiday-impact">
                                                {formatHours(holiday.impact)} Std.
                                                {holiday.source === "Manuell" && (
                                                    <button type="button" onClick={() => removeCustomHoliday(holiday.date)}>Entfernen</button>
                                                )}
                                            </div>
                                        </div>
                                    )) : (
                                        <p className="worktime-empty">Keine Feiertage im gewaehlten Zeitraum.</p>
                                    )}
                                </div>
                            </div>

                            <div className="worktime-adjustment-panel">
                                <div className="worktime-section-title compact">
                                    <div>
                                        <h2>Relevante Tage</h2>
                                        <p>Feiertage und Tage mit Vortagsreduktion als schnelle Kontrolle.</p>
                                    </div>
                                </div>
                                <div className="worktime-adjustment-list">
                                    {highlightedDays.length > 0 ? highlightedDays.map((day) => (
                                        <div className="worktime-adjustment-row" key={day.date}>
                                            <div>
                                                <strong>{formatDateDisplay(day.date)}</strong>
                                                <span>{day.dayLabel}</span>
                                            </div>
                                            <p>
                                                {day.holiday
                                                    ? `${day.holiday.name}: -${formatHours(day.holidayDeduction)} Std.`
                                                    : `Vortagsreduktion: -${formatHours(day.preHolidayReduction)} Std.`}
                                            </p>
                                            <small>Netto {formatHours(day.targetHours)} Std.</small>
                                        </div>
                                    )) : (
                                        <p className="worktime-empty">Keine Abzuege oder Sondertage im Zeitraum.</p>
                                    )}
                                </div>
                            </div>
                        </section>
                    </section>
                </section>
            </main>
        </>
    );
};

export default WorkTimeCalculatorPage;
