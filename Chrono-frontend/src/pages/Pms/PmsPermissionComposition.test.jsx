/** @vitest-environment jsdom */
import React from 'react';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }));
vi.mock('../../utils/api.js', () => ({ default: api }));
import PmsOperationsWorkspace from './PmsOperationsWorkspace.jsx';
import PmsAdvancedWorkspace from './PmsAdvancedWorkspace.jsx';
import PmsReceivablesWorkspace from './PmsReceivablesWorkspace.jsx';
import PmsExtensionsWorkspace from './PmsExtensionsWorkspace.jsx';
const property = { id: 5, name: 'Hotel', currencyCode: 'CHF', roomTypes: [] };
const operations = { propertyId: 5, metrics: {}, guests: [], reservations: [{ id: 7, roomNumber: '101', guestName: 'Anna Gast', status: 'CONFIRMED' }], rooms: [], ratePlans: [], rateOverrides: [], housekeepingTasks: [], roomBlocks: [], folios: [{ id: 8, reservationId: 7, guestName: 'Anna Gast', status: 'OPEN', currencyCode: 'CHF', balance: 120, items: [], paymentEntries: [{ id: 9, amount: 40, method: 'CARD', status: 'POSTED', kind: 'PAYMENT' }] }] };
const advanced = { propertyId: 5, organizations: [], groups: [], invoices: [{ id: 10, invoiceNumber: 'R-10', type: 'INVOICE', status: 'ISSUED', recipientName: 'Anna Gast', currencyCode: 'CHF', netAmount: 100, vatAmount: 8.1, grossAmount: 108.1 }], nightAudits: [], communications: [], communicationTemplates: [], guestRegistrations: [], hotelResources: [], resourceBookings: [], channelConnections: [], integrationOutbox: [], auditEvents: [] };
const page = { items: [], totalElements: 0, hasNext: false };
beforeEach(() => {
    vi.clearAllMocks();
    api.get.mockImplementation(async (path) => ({ data: path === '/api/pms/advanced' ? advanced : path === '/api/pms/operations' ? operations
        : path.endsWith('/financial-day') ? { businessDate: '2030-01-01' }
        : path.endsWith('/receivables') ? { ...page, items: [{ id: 11, invoiceNumber: 'R-11', organizationName: 'Firma', dueDate: '2030-01-01', balance: -40 }, { id: 12, invoiceNumber: 'R-12', organizationName: 'Firma', dueDate: '2030-01-01', balance: 80 }] }
        : path.includes('/history/') ? page : path === '/api/pms/extensions' ? { accessCredentials: [], posTickets: [], migrationBatches: [] } : [] }));
    api.post.mockImplementation(async (path) => ({ data: path.includes('/invoices/') ? advanced : operations }));
});
afterEach(cleanup);
describe('independent hotel module permissions in composed PMS panels', () => {
    it('lets a rejected cash refund be corrected when a successful same-hotel read proves no intent was created', async () => {
        const cashOperations = { ...operations, folios: operations.folios.map((folio) => ({ ...folio, paymentEntries: [{ ...folio.paymentEntries[0], method: 'CASH' }] })) };
        const normalGet = api.get.getMockImplementation();
        api.get.mockImplementation((path, options) => path === '/api/pms/operations' ? Promise.resolve({ data: cashOperations })
            : path.endsWith('/cash-shifts') ? Promise.resolve({ data: [{ id: 15, status: 'OPEN', registerCode: 'R1', outletCode: 'FRONTDESK' }] }) : normalGet(path, options));
        api.post.mockRejectedValue({ response: { status: 409, data: { detail: 'Kassenschicht wurde geschlossen.' } } });
        render(<PmsOperationsWorkspace section="folios" property={property} operations={cashOperations} setup={{ properties: [property] }} businessDate="2030-01-01" canManage={false} canRefund onOperationsChange={() => {}} onClose={() => {}} />);
        fireEvent.click(screen.getByRole('button', { name: 'Erstatten' }));
        await screen.findByRole('option', { name: /R1/ });
        fireEvent.change(screen.getByLabelText('Auszahlende Kasse'), { target: { value: '15' } });
        fireEvent.change(screen.getByLabelText('Begründung'), { target: { value: 'Barerstattung geprüft' } });
        fireEvent.submit(screen.getByRole('button', { name: 'Erstattung ausführen' }).closest('form'));
        await waitFor(() => expect(api.post).toHaveBeenCalledOnce());
        await waitFor(() => expect(screen.getByRole('button', { name: 'Abbrechen' })).toBeEnabled());
        expect(screen.getByLabelText('Auszahlende Kasse')).toBeEnabled();
        expect(screen.getByLabelText('Erstattungsbetrag (CHF)')).toBeEnabled();
        expect(screen.getByLabelText('Begründung')).toHaveValue('Barerstattung geprüft');
    });
    it('does not unlock a previously uncertain refund after a later 409 and an empty intent lookup', async () => {
        api.post.mockRejectedValueOnce(new Error('Response lost')).mockRejectedValueOnce({ response: { status: 409, data: { detail: 'Betrag derzeit nicht verfügbar.' } } });
        render(<PmsOperationsWorkspace section="folios" property={property} operations={operations} setup={{ properties: [property] }} businessDate="2030-01-01" canManage={false} canRefund onOperationsChange={() => {}} onClose={() => {}} />);
        fireEvent.click(screen.getByRole('button', { name: 'Erstatten' }));
        fireEvent.change(screen.getByLabelText('Begründung'), { target: { value: 'Reklamation geprüft' } });
        fireEvent.submit(screen.getByRole('button', { name: 'Erstattung ausführen' }).closest('form'));
        const retry = await screen.findByRole('button', { name: 'Bestehende Erstattung erneut prüfen' });
        await waitFor(() => expect(retry).toBeEnabled());
        fireEvent.submit(retry.closest('form'));
        await waitFor(() => expect(api.post).toHaveBeenCalledTimes(2));
        await waitFor(() => expect(screen.getByRole('button', { name: 'Bestehende Erstattung erneut prüfen' })).toBeEnabled());
        expect(screen.getByRole('button', { name: 'Abbrechen' })).toBeDisabled();
        expect(screen.getByLabelText('Erstattungsbetrag (CHF)')).toBeDisabled();
        expect(api.post.mock.calls[1][1]).toEqual(api.post.mock.calls[0][1]);
    });
    it('keeps a first 409 frozen when the confirming operations read fails', async () => {
        const normalGet = api.get.getMockImplementation();
        api.get.mockImplementation((path, options) => path === '/api/pms/operations' ? Promise.reject(new Error('Read unavailable')) : normalGet(path, options));
        api.post.mockRejectedValue({ response: { status: 409, data: { detail: 'Anbieterstatus unklar.' } } });
        render(<PmsOperationsWorkspace section="folios" property={property} operations={operations} setup={{ properties: [property] }} businessDate="2030-01-01" canManage={false} canRefund onOperationsChange={() => {}} onClose={() => {}} />);
        fireEvent.click(screen.getByRole('button', { name: 'Erstatten' }));
        fireEvent.change(screen.getByLabelText('Begründung'), { target: { value: 'Reklamation geprüft' } });
        fireEvent.submit(screen.getByRole('button', { name: 'Erstattung ausführen' }).closest('form'));
        await waitFor(() => expect(screen.getByRole('button', { name: 'Bestehende Erstattung erneut prüfen' })).toBeEnabled());
        expect(screen.getByRole('button', { name: 'Abbrechen' })).toBeDisabled();
        expect(screen.getByLabelText('Erstattungsbetrag (CHF)')).toBeDisabled();
    });
    it('recovers a committed refund after HTTP failure and offers only that same pending request for retry', async () => {
        let savedOperations;
        const change = vi.fn(); const normalGet = api.get.getMockImplementation();
        api.post.mockImplementation(async (_path, body) => {
            savedOperations = { ...operations, folios: operations.folios.map((folio) => ({ ...folio, paymentEntries: [...folio.paymentEntries, { id: 13, kind: 'REFUND', status: 'PENDING', amount: -body.amount, originalPaymentId: 9, refundRequestId: body.requestId, reason: body.reason, cashShiftId: body.cashShiftId, method: 'CARD' }] })) };
            throw new Error('Provider response lost');
        });
        api.get.mockImplementation((path, options) => path === '/api/pms/operations' ? Promise.resolve({ data: savedOperations }) : normalGet(path, options));
        const props = { section: 'folios', property, operations, setup: { properties: [property] }, businessDate: '2030-01-01', canManage: false, canRefund: true, onOperationsChange: change, onClose: () => {} };
        const view = render(<PmsOperationsWorkspace {...props} />);
        fireEvent.click(screen.getByRole('button', { name: 'Erstatten' }));
        fireEvent.change(screen.getByLabelText('Erstattungsbetrag (CHF)'), { target: { value: '20' } });
        fireEvent.change(screen.getByLabelText('Begründung'), { target: { value: 'Frühstück reklamiert' } });
        fireEvent.submit(screen.getByRole('button', { name: 'Erstattung ausführen' }).closest('form'));
        await waitFor(() => expect(change).toHaveBeenCalledWith(savedOperations));
        await waitFor(() => expect(screen.queryByRole('heading', { name: 'Betrag zurückerstatten' })).not.toBeInTheDocument());
        view.rerender(<PmsOperationsWorkspace {...props} operations={savedOperations} />);
        expect(screen.getByRole('button', { name: 'Erstatten' })).toBeDisabled();
        const request = api.post.mock.calls[0][1];
        fireEvent.click(screen.getByRole('button', { name: 'Erstattungsstatus prüfen' }));
        await waitFor(() => expect(api.post).toHaveBeenCalledTimes(2));
        expect(api.post.mock.calls[1][1]).toEqual(request);
    });
    it('executes a folio refund with refund management and finance read access', async () => {
        render(<PmsOperationsWorkspace section="folios" property={property} operations={operations} setup={{ properties: [property] }} businessDate="2030-01-01" canManage={false} canRefund onOperationsChange={() => {}} onClose={() => {}} />);
        fireEvent.click(screen.getByRole('button', { name: 'Erstatten' }));
        fireEvent.change(screen.getByLabelText('Begründung'), { target: { value: 'Gutschrift bestätigt' } });
        fireEvent.submit(screen.getByRole('button', { name: 'Erstattung ausführen' }).closest('form'));
        await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/pms/properties/5/payments/9/refund?businessDate=2030-01-01', expect.objectContaining({ amount: 40, reason: 'Gutschrift bestätigt', requestId: expect.any(String) })));
        expect(screen.getByRole('button', { name: 'Zahlung verbuchen' })).toBeDisabled();
    });
    it('does not expose refund actions to a finance manager without refund access', () => {
        render(<PmsOperationsWorkspace section="folios" property={property} operations={operations} setup={{ properties: [property] }} businessDate="2030-01-01" canManage canRefund={false} onOperationsChange={() => {}} onClose={() => {}} />);
        expect(screen.getByRole('button', { name: 'Erstatten' })).toBeDisabled();
        expect(screen.getByRole('button', { name: 'Stornieren' })).toBeDisabled();
    });
    it('executes invoice correction with refund management and finance read access', async () => {
        render(<PmsAdvancedWorkspace section="invoices" property={property} operations={operations} businessDate="2030-01-01" canManage={false} canRefund onOperationsChange={() => {}} />);
        const credit = await screen.findByRole('button', { name: 'Gutschrift' });
        await waitFor(() => expect(credit).toBeEnabled()); fireEvent.click(credit);
        fireEvent.change(screen.getByLabelText('Begründung'), { target: { value: 'Falscher Empfänger' } });
        fireEvent.submit(screen.getByRole('button', { name: 'Korrektur buchen' }).closest('form'));
        await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/pms/properties/5/invoices/10/correct?businessDate=2030-01-01', { mode: 'REISSUE', reason: 'Falscher Empfänger' }));
        expect(screen.getByRole('button', { name: 'Rechnung ausstellen' })).toBeDisabled();
    });
    it('allows an AR credit payout with refund access while incoming settlements stay disabled', async () => {
        render(<PmsReceivablesWorkspace property={property} canManage={false} canRefund />);
        fireEvent.click(await screen.findByRole('button', { name: 'Guthaben auszahlen' }));
        expect(screen.getByRole('button', { name: 'Zahlung zuordnen' })).toBeDisabled();
        fireEvent.change(screen.getByLabelText('Bankreferenz'), { target: { value: 'BANK-REFUND-1' } });
        fireEvent.submit(screen.getByRole('button', { name: 'Bankbewegung buchen' }).closest('form'));
        await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/pms/properties/5/receivables/11/refunds', expect.objectContaining({ amount: 40, bankReference: 'BANK-REFUND-1', requestId: expect.any(String) })));
    });
    it('keeps AR payouts disabled for a finance manager without refund access', async () => {
        render(<PmsReceivablesWorkspace property={property} canManage canRefund={false} />);
        expect(await screen.findByRole('button', { name: 'Guthaben auszahlen' })).toBeDisabled();
        expect(screen.getByRole('button', { name: 'Zahlung zuordnen' })).toBeEnabled();
    });
    it('lets reception issue keys from reservations without finance or integration rights', async () => {
        render(<PmsOperationsWorkspace section="reservations" property={property} operations={operations} setup={{ properties: [property] }} businessDate="2030-01-01" canManage canViewFrontDesk canManageFrontDesk canFinance={false} canViewFinance={false} canViewIntegrations={false} canViewReports={false} onOperationsChange={() => {}} onClose={() => {}} />);
        fireEvent.change(screen.getByLabelText('Reservierung für Zimmerschlüssel'), { target: { value: '7' } });
        fireEvent.change(screen.getByLabelText('Schlüsselanbieter'), { target: { value: 'SALTO' } });
        fireEvent.change(screen.getByLabelText('Externe Schlüsselreferenz'), { target: { value: 'env:key-ref' } });
        fireEvent.submit(screen.getByRole('button', { name: 'Schlüssel ausstellen' }).closest('form'));
        await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/pms/properties/5/access-credentials', expect.objectContaining({ reservationId: 7, providerCode: 'SALTO', externalReference: 'env:key-ref' })));
    });
    it('hides reception-only keys and their history from finance/integration users', async () => {
        render(<PmsExtensionsWorkspace property={property} operations={operations} businessDate="2030-01-01" canManage canViewFinance canViewIntegrations canViewFrontDesk={false} canManageFrontDesk={false} canViewReports={false} onOperationsChange={() => {}} />);
        await waitFor(() => expect(api.get).toHaveBeenCalledWith('/api/pms/extensions', expect.anything()));
        expect(screen.queryByRole('heading', { name: 'Digitale Zimmerschlüssel' })).not.toBeInTheDocument();
        expect(screen.queryByRole('option', { name: /Schlüssel/ })).not.toBeInTheDocument();
    });
});
