import { useEffect, useMemo, useRef, useState } from 'react';
import Navbar from '../../components/Navbar.jsx';
import { useAuth } from '../../context/AuthContext.jsx';
import api from '../../utils/api.js';
import { ACCESS_MANAGE, hasPageAccess, isAdminUser } from '../../utils/pageAccess.js';
import { getUserDisplayName } from '../../utils/userDisplay.js';
import PmsSetupWorkspace from './PmsSetupWorkspace.jsx';
import PmsOperationsWorkspace from './PmsOperationsWorkspace.jsx';
import '../../styles/PmsDashboardScoped.css';

const navigationItems = [
    { key: 'overview', code: 'HQ', label: 'Übersicht' },
    { key: 'portfolio', code: 'MP', label: 'Portfolio' },
    { key: 'reservations', code: 'RES', label: 'Reservierungen' },
    { key: 'events', code: 'MICE', label: 'Events & Ressourcen' },
    { key: 'room-plan', code: 'ZIM', label: 'Zimmerplan' },
    { key: 'guests', code: 'GAS', label: 'Gäste' },
    { key: 'rates', code: 'RAT', label: 'Raten & Verfügbarkeit' },
    { key: 'housekeeping', code: 'HK', label: 'Housekeeping' },
    { key: 'folios', code: 'CHF', label: 'Folios & Zahlungen' },
    { key: 'reports', code: 'KPI', label: 'Berichte' },
    { key: 'integrations', code: 'SYS', label: 'Integrationen' },
];

const quickActions = [
    {
        key: 'reservation',
        label: 'Neue Reservierung',
        description: 'Einzel-, Gruppen- oder Walk-in-Buchung',
        shortcut: 'Ctrl N',
    },
    {
        key: 'check-in',
        label: 'Check-in',
        description: 'Gast suchen und Anreise abschließen',
        shortcut: 'Ctrl I',
    },
    {
        key: 'check-out',
        label: 'Check-out',
        description: 'Folio prüfen und Abreise abschließen',
        shortcut: 'Ctrl O',
    },
    {
        key: 'guest-search',
        label: 'Gast suchen',
        description: 'Profile, Aufenthalte und Reservierungen',
        shortcut: 'Ctrl G',
    },
    {
        key: 'payment',
        label: 'Zahlung',
        description: 'Offene Folios und Zahlungen bearbeiten',
        shortcut: 'Ctrl P',
    },
];

const commandItems = [
    { key: 'setup', label: 'Hotel und Zimmer einrichten', hint: 'Ctrl E' },
    { key: 'reservation', label: 'Neue Reservierung anlegen', hint: 'Ctrl N' },
    { key: 'guest-search', label: 'Gast oder Reservierung suchen', hint: 'Ctrl G' },
    { key: 'check-in', label: 'Check-in starten', hint: 'Ctrl I' },
    { key: 'check-out', label: 'Check-out starten', hint: 'Ctrl O' },
    { key: 'payment', label: 'Zahlung erfassen', hint: 'Ctrl P' },
    { key: 'room-plan', label: 'Zimmerplan öffnen', hint: 'Ctrl R' },
];

const emptySetup = {
    properties: [],
    totalProperties: 0,
    totalRoomTypes: 0,
    totalRooms: 0,
    foundationComplete: false,
};

const emptyOperations = {
    metrics: {
        totalRooms: 0,
        occupiedRooms: 0,
        availableRooms: 0,
        occupancyPercent: 0,
        inHouse: 0,
        arrivals: 0,
        departures: 0,
        dirtyRooms: 0,
        openFolios: 0,
        openBalance: 0,
    },
    reservations: [],
    arrivals: [],
    departures: [],
    guests: [],
    ratePlans: [],
    rateOverrides: [],
    rooms: [],
    housekeepingTasks: [],
    folios: [],
};

const emptyHealth = {
    status: 'WARNING',
    pendingEvents: 0,
    failedEvents: 0,
    deadLetterEvents: 0,
    components: [],
    alerts: [],
};

const toDateKey = (date) => [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
].join('-');

const formatBusinessDate = (date) => new Intl.DateTimeFormat('de-CH', {
    weekday: 'long',
    day: '2-digit',
    month: 'long',
    year: 'numeric',
}).format(date);

