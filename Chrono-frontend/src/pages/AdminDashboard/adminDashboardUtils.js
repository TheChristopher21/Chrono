// src/pages/AdminDashboard/adminDashboardUtils.js
import { parseISO, format as formatDateFns } from "date-fns"; // Import muss oben in der Datei sein

export function getMondayOfWeek(date) {
    const copy = new Date(date);
    const day = copy.getDay(); // Sonntag = 0, Montag = 1, ...
    const diff = day === 0 ? -6 : 1 - day; // Montag ist Ziel
    copy.setDate(copy.getDate() + diff);
    copy.setHours(0, 0, 0, 0);
    return copy;
}

export function formatDate(dateInput) {
    if (!dateInput) return "-";
    const date = (dateInput instanceof Date) ? dateInput : new Date(dateInput);
    if (isNaN(date.getTime())) return "-";
    // Korrektur: Verwendet lokale Datumsteile
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0'); // getMonth() ist 0-basiert
    const year = date.getFullYear();
    return `${day}.${month}.${year}`;
}
export function formatTime(dateInput) {
    if (!dateInput) {
        return "--:--";
    }
    try {
        // new Date() konvertiert einen ISO-String korrekt in die lokale Zeitzone des Browsers.
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
export function formatLocalDateYMD(d) {
    if (!d) return "";
    let dateToFormat = d;
    if (typeof d === 'string') {
        if (d.includes('T') || d.includes('Z')) {
            dateToFormat = parseISO(d);
        } else {
            const parts = d.split('-');
            if (parts.length === 3) {
                dateToFormat = new Date(parseInt(parts[0],10), parseInt(parts[1],10) - 1, parseInt(parts[2],10));
            } else {
                dateToFormat = new Date(d);
            }
        }
    }
    if (!(dateToFormat instanceof Date) || isNaN(dateToFormat.getTime())) {
        console.warn("formatLocalDateYMD: Invalid date input after parsing:", d, dateToFormat);
        return "";
    }
    const year = dateToFormat.getFullYear();
    const month = String(dateToFormat.getMonth() + 1).padStart(2, '0');
    const day = String(dateToFormat.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

export function addDays(date, days) {
    const d = new Date(date);
    d.setDate(d.getDate() + days);
    return d;
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

export function getExpectedHoursForDay(
    dayObj,
    userConfig,
    defaultExpectedHours,
    holidaysForUserCanton,
    userApprovedVacations,
    userSickLeaves,
    userHolidayOptionsForThisDay
) {
    if (!dayObj || !(dayObj instanceof Date) || isNaN(dayObj.getTime())) {
        return userConfig?.isHourly ? 0 : defaultExpectedHours;
    }
    if (userConfig?.isHourly === true) return 0;

    const isoDate = formatLocalDateYMD(dayObj);
    const dayOfWeekJs = dayObj.getDay();

    const isHoliday = holidaysForUserCanton && holidaysForUserCanton[isoDate];
    const vacationToday = userApprovedVacations?.find(v => isoDate >= v.startDate && isoDate <= v.endDate && v.approved);
    const sickToday = userSickLeaves?.find(sl => isoDate >= sl.startDate && isoDate <= sl.endDate);

    if (userConfig?.isPercentage === true) {
        let dailySollPercentage = 0;
        const workDaysInModel = userConfig.expectedWorkDays || 5;
        const baseWeekHoursFullTime = defaultExpectedHours * 5;
        const userWeeklyHours = baseWeekHoursFullTime * ((userConfig.workPercentage || 100) / 100.0);
        if (workDaysInModel > 0) {
            dailySollPercentage = userWeeklyHours / workDaysInModel;
        }

        if (isHoliday) {
            const handlingOption = userHolidayOptionsForThisDay?.holidayHandlingOption || 'PENDING_DECISION';
            if (handlingOption === 'DEDUCT_FROM_WEEKLY_TARGET') return 0;
        }
        if (vacationToday) return vacationToday.halfDay ? dailySollPercentage / 2 : 0;
        if (sickToday) return sickToday.halfDay ? dailySollPercentage / 2 : 0;

        if (dayOfWeekJs === 0 || dayOfWeekJs === 6) {
            if (workDaysInModel <= 5) return 0;
        }
        return dailySollPercentage;
    }

    if (isHoliday) return 0;
    if (vacationToday) {
        const baseHours = getBaseExpectedHoursFromSchedule(dayObj, userConfig, defaultExpectedHours);
        return vacationToday.halfDay ? baseHours / 2 : 0;
    }
    if (sickToday) {
        const baseHours = getBaseExpectedHoursFromSchedule(dayObj, userConfig, defaultExpectedHours);
        return sickToday.halfDay ? baseHours / 2 : 0;
    }
    return getBaseExpectedHoursFromSchedule(dayObj, userConfig, defaultExpectedHours);
}

function getBaseExpectedHoursFromSchedule(dayObj, userConfig, defaultExpectedHours) {
    const dayOfWeekJs = dayObj.getDay();
    const dayOfWeekName = ['sunday', 'monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday'][dayOfWeekJs];

    if (userConfig?.weeklySchedule && Array.isArray(userConfig.weeklySchedule) && userConfig.weeklySchedule.length > 0 && userConfig?.scheduleCycle > 0) {
        try {
            const epochMonday = getMondayOfWeek(new Date("2020-01-06T00:00:00Z"));
            const currentDayMonday = getMondayOfWeek(dayObj);

            const diffMillis = currentDayMonday.getTime() - epochMonday.getTime();
            const weeksSinceEpoch = Math.floor(diffMillis / (1000 * 60 * 60 * 24 * 7));

            let cycleIndex = weeksSinceEpoch % userConfig.scheduleCycle;
            if (cycleIndex < 0) cycleIndex += userConfig.scheduleCycle;

            if (userConfig.weeklySchedule[cycleIndex] && typeof userConfig.weeklySchedule[cycleIndex][dayOfWeekName] === 'number') {
                return userConfig.weeklySchedule[cycleIndex][dayOfWeekName];
            }
        } catch (e) {
            console.error("Error in getBaseExpectedHoursFromSchedule:", e);
        }
    }
    return (dayOfWeekJs === 0 || dayOfWeekJs === 6) ? 0 : defaultExpectedHours;
}

export function computeDailyDiff(dailySummary, expectedHoursToday, isHourly) {
    if (!dailySummary) return "0h 0m";
    const actualWorkedMinutes = dailySummary.workedMinutes;

    if (isHourly) {
        return minutesToHHMM(actualWorkedMinutes);
    }
    const expectedMinutes = Math.round(expectedHoursToday * 60);
    const diff = actualWorkedMinutes - expectedMinutes;
    return minutesToHHMM(diff);
}

export function calculateWeeklyActualMinutes(userSummariesForWeek) {
    return userSummariesForWeek.reduce((acc, summary) => acc + (summary?.workedMinutes || 0), 0);
}

export function calculateWeeklyExpectedMinutes(
    userConfig, weekDates, defaultExpectedHours,
    userApprovedVacations, userSickLeaves, holidaysForUserCanton,
    userHolidayOptionsForWeek
) {
    if (!userConfig || userConfig.isHourly === true) return 0;

    if (userConfig.isPercentage === true) {
        const pct = userConfig.workPercentage ?? 100;
        const workDaysInModel = userConfig.expectedWorkDays || 5;
        const baseWeeklyHoursFullTimeStandard = defaultExpectedHours * 5;
        const userTargetWeeklyHours = baseWeeklyHoursFullTimeStandard * (pct / 100.0);
        let userTargetWeeklyMinutes = Math.round(userTargetWeeklyHours * 60);

        let absenceAndHolidayDeductionMinutes = 0;
        const valueOfOneUserWorkDayMinutes = workDaysInModel > 0 ? Math.round(userTargetWeeklyMinutes / workDaysInModel) : 0;

        for (const date of weekDates) {
            const isoDate = formatLocalDateYMD(date);
            const dayOfWeekJs = date.getDay();

            let isPotentialWorkDayForUser = true;
            if (workDaysInModel <= 5 && (dayOfWeekJs === 0 || dayOfWeekJs === 6)) isPotentialWorkDayForUser = false;
            if (workDaysInModel === 6 && dayOfWeekJs === 0) isPotentialWorkDayForUser = false;
            if (!isPotentialWorkDayForUser) continue;

            const isHoliday = holidaysForUserCanton && holidaysForUserCanton[isoDate];
            const vacationToday = userApprovedVacations?.find(v => isoDate >= v.startDate && isoDate <= v.endDate && v.approved);
            const sickToday = userSickLeaves?.find(sl => isoDate >= sl.startDate && isoDate <= sl.endDate);

            if (isHoliday) {
                const holidayOption = userHolidayOptionsForWeek?.find(opt => opt.holidayDate === isoDate)?.holidayHandlingOption || 'PENDING_DECISION';
                if (holidayOption === 'DEDUCT_FROM_WEEKLY_TARGET') {
                    absenceAndHolidayDeductionMinutes += valueOfOneUserWorkDayMinutes;
                }
                continue;
            }
            if (vacationToday) {
                absenceAndHolidayDeductionMinutes += vacationToday.halfDay ? Math.round(valueOfOneUserWorkDayMinutes / 2) : valueOfOneUserWorkDayMinutes;
                continue;
            }
            if (sickToday) {
                absenceAndHolidayDeductionMinutes += sickToday.halfDay ? Math.round(valueOfOneUserWorkDayMinutes / 2) : valueOfOneUserWorkDayMinutes;
            }
        }
        return Math.max(0, userTargetWeeklyMinutes - absenceAndHolidayDeductionMinutes);

    } else {
        return weekDates.reduce((acc, date) => {
            const dailyExpectedHours = getExpectedHoursForDay(date, userConfig, defaultExpectedHours, holidaysForUserCanton, userApprovedVacations, userSickLeaves, null);
            return acc + Math.round(dailyExpectedHours * 60);
        }, 0);
    }
}

export function isLateTime(timeString) {
    if (!timeString || typeof timeString !== 'string' || !timeString.includes(':')) return false;
    try {
        const [hours, minutes] = timeString.split(':').map(Number);
        if (isNaN(hours) || isNaN(minutes)) return false;
        return (hours === 23 && minutes >= 20 && minutes <= 25) || (hours === 22 && minutes >=55);
    } catch (e) { return false; }
}

export function getDetailedGlobalProblemIndicators(
    userDailySummaries,
    userApprovedVacations, userConfig, defaultExpectedHours, userSickLeaves,
    holidaysForUserCanton, userAllHolidayOptions
) {
    const indicators = { missingEntriesCount: 0, incompleteDaysCount: 0, autoCompletedUncorrectedCount: 0, holidayPendingCount: 0, problematicDays: [] };
    if (!userConfig || !userDailySummaries) return indicators;

    let checkStartDate = null;
    if (userConfig.scheduleEffectiveDate) checkStartDate = parseISO(userConfig.scheduleEffectiveDate);
    else if (userConfig.companyJoinedDate) checkStartDate = parseISO(userConfig.companyJoinedDate);

    const today = new Date(); today.setHours(0,0,0,0);
    if (!checkStartDate) {
        const earliestEntryDate = userDailySummaries.length > 0 ? parseISO(userDailySummaries[userDailySummaries.length -1].date) : today;
        checkStartDate = new Date(Math.min(today.getTime(), earliestEntryDate.getTime()));
        if (userDailySummaries.length === 0) checkStartDate = new Date(today.getFullYear(), today.getMonth()-1, today.getDate());
    }
    checkStartDate.setHours(0,0,0,0);

    for (let currentDateIter = new Date(checkStartDate); currentDateIter <= today; currentDateIter.setDate(currentDateIter.getDate() + 1)) {
        const isoDate = formatLocalDateYMD(currentDateIter);
        const summary = userDailySummaries.find(s => s.date === isoDate);

        const isHoliday = holidaysForUserCanton && holidaysForUserCanton[isoDate];
        const vacationToday = userApprovedVacations?.find(v => isoDate >= v.startDate && isoDate <= v.endDate && v.approved);
        const sickToday = userSickLeaves?.find(sl => isoDate >= sl.startDate && isoDate <= sl.endDate);
        const holidayOptionForDay = userAllHolidayOptions?.find(opt => opt.holidayDate === isoDate);
        let expectedHoursToday = getExpectedHoursForDay(currentDateIter, userConfig, defaultExpectedHours, holidaysForUserCanton, userApprovedVacations, userSickLeaves, holidayOptionForDay);
        let isPotentiallyWorkDay = expectedHoursToday > 0;

        if (userConfig.isPercentage === true && isHoliday) {
            const handling = holidayOptionForDay?.holidayHandlingOption || 'PENDING_DECISION';
            if (handling === 'DEDUCT_FROM_WEEKLY_TARGET') isPotentiallyWorkDay = false;
            else isPotentiallyWorkDay = (currentDateIter.getDay() >= 1 && currentDateIter.getDay() <= (userConfig.expectedWorkDays || 5));

            if (handling === 'PENDING_DECISION') {
                indicators.holidayPendingCount++;
                indicators.problematicDays.push({ dateIso: isoDate, type: 'holiday_pending_decision' });
            }
        }

        if (isPotentiallyWorkDay && !vacationToday && !sickToday) {
            if (!summary || summary.entries.length === 0) {
                indicators.missingEntriesCount++;
                indicators.problematicDays.push({ dateIso: isoDate, type: 'missing' });
            } else {
                if (summary.primaryTimes.isOpen) {
                    indicators.incompleteDaysCount++;
                    indicators.problematicDays.push({ dateIso: isoDate, type: 'incomplete_work_end_missing' });
                }
                if (summary.needsCorrection) {
                    indicators.autoCompletedUncorrectedCount++;
                    const existingProblemIndex = indicators.problematicDays.findIndex(p => p.dateIso === isoDate && p.type.startsWith('incomplete'));
                    if (existingProblemIndex !== -1) {
                        indicators.problematicDays[existingProblemIndex].type = 'auto_completed_incomplete_uncorrected';
                    } else {
                        indicators.problematicDays.push({ dateIso: isoDate, type: 'auto_completed_uncorrected' });
                    }
                }
            }
        }
    }
    const uniqueProblematicDays = indicators.problematicDays.reduce((acc, current) => {
        if (!acc.find(item => item.dateIso === current.dateIso && item.type === current.type)) {
            acc.push(current);
        }
        return acc;
    }, []);
    indicators.problematicDays = uniqueProblematicDays.sort((a, b) => a.dateIso.localeCompare(b.dateIso));

    indicators.missingEntriesCount = uniqueProblematicDays.filter(p => p.type === 'missing').length;
    indicators.incompleteDaysCount = uniqueProblematicDays.filter(p => p.type.startsWith('incomplete_')).length;
    indicators.autoCompletedUncorrectedCount = uniqueProblematicDays.filter(p => p.type.endsWith('_uncorrected')).length;

    return indicators;
}