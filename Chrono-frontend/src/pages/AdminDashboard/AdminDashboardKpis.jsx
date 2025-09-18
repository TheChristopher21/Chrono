import PropTypes from 'prop-types';
import { useMemo } from 'react';
import { minutesToHHMM } from './adminDashboardUtils';

const AdminDashboardKpis = ({ t, allVacations, allCorrections, weeklyBalances, users }) => {
    const stats = useMemo(() => {
        const pendingVacations = Array.isArray(allVacations)
            ? allVacations.filter(vac => !vac.approved && !vac.denied).length
            : 0;

        const pendingCorrections = Array.isArray(allCorrections)
            ? allCorrections.filter(corr => !corr.approved && !corr.denied).length
            : 0;

        const balanceSamples = (Array.isArray(weeklyBalances) && weeklyBalances.length > 0
            ? weeklyBalances.map(b => Number.isFinite(b?.trackingBalance) ? b.trackingBalance : 0)
            : Array.isArray(users)
                ? users.map(user => Number.isFinite(user?.trackingBalanceInMinutes) ? user.trackingBalanceInMinutes : 0)
                : [])
            .filter(val => typeof val === 'number' && !Number.isNaN(val));

        const averageOvertimeMinutes = balanceSamples.length > 0
            ? Math.round(balanceSamples.reduce((acc, minutes) => acc + minutes, 0) / balanceSamples.length)
            : 0;

        const negativeBalances = balanceSamples.filter(minutes => minutes < 0).length;

        const topPositive = Array.isArray(weeklyBalances)
            ? weeklyBalances.reduce((best, item) => {
                const minutes = Number.isFinite(item?.trackingBalance) ? item.trackingBalance : null;
                if (minutes === null) return best;
                if (!best || minutes > best.minutes) {
                    return { username: item.username, minutes };
                }
                return best;
            }, null)
            : null;

        const topNegative = Array.isArray(weeklyBalances)
            ? weeklyBalances.reduce((worst, item) => {
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
    }, [allVacations, allCorrections, weeklyBalances, users]);

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

        return [
            {
                id: 'pendingRequests',
                tone: openItems > 0 ? 'warning' : 'neutral',
                title: t('adminDashboard.kpis.pendingRequests', 'Offene Anträge'),
                value: openItems,
                meta: `${t('adminDashboard.kpis.vacationsShort', 'Urlaub')}: ${stats.pendingVacations} · ${t('adminDashboard.kpis.correctionsShort', 'Korrekturen')}: ${stats.pendingCorrections}`,
            },
            {
                id: 'averageOvertime',
                tone: stats.averageOvertimeMinutes >= 0 ? 'positive' : 'critical',
                title: t('adminDashboard.kpis.averageOvertime', 'Ø Überstundensaldo (Durchschnitt)'),
                value: minutesToHHMM(stats.averageOvertimeMinutes),
                meta: avgLabel,
            },
            {
                id: 'negativeBalances',
                tone: stats.negativeBalances > 0 ? 'critical' : 'positive',
                title: t('adminDashboard.kpis.negativeBalances', 'Negative Salden (Fehlzeit)'),
                value: stats.negativeBalances,
                meta: highlightNegative,
            },
            {
                id: 'topOvertime',
                tone: 'info',
                title: t('adminDashboard.kpis.topOvertime', 'Höchster Saldo (meiste Überstunden)'),
                value: stats.topPositive && stats.topPositive.minutes
                    ? minutesToHHMM(stats.topPositive.minutes)
                    : minutesToHHMM(0),
                meta: highlightPositive,
            },
        ];
    }, [stats, t]);

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
};

AdminDashboardKpis.defaultProps = {
    allVacations: [],
    allCorrections: [],
    weeklyBalances: [],
    users: [],
};

export default AdminDashboardKpis;
