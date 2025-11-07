import {
    useMemo,
    useCallback,
    useEffect,
    useLayoutEffect,
    useRef,
    useState,
} from 'react';
import PropTypes from 'prop-types';
import { FixedSizeList as VirtualList } from 'react-window';
import { formatDate, formatTime } from './adminDashboardUtils';

const ROW_HEIGHT = 88;
const DEFAULT_WIDTH = 680;

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

const deriveItemsFromLegacyProps = (allVacations, allCorrections) => {
    const pendingVacations = (Array.isArray(allVacations) ? allVacations : []).map((vac) => {
        const priorityDate = buildPriorityDate(vac.startDate, vac.createdAt, vac.requestedAt);
        return {
            id: `vac-${vac.id}`,
            entityId: vac.id,
            type: 'vacation',
            status: vac.approved ? 'approved' : vac.denied ? 'denied' : 'pending',
            username: vac.username,
            title: `${formatDate(vac.startDate)} – ${formatDate(vac.endDate)}`,
            createdAt: vac.createdAt || vac.requestedAt,
            priority: priorityDate ? priorityDate.getTime() : Number.MAX_SAFE_INTEGER,
            flags: {
                halfDay: !!vac.halfDay,
                usesOvertime: !!vac.usesOvertime,
            },
            raw: vac,
        };
    });

    const pendingCorrections = (Array.isArray(allCorrections) ? allCorrections : []).map((corr) => {
        const priorityDate = buildPriorityDate(
            corr.requestDate,
            corr.desiredTimestamp,
            corr.originalTimestamp,
            corr.entries && corr.entries[0] && (corr.entries[0].desiredTimestamp || corr.entries[0].originalTimestamp),
        );
        return {
            id: `corr-${corr.id}`,
            entityId: corr.id,
            type: 'correction',
            status: corr.approved ? 'approved' : corr.denied ? 'denied' : 'pending',
            username: corr.username,
            title: corr.reason || '',
            createdAt: corr.requestDate,
            priority: priorityDate ? priorityDate.getTime() : Number.MAX_SAFE_INTEGER,
            flags: {
                entryCount: Array.isArray(corr.entries) ? corr.entries.length : 0,
            },
            raw: corr,
        };
    });

    return [...pendingVacations, ...pendingCorrections].sort((a, b) => a.priority - b.priority);
};

