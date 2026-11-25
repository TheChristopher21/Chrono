import { formatTime } from './adminDashboardUtils';

export const hasOriginalDay = entry => !entry.originalTimestamp && entry.originalDayEntries?.length > 0;

export const originalTimeHeading = (entry, t) => hasOriginalDay(entry)
    ? t('adminDashboard.originalDayTimes', 'Bisherige Tagesstempel')
    : t('adminDashboard.originalTimeLabel', 'Gestempelt');

// Requests without a target ID represent a replacement of the whole day.
// Show the actual previous day instead of inventing a one-to-one stamp pairing.
export const originalDayText = (entry, t) => hasOriginalDay(entry)
    ? entry.originalDayEntries.map(stamp => `${formatTime(stamp.entryTimestamp)} ${t(`punchTypes.${stamp.punchType}`, stamp.punchType)}`).join(' · ')
    : t('adminDashboard.noOriginalTimeLabel', 'Kein ursprünglicher Stempel');
