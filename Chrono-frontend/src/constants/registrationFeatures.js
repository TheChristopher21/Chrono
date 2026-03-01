export const BASE_FEATURE = {
    key: "base",
    name: "Zeiterfassung (Basis)",
    price: 5,
    priceType: "perEmployee",
    required: true,
    description: "Digitale Zeiterfassung für alle Mitarbeiter (Pflichtmodul).",
    alwaysAvailable: true,
};

export const FEATURE_CATALOG = [
    {
        key: "vacation",
        name: "Urlaubs- & Abwesenheitsmodul",
        price: 1,
        priceType: "perEmployee",
        required: false,
        description: "Digitale Urlaubsanträge, Abwesenheitsübersicht, Freigabe-Workflow.",
        alwaysAvailable: true,
    },
    {
        key: "payroll",
        name: "Lohnabrechnung",
        price: 4,
        priceType: "perEmployee",
        required: false,
        description: "Lohnabrechnung (DE & CH), Gehaltsabrechnungen als PDF, Abrechnungsexport.",
        alwaysAvailable: false,
    },
    {
        key: "projects",
        name: "Projektzeiten & Kundenverwaltung",
        price: 1,
        priceType: "perEmployee",
        required: false,
        description: "Erfassen von Projektzeiten und Kunden, Berichte & Auswertungen.",
        alwaysAvailable: false,
    },
    {
        key: "accounting",
        name: "Finanzbuchhaltung & Anlagen",
        price: 2.5,
        priceType: "perEmployee",
        required: false,
        description:
            "Hauptbuch, Debitoren/Kreditoren, Anlagenverwaltung inkl. automatischer Übergabe aus Payroll & Billing.",
        alwaysAvailable: false,
    },
    {
        key: "crm",
        name: "CRM & Opportunity-Management",
        price: 1.2,
        priceType: "perEmployee",
        required: false,
        description: "Leads, Aktivitäten, Kampagnen und Pipeline-Visualisierung mit Team-Zugriff.",
        alwaysAvailable: false,
    },
    {
        key: "supplyChain",
        name: "Supply Chain & Lager",
        price: 3.5,
        priceType: "perEmployee",
        required: false,
        description: "Artikel-, Lager- und Auftragsverwaltung, Wareneingang/-ausgang, Produktion & Servicefälle.",
        alwaysAvailable: false,
    },
    {
        key: "banking",
        name: "Banking & Zahlungsverkehr",
        price: 89,
        priceType: "flat",
        required: false,
        description: "ISO-20022 pain.001 Export, Zahlungsfreigaben, sichere Nachrichten & Idempotency-Workflows.",
        alwaysAvailable: false,
    },
    {
        key: "analytics",
        name: "Reporting & BI-Dashboards",
        price: 0.8,
        priceType: "perEmployee",
        required: false,
        description: "Drill-down-Kennzahlen, Forecasts und Export in Echtzeit über alle Module.",
        alwaysAvailable: false,
    },
    {
        key: "signature",
        name: "Digitale Signaturen & sichere Zustellung",
        price: 0.6,
        priceType: "perEmployee",
        required: false,
        description: "Elektronische Signatur von Lohnabrechnungen, Verträgen & Rechnungen mit verschlüsselter Zustellung.",
        alwaysAvailable: false,
    },
    {
        key: "nfc",
        name: "NFC-Stempeluhr",
        price: 0.5,
        priceType: "perEmployee",
        required: false,
        description: "Stempeln per NFC-Karte oder Chip am Terminal oder Smartphone.",
        alwaysAvailable: true,
    },
    {
        key: "chatbot",
        name: "Integrierter Support-Chatbot",
        price: 0.5,
        priceType: "perEmployee",
        required: false,
        description: "KI-basierte Hilfe & Erklärungen direkt in der App.",
        alwaysAvailable: false,
    },
    {
        key: "premiumSupport",
        name: "Premium-Support (SLA 2h)",
        price: 129,
        priceType: "flat",
        required: false,
        description: "Telefonischer Premium-Support, dedizierte Success-Manager & priorisierte Umsetzung.",
        alwaysAvailable: false,
    },
    {
        key: "roster",
        name: "Dienstplan & Schichtplanung",
        price: 1.2,
        priceType: "perEmployee",
        required: false,
        description:
            "Intelligente Schichtplanung mit Drag & Drop, Konflikterkennung, Mitarbeiterwünschen, Urlaubsabgleich und Export als PDF/Excel.",
        alwaysAvailable: false,
    },
];

export const ALWAYS_AVAILABLE_FEATURE_KEYS = FEATURE_CATALOG.filter((feature) => feature.alwaysAvailable).map(
    (feature) => feature.key
);

export const TOGGLABLE_FEATURE_KEYS = FEATURE_CATALOG.filter((feature) => !feature.alwaysAvailable).map(
    (feature) => feature.key
);

export const ALL_FEATURE_KEYS = FEATURE_CATALOG.map((feature) => feature.key);
