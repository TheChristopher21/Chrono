export const FREE_GRID_COLUMNS = 12;
export const FREE_GRID_ROWS = 200;
export const FREE_GRID_MAX_HEIGHT = 24;
export const FREE_GRID_ROW_HEIGHT = 72;

export const widthForDashboardSize = (size) => ({ S: 3, M: 6, L: 9, full: 12 }[size] || 12);
const integer = (value, fallback) => Number.isFinite(value) ? Math.round(value) : fallback;
const bound = (value, min, max) => Math.min(max, Math.max(min, value));

export const widgetMinimum = (widget = {}) => ({
    w: bound(integer(widget.minW, 2), 1, FREE_GRID_COLUMNS),
    h: bound(integer(widget.minH, 2), 1, FREE_GRID_MAX_HEIGHT),
});

export const hasDashboardRect = (item) => ['x', 'y', 'w', 'h'].every((key) => Number.isInteger(item?.[key]));

export const dashboardRectsOverlap = (a, b) => (
    a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y
);

export const canPlaceDashboardRect = (rect, layout, excludedId) => (
    hasDashboardRect(rect)
    && rect.x >= 0 && rect.y >= 0 && rect.w >= 1 && rect.h >= 1
    && rect.w <= FREE_GRID_COLUMNS && rect.h <= FREE_GRID_MAX_HEIGHT
    && rect.x + rect.w <= FREE_GRID_COLUMNS && rect.y + rect.h <= FREE_GRID_ROWS
    && !layout.some((item) => item.visible && item.id !== excludedId && dashboardRectsOverlap(rect, item))
);

export const firstFreeDashboardRect = (rect, layout, excludedId) => {
    if (canPlaceDashboardRect(rect, layout, excludedId)) return rect;
    for (let y = 0; y <= FREE_GRID_ROWS - rect.h; y += 1) {
        for (let x = 0; x <= FREE_GRID_COLUMNS - rect.w; x += 1) {
            const candidate = { x, y, w: rect.w, h: rect.h };
            if (canPlaceDashboardRect(candidate, layout, excludedId)) return candidate;
        }
    }
    return null;
};

/** Repairs legacy/corrupt geometry once. Valid positions, including empty rows, never compact. */
export const normalizeFreeDashboardLayout = (layout, registry = []) => {
    const widgets = new Map(registry.map((widget) => [String(widget.id), widget]));
    const placed = [];
    // Reserve valid explicit positions before migrating old entries or adding newly available widgets.
    const pending = [];
    layout.forEach((item) => {
        const widget = widgets.get(item.id) || {};
        const min = widgetMinimum(widget);
        const w = bound(integer(item.w, widthForDashboardSize(item.size)), min.w, FREE_GRID_COLUMNS);
        const h = bound(integer(item.h, widget.defaultRect?.h ?? 4), min.h, FREE_GRID_MAX_HEIGHT);
        const rect = {
            x: bound(integer(item.x, 0), 0, FREE_GRID_COLUMNS - w),
            y: bound(integer(item.y, 0), 0, FREE_GRID_ROWS - h),
            w, h,
        };
        const entry = { ...item, ...rect };
        if (!item.visible || (hasDashboardRect(item) && canPlaceDashboardRect(rect, placed))) placed.push(entry);
        else pending.push(entry);
    });
    pending.forEach((item) => {
        const rect = firstFreeDashboardRect(item, placed);
        // A completely full canvas must not create overlapping or out-of-bounds cards.
        placed.push(rect ? { ...item, ...rect } : { ...item, visible: false });
    });
    return placed.sort((a, b) => a.order - b.order);
};

/** Move only the intersecting chain down. Unrelated empty space is never compacted. */
export const updateFreeDashboardRect = (layout, id, rect, registry = []) => {
    const widgets = new Map(registry.map((widget) => [String(widget.id), widget]));
    const widget = widgets.get(id) || {};
    const item = layout.find((entry) => entry.id === id);
    const min = widgetMinimum(widget);
    if (!item || rect.w < min.w || rect.h < min.h || !canPlaceDashboardRect(rect, [])) return null;
    if (widget.locked || (widget.lockedOrder && (item.x !== rect.x || item.y !== rect.y))
        || (widget.lockedSize && (item.w !== rect.w || item.h !== rect.h))) return null;
    const planned = layout.map((entry) => entry.id === id ? { ...entry, ...rect } : { ...entry });
    const queue = [id];
    while (queue.length) {
        const movingId = queue.shift();
        const moving = planned.find((entry) => entry.id === movingId);
        const collisions = planned.filter((entry) => entry.visible && entry.id !== moving.id && dashboardRectsOverlap(moving, entry))
            .sort((a, b) => a.y - b.y || a.x - b.x || a.order - b.order);
        for (const other of collisions) {
            const otherWidget = widgets.get(other.id) || {};
            if (other.id === id || otherWidget.locked || otherWidget.lockedOrder) return null;
            other.y = moving.y + moving.h;
            if (other.y + other.h > FREE_GRID_ROWS) return null;
            queue.push(other.id);
        }
    }
    return planned;
};

export const showFreeDashboardWidget = (layout, id) => {
    const item = layout.find((entry) => entry.id === id);
    if (!item) return null;
    const rect = firstFreeDashboardRect(item, layout, id);
    return rect ? layout.map((entry) => entry.id === id ? { ...entry, ...rect, visible: true } : entry) : null;
};
