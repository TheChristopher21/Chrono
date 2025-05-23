// adminDashboardUtils.js
//
// Hier liegen alle Hilfsfunktionen 1:1 aus deinem AdminDashboard-Code,
// damit du sie an zentraler Stelle wiederverwenden kannst.
// Du importierst sie in AdminDashboard.jsx, AdminWeekSection.jsx etc.

import {differenceInMinutes, parseISO} from "date-fns";

export function getMondayOfWeek(date) {
    const copy = new Date(date);
    const day = copy.getDay();
    const diff = day === 0 ? 6 : day - 1;
    copy.setDate(copy.getDate() - diff);
    copy.setHours(0, 0, 0, 0);
    return copy;
}

export function formatDate(dateInput) {
    const date = (dateInput instanceof Date) ? dateInput : new Date(dateInput);
    if (isNaN(date.getTime())) return "-";
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();
    return `${day}-${month}-${year}`;
}
export function timeToDate(dateStr, timeStr) {
    // → JS-Date an einem beliebigen Tag (UTC-safe ISO)
    return parseISO(`${dateStr}T${timeStr}`);
}

export function addDays(date, days) {
    const d = new Date(date);
    d.setDate(d.getDate() + days);
    return d;
}

export function formatTime(value) {
    if (!value) return '-';

    // ▸ Zeit-String ohne Datum (“HH:MM” oder “HH:MM:SS”)?
    if (/^\d{2}:\d{2}(:\d{2})?$/.test(value)) {
        return value.slice(0, 5);                 // “07:40:00” → “07:40”
    }

    // ▸ ISO-DateTime oder anderes parsebares Format
    const d = new Date(value);
    return isNaN(d.getTime())
        ? '-'
        : d.toLocaleTimeString('de-DE', {
            hour:   '2-digit',
            minute: '2-digit',
            timeZone: 'Europe/Berlin'
        });
}

