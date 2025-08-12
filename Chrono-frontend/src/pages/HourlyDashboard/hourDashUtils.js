// src/pages/HourlyDashboard/hourDashUtils.js
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

