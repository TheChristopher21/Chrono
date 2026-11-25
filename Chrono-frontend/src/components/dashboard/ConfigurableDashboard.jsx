import { useId, useMemo, useRef, useState } from 'react';
import PropTypes from 'prop-types';
import { ACCESS_VIEW, hasFeatureAccess, hasPageAccess } from '../../utils/pageAccess.js';
import useDashboardPreferences, { DASHBOARD_WIDGET_SIZES } from '../../hooks/useDashboardPreferences.js';
import FreeDashboardLayout from './FreeDashboardLayout.jsx';
import './ConfigurableDashboard.css';

const DEFAULT_LABELS = {
    customize: 'Dashboard anpassen',
    finish: 'Fertig',
    configurationTitle: 'Dashboard-Bereiche',
    configurationHint: 'Bereiche ein- oder ausblenden, verschieben und in der Größe anpassen.',
    reset: 'Standard wiederherstellen',
    show: 'Anzeigen',
    hide: 'Ausblenden',
    moveUp: 'Nach oben',
    moveDown: 'Nach unten',
    size: 'Größe',
    drag: 'Zum Verschieben ziehen',
    serverSaved: 'Mit dem Benutzerkonto synchronisiert',
    localSaved: 'Lokal auf diesem Gerät gespeichert',
    loading: 'Einstellungen werden geladen',
    saving: 'Wird gespeichert …',
    empty: 'Für dieses Dashboard sind keine Bereiche verfügbar.',
};

const SIZE_LABELS = {
    S: 'S',
    M: 'M',
    L: 'L',
    full: 'Volle Breite',
};

const toArray = (value) => (Array.isArray(value) ? value : [value]);

const isWidgetAvailable = (widget, context, permissionContext) => {
    if (Array.isArray(widget.allowedContexts) && !widget.allowedContexts.includes(context)) {
        return false;
    }

    if (widget.requiredPagePermission) {
        const pagePermissions = toArray(widget.requiredPagePermission);
        const requiredAccess = widget.requiredAccess || ACCESS_VIEW;
        if (!pagePermissions.some((pageKey) => hasPageAccess(permissionContext, pageKey, requiredAccess))) {
            return false;
        }
    }

    if (widget.featureKey || widget.requiredFeature) {
        const features = toArray(widget.featureKey || widget.requiredFeature);
        if (!features.every((featureKey) => hasFeatureAccess(permissionContext, featureKey))) {
            return false;
        }
    }

    if (widget.requiredRole) {
        const userRoles = permissionContext?.roles || [];
        if (!toArray(widget.requiredRole).some((role) => userRoles.includes(role))) {
            return false;
        }
    }

    return typeof widget.isAvailable !== 'function'
        || widget.isAvailable(permissionContext, context);
};

const getAllowedSizes = (widget) => {
    const configured = Array.isArray(widget.sizes) && widget.sizes.length > 0
        ? widget.sizes
        : DASHBOARD_WIDGET_SIZES;
    return [...new Set(configured.map((size) => (
        String(size).toLowerCase() === 'full' ? 'full' : String(size).toUpperCase()
    )).filter((size) => DASHBOARD_WIDGET_SIZES.includes(size)))];
};

const renderRegistryWidget = (widget) => {
    if (typeof widget.render === 'function') return widget.render();
    if (typeof widget.component === 'function') {
        const WidgetComponent = widget.component;
        return <WidgetComponent {...(widget.props || {})} />;
    }
    return widget.component ?? null;
};

const getPersistenceLabel = (status, labels) => {
    if (status === 'server') return labels.serverSaved;
    if (status === 'local') return labels.localSaved;
    if (status === 'saving') return labels.saving;
    return labels.loading;
};

