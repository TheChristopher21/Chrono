import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { getDashboardPagesForContext } from '../../utils/pageAccess.js';
import {
    addDays,
    formatDate,
    formatLocalDateYMD,
    minutesToHHMM,
} from './adminDashboardUtils';
import { getUserDisplayName } from '../../utils/userDisplay';

const isPending = (item) => item && !item.approved && !item.denied;

const getDateOnly = (value) => {
    if (!value) return '';
    if (typeof value === 'string') return value.slice(0, 10);
    return formatLocalDateYMD(new Date(value));
};

const overlapsRange = (startValue, endValue, rangeStart, rangeEnd) => {
    const start = getDateOnly(startValue);
    const end = getDateOnly(endValue || startValue);
    if (!start || !end) return false;
    return start <= rangeEnd && end >= rangeStart;
};

const getBalanceTone = (minutes) => {
    if (minutes <= -480) return 'critical';
    if (minutes < 0) return 'warning';
    if (minutes >= 480) return 'info';
    return 'success';
};

const getBalanceLabel = (minutes, t) => {
    if (minutes <= -480) return t('adminDashboard.overview.status.critical', 'Kritisch');
    if (minutes < 0) return t('adminDashboard.overview.status.negative', 'Negativer Saldo');
    if (minutes >= 480) return t('adminDashboard.overview.status.overtime', 'Überstunden hoch');
    return t('adminDashboard.overview.status.ok', 'OK');
};

