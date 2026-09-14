const DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})/;
const TIME_PATTERN = /T(\d{2}):(\d{2})/;

export const formatPmsDate = (value) => {
    const match = String(value ?? '').match(DATE_PATTERN);
    if (!match) {
        return 'Nicht angegeben';
    }
    return `${match[3]}.${match[2]}.${match[1]}`;
};

export const formatPmsDateTime = (value) => {
    const date = formatPmsDate(value);
    const time = String(value ?? '').match(TIME_PATTERN);
    if (date === 'Nicht angegeben' || !time) {
        return date;
    }
    return `${date}, ${time[1]}:${time[2]} Uhr`;
};
