export const PMS_SECTIONS = Object.freeze([
    {
        key: 'overview', code: 'HQ', label: 'Übersicht', group: 'front-desk',
        description: 'Betriebstag, Anreisen, Abreisen und wichtige Aufgaben auf einen Blick.',
    },
    {
        key: 'reservations', code: 'RES', label: 'Reservierungen', group: 'front-desk',
        description: 'Vor-Ort-, Telefon-, E-Mail- und Direktbuchungen aufnehmen und bearbeiten.',
    },
    {
        key: 'room-plan', code: 'ZIM', label: 'Zimmerplan', group: 'front-desk',
        description: 'Freie, belegte und noch nicht bezugsbereite Zimmer erkennen und zuweisen.',
    },
    {
        key: 'guests', code: 'GAS', label: 'Gästeprofile', group: 'front-desk',
        description: 'Gäste suchen, Kontaktdaten pflegen und wiederkehrende Gäste erkennen.',
    },
    {
        key: 'folios', code: 'CHF', label: 'Gastkonten & Zahlungen', group: 'front-desk',
        description: 'Offene Beträge, Leistungen und Zahlungen je Aufenthalt bearbeiten.',
    },
    {
        key: 'digital-check-in', code: 'DCI', label: 'Gästeanmeldung', group: 'front-desk',
        description: 'Meldescheine prüfen oder Gästen einen sicheren Erfassungslink senden.',
    },
    {
        key: 'housekeeping', code: 'HK', label: 'Housekeeping & Reinigung', group: 'operations',
        description: 'Reinigung, Zimmerfreigabe und technische Sperren koordinieren.',
    },
    {
        key: 'communications', code: 'COM', label: 'Gästekommunikation', group: 'operations',
        description: 'Nachrichten, Vorlagen und Antworten zentral bearbeiten.',
    },
    {
        key: 'events', code: 'VER', label: 'Veranstaltungen & Ressourcen', group: 'operations',
        description: 'Räume, Ressourcen und Veranstaltungen planen.',
    },
    {
        key: 'commerce', code: 'POS', label: 'Verkauf & lokale Integrationen', group: 'operations',
        description: 'Zusatzverkäufe und lokale operative Systeme verwalten.',
    },
    {
        key: 'rates', code: 'RAT', label: 'Raten & Verfügbarkeit', group: 'sales',
        description: 'Preise, Einschränkungen und verkaufbare Kapazität steuern.',
    },
    {
        key: 'groups', code: 'GRP', label: 'Gruppenreservierungen', group: 'sales',
        description: 'Zimmerkontingente und Teilnehmerlisten für Gruppen organisieren.',
    },
    {
        key: 'organizations', code: 'ORG', label: 'Geschäftspartner', group: 'sales',
        description: 'Firmen, Reisebüros und weitere Buchungspartner pflegen.',
    },
    {
        key: 'portfolio', code: 'HP', label: 'Hotelportfolio', group: 'management',
        description: 'Mehrere Hotels vergleichen und zentral steuern.',
    },
    {
        key: 'invoices', code: 'RE', label: 'Rechnungen', group: 'management',
        description: 'Rechnungen erstellen, ausgeben und korrigieren.',
    },
    {
        key: 'audit', code: 'NA', label: 'Tagesabschluss', group: 'management',
        description: 'Betriebstag kontrolliert abschliessen und Abweichungen prüfen.',
    },
    {
        key: 'reports', code: 'KPI', label: 'Berichte & Kennzahlen', group: 'management',
        description: 'Auslastung, Umsatz und operative Leistung auswerten.',
    },
    {
        key: 'integrations', code: 'SYS', label: 'Schnittstellen', group: 'management',
        description: 'Buchungskanäle und externe Systeme anbinden und überwachen.',
    },
]);

const GROUP_DEFINITIONS = [
    { key: 'front-desk', label: 'Rezeption' },
    { key: 'operations', label: 'Hotelbetrieb' },
    { key: 'sales', label: 'Verkauf & Partner' },
    { key: 'management', label: 'Steuerung & System' },
];

export const PMS_NAVIGATION_GROUPS = Object.freeze(GROUP_DEFINITIONS.map((group) => Object.freeze({
    ...group,
    items: Object.freeze(PMS_SECTIONS.filter((section) => section.group === group.key)),
})));

export const PMS_SECTION_KEYS = new Set(PMS_SECTIONS.map((section) => section.key));
