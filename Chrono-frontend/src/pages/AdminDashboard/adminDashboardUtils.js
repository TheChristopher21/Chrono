// src/pages/AdminDashboard/adminDashboardUtils.js
import { differenceInMinutes, parseISO } from "date-fns";

export function getMondayOfWeek(date) {
    const copy = new Date(date);
    const day = copy.getDay();
    const diff = day === 0 ? 6 : day - 1; // Montag als Start der Woche (0=Sonntag, 1=Montag, ..., 6=Samstag)
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
    // Stellt sicher, dass dateStr im YYYY-MM-DD Format ist, ggf. parsen
    let isoDateStr = dateStr;
    if (dateStr.includes('-') && dateStr.split('-')[0].length === 2) { // DD-MM-YYYY
        const parts = dateStr.split('-');
        isoDateStr = `${parts[2]}-${parts[1]}-${parts[0]}`;
    }
    try {
        return parseISO(`${isoDateStr}T${timeStr}`);
    } catch (e) {
        console.warn(`Could not parse date/time: ${dateStr}T${timeStr}`, e);
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
    const d = new Date(value); // Versucht, es als komplettes Datum zu parsen
    if (!isNaN(d.getTime())) { // Wenn gültig
        return d.toLocaleTimeString('de-DE', { // Oder 'en-GB' für 24h
            hour: '2-digit',
            minute: '2-digit',
            timeZone: 'Europe/Berlin' // Wichtig für Konsistenz
        });
    }
    // Fallback, falls es nur eine Zeit ist aber nicht im HH:mm Format
    console.warn("Unrecognized time format for formatTime:", value);
    return '-';
}

export function formatLocalDateYMD(d) {
    if (!(d instanceof Date) || isNaN(d.getTime())) {
        // Versuche, aus String zu parsen, wenn es kein Date-Objekt ist
        if (typeof d === 'string') {
            const parsedDate = new Date(d);
            if (!isNaN(parsedDate.getTime())) {
                d = parsedDate;
            } else {
                console.warn("formatLocalDateYMD: Invalid date input for string:", d);
                return "";
            }
        } else {
            console.warn("formatLocalDateYMD: Invalid date input:", d);
            return "";
        }
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
            // Stelle sicher, dass die Pause innerhalb der Arbeitszeit liegt
            const effectiveBreakStart = breakStartTime < workStartTime ? workStartTime : breakStartTime;
            const effectiveBreakEnd = breakEndTime > workEndTime ? workEndTime : breakEndTime;

            if (effectiveBreakEnd > effectiveBreakStart) {
                const breakDuration = differenceInMinutes(effectiveBreakEnd, effectiveBreakStart);
                minutesWorked -= breakDuration;
            }
        }
    }
    return Math.max(0, minutesWorked);
}

export function computeDailyDiffValue(dayEntries, expectedWorkHours, isHourly, dailyDateObj, userConfig, holidaysForUserCanton, userHolidayOptionsForWeek) {
    const actualWorkedMinutes = computeDayTotalMinutesFromEntries(dayEntries);

    if (isHourly) {
        return actualWorkedMinutes; // Für stündliche MA ist die Differenz = Ist-Zeit
    }

    let expectedMinutes = (expectedWorkHours || 0) * 60;

    // Anpassung für prozentuale Mitarbeiter und Feiertage
    if (userConfig?.isPercentage === true) {
        const isoDate = formatLocalDateYMD(dailyDateObj);
        const isHoliday = holidaysForUserCanton && holidaysForUserCanton[isoDate];
        if (isHoliday) {
            const holidayOption = userHolidayOptionsForWeek?.find(opt => opt.holidayDate === isoDate)?.holidayHandlingOption;
            if (holidayOption === 'DO_NOT_DEDUCT_FROM_WEEKLY_TARGET' || holidayOption === 'PENDING_DECISION') {
                // Soll wird für diesen Tag NICHT reduziert, also bleibt expectedMinutes wie berechnet
                // (was den Tagesanteil des Wochensolls darstellt).
            } else if (holidayOption === 'DEDUCT_FROM_WEEKLY_TARGET') {
                expectedMinutes = 0; // Soll wird auf 0 gesetzt, da der Feiertag das Soll reduziert
            }
            // Falls keine Option explizit gesetzt ist (selten, da PENDING_DECISION der Fallback sein sollte)
            // oder PENDING_DECISION -> keine Reduktion als Standard.
        }
    }

    if (actualWorkedMinutes === 0 && dayEntries.length === 0 && expectedMinutes > 0) {
        return -expectedMinutes; // Wenn keine Einträge und Soll > 0, dann ist Diff = -Soll
    }
    if (actualWorkedMinutes === 0 && dayEntries.length === 0 && expectedMinutes === 0) return 0;


    return actualWorkedMinutes - expectedMinutes;
}

