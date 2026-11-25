import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useSearchParams } from 'react-router-dom';
import Navbar from '../../components/Navbar.jsx';
import { useWorkspaceTabs } from '../../components/workspace/WorkspaceTabsContext.jsx';
import ConfigurableDashboard from '../../components/dashboard/ConfigurableDashboard.jsx';
import { useAuth } from '../../context/AuthContext.jsx';
import { useRefreshOnMutation } from '../../hooks/useRefreshOnMutation.js';
import api from '../../utils/api.js';
import { ACCESS_MANAGE, hasPageAccess, isAdminUser, canManagePmsSettings } from '../../utils/pageAccess.js';
import { getUserDisplayName } from '../../utils/userDisplay.js';
import PmsSetupWorkspace from './PmsSetupWorkspace.jsx';
import PmsOperationsWorkspace from './PmsOperationsWorkspace.jsx';
import usePmsLiveRefresh from './usePmsLiveRefresh.js';
import { PMS_SECTION_PERMISSIONS, pmsHasPermission } from './pmsAccess.js';
import { reconcilePmsOfflineAccess } from './pmsOfflineStore.js';
import { getPmsEnumLabel } from './pmsTerminology.js';
import { PMS_NAVIGATION_GROUPS, PMS_SECTION_KEYS } from './pmsNavigation.js';
import { PmsTranslationBoundary, usePmsLocale } from './pmsI18n.jsx';
import '../../styles/PmsDashboardScoped.css';
import './PmsDashboardWidgets.css';

const quickActions = [
    {
        key: 'walk-in',
        label: 'Gast vor Ort aufnehmen',
        description: 'Gastdaten, Meldeschein, Zimmer und Check-in in einem Ablauf',
        shortcut: 'Alt W',
    },
    {
        key: 'reservation',
        label: 'Reservierung anlegen',
        description: 'Telefon-, E-Mail- oder Direktbuchung erfassen',
        shortcut: 'Ctrl N',
    },
    {
        key: 'check-in',
        label: 'Einchecken',
        description: 'Anreise prüfen und Gast einchecken',
        shortcut: 'Ctrl I',
    },
    {
        key: 'check-out',
        label: 'Auschecken',
        description: 'Gastkonto prüfen und Gast auschecken',
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
        label: 'Zahlung erfassen',
        description: 'Offene Gastkonten und Zahlungen bearbeiten',
        shortcut: 'Ctrl P',
    },
];

const commandItems = [
    { key: 'setup', label: 'Hotel und Zimmer einrichten', hint: 'Ctrl E' },
    { key: 'walk-in', label: 'Gast vor Ort aufnehmen und einchecken', hint: 'Alt W' },
    { key: 'reservation', label: 'Direktreservierung anlegen', hint: 'Ctrl N' },
    { key: 'guest-search', label: 'Gast oder Reservierung suchen', hint: 'Ctrl G' },
    { key: 'check-in', label: 'Gast einchecken', hint: 'Ctrl I' },
    { key: 'check-out', label: 'Gast auschecken', hint: 'Ctrl O' },
    { key: 'payment', label: 'Zahlung erfassen', hint: 'Ctrl P' },
    { key: 'room-plan', label: 'Zimmerplan öffnen', hint: 'Ctrl R' },
];

