import React, { useCallback, useEffect, useMemo, useState } from 'react';
import Navbar from '../../components/Navbar';
import { useTranslation } from '../../context/LanguageContext';
import { useNotification } from '../../context/NotificationContext';
import { useAuth } from '../../context/AuthContext';
import api from '../../utils/api';
import {
    getMondayOfWeek,
    addDays,
    formatLocalDateYMD,
    calculateWeeklyExpectedMinutes,
    minutesToHHMM,
} from '../AdminDashboard/adminDashboardUtils';
import { Line, Bar, Doughnut } from 'react-chartjs-2';
import {
    Chart as ChartJS,
    CategoryScale,
    LinearScale,
    PointElement,
    LineElement,
    BarElement,
    ArcElement,
    Tooltip,
    Legend,
    Filler,
} from 'chart.js';
import '../../styles/AdminAnalyticsPageScoped.css';

ChartJS.register(
    CategoryScale,
    LinearScale,
    PointElement,
    LineElement,
    BarElement,
    ArcElement,
    Tooltip,
    Legend,
    Filler,
);

const DEFAULT_EXPECTED_HOURS = 8.5;

const AdminAnalyticsPage = () => {
    const { t } = useTranslation();
    const { notify } = useNotification();
    const { currentUser } = useAuth();

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [users, setUsers] = useState([]);
    const [dailySummaries, setDailySummaries] = useState([]);
    const [allVacations, setAllVacations] = useState([]);
    const [allSickLeaves, setAllSickLeaves] = useState([]);
    const [weeklyBalances, setWeeklyBalances] = useState([]);
    const [holidaysCache, setHolidaysCache] = useState({});
    const [selectedWeeks, setSelectedWeeks] = useState(12);
    const [selectedUsernames, setSelectedUsernames] = useState([]);

    const trackableUsers = useMemo(
        () => (Array.isArray(users) ? users.filter(user => user?.includeInTimeTracking !== false) : []),
        [users]
    );

    useEffect(() => {
        let isMounted = true;
        const fetchData = async () => {
            setLoading(true);
            try {
                const [usersRes, summariesRes, vacationsRes, sickRes, balancesRes] = await Promise.all([
                    api.get('/api/admin/users'),
                    api.get('/api/admin/timetracking/all-summaries'),
                    api.get('/api/vacation/all'),
                    api.get('/api/sick-leave/company'),
                    api.get('/api/admin/timetracking/admin/tracking-balances'),
                ]);
                if (!isMounted) {
                    return;
                }
                setUsers(Array.isArray(usersRes.data) ? usersRes.data : []);
                setDailySummaries(Array.isArray(summariesRes.data) ? summariesRes.data : []);
                setAllVacations(Array.isArray(vacationsRes.data) ? vacationsRes.data : []);
                setAllSickLeaves(Array.isArray(sickRes.data) ? sickRes.data : []);
                setWeeklyBalances(Array.isArray(balancesRes.data) ? balancesRes.data : []);
                setError(null);
            } catch (err) {
                if (!isMounted) {
                    return;
                }
                console.error('Failed to fetch analytics data', err);
                setError(err);
                notify(t('adminAnalytics.fetchError', 'Analytics konnten nicht geladen werden.'), 'error');
            } finally {
                if (isMounted) {
                    setLoading(false);
                }
            }
        };

        fetchData();
        return () => {
            isMounted = false;
        };
    }, [notify, t]);

    const selectableUsers = useMemo(
        () =>
            users
                .filter(user => !user.isHourly && user?.username)
                .slice()
                .sort((a, b) => (a.username || '').localeCompare(b.username || '', undefined, { sensitivity: 'base' })),
        [users],
    );

    const selectableUsernames = useMemo(() => selectableUsers.map(user => user.username), [selectableUsers]);

    useEffect(() => {
        setSelectedUsernames(prev => {
            if (!prev.length) {
                return prev;
            }
            const allowed = new Set(selectableUsernames);
            const filtered = prev.filter(username => allowed.has(username));
            return filtered.length === prev.length ? prev : filtered;
        });
    }, [selectableUsernames]);

    const userListMaxHeight = useMemo(() => {
        const visibleItems = Math.min(8, Math.max(3, selectableUsers.length || 0));
        return `${visibleItems * 2.6}rem`;
    }, [selectableUsers.length]);

    const analysisWeeks = useMemo(() => {
        const formatter = new Intl.DateTimeFormat(undefined, { month: 'short', day: '2-digit' });
        const currentMonday = getMondayOfWeek(new Date());
        const totalWeeks = Number.isFinite(selectedWeeks) && selectedWeeks > 0 ? selectedWeeks : 12;
        return Array.from({ length: totalWeeks }, (_, idx) => {
            const offset = totalWeeks - 1 - idx;
            const monday = addDays(currentMonday, -7 * offset);
            const dates = Array.from({ length: 7 }, (_, dayIndex) => addDays(new Date(monday), dayIndex));
            const end = dates[dates.length - 1];
            const label = `${formatter.format(monday)} – ${formatter.format(end)}`;
            return { monday, dates, label };
        });
    }, [selectedWeeks]);

    const weekRangeOptions = useMemo(
        () => [
            { value: 4, label: t('adminAnalytics.filters.rangeOption.fourWeeks', 'Letzte 4 Wochen') },
            { value: 8, label: t('adminAnalytics.filters.rangeOption.eightWeeks', 'Letzte 8 Wochen') },
            { value: 12, label: t('adminAnalytics.filters.rangeOption.twelveWeeks', 'Letzte 12 Wochen') },
            { value: 24, label: t('adminAnalytics.filters.rangeOption.twentyFourWeeks', 'Letzte 24 Wochen') },
        ],
        [t],
    );

    const currentRangeLabel = useMemo(() => {
        const option = weekRangeOptions.find(opt => opt.value === selectedWeeks);
        return option ? option.label : '';
    }, [weekRangeOptions, selectedWeeks]);

    const handleRangeChange = useCallback(event => {
        const value = Number(event.target.value);
        setSelectedWeeks(Number.isFinite(value) && value > 0 ? value : 12);
    }, []);

    const handleToggleUserSelection = useCallback(username => {
        setSelectedUsernames(prev => {
            if (prev.includes(username)) {
                return prev.filter(entry => entry !== username);
            }
            return [...prev, username];
        });
    }, []);

    const handleSelectAllUsers = useCallback(() => {
        setSelectedUsernames([...selectableUsernames]);
    }, [selectableUsernames]);

    const handleResetUsers = useCallback(() => {
        setSelectedUsernames([]);
    }, []);

    const selectionSummary = useMemo(() => {
        if (!selectableUsers.length) {
            return null;
        }
        if (!selectedUsernames.length) {
            return t('adminAnalytics.filters.autoSelectionLabel', 'Top 5 (automatisch)');
        }
        if (selectedUsernames.length === selectableUsers.length) {
            return t('adminAnalytics.filters.allSelectedLabel', 'Alle');
        }
        return `${selectedUsernames.length}/${selectableUsers.length}`;
    }, [selectableUsers.length, selectedUsernames, t]);

    const displayRangeLabel = useMemo(() => {
        if (currentRangeLabel) {
            return currentRangeLabel;
        }
        return `${selectedWeeks} ${t('adminAnalytics.filters.weeksSuffix', 'Wochen')}`;
    }, [currentRangeLabel, selectedWeeks, t]);

    useEffect(() => {
        if (!analysisWeeks.length) {
            return;
        }
        const cantons = new Set();
        if (currentUser?.companyCantonAbbreviation) {
            cantons.add(currentUser.companyCantonAbbreviation);
        }
        trackableUsers.forEach(user => {
            if (user.companyCantonAbbreviation) {
                cantons.add(user.companyCantonAbbreviation);
            }
        });
        if (cantons.size === 0) {
            cantons.add('');
        }

        const years = new Set();
        analysisWeeks.forEach(({ dates }) => {
            const first = dates[0];
            const last = dates[dates.length - 1];
            years.add(first.getFullYear());
            years.add(last.getFullYear());
        });

        const fetchJobs = [];
        cantons.forEach(canton => {
            const cacheKey = canton || 'GENERAL';
            years.forEach(year => {
                if (holidaysCache[cacheKey]?.[year]) {
                    return;
                }
                fetchJobs.push({ canton, cacheKey, year });
            });
        });

        if (!fetchJobs.length) {
            return;
        }

        let isMounted = true;
        (async () => {
            try {
                const responses = await Promise.all(
                    fetchJobs.map(job => {
                        const startDate = `${job.year}-01-01`;
                        const endDate = `${job.year}-12-31`;
                        return api
                            .get('/api/holidays/details', {
                                params: {
                                    cantonAbbreviation: job.canton,
                                    year: job.year,
                                    startDate,
                                    endDate,
                                },
                            })
                            .then(res => ({ job, data: res.data || {} }))
                            .catch(err => {
                                console.error('Failed to fetch holidays for analytics', err);
                                return { job, data: {} };
                            });
                    }),
                );
                if (!isMounted) {
                    return;
                }
                setHolidaysCache(prev => {
                    const next = { ...prev };
                    responses.forEach(({ job, data }) => {
                        if (!next[job.cacheKey]) {
                            next[job.cacheKey] = {};
                        }
                        next[job.cacheKey] = { ...next[job.cacheKey], [job.year]: data };
                    });
                    return next;
                });
            } catch (err) {
                console.error('Unexpected error while preloading holidays', err);
            }
        })();

        return () => {
            isMounted = false;
        };
    }, [analysisWeeks, trackableUsers, currentUser, holidaysCache]);

    const userSummaryDateMap = useMemo(() => {
        const map = new Map();
        dailySummaries.forEach(summary => {
            if (!summary?.username || !summary?.date) {
                return;
            }
            if (!map.has(summary.username)) {
                map.set(summary.username, new Map());
            }
            map.get(summary.username).set(summary.date, summary);
        });
        return map;
    }, [dailySummaries]);

    const vacationsByUser = useMemo(() => {
        const map = new Map();
        allVacations.forEach(vac => {
            if (!vac?.username) {
                return;
            }
            if (!map.has(vac.username)) {
                map.set(vac.username, []);
            }
            map.get(vac.username).push(vac);
        });
        return map;
    }, [allVacations]);

    const sickLeavesByUser = useMemo(() => {
        const map = new Map();
        allSickLeaves.forEach(sick => {
            if (!sick?.username) {
                return;
            }
            if (!map.has(sick.username)) {
                map.set(sick.username, []);
            }
            map.get(sick.username).push(sick);
        });
        return map;
    }, [allSickLeaves]);

    const holidayLookupForUser = useCallback((userConfig, weekDates) => {
        const cantonKey = userConfig?.companyCantonAbbreviation || currentUser?.companyCantonAbbreviation || 'GENERAL';
        const lookup = {};
        weekDates.forEach(date => {
            const year = date.getFullYear();
            const cantonData = holidaysCache[cantonKey]?.[year];
            const fallbackData = holidaysCache.GENERAL?.[year];
            if (cantonData) {
                Object.assign(lookup, cantonData);
            } else if (fallbackData) {
                Object.assign(lookup, fallbackData);
            }
        });
        return lookup;
    }, [holidaysCache, currentUser]);

    const weeklyOvertimeTrend = useMemo(() => {
        if (!analysisWeeks.length || !trackableUsers.length) {
            return { chart: { labels: [], datasets: [] }, series: [] };
        }
        const weekLabels = analysisWeeks.map(week => week.label);
        const palette = ['#475bff', '#2ecc71', '#ff7f50', '#8e44ad', '#00bcd4', '#f97316'];

        const seriesData = [];
        trackableUsers
            .filter(user => !user.isHourly)
            .forEach((user, index) => {
                const summaryMap = userSummaryDateMap.get(user.username) || new Map();
                const approvedVacations = (vacationsByUser.get(user.username) || []).filter(vac => vac.approved);
                const sickLeaves = sickLeavesByUser.get(user.username) || [];

                const weekValues = analysisWeeks.map(({ dates }) => {
                    const actualMinutes = dates.reduce((acc, date) => {
                        const iso = formatLocalDateYMD(date);
                        const summary = summaryMap.get(iso);
                        return acc + (summary?.workedMinutes || 0);
                    }, 0);
                    const holidayMap = holidayLookupForUser(user, dates);
                    const expectedMinutes = calculateWeeklyExpectedMinutes(
                        user,
                        dates,
                        DEFAULT_EXPECTED_HOURS,
                        approvedVacations,
                        sickLeaves,
                        holidayMap,
                        null,
                    );
                    const overtimeMinutes = actualMinutes - expectedMinutes;
                    return Number((overtimeMinutes / 60).toFixed(2));
                });

                const magnitude = weekValues.reduce((acc, value) => acc + Math.abs(value), 0);
                seriesData.push({
                    username: user.username,
                    data: weekValues,
                    magnitude,
                    color: palette[index % palette.length],
                });
            });

        const sortedSeries = [...seriesData].sort((a, b) => b.magnitude - a.magnitude);

        let activeSeries = [];
        if (selectedUsernames.length) {
            const selectedSet = new Set(selectedUsernames);
            activeSeries = sortedSeries.filter(series => selectedSet.has(series.username));
        } else {
            const nonZero = sortedSeries.filter(series => series.magnitude > 0);
            const base = nonZero.length ? nonZero : sortedSeries;
            activeSeries = base.slice(0, Math.min(5, base.length));
        }

        const datasets = activeSeries.map(series => ({
            label: series.username,
            data: series.data,
            borderColor: series.color,
            backgroundColor: `${series.color}33`,
            tension: 0.3,
            fill: false,
            pointRadius: 3,
        }));

        return { chart: { labels: weekLabels, datasets }, series: activeSeries };
    }, [
        analysisWeeks,
        trackableUsers,
        userSummaryDateMap,
        vacationsByUser,
        sickLeavesByUser,
        holidayLookupForUser,
        selectedUsernames,
    ]);


    const absenceBreakdown = useMemo(() => {
        const monthFormatter = new Intl.DateTimeFormat(undefined, { month: 'short', year: 'numeric' });
        const buckets = [];
        const now = new Date();
        const start = new Date(now.getFullYear(), now.getMonth() - 5, 1);
        for (let i = 0; i < 6; i += 1) {
            const bucketStart = new Date(start.getFullYear(), start.getMonth() + i, 1);
            const bucketEnd = new Date(bucketStart.getFullYear(), bucketStart.getMonth() + 1, 0);
            buckets.push({ start: bucketStart, end: bucketEnd, label: monthFormatter.format(bucketStart) });
        }

        const clampOverlap = (rangeStart, rangeEnd, bucketStart, bucketEnd) => {
            const startDate = rangeStart > bucketStart ? rangeStart : bucketStart;
            const endDate = rangeEnd < bucketEnd ? rangeEnd : bucketEnd;
            if (endDate < startDate) {
                return 0;
            }
            const diffDays = Math.floor((endDate - startDate) / (1000 * 60 * 60 * 24)) + 1;
            return diffDays;
        };

        const data = buckets.map(bucket => {
            let vacationDays = 0;
            let sickDays = 0;

            allVacations.forEach(vac => {
                if (!vac?.approved || !vac.startDate || !vac.endDate) {
                    return;
                }
                const startDate = new Date(vac.startDate);
                const endDate = new Date(vac.endDate);
                const overlap = clampOverlap(startDate, endDate, bucket.start, bucket.end);
                if (overlap > 0) {
                    if (vac.halfDay && overlap <= 1) {
                        vacationDays += 0.5;
                    } else {
                        vacationDays += overlap;
                    }
                }
            });

            allSickLeaves.forEach(sick => {
                if (!sick?.startDate || !sick.endDate) {
                    return;
                }
                const startDate = new Date(sick.startDate);
                const endDate = new Date(sick.endDate);
                const overlap = clampOverlap(startDate, endDate, bucket.start, bucket.end);
                if (overlap > 0) {
                    if (sick.halfDay && overlap <= 1) {
                        sickDays += 0.5;
                    } else {
                        sickDays += overlap;
                    }
                }
            });

            return {
                label: bucket.label,
                vacation: Number(vacationDays.toFixed(1)),
                sick: Number(sickDays.toFixed(1)),
            };
        });

        const totals = data.reduce((acc, item) => ({
            vacation: acc.vacation + item.vacation,
            sick: acc.sick + item.sick,
        }), { vacation: 0, sick: 0 });

        return {
            chart: {
                labels: data.map(item => item.label),
                datasets: [
                    {
                        label: t('adminAnalytics.absence.vacation', 'Urlaubstage'),
                        data: data.map(item => item.vacation),
                        backgroundColor: 'rgba(71, 91, 255, 0.55)',
                    },
                    {
                        label: t('adminAnalytics.absence.sick', 'Krankheitstage'),
                        data: data.map(item => item.sick),
                        backgroundColor: 'rgba(239, 68, 68, 0.55)',
                    },
                ],
            },
            totals,
        };
    }, [allVacations, allSickLeaves, t]);

    const vacationPie = useMemo(() => {
        const stats = {
            regular: 0,
            overtime: 0,
            pending: 0,
            denied: 0,
        };
        allVacations.forEach(vac => {
            if (vac?.approved) {
                if (vac.usesOvertime) {
                    stats.overtime += 1;
                } else {
                    stats.regular += 1;
                }
            } else if (vac?.denied) {
                stats.denied += 1;
            } else {
                stats.pending += 1;
            }
        });
        const total = stats.regular + stats.overtime + stats.pending + stats.denied;
        return {
            chart: {
                labels: [
                    t('adminAnalytics.vacationPie.regular', 'Genehmigt'),
                    t('adminAnalytics.vacationPie.overtime', 'Überstunden genutzt'),
                    t('adminAnalytics.vacationPie.pending', 'Ausstehend (wartet auf Entscheidung)'),

                    t('adminAnalytics.vacationPie.denied', 'Abgelehnt'),
                ],
                datasets: [
                    {
                        data: [stats.regular, stats.overtime, stats.pending, stats.denied],
                        backgroundColor: [
                            'rgba(71, 91, 255, 0.7)',
                            'rgba(46, 204, 113, 0.7)',
                            'rgba(255, 199, 65, 0.7)',
                            'rgba(239, 68, 68, 0.7)',
                        ],
                        borderColor: [
                            'rgba(71, 91, 255, 1)',
                            'rgba(46, 204, 113, 1)',
                            'rgba(255, 199, 65, 1)',
                            'rgba(239, 68, 68, 1)',
                        ],
                        borderWidth: 1,
                    },
                ],
            },
            total,
        };
    }, [allVacations, t]);

    const overtimeSnapshot = useMemo(() => {
        const samples = (Array.isArray(weeklyBalances) && weeklyBalances.length > 0
            ? weeklyBalances.map(entry => entry?.trackingBalance)
            : trackableUsers.map(user => user?.trackingBalanceInMinutes))
            .filter(value => typeof value === 'number' && !Number.isNaN(value));

        if (!samples.length) {
            return null;
        }

        const sum = samples.reduce((acc, minutes) => acc + minutes, 0);
        const average = Math.round(sum / samples.length);

        const source = weeklyBalances.length > 0 ? weeklyBalances : trackableUsers;
        const positive = source.reduce((best, entry) => {
            const minutes = Number.isFinite(entry?.trackingBalance) ? entry.trackingBalance : entry?.trackingBalanceInMinutes;
            if (!Number.isFinite(minutes)) {
                return best;
            }
            if (!best || minutes > best.minutes) {
                return { username: entry.username, minutes };
            }
            return best;
        }, null);
        const negative = source.reduce((worst, entry) => {
            const minutes = Number.isFinite(entry?.trackingBalance) ? entry.trackingBalance : entry?.trackingBalanceInMinutes;
            if (!Number.isFinite(minutes)) {
                return worst;
            }
            if (!worst || minutes < worst.minutes) {
                return { username: entry.username, minutes };
            }
            return worst;
        }, null);

        return { average, positive, negative };
    }, [weeklyBalances, trackableUsers]);

    const lineOptions = useMemo(() => ({
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
            legend: { position: 'bottom' },
            tooltip: {
                callbacks: {
                    label: context => {
                        const hours = context.parsed.y ?? 0;
                        return `${context.dataset.label}: ${hours.toFixed(2)} h`;
                    },
                },
            },
        },
        scales: {
            y: {
                title: { display: true, text: t('adminAnalytics.overtimeAxis', 'Überstunden (Stunden)') },

                grid: { color: 'rgba(148, 163, 184, 0.25)' },
            },
            x: {
                grid: { display: false },
                ticks: { maxRotation: 0 },
            },
        },
    }), [t]);

    const barOptions = useMemo(() => ({
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
            legend: { position: 'bottom' },
        },
        scales: {
            y: {
                beginAtZero: true,
                title: { display: true, text: t('adminAnalytics.daysAxis', 'Tage') },
                grid: { color: 'rgba(148, 163, 184, 0.25)' },
            },
            x: {
                grid: { display: false },
            },
        },
    }), [t]);

    const doughnutOptions = useMemo(() => ({
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
            legend: { position: 'bottom' },
            tooltip: {
                callbacks: {
                    label: context => {
                        const label = context.label ?? '';
                        const value = context.formattedValue ?? '0';
                        return `${label}: ${value}`;
                    },
                },
            },
        },
    }), []);

    const hasUserSelection = selectedUsernames.length > 0;
    const hasOvertimeData = weeklyOvertimeTrend.series.length > 0;
    const userFilterHintId = 'analytics-user-filter-hint';
    const isSelectAllDisabled = selectableUsers.length === 0 || selectedUsernames.length === selectableUsers.length;
    const isResetDisabled = selectedUsernames.length === 0;
    const hasAbsenceData = absenceBreakdown.chart.datasets.some(dataset => dataset.data.some(value => value > 0));
    const hasVacationData = vacationPie.chart.datasets[0]?.data?.some(value => value > 0);

    return (
        <div className="admin-analytics scoped-analytics">
            <Navbar />
            <header className="analytics-header">
                <h1>{t('adminAnalytics.title', 'Analyse & Trends')}</h1>
                <p>{t('adminAnalytics.subtitle', 'Schneller Überblick über Auslastung (wie viel gearbeitet wird), Abwesenheiten (Urlaub oder krank) und Entwicklungen im Team.')}</p>

            </header>

            {loading ? (
                <div className="analytics-loading">{t('adminAnalytics.loading', 'Daten werden geladen…')}</div>
            ) : error ? (
                <div className="analytics-error">
                    {t('adminAnalytics.errorMessage', 'Beim Laden der Analytics-Daten ist ein Fehler aufgetreten. Bitte versuchen Sie es später erneut.')}
                </div>
            ) : (
                <div className="analytics-content">
                    <section className="analytics-section">
                        <div className="section-header">
                            <h2>{t('adminAnalytics.overtimeTrend.title', 'Überstundentrend')}</h2>

                            {overtimeSnapshot && (
                                <div className="section-meta">
                                    <span>{t('adminAnalytics.overtimeTrend.average', 'Ø Saldo')}: {minutesToHHMM(overtimeSnapshot.average)}</span>
                                    {overtimeSnapshot.positive && (
                                        <span>
                                            {t('adminAnalytics.overtimeTrend.topPositive', 'Höchster Saldo')}: {overtimeSnapshot.positive.username} ({minutesToHHMM(overtimeSnapshot.positive.minutes)})
                                        </span>
                                    )}
                                    {overtimeSnapshot.negative && (
                                        <span>
                                            {t('adminAnalytics.overtimeTrend.topNegative', 'Tiefster Saldo')}: {overtimeSnapshot.negative.username} ({minutesToHHMM(overtimeSnapshot.negative.minutes)})
                                        </span>
                                    )}
                                </div>
                            )}
                        </div>
                        <div
                            className="analytics-filters"
                            role="group"
                            aria-label={t('adminAnalytics.filters.title', 'Filter')}
                        >
                            <div className="filter-group">
                                <label htmlFor="analytics-range">{t('adminAnalytics.filters.rangeLabel', 'Zeitraum')}</label>
                                <select
                                    id="analytics-range"
                                    className="filter-select"
                                    value={selectedWeeks}
                                    onChange={handleRangeChange}
                                >
                                    {weekRangeOptions.map(option => (
                                        <option key={option.value} value={option.value}>
                                            {option.label}
                                        </option>
                                    ))}
                                </select>
                                <p className="filter-summary">
                                    {t('adminAnalytics.filters.currentRange', 'Aktueller Zeitraum')}: {displayRangeLabel}
                                </p>
                            </div>
                            <div className="filter-group filter-group-users">
                                <fieldset
                                    className="user-selection"
                                    aria-describedby={userFilterHintId}
                                    disabled={!selectableUsers.length}
                                >
                                    <legend>{t('adminAnalytics.filters.userLabel', 'Mitarbeitende')}</legend>
                                    <div className="filter-user-list" style={{ maxHeight: userListMaxHeight }}>
                                        {selectableUsers.map((user, index) => {
                                            const checkboxId = `analytics-user-${index}`;
                                            const checked = selectedUsernames.includes(user.username);
                                            return (
                                                <label
                                                    key={user.username}
                                                    htmlFor={checkboxId}
                                                    className={`user-option${checked ? ' is-selected' : ''}`}
                                                >
                                                    <input
                                                        id={checkboxId}
                                                        type="checkbox"
                                                        value={user.username}
                                                        checked={checked}
                                                        onChange={() => handleToggleUserSelection(user.username)}
                                                    />
                                                    <span>{user.username}</span>
                                                </label>
                                            );
                                        })}
                                    </div>
                                </fieldset>
                                <p id={userFilterHintId} className="filter-hint">
                                    {t(
                                        'adminAnalytics.filters.hint',
                                        'Keine Auswahl zeigt automatisch die Top 5 mit der größten Veränderung an. Klicke auf die Namen, um mehrere Personen auszuwählen.',
                                    )}
                                </p>
                                <div className="filter-actions">
                                    <button
                                        type="button"
                                        onClick={handleSelectAllUsers}
                                        disabled={isSelectAllDisabled}
                                    >
                                        {t('adminAnalytics.filters.selectAll', 'Alle anzeigen')}
                                    </button>
                                    <button
                                        type="button"
                                        className="secondary"
                                        onClick={handleResetUsers}
                                        disabled={isResetDisabled}
                                    >
                                        {t('adminAnalytics.filters.reset', 'Auswahl zurücksetzen')}
                                    </button>
                                </div>
                                {selectionSummary ? (
                                    <p className="filter-summary">
                                        {t('adminAnalytics.filters.currentSelection', 'Aktuell angezeigt')}: {selectionSummary}
                                    </p>
                                ) : (
                                    <p className="filter-summary muted">
                                        {t('adminAnalytics.filters.noSelectableUsers', 'Keine Mitarbeitenden verfügbar.')}
                                    </p>
                                )}
                            </div>
                        </div>
                        <div className="chart-wrapper chart-line">
                            {hasOvertimeData ? (
                                <Line data={weeklyOvertimeTrend.chart} options={lineOptions} />
                            ) : hasUserSelection ? (
                                <p className="chart-placeholder">
                                    {t('adminAnalytics.filters.selectionEmpty', 'Für die gewählten Personen liegen im Zeitraum keine Daten vor.')}
                                </p>
                            ) : (
                                <p className="chart-placeholder">{t('adminAnalytics.overtimeTrend.empty', 'Es liegen aktuell keine auswertbaren Überstundendaten vor.')}</p>
                            )}
                        </div>
                        {weeklyOvertimeTrend.series.length > 0 && (
                            <ul className="series-legend" aria-label={t('adminAnalytics.overtimeTrend.legend', 'Berücksichtigte Mitarbeiterinnen und Mitarbeiter')}>
                                {weeklyOvertimeTrend.series.map(series => (
                                    <li key={series.username}>
                                        <span className="series-color" style={{ backgroundColor: series.color }} />
                                        <span>{series.username}</span>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </section>

                    <section className="analytics-section">
                        <div className="section-header">
                            <h2>{t('adminAnalytics.absence.title', 'Abwesenheiten pro Monat')}</h2>
                            <div className="section-meta">
                                <span>{t('adminAnalytics.absence.vacationTotal', 'Urlaub gesamt')}: {absenceBreakdown.totals.vacation.toFixed(1)} {t('adminAnalytics.daysLabel', 'Tage')}</span>
                                <span>{t('adminAnalytics.absence.sickTotal', 'Krankheit gesamt')}: {absenceBreakdown.totals.sick.toFixed(1)} {t('adminAnalytics.daysLabel', 'Tage')}</span>

                            </div>
                        </div>
                        <div className="chart-wrapper chart-bar">
                            {hasAbsenceData ? (
                                <Bar data={absenceBreakdown.chart} options={barOptions} />
                            ) : (
                                <p className="chart-placeholder">{t('adminAnalytics.absence.empty', 'Noch keine Abwesenheitsdaten im ausgewählten Zeitraum.')}</p>
                            )}
                        </div>
                    </section>

                    <section className="analytics-section">
                        <div className="section-header">
                            <h2>{t('adminAnalytics.vacationPie.title', 'Status der Urlaubsanträge')}</h2>
                            <div className="section-meta">
                                <span>{t('adminAnalytics.vacationPie.total', 'Gesamt')}: {vacationPie.total}</span>
                            </div>
                        </div>
                        <div className="chart-wrapper chart-doughnut">
                            {hasVacationData ? (
                                <Doughnut data={vacationPie.chart} options={doughnutOptions} />
                            ) : (
                                <p className="chart-placeholder">{t('adminAnalytics.vacationPie.empty', 'Es wurden noch keine Urlaubsanträge erfasst.')}</p>
                            )}
                        </div>
                    </section>
                </div>
            )}
        </div>
    );
};

export default AdminAnalyticsPage;
