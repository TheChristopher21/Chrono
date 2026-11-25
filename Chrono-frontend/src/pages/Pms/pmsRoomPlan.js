/** Calendar dates stay in the hotel's timezone; UTC is used only for date arithmetic. */
export const hotelToday = (timezone = 'Europe/Zurich', now = new Date()) => {
    const parts = new Intl.DateTimeFormat('en-CA', { timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(now);
    return ['year', 'month', 'day'].map((key) => parts.find((part) => part.type === key).value).join('-');
};

export const addPlanDays = (value, days) => {
    const date = new Date(`${value}T12:00:00Z`);
    date.setUTCDate(date.getUTCDate() + days);
    return date.toISOString().slice(0, 10);
};

export const planDayLabel = (value) => {
    const date = new Date(`${value}T12:00:00Z`);
    return `${['So.', 'Mo.', 'Di.', 'Mi.', 'Do.', 'Fr.', 'Sa.'][date.getUTCDay()]} ${value.slice(8, 10)}.${value.slice(5, 7)}`;
};

export const planDayDistance = (from, to) => Math.round((Date.parse(`${to}T12:00:00Z`) - Date.parse(`${from}T12:00:00Z`)) / 86400000);

/** Clip at the viewport, keep departure exclusive, and preserve overlapping events in separate lanes. */
export const layoutRoomEvents = (reservations, blocks, from, days) => {
    const events = [
        ...reservations.map((entry) => ({ kind: 'reservation', entry, startDate: entry.segmentStartDate || entry.arrivalDate, endDate: entry.segmentEndDate || entry.departureDate })),
        ...blocks.map((entry) => ({ kind: 'block', entry, startDate: entry.startDate, endDate: entry.endDate })),
    ].map((event) => ({ ...event, start: Math.max(0, planDayDistance(from, event.startDate)), end: Math.min(days, planDayDistance(from, event.endDate)) }))
        .filter((event) => event.end > event.start).sort((a, b) => a.start - b.start || a.end - b.end);
    const lanes = [];
    return events.map((event) => {
        let lane = lanes.findIndex((end) => end <= event.start);
        if (lane < 0) lane = lanes.length;
        lanes[lane] = event.end;
        return { ...event, lane };
    });
};
