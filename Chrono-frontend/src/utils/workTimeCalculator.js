export const DAY_KEYS = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"];

export const DAY_LABELS = {
    monday: "Mo",
    tuesday: "Di",
    wednesday: "Mi",
    thursday: "Do",
    friday: "Fr",
    saturday: "Sa",
    sunday: "So",
};

export const DAY_LABELS_BY_LANGUAGE = {
    de: DAY_LABELS,
    en: {
        monday: "Mon",
        tuesday: "Tue",
        wednesday: "Wed",
        thursday: "Thu",
        friday: "Fri",
        saturday: "Sat",
        sunday: "Sun",
    },
};

export const MONTH_LABELS = [
    "Januar",
    "Februar",
    "Maerz",
    "April",
    "Mai",
    "Juni",
    "Juli",
    "August",
    "September",
    "Oktober",
    "November",
    "Dezember",
];

export const MONTH_LABELS_BY_LANGUAGE = {
    de: MONTH_LABELS,
    en: [
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December",
    ],
};

export const DEFAULT_WEEK_PATTERN = {
    monday: { active: true, hours: 8.5 },
    tuesday: { active: true, hours: 8.5 },
    wednesday: { active: true, hours: 8.5 },
    thursday: { active: true, hours: 8.5 },
    friday: { active: true, hours: 8.5 },
    saturday: { active: false, hours: 0 },
    sunday: { active: false, hours: 0 },
};

export const toDate = (value) => {
    if (value instanceof Date) {
        return new Date(value.getFullYear(), value.getMonth(), value.getDate());
    }
    return new Date(`${value}T00:00:00`);
};

