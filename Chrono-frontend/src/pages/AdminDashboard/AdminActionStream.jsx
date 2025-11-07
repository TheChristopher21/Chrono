import { useCallback, useMemo, useState } from 'react';
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

const MS_IN_MINUTE = 60 * 1000;
const MS_IN_HOUR = 60 * MS_IN_MINUTE;
const MS_IN_DAY = 24 * MS_IN_HOUR;

const describePriorityBucket = (date, t, now) => {
    if (!date) {
        return {
            bucket: 'scheduled',
            label: t('adminDashboard.actionStream.bucket.scheduled', 'Geplant'),
            hint: t('adminDashboard.actionStream.bucket.scheduledHint', 'Anträge mit Datum in der Zukunft.'),
        };
    }

    const diffMs = now.getTime() - date.getTime();
    const diffDays = Math.floor(diffMs / MS_IN_DAY);

    if (diffMs > 3 * MS_IN_DAY) {
        return {
            bucket: 'overdue',
            label: t('adminDashboard.actionStream.bucket.overdue', 'Überfällig'),
            hint: t('adminDashboard.actionStream.bucket.overdueHint', 'Seit mehr als drei Tagen offen.'),
            daysOpen: diffDays,
        };
    }

    if (diffMs > MS_IN_DAY) {
        return {
            bucket: 'dueSoon',
            label: t('adminDashboard.actionStream.bucket.dueSoon', 'Fällig'),
            hint: t('adminDashboard.actionStream.bucket.dueSoonHint', 'Innerhalb der letzten drei Tage eingegangen.'),
            daysOpen: diffDays,
        };
    }

    if (diffMs >= 0) {
        return {
            bucket: 'today',
            label: t('adminDashboard.actionStream.bucket.today', 'Heute'),
            hint: t('adminDashboard.actionStream.bucket.todayHint', 'Heute eingegangen.'),
            daysOpen: diffDays,
        };
    }

    return {
        bucket: 'upcoming',
        label: t('adminDashboard.actionStream.bucket.upcoming', 'Bald fällig'),
        hint: t('adminDashboard.actionStream.bucket.upcomingHint', 'Beginnt in der Zukunft oder wartet auf Start.'),
        daysOpen: diffDays,
    };
};

