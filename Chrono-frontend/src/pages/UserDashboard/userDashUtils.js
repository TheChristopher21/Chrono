// userDashUtils.js

export function getMondayOfWeek(date) {
    const copy = new Date(date);
    const day = copy.getDay();
    const diff = day === 0 ? 6 : day - 1;
    copy.setDate(copy.getDate() - diff);
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

/**
 * Wandelt die vom Backend gelieferten Tagesobjekte in die bisher
 * genutzte Event-Liste um, damit die bestehende Logik weiterhin
 * funktioniert. Jeder Tag wird in bis zu vier Einträge aufgeteilt
 * (punchOrder 1–4).
 */
export function expandDayRows(days) {
    const result = [];
    days.forEach((day) => {
        const base = day.id ? day.id * 10 : Math.floor(Math.random() * 1e6);
        const date = day.dailyDate;
        if (day.workStart) {
            result.push({
                ...day,
                id: `${base + 1}`,
                punchOrder: 1,
                startTime: `${date}T${day.workStart}`,
            });
        }
        if (day.breakStart) {
            result.push({
                ...day,
                id: `${base + 2}`,
                punchOrder: 2,
                startTime: `${date}T${day.breakStart}`,
                breakStart: day.breakStart,
            });
        }
        if (day.breakEnd) {
            result.push({
                ...day,
                id: `${base + 3}`,
                punchOrder: 3,
                startTime: `${date}T${day.breakEnd}`,
                breakEnd: day.breakEnd,
            });
        }
        if (day.workEnd) {
            result.push({
                ...day,
                id: `${base + 4}`,
                punchOrder: 4,
                startTime: `${date}T${day.workEnd}`,
                endTime: `${date}T${day.workEnd}`,
                workEnd: day.workEnd,
            });
        }
    });
    return result;
}

// userDashUtils.js
export function formatTime(dateOrTimeStr) {
    if (!dateOrTimeStr) return '-';

    const trimmed = dateOrTimeStr.trim();

    /* Akzeptiere jetzt sowohl “HH:mm”   als auch “HH:mm:ss” */
    if (/^\d{2}:\d{2}(:\d{2})?$/.test(trimmed)) {
        // Wir zeigen nur Stunden:Minuten an
        return trimmed.slice(0, 5);      // → "07:40"
    }

    /* Fallback für ISO-Date-Strings (2025-05-23T09:17:00Z …) */
    const d = new Date(trimmed);
    return isNaN(d.getTime())
        ? '-'
        : d.toLocaleTimeString('de-DE', {
            hour:   '2-digit',
            minute: '2-digit',
            timeZone: 'Europe/Berlin',
        });
}


export function formatLocalDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

export function formatDate(dateStr) {
    const date = new Date(dateStr);
    if (isNaN(date.getTime())) return '-';
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();
    return `${day}-${month}-${year}`;
}

export function getMinutesSinceMidnight(datetimeStr) {
    if (!datetimeStr) return 0;
    const d = new Date(datetimeStr);
    return d.getHours() * 60 + d.getMinutes();
}

/**
 * Rechnet die tatsächlich gearbeiteten Minuten minus die erwarteten Minuten aus.
 * dayEntries = { startTime, endTime, breakStart, breakEnd, punchOrder }
 * expectedWorkHours (z.B. 8.5 => 8 Stunden 30 Min)
 */
export function computeDailyDiffValue(dayEntries, expectedWorkHours) {
    const entryStart = dayEntries.find(e => e.punchOrder === 1);
    const entryBreakStart = dayEntries.find(e => e.punchOrder === 2);
    const entryBreakEnd = dayEntries.find(e => e.punchOrder === 3);
    const entryEnd = dayEntries.find(e => e.punchOrder === 4);

    if (entryStart && entryBreakStart && entryBreakEnd && entryEnd) {
        const workStartMins = getMinutesSinceMidnight(entryStart.startTime);
        const workEndMins = getMinutesSinceMidnight(entryEnd.endTime);
        const breakStartMins = entryBreakStart.breakStart
            ? parseInt(entryBreakStart.breakStart.slice(0, 2), 10) * 60 +
            parseInt(entryBreakStart.breakStart.slice(3, 5), 10)
            : getMinutesSinceMidnight(entryBreakStart.startTime);
        const breakEndMins = entryBreakEnd.breakEnd
            ? parseInt(entryBreakEnd.breakEnd.slice(0, 2), 10) * 60 +
            parseInt(entryBreakEnd.breakEnd.slice(3, 5), 10)
            : getMinutesSinceMidnight(entryBreakEnd.startTime);

        const workDuration = workEndMins - workStartMins;
        const breakDuration = breakEndMins - breakStartMins;
        const actualWorked = workDuration - breakDuration;

        const expectedMinutes = expectedWorkHours * 60;
        return actualWorked - expectedMinutes;
    }
    return 0;
}

export function formatDiffDecimal(diffInMinutes) {
    const sign = diffInMinutes >= 0 ? '+' : '-';
    const absHours = Math.abs(diffInMinutes) / 60;
    return `${sign}${absHours.toFixed(2)}h`;
}



export function getExpectedHoursForDay(dayObj, userConfig, defaultExpectedHours) {
    if (userConfig?.isHourly) return 0;

    // 🛑 NEU: Prozentnutzer → KEIN Tages-Soll
    if (userConfig?.isPercentage) return null;

    let expectedForDay = defaultExpectedHours;

    if (userConfig?.weeklySchedule && userConfig?.scheduleCycle) {
        const epoch = new Date(2020, 0, 1);
        const diffWeeks = Math.floor((dayObj - epoch) / (7 * 24 * 60 * 60 * 1000));
        const cycleIndex = diffWeeks % userConfig.scheduleCycle;
        const dayOfWeek = dayObj
            .toLocaleDateString('en-US', { weekday: 'long' })
            .toLowerCase();
        const value = userConfig.weeklySchedule[cycleIndex]?.[dayOfWeek];
        if (!isNaN(value)) expectedForDay = Number(value);
    }

    return expectedForDay;
}

export function getStatusLabel(punchOrder) {
    switch (punchOrder) {
        case 1: return 'Work Start';
        case 2: return 'Break Start';
        case 3: return 'Break End';
        case 4: return 'Work End';
        default: return '';
    }
}

export function groupEntriesByDay(entries) {
    const dayMap = {};
    entries.forEach(entry => {
        const ds = new Date(entry.startTime).toLocaleDateString('de-DE');
        if (!dayMap[ds]) {
            dayMap[ds] = [];
        }
        dayMap[ds].push(entry);
    });
    return dayMap;
}

// /utils/timeUtils.js
export function isLateTime(timeString) {
    const time = new Date(`1970-01-01T${timeString}`);
    const lateStart = new Date('1970-01-01T22:20:00');
    const lateEnd = new Date('1970-01-01T23:40:00');
    return time >= lateStart && time <= lateEnd;
}
