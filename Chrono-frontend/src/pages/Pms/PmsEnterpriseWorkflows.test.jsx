import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import PmsFinancialActionDialog from './PmsFinancialActionDialog.jsx';
import PmsFinancialDayPanel from './PmsFinancialDayPanel.jsx';
import PmsAccessWorkspace from './PmsAccessWorkspace.jsx';
import { pmsHasPermission } from './pmsAccess.js';
import { layoutRoomEvents } from './pmsRoomPlan.js';
const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }));
vi.mock('../../utils/api.js', () => ({ default: api }));
afterEach(() => { cleanup(); vi.clearAllMocks(); });

describe('enterprise PMS workflows', () => {
    it('keeps a refund identity after failure and uses a different identity for a separate refund', async () => {
        const submit = vi.fn().mockResolvedValue(false);
        const props = { payment: { id: 5, amount: 100, method: 'CARD' }, currency: 'EUR', onClose: vi.fn(), onSubmit: submit };
        const first = render(<PmsFinancialActionDialog {...props} />);
        fireEvent.change(screen.getByLabelText('Erstattungsbetrag (EUR)'), { target: { value: '25' } });
        fireEvent.change(screen.getByLabelText('Begründung'), { target: { value: 'Reklamation Frühstück' } });
        fireEvent.submit(screen.getByRole('button', { name: 'Erstattung ausführen' }).closest('form'));
        await waitFor(() => expect(submit).toHaveBeenCalledTimes(1));
        fireEvent.submit((await screen.findByRole('button', { name: 'Bestehende Erstattung erneut prüfen' })).closest('form'));
        await waitFor(() => expect(submit).toHaveBeenCalledTimes(2));
        expect(submit.mock.calls[0][0].requestId).toEqual(submit.mock.calls[1][0].requestId);
        expect(submit.mock.calls[0][0].amount).toBe(25);
        first.unmount(); render(<PmsFinancialActionDialog {...props} />);
        fireEvent.change(screen.getByLabelText('Begründung'), { target: { value: 'Zweite Erstattung' } });
        fireEvent.submit(screen.getByRole('button', { name: 'Erstattung ausführen' }).closest('form'));
        await waitFor(() => expect(submit).toHaveBeenCalledTimes(3));
        expect(submit.mock.calls[2][0].requestId).not.toEqual(submit.mock.calls[0][0].requestId);
    });

    it('requires explicit review before closing and keeps an open cashier as a blocker', async () => {
        api.get.mockResolvedValue({ data: { businessDate: '2026-09-12', openCashShifts: 1, pendingArrivals: 0, pendingDepartures: 0, blockers: ['Kasse schließen'] } });
        render(<PmsFinancialDayPanel property={{ id: 1 }} canManage />);
        const close = await screen.findByRole('button', { name: 'Betriebstag abschließen' });
        expect(close.disabled).toBe(true);
        fireEvent.click(screen.getByLabelText('Kassen, Abreisen und offene Anreisen sind geprüft.'));
        expect(close.disabled).toBe(true);
        expect(api.post).not.toHaveBeenCalled();
    });

    it('saves the selected employee hotel grant without implicitly granting other hotels', async () => {
        api.get.mockResolvedValue({ data: { permissionKeys: ['FINANCE', 'REFUNDS'], properties: [{ propertyId: 1, propertyName: 'Hotel Zürich' }, { propertyId: 2, propertyName: 'Hotel Berlin' }], users: [{ userId: 7, username: 'anna', displayName: 'Anna', master: false, grants: [] }] } });
        api.put.mockResolvedValue({ data: { userId: 7, username: 'anna', grants: [{ propertyId: 1, permissions: { FINANCE: 'VIEW' } }] } });
        render(<PmsAccessWorkspace />);
        fireEvent.change(await screen.findByLabelText('Mitarbeiter'), { target: { value: '7' } });
        fireEvent.change(screen.getByLabelText('Hotel Zürich: Kasse & Abrechnung'), { target: { value: 'VIEW' } });
        fireEvent.click(screen.getByRole('button', { name: 'Hotelrechte speichern' }));
        await waitFor(() => expect(api.put).toHaveBeenCalledWith('/api/pms/access/users/7', { grants: [{ propertyId: 1, permissions: { FINANCE: 'VIEW' } }] }));
    });

    it('checks hotel and activity independently and displays only the correct room segment', () => {
        const access = { properties: [{ propertyId: 1, permissions: { FINANCE: 'VIEW', REFUNDS: 'MANAGE' } }] };
        expect(pmsHasPermission(access, 1, 'FINANCE')).toBe(true);
        expect(pmsHasPermission(access, 1, 'FINANCE', true)).toBe(false);
        expect(pmsHasPermission(access, 2, 'REFUNDS', true)).toBe(false);
        const [event] = layoutRoomEvents([{ id: 7, arrivalDate: '2026-09-07', departureDate: '2026-09-12', segmentStartDate: '2026-09-09', segmentEndDate: '2026-09-12' }], [], '2026-09-07', 10);
        expect([event.start, event.end]).toEqual([2, 5]);
    });
});
