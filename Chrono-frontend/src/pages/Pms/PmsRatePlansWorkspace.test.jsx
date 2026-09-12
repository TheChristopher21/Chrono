/** @vitest-environment jsdom */
import React from 'react';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
const apiMock = vi.hoisted(() => ({ post: vi.fn(), put: vi.fn() }));
vi.mock('../../utils/api.js', () => ({ default: apiMock }));
import PmsRatePlansWorkspace, { ratePricePreview } from './PmsRatePlansWorkspace.jsx';

const property = { id: 9, name: 'Hotel Singapore', currencyCode: 'SGD', roomTypes: [{ id: 2, name: 'Doppelzimmer' }] };
const rate = { id: 7, roomTypeId: 2, code: 'CORP', name: 'Firmenvereinbarung', nightlyRate: 120, minStay: 2,
    maxStay: 14, vatRate: 7, taxIncluded: false, breakfastIncluded: true, breakfastAmount: 20, breakfastVatRate: 19,
    includedAdults: 1, extraAdultRate: 15, childRate: 8, active: true, refundable: true, validFrom: '2030-01-01',
    cancellationPolicy: '48 Stunden vor Anreise', depositPercent: 20, organizationId: 4, organizationName: 'Firma' };
const operations = { ratePlans: [rate], organizations: [{ id: 4, name: 'Firma', active: true }], rateOverrides: [] };
beforeEach(() => { vi.clearAllMocks(); apiMock.put.mockResolvedValue({ data: operations }); apiMock.post.mockResolvedValue({ data: operations }); });
afterEach(cleanup);

describe('expanded rate plans', () => {
    it('preserves inclusive totals and splits two net tax bases', () => {
        expect(ratePricePreview({ ...rate, taxIncluded: true })).toEqual({ gross: 120, net: 110.27, tax: 9.73 });
        expect(ratePricePreview(rate)).toEqual({ gross: 130.8, net: 120, tax: 10.8 });
    });

    it('loads and saves the full corporate tax and policy configuration', async () => {
        const onOperationsChange = vi.fn();
        render(<PmsRatePlansWorkspace property={property} operations={operations} canManage businessDate="2030-01-01" onOperationsChange={onOperationsChange} />);
        fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }));
        expect(screen.getByLabelText('Steuer Beherbergung (%)').value).toBe('7');
        expect(screen.getByLabelText('Stornobedingungen').value).toBe('48 Stunden vor Anreise');
        fireEvent.change(screen.getByLabelText('Steuer Beherbergung (%)'), { target: { value: '9' } });
        fireEvent.click(screen.getByRole('button', { name: 'Ratenplan speichern' }));
        await waitFor(() => expect(apiMock.put).toHaveBeenCalledWith('/api/pms/properties/9/rate-plans/7', expect.objectContaining({
            vatRate: 9, taxIncluded: false, breakfastAmount: 20, breakfastVatRate: 19, maxStay: 14,
            extraAdultRate: 15, childRate: 8, organizationId: 4, cancellationPolicy: '48 Stunden vor Anreise', depositPercent: 20,
        }), { params: { businessDate: '2030-01-01' } }));
        expect(onOperationsChange).toHaveBeenCalledWith(operations);
    });

    it('keeps all rate write controls disabled for staff without master access', () => {
        render(<PmsRatePlansWorkspace property={property} operations={operations} canManage={false} />);
        fireEvent.click(screen.getByRole('button', { name: 'Ansehen' }));
        expect(screen.getByLabelText('Steuer Beherbergung (%)').closest('fieldset').disabled).toBe(true);
        fireEvent.submit(screen.getByRole('button', { name: 'Ratenplan speichern' }).closest('form'));
        expect(apiMock.put).not.toHaveBeenCalled();
        expect(apiMock.post).not.toHaveBeenCalled();
    });

    it('saves stop-sell using the existing daily override API', async () => {
        render(<PmsRatePlansWorkspace property={property} operations={operations} canManage businessDate="2030-01-01" />);
        fireEvent.change(screen.getByLabelText('Ratenplan'), { target: { value: '7' } });
        fireEvent.change(screen.getByLabelText('Tagespreis'), { target: { value: '150' } });
        fireEvent.click(screen.getByLabelText('Verkauf geschlossen (Stop Sell)'));
        fireEvent.click(screen.getByRole('button', { name: 'Tagesrate speichern' }));
        await waitFor(() => expect(apiMock.put).toHaveBeenCalledWith('/api/pms/properties/9/rate-plans/7/override', expect.objectContaining({
            stayDate: '2030-01-01', price: 150, closed: true,
        }), expect.any(Object)));
    });
});
