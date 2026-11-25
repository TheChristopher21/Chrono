/** @vitest-environment jsdom */
import React from 'react';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const apiMock = vi.hoisted(() => ({
    get: vi.fn(),
    post: vi.fn(),
}));

vi.mock('../../utils/api.js', () => ({ default: apiMock }));

import PmsReceptionBookingFlow from './PmsReceptionBookingFlow.jsx';

const property = {
    id: 5,
    name: 'Chrono Zürich',
    currencyCode: 'CHF',
};

const localDateKey = (value) => [
    value.getFullYear(),
    String(value.getMonth() + 1).padStart(2, '0'),
    String(value.getDate()).padStart(2, '0'),
].join('-');

const TODAY = localDateKey(new Date());
const tomorrow = new Date();
tomorrow.setDate(tomorrow.getDate() + 1);
const TOMORROW = localDateKey(tomorrow);
const dayAfterTomorrow = new Date();
dayAfterTomorrow.setDate(dayAfterTomorrow.getDate() + 2);
const DAY_AFTER_TOMORROW = localDateKey(dayAfterTomorrow);

const existingGuest = {
    id: 7,
    firstName: 'Gabriela',
    lastName: 'Tschopp',
    email: 'gabriela@example.com',
    phone: '+41 79 111 22 33',
    dateOfBirth: '1988-05-12',
    nationalityCode: 'CH',
};

const cleanRoom = {
    id: 30,
    roomTypeId: 10,
    roomTypeName: 'Doppelzimmer',
    number: '101',
    operationalStatus: 'IN_SERVICE',
    housekeepingStatus: 'CLEAN',
    currentReservation: null,
};

const baseOperations = {
    guests: [existingGuest],
    rooms: [cleanRoom],
    roomBlocks: [],
    reservations: [],
    folios: [],
};

const availability = {
    roomTypes: [{
        roomTypeId: 10,
        name: 'Doppelzimmer',
        totalRooms: 1,
        availableRooms: 1,
        rates: [{
            ratePlanId: 20,
            name: 'Beste Rate',
            currencyCode: 'CHF',
            totalAmount: 160,
            available: true,
        }],
        freeRooms: [{
            roomId: 30,
            roomNumber: '101',
            floor: '1',
            housekeepingStatus: 'CLEAN',
            checkInReady: true,
        }],
    }],
};

const successfulResult = ({
    guestId = 8,
    guestName = 'Mara Muster',
    roomNumber = '101',
    reservationStatus = 'CHECKED_IN',
    registrationStatus = 'COMPLETED',
} = {}) => ({
    guestId,
    reservationId: 60,
    folioId: 70,
    confirmationCode: 'CHR-WALK-60',
    totalAmount: 160,
    balance: 160,
    registrationStatus,
    reservationStatus,
    checkInReady: true,
    checkInBlockers: [],
    operations: {
        ...baseOperations,
        reservations: [{
            id: 60,
            confirmationCode: 'CHR-WALK-60',
            guestId,
            guestName,
            roomTypeId: 10,
            roomTypeName: 'Doppelzimmer',
            roomId: roomNumber ? 30 : null,
            roomNumber,
            ratePlanId: 20,
            ratePlanName: 'Beste Rate',
            currencyCode: 'CHF',
            totalAmount: 160,
            status: reservationStatus,
        }],
        folios: [{
            id: 70,
            reservationId: 60,
            confirmationCode: 'CHR-WALK-60',
            guestName,
            currencyCode: 'CHF',
            balance: 160,
        }],
    },
});

const renderFlow = (overrides = {}) => {
    const onOperationsChange = vi.fn();
    const onComplete = vi.fn();
    const view = render(
        <PmsReceptionBookingFlow
            property={property}
            operations={baseOperations}
            businessDate="2026-09-01"
            canManage
            mode="reservation"
            onOperationsChange={onOperationsChange}
            onComplete={onComplete}
            {...overrides}
        />,
    );
    return { ...view, onOperationsChange, onComplete };
};

const enterAvailabilityStep = async () => {
    await userEvent.click(screen.getByRole('button', { name: 'Weiter zur Verfügbarkeit' }));
    await screen.findByRole('radio', { name: /Doppelzimmer · Beste Rate/ });
};

