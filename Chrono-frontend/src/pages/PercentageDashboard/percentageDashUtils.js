//--------------------------------------------------
// parseHex16
//--------------------------------------------------
export function parseHex16(hexString) {
    if (!hexString) return null;
    const clean = hexString.replace(/\s+/g, '');
    if (clean.length !== 32) return null;
    let out = '';
    for (let i = 0; i < 16; i++) {
        const val = parseInt(clean.slice(i * 2, i * 2 + 2), 16);
        if (val !== 0) out += String.fromCharCode(val);
    }
    return out;
}

//--------------------------------------------------
// Zeit- und Datums‐Utilities
//--------------------------------------------------
export function minutesToHours(min) {
    if (min == null) return "0h 0min";
    const absMins = Math.abs(min);
    const sign = min < 0 ? "-" : "";
    const h = Math.floor(absMins / 60);
    const m = absMins % 60;
    return `${sign}${h}h ${m}min`;
}

/**
 * getMondayOfWeek(d):
 *   Ermittelt den Montag der Kalenderwoche, in der das Datum d liegt,
 *   arbeitet rein mit lokalem Datum, damit kein Zeitzonen-Offset entsteht.
 */
export function getMondayOfWeek(date) {
    // “Abstreifen” von Uhrzeit: wir erzeugen ein neues Date nur aus y/m/d
    const local = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    // Wochentag (So=0, Mo=1, Di=2, …)
    const day = local.getDay();
    // Wenn Sonntag, diff=6, sonst day-1
    const diff = day === 0 ? 6 : day - 1;
    // Zurück auf Montag
    local.setDate(local.getDate() - diff);
    // => local hat jetzt Mo… – Mitternacht (lokal)
    return local;
}

/** addDays(d, n) – simpel: addiere n Tage auf lokales Datum. */
export const addDays = (d, n) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);

/** formatISO(d) = "yyyy-MM-dd" (lokal), aber ohne TZ-Offset. */
export function formatLocalISO(date) {
    const year  = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day   = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

export const formatISO = (d) => formatLocalISO(d);

/** formatDate(isoStrOrDate) = "dd.mm.yyyy" */
export const formatDate = (isoOrDate) => {
    // Falls es schon Date ist:
    if (isoOrDate instanceof Date) {
        const dd = String(isoOrDate.getDate()).padStart(2, '0');
        const mm = String(isoOrDate.getMonth() + 1).padStart(2, '0');
        const yy = isoOrDate.getFullYear();
        return `${dd}.${mm}.${yy}`;
    }
    // sonst iso-String "yyyy-MM-dd"
    if (typeof isoOrDate === 'string' && isoOrDate.length >= 10) {
        const [y, m, d] = isoOrDate.slice(0,10).split("-");
        return `${d}.${m}.${y}`;
    }
    return "-";
};

/** formatTime(stamp): zeigt "HH:MM" in deutscher Zeit an. */
export function formatTime(stamp) {
    if (!stamp) return '-';
    const d = new Date(stamp);
    // Lokales "HH:MM"
    return d.toLocaleTimeString('de-DE', {
        hour: '2-digit',
        minute: '2-digit',
        timeZone: 'Europe/Berlin'
    });
}

// Hilfsfunktionen zu Minuten
export const timeToMinutes = (hhmm) => {
    const [h, m] = hhmm.split(':').map(Number);
    return (h * 60) + (m || 0);
};
export const minutesSinceMidnight = (stamp) => {
    const d = new Date(stamp);
    return d.getHours() * 60 + d.getMinutes();
};

/**
 * computeDayTotalMinutes(dayEntries):
 *   Summiert die reine Arbeitszeit (ohne Pause).
 *   Sucht PunchOrder=1 (WorkStart) + PunchOrder=4 (WorkEnd),
 *   und ggf. PunchOrder=2/3 (BreakStart/BreakEnd).
 */
export function computeDayTotalMinutes(dayEntries) {
    const ws = dayEntries.find(e => e.punchOrder === 1);
    const we = dayEntries.find(e => e.punchOrder === 4);
    if (!ws || !we) return 0;

    let mins = (new Date(we.endTime || we.startTime) - new Date(ws.startTime)) / 60000;

    const bs = dayEntries.find(e => e.punchOrder === 2);
    const be = dayEntries.find(e => e.punchOrder === 3);
    if (bs && be) {
        const s = bs.breakStart
            ? timeToMinutes(bs.breakStart)
            : minutesSinceMidnight(bs.startTime);
        const e = be.breakEnd
            ? timeToMinutes(be.breakEnd)
            : minutesSinceMidnight(be.startTime);
        mins -= (e - s);
    }
    return Math.max(0, mins);
}

/** expectedDayMinutes(user) – rechne 8.5h=510 bei isPercentage, 0 bei isHourly, sonst 8h=480 */
export const expectedDayMinutes = (u) => {
    if (u.isPercentage) {
        const p = (u.workPercentage ?? 100) / 100;
        return Math.round(510 * p); // 8.5h * p
    }
    if (u.isHourly) {
        return 0;
    }
    // sonst normal => 480
    return 480;
};

/** isLateTime("HH:MM") – optional: späte Zeit >=22:10 oder <=23:50. */
export function isLateTime(timeStr) {
    if (!timeStr) return false;
    const [hours, minutes] = timeStr.split(":").map(Number);
    // z.B. wenn ab 22:10 bis 23:50 => true
    return (
        (hours === 22 && minutes >= 10) ||
        (hours === 23 && minutes <= 50)
    );
}