const ConfigurableDashboard = ({
    context,
    scope = 'default',
    registry,
    defaultLayout = [],
    permissionContext,
    storageIdentity = 'current-user',
    preferenceEndpoint,
    preferenceParams,
    remoteEnabled = true,
    labels: customLabels,
    className = '',
    editable = true,
    emptyState,
    layoutMode = 'ordered',
    gridRowHeight = 72,
}) => {
    const labels = { ...DEFAULT_LABELS, ...(customLabels || {}) };
    const editorId = useId();
    const [isEditing, setIsEditing] = useState(false);
    const [draggedId, setDraggedId] = useState(null);
    const [announcement, setAnnouncement] = useState('');
    const draggedIdRef = useRef(null);

    const availableRegistry = useMemo(
        () => registry.filter((widget) => (
            widget?.id && isWidgetAvailable(widget, context, permissionContext)
        )),
        [context, permissionContext, registry]
    );
    const widgetById = useMemo(
        () => new Map(availableRegistry.map((widget) => [String(widget.id), widget])),
        [availableRegistry]
    );

    const {
        layout,
        updateLayout,
        resetLayout,
        persistenceStatus,
    } = useDashboardPreferences({
        context,
        scope,
        registry: availableRegistry,
        defaultLayout,
        storageIdentity,
        preferenceEndpoint,
        preferenceParams,
        remoteEnabled,
        layoutMode,
    });

    const orderedLayout = useMemo(
        () => [...layout].sort((a, b) => a.order - b.order),
        [layout]
    );
    const visibleLayout = orderedLayout.filter((item) => item.visible && widgetById.has(item.id));

    const announce = (message) => {
        setAnnouncement('');
        window.setTimeout(() => setAnnouncement(message), 0);
    };

    const changeVisibility = (id, visible) => {
        const widget = widgetById.get(id);
        if (!widget || widget.locked || widget.lockedVisibility) return;
        updateLayout((current) => current.map((item) => (
            item.id === id ? { ...item, visible } : item
        )));
        announce(`${widget.title}: ${visible ? labels.show : labels.hide}`);
    };

    const changeSize = (id, size) => {
        const widget = widgetById.get(id);
        const allowedSizes = widget ? getAllowedSizes(widget) : [];
        if (!widget || widget.locked || widget.lockedSize || !allowedSizes.includes(size)) return;
        updateLayout((current) => current.map((item) => (
            item.id === id ? { ...item, size } : item
        )));
        announce(`${widget.title}: ${labels.size} ${SIZE_LABELS[size]}`);
    };

    const moveWidgetToIndex = (id, targetIndex) => {
        const widget = widgetById.get(id);
        if (!widget || widget.locked || widget.lockedOrder) return;

        updateLayout((current) => {
            const sorted = [...current].sort((a, b) => a.order - b.order);
            const sourceIndex = sorted.findIndex((item) => item.id === id);
            if (sourceIndex < 0) return current;
            const boundedTarget = Math.max(0, Math.min(targetIndex, sorted.length - 1));
            if (boundedTarget === sourceIndex) return current;
            const [moved] = sorted.splice(sourceIndex, 1);
            sorted.splice(boundedTarget, 0, moved);
            return sorted.map((item, order) => ({ ...item, order }));
        });
        announce(`${widget.title}: Position ${targetIndex + 1}`);
    };

    const moveWidget = (id, direction) => {
        const index = orderedLayout.findIndex((item) => item.id === id);
        if (index < 0) return;
        moveWidgetToIndex(id, index + direction);
    };

    const handleDragStart = (event, id) => {
        draggedIdRef.current = id;
        setDraggedId(id);
        if (event.dataTransfer) {
            event.dataTransfer.effectAllowed = 'move';
            event.dataTransfer.setData('text/plain', id);
        }
    };

    const handleDrop = (event, targetId) => {
        event.preventDefault();
        const sourceId = draggedIdRef.current || event.dataTransfer?.getData('text/plain');
        const targetIndex = orderedLayout.findIndex((item) => item.id === targetId);
        if (sourceId && sourceId !== targetId && targetIndex >= 0) {
            moveWidgetToIndex(sourceId, targetIndex);
        }
        draggedIdRef.current = null;
        setDraggedId(null);
    };

    const toggleEditing = () => {
        setIsEditing((current) => !current);
        setAnnouncement('');
    };

    if (layoutMode === 'free') {
        return <FreeDashboardLayout key={`${context}:${scope}:${storageIdentity}`}
            context={context} scope={scope} className={className} editable={editable}
            registry={availableRegistry} layout={layout} updateLayout={updateLayout} resetLayout={resetLayout}
            persistenceStatus={persistenceStatus} persistenceLabel={getPersistenceLabel(persistenceStatus, labels)}
            labels={labels} emptyState={emptyState} renderWidget={renderRegistryWidget} gridRowHeight={gridRowHeight} />;
    }

    return (
        <section
            className={`configurable-dashboard ${isEditing ? 'is-editing' : ''} ${className}`.trim()}
            data-dashboard-context={context}
            data-dashboard-scope={scope}
        >
            {editable && (
                <div className="dashboard-config-toolbar">
                    <button
                        type="button"
                        className="dashboard-config-primary"
                        onClick={toggleEditing}
                        aria-expanded={isEditing}
                        aria-controls={editorId}
                    >
                        {isEditing ? labels.finish : labels.customize}
                    </button>
                    {isEditing && (
                        <button
                            type="button"
                            className="dashboard-config-secondary"
                            onClick={() => {
                                resetLayout();
                                announce(labels.reset);
                            }}
                        >
                            {labels.reset}
                        </button>
                    )}
                    <span className={`dashboard-persistence-status is-${persistenceStatus}`} role="status">
                        {getPersistenceLabel(persistenceStatus, labels)}
                    </span>
                </div>
            )}

            {isEditing && (
                <aside id={editorId} className="dashboard-config-panel" aria-label={labels.configurationTitle}>
                    <div className="dashboard-config-panel-heading">
                        <h3>{labels.configurationTitle}</h3>
                        <p>{labels.configurationHint}</p>
                    </div>
                    <ol className="dashboard-config-list">
                        {orderedLayout.map((item, index) => {
                            const widget = widgetById.get(item.id);
                            if (!widget) return null;
                            const locked = Boolean(widget.locked);
                            const allowedSizes = getAllowedSizes(widget);
                            return (
                                <li key={item.id} className={!item.visible ? 'is-hidden' : ''}>
                                    <label className="dashboard-config-visibility">
                                        <input
                                            type="checkbox"
                                            checked={item.visible}
                                            disabled={locked || widget.lockedVisibility}
                                            onChange={(event) => changeVisibility(item.id, event.target.checked)}
                                        />
                                        <span>
                                            <strong>{widget.title}</strong>
                                            {widget.description && <small>{widget.description}</small>}
                                        </span>
                                    </label>
                                    <div className="dashboard-config-item-actions">
                                        <label>
                                            <span className="sr-only">{labels.size}: {widget.title}</span>
                                            <select
                                                value={item.size}
                                                disabled={locked || widget.lockedSize}
                                                onChange={(event) => changeSize(item.id, event.target.value)}
                                                aria-label={`${labels.size}: ${widget.title}`}
                                            >
                                                {allowedSizes.map((size) => (
                                                    <option key={size} value={size}>{SIZE_LABELS[size]}</option>
                                                ))}
                                            </select>
                                        </label>
                                        <button
                                            type="button"
                                            onClick={() => moveWidget(item.id, -1)}
                                            disabled={index === 0 || locked || widget.lockedOrder}
                                            aria-label={`${widget.title}: ${labels.moveUp}`}
                                            title={labels.moveUp}
                                        >
                                            ↑
                                        </button>
                                        <button
                                            type="button"
                                            onClick={() => moveWidget(item.id, 1)}
                                            disabled={index === orderedLayout.length - 1 || locked || widget.lockedOrder}
                                            aria-label={`${widget.title}: ${labels.moveDown}`}
                                            title={labels.moveDown}
                                        >
                                            ↓
                                        </button>
                                    </div>
                                </li>
                            );
                        })}
                    </ol>
                </aside>
            )}

            <div className="dashboard-widget-grid">
                {visibleLayout.map((item) => {
                    const widget = widgetById.get(item.id);
                    const index = orderedLayout.findIndex((entry) => entry.id === item.id);
                    const locked = Boolean(widget.locked);
                    const allowedSizes = getAllowedSizes(widget);
                    const canDrag = isEditing && !locked && !widget.lockedOrder;
                    return (
                        <section
                            key={item.id}
                            className={`dashboard-widget dashboard-widget--size-${String(item.size).toLowerCase()} ${draggedId === item.id ? 'is-dragging' : ''} ${widget.className || ''}`.trim()}
                            data-dashboard-widget={item.id}
                            aria-label={widget.title}
                            draggable={canDrag}
                            onDragStart={(event) => handleDragStart(event, item.id)}
                            onDragEnd={() => {
                                draggedIdRef.current = null;
                                setDraggedId(null);
                            }}
                            onDragOver={(event) => {
                                if (canDrag || draggedIdRef.current) event.preventDefault();
                            }}
                            onDrop={(event) => handleDrop(event, item.id)}
                            onKeyDown={(event) => {
                                if (!isEditing || !event.altKey) return;
                                if (event.key === 'ArrowUp') {
                                    event.preventDefault();
                                    moveWidget(item.id, -1);
                                }
                                if (event.key === 'ArrowDown') {
                                    event.preventDefault();
                                    moveWidget(item.id, 1);
                                }
                            }}
                            tabIndex={isEditing ? 0 : undefined}
                        >
                            {isEditing && (
                                <div className="dashboard-widget-edit-bar">
                                    <span className="dashboard-widget-drag-handle" title={labels.drag} aria-hidden="true">⋮⋮</span>
                                    <strong>{widget.title}</strong>
                                    <div className="dashboard-widget-edit-actions">
                                        <select
                                            value={item.size}
                                            disabled={locked || widget.lockedSize}
                                            onChange={(event) => changeSize(item.id, event.target.value)}
                                            aria-label={`${labels.size}: ${widget.title}`}
                                        >
                                            {allowedSizes.map((size) => (
                                                <option key={size} value={size}>{SIZE_LABELS[size]}</option>
                                            ))}
                                        </select>
                                        <button
                                            type="button"
                                            onClick={() => moveWidget(item.id, -1)}
                                            disabled={index === 0 || locked || widget.lockedOrder}
                                            aria-label={`${widget.title}: ${labels.moveUp}`}
                                        >↑</button>
                                        <button
                                            type="button"
                                            onClick={() => moveWidget(item.id, 1)}
                                            disabled={index === orderedLayout.length - 1 || locked || widget.lockedOrder}
                                            aria-label={`${widget.title}: ${labels.moveDown}`}
                                        >↓</button>
                                        <button
                                            type="button"
                                            onClick={() => changeVisibility(item.id, false)}
                                            disabled={locked || widget.lockedVisibility}
                                            aria-label={`${widget.title}: ${labels.hide}`}
                                        >×</button>
                                    </div>
                                </div>
                            )}
                            <div className="dashboard-widget-content">
                                {renderRegistryWidget(widget)}
                            </div>
                        </section>
                    );
                })}
            </div>

            {visibleLayout.length === 0 && !isEditing && (
                <div className="dashboard-config-empty">
                    {emptyState || labels.empty}
                </div>
            )}
            <span className="sr-only" aria-live="polite" aria-atomic="true">{announcement}</span>
        </section>
    );
};

