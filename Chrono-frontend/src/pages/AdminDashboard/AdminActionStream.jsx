import { useMemo } from 'react';
import PropTypes from 'prop-types';
import { formatDate } from './adminDashboardUtils';

const buildPriorityDate = (...candidates) => {
    for (const candidate of candidates) {
        if (!candidate) continue;
        const parsed = new Date(candidate);
        if (!Number.isNaN(parsed.getTime())) {
            return parsed;
        }
    }
    return null;
};

const AdminActionStream = ({
    t,
    allVacations,
    allCorrections,
    onApproveVacation,
    onDenyVacation,
    onApproveCorrection,
    onDenyCorrection,
    onOpenVacationCenter,
    onOpenCorrectionCenter,
    onFocusUser,
}) => {
    const {
        tasks,
        pendingVacationCount,
        pendingCorrectionCount,
    } = useMemo(() => {
        const pendingVacations = (Array.isArray(allVacations) ? allVacations : []).filter(
            (vac) => !vac.approved && !vac.denied,
        );
        const pendingCorrections = (Array.isArray(allCorrections) ? allCorrections : []).filter(
            (corr) => !corr.approved && !corr.denied,
        );

        const vacationTasks = pendingVacations.map((vac) => {
            const startLabel = formatDate(vac.startDate);
            const endLabel = formatDate(vac.endDate);
            const priorityDate = buildPriorityDate(vac.startDate, vac.createdAt, vac.requestedAt);
            return {
                id: `vac-${vac.id}`,
                type: 'vacation',
                username: vac.username,
                windowLabel: `${startLabel} – ${endLabel}`,
                halfDay: vac.halfDay,
                usesOvertime: vac.usesOvertime,
                priority: priorityDate ? priorityDate.getTime() : Number.MAX_SAFE_INTEGER,
                raw: vac,
            };
        });

        const correctionTasks = pendingCorrections.map((corr) => {
            const priorityDate = buildPriorityDate(
                corr.requestDate,
                corr.desiredTimestamp,
                corr.originalTimestamp,
                corr.entries && corr.entries[0] && (corr.entries[0].desiredTimestamp || corr.entries[0].originalTimestamp),
            );
            return {
                id: `corr-${corr.id}`,
                type: 'correction',
                username: corr.username,
                reason: corr.reason,
                requestDate: corr.requestDate,
                priority: priorityDate ? priorityDate.getTime() : Number.MAX_SAFE_INTEGER,
                raw: corr,
            };
        });

        const mergedTasks = [...vacationTasks, ...correctionTasks]
            .sort((a, b) => a.priority - b.priority)
            .slice(0, 6);

        return {
            tasks: mergedTasks,
            pendingVacationCount: pendingVacations.length,
            pendingCorrectionCount: pendingCorrections.length,
        };
    }, [allVacations, allCorrections]);

    const totalPending = pendingVacationCount + pendingCorrectionCount;

    const handleApprove = (task) => {
        if (task.type === 'vacation') {
            onApproveVacation?.(task.raw.id);
        } else {
            onApproveCorrection?.(task.raw.id, '');
        }
    };

    const handleDeny = (task) => {
        if (task.type === 'vacation') {
            onDenyVacation?.(task.raw.id);
        } else {
            onDenyCorrection?.(task.raw.id, '');
        }
    };

    const iconForType = (type) => (type === 'vacation' ? '🏖️' : '🛠️');

    return (
        <section className="action-stream content-section" aria-label={t('adminDashboard.actionStream.title', 'Priorisierte Aufgaben')}>
            <div className="stream-header">
                <h3 className="section-title">{t('adminDashboard.actionStream.title', 'Priorisierte Aufgaben')}</h3>
                <span className="stream-counter">
                    {t('adminDashboard.actionStream.counter', '{count} offen', { count: totalPending })}
                </span>
            </div>

            {tasks.length === 0 ? (
                <p className="stream-empty">{t('adminDashboard.actionStream.empty', 'Aktuell liegen keine offenen Aufgaben an.')}</p>
            ) : (
                <ul className="action-stream-list">
                    {tasks.map((task) => (
                        <li key={task.id} className={`stream-item stream-${task.type}`}>
                            <div className="stream-item-main">
                                <div className="stream-item-icon" aria-hidden="true">{iconForType(task.type)}</div>
                                <div className="stream-item-body">
                                    <div className="stream-item-meta">
                                        <span className="stream-user">{task.username || t('adminDashboard.unknownUser', 'Unbekannt')}</span>
                                        {task.type === 'vacation' ? (
                                            <span className="stream-date">{task.windowLabel}</span>
                                        ) : (
                                            <span className="stream-date">{formatDate(task.requestDate)}</span>
                                        )}
                                    </div>
                                    <div className="stream-item-detail">
                                        {task.type === 'vacation' ? (
                                            <>
                                                <span className="stream-badge">
                                                    {t('adminDashboard.actionStream.vacationRequest', 'Urlaubsantrag')}
                                                </span>
                                                {task.halfDay && (
                                                    <span className="stream-flag">{t('adminDashboard.halfDayShort', '½ Tag')}</span>
                                                )}
                                                {task.usesOvertime && (
                                                    <span className="stream-flag overtime">{t('adminDashboard.overtimeVacationShort', 'ÜS')}</span>
                                                )}
                                            </>
                                        ) : (
                                            <>
                                                <span className="stream-badge correction">
                                                    {t('adminDashboard.actionStream.correctionRequest', 'Korrekturantrag')}
                                                </span>
                                                {task.reason && <span className="stream-flag">{task.reason}</span>}
                                            </>
                                        )}
                                    </div>
                                </div>
                            </div>
                            <div className="stream-actions">
                                <button
                                    type="button"
                                    className="stream-btn approve"
                                    onClick={() => handleApprove(task)}
                                >
                                    {t('adminDashboard.approveButton', 'Genehmigen')}
                                </button>
                                <button
                                    type="button"
                                    className="stream-btn deny"
                                    onClick={() => handleDeny(task)}
                                >
                                    {t('adminDashboard.rejectButton', 'Ablehnen')}
                                </button>
                                <button
                                    type="button"
                                    className="stream-btn ghost"
                                    onClick={() => onFocusUser?.(task.username)}
                                >
                                    {t('adminDashboard.actionStream.focusUser', 'Im Wochenraster öffnen')}
                                </button>
                            </div>
                        </li>
                    ))}
                </ul>
            )}

            <div className="stream-footer">
                {onOpenVacationCenter && (
                    <button
                        type="button"
                        className="stream-link"
                        onClick={onOpenVacationCenter}
                        disabled={pendingVacationCount === 0}
                    >
                        {t('adminDashboard.actionStream.openVacations', 'Alle Urlaubsanträge öffnen')}
                    </button>
                )}
                {onOpenCorrectionCenter && (
                    <button
                        type="button"
                        className="stream-link"
                        onClick={onOpenCorrectionCenter}
                        disabled={pendingCorrectionCount === 0}
                    >
                        {t('adminDashboard.actionStream.openCorrections', 'Alle Korrekturen öffnen')}
                    </button>
                )}
                {totalPending > tasks.length && (
                    <span className="stream-more">
                        {t('adminDashboard.actionStream.morePending', '+{count} weitere Aufgaben', {
                            count: totalPending - tasks.length,
                        })}
                    </span>
                )}
            </div>
        </section>
    );
};

