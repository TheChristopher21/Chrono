// adminDashboardUtils.js
//
// Hier liegen alle Hilfsfunktionen 1:1 aus deinem AdminDashboard-Code,
// damit du sie an zentraler Stelle wiederverwenden kannst.
// Du importierst sie in AdminDashboard.jsx, AdminWeekSection.jsx etc.

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

export function addDays(date, days) {
    const d = new Date(date);
    d.setDate(d.getDate() + days);
    return d;
}

export function formatTime(dateStr) {
    const d = new Date(dateStr);
    return isNaN(d.getTime())
        ? '-'
        : d.toLocaleTimeString('de-DE', {
            hour: '2-digit',
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
    if (userConfig?.isHourly) return 0;
    let expectedForDay = defaultExpectedHours;
    if (userConfig && userConfig.weeklySchedule && userConfig.scheduleCycle) {
        const epoch = new Date(2020, 0, 1);
        const diffWeeks = Math.floor((dayObj - epoch) / (7 * 24 * 60 * 60 * 1000));
        const cycleIndex = diffWeeks % userConfig.scheduleCycle;
        const dayOfWeek = dayObj.toLocaleDateString('en-US', { weekday: 'long' }).toLowerCase();
        if (Array.isArray(userConfig.weeklySchedule) && userConfig.weeklySchedule[cycleIndex]) {
            const scheduleValue = Number(userConfig.weeklySchedule[cycleIndex][dayOfWeek]);
            if (!isNaN(scheduleValue)) {
                expectedForDay = scheduleValue;
            }
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
export function computeDayTotalMinutes(dayEntries) {
    const entryStart = dayEntries.find(e => e.punchOrder === 1);
    let entryEnd = dayEntries.find(e => e.punchOrder === 4);
    if (!entryEnd) {
        entryEnd = dayEntries.find(e => e.punchOrder === 2 || e.punchOrder === 3);
    }
    if (!entryStart || !entryEnd) return 0;

    const start = new Date(entryStart.startTime);
    const end = new Date(entryEnd.endTime || entryEnd.startTime);
    let totalMins = 0;

    if (end.toDateString() !== start.toDateString()) {
        const midnight = new Date(start);
        midnight.setHours(24, 0, 0, 0);
        totalMins = (midnight - start) / 60000;
    } else {
        const startM = getMinutesSinceMidnight(start.toISOString());
        const endM = getMinutesSinceMidnight(end.toISOString());
        totalMins = endM - startM;
    }

    const breakStart = dayEntries.find(e => e.punchOrder === 2);
    const breakEnd = dayEntries.find(e => e.punchOrder === 3);
    if (breakStart && breakEnd) {
        let breakStartMins = breakStart.breakStart
            ? parseTimeToMinutes(breakStart.breakStart)
            : getMinutesSinceMidnight(breakStart.startTime);
        let breakEndMins = breakEnd.breakEnd
            ? parseTimeToMinutes(breakEnd.breakEnd)
            : getMinutesSinceMidnight(breakEnd.startTime);
        if (breakEndMins < breakStartMins) {
            breakEndMins += 24 * 60;
        }
        totalMins -= (breakEndMins - breakStartMins);
    }
    return Math.max(0, totalMins);
}
