/** @vitest-environment jsdom */
import React from 'react';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
const apiMock=vi.hoisted(() => ({ get:vi.fn(),post:vi.fn() }));
vi.mock('../../utils/api.js',() => ({ default:apiMock }));
import PmsCentralRatesPanel from './PmsCentralRatesPanel.jsx';
const property={id:9,name:'Zürich',currencyCode:'CHF'};
const hotel={id:10,name:'Kuwait',currencyCode:'KWD',roomTypes:[{id:20,name:'Deluxe'}]};
const source={id:7,code:'CORP',name:'Firmenrate',nightlyRate:120,breakfastIncluded:false,taxIncluded:true};
let existing=[];
beforeEach(() => {
    vi.clearAllMocks();existing=[];
    apiMock.get.mockImplementation((path) => Promise.resolve({data:path.endsWith('/setup') ? {properties:[property,hotel]} : {ratePlans:existing}}));
    apiMock.post.mockResolvedValue({data:{targets:[{propertyId:10,ratePlanId:30,code:'CORP',currencyCode:'KWD'}]}});
});
afterEach(cleanup);
async function targetFor(rate=source) {
    render(<PmsCentralRatesPanel property={property} rates={[rate]} />);
    await screen.findByRole('option',{name:'Kuwait · KWD'});
    fireEvent.change(screen.getByLabelText('Gespeicherte Ratenvorlage'),{target:{value:'7'}});
    fireEvent.change(screen.getByLabelText('Zielhotel'),{target:{value:'10'}});
    fireEvent.click(screen.getByRole('button',{name:'Zielhotel hinzufügen'}));
    const card=await screen.findByRole('article',{name:'Zielrate Kuwait'});
    fireEvent.change(within(card).getByLabelText('Ziel-Zimmertyp'),{target:{value:'20'}});
    return card;
}
describe('central hotel rate publication',() => {
    it('requires explicit local prices and publishes only the selected hotel in its own currency',async () => {
        const card=await targetFor();
        expect(within(card).getByLabelText('Nachtpreis (KWD)').value).toBe('');
        expect(within(card).getByLabelText('Nachtpreis (KWD)').step).toBe('0.001');
        fireEvent.submit(screen.getByRole('button',{name:'1 Zielrate veröffentlichen'}).closest('form'));
        expect(apiMock.post).not.toHaveBeenCalled();
        for(const [label,value] of [['Nachtpreis (KWD)','42.123'],['Weiterer Erwachsener (KWD)','0'],['Kind (KWD)','0'],['Steuer Beherbergung im Zielhotel (%)','0']])
            fireEvent.change(within(card).getByLabelText(label),{target:{value}});
        fireEvent.click(screen.getByRole('button',{name:'1 Zielrate veröffentlichen'}));
        await waitFor(() => expect(apiMock.post).toHaveBeenCalledWith('/api/pms/rate-plans/7/publish',{targets:[expect.objectContaining({propertyId:10,roomTypeId:20,targetRatePlanId:null,currencyCode:'KWD',nightlyRate:42.123,vatRate:0,breakfastAmount:0,extraAdultRate:0,childRate:0})]}));
        expect(apiMock.get).toHaveBeenCalledWith('/api/pms/setup',expect.objectContaining({params:{includeRooms:false}}));
    });
    it('requires selecting the existing target rate before updating it and uses its local values',async () => {
        existing=[{id:30,roomTypeId:20,code:'LOCAL',name:'Lokale Rate',nightlyRate:22.125,breakfastAmount:0,extraAdultRate:0,childRate:0,vatRate:0,policyFeeTaxRate:0}];
        const card=await targetFor({...source,noShowFeePercent:100});
        await within(card).findByRole('option',{name:'LOCAL · bestehende Rate aktualisieren'});
        fireEvent.change(within(card).getByLabelText('Zielrate'),{target:{value:'30'}});
        expect(within(card).getByLabelText('Nachtpreis (KWD)').value).toBe('22.125');
        fireEvent.click(screen.getByRole('button',{name:'1 Zielrate veröffentlichen'}));
        await waitFor(() => expect(apiMock.post).toHaveBeenCalledWith(expect.any(String),{targets:[expect.objectContaining({targetRatePlanId:30,code:'LOCAL',nightlyRate:22.125,policyFeeTaxRate:0})]}));
    });
    it('keeps the selected target form when the server rejects publication',async () => {
        apiMock.post.mockRejectedValue({response:{data:{detail:'Zielratencode existiert bereits.'}}});
        const card=await targetFor();
        for(const label of ['Nachtpreis (KWD)','Weiterer Erwachsener (KWD)','Kind (KWD)','Steuer Beherbergung im Zielhotel (%)']) fireEvent.change(within(card).getByLabelText(label),{target:{value:'0'}});
        fireEvent.click(screen.getByRole('button',{name:'1 Zielrate veröffentlichen'}));
        await screen.findByText('Zielratencode existiert bereits.');
        expect(screen.getByRole('article',{name:'Zielrate Kuwait'})).toBeTruthy();
    });
});
