/** @vitest-environment jsdom */
import React from 'react';
import { cleanup,fireEvent,render,screen,waitFor } from '@testing-library/react';
import { MemoryRouter,Route,Routes } from 'react-router-dom';
import { afterEach,beforeEach,describe,it,expect,vi } from 'vitest';
const api=vi.hoisted(()=>({get:vi.fn(),post:vi.fn(),put:vi.fn(),patch:vi.fn()}));
vi.mock('../../utils/api.js',()=>({default:api}));
import PmsRevenueAutomationPanel from './PmsRevenueAutomationPanel.jsx';
import PmsEventOffersPanel from './PmsEventOffersPanel.jsx';
import PmsEventOfferPage from './PmsEventOfferPage.jsx';
import PmsRateInheritancePanel from './PmsRateInheritancePanel.jsx';
const property={id:1,name:'Hotel',currencyCode:'CHF'};
const order={title:'Meeting',resourceName:'Hall',attendees:4,currencyCode:'CHF',grossAmount:110,netAmount:100,startAt:'2026-10-12T10:00:00',endAt:'2026-10-12T12:00:00',lines:[{id:1,description:'Room',quantity:1,netUnitPrice:100,taxRate:10,grossAmount:110}]};
const offer={id:2,version:1,status:'OPEN',order,terms:'Agreed services',validUntil:'2026-10-10T12:00:00',contentHash:'a'.repeat(64)};
beforeEach(()=>{vi.clearAllMocks();api.post.mockResolvedValue({data:{}});api.put.mockResolvedValue({data:{}});api.patch.mockResolvedValue({data:{}});});afterEach(cleanup);
describe('commercial workflows',()=>{
 it('shows missing forecast history without reporting zero and keeps dirty rules after failure',async()=>{
  const config={propertyId:1,version:0,snapshotsEnabled:true,pricingEnabled:false,horizonDays:30,minSamples:8,rules:[]};
  api.get.mockImplementation(path=>Promise.resolve({data:path.endsWith('automation')?config:{propertyId:1,observedDays:0,capacity:10,model:'Observed data',suggestions:[],days:[{date:'2026-10-12',onBooks:2,expectedRoomNights:null,samples:0,seasonalSamples:0,pickupSamples:0}]}}));
  api.put.mockRejectedValue({response:{data:{detail:'Die Regeln wurden inzwischen geändert.'}}});
  render(<PmsRevenueAutomationPanel property={property} canManageRates isMaster/>);await screen.findByText('Daten fehlen');fireEvent.change(screen.getByLabelText('Mindestzahl vergleichbarer Tage'),{target:{value:'12'}});fireEvent.click(screen.getByRole('button',{name:'Regeln speichern'}));await screen.findByText('Die Regeln wurden inzwischen geändert.');expect(screen.getByLabelText('Mindestzahl vergleichbarer Tage').value).toBe('12');
 });
 it('creates a customer link explicitly without dispatching messages',async()=>{
  api.get.mockResolvedValue({data:[offer]});api.post.mockResolvedValue({data:{token:'t'.repeat(43)}});
  render(<PmsEventOffersPanel property={property} bookings={[{id:5,title:'Meeting',startAt:order.startAt,status:'CONFIRMED'}]} canManage/>);fireEvent.change(screen.getByLabelText('Veranstaltung für das Angebot'),{target:{value:'5'}});fireEvent.click(await screen.findByRole('button',{name:'Kundenlink erstellen'}));await waitFor(()=>expect(screen.getByLabelText('Kundenlink für diese Angebotsversion').value).toContain('/pms-event-offer/'));expect(api.post).toHaveBeenCalledTimes(1);expect(api.post).toHaveBeenCalledWith('/api/pms/properties/1/resource-bookings/5/offers/2/link');
 });
 it('requires an explicit name and consent and then displays the recorded decision',async()=>{
  api.get.mockResolvedValue({data:offer});api.post.mockResolvedValue({data:{...offer,status:'ACCEPTED',signatureName:'Alex Guest',decidedAt:'2026-10-09T10:00:00'}});
  render(<MemoryRouter initialEntries={['/offer/token']}><Routes><Route path="/offer/:token" element={<PmsEventOfferPage/>}/></Routes></MemoryRouter>);const accept=await screen.findByRole('button',{name:'Angebot verbindlich annehmen'});expect(accept.disabled).toBe(true);fireEvent.change(screen.getByLabelText('Vollständiger Name'),{target:{value:'Alex Guest'}});expect(accept.disabled).toBe(true);fireEvent.click(screen.getByRole('checkbox'));fireEvent.click(accept);await screen.findByText('Entscheidung dokumentiert');expect(api.post).toHaveBeenCalledWith('/api/public/pms/event-offers/token/decision',expect.objectContaining({decision:'ACCEPTED',signatureName:'Alex Guest',consent:true}));
 });
 it('local rate freeze uses the saved version and needs no master account',async()=>{
  const rule={id:7,version:4,targetRateName:'Local',sourceRateName:'Parent',sourceHotel:'HQ',targetCurrency:'CHF',sourceCurrency:'CHF',manualFxRate:1,previewPrice:100,adjustmentPercent:0,enabled:true,frozen:false};api.get.mockResolvedValue({data:[rule]});
  render(<PmsRateInheritancePanel property={property} canManage/>);fireEvent.click(await screen.findByRole('button',{name:'Als lokale Ausnahme einfrieren'}));await waitFor(()=>expect(api.patch).toHaveBeenCalledWith('/api/pms/properties/1/rate-inheritance/7/freeze',{frozen:true,expectedVersion:4}));expect(screen.queryByRole('button',{name:'Jetzt übernehmen'})).toBeNull();
 });
});
