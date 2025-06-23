// src/pages/UserDashboard/userDashUtils.js
import { parseISO } from 'date-fns'; // parseISO wird für String-Datumskonvertierung verwendet

export function getMondayOfWeek(date) {
    const copy = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    const day = copy.getDay();
    const diff = copy.getDate() - day + (day === 0 ? -6 : 1); // Montag ist Ziel
    copy.setDate(diff);
    copy.setHours(0, 0, 0, 0);
    return copy;
}

export function addDays(date, days) {
    const d = new Date(date);
    d.setDate(d.getDate() + days);
    return d;
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

export function formatTime(dateInput) {
    if (!dateInput) {
        return "--:--";
    }
    try {
        const date = new Date(dateInput);
        if (isNaN(date.getTime())) {
            return "--:--";
        }
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        return `${hours}:${minutes}`;
    } catch (error) {
        console.error("Fehler beim Formatieren der Zeit:", dateInput, error);
        return "--:--";
    }
}
export function formatPunchedTimeFromEntry(entry) {
    if (!entry || !entry.entryTimestamp) return '-';
    return formatTime(entry.entryTimestamp);
}


export function formatLocalDate(date) { // Gibt YYYY-MM-DD zurück
    if (!date || !(date instanceof Date) || isNaN(date.getTime())) return "";
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

export function formatDate(dateInput) {
    if (!dateInput) return "-";
    const date = (dateInput instanceof Date) ? dateInput : new Date(dateInput);

    if (isNaN(date.getTime())) return "-";

    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();
    return `${day}.${month}.${year}`;
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

export function minutesToHHMM(totalMinutes) {
    if (typeof totalMinutes !== 'number' || isNaN(totalMinutes)) {
        return "0h 0m";
    }
    const sign = totalMinutes < 0 ? "-" : "";
    const absMinutes = Math.abs(totalMinutes);
    const h = Math.floor(absMinutes / 60);
    const m = absMinutes % 60;
    return `${sign}${h}h ${String(m).padStart(2, '0')}m`;
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

export function isLateTime(timeString) {
    if (!timeString || typeof timeString !== 'string' || !timeString.includes(':')) return false;
    try {
        const [hours, minutes] = timeString.split(':').map(Number);
        if (isNaN(hours) || isNaN(minutes)) return false;
        return (hours === 23 && minutes >= 20 && minutes <= 25) || (hours === 22 && minutes >=55);
    } catch (e) {
        return false;
    }
}