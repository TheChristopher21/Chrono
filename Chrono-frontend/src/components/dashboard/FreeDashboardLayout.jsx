import { useEffect, useId, useRef, useState } from 'react';
import {
    FREE_GRID_COLUMNS, FREE_GRID_ROWS, FREE_GRID_MAX_HEIGHT, FREE_GRID_ROW_HEIGHT,
    showFreeDashboardWidget, updateFreeDashboardRect, widgetMinimum,
} from './freeDashboardLayout.js';

const rectOf = ({ x, y, w, h }) => ({ x, y, w, h });
const bound = (value, min, max) => Math.max(min, Math.min(max, value));
const rectStyle = (item) => ({ gridColumn: `${item.x + 1} / span ${item.w}`, gridRow: `${item.y + 1} / span ${item.h}` });
const coordinates = (rect) => `Spalte ${rect.x + 1}, Zeile ${rect.y + 1}, Breite ${rect.w}, Höhe ${rect.h}`;

function PositionEditor({ item, widget, onApply }) {
    const [draft, setDraft] = useState(rectOf(item));
    const min = widgetMinimum(widget);
    return (
        <form className="dashboard-free-position" onSubmit={(event) => { event.preventDefault(); onApply(draft); }}>
            {[
                ['x', 'Spalte', 1, FREE_GRID_COLUMNS, widget.locked || widget.lockedOrder],
                ['y', 'Zeile', 1, FREE_GRID_ROWS, widget.locked || widget.lockedOrder],
                ['w', 'Breite', min.w, FREE_GRID_COLUMNS, widget.locked || widget.lockedSize],
                ['h', 'Höhe', min.h, FREE_GRID_MAX_HEIGHT, widget.locked || widget.lockedSize],
            ].map(([key, title, minimum, maximum, disabled]) => (
                <label key={key}>
                    <span>{title}</span>
                    <input type="number" min={minimum} max={maximum} step="1" required disabled={disabled}
                        aria-label={`${title}: ${widget.title}`}
                        value={draft[key] === '' ? '' : draft[key] + (key === 'x' || key === 'y' ? 1 : 0)}
                        onChange={(event) => setDraft((current) => ({ ...current,
                            [key]: event.target.value === '' ? '' : Number(event.target.value) - (key === 'x' || key === 'y' ? 1 : 0),
                        }))} />
                </label>
            ))}
            <button type="submit" disabled={widget.locked || (widget.lockedOrder && widget.lockedSize)}>Position übernehmen</button>
        </form>
    );
}