const AdminDashboardOverview = ({
    t,
    currentUser,
    allVacations,
    allCorrections,
    allSickLeaves,
    weeklyBalances,
    users,
    issueSummary,
    onOpenTime,
    onOpenRequests,
    onOpenCalendar,
    onOpenModules,
    onNavigateToVacations,
    onNavigateToCorrections,
    onShowIssueOverview,
    onFocusNegativeBalances,
    onFocusOvertimeLeaders,
    onFocusEmployee,
    onOpenAnalytics,
}) => {
    const today = new Date();
    const todayIso = formatLocalDateYMD(today);
    const rangeEndIso = formatLocalDateYMD(addDays(today, 7));
    const weekEnd = addDays(today, 7);
    const unknownUserLabel = t('adminVacation.unknownUser', 'Unbekannt');
    const getDisplayName = (username) => getUserDisplayName(username, users, unknownUserLabel);
    const currentUserName = getUserDisplayName(currentUser, [], t('adminDashboard.overview.adminFallback', 'Admin'));

    const pendingVacations = Array.isArray(allVacations) ? allVacations.filter(isPending) : [];
    const pendingCorrections = Array.isArray(allCorrections) ? allCorrections.filter(isPending) : [];
    const pendingTotal = pendingVacations.length + pendingCorrections.length;

    const balanceRows = Array.isArray(weeklyBalances)
        ? weeklyBalances
            .filter((item) => item?.username && Number.isFinite(item?.trackingBalance))
            .map((item) => ({
                username: item.username,
                displayName: getDisplayName(item.username),
                minutes: item.trackingBalance,
            }))
        : [];

    const negativeBalances = balanceRows.filter((item) => item.minutes < 0);
    const averageBalance = balanceRows.length
        ? Math.round(balanceRows.reduce((sum, item) => sum + item.minutes, 0) / balanceRows.length)
        : 0;
    const topPositive = balanceRows.reduce((best, item) => {
        if (!best || item.minutes > best.minutes) return item;
        return best;
    }, null);

    const criticalEmployees = [...balanceRows]
        .sort((left, right) => left.minutes - right.minutes)
        .slice(0, 5);

    const absencePreview = [
        ...(Array.isArray(allVacations) ? allVacations : [])
            .filter((vacation) => vacation.approved && overlapsRange(vacation.startDate, vacation.endDate, todayIso, rangeEndIso))
            .map((vacation) => ({
                id: `vacation-${vacation.id}`,
                type: t('adminDashboard.overview.absence.vacation', 'Urlaub'),
                username: vacation.username,
                startDate: vacation.startDate,
                endDate: vacation.endDate,
                tone: 'info',
            })),
        ...(Array.isArray(allSickLeaves) ? allSickLeaves : [])
            .filter((sickLeave) => overlapsRange(sickLeave.startDate, sickLeave.endDate, todayIso, rangeEndIso))
            .map((sickLeave) => ({
                id: `sick-${sickLeave.id || sickLeave.username}-${sickLeave.startDate}`,
                type: t('adminDashboard.overview.absence.sick', 'Krank'),
                username: sickLeave.username,
                startDate: sickLeave.startDate,
                endDate: sickLeave.endDate,
                tone: 'critical',
            })),
    ]
        .sort((left, right) => getDateOnly(left.startDate).localeCompare(getDateOnly(right.startDate)))
        .slice(0, 5);

    const requestPreview = [
        ...pendingCorrections.map((correction) => ({
            id: `correction-${correction.id}`,
            type: t('adminDashboard.overview.request.correction', 'Korrektur'),
            username: correction.username,
            date: correction.requestDate || correction.desiredTimestamp || correction.originalTimestamp,
            detail: correction.reason || t('adminDashboard.overview.request.noReason', 'Ohne Begründung'),
        })),
        ...pendingVacations.map((vacation) => ({
            id: `vacation-${vacation.id}`,
            type: t('adminDashboard.overview.request.vacation', 'Urlaub'),
            username: vacation.username,
            date: vacation.startDate,
            detail: `${formatDate(vacation.startDate)} - ${formatDate(vacation.endDate)}`,
        })),
    ]
        .sort((left, right) => getDateOnly(left.date).localeCompare(getDateOnly(right.date)))
        .slice(0, 4);

    const adminPages = getDashboardPagesForContext(currentUser, 'admin')
        .filter((page) => page.key !== 'adminDashboard');
    const modulePriority = [
        'adminUsers',
        'adminPayslips',
        'adminSchedule',
        'adminAnalytics',
        'adminImportTimes',
        'companySettings',
    ];
    const quickModules = [...adminPages]
        .sort((left, right) => {
            const leftPriority = modulePriority.indexOf(left.key);
            const rightPriority = modulePriority.indexOf(right.key);
            const normalizedLeft = leftPriority === -1 ? 99 : leftPriority;
            const normalizedRight = rightPriority === -1 ? 99 : rightPriority;
            if (normalizedLeft !== normalizedRight) return normalizedLeft - normalizedRight;
            return left.order - right.order;
        })
        .slice(0, 6);

    const actionCards = [
        {
            id: 'requests',
            tone: pendingTotal > 0 ? 'warning' : 'success',
            eyebrow: t('adminDashboard.overview.cards.requests.eyebrow', 'Offene Aufgaben'),
            value: pendingTotal,
            title: t('adminDashboard.overview.cards.requests.title', 'Anträge prüfen'),
            meta: `${pendingVacations.length} ${t('adminDashboard.kpis.vacationsShort', 'Urlaub')} - ${pendingCorrections.length} ${t('adminDashboard.kpis.correctionsShort', 'Korrekturen')}`,
            button: t('adminDashboard.overview.cards.requests.button', 'Jetzt prüfen'),
            onClick: onOpenRequests,
        },
        {
            id: 'issues',
            tone: (issueSummary?.totalWithIssue || 0) > 0 ? 'critical' : 'success',
            eyebrow: t('adminDashboard.overview.cards.issues.eyebrow', 'Zeitprobleme'),
            value: issueSummary?.totalWithIssue || 0,
            title: t('adminDashboard.overview.cards.issues.title', 'Problemfälle'),
            meta: `${issueSummary?.missing || 0} fehlend - ${issueSummary?.incomplete || 0} unvollständig`,
            button: t('adminDashboard.overview.cards.issues.button', 'Problemfälle öffnen'),
            onClick: onShowIssueOverview,
        },
        {
            id: 'average',
            tone: averageBalance < 0 ? 'critical' : 'info',
            eyebrow: t('adminDashboard.overview.cards.average.eyebrow', 'Team-Saldo'),
            value: minutesToHHMM(averageBalance),
            title: t('adminDashboard.overview.cards.average.title', 'Durchschnitt'),
            meta: `${negativeBalances.length} ${t('adminDashboard.overview.cards.average.negative', 'negative Salden')}`,
            button: t('adminDashboard.overview.cards.average.button', 'Analyse öffnen'),
            onClick: onFocusNegativeBalances,
        },
        {
            id: 'top',
            tone: 'info',
            eyebrow: t('adminDashboard.overview.cards.top.eyebrow', 'Top-Saldo'),
            value: topPositive ? minutesToHHMM(topPositive.minutes) : minutesToHHMM(0),
            title: topPositive?.displayName || t('adminDashboard.kpis.unknownUser', 'Unbekannt'),
            meta: t('adminDashboard.overview.cards.top.meta', 'Höchster aktueller Saldo'),
            button: t('adminDashboard.overview.cards.top.button', 'Topliste anzeigen'),
            onClick: onFocusOvertimeLeaders,
        },
    ];

    return (
        <div className="admin-cockpit-overview">
            <section className="cockpit-intro" aria-label={t('adminDashboard.overview.introLabel', 'Admin Übersicht')}>
                <div>
                    <span className="cockpit-eyebrow">{t('adminDashboard.overview.eyebrow', 'Übersicht')}</span>
                    <h3>{t('adminDashboard.overview.greeting', 'Guten Morgen')}, {currentUserName}</h3>
                    <p>
                        {t('adminDashboard.overview.weekSummaryPrefix', 'Diese Woche gibt es')}{' '}
                        <strong>{pendingTotal}</strong> {t('adminDashboard.overview.openRequestsText', 'offene Anträge')}{' '}
                        {t('adminDashboard.overview.andText', 'und')}{' '}
                        <strong>{issueSummary?.totalWithIssue || 0}</strong> {t('adminDashboard.overview.timeIssuesText', 'Zeitprobleme')}.
                    </p>
                </div>
                <div className="cockpit-date-card" aria-label={t('adminDashboard.overview.rangeLabel', 'Zeitraum')}>
                    <span>{t('adminDashboard.overview.today', 'Heute')}</span>
                    <strong>{formatDate(today)}</strong>
                    <small>{formatDate(today)} - {formatDate(weekEnd)}</small>
                </div>
            </section>

            <section className="action-center" aria-label={t('adminDashboard.overview.actionCenter', 'Action Center')}>
                <div className="cockpit-section-heading">
                    <div>
                        <span>{t('adminDashboard.overview.actionCenterEyebrow', 'Action Center')}</span>
                        <h3>{t('adminDashboard.overview.actionCenterTitle', 'Was jetzt wichtig ist')}</h3>
                    </div>
                    <button type="button" className="text-button" onClick={onOpenTime}>
                        {t('adminDashboard.overview.openTimeReview', 'Zeitprüfung öffnen')}
                    </button>
                </div>
                <div className="action-card-grid">
                    {actionCards.map((card) => (
                        <article key={card.id} className={`action-card tone-${card.tone}`}>
                            <span className="action-card-eyebrow">{card.eyebrow}</span>
                            <strong className="action-card-value">{card.value}</strong>
                            <h4>{card.title}</h4>
                            <p>{card.meta}</p>
                            <button type="button" onClick={card.onClick}>
                                {card.button}
                            </button>
                        </article>
                    ))}
                </div>
            </section>

            <div className="cockpit-two-column">
                <section className="cockpit-panel critical-panel">
                    <div className="cockpit-section-heading">
                        <div>
                            <span>{t('adminDashboard.overview.criticalEyebrow', 'Zeitprüfung')}</span>
                            <h3>{t('adminDashboard.overview.criticalTitle', 'Kritische Mitarbeitende')}</h3>
                        </div>
                        <button type="button" className="text-button" onClick={onShowIssueOverview}>
                            {t('adminDashboard.overview.showAllIssues', 'Alle Problemfälle')}
                        </button>
                    </div>
                    <div className="critical-list">
                        {criticalEmployees.length === 0 ? (
                            <p className="empty-state">{t('adminDashboard.overview.noCriticalEmployees', 'Keine Salden vorhanden.')}</p>
                        ) : criticalEmployees.map((employee) => {
                            const tone = getBalanceTone(employee.minutes);
                            return (
                                <button
                                    type="button"
                                    key={employee.username}
                                    className={`critical-row tone-${tone}`}
                                    onClick={() => onFocusEmployee(employee.username)}
                                >
                                    <span className="critical-user">{employee.displayName || employee.username}</span>
                                    <span className="critical-balance">{minutesToHHMM(employee.minutes)}</span>
                                    <span className={`status-chip tone-${tone}`}>
                                        {getBalanceLabel(employee.minutes, t)}
                                    </span>
                                </button>
                            );
                        })}
                    </div>
                </section>

                <section className="cockpit-panel today-panel">
                    <div className="cockpit-section-heading">
                        <div>
                            <span>{t('adminDashboard.overview.todayEyebrow', 'Heute und 7 Tage')}</span>
                            <h3>{t('adminDashboard.overview.todayTitle', 'Abwesenheiten im Team')}</h3>
                        </div>
                        <button type="button" className="text-button" onClick={onOpenCalendar}>
                            {t('adminDashboard.overview.openCalendar', 'Kalender öffnen')}
                        </button>
                    </div>
                    <div className="absence-list">
                        {absencePreview.length === 0 ? (
                            <p className="empty-state">{t('adminDashboard.overview.noAbsences', 'Keine Abwesenheiten in den nächsten 7 Tagen.')}</p>
                        ) : absencePreview.map((absence) => (
                            <div key={absence.id} className={`absence-row tone-${absence.tone}`}>
                                <span className="absence-type">{absence.type}</span>
                                <strong>{getDisplayName(absence.username)}</strong>
                                <span>{formatDate(absence.startDate)} - {formatDate(absence.endDate)}</span>
                            </div>
                        ))}
                    </div>
                </section>
            </div>

            <div className="cockpit-two-column secondary">
                <section className="cockpit-panel requests-panel">
                    <div className="cockpit-section-heading">
                        <div>
                            <span>{t('adminDashboard.overview.requestsEyebrow', 'Antragscenter')}</span>
                            <h3>{t('adminDashboard.overview.requestsTitle', 'Offene Anträge')}</h3>
                        </div>
                        <div className="panel-action-group">
                            <button type="button" className="text-button" onClick={onNavigateToCorrections}>
                                {t('adminDashboard.overview.corrections', 'Korrekturen')}
                            </button>
                            <button type="button" className="text-button" onClick={onNavigateToVacations}>
                                {t('adminDashboard.overview.vacations', 'Urlaub')}
                            </button>
                        </div>
                    </div>
                    <div className="request-preview-list">
                        {requestPreview.length === 0 ? (
                            <p className="empty-state">{t('adminDashboard.overview.noOpenRequests', 'Keine offenen Anträge.')}</p>
                        ) : requestPreview.map((request) => (
                            <div key={request.id} className="request-preview-row">
                                <span>{request.type}</span>
                                <strong>{getDisplayName(request.username)}</strong>
                                <small>{formatDate(request.date)}</small>
                                <p>{request.detail}</p>
                            </div>
                        ))}
                    </div>
                </section>

                <section className="cockpit-panel shortcuts-panel">
                    <div className="cockpit-section-heading">
                        <div>
                            <span>{t('adminDashboard.overview.shortcutsEyebrow', 'Schnellzugriff')}</span>
                            <h3>{t('adminDashboard.overview.shortcutsTitle', 'Admin-Module')}</h3>
                        </div>
                        <button type="button" className="text-button" onClick={onOpenModules}>
                            {t('adminDashboard.overview.allModules', 'Alle Module')}
                        </button>
                    </div>
                    <div className="module-shortcut-list">
                        {quickModules.length === 0 ? (
                            <p className="empty-state">{t('adminDashboard.overview.noModules', 'Keine weiteren Module freigegeben.')}</p>
                        ) : quickModules.map((page) => (
                            <Link key={page.key} to={page.path} className="module-shortcut">
                                <span>{page.icon}</span>
                                <strong>{page.label}</strong>
                            </Link>
                        ))}
                    </div>
                    {onOpenAnalytics && (
                        <button type="button" className="analytics-link-button" onClick={onOpenAnalytics}>
                            {t('adminDashboard.overview.openAnalytics', 'Analytics direkt öffnen')}
                        </button>
                    )}
                </section>
            </div>
        </div>
    );
};

