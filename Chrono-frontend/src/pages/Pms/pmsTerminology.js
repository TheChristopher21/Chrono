const freezeLabels = (labels) => Object.freeze(labels);

export const CASH_SHIFT_STATUS_LABELS = freezeLabels({
    OPEN: 'Offen',
    CLOSED: 'Geschlossen',
});

export const CHANNEL_CONNECTION_STATUS_LABELS = freezeLabels({
    DISABLED: 'Deaktiviert',
    READY: 'Betriebsbereit',
    SYNCING: 'Synchronisierung läuft',
    ERROR: 'Fehler',
});

export const CHANNEL_ENVIRONMENT_LABELS = freezeLabels({
    SANDBOX: 'Testbetrieb',
    LIVE: 'Produktivbetrieb',
});

export const COMMUNICATION_CHANNEL_LABELS = freezeLabels({
    EMAIL: 'E-Mail',
    SMS: 'SMS',
    WHATSAPP: 'WhatsApp',
    OTA: 'Buchungsportal (OTA)',
    PORTAL: 'Gästeportal',
});

export const COMMUNICATION_DIRECTION_LABELS = freezeLabels({
    INBOUND: 'Eingehend',
    OUTBOUND: 'Ausgehend',
});

export const COMMUNICATION_STATUS_LABELS = freezeLabels({
    DRAFT: 'Entwurf',
    QUEUED: 'Versand eingeplant',
    SENT: 'Gesendet',
    RECEIVED: 'Empfangen',
    FAILED: 'Fehlgeschlagen',
});

export const FOLIO_ITEM_TYPE_LABELS = freezeLabels({
    ROOM: 'Übernachtung',
    TAX: 'Kurtaxe / Beherbergungsabgabe',
    BREAKFAST: 'Frühstück',
    SERVICE: 'Zusatzleistung',
    DISCOUNT: 'Rabatt',
    OTHER: 'Sonstiges',
});

export const FOLIO_STATUS_LABELS = freezeLabels({
    OPEN: 'Offen',
    CLOSED: 'Geschlossen',
});

export const GROUP_BOOKING_STATUS_LABELS = freezeLabels({
    OPTION: 'Option',
    CONFIRMED: 'Bestätigt',
    IN_HOUSE: 'Gruppe im Haus',
    COMPLETED: 'Abgeschlossen',
    CANCELLED: 'Storniert',
});

export const GUEST_REGISTRATION_STATUS_LABELS = freezeLabels({
    PENDING: 'Noch nicht ausgefüllt',
    COMPLETED: 'Meldeschein übermittelt',
});

export const HOTEL_RESOURCE_TYPE_LABELS = freezeLabels({
    CONFERENCE_ROOM: 'Tagungsraum',
    PARKING: 'Parkplatz',
    SPA: 'Spa',
    RESTAURANT: 'Restaurant',
    BAR: 'Bar',
    EQUIPMENT: 'Ausstattung oder Gerät',
    OTHER: 'Sonstiges',
});

export const HOUSEKEEPING_STATUS_LABELS = freezeLabels({
    CLEAN: 'Sauber',
    DIRTY: 'Zu reinigen',
    IN_PROGRESS: 'Reinigung läuft',
    INSPECTION: 'Kontrolle ausstehend',
    OUT_OF_SERVICE: 'Eingeschränkter Betrieb (OOS)',
});

export const HOUSEKEEPING_TASK_TYPE_LABELS = freezeLabels({
    ARRIVAL: 'Anreisevorbereitung',
    DEPARTURE: 'Abreisereinigung',
    STAYOVER: 'Aufenthaltsreinigung (Bleiber)',
    INSPECTION: 'Zimmerkontrolle',
    MANUAL: 'Manuelle Aufgabe',
});

export const INVOICE_STATUS_LABELS = freezeLabels({
    DRAFT: 'Entwurf',
    ISSUED: 'Ausgestellt',
    PAID: 'Bezahlt',
    CREDITED: 'Gutgeschrieben',
    CANCELLED: 'Storniert',
});

export const INVOICE_TYPE_LABELS = freezeLabels({
    INVOICE: 'Rechnung',
    CREDIT_NOTE: 'Gutschrift',
});

export const MAINTENANCE_PRIORITY_LABELS = freezeLabels({
    LOW: 'Niedrig',
    NORMAL: 'Normal',
    HIGH: 'Hoch',
    CRITICAL: 'Kritisch',
});