export function computeOverallDiffForUser(allTracks, username, userConfig, defaultExpectedHours, allUserVacations, allUserSickLeaves, allHolidaysForAllCantons, allUserHolidayOptions) {
    const userEntries = allTracks.filter(e => e.username === username);
    const dayMap = groupTracksByDay(userEntries);
    let totalDiffMinutes = 0;

    const userCantonKey = userConfig.companyCantonAbbreviation || 'GENERAL';
    const holidaysForThisUser = allHolidaysForAllCantons[userCantonKey] || allHolidaysForAllCantons['GENERAL'] || {};

    for (const isoDay in dayMap) {
        const dayEntries = dayMap[isoDay];
        if (dayEntries.length > 0) {
            const dayDate = new Date(dayEntries[0].dailyDate || isoDay + "T00:00:00");
            const expected = getExpectedHoursForDay(dayDate, userConfig, defaultExpectedHours, holidaysForThisUser, allUserVacations, allUserSickLeaves, allUserHolidayOptions);
            const diffMins = computeDailyDiffValue(dayEntries, expected, userConfig.isHourly, dayDate, userConfig, holidaysForThisUser, allUserHolidayOptions);
            totalDiffMinutes += diffMins;
        }
    }
    return totalDiffMinutes;
}

export function computeDailyDiff(dayEntries, expectedWorkHours, isHourly, dailyDateObj, userConfig, holidaysForUserCanton, userHolidayOptionsForWeek) {
    const diff = computeDailyDiffValue(dayEntries, expectedWorkHours, isHourly, dailyDateObj, userConfig, holidaysForUserCanton, userHolidayOptionsForWeek);

    if (isHourly) {
        const hours = Math.floor(diff / 60);
        const minutes = diff % 60;
        return `${hours}h ${minutes}m`; // Zeigt die Ist-Zeit für stündliche MA
    }

    const sign = diff >= 0 ? '+' : '-';
    const absDiff = Math.abs(diff);
    const hours = Math.floor(absDiff / 60);
    const minutes = absDiff % 60;
    return `${sign}${hours}h ${minutes}m`;
}