const selectRateAndContinue = async ({ room = false } = {}) => {
    await userEvent.click(screen.getByRole('radio', { name: /Doppelzimmer · Beste Rate/ }));
    if (room) await userEvent.click(screen.getByRole('radio', { name: /Zimmer 101/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Weiter zum Gast' }));
};

const fillWalkInGuestAndRegistration = async () => {
    await userEvent.click(screen.getByRole('button', { name: 'Neuen Gast erfassen' }));
    const guestGroup = screen.getByRole('group', { name: 'Neues Gastprofil' });
    await userEvent.type(within(guestGroup).getByLabelText('Vorname'), 'Mara');
    await userEvent.type(within(guestGroup).getByLabelText('Nachname'), 'Muster');
    fireEvent.change(within(guestGroup).getByLabelText('Geburtsdatum'), { target: { value: '1990-04-03' } });
    await userEvent.type(within(guestGroup).getByLabelText('E-Mail'), 'mara@example.com');
    await userEvent.type(within(guestGroup).getByLabelText('Telefon'), '+41791234567');

    const registrationGroup = screen.getByRole('group', { name: 'Pflicht-Meldedaten für den Walk-in' });
    await userEvent.type(within(registrationGroup).getByLabelText('Adresse'), 'Musterweg 12');
    await userEvent.type(within(registrationGroup).getByLabelText('PLZ'), '8000');
    await userEvent.type(within(registrationGroup).getByLabelText('Ort'), 'Zürich');
    await userEvent.type(within(registrationGroup).getByLabelText('Ausweis- oder Passnummer'), 'PASS-998877');
    await userEvent.type(within(registrationGroup).getByLabelText('Kennzeichen (optional)'), 'ZH 12345');
    await userEvent.type(within(registrationGroup).getByLabelText('Unterschrift / vollständiger Name'), 'Mara Muster');
    await userEvent.click(within(registrationGroup).getByRole('checkbox'));
};

describe('PmsReceptionBookingFlow', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        apiMock.get.mockImplementation((url) => {
            if (url.endsWith('/availability')) return Promise.resolve({ data: availability });
            if (url.endsWith('/guests/search')) return Promise.resolve({ data: [existingGuest] });
            return Promise.resolve({ data: [] });
        });
        apiMock.post.mockResolvedValue({ data: successfulResult() });
    });

    it('captures one validated age for every child before checking availability', async () => {
        renderFlow();

        fireEvent.change(screen.getByLabelText('Kinder'), { target: { value: '2' } });
        expect(screen.getByLabelText('Alter Kind 1')).toBeRequired();
        expect(screen.getByLabelText('Alter Kind 2')).toBeRequired();

        fireEvent.submit(screen.getByRole('button', { name: 'Weiter zur Verfügbarkeit' }).closest('form'));
        expect(screen.getByRole('alert')).toHaveTextContent('für jedes Kind ein Alter');

        fireEvent.change(screen.getByLabelText('Alter Kind 1'), { target: { value: '4' } });
        fireEvent.change(screen.getByLabelText('Alter Kind 2'), { target: { value: '9' } });
        await userEvent.click(screen.getByRole('button', { name: 'Weiter zur Verfügbarkeit' }));
        expect(await screen.findByRole('radio', { name: /Doppelzimmer · Beste Rate/ })).toBeInTheDocument();
    });

    it('fixes a walk-in arrival to today while allowing a multi-night stay', async () => {
        renderFlow({ mode: 'walk-in', businessDate: '2030-01-15' });
        await act(async () => {
            await Promise.resolve();
        });

        expect(screen.getByLabelText('Anreise')).toHaveAttribute('readonly');
        expect(screen.getByLabelText('Anreise')).toHaveValue(TODAY);
        expect(screen.getByLabelText('Abreise')).not.toHaveAttribute('readonly');

        await act(async () => {
            fireEvent.change(screen.getByLabelText('Abreise'), {
                target: { value: DAY_AFTER_TOMORROW },
            });
            await Promise.resolve();
        });

        expect(screen.getByLabelText('Abreise')).toHaveValue(DAY_AFTER_TOMORROW);
    });

    it('completes a walk-in atomically with a new guest, registration and immediate check-in', async () => {
        const { onOperationsChange } = renderFlow({
            mode: 'walk-in',
            businessDate: '2030-01-15',
        });

        expect(screen.getByLabelText('Anreise')).toHaveValue(TODAY);
        expect(screen.getByLabelText('Abreise')).toHaveValue(TOMORROW);
        expect(screen.getByLabelText('Buchungsquelle')).toHaveValue('WALK_IN');

        await enterAvailabilityStep();
        await selectRateAndContinue({ room: true });
        await fillWalkInGuestAndRegistration();
        await userEvent.click(screen.getByRole('button', { name: 'Weiter zur Prüfung' }));
        await userEvent.click(screen.getByRole('button', { name: 'Walk-in anlegen und einchecken' }));

        expect(apiMock.post).toHaveBeenCalledWith(
            `/api/pms/properties/5/front-desk-bookings?businessDate=${TODAY}`,
            {
                existingGuestId: null,
                newGuest: {
                    privateEmail: '', businessEmail: '', additionalEmails: [], dietaryNotes: '', vatNumber: '',
                    organizationContactId: null, billingOverride: false, billingProfile: {},
                    firstName: 'Mara',
                    lastName: 'Muster',
                    email: 'mara@example.com',
                    phone: '+41791234567',
                    dateOfBirth: '1990-04-03',
                    nationalityCode: 'CH',
                    languageCode: 'de',
                    addressLine1: 'Musterweg 12',
                    postalCode: '8000',
                    city: 'Zürich',
                    countryCode: 'CH',
                    vehiclePlate: 'ZH 12345',
                    roomPreferences: null,
                    organizationId: null,
                    notes: null,
                    vip: false,
                },
                roomTypeId: 10,
                roomId: 30,
                ratePlanId: 20,
                arrivalDate: TODAY,
                departureDate: TOMORROW,
                adults: 1,
                children: 0,
                childAges: [],
                source: 'WALK_IN',
                guaranteeStatus: 'UNGUARANTEED',
                notes: null,
                registration: {
                    addressLine: 'Musterweg 12',
                    postalCode: '8000',
                    city: 'Zürich',
                    countryCode: 'CH',
                    nationalityCode: 'CH',
                    documentNumber: 'PASS-998877',
                    vehiclePlate: 'ZH 12345',
                    signatureName: 'Mara Muster',
                    privacyConsent: true,
                },
                checkInNow: true,
            },
            { headers: { 'Idempotency-Key': expect.stringMatching(/^[A-Za-z0-9._:-]{16,80}$/) } },
        );
        expect(await screen.findByRole('heading', { name: 'Walk-in erfolgreich aufgenommen' })).toBeInTheDocument();
        expect(screen.getByText(/CHR-WALK-60 · Mara Muster/)).toBeInTheDocument();
        expect(screen.getByText('Eingecheckt')).toBeInTheDocument();
        expect(onOperationsChange).toHaveBeenCalledWith(expect.objectContaining({
            reservations: [expect.objectContaining({ id: 60 })],
        }));
    });

    it('creates a direct reservation with an existing guest and leaves the room and registration open', async () => {
        const result = successfulResult({
            guestId: 7,
            guestName: 'Gabriela Tschopp',
            roomNumber: null,
            reservationStatus: 'CONFIRMED',
            registrationStatus: 'PENDING',
        });
        apiMock.post.mockResolvedValue({ data: result });
        const { onComplete } = renderFlow();

        await enterAvailabilityStep();
        await selectRateAndContinue();
        await userEvent.click(screen.getByRole('radio', { name: /Gabriela Tschopp/ }));
        await userEvent.click(screen.getByRole('button', { name: 'Weiter zur Prüfung' }));
        await userEvent.click(screen.getByRole('button', { name: 'Reservierung verbindlich anlegen' }));

        expect(apiMock.post).toHaveBeenCalledWith(
            '/api/pms/properties/5/front-desk-bookings?businessDate=2026-09-01',
            expect.objectContaining({
                existingGuestId: 7,
                newGuest: null,
                roomTypeId: 10,
                roomId: null,
                ratePlanId: 20,
                source: 'DIRECT',
                registration: null,
                checkInNow: false,
            }),
            expect.objectContaining({ headers: expect.objectContaining({ 'Idempotency-Key': expect.any(String) }) }),
        );
        expect(await screen.findByRole('heading', { name: 'Reservierung erfolgreich angelegt' })).toBeInTheDocument();
        expect(screen.getByText(/Zimmer wird später zugewiesen/)).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Gastkonto öffnen' }));
        expect(onComplete).toHaveBeenCalledWith(expect.objectContaining({
            action: 'folio',
            reservationId: 60,
            folioId: 70,
            guestName: 'Gabriela Tschopp',
        }));
    });

    it('loads availability automatically and exposes only backend-confirmed suitable walk-in rooms', async () => {
        const dirtyRoom = {
            ...cleanRoom,
            id: 31,
            number: '102',
            housekeepingStatus: 'DIRTY',
        };
        const outOfOrderRoom = {
            ...cleanRoom,
            id: 32,
            number: '103',
            operationalStatus: 'OUT_OF_ORDER',
        };
        const localOnlyRoom = { ...cleanRoom, id: 33, number: '104' };
        apiMock.get.mockResolvedValue({
            data: {
                ...availability,
                roomTypes: [{
                    ...availability.roomTypes[0],
                    freeRooms: [
                        { roomId: 30, roomNumber: '101', housekeepingStatus: 'CLEAN', checkInReady: true },
                        { roomId: 31, roomNumber: '102', housekeepingStatus: 'DIRTY', checkInReady: false },
                        { roomId: 32, roomNumber: '103', housekeepingStatus: 'CLEAN', checkInReady: false },
                    ],
                }],
            },
        });

        renderFlow({
            mode: 'walk-in',
            operations: {
                ...baseOperations,
                rooms: [cleanRoom, dirtyRoom, outOfOrderRoom, localOnlyRoom],
            },
        });

        await waitFor(() => expect(apiMock.get).toHaveBeenCalledWith(
            '/api/pms/properties/5/availability',
            expect.objectContaining({
                params: { arrival: TODAY, departure: TOMORROW, adults: 1, children: 0, guestId: undefined, organizationId: undefined },
                signal: expect.any(AbortSignal),
            }),
        ));
        await enterAvailabilityStep();
        await userEvent.click(screen.getByRole('radio', { name: /Doppelzimmer · Beste Rate/ }));

        expect(screen.getByRole('radio', { name: /Zimmer 101/ })).toBeInTheDocument();
        expect(screen.queryByRole('radio', { name: /Zimmer 102/ })).not.toBeInTheDocument();
        expect(screen.queryByRole('radio', { name: /Zimmer 103/ })).not.toBeInTheDocument();
        expect(screen.queryByRole('radio', { name: /Zimmer 104/ })).not.toBeInTheDocument();
    });

    it('copies the selected guest nationality into the legal registration data', async () => {
        const internationalGuest = {
            ...existingGuest,
            id: 9,
            firstName: 'Giulia',
            lastName: 'Bianchi',
            nationalityCode: 'IT',
            dateOfBirth: '1985-11-09',
        };
        renderFlow({
            mode: 'walk-in',
            operations: { ...baseOperations, guests: [internationalGuest] },
        });

        await enterAvailabilityStep();
        await selectRateAndContinue({ room: true });
        await userEvent.click(screen.getByRole('radio', { name: /Giulia Bianchi/ }));

        const registrationGroup = screen.getByRole('group', { name: 'Pflicht-Meldedaten für den Walk-in' });
        expect(within(registrationGroup).getByLabelText('Nationalität (ISO-Code)')).toHaveValue('IT');

        await userEvent.click(screen.getByRole('button', { name: 'Neuen Gast erfassen' }));
        const guestGroup = screen.getByRole('group', { name: 'Neues Gastprofil' });
        fireEvent.change(within(guestGroup).getByLabelText('Nationalität (ISO-Code)'), {
            target: { value: 'de' },
        });

        expect(within(guestGroup).getByLabelText('Nationalität (ISO-Code)')).toHaveValue('DE');
        expect(within(registrationGroup).getByLabelText('Nationalität (ISO-Code)')).toHaveValue('DE');
    });

    it('blocks incomplete steps before any booking mutation', async () => {
        renderFlow({ mode: 'walk-in' });
        await enterAvailabilityStep();

        await userEvent.click(screen.getByRole('button', { name: 'Weiter zum Gast' }));

        expect(screen.getByRole('alert')).toHaveTextContent('Bitte wähle einen verfügbaren Zimmertyp mit Rate.');
        expect(screen.getByRole('heading', { name: 'Verfügbarkeit, Rate und Zimmer' })).toBeInTheDocument();
        expect(apiMock.post).not.toHaveBeenCalled();
    });

    it('requires at least an email address or phone number for a new main guest', async () => {
        renderFlow();
        await enterAvailabilityStep();
        await selectRateAndContinue();
        await userEvent.click(screen.getByRole('button', { name: 'Neuen Gast erfassen' }));
        const guestGroup = screen.getByRole('group', { name: 'Neues Gastprofil' });
        await userEvent.type(within(guestGroup).getByLabelText('Vorname'), 'Noemi');
        await userEvent.type(within(guestGroup).getByLabelText('Nachname'), 'Neu');

        await userEvent.click(screen.getByRole('button', { name: 'Weiter zur Prüfung' }));

        expect(screen.getByRole('alert')).toHaveTextContent(
            'Für einen neuen Hauptgast ist mindestens E-Mail oder Telefon erforderlich.',
        );
        expect(screen.getByRole('heading', { name: 'Hauptgast und Meldedaten' })).toBeInTheDocument();
        expect(apiMock.post).not.toHaveBeenCalled();
    });

    it('prevents double submission, retains inputs after an error and reuses the idempotency key on retry', async () => {
        let rejectFirstRequest;
        apiMock.post
            .mockImplementationOnce(() => new Promise((resolve, reject) => {
                rejectFirstRequest = reject;
            }))
            .mockResolvedValueOnce({ data: successfulResult() });
        renderFlow({ mode: 'walk-in' });

        await enterAvailabilityStep();
        await selectRateAndContinue({ room: true });
        await fillWalkInGuestAndRegistration();
        await userEvent.click(screen.getByRole('button', { name: 'Weiter zur Prüfung' }));
        await userEvent.type(screen.getByLabelText('Reservierungsnotizen'), 'Späte Anreise');
        const submitButton = screen.getByRole('button', { name: 'Walk-in anlegen und einchecken' });

        await userEvent.click(submitButton);
        await userEvent.click(submitButton);
        await waitFor(() => expect(apiMock.post).toHaveBeenCalledTimes(1));

        await act(async () => {
            rejectFirstRequest({ response: { data: { detail: 'Zimmer wurde gerade vergeben.' } } });
        });
        expect(await screen.findByRole('alert')).toHaveTextContent('Zimmer wurde gerade vergeben.');
        expect(screen.getByLabelText('Reservierungsnotizen')).toHaveValue('Späte Anreise');

        await userEvent.click(screen.getByRole('button', { name: 'Zurück' }));
        expect(screen.getByLabelText('Ausweis- oder Passnummer')).toHaveValue('PASS-998877');
        expect(screen.getByLabelText('Vorname')).toHaveValue('Mara');
        await userEvent.click(screen.getByRole('button', { name: 'Weiter zur Prüfung' }));
        await userEvent.click(screen.getByRole('button', { name: 'Walk-in anlegen und einchecken' }));

        await waitFor(() => expect(apiMock.post).toHaveBeenCalledTimes(2));
        const firstKey = apiMock.post.mock.calls[0][2].headers['Idempotency-Key'];
        const secondKey = apiMock.post.mock.calls[1][2].headers['Idempotency-Key'];
        expect(secondKey).toBe(firstKey);
        expect(await screen.findByRole('heading', { name: 'Walk-in erfolgreich aufgenommen' })).toBeInTheDocument();
    });
});
