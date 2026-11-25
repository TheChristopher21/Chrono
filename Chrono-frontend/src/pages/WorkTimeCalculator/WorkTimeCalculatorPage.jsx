import React, { useCallback, useContext, useEffect, useMemo, useState } from "react";
import Navbar from "../../components/Navbar.jsx";
import api from "../../utils/api.js";
import { useAuth } from "../../context/AuthContext.jsx";
import { LanguageContext, useTranslation } from "../../context/LanguageContext.jsx";
import { useNotification } from "../../context/NotificationContext.jsx";
import {
    DAY_KEYS,
    DAY_LABELS_BY_LANGUAGE,
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
    { value: "", label: "Allgemein Schweiz", labelEn: "General Switzerland" },
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

const formatHours = (value, locale = "de-CH") => roundHours(value).toLocaleString(locale, {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
});

const csvCell = (value) => `"${String(value ?? "").replaceAll('"', '""')}"`;

const WorkTimeCalculatorPage = () => {
    const { currentUser } = useAuth();
    const { notify } = useNotification();
    const { t } = useTranslation();
    const { language } = useContext(LanguageContext);
    const locale = language === "en" ? "en-US" : "de-CH";
    const dayLabels = DAY_LABELS_BY_LANGUAGE[language] ?? DAY_LABELS_BY_LANGUAGE.de;
    const displayHours = useCallback((value) => formatHours(value, locale), [locale]);
    const displayDate = useCallback((date) => formatDateDisplay(date, locale), [locale]);
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
            notify({ message: t("workTimeCalculator.holidaysLoadError", "Feiertage konnten nicht geladen werden."), type: "error" });
        } finally {
            setLoadingHolidays(false);
        }
    }, [canton, endDate, normalizedEndDate, normalizedStartDate, notify, startDate, t]);

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
        language,
    }), [holidayMap, language, normalizedEndDate, normalizedStartDate, preHolidayReductionHours, weekPattern, workloadPercent]);

    const allHolidayRows = useMemo(() => {
        const rows = new Map();
        Object.entries(apiHolidays).forEach(([date, name]) => {
            rows.set(date, { date, name, source: "Chrono", sourceType: "chrono" });
        });
        customHolidays.forEach((holiday) => {
            rows.set(holiday.date, { ...holiday, source: t("workTimeCalculator.manualSource", "Manuell"), sourceType: "manual" });
        });
        return Array.from(rows.values())
            .filter((holiday) => holiday.date >= normalizedStartDate && holiday.date <= normalizedEndDate)
            .sort((left, right) => left.date.localeCompare(right.date))
            .map((holiday) => ({
                ...holiday,
                enabled: includeHolidays && !disabledHolidayDates.includes(holiday.date),
                impact: calculation.days.find((day) => day.date === holiday.date)?.holidayDeduction ?? 0,
            }));
    }, [apiHolidays, calculation.days, customHolidays, disabledHolidayDates, includeHolidays, normalizedEndDate, normalizedStartDate, t]);

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
            notify({ message: t("workTimeCalculator.customHolidayDateRequired", "Bitte ein Datum für den eigenen Feiertag auswählen."), type: "warn" });
            return;
        }
        setCustomHolidays((current) => [
            ...current.filter((holiday) => holiday.date !== customHolidayDraft.date),
            {
                date: customHolidayDraft.date,
                name: customHolidayDraft.name || t("workTimeCalculator.customHolidayFallback", "Eigener Feiertag"),
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
            [
                t("workTimeCalculator.csv.month", "Monat"),
                t("workTimeCalculator.csv.calendarDays", "Kalendertage"),
                t("workTimeCalculator.csv.grossWorkdays", "Arbeitstage brutto"),
                t("workTimeCalculator.csv.holidayWorkdays", "Feiertage an Arbeitstagen"),
                t("workTimeCalculator.csv.grossHours", "Sollstunden brutto"),
                t("workTimeCalculator.csv.holidayDeduction", "Feiertagsabzug"),
                t("workTimeCalculator.csv.preHolidayReduction", "Vortagsreduktion"),
                t("workTimeCalculator.csv.netTargetHours", "Sollstunden netto"),
            ],
            ...calculation.months.map((month) => [
                month.label,
                month.calendarDays,
                month.grossWorkdays,
                month.holidayWorkdays,
                displayHours(month.grossHours),
                displayHours(month.holidayHours),
                displayHours(month.preHolidayReductionHours),
                displayHours(month.targetHours),
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
                        <p className="worktime-kicker">{t("workTimeCalculator.kicker", "Chrono Rechner")}</p>
                        <h1>{t("workTimeCalculator.title", "Arbeitszeit-Rechner")}</h1>
                        <p>
                            {t("workTimeCalculator.subtitle", "Sollzeit, Arbeitstage, Feiertage, Pensum und Vortagsreduktionen in einer Ansicht.")}
                        </p>
                    </div>
                    <div className="worktime-hero-actions">
                        <button type="button" className="button-secondary" onClick={applyUserDefaults}>
                            {t("workTimeCalculator.applyProfile", "Profilwerte übernehmen")}
                        </button>
                        <button type="button" className="button-secondary" onClick={() => window.print()}>
                            {t("workTimeCalculator.print", "Drucken")}
                        </button>
                        <button type="button" className="button-primary" onClick={exportCsv}>
                            {t("workTimeCalculator.exportCsv", "CSV exportieren")}
                        </button>
                    </div>
                </header>

                <section className="worktime-layout" aria-label={t("workTimeCalculator.layoutAria", "Arbeitszeit-Rechner Eingaben und Ergebnis")}>
                    <aside className="worktime-settings-panel">
                        <div className="worktime-panel-header">
                            <h2>{t("workTimeCalculator.settings", "Einstellungen")}</h2>
                            <span>{loadingHolidays ? t("workTimeCalculator.loadingHolidays", "Feiertage laden...") : t("workTimeCalculator.ready", "Bereit")}</span>
                        </div>

                        <div className="worktime-field">
                            <label htmlFor="worktimePreset">{t("workTimeCalculator.period", "Zeitraum")}</label>
                            <select id="worktimePreset" value={preset} onChange={(event) => applyPreset(event.target.value)}>
                                <option value="year">{t("workTimeCalculator.presets.currentYear", "Aktuelles Jahr")}</option>
                                <option value="nextYear">{t("workTimeCalculator.presets.nextYear", "Nächstes Jahr")}</option>
                                <option value="quarter">{t("workTimeCalculator.presets.currentQuarter", "Aktuelles Quartal")}</option>
                                <option value="month">{t("workTimeCalculator.presets.currentMonth", "Aktueller Monat")}</option>
                                <option value="custom">{t("workTimeCalculator.presets.custom", "Eigener Zeitraum")}</option>
                            </select>
                        </div>

                        <div className="worktime-field-grid">
                            <div className="worktime-field">
                                <label htmlFor="worktimeStart">{t("workTimeCalculator.from", "Von")}</label>
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
                                <label htmlFor="worktimeEnd">{t("workTimeCalculator.to", "Bis")}</label>
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
                                <label htmlFor="worktimeCanton">{t("workTimeCalculator.canton", "Kanton")}</label>
                                <select id="worktimeCanton" value={canton} onChange={(event) => setCanton(event.target.value)}>
                                    {CANTONS.map((option) => (
                                        <option key={option.value || "general"} value={option.value}>
                                            {language === "en" ? option.labelEn ?? option.label : option.label}
                                        </option>
                                    ))}
                                </select>
                            </div>
                            <div className="worktime-field">
                                <label htmlFor="workloadPercent">{t("workTimeCalculator.workload", "Pensum")}</label>
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
                            <label htmlFor="includeHolidays">{t("workTimeCalculator.includeHolidays", "Feiertage berücksichtigen")}</label>
                            <input
                                id="includeHolidays"
                                type="checkbox"
                                checked={includeHolidays}
                                onChange={(event) => setIncludeHolidays(event.target.checked)}
                            />
                        </div>

                        <div className="worktime-field">
                            <label htmlFor="preHolidayReduction">{t("workTimeCalculator.preHolidayReduction", "Zeitreduktion am Vortag")}</label>
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
                                <span>{t("workTimeCalculator.hoursShort", "Std.")}</span>
                            </div>
                        </div>

                        <div className="worktime-week-pattern">
                            <div className="worktime-subhead">
                                <h3>{t("workTimeCalculator.workWeek", "Arbeitswoche")}</h3>
                                <button
                                    type="button"
                                    className="worktime-link-button"
                                    onClick={() => setWeekPattern(DEFAULT_WEEK_PATTERN)}
                                >
                                    {t("workTimeCalculator.standard", "Standard")}
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
                                        <span>{dayLabels[dayKey]}</span>
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
                                <span>{t("workTimeCalculator.kpis.netTargetHours", "Sollstunden netto")}</span>
                                <strong>{displayHours(summary.targetHours)}</strong>
                                <small>{displayDate(normalizedStartDate)} - {displayDate(normalizedEndDate)}</small>
                            </article>
                            <article className="worktime-kpi">
                                <span>{t("workTimeCalculator.kpis.workdays", "Arbeitstage")}</span>
                                <strong>{summary.netWorkdays}</strong>
                                <small>{t("workTimeCalculator.kpis.grossWorkdays", "{{count}} brutto", { count: summary.grossWorkdays })}</small>
                            </article>
                            <article className="worktime-kpi">
                                <span>{t("workTimeCalculator.kpis.holidayDeduction", "Feiertagsabzug")}</span>
                                <strong>{displayHours(summary.holidayHours)}</strong>
                                <small>{t("workTimeCalculator.kpis.onWorkdays", "{{count}} auf Arbeitstagen", { count: summary.holidayWorkdays })}</small>
                            </article>
                            <article className="worktime-kpi">
                                <span>{t("workTimeCalculator.kpis.weeklyTarget", "Wochen-Soll")}</span>
                                <strong>{displayHours(summary.weeklyContractHours)}</strong>
                                <small>{t("workTimeCalculator.kpis.averageNet", "Ø netto im Zeitraum {{hours}} Std.", { hours: displayHours(summary.averageNetWeeklyHours) })}</small>
                            </article>
                        </div>

                        <section className="worktime-month-section">
                            <div className="worktime-section-title">
                                <div>
                                    <h2>{t("workTimeCalculator.monthOverviewTitle", "Monatsübersicht")}</h2>
                                    <p>{t("workTimeCalculator.monthOverviewText", "Brutto-Soll, Feiertagsabzug und Netto-Soll auf einen Blick.")}</p>
                                </div>
                            </div>
                            <div className="worktime-month-table-wrap">
                                <table className="worktime-month-table">
                                    <thead>
                                    <tr>
                                        <th>{t("workTimeCalculator.table.month", "Monat")}</th>
                                        <th>{t("workTimeCalculator.table.workdays", "Arbeitstage")}</th>
                                        <th>{t("workTimeCalculator.table.holidays", "Feiertage")}</th>
                                        <th>{t("workTimeCalculator.table.gross", "Brutto")}</th>
                                        <th>{t("workTimeCalculator.table.deduction", "Abzug")}</th>
                                        <th>{t("workTimeCalculator.table.net", "Netto")}</th>
                                        <th aria-label={t("workTimeCalculator.table.netBar", "Netto-Balken")} />
                                    </tr>
                                    </thead>
                                    <tbody>
                                    {calculation.months.map((month) => (
                                        <tr key={month.key}>
                                            <td>{month.label}</td>
                                            <td>{month.netWorkdays} / {month.grossWorkdays}</td>
                                            <td>{month.holidayWorkdays}</td>
                                            <td>{displayHours(month.grossHours)}</td>
                                            <td>{displayHours(month.holidayHours + month.preHolidayReductionHours)}</td>
                                            <td><strong>{displayHours(month.targetHours)}</strong></td>
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
                                        <h2>{t("workTimeCalculator.holidaysTitle", "Feiertage")}</h2>
                                        <p>{t("workTimeCalculator.holidaysText", "Einzelne Feiertage können für diese Berechnung abgewählt werden.")}</p>
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
                                        placeholder={t("workTimeCalculator.customHolidayPlaceholder", "Eigener Feiertag")}
                                        onChange={(event) => setCustomHolidayDraft((draft) => ({ ...draft, name: event.target.value }))}
                                    />
                                    <button type="button" className="button-secondary" onClick={addCustomHoliday}>{t("workTimeCalculator.add", "Hinzufügen")}</button>
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
                                                    <small>{displayDate(holiday.date)} · {holiday.source}</small>
                                                </span>
                                            </label>
                                            <div className="worktime-holiday-impact">
                                                {displayHours(holiday.impact)} {t("workTimeCalculator.hoursShort", "Std.")}
                                                {holiday.sourceType === "manual" && (
                                                    <button type="button" onClick={() => removeCustomHoliday(holiday.date)}>{t("workTimeCalculator.remove", "Entfernen")}</button>
                                                )}
                                            </div>
                                        </div>
                                    )) : (
                                        <p className="worktime-empty">{t("workTimeCalculator.noHolidays", "Keine Feiertage im gewählten Zeitraum.")}</p>
                                    )}
                                </div>
                            </div>

                            <div className="worktime-adjustment-panel">
                                <div className="worktime-section-title compact">
                                    <div>
                                        <h2>{t("workTimeCalculator.relevantDaysTitle", "Relevante Tage")}</h2>
                                        <p>{t("workTimeCalculator.relevantDaysText", "Feiertage und Tage mit Vortagsreduktion als schnelle Kontrolle.")}</p>
                                    </div>
                                </div>
                                <div className="worktime-adjustment-list">
                                    {highlightedDays.length > 0 ? highlightedDays.map((day) => (
                                        <div className="worktime-adjustment-row" key={day.date}>
                                            <div>
                                                <strong>{displayDate(day.date)}</strong>
                                                <span>{day.dayLabel}</span>
                                            </div>
                                            <p>
                                                {day.holiday
                                                    ? t("workTimeCalculator.holidayDeductionLine", "{{name}}: -{{hours}} Std.", { name: day.holiday.name, hours: displayHours(day.holidayDeduction) })
                                                    : t("workTimeCalculator.preHolidayReductionLine", "Vortagsreduktion: -{{hours}} Std.", { hours: displayHours(day.preHolidayReduction) })}
                                            </p>
                                            <small>{t("workTimeCalculator.netLine", "Netto {{hours}} Std.", { hours: displayHours(day.targetHours) })}</small>
                                        </div>
                                    )) : (
                                        <p className="worktime-empty">{t("workTimeCalculator.noAdjustments", "Keine Abzüge oder Sondertage im Zeitraum.")}</p>
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
