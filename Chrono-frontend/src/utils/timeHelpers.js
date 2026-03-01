// src/utils/timeHelpers.js
/**
 * Wandelt eine einzige Tages-Zeile aus 'time_tracking_new'
 * in 1–4 Punch-Einträge (punchOrder 1..4) um.
 */
export function expandDayRows(dayRows = []) {
    const expanded = [];

    dayRows.forEach(row => {
        const base = {
            id:        row.id,          // behalten, falls vorhanden
            userId:    row.userId,
            username:  row.username,    // kommt im Admin-Dashboard noch vor
            dailyDate: row.dailyDate
        };

        if (row.workStart) {
            expanded.push({ ...base,
                punchOrder: 1,
                startTime:  `${row.dailyDate}T${row.workStart}`,
                workStart:  row.workStart
            });
        }
        if (row.breakStart) {
            expanded.push({ ...base,
                punchOrder: 2,
                startTime:  `${row.dailyDate}T${row.breakStart}`,
                breakStart: row.breakStart
            });
        }
        if (row.breakEnd) {
            expanded.push({ ...base,
                punchOrder: 3,
                startTime:  `${row.dailyDate}T${row.breakEnd}`,
                breakEnd:   row.breakEnd
            });
        }
        if (row.workEnd) {
            expanded.push({ ...base,
                punchOrder: 4,
                startTime:  `${row.dailyDate}T${row.workEnd}`,
                endTime:    `${row.dailyDate}T${row.workEnd}`,
                workEnd:    row.workEnd
            });
        }
    });

    return expanded;
}