const AdminActionStream = ({
    t,
    items,
    allVacations,
    allCorrections,
    selectedIds,
    focusedId,
    onRequestFocus,
    onToggleSelect,
    onToggleSelectRange,
    onApprove,
    onDeny,
    onBulkApprove,
    onBulkDeny,
    onBulkAutoApprove,
    onFocusUser,
    searchTerm,
    onSearchTermChange,
    statusSummary,
    searchInputRef,
}) => {
    const containerRef = useRef(null);
    const [listWidth, setListWidth] = useState(DEFAULT_WIDTH);
    const [lastClickedIndex, setLastClickedIndex] = useState(null);

    useLayoutEffect(() => {
        if (!containerRef.current || typeof ResizeObserver === 'undefined') {
            return undefined;
        }
        const observer = new ResizeObserver((entries) => {
            const entry = entries[0];
            if (!entry) return;
            setListWidth(entry.contentRect.width);
        });
        observer.observe(containerRef.current);
        return () => observer.disconnect();
    }, []);

    useEffect(() => {
        const node = containerRef.current;
        if (!node) return;
        const width = node.getBoundingClientRect().width;
        if (width > 0) {
            setListWidth(width);
        }
    }, [items]);

    const derivedItems = useMemo(() => {
        if (Array.isArray(items) && items.length > 0) {
            return items;
        }
        return deriveItemsFromLegacyProps(allVacations, allCorrections);
    }, [items, allVacations, allCorrections]);

    const pendingCount = useMemo(() => derivedItems.filter((item) => item.status === 'pending').length, [derivedItems]);

    const handleToggle = useCallback((item, index, event) => {
        const previousIndex = lastClickedIndex;
        onToggleSelect?.(item, event?.shiftKey);
        setLastClickedIndex(index);
        if (event?.shiftKey && typeof onToggleSelectRange === 'function' && previousIndex !== null && previousIndex !== index) {
            onToggleSelectRange(index, previousIndex);
        }
    }, [onToggleSelect, onToggleSelectRange, lastClickedIndex]);

    const handleRowClick = useCallback((item, index, event) => {
        if (event?.defaultPrevented) return;
        onRequestFocus?.(item, index);
        if (event?.detail === 2) {
            onFocusUser?.(item.username);
        }
    }, [onRequestFocus, onFocusUser]);

    const listHeight = useMemo(() => {
        const length = Math.max(derivedItems.length, 1);
        return Math.min(length, 12) * ROW_HEIGHT;
    }, [derivedItems.length]);

    const renderRow = useCallback(({ index, style }) => {
        const item = derivedItems[index];
        if (!item) {
            return (
                <div style={style} className="stream-row stream-row-empty">
                    {t('adminDashboard.actionStream.emptyRow', 'Keine Elemente')}
                </div>
            );
        }
        const isSelected = selectedIds.includes(item.id);
        const isFocused = focusedId === item.id;
        const flags = item.flags || {};

        return (
            <div
                key={item.id}
                className={`stream-row stream-${item.type}${isSelected ? ' is-selected' : ''}${isFocused ? ' is-focused' : ''}`}
                style={style}
                role="row"
                tabIndex={0}
                aria-selected={isSelected}
                onClick={(event) => handleRowClick(item, index, event)}
                onContextMenu={(event) => {
                    event.preventDefault();
                    onFocusUser?.(item.username);
                }}
                onFocus={() => onRequestFocus?.(item, index)}
            >
                <div className="stream-cell stream-select">
                    <input
                        type="checkbox"
                        checked={isSelected}
                        onChange={(event) => handleToggle(item, index, event)}
                        onClick={(event) => event.stopPropagation()}
                        aria-label={t('adminDashboard.actionStream.selectRow', 'Element auswählen')}
                    />
                </div>
                <div className="stream-cell stream-icon" aria-hidden="true">
                    {item.type === 'vacation' ? '🏖️' : '🛠️'}
                </div>
                <div className="stream-cell stream-body">
                    <div className="stream-meta">
                        <span className="stream-user">{item.username || t('adminDashboard.unknownUser', 'Unbekannt')}</span>
                        <span className="stream-date">{item.createdAt ? formatDate(item.createdAt) : t('adminDashboard.actionStream.noDate', 'Kein Datum')}</span>
                    </div>
                    <div className="stream-title">
                        {item.type === 'vacation'
                            ? `${formatDate(item.raw?.startDate)} – ${formatDate(item.raw?.endDate)}`
                            : item.title || t('adminDashboard.actionStream.correctionRequest', 'Korrekturantrag')}
                    </div>
                    {item.type === 'correction' && Array.isArray(item.raw?.entries) && item.raw.entries.length > 0 && (
                        <div className="stream-subtitle">
                            {item.raw.entries.map((entry, entryIndex) => {
                                const desired = entry.desiredTimestamp ? formatTime(entry.desiredTimestamp) : null;
                                const original = entry.originalTimestamp ? formatTime(entry.originalTimestamp) : null;
                                return (
                                    <span key={`${item.id}-entry-${entryIndex}`} className="stream-entry-pill">
                                        {desired || original}
                                    </span>
                                );
                            })}
                        </div>
                    )}
                    <div className="stream-flags">
                        {item.type === 'vacation' && flags.halfDay && (
                            <span className="stream-flag">{t('adminDashboard.halfDayShort', '½ Tag')}</span>
                        )}
                        {item.type === 'vacation' && flags.usesOvertime && (
                            <span className="stream-flag overtime">{t('adminDashboard.overtimeVacationShort', 'ÜS')}</span>
                        )}
                        {item.type === 'correction' && item.isLowRisk && (
                            <span className="stream-flag low-risk">{t('adminDashboard.actionStream.lowRisk', 'Low Risk')}</span>
                        )}
                        {item.status !== 'pending' && (
                            <span className={`stream-flag status-${item.status}`}>{t(`status.${item.status}`, item.status)}</span>
                        )}
                    </div>
                </div>
                <div className="stream-cell stream-actions">
                    <button
                        type="button"
                        className="stream-btn approve"
                        onClick={(event) => {
                            event.stopPropagation();
                            onApprove?.(item);
                        }}
                    >
                        {t('adminDashboard.approveButton', 'Genehmigen')}
                    </button>
                    <button
                        type="button"
                        className="stream-btn deny"
                        onClick={(event) => {
                            event.stopPropagation();
                            onDeny?.(item);
                        }}
                    >
                        {t('adminDashboard.rejectButton', 'Ablehnen')}
                    </button>
                </div>
            </div>
        );
    }, [derivedItems, focusedId, handleRowClick, handleToggle, onApprove, onDeny, onFocusUser, onRequestFocus, selectedIds, t]);

    const summaryLabel = useMemo(() => {
        if (!statusSummary) {
            return t('adminDashboard.actionStream.counter', '{count} offen', { count: pendingCount });
        }
        const parts = [];
        if (typeof statusSummary.pending === 'number') {
            parts.push(t('adminDashboard.actionStream.summary.pending', '{count} offen', { count: statusSummary.pending }));
        }
        if (typeof statusSummary.vacations === 'number') {
            parts.push(t('adminDashboard.actionStream.summary.vacations', '{count} Urlaub', { count: statusSummary.vacations }));
        }
        if (typeof statusSummary.corrections === 'number') {
            parts.push(t('adminDashboard.actionStream.summary.corrections', '{count} Korrekturen', { count: statusSummary.corrections }));
        }
        return parts.join(' · ') || t('adminDashboard.actionStream.counter', '{count} offen', { count: pendingCount });
    }, [statusSummary, pendingCount, t]);

    return (
        <section className="action-stream content-section expanded" aria-label={t('adminDashboard.actionStream.title', 'Priorisierte Aufgaben')}>
            <div className="stream-header sticky">
                <div>
                    <h3 className="section-title">{t('adminDashboard.actionStream.title', 'Priorisierte Aufgaben')}</h3>
                    <p className="stream-counter">{summaryLabel}</p>
                </div>
                <div className="stream-toolbar">
                    <input
                        type="search"
                        className="inbox-search"
                        placeholder={t('adminDashboard.actionStream.searchPlaceholder', 'Suche (Benutzer, Grund, Datum)…')}
                        value={searchTerm}
                        ref={searchInputRef}
                        onChange={(event) => onSearchTermChange?.(event.target.value)}
                        aria-label={t('search', 'Suchen')}
                    />
                    <div className="stream-bulk-actions">
                        <button
                            type="button"
                            className="button-secondary"
                            onClick={() => onBulkApprove?.()}
                            disabled={selectedIds.length === 0}
                        >
                            {t('adminDashboard.actionStream.bulkApprove', 'Auswahl genehmigen')}
                        </button>
                        <button
                            type="button"
                            className="button-secondary"
                            onClick={() => onBulkDeny?.()}
                            disabled={selectedIds.length === 0}
                        >
                            {t('adminDashboard.actionStream.bulkDeny', 'Auswahl ablehnen')}
                        </button>
                        <button
                            type="button"
                            className="button-ghost"
                            onClick={() => onBulkAutoApprove?.()}
                        >
                            {t('adminDashboard.actionStream.approveLowRisk', 'Low-Risk genehmigen')}
                        </button>
                    </div>
                </div>
            </div>
            <div className="stream-table" ref={containerRef}>
                {derivedItems.length === 0 ? (
                    <div className="stream-empty">
                        {t('adminDashboard.actionStream.empty', 'Aktuell liegen keine offenen Aufgaben an.')}
                    </div>
                ) : (
                    <VirtualList
                        height={listHeight}
                        itemCount={derivedItems.length}
                        itemSize={ROW_HEIGHT}
                        width={listWidth || DEFAULT_WIDTH}
                    >
                        {renderRow}
                    </VirtualList>
                )}
            </div>
        </section>
    );
};