export function getExpectedHoursForDay(
    dayObj,
    userConfig,
    defaultExpectedHours,
    holidaysForUserCanton, // Objekt { "YYYY-MM-DD": "Feiertagsname", ... }
    userApprovedVacations, // Array von Urlaubsanträgen des Users
    userSickLeaves, // Array von Krankmeldungen des Users
    userHolidayOptionsForWeek // Array der Feiertagsoptionen für die Woche
) {
    if (!dayObj || !(dayObj instanceof Date) || isNaN(dayObj.getTime())) {
        return userConfig?.isHourly ? 0 : defaultExpectedHours;
    }

    if (userConfig?.isHourly === true) return 0; // Kein Soll für stündliche

    const isoDate = formatLocalDateYMD(dayObj);
    const dayOfWeek = dayObj.getDay(); // Sonntag = 0, Samstag = 6

    // Prüfung auf Feiertag
    const isHoliday = holidaysForUserCanton && holidaysForUserCanton[isoDate];

    // Prüfung auf Urlaub
    const vacationToday = userApprovedVacations?.find(v => isoDate >= v.startDate && isoDate <= v.endDate && v.approved);

    // Prüfung auf Krankheit
    const sickToday = userSickLeaves?.find(sl => isoDate >= sl.startDate && isoDate <= sl.endDate);

    // Wenn prozentualer Mitarbeiter
    if (userConfig?.isPercentage === true) {
        // Wochenenden haben kein Soll, es sei denn, es ist ein Feiertag, der speziell behandelt wird
        if (dayOfWeek === 0 || dayOfWeek === 6) {
            if (!isHoliday) return 0; // Kein Soll an normalen Wochenenden
        }

        // Basis-Tagessoll für prozentuale User (Anteil am Wochensoll)
        const pct = userConfig.workPercentage ?? 100;
        const workDaysInModel = userConfig.expectedWorkDays ?? 5;
        const baseWeekHoursFullTime = defaultExpectedHours * 5; // Annahme: 5 Tage Woche für 100%
        const userWeeklyHours = baseWeekHoursFullTime * (pct / 100.0);
        let dailyExpectedHours = workDaysInModel > 0 ? userWeeklyHours / workDaysInModel : 0;

        if (isHoliday) {
            const holidayOption = userHolidayOptionsForWeek?.find(opt => opt.holidayDate === isoDate)?.holidayHandlingOption;
            if (holidayOption === 'DEDUCT_FROM_WEEKLY_TARGET') {
                dailyExpectedHours = 0; // Feiertag reduziert Soll auf 0
            } else if (holidayOption === 'DO_NOT_DEDUCT_FROM_WEEKLY_TARGET' || holidayOption === 'PENDING_DECISION') {
                // Soll wird NICHT reduziert, bleibt `dailyExpectedHours`
            } else {
                // PENDING_DECISION oder keine Option -> Soll wird NICHT reduziert (Standard)
            }
        }

        // Wenn Urlaub an diesem Tag (und kein Feiertag, der das Soll eh auf 0 setzt)
        if (vacationToday && dailyExpectedHours > 0) {
            return vacationToday.halfDay ? dailyExpectedHours / 2 : 0;
        }
        // Wenn krank an diesem Tag (und kein Feiertag/Urlaub, der das Soll eh auf 0 setzt)
        if (sickToday && !vacationToday && dailyExpectedHours > 0) {
            return sickToday.halfDay ? dailyExpectedHours / 2 : 0;
        }
        return dailyExpectedHours;
    }

    // Für Standard-Mitarbeiter (nicht prozentual, nicht stündlich)
    if (isHoliday) return 0; // An Feiertagen ist das Soll für Standard-MA immer 0
    if (vacationToday) { // Urlaub hat Vorrang vor Krankheit für die Soll-Reduktion
        const baseHoursForVacationDay = getBaseExpectedHoursFromSchedule(dayObj, userConfig, defaultExpectedHours);
        return vacationToday.halfDay ? baseHoursForVacationDay / 2 : 0;
    }
    if (sickToday) {
        const baseHoursForSickDay = getBaseExpectedHoursFromSchedule(dayObj, userConfig, defaultExpectedHours);
        return sickToday.halfDay ? baseHoursForSickDay / 2 : 0;
    }

    return getBaseExpectedHoursFromSchedule(dayObj, userConfig, defaultExpectedHours);
}

// Hilfsfunktion für Standard-Mitarbeiter, um das Basis-Soll laut Plan zu bekommen
function getBaseExpectedHoursFromSchedule(dayObj, userConfig, defaultExpectedHours) {
    const dayOfWeek = dayObj.getDay();
    let expectedForDay = defaultExpectedHours;

    if (userConfig?.weeklySchedule && Array.isArray(userConfig.weeklySchedule) && userConfig.weeklySchedule.length > 0 && userConfig?.scheduleCycle > 0) {
        try {
            const epochMonday = getMondayOfWeek(new Date(2020, 0, 6)); // Fester Referenz-Montag
            const currentDayMonday = getMondayOfWeek(dayObj);
            const weeksSinceEpoch = Math.floor(differenceInMinutes(currentDayMonday, epochMonday) / (60 * 24 * 7));
            let cycleIndex = weeksSinceEpoch % userConfig.scheduleCycle;
            if (cycleIndex < 0) cycleIndex += userConfig.scheduleCycle;

            const dayOfWeekName = dayObj.toLocaleDateString('en-US', { weekday: 'long' }).toLowerCase();

            if (userConfig.weeklySchedule[cycleIndex] && typeof userConfig.weeklySchedule[cycleIndex][dayOfWeekName] === 'number') {
                expectedForDay = userConfig.weeklySchedule[cycleIndex][dayOfWeekName];
            } else { // Fallback, falls im Schedule für diesen Tag nichts steht
                expectedForDay = (dayOfWeek === 0 || dayOfWeek === 6) ? 0 : defaultExpectedHours;
            }
        } catch (e) {
            console.error("Error in getBaseExpectedHoursFromSchedule:", e);
            expectedForDay = (dayOfWeek === 0 || dayOfWeek === 6) ? 0 : defaultExpectedHours;
        }
    } else { // Kein detaillierter Wochenplan, Standardwerte
        expectedForDay = (dayOfWeek === 0 || dayOfWeek === 6) ? 0 : defaultExpectedHours;
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
        let isoDay = tt.dailyDate; // Format YYYY-MM-DD
        if (!isoDay && tt.startTime) { // Veraltetes Format mit startTime
            isoDay = tt.startTime.slice(0, 10);
        }
        if (!isoDay) {
            console.warn("Track without dailyDate or startTime:", tt);
            return;
        }

        // Direktes Mapping, da Backend jetzt pro Tag ein Objekt liefert
        dayMap[isoDay] = [tt]; // Array beibehalten für Konsistenz mit alter Logik in Komponenten
    });
    return dayMap;
}


