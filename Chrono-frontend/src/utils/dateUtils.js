export function getMondayOfWeek(date) {
    const copy = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    const day = copy.getDay();
    const diff = copy.getDate() - day + (day === 0 ? -6 : 1);
    copy.setDate(diff);
    copy.setHours(0,0,0,0);
    return copy;
}

export function addDays(date, days) {
    const d = new Date(date);
    d.setDate(d.getDate() + days);
    return d;
}

export function formatLocalDate(date) {
    if (!date || !(date instanceof Date) || isNaN(date.getTime())) return "";
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

export const formatISO = formatLocalDate;

export function formatDate(dateInput) {
    if (!dateInput) return "-";
    const date = (dateInput instanceof Date) ? dateInput : new Date(dateInput);
    if (isNaN(date.getTime())) return "-";
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();
    return `${day}.${month}.${year}`;
}

export function getWeekdayName(dateInput, locale = 'de-DE') {
    if (!dateInput) return "-";
    const date = (dateInput instanceof Date) ? dateInput : new Date(dateInput);
    if (isNaN(date.getTime())) return "-";
    return date.toLocaleDateString(locale, { weekday: 'long' });
}

export function formatDateWithWeekday(dateInput, locale = 'de-DE') {
    const formattedDate = formatDate(dateInput);
    if (formattedDate === "-") return formattedDate;
    const weekday = getWeekdayName(dateInput, locale);
    if (weekday === "-") return formattedDate;
    return `${formattedDate} (${weekday})`;
}