AdminActionStream.propTypes = {
    t: PropTypes.func.isRequired,
    allVacations: PropTypes.arrayOf(PropTypes.shape({
        id: PropTypes.number,
        username: PropTypes.string,
        startDate: PropTypes.string,
        endDate: PropTypes.string,
        approved: PropTypes.bool,
        denied: PropTypes.bool,
        halfDay: PropTypes.bool,
        usesOvertime: PropTypes.bool,
    })),
    allCorrections: PropTypes.arrayOf(PropTypes.shape({
        id: PropTypes.number,
        username: PropTypes.string,
        requestDate: PropTypes.string,
        reason: PropTypes.string,
        approved: PropTypes.bool,
        denied: PropTypes.bool,
        desiredTimestamp: PropTypes.string,
        originalTimestamp: PropTypes.string,
        entries: PropTypes.array,
    })),
    onApproveVacation: PropTypes.func,
    onDenyVacation: PropTypes.func,
    onApproveCorrection: PropTypes.func,
    onDenyCorrection: PropTypes.func,
    onOpenVacationCenter: PropTypes.func,
    onOpenCorrectionCenter: PropTypes.func,
    onFocusUser: PropTypes.func,
};

AdminActionStream.defaultProps = {
    allVacations: [],
    allCorrections: [],
    onApproveVacation: undefined,
    onDenyVacation: undefined,
    onApproveCorrection: undefined,
    onDenyCorrection: undefined,
    onOpenVacationCenter: undefined,
    onOpenCorrectionCenter: undefined,
    onFocusUser: undefined,
};

export default AdminActionStream;
