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
    const expectedWorkDaysPerWeek = userProfile.expectedWorkDays || 5;

    if (expectedWorkDaysPerWeek <= 0) return 0;

    const weeklyFullTimeMinutes = defaultFullDayHours * 5 * 60;
    const userWeeklyTargetMinutes = weeklyFullTimeMinutes * (workPercentage / 100);

    return Math.round(userWeeklyTargetMinutes / expectedWorkDaysPerWeek);
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