const formatRelativeTime = (date, t, now) => {
    if (!date) {
        return t('adminDashboard.actionStream.relative.unknown', 'Datum offen');
    }

    const diffMs = now.getTime() - date.getTime();
    const diffMinutes = Math.round(diffMs / MS_IN_MINUTE);

    if (diffMs < 0) {
        const minutes = Math.abs(diffMinutes);
        if (minutes < 60) {
            return t('adminDashboard.actionStream.relative.inMinutes', 'In {count} Minuten', { count: minutes });
        }
        const hours = Math.round(minutes / 60);
        if (hours < 24) {
            return t('adminDashboard.actionStream.relative.inHours', 'In {count} Stunden', { count: hours });
        }
        const days = Math.round(hours / 24);
        return t('adminDashboard.actionStream.relative.inDays', 'In {count} Tagen', { count: days });
    }

    if (diffMinutes < 60) {
        return t('adminDashboard.actionStream.relative.minutes', 'Seit {count} Minuten offen', { count: Math.max(diffMinutes, 1) });
    }

    const hours = Math.floor(diffMinutes / 60);
    if (hours < 24) {
        return t('adminDashboard.actionStream.relative.hours', 'Seit {count} Stunden offen', { count: hours });
    }

    const days = Math.floor(hours / 24);
    if (days === 1) {
        return t('adminDashboard.actionStream.relative.yesterday', 'Seit gestern offen');
    }

    return t('adminDashboard.actionStream.relative.days', 'Seit {count} Tagen offen', { count: days });
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

        const now = new Date();

        const vacationTasks = pendingVacations.map((vac) => {
            const startLabel = formatDate(vac.startDate);
            const endLabel = formatDate(vac.endDate);
            const priorityDate = buildPriorityDate(vac.startDate, vac.createdAt, vac.requestedAt);
            const priorityDescriptor = describePriorityBucket(priorityDate, t, now);
            return {
                id: `vac-${vac.id}`,
                type: 'vacation',
                categoryLabel: t('adminDashboard.actionStream.vacationRequest', 'Urlaubsantrag'),
                username: vac.username,
                windowLabel: `${startLabel} – ${endLabel}`,
                halfDay: vac.halfDay,
                usesOvertime: vac.usesOvertime,
                priorityDate,
                priorityValue: priorityDate ? priorityDate.getTime() : Number.MAX_SAFE_INTEGER,
                priorityBucket: priorityDescriptor.bucket,
                priorityLabel: priorityDescriptor.label,
                priorityHint: priorityDescriptor.hint,
                relativeTimeLabel: formatRelativeTime(priorityDate, t, now),
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
            const priorityDescriptor = describePriorityBucket(priorityDate, t, now);
            return {
                id: `corr-${corr.id}`,
                type: 'correction',
                categoryLabel: t('adminDashboard.actionStream.correctionRequest', 'Korrekturantrag'),
                username: corr.username,
                reason: corr.reason,
                requestDate: corr.requestDate,
                priorityDate,
                priorityValue: priorityDate ? priorityDate.getTime() : Number.MAX_SAFE_INTEGER,
                priorityBucket: priorityDescriptor.bucket,
                priorityLabel: priorityDescriptor.label,
                priorityHint: priorityDescriptor.hint,
                relativeTimeLabel: formatRelativeTime(priorityDate, t, now),
                raw: corr,
            };
        });

        const mergedTasks = [...vacationTasks, ...correctionTasks].sort((a, b) => a.priorityValue - b.priorityValue);

        return {
            tasks: mergedTasks,
            pendingVacationCount: pendingVacations.length,
            pendingCorrectionCount: pendingCorrections.length,
        };
    }, [allVacations, allCorrections, t]);

    const totalPending = pendingVacationCount + pendingCorrectionCount;

    const filterCounts = useMemo(() => ({
        all: tasks.length,
        vacation: tasks.filter((task) => task.type === 'vacation').length,
        correction: tasks.filter((task) => task.type === 'correction').length,
    }), [tasks]);

    const [activeFilter, setActiveFilter] = useState('all');
    const [searchTerm, setSearchTerm] = useState('');
    const [autoAdvance, setAutoAdvance] = useState(true);
    const [processingTaskId, setProcessingTaskId] = useState(null);
    const [batchProcessing, setBatchProcessing] = useState(false);

    const normalizedSearch = searchTerm.trim().toLowerCase();

    const filteredTasks = useMemo(() => {
        return tasks.filter((task) => {
            if (activeFilter !== 'all' && task.type !== activeFilter) {
                return false;
            }
            if (!normalizedSearch) {
                return true;
            }
            const requestLabel = task.requestDate ? formatDate(task.requestDate) : '';
            const haystack = [
                task.username,
                task.categoryLabel,
                task.reason,
                task.windowLabel,
                requestLabel,
            ]
                .filter(Boolean)
                .join(' ')
                .toLowerCase();
            return haystack.includes(normalizedSearch);
        });
    }, [tasks, activeFilter, normalizedSearch]);

    const bucketOrder = ['overdue', 'dueSoon', 'today', 'upcoming', 'scheduled'];

    const groupedTasks = useMemo(() => bucketOrder
        .map((bucket) => {
            const tasksInBucket = filteredTasks.filter((task) => task.priorityBucket === bucket);
            if (tasksInBucket.length === 0) {
                return null;
            }

            const usersMap = new Map();
            tasksInBucket.forEach((task) => {
                const key = task.username || 'unknown';
                if (!usersMap.has(key)) {
                    usersMap.set(key, []);
                }
                usersMap.get(key).push(task);
            });

            return {
                bucket,
                label: tasksInBucket[0].priorityLabel,
                hint: tasksInBucket[0].priorityHint,
                total: tasksInBucket.length,
                groups: Array.from(usersMap.entries()).map(([username, userTasks]) => ({
                    username,
                    tasks: userTasks,
                })),
            };
        })
        .filter(Boolean), [filteredTasks]);

    const getNextTaskUsername = useCallback((currentTaskId) => {
        const index = filteredTasks.findIndex((task) => task.id === currentTaskId);
        if (index === -1) {
            return null;
        }
        for (let i = index + 1; i < filteredTasks.length; i += 1) {
            if (filteredTasks[i].username) {
                return filteredTasks[i].username;
            }
        }
        return null;
    }, [filteredTasks]);

    const handleApprove = useCallback((task) => {
        if (task.type === 'vacation') {
            return onApproveVacation?.(task.raw.id);
        }
        return onApproveCorrection?.(task.raw.id, '');
    }, [onApproveVacation, onApproveCorrection]);

    const handleDeny = useCallback((task) => {
        if (task.type === 'vacation') {
            return onDenyVacation?.(task.raw.id);
        }
        return onDenyCorrection?.(task.raw.id, '');
    }, [onDenyVacation, onDenyCorrection]);

    const handleAfterAction = useCallback((taskId) => {
        if (!autoAdvance) {
            return;
        }
        const nextUsername = getNextTaskUsername(taskId);
        if (nextUsername) {
            onFocusUser?.(nextUsername);
        }
    }, [autoAdvance, getNextTaskUsername, onFocusUser]);

    const handleApproveClick = useCallback(async (task) => {
        if (!task) return;
        setProcessingTaskId(task.id);
        try {
            await handleApprove(task);
            handleAfterAction(task.id);
        } finally {
            setProcessingTaskId(null);
        }
    }, [handleApprove, handleAfterAction]);

    const handleDenyClick = useCallback(async (task) => {
        if (!task) return;
        setProcessingTaskId(task.id);
        try {
            await handleDeny(task);
            handleAfterAction(task.id);
        } finally {
            setProcessingTaskId(null);
        }
    }, [handleAfterAction, handleDeny]);

    const canBatchApprove = activeFilter !== 'all' && filteredTasks.length > 0;

    const handleBatchApprove = useCallback(async () => {
        if (!canBatchApprove) {
            return;
        }

        const confirmMessage = t('adminDashboard.actionStream.batchConfirm', 'Alle angezeigten Anträge dieses Filters genehmigen?');
        if (typeof window !== 'undefined' && !window.confirm(confirmMessage)) {
            return;
        }

        setBatchProcessing(true);
        try {
            // eslint-disable-next-line no-restricted-syntax
            for (const task of filteredTasks) {
                // eslint-disable-next-line no-await-in-loop
                await handleApprove(task);
            }
        } finally {
            setBatchProcessing(false);
        }
    }, [canBatchApprove, filteredTasks, handleApprove, t]);

    const iconForType = (type) => (type === 'vacation' ? '🏖️' : '🛠️');

    return (
        <section className="action-stream content-section" aria-label={t('adminDashboard.actionStream.title', 'Priorisierte Aufgaben')}>
            <div className="stream-header">
                <div className="stream-header-main">
                    <h3 className="section-title">{t('adminDashboard.actionStream.title', 'Priorisierte Aufgaben')}</h3>
                    <span className="stream-counter">
                        {t('adminDashboard.actionStream.counter', '{count} offen', { count: totalPending })}
                    </span>
                </div>
                <div className="stream-header-actions">
                    <label className="auto-advance-toggle">
                        <input
                            type="checkbox"
                            checked={autoAdvance}
                            onChange={() => setAutoAdvance((prev) => !prev)}
                        />
                        <span>{t('adminDashboard.actionStream.autoAdvance', 'Nächsten Vorgang automatisch öffnen')}</span>
                    </label>
                    <button
                        type="button"
                        className="stream-btn batch"
                        onClick={handleBatchApprove}
                        disabled={!canBatchApprove || batchProcessing}
                    >
                        {batchProcessing
                            ? t('adminDashboard.actionStream.batchProcessing', 'Serie läuft …')
                            : t('adminDashboard.actionStream.batchApprove', 'Alle im Filter genehmigen')}
                    </button>
                </div>
            </div>

            <div className="stream-filters" role="tablist" aria-label={t('adminDashboard.actionStream.filterLabel', 'Aufgaben filtern')}>
                {(['all', 'vacation', 'correction']).map((filterKey) => (
                    <button
                        key={filterKey}
                        type="button"
                        role="tab"
                        className={`stream-filter ${activeFilter === filterKey ? 'is-active' : ''}`}
                        aria-selected={activeFilter === filterKey}
                        onClick={() => setActiveFilter(filterKey)}
                    >
                        <span className="stream-filter-label">
                            {filterKey === 'all'
                                ? t('adminDashboard.actionStream.filter.all', 'Alle')
                                : filterKey === 'vacation'
                                    ? t('adminDashboard.actionStream.filter.vacation', 'Urlaub')
                                    : t('adminDashboard.actionStream.filter.correction', 'Korrekturen')}
                        </span>
                        <span className="stream-filter-count">{filterCounts[filterKey]}</span>
                    </button>
                ))}
            </div>

            <div className="stream-meta">
                <div className="stream-search">
                    <input
                        type="search"
                        value={searchTerm}
                        onChange={(event) => setSearchTerm(event.target.value)}
                        placeholder={t('adminDashboard.actionStream.searchPlaceholder', 'Suche nach Person, Grund oder Zeitraum')}
                        aria-label={t('adminDashboard.actionStream.searchLabel', 'Aufgaben durchsuchen')}
                    />
                </div>
                <div className="stream-summary">
                    <span>{t('adminDashboard.actionStream.summary', '{count} Aufgaben im aktuellen Filter', { count: filteredTasks.length })}</span>
                </div>
            </div>

            <div className="stream-groups">
                {groupedTasks.length === 0 ? (
                    <p className="stream-empty">{t('adminDashboard.actionStream.empty', 'Aktuell liegen keine offenen Aufgaben an.')}</p>
                ) : (
                    groupedTasks.map((group) => (
                        <div key={group.bucket} className={`stream-bucket bucket-${group.bucket}`}>
                            <div className="stream-bucket-header">
                                <div>
                                    <span className="stream-bucket-title">{group.label}</span>
                                    <span className="stream-bucket-hint">{group.hint}</span>
                                </div>
                                <span className="stream-bucket-count">{group.total}</span>
                            </div>
                            {group.groups.map((userGroup) => (
                                <div key={userGroup.username} className="stream-user-group">
                                    <div className="stream-user-header">
                                        <span className="stream-user-name">{userGroup.username === 'unknown' ? t('adminDashboard.unknownUser', 'Unbekannt') : userGroup.username}</span>
                                        <span className="stream-user-count">{t('adminDashboard.actionStream.itemsForUser', '{count} Vorgänge', { count: userGroup.tasks.length })}</span>
                                    </div>
                                    <ul className="action-stream-list" role="list">
                                        {userGroup.tasks.map((task) => {
                                            const isProcessing = processingTaskId === task.id || batchProcessing;
                                            return (
                                                <li key={task.id} className={`stream-item stream-${task.type}`}>
                                                    <div className="stream-item-main">
                                                        <div className="stream-item-icon" aria-hidden="true">{iconForType(task.type)}</div>
                                                        <div className="stream-item-body">
                                                            <div className="stream-item-meta">
                                                                <span className="stream-date">{task.type === 'vacation' ? task.windowLabel : formatDate(task.requestDate)}</span>
                                                                <span className="stream-priority" title={task.priorityHint}>{task.priorityLabel}</span>
                                                                <span className="stream-relative">{task.relativeTimeLabel}</span>
                                                            </div>
                                                            <div className="stream-item-detail">
                                                                <span className={`stream-badge badge-${task.type}`}>{task.categoryLabel}</span>
                                                                {task.type === 'vacation' && task.halfDay && (
                                                                    <span className="stream-flag">{t('adminDashboard.halfDayShort', '½ Tag')}</span>
                                                                )}
                                                                {task.type === 'vacation' && task.usesOvertime && (
                                                                    <span className="stream-flag overtime">{t('adminDashboard.overtimeVacationShort', 'ÜS')}</span>
                                                                )}
                                                                {task.type === 'correction' && task.reason && (
                                                                    <span className="stream-flag">{task.reason}</span>
                                                                )}
                                                            </div>
                                                        </div>
                                                    </div>
                                                    <div className="stream-actions">
                                                        <button
                                                            type="button"
                                                            className="stream-btn approve"
                                                            onClick={() => handleApproveClick(task)}
                                                            disabled={isProcessing}
                                                        >
                                                            {t('adminDashboard.approveButton', 'Genehmigen')}
                                                        </button>
                                                        <button
                                                            type="button"
                                                            className="stream-btn deny"
                                                            onClick={() => handleDenyClick(task)}
                                                            disabled={isProcessing}
                                                        >
                                                            {t('adminDashboard.rejectButton', 'Ablehnen')}
                                                        </button>
                                                        <button
                                                            type="button"
                                                            className="stream-btn ghost"
                                                            onClick={() => onFocusUser?.(task.username)}
                                                            disabled={batchProcessing}
                                                        >
                                                            {t('adminDashboard.actionStream.focusUser', 'Im Wochenraster öffnen')}
                                                        </button>
                                                    </div>
                                                </li>
                                            );
                                        })}
                                    </ul>
                                </div>
                            ))}
                        </div>
                    ))
                )}
            </div>

            <div className="stream-footer">
                {onOpenVacationCenter && (
                    <button
                        type="button"
                        className="stream-link"
                        onClick={onOpenVacationCenter}
                        disabled={pendingVacationCount === 0 || batchProcessing}
                    >
                        {t('adminDashboard.actionStream.openVacations', 'Alle Urlaubsanträge öffnen')}
                    </button>
                )}
                {onOpenCorrectionCenter && (
                    <button
                        type="button"
                        className="stream-link"
                        onClick={onOpenCorrectionCenter}
                        disabled={pendingCorrectionCount === 0 || batchProcessing}
                    >
                        {t('adminDashboard.actionStream.openCorrections', 'Alle Korrekturen öffnen')}
                    </button>
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
