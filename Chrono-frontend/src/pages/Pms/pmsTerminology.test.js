import { describe, expect, it } from 'vitest';
import {
    HOUSEKEEPING_TASK_TYPE_LABELS,
    PMS_ENUM_LABELS,
    RESERVATION_STATUS_LABELS,
    getFolioDisplayLabel,
    getPmsEnumLabel,
    getPmsEnumOptions,
} from './pmsTerminology.js';

const BACKEND_ENUM_VALUES = {
    CashShiftStatus: ['OPEN', 'CLOSED'],
    ChannelConnectionStatus: ['DISABLED', 'READY', 'SYNCING', 'ERROR'],
    ChannelEnvironment: ['SANDBOX', 'LIVE'],
    CommunicationChannel: ['EMAIL', 'SMS', 'WHATSAPP', 'OTA', 'PORTAL'],
    CommunicationDirection: ['INBOUND', 'OUTBOUND'],
    CommunicationStatus: ['DRAFT', 'QUEUED', 'SENT', 'RECEIVED', 'FAILED'],
    FolioItemType: ['ROOM', 'TAX', 'BREAKFAST', 'SERVICE', 'DISCOUNT', 'OTHER'],
    FolioStatus: ['OPEN', 'CLOSED'],
    GroupBookingStatus: ['OPTION', 'CONFIRMED', 'IN_HOUSE', 'COMPLETED', 'CANCELLED'],
    GuestRegistrationStatus: ['PENDING', 'COMPLETED'],
    HotelResourceType: ['CONFERENCE_ROOM', 'PARKING', 'SPA', 'RESTAURANT', 'BAR', 'EQUIPMENT', 'OTHER'],
    HousekeepingStatus: ['CLEAN', 'DIRTY', 'IN_PROGRESS', 'INSPECTION', 'OUT_OF_SERVICE'],
    HousekeepingTaskType: ['ARRIVAL', 'DEPARTURE', 'STAYOVER', 'INSPECTION', 'MANUAL'],
    InvoiceStatus: ['DRAFT', 'ISSUED', 'PAID', 'CREDITED', 'CANCELLED'],
    InvoiceType: ['INVOICE', 'CREDIT_NOTE'],
    MaintenancePriority: ['LOW', 'NORMAL', 'HIGH', 'CRITICAL'],
    MaintenanceStatus: ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CANCELLED'],
    OrganizationType: ['COMPANY', 'TRAVEL_AGENCY', 'EVENT_ORGANIZER'],
    OutboxStatus: ['PENDING', 'PROCESSING', 'DELIVERED', 'FAILED', 'DEAD_LETTER'],
    PaymentKind: ['PAYMENT', 'REFUND'],
    PaymentMethod: ['CASH', 'CARD', 'BANK_TRANSFER', 'VOUCHER', 'OTHER'],
    PaymentStatus: ['POSTED', 'VOIDED'],
    ReservationGuaranteeStatus: [
        'UNGUARANTEED',
        'CREDIT_CARD',
        'DEPOSIT_REQUIRED',
        'DEPOSIT_PAID',
        'COMPANY_GUARANTEE',
        'OTA_GUARANTEE',
    ],
    ReservationSource: ['DIRECT', 'PHONE', 'EMAIL', 'WALK_IN', 'BOOKING_ENGINE', 'CHANNEL_MANAGER'],
    ReservationStatus: [
        'OFFERED',
        'TENTATIVE',
        'WAITLISTED',
        'CONFIRMED',
        'CHECKED_IN',
        'CHECKED_OUT',
        'CANCELLED',
        'NO_SHOW',
    ],
    ResourceBookingStatus: ['TENTATIVE', 'CONFIRMED', 'CANCELLED'],
    RoomBlockStatus: ['ACTIVE', 'COMPLETED', 'CANCELLED'],
    RoomBlockType: ['OUT_OF_ORDER', 'OUT_OF_SERVICE', 'OWNER_USE'],
    RoomOperationalStatus: ['IN_SERVICE', 'OUT_OF_ORDER', 'INACTIVE'],
    OperationalHealthStatus: ['OK', 'WARNING', 'CRITICAL', 'NOT_CONFIGURED'],
};

