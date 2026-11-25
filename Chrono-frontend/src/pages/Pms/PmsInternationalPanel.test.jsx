/** @vitest-environment jsdom */
import React, { Activity } from 'react';
import { act,cleanup,fireEvent,render,screen,waitFor } from '@testing-library/react';
import { afterEach,beforeEach,it,expect,vi } from 'vitest';
const api=vi.hoisted(()=>({get:vi.fn(),put:vi.fn(),post:vi.fn()}));
vi.mock('../../utils/api.js',()=>({default:api}));
import PmsInternationalPanel from './PmsInternationalPanel.jsx';
const property={id:1,name:'Hotel',currencyCode:'CHF'},settings={propertyId:1,version:3,countryCode:'CH',ruleCode:'CH-LOCAL',jurisdictionLabel:'Local',sourceReference:'Approved reference',requiredFields:['city'],availableFields:['city','documentNumber'],taxScheme:'VAT',zeroTaxCategory:null,zeroTaxReason:null,configured:true};
beforeEach(()=>{vi.clearAllMocks();api.get.mockImplementation(path=>Promise.resolve({data:path.endsWith('international-settings')?settings:{items:[{id:2,invoiceNumber:'CH-002',recipientName:'Client'}]}}));});afterEach(cleanup);
it('keeps original rule version and invoice-specific tax text when a financial subarea is hidden and restored', async () => {
 const element=<PmsInternationalPanel property={property} isMaster canManageFinance/>;
 const view=render(<Activity mode="visible">{element}</Activity>);
 fireEvent.click(await screen.findByLabelText('Ausweisnummer verpflichtend'));
 fireEvent.change(screen.getByLabelText('Begründung dieser Rechnung'),{target:{value:'Einzelfallbegründung'}});
 api.get.mockImplementation(path=>Promise.resolve({data:path.endsWith('international-settings')?{...settings,version:4}:{items:[]}}));
 await act(async()=>view.rerender(<Activity mode="hidden">{element}</Activity>));
 await act(async()=>view.rerender(<Activity mode="visible">{element}</Activity>));
 expect(screen.getByLabelText('Ausweisnummer verpflichtend')).toBeChecked();
 expect(screen.getByLabelText('Begründung dieser Rechnung')).toHaveValue('Einzelfallbegründung');
 api.put.mockRejectedValue({response:{status:409,data:{detail:'Neue Version liegt vor.'}}});
 fireEvent.click(screen.getByRole('button',{name:'Lokale Regeln speichern'}));
 await screen.findByText('Neue Version liegt vor.');
 expect(api.put).toHaveBeenCalledWith(expect.any(String),expect.objectContaining({version:3}));
});
it('preserves changed required fields when optimistic save is rejected',async()=>{api.put.mockRejectedValue({response:{data:{detail:'Neue Version liegt vor.'}}});render(<PmsInternationalPanel property={property} isMaster canManageFinance/>);fireEvent.click(await screen.findByLabelText('Ausweisnummer verpflichtend'));fireEvent.click(screen.getByRole('button',{name:'Lokale Regeln speichern'}));await screen.findByText('Neue Version liegt vor.');expect(screen.getByLabelText('Ausweisnummer verpflichtend').checked).toBe(true);expect(api.put).toHaveBeenCalledWith('/api/pms/properties/1/international-settings',expect.objectContaining({version:3,requiredFields:['city','documentNumber']}));});
it('requires an explicit prepaid allocation before storing a validated UBL artifact',async()=>{api.post.mockResolvedValue({data:{invoiceId:2,invoiceNumber:'CH-002',ready:true,stored:true,errors:[],scope:'Official XSD',contentHash:'hash'}});render(<PmsInternationalPanel property={property} canManageFinance/>);await screen.findByRole('option',{name:'CH-002 · Client'});fireEvent.change(screen.getByLabelText('Rechnung auswählen'),{target:{value:'2'}});const create=screen.getByRole('button',{name:'UBL-Datei validieren und fest speichern'});expect(create.disabled).toBe(true);fireEvent.change(screen.getByLabelText('Dieser Rechnung zugeordnete Vorauszahlung'),{target:{value:'20'}});fireEvent.click(create);await waitFor(()=>expect(api.post).toHaveBeenCalledWith('/api/pms/properties/1/invoices/2/structured-invoice',{zeroTaxCategory:null,zeroTaxReason:null,prepaidAmount:20}));await screen.findByText(/Prüfung bestanden/);expect(screen.getByLabelText('Dieser Rechnung zugeordnete Vorauszahlung').disabled).toBe(true);});
