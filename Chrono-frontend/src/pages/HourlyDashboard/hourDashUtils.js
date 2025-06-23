// src/pages/HourlyDashboard/hourDashUtils.js
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
    const date = (dateInput instanceof Date) ? dateInput : new Date(dateInput);
    if (isNaN(date.getTime())) return "-";
    // KORREKTUR: Lokale Datums-Methoden verwenden
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();
    return `${day}.${month}.${year}`;
}

export function formatTime(dateInput) {
    // Stellt sicher, dass der Input ein gültiger String ist (z.B. "2025-05-19T10:00:00")
    if (!dateInput || typeof dateInput !== 'string' || dateInput.length < 16) {
        // Falls kein gültiger Zeitstempel vorhanden ist, gib "--:--" zurück.
        return "--:--";
    }

    // Extrahiert den Zeit-Teil "HH:mm" direkt aus dem String.
    // Dies verhindert, dass der Browser eine Zeitzonen-Konvertierung durchführt.
    try {
        const timePart = dateInput.substring(11, 16);
        return timePart;
    } catch (error) {
        console.error("Fehler beim Extrahieren der Zeit:", dateInput, error);
        return "--:--";
    }
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