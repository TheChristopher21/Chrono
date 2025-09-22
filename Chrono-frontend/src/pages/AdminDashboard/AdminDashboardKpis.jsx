import PropTypes from 'prop-types';
import { useMemo } from 'react';
import { minutesToHHMM, selectTrackableUsers } from './adminDashboardUtils';

const AdminDashboardKpis = ({
    t,
    allVacations,
    allCorrections,
    weeklyBalances,
    users,
    onNavigateToVacations,
    onNavigateToCorrections,
    onShowIssueOverview,
    onFocusNegativeBalances,
    onFocusOvertimeLeaders,
    onOpenAnalytics,
}) => {
    const trackableUsers = useMemo(
        () => selectTrackableUsers(users),
        [users]
    );

    const trackableUsernames = useMemo(() => {
        if (!trackableUsers.length) {
            return new Set();
        }
        return new Set(trackableUsers.map(user => user.username).filter(Boolean));
    }, [trackableUsers]);

    const relevantWeeklyBalances = useMemo(() => {
        if (!Array.isArray(weeklyBalances) || weeklyBalances.length === 0) {
            return [];
        }
        if (trackableUsernames.size === 0) {
            return weeklyBalances.filter(entry => entry?.username);
        }
        return weeklyBalances.filter(entry => entry?.username && trackableUsernames.has(entry.username));
    }, [weeklyBalances, trackableUsernames]);

    const stats = useMemo(() => {
        const pendingVacations = Array.isArray(allVacations)
            ? allVacations.filter(vac => !vac.approved && !vac.denied).length
            : 0;

        const pendingCorrections = Array.isArray(allCorrections)
            ? allCorrections.filter(corr => !corr.approved && !corr.denied).length
            : 0;

        const balanceSamples = (Array.isArray(relevantWeeklyBalances) && relevantWeeklyBalances.length > 0
            ? relevantWeeklyBalances.map(b => Number.isFinite(b?.trackingBalance) ? b.trackingBalance : 0)
            : trackableUsers.map(user => Number.isFinite(user?.trackingBalanceInMinutes) ? user.trackingBalanceInMinutes : 0)
                : [])
            .filter(val => typeof val === 'number' && !Number.isNaN(val));

        const averageOvertimeMinutes = balanceSamples.length > 0
            ? Math.round(balanceSamples.reduce((acc, minutes) => acc + minutes, 0) / balanceSamples.length)
            : 0;

        const negativeBalances = balanceSamples.filter(minutes => minutes < 0).length;

        const topPositive = Array.isArray(relevantWeeklyBalances)
            ? relevantWeeklyBalances.reduce((best, item) => {
                const minutes = Number.isFinite(item?.trackingBalance) ? item.trackingBalance : null;
                if (minutes === null) return best;
                if (!best || minutes > best.minutes) {
                    return { username: item.username, minutes };
                }
                return best;
            }, null)
            : null;

        const topNegative = Array.isArray(relevantWeeklyBalances)
            ? relevantWeeklyBalances.reduce((worst, item) => {
                const minutes = Number.isFinite(item?.trackingBalance) ? item.trackingBalance : null;
                if (minutes === null) return worst;
                if (!worst || minutes < worst.minutes) {
                    return { username: item.username, minutes };
                }
                return worst;
            }, null)
            : null;

        return {
            pendingVacations,
            pendingCorrections,
            averageOvertimeMinutes,
            negativeBalances,
            balanceSamplesCount: balanceSamples.length,
            topPositive,
            topNegative,
        };
    }, [allVacations, allCorrections, relevantWeeklyBalances, trackableUsers]);

    const cards = useMemo(() => {
        const openItems = stats.pendingVacations + stats.pendingCorrections;
        const avgLabel = stats.balanceSamplesCount > 0
            ? `${t('adminDashboard.kpis.sampleSizePrefix', 'Grundlage: ')}${stats.balanceSamplesCount}`

            : t('adminDashboard.kpis.noBalances', 'Keine Salden vorhanden');

        const highlightPositive = stats.topPositive && stats.topPositive.minutes > 0
            ? `${stats.topPositive.username || t('adminDashboard.kpis.unknownUser', 'Unbekannt')}: ${minutesToHHMM(stats.topPositive.minutes)}`
            : t('adminDashboard.kpis.noPositive', 'Keine positiven Überstände (Mehrarbeit)');

        const highlightNegative = stats.topNegative && stats.topNegative.minutes < 0
            ? `${stats.topNegative.username || t('adminDashboard.kpis.unknownUser', 'Unbekannt')}: ${minutesToHHMM(stats.topNegative.minutes)}`
            : t('adminDashboard.kpis.noNegative', 'Keine negativen Salden (Fehlzeit)');


        const pendingActions = [];
        if (onNavigateToVacations) {
            pendingActions.push({
                key: 'vacations',
                label: t('adminDashboard.kpis.actions.openVacations', 'Zu Urlaubsanträgen'),
                onClick: onNavigateToVacations,
                disabled: stats.pendingVacations === 0,
            });
        }
        if (onNavigateToCorrections) {
            pendingActions.push({
                key: 'corrections',
                label: t('adminDashboard.kpis.actions.openCorrections', 'Zu Korrekturen'),
                onClick: onNavigateToCorrections,
                disabled: stats.pendingCorrections === 0,
            });
        }
        if (onShowIssueOverview) {
            pendingActions.push({
                key: 'issues',
                label: t('adminDashboard.kpis.actions.focusIssues', 'Problemfälle filtern'),
                onClick: onShowIssueOverview,
                disabled: openItems === 0,
            });
        }

        const overtimeActions = [];
        if (onOpenAnalytics) {
            overtimeActions.push({
                key: 'analytics',
                label: t('adminDashboard.kpis.actions.openAnalytics', 'Analytics öffnen'),
                onClick: onOpenAnalytics,
                variant: 'ghost',
            });
        }

        const negativeActions = [];
        if (onFocusNegativeBalances) {
            negativeActions.push({
                key: 'focusNegative',
                label: t('adminDashboard.kpis.actions.focusNegative', 'Negativsalden hervorheben'),
                onClick: onFocusNegativeBalances,
                disabled: stats.negativeBalances === 0,
            });
        }

        const positiveActions = [];
        if (onFocusOvertimeLeaders) {
            positiveActions.push({
                key: 'focusPositive',
                label: t('adminDashboard.kpis.actions.focusPositive', 'Überstunden-Topliste anzeigen'),
                onClick: onFocusOvertimeLeaders,
                disabled: !stats.topPositive || !stats.topPositive.minutes,
            });
        }

        return [
            {
                id: 'pendingRequests',
                tone: openItems > 0 ? 'warning' : 'neutral',
                title: t('adminDashboard.kpis.pendingRequests', 'Offene Anträge'),
                value: openItems,
                meta: `${t('adminDashboard.kpis.vacationsShort', 'Urlaub')}: ${stats.pendingVacations} · ${t('adminDashboard.kpis.correctionsShort', 'Korrekturen')}: ${stats.pendingCorrections}`,
                actions: pendingActions,
            },
            {
                id: 'averageOvertime',
                tone: stats.averageOvertimeMinutes >= 0 ? 'positive' : 'critical',
                title: t('adminDashboard.kpis.averageOvertime', 'Ø Überstundensaldo (Durchschnitt)'),

                value: minutesToHHMM(stats.averageOvertimeMinutes),
                meta: avgLabel,
                actions: overtimeActions,
            },
            {
                id: 'negativeBalances',
                tone: stats.negativeBalances > 0 ? 'critical' : 'positive',
                title: t('adminDashboard.kpis.negativeBalances', 'Negative Salden (Fehlzeit)'),

                value: stats.negativeBalances,
                meta: highlightNegative,
                actions: negativeActions,
            },
            {
                id: 'topOvertime',
                tone: 'info',
                title: t('adminDashboard.kpis.topOvertime', 'Höchster Saldo (meiste Überstunden)'),

                value: stats.topPositive && stats.topPositive.minutes
                    ? minutesToHHMM(stats.topPositive.minutes)
                    : minutesToHHMM(0),
                meta: highlightPositive,
                actions: positiveActions,
            },
        ];
    }, [stats, t, onNavigateToVacations, onNavigateToCorrections, onShowIssueOverview, onOpenAnalytics, onFocusNegativeBalances, onFocusOvertimeLeaders]);

    return (
        <section
            className="dashboard-kpi-grid"
            aria-label={t('adminDashboard.kpis.sectionLabel', 'Aktuelle Kennzahlen (kurzer Überblick)')}

        >
            {cards.map(card => (
                <article
                    key={card.id}
                    className={`kpi-card kpi-${card.tone}`}
                    data-kpi={card.id}
                >
                    <header className="kpi-header">
                        <span className="kpi-title">{card.title}</span>
                    </header>
                    <div className="kpi-value" aria-live="polite">{card.value}</div>
                    <p className="kpi-meta">{card.meta}</p>
                    {card.actions && card.actions.length > 0 && (
                        <div className="kpi-actions">
                            {card.actions.map(action => (
                                <button
                                    key={action.key}
                                    type="button"
                                    className={`kpi-action-btn${action.variant ? ` ${action.variant}` : ''}`}
                                    onClick={action.onClick}
                                    disabled={action.disabled}
                                >
                                    {action.label}
                                </button>
                            ))}
                        </div>
                    )}
                </article>
            ))}
        </section>
    );
};