const PMS_DASHBOARD_DEFAULT_LAYOUT = [
    { id: 'quick-actions', visible: true, order: 0, size: 'full', x: 0, y: 0, w: 12, h: 4 },
    { id: 'metrics', visible: true, order: 1, size: 'full', x: 0, y: 4, w: 12, h: 2 },
    { id: 'arrivals', visible: true, order: 2, size: 'M', x: 0, y: 6, w: 6, h: 5 },
    { id: 'room-status', visible: true, order: 3, size: 'M', x: 6, y: 6, w: 6, h: 5 },
    { id: 'departures-folios', visible: true, order: 4, size: 'M', x: 0, y: 11, w: 6, h: 4 },
    { id: 'housekeeping', visible: true, order: 5, size: 'M', x: 6, y: 11, w: 6, h: 4 },
    { id: 'systems', visible: true, order: 6, size: 'M', x: 0, y: 15, w: 6, h: 5 },
    { id: 'checks', visible: true, order: 7, size: 'M', x: 6, y: 15, w: 6, h: 5 },
    { id: 'setup', visible: true, order: 8, size: 'full', x: 0, y: 20, w: 12, h: 5 },
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
    roomBlocks: [],
    maintenanceWorkOrders: [],
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

const formatBusinessDate = (date, locale) => new Intl.DateTimeFormat(locale, {
    weekday: 'long',
    day: '2-digit',
    month: 'long',
    year: 'numeric',
}).format(date);

const PmsDashboard = () => {
    const { currentUser } = useAuth();
    const workspace = useWorkspaceTabs();
    const location = useLocation();
    const locale = usePmsLocale();
    const [searchParams, setSearchParams] = useSearchParams();
    const [businessDate, setBusinessDate] = useState(() => new Date());
    const requestedSection = searchParams.get('section') ?? 'overview';
    const activeNavigation = PMS_SECTION_KEYS.has(requestedSection) ? requestedSection : 'overview';
    const [hotelAccess, setHotelAccess] = useState(null);
    const [accessStatus, setAccessStatus] = useState('loading');
    const [commandOpen, setCommandOpen] = useState(false);
    const [commandQuery, setCommandQuery] = useState('');
    const [selectedAction, setSelectedAction] = useState(null);
    const [setup, setSetup] = useState(emptySetup);
    const [setupLoading, setSetupLoading] = useState(true);
    const [setupError, setSetupError] = useState('');
    const [setupOpen, setSetupOpen] = useState(false);
    const [settingsSetup, setSettingsSetup] = useState(null);
    const [settingsSetupError, setSettingsSetupError] = useState('');
    const [activePropertyId, setActivePropertyId] = useState(null);
    const [operationsSnapshot, setOperationsSnapshot] = useState({ scope: null, data: emptyOperations });
    const [operationsLoading, setOperationsLoading] = useState(false);
    const [operationsError, setOperationsError] = useState('');
    const [operationalHealth, setOperationalHealth] = useState(emptyHealth);
    const [healthError, setHealthError] = useState('');
    const [initialOperationAction, setInitialOperationAction] = useState(null);
    const handledActionRequestRef = useRef(null);
    const commandInputRef = useRef(null);
    const commandTriggerRef = useRef(null);
    const commandReturnFocusRef = useRef(null);
    const setupRequestRef = useRef(null);
    const operationsRequestRef = useRef(null);
    const healthRequestRef = useRef(null);
    const accessIdentityRef = useRef(null);

    const navigateToSection = useCallback((sectionKey, options = {}) => {
        const nextParams = new URLSearchParams(searchParams);
        if (!sectionKey || sectionKey === 'overview') {
            nextParams.delete('section');
        } else {
            nextParams.set('section', sectionKey);
        }
        const url = `/pms${nextParams.size ? `?${nextParams}` : ''}`;
        if (workspace && !options.replace) {
            workspace.openRoute(url, { forceNew: options.forceNew === true, ...(options.state ? { state: options.state } : {}) });
        } else {
            setSearchParams(nextParams, { replace: options.replace === true, state: options.state });
        }
    }, [searchParams, setSearchParams, workspace]);

    useEffect(() => {
        const action = location.state?.pmsAction;
        const requestId = location.state?.pmsActionRequestId;
        if (!requestId || handledActionRequestRef.current === requestId
            || ![...quickActions.map((item) => item.key), 'room-plan'].includes(action)) return;
        handledActionRequestRef.current = requestId;
        if (action === 'walk-in') setBusinessDate(new Date());
        setInitialOperationAction(action);
    }, [location.state]);

    const formattedDate = useMemo(() => formatBusinessDate(businessDate, locale), [businessDate, locale]);
    const displayName = getUserDisplayName(currentUser) || currentUser?.username || 'Gastgeber';
    const canManagePms = hasPageAccess(currentUser, 'pms', ACCESS_MANAGE);
    const canManageSettings = canManagePmsSettings(currentUser);
    const canManageGuestPrivacy = canManagePms && isAdminUser(currentUser);
    const activeProperty = useMemo(
        () => setup.properties.find((property) => property.id === activePropertyId)
            ?? setup.properties[0]
            ?? null,
        [activePropertyId, setup.properties],
    );
    const effectiveAccess = hotelAccess ?? (canManageSettings ? { master: true } : null);
    const awaitingAccess = !canManageSettings && hotelAccess === null;
    const sectionAccessDenied = accessStatus === 'denied' || (hotelAccess !== null && activeProperty?.id != null && activeNavigation !== 'overview'
        && !pmsHasPermission(effectiveAccess, activeProperty?.id, PMS_SECTION_PERMISSIONS[activeNavigation]));
    const missingHotelAssignment = !setupLoading && !setupError && !activeProperty && !canManageSettings;
    const displayedOperationsScope = `${activeProperty?.id || ''}:${toDateKey(businessDate)}`;
    const operations = operationsSnapshot.scope === displayedOperationsScope ? operationsSnapshot.data : emptyOperations;
    const currentOperationsScope = useRef(displayedOperationsScope);
    currentOperationsScope.current = displayedOperationsScope;
    const navigationGroups = PMS_NAVIGATION_GROUPS.map((group) => ({ ...group,
        items: group.items.filter((item) => item.key === 'overview'
            || pmsHasPermission(effectiveAccess, activeProperty?.id, PMS_SECTION_PERMISSIONS[item.key])),
    })).filter((group) => group.items.length);
    const sectionCanManage = canManagePms && pmsHasPermission(
        effectiveAccess, activeProperty?.id, PMS_SECTION_PERMISSIONS[activeNavigation], true);
    useEffect(() => {
        let active = true; let controller = null;
        const identity = `${currentUser?.id ?? ''}:${currentUser?.username ?? ''}`;
        if (accessIdentityRef.current !== identity) {
            accessIdentityRef.current = identity; setHotelAccess(null); setAccessStatus('loading');
        }
        const refreshAccess = async () => {
            if (!active || controller) return;
            const request = new AbortController(); controller = request;
            const token = localStorage.getItem('token');
            try {
                const { data } = await api.get('/api/pms/access/me', { signal: request.signal });
                if (!active || request.signal.aborted || token !== localStorage.getItem('token')) return;
                if (typeof data?.master !== 'boolean' || !Array.isArray(data.properties)
                    || (data.userId != null && currentUser?.id != null && String(data.userId) !== String(currentUser.id))) {
                    setAccessStatus((status) => status === 'loading' ? 'unavailable' : status); return;
                }
                reconcilePmsOfflineAccess(data, token);
                setHotelAccess(data); setAccessStatus('ready');
            } catch (error) {
                if (!active || request.signal.aborted || token !== localStorage.getItem('token')) return;
                if ([401, 403].includes(error.response?.status)) {
                    const denied = { master: false, properties: [] };
                    reconcilePmsOfflineAccess(denied, token);
                    setHotelAccess(denied); setAccessStatus('denied');
                } else setAccessStatus((status) => status === 'loading' ? 'unavailable' : status);
            } finally { if (controller === request) controller = null; }
        };
        refreshAccess();
        const timer = setInterval(refreshAccess, 60_000);
        window.addEventListener('focus', refreshAccess); window.addEventListener('online', refreshAccess);
        return () => { active = false; clearInterval(timer); controller?.abort(); window.removeEventListener('focus', refreshAccess); window.removeEventListener('online', refreshAccess); };
    }, [currentUser?.id, currentUser?.username]);
    const roomStatusSummary = useMemo(() => {
        const rooms = operations.rooms ?? [];
        const dateKey = toDateKey(businessDate);
        const activeRoomBlocks = (operations.roomBlocks ?? [])
            .filter((block) => block.status === 'ACTIVE')
            .filter((block) => block.startDate <= dateKey && dateKey < block.endDate);
        const inventoryBlockingRoomIds = new Set(
            activeRoomBlocks
                .filter((block) => ['OUT_OF_ORDER', 'OWNER_USE'].includes(block.type))
                .map((block) => block.roomId)
        );
        const activeRooms = rooms.filter((room) => room.operationalStatus !== 'INACTIVE');
        const sellableRooms = activeRooms.filter(
            (room) => room.operationalStatus === 'IN_SERVICE' && !inventoryBlockingRoomIds.has(room.id)
        );
        const sellableRoomIds = new Set(sellableRooms.map((room) => room.id));
        const limitedServiceRoomIds = new Set([
            ...activeRoomBlocks
                .filter((block) => block.type === 'OUT_OF_SERVICE')
                .map((block) => block.roomId),
            ...sellableRooms
                .filter((room) => room.housekeepingStatus === 'OUT_OF_SERVICE')
                .map((room) => room.id),
        ].filter((roomId) => sellableRoomIds.has(roomId)));
        const unavailableRoomIds = new Set([
            ...activeRooms
                .filter((room) => room.operationalStatus !== 'IN_SERVICE')
                .map((room) => room.id),
            ...inventoryBlockingRoomIds,
        ]);
        return {
            total: activeRooms.length || operations.metrics?.totalRooms || 0,
            cleanAndFree: sellableRooms.filter(
                (room) => room.housekeepingStatus === 'CLEAN'
                    && !room.currentReservation
                    && !limitedServiceRoomIds.has(room.id)
            ).length,
            dirty: sellableRooms.filter((room) => room.housekeepingStatus === 'DIRTY').length,
            cleaning: sellableRooms.filter((room) => room.housekeepingStatus === 'IN_PROGRESS').length,
            inspection: sellableRooms.filter((room) => room.housekeepingStatus === 'INSPECTION').length,
            limitedService: limitedServiceRoomIds.size,
            unavailable: unavailableRoomIds.size,
        };
    }, [businessDate, operations.metrics?.totalRooms, operations.roomBlocks, operations.rooms]);
    const metrics = useMemo(() => [
        {
            key: 'occupancy',
            label: 'Auslastung',
            value: `${operations.metrics?.occupancyPercent ?? 0} %`,
            meta: `${operations.metrics?.occupiedRooms ?? 0} von ${operations.metrics?.totalRooms ?? setup.totalRooms} Zimmern verkauft oder belegt`,
        },
        { key: 'in-house', label: 'Gäste im Haus', value: String(operations.metrics?.inHouse ?? 0), meta: 'Eingecheckte Aufenthalte' },
        { key: 'arrivals', label: 'Anreisen', value: String(operations.metrics?.arrivals ?? 0), meta: 'Am Betriebstag' },
        { key: 'departures', label: 'Abreisen', value: String(operations.metrics?.departures ?? 0), meta: 'Am Betriebstag' },
        {
            key: 'housekeeping',
            label: 'Zu reinigen',
            value: String(operations.rooms?.length ? roomStatusSummary.dirty : operations.metrics?.dirtyRooms ?? 0),
            meta: `${operations.housekeepingTasks?.length ?? 0} Aufgaben`,
        },
        {
            key: 'open-folios',
            label: 'Offene Gastkonten',
            value: `${activeProperty?.currencyCode ?? 'CHF'} ${Number(operations.metrics?.openBalance ?? 0).toFixed(2)}`,
            meta: `${operations.metrics?.openFolios ?? 0} offene Gastkonten`,
        },
    ], [activeProperty?.currencyCode, operations, roomStatusSummary.dirty, setup.totalRooms]);
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
            { key: 'rates', label: 'Ratenpläne und Verfügbarkeiten definieren', state: operations.ratePlans?.length ? 'done' : hasRooms ? 'next' : 'waiting' },
            { key: 'payments', label: 'Gastkonten und Zahlungsarten aktivieren', state: operations.ratePlans?.length ? 'done' : 'waiting' },
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
        if (!PMS_SECTION_KEYS.has(requestedSection)) {
            navigateToSection('overview', { replace: true });
        }
    }, [navigateToSection, requestedSection]);

    useEffect(() => {
        if (!setupOpen) { setSettingsSetup(null); return undefined; }
        const controller = new AbortController();
        setSettingsSetupError('');
        api.get('/api/pms/setup', { signal: controller.signal }).then(({ data }) => {
            if (!controller.signal.aborted) setSettingsSetup(data ?? emptySetup);
        }).catch((error) => {
            if (!controller.signal.aborted) setSettingsSetupError(error?.response?.data?.detail || 'Die Hoteleinrichtung konnte nicht geladen werden.');
        });
        return () => controller.abort();
    }, [setupOpen]);

    const loadSetup = useCallback(async ({ background = false } = {}) => {
        setupRequestRef.current?.abort();
        const controller = new AbortController();
        setupRequestRef.current = controller;
        if (!background) { setSetupLoading(true); setSetupError(''); }
        try {
            const response = await api.get('/api/pms/setup', { params: { includeRooms: false }, signal: controller.signal });
            if (controller.signal.aborted || setupRequestRef.current !== controller) return;
            setSetupError('');
            setSetup(response.data ?? emptySetup);
            const firstPropertyId = response.data?.properties?.[0]?.id ?? null;
            setActivePropertyId((current) => {
                const propertyStillExists = response.data?.properties?.some((property) => property.id === current);
                return propertyStillExists ? current : firstPropertyId;
            });
        } catch (error) {
            if (controller.signal.aborted || setupRequestRef.current !== controller) return;
            setSetupError(
                error?.response?.data?.detail
                || error?.response?.data?.message
                || 'Die PMS-Stammdaten konnten nicht geladen werden.'
            );
        } finally {
            if (setupRequestRef.current === controller) {
                setupRequestRef.current = null;
                setSetupLoading(false);
            }
        }
    }, []);

    const loadOperations = useCallback(async ({ background = false } = {}) => {
        operationsRequestRef.current?.abort();
        if (!activeProperty?.id) {
            operationsRequestRef.current = null;
            setOperationsSnapshot({ scope: null, data: emptyOperations });
            setOperationsError('');
            if (!background) setOperationsLoading(false);
            return;
        }
        const controller = new AbortController();
        operationsRequestRef.current = controller;
        if (!background) setOperationsLoading(true);
        setOperationsError('');
        try {
            const response = await api.get('/api/pms/operations', {
                params: {
                    propertyId: activeProperty.id,
                    businessDate: toDateKey(businessDate),
                },
                signal: controller.signal,
            });
            if (controller.signal.aborted || operationsRequestRef.current !== controller) return;
            setOperationsSnapshot({ scope: `${activeProperty.id}:${toDateKey(businessDate)}`, data: response.data ?? emptyOperations });
        } catch (error) {
            if (controller.signal.aborted || operationsRequestRef.current !== controller) return;
            setOperationsError(
                error?.response?.data?.detail
                || error?.response?.data?.message
                || 'Die operativen PMS-Daten konnten nicht geladen werden.'
            );
        } finally {
            if (operationsRequestRef.current === controller) {
                operationsRequestRef.current = null;
                setOperationsLoading(false);
            }
        }
    }, [activeProperty?.id, businessDate]);

    const loadHealth = useCallback(async () => {
        healthRequestRef.current?.abort();
        if (!activeProperty?.id) {
            healthRequestRef.current = null;
            setOperationalHealth(emptyHealth);
            setHealthError('');
            return;
        }
        const controller = new AbortController();
        healthRequestRef.current = controller;
        try {
            const response = await api.get('/api/pms/health', {
                params: { propertyId: activeProperty.id },
                signal: controller.signal,
            });
            if (controller.signal.aborted || healthRequestRef.current !== controller) return;
            setOperationalHealth(response.data ?? emptyHealth);
            setHealthError('');
        } catch (error) {
            if (controller.signal.aborted || healthRequestRef.current !== controller) return;
            setOperationalHealth(emptyHealth);
            setHealthError(
                error?.response?.data?.detail
                || error?.response?.data?.message
                || 'Der PMS-Betriebsstatus konnte nicht geprüft werden.'
            );
        } finally {
            if (healthRequestRef.current === controller) healthRequestRef.current = null;
        }
    }, [activeProperty?.id]);

    const refreshPms = useCallback(async () => {
        await Promise.all([
            loadSetup({ background: true }),
            loadOperations({ background: true }),
            loadHealth(),
        ]);
    }, [loadHealth, loadOperations, loadSetup]);

    useRefreshOnMutation(['pms'], refreshPms, {
        debounceMs: 120,
        refreshOnFocus: true,
        focusThrottleMs: 30_000,
    });
    usePmsLiveRefresh(() => loadOperations({ background: true }), { enabled: Boolean(activeProperty?.id), propertyId: activeProperty?.id });
    usePmsLiveRefresh(() => loadSetup({ background: true }), { enabled: Boolean(setupError) });

    useEffect(() => {
        loadSetup();
        return () => {
            setupRequestRef.current?.abort();
        };
    }, [loadSetup]);

    useEffect(() => {
        loadOperations();
        return () => {
            operationsRequestRef.current?.abort();
        };
    }, [loadOperations]);

    useEffect(() => {
        loadHealth();
        return () => {
            healthRequestRef.current?.abort();
        };
    }, [loadHealth]);

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

    const hasOpenDialog = commandOpen || setupOpen;

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
        if (actionKey === 'walk-in') {
            // A personal arrival is always handled on the actual local hotel day.
            // Keeping the dashboard on a previously selected planning day would mix
            // two business dates in the same reception workflow.
            setBusinessDate(new Date());
        }
        const targetSections = {
            'walk-in': 'reservations',
            reservation: 'reservations',
            'check-in': 'reservations',
            'check-out': 'reservations',
            'guest-search': 'guests',
            payment: 'folios',
            'room-plan': 'room-plan',
        };
        setInitialOperationAction(actionKey);
        navigateToSection(targetSections[actionKey] ?? actionKey, { state: {
            pmsAction: actionKey,
            pmsActionRequestId: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
        } });
        setSelectedAction(null);
        setCommandOpen(false);
    };

    const openNavigation = (navigationKey, options) => {
        navigateToSection(navigationKey, options);
        setInitialOperationAction(null);
        setSelectedAction(null);
    };

    useEffect(() => {
        const handleShortcut = (event) => {
            const key = event.key.toLowerCase();
            const primaryModifier = event.ctrlKey || event.metaKey;

            if (primaryModifier && key === 'k') {
                event.preventDefault();
                setCommandOpen(true);
                return;
            }
            if (primaryModifier && key === 'e') {
                event.preventDefault();
                selectAction('setup');
                return;
            }
            if (event.altKey && key === 'w') {
                event.preventDefault();
                selectAction('walk-in');
                return;
            }
            const actionByKey = {
                n: 'reservation',
                i: 'check-in',
                o: 'check-out',
                g: 'guest-search',
                p: 'payment',
                r: 'room-plan',
            };
            if (primaryModifier && actionByKey[key]) {
                event.preventDefault();
                selectAction(actionByKey[key]);
                return;
            }
            if (event.key === 'Escape') {
                if (commandOpen) {
                    setCommandOpen(false);
                } else if (setupOpen) {
                    setSetupOpen(false);
                } else if (activeNavigation !== 'overview') {
                    navigateToSection('overview');
                    setInitialOperationAction(null);
                }
            }
        };
        window.addEventListener('keydown', handleShortcut);
        return () => window.removeEventListener('keydown', handleShortcut);
    }, [activeNavigation, commandOpen, navigateToSection, setupOpen]);

    const pmsDashboardRegistry = [
        {
            id: 'quick-actions',
            title: 'Schnellaktionen',
            description: 'Die häufigsten Rezeptionsvorgänge direkt starten.',
            allowedContexts: ['PMS'],
            requiredPagePermission: 'pms',
            featureKey: 'pms',
            defaultSize: 'full',
            sizes: ['L', 'full'],
            component: (
                <section className="pms-quick-section" aria-labelledby="pms-quick-heading">
                    <div className="pms-section-heading">
                        <div>
                            <span className="pms-eyebrow">Rezeption</span>
                            <h2 id="pms-quick-heading">Schnellaktionen</h2>
                        </div>
                        <p>
                            Für schnelle Bedienung werden die Tastaturkürzel eingeblendet.
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
                                <kbd>{action.shortcut}</kbd>
                                <span className="pms-quick-arrow" aria-hidden="true">→</span>
                            </button>
                        ))}
                    </div>
                </section>
            ),
        },
        {
            id: 'metrics',
            title: 'Kennzahlen',
            description: 'Auslastung, Gäste, An- und Abreisen sowie offene Gastkonten.',
            allowedContexts: ['PMS'],
            requiredPagePermission: 'pms',
            featureKey: 'pms',
            defaultSize: 'full',
            sizes: ['M', 'L', 'full'],
            component: (
                <section className="pms-metrics" aria-label="Kennzahlen des Betriebstags">
                    {metrics.map((metric) => (
                        <article key={metric.key}>
                            <span>{metric.label}</span>
                            <strong>{metric.value}</strong>
                            <small>{metric.meta}</small>
                        </article>
                    ))}
                </section>
            ),
        },
        {
            id: 'arrivals',
            title: 'Anreisen',
            description: 'Die nächsten Anreisen des aktuellen Betriebstags.',
            allowedContexts: ['PMS'],
            requiredPagePermission: 'pms',
            featureKey: 'pms',
            defaultSize: 'M',
            sizes: ['M', 'L', 'full'],
            component: (
                <section className="pms-panel pms-arrivals-panel">
                    <div className="pms-panel-header">
                        <div>
                            <span className="pms-eyebrow">Rezeption</span>
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
                            {arrival.status === 'CONFIRMED' ? (
                                <button type="button" onClick={() => selectAction('check-in')}>
                                    Einchecken
                                </button>
                            ) : (
                                <span className="pms-arrival-status">
                                    {getPmsEnumLabel('ReservationStatus', arrival.status)}
                                </span>
                            )}
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
            ),
        },
        {
            id: 'room-status',
            title: 'Zimmerstatus',
            description: 'Reinigungs-, Belegungs- und Verkaufsstatus der Zimmer.',
            allowedContexts: ['PMS'],
            requiredPagePermission: 'pms',
            featureKey: 'pms',
            defaultSize: 'M',
            sizes: ['M', 'L', 'full'],
            component: (
                <section className="pms-panel pms-room-state-panel">
                    <div className="pms-panel-header">
                        <div>
                            <span className="pms-eyebrow">Zimmerstatus</span>
                            <h2>Zimmerstatus</h2>
                        </div>
                        <button type="button" onClick={() => openNavigation('room-plan')}>Zimmerplan</button>
                    </div>
                    <div className="pms-room-state-empty">
                        <div className="pms-room-ring" aria-label={`${roomStatusSummary.total} Zimmer eingerichtet`}>
                            <strong>{roomStatusSummary.total}</strong>
                            <span>Zimmer</span>
                        </div>
                        <div className="pms-room-legend">
                            <span><i className="is-ready" /> Sauber & frei <strong>{roomStatusSummary.cleanAndFree}</strong></span>
                            <span><i className="is-dirty" /> Zu reinigen <strong>{roomStatusSummary.dirty}</strong></span>
                            <span><i className="is-dirty" /> Reinigung läuft <strong>{roomStatusSummary.cleaning}</strong></span>
                            <span><i className="is-ready" /> Zu kontrollieren <strong>{roomStatusSummary.inspection}</strong></span>
                            <span><i className="is-occupied" /> Belegt <strong>{operations.metrics?.occupiedRooms ?? 0}</strong></span>
                            <span><i className="is-limited" /> Eingeschränkter Betrieb (OOS) <strong>{roomStatusSummary.limitedService}</strong></span>
                            <span><i className="is-blocked" /> Nicht verkaufbar <strong>{roomStatusSummary.unavailable}</strong></span>
                        </div>
                    </div>
                </section>
            ),
        },
        {
            id: 'departures-folios',
            title: 'Abreisen & Gastkonten',
            description: 'Anstehende Abreisen und offene Beträge im Blick behalten.',
            allowedContexts: ['PMS'],
            requiredPagePermission: 'pms',
            featureKey: 'pms',
            defaultSize: 'M',
            sizes: ['M', 'L', 'full'],
            component: (
                <section className="pms-panel pms-departures-panel">
                    <div className="pms-panel-header">
                        <div>
                            <span className="pms-eyebrow">Rezeption</span>
                            <h2>Abreisen & offene Gastkonten</h2>
                        </div>
                        <button type="button" onClick={() => openNavigation('folios')}>Gastkonten öffnen</button>
                    </div>
                    {operations.departures?.length || Number(operations.metrics?.openBalance ?? 0) > 0 ? (
                        <div className="pms-operational-summary">
                            <div><span>Abreisen</span><strong>{operations.departures?.length ?? 0}</strong></div>
                            <div><span>Offener Betrag</span><strong>{activeProperty?.currencyCode ?? 'CHF'} {Number(operations.metrics?.openBalance ?? 0).toFixed(2)}</strong></div>
                            <button type="button" onClick={() => openNavigation('folios')}>Gastkonten bearbeiten</button>
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
            ),
        },
        {
            id: 'housekeeping',
            title: 'Housekeeping',
            description: 'Arbeitslast und offene Reinigungsaufgaben.',
            allowedContexts: ['PMS'],
            requiredPagePermission: 'pms',
            featureKey: 'pms',
            defaultSize: 'M',
            sizes: ['M', 'L', 'full'],
            component: (
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
            ),
        },
        {
            id: 'systems',
            title: 'Systeme',
            description: 'Status der PMS-Schnittstellen und Betriebsüberwachung.',
            allowedContexts: ['PMS'],
            requiredPagePermission: 'pms',
            featureKey: 'pms',
            defaultSize: 'M',
            sizes: ['M', 'L', 'full'],
            component: (
                <section className="pms-panel pms-system-panel">
                    <div className="pms-panel-header">
                        <div>
                            <span className="pms-eyebrow">Schnittstellenstatus</span>
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
            ),
        },
        {
            id: 'checks',
            title: 'Prüfungen',
            description: 'Nachvollziehbare Betriebsalarme und empfohlene Maßnahmen.',
            allowedContexts: ['PMS'],
            requiredPagePermission: 'pms',
            featureKey: 'pms',
            defaultSize: 'M',
            sizes: ['M', 'L', 'full'],
            component: (
                <section className="pms-panel pms-ai-panel">
                    <div className="pms-panel-header">
                        <div>
                            <span className="pms-eyebrow">Betriebsprüfungen</span>
                            <h2>Prüfungen</h2>
                        </div>
                        <span className="pms-local-badge">Nachvollziehbar geprüft</span>
                    </div>
                    {operationalHealth.alerts?.length ? (
                        <div className="pms-record-list">
                            {operationalHealth.alerts.map((alert) => (
                                <article className="pms-record" key={alert.code}>
                                    <div>
                                        <span>{getPmsEnumLabel('OperationalHealthStatus', alert.severity)}</span>
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
                                <p>Datenbank, Übertragungen, Änderungsprotokoll und Sicherungen werden nachvollziehbar geprüft.</p>
                            </div>
                        </div>
                    )}
                </section>
            ),
        },
        {
            id: 'setup',
            title: 'Ersteinrichtung',
            description: 'Fortschritt der Hotel-, Zimmer- und Tarifkonfiguration.',
            allowedContexts: ['PMS'],
            requiredPagePermission: 'pms',
            featureKey: 'pms',
            defaultSize: 'full',
            sizes: ['L', 'full'],
            component: (
                <section className="pms-setup-panel">
                    <div className="pms-setup-copy">
                        <span className="pms-eyebrow">Ersteinrichtung</span>
                        <h2>Hoteleinrichtung abschliessen</h2>
                        <p>
                            {setup.foundationComplete
                                ? `${setup.totalProperties} Hotel, ${setup.totalRoomTypes} Zimmertypen und ${setup.totalRooms} Zimmer sind eingerichtet.`
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
            ),
        },
    ];

    // Legacy order/size preferences also inherit the content-appropriate default heights.
    pmsDashboardRegistry.forEach((widget) => {
        const defaults = PMS_DASHBOARD_DEFAULT_LAYOUT.find((item) => item.id === widget.id);
        if (defaults) widget.defaultRect = { x: defaults.x, y: defaults.y, w: defaults.w, h: defaults.h };
    });
    pmsDashboardRegistry.push(
        ...metrics.map((metric) => ({
            id: `metric-${metric.key}`,
            title: `Kennzahl: ${metric.label}`,
            description: 'Einzelne Kennzahl unabhängig vom Kennzahlenblock platzieren.',
            allowedContexts: ['PMS'],
            requiredPagePermission: 'pms',
            featureKey: 'pms',
            defaultVisible: false,
            defaultSize: 'S',
            defaultRect: { x: 0, y: 0, w: 3, h: 2 },
            minW: 2,
            minH: 2,
            component: (
                <article className="pms-dashboard-single-metric" aria-label={metric.label}>
                    <span>{metric.label}</span>
                    <strong>{metric.value}</strong>
                    <small>{metric.meta}</small>
                </article>
            ),
        })),
        ...quickActions.map((action) => ({
            id: `action-${action.key}`,
            title: `Aktion: ${action.label}`,
            description: 'Einzelne Schnellaktion an einer eigenen Position anzeigen.',
            allowedContexts: ['PMS'],
            requiredPagePermission: 'pms',
            featureKey: 'pms',
            defaultVisible: false,
            defaultSize: 'S',
            defaultRect: { x: 0, y: 0, w: 3, h: 2 },
            minW: 2,
            minH: 2,
            component: (
                <button type="button" className="pms-dashboard-action-tile" onClick={() => selectAction(action.key)}>
                    <span className="pms-dashboard-action-label"><strong>{action.label}</strong><span aria-hidden="true">↗</span></span>
                    <small>{action.description}</small>
                    <kbd>{action.shortcut}</kbd>
                </button>
            ),
        })),
    );

    const dashboardStorageIdentity = [
        currentUser?.company?.id ?? currentUser?.companyId ?? 'tenant',
        currentUser?.id ?? 'user',
    ].join(':');

    return (
        <PmsTranslationBoundary>
        <div className="pms-page">
            <Navbar />
            <div className="pms-app-shell">
                <aside className="pms-sidebar" aria-label="PMS-Navigation">
                    <button
                        type="button"
                        className="pms-property-switcher"
                        disabled={missingHotelAssignment}
                        onClick={() => setSetupOpen(true)}
                    >
                        <span className="pms-property-mark" aria-hidden="true">CH</span>
                        <div>
                            <small>Aktives Hotel</small>
                            <strong>
                                {setupLoading ? 'Hotel wird geladen…' : activeProperty?.name ?? (canManageSettings ? 'Hotel einrichten' : 'Kein Hotel zugewiesen')}
                            </strong>
                        </div>
                        <span className="pms-sidebar-chevron" aria-hidden="true">⌄</span>
                    </button>

                    <nav className="pms-section-nav" aria-label="PMS-Arbeitsbereiche">
                        {navigationGroups.map((group) => (
                            <div className="pms-nav-group" key={group.key}>
                                <span className="pms-nav-label">{group.label}</span>
                                {group.items.map((item) => (
                                    <button
                                        type="button"
                                        key={item.key}
                                        className={activeNavigation === item.key ? 'is-active' : ''}
                                        aria-current={activeNavigation === item.key ? 'page' : undefined}
                                        title={item.description}
                                        onClick={() => openNavigation(item.key)}
                                        onMouseDown={(event) => { if (event.button === 1) event.preventDefault(); }}
                                        onAuxClick={(event) => {
                                            if (event.button !== 1) return;
                                            event.preventDefault();
                                            openNavigation(item.key, { forceNew: true });
                                        }}
                                    >
                                        <span aria-hidden="true">{item.code}</span>
                                        {item.label}
                                    </button>
                                ))}
                            </div>
                        ))}
                    </nav>

                    <div className="pms-sidebar-status">
                        <span className="pms-status-dot" aria-hidden="true" />
                        <div>
                            <strong>{missingHotelAssignment ? 'Hotelzugriff fehlt' : 'PMS betriebsbereit'}</strong>
                            <small>{missingHotelAssignment ? 'Freigabe durch PMS-Master erforderlich' : 'Status- und Schnittstellenprüfung aktiv'}</small>
                        </div>
                    </div>
                </aside>

                <main className={`pms-main${activeNavigation === 'room-plan' ? ' pms-main-room-plan' : ''}`}>
                    <header className={`pms-topbar${activeNavigation !== 'overview' ? ' is-compact' : ''}`}>
                        <div>
                            <span className="pms-eyebrow">Chrono Hotel-PMS</span>
                            <h1>{activeNavigation === 'overview' ? `Guten Tag, ${displayName}` : activeProperty?.name ?? 'Hotelbetrieb'}</h1>
                            {activeNavigation === 'overview' && <p>Deine operative Übersicht für den aktuellen Hotelbetrieb.</p>}
                        </div>
                        <div className="pms-topbar-tools">
                            <button
                                type="button"
                                className="pms-command-trigger"
                                disabled={missingHotelAssignment}
                                ref={commandTriggerRef}
                                onClick={() => setCommandOpen(true)}
                            >
                                <span aria-hidden="true">⌕</span>
                                Suchen oder Aktion starten
                                <kbd>Ctrl K</kbd>
                            </button>
                        </div>
                    </header>

                    {awaitingAccess ? (
                        <section className="pms-action-notice" role="status"><div><p>{accessStatus === 'unavailable'
                            ? 'Die PMS-Berechtigungen konnten noch nicht geprüft werden. Die Prüfung wird bei Verbindung und regelmäßig wiederholt.'
                            : 'PMS-Berechtigungen werden geprüft …'}</p></div></section>
                    ) : !activeProperty && (setupLoading || setupError) ? (
                        <section className={`pms-action-notice${setupError ? ' is-error' : ''}`} role={setupError ? 'alert' : 'status'}>
                            <div><div><h2>{setupError ? 'PMS-Stammdaten nicht erreichbar' : 'Hotel wird geladen …'}</h2>
                                <p>{setupError || 'Hotel und verfügbare Arbeitsbereiche werden geladen.'}</p>
                                {setupError && <p>Die Verbindung wird automatisch erneut geprüft. Deine Hoteleinrichtung bleibt erhalten.</p>}
                            </div></div>
                            {setupError && <button type="button" onClick={() => loadSetup()}>Erneut laden</button>}
                        </section>
                    ) : sectionAccessDenied ? (
                        <section className="pms-action-notice" role="alert"><div><div><h2>Zugriff nicht freigegeben</h2><p>Für diesen Arbeitsbereich liegt keine aktuelle PMS-Berechtigung vor. Ein PMS-Master kann die Hotelrechte prüfen.</p></div></div></section>
                    ) : missingHotelAssignment ? (
                        <section className="pms-action-notice" aria-labelledby="pms-hotel-assignment-title">
                            <div><span className="pms-action-icon" aria-hidden="true">i</span><div>
                                <h2 id="pms-hotel-assignment-title">Hotelzuweisung erforderlich</h2>
                                <p>Deinem Konto ist noch kein Hotel zugewiesen. Ein PMS-Master muss dir das gewünschte Hotel und die benötigten Arbeitsbereiche freigeben.</p>
                                <p>Wende dich dafür an die PMS-Verantwortlichen deines Hotels. Danach kannst du das PMS erneut öffnen.</p>
                            </div></div>
                        </section>
                    ) : activeNavigation === 'overview' ? (
                        <>
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
                                            ? 'Hoteleinrichtung vollständig'
                                            : 'Hoteleinrichtung unvollständig'}
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
                                        Der gewählte Arbeitsbereich konnte nicht geöffnet werden.
                                        Bitte versuche es erneut oder prüfe deine Berechtigung.
                                    </p>
                                </div>
                            </div>
                            <button type="button" onClick={() => setSelectedAction(null)} aria-label="Hinweis schliessen">×</button>
                        </section>
                    )}

                    <ConfigurableDashboard
                        context="PMS"
                        layoutMode="free"
                        gridRowHeight={60}
                        scope={activeProperty?.id != null ? `property:${activeProperty.id}` : 'default'}
                        registry={pmsDashboardRegistry}
                        defaultLayout={PMS_DASHBOARD_DEFAULT_LAYOUT}
                        permissionContext={currentUser}
                        storageIdentity={dashboardStorageIdentity}
                        preferenceParams={activeProperty?.id != null ? { propertyId: activeProperty.id } : undefined}
                        remoteEnabled={activeProperty?.id != null}
                        className="pms-configurable-overview"
                        labels={{
                            customize: 'Übersicht anpassen',
                            configurationTitle: 'PMS-Übersicht',
                            configurationHint: 'Ziehe Bereiche an ihre gewünschte Position und passe Breite und Höhe an. Auch einzelne Kennzahlen und Schnellaktionen lassen sich hinzufügen.',
                            empty: 'Für diese PMS-Übersicht sind keine Bereiche verfügbar.',
                        }}
                    />
                        </>
                    ) : (
                        <PmsOperationsWorkspace
                            key={activeProperty?.id || 'loading'}
                            embedded
                            section={activeNavigation}
                            setup={setup}
                            operations={operations}
                            property={activeProperty}
                            businessDate={toDateKey(businessDate)}
                            canManage={sectionCanManage}
                            canRefund={canManagePms && pmsHasPermission(effectiveAccess, activeProperty?.id, 'REFUNDS', true)}
                            canFinance={canManagePms && pmsHasPermission(effectiveAccess, activeProperty?.id, 'FINANCE', true)}
                            canViewFinance={pmsHasPermission(effectiveAccess, activeProperty?.id, 'FINANCE')}
                            canViewFrontDesk={pmsHasPermission(effectiveAccess, activeProperty?.id, 'FRONT_DESK')}
                            canManageFrontDesk={canManagePms && pmsHasPermission(effectiveAccess, activeProperty?.id, 'FRONT_DESK', true)}
                            canManageRates={canManagePms && pmsHasPermission(effectiveAccess, activeProperty?.id, 'RATES', true)}
                            canManageHousekeeping={canManagePms && pmsHasPermission(effectiveAccess, activeProperty?.id, 'HOUSEKEEPING', true)}
                            canViewReports={pmsHasPermission(effectiveAccess, activeProperty?.id, 'REPORTS')}
                            canViewIntegrations={pmsHasPermission(effectiveAccess, activeProperty?.id, 'INTEGRATIONS')}
                            canManageSettings={canManageSettings}
                            canManageGuestPrivacy={canManageGuestPrivacy}
                            initialAction={initialOperationAction}
                            onSectionChange={openNavigation}
                            onOperationsChange={(nextOperations) => {
                                if (currentOperationsScope.current !== displayedOperationsScope) return;
                                if (nextOperations?.propertyId && nextOperations.propertyId !== activeProperty.id) return;
                                setOperationsSnapshot({ scope: displayedOperationsScope, data: nextOperations ?? emptyOperations });
                                setOperationsError('');
                            }}
                            onClose={() => {
                                navigateToSection('overview');
                                setInitialOperationAction(null);
                            }}
                        />
                    )}
                </main>
            </div>

            {setupOpen && !settingsSetup && <div className="pms-command-backdrop">
                <section className="pms-command-palette" role="dialog" aria-modal="true" aria-label="Hoteleinrichtung laden">
                    <p role={settingsSetupError ? 'alert' : 'status'}>{settingsSetupError || 'Hoteleinrichtung wird geladen…'}</p>
                    <button type="button" onClick={() => setSetupOpen(false)}>Schließen</button>
                </section>
            </div>}
            {setupOpen && settingsSetup && (
                <PmsSetupWorkspace
                    setup={settingsSetup}
                    activePropertyId={activeProperty?.id ?? null}
                    canManage={canManageSettings}
                    onSetupChange={(nextSetup) => {
                        setSetup(nextSetup ?? emptySetup);
                        setSettingsSetup(nextSetup ?? emptySetup);
                        setSetupError('');
                    }}
                    onPropertyChange={setActivePropertyId}
                    onClose={() => setSetupOpen(false)}
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
        </PmsTranslationBoundary>
    );
};

export default PmsDashboard;
