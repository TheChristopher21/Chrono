/** @vitest-environment jsdom */
import React, { Activity } from 'react';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
const apiMock = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }));
vi.mock('../../utils/api.js', () => ({ default: apiMock }));
import PmsRevenuePlanningPanel from './PmsRevenuePlanningPanel.jsx';
const property = { id: 9, name: 'Kuwait', currencyCode: 'KWD' };
const snapshot = { id: 7, asOfDate: '2026-09-12', toExclusive: '2027-09-13', capturedAt: '2026-09-12T09:00:00', capturedBy: 'Manager' };
const budget = { id: 5, version: 3, monthStart: '2026-10-01', netRoomRevenue: '10.123', roomNights: 10, updatedAt: '2026-09-12T10:00:00', updatedBy: 'Manager' };
function report() {
    return { propertyId: 9, currencyCode: 'KWD', businessDate: '2026-09-12', from: '2026-10-01', toExclusive: '2026-10-03', comparisonSnapshot: null, snapshots: [],
        summary: { roomNights: 1, netRoomRevenue: '12.345', previousRoomNights: null, previousNetRoomRevenue: null, pickupRoomNights: null, pickupNetRoomRevenue: null, unknownRevenueRoomNights: 0, comparisonCoverageDays: 0, requestedDays: 2 },
        days: [{ date: '2026-10-01', roomNights: 1, netRoomRevenue: '12.345', previousNetRoomRevenue: null, pickupNetRoomRevenue: null }, { date: '2026-10-02', roomNights: 0, netRoomRevenue: 0, previousNetRoomRevenue: null, pickupNetRoomRevenue: null }],
        months: [{ monthStart: '2026-10-01', coveredDays: 2, daysInMonth: 31, roomNights: 1, netRoomRevenue: '12.345', pickupNetRoomRevenue: null, proratedBudgetRoomNights: null, proratedBudgetNetRoomRevenue: null, budgetRevenueVariance: null, budget: null }] };
}
let data;
beforeEach(() => { vi.clearAllMocks(); data = report(); apiMock.get.mockImplementation(() => Promise.resolve({ data })); apiMock.post.mockResolvedValue({ data: snapshot }); apiMock.put.mockResolvedValue({ data: budget }); });
afterEach(cleanup);
describe('revenue planning', () => {
    it.each([
        undefined,
        { roomRevenue: 150, daily: [] },
        { ...report(), months: null },
        { ...report(), propertyId: 10 },
    ])('shows a recoverable error for an incomplete or mismatched report response', async (invalid) => {
        apiMock.get.mockResolvedValueOnce({ data: invalid });
        render(<PmsRevenuePlanningPanel property={property} />);
        expect(await screen.findByRole('alert')).toHaveTextContent('Die Antwort zur Umsatzplanung ist unvollständig oder gehört nicht zu diesem Hotel.');
        expect(screen.queryByRole('img', { name: /Netto-Zimmererlöse je Aufenthaltstag/ })).toBeNull();
        fireEvent.click(screen.getByRole('button', { name: 'Erneut laden' }));
        await screen.findByText(/Noch kein Vergleichsstand vorhanden/);
        expect(screen.queryByRole('alert')).toBeNull();
    });
    it('states missing historical evidence and does not offer report writes to readers', async () => {
        render(<PmsRevenuePlanningPanel property={property} />);
        await screen.findByText(/Noch kein Vergleichsstand vorhanden/);
        expect(screen.getByRole('img', { name: /Netto-Zimmererlöse je Aufenthaltstag/ })).toBeTruthy();
        expect(screen.queryByRole('button', { name: 'Buchungsstand jetzt sichern' })).toBeNull();
        expect(screen.queryByRole('button', { name: 'Monatsbudget speichern' })).toBeNull();
        expect(screen.getAllByText('Kein Vergleich').length).toBeGreaterThan(0);
    });
    it('captures a genuine immutable business-day snapshot and then disables overwriting it', async () => {
        render(<PmsRevenuePlanningPanel property={property} canManage />);
        const capture = await screen.findByRole('button', { name: 'Buchungsstand jetzt sichern' });
        data = { ...data, snapshots: [snapshot], comparisonSnapshot: snapshot, summary: { ...data.summary, comparisonCoverageDays: 1 } };
        fireEvent.click(capture);
        await waitFor(() => expect(apiMock.post).toHaveBeenCalledWith('/api/pms/properties/9/reports/revenue-planning/snapshots'));
        expect((await screen.findByRole('button', { name: 'Heutiger Betriebstag bereits gespeichert' })).disabled).toBe(true);
        expect(screen.getByText(/Die Historie deckt den Zeitraum nur teilweise ab/)).toBeTruthy();
    });
    it('saves a full-month budget at hotel currency precision with its edit version', async () => {
        data.months[0].budget = budget;
        render(<PmsRevenuePlanningPanel property={property} canManage />);
        const input = await screen.findByLabelText('Netto-Zimmererlöse (KWD)');
        await waitFor(() => expect(input.value).toBe('10.123'));
        expect(input.step).toBe('0.001');
        fireEvent.change(input, { target: { value: '42.123' } });
        fireEvent.change(screen.getByLabelText('Zimmernächte'), { target: { value: '20' } });
        fireEvent.submit(screen.getByRole('button', { name: 'Monatsbudget speichern' }).closest('form'));
        await waitFor(() => expect(apiMock.put).toHaveBeenCalledWith('/api/pms/properties/9/reports/revenue-planning/budgets/2026-10', { netRoomRevenue: '42.123', roomNights: 20, expectedVersion: 3 }));
    });
    it('preserves an unsaved monthly budget when an application tab is hidden and restored', async () => {
        const view = render(<Activity mode="visible"><PmsRevenuePlanningPanel property={property} canManage /></Activity>);
        const input = await screen.findByLabelText('Netto-Zimmererlöse (KWD)');
        fireEvent.change(input, { target: { value: '77.123' } });
        view.rerender(<Activity mode="hidden"><PmsRevenuePlanningPanel property={property} canManage /></Activity>);
        view.rerender(<Activity mode="visible"><PmsRevenuePlanningPanel property={property} canManage /></Activity>);
        expect(screen.getByLabelText('Netto-Zimmererlöse (KWD)').value).toBe('77.123');
        expect(apiMock.get).toHaveBeenCalledTimes(1);
    });
    it('reloads when returning to a hotel before another hotel has finished loading', async () => {
        let resolveOther;
        const pending = new Promise((resolve) => { resolveOther = resolve; });
        apiMock.get.mockImplementation((url) => url.includes('/10/') ? pending : Promise.resolve({ data }));
        const view = render(<PmsRevenuePlanningPanel property={property} canManage />);
        await screen.findByLabelText('Netto-Zimmererlöse (KWD)');
        view.rerender(<PmsRevenuePlanningPanel property={{ id: 10, currencyCode: 'JPY' }} canManage />);
        expect(screen.queryByLabelText('Netto-Zimmererlöse (KWD)')).toBeNull();
        view.rerender(<PmsRevenuePlanningPanel property={property} canManage />);
        await screen.findByLabelText('Netto-Zimmererlöse (KWD)');
        await act(async () => { resolveOther({ data: { ...data, propertyId: 10, currencyCode: 'JPY' } }); });
        expect(screen.queryByLabelText('Netto-Zimmererlöse (JPY)')).toBeNull();
        expect(screen.getByLabelText('Netto-Zimmererlöse (KWD)')).toBeTruthy();
        expect(apiMock.get).toHaveBeenCalledTimes(3);
    });
    it('keeps negative room credits and isolated comparison points inside the chart', async () => {
        data.days[0].netRoomRevenue = '-5.125';
        data.days[1].previousNetRoomRevenue = '3.125';
        render(<PmsRevenuePlanningPanel property={property} />);
        const chart = await screen.findByRole('img', { name: /Netto-Zimmererlöse je Aufenthaltstag/ });
        const points = chart.querySelector('polyline').getAttribute('points').split(' ').map((point) => Number(point.split(',')[1]));
        expect(points.every((y) => y >= 12 && y <= 158)).toBe(true);
        expect(chart.querySelector('circle')).toBeTruthy();
    });
});