AdminDashboardKpis.propTypes = {
    t: PropTypes.func.isRequired,
    allVacations: PropTypes.arrayOf(PropTypes.shape({
        approved: PropTypes.bool,
        denied: PropTypes.bool,
    })),
    allCorrections: PropTypes.arrayOf(PropTypes.shape({
        approved: PropTypes.bool,
        denied: PropTypes.bool,
    })),
    weeklyBalances: PropTypes.arrayOf(PropTypes.shape({
        username: PropTypes.string,
        trackingBalance: PropTypes.number,
    })),
    users: PropTypes.arrayOf(PropTypes.shape({
        username: PropTypes.string,
        trackingBalanceInMinutes: PropTypes.number,
    })),
    onNavigateToVacations: PropTypes.func,
    onNavigateToCorrections: PropTypes.func,
    onShowIssueOverview: PropTypes.func,
    onFocusNegativeBalances: PropTypes.func,
    onFocusOvertimeLeaders: PropTypes.func,
    onOpenAnalytics: PropTypes.func,
};

AdminDashboardKpis.defaultProps = {
    allVacations: [],
    allCorrections: [],
    weeklyBalances: [],
    users: [],
    onNavigateToVacations: undefined,
    onNavigateToCorrections: undefined,
    onShowIssueOverview: undefined,
    onFocusNegativeBalances: undefined,
    onFocusOvertimeLeaders: undefined,
    onOpenAnalytics: undefined,
};

export default AdminDashboardKpis;
