// src/pages/AdminDashboard/adminDashboardUtils.js
import { differenceInMinutes, parseISO } from "date-fns";

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
    if (!dateStr || !timeStr) return null;
    try {
        return parseISO(`${dateStr}T${timeStr}`);
    } catch (e) {
        return null;
    }
}

export function addDays(date, days) {
    const d = new Date(date);
    d.setDate(d.getDate() + days);
    return d;
}

export function formatTime(value) {
    if (!value) return '-';
    if (/^\d{2}:\d{2}(:\d{2})?$/.test(value)) {
        return value.slice(0, 5);
    }
    const d = new Date(value);
    return isNaN(d.getTime())
        ? '-'
        : d.toLocaleTimeString('de-DE', {
            hour: '2-digit',
            minute: '2-digit',
            timeZone: 'Europe/Berlin'
        });
}

export function formatLocalDateYMD(d) {
    if (!(d instanceof Date) || isNaN(d.getTime())) {
        return "";
    }
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

export function getMinutesSinceMidnight(datetimeStr) {
    if (!datetimeStr) return 0;
    const d = new Date(datetimeStr);
    if (isNaN(d.getTime())) return 0;
    return d.getHours() * 60 + d.getMinutes();
}

export function parseTimeToMinutes(timeStr) {
    if (!timeStr || !/^\d{2}:\d{2}(:\d{2})?$/.test(timeStr)) return 0;
    const parts = timeStr.split(':');
    const hours = parseInt(parts[0], 10);
    const minutes = parseInt(parts[1], 10);
    if (isNaN(hours) || isNaN(minutes)) return 0;
    return hours * 60 + minutes;
}

export function computeDailyDiffValue(dayEntries, expectedWorkHours, isHourly) {
    if (isHourly) {
        return computeDayTotalMinutesFromEntries(dayEntries);
    }
    const actualWorkedMinutes = computeDayTotalMinutesFromEntries(dayEntries);
    if (actualWorkedMinutes === 0 && dayEntries.length === 0) return 0;
    const expectedMinutes = (expectedWorkHours || 0) * 60;
    return actualWorkedMinutes - expectedMinutes;
}

export function computeOverallDiffForUser(allTracks, username, userConfig, defaultExpectedHours) {
    const userEntries = allTracks.filter(e => e.username === username);
    const dayMap = groupTracksByDay(userEntries);
    let totalDiffMinutes = 0;
    for (const isoDay in dayMap) {
        const dayEntries = dayMap[isoDay];
        if (dayEntries.length > 0) {
            const dayDate = new Date(dayEntries[0].dailyDate || isoDay + "T00:00:00");
            const expected = getExpectedHoursForDay(dayDate, userConfig, defaultExpectedHours);
            const diffMins = computeDailyDiffValue(dayEntries, expected, userConfig.isHourly);
            totalDiffMinutes += diffMins;
        }
    }
    return totalDiffMinutes;
}

export function computeDailyDiff(dayEntries, expectedWorkHours, isHourly) {
    const diff = computeDailyDiffValue(dayEntries, expectedWorkHours, isHourly);
    if (isHourly) {
        const hours = Math.floor(diff / 60);
        const minutes = diff % 60;
        return `${hours}h ${minutes}m`;
    }
    const sign = diff >= 0 ? '+' : '-';
    const absDiff = Math.abs(diff);
    const hours = Math.floor(absDiff / 60);
    const minutes = absDiff % 60;
    return `${sign}${hours}h ${minutes}m`;
}

export function getExpectedHoursForDay(dayObj, userConfig, defaultExpectedHours) {
    if (!dayObj || !(dayObj instanceof Date) || isNaN(dayObj.getTime())) {
        return userConfig?.isHourly ? 0 : (userConfig?.isPercentage ? null : defaultExpectedHours);
    }
    if (userConfig?.isHourly) return 0;
    if (userConfig?.isPercentage) return null; // Für Prozent-Nutzer ist das Tagessoll hier nicht relevant.

    let expectedForDay = defaultExpectedHours;
    if (userConfig?.weeklySchedule && Array.isArray(userConfig.weeklySchedule) && userConfig?.scheduleCycle > 0) {
        try {
            const epochMonday = new Date(2020, 0, 6);
            epochMonday.setHours(0, 0, 0, 0);
            const currentDayMonday = getMondayOfWeek(dayObj);
            const weeksSinceEpoch = Math.floor(differenceInMinutes(currentDayMonday, epochMonday) / (60 * 24 * 7));
            let cycleIndex = weeksSinceEpoch % userConfig.scheduleCycle;
            if (cycleIndex < 0) {
                cycleIndex += userConfig.scheduleCycle;
            }
            const dayOfWeek = dayObj.toLocaleDateString('en-US', { weekday: 'long' }).toLowerCase();
            if (userConfig.weeklySchedule[cycleIndex] && typeof userConfig.weeklySchedule[cycleIndex][dayOfWeek] === 'number') {
                expectedForDay = userConfig.weeklySchedule[cycleIndex][dayOfWeek];
            } else {
                if (dayObj.getDay() === 0 || dayObj.getDay() === 6) { // Sonntag oder Samstag
                    expectedForDay = 0;
                }
            }
        } catch (e) {
            if (dayObj.getDay() === 0 || dayObj.getDay() === 6) {
                expectedForDay = 0;
            }
        }
    } else {
        if (dayObj.getDay() === 0 || dayObj.getDay() === 6) { // Sonntag oder Samstag
            expectedForDay = 0;
        }
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

export function groupTracksByDay(tracks) {
    const dayMap = {};
    (tracks || []).forEach((tt) => {
        let isoDay = tt.dailyDate;
        if (!isoDay && tt.startTime) {
            isoDay = tt.startTime.slice(0, 10);
        }
        if (!isoDay) {
            return;
        }
        if (tt.workStart !== undefined) {
            dayMap[isoDay] = [tt];
        } else {
            if (!dayMap[isoDay]) {
                dayMap[isoDay] = [];
            }
            dayMap[isoDay].push(tt);
        }
    });
    for (const isoDay in dayMap) {
        const entries = dayMap[isoDay];
        if (entries.length > 0 && entries[0].punchOrder !== undefined) {
            const consolidatedEntry = {
                dailyDate: isoDay,
                username: entries[0].username,
                id: entries[0].id,
                corrected: entries.some(e => e.corrected === true)
            };
            entries.forEach(e => {
                switch (e.punchOrder) {
                    case 1: consolidatedEntry.workStart = formatTime(e.startTime); break;
                    case 2: consolidatedEntry.breakStart = formatTime(e.startTime); break;
                    case 3: consolidatedEntry.breakEnd = formatTime(e.endTime || e.startTime); break;
                    case 4: consolidatedEntry.workEnd = formatTime(e.endTime || e.startTime); break;
                }
            });
            dayMap[isoDay] = [consolidatedEntry];
        }
    }
    return dayMap;
}

export function computeDayTotalMinutes(dayEntry) {
    if (!dayEntry || !dayEntry.workStart || !dayEntry.workEnd || !dayEntry.dailyDate) {
        return 0;
    }
    const workStartTime = timeToDate(dayEntry.dailyDate, dayEntry.workStart);
    const workEndTime = timeToDate(dayEntry.dailyDate, dayEntry.workEnd);
    if (!workStartTime || !workEndTime || workEndTime < workStartTime) {
        return 0;
    }
    let minutesWorked = differenceInMinutes(workEndTime, workStartTime);
    if (dayEntry.breakStart && dayEntry.breakEnd) {
        const breakStartTime = timeToDate(dayEntry.dailyDate, dayEntry.breakStart);
        const breakEndTime = timeToDate(dayEntry.dailyDate, dayEntry.breakEnd);
        if (breakStartTime && breakEndTime && breakEndTime > breakStartTime) {
            const breakDuration = differenceInMinutes(breakEndTime, breakStartTime);
            minutesWorked -= breakDuration;
        }
    }
    return Math.max(0, minutesWorked);
}

export function computeDayTotalMinutesFromEntries(dayEntriesArray) {
    if (!dayEntriesArray || dayEntriesArray.length === 0) return 0;
    return computeDayTotalMinutes(dayEntriesArray[0]);
}

export function minutesToHHMM(totalMinutes) {
    if (typeof totalMinutes !== 'number' || isNaN(totalMinutes)) {
        return "0h 0m";
    }
    const sign = totalMinutes < 0 ? "-" : "";
    const absMinutes = Math.abs(totalMinutes);
    const h = Math.floor(absMinutes / 60);
    const m = absMinutes % 60;
    return `${sign}${h}h ${m}m`;
}

export function computeTotalMinutesInRange(allEntries, startDate, endDate) {
    const userDayMap = groupTracksByDay(allEntries);
    let totalMinutes = 0;
    for (const isoDay in userDayMap) {
        const dayDate = new Date(isoDay + "T00:00:00");
        if (dayDate >= startDate && dayDate <= endDate) {
            totalMinutes += computeDayTotalMinutesFromEntries(userDayMap[isoDay]);
        }
    }
    return totalMinutes;
}

export function isLateTime(timeString) {
    if (!timeString || typeof timeString !== 'string' || !timeString.includes(':')) return false;
    try {
        const [hours, minutes] = timeString.split(':').map(Number);
        if (isNaN(hours) || isNaN(minutes)) return false;
        const timeInMinutes = hours * 60 + minutes;
        const lateStartInMinutes = 22 * 60 + 30;
        const lateEndInMinutes = 23 * 60 + 40;
        return timeInMinutes >= lateStartInMinutes && timeInMinutes <= lateEndInMinutes;
    } catch (e) {
        return false;
    }
}

export function calculateWeeklyActualMinutes(userDayMap, weekDates) {
    let totalMinutes = 0;
    weekDates.forEach(date => {
        const isoDate = formatLocalDateYMD(date);
        const dayEntries = userDayMap[isoDate] || [];
        totalMinutes += computeDayTotalMinutesFromEntries(dayEntries);
    });
    return totalMinutes;
}

export function calculateWeeklyExpectedMinutes(userConfig, weekDates, defaultExpectedHours, userApprovedVacations) {
    if (userConfig.isHourly) return 0; // Stundenlöhner haben kein Wochensoll in diesem Sinne
    let totalExpectedMinutes = 0;
    if (userConfig.isPercentage) { // Für prozentuale User
        const workPercentage = userConfig.workPercentage || 100; //
        let rawWeeklyTargetMinutes = Math.round(defaultExpectedHours * 5 * 60 * (workPercentage / 100.0)); // Annahme: defaultExpectedHours ist für 100% bei 5 Tagen
        let vacationDeductionMinutes = 0;
        const dailyTargetMinutesForPercentageUser = Math.round((defaultExpectedHours * (workPercentage / 100.0)) * 60); //

        weekDates.forEach(date => {
            const dayOfWeek = date.getDay();
            if (dayOfWeek >= 1 && dayOfWeek <= 5) { // Nur Mo-Fr für Urlaubsreduktion prüfen
                let isVacationDayThisDate = false;
                let isHalfDayVacation = false;
                for (const vac of userApprovedVacations) { //
                    const vacStart = new Date(vac.startDate + "T00:00:00"); //
                    const vacEnd = new Date(vac.endDate + "T23:59:59"); //
                    const currentDate = new Date(date);
                    currentDate.setHours(0, 0, 0, 0);
                    if (currentDate >= vacStart && currentDate <= vacEnd) { //
                        isVacationDayThisDate = true;
                        isHalfDayVacation = vac.halfDay; //
                        break;
                    }
                }
                if (isVacationDayThisDate) {
                    vacationDeductionMinutes += isHalfDayVacation ? (dailyTargetMinutesForPercentageUser / 2) : dailyTargetMinutesForPercentageUser; //
                }
            }
        });
        totalExpectedMinutes = Math.max(0, rawWeeklyTargetMinutes - vacationDeductionMinutes); //
    } else { // Für festangestellte, nicht-prozentuale User
        weekDates.forEach(date => {
            let dailyExpectedMinutes = (getExpectedHoursForDay(date, userConfig, defaultExpectedHours) || 0) * 60; //
            if (dailyExpectedMinutes > 0) { // Nur anpassen, wenn es ein erwarteter Arbeitstag war
                let isVacationDayThisDate = false;
                let isHalfDayVacation = false;
                for (const vac of userApprovedVacations) { //
                    const vacStart = new Date(vac.startDate + "T00:00:00"); //
                    const vacEnd = new Date(vac.endDate + "T23:59:59"); //
                    const currentDate = new Date(date);
                    currentDate.setHours(0, 0, 0, 0);
                    if (currentDate >= vacStart && currentDate <= vacEnd) { //
                        isVacationDayThisDate = true;
                        isHalfDayVacation = vac.halfDay; //
                        break;
                    }
                }
                if (isVacationDayThisDate) {
                    if (isHalfDayVacation) { //
                        dailyExpectedMinutes /= 2; //
                    } else {
                        dailyExpectedMinutes = 0; // Ganzer Urlaubstag = 0 Sollstunden
                    }
                }
            }
            totalExpectedMinutes += dailyExpectedMinutes; //
        });
    }
    return Math.round(totalExpectedMinutes); //
}


export function getDetailedGlobalProblemIndicators(allUserDayMap, userApprovedVacations, userConfig, defaultExpectedHours) {
    const indicators = {
        missingEntriesCount: 0,
        incompleteDaysCount: 0,
        autoCompletedCount: 0,
        problematicDays: []
    };

    if (!userConfig || !allUserDayMap) {
        return indicators;
    }

    let checkStartDate = null;
    if (userConfig.scheduleEffectiveDate) { //
        try {
            const parts = userConfig.scheduleEffectiveDate.split('-');
            if (parts.length === 3) {
                checkStartDate = new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10));
            }
        } catch (e) { /* ignore */ }
    }

    const entryDates = Object.keys(allUserDayMap).sort();
    if (entryDates.length > 0) {
        const firstEntryDate = new Date(entryDates[0] + "T00:00:00");
        if (!checkStartDate || firstEntryDate < checkStartDate) {
            checkStartDate = firstEntryDate;
        }
    }

    if (!checkStartDate && userConfig.companyJoinedDate) {
        try {
            const parts = userConfig.companyJoinedDate.split('-');
            if (parts.length === 3) {
                checkStartDate = new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10));
            }
        } catch (e) { /* ignore */ }
    }

    if (!checkStartDate) {
        return indicators;
    }
    checkStartDate.setHours(0, 0, 0, 0);

    const today = new Date();
    today.setHours(0, 0, 0, 0);

    for (let currentDateIter = new Date(checkStartDate); currentDateIter <= today; currentDateIter.setDate(currentDateIter.getDate() + 1)) {
        const isoDate = formatLocalDateYMD(currentDateIter);
        const dayEntry = allUserDayMap[isoDate]?.[0];

        let relevantEffectiveStartDate = null;
        if (userConfig.scheduleEffectiveDate) { //
            try {
                const parts = userConfig.scheduleEffectiveDate.split('-');
                if (parts.length === 3) {
                    relevantEffectiveStartDate = new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10));
                    relevantEffectiveStartDate.setHours(0, 0, 0, 0);
                }
            } catch (e) { /* ignore */ }
        }
        if (relevantEffectiveStartDate && new Date(currentDateIter) < relevantEffectiveStartDate) {
            continue;
        }

        const expectedHoursToday = getExpectedHoursForDay(new Date(currentDateIter), userConfig, defaultExpectedHours); //
        const dayOfWeek = currentDateIter.getDay(); // 0 für Sonntag, 6 für Samstag

        // Für Prozent-Nutzer ist ein "potenzieller Arbeitstag" jeder Wochentag (Mo-Fr),
        // da sie flexibel arbeiten und nur das Wochensoll erreichen müssen.
        // Für andere Nutzer hängt es von den `expectedHoursToday` ab.
        const isPotentiallyWorkDay = userConfig.isPercentage
            ? (dayOfWeek >= 1 && dayOfWeek <= 5) // Mo-Fr für Prozent-Nutzer
            : (expectedHoursToday != null && expectedHoursToday > 0); // Für Standard-Nutzer

        let isVacation = false;
        if (userApprovedVacations && Array.isArray(userApprovedVacations)) { //
            for (const vac of userApprovedVacations) { //
                const vacStart = new Date(vac.startDate + "T00:00:00"); //
                const vacEnd = new Date(vac.endDate + "T23:59:59"); //
                const checkDate = new Date(currentDateIter);
                checkDate.setHours(0, 0, 0, 0);
                if (checkDate >= vacStart && checkDate <= vacEnd) { //
                    isVacation = true;
                    break;
                }
            }
        }

        if (isPotentiallyWorkDay && !isVacation) {
            // *** Änderung für Prozent-Nutzer: Fehlende Einträge nicht als Problem zählen ***
            if (!userConfig.isPercentage) { //
                if (!dayEntry || (!dayEntry.workStart && !dayEntry.workEnd)) { //
                    // Für Standard-Nutzer bleibt die Logik für fehlende Einträge bestehen.
                    // Ggf. könnte man hier für Prozent-Nutzer nur dann einen fehlenden Eintrag zählen,
                    // wenn sie zwar gekommen sind (workStart), aber nie gegangen sind (workEnd fehlt)
                    // - dies wird aber eher unter "incompleteDays" abgedeckt.
                    // Für den Moment: Prozent-Nutzer bekommen hier keinen 'missingEntriesCount'.
                    indicators.missingEntriesCount++; //
                    indicators.problematicDays.push({ dateIso: isoDate, type: 'missing' }); //
                }
            }

            // *** Unvollständige und Auto-Vervollständigte Tage gelten weiterhin für alle (auch Prozent-Nutzer), WENN sie gestempelt haben ***
            if (dayEntry && (dayEntry.workStart || dayEntry.workEnd)) { // Nur prüfen, wenn überhaupt ein Eintrag existiert
                let dayIsIncomplete = false;
                const hasWorkStart = !!dayEntry.workStart; //
                const hasWorkEnd = !!dayEntry.workEnd; //
                const hasBreakStart = !!dayEntry.breakStart; //
                const hasBreakEnd = !!dayEntry.breakEnd; //

                if (hasWorkStart && !hasWorkEnd) { //
                    dayIsIncomplete = true;
                    indicators.problematicDays.push({ dateIso: isoDate, type: 'incomplete_work_end_missing' }); //
                }
                if (hasBreakStart && !hasBreakEnd) { //
                    dayIsIncomplete = true;
                    if (!indicators.problematicDays.some(p => p.dateIso === isoDate && p.type === 'incomplete_work_end_missing')) { //
                        indicators.problematicDays.push({ dateIso: isoDate, type: 'incomplete_break_end_missing' }); //
                    }
                }
                if (dayIsIncomplete) {
                    indicators.incompleteDaysCount++; //
                }

                if (dayEntry.corrected === true && dayEntry.workEnd && dayEntry.workEnd.startsWith("23:20")) { //
                    indicators.autoCompletedCount++; //
                    const existingProblemIndex = indicators.problematicDays.findIndex(p => p.dateIso === isoDate && p.type.startsWith('incomplete')); //
                    if (existingProblemIndex !== -1) { //
                        indicators.problematicDays[existingProblemIndex].subType = 'auto_completed_override'; //
                        indicators.problematicDays[existingProblemIndex].type = 'auto_completed_incomplete'; //
                    } else if (!indicators.problematicDays.some(p => p.dateIso === isoDate && p.type === 'auto_completed')) { //
                        indicators.problematicDays.push({ dateIso: isoDate, type: 'auto_completed' }); //
                    }
                }
            }
        }
    }
    indicators.problematicDays.sort((a, b) => a.dateIso.localeCompare(b.dateIso)); //
    return indicators;
}