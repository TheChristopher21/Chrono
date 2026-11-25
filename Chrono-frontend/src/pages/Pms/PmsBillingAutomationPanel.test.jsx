/** @vitest-environment jsdom */
import React, { Activity } from 'react';
import { cleanup, fireEvent, render, screen, waitFor, act } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }));
vi.mock('../../utils/api.js', () => ({ default: api }));
import PmsBillingAutomationPanel from './PmsBillingAutomationPanel.jsx';
import PmsBankReconciliationPanel from './PmsBankReconciliationPanel.jsx';
const settings = { version: 1, mailEnabled: true, senderEmail: 'hotel@example.test', senderName: 'Hotel', replyTo: null, automaticInvoices: false, invoiceLanguage: 'DE', automaticReminders: false, sendReminders: false, firstReminderDays: 7, reminderIntervalDays: 14, maxReminders: 3, invoiceSubject: 'Invoice', invoiceBody: 'Attached', reminderSubject: 'Reminder', reminderBody: 'Outstanding' };
const invoice = { id: 41, invoiceNumber: 'INV-41', recipientName: 'ACME' };
const batch = { id: 8, filename: 'bank.csv', sha256: 'hash', rowCount: 1, rows: [{ id: 71, bankAccount: 'MAIN', externalId: 'BANK-71', bookingDate: '2026-09-12', currencyCode: 'CHF', amount: 40, reference: 'INV-41', debtorName: 'ACME', status: 'OPEN', suggestions: [{ receivableId: 5, invoiceNumber: 'INV-41', organization: 'ACME', outstandingAmount: 40 }] }] };
beforeEach(() => {
    vi.clearAllMocks(); sessionStorage.clear();
    api.get.mockImplementation((url) => Promise.resolve({ data: url.endsWith('/settings') ? settings : url.includes('/deliveries?') ? { items: [], hasNext: false } : url.endsWith('/bank-imports/8') ? batch : url.endsWith('/bank-imports') ? [batch] : [] }));
    api.post.mockResolvedValue({ data: {} }); api.put.mockResolvedValue({ data: settings });
});
afterEach(cleanup);

