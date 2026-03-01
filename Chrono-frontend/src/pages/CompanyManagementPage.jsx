import React, { useEffect, useMemo, useRef, useState } from 'react';
import api from '../utils/api';
import { useTranslation } from '../context/LanguageContext';
import Navbar from '../components/Navbar';
import '../styles/CompanyManagementScoped.css';
import { useAuth } from '../context/AuthContext';
import { FEATURE_CATALOG } from '../constants/registrationFeatures';

const OPTIONAL_FEATURES = FEATURE_CATALOG.filter((feature) => !feature.alwaysAvailable);
const ALWAYS_AVAILABLE_LABELS = FEATURE_CATALOG.filter((feature) => feature.alwaysAvailable).map(
    (feature) => feature.name
);
const FEATURE_LABEL_MAP = FEATURE_CATALOG.reduce((acc, feature) => {
    acc[feature.key] = feature.name;
    return acc;
}, {});

const MODULE_ICON_MAP = {
    notifyVacation: '🌴',
    notifyOvertime: '⏱️',
    customerTrackingEnabled: '👥',
    payroll: '💼',
    projects: '📁',
    accounting: '💰',
    crm: '📊',
    supplyChain: '🚚',
    banking: '🏦',
    analytics: '📈',
    signature: '✍️',
    chatbot: '🤖',
    premiumSupport: '🛟',
    roster: '📅',
};

const MODULE_CATEGORIES = [
    {
        key: 'core',
        title: 'Basisfunktionen',
        description: 'Sofort nutzbare Essentials für jede Firma',
        items: [
            { type: 'boolean', key: 'notifyVacation', label: 'Urlaub' },
            { type: 'boolean', key: 'notifyOvertime', label: 'Überstunden' },
            { type: 'boolean', key: 'customerTrackingEnabled', label: 'Kunden-Zeiterfassung' },
        ],
    },
    {
        key: 'business',
        title: 'Business-Module',
        description: 'Erweiterte Produktivität für Teams',
        items: [
            { type: 'feature', key: 'payroll', label: 'Lohnabrechnung' },
            { type: 'feature', key: 'projects', label: 'Projekte/Kunden' },
            { type: 'feature', key: 'accounting', label: 'Finanzen' },
            { type: 'feature', key: 'crm', label: 'CRM/Opportunities' },
            { type: 'feature', key: 'supplyChain', label: 'Supply Chain' },
            { type: 'feature', key: 'banking', label: 'Banking' },
        ],
    },
    {
        key: 'premium',
        title: 'Premium & Extras',
        description: 'Highlights für besondere Anforderungen',
        items: [
            { type: 'feature', key: 'analytics', label: 'Dashboards' },
            { type: 'feature', key: 'signature', label: 'Signaturen' },
            { type: 'feature', key: 'chatbot', label: 'Chatbot' },
            { type: 'feature', key: 'premiumSupport', label: 'Premium Support' },
            { type: 'feature', key: 'roster', label: 'Dienstplan' },
        ],
    },
];

const CATEGORY_BY_FEATURE = MODULE_CATEGORIES.reduce((acc, category) => {
    category.items.forEach((item) => {
        if (item.type === 'feature') {
            acc[item.key] = category.key;
        }
    });
    return acc;
}, {});

const STATUS_FILTERS = [
    { key: 'all', label: 'Alle' },
    { key: 'active', label: 'Aktiv' },
    { key: 'inactive', label: 'Inaktiv' },
    { key: 'canceled', label: 'Gekündigt' },
];