export const formatDateISO = (date) => {
    const parsed = toDate(date);
    const year = parsed.getFullYear();
    const month = String(parsed.getMonth() + 1).padStart(2, "0");
    const day = String(parsed.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
};

export const formatDateDisplay = (date, locale = "de-CH") => {
    const parsed = toDate(date);
    return parsed.toLocaleDateString(locale, {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
    });
};

export const addDays = (date, amount) => {
    const next = toDate(date);
    next.setDate(next.getDate() + amount);
    return next;
};

export const getDayKey = (date) => DAY_KEYS[(toDate(date).getDay() + 6) % 7];

export const getYearsBetween = (startDate, endDate) => {
    const { start, end } = clampRange(startDate, endDate);
    const years = [];
    for (let year = start.getFullYear(); year <= end.getFullYear(); year += 1) {
        years.push(year);
    }
    return years;
};

export const clampRange = (startDate, endDate) => {
    const start = toDate(startDate);
    const end = toDate(endDate);
    if (end < start) {
        return { start: end, end: start };
    }
    return { start, end };
};

export const buildHolidayMap = (apiHolidays = {}, customHolidays = [], disabledDates = []) => {
    const disabled = new Set(disabledDates);
    const holidays = new Map();

    Object.entries(apiHolidays ?? {}).forEach(([date, name]) => {
        if (!disabled.has(date)) {
            holidays.set(date, { date, name: name || "Feiertag", source: "Chrono" });
        }
    });

    customHolidays.forEach((holiday) => {
        if (holiday?.date && !disabled.has(holiday.date)) {
            holidays.set(holiday.date, {
                date: holiday.date,
                name: holiday.name?.trim() || "Eigener Feiertag",
                source: "Manuell",
            });
        }
    });

    return holidays;
};

export const calculateWorkTime = ({
    startDate,
    endDate,
    weekPattern = DEFAULT_WEEK_PATTERN,
    workloadPercent = 100,
    holidays = new Map(),
    preHolidayReductionHours = 0,
    language = "de",
}) => {
    const { start, end } = clampRange(startDate, endDate);
    const workloadFactor = Math.max(0, Number(workloadPercent || 0)) / 100;
    const reductionHours = Math.max(0, Number(preHolidayReductionHours || 0));
    const weeklyContractHours = DAY_KEYS.reduce((sum, dayKey) => {
        const pattern = weekPattern[dayKey] ?? { active: false, hours: 0 };
        return sum + (pattern.active ? Math.max(0, Number(pattern.hours || 0)) * workloadFactor : 0);
    }, 0);
    const days = [];
    const monthMap = new Map();
    let cursor = start;

    const summary = {
        calendarDays: 0,
        grossWorkdays: 0,
        netWorkdays: 0,
        holidayCount: 0,
        holidayWorkdays: 0,
        grossHours: 0,
        holidayHours: 0,
        preHolidayReductionHours: 0,
        targetHours: 0,
        weeklyContractHours,
        averageNetWeeklyHours: 0,
    };

    while (cursor <= end) {
        const iso = formatDateISO(cursor);
        const dayKey = getDayKey(cursor);
        const pattern = weekPattern[dayKey] ?? { active: false, hours: 0 };
        const baseHours = pattern.active ? Math.max(0, Number(pattern.hours || 0)) : 0;
        const scheduledHours = baseHours * workloadFactor;
        const holiday = holidays.get(iso) ?? null;
        const nextDate = addDays(cursor, 1);
        const nextDayKey = getDayKey(nextDate);
        const nextPattern = weekPattern[nextDayKey] ?? { active: false, hours: 0 };
        const nextScheduledHours = nextPattern.active
            ? Math.max(0, Number(nextPattern.hours || 0)) * workloadFactor
            : 0;
        const nextHoliday = holidays.get(formatDateISO(nextDate)) ?? null;
        const isWorkday = scheduledHours > 0;
        const holidayDeduction = isWorkday && holiday ? scheduledHours : 0;
        const preHolidayReduction = isWorkday && !holiday && nextHoliday && nextScheduledHours > 0
            ? Math.min(scheduledHours, reductionHours)
            : 0;
        const targetHours = Math.max(0, scheduledHours - holidayDeduction - preHolidayReduction);

        const dayRecord = {
            date: iso,
            monthKey: `${cursor.getFullYear()}-${String(cursor.getMonth() + 1).padStart(2, "0")}`,
            monthLabel: `${(MONTH_LABELS_BY_LANGUAGE[language] ?? MONTH_LABELS)[cursor.getMonth()]} ${cursor.getFullYear()}`,
            dayKey,
            dayLabel: (DAY_LABELS_BY_LANGUAGE[language] ?? DAY_LABELS)[dayKey],
            scheduledHours,
            targetHours,
            holiday,
            holidayDeduction,
            preHolidayReduction,
            isWorkday,
        };

        days.push(dayRecord);
        summary.calendarDays += 1;
        if (isWorkday) {
            summary.grossWorkdays += 1;
            summary.grossHours += scheduledHours;
        }
        if (holiday) {
            summary.holidayCount += 1;
        }
        if (holidayDeduction > 0) {
            summary.holidayWorkdays += 1;
            summary.holidayHours += holidayDeduction;
        }
        if (targetHours > 0) {
            summary.netWorkdays += 1;
        }
        summary.preHolidayReductionHours += preHolidayReduction;
        summary.targetHours += targetHours;

        const month = monthMap.get(dayRecord.monthKey) ?? {
            key: dayRecord.monthKey,
            label: dayRecord.monthLabel,
            calendarDays: 0,
            grossWorkdays: 0,
            netWorkdays: 0,
            holidayWorkdays: 0,
            grossHours: 0,
            holidayHours: 0,
            preHolidayReductionHours: 0,
            targetHours: 0,
        };
        month.calendarDays += 1;
        if (isWorkday) {
            month.grossWorkdays += 1;
            month.grossHours += scheduledHours;
        }
        if (holidayDeduction > 0) {
            month.holidayWorkdays += 1;
            month.holidayHours += holidayDeduction;
        }
        if (targetHours > 0) {
            month.netWorkdays += 1;
        }
        month.preHolidayReductionHours += preHolidayReduction;
        month.targetHours += targetHours;
        monthMap.set(dayRecord.monthKey, month);

        cursor = addDays(cursor, 1);
    }

    const weeks = summary.calendarDays / 7;
    summary.averageNetWeeklyHours = weeks > 0 ? summary.targetHours / weeks : 0;
    summary.averageWeeklyHours = summary.averageNetWeeklyHours;

    return {
        days,
        months: Array.from(monthMap.values()),
        summary,
    };
};

export const roundHours = (value) => Math.round((Number(value || 0) + Number.EPSILON) * 100) / 100;
