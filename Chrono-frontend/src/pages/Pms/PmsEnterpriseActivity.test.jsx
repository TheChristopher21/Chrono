/** @vitest-environment jsdom */
import React, { Activity } from 'react';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
const apiMock=vi.hoisted(() => ({ get:vi.fn(),post:vi.fn(),put:vi.fn() }));
vi.mock('../../utils/api.js',() => ({default:apiMock}));
import PmsEventOrderPanel from './PmsEventOrderPanel.jsx';
import PmsAccountingSettingsPanel from './PmsAccountingSettingsPanel.jsx';
import PmsStayDetailsPanel from './PmsStayDetailsPanel.jsx';
import PmsPaymentRequestsPanel from './PmsPaymentRequestsPanel.jsx';
import PmsReceivablesWorkspace from './PmsReceivablesWorkspace.jsx';
import PmsGroupOperations from './PmsGroupOperations.jsx';
import PmsExtensionsWorkspace from './PmsExtensionsWorkspace.jsx';
import PmsFinancialActionDialog from './PmsFinancialActionDialog.jsx';
const property={id:5,name:'Hotel',currencyCode:'CHF',timezone:'UTC'};
const order={status:'DRAFT',setupMinutes:15,teardownMinutes:15,agenda:'Gespeichert',lines:[],resourceName:'Saal',attendees:10,netAmount:0,taxAmount:0,grossAmount:0,currencyCode:'CHF'};
function pane(element) {
    const result=render(<Activity mode="visible">{element}</Activity>);
    return { ...result, cycle:async () => { await act(async () => result.rerender(<Activity mode="hidden">{element}</Activity>)); await act(async () => result.rerender(<Activity mode="visible">{element}</Activity>)); } };
}
beforeEach(() => {vi.clearAllMocks();apiMock.post.mockResolvedValue({data:{}});apiMock.put.mockResolvedValue({data:{}});});
afterEach(cleanup);
describe('enterprise drafts in retained workspace panes',() => {
    it('keeps an uncertain partial refund frozen and unclosable across Activity switches', async () => {
        const submit = vi.fn().mockRejectedValueOnce(new Error('Lost response')).mockResolvedValueOnce(true);
        const close = vi.fn();
        const mounted = pane(<PmsFinancialActionDialog payment={{ id: 4, amount: 100, method: 'CARD' }} currency="CHF" canSubmit onSubmit={submit} onClose={close} />);
        fireEvent.change(screen.getByLabelText('Erstattungsbetrag (CHF)'), { target: { value: '20' } });
        fireEvent.change(screen.getByLabelText('Begründung'), { target: { value: 'Frühstück reklamiert' } });
        fireEvent.submit(screen.getByRole('button', { name: 'Erstattung ausführen' }).closest('form'));
        await screen.findByRole('alert');
        const request = submit.mock.calls[0][0];
        expect(screen.getByLabelText('Erstattungsbetrag (CHF)')).toBeDisabled();
        expect(screen.getByLabelText('Begründung')).toBeDisabled();
        expect(screen.getByRole('button', { name: 'Abbrechen' })).toBeDisabled();
        expect(screen.getByRole('button', { name: 'Schliessen' })).toBeDisabled();
        fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' });
        fireEvent.mouseDown(screen.getByRole('dialog').parentElement);
        expect(close).not.toHaveBeenCalled();
        await mounted.cycle();
        fireEvent.submit(screen.getByRole('button', { name: 'Bestehende Erstattung erneut prüfen' }).closest('form'));
        await waitFor(() => expect(submit).toHaveBeenCalledTimes(2));
        expect(submit.mock.calls[1][0]).toEqual(request);
        await waitFor(() => expect(close).toHaveBeenCalledOnce());
    });
    it('unlocks a first refund attempt after a definitive validation rejection and respects withdrawn refund rights', async () => {
        const submit = vi.fn().mockRejectedValue({ response: { status: 400, data: { detail: 'Betrag ungültig' } } });
        const props = { payment: { id: 4, amount: 100, method: 'CARD' }, currency: 'CHF', onSubmit: submit, onClose: vi.fn() };
        const view = render(<PmsFinancialActionDialog {...props} canSubmit />);
        fireEvent.change(screen.getByLabelText('Begründung'), { target: { value: 'Reklamation' } });
        fireEvent.submit(screen.getByRole('button', { name: 'Erstattung ausführen' }).closest('form'));
        await screen.findByText('Betrag ungültig'); expect(screen.getByRole('button', { name: 'Abbrechen' })).toBeEnabled();
        expect(screen.getByLabelText('Erstattungsbetrag (CHF)')).toBeEnabled();
        view.rerender(<PmsFinancialActionDialog {...props} canSubmit={false} />);
        fireEvent.submit(screen.getByRole('button', { name: 'Erstattung ausführen' }).closest('form'));
        expect(submit).toHaveBeenCalledOnce();
    });
    it('retains booking-engine and tourism-tax edits across tab switches and unrelated saves', async () => {
        const stored = { bookingEngine: { publicSlug: 'saved-hotel', enabled: true, requireGuarantee: false }, tourismTax: { enabled: true, name: 'Kurtaxe', adultRate: 2, childRate: 1, childFreeUnder: 16 } };
        apiMock.get.mockImplementation(async (path) => ({ data: path === '/api/pms/extensions' ? stored : [] }));
        apiMock.put.mockImplementation(async (_path, body) => ({ data: { ...stored, bookingEngine: body } }));
        const mounted = pane(<PmsExtensionsWorkspace property={property} operations={{ reservations: [], folios: [] }} businessDate="2030-01-01" canManage canManageSettings canViewReports={false} canViewFinance={false} canViewIntegrations={false} canViewFrontDesk={false} />);
        await screen.findByDisplayValue('saved-hotel');
        fireEvent.change(screen.getByLabelText(/Öffentlicher Buchungsname/), { target: { value: 'draft-hotel' } });
        fireEvent.change(screen.getByLabelText('Erwachsene/Nacht'), { target: { value: '5.30' } });
        await mounted.cycle();
        await waitFor(() => expect(apiMock.get.mock.calls.filter(([path]) => path === '/api/pms/extensions')).toHaveLength(2));
        expect(screen.getByLabelText(/Öffentlicher Buchungsname/).value).toBe('draft-hotel');
        expect(screen.getByLabelText('Erwachsene/Nacht').value).toBe('5.30');
        fireEvent.submit(screen.getByRole('button', { name: 'Booking Engine speichern' }).closest('form'));
        await waitFor(() => expect(apiMock.put).toHaveBeenCalled());
        expect(screen.getByLabelText('Erwachsene/Nacht').value).toBe('5.30');
    });
    it('retains dirty event-order text when Activity restarts loading effects',async () => {
        apiMock.get.mockResolvedValue({data:order});
        const mounted=pane(<PmsEventOrderPanel property={property} bookings={[{id:1,title:'Tagung'}]} canManage />);
        fireEvent.change(screen.getByLabelText('Veranstaltung'),{target:{value:'1'}});
        await screen.findByLabelText('Ablauf / Agenda');fireEvent.change(screen.getByLabelText('Ablauf / Agenda'),{target:{value:'Ungespeicherter Ablauf'}});
        await mounted.cycle();await waitFor(() => expect(apiMock.get).toHaveBeenCalledTimes(2));
        expect(screen.getByLabelText('Ablauf / Agenda').value).toBe('Ungespeicherter Ablauf');
        expect(screen.getByRole('button',{name:'Auftrag speichern'}).disabled).toBe(false);
    });
    it('does not replace a selected event with a late response for the prior booking',async () => {
        let resolveFirst;apiMock.get.mockImplementation((path) => path.includes('/1/') ? new Promise((resolve) => {resolveFirst=resolve;}) : Promise.resolve({data:{...order,agenda:'Zweite Veranstaltung'}}));
        render(<PmsEventOrderPanel property={property} bookings={[{id:1,title:'Erste'},{id:2,title:'Zweite'}]} canManage />);
        fireEvent.change(screen.getByLabelText('Veranstaltung'),{target:{value:'1'}});fireEvent.change(screen.getByLabelText('Veranstaltung'),{target:{value:'2'}});
        await screen.findByDisplayValue('Zweite Veranstaltung');await act(async () => resolveFirst({data:order}));
        expect(screen.getByLabelText('Ablauf / Agenda').value).toBe('Zweite Veranstaltung');
    });
    it('preserves dirty hotel accounting mappings when a pane is hidden and restored',async () => {
        apiMock.get.mockResolvedValue({data:{guestReceivableAccount:'1100',corporateReceivableAccount:'1200',bankAccount:'1000',posRevenueAccount:'3000',revenueAccounts:{},paymentAccounts:{}}});
        const mounted=pane(<PmsAccountingSettingsPanel property={property} canManage />);
        await screen.findByLabelText('Forderungen aus Gastkonten');fireEvent.change(screen.getByLabelText('Forderungen aus Gastkonten'),{target:{value:'1199'}});
        await mounted.cycle();expect(screen.getByLabelText('Forderungen aus Gastkonten').value).toBe('1199');expect(apiMock.get).toHaveBeenCalledTimes(1);
    });
    it('preserves unsaved accompanying guest dates while refreshing stay metadata',async () => {
        apiMock.get.mockImplementation((path) => Promise.resolve({data:path.endsWith('/policy') ? {} : {roomSegments:[],coGuests:[{guestId:9,guestName:'Anna Gast',arrivalDate:'2030-01-01',departureDate:'2030-01-05',child:false}]}}));
        const mounted=pane(<PmsStayDetailsPanel property={property} reservation={{id:1,guestId:8,arrivalDate:'2030-01-01',departureDate:'2030-01-05',status:'CONFIRMED'}} operations={{rooms:[],ratePlans:[]}} canManage onClose={() => {}} />);
        await screen.findByLabelText('Anreise Anna Gast');fireEvent.change(screen.getByLabelText('Anreise Anna Gast'),{target:{value:'2030-01-02'}});
        await mounted.cycle();expect(screen.getByLabelText('Anreise Anna Gast').value).toBe('2030-01-02');
    });
    it('freezes an uncertain payment creation and retries the exact request after tab switches',async () => {
        apiMock.get.mockResolvedValue({data:[]});apiMock.post.mockRejectedValueOnce(new Error('Network lost'));
        const mounted=pane(<PmsPaymentRequestsPanel property={property} folios={[{id:8,status:'OPEN',guestName:'Gast',balance:120}]} canManage />);
        fireEvent.change(screen.getByLabelText('Gastkonto für Kartenzahlung'),{target:{value:'8'}});fireEvent.click(screen.getByRole('button',{name:'Sicheren Zahlungslink erstellen'}));
        await screen.findByRole('alert');const submitted=apiMock.post.mock.calls[0][1];expect(screen.getByLabelText('Betrag (CHF)').disabled).toBe(true);
        await mounted.cycle();fireEvent.click(screen.getByRole('button',{name:'Bestehenden Auftrag erneut prüfen'}));
        await waitFor(() => expect(apiMock.post).toHaveBeenCalledTimes(2));expect(apiMock.post.mock.calls[1][1]).toEqual(submitted);
        await waitFor(() => expect(screen.getByLabelText('Betrag (CHF)').disabled).toBe(false));
    });
    it('retains a frozen bank settlement and its idempotency key after uncertain submission',async () => {
        apiMock.get.mockImplementation((path) => Promise.resolve({data:path.endsWith('/credit-accounts') ? [] : {items:[{id:7,invoiceNumber:'R-7',organizationName:'Firma',dueDate:'2030-01-01',balance:120,reminderLevel:0}],totalElements:1,hasNext:false,totalOutstanding:120,totalOverdue:0}}));
        apiMock.post.mockRejectedValueOnce(new Error('Network lost'));
        const mounted=pane(<PmsReceivablesWorkspace property={property} canManage />);
        fireEvent.click(await screen.findByRole('button',{name:'Zahlung zuordnen'}));fireEvent.change(screen.getByLabelText('Bankreferenz'),{target:{value:'BANK-1'}});fireEvent.click(screen.getByRole('button',{name:'Bankbewegung buchen'}));
        await screen.findByRole('alert');const submitted=apiMock.post.mock.calls[0][1];expect(screen.getByLabelText('Bankreferenz').disabled).toBe(true);
        await mounted.cycle();fireEvent.click(screen.getByRole('button',{name:'Dieselbe Bankbewegung erneut prüfen'}));
        await waitFor(() => expect(apiMock.post).toHaveBeenCalledTimes(2));expect(apiMock.post.mock.calls[1][1]).toEqual(submitted);
    });
    it('keeps unsaved group routing when Activity refreshes saved group details',async () => {
        const group={id:1,name:'Tagung',groupCode:'G-1',arrivalDate:'2030-01-01',departureDate:'2030-01-05',rooms:[]};
        apiMock.get.mockImplementation((path) => Promise.resolve({data:path.endsWith('/directory/groups/1') ? group : {groupId:1,routedTypes:[],allotments:[]}}));
        const mounted=pane(<PmsGroupOperations property={property} groups={[group]} operations={{ratePlans:[]}} canManage />);
        fireEvent.change(screen.getByLabelText('Gruppe bearbeiten'),{target:{value:'1'}});
        await screen.findByText('Leistungen automatisch auf Gruppen-Masterkonto buchen');
        const routing=screen.getByText('Leistungen automatisch auf Gruppen-Masterkonto buchen').closest('details'); routing.open=true;
        const firstType=within(routing).getAllByRole('checkbox')[0];fireEvent.click(firstType);
        await mounted.cycle();await waitFor(() => expect(apiMock.get.mock.calls.filter(([path]) => path.endsWith('/groups/1/operations'))).toHaveLength(2));
        expect(firstType.checked).toBe(true);expect(screen.getByLabelText('Gruppe bearbeiten').disabled).toBe(true);
        fireEvent.click(screen.getByRole('button',{name:'Zuordnung speichern'}));
        await waitFor(() => expect(apiMock.put).toHaveBeenCalledWith('/api/pms/properties/5/groups/1/routing',{types:['ROOM']}));
    });
    it('sends an explicit age for each child added through the group rooming list',async () => {
        apiMock.get.mockImplementation((path) => Promise.resolve({data:path.includes('/guests/search') ? [{id:9,firstName:'Anna',lastName:'Gast'}] : {routedTypes:[],allotments:[]}}));
        render(<PmsGroupOperations property={{...property,roomTypes:[{id:2,name:'Familie'}]}} groups={[{id:1,name:'Tagung',groupCode:'G-1',rooms:[],arrivalDate:'2030-01-01',departureDate:'2030-01-05'}]} operations={{ratePlans:[{id:3,roomTypeId:2,name:'Familienrate',active:true}]}} canManage />);
        fireEvent.change(screen.getByLabelText('Gruppe bearbeiten'),{target:{value:'1'}});
        const title=await screen.findByText('Gast zur Zimmerliste hinzufügen'); const panel=title.closest('details');panel.open=true;const form=within(panel);
        fireEvent.change(form.getByLabelText('Gast suchen'),{target:{value:'Anna'}});await form.findByRole('option',{name:'Anna Gast'});
        fireEvent.change(form.getByLabelText('Gast auswählen'),{target:{value:'9'}});fireEvent.change(form.getByLabelText('Zimmertyp'),{target:{value:'2'}});fireEvent.change(form.getByLabelText('Rate'),{target:{value:'3'}});
        fireEvent.change(form.getByLabelText('Kinder'),{target:{value:'2'}});fireEvent.change(form.getByLabelText('Alter Kind 1'),{target:{value:'0'}});fireEvent.change(form.getByLabelText('Alter Kind 2'),{target:{value:'8'}});fireEvent.click(form.getByRole('button',{name:'Teilnehmer reservieren'}));
        await waitFor(() => expect(apiMock.post).toHaveBeenCalledWith('/api/pms/properties/5/groups/1/rooming-list',{rooms:[expect.objectContaining({guestId:9,roomTypeId:2,ratePlanId:3,children:2,childAges:[0,8]})]}));
    });
});
