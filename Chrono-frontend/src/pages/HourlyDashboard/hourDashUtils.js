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

// Konvertiert Datum zu "YYYY-MM-DD" für API-Requests oder interne Keys
export function formatLocalDate(date) {
    if (!date || !(date instanceof Date) || isNaN(date.getTime())) return "";
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}
// Behält formatISO für interne Logik, wo ISO-String-Format benötigt wird
export const formatISO = (d) => formatLocalDate(d);


// Formatiert ein Datumsobjekt oder einen ISO-String zu "DD.MM.YYYY" für die Anzeige
export function formatDate(dateInput) {
    if (!dateInput) return "-";
    const date = (dateInput instanceof Date) ? dateInput : parseISO(dateInput);
    if (isNaN(date.getTime())) return "-";
    const day = String(date.getUTCDate()).padStart(2, '0');
    const month = String(date.getUTCMonth() + 1).padStart(2, '0');
    const year = date.getUTCFullYear();
    return `${day}.${month}.${year}`;
}

// Formatiert ein Datumsobjekt oder einen ISO-String zu "HH:mm" für die Anzeige
export function formatTime(dateInput) {
    if (!dateInput) return "--:--";
    let dateToFormat;
    if (dateInput instanceof Date) {
        dateToFormat = dateInput;
    } else if (typeof dateInput === 'string') {
        const trimmed = dateInput.trim();
        if (/^\d{2}:\d{2}(:\d{2})?$/.test(trimmed)) { // Already HH:mm or HH:mm:ss
            return trimmed.slice(0, 5);
        }
        dateToFormat = parseISO(trimmed);
    } else {
        try {
            dateToFormat = new Date(dateInput);
        } catch (e) {
            console.warn("formatTime: Could not parse dateInput", dateInput, e);
            return "--:--";
        }
    }

    if (isNaN(dateToFormat.getTime())) {
        return "--:--";
    }
    const hours = String(dateToFormat.getHours()).padStart(2, '0');
    const minutes = String(dateToFormat.getMinutes()).padStart(2, '0');
    return `${hours}:${minutes}`;
}

// Konvertiert Minuten in "Xh YYm" Format
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
// Alias für Konsistenz mit älteren Teilen, falls noch irgendwo verwendet, aber minutesToHHMM ist präziser.
export const minutesToHours = minutesToHHMM;


// Extrahiert Zeit für die Anzeige aus einem TimeTrackingEntryDTO
export function formatPunchedTimeFromEntry(entry) {
    if (!entry || !entry.entryTimestamp) return '-';
    return formatTime(entry.entryTimestamp);
}

// Holt gearbeitete Minuten aus einem DailyTimeSummaryDTO
export function getWorkedMinutesFromSummary(dailySummary) {
    return dailySummary?.workedMinutes || 0;
}

// Berechnet die gesamten gearbeiteten Minuten in einem Zeitraum
// anhand der `workedMinutes` aus den `DailyTimeSummaryDTOs`.
export function computeTotalWorkedMinutesInRange(dailySummaries, startDate, endDate) {
    let totalMinutes = 0;
    const start = formatLocalDate(startDate); // YYYY-MM-DD
    const end = formatLocalDate(endDate);   // YYYY-MM-DD

    dailySummaries.forEach(summary => {
        if (summary.date >= start && summary.date <= end) {
            totalMinutes += summary.workedMinutes || 0;
        }
    });
    return totalMinutes;
}

// Berechnet das Tagessoll für prozentuale Mitarbeiter.
// Diese Funktion ist spezifisch für das PercentageDashboard.
export function expectedDayMinutesForPercentageUser(userProfile, defaultFullDayHours = 8.5) {
    if (!userProfile || !userProfile.isPercentage) return 0; // Nur für prozentuale User

    const workPercentage = userProfile.workPercentage || 100; // Fallback auf 100%
    const expectedWorkDaysPerWeek = userProfile.expectedWorkDays || 5; // Fallback auf 5 Tage

    if (expectedWorkDaysPerWeek <= 0) return 0; // Keine Arbeitstage, kein Soll

    // Annahme: defaultFullDayHours ist das Soll für einen 100%-Tag an einem Standardarbeitstag.
    // Das Wochensoll für einen 100%-Mitarbeiter wäre defaultFullDayHours * 5 (bei einer 5-Tage-Woche).
    const weeklyFullTimeMinutes = defaultFullDayHours * 5 * 60;
    const userWeeklyTargetMinutes = weeklyFullTimeMinutes * (workPercentage / 100);

    // Tagessoll ist das Wochensoll verteilt auf die erwarteten Arbeitstage.
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

// parseHex16 und pickTime sind hier nicht mehr relevant, da sie für die alte Datenstruktur waren.
// computeDayTotalMinutes (alte Version) wird durch getWorkedMinutesFromSummary ersetzt.