export const MAINTENANCE_STATUS_LABELS = freezeLabels({
    OPEN: 'Offen',
    IN_PROGRESS: 'In Bearbeitung',
    RESOLVED: 'Behoben',
    CANCELLED: 'Storniert',
});

export const ORGANIZATION_TYPE_LABELS = freezeLabels({
    COMPANY: 'Firma',
    TRAVEL_AGENCY: 'Reisebüro',
    EVENT_ORGANIZER: 'Veranstalter',
});

export const OUTBOX_STATUS_LABELS = freezeLabels({
    PENDING: 'Ausstehend',
    PROCESSING: 'In Verarbeitung',
    DELIVERED: 'Zugestellt',
    FAILED: 'Fehlgeschlagen',
    DEAD_LETTER: 'Endgültig fehlgeschlagen',
});

export const PAYMENT_KIND_LABELS = freezeLabels({
    PAYMENT: 'Zahlung',
    REFUND: 'Rückerstattung',
});

export const PAYMENT_METHOD_LABELS = freezeLabels({
    CASH: 'Barzahlung',
    CARD: 'Kartenzahlung',
    BANK_TRANSFER: 'Banküberweisung',
    VOUCHER: 'Gutschein',
    OTHER: 'Sonstige Zahlungsart',
});

export const PAYMENT_STATUS_LABELS = freezeLabels({
    POSTED: 'Verbucht',
    VOIDED: 'Storniert',
});

export const RESERVATION_GUARANTEE_STATUS_LABELS = freezeLabels({
    UNGUARANTEED: 'Ohne Garantie',
    CREDIT_CARD: 'Kreditkartengarantie',
    DEPOSIT_REQUIRED: 'Anzahlung erforderlich',
    DEPOSIT_PAID: 'Anzahlung eingegangen',
    COMPANY_GUARANTEE: 'Firmengarantie',
    OTA_GUARANTEE: 'Garantie durch Buchungsportal (OTA)',
});

export const RESERVATION_SOURCE_LABELS = freezeLabels({
    DIRECT: 'Direktreservierung',
    PHONE: 'Telefon',
    EMAIL: 'E-Mail',
    WALK_IN: 'Walk-in (ohne Vorreservierung)',
    BOOKING_ENGINE: 'Online-Buchungsmaschine',
    CHANNEL_MANAGER: 'Channel Manager',
});

export const RESERVATION_STATUS_LABELS = freezeLabels({
    OFFERED: 'Angebot',
    TENTATIVE: 'Option',
    WAITLISTED: 'Warteliste',
    CONFIRMED: 'Bestätigt',
    CHECKED_IN: 'Eingecheckt',
    CHECKED_OUT: 'Ausgecheckt',
    CANCELLED: 'Storniert',
    NO_SHOW: 'Nicht angereist (No-Show)',
});

export const RESOURCE_BOOKING_STATUS_LABELS = freezeLabels({
    TENTATIVE: 'Option',
    CONFIRMED: 'Bestätigt',
    CANCELLED: 'Storniert',
});

export const ROOM_BLOCK_STATUS_LABELS = freezeLabels({
    ACTIVE: 'Aktiv',
    COMPLETED: 'Beendet',
    CANCELLED: 'Aufgehoben',
});

export const ROOM_BLOCK_TYPE_LABELS = freezeLabels({
    OUT_OF_ORDER: 'Ausser Betrieb – nicht verkaufbar (OOO)',
    OUT_OF_SERVICE: 'Eingeschränkter Betrieb – weiterhin zuweisbar (OOS)',
    OWNER_USE: 'Eigennutzung – nicht verkaufbar',
});

export const ROOM_OPERATIONAL_STATUS_LABELS = freezeLabels({
    IN_SERVICE: 'In Betrieb und verkaufbar',
    OUT_OF_ORDER: 'Technisch ausser Betrieb – nicht verkaufbar',
    INACTIVE: 'Inaktiv (nicht im Verkauf)',
});

export const OPERATIONAL_HEALTH_STATUS_LABELS = freezeLabels({
    OK: 'In Ordnung',
    WARNING: 'Warnung',
    CRITICAL: 'Kritisch',
    NOT_CONFIGURED: 'Nicht eingerichtet',
});

