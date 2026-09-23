// src/pages/PercentageDashboard/percentageDashUtils.js
import {
    getMondayOfWeek,
    addDays,
    formatLocalDate,
    formatISO,
    formatDate,
} from '../../utils/dateUtils';
import {
    formatTime,
    minutesToHHMM,
    minutesToHours,
    formatPunchedTimeFromEntry,
    isLateTime,
    sortEntries,
} from '../../utils/timeUtils';

export {
    getMondayOfWeek,
    addDays,
    formatLocalDate,
    formatISO,
    formatDate,
    formatTime,
    minutesToHHMM,
    minutesToHours,
    formatPunchedTimeFromEntry,
    isLateTime,
    sortEntries,
};

export function getWorkedMinutesFromSummary(dailySummary) {
    return dailySummary?.workedMinutes || 0;
}

export function computeTotalWorkedMinutesInRange(dailySummaries, startDate, endDate) {
    let totalMinutes = 0;
    const start = formatLocalDate(startDate);
    const end = formatLocalDate(endDate);

    dailySummaries.forEach(summary => {
        if (summary.date >= start && summary.date <= end) {
            totalMinutes += summary.workedMinutes || 0;
        }
    });
    return totalMinutes;
}

export function expectedDayMinutesForPercentageUser(userProfile, defaultFullDayHours = 8.5) {
    if (!userProfile || !userProfile.isPercentage) return 0;

    const workPercentage = userProfile.workPercentage || 100;
    const expectedWorkDaysPerWeek = normalizeExpectedWorkDays(userProfile.expectedWorkDays);

    if (expectedWorkDaysPerWeek <= 0) return 0;

    const weeklyFullTimeMinutes = defaultFullDayHours * 5 * 60;
    const userWeeklyTargetMinutes = weeklyFullTimeMinutes * (workPercentage / 100);

    return Math.round(userWeeklyTargetMinutes / expectedWorkDaysPerWeek);
}

export function normalizeExpectedWorkDays(expectedWorkDays) {
    const parsed = Number(expectedWorkDays);
    if (Number.isFinite(parsed) && parsed >= 1 && parsed <= 7) {
        return Math.trunc(parsed);
    }
    return 5;
}

export function isModeledPercentageWorkDay(date, expectedWorkDays) {
    if (!(date instanceof Date) || Number.isNaN(date.getTime())) return false;
    const modeledWorkDays = normalizeExpectedWorkDays(expectedWorkDays);
    const dayIndex = date.getDay() === 0 ? 7 : date.getDay();
    return dayIndex <= modeledWorkDays;
}

export function getDatesUpToReferenceDate(dates, referenceDate = new Date()) {
    if (!Array.isArray(dates)) return [];
    if (!(referenceDate instanceof Date) || Number.isNaN(referenceDate.getTime())) {
        return dates.filter(date => date instanceof Date && !Number.isNaN(date.getTime()));
    }

    const referenceIso = formatLocalDate(referenceDate);
    return dates.filter(date => (
        date instanceof Date
        && !Number.isNaN(date.getTime())
        && formatLocalDate(date) <= referenceIso
    ));
}

export function calculateExpectedPercentageMinutesForDates(
    userProfile,
    dates,
    {
        holidaysForUserCanton = {},
        vacationRequests = [],
        sickLeaves = [],
        holidayOptions = [],
        workedDateSet = new Set(),
        defaultFullDayHours = 8.5,
    } = {}
) {
    if (!userProfile?.isPercentage || !Array.isArray(dates)) return 0;

    const dailyExpectedMinutes = expectedDayMinutesForPercentageUser(userProfile, defaultFullDayHours);
    if (dailyExpectedMinutes <= 0) return 0;

    return dates.reduce((sum, dayObj) => {
        if (!isModeledPercentageWorkDay(dayObj, userProfile.expectedWorkDays)) return sum;

        const isoDate = formatLocalDate(dayObj);
        const hasTrackedEntriesToday = workedDateSet?.has?.(isoDate) === true;
        const vacationToday = hasTrackedEntriesToday
            ? null
            : vacationRequests.find(v => v.approved && isoDate >= v.startDate && isoDate <= v.endDate);
        const sickToday = sickLeaves.find(sl => isoDate >= sl.startDate && isoDate <= sl.endDate);
        const holidayName = holidaysForUserCanton?.[isoDate];
        const holidayHandling = holidayOptions
            .find(opt => opt?.holidayDate === isoDate)
            ?.holidayHandlingOption || 'PENDING_DECISION';

        let daySoll = dailyExpectedMinutes;
        if (holidayName && holidayHandling === 'DEDUCT_FROM_WEEKLY_TARGET') {
            daySoll = 0;
        } else if (!holidayName && vacationToday) {
            daySoll = vacationToday.halfDay ? Math.round(daySoll / 2) : 0;
        } else if (!holidayName && sickToday) {
            daySoll = sickToday.halfDay ? Math.round(daySoll / 2) : 0;
        }

        return sum + daySoll;
    }, 0);
}


export function parseHex16(hexString) {
    if (!hexString) return null;
    const clean = hexString.replace(/\s+/g, '');
    if (clean.length !== 32) return null;
    let output = '';
    for (let i = 0; i < 16; i++) {
        const byteHex = clean.slice(i * 2, i * 2 + 2);
        const val = parseInt(byteHex, 16);
        if (val !== 0) {
            output += String.fromCharCode(val);
        }
    }
    return output;
}
