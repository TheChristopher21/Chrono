/** @vitest-environment jsdom */
import React from 'react';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ConfigurableDashboard from '../ConfigurableDashboard.jsx';
import api from '../../../utils/api.js';

vi.mock('../../../utils/api.js', () => ({ default: { get: vi.fn(), put: vi.fn() } }));
const registry = [
    { id: 'alpha', title: 'Alpha', defaultRect: { x: 0, y: 0, w: 4, h: 2 }, component: <div>Alpha Inhalt</div> },
    { id: 'beta', title: 'Beta', defaultRect: { x: 0, y: 2, w: 4, h: 2 }, component: <div>Beta Inhalt</div> },
    { id: 'extra', title: 'Zusatzkennzahl', defaultVisible: false, defaultSize: 'S', defaultRect: { x: 0, y: 0, w: 3, h: 2 }, component: <div>Zusatz Inhalt</div> },
];
const storageKey = 'chrono.dashboard-layout.17.PMS.property%3A1';
const stored = () => JSON.parse(window.localStorage.getItem(storageKey)).layout;
const widget = (id) => document.querySelector(`[data-dashboard-widget="${id}"]`);
const deferred = () => {
    let resolve;
    let reject;
    const promise = new Promise((success, failure) => { resolve = success; reject = failure; });
    return { promise, resolve, reject };
};
const renderDashboard = (props = {}) => render(<ConfigurableDashboard context="PMS" scope="property:1" registry={registry}
    storageIdentity={17} preferenceParams={{ propertyId: 1 }} layoutMode="free" {...props} />);

