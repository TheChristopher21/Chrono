/** @vitest-environment jsdom */
import React from 'react';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
const api = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn(), post: vi.fn() }));
const refresh = vi.hoisted(() => ({ callback: null }));
vi.mock('../../utils/api.js', () => ({ default: api }));
vi.mock('./usePmsLiveRefresh.js', () => ({ default: (callback) => { refresh.callback = callback; } }));
import PmsBeds24Panel from './PmsBeds24Panel.jsx';
const property = { id: 2, currencyCode: 'CHF', timezone: 'Europe/Zurich' };
const saved = { externalPropertyId: 41, secretReference: 'env:HOTEL_BEDS24', enabled: true, version: 3, workerEnabled: false,
    mappings: [{ ratePlanId: 3, externalRoomId: 71, priceSlot: 1 }], jobs: [], rates: [{ id: 3, name: 'Flexibel', roomType: 'Doppelzimmer', includedAdults: 2 }] };
beforeEach(() => {
    vi.clearAllMocks();
    api.get.mockResolvedValue({ data: saved });
    api.put.mockResolvedValue({ data: { ...saved, version: 4 } });
    api.post.mockResolvedValue({ data: {} });
});
afterEach(cleanup);
describe('Beds24 hotel integration', () => {
    it('preserves unsaved mappings through live journal updates and sends the version guard', async () => {
        render(<PmsBeds24Panel property={property} canManage canManageSettings />);
        const input = await screen.findByLabelText('Beds24-Zimmer-ID 1');
        fireEvent.click(screen.getByText('Verbindung & Ratenzuordnung'));
        fireEvent.change(input, { target: { value: '72' } });
        await act(async () => { await refresh.callback(); });
        expect(input.value).toBe('72');
        fireEvent.submit(screen.getByRole('button', { name: 'Zuordnung speichern' }).closest('form'));
        await waitFor(() => expect(api.put).toHaveBeenCalledWith('/api/pms/properties/2/beds24/settings', expect.objectContaining({ version: 3, mappings: [{ ratePlanId: 3, externalRoomId: 72, priceSlot: 1 }] })));
    });
    it('retries an uncertain publication with the same immutable request and dates', async () => {
        api.post.mockRejectedValueOnce(new Error('connection closed')).mockResolvedValueOnce({ data: {} });
        render(<PmsBeds24Panel property={property} canManage canManageSettings />);
        await screen.findByLabelText('Beds24-Hotel-ID');
        fireEvent.submit(screen.getByRole('button', { name: 'Aktuellen Kalenderstand übertragen' }).closest('form'));
        await screen.findByRole('alert');
        expect(screen.getByLabelText('Von').disabled).toBe(true);
        const first = api.post.mock.calls[0][1];
        expect(first.requestId).toBeTruthy();
        fireEvent.submit(screen.getByRole('button', { name: 'Denselben Kalenderauftrag erneut prüfen' }).closest('form'));
        await waitFor(() => expect(api.post).toHaveBeenCalledTimes(2));
        expect(api.post.mock.calls[1][1]).toEqual(first);
        await screen.findByText('Kalenderstand gespeichert und zur Übertragung eingeplant.');
        expect(screen.getByLabelText('Von').disabled).toBe(false);
    });
    it('shows actual worker state and limits non-managers to reading the reconciliation', async () => {
        render(<PmsBeds24Panel property={property} canManage={false} canManageSettings={false} />);
        expect((await screen.findByLabelText('Beds24-Hotel-ID')).disabled).toBe(true);
        expect(screen.getByText('Server-Übertragung inaktiv')).toBeTruthy();
        expect(screen.queryByRole('button', { name: 'Zuordnung speichern' })).toBeNull();
        expect(screen.getByRole('button', { name: 'Aktuellen Kalenderstand übertragen' }).disabled).toBe(true);
        api.get.mockImplementation((path) => Promise.resolve({ data: path.endsWith('/reconciliation') ? [{ bookingId: 101, roomId: 71, status: 'confirmed', arrival: '2026-10-01', departure: '2026-10-03', result: 'NOT_LINKED' }] : saved }));
        fireEvent.click(screen.getByRole('button', { name: 'Buchungen abgleichen' }));
        await screen.findByText('Noch keine verknüpfte Chrono-Reservierung');
        expect(api.post).not.toHaveBeenCalled();
    });
    it('links a reviewed existing reservation and retains input after a rejected comparison', async () => {
        const booking = { bookingId: 101, roomId: 71, status: 'confirmed', arrival: '2026-10-01', departure: '2026-10-03', result: 'NOT_LINKED' };
        api.get.mockImplementation((path) => Promise.resolve({ data: path.endsWith('/reconciliation') ? [booking] : saved }));
        api.post.mockRejectedValueOnce({ response: { data: { detail: 'Preis stimmt nicht überein.' } } }).mockResolvedValueOnce({ data: { ...booking, result: 'MATCHED', reservationId: 80 } });
        render(<PmsBeds24Panel property={property} canManage canManageSettings />);
        await screen.findByLabelText('Beds24-Hotel-ID');
        fireEvent.click(screen.getByRole('button', { name: 'Buchungen abgleichen' }));
        const input = await screen.findByLabelText('Chrono-Reservierungs-ID für 101');
        fireEvent.change(input, { target: { value: '80' } });
        fireEvent.submit(screen.getByRole('button', { name: 'Prüfen & verknüpfen' }).closest('form'));
        await screen.findByText('Preis stimmt nicht überein.');
        expect(input.value).toBe('80');
        fireEvent.submit(screen.getByRole('button', { name: 'Prüfen & verknüpfen' }).closest('form'));
        await screen.findByText('Stimmt überein');
        expect(api.post).toHaveBeenLastCalledWith('/api/pms/properties/2/beds24/bookings/101/link', { reservationId: 80 });
    });
});