/** Opt-in canvas. The ordered dashboard remains a separate rendering path. */
export default function FreeDashboardLayout({
    context, scope, className, editable, registry, layout, updateLayout, resetLayout,
    persistenceStatus, persistenceLabel, labels, emptyState, renderWidget, gridRowHeight = FREE_GRID_ROW_HEIGHT,
}) {
    const editorId = useId();
    const instructionsId = useId();
    const [isEditing, setIsEditing] = useState(false);
    const [search, setSearch] = useState('');
    const [selectedId, setSelectedId] = useState(null);
    const [announcement, setAnnouncement] = useState('');
    const [error, setError] = useState('');
    const [undoStack, setUndoStack] = useState([]);
    const [interaction, setInteraction] = useState(null);
    const [compact, setCompact] = useState(() => window.matchMedia?.('(max-width: 768px)').matches ?? false);
    const gridRef = useRef(null);
    const interactionRef = useRef(null);
    const layoutRef = useRef(layout);
    layoutRef.current = layout;
    const registryRef = useRef(registry);
    registryRef.current = registry;
    const widgets = new Map(registry.map((widget) => [String(widget.id), widget]));
    const visible = layout.filter((item) => item.visible && widgets.has(item.id)).sort((a, b) => a.y - b.y || a.x - b.x || a.order - b.order);
    const selected = visible.find((item) => item.id === selectedId) || visible[0];
    const palette = layout.filter((item) => {
        const widget = widgets.get(item.id);
        return widget && `${widget.title} ${widget.description || ''}`.toLocaleLowerCase().includes(search.toLocaleLowerCase());
    });
    const rows = Math.min(FREE_GRID_ROWS, Math.max(isEditing ? 6 : 1,
        ...visible.map((item) => item.y + item.h), interaction ? interaction.preview.y + interaction.preview.h : 0) + (isEditing ? 4 : 0));

    useEffect(() => {
        const media = window.matchMedia?.('(max-width: 768px)');
        if (!media) return undefined;
        const change = () => setCompact(media.matches);
        change();
        media.addEventListener?.('change', change);
        return () => media.removeEventListener?.('change', change);
    }, []);

    const cancelInteraction = () => { interactionRef.current = null; setInteraction(null); };
    useEffect(() => { if (compact) cancelInteraction(); }, [compact]);
    const selectForEditing = (id) => {
        setSelectedId(id);
        if (compact) document.getElementById(editorId)?.scrollIntoView?.({ block: 'start' });
    };

    const commit = (next, message) => {
        if (!next) {
            setError('Diese Position würde einen gesperrten Bereich überlagern oder das Raster überschreiten. Bitte wähle eine andere Position.');
            return false;
        }
        setError('');
        if (JSON.stringify(next) !== JSON.stringify(layoutRef.current)) {
            const previous = layoutRef.current;
            setUndoStack((stack) => [...stack.slice(-29), previous]);
            updateLayout(next);
        }
        setAnnouncement(message);
        return true;
    };

    const applyRect = (id, rect) => {
        const widget = widgets.get(id);
        const item = layoutRef.current.find((entry) => entry.id === id);
        if (!widget || !item || widget.locked) return;
        if (widget.lockedOrder && (rect.x !== item.x || rect.y !== item.y)) return;
        if (widget.lockedSize && (rect.w !== item.w || rect.h !== item.h)) return;
        commit(updateFreeDashboardRect(layoutRef.current, id, rect, registryRef.current), `${widget.title}: ${coordinates(rect)}`);
    };

    const toggleWidget = (item) => {
        const widget = widgets.get(item.id);
        if (widget.locked || widget.lockedVisibility) return;
        const next = item.visible
            ? layoutRef.current.map((entry) => entry.id === item.id ? { ...entry, visible: false } : entry)
            : showFreeDashboardWidget(layoutRef.current, item.id);
        if (commit(next, `${widget.title}: ${item.visible ? labels.hide : labels.show}`) && !item.visible) setSelectedId(item.id);
    };

    const previewPointer = (clientX, clientY) => {
        const gesture = interactionRef.current;
        if (!gesture) return;
        gesture.clientX = clientX;
        gesture.clientY = clientY;
        const dx = Math.round((clientX - gesture.startX + window.scrollX - gesture.scrollX) / gesture.stepX);
        const dy = Math.round((clientY - gesture.startY + window.scrollY - gesture.scrollY) / gesture.stepY);
        const { rect, min } = gesture;
        const preview = gesture.kind === 'move'
            ? { ...rect, x: bound(rect.x + dx, 0, FREE_GRID_COLUMNS - rect.w), y: bound(rect.y + dy, 0, FREE_GRID_ROWS - rect.h) }
            : { ...rect, w: bound(rect.w + dx, min.w, FREE_GRID_COLUMNS - rect.x), h: bound(rect.h + dy, min.h, Math.min(FREE_GRID_MAX_HEIGHT, FREE_GRID_ROWS - rect.y)) };
        const planned = updateFreeDashboardRect(layoutRef.current, gesture.id, preview, registryRef.current);
        const valid = Boolean(planned);
        if (JSON.stringify(preview) !== JSON.stringify(gesture.preview) || gesture.valid !== valid) {
            const next = { ...gesture, preview, valid, planned };
            interactionRef.current = next;
            setInteraction(next);
        }
    };

    useEffect(() => {
        if (!interaction) return undefined;
        let frame;
        const tick = () => {
            const gesture = interactionRef.current;
            if (!gesture) return;
            const direction = gesture.clientY < 64 ? -1 : gesture.clientY > window.innerHeight - 64 ? 1 : 0;
            if (direction) {
                window.scrollBy(0, direction * 10);
                previewPointer(gesture.clientX, gesture.clientY);
            }
            frame = window.requestAnimationFrame(tick);
        };
        frame = window.requestAnimationFrame(tick);
        return () => window.cancelAnimationFrame(frame);
        // A gesture keeps its initial geometry; current layout and pointer coordinates live in refs.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [Boolean(interaction)]);

    const startPointer = (event, item, kind) => {
        const widget = widgets.get(item.id);
        if (compact || event.button !== 0 || widget.locked || (kind === 'move' ? widget.lockedOrder : widget.lockedSize)) return;
        const grid = gridRef.current;
        if (!grid) return;
        event.preventDefault();
        event.currentTarget.focus({ preventScroll: true });
        event.currentTarget.setPointerCapture?.(event.pointerId);
        const computed = window.getComputedStyle(grid);
        const gapX = parseFloat(computed.columnGap) || 0;
        const gapY = parseFloat(computed.rowGap) || 0;
        const next = {
            id: item.id, kind, rect: rectOf(item), preview: rectOf(item), valid: true, min: widgetMinimum(widget),
            pointerId: event.pointerId, startX: event.clientX, startY: event.clientY,
            clientX: event.clientX, clientY: event.clientY, scrollX: window.scrollX, scrollY: window.scrollY,
            stepX: (grid.getBoundingClientRect().width + gapX) / FREE_GRID_COLUMNS, stepY: gridRowHeight + gapY,
        };
        if (next.stepX <= 0) return;
        setError('');
        setSelectedId(item.id);
        interactionRef.current = next;
        setInteraction(next);
    };

    const endPointer = (event) => {
        const gesture = interactionRef.current;
        if (!gesture || gesture.pointerId !== event.pointerId) return;
        applyRect(gesture.id, gesture.preview);
        cancelInteraction();
    };

    const keyboardRect = (event, item, kind) => {
        if (event.key === 'Escape') { cancelInteraction(); return; }
        const direction = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] }[event.key];
        if (!direction) return;
        event.preventDefault();
        const [dx, dy] = direction;
        const rect = rectOf(item);
        applyRect(item.id, kind === 'resize' || event.shiftKey
            ? { ...rect, w: rect.w + dx, h: rect.h + dy }
            : { ...rect, x: rect.x + dx, y: rect.y + dy });
    };

    const pointerHandlers = (item, kind) => ({
        onPointerDown: (event) => startPointer(event, item, kind),
        onPointerMove: (event) => {
            if (interactionRef.current?.pointerId === event.pointerId) previewPointer(event.clientX, event.clientY);
        },
        onPointerUp: endPointer,
        onPointerCancel: cancelInteraction,
        onLostPointerCapture: cancelInteraction,
        onKeyDown: (event) => keyboardRect(event, item, kind),
    });

    return (
        <section className={`configurable-dashboard dashboard-free ${isEditing ? 'is-editing' : ''} ${className}`.trim()}
            data-dashboard-context={context} data-dashboard-scope={scope} data-dashboard-layout="free">
            {editable && <div className="dashboard-config-toolbar">
                <button type="button" className="dashboard-config-primary" aria-expanded={isEditing} aria-controls={editorId}
                    onClick={() => { cancelInteraction(); setIsEditing((value) => !value); setError(''); }}>
                    {isEditing ? labels.finish : labels.customize}
                </button>
                {isEditing && <>
                    <button type="button" className="dashboard-config-secondary" disabled={!undoStack.length} onClick={() => {
                        const previous = undoStack.at(-1);
                        if (!previous) return;
                        cancelInteraction(); updateLayout(previous); setUndoStack((stack) => stack.slice(0, -1)); setError(''); setAnnouncement('Letzte Layoutänderung rückgängig gemacht.');
                    }}>Rückgängig</button>
                    <button type="button" className="dashboard-config-secondary" onClick={() => {
                        const previous = layoutRef.current;
                        cancelInteraction(); setUndoStack((stack) => [...stack.slice(-29), previous]); resetLayout(); setError(''); setAnnouncement(labels.reset);
                    }}>{labels.reset}</button>
                </>}
                <span className={`dashboard-persistence-status is-${persistenceStatus}`} role="status">{persistenceLabel}</span>
            </div>}

            {isEditing && <aside id={editorId} className="dashboard-config-panel dashboard-free-editor" aria-label={labels.configurationTitle}>
                <div className="dashboard-config-panel-heading">
                    <div><h3>{labels.configurationTitle}</h3><p>{visible.length} Bereiche auf deinem Dashboard · 12 Spalten</p></div>
                    <input type="search" aria-label="Bereiche suchen" placeholder="Bereich suchen …" value={search} onChange={(event) => setSearch(event.target.value)} />
                </div>
                <div className="dashboard-free-palette" aria-label="Verfügbare Bereiche">
                    {palette.map((item) => {
                        const widget = widgets.get(item.id);
                        return <button type="button" key={item.id} className={item.visible ? 'is-added' : ''}
                            aria-pressed={item.visible} aria-label={`${widget.title}: ${item.visible ? 'Entfernen' : 'Hinzufügen'}`}
                            title={widget.description} disabled={widget.locked || widget.lockedVisibility} onClick={() => toggleWidget(item)}>
                            <span aria-hidden="true">{item.visible ? '✓' : '+'}</span> {widget.title}
                        </button>;
                    })}
                    {!palette.length && <p>Kein passender Bereich gefunden.</p>}
                </div>
                <div className="dashboard-free-inspector">
                    {selected && <>
                        <label className="dashboard-free-selection"><span>Bereich positionieren</span>
                            <select aria-label="Bereich positionieren" value={selected.id} onChange={(event) => { setSelectedId(event.target.value); setError(''); }}>
                                {visible.map((item) => <option key={item.id} value={item.id}>{widgets.get(item.id).title}</option>)}
                            </select>
                        </label>
                        <PositionEditor key={`${selected.id}:${selected.x}:${selected.y}:${selected.w}:${selected.h}`} item={selected}
                            widget={widgets.get(selected.id)} onApply={(rect) => applyRect(selected.id, rect)} />
                    </>}
                </div>
                <p id={instructionsId} className="dashboard-free-help">{compact
                    ? 'Mobile Ansicht: Bereiche werden untereinander angezeigt. Spalte, Zeile, Breite und Höhe ändern dein Desktoplayout.'
                    : 'Am Griff frei ins Raster ziehen. Die Ecke verändert die Größe. Pfeiltasten am Griff verschieben, Umschalt + Pfeiltasten ändern die Größe. Betroffene Bereiche weichen nach unten aus; gesperrte bleiben fest. Escape bricht Ziehen ab.'}</p>
                {error && <p className="dashboard-free-error" role="alert">{error}</p>}
            </aside>}

            <div ref={gridRef} className="dashboard-widget-grid dashboard-free-grid"
                style={{ '--dashboard-free-row': `${gridRowHeight}px`, '--dashboard-free-rows': rows }}>
                {visible.map((item) => {
                    const widget = widgets.get(item.id);
                    const moving = interaction?.id === item.id;
                    return <section key={item.id} className={`dashboard-widget dashboard-free-widget ${widget.className || ''} ${moving ? 'is-dragging' : ''} ${isEditing && selected?.id === item.id ? 'is-selected' : ''}`.trim()}
                        style={rectStyle(interaction?.valid ? interaction.planned?.find((entry) => entry.id === item.id) || item : item)} data-dashboard-widget={item.id} aria-label={widget.title}
                        data-grid-x={item.x} data-grid-y={item.y} data-grid-w={item.w} data-grid-h={item.h}>
                        {isEditing && <div className="dashboard-widget-edit-bar">
                            <button type="button" className="dashboard-free-move" aria-label={`${widget.title}: Verschieben`}
                                aria-describedby={instructionsId} title={compact ? 'Position über die Felder einstellen' : 'Ziehen oder Pfeiltasten verwenden'}
                                disabled={widget.locked || widget.lockedOrder} {...pointerHandlers(item, 'move')} onClick={() => selectForEditing(item.id)}>
                                <span aria-hidden="true">⠿</span>
                            </button>
                            <button type="button" className="dashboard-free-title" onClick={() => selectForEditing(item.id)}
                                aria-label={`${widget.title}: Position bearbeiten`}>{widget.title}</button>
                            <button type="button" aria-label={`${widget.title}: Entfernen`} title="Bereich entfernen"
                                disabled={widget.locked || widget.lockedVisibility} onClick={() => toggleWidget(item)}>×</button>
                        </div>}
                        <div className="dashboard-widget-content">{renderWidget(widget)}</div>
                        {isEditing && <button type="button" className="dashboard-free-resize" aria-label={`${widget.title}: Größe ändern`}
                            aria-describedby={instructionsId} title="Ziehen oder mit Pfeiltasten die Größe ändern"
                            disabled={widget.locked || widget.lockedSize} onClick={() => selectForEditing(item.id)} {...pointerHandlers(item, 'resize')}><span aria-hidden="true">↘</span></button>}
                    </section>;
                })}
                {isEditing && interaction && <div className={`dashboard-free-preview ${interaction.valid ? 'is-valid' : 'is-blocked'}`}
                    style={rectStyle(interaction.preview)} data-testid="dashboard-placement-preview" data-valid={interaction.valid} aria-hidden="true">
                    <span>{interaction.valid ? coordinates(interaction.preview) : 'Position gesperrt'}</span>
                </div>}
                {isEditing && !visible.length && <p className="dashboard-free-placeholder">Füge oben einen Bereich hinzu und platziere ihn frei im Raster.</p>}
            </div>
            {!visible.length && !isEditing && <div className="dashboard-config-empty">{emptyState || labels.empty}</div>}
            <span className="sr-only" role="status" aria-live="polite" aria-atomic="true">{announcement}</span>
        </section>
    );
}
