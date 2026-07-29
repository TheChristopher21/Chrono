/** @vitest-environment jsdom */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

const apiMock = vi.hoisted(() => ({
    post: vi.fn(),
    put: vi.fn(),
}));

vi.mock('../../utils/api.js', () => ({ default: apiMock }));

import PmsSetupWorkspace from './PmsSetupWorkspace.jsx';

const emptySetup = {
    properties: [],
    totalProperties: 0,
    totalRoomTypes: 0,
    totalRooms: 0,
    foundationComplete: false,
};

describe('PmsSetupWorkspace', () => {
    it('creates a hotel with normalized PMS master data and advances to room types', async () => {
        const savedSetup = {
            properties: [{
                id: 8,
                code: 'BASEL',
                name: 'Hotel Basel',
                countryCode: 'CH',
                currencyCode: 'CHF',
                timezone: 'Europe/Zurich',
                checkInTime: '15:00:00',
                checkOutTime: '11:00:00',
                active: true,
                roomTypes: [],
                rooms: [],
            }],
            totalProperties: 1,
            totalRoomTypes: 0,
            totalRooms: 0,
            foundationComplete: false,
        };
        apiMock.post.mockResolvedValueOnce({ data: savedSetup });
        const onSetupChange = vi.fn();
        const onPropertyChange = vi.fn();

        render(
            <PmsSetupWorkspace
                setup={emptySetup}
                activePropertyId={null}
                canManage
                onSetupChange={onSetupChange}
                onPropertyChange={onPropertyChange}
                onClose={vi.fn()}
            />
        );

        await userEvent.type(screen.getByLabelText('Hotelcode'), 'BASEL');
        await userEvent.type(screen.getByLabelText('Hotelname'), 'Hotel Basel');
        await userEvent.click(screen.getByRole('button', { name: 'Hotel speichern' }));

        expect(apiMock.post).toHaveBeenCalledWith('/api/pms/properties', expect.objectContaining({
            code: 'BASEL',
            name: 'Hotel Basel',
            currencyCode: 'CHF',
            timezone: 'Europe/Zurich',
        }));
        expect(onSetupChange).toHaveBeenCalledWith(savedSetup);
        expect(onPropertyChange).toHaveBeenCalledWith(8);
        expect(await screen.findByRole('button', { name: /2 Zimmertypen/ })).toHaveClass('is-active');
    });

    it('keeps all setup writes disabled for view-only users', () => {
        render(
            <PmsSetupWorkspace
                setup={emptySetup}
                activePropertyId={null}
                canManage={false}
                onSetupChange={vi.fn()}
                onPropertyChange={vi.fn()}
                onClose={vi.fn()}
            />
        );

        expect(screen.getByText(/Du hast Lesezugriff/)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Hotel speichern' })).toBeDisabled();
    });

    it('closes from the backdrop without closing when the dialog itself is clicked', () => {
        const onClose = vi.fn();
        render(
            <PmsSetupWorkspace
                setup={emptySetup}
                activePropertyId={null}
                canManage
                onSetupChange={vi.fn()}
                onPropertyChange={vi.fn()}
                onClose={onClose}
            />
        );

        const dialog = screen.getByRole('dialog', { name: 'Hoteleinrichtung' });
        fireEvent.mouseDown(dialog);
        expect(onClose).not.toHaveBeenCalled();

        fireEvent.mouseDown(dialog.parentElement);
        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('distinguishes all operational room statuses in hotel language', async () => {
        const configuredSetup = {
            properties: [{
                id: 5,
                code: 'ZRH',
                name: 'Chrono Zürich',
                roomTypes: [{ id: 10, code: 'DBL', name: 'Doppelzimmer', roomCount: 3 }],
                rooms: [
                    { id: 1, number: '101', roomTypeCode: 'DBL', roomTypeName: 'Doppelzimmer', operationalStatus: 'IN_SERVICE' },
                    { id: 2, number: '102', roomTypeCode: 'DBL', roomTypeName: 'Doppelzimmer', operationalStatus: 'OUT_OF_ORDER' },
                    { id: 3, number: '103', roomTypeCode: 'DBL', roomTypeName: 'Doppelzimmer', operationalStatus: 'INACTIVE' },
                ],
            }],
            totalProperties: 1,
            totalRoomTypes: 1,
            totalRooms: 3,
            foundationComplete: true,
        };

        render(
            <PmsSetupWorkspace
                setup={configuredSetup}
                activePropertyId={5}
                canManage
                onSetupChange={vi.fn()}
                onPropertyChange={vi.fn()}
                onClose={vi.fn()}
            />
        );

        await userEvent.click(screen.getByRole('button', { name: /3 Zimmer/ }));

        expect(screen.getByLabelText('Betriebs-/Verkaufsstatus')).toBeInTheDocument();
        expect(screen.getAllByText('In Betrieb und verkaufbar')).toHaveLength(2);
        expect(screen.getAllByText('Technisch ausser Betrieb – nicht verkaufbar')).toHaveLength(2);
        expect(screen.getAllByText('Inaktiv (nicht im Verkauf)')).toHaveLength(2);
        expect(screen.queryByText('Gesperrt')).not.toBeInTheDocument();
    });
});
