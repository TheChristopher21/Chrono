/** @vitest-environment jsdom */
import React, { Activity } from 'react';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }));
vi.mock('../../utils/api.js', () => ({ default: api }));
vi.mock('./usePmsLiveRefresh.js', () => ({ default: () => {} }));
import { AuthContext } from '../../context/AuthContext.jsx';
import PmsApprovalPanel from './PmsApprovalPanel.jsx';
import PmsFinancialActionDialog from './PmsFinancialActionDialog.jsx';

const property = { id: 5, currencyCode: 'CHF', name: 'Hotel' };
const pending = { id: 9, version: 2, paymentId: 4, requestId: 'approval-request-001', amount: 500,
    currency: 'CHF', reason: 'Room complaint', cashShiftId: 12, requestedBy: 'requester', status: 'PENDING' };
let serverPolicy, serverEntries;
beforeEach(() => {
    vi.clearAllMocks();
    serverPolicy = { enabled: true, refundThreshold: 500, version: 2 }; serverEntries = [pending];
    api.get.mockImplementation(async (path, options) => ({ data: path.endsWith('/approval-policy') ? { ...serverPolicy } : {
        items: serverEntries, page: options.params.page, size: options.params.size, totalElements: serverEntries.length, hasNext: false, status: options.params.status,
    } }));
    api.put.mockResolvedValue({ data: {} }); api.post.mockResolvedValue({ data: {} });
});
afterEach(cleanup);
const panel = (username = 'reviewer') => <AuthContext.Provider value={{ currentUser: { username } }}>
    <PmsApprovalPanel property={property} canManage isMaster />
</AuthContext.Provider>;
const submit = buttonName => fireEvent.submit(screen.getByRole('button', { name: buttonName, hidden: true }).closest('form'));

it('keeps the draft version when a newer policy arrives, allowing the server to reject stale edits', async () => {
    api.put.mockRejectedValue({ response: { data: { detail: 'Die Regel wurde inzwischen geändert.' } } });
    render(panel());
    const threshold = await screen.findByLabelText('Summe je Originalzahlung ab (CHF)');
    fireEvent.change(threshold, { target: { value: '650' } });
    serverPolicy = { ...serverPolicy, version: 3, refundThreshold: 700 };
    fireEvent.click(screen.getByRole('button', { name: 'Freigaben aktualisieren' }));
    await waitFor(() => expect(api.get).toHaveBeenCalledTimes(4));
    await waitFor(() => expect(screen.getByText(/700/)).toBeTruthy());
    expect(threshold.value).toBe('650');
    submit('Freigaberegel speichern');
    await screen.findByText('Die Regel wurde inzwischen geändert.');
    expect(api.put).toHaveBeenCalledWith('/api/pms/properties/5/approval-policy', { enabled: true, refundThreshold: 650, expectedVersion: 2 });
    expect(threshold.value).toBe('650');
});

it('retains both policy and decision drafts across retained-tab Activity switches', async () => {
    const element = panel();
    const mounted = render(<Activity mode="visible">{element}</Activity>);
    fireEvent.change(await screen.findByLabelText('Summe je Originalzahlung ab (CHF)'), { target: { value: '650' } });
    fireEvent.click(screen.getByRole('button', { name: 'Antrag prüfen' }));
    fireEvent.change(screen.getByLabelText('Entscheidung'), { target: { value: 'false' } });
    fireEvent.change(screen.getByLabelText('Begründung'), { target: { value: 'Beleg fehlt noch' } });
    await act(async () => mounted.rerender(<Activity mode="hidden">{element}</Activity>));
    await act(async () => mounted.rerender(<Activity mode="visible">{element}</Activity>));
    await waitFor(() => expect(api.get).toHaveBeenCalledTimes(4));
    expect(screen.getByLabelText('Summe je Originalzahlung ab (CHF)').value).toBe('650');
    expect(screen.getByLabelText('Entscheidung').value).toBe('false');
    expect(screen.getByLabelText('Begründung').value).toBe('Beleg fehlt noch');
});

it('disables self-approval and sends the second person decision against the displayed version', async () => {
    const mounted = render(panel('requester'));
    expect((await screen.findByRole('button', { name: 'Antrag prüfen' })).disabled).toBe(true);
    mounted.rerender(panel('reviewer'));
    fireEvent.click(screen.getByRole('button', { name: 'Antrag prüfen' }));
    fireEvent.change(screen.getByLabelText('Begründung'), { target: { value: 'Rechnung und Betrag geprüft' } });
    submit('Entscheidung speichern');
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/pms/properties/5/approvals/refunds/9/decision',
        { approve: true, reason: 'Rechnung und Betrag geprüft', expectedVersion: 2 }));
    await screen.findByText('Entscheidung protokolliert.');
});

it('executes an approved refund with its original bound ID, amount, reason and cash shift', async () => {
    serverEntries = [{ ...pending, status: 'APPROVED' }];
    render(panel());
    fireEvent.click(await screen.findByRole('button', { name: 'Freigegebene Erstattung ausführen' }));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/pms/properties/5/payments/4/refund',
        { requestId: 'approval-request-001', amount: 500, reason: 'Room complaint', cashShiftId: 12 }));
});

it('loads open pages by default and retains policy and decision drafts across pages and history filters', async () => {
    api.get.mockImplementation(async (path, options) => ({ data: path.endsWith('/approval-policy') ? { ...serverPolicy } : {
        items: options.params.page === 0 ? [pending] : [{ ...pending, id: 10, reason: 'Second page request' }],
        page: options.params.page, size: 50, totalElements: 51, hasNext: options.params.page === 0, status: options.params.status,
    } }));
    render(panel());
    fireEvent.change(await screen.findByLabelText('Summe je Originalzahlung ab (CHF)'), { target: { value: '650' } });
    expect(api.get).toHaveBeenCalledWith('/api/pms/properties/5/approvals/refunds', { params: { page: 0, size: 50, status: 'OPEN' } });
    fireEvent.click(screen.getByRole('button', { name: 'Antrag prüfen' }));
    fireEvent.change(screen.getByLabelText('Begründung'), { target: { value: 'Nachweis noch offen' } });
    fireEvent.click(screen.getByRole('button', { name: 'Nächste Freigabeseite' }));
    await screen.findByText('Seite 2 · 51 Anträge');
    expect(api.get).toHaveBeenCalledWith('/api/pms/properties/5/approvals/refunds', { params: { page: 1, size: 50, status: 'OPEN' } });
    expect(screen.getByLabelText('Summe je Originalzahlung ab (CHF)').value).toBe('650');
    expect(screen.getByLabelText('Begründung').value).toBe('Nachweis noch offen');
    expect(screen.getByRole('button', { name: 'Antrag prüfen' }).disabled).toBe(true);
    fireEvent.change(screen.getByLabelText('Freigaben anzeigen'), { target: { value: 'HISTORY' } });
    await screen.findByText('Seite 1 · 51 Anträge');
    expect(api.get).toHaveBeenCalledWith('/api/pms/properties/5/approvals/refunds', { params: { page: 0, size: 50, status: 'HISTORY' } });
    expect(screen.getByLabelText('Summe je Originalzahlung ab (CHF)').value).toBe('650');
    expect(screen.getByLabelText('Begründung').value).toBe('Nachweis noch offen');
    expect(screen.getByText('Entscheidung zu Antrag 9 · Zahlung 4')).toBeTruthy();
});

it('ignores a late response from the previous status filter', async () => {
    let resolveOpen;
    api.get.mockImplementation((path, options) => {
        if (path.endsWith('/approval-policy')) return Promise.resolve({ data: serverPolicy });
        if (options.params.status === 'OPEN') return new Promise(resolve => { resolveOpen = resolve; });
        return Promise.resolve({ data: { items: [{ ...pending, status: 'REJECTED', reason: 'Historical rejection' }], page: 0, size: 50, totalElements: 1, hasNext: false, status: 'HISTORY' } });
    });
    render(panel());
    fireEvent.change(screen.getByLabelText('Freigaben anzeigen'), { target: { value: 'HISTORY' } });
    await screen.findByText('Historical rejection');
    await act(async () => resolveOpen({ data: { items: [pending], page: 0, size: 50, totalElements: 1, hasNext: false, status: 'OPEN' } }));
    expect(screen.getByText('Historical rejection')).toBeTruthy();
    expect(screen.queryByText('Room complaint')).toBeNull();
});

it('creates the approval request from the exact rejected refund payload and reuses it after approval', async () => {
    const refund = vi.fn().mockRejectedValueOnce({ response: { status: 428 } }).mockResolvedValueOnce(true);
    const close = vi.fn();
    render(<PmsFinancialActionDialog payment={{ id: 4, amount: 1000, method: 'CASH' }} propertyId={5} currency="CHF"
        cashShifts={[{ id: 12, status: 'OPEN', registerCode: 'DESK', openedBy: 'requester' }]}
        onSubmit={refund} onClose={close} />);
    fireEvent.change(screen.getByLabelText('Erstattungsbetrag (CHF)'), { target: { value: '500' } });
    fireEvent.change(screen.getByLabelText('Auszahlende Kasse'), { target: { value: '12' } });
    fireEvent.change(screen.getByLabelText('Begründung'), { target: { value: 'Room complaint' } });
    submit('Erstattung ausführen');
    await screen.findByText(/Der Freigabeantrag ist/);
    await waitFor(() => expect(api.post).toHaveBeenCalledTimes(1));
    const intent = refund.mock.calls[0][0];
    expect(intent).toEqual({ amount: 500, reason: 'Room complaint', cashShiftId: 12, requestId: expect.any(String) });
    expect(api.post).toHaveBeenCalledWith('/api/pms/properties/5/approvals/refunds', { paymentId: 4, refund: intent });
    expect(screen.getByLabelText('Erstattungsbetrag (CHF)').disabled).toBe(true);
    expect(screen.getByLabelText('Auszahlende Kasse').disabled).toBe(true);
    expect(screen.getByRole('button', { name: 'Schließen' }).disabled).toBe(false);
    submit('Freigabe erneut prüfen');
    await waitFor(() => expect(close).toHaveBeenCalledOnce());
    expect(refund.mock.calls[1][0]).toEqual(intent);
});

it('keeps the same approval request ID on uncertain approval creation and retry', async () => {
    const refund = vi.fn().mockRejectedValue({ response: { status: 428 } });
    api.post.mockRejectedValueOnce(new Error('Lost response')).mockResolvedValueOnce({ data: pending });
    render(<PmsFinancialActionDialog payment={{ id: 4, amount: 1000, method: 'CARD' }} propertyId={5} currency="CHF" onSubmit={refund} onClose={vi.fn()} />);
    fireEvent.change(screen.getByLabelText('Begründung'), { target: { value: 'Room complaint' } });
    submit('Erstattung ausführen');
    await screen.findByText('Freigabeantrag noch nicht bestätigt. Dieselbe Anfrage erneut prüfen.');
    const first = api.post.mock.calls[0][1];
    submit('Freigabe erneut prüfen');
    await waitFor(() => expect(api.post).toHaveBeenCalledTimes(2));
    expect(api.post.mock.calls[1][1]).toEqual(first);
    expect(refund.mock.calls[1][0]).toEqual(refund.mock.calls[0][0]);
});
