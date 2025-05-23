// hourDashUtils.js
const PLAIN_TIME_RX = /^\d{2}:\d{2}(?::\d{2})?$/;

// NFC-Parsing: Liest aus 32-stelligem Hex-String den Usernamen
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
// Gibt HH:mm zurück, egal in welchem Feld die Uhrzeit steckt.
// raw kann ein Date-String "2025-05-14T05:45:00" oder "05:45:00" sein.
export function pickTime(entry, preferredField) {
    if (!entry) return null;

    const raw =
        entry[preferredField] ||
        entry.startTime ||
        entry.endTime ||
        entry.workStart ||
        entry.breakStart ||
        entry.breakEnd ||
        entry.workEnd;

    if (!raw) return null;

    //  ⬇ HH:mm extrahieren
    const iso = typeof raw === "string" ? raw : raw.toString();
    return iso.slice(11, 16) || iso.slice(0, 5);
}

export function getMondayOfWeek(date) {
    const copy = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
    const day = copy.getUTCDay();
    const diff = day === 0 ? 6 : day - 1;
    copy.setUTCDate(copy.getUTCDate() - diff);
    copy.setUTCHours(0, 0, 0, 0);
    return copy;
}

export function addDays(date, days) {
    const d = new Date(date);
    d.setDate(d.getDate() + days);
    return d;
}

// Liefert Datum im Format yyyy-mm-dd
export function formatLocalDate(date) {
    return date.toISOString().slice(0, 10);
}

// "YYYY-MM-DD" -> "DD-MM-YYYY"
export function formatDate(dateStr) {
    if (!dateStr) return '-';
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return '-';
    const day = String(d.getDate()).padStart(2, '0');
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const year = d.getFullYear();
    return `${day}-${month}-${year}`;
}

// "YYYY-MM-DDTHH:mm:ss" -> "HH:mm"
export function toTimeInput(dateStr) {
    if (!dateStr) return "";
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return "";
    const hh = String(d.getHours()).padStart(2, '0');
    const mm = String(d.getMinutes()).padStart(2, '0');
    return `${hh}:${mm}`;
}

// Zeitstempel formatiert anzeigen
export function formatTime(value) {
    if (!value) return '-';

    //  ⏰  Nur HH:mm(:ss) ?
    if (typeof value === 'string' && PLAIN_TIME_RX.test(value)) {
        return value.slice(0, 5);           // nur HH:mm anzeigen
    }

    const d = new Date(value);
    if (isNaN(d.getTime())) return '-';

    return d.toLocaleTimeString('de-DE', {
        hour:   '2-digit',
        minute: '2-digit',
        timeZone: 'Europe/Berlin'
    });
}

export function parseTimeToMinutes(timeStr) {
    if (!timeStr) return 0;
    const parts = timeStr.split(':');
    const hours = parseInt(parts[0], 10);
    const minutes = parseInt(parts[1], 10);
    return hours * 60 + minutes;
}

export function getMinutesSinceMidnight(dateStr) {
    if (!dateStr) return 0;
    const d = new Date(dateStr);
    return d.getHours() * 60 + d.getMinutes();
}

/**
 * Berechnet die Arbeitsminuten eines Tages (Work Start-End / Pausen abziehen).
 */
export function computeDayTotalMinutes(dayEntries) {

    /* ----- ✅ NEUER MODE: genau 1 Eintrag mit workStart/... ---------- */
    if (Array.isArray(dayEntries) &&
        dayEntries.length === 1 &&
        dayEntries[0].workStart /*⇢ daily-table-Datensatz */) {

        const {workStart, breakStart, breakEnd, workEnd} = dayEntries[0];
        if (!workStart || !workEnd) return 0;

        const start = parseTimeToMinutes(workStart);
        const end = parseTimeToMinutes(workEnd) >= start
            ? parseTimeToMinutes(workEnd)
            : parseTimeToMinutes(workEnd) + 24 * 60;

        let minutes = end - start;

        if (breakStart && breakEnd) {
            const bs = parseTimeToMinutes(breakStart);
            const be = parseTimeToMinutes(breakEnd) >= bs
                ? parseTimeToMinutes(breakEnd)
                : parseTimeToMinutes(breakEnd) + 24 * 60;
            minutes -= (be - bs);
        }
        return Math.max(0, minutes);
    }
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
    const lateEnd   = new Date('1970-01-01T23:40:00');
    return time >= lateStart && time <= lateEnd;
}