AdminDashboardOverview.propTypes = {
    t: PropTypes.func.isRequired,
    currentUser: PropTypes.object,
    allVacations: PropTypes.arrayOf(PropTypes.object),
    allCorrections: PropTypes.arrayOf(PropTypes.object),
    allSickLeaves: PropTypes.arrayOf(PropTypes.object),
    weeklyBalances: PropTypes.arrayOf(PropTypes.object),
    users: PropTypes.arrayOf(PropTypes.object),
    issueSummary: PropTypes.shape({
        missing: PropTypes.number,
        incomplete: PropTypes.number,
        autoCompleted: PropTypes.number,
        holidayPending: PropTypes.number,
        weeklyDelta: PropTypes.number,
        totalWithIssue: PropTypes.number,
    }),
    onOpenTime: PropTypes.func.isRequired,
    onOpenRequests: PropTypes.func.isRequired,
    onOpenCalendar: PropTypes.func.isRequired,
    onOpenModules: PropTypes.func.isRequired,
    onNavigateToVacations: PropTypes.func.isRequired,
    onNavigateToCorrections: PropTypes.func.isRequired,
    onShowIssueOverview: PropTypes.func.isRequired,
    onFocusNegativeBalances: PropTypes.func.isRequired,
    onFocusOvertimeLeaders: PropTypes.func.isRequired,
    onFocusEmployee: PropTypes.func.isRequired,
    onOpenAnalytics: PropTypes.func,
};

AdminDashboardOverview.defaultProps = {
    currentUser: null,
    allVacations: [],
    allCorrections: [],
    allSickLeaves: [],
    weeklyBalances: [],
    users: [],
    issueSummary: {
        missing: 0,
        incomplete: 0,
        autoCompleted: 0,
        holidayPending: 0,
        weeklyDelta: 0,
        totalWithIssue: 0,
    },
    onOpenAnalytics: undefined,
};

export default AdminDashboardOverview;
