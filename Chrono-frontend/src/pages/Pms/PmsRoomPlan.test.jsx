/** @vitest-environment jsdom */
import React from 'react';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { addPlanDays, hotelToday, layoutRoomEvents, planDayLabel } from './pmsRoomPlan.js';

const apiMock = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock('../../utils/api.js', () => ({ default: apiMock }));
import PmsRoomPlan from './PmsRoomPlan.jsx';

const property = { id: 5, name: 'Grand Hotel', timezone: 'Europe/Zurich' };
const room = { id: 11, number: '101', floor: '1', roomTypeId: 10, roomTypeName: 'Doppelzimmer', bedType: 'Doppelbett', maxOccupancy: 2, features: 'Badewanne, ruhig', operationalStatus: 'IN_SERVICE', housekeepingStatus: 'CLEAN', active: true };
const response = {
    page: 0, size: 50, totalRooms: 2000, rooms: [room], reservations: [], blocks: [],
    filters: { roomTypes: [{ id: 10, name: 'Doppelzimmer' }], floors: ['1'], bedTypes: ['Doppelbett'], features: ['Badewanne', 'ruhig'], housekeepingSections: ['Nord'] },
};

beforeEach(() => { vi.clearAllMocks(); apiMock.get.mockResolvedValue({ data: response }); });
afterEach(cleanup);

describe('room plan date and event semantics', () => {
    it('uses hotel calendar dates across UTC midnight and DST boundaries', () => {
        expect(hotelToday('Europe/Zurich', new Date('2026-09-07T22:30:00Z'))).toBe('2026-09-08');
        expect(hotelToday('America/Los_Angeles', new Date('2026-09-08T02:30:00Z'))).toBe('2026-09-07');
        expect(planDayLabel('2026-09-07')).toBe('Mo. 07.09');
        expect(addPlanDays('2026-03-28', 2)).toBe('2026-03-30');
    });
    it('clips stays, frees departure days, and keeps overlaps visible in separate lanes', () => {
        const layout = layoutRoomEvents([
            { id: 1, arrivalDate: '2026-09-01', departureDate: '2026-09-09' },
            { id: 2, arrivalDate: '2026-09-09', departureDate: '2026-09-11' },
            { id: 3, arrivalDate: '2026-09-02', departureDate: '2026-09-07' },
        ], [{ id: 4, startDate: '2026-09-08', endDate: '2026-10-01' }], '2026-09-07', 10);
        expect(layout.map(({ entry, start, end, lane }) => [entry.id, start, end, lane])).toEqual([[1, 0, 2, 0], [4, 1, 10, 1], [2, 2, 4, 0]]);
    });
});

