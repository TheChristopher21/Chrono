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

export function getExpectedHoursForDay(dayObj, userProfile, defaultExpectedHours = 8.5) {
    if (!userProfile || !dayObj || !(dayObj instanceof Date) || isNaN(dayObj.getTime())) {
        return defaultExpectedHours;
    }
    if (userProfile.isHourly) return 0;
    if (userProfile.isPercentage) {
        const workDays = userProfile.expectedWorkDays || 5;
        const percentageFactor = (userProfile.workPercentage || 100) / 100;
        if (workDays === 0) return 0;
        return (defaultExpectedHours / 5) * workDays * percentageFactor / workDays;
    }

    const dayOfWeekJs = dayObj.getDay();
    const dayOfWeekName = ['sunday', 'monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday'][dayOfWeekJs];

    if (userProfile.weeklySchedule && Array.isArray(userProfile.weeklySchedule) && userProfile.weeklySchedule.length > 0 && userProfile.scheduleCycle > 0) {
        try {
            const epochMonday = getMondayOfWeek(new Date("2020-01-06T00:00:00Z"));
            const currentDayMonday = getMondayOfWeek(dayObj);
            const diffMillis = currentDayMonday.getTime() - epochMonday.getTime();
            const weeksSinceEpoch = Math.floor(diffMillis / (1000 * 60 * 60 * 24 * 7));
            let cycleIndex = weeksSinceEpoch % userProfile.scheduleCycle;
            if (cycleIndex < 0) cycleIndex += userProfile.scheduleCycle;

            if (userProfile.weeklySchedule[cycleIndex] && typeof userProfile.weeklySchedule[cycleIndex][dayOfWeekName] === 'number') {
                return userProfile.weeklySchedule[cycleIndex][dayOfWeekName];
            }
        } catch (e) {
            console.error("Error in getExpectedHoursForDay from schedule:", e);
        }
    }
    return (dayOfWeekJs === 0 || dayOfWeekJs === 6) ? 0 : defaultExpectedHours;
}
