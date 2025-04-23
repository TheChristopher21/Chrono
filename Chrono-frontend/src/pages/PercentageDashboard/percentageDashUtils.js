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
export function minutesToHours(min) {
    if (min == null) return "0h 0min";

    const h = Math.floor(Math.abs(min) / 60);
    const m = (Math.abs(min) % 60).toFixed(2); // hier wird gerundet

    const sign = min < 0 ? "-" : "";
    return `${sign}${h}h ${m}min`;
}


export function getMondayOfWeek(date) {
    const copy = new Date(date);
    const day = copy.getDay();
    const diff = day === 0 ? 6 : day - 1;  // Wenn Sonntag, dann zurück zum Montag
    copy.setDate(copy.getDate() - diff);

    // Setze die Zeit auf 00:00 in der Zeitzone Europa/Zurich
    const options = { timeZone: 'Europe/Zurich' };
    const monday = copy.toLocaleString('en-US', options);  // Wandle in String mit Zeitzone
    const mondayDate = new Date(monday);
    mondayDate.setHours(0, 0, 0, 0); // Setze die Uhrzeit auf Mitternacht

    return mondayDate;
}


export const addDays = (d, n) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
export const formatISO = (d) => d.toISOString().slice(0, 10);
export const formatDate = (iso) => {
    const isoStr = typeof iso === "string" ? iso : iso?.toISOString?.().slice(0, 10);
    if (!isoStr) return "–";

    const [year, month, day] = isoStr.split("-");
    return `${day}.${month}.${year}`;
};


export function formatTime(stamp) {
    if (!stamp) return '-';
    const d = new Date(stamp);
    return d.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Berlin' });
}

// Minuten‑Rechenhelfer
export const timeToMinutes = (hhmm) => {
    const [h, m] = hhmm.split(':').map(Number);
    return h * 60 + m;
};
export const minutesSinceMidnight = (stamp) => {
    const d = new Date(stamp);
    return d.getHours() * 60 + d.getMinutes();
};

export function computeDayTotalMinutes(dayEntries) {
    const ws = dayEntries.find(e => e.punchOrder === 1);
    const we = dayEntries.find(e => e.punchOrder === 4);
    if (!ws || !we) return 0;

    let mins = (new Date(we.endTime || we.startTime) - new Date(ws.startTime)) / 60000;

    const bs = dayEntries.find(e => e.punchOrder === 2);
    const be = dayEntries.find(e => e.punchOrder === 3);
    if (bs && be) {
        const s = bs.breakStart ? timeToMinutes(bs.breakStart) : minutesSinceMidnight(bs.startTime);
        const e = be.breakEnd   ? timeToMinutes(be.breakEnd)   : minutesSinceMidnight(be.startTime);
        mins -= (e - s);
    }
    return Math.max(0, mins);
}

export const expectedDayMinutes = (u) =>
    u.isPercentage ? Math.round(510 * ((u.workPercentage ?? 100) / 100))
        : u.isHourly  ? 0
            : 480;
// /utils/timeUtils.js
export function isLateTime(timeStr) {
    if (!timeStr) return false;
    const [hours, minutes] = timeStr.split(":").map(Number);
    return (
        (hours === 22 && minutes >= 10) ||
        (hours === 23 && minutes <= 50)
    );
}