describe('PmsRoomPlan', () => {
    it('opens with hotel today and 30 daily columns, independently of a past business date', async () => {
        render(<PmsRoomPlan property={property} businessDate="2000-01-01" />);
        await screen.findByRole('table');
        const today = hotelToday(property.timezone);
        expect(apiMock.get).toHaveBeenCalledWith('/api/pms/properties/5/room-plan', expect.objectContaining({ params: expect.objectContaining({ from: today, days: 30, page: 0, size: 50 }) }));
        expect(screen.getByLabelText('Startdatum').value).toBe(today);
        expect(screen.getAllByRole('columnheader')).toHaveLength(31);
        expect(screen.getByRole('columnheader', { name: `${planDayLabel(today)} Heute` }).getAttribute('aria-current')).toBe('date');
        expect(screen.getByText(/2000 Zimmer · fortlaufende Tabelle/)).toBeTruthy();
        expect(screen.getAllByRole('rowheader')).toHaveLength(1);
    });
    it('requests a 10-day range and applies multiple room features together server-side', async () => {
        render(<PmsRoomPlan property={property} />);
        await screen.findByRole('table');
        fireEvent.change(screen.getByLabelText('Zeitraum'), { target: { value: '10' } });
        await waitFor(() => expect(apiMock.get).toHaveBeenLastCalledWith(expect.any(String), expect.objectContaining({ params: expect.objectContaining({ days: 10 }) })));
        fireEvent.click(screen.getByLabelText('Badewanne'));
        await waitFor(() => expect(apiMock.get.mock.calls.at(-1)[1].params.features).toEqual(['Badewanne']));
        fireEvent.click(screen.getByLabelText('ruhig'));
        fireEvent.change(screen.getByLabelText('Bettenart'), { target: { value: 'Doppelbett' } });
        await waitFor(() => expect(apiMock.get).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({ params: expect.objectContaining({ days: 10, bedType: 'Doppelbett', features: ['Badewanne', 'ruhig'], page: 0 }) })));
        expect(screen.getAllByRole('columnheader')).toHaveLength(11);
    });
    it('paginates thousands of rooms on the server and searches all pages', async () => {
        render(<PmsRoomPlan property={property} />);
        await screen.findByRole('table');
        fireEvent.click(screen.getByRole('button', { name: 'Weitere Zimmer' }));
        await waitFor(() => expect(apiMock.get.mock.calls.at(-1)[1].params.page).toBe(1));
        fireEvent.change(screen.getByLabelText('Zimmer suchen'), { target: { value: '9001' } });
        await waitFor(() => expect(apiMock.get.mock.calls.at(-1)[1].params).toMatchObject({ page: 0, search: '9001' }));
    });
    it('opens a future reservation with its form fields and restricts drag to managers', async () => {
        const today = hotelToday(property.timezone);
        const reservation = { id: 90, confirmationCode: 'CHR-90', roomId: 11, roomTypeId: 10, guestId: 2, ratePlanId: 3, guestName: 'Anna Gast', status: 'CONFIRMED', arrivalDate: addPlanDays(today, 15), departureDate: addPlanDays(today, 18), adults: 2, children: 0 };
        apiMock.get.mockResolvedValue({ data: { ...response, reservations: [reservation] } });
        const select = vi.fn();
        render(<PmsRoomPlan property={property} onSelectReservation={select} />);
        const booking = await screen.findByRole('button', { name: 'Anna Gast' });
        expect(booking.draggable).toBe(false);
        expect(booking.style.gridColumn).toBe('17 / span 3');
        fireEvent.click(booking);
        fireEvent.click(within(screen.getByRole('complementary')).getByRole('button', { name: 'Reservierung öffnen' }));
        expect(select).toHaveBeenCalledWith(reservation);
    });
    it('uses the existing conflict-checked move endpoint and refreshes after success', async () => {
        const today = hotelToday(property.timezone);
        const reservation = { id: 90, confirmationCode: 'CHR-90', roomId: 11, roomTypeId: 10, guestName: 'Anna Gast', status: 'CONFIRMED', arrivalDate: today, departureDate: addPlanDays(today, 2) };
        apiMock.get.mockResolvedValue({ data: { ...response, rooms: [room, { ...room, id: 12, number: '102' }], reservations: [reservation] } });
        apiMock.post.mockResolvedValue({ data: { reservations: [] } });
        const changed = vi.fn();
        render(<PmsRoomPlan property={property} canManage businessDate={today} onOperationsChange={changed} />);
        const booking = await screen.findByRole('button', { name: 'Anna Gast' });
        expect(booking.draggable).toBe(true);
        const target = screen.getByRole('rowheader', { name: /102/ }).closest('[role=row]');
        fireEvent.drop(target, { dataTransfer: { getData: (key) => key === 'text/reservation-id' ? '90' : '' } });
        await waitFor(() => expect(apiMock.post).toHaveBeenCalledWith('/api/pms/reservations/90/move-room', { roomId: 12, effectiveDate: today, reason: 'Verschoben im fortlaufenden Zimmerplan' }, { params: { businessDate: today } }));
        expect(changed).toHaveBeenCalledWith({ reservations: [] });
    });
    it('shows server errors without presenting stale availability as current', async () => {
        apiMock.get.mockRejectedValue({ response: { data: { detail: 'Hotel nicht gefunden.' } } });
        render(<PmsRoomPlan property={property} />);
        expect((await screen.findByRole('alert')).textContent).toContain('Hotel nicht gefunden.');
        expect(screen.queryByRole('table')).toBeNull();
    });
    it('jumps across a 20,000-room hotel with a bounded server and DOM window', async () => {
        apiMock.get.mockImplementation((path, { params }) => Promise.resolve({ data: { ...response, page: params.page, totalRooms: 20_000,
            rooms: Array.from({ length: 50 }, (_, index) => ({ ...room, id: params.page * 50 + index + 1, number: String(params.page * 50 + index + 1) })) } }));
        render(<PmsRoomPlan property={property} />);
        await screen.findByRole('rowheader', { name: /^1 / });
        expect(screen.getAllByRole('rowheader').length).toBeLessThan(30);
        fireEvent.change(screen.getByLabelText('Zu Zeile springen'), { target: { value: '15001' } });
        fireEvent.click(screen.getByRole('button', { name: 'Gehe zu' }));
        await screen.findByRole('rowheader', { name: /^15001 / });
        expect(screen.getAllByRole('rowheader').length).toBeLessThan(30);
        expect(apiMock.get.mock.calls.every(([, config]) => config.params.size === 50)).toBe(true);
        expect(apiMock.get.mock.calls.length).toBeLessThan(10);
        expect(screen.queryByRole('rowheader', { name: /^1 / })).toBeNull();
    });
    it('selects cells with shift/arrows and copies a spreadsheet-safe rectangular range', async () => {
        apiMock.get.mockResolvedValue({ data: { ...response, totalRooms: 2, rooms: [room, { ...room, id: 12, number: '=2+2' }] } });
        render(<PmsRoomPlan property={property} />);
        await screen.findByRole('table');
        const cells = screen.getAllByRole('cell');
        fireEvent.click(cells[0]); fireEvent.click(cells[31], { shiftKey: true });
        expect(screen.getByText('2 Zimmer × 2 Tage')).toBeTruthy();
        const viewport = screen.getByLabelText('Zimmer und Tage, horizontal und vertikal scrollbar');
        fireEvent.keyDown(viewport, { key: 'ArrowRight', shiftKey: true });
        const clipboardData = { setData: vi.fn() };
        fireEvent.copy(viewport, { clipboardData });
        const copied = clipboardData.setData.mock.calls[0][1];
        expect(copied.split('\n')).toHaveLength(3);
        expect(copied.split('\n').every((line) => line.split('\t').length === 4)).toBe(true);
        expect(copied).toContain("'=2+2\tFrei");
    });
});