const PmsDashboard = () => {
    const { currentUser } = useAuth();
    const [businessDate, setBusinessDate] = useState(() => new Date());
    const [operationMode, setOperationMode] = useState('simple');
    const [activeNavigation, setActiveNavigation] = useState('overview');
    const [commandOpen, setCommandOpen] = useState(false);
    const [commandQuery, setCommandQuery] = useState('');
    const [selectedAction, setSelectedAction] = useState(null);
    const [setup, setSetup] = useState(emptySetup);
    const [setupLoading, setSetupLoading] = useState(true);
    const [setupError, setSetupError] = useState('');
    const [setupOpen, setSetupOpen] = useState(false);
    const [activePropertyId, setActivePropertyId] = useState(null);
    const [operations, setOperations] = useState(emptyOperations);
    const [operationsLoading, setOperationsLoading] = useState(false);
    const [operationsError, setOperationsError] = useState('');
    const [operationalHealth, setOperationalHealth] = useState(emptyHealth);
    const [healthError, setHealthError] = useState('');
    const [initialOperationAction, setInitialOperationAction] = useState(null);
    const commandInputRef = useRef(null);
    const commandTriggerRef = useRef(null);
    const commandReturnFocusRef = useRef(null);

    const formattedDate = useMemo(() => formatBusinessDate(businessDate), [businessDate]);
    const displayName = getUserDisplayName(currentUser) || currentUser?.username || 'Gastgeber';
    const canManagePms = hasPageAccess(currentUser, 'pms', ACCESS_MANAGE);
    const canManageGuestPrivacy = canManagePms && isAdminUser(currentUser);
    const activeProperty = useMemo(
        () => setup.properties.find((property) => property.id === activePropertyId)
            ?? setup.properties[0]
            ?? null,
        [activePropertyId, setup.properties],
    );
    const activeRoomCount = operations.metrics?.totalRooms
        ?? activeProperty?.rooms?.filter((room) => room.active).length
        ?? 0;
    const blockedRoomCount = operations.rooms?.filter(
        (room) => room.operationalStatus !== 'IN_SERVICE'
    ).length ?? 0;
    const metrics = useMemo(() => [
        {
            key: 'occupancy',
            label: 'Auslastung',
            value: `${operations.metrics?.occupancyPercent ?? 0} %`,
            meta: `${operations.metrics?.occupiedRooms ?? 0} von ${operations.metrics?.totalRooms ?? setup.totalRooms} Zimmern`,
        },
        { key: 'in-house', label: 'In-House', value: String(operations.metrics?.inHouse ?? 0), meta: 'Aktive Aufenthalte' },
        { key: 'arrivals', label: 'Anreisen', value: String(operations.metrics?.arrivals ?? 0), meta: 'Am Betriebstag' },
        { key: 'departures', label: 'Abreisen', value: String(operations.metrics?.departures ?? 0), meta: 'Am Betriebstag' },
        { key: 'housekeeping', label: 'Zu reinigen', value: String(operations.metrics?.dirtyRooms ?? 0), meta: `${operations.housekeepingTasks?.length ?? 0} Aufgaben` },
        {
            key: 'open-folios',
            label: 'Offene Folios',
            value: `${activeProperty?.currencyCode ?? 'CHF'} ${Number(operations.metrics?.openBalance ?? 0).toFixed(2)}`,
            meta: `${operations.metrics?.openFolios ?? 0} offene Konten`,
        },
    ], [activeProperty?.currencyCode, operations, setup.totalRooms]);
    const setupSteps = useMemo(() => {
        const hasProperty = setup.totalProperties > 0;
        const hasRoomTypes = setup.totalRoomTypes > 0;
        const hasRooms = setup.totalRooms > 0;
        return [
            {
                key: 'property',
                label: 'Hotel und Betriebsdaten anlegen',
                state: hasProperty ? 'done' : 'next',
            },
            {
                key: 'units',
                label: 'Zimmertypen und Zimmer erfassen',
                state: hasRooms ? 'done' : hasProperty ? 'next' : 'waiting',
            },
            { key: 'rates', label: 'Raten und Verfügbarkeiten definieren', state: operations.ratePlans?.length ? 'done' : hasRooms ? 'next' : 'waiting' },
            { key: 'payments', label: 'Folios und Zahlungsarten aktivieren', state: operations.ratePlans?.length ? 'done' : 'waiting' },
            { key: 'channels', label: 'Buchungskanäle anbinden', state: 'waiting' },
        ];
    }, [operations.ratePlans?.length, setup.totalProperties, setup.totalRoomTypes, setup.totalRooms]);
    const filteredCommands = useMemo(() => {
        const normalizedQuery = commandQuery.trim().toLocaleLowerCase('de-CH');
        if (!normalizedQuery) return commandItems;
        return commandItems.filter((command) =>
            command.label.toLocaleLowerCase('de-CH').includes(normalizedQuery)
        );
    }, [commandQuery]);

    useEffect(() => {
        let cancelled = false;
        const loadSetup = async () => {
            setSetupLoading(true);
            setSetupError('');
            try {
                const response = await api.get('/api/pms/setup');
                if (cancelled) return;
                setSetup(response.data ?? emptySetup);
                const firstPropertyId = response.data?.properties?.[0]?.id ?? null;
                setActivePropertyId((current) => current ?? firstPropertyId);
            } catch (error) {
                if (cancelled) return;
                setSetupError(
                    error?.response?.data?.detail
                    || error?.response?.data?.message
                    || 'Die PMS-Stammdaten konnten nicht geladen werden.'
                );
            } finally {
                if (!cancelled) setSetupLoading(false);
            }
        };
        loadSetup();
        return () => {
            cancelled = true;
        };
    }, []);

    useEffect(() => {
        if (!activeProperty?.id) {
            setOperations(emptyOperations);
            setOperationalHealth(emptyHealth);
            return undefined;
        }
        let cancelled = false;
        const loadOperations = async () => {
            setOperationsLoading(true);
            setOperationsError('');
            try {
                const response = await api.get('/api/pms/operations', {
                    params: {
                        propertyId: activeProperty.id,
                        businessDate: toDateKey(businessDate),
                    },
                });
                if (!cancelled) setOperations(response.data ?? emptyOperations);
            } catch (error) {
                if (!cancelled) setOperationsError(
                    error?.response?.data?.detail
                    || error?.response?.data?.message
                    || 'Die operativen PMS-Daten konnten nicht geladen werden.'
                );
            } finally {
                if (!cancelled) setOperationsLoading(false);
            }
        };
        loadOperations();
        return () => {
            cancelled = true;
        };
    }, [activeProperty?.id, businessDate]);

    useEffect(() => {
        if (!activeProperty?.id) {
            setOperationalHealth(emptyHealth);
            setHealthError('');
            return undefined;
        }
        let cancelled = false;
        const loadHealth = async () => {
            try {
                const response = await api.get('/api/pms/health', {
                    params: { propertyId: activeProperty.id },
                });
                if (!cancelled) {
                    setOperationalHealth(response.data ?? emptyHealth);
                    setHealthError('');
                }
            } catch (error) {
                if (!cancelled) {
                    setOperationalHealth(emptyHealth);
                    setHealthError(
                        error?.response?.data?.detail
                        || error?.response?.data?.message
                        || 'Der PMS-Betriebsstatus konnte nicht geprüft werden.'
                    );
                }
            }
        };
        loadHealth();
        return () => {
            cancelled = true;
        };
    }, [activeProperty?.id]);

    useEffect(() => {
        const handleShortcut = (event) => {
            if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
                event.preventDefault();
                setCommandOpen(true);
            }
            if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'e') {
                event.preventDefault();
                setCommandOpen(false);
                setSetupOpen(true);
            }
            if (event.key === 'Escape') {
                if (commandOpen) {
                    setCommandOpen(false);
                } else if (setupOpen) {
                    setSetupOpen(false);
                } else if (activeNavigation !== 'overview') {
                    setActiveNavigation('overview');
                    setInitialOperationAction(null);
                }
            }
        };
        window.addEventListener('keydown', handleShortcut);
        return () => window.removeEventListener('keydown', handleShortcut);
    }, [activeNavigation, commandOpen, setupOpen]);

    useEffect(() => {
        if (commandOpen) {
            commandReturnFocusRef.current = document.activeElement;
            commandInputRef.current?.focus();
        } else {
            setCommandQuery('');
            if (commandReturnFocusRef.current instanceof HTMLElement) {
                commandReturnFocusRef.current.focus();
                commandReturnFocusRef.current = null;
            }
        }
    }, [commandOpen]);

    const hasOpenDialog = commandOpen || setupOpen || activeNavigation !== 'overview';

    useEffect(() => {
        if (!hasOpenDialog) return undefined;

        const previousOverflow = document.body.style.overflow;
        const previousPaddingRight = document.body.style.paddingRight;
        const viewportWidth = document.documentElement.clientWidth;
        const scrollbarWidth = viewportWidth > 0 ? Math.max(0, window.innerWidth - viewportWidth) : 0;

        document.body.style.overflow = 'hidden';
        if (scrollbarWidth > 0) {
            document.body.style.paddingRight = `${scrollbarWidth}px`;
        }

        return () => {
            document.body.style.overflow = previousOverflow;
            document.body.style.paddingRight = previousPaddingRight;
        };
    }, [hasOpenDialog]);

    const moveDate = (days) => {
        setBusinessDate((current) => {
            const next = new Date(current);
            next.setDate(next.getDate() + days);
            return next;
        });
    };

    const selectAction = (actionKey) => {
        if (actionKey === 'setup') {
            setSetupOpen(true);
            setCommandOpen(false);
            return;
        }
        const targetSections = {
            reservation: 'reservations',
            'check-in': 'reservations',
            'check-out': 'reservations',
            'guest-search': 'guests',
            payment: 'folios',
            'room-plan': 'room-plan',
        };
        setInitialOperationAction(actionKey);
        setActiveNavigation(targetSections[actionKey] ?? actionKey);
        setSelectedAction(null);
        setCommandOpen(false);
    };

    const openNavigation = (navigationKey) => {
        setActiveNavigation(navigationKey);
        setInitialOperationAction(null);
        setSelectedAction(null);
    };

    return (
        <div className="pms-page">
            <Navbar />
            <div className="pms-app-shell">
                <aside className="pms-sidebar" aria-label="PMS-Navigation">
                    <button
                        type="button"
                        className="pms-property-switcher"
                        onClick={() => setSetupOpen(true)}
                    >
                        <span className="pms-property-mark" aria-hidden="true">CH</span>
                        <div>
                            <small>Aktiver Betrieb</small>
                            <strong>
                                {setupLoading ? 'Hotel wird geladen…' : activeProperty?.name ?? 'Hotel einrichten'}
                            </strong>
                        </div>
                        <span className="pms-sidebar-chevron" aria-hidden="true">⌄</span>
                    </button>

                    <nav className="pms-section-nav">
                        <span className="pms-nav-label">Hotelbetrieb</span>
                        {navigationItems.map((item) => (
                            <button
                                type="button"
                                key={item.key}
                                className={activeNavigation === item.key ? 'is-active' : ''}
                                onClick={() => openNavigation(item.key)}
                            >
                                <span aria-hidden="true">{item.code}</span>
                                {item.label}
                            </button>
                        ))}
                    </nav>

                    <div className="pms-sidebar-status">
                        <span className="pms-status-dot" aria-hidden="true" />
                        <div>
                            <strong>Chrono lokal aktiv</strong>
                            <small>Keine externen Systeme verbunden</small>
                        </div>
                    </div>
                </aside>

                <main className="pms-main">
                    <header className="pms-topbar">
                        <div>
                            <span className="pms-eyebrow">Chrono Hotel OS</span>
                            <h1>Guten Tag, {displayName}</h1>
                            <p>Deine operative Übersicht für den aktuellen Hotelbetrieb.</p>
                        </div>
                        <div className="pms-topbar-tools">
                            <button
                                type="button"
                                className="pms-command-trigger"
                                ref={commandTriggerRef}
                                onClick={() => setCommandOpen(true)}
                            >
                                <span aria-hidden="true">⌕</span>
                                Suchen oder Aktion starten
                                <kbd>Ctrl K</kbd>
                            </button>
                            <div className="pms-mode-switch" aria-label="Bedienmodus">
                                <button
                                    type="button"
                                    className={operationMode === 'simple' ? 'is-active' : ''}
                                    onClick={() => setOperationMode('simple')}
                                >
                                    Einfach
                                </button>
                                <button
                                    type="button"
                                    className={operationMode === 'pro' ? 'is-active' : ''}
                                    onClick={() => setOperationMode('pro')}
                                >
                                    Profi
                                </button>
                            </div>
                        </div>
                    </header>

                    <section className="pms-business-bar" aria-label="Betriebstag und Status">
                        <div className="pms-business-date">
                            <button type="button" onClick={() => moveDate(-1)} aria-label="Vorheriger Tag">←</button>
                            <div>
                                <small>Betriebstag</small>
                                <strong>{formattedDate}</strong>
                            </div>
                            <button type="button" onClick={() => moveDate(1)} aria-label="Nächster Tag">→</button>
                            <button type="button" className="pms-today-button" onClick={() => setBusinessDate(new Date())}>
                                Heute
                            </button>
                        </div>
                        <div className="pms-operational-state">
                            <span className={`pms-status-dot ${
                                setupError || healthError || operationalHealth.status === 'CRITICAL'
                                    ? 'is-error'
                                    : operationalHealth.status === 'WARNING'
                                        ? 'is-warning'
                                        : ''
                            }`} aria-hidden="true" />
                            <div>
                                <small>Systemstatus</small>
                                <strong>
                                    {setupError || healthError
                                        ? 'PMS-Stammdaten nicht erreichbar'
                                        : operationalHealth.status === 'CRITICAL'
                                            ? 'Kritischer Betriebsalarm'
                                            : operationalHealth.status === 'WARNING'
                                                ? 'Betriebsprüfung mit Hinweisen'
                                        : setup.foundationComplete
                                            ? 'Hotelfundament betriebsbereit'
                                            : 'Hoteleinrichtung offen'}
                                </strong>
                            </div>
                        </div>
                    </section>

                    {setupError && (
                        <section className="pms-action-notice is-error" role="alert">
                            <div>
                                <span className="pms-action-icon" aria-hidden="true">!</span>
                                <div>
                                    <strong>Verbindung zur PMS-API fehlgeschlagen</strong>
                                    <p>{setupError}</p>
                                </div>
                            </div>
                        </section>
                    )}

                    {operationsError && (
                        <section className="pms-action-notice is-error" role="alert">
                            <div>
                                <span className="pms-action-icon" aria-hidden="true">!</span>
                                <div>
                                    <strong>Operative PMS-Daten nicht erreichbar</strong>
                                    <p>{operationsError}</p>
                                </div>
                            </div>
                        </section>
                    )}

                    {healthError && (
                        <section className="pms-action-notice is-error" role="alert">
                            <div>
                                <span className="pms-action-icon" aria-hidden="true">!</span>
                                <div>
                                    <strong>Betriebsüberwachung nicht erreichbar</strong>
                                    <p>{healthError}</p>
                                </div>
                            </div>
                        </section>
                    )}

                    {selectedAction && (
                        <section className="pms-action-notice" aria-live="polite">
                            <div>
                                <span className="pms-action-icon" aria-hidden="true">→</span>
                                <div>
                                    <strong>{selectedAction}</strong>
                                    <p>
                                        Dieser Arbeitsbereich ist im Hauptdashboard vorbereitet.
                                        Die Datenfunktion bauen wir im nächsten PMS-Schritt an.
                                    </p>
                                </div>
                            </div>
                            <button type="button" onClick={() => setSelectedAction(null)} aria-label="Hinweis schließen">×</button>
                        </section>
                    )}

                    <section className="pms-quick-section" aria-labelledby="pms-quick-heading">
                        <div className="pms-section-heading">
                            <div>
                                <span className="pms-eyebrow">Rezeption</span>
                                <h2 id="pms-quick-heading">Schnellaktionen</h2>
                            </div>
                            <p>
                                {operationMode === 'simple'
                                    ? 'Die häufigsten Vorgänge mit einem Klick starten.'
                                    : 'Für schnelle Bedienung werden die Tastaturkürzel eingeblendet.'}
                            </p>
                        </div>
                        <div className="pms-quick-grid">
                            {quickActions.map((action, index) => (
                                <button
                                    type="button"
                                    key={action.key}
                                    className={index === 0 ? 'is-primary' : ''}
                                    onClick={() => selectAction(action.key)}
                                >
                                    <span className="pms-quick-number" aria-hidden="true">
                                        {String(index + 1).padStart(2, '0')}
                                    </span>
                                    <span className="pms-quick-copy">
                                        <strong>{action.label}</strong>
                                        <small>{action.description}</small>
                                    </span>
                                    {operationMode === 'pro' && <kbd>{action.shortcut}</kbd>}
                                    <span className="pms-quick-arrow" aria-hidden="true">→</span>
                                </button>
                            ))}
                        </div>
                    </section>

                    <section className="pms-metrics" aria-label="Kennzahlen des Betriebstags">
                        {metrics.map((metric) => (
                            <article key={metric.key}>
                                <span>{metric.label}</span>
                                <strong>{metric.value}</strong>
                                <small>{metric.meta}</small>
                            </article>
                        ))}
                    </section>

                    <div className="pms-dashboard-grid">
                        <section className="pms-panel pms-arrivals-panel">
                            <div className="pms-panel-header">
                                <div>
                                    <span className="pms-eyebrow">Front Office</span>
                                    <h2>Anreisen heute</h2>
                                </div>
                                <button type="button" onClick={() => openNavigation('reservations')}>Alle anzeigen</button>
                            </div>
                            <div className="pms-table-head" aria-hidden="true">
                                <span>Gast</span>
                                <span>Zeit</span>
                                <span>Zimmer</span>
                                <span>Status</span>
                            </div>
                            {operations.arrivals?.length ? operations.arrivals.slice(0, 5).map((arrival) => (
                                <div className="pms-arrival-row" key={arrival.id}>
                                    <div>
                                        <strong>{arrival.guestName}</strong>
                                        <small>{arrival.confirmationCode}</small>
                                    </div>
                                    <span>{activeProperty?.checkInTime?.slice(0, 5) ?? '15:00'}</span>
                                    <span>{arrival.roomNumber ?? arrival.roomTypeName}</span>
                                    <button type="button" onClick={() => selectAction('check-in')}>
                                        {arrival.status === 'CHECKED_IN' ? 'In-House' : 'Check-in'}
                                    </button>
                                </div>
                            )) : (
                                <div className="pms-empty-row">
                                    <span className="pms-empty-symbol" aria-hidden="true">A</span>
                                    <div>
                                        <strong>Keine Anreisen vorhanden</strong>
                                        <p>Sobald Reservierungen erfasst sind, erscheinen die heutigen Anreisen hier.</p>
                                    </div>
                                    <button type="button" onClick={() => selectAction('reservation')}>Reservierung anlegen</button>
                                </div>
                            )}
                        </section>

                        <section className="pms-panel pms-room-state-panel">
                            <div className="pms-panel-header">
                                <div>
                                    <span className="pms-eyebrow">Zimmerstatus</span>
                                    <h2>Bereitschaft</h2>
                                </div>
                                <button type="button" onClick={() => openNavigation('room-plan')}>Zimmerplan</button>
                            </div>
                            <div className="pms-room-state-empty">
                                <div className="pms-room-ring" aria-label={`${activeRoomCount} Zimmer eingerichtet`}>
                                    <strong>{activeRoomCount}</strong>
                                    <span>Zimmer</span>
                                </div>
                                <div className="pms-room-legend">
                                    <span><i className="is-ready" /> Sauber & frei <strong>{operations.rooms?.filter((room) => room.housekeepingStatus === 'CLEAN' && !room.currentReservation).length ?? 0}</strong></span>
                                    <span><i className="is-dirty" /> Zu reinigen <strong>{operations.metrics?.dirtyRooms ?? 0}</strong></span>
                                    <span><i className="is-occupied" /> Belegt <strong>{operations.metrics?.occupiedRooms ?? 0}</strong></span>
                                    <span><i className="is-blocked" /> Gesperrt <strong>{blockedRoomCount}</strong></span>
                                </div>
                            </div>
                        </section>

                        <section className="pms-panel pms-departures-panel">
                            <div className="pms-panel-header">
                                <div>
                                    <span className="pms-eyebrow">Front Office</span>
                                    <h2>Abreisen & offene Folios</h2>
                                </div>
                                <button type="button" onClick={() => openNavigation('folios')}>Folios öffnen</button>
                            </div>
                            {operations.departures?.length || Number(operations.metrics?.openBalance ?? 0) > 0 ? (
                                <div className="pms-operational-summary">
                                    <div><span>Abreisen</span><strong>{operations.departures?.length ?? 0}</strong></div>
                                    <div><span>Offener Betrag</span><strong>{activeProperty?.currencyCode ?? 'CHF'} {Number(operations.metrics?.openBalance ?? 0).toFixed(2)}</strong></div>
                                    <button type="button" onClick={() => openNavigation('folios')}>Folios bearbeiten</button>
                                </div>
                            ) : (
                                <div className="pms-empty-compact">
                                    <span className="pms-empty-symbol" aria-hidden="true">CHF</span>
                                    <div>
                                        <strong>Keine Abreisen oder offenen Beträge</strong>
                                        <p>Check-outs und Zahlungsbedarf werden hier priorisiert.</p>
                                    </div>
                                </div>
                            )}
                        </section>

                        <section className="pms-panel pms-housekeeping-panel">
                            <div className="pms-panel-header">
                                <div>
                                    <span className="pms-eyebrow">Housekeeping</span>
                                    <h2>Arbeitslast</h2>
                                </div>
                                <button type="button" onClick={() => openNavigation('housekeeping')}>Aufgaben öffnen</button>
                            </div>
                            <div className="pms-capacity-row">
                                <div>
                                    <small>Benötigt</small>
                                    <strong>{((operations.housekeepingTasks ?? []).reduce((sum, task) => sum + task.estimatedMinutes, 0) / 60).toFixed(1)} h</strong>
                                </div>
                                <span aria-hidden="true">→</span>
                                <div>
                                    <small>Eingeplant</small>
                                    <strong>0 h</strong>
                                </div>
                                <span className="pms-capacity-state">{operations.housekeepingTasks?.length ?? 0} Aufgaben</span>
                            </div>
                            <p className="pms-panel-note">
                                Abreisen erzeugen automatisch priorisierte Reinigungsaufgaben. Sauberstatus und
                                Check-in-Bereitschaft bleiben dadurch synchron.
                            </p>
                        </section>

                        <section className="pms-panel pms-system-panel">
                            <div className="pms-panel-header">
                                <div>
                                    <span className="pms-eyebrow">Integration Control Center</span>
                                    <h2>Systeme</h2>
                                </div>
                                <button type="button" onClick={() => openNavigation('integrations')}>Details</button>
                            </div>
                            <div className="pms-system-list">
                                {operationalHealth.components?.length ? operationalHealth.components.map((component) => (
                                    <div key={component.key}>
                                        <span><i className={
                                            component.status === 'OK'
                                                ? 'is-online'
                                                : component.status === 'CRITICAL'
                                                    ? 'is-error'
                                                    : 'is-warning'
                                        } /> {component.label}</span>
                                        <strong>{component.summary}</strong>
                                    </div>
                                )) : (
                                    <div>
                                        <span><i className="is-neutral" /> Betriebsüberwachung</span>
                                        <strong>Noch nicht geprüft</strong>
                                    </div>
                                )}
                            </div>
                        </section>

                        <section className="pms-panel pms-ai-panel">
                            <div className="pms-panel-header">
                                <div>
                                    <span className="pms-eyebrow">Operations Watch</span>
                                    <h2>Prüfungen</h2>
                                </div>
                                <span className="pms-local-badge">Keine Blackbox</span>
                            </div>
                            {operationalHealth.alerts?.length ? (
                                <div className="pms-record-list">
                                    {operationalHealth.alerts.map((alert) => (
                                        <article className="pms-record" key={alert.code}>
                                            <div>
                                                <span>{alert.severity}</span>
                                                <strong>{alert.title}</strong>
                                                <small>{alert.details} · {alert.recommendedAction}</small>
                                            </div>
                                        </article>
                                    ))}
                                </div>
                            ) : (
                                <div className="pms-empty-compact">
                                    <span className="pms-empty-symbol" aria-hidden="true">✓</span>
                                    <div>
                                        <strong>Keine aktiven Betriebsalarme</strong>
                                        <p>Datenbank, Integrationsqueue, Audit und Sicherungen werden nachvollziehbar geprüft.</p>
                                    </div>
                                </div>
                            )}
                        </section>
                    </div>

                    <section className="pms-setup-panel">
                        <div className="pms-setup-copy">
                            <span className="pms-eyebrow">Ersteinrichtung</span>
                            <h2>Das Hotelfundament zuerst</h2>
                            <p>
                                {setup.foundationComplete
                                    ? `${setup.totalProperties} Hotelbetrieb, ${setup.totalRoomTypes} Zimmertypen und ${setup.totalRooms} Zimmer sind persistent eingerichtet.`
                                    : 'Lege Hotelstruktur, Zimmertypen und konkrete Zimmer an. Diese Stammdaten bilden die Grundlage für Verfügbarkeit, Reservierungen und Aufenthalte.'}
                            </p>
                            <button type="button" onClick={() => setSetupOpen(true)}>
                                {setup.foundationComplete ? 'Einrichtung verwalten' : 'Einrichtung beginnen'}
                            </button>
                        </div>
                        <ol className="pms-setup-list">
                            {setupSteps.map((step, index) => (
                                <li
                                    key={step.key}
                                    className={`${step.state === 'next' ? 'is-next' : ''} ${step.state === 'done' ? 'is-done' : ''}`}
                                >
                                    <span>{index + 1}</span>
                                    <strong>{step.label}</strong>
                                    <small>
                                        {step.state === 'done'
                                            ? 'Abgeschlossen'
                                            : step.state === 'next'
                                                ? 'Als Nächstes'
                                                : 'Wartet auf Grundlage'}
                                    </small>
                                </li>
                            ))}
                        </ol>
                    </section>
                </main>
            </div>

            {setupOpen && (
                <PmsSetupWorkspace
                    setup={setup}
                    activePropertyId={activeProperty?.id ?? null}
                    canManage={canManagePms}
                    onSetupChange={(nextSetup) => {
                        setSetup(nextSetup ?? emptySetup);
                        setSetupError('');
                    }}
                    onPropertyChange={setActivePropertyId}
                    onClose={() => setSetupOpen(false)}
                />
            )}

            {activeNavigation !== 'overview' && (
                <PmsOperationsWorkspace
                    section={activeNavigation}
                    setup={setup}
                    operations={operations}
                    property={activeProperty}
                    businessDate={toDateKey(businessDate)}
                    canManage={canManagePms}
                    canManageGuestPrivacy={canManageGuestPrivacy}
                    initialAction={initialOperationAction}
                    onOperationsChange={(nextOperations) => {
                        setOperations(nextOperations ?? emptyOperations);
                        setOperationsError('');
                    }}
                    onClose={() => {
                        setActiveNavigation('overview');
                        setInitialOperationAction(null);
                    }}
                />
            )}

            {commandOpen && (
                <div className="pms-command-backdrop" role="presentation" onMouseDown={() => setCommandOpen(false)}>
                    <section
                        className="pms-command-palette"
                        role="dialog"
                        aria-modal="true"
                        aria-labelledby="pms-command-title"
                        onMouseDown={(event) => event.stopPropagation()}
                    >
                        <div className="pms-command-search">
                            <span aria-hidden="true">⌕</span>
                            <label className="sr-only" htmlFor="pms-command-input">PMS durchsuchen</label>
                            <input
                                id="pms-command-input"
                                ref={commandInputRef}
                                value={commandQuery}
                                onChange={(event) => setCommandQuery(event.target.value)}
                                placeholder="Gast, Reservierung oder Aktion suchen..."
                            />
                            <kbd>Esc</kbd>
                        </div>
                        <div className="pms-command-results">
                            <span id="pms-command-title">Schnellaktionen</span>
                            {filteredCommands.length > 0 ? filteredCommands.map((command) => (
                                <button type="button" key={command.key} onClick={() => selectAction(command.key)}>
                                    <span>{command.label}</span>
                                    <kbd>{command.hint}</kbd>
                                </button>
                            )) : (
                                <p>Keine passende Aktion gefunden.</p>
                            )}
                        </div>
                        <footer>
                            <span>Chrono führt sensible oder finanzielle Aktionen nie ohne Bestätigung aus.</span>
                        </footer>
                    </section>
                </div>
            )}
        </div>
    );
};

export default PmsDashboard;