export const PMS_ENUM_LABELS = Object.freeze({
    CashShiftStatus: CASH_SHIFT_STATUS_LABELS,
    ChannelConnectionStatus: CHANNEL_CONNECTION_STATUS_LABELS,
    ChannelEnvironment: CHANNEL_ENVIRONMENT_LABELS,
    CommunicationChannel: COMMUNICATION_CHANNEL_LABELS,
    CommunicationDirection: COMMUNICATION_DIRECTION_LABELS,
    CommunicationStatus: COMMUNICATION_STATUS_LABELS,
    FolioItemType: FOLIO_ITEM_TYPE_LABELS,
    FolioStatus: FOLIO_STATUS_LABELS,
    GroupBookingStatus: GROUP_BOOKING_STATUS_LABELS,
    GuestRegistrationStatus: GUEST_REGISTRATION_STATUS_LABELS,
    HotelResourceType: HOTEL_RESOURCE_TYPE_LABELS,
    HousekeepingStatus: HOUSEKEEPING_STATUS_LABELS,
    HousekeepingTaskType: HOUSEKEEPING_TASK_TYPE_LABELS,
    InvoiceStatus: INVOICE_STATUS_LABELS,
    InvoiceType: INVOICE_TYPE_LABELS,
    MaintenancePriority: MAINTENANCE_PRIORITY_LABELS,
    MaintenanceStatus: MAINTENANCE_STATUS_LABELS,
    OrganizationType: ORGANIZATION_TYPE_LABELS,
    OutboxStatus: OUTBOX_STATUS_LABELS,
    PaymentKind: PAYMENT_KIND_LABELS,
    PaymentMethod: PAYMENT_METHOD_LABELS,
    PaymentStatus: PAYMENT_STATUS_LABELS,
    ReservationGuaranteeStatus: RESERVATION_GUARANTEE_STATUS_LABELS,
    ReservationSource: RESERVATION_SOURCE_LABELS,
    ReservationStatus: RESERVATION_STATUS_LABELS,
    ResourceBookingStatus: RESOURCE_BOOKING_STATUS_LABELS,
    RoomBlockStatus: ROOM_BLOCK_STATUS_LABELS,
    RoomBlockType: ROOM_BLOCK_TYPE_LABELS,
    RoomOperationalStatus: ROOM_OPERATIONAL_STATUS_LABELS,
    OperationalHealthStatus: OPERATIONAL_HEALTH_STATUS_LABELS,
});

export const PMS_LABEL_FALLBACKS = Object.freeze({
    empty: 'Nicht angegeben',
    unknown: 'Unbekannt',
});

const resolveLabels = (enumTypeOrLabels) => (
    typeof enumTypeOrLabels === 'string'
        ? PMS_ENUM_LABELS[enumTypeOrLabels]
        : enumTypeOrLabels
);

/**
 * Liefert ein verständliches Label und zeigt nie ungeprüfte Backend-Werte an.
 * Als erster Parameter kann der Backend-Enumname oder direkt eine Label-Map
 * übergeben werden.
 */
export const getPmsEnumLabel = (
    enumTypeOrLabels,
    value,
    fallback = PMS_LABEL_FALLBACKS.unknown,
) => {
    if (value === null || value === undefined || String(value).trim() === '') {
        return PMS_LABEL_FALLBACKS.empty;
    }

    const labels = resolveLabels(enumTypeOrLabels);
    if (!labels || typeof labels !== 'object') {
        return fallback;
    }

    const normalizedValue = String(value).trim().toUpperCase();
    return labels[normalizedValue] ?? fallback;
};

export const getPmsEnumOptions = (enumTypeOrLabels) => {
    const labels = resolveLabels(enumTypeOrLabels);
    if (!labels || typeof labels !== 'object') {
        return [];
    }

    return Object.entries(labels).map(([value, label]) => ({ value, label }));
};

/**
 * Hält ältere, bereits gespeicherte Standardbezeichnungen verständlich, ohne
 * frei vergebene Gastkonto-Namen zu verändern.
 */
export const getFolioDisplayLabel = (value) => {
    const label = String(value ?? '').trim();
    if (!label) {
        return 'Gastkonto';
    }
    return label.toLocaleLowerCase('de-CH') === 'hauptfolio' ? 'Hauptkonto' : label;
};
