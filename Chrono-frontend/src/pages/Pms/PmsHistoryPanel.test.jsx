/** @vitest-environment jsdom */
import React from 'react';
import { cleanup,fireEvent,render,screen,waitFor } from '@testing-library/react';
import { afterEach,beforeEach,describe,expect,it,vi } from 'vitest';
const apiMock=vi.hoisted(() => ({get:vi.fn()}));
vi.mock('../../utils/api.js',() => ({default:apiMock}));
import PmsHistoryPanel from './PmsHistoryPanel.jsx';
beforeEach(() => {
    vi.clearAllMocks();apiMock.get.mockImplementation((path,{params}) => Promise.resolve({data:{section:'invoices',items:[{id:params.page+1,invoiceNumber:`INV-${params.page+1}`,grossAmount:123,currencyCode:'JPY'}],page:params.page,size:params.size,totalElements:60,hasNext:params.page<2}}));
});
afterEach(cleanup);
describe('paged hotel history',() => {
    it('retrieves old pages and resets pagination after debounced search',async () => {
        render(<PmsHistoryPanel propertyId={5} sections={['invoices','communications']} />);
        await screen.findByText('INV-1');fireEvent.click(screen.getByRole('button',{name:'Nächste Seite'}));await screen.findByText('INV-2');
        fireEvent.change(screen.getByLabelText('Verlauf durchsuchen'),{target:{value:'Acme'}});
        await waitFor(() => expect(apiMock.get).toHaveBeenLastCalledWith('/api/pms/properties/5/history/invoices',expect.objectContaining({params:{page:0,size:25,query:'Acme'}})));
        fireEvent.change(screen.getByLabelText('Verlaufsbereich'),{target:{value:'communications'}});
        await waitFor(() => expect(apiMock.get).toHaveBeenLastCalledWith('/api/pms/properties/5/history/communications',expect.objectContaining({params:{page:0,size:25,query:'Acme'}})));
    });
    it('shows only allowed sections, bounds page sizes and reports server failures',async () => {
        apiMock.get.mockRejectedValue({response:{data:{detail:'Keine Berechtigung für diesen Hotelbereich.'}}});
        render(<PmsHistoryPanel propertyId={5} sections={['outbox']} />);
        await screen.findByRole('alert');expect(screen.queryByRole('option',{name:'Rechnungen'})).toBeNull();
        expect(screen.getByLabelText('Einträge pro Seite').options.length).toBe(4);expect(screen.getByRole('button',{name:'Nächste Seite'}).disabled).toBe(true);
    });
    it('does not fetch anything when no history module is granted',() => {
        render(<PmsHistoryPanel propertyId={5} sections={[]} />);expect(apiMock.get).not.toHaveBeenCalled();
    });
    it.each([
        ['invoices', { type: 'INVOICE', status: 'ISSUED' }, ['Rechnung', 'Ausgestellt']],
        ['communications', { channel: 'EMAIL', direction: 'OUTBOUND', status: 'QUEUED' }, ['E-Mail', 'Ausgehend', 'Versand eingeplant']],
        ['outbox', { status: 'DEAD_LETTER' }, ['Endgültig fehlgeschlagen']],
        ['guest-registrations', { status: 'COMPLETED' }, ['Meldeschein übermittelt']],
        ['resource-bookings', { status: 'TENTATIVE' }, ['Option']],
        ['pos-tickets', { status: 'SETTLED', paymentMethod: 'CARD' }, ['Abgerechnet', 'Kartenzahlung']],
        ['access-credentials', { status: 'REVOKED' }, ['Widerrufen']],
        ['migration-batches', { status: 'RECONCILIATION_REQUIRED' }, ['Abstimmung erforderlich']],
    ])('renders known %s enum cells using the terminology for that history kind', async (section, fields, expected) => {
        const row = Object.freeze({ id: 1, ...fields });
        apiMock.get.mockResolvedValue({ data: { section, items: [row], page: 0, size: 25, totalElements: 1, hasNext: false } });
        render(<PmsHistoryPanel propertyId={5} sections={[section]} />);
        for (const label of expected) expect(await screen.findByRole('cell', { name: label, exact: true })).toBeTruthy();
        expect(row).toEqual({ id: 1, ...fields });
        expect(apiMock.get).toHaveBeenCalledWith(`/api/pms/properties/5/history/${section}`, expect.objectContaining({ params: { page: 0, size: 25, query: undefined } }));
    });
    it('changes only enum presentation and preserves free text and original response values', async () => {
        const row = Object.freeze({ id: 1, invoiceNumber: 'INVOICE', recipientName: 'ISSUED', type: 'CREDIT_NOTE', status: 'CREDITED' });
        apiMock.get.mockResolvedValue({ data: { section: 'invoices', items: [row], page: 0, size: 25, totalElements: 1, hasNext: false } });
        render(<PmsHistoryPanel propertyId={5} sections={['invoices']} />);
        await screen.findByRole('cell', { name: 'Gutschrift', exact: true });
        expect(screen.getByRole('cell', { name: 'Gutgeschrieben', exact: true })).toBeTruthy();
        expect(screen.getByRole('cell', { name: 'INVOICE', exact: true })).toBeTruthy();
        expect(screen.getByRole('cell', { name: 'ISSUED', exact: true })).toBeTruthy();
        expect(row).toEqual({ id: 1, invoiceNumber: 'INVOICE', recipientName: 'ISSUED', type: 'CREDIT_NOTE', status: 'CREDITED' });
    });
});
