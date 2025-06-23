// src/pages/PercentageDashboard/percentageDashUtils.js
import { parseISO } from 'date-fns';

export function getMondayOfWeek(date) {
    const dateCopy = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    const day = dateCopy.getDay();
    const diff = dateCopy.getDate() - day + (day === 0 ? -6 : 1); // Adjust for Sunday being 0
    dateCopy.setDate(diff);
    dateCopy.setHours(0, 0, 0, 0);
    return dateCopy;
}

export function addDays(date, days) {
    const result = new Date(date);
    result.setDate(result.getDate() + days);
    return result;
}

export function formatLocalDate(date) { // Gibt YYYY-MM-DD zurück
    if (!date || !(date instanceof Date) || isNaN(date.getTime())) return "";
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}
export const formatISO = formatLocalDate;

export function formatDate(dateInput) { // Gibt DD.MM.YYYY zurück
    if (!dateInput) return "-";
    const date = (dateInput instanceof Date) ? dateInput : parseISO(dateInput);
    if (isNaN(date.getTime())) return "-";
    const day = String(date.getUTCDate()).padStart(2, '0');
    const month = String(date.getUTCMonth() + 1).padStart(2, '0');
    const year = date.getUTCFullYear();
    return `${day}.${month}.${year}`;
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
export const minutesToHours = minutesToHHMM;

export function formatPunchedTimeFromEntry(entry) {
    if (!entry || !entry.entryTimestamp) return '-';
    return formatTime(entry.entryTimestamp);
}

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