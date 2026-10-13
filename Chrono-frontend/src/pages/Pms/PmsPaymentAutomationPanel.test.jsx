/** @vitest-environment jsdom */
import React, { Activity } from 'react';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
const apiMock = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn(), post: vi.fn() }));
const refresh = vi.hoisted(() => ({ callback: null }));
vi.mock('../../utils/api.js', () => ({ default: apiMock }));
vi.mock('./usePmsLiveRefresh.js', () => ({ default: (callback) => { refresh.callback = callback; } }));
import PmsPaymentAutomationPanel from './PmsPaymentAutomationPanel.jsx';
const property = { id: 2, currencyCode: 'CHF' };
const saved = { merchantContext: 'CONNECT:acct_original', automaticDepositLinks: false, version: 3, verifiedAt: '2026-09-12T10:00:00' };
beforeEach(() => {
    vi.clearAllMocks();
    apiMock.get.mockImplementation((path) => Promise.resolve({ data: path.endsWith('payment-settings') ? saved : { workerEnabled: false, requests: [{ id: 4, folioId: 5, requestId: 'payment-one', status: 'OPEN', merchantContext: saved.merchantContext }], legacyPayments: [] } }));
    apiMock.put.mockResolvedValue({ data: { ...saved, version: 4 } }); apiMock.post.mockResolvedValue({ data: {} });
});
afterEach(cleanup);
describe('hotel payment automation settings', () => {
    it('retains a merchant draft during status refresh and submits the optimistic version', async () => {
        render(<PmsPaymentAutomationPanel property={property} canManageSettings canManage />);
        const input = await screen.findByLabelText('Stripe-Konto-ID');
        fireEvent.change(input, { target: { value: 'acct_next' } });
        await act(async () => { await refresh.callback(); });
        expect(input.value).toBe('acct_next');
        fireEvent.click(screen.getByText('Fällige Anzahlungslinks automatisch erstellen und per E-Mail bereitstellen').querySelector('input'));
        fireEvent.submit(screen.getByRole('button', { name: 'Prüfen & speichern' }).closest('form'));
        await waitFor(() => expect(apiMock.put).toHaveBeenCalledWith('/api/pms/properties/2/payment-settings', expect.objectContaining({ merchantContext: 'CONNECT:acct_next', automaticDepositLinks: true, version: 3 })));
    });
    it('shows real disabled worker state and prevents settings writes without master access', async () => {
        render(<PmsPaymentAutomationPanel property={property} canManageSettings={false} canManage={false} />);
        expect((await screen.findByLabelText('Stripe-Konto-ID')).disabled).toBe(true);
        expect(screen.getByText('Automatik auf Server inaktiv')).toBeTruthy();
        expect(screen.queryByRole('button', { name: 'Prüfen & speichern' })).toBeNull();
        expect(screen.getByRole('button', { name: 'Status prüfen' }).disabled).toBe(true);
    });
    it('preserves the merchant and opt-in draft after a failed validation', async () => {
        apiMock.put.mockRejectedValue({ response: { data: { detail: 'Händlerkonto konnte nicht bestätigt werden.' } } });
        render(<PmsPaymentAutomationPanel property={property} canManageSettings canManage />);
        const input = await screen.findByLabelText('Stripe-Konto-ID'); fireEvent.change(input, { target: { value: 'acct_other' } });
        fireEvent.submit(screen.getByRole('button', { name: 'Prüfen & speichern' }).closest('form'));
        await screen.findByText('Händlerkonto konnte nicht bestätigt werden.'); expect(input.value).toBe('acct_other');
    });
    it('keeps the merchant draft across Activity hide/show and discards it only on an explicit reload', async () => {
        const panel = (mode) => <Activity mode={mode}><PmsPaymentAutomationPanel property={property} canManageSettings canManage /></Activity>;
        const view = render(panel('visible'));
        const input = await screen.findByLabelText('Stripe-Konto-ID');
        fireEvent.change(input, { target: { value: 'acct_draft' } });
        await act(async () => { view.rerender(panel('hidden')); });
        const nextSaved = { ...saved, merchantContext: 'CONNECT:acct_serverchange', version: 8 };
        apiMock.get.mockImplementation((path) => Promise.resolve({ data: path.endsWith('payment-settings') ? nextSaved : { workerEnabled: false, requests: [], legacyPayments: [] } }));
        await act(async () => { view.rerender(panel('visible')); });
        expect(screen.getByLabelText('Stripe-Konto-ID').value).toBe('acct_draft');
        expect(screen.getByRole('button', { name: 'Prüfen & speichern' }).disabled).toBe(false);
        fireEvent.click(screen.getByRole('button', { name: 'Gespeicherte Einstellungen laden' }));
        await waitFor(() => expect(screen.getByLabelText('Stripe-Konto-ID').value).toBe('acct_serverchange'));
        expect(screen.getByRole('button', { name: 'Prüfen & speichern' }).disabled).toBe(true);
    });
});
