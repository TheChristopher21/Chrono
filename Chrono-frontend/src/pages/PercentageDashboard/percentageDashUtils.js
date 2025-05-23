// percentageDashUtils.js

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
export const pickTime = (e, ...cand) => {
    for (const c of cand) {
        if (e && e[c]) return e[c];
    }
    return null;
};

// Zeitstempel formatiert anzeigen
export function formatTime(stamp) {
    if (!stamp) return "-";
    const d = new Date(stamp);
    return isNaN(d) ? "-" : d.toLocaleTimeString("de-DE", {
        hour: "2-digit",
        minute: "2-digit",
        timeZone: "Europe/Berlin",
    });
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

export function getMondayOfWeek(date) {
    const local = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    const day = local.getDay();
    const diff = day === 0 ? 6 : day - 1;
    local.setDate(local.getDate() - diff);
    return local;
}

export const addDays = (d, n) =>
    new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);

export function formatLocalISO(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}
export const formatISO = (d) => formatLocalISO(d);

export const formatDate = (isoOrDate) => {
    if (isoOrDate instanceof Date) {
        const dd = String(isoOrDate.getDate()).padStart(2, '0');
        const mm = String(isoOrDate.getMonth() + 1).padStart(2, '0');
        const yy = isoOrDate.getFullYear();
        return `${dd}.${mm}.${yy}`;
    }
    if (typeof isoOrDate === 'string' && isoOrDate.length >= 10) {
        const [y, m, d] = isoOrDate.slice(0, 10).split("-");
        return `${d}.${m}.${y}`;
    }
    return "-";
};



export const timeToMinutes = (hhmm) => {
    const [h, m] = hhmm.split(':').map(Number);
    return (h * 60) + (m || 0);
};
export const minutesSinceMidnight = (stamp) => {
    const d = new Date(stamp);
    return d.getHours() * 60 + d.getMinutes();
};

export function computeDayTotalMinutes(dayEntries) {
    const ws = dayEntries.find(e => e.punchOrder === 1);
    const we = dayEntries.find(e => e.punchOrder === 4);
    if (!ws || !we) return 0;

    const start = new Date(pickTime(ws, "workStart", "startTime"));
    const end   = new Date(pickTime(we, "workEnd",   "endTime",   "startTime"));

    let mins = (end - start) / 60000;

    const bs = dayEntries.find(e => e.punchOrder === 2);
    const be = dayEntries.find(e => e.punchOrder === 3);
    if (bs && be) {
        const bStart = new Date(pickTime(bs, "breakStart", "startTime"));
        const bEnd   = new Date(pickTime(be, "breakEnd",   "endTime",   "startTime"));
        mins -= (bEnd - bStart) / 60000;
    }
    return Math.max(0, mins);
}

export const expectedDayMinutes = (u) => {
    if (u.isPercentage) {
        const p = (u.workPercentage ?? 100) / 100;
        return Math.round(510 * p); // 8.5h * p
    }
    if (u.isHourly) return 0;
    return 480; // fallback 8h
};

export function isLateTime(timeStr) {
    if (!timeStr) return false;
    const [hours, minutes] = timeStr.split(":").map(Number);
    // Beispiel: >= 22:10 bis <= 23:50
    return (
        (hours === 22 && minutes >= 10) ||
        (hours === 23 && minutes <= 50)
    );
}
