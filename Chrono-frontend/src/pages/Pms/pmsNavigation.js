export const PMS_SECTIONS = Object.freeze([
    { key: 'overview', code: 'HQ', label: 'Übersicht' },
    { key: 'portfolio', code: 'HP', label: 'Hotelportfolio' },
    { key: 'reservations', code: 'RES', label: 'Reservierungen' },
    { key: 'groups', code: 'GRP', label: 'Gruppenreservierungen' },
    { key: 'events', code: 'VER', label: 'Veranstaltungen & Ressourcen' },
    { key: 'room-plan', code: 'ZIM', label: 'Zimmerplan' },
    { key: 'guests', code: 'GAS', label: 'Gästeprofile' },
    { key: 'organizations', code: 'ORG', label: 'Geschäftspartner' },
    { key: 'rates', code: 'RAT', label: 'Ratenpläne & Verfügbarkeit' },
    { key: 'housekeeping', code: 'HK', label: 'Housekeeping & Reinigung' },
    { key: 'folios', code: 'CHF', label: 'Gastkonten & Zahlungen' },
    { key: 'invoices', code: 'RE', label: 'Rechnungen' },
    { key: 'audit', code: 'NA', label: 'Tagesabschluss' },
    { key: 'digital-check-in', code: 'DCI', label: 'Digitale Gästeanmeldung' },
    { key: 'communications', code: 'COM', label: 'Gästekommunikation' },
    { key: 'reports', code: 'KPI', label: 'Berichte & Kennzahlen' },
    { key: 'commerce', code: 'POS', label: 'Verkauf & lokale Integrationen' },
    { key: 'integrations', code: 'SYS', label: 'Schnittstellen & Integrationen' },
]);

export const PMS_SECTION_KEYS = new Set(PMS_SECTIONS.map((section) => section.key));