it('keeps sending and master settings unavailable to finance readers', async () => {
    render(<PmsBillingAutomationPanel propertyId={1} invoices={[invoice]} />);
    expect(await screen.findByLabelText('Absender-E-Mail')).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Versandregeln speichern' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Rechnung zum Versand einplanen' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Fällige Mahnungen nach Hotelregel anlegen' })).toBeNull();
});
it('retries an uncertain invoice response with the same frozen recipient and request id', async () => {
    api.post.mockRejectedValueOnce(new Error('network interrupted')).mockResolvedValueOnce({ data: { id: 10 } });
    render(<PmsBillingAutomationPanel propertyId={1} canManage invoices={[invoice]} />);
    fireEvent.change(screen.getByLabelText('Rechnung'), { target: { value: '41' } });
    fireEvent.change(screen.getByLabelText(/Empfänger-E-Mail/), { target: { value: 'chosen@example.test' } });
    fireEvent.submit(screen.getByRole('button', { name: 'Rechnung zum Versand einplanen' }).closest('form'));
    await screen.findByRole('alert');
    expect(screen.getByLabelText(/Empfänger-E-Mail/)).toBeDisabled();
    const first = api.post.mock.calls[0];
    fireEvent.submit(screen.getByRole('button', { name: 'Denselben Rechnungsversand wiederholen' }).closest('form'));
    await waitFor(() => expect(api.post).toHaveBeenCalledTimes(2));
    expect(api.post.mock.calls[1]).toEqual(first); expect(first[1].recipient).toBe('chosen@example.test');
});
it('requires an acknowledgement before repeating UNKNOWN SMTP acceptance', async () => {
    api.get.mockImplementation((url) => Promise.resolve({ data: url.endsWith('/settings') ? settings : url.includes('/deliveries?') ? { items: [{ id: 90, recipient: 'guest@example.test', subject: 'Invoice', status: 'UNKNOWN', attempts: 1, attachments: [] }], hasNext: false } : [] }));
    render(<PmsBillingAutomationPanel propertyId={1} canManage />);
    fireEvent.click(await screen.findByRole('button', { name: 'Erneut prüfen' }));
    expect(screen.getByRole('button', { name: 'Versandwiederholung bestätigen' })).toBeDisabled();
    fireEvent.click(screen.getByLabelText('Mögliche Doppelzustellung geprüft und akzeptiert'));
    fireEvent.click(screen.getByRole('button', { name: 'Versandwiederholung bestätigen' }));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/pms/properties/1/billing/deliveries/90/retry', { acknowledgePossibleDuplicate: true }));
});
it('keeps master setting drafts when the journal refreshes', async () => {
    render(<PmsBillingAutomationPanel propertyId={1} canManage canMaster />);
    fireEvent.change(await screen.findByLabelText('Absendername'), { target: { value: 'Unsaved sender' } });
    fireEvent.click(screen.getByRole('button', { name: 'Aktualisieren' }));
    await waitFor(() => expect(api.get.mock.calls.length).toBeGreaterThan(4));
    expect(screen.getByLabelText('Absendername')).toHaveValue('Unsaved sender');
});
it('does not auto-select a bank suggestion or post until explicit confirmation', async () => {
    render(<PmsBankReconciliationPanel propertyId={1} canManage businessDate="2026-09-12" />);
    await waitFor(() => expect(screen.getByRole('option', { name: /bank.csv/ })).toBeTruthy());
    fireEvent.change(screen.getByLabelText('Gespeicherter Bankimport'), { target: { value: '8' } });
    const select = await screen.findByLabelText('Forderung für BANK-71'); expect(select).toHaveValue(''); expect(api.post).not.toHaveBeenCalled();
    fireEvent.change(select, { target: { value: '5' } });
    expect(screen.getByRole('button', { name: 'Ausgewählte Forderungen ausgleichen' })).toBeDisabled();
    fireEvent.click(screen.getByLabelText('Bankeingänge und ausgewählte Rechnungen geprüft'));
    api.post.mockResolvedValueOnce({ data: { ...batch, rows: [{ ...batch.rows[0], status: 'MATCHED', receivableId: 5 }] } });
    fireEvent.click(screen.getByRole('button', { name: 'Ausgewählte Forderungen ausgleichen' }));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/pms/properties/1/billing/bank-imports/8/confirm', { matches: [{ transactionId: 71, receivableId: 5 }] }));
});
it('hides all bank and export mutation controls for readers', async () => {
    render(<PmsBankReconciliationPanel propertyId={1} />);
    await waitFor(() => expect(api.get).toHaveBeenCalledTimes(2));
    expect(screen.queryByLabelText('Bankdatei')).toBeNull(); expect(screen.queryByRole('button', { name: 'Exportlauf erzeugen' })).toBeNull();
});
it('ignores a previous hotel settings response after changing property', async () => {
    let finish; api.get.mockImplementation((url) => url.includes('/properties/1/') && url.endsWith('/settings') ? new Promise((resolve) => { finish = resolve; }) : Promise.resolve({ data: url.endsWith('/settings') ? { ...settings, senderName: 'New hotel' } : url.includes('/deliveries?') ? { items: [], hasNext: false } : [] }));
    const view = render(<PmsBillingAutomationPanel propertyId={1} canMaster />);
    view.rerender(<PmsBillingAutomationPanel propertyId={2} canMaster />);
    expect(await screen.findByLabelText('Absendername')).toHaveValue('New hotel');
    await act(async () => finish({ data: { ...settings, senderName: 'Old hotel' } }));
    expect(screen.getByLabelText('Absendername')).toHaveValue('New hotel');
});
it('keeps unsent invoice, message and master settings drafts through Activity restoration and refresh', async () => {
    const content = <PmsBillingAutomationPanel propertyId={1} canManage canMaster invoices={[invoice]} />;
    const view = render(<Activity mode="visible">{content}</Activity>);
    fireEvent.change(await screen.findByLabelText('Absendername'), { target: { value: 'Draft sender' } });
    fireEvent.change(screen.getByLabelText('Rechnung'), { target: { value: '41' } });
    fireEvent.change(screen.getByLabelText(/Empfänger-E-Mail/), { target: { value: 'draft@example.test' } });
    fireEvent.change(screen.getByLabelText('Betreff'), { target: { value: 'Unsent custom message' } });
    view.rerender(<Activity mode="hidden">{content}</Activity>);
    view.rerender(<Activity mode="visible">{content}</Activity>);
    fireEvent.click(screen.getByRole('button', { name: 'Aktualisieren' }));
    await waitFor(() => expect(api.get.mock.calls.length).toBeGreaterThan(8));
    expect(screen.getByLabelText('Absendername')).toHaveValue('Draft sender');
    expect(screen.getByLabelText('Rechnung')).toHaveValue('41');
    expect(screen.getByLabelText(/Empfänger-E-Mail/)).toHaveValue('draft@example.test');
    expect(screen.getByLabelText('Betreff')).toHaveValue('Unsent custom message'); expect(api.post).not.toHaveBeenCalled();
});
it('does not remain busy when an invoice send finishes while its Activity is hidden', async () => {
    let finish; api.post.mockImplementationOnce(() => new Promise((resolve) => { finish = resolve; }));
    const content = <PmsBillingAutomationPanel propertyId={1} canManage invoices={[invoice]} />;
    const view = render(<Activity mode="visible">{content}</Activity>);
    fireEvent.change(screen.getByLabelText('Rechnung'), { target: { value: '41' } });
    fireEvent.submit(screen.getByRole('button', { name: 'Rechnung zum Versand einplanen' }).closest('form'));
    await waitFor(() => expect(api.post).toHaveBeenCalledTimes(1));
    view.rerender(<Activity mode="hidden">{content}</Activity>);
    await act(async () => finish({ data: { id: 13 } }));
    view.rerender(<Activity mode="visible">{content}</Activity>);
    expect(await screen.findByRole('button', { name: 'Rechnung zum Versand einplanen' })).not.toBeDisabled();
    expect(sessionStorage.getItem('pms-invoice-send:1')).toBeNull(); expect(api.post).toHaveBeenCalledTimes(1);
});
it('preserves bank selections and export date drafts across Activity changes and background reads', async () => {
    const content = <PmsBankReconciliationPanel propertyId={1} canManage businessDate="2026-09-12" />;
    const view = render(<Activity mode="visible">{content}</Activity>);
    await waitFor(() => expect(screen.getByRole('option', { name: /bank.csv/ })).toBeTruthy());
    fireEvent.change(screen.getByLabelText('Gespeicherter Bankimport'), { target: { value: '8' } });
    fireEvent.change(await screen.findByLabelText('Forderung für BANK-71'), { target: { value: '5' } });
    fireEvent.change(screen.getByLabelText('Ab Buchungstag'), { target: { value: '2026-09-01' } });
    fireEvent.change(screen.getByLabelText('Bis Datum (ausschließlich)'), { target: { value: '2026-09-13' } });
    view.rerender(<Activity mode="hidden">{content}</Activity>); view.rerender(<Activity mode="visible">{content}</Activity>);
    fireEvent.click(screen.getByRole('button', { name: 'Aktualisieren' }));
    await waitFor(() => expect(screen.getByLabelText('Forderung für BANK-71')).toHaveValue('5'));
    expect(screen.getByLabelText('Ab Buchungstag')).toHaveValue('2026-09-01'); expect(screen.getByLabelText('Bis Datum (ausschließlich)')).toHaveValue('2026-09-13'); expect(api.post).not.toHaveBeenCalled();
});
it('ignores a previous hotel bank preview that completes after switching property', async () => {
    let finish; api.get.mockImplementation((url) => url.endsWith('/bank-imports/8') ? new Promise((resolve) => { finish = resolve; }) : Promise.resolve({ data: url.includes('/properties/1/') && url.endsWith('/bank-imports') ? [batch] : [] }));
    const view = render(<PmsBankReconciliationPanel propertyId={1} canManage businessDate="2026-09-12" />);
    await waitFor(() => expect(screen.getByRole('option', { name: /bank.csv/ })).toBeTruthy());
    fireEvent.change(screen.getByLabelText('Gespeicherter Bankimport'), { target: { value: '8' } });
    view.rerender(<PmsBankReconciliationPanel propertyId={2} canManage businessDate="2026-09-12" />);
    await act(async () => finish({ data: batch }));
    expect(screen.queryByLabelText('Forderung für BANK-71')).toBeNull(); expect(screen.getByLabelText('Gespeicherter Bankimport')).toHaveValue('');
});