export function formatLocalDateYMD(d) {
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

export function getMinutesSinceMidnight(datetimeStr) {
    if (!datetimeStr) return 0;
    const d = new Date(datetimeStr);
    return d.getHours() * 60 + d.getMinutes();
}

export function parseTimeToMinutes(timeStr) {
    if (!timeStr) return 0;
    const parts = timeStr.split(':');
    const hours = parseInt(parts[0], 10);
    const minutes = parseInt(parts[1], 10);
    return hours * 60 + minutes;
}

/**
 * Berechnet die Abweichung (IST - SOLL) in Minuten.
 * dayEntries = Array mit Punches (1..4). Falls isHourly, wird Pause abgezogen.
 */
export function computeDailyDiffValue(dayEntries, expectedWorkHours, isHourly) {
    // 1:1 dein Code aus AdminDashboard.jsx
    // (siehe Original)
    // ...
    if (isHourly) {
        const entryStart = dayEntries.find(e => e.punchOrder === 1);
        const entryBreakStart = dayEntries.find(e => e.punchOrder === 2);
        const entryBreakEnd = dayEntries.find(e => e.punchOrder === 3);
        const entryEnd = dayEntries.find(e => e.punchOrder === 4);

        if (entryStart && entryBreakStart && entryBreakEnd && entryEnd) {
            const workStartMins = getMinutesSinceMidnight(entryStart.startTime);
            const workEndMins = getMinutesSinceMidnight(entryEnd.endTime || entryEnd.startTime);
            let minutesWorked = workEndMins - workStartMins;

            let breakStartMins = entryBreakStart.breakStart
                ? parseTimeToMinutes(entryBreakStart.breakStart)
                : getMinutesSinceMidnight(entryBreakStart.startTime);
            let breakEndMins = entryBreakEnd.breakEnd
                ? parseTimeToMinutes(entryBreakEnd.breakEnd)
                : getMinutesSinceMidnight(entryBreakEnd.startTime);
            if (breakEndMins < breakStartMins) {
                breakEndMins += 24 * 60;
            }
            const breakDuration = breakEndMins - breakStartMins;
            const actualWorked = minutesWorked - breakDuration;
            const expectedMinutes = expectedWorkHours * 60;
            return actualWorked - expectedMinutes;
        } else {
            // Falls z.B. nicht alle Stempel existieren
            const entryStartFallback = dayEntries.find(e => e.punchOrder === 1);
            let entryEndFallback = dayEntries.find(e => e.punchOrder === 4);
            if (!entryEndFallback) {
                // Falls 4 nicht existiert, nimm 2
                entryEndFallback = dayEntries.find(e => e.punchOrder === 2);
            }
            if (entryStartFallback && entryEndFallback) {
                const startTime = new Date(entryStartFallback.startTime);
                const endTime = new Date(entryEndFallback.endTime || entryEndFallback.startTime);
                if (endTime.toDateString() !== startTime.toDateString()) {
                    const midnight = new Date(startTime);
                    midnight.setHours(24, 0, 0, 0);
                    const minutesWorked = (midnight - startTime) / 60000;
                    const expectedMinutes = expectedWorkHours * 60;
                    return minutesWorked - expectedMinutes;
                } else {
                    const workStartMins = getMinutesSinceMidnight(entryStartFallback.startTime);
                    const workEndMins = getMinutesSinceMidnight(endTime.toISOString());
                    const minutesWorked = workEndMins - workStartMins;
                    const expectedMinutes = expectedWorkHours * 60;
                    return minutesWorked - expectedMinutes;
                }
            }
            return 0;
        }
    }

    // Normalfall Festangestellte
    const entryStart = dayEntries.find(e => e.punchOrder === 1);
    const entryBreakStart = dayEntries.find(e => e.punchOrder === 2);
    const entryBreakEnd = dayEntries.find(e => e.punchOrder === 3);
    const entryEnd = dayEntries.find(e => e.punchOrder === 4);
    if (entryStart && entryBreakStart && entryBreakEnd && entryEnd) {
        const workStartMins = getMinutesSinceMidnight(entryStart.startTime);
        let workEndMins = getMinutesSinceMidnight(entryEnd.endTime);
        if (workEndMins < workStartMins) {
            workEndMins += 24 * 60;
        }
        const workDuration = workEndMins - workStartMins;

        let breakStartMins = entryBreakStart.breakStart
            ? parseTimeToMinutes(entryBreakStart.breakStart)
            : getMinutesSinceMidnight(entryBreakStart.startTime);
        let breakEndMins = entryBreakEnd.breakEnd
            ? parseTimeToMinutes(entryBreakEnd.breakEnd)
            : getMinutesSinceMidnight(entryBreakEnd.startTime);
        if (breakEndMins < breakStartMins) {
            breakEndMins += 24 * 60;
        }
        const breakDuration = breakEndMins - breakStartMins;
        const actualWorked = workDuration - breakDuration;
        const expectedMinutes = expectedWorkHours * 60;
        return actualWorked - expectedMinutes;
    }
    return 0;
}

/**
 * Summiert die gesamte (IST - SOLL)-Differenz für einen User.
 */
export function computeOverallDiffForUser(allTracks, username, userConfig, defaultExpectedHours) {
    const userEntries = allTracks.filter(e => e.username === username);

    // Nach Datum gruppieren
    const dayMap = {};
    userEntries.forEach(entry => {
        const isoDay = entry.startTime.slice(0, 10);
        if (!dayMap[isoDay]) {
            dayMap[isoDay] = [];
        }
        dayMap[isoDay].push(entry);
    });

    let totalDiffMinutes = 0;
    for (const isoDay in dayMap) {
        const dayEntries = dayMap[isoDay];
        if (dayEntries.length > 0) {
            const someDate = new Date(dayEntries[0].startTime);
            const expected = getExpectedHoursForDay(someDate, userConfig, defaultExpectedHours);
            const diffMins = computeDailyDiffValue(dayEntries, expected, userConfig.isHourly);
            totalDiffMinutes += diffMins;
        }
    }
    return totalDiffMinutes;
}

export function computeDailyDiff(dayEntries, expectedWorkHours, isHourly) {
    const diff = computeDailyDiffValue(dayEntries, expectedWorkHours, isHourly);
    const sign = diff >= 0 ? '+' : '';
    return `${sign}${Math.round(diff)} min`;
}

export function getExpectedHoursForDay(dayObj, userConfig, defaultExpectedHours) {
    // ⏱️ Stundenbasierte Nutzer: kein Tages-Soll
    if (userConfig?.isHourly) return 0;

    // 🧮 Prozent-Nutzer: Wochenmodell → kein Tages-Soll anzeigen
    if (userConfig?.isPercentage) return null;

    // 📅 Klassische Arbeitszeitnutzung → berechne echtes Tages-Soll
    let expectedForDay = defaultExpectedHours;

    if (userConfig?.weeklySchedule && userConfig?.scheduleCycle) {
        try {
            const epoch = new Date(2020, 0, 1);
            const diffWeeks = Math.floor((dayObj - epoch) / (7 * 24 * 60 * 60 * 1000));
            const cycleIndex = diffWeeks % userConfig.scheduleCycle;
            const dayOfWeek = dayObj.toLocaleDateString('en-US', { weekday: 'long' }).toLowerCase();
            const value = userConfig.weeklySchedule[cycleIndex]?.[dayOfWeek];

            if (!isNaN(value)) expectedForDay = Number(value);
        } catch (e) {
            console.warn("getExpectedHoursForDay: Fehler beim Schedule-Parsing", e);
        }
    }

    return expectedForDay;
}


export function getStatusLabel(punchOrder) {
    switch (punchOrder) {
        case 1:
            return 'Work Start';
        case 2:
            return 'Break Start';
        case 3:
            return 'Break End';
        case 4:
            return 'Work End';
        default:
            return '';
    }
}

/**
 * Holt die reine Arbeitszeit (IST-Zeit) eines Tages:
 * (Nicht identisch mit computeDailyDiffValue, hier wird nur die
 * tatsächlich gestempelte Zeit berechnet).
 */
export function computeDayTotalMinutes(day) {
    // WorkEnd − WorkStart − Pausendauer
    const { workStart, breakStart, breakEnd, workEnd, dailyDate } = day;
    if (!workStart || !workEnd) return 0;

    let minutes = differenceInMinutes(
        timeToDate(dailyDate, workEnd),
        timeToDate(dailyDate, workStart)
    );

    if (breakStart && breakEnd) {
        minutes -= differenceInMinutes(
            timeToDate(dailyDate, breakEnd),
            timeToDate(dailyDate, breakStart)
        );
    }
    return Math.max(0, minutes);
}

export function minutesToHHMM(min) {
    const h = String(Math.floor(min / 60)).padStart(2, '0');
    const m = String(min % 60).padStart(2, '0');
    return `${h}:${m}`;
}

export function computeTotalMinutesInRange(allEntries, startDate, endDate) {
    const filtered = allEntries.filter(e => {
        const d = new Date(e.startTime);
        return d >= startDate && d <= endDate;
    });
    const dayMap = {};
    filtered.forEach(entry => {
        const ds = entry.startTime.slice(0, 10);
        if (!dayMap[ds]) dayMap[ds] = [];
        dayMap[ds].push(entry);
    });
    let total = 0;
    Object.keys(dayMap).forEach(ds => {
        total += computeDayTotalMinutes(dayMap[ds]);
    });
    return total;
}
// /utils/timeUtils.js
export function isLateTime(timeString) {
    const time = new Date(`1970-01-01T${timeString}`);
    const lateStart = new Date('1970-01-01T22:30:00');
    const lateEnd = new Date('1970-01-01T23:40:00');
    return time >= lateStart && time <= lateEnd;
}