describe('free dashboard editor', () => {
    beforeEach(() => {
        window.localStorage.clear();
        api.get.mockReset().mockResolvedValue({ data: { revision: 0, payload: {} } });
        api.put.mockReset().mockImplementation((_url, body) => Promise.resolve({ data: { revision: body.revision + 1, payload: body.payload } }));
        vi.stubGlobal('PointerEvent', class extends MouseEvent {
            constructor(type, props = {}) { super(type, props); this.pointerId = props.pointerId ?? 1; this.pointerType = props.pointerType || 'mouse'; }
        });
        vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() }));
    });
    afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });

    it('persists exact free positions remotely and restores them after remount', async () => {
        const user = userEvent.setup();
        const first = renderDashboard();
        await screen.findByText('Mit dem Benutzerkonto synchronisiert');
        await user.click(screen.getByRole('button', { name: 'Dashboard anpassen' }));
        fireEvent.change(screen.getByRole('spinbutton', { name: 'Spalte: Alpha' }), { target: { value: '7' } });
        fireEvent.change(screen.getByRole('spinbutton', { name: 'Zeile: Alpha' }), { target: { value: '11' } });
        await user.click(screen.getByRole('button', { name: 'Position übernehmen' }));
        expect(widget('alpha')).toHaveStyle({ gridColumn: '7 / span 4', gridRow: '11 / span 2' });
        await waitFor(() => expect(api.put).toHaveBeenCalled());
        const payload = api.put.mock.calls.at(-1)[1].payload;
        expect(payload.layouts['property:1'].widgets.find((item) => item.id === 'alpha')).toMatchObject({ x: 6, y: 10, w: 4, h: 2 });
        expect(api.put.mock.calls.at(-1)[2]).toEqual({ params: { propertyId: 1 } });
        first.unmount();
        api.get.mockResolvedValueOnce({ data: { revision: 1, payload } });
        renderDashboard();
        await screen.findByText('Mit dem Benutzerkonto synchronisiert');
        expect(widget('alpha')).toHaveAttribute('data-grid-y', '10');
        expect(stored().find((item) => item.id === 'alpha').x).toBe(6);
    });

    it('resizes by keyboard, previews collision chains and can undo a move or reset', async () => {
        const user = userEvent.setup();
        renderDashboard({ remoteEnabled: false });
        await user.click(screen.getByRole('button', { name: 'Dashboard anpassen' }));
        fireEvent.keyDown(screen.getByRole('button', { name: 'Alpha: Größe ändern' }), { key: 'ArrowDown' });
        expect(widget('alpha')).toHaveAttribute('data-grid-h', '3');
        expect(widget('beta')).toHaveAttribute('data-grid-y', '3');
        await user.click(screen.getByRole('button', { name: 'Rückgängig' }));
        expect(widget('alpha')).toHaveAttribute('data-grid-h', '2');
        expect(widget('beta')).toHaveAttribute('data-grid-y', '2');
        fireEvent.keyDown(screen.getByRole('button', { name: 'Alpha: Verschieben' }), { key: 'ArrowRight' });
        expect(widget('alpha')).toHaveAttribute('data-grid-x', '1');
        await user.click(screen.getByRole('button', { name: 'Standard wiederherstellen' }));
        expect(widget('alpha')).toHaveAttribute('data-grid-x', '0');
        await user.click(screen.getByRole('button', { name: 'Rückgängig' }));
        expect(widget('alpha')).toHaveAttribute('data-grid-x', '1');
    });

    it('has a searchable add/remove palette and restores removed positions with undo', async () => {
        const user = userEvent.setup();
        renderDashboard({ remoteEnabled: false });
        await user.click(screen.getByRole('button', { name: 'Dashboard anpassen' }));
        await user.type(screen.getByRole('searchbox', { name: 'Bereiche suchen' }), 'Zusatz');
        const palette = screen.getByLabelText('Verfügbare Bereiche');
        expect(within(palette).queryByRole('button', { name: 'Alpha: Entfernen' })).not.toBeInTheDocument();
        await user.click(within(palette).getByRole('button', { name: 'Zusatzkennzahl: Hinzufügen' }));
        expect(widget('extra')).toHaveAttribute('data-grid-x', '4');
        await user.click(within(palette).getByRole('button', { name: 'Zusatzkennzahl: Entfernen' }));
        expect(screen.queryByText('Zusatz Inhalt')).not.toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Rückgängig' }));
        expect(widget('extra')).toHaveAttribute('data-grid-x', '4');
    });

    it('shows pointer placement before saving, moves intersecting cards in the preview and cancels with Escape', async () => {
        const user = userEvent.setup();
        renderDashboard({ remoteEnabled: false });
        await user.click(screen.getByRole('button', { name: 'Dashboard anpassen' }));
        const grid = document.querySelector('.dashboard-free-grid');
        vi.spyOn(grid, 'getBoundingClientRect').mockReturnValue({ width: 1200, height: 720, x: 0, y: 0, top: 0, left: 0, right: 1200, bottom: 720 });
        const handle = screen.getByRole('button', { name: 'Beta: Verschieben' });
        fireEvent.pointerDown(handle, { button: 0, pointerId: 1, clientX: 100, clientY: 300, pointerType: 'touch' });
        fireEvent.pointerMove(handle, { pointerId: 1, clientX: 100, clientY: 156 });
        expect(screen.getByTestId('dashboard-placement-preview')).toHaveAttribute('data-valid', 'true');
        expect(widget('alpha')).toHaveStyle({ gridRow: '3 / span 2' });
        expect(window.localStorage.getItem(storageKey)).toBeNull();
        fireEvent.keyDown(handle, { key: 'Escape' });
        expect(screen.queryByTestId('dashboard-placement-preview')).not.toBeInTheDocument();
        expect(widget('alpha')).toHaveStyle({ gridRow: '1 / span 2' });
        fireEvent.pointerDown(handle, { button: 0, pointerId: 2, clientX: 100, clientY: 300 });
        fireEvent.pointerMove(handle, { pointerId: 2, clientX: 600, clientY: 516 });
        fireEvent.pointerUp(handle, { pointerId: 2 });
        expect(stored().find((item) => item.id === 'beta')).toMatchObject({ x: 5, y: 5 });
    });

    it('rejects a locked collision without persisting a partial rearrangement', async () => {
        const user = userEvent.setup();
        renderDashboard({ remoteEnabled: false, registry: registry.map((item) => item.id === 'beta' ? { ...item, lockedOrder: true } : item) });
        await user.click(screen.getByRole('button', { name: 'Dashboard anpassen' }));
        fireEvent.keyDown(screen.getByRole('button', { name: 'Alpha: Größe ändern' }), { key: 'ArrowDown' });
        expect(screen.getByRole('alert')).toHaveTextContent('gesperrten Bereich');
        expect(widget('alpha')).toHaveAttribute('data-grid-h', '2');
        expect(window.localStorage.getItem(storageKey)).toBeNull();
    });

    it('keeps desktop coordinates on narrow screens and supports the position form there', async () => {
        let listener;
        const media = { matches: false, addEventListener: (_event, fn) => { listener = fn; }, removeEventListener: vi.fn() };
        window.matchMedia.mockReturnValue(media);
        const user = userEvent.setup();
        renderDashboard({ remoteEnabled: false });
        await user.click(screen.getByRole('button', { name: 'Dashboard anpassen' }));
        act(() => { media.matches = true; listener(); });
        expect(screen.getByText(/Mobile Ansicht:/)).toBeInTheDocument();
        expect(widget('beta')).toHaveAttribute('data-grid-y', '2');
        expect(window.localStorage.getItem(storageKey)).toBeNull();
        fireEvent.change(screen.getByRole('spinbutton', { name: 'Zeile: Alpha' }), { target: { value: '15' } });
        await user.click(screen.getByRole('button', { name: 'Position übernehmen' }));
        act(() => { media.matches = false; listener(); });
        expect(widget('alpha')).toHaveAttribute('data-grid-y', '14');
        expect(stored().find((item) => item.id === 'alpha').y).toBe(14);
    });

    it('retries a revision conflict while preserving the operator’s exact local geometry', async () => {
        const user = userEvent.setup();
        api.get.mockResolvedValueOnce({ data: { revision: 0, payload: {} } }).mockResolvedValueOnce({ data: { revision: 2, payload: { layouts: { 'property:1': { widgets: [{ id: 'alpha', visible: true, order: 0, size: 'full', x: 0, y: 12, w: 12, h: 2 }] } } } } });
        api.put.mockRejectedValueOnce({ response: { status: 409 } });
        renderDashboard();
        await screen.findByText('Mit dem Benutzerkonto synchronisiert');
        await user.click(screen.getByRole('button', { name: 'Dashboard anpassen' }));
        fireEvent.keyDown(screen.getByRole('button', { name: 'Alpha: Verschieben' }), { key: 'ArrowRight' });
        await waitFor(() => expect(api.put).toHaveBeenCalledTimes(2));
        expect(api.put.mock.calls[1][1]).toMatchObject({ revision: 2, payload: { layouts: {
            'property:1': { widgets: expect.arrayContaining([expect.objectContaining({ id: 'alpha', x: 1 })]) },
        } } });
    });

    it.each([
        ['success', 'success'], ['success', 'failure'], ['failure', 'success'],
    ])('keeps saving through two queued requests (%s then %s) until the final result', async (firstResult, lastResult) => {
        const first = deferred();
        const last = deferred();
        api.put.mockImplementationOnce(() => first.promise).mockImplementationOnce(() => last.promise);
        const user = userEvent.setup();
        renderDashboard();
        await screen.findByText('Mit dem Benutzerkonto synchronisiert');
        await user.click(screen.getByRole('button', { name: 'Dashboard anpassen' }));
        const move = screen.getByRole('button', { name: 'Alpha: Verschieben' });
        fireEvent.keyDown(move, { key: 'ArrowRight' });
        fireEvent.keyDown(move, { key: 'ArrowRight' });
        expect(screen.getByText('Wird gespeichert …')).toBeInTheDocument();
        await waitFor(() => expect(api.put).toHaveBeenCalledTimes(1));
        await act(async () => {
            if (firstResult === 'success') first.resolve({ data: { revision: 1 } });
            else first.reject(new Error('temporarily offline'));
        });
        await waitFor(() => expect(api.put).toHaveBeenCalledTimes(2));
        expect(screen.getByText('Wird gespeichert …')).toBeInTheDocument();
        expect(screen.queryByText('Mit dem Benutzerkonto synchronisiert')).not.toBeInTheDocument();
        expect(screen.queryByText('Lokal auf diesem Gerät gespeichert')).not.toBeInTheDocument();
        expect(api.put.mock.calls[1][1].revision).toBe(firstResult === 'success' ? 1 : 0);
        await act(async () => {
            if (lastResult === 'success') last.resolve({ data: { revision: 2 } });
            else last.reject(new Error('offline'));
        });
        expect(await screen.findByText(lastResult === 'success' ? 'Mit dem Benutzerkonto synchronisiert' : 'Lokal auf diesem Gerät gespeichert')).toBeInTheDocument();
        expect(screen.queryByText('Wird gespeichert …')).not.toBeInTheDocument();
        expect(stored().find((item) => item.id === 'alpha').x).toBe(2);
    });

    it('does not acknowledge a pending write when the initial preferences GET finishes late', async () => {
        const load = deferred();
        const save = deferred();
        api.get.mockImplementationOnce(() => load.promise);
        api.put.mockImplementationOnce(() => save.promise);
        const user = userEvent.setup();
        renderDashboard();
        await user.click(screen.getByRole('button', { name: 'Dashboard anpassen' }));
        fireEvent.keyDown(screen.getByRole('button', { name: 'Alpha: Verschieben' }), { key: 'ArrowRight' });
        await waitFor(() => expect(api.put).toHaveBeenCalledTimes(1));
        await act(async () => load.resolve({ data: { revision: 0, payload: {} } }));
        expect(screen.getByText('Wird gespeichert …')).toBeInTheDocument();
        expect(widget('alpha')).toHaveAttribute('data-grid-x', '1');
        await act(async () => save.reject(new Error('offline')));
        expect(await screen.findByText('Lokal auf diesem Gerät gespeichert')).toBeInTheDocument();
    });

    it('does not apply the previous hotel’s save acknowledgement to the next hotel’s loading status', async () => {
        const save = deferred();
        const nextHotelLoad = deferred();
        api.get.mockResolvedValueOnce({ data: { revision: 0, payload: {} } }).mockImplementationOnce(() => nextHotelLoad.promise);
        api.put.mockImplementationOnce(() => save.promise);
        const user = userEvent.setup();
        const view = renderDashboard();
        await screen.findByText('Mit dem Benutzerkonto synchronisiert');
        await user.click(screen.getByRole('button', { name: 'Dashboard anpassen' }));
        fireEvent.keyDown(screen.getByRole('button', { name: 'Alpha: Verschieben' }), { key: 'ArrowRight' });
        await waitFor(() => expect(api.put).toHaveBeenCalledTimes(1));
        view.rerender(<ConfigurableDashboard context="PMS" scope="property:2" registry={registry}
            storageIdentity={17} preferenceParams={{ propertyId: 2 }} layoutMode="free" />);
        expect(screen.getByText('Einstellungen werden geladen')).toBeInTheDocument();
        await act(async () => save.resolve({ data: { revision: 1 } }));
        expect(screen.getByText('Einstellungen werden geladen')).toBeInTheDocument();
        expect(screen.queryByText('Mit dem Benutzerkonto synchronisiert')).not.toBeInTheDocument();
        await act(async () => nextHotelLoad.resolve({ data: { revision: 0, payload: {} } }));
        expect(await screen.findByText('Mit dem Benutzerkonto synchronisiert')).toBeInTheDocument();
        expect(widget('alpha')).toHaveAttribute('data-grid-x', '0');
    });

    it('keeps edit B durable when a hotel switch skips its queued PUT and retries B after loading server edit A', async () => {
        const firstSave = deferred();
        const resumedSave = deferred();
        let server = { revision: 0, payload: {} };
        api.get.mockImplementation((_url, options) => Promise.resolve({ data: options.params.propertyId === 1 ? server : { revision: 0, payload: {} } }));
        api.put.mockImplementationOnce(() => firstSave.promise).mockImplementationOnce(() => resumedSave.promise);
        const user = userEvent.setup();
        const view = renderDashboard();
        await screen.findByText('Mit dem Benutzerkonto synchronisiert');
        await user.click(screen.getByRole('button', { name: 'Dashboard anpassen' }));
        const move = screen.getByRole('button', { name: 'Alpha: Verschieben' });
        fireEvent.keyDown(move, { key: 'ArrowRight' });
        fireEvent.keyDown(move, { key: 'ArrowRight' });
        await waitFor(() => expect(api.put).toHaveBeenCalledTimes(1));
        const editB = JSON.parse(window.localStorage.getItem(storageKey));
        expect(editB).toMatchObject({ dirty: true, editId: expect.any(String) });
        expect(editB.layout.find((item) => item.id === 'alpha').x).toBe(2);

        view.rerender(<ConfigurableDashboard context="PMS" scope="property:2" registry={registry}
            storageIdentity={17} preferenceParams={{ propertyId: 2 }} layoutMode="free" />);
        await screen.findByText('Mit dem Benutzerkonto synchronisiert');
        server = { revision: 1, payload: api.put.mock.calls[0][1].payload };
        await act(async () => firstSave.resolve({ data: server }));
        expect(api.put).toHaveBeenCalledTimes(1);
        expect(JSON.parse(window.localStorage.getItem(storageKey))).toEqual(editB);
        expect(widget('alpha')).toHaveAttribute('data-grid-x', '0');

        view.rerender(<ConfigurableDashboard context="PMS" scope="property:1" registry={registry}
            storageIdentity={17} preferenceParams={{ propertyId: 1 }} layoutMode="free" />);
        await waitFor(() => expect(api.put).toHaveBeenCalledTimes(2));
        expect(screen.getByText('Wird gespeichert …')).toBeInTheDocument();
        expect(widget('alpha')).toHaveAttribute('data-grid-x', '2');
        expect(api.put.mock.calls[1][2]).toEqual({ params: { propertyId: 1 } });
        expect(api.put.mock.calls[1][1]).toMatchObject({ revision: 1, payload: { layouts: {
            'property:1': { widgets: expect.arrayContaining([expect.objectContaining({ id: 'alpha', x: 2 })]) },
        } } });
        expect(JSON.parse(window.localStorage.getItem(storageKey)).dirty).toBe(true);
        await act(async () => resumedSave.resolve({ data: { revision: 2, payload: api.put.mock.calls[1][1].payload } }));
        expect(await screen.findByText('Mit dem Benutzerkonto synchronisiert')).toBeInTheDocument();
        expect(JSON.parse(window.localStorage.getItem(storageKey))).toMatchObject({ dirty: false, editId: editB.editId });
        expect(stored().find((item) => item.id === 'alpha').x).toBe(2);
    });

    it('retries failed local edits after remount only for the same user identity', async () => {
        const user = userEvent.setup();
        api.put.mockRejectedValueOnce(new Error('offline'));
        const first = renderDashboard();
        await screen.findByText('Mit dem Benutzerkonto synchronisiert');
        await user.click(screen.getByRole('button', { name: 'Dashboard anpassen' }));
        fireEvent.keyDown(screen.getByRole('button', { name: 'Alpha: Verschieben' }), { key: 'ArrowRight' });
        await screen.findByText('Lokal auf diesem Gerät gespeichert');
        const dirty = JSON.parse(window.localStorage.getItem(storageKey));
        expect(dirty.dirty).toBe(true);
        first.unmount();

        const differentUser = renderDashboard({ storageIdentity: 18 });
        await screen.findByText('Mit dem Benutzerkonto synchronisiert');
        expect(widget('alpha')).toHaveAttribute('data-grid-x', '0');
        expect(api.put).toHaveBeenCalledTimes(1);
        expect(JSON.parse(window.localStorage.getItem(storageKey))).toEqual(dirty);
        differentUser.unmount();

        const retry = deferred();
        api.put.mockImplementationOnce(() => retry.promise);
        renderDashboard();
        await waitFor(() => expect(api.put).toHaveBeenCalledTimes(2));
        expect(widget('alpha')).toHaveAttribute('data-grid-x', '1');
        expect(screen.getByText('Wird gespeichert …')).toBeInTheDocument();
        await act(async () => retry.resolve({ data: { revision: 1, payload: api.put.mock.calls[1][1].payload } }));
        expect(await screen.findByText('Mit dem Benutzerkonto synchronisiert')).toBeInTheDocument();
        expect(JSON.parse(window.localStorage.getItem(storageKey)).dirty).toBe(false);
    });
});