ConfigurableDashboard.propTypes = {
    context: PropTypes.oneOf(['USER_STANDARD', 'USER_HOURLY', 'USER_PERCENTAGE', 'ADMIN', 'PMS']).isRequired,
    scope: PropTypes.string,
    registry: PropTypes.arrayOf(PropTypes.shape({
        id: PropTypes.string.isRequired,
        title: PropTypes.string.isRequired,
        description: PropTypes.string,
        render: PropTypes.func,
        component: PropTypes.oneOfType([PropTypes.node, PropTypes.elementType]),
        props: PropTypes.object,
        requiredPagePermission: PropTypes.oneOfType([PropTypes.string, PropTypes.arrayOf(PropTypes.string)]),
        requiredAccess: PropTypes.string,
        featureKey: PropTypes.oneOfType([PropTypes.string, PropTypes.arrayOf(PropTypes.string)]),
        requiredFeature: PropTypes.oneOfType([PropTypes.string, PropTypes.arrayOf(PropTypes.string)]),
        requiredRole: PropTypes.oneOfType([PropTypes.string, PropTypes.arrayOf(PropTypes.string)]),
        allowedContexts: PropTypes.arrayOf(PropTypes.string),
        defaultVisible: PropTypes.bool,
        defaultSize: PropTypes.string,
        defaultRect: PropTypes.shape({ x: PropTypes.number, y: PropTypes.number, w: PropTypes.number, h: PropTypes.number }),
        minW: PropTypes.number,
        minH: PropTypes.number,
        sizes: PropTypes.arrayOf(PropTypes.string),
        locked: PropTypes.bool,
        lockedVisibility: PropTypes.bool,
        lockedOrder: PropTypes.bool,
        lockedSize: PropTypes.bool,
        isAvailable: PropTypes.func,
        className: PropTypes.string,
    })).isRequired,
    defaultLayout: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
    permissionContext: PropTypes.object,
    storageIdentity: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    preferenceEndpoint: PropTypes.string,
    preferenceParams: PropTypes.object,
    remoteEnabled: PropTypes.bool,
    labels: PropTypes.object,
    className: PropTypes.string,
    editable: PropTypes.bool,
    emptyState: PropTypes.node,
    layoutMode: PropTypes.oneOf(['ordered', 'free']),
    gridRowHeight: PropTypes.number,
};

export default ConfigurableDashboard;
