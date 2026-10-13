import { describe, expect, it } from "vitest";
import {
    DEFAULT_WEEK_PATTERN,
    calculateWorkTime,
    buildHolidayMap,
    getYearsBetween,
    roundHours,
} from "../workTimeCalculator.js";

describe("workTimeCalculator", () => {
    it("calculates gross and target hours with a holiday on a workday", () => {
        const holidays = buildHolidayMap({ "2026-01-01": "Neujahr" });
        const result = calculateWorkTime({
            startDate: "2026-01-01",
            endDate: "2026-01-02",
            weekPattern: DEFAULT_WEEK_PATTERN,
            workloadPercent: 100,
            holidays,
        });

        expect(result.summary.calendarDays).toBe(2);
        expect(result.summary.grossWorkdays).toBe(2);
        expect(result.summary.holidayWorkdays).toBe(1);
        expect(roundHours(result.summary.grossHours)).toBe(17);
        expect(roundHours(result.summary.targetHours)).toBe(8.5);
        expect(roundHours(result.summary.weeklyContractHours)).toBe(42.5);
    });

    it("applies workload percent and pre-holiday reduction", () => {
        const holidays = buildHolidayMap({ "2026-12-25": "Weihnachten" });
        const result = calculateWorkTime({
            startDate: "2026-12-24",
            endDate: "2026-12-25",
            weekPattern: DEFAULT_WEEK_PATTERN,
            workloadPercent: 50,
            holidays,
            preHolidayReductionHours: 1,
        });

        expect(roundHours(result.summary.grossHours)).toBe(8.5);
        expect(roundHours(result.summary.preHolidayReductionHours)).toBe(1);
        expect(roundHours(result.summary.holidayHours)).toBe(4.25);
        expect(roundHours(result.summary.targetHours)).toBe(3.25);
        expect(roundHours(result.summary.weeklyContractHours)).toBe(21.25);
    });

    it("matches the full 2026 St. Gallen scenario with 8.5 hours Monday to Friday", () => {
        const holidays = buildHolidayMap({
            "2026-01-01": "Neujahr",
            "2026-01-02": "Berchtoldstag",
            "2026-04-03": "Karfreitag",
            "2026-04-06": "Ostermontag",
            "2026-05-01": "Tag der Arbeit",
            "2026-05-14": "Auffahrt",
            "2026-05-25": "Pfingstmontag",
            "2026-08-01": "Nationalfeiertag",
            "2026-11-01": "Allerheiligen",
            "2026-12-25": "Weihnachten",
            "2026-12-26": "Stephanstag",
        });

        const result = calculateWorkTime({
            startDate: "2026-01-01",
            endDate: "2026-12-31",
            weekPattern: DEFAULT_WEEK_PATTERN,
            workloadPercent: 100,
            holidays,
        });

        expect(result.summary.calendarDays).toBe(365);
        expect(result.summary.grossWorkdays).toBe(261);
        expect(result.summary.holidayWorkdays).toBe(8);
        expect(result.summary.netWorkdays).toBe(253);
        expect(roundHours(result.summary.grossHours)).toBe(2218.5);
        expect(roundHours(result.summary.holidayHours)).toBe(68);
        expect(roundHours(result.summary.targetHours)).toBe(2150.5);
        expect(roundHours(result.summary.weeklyContractHours)).toBe(42.5);
        expect(roundHours(result.summary.averageNetWeeklyHours)).toBe(41.24);
    });

    it("matches the full 2026 baseline without holiday deductions", () => {
        const result = calculateWorkTime({
            startDate: "2026-01-01",
            endDate: "2026-12-31",
            weekPattern: DEFAULT_WEEK_PATTERN,
            workloadPercent: 100,
            holidays: new Map(),
        });

        expect(result.summary.calendarDays).toBe(365);
        expect(result.summary.grossWorkdays).toBe(261);
        expect(result.summary.netWorkdays).toBe(261);
        expect(roundHours(result.summary.targetHours)).toBe(2218.5);
        expect(roundHours(result.summary.weeklyContractHours)).toBe(42.5);
    });


    it("does not reduce the previous workday when the holiday falls on an inactive day", () => {
        const holidays = buildHolidayMap({ "2026-08-01": "Nationalfeiertag" });
        const result = calculateWorkTime({
            startDate: "2026-07-31",
            endDate: "2026-08-01",
            weekPattern: DEFAULT_WEEK_PATTERN,
            workloadPercent: 100,
            holidays,
            preHolidayReductionHours: 1,
        });

        expect(roundHours(result.summary.preHolidayReductionHours)).toBe(0);
        expect(roundHours(result.summary.targetHours)).toBe(8.5);
    });

    it("normalizes reversed ranges consistently", () => {
        const result = calculateWorkTime({
            startDate: "2026-01-05",
            endDate: "2026-01-01",
            weekPattern: DEFAULT_WEEK_PATTERN,
            workloadPercent: 100,
            holidays: new Map(),
        });

        expect(result.summary.calendarDays).toBe(5);
        expect(result.summary.grossWorkdays).toBe(3);
        expect(getYearsBetween("2027-01-01", "2026-12-31")).toEqual([2026, 2027]);
    });

    it("keeps monthly totals equal to the summary totals", () => {
        const holidays = buildHolidayMap({ "2026-01-01": "Neujahr", "2026-04-03": "Karfreitag" });
        const result = calculateWorkTime({
            startDate: "2026-01-01",
            endDate: "2026-04-30",
            weekPattern: DEFAULT_WEEK_PATTERN,
            workloadPercent: 80,
            holidays,
            preHolidayReductionHours: 0.5,
        });
        const monthlyTargetHours = result.months.reduce((sum, month) => sum + month.targetHours, 0);
        const monthlyGrossWorkdays = result.months.reduce((sum, month) => sum + month.grossWorkdays, 0);

        expect(roundHours(monthlyTargetHours)).toBe(roundHours(result.summary.targetHours));
        expect(monthlyGrossWorkdays).toBe(result.summary.grossWorkdays);
    });
});
