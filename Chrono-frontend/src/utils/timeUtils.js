export function formatTime(dateInput) {
    if (!dateInput) {
        return "--:--";
    }
    try {
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
export const minutesToHours = minutesToHHMM;

export function formatPunchedTimeFromEntry(entry) {
    if (!entry || !entry.entryTimestamp) return '-';
    return formatTime(entry.entryTimestamp);
}

export function isLateTime(timeString) {
    if (!timeString || typeof timeString !== 'string' || !timeString.includes(':')) return false;
    try {
        const [hours, minutes] = timeString.split(':').map(Number);
        if (isNaN(hours) || isNaN(minutes)) return false;
        return (hours === 23 && minutes >= 20 && minutes <= 25) || (hours === 22 && minutes >=55);
    } catch (e) {
        return false;
    }
}

export function sortEntries(entries) {
    return [...(entries || [])].sort(
        (a, b) => new Date(a.entryTimestamp) - new Date(b.entryTimestamp)
    );
}