const toFeatureKeyArray = (rawKeys) => {
    if (!rawKeys) {
        return [];
    }

    if (Array.isArray(rawKeys)) {
        return rawKeys;
    }

    if (typeof rawKeys === 'string') {
        const trimmed = rawKeys.trim();
        if (!trimmed) {
            return [];
        }

        try {
            const parsed = JSON.parse(trimmed);
            if (Array.isArray(parsed)) {
                return parsed;
            }
        } catch (error) {
            // ignore and fallback below
        }

        return trimmed
            .split(',')
            .map((key) => key.replace(/[\[\]"']+/g, '').trim())
            .filter(Boolean);
    }

    if (typeof rawKeys?.[Symbol.iterator] === 'function') {
        return Array.from(rawKeys);
    }

    if (typeof rawKeys === 'object') {
        return Object.values(rawKeys).filter((value) => typeof value === 'string' && value.trim().length > 0);
    }

    return [];
};

const normalizeFeatureSelection = (keys = []) => {
    const keyArray = toFeatureKeyArray(keys);
    return OPTIONAL_FEATURES.filter((feature) => keyArray.includes(feature.key)).map((feature) => feature.key);
};

const ModulePicker = ({
    title = 'Module freischalten',
    hint,
    selectedFeatures = [],
    onToggleFeature,
    toggles = {},
    onToggleBoolean,
}) => (
    <div className="cmp-module-picker">
        <div className="cmp-module-picker__header">
            <div>
                <strong>{title}</strong>
                <p className="cmp-module-picker__hint">
                    {hint || `Immer verfügbar: ${ALWAYS_AVAILABLE_LABELS.join(', ')}`}
                </p>
            </div>
        </div>
        <div className="cmp-module-picker__categories">
            {MODULE_CATEGORIES.map((category) => (
                <div key={category.key} className={`cmp-module-category cmp-module-category--${category.key}`}>
                    <div className="cmp-module-category__title">{category.title}</div>
                    <p className="cmp-module-category__description">{category.description}</p>
                    <div className="cmp-module-category__items">
                        {category.items.map((item) => {
                            const icon = MODULE_ICON_MAP[item.key] || '•';
                            const checked =
                                item.type === 'feature'
                                    ? selectedFeatures.includes(item.key)
                                    : Boolean(toggles[item.key]);
                            const toggle = () => {
                                if (item.type === 'feature') {
                                    onToggleFeature?.(item.key);
                                } else {
                                    onToggleBoolean?.(item.key, !checked);
                                }
                            };

                            return (
                                <label key={item.key} className="cmp-module-item">
                                    <input type="checkbox" checked={checked} onChange={toggle} />
                                    <span className="cmp-module-item__icon" aria-hidden="true">
                                        {icon}
                                    </span>
                                    <span className="cmp-module-item__label">{item.label}</span>
                                </label>
                            );
                        })}
                    </div>
                </div>
            ))}
        </div>
    </div>
);

const formatDate = (value) => {
    if (!value && value !== 0) return '–';
    const date = typeof value === 'number' ? new Date(value) : new Date(value);
    if (Number.isNaN(date.getTime())) {
        return '–';
    }
    return date.toLocaleDateString();
};

const CompanyManagementPage = () => {
    const { t } = useTranslation();
    const { currentUser } = useAuth();
    const [companies, setCompanies] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    const [newCompanyName, setNewCompanyName] = useState('');
    const [newCompanyCanton, setNewCompanyCanton] = useState('');
    const [newAddressLine1, setNewAddressLine1] = useState('');
    const [newAddressLine2, setNewAddressLine2] = useState('');
    const [newPostalCode, setNewPostalCode] = useState('');
    const [newCity, setNewCity] = useState('');
    const [newSlackWebhook, setNewSlackWebhook] = useState('');
    const [newTeamsWebhook, setNewTeamsWebhook] = useState('');
    const [newNotifyVacation, setNewNotifyVacation] = useState(false);
    const [newNotifyOvertime, setNewNotifyOvertime] = useState(false);
    const [newCustomerTrackingEnabled, setNewCustomerTrackingEnabled] = useState(false);
    const [newEnabledFeatures, setNewEnabledFeatures] = useState([]);

    const [createWithAdmin, setCreateWithAdmin] = useState({
        companyName: '',
        adminUsername: '',
        adminPassword: '',
        adminEmail: '',
        adminFirstName: '',
        adminLastName: '',
        addressLine1: '',
        addressLine2: '',
        postalCode: '',
        city: '',
        companyCanton: '',
        slackWebhookUrl: '',
        teamsWebhookUrl: '',
        notifyVacation: false,
        notifyOvertime: false,
        customerTrackingEnabled: false,
        enabledFeatures: [],
    });

    const [editingCompany, setEditingCompany] = useState(null);
    const [paymentDetails, setPaymentDetails] = useState({});
    const [openPayments, setOpenPayments] = useState({});

    const [changelogVersion, setChangelogVersion] = useState('');
    const [changelogTitle, setChangelogTitle] = useState('');
    const [changelogContent, setChangelogContent] = useState('');

    const [searchTerm, setSearchTerm] = useState('');
    const [statusFilter, setStatusFilter] = useState('all');
    const [showFilterBar, setShowFilterBar] = useState(false);
    const [showQuickAdvanced, setShowQuickAdvanced] = useState(false);

    const quickCreateRef = useRef(null);
    const advancedCreateRef = useRef(null);
    const companiesRef = useRef(null);

    useEffect(() => {
        fetchCompanies();
        const interval = setInterval(fetchCompanies, 30000);
        return () => clearInterval(interval);
    }, []);

    async function fetchCompanies() {
        setLoading(true);
        setError('');
        try {
            const res = await api.get('/api/superadmin/companies');
            const payload = Array.isArray(res.data)
                ? res.data.map((company) => ({
                      ...company,
                      enabledFeatures: normalizeFeatureSelection(company.enabledFeatures || []),
                  }))
                : [];
            setCompanies(payload);
        } catch (err) {
            console.error('Error fetching companies:', err);
            setError('Fehler beim Laden der Firmenliste');
        } finally {
            setLoading(false);
        }
    }

    const toggleNewCompanyFeature = (featureKey) => {
        setNewEnabledFeatures((prev) => {
            const next = prev.includes(featureKey)
                ? prev.filter((key) => key !== featureKey)
                : [...prev, featureKey];
            return normalizeFeatureSelection(next);
        });
    };

    const handleQuickBooleanToggle = (key, value) => {
        const setters = {
            notifyVacation: setNewNotifyVacation,
            notifyOvertime: setNewNotifyOvertime,
            customerTrackingEnabled: setNewCustomerTrackingEnabled,
        };
        setters[key]?.(value);
    };

    const toggleCreateWithAdminFeature = (featureKey) => {
        setCreateWithAdmin((prev) => {
            const current = prev.enabledFeatures || [];
            const next = current.includes(featureKey)
                ? current.filter((key) => key !== featureKey)
                : [...current, featureKey];
            return { ...prev, enabledFeatures: normalizeFeatureSelection(next) };
        });
    };

    const handleCreateWithAdminBooleanToggle = (key, value) => {
        setCreateWithAdmin((prev) => ({ ...prev, [key]: value }));
    };

    const toggleEditingCompanyFeature = (featureKey) => {
        setEditingCompany((prev) => {
            if (!prev) return prev;
            const current = prev.enabledFeatures || [];
            const next = current.includes(featureKey)
                ? current.filter((key) => key !== featureKey)
                : [...current, featureKey];
            return { ...prev, enabledFeatures: normalizeFeatureSelection(next) };
        });
    };

    const handleEditingBooleanToggle = (key, value) => {
        setEditingCompany((prev) => (prev ? { ...prev, [key]: value } : prev));
    };

    async function handleCreateCompany(e) {
        e.preventDefault();
        if (!newCompanyName.trim()) return;
        try {
            const payload = {
                name: newCompanyName.trim(),
                addressLine1: newAddressLine1.trim() || null,
                addressLine2: newAddressLine2.trim() || null,
                postalCode: newPostalCode.trim() || null,
                city: newCity.trim() || null,
                active: true,
                cantonAbbreviation: newCompanyCanton.trim().toUpperCase() || null,
                slackWebhookUrl: newSlackWebhook || null,
                teamsWebhookUrl: newTeamsWebhook || null,
                notifyVacation: newNotifyVacation,
                notifyOvertime: newNotifyOvertime,
                customerTrackingEnabled: newCustomerTrackingEnabled,
                enabledFeatures: newEnabledFeatures,
            };
            await api.post('/api/superadmin/companies', payload);
            setNewCompanyName('');
            setNewAddressLine1('');
            setNewAddressLine2('');
            setNewPostalCode('');
            setNewCity('');
            setNewCompanyCanton('');
            setNewSlackWebhook('');
            setNewTeamsWebhook('');
            setNewNotifyVacation(false);
            setNewNotifyOvertime(false);
            setNewCustomerTrackingEnabled(false);
            setNewEnabledFeatures([]);
            setShowQuickAdvanced(false);
            fetchCompanies();
        } catch (err) {
            console.error('Error creating company:', err);
        }
    }

    async function handleCreateWithAdmin(e) {
        e.preventDefault();

        if (
            !createWithAdmin.companyName.trim() ||
            !createWithAdmin.adminUsername.trim() ||
            !createWithAdmin.adminPassword.trim()
        ) {
            alert('Bitte Firmenname, Admin-Username und Admin-Passwort angeben');
            return;
        }

        try {
            const payload = {
                companyName: createWithAdmin.companyName.trim(),
                adminUsername: createWithAdmin.adminUsername.trim(),
                adminPassword: createWithAdmin.adminPassword,
                adminEmail: createWithAdmin.adminEmail,
                adminFirstName: createWithAdmin.adminFirstName,
                adminLastName: createWithAdmin.adminLastName,
                addressLine1: createWithAdmin.addressLine1.trim() || null,
                addressLine2: createWithAdmin.addressLine2.trim() || null,
                postalCode: createWithAdmin.postalCode.trim() || null,
                city: createWithAdmin.city.trim() || null,
                cantonAbbreviation: createWithAdmin.companyCanton.trim().toUpperCase() || null,
                slackWebhookUrl: createWithAdmin.slackWebhookUrl || null,
                teamsWebhookUrl: createWithAdmin.teamsWebhookUrl || null,
                notifyVacation: createWithAdmin.notifyVacation,
                notifyOvertime: createWithAdmin.notifyOvertime,
                customerTrackingEnabled: createWithAdmin.customerTrackingEnabled,
                enabledFeatures: createWithAdmin.enabledFeatures,
            };

            const res = await api.post('/api/superadmin/companies/create-with-admin', payload);
            console.log('Created Company + Admin:', res.data);

            setCreateWithAdmin({
                companyName: '',
                adminUsername: '',
                adminPassword: '',
                adminEmail: '',
                adminFirstName: '',
                adminLastName: '',
                addressLine1: '',
                addressLine2: '',
                postalCode: '',
                city: '',
                companyCanton: '',
                slackWebhookUrl: '',
                teamsWebhookUrl: '',
                notifyVacation: false,
                notifyOvertime: false,
                customerTrackingEnabled: false,
                enabledFeatures: [],
            });

            fetchCompanies();
            alert('Firma + AdminUser wurden erfolgreich erstellt.');
        } catch (err) {
            console.error('Error create-with-admin:', err);
            let backendErrorMessage = err.message;
            if (err.response && err.response.data) {
                if (typeof err.response.data === 'string' && err.response.data.length > 0) {
                    backendErrorMessage = err.response.data;
                } else if (err.response.data.message) {
                    backendErrorMessage = err.response.data.message;
                }
            }
            alert('Fehler beim Anlegen von Firma + Admin: ' + backendErrorMessage);
        }
    }

    async function toggleActive(co) {
        try {
            const updated = {
                name: co.name,
                active: !co.active,
                cantonAbbreviation: co.cantonAbbreviation,
            };
            await api.put(`/api/superadmin/companies/${co.id}`, updated);
            fetchCompanies();
        } catch (err) {
            console.error('Fehler beim Toggle', err);
        }
    }

    const handlePublishChangelog = async (e) => {
        e.preventDefault();
        try {
            await api.post('/api/changelog', {
                version: changelogVersion,
                title: changelogTitle,
                content: changelogContent,
            });
            alert('Changelog erfolgreich veröffentlicht!');
            setChangelogVersion('');
            setChangelogTitle('');
            setChangelogContent('');
        } catch (error) {
            console.error('Fehler beim Veröffentlichen des Changelogs:', error);
            alert('Fehler: Konnte Changelog nicht veröffentlichen.');
        }
    };

    function startEdit(company) {
        setEditingCompany({
            ...company,
            cantonAbbreviation: company.cantonAbbreviation || '',
            addressLine1: company.addressLine1 || '',
            addressLine2: company.addressLine2 || '',
            postalCode: company.postalCode || '',
            city: company.city || '',
            slackWebhookUrl: company.slackWebhookUrl || '',
            teamsWebhookUrl: company.teamsWebhookUrl || '',
            notifyVacation: company.notifyVacation || false,
            notifyOvertime: company.notifyOvertime || false,
            customerTrackingEnabled: company.customerTrackingEnabled || false,
            enabledFeatures: normalizeFeatureSelection(company.enabledFeatures || []),
        });
    }

    async function handleSaveEdit(e) {
        e.preventDefault();
        if (!editingCompany || !editingCompany.name.trim()) return;

        try {
            const payload = {
                name: editingCompany.name.trim(),
                active: editingCompany.active,
                cantonAbbreviation: editingCompany.cantonAbbreviation.trim().toUpperCase() || null,
                addressLine1: editingCompany.addressLine1.trim() || null,
                addressLine2: editingCompany.addressLine2.trim() || null,
                postalCode: editingCompany.postalCode.trim() || null,
                city: editingCompany.city.trim() || null,
                slackWebhookUrl: editingCompany.slackWebhookUrl,
                teamsWebhookUrl: editingCompany.teamsWebhookUrl,
                notifyVacation: editingCompany.notifyVacation,
                notifyOvertime: editingCompany.notifyOvertime,
                customerTrackingEnabled: editingCompany.customerTrackingEnabled,
                enabledFeatures: editingCompany.enabledFeatures || [],
            };
            await api.put(`/api/superadmin/companies/${editingCompany.id}`, payload);
            setEditingCompany(null);
            fetchCompanies();
        } catch (err) {
            console.error('Fehler beim Edit:', err);
        }
    }

    async function handleUpdatePayment(co, paid, method, canceled = false) {
        try {
            const body = { paid, paymentMethod: method, canceled };
            await api.put(`/api/superadmin/companies/${co.id}/payment`, body);
            fetchCompanies();
        } catch (err) {
            console.error('Fehler beim Update Payment:', err);
        }
    }

    async function handleDeleteCompany(id) {
        if (!window.confirm('Wirklich löschen? (Company muss leer sein)')) return;
        try {
            await api.delete(`/api/superadmin/companies/${id}`);
            fetchCompanies();
        } catch (err) {
            console.error('Fehler beim Löschen:', err);
            alert('Fehler beim Löschen (evtl. noch User in der Firma?)');
        }
    }

    async function fetchPayments(companyId) {
        try {
            const res = await api.get(`/api/superadmin/companies/${companyId}/payments`);
            setPaymentDetails((prev) => ({ ...prev, [companyId]: res.data || [] }));
        } catch (err) {
            console.error('Fehler beim Laden der Zahlungen:', err);
            setPaymentDetails((prev) => ({ ...prev, [companyId]: [] }));
        }
    }

    function togglePayments(co) {
        setOpenPayments((prev) => {
            const open = !prev[co.id];
            if (open && !paymentDetails[co.id]) {
                fetchPayments(co.id);
            }
            return { ...prev, [co.id]: open };
        });
    }

    const filteredCompanies = useMemo(() => {
        const term = searchTerm.trim().toLowerCase();
        return companies.filter((company) => {
            const matchesTerm = term
                ? [company.name, company.city, company.cantonAbbreviation, company.licenseType]
                      .filter(Boolean)
                      .some((value) => value.toLowerCase().includes(term))
                : true;

            const matchesStatus =
                statusFilter === 'all'
                    ? true
                    : statusFilter === 'active'
                    ? company.active
                    : statusFilter === 'inactive'
                    ? !company.active && !company.canceled
                    : company.canceled;

            return matchesTerm && matchesStatus;
        });
    }, [companies, searchTerm, statusFilter]);

    return (
        <div className="company-management-page scoped-company">
            <Navbar />
            <div className="cmp-container">
                <header className="cmp-topbar">
                    <div className="cmp-breadcrumb">
                        <span className="cmp-breadcrumb__title">🏢 Firmenverwaltung</span>
                        <span className="cmp-breadcrumb__subtitle">SuperAdmin</span>
                    </div>
                    <div className="cmp-topbar-actions">
                        <div className="cmp-search">
                            <input
                                type="text"
                                placeholder="Firma suchen…"
                                value={searchTerm}
                                onChange={(e) => setSearchTerm(e.target.value)}
                                aria-label="Firma suchen"
                            />
                        </div>
                        <button
                            type="button"
                            className="cmp-button cmp-button--primary"
                            onClick={() => {
                                quickCreateRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
                                setShowQuickAdvanced(false);
                            }}
                        >
                            Neue Firma
                        </button>
                        <button
                            type="button"
                            className="cmp-button"
                            onClick={() => {
                                advancedCreateRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
                            }}
                        >
                            Neue Firma + Admin
                        </button>
                        <button
                            type="button"
                            className={`cmp-icon-button${showFilterBar ? ' is-active' : ''}`}
                            onClick={() => setShowFilterBar((prev) => !prev)}
                            aria-label="Filter anzeigen"
                            aria-pressed={showFilterBar}
                        >
                            <span aria-hidden="true">⚙️</span>
                        </button>
                    </div>
                </header>

                {showFilterBar && (
                    <div className="cmp-filter-bar">
                        <span className="cmp-filter-label">Status:</span>
                        <div className="cmp-filter-tabs">
                            {STATUS_FILTERS.map((option) => (
                                <button
                                    key={option.key}
                                    type="button"
                                    className={`cmp-filter-tab${statusFilter === option.key ? ' is-active' : ''}`}
                                    onClick={() => setStatusFilter(option.key)}
                                >
                                    {option.label}
                                </button>
                            ))}
                        </div>
                    </div>
                )}

                {loading ? (
                    <div className="cmp-state">{t('loading', 'Lade...')}</div>
                ) : error ? (
                    <div className="cmp-state cmp-state--error">{error}</div>
                ) : (
                    <>
                        <section className="cmp-section cmp-section--quick" ref={quickCreateRef}>
                            <div className="cmp-section__header">
                                <div>
                                    <h3>Neue Firma anlegen</h3>
                                    <p>Lege mit drei Feldern in Sekunden eine Firma an.</p>
                                </div>
                            </div>
                            <form onSubmit={handleCreateCompany} className="cmp-form">
                                <div className="cmp-form-grid cmp-form-grid--compact">
                                    <label className="cmp-field">
                                        <span>Firmenname</span>
                                        <input
                                            type="text"
                                            value={newCompanyName}
                                            onChange={(e) => setNewCompanyName(e.target.value)}
                                            required
                                        />
                                    </label>
                                    <label className="cmp-field">
                                        <span>Kanton</span>
                                        <input
                                            type="text"
                                            value={newCompanyCanton}
                                            onChange={(e) => setNewCompanyCanton(e.target.value.toUpperCase())}
                                            maxLength={2}
                                            className="text-uppercase"
                                        />
                                    </label>
                                    <label className="cmp-field">
                                        <span>Ort</span>
                                        <input
                                            type="text"
                                            value={newCity}
                                            onChange={(e) => setNewCity(e.target.value)}
                                        />
                                    </label>
                                </div>
                                <div className="cmp-form-actions">
                                    <button
                                        type="button"
                                        className="cmp-text-link"
                                        onClick={() => setShowQuickAdvanced((prev) => !prev)}
                                    >
                                        {showQuickAdvanced ? 'Erweiterte Felder ausblenden' : 'Erweiterte Felder anzeigen'}
                                    </button>
                                </div>
                                {showQuickAdvanced && (
                                    <div className="cmp-collapsible">
                                        <div className="cmp-form-grid cmp-form-grid--two">
                                            <label className="cmp-field">
                                                <span>Adresse</span>
                                                <input
                                                    type="text"
                                                    value={newAddressLine1}
                                                    onChange={(e) => setNewAddressLine1(e.target.value)}
                                                />
                                            </label>
                                            <label className="cmp-field">
                                                <span>Adresszusatz</span>
                                                <input
                                                    type="text"
                                                    value={newAddressLine2}
                                                    onChange={(e) => setNewAddressLine2(e.target.value)}
                                                />
                                            </label>
                                            <label className="cmp-field">
                                                <span>PLZ</span>
                                                <input
                                                    type="text"
                                                    value={newPostalCode}
                                                    onChange={(e) => setNewPostalCode(e.target.value)}
                                                />
                                            </label>
                                            <label className="cmp-field">
                                                <span>Slack Webhook</span>
                                                <input
                                                    type="text"
                                                    value={newSlackWebhook}
                                                    onChange={(e) => setNewSlackWebhook(e.target.value)}
                                                />
                                            </label>
                                            <label className="cmp-field">
                                                <span>Teams Webhook</span>
                                                <input
                                                    type="text"
                                                    value={newTeamsWebhook}
                                                    onChange={(e) => setNewTeamsWebhook(e.target.value)}
                                                />
                                            </label>
                                        </div>
                                        <ModulePicker
                                            selectedFeatures={newEnabledFeatures}
                                            onToggleFeature={toggleNewCompanyFeature}
                                            toggles={{
                                                notifyVacation: newNotifyVacation,
                                                notifyOvertime: newNotifyOvertime,
                                                customerTrackingEnabled: newCustomerTrackingEnabled,
                                            }}
                                            onToggleBoolean={handleQuickBooleanToggle}
                                        />
                                    </div>
                                )}
                                <div className="cmp-form-actions">
                                    <button type="submit" className="cmp-button cmp-button--primary">
                                        Firma erstellen
                                    </button>
                                </div>
                            </form>
                        </section>

                        <section className="cmp-section cmp-section--advanced" ref={advancedCreateRef}>
                            <div className="cmp-section__header">
                                <div>
                                    <h3>Neue Firma + Admin anlegen</h3>
                                    <p>Alle Daten in einem strukturierten Formular erfassen.</p>
                                </div>
                            </div>
                            <form onSubmit={handleCreateWithAdmin} className="cmp-form">
                                <div className="cmp-form-group">
                                    <h4>Firmendaten</h4>
                                    <div className="cmp-form-grid cmp-form-grid--two">
                                        <label className="cmp-field">
                                            <span>Firmenname</span>
                                            <input
                                                type="text"
                                                value={createWithAdmin.companyName}
                                                onChange={(e) =>
                                                    setCreateWithAdmin({
                                                        ...createWithAdmin,
                                                        companyName: e.target.value,
                                                    })
                                                }
                                                required
                                            />
                                        </label>
                                        <label className="cmp-field">
                                            <span>Kanton</span>
                                            <input
                                                type="text"
                                                value={createWithAdmin.companyCanton}
                                                onChange={(e) =>
                                                    setCreateWithAdmin({
                                                        ...createWithAdmin,
                                                        companyCanton: e.target.value.toUpperCase(),
                                                    })
                                                }
                                                maxLength={2}
                                                className="text-uppercase"
                                            />
                                        </label>
                                        <label className="cmp-field">
                                            <span>Adresse</span>
                                            <input
                                                type="text"
                                                value={createWithAdmin.addressLine1}
                                                onChange={(e) =>
                                                    setCreateWithAdmin({
                                                        ...createWithAdmin,
                                                        addressLine1: e.target.value,
                                                    })
                                                }
                                            />
                                        </label>
                                        <label className="cmp-field">
                                            <span>Adresszusatz</span>
                                            <input
                                                type="text"
                                                value={createWithAdmin.addressLine2}
                                                onChange={(e) =>
                                                    setCreateWithAdmin({
                                                        ...createWithAdmin,
                                                        addressLine2: e.target.value,
                                                    })
                                                }
                                            />
                                        </label>
                                        <label className="cmp-field">
                                            <span>PLZ</span>
                                            <input
                                                type="text"
                                                value={createWithAdmin.postalCode}
                                                onChange={(e) =>
                                                    setCreateWithAdmin({
                                                        ...createWithAdmin,
                                                        postalCode: e.target.value,
                                                    })
                                                }
                                            />
                                        </label>
                                        <label className="cmp-field">
                                            <span>Ort</span>
                                            <input
                                                type="text"
                                                value={createWithAdmin.city}
                                                onChange={(e) =>
                                                    setCreateWithAdmin({
                                                        ...createWithAdmin,
                                                        city: e.target.value,
                                                    })
                                                }
                                            />
                                        </label>
                                        <label className="cmp-field">
                                            <span>Slack Webhook</span>
                                            <input
                                                type="text"
                                                value={createWithAdmin.slackWebhookUrl}
                                                onChange={(e) =>
                                                    setCreateWithAdmin({
                                                        ...createWithAdmin,
                                                        slackWebhookUrl: e.target.value,
                                                    })
                                                }
                                            />
                                        </label>
                                        <label className="cmp-field">
                                            <span>Teams Webhook</span>
                                            <input
                                                type="text"
                                                value={createWithAdmin.teamsWebhookUrl}
                                                onChange={(e) =>
                                                    setCreateWithAdmin({
                                                        ...createWithAdmin,
                                                        teamsWebhookUrl: e.target.value,
                                                    })
                                                }
                                            />
                                        </label>
                                    </div>
                                </div>

                                <div className="cmp-form-group">
                                    <h4>Module freischalten</h4>
                                    <ModulePicker
                                        selectedFeatures={createWithAdmin.enabledFeatures}
                                        onToggleFeature={toggleCreateWithAdminFeature}
                                        toggles={{
                                            notifyVacation: createWithAdmin.notifyVacation,
                                            notifyOvertime: createWithAdmin.notifyOvertime,
                                            customerTrackingEnabled: createWithAdmin.customerTrackingEnabled,
                                        }}
                                        onToggleBoolean={handleCreateWithAdminBooleanToggle}
                                    />
                                </div>

                                <div className="cmp-form-group">
                                    <h4>Admin-Daten</h4>
                                    <div className="cmp-form-grid cmp-form-grid--two">
                                        <label className="cmp-field">
                                            <span>Admin E-Mail</span>
                                            <input
                                                type="email"
                                                value={createWithAdmin.adminEmail}
                                                onChange={(e) =>
                                                    setCreateWithAdmin({
                                                        ...createWithAdmin,
                                                        adminEmail: e.target.value,
                                                    })
                                                }
                                            />
                                        </label>
                                        <label className="cmp-field">
                                            <span>Admin Benutzername</span>
                                            <input
                                                type="text"
                                                value={createWithAdmin.adminUsername}
                                                onChange={(e) =>
                                                    setCreateWithAdmin({
                                                        ...createWithAdmin,
                                                        adminUsername: e.target.value,
                                                    })
                                                }
                                                required
                                            />
                                        </label>
                                        <label className="cmp-field">
                                            <span>Admin Passwort</span>
                                            <input
                                                type="password"
                                                value={createWithAdmin.adminPassword}
                                                onChange={(e) =>
                                                    setCreateWithAdmin({
                                                        ...createWithAdmin,
                                                        adminPassword: e.target.value,
                                                    })
                                                }
                                                required
                                            />
                                        </label>
                                        <label className="cmp-field">
                                            <span>Vorname</span>
                                            <input
                                                type="text"
                                                value={createWithAdmin.adminFirstName}
                                                onChange={(e) =>
                                                    setCreateWithAdmin({
                                                        ...createWithAdmin,
                                                        adminFirstName: e.target.value,
                                                    })
                                                }
                                            />
                                        </label>
                                        <label className="cmp-field">
                                            <span>Nachname</span>
                                            <input
                                                type="text"
                                                value={createWithAdmin.adminLastName}
                                                onChange={(e) =>
                                                    setCreateWithAdmin({
                                                        ...createWithAdmin,
                                                        adminLastName: e.target.value,
                                                    })
                                                }
                                            />
                                        </label>
                                    </div>
                                </div>

                                <div className="cmp-form-actions">
                                    <button type="submit" className="cmp-button cmp-button--primary">
                                        Firma + Admin erstellen
                                    </button>
                                </div>
                            </form>
                        </section>

                        <section className="cmp-section cmp-section--companies" ref={companiesRef}>
                            <div className="cmp-section__header">
                                <div>
                                    <h3>Bestehende Firmen</h3>
                                    <p>Alle Kundenkonten mit Status, Modulen und Aktionen.</p>
                                </div>
                            </div>

                            {filteredCompanies.length === 0 ? (
                                <div className="cmp-empty-state">
                                    <p>Keine Firmen gefunden. Passe deine Suche oder Filter an.</p>
                                </div>
                            ) : (
                                <div className="cmp-company-grid">
                                    {filteredCompanies.map((co) => {
                                        const activeFeatures = normalizeFeatureSelection(co.enabledFeatures);
                                        const inactiveFeatures = OPTIONAL_FEATURES.map((feature) => feature.key).filter(
                                            (key) => !activeFeatures.includes(key)
                                        );
                                        const activeBooleanKeys = ['notifyVacation', 'notifyOvertime', 'customerTrackingEnabled'].filter(
                                            (key) => Boolean(co[key])
                                        );
                                        const activeModules = [
                                            ...activeBooleanKeys.map((key) => ({
                                                key,
                                                label:
                                                    MODULE_CATEGORIES.find((category) =>
                                                        category.items.some((item) => item.key === key)
                                                    )?.items.find((item) => item.key === key)?.label || key,
                                                category: 'core',
                                            })),
                                            ...activeFeatures.map((key) => ({
                                                key,
                                                label: FEATURE_LABEL_MAP[key] || key,
                                                category: CATEGORY_BY_FEATURE[key] || 'business',
                                            })),
                                        ];

                                        const statusLabel = co.canceled
                                            ? 'Gekündigt'
                                            : co.active
                                            ? 'Aktiv'
                                            : 'Deaktiviert';

                                        return (
                                            <article key={co.id} className="cmp-company-card">
                                                {editingCompany && editingCompany.id === co.id ? (
                                                    <form onSubmit={handleSaveEdit} className="cmp-edit-form">
                                                        <div className="cmp-edit-form__grid">
                                                            <label className="cmp-field">
                                                                <span>Firmenname</span>
                                                                <input
                                                                    type="text"
                                                                    value={editingCompany.name}
                                                                    onChange={(e) =>
                                                                        setEditingCompany({
                                                                            ...editingCompany,
                                                                            name: e.target.value,
                                                                        })
                                                                    }
                                                                    required
                                                                />
                                                            </label>
                                                            <label className="cmp-field">
                                                                <span>Kanton</span>
                                                                <input
                                                                    type="text"
                                                                    value={editingCompany.cantonAbbreviation}
                                                                    onChange={(e) =>
                                                                        setEditingCompany({
                                                                            ...editingCompany,
                                                                            cantonAbbreviation: e.target.value,
                                                                        })
                                                                    }
                                                                    maxLength={2}
                                                                    className="text-uppercase"
                                                                />
                                                            </label>
                                                            <label className="cmp-field">
                                                                <span>Adresse</span>
                                                                <input
                                                                    type="text"
                                                                    value={editingCompany.addressLine1}
                                                                    onChange={(e) =>
                                                                        setEditingCompany({
                                                                            ...editingCompany,
                                                                            addressLine1: e.target.value,
                                                                        })
                                                                    }
                                                                />
                                                            </label>
                                                            <label className="cmp-field">
                                                                <span>Adresszusatz</span>
                                                                <input
                                                                    type="text"
                                                                    value={editingCompany.addressLine2}
                                                                    onChange={(e) =>
                                                                        setEditingCompany({
                                                                            ...editingCompany,
                                                                            addressLine2: e.target.value,
                                                                        })
                                                                    }
                                                                />
                                                            </label>
                                                            <label className="cmp-field">
                                                                <span>PLZ</span>
                                                                <input
                                                                    type="text"
                                                                    value={editingCompany.postalCode}
                                                                    onChange={(e) =>
                                                                        setEditingCompany({
                                                                            ...editingCompany,
                                                                            postalCode: e.target.value,
                                                                        })
                                                                    }
                                                                />
                                                            </label>
                                                            <label className="cmp-field">
                                                                <span>Ort</span>
                                                                <input
                                                                    type="text"
                                                                    value={editingCompany.city}
                                                                    onChange={(e) =>
                                                                        setEditingCompany({
                                                                            ...editingCompany,
                                                                            city: e.target.value,
                                                                        })
                                                                    }
                                                                />
                                                            </label>
                                                            <label className="cmp-field">
                                                                <span>Slack Webhook</span>
                                                                <input
                                                                    type="text"
                                                                    value={editingCompany.slackWebhookUrl}
                                                                    onChange={(e) =>
                                                                        setEditingCompany({
                                                                            ...editingCompany,
                                                                            slackWebhookUrl: e.target.value,
                                                                        })
                                                                    }
                                                                />
                                                            </label>
                                                            <label className="cmp-field">
                                                                <span>Teams Webhook</span>
                                                                <input
                                                                    type="text"
                                                                    value={editingCompany.teamsWebhookUrl}
                                                                    onChange={(e) =>
                                                                        setEditingCompany({
                                                                            ...editingCompany,
                                                                            teamsWebhookUrl: e.target.value,
                                                                        })
                                                                    }
                                                                />
                                                            </label>
                                                        </div>

                                                        <ModulePicker
                                                            selectedFeatures={editingCompany.enabledFeatures || []}
                                                            onToggleFeature={toggleEditingCompanyFeature}
                                                            toggles={{
                                                                notifyVacation: editingCompany.notifyVacation,
                                                                notifyOvertime: editingCompany.notifyOvertime,
                                                                customerTrackingEnabled: editingCompany.customerTrackingEnabled,
                                                            }}
                                                            onToggleBoolean={handleEditingBooleanToggle}
                                                        />

                                                        <div className="cmp-edit-actions">
                                                            <label className="cmp-toggle">
                                                                <input
                                                                    type="checkbox"
                                                                    checked={editingCompany.active}
                                                                    onChange={(e) =>
                                                                        setEditingCompany({
                                                                            ...editingCompany,
                                                                            active: e.target.checked,
                                                                        })
                                                                    }
                                                                />
                                                                <span>Aktiv</span>
                                                            </label>
                                                            <div className="cmp-edit-actions__buttons">
                                                                <button
                                                                    type="button"
                                                                    className="cmp-button"
                                                                    onClick={() => setEditingCompany(null)}
                                                                >
                                                                    Abbrechen
                                                                </button>
                                                                <button type="submit" className="cmp-button cmp-button--primary">
                                                                    Speichern
                                                                </button>
                                                            </div>
                                                        </div>
                                                    </form>
                                                ) : (
                                                    <>
                                                        <div className="cmp-company-card__header">
                                                            <div>
                                                                <h4>{co.name}</h4>
                                                                <div className="cmp-company-card__meta">
                                                                    <span className={`cmp-status-badge cmp-status-badge--${statusLabel.toLowerCase()}`}>
                                                                        {statusLabel}
                                                                    </span>
                                                                    {typeof co.userCount === 'number' && (
                                                                        <span className="cmp-tag cmp-tag--users">{co.userCount} Nutzer</span>
                                                                    )}
                                                                    {co.city && <span className="cmp-tag">{co.city}</span>}
                                                                    {co.cantonAbbreviation && <span className="cmp-tag">{co.cantonAbbreviation}</span>}
                                                                    {co.licenseType && <span className="cmp-tag">{co.licenseType}</span>}
                                                                </div>
                                                            </div>
                                                            <div className="cmp-company-card__actions">
                                                                <button type="button" className="cmp-button" onClick={() => startEdit(co)}>
                                                                    Bearbeiten
                                                                </button>
                                                                <button type="button" className="cmp-button" onClick={() => togglePayments(co)}>
                                                                    Zahlungen
                                                                </button>
                                                                <button type="button" className="cmp-button" onClick={() => toggleActive(co)}>
                                                                    {co.active ? 'Deaktivieren' : 'Aktivieren'}
                                                                </button>
                                                                <button
                                                                    type="button"
                                                                    className="cmp-button"
                                                                    onClick={() =>
                                                                        handleUpdatePayment(
                                                                            co,
                                                                            !co.paid,
                                                                            co.paymentMethod || 'manuell'
                                                                        )
                                                                    }
                                                                >
                                                                    {co.paid ? 'Zahlung zurücksetzen' : 'Zahlung bestätigen'}
                                                                </button>
                                                                <button
                                                                    type="button"
                                                                    className="cmp-button cmp-button--danger"
                                                                    onClick={() => handleDeleteCompany(co.id)}
                                                                >
                                                                    Löschen
                                                                </button>
                                                            </div>
                                                        </div>

                                                        <div className="cmp-company-card__modules">
                                                            <div>
                                                                <span className="cmp-module-title">Aktive Module</span>
                                                                {activeModules.length > 0 ? (
                                                                    <div className="cmp-module-chip-row">
                                                                        {activeModules.map((module) => (
                                                                            <span
                                                                                key={module.key}
                                                                                className={`cmp-module-chip cmp-module-chip--${module.category}`}
                                                                            >
                                                                                {MODULE_ICON_MAP[module.key] || '•'} {module.label}
                                                                            </span>
                                                                        ))}
                                                                    </div>
                                                                ) : (
                                                                    <p className="cmp-module-empty">Keine Zusatzmodule aktiviert</p>
                                                                )}
                                                            </div>
                                                            <div>
                                                                <span className="cmp-module-title">Verfügbare Module</span>
                                                                {inactiveFeatures.length > 0 ? (
                                                                    <div className="cmp-module-chip-row">
                                                                        {inactiveFeatures.map((key) => (
                                                                            <span key={key} className="cmp-module-chip cmp-module-chip--inactive">
                                                                                {MODULE_ICON_MAP[key] || '•'} {FEATURE_LABEL_MAP[key] || key}
                                                                            </span>
                                                                        ))}
                                                                    </div>
                                                                ) : (
                                                                    <p className="cmp-module-empty">Alle Module sind aktiviert</p>
                                                                )}
                                                            </div>
                                                        </div>

                                                        <div className="cmp-company-card__footer">
                                                            <div>
                                                                <span>Erstellt am</span>
                                                                <strong>{formatDate(co.createdAt)}</strong>
                                                            </div>
                                                            <div>
                                                                <span>Zuletzt aktualisiert</span>
                                                                <strong>{formatDate(co.updatedAt)}</strong>
                                                            </div>
                                                            <div>
                                                                <span>Letzte Zahlung</span>
                                                                <strong>{formatDate(co.lastPaymentAt || co.lastPaymentDate)}</strong>
                                                            </div>
                                                        </div>

                                                        {openPayments[co.id] && (
                                                            <div className="cmp-payments">
                                                                <table className="cmp-payments-table">
                                                                    <thead>
                                                                        <tr>
                                                                            <th>ID</th>
                                                                            <th>Betrag</th>
                                                                            <th>Status</th>
                                                                            <th>Erstellt</th>
                                                                        </tr>
                                                                    </thead>
                                                                    <tbody>
                                                                        {(paymentDetails[co.id] || []).map((p) => (
                                                                            <tr key={p.id}>
                                                                                <td>{p.id}</td>
                                                                                <td>
                                                                                    {(p.amount / 100).toFixed(2)} {p.currency?.toUpperCase()}
                                                                                </td>
                                                                                <td>{p.status}</td>
                                                                                <td>{formatDate(p.created * 1000)}</td>
                                                                            </tr>
                                                                        ))}
                                                                    </tbody>
                                                                </table>
                                                            </div>
                                                        )}
                                                    </>
                                                )}
                                            </article>
                                        );
                                    })}
                                </div>
                            )}
                        </section>

                        {currentUser?.roles?.includes('ROLE_SUPERADMIN') && (
                            <section className="cmp-section cmp-section--release">
                                <div className="cmp-section__header">
                                    <div>
                                        <h3>Release Notes erstellen</h3>
                                        <p>Dokumentiere Änderungen und veröffentliche sie mit einem Klick.</p>
                                    </div>
                                </div>
                                <form onSubmit={handlePublishChangelog} className="cmp-form">
                                    <div className="cmp-release-grid">
                                        <div className="cmp-release-grid__left">
                                            <label className="cmp-field">
                                                <span>Version</span>
                                                <input
                                                    type="text"
                                                    value={changelogVersion}
                                                    onChange={(e) => setChangelogVersion(e.target.value)}
                                                    placeholder="v1.1.0"
                                                    required
                                                />
                                            </label>
                                            <label className="cmp-field">
                                                <span>Titel</span>
                                                <input
                                                    type="text"
                                                    value={changelogTitle}
                                                    onChange={(e) => setChangelogTitle(e.target.value)}
                                                    placeholder="Neue Funktionen im Dashboard"
                                                    required
                                                />
                                            </label>
                                        </div>
                                        <div className="cmp-release-grid__right">
                                            <label className="cmp-field">
                                                <span>Änderungen</span>
                                                <textarea
                                                    value={changelogContent}
                                                    onChange={(e) => setChangelogContent(e.target.value)}
                                                    placeholder="- Neues Feature: ...\n- Bugfix: ..."
                                                    rows={10}
                                                    required
                                                />
                                            </label>
                                        </div>
                                    </div>
                                    <div className="cmp-form-actions">
                                        <button type="submit" className="cmp-button cmp-button--primary">
                                            Release veröffentlichen
                                        </button>
                                    </div>
                                </form>
                            </section>
                        )}
                    </>
                )}
            </div>
        </div>
    );
};

export default CompanyManagementPage;