export function computeDayTotalMinutesFromEntries(dayEntriesArray) {
    if (!dayEntriesArray || dayEntriesArray.length === 0) return 0;
    // Nimmt das erste (und einzige erwartete) Objekt für den Tag
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
        const dayDate = new Date(isoDay + "T00:00:00"); // Sicherstellen, dass es als Mitternacht UTC interpretiert wird für Datumsvergleich
        const start = new Date(startDate); start.setHours(0,0,0,0);
        const end = new Date(endDate); end.setHours(0,0,0,0);

        if (dayDate >= start && dayDate <= end) {
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
        // Spezifische Zeiten für "automatisch beendet"
        return (hours === 23 && minutes >= 20 && minutes <= 25) || (hours === 22 && minutes >=55);
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

export function calculateWeeklyExpectedMinutes(
    userConfig,
    weekDates,
    defaultExpectedHours, // z.B. 8.5h (Tagessoll eines 100% MA bei 5 Tagen)
    userApprovedVacations,
    userSickLeaves,
    holidaysForUserCanton,
    userHolidayOptionsForWeek
) {
    if (!userConfig || userConfig.isHourly === true) {
        return 0;
    }

    let totalExpectedMinutesForWeek = 0;

    if (userConfig.isPercentage === true) {
        const pct = userConfig.workPercentage ?? 100;

        // KORREKTUR: Basis-Wochensoll für 100% Mitarbeiter (z.B. bei 5 Tagen à defaultExpectedHours)
        const baseWeeklyHoursFullTimeStandard = defaultExpectedHours * 5; // z.B. 8.5 * 5 = 42.5 Stunden

        // Korrektes Wochensoll des prozentualen Mitarbeiters
        const userActualWeeklyHours = baseWeeklyHoursFullTimeStandard * (pct / 100.0); // z.B. 42.5 * 0.60 = 25.5 Stunden
        const userWeeklyTotalExpectedMinutes = Math.round(userActualWeeklyHours * 60); // z.B. 25.5 * 60 = 1530 Minuten

        let absenceAndHolidayDeductionMinutes = 0;
        const workDaysInModel = userConfig.expectedWorkDays ?? 5; // z.B. 3 Tage für User "test"

        // Korrekter Wert eines einzelnen Arbeitstages für diesen prozentualen Mitarbeiter
        const valueOfOneUserWorkDayMinutes = workDaysInModel > 0 ? Math.round(userWeeklyTotalExpectedMinutes / workDaysInModel) : 0;
        // z.B. 1530 Minuten / 3 Tage = 510 Minuten (8.5 Stunden) pro Arbeitstag

        for (const date of weekDates) {
            const isoDate = formatLocalDateYMD(date);
            const dayOfWeek = date.getDay();

            // Berücksichtige die tatsächlichen Arbeitstage des Users, wenn workDaysInModel < 5
            // (Diese Logik muss ggf. verfeinert werden, je nachdem, wie Sa/So für prozentuale MA gehandhabt werden sollen,
            // wenn ihre <5 Tage nicht Mo-Fr sind. Aktuell: WE sind frei, außer `workDaysInModel` ist >5)
            let isPotentialWorkDayForUser = true;
            if (workDaysInModel <= 5 && (dayOfWeek === 0 /* So */ || dayOfWeek === 6 /* Sa */)) {
                isPotentialWorkDayForUser = false;
            }
            if (workDaysInModel === 6 && dayOfWeek === 0 /* So */) { // Bei 6-Tage-Modell ist Sonntag typischerweise frei
                isPotentialWorkDayForUser = false;
            }
            // Bei 7 Tagen sind alle Tage potenzielle Arbeitstage

            if (!isPotentialWorkDayForUser) continue;


            const isHoliday = holidaysForUserCanton && holidaysForUserCanton[isoDate];
            const vacationToday = userApprovedVacations?.find(v => isoDate >= v.startDate && isoDate <= v.endDate && v.approved);
            const sickToday = userSickLeaves?.find(sl => isoDate >= sl.startDate && isoDate <= sl.endDate);

            let dailyDeductionValue = valueOfOneUserWorkDayMinutes;

            if (isHoliday) {
                const holidayOption = userHolidayOptionsForWeek?.find(opt => opt.holidayDate === isoDate)?.holidayHandlingOption || 'PENDING_DECISION';
                if (holidayOption === 'DEDUCT_FROM_WEEKLY_TARGET') {
                    absenceAndHolidayDeductionMinutes += dailyDeductionValue;
                }
                // Bei 'DO_NOT_DEDUCT_FROM_WEEKLY_TARGET' oder 'PENDING_DECISION' erfolgt kein Abzug für den Feiertag.
                continue; // Feiertagslogik hat Vorrang
            }

            if (vacationToday) {
                absenceAndHolidayDeductionMinutes += vacationToday.halfDay ? Math.round(dailyDeductionValue / 2) : dailyDeductionValue;
                continue; // Urlaub hat Vorrang vor Krankheit für denselben Tag
            }
            if (sickToday) {
                absenceAndHolidayDeductionMinutes += sickToday.halfDay ? Math.round(dailyDeductionValue / 2) : dailyDeductionValue;
            }
        }
        totalExpectedMinutesForWeek = Math.max(0, userWeeklyTotalExpectedMinutes - absenceAndHolidayDeductionMinutes);

    } else {  // Standard-Mitarbeiter (nicht prozentual)
        weekDates.forEach(date => {
            const dailyExpectedHours = getExpectedHoursForDay(
                date,
                userConfig,
                defaultExpectedHours,
                holidaysForUserCanton,
                userApprovedVacations,
                userSickLeaves,
                null // userHolidayOptionsForWeek ist für Standard-MA nicht direkt für die Soll-Berechnung hier relevant
            );
            totalExpectedMinutesForWeek += Math.round(dailyExpectedHours * 60);
        });
    }
    return Math.max(0, totalExpectedMinutesForWeek);
}



export function getDetailedGlobalProblemIndicators(
    allUserDayMap,
    userApprovedVacations,
    userConfig,
    defaultExpectedHours,
    userSickLeaves,
    holidaysForUserCanton,
    userHolidayOptionsForWeek // Wird jetzt für die spezifische Woche des Users im Detail genutzt
) {
    const indicators = {
        missingEntriesCount: 0,
        incompleteDaysCount: 0,
        autoCompletedCount: 0,
        holidayPendingCount: 0, // NEU
        problematicDays: []
    };

    if (!userConfig || !allUserDayMap) {
        return indicators;
    }

    let checkStartDate = null;
    if (userConfig.scheduleEffectiveDate) {
        try {
            const parts = userConfig.scheduleEffectiveDate.split('-');
            if (parts.length === 3) {
                checkStartDate = new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10));
            }
        } catch (e) { console.warn("Could not parse scheduleEffectiveDate", e); }
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
        } catch (e) { console.warn("Could not parse companyJoinedDate", e); }
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

        if (userConfig.scheduleEffectiveDate) {
            const effectiveDate = new Date(userConfig.scheduleEffectiveDate + "T00:00:00");
            if (currentDateIter < effectiveDate) {
                continue;
            }
        }

        const isHoliday = holidaysForUserCanton && holidaysForUserCanton[isoDate];
        const vacationToday = userApprovedVacations?.find(v => isoDate >= v.startDate && isoDate <= v.endDate && v.approved);
        const sickToday = userSickLeaves?.find(sl => isoDate >= sl.startDate && isoDate <= sl.endDate);

        let expectedHoursToday = getExpectedHoursForDay(
            new Date(currentDateIter),
            userConfig,
            defaultExpectedHours,
            holidaysForUserCanton,
            userApprovedVacations,
            userSickLeaves,
            userHolidayOptionsForWeek // Wichtig: Optionen der Woche für die Tages-Soll-Berechnung
        );

        let isPotentiallyWorkDay = expectedHoursToday > 0;

        // Spezifische Prüfung für prozentuale Mitarbeiter und Feiertage
        if (userConfig.isPercentage === true && isHoliday) {
            const holidayOption = userHolidayOptionsForWeek?.find(opt => opt.holidayDate === isoDate)?.holidayHandlingOption
                || 'PENDING_DECISION'; // Default, wenn keine spezifische Option für die Woche da ist

            if (holidayOption === 'DEDUCT_FROM_WEEKLY_TARGET') {
                isPotentiallyWorkDay = false;
            } else { // DO_NOT_DEDUCT oder PENDING
                const dayOfWeek = currentDateIter.getDay();
                // Ein Eintrag wird erwartet, wenn es ein potenzieller Arbeitstag lt. Pensum ist
                // UND die Feiertagsoption das Soll nicht reduziert.
                isPotentiallyWorkDay = (dayOfWeek >= 1 && dayOfWeek <= (userConfig.expectedWorkDays || 5));

                // NEU: Problem für ausstehende Feiertagsentscheidung hinzufügen
                if (holidayOption === 'PENDING_DECISION') {
                    indicators.holidayPendingCount++;
                    indicators.problematicDays.push({ dateIso: isoDate, type: 'holiday_pending_decision' });
                }
            }
        }


        if (isPotentiallyWorkDay && !vacationToday && !sickToday) {
            if (!dayEntry || (!dayEntry.workStart && !dayEntry.workEnd)) {
                indicators.missingEntriesCount++;
                indicators.problematicDays.push({ dateIso: isoDate, type: 'missing' });
            }

            if (dayEntry && (dayEntry.workStart || dayEntry.workEnd)) {
                let dayIsIncomplete = false;
                const hasWorkStart = !!dayEntry.workStart;
                const hasWorkEnd = !!dayEntry.workEnd;
                const hasBreakStart = !!dayEntry.breakStart;
                const hasBreakEnd = !!dayEntry.breakEnd;

                if (hasWorkStart && !hasWorkEnd) {
                    dayIsIncomplete = true;
                    indicators.problematicDays.push({ dateIso: isoDate, type: 'incomplete_work_end_missing' });
                }
                if (hasBreakStart && !hasBreakEnd && hasWorkStart && hasWorkEnd) {
                    dayIsIncomplete = true;
                    if (!indicators.problematicDays.some(p => p.dateIso === isoDate && p.type === 'incomplete_work_end_missing')) {
                        indicators.problematicDays.push({ dateIso: isoDate, type: 'incomplete_break_end_missing' });
                    }
                }
                if (dayIsIncomplete) {
                    indicators.incompleteDaysCount++;
                }

                if (dayEntry.corrected === true && dayEntry.workEnd && isLateTime(dayEntry.workEnd)) {
                    indicators.autoCompletedCount++;
                    const existingProblemIndex = indicators.problematicDays.findIndex(p => p.dateIso === isoDate && p.type.startsWith('incomplete'));
                    if (existingProblemIndex !== -1) {
                        indicators.problematicDays[existingProblemIndex].type = 'auto_completed_incomplete';
                    } else if (!indicators.problematicDays.some(p => p.dateIso === isoDate && p.type === 'auto_completed')){
                        indicators.problematicDays.push({ dateIso: isoDate, type: 'auto_completed' });
                    }
                }
            }
        }
    }
    const uniqueProblematicDays = indicators.problematicDays.reduce((acc, current) => {
        const x = acc.find(item => item.dateIso === current.dateIso && item.type === current.type);
        if (!x) {
            return acc.concat([current]);
        } else {
            return acc;
        }
    }, []);
    indicators.problematicDays = uniqueProblematicDays.sort((a, b) => a.dateIso.localeCompare(b.dateIso));

    indicators.missingEntriesCount = uniqueProblematicDays.filter(p => p.type === 'missing').length;
    indicators.incompleteDaysCount = uniqueProblematicDays.filter(p => p.type.startsWith('incomplete_')).length;
    // holidayPendingCount wird bereits oben korrekt inkrementiert.
    // autoCompletedCount bleibt, da es eine Eigenschaft eines vorhandenen Eintrags ist.

    return indicators;
}
