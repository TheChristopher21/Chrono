import { describe, expect, it } from 'vitest';
import {
    canPlaceDashboardRect, dashboardRectsOverlap, normalizeFreeDashboardLayout,
    showFreeDashboardWidget, updateFreeDashboardRect,
} from '../freeDashboardLayout.js';
import { createDefaultDashboardLayout, normalizeDashboardLayout } from '../../../hooks/useDashboardPreferences.js';

const entry = (id, x, y, w = 4, h = 2, order = 0) => ({ id, x, y, w, h, order, visible: true, size: 'M' });

describe('free dashboard layout', () => {
    it('migrates legacy size and order into non-overlapping rectangles without changing the ordered mode', () => {
        const registry = [{ id: 'alpha', defaultSize: 'full' }, { id: 'beta', defaultSize: 'M' }];
        const old = [{ id: 'beta', order: 0, size: 'M', visible: true }, { id: 'alpha', order: 1, size: 'full', visible: true }];
        expect(normalizeDashboardLayout(old, registry)).toEqual(old);
        expect(normalizeDashboardLayout(old, registry, [], { layoutMode: 'free' })).toEqual([
            { ...old[0], x: 0, y: 0, w: 6, h: 4 }, { ...old[1], x: 0, y: 4, w: 12, h: 4 },
        ]);
    });

    it('keeps explicit gaps and saved geometry when registry defaults change', () => {
        const registry = [{ id: 'alpha', defaultRect: { x: 0, y: 0, w: 12, h: 4 } }, { id: 'beta' }];
        const saved = [entry('alpha', 2, 8, 4, 3), entry('beta', 8, 20, 4, 6, 1)];
        expect(normalizeDashboardLayout(saved, registry, [], { layoutMode: 'free' })).toEqual(saved);
        expect(createDefaultDashboardLayout(registry, [{ id: 'alpha', x: 6, y: 10, w: 6, h: 3 }], { layoutMode: 'free' })[0])
            .toMatchObject({ x: 6, y: 10, w: 6, h: 3 });
    });

    it('allows adjacent rectangles and rejects edges outside the bounded canvas', () => {
        const layout = [entry('first', 0, 0)];
        expect(canPlaceDashboardRect({ x: 4, y: 0, w: 8, h: 2 }, layout)).toBe(true);
        expect(canPlaceDashboardRect({ x: 0, y: 2, w: 12, h: 2 }, layout)).toBe(true);
        expect(canPlaceDashboardRect({ x: 11, y: 2, w: 2, h: 2 }, layout)).toBe(false);
        expect(canPlaceDashboardRect({ x: 0, y: 199, w: 2, h: 2 }, layout)).toBe(false);
    });

    it('moves only the collision chain downward and retains unrelated gaps', () => {
        const layout = [entry('move', 0, 8), entry('first', 0, 0), entry('second', 0, 2), entry('apart', 8, 14)];
        const result = updateFreeDashboardRect(layout, 'move', { x: 0, y: 0, w: 4, h: 2 });
        expect(result.map(({ id, x, y }) => ({ id, x, y }))).toEqual([
            { id: 'move', x: 0, y: 0 }, { id: 'first', x: 0, y: 2 }, { id: 'second', x: 0, y: 4 }, { id: 'apart', x: 8, y: 14 },
        ]);
        result.forEach((item, index) => result.slice(index + 1).forEach((other) => expect(dashboardRectsOverlap(item, other)).toBe(false)));
        expect(layout[1].y).toBe(0);
    });

    it('previews resize cascades and rejects the entire action when it reaches a locked card or row 200', () => {
        const layout = [entry('grow', 0, 0), entry('next', 0, 2), entry('locked', 0, 4)];
        expect(updateFreeDashboardRect(layout, 'grow', { x: 0, y: 0, w: 4, h: 3 }, [{ id: 'locked', lockedOrder: true }])).toBeNull();
        expect(layout.map((item) => item.y)).toEqual([0, 2, 4]);
        const bottom = [entry('grow', 0, 194), entry('next', 0, 198)];
        expect(updateFreeDashboardRect(bottom, 'grow', { x: 0, y: 194, w: 4, h: 5 })).toBeNull();
        expect(updateFreeDashboardRect(layout, 'grow', { x: 0, y: 0, w: 4, h: 3 })[2].y).toBe(5);
    });

    it('respects minimum sizes and locked properties', () => {
        const layout = [entry('fixed', 0, 0)];
        expect(updateFreeDashboardRect(layout, 'fixed', { x: 0, y: 0, w: 2, h: 2 }, [{ id: 'fixed', minW: 3 }])).toBeNull();
        expect(updateFreeDashboardRect(layout, 'fixed', { x: 1, y: 0, w: 4, h: 2 }, [{ id: 'fixed', lockedOrder: true }])).toBeNull();
        expect(updateFreeDashboardRect(layout, 'fixed', { x: 0, y: 0, w: 6, h: 2 }, [{ id: 'fixed', lockedSize: true }])).toBeNull();
    });

    it('re-adds hidden widgets in free space without moving an existing card', () => {
        const layout = [entry('shown', 0, 0), { ...entry('hidden', 0, 0), visible: false }];
        const result = showFreeDashboardWidget(layout, 'hidden');
        expect(result[0]).toEqual(layout[0]);
        expect(result[1]).toMatchObject({ visible: true, x: 4, y: 0 });
    });

    it('repairs malformed or overlapping persisted positions while leaving valid placements intact', () => {
        const result = normalizeFreeDashboardLayout([entry('first', 0, 8), entry('overlap', 0, 8, 4, 2, 1), entry('bad', -4, -1, 18, 60, 2)]);
        expect(result[0]).toMatchObject({ x: 0, y: 8 });
        result.forEach((item) => expect(canPlaceDashboardRect(item, result, item.id)).toBe(true));
        expect(result[2]).toMatchObject({ w: 12, h: 24 });
    });
});