AdminActionStream.propTypes = {
    t: PropTypes.func.isRequired,
    items: PropTypes.arrayOf(PropTypes.shape({
        id: PropTypes.string.isRequired,
        type: PropTypes.oneOf(['vacation', 'correction']).isRequired,
    })),
    allVacations: PropTypes.array,
    allCorrections: PropTypes.array,
    selectedIds: PropTypes.arrayOf(PropTypes.string),
    focusedId: PropTypes.string,
    onRequestFocus: PropTypes.func,
    onToggleSelect: PropTypes.func,
    onToggleSelectRange: PropTypes.func,
    onApprove: PropTypes.func,
    onDeny: PropTypes.func,
    onBulkApprove: PropTypes.func,
    onBulkDeny: PropTypes.func,
    onBulkAutoApprove: PropTypes.func,
    onFocusUser: PropTypes.func,
    searchTerm: PropTypes.string,
    onSearchTermChange: PropTypes.func,
    statusSummary: PropTypes.shape({
        pending: PropTypes.number,
        vacations: PropTypes.number,
        corrections: PropTypes.number,
    }),
    searchInputRef: PropTypes.shape({ current: PropTypes.any }),
};

AdminActionStream.defaultProps = {
    items: undefined,
    allVacations: [],
    allCorrections: [],
    selectedIds: [],
    focusedId: undefined,
    onRequestFocus: undefined,
    onToggleSelect: undefined,
    onToggleSelectRange: undefined,
    onApprove: undefined,
    onDeny: undefined,
    onBulkApprove: undefined,
    onBulkDeny: undefined,
    onBulkAutoApprove: undefined,
    onFocusUser: undefined,
    searchTerm: '',
    onSearchTermChange: undefined,
    statusSummary: undefined,
    searchInputRef: undefined,
};

export default AdminActionStream;