describe('PMS terminology', () => {
    it('covers every audited PMS backend enum value', () => {
        expect(Object.keys(PMS_ENUM_LABELS)).toEqual(Object.keys(BACKEND_ENUM_VALUES));

        Object.entries(BACKEND_ENUM_VALUES).forEach(([enumType, values]) => {
            expect(Object.keys(PMS_ENUM_LABELS[enumType]), enumType).toEqual(values);
        });
    });

    it('uses readable Swiss German labels instead of technical enum values', () => {
        Object.entries(PMS_ENUM_LABELS).forEach(([enumType, labels]) => {
            Object.entries(labels).forEach(([value, label]) => {
                expect(label, `${enumType}.${value}`).toBeTypeOf('string');
                expect(label.trim(), `${enumType}.${value}`).not.toBe('');
                expect(label, `${enumType}.${value}`).not.toContain('_');
                expect(label, `${enumType}.${value}`).not.toContain('ß');
            });
        });

        expect(getPmsEnumLabel('ReservationStatus', 'NO_SHOW'))
            .toBe('Nicht angereist (No-Show)');
        expect(getPmsEnumLabel('RoomBlockType', 'OUT_OF_ORDER'))
            .toBe('Ausser Betrieb – nicht verkaufbar (OOO)');
        expect(getPmsEnumLabel('RoomBlockType', 'OUT_OF_SERVICE'))
            .toBe('Eingeschränkter Betrieb – weiterhin zuweisbar (OOS)');
        expect(getPmsEnumLabel('HousekeepingTaskType', 'STAYOVER'))
            .toBe('Aufenthaltsreinigung (Bleiber)');
    });

    it('accepts enum names and direct label maps', () => {
        expect(getPmsEnumLabel('ReservationStatus', ' confirmed '))
            .toBe('Bestätigt');
        expect(getPmsEnumLabel(RESERVATION_STATUS_LABELS, 'CHECKED_IN'))
            .toBe('Eingecheckt');
    });

    it('never leaks unknown or empty raw backend values', () => {
        expect(getPmsEnumLabel('ReservationStatus', 'FUTURE_INTERNAL_STATUS'))
            .toBe('Unbekannt');
        expect(getPmsEnumLabel('UnknownEnum', 'INTERNAL_VALUE'))
            .toBe('Unbekannt');
        expect(getPmsEnumLabel('ReservationStatus', null))
            .toBe('Nicht angegeben');
        expect(getPmsEnumLabel('ReservationStatus', '   '))
            .toBe('Nicht angegeben');
        expect(getPmsEnumLabel('ReservationStatus', 'FUTURE_INTERNAL_STATUS', 'Status nicht verfügbar'))
            .toBe('Status nicht verfügbar');
    });

    it('creates safe select options from a known map only', () => {
        expect(getPmsEnumOptions(HOUSEKEEPING_TASK_TYPE_LABELS)).toEqual([
            { value: 'ARRIVAL', label: 'Anreisevorbereitung' },
            { value: 'DEPARTURE', label: 'Abreisereinigung' },
            { value: 'STAYOVER', label: 'Aufenthaltsreinigung (Bleiber)' },
            { value: 'INSPECTION', label: 'Zimmerkontrolle' },
            { value: 'MANUAL', label: 'Manuelle Aufgabe' },
        ]);
        expect(getPmsEnumOptions('MissingEnum')).toEqual([]);
    });

    it('translates the former default folio label without changing custom account names', () => {
        expect(getFolioDisplayLabel('Hauptfolio')).toBe('Hauptkonto');
        expect(getFolioDisplayLabel('Firmenkonto')).toBe('Firmenkonto');
        expect(getFolioDisplayLabel('')).toBe('Gastkonto');
    });

    it('keeps exported maps immutable', () => {
        expect(Object.isFrozen(PMS_ENUM_LABELS)).toBe(true);
        Object.values(PMS_ENUM_LABELS).forEach((labels) => {
            expect(Object.isFrozen(labels)).toBe(true);
        });
    });
});
