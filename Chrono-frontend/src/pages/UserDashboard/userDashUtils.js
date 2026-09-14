// src/pages/UserDashboard/userDashUtils.js
import { parseISO } from 'date-fns'; // parseISO wird für String-Datumskonvertierung verwendet
import {
    getMondayOfWeek,
    addDays,
    formatLocalDate,
    formatDate,
    formatDateWithWeekday,
} from '../../utils/dateUtils';
import {
    formatTime,
    formatPunchedTimeFromEntry,
    minutesToHHMM,
    isLateTime,
    sortEntries,
} from '../../utils/timeUtils';

export {
    getMondayOfWeek,
    addDays,
    formatLocalDate,
    formatDate,
    formatDateWithWeekday,
    formatTime,
    formatPunchedTimeFromEntry,
    minutesToHHMM,
    isLateTime,
    sortEntries,
};

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



export function getMinutesSinceMidnight(datetimeStr) {
    if (!datetimeStr) return 0;
    const d = parseISO(datetimeStr);
    if (isNaN(d.getTime())) return 0;
    return d.getHours() * 60 + d.getMinutes();
}


export function computeDailyDiffValue(dailySummary, expectedWorkHours) {
    if (!dailySummary || typeof dailySummary.workedMinutes !== 'number') return 0;
    if (typeof expectedWorkHours !== 'number' || isNaN(expectedWorkHours)) return dailySummary.workedMinutes;

    const expectedMinutes = Math.round(expectedWorkHours * 60);
    return dailySummary.workedMinutes - expectedMinutes;
}

export function formatDiffDecimal(diffInMinutes) {
    if (typeof diffInMinutes !== 'number' || isNaN(diffInMinutes)) {
        return "0.00h";
    }
    const sign = diffInMinutes >= 0 ? '+' : '-';
    const absHours = Math.abs(diffInMinutes) / 60;
    return `${sign}${absHours.toFixed(2)}h`;
}

function normalizeExpectedWorkDays(expectedWorkDays) {
    const parsed = Number(expectedWorkDays);
    if (Number.isFinite(parsed) && parsed >= 1 && parsed <= 7) {
        return Math.trunc(parsed);
    }
    return 5;
}

function isModeledPercentageWorkDay(dayObj, workDays) {
    const dayIndex = dayObj.getDay() === 0 ? 7 : dayObj.getDay();
    return dayIndex <= workDays;
}

function findApprovedVacation(vacations, isoDate) {
    return vacations?.find(v => v.approved && isoDate >= v.startDate && isoDate <= v.endDate);
}

function findSickLeave(sickLeaves, isoDate) {
    return sickLeaves?.find(sl => isoDate >= sl.startDate && isoDate <= sl.endDate);
}

export function getExpectedHoursForDay(
    dayObj,
    userProfile,
    defaultExpectedHours = 8.5,
    holidaysForUserCanton = null,
    userApprovedVacations = [],
    userSickLeaves = [],
    userHolidayOptionForThisDay = null,
    hasWorkedEntries = false
) {
    if (!userProfile || !dayObj || !(dayObj instanceof Date) || isNaN(dayObj.getTime())) {
        return defaultExpectedHours;
    }
    if (userProfile.isHourly) return 0;

    const isoDate = formatLocalDate(dayObj);
    const isHoliday = holidaysForUserCanton && holidaysForUserCanton[isoDate];
    const vacationToday = hasWorkedEntries ? null : findApprovedVacation(userApprovedVacations, isoDate);
    const sickToday = findSickLeave(userSickLeaves, isoDate);

    if (userProfile.isPercentage) {
        const workDays = normalizeExpectedWorkDays(userProfile.expectedWorkDays);
        const percentageFactor = (userProfile.workPercentage || 100) / 100;
        if (!isModeledPercentageWorkDay(dayObj, workDays)) return 0;

        const dailyExpectedMinutes = Math.round((defaultExpectedHours * 5 * 60 * percentageFactor) / workDays);
        if (isHoliday) {
            const handlingOption = userHolidayOptionForThisDay?.holidayHandlingOption || 'PENDING_DECISION';
            if (handlingOption === 'DEDUCT_FROM_WEEKLY_TARGET') return 0;
        }
        if (vacationToday) return vacationToday.halfDay ? (dailyExpectedMinutes / 2) / 60 : 0;
        if (sickToday) return sickToday.halfDay ? (dailyExpectedMinutes / 2) / 60 : 0;
        return dailyExpectedMinutes / 60;
    }

    const dayOfWeekJs = dayObj.getDay();
    const dayOfWeekName = ['sunday', 'monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday'][dayOfWeekJs];

    let expectedHours = (dayOfWeekJs === 0 || dayOfWeekJs === 6) ? 0 : defaultExpectedHours;
    if (userProfile.weeklySchedule && Array.isArray(userProfile.weeklySchedule) && userProfile.weeklySchedule.length > 0 && userProfile.scheduleCycle > 0) {
        try {
            const epochMonday = getMondayOfWeek(new Date("2020-01-06T00:00:00Z"));
            const currentDayMonday = getMondayOfWeek(dayObj);
            const diffMillis = currentDayMonday.getTime() - epochMonday.getTime();
            const weeksSinceEpoch = Math.floor(diffMillis / (1000 * 60 * 60 * 24 * 7));
            let cycleIndex = weeksSinceEpoch % userProfile.scheduleCycle;
            if (cycleIndex < 0) cycleIndex += userProfile.scheduleCycle;

            if (userProfile.weeklySchedule[cycleIndex] && typeof userProfile.weeklySchedule[cycleIndex][dayOfWeekName] === 'number') {
                expectedHours = userProfile.weeklySchedule[cycleIndex][dayOfWeekName];
            }
        } catch (e) {
            console.error("Error in getExpectedHoursForDay from schedule:", e);
        }
    }

    if (isHoliday) return 0;
    if (vacationToday) return vacationToday.halfDay ? expectedHours / 2 : 0;
    if (sickToday) return sickToday.halfDay ? expectedHours / 2 : 0;
    return expectedHours;
}
