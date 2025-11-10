import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import Navbar from '../../components/Navbar';
import { useAuth } from '../../context/AuthContext';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import api from '../../utils/api';
import { useNavigate } from 'react-router-dom';
import '../../styles/AdminDashboardScoped.css';
import jsPDF from "jspdf";

import AdminWeekSection from './AdminWeekSection';
import EditTimeModal from './EditTimeModal';
import PrintUserTimesModal from './PrintUserTimesModal';
import VacationCalendarAdmin from '../../components/VacationCalendarAdmin';
import AdminDashboardKpis from './AdminDashboardKpis';
import AdminQuickFixPanel from './AdminQuickFixPanel';
import AdminVacationRequests from './AdminVacationRequests';
import AdminCorrectionsList from './AdminCorrectionsList';

import {
    getMondayOfWeek,
    formatLocalDateYMD,
    addDays,
    minutesToHHMM,
    formatDate,
    formatTime,
    processEntriesForReport,
    selectTrackableUsers,
} from './adminDashboardUtils';

const INBOX_FILTER_STORAGE_KEY = 'adminDashboard_inboxFilters_v1';
const INBOX_VIEWS_STORAGE_KEY = 'adminDashboard_savedViews_v1';

const DEFAULT_INBOX_FILTERS = {
    status: 'pending',
    types: ['vacation', 'correction'],
    user: null,
    department: null,
    lowRiskOnly: false,
    savedViewId: 'builtin:pending',
};

const isBrowserEnvironment = () => typeof window !== 'undefined' && !!window.localStorage;

const loadStoredFilters = () => {
    if (!isBrowserEnvironment()) return null;
    try {
        const raw = window.localStorage.getItem(INBOX_FILTER_STORAGE_KEY);
        if (!raw) return null;
        const parsed = JSON.parse(raw);
        if (!parsed || typeof parsed !== 'object') return null;
        const types = Array.isArray(parsed.types) && parsed.types.length > 0
            ? parsed.types.filter((type) => ['vacation', 'correction'].includes(type))
            : DEFAULT_INBOX_FILTERS.types;
        return {
            ...DEFAULT_INBOX_FILTERS,
            ...parsed,
            types,
        };
    } catch (error) {
        console.error('Failed to parse stored inbox filters', error);
        return null;
    }
};

const loadStoredViews = () => {
    if (!isBrowserEnvironment()) return [];
    try {
        const raw = window.localStorage.getItem(INBOX_VIEWS_STORAGE_KEY);
        if (!raw) return [];
        const parsed = JSON.parse(raw);
        if (!Array.isArray(parsed)) return [];
        return parsed.filter((view) => view && typeof view === 'object' && view.id && view.filters);
    } catch (error) {
        console.error('Failed to parse stored inbox views', error);
        return [];
    }
};

const persistFilters = (filters, searchValue) => {
    if (!isBrowserEnvironment()) return;
    try {
        window.localStorage.setItem(INBOX_FILTER_STORAGE_KEY, JSON.stringify({ ...filters, query: searchValue }));
    } catch (error) {
        console.error('Failed to persist inbox filters', error);
    }
};

const persistViews = (views) => {
    if (!isBrowserEnvironment()) return;
    try {
        window.localStorage.setItem(INBOX_VIEWS_STORAGE_KEY, JSON.stringify(views));
    } catch (error) {
        console.error('Failed to persist inbox views', error);
    }
};

const isLowRiskCorrection = (corr) => {
    if (!corr) return false;
    const entries = Array.isArray(corr.entries) && corr.entries.length > 0 ? corr.entries : [corr];
    const firstEntry = entries[0] || {};
    const desired = new Date(firstEntry.desiredTimestamp || corr.desiredTimestamp || 0);
    const original = new Date(firstEntry.originalTimestamp || corr.originalTimestamp || 0);
    if (Number.isNaN(desired.getTime()) || Number.isNaN(original.getTime())) return false;
    const deltaMinutes = Math.abs(desired.getTime() - original.getTime()) / 60000;
    const desiredHour = desired.getHours();
    const withinWindow = desiredHour >= 6 && desiredHour <= 22;
    const singlePair = (entries?.length ?? 1) <= 2;
    return deltaMinutes <= 15 && withinWindow && singlePair && !corr.denied && !corr.approved;
};

const AdminDashboard = () => {
    const { currentUser } = useAuth();
    const { notify } = useNotification();
    const { t } = useTranslation();
    const navigate = useNavigate();

    const [dailySummaries, setDailySummaries] = useState([]);
    const [allVacations, setAllVacations] = useState([]);
    const [allCorrections, setAllCorrections] = useState([]);
    const [users, setUsers] = useState([]);
    const [allSickLeaves, setAllSickLeaves] = useState([]);
    const [holidaysByCanton, setHolidaysByCanton] = useState({});

    const [selectedMonday, setSelectedMonday] = useState(getMondayOfWeek(new Date()));
    const [editModalVisible, setEditModalVisible] = useState(false);
    const [editDate, setEditDate] = useState(null);
    const [editTargetUsername, setEditTargetUsername] = useState('');
    const [editDayEntries, setEditDayEntries] = useState([]);

    const [printUserModalVisible, setPrintUserModalVisible] = useState(false);
    const [printUser, setPrintUser] = useState('');
    const [printUserStartDate, setPrintUserStartDate] = useState(formatLocalDateYMD(new Date()));
    const [printUserEndDate, setPrintUserEndDate] = useState(formatLocalDateYMD(new Date()));
    const [weeklyBalances, setWeeklyBalances] = useState([]);
    const defaultExpectedHours = 8.5;

    const {
        trackableUsers,
        fallbackApplied: didFallbackTrackableUsers,
        excludedUsernames,
    } = useMemo(
        () => selectTrackableUsers(users),
        [users]
    );

    const trackableUsernames = useMemo(() => {
        if (!Array.isArray(trackableUsers) || trackableUsers.length === 0) {
            return new Set();
        }
        return new Set(
            trackableUsers
                .map(user => user?.username)
                .filter(Boolean)
        );
    }, [trackableUsers]);

    const storedFilters = useMemo(() => loadStoredFilters(), []);
    const [inboxFilters, setInboxFilters] = useState(storedFilters || DEFAULT_INBOX_FILTERS);
    const [inboxSearch, setInboxSearch] = useState(() => (storedFilters?.query ? String(storedFilters.query) : ''));
    const [customViews, setCustomViews] = useState(() => loadStoredViews());
    const [, setActiveMainTab] = useState('team');
    const [quickFixQueue, setQuickFixQueue] = useState([]);
    const [vacationOpenSignal, setVacationOpenSignal] = useState(0);
    const [correctionOpenSignal, setCorrectionOpenSignal] = useState(0);

    const weekSectionRef = useRef(null);
    const vacationSectionRef = useRef(null);
    const correctionSectionRef = useRef(null);
    const gSequenceRef = useRef({ last: 0, count: 0 });

    const handleNavigateToVacations = useCallback(() => {
        setVacationOpenSignal((prev) => prev + 1);
        vacationSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, []);

    const handleNavigateToCorrections = useCallback(() => {
        setCorrectionOpenSignal((prev) => prev + 1);
        correctionSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, []);

    const filteredWeeklyBalances = useMemo(() => {
        if (!Array.isArray(weeklyBalances) || weeklyBalances.length === 0) {
            return [];
        }

        if (trackableUsernames.size === 0) {
            if (didFallbackTrackableUsers || excludedUsernames.size === 0) {
                return weeklyBalances.filter(entry => entry?.username);
            }

            return weeklyBalances.filter(
                entry => entry?.username && !excludedUsernames.has(entry.username)
            );
        }

        return weeklyBalances.filter(entry => entry?.username && trackableUsernames.has(entry.username));
    }, [weeklyBalances, trackableUsernames, didFallbackTrackableUsers, excludedUsernames]);

    const userMap = useMemo(() => {
        const map = new Map();
        users.forEach((user) => {
            if (user?.username) {
                map.set(user.username, user);
            }
        });
        return map;
    }, [users]);

    const myDepartment = currentUser?.departmentName || null;

    const inboxItems = useMemo(() => {
        const vacationItems = (Array.isArray(allVacations) ? allVacations : []).map((vac) => {
            const user = userMap.get(vac.username);
            const priorityDate = vac.startDate || vac.createdAt || vac.requestedAt;
            const parsedPriority = priorityDate ? new Date(priorityDate) : null;
            const priority = parsedPriority && !Number.isNaN(parsedPriority.getTime())
                ? parsedPriority.getTime()
                : Number.MAX_SAFE_INTEGER;
            return {
                id: `vac-${vac.id}`,
                entityId: vac.id,
                type: 'vacation',
                status: vac.approved ? 'approved' : vac.denied ? 'denied' : 'pending',
                username: vac.username,
                displayName: user ? `${user.firstName || ''} ${user.lastName || ''}`.trim() : vac.username,
                department: user?.departmentName || null,
                startDate: vac.startDate,
                endDate: vac.endDate,
                createdAt: vac.createdAt || vac.requestedAt,
                requestedAt: vac.requestedAt,
                priority,
                flags: {
                    halfDay: !!vac.halfDay,
                    usesOvertime: !!vac.usesOvertime,
                },
                isLowRisk: false,
                raw: vac,
            };
        });

        const correctionItems = (Array.isArray(allCorrections) ? allCorrections : []).map((corr) => {
            const user = userMap.get(corr.username);
            const priorityDate = corr.requestDate || corr.desiredTimestamp || corr.originalTimestamp;
            const parsedPriority = priorityDate ? new Date(priorityDate) : null;
            const priority = parsedPriority && !Number.isNaN(parsedPriority.getTime())
                ? parsedPriority.getTime()
                : Number.MAX_SAFE_INTEGER;
            const normalizedEntries = Array.isArray(corr.entries) ? corr.entries : [];
            return {
                id: `corr-${corr.id}`,
                entityId: corr.id,
                type: 'correction',
                status: corr.approved ? 'approved' : corr.denied ? 'denied' : 'pending',
                username: corr.username,
                displayName: user ? `${user.firstName || ''} ${user.lastName || ''}`.trim() : corr.username,
                department: user?.departmentName || null,
                createdAt: corr.requestDate,
                reason: corr.reason,
                priority,
                flags: {
                    entryCount: normalizedEntries.length,
                },
                entries: normalizedEntries,
                isLowRisk: isLowRiskCorrection(corr),
                raw: corr,
            };
        });

        return [...vacationItems, ...correctionItems].sort((a, b) => a.priority - b.priority);
    }, [allVacations, allCorrections, userMap]);

    const filteredInboxItems = useMemo(() => {
        let list = [...inboxItems];
        const { status, types, user, department, lowRiskOnly } = inboxFilters;
        if (status && status !== 'all') {
            list = list.filter((item) => item.status === status);
        }
        if (Array.isArray(types) && types.length > 0) {
            list = list.filter((item) => types.includes(item.type));
        }
        if (user) {
            list = list.filter((item) => item.username === user);
        }
        if (department) {
            list = list.filter((item) => (item.department || null) === department);
        }
        if (lowRiskOnly) {
            list = list.filter((item) => item.isLowRisk);
        }
        if (inboxSearch) {
            const query = inboxSearch.toLowerCase();
            list = list.filter((item) => {
                return [
                    item.username,
                    item.displayName,
                    item.reason,
                    item.type,
                    item.raw?.startDate,
                    item.raw?.endDate,
                ].some((value) => (value || '').toString().toLowerCase().includes(query));
            });
        }
        return list;
    }, [inboxItems, inboxFilters, inboxSearch]);

    const statusSummary = useMemo(() => {
        const summary = { pending: 0, vacations: 0, corrections: 0 };
        inboxItems.forEach((item) => {
            if (item.status === 'pending') summary.pending += 1;
            if (item.type === 'vacation' && item.status === 'pending') summary.vacations += 1;
            if (item.type === 'correction' && item.status === 'pending') summary.corrections += 1;
        });
        return summary;
    }, [inboxItems]);

    const inboxSummaryItems = useMemo(() => ([
        {
            id: 'pending',
            tone: 'warning',
            label: t('adminDashboard.inboxSummary.pendingLabel', 'Offene Vorgänge'),
            description: t('adminDashboard.inboxSummary.pendingDescription', 'Alles, was deine Entscheidung benötigt.'),
            count: statusSummary?.pending ?? 0,
        },
        {
            id: 'vacations',
            tone: 'info',
            label: t('adminDashboard.inboxSummary.vacationsLabel', 'Urlaubsanträge'),
            description: t('adminDashboard.inboxSummary.vacationsDescription', 'Abwesenheiten im Blick behalten.'),
            count: statusSummary?.vacations ?? 0,
        },
        {
            id: 'corrections',
            tone: 'accent',
            label: t('adminDashboard.inboxSummary.correctionsLabel', 'Korrekturanträge'),
            description: t('adminDashboard.inboxSummary.correctionsDescription', 'Zeitkorrekturen schnell abgleichen.'),
            count: statusSummary?.corrections ?? 0,
        },
    ]), [statusSummary, t]);
    const renderInboxSummary = () => (
        <section
            className="dashboard-summary-card"
            aria-label={t('adminDashboard.inboxSummary.title', 'Statusübersicht Posteingang')}
        >
            <header className="summary-header">
                <div className="summary-headline">
                    <h3>{t('adminDashboard.inboxSummary.title', 'Statusübersicht')}</h3>
                    <p>{t('adminDashboard.inboxSummary.subtitle', 'Was heute besondere Aufmerksamkeit benötigt.')}</p>
                </div>
                <span className="summary-total" aria-label={t('adminDashboard.inboxSummary.total', 'Offene Gesamtanzahl')}>
                    {statusSummary?.pending ?? 0}
                </span>
            </header>
            <ul className="summary-list">
                {inboxSummaryItems.map((item) => (
                    <li key={item.id} className={`summary-item summary-${item.tone}`}>
                        <span className="summary-count">{item.count}</span>
                        <div className="summary-content">
                            <span className="summary-label">{item.label}</span>
                            <span className="summary-description">{item.description}</span>
                        </div>
                    </li>
                ))}
            </ul>
        </section>
    );

    const [paletteOpen, setPaletteOpen] = useState(false);
    const [paletteQuery, setPaletteQuery] = useState('');
    const [paletteActiveIndex, setPaletteActiveIndex] = useState(0);
    const paletteInputRef = useRef(null);
    const searchInputRef = useRef(null);
    const searchDebounceRef = useRef(null);

    const [selectedInboxIds, setSelectedInboxIds] = useState([]);
    const [focusedInboxId, setFocusedInboxId] = useState(null);
    const [lastFocusedIndex, setLastFocusedIndex] = useState(null);
    const [activeQuickFilter, setActiveQuickFilter] = useState(inboxFilters.savedViewId || 'builtin:pending');
    const [decisionDrafts, setDecisionDrafts] = useState({});

    const lowRiskPending = useMemo(() => inboxItems.filter((item) => item.type === 'correction' && item.isLowRisk && item.status === 'pending'), [inboxItems]);

    useEffect(() => {
        setSelectedInboxIds((prev) => prev.filter((id) => filteredInboxItems.some((item) => item.id === id)));
        if (filteredInboxItems.length === 0) {
            setFocusedInboxId(null);
            setLastFocusedIndex(null);
            return;
        }
        if (!focusedInboxId || !filteredInboxItems.some((item) => item.id === focusedInboxId)) {
            setFocusedInboxId(filteredInboxItems[0].id);
            setLastFocusedIndex(0);
        } else {
            const idx = filteredInboxItems.findIndex((item) => item.id === focusedInboxId);
            setLastFocusedIndex(idx);
        }
    }, [filteredInboxItems, focusedInboxId]);

    const focusedInboxItem = useMemo(() => {
        if (!focusedInboxId) return null;
        return filteredInboxItems.find((item) => item.id === focusedInboxId) || null;
    }, [filteredInboxItems, focusedInboxId]);

    const applyFilter = useCallback((modifier, savedViewId = null) => {
        setInboxFilters((prev) => {
            const draft = typeof modifier === 'function' ? modifier({ ...prev }) : { ...prev, ...modifier };
            const next = { ...prev, ...draft };
            if (!Array.isArray(next.types) || next.types.length === 0) {
                next.types = [...DEFAULT_INBOX_FILTERS.types];
            } else {
                next.types = Array.from(new Set(next.types));
            }
            next.savedViewId = savedViewId;
            return next;
        });
        setActiveQuickFilter(savedViewId || 'custom');
    }, []);

    const clearSelection = useCallback(() => {
        setSelectedInboxIds([]);
    }, []);

    const clearSavedView = useCallback(() => {
        setInboxFilters((prev) => {
            if (!prev.savedViewId) return prev;
            return { ...prev, savedViewId: null };
        });
        setActiveQuickFilter('custom');
    }, []);

    const applyQuickFilter = useCallback((filterId) => {
        clearSelection();
        setInboxSearch('');
        switch (filterId) {
            case 'builtin:pending':
                applyFilter({
                    status: 'pending',
                    types: ['vacation', 'correction'],
                    user: null,
                    department: null,
                    lowRiskOnly: false,
                }, 'builtin:pending');
                break;
            case 'builtin:vacations':
                applyFilter({
                    status: 'pending',
                    types: ['vacation'],
                    user: null,
                    department: null,
                    lowRiskOnly: false,
                }, 'builtin:vacations');
                break;
            case 'builtin:corrections':
                applyFilter({
                    status: 'pending',
                    types: ['correction'],
                    user: null,
                    department: null,
                    lowRiskOnly: false,
                }, 'builtin:corrections');
                break;
            case 'builtin:lowRisk':
                applyFilter({
                    status: 'pending',
                    types: ['correction'],
                    lowRiskOnly: true,
                    user: null,
                    department: null,
                }, 'builtin:lowRisk');
                break;
            case 'builtin:department':
                if (myDepartment) {
                    applyFilter({
                        status: 'pending',
                        department: myDepartment,
                        user: null,
                        lowRiskOnly: false,
                    }, 'builtin:department');
                }
                break;
            case 'builtin:approved':
                applyFilter({
                    status: 'approved',
                    lowRiskOnly: false,
                }, 'builtin:approved');
                break;
            case 'builtin:denied':
                applyFilter({
                    status: 'denied',
                    lowRiskOnly: false,
                }, 'builtin:denied');
                break;
            default:
                applyFilter({}, filterId);
        }
    }, [applyFilter, clearSelection, myDepartment]);

    const builtInViews = useMemo(() => {
        const views = [
            {
                id: 'builtin:pending',
                label: t('adminDashboard.inbox.views.pending', 'Alle offen'),
                description: t('adminDashboard.inbox.views.pendingHint', 'Urlaub + Korrekturen · Status offen'),
                action: () => applyQuickFilter('builtin:pending'),
            },
            {
                id: 'builtin:vacations',
                label: t('adminDashboard.inbox.views.vacations', 'Urlaubscenter'),
                description: t('adminDashboard.inbox.views.vacationsHint', 'Nur Urlaubsanträge anzeigen'),
                action: () => applyQuickFilter('builtin:vacations'),
            },
            {
                id: 'builtin:corrections',
                label: t('adminDashboard.inbox.views.corrections', 'Korrekturcenter'),
                description: t('adminDashboard.inbox.views.correctionsHint', 'Nur Korrekturanträge anzeigen'),
                action: () => applyQuickFilter('builtin:corrections'),
            },
            {
                id: 'builtin:lowRisk',
                label: t('adminDashboard.inbox.views.lowRisk', '≤15 Min Low-Risk'),
                description: t('adminDashboard.inbox.views.lowRiskHint', 'Schnell genehmigbare Änderungen'),
                action: () => applyQuickFilter('builtin:lowRisk'),
            },
        ];
        if (myDepartment) {
            views.push({
                id: 'builtin:department',
                label: t('adminDashboard.inbox.views.myDepartment', 'Meine Abteilung'),
                description: t('adminDashboard.inbox.views.myDepartmentHint', 'Filter auf eigenes Team setzen'),
                action: () => applyQuickFilter('builtin:department'),
            });
        }
        views.push(
            {
                id: 'builtin:approved',
                label: t('adminDashboard.inbox.views.approved', 'Genehmigt'),
                description: t('adminDashboard.inbox.views.approvedHint', 'Historie genehmigter Anträge'),
                action: () => applyQuickFilter('builtin:approved'),
            },
            {
                id: 'builtin:denied',
                label: t('adminDashboard.inbox.views.denied', 'Abgelehnt'),
                description: t('adminDashboard.inbox.views.deniedHint', 'Abgelehnte Anträge im Blick behalten'),
                action: () => applyQuickFilter('builtin:denied'),
            },
        );
        return views;
    }, [applyQuickFilter, myDepartment, t]);

    const handleApplyCustomView = useCallback((view) => {
        if (!view) return;
        clearSelection();
        setInboxSearch(view.query || '');
        const filters = view.filters ? { ...view.filters } : {};
        delete filters.savedViewId;
        applyFilter(filters, view.id);
    }, [applyFilter, clearSelection]);

    const handleSaveCurrentView = useCallback(() => {
        if (!isBrowserEnvironment()) return;
        const name = window.prompt(t('adminDashboard.inbox.saveViewPrompt', 'Ansicht speichern als…'));
        if (!name) return;
        const filtersToStore = { ...inboxFilters };
        delete filtersToStore.savedViewId;
        const newView = {
            id: `custom-${Date.now()}`,
            label: name,
            filters: filtersToStore,
            query: inboxSearch,
        };
        setCustomViews((prev) => [...prev, newView]);
        applyFilter(filtersToStore, newView.id);
        setInboxSearch(inboxSearch);
    }, [inboxFilters, inboxSearch, applyFilter, t]);

    const handleDeleteView = useCallback((viewId) => {
        setCustomViews((prev) => prev.filter((view) => view.id !== viewId));
        if (inboxFilters.savedViewId === viewId) {
            applyQuickFilter('builtin:pending');
        }
    }, [applyQuickFilter, inboxFilters.savedViewId]);

    const handleRequestFocus = useCallback((item, index) => {
        if (!item) return;
        setFocusedInboxId(item.id);
        setLastFocusedIndex(index);
    }, []);

    const handleToggleSelect = useCallback((item) => {
        if (!item) return;
        setSelectedInboxIds((prev) => {
            if (prev.includes(item.id)) {
                return prev.filter((id) => id !== item.id);
            }
            return [...prev, item.id];
        });
    }, []);

    const handleToggleRange = useCallback((indexA, indexB) => {
        if (typeof indexA !== 'number' || typeof indexB !== 'number') return;
        const [start, end] = indexA < indexB ? [indexA, indexB] : [indexB, indexA];
        const idsInRange = filteredInboxItems.slice(start, end + 1).map((item) => item.id);
        setSelectedInboxIds((prev) => Array.from(new Set([...prev, ...idsInRange])));
    }, [filteredInboxItems]);

    const updateDecisionDraft = useCallback((itemId, value) => {
        setDecisionDrafts((prev) => ({ ...prev, [itemId]: value }));
    }, []);

    const approveItem = useCallback(async (item, comment = '') => {
        if (!item) return;
        if (item.type === 'vacation') {
            await handleApproveVacation(item.entityId);
        } else {
            await handleApproveCorrection(item.entityId, comment);
        }
        setDecisionDrafts((prev) => {
            const next = { ...prev };
            delete next[item.id];
            return next;
        });
    }, [handleApproveVacation, handleApproveCorrection]);

    const denyItem = useCallback(async (item, comment = '') => {
        if (!item) return;
        if (item.type === 'vacation') {
            await handleDenyVacation(item.entityId);
        } else {
            await handleDenyCorrection(item.entityId, comment);
        }
        setDecisionDrafts((prev) => {
            const next = { ...prev };
            delete next[item.id];
            return next;
        });
    }, [handleDenyVacation, handleDenyCorrection]);

    const handleBulkApproveSelection = useCallback(async () => {
        const candidates = filteredInboxItems.filter((item) => selectedInboxIds.includes(item.id) && item.status === 'pending');
        if (candidates.length === 0) return;
        await Promise.all(candidates.map((item) => approveItem(item, decisionDrafts[item.id] || '')));
        clearSelection();
    }, [approveItem, clearSelection, decisionDrafts, filteredInboxItems, selectedInboxIds]);

    const handleBulkDenySelection = useCallback(async () => {
        const candidates = filteredInboxItems.filter((item) => selectedInboxIds.includes(item.id) && item.status === 'pending');
        if (candidates.length === 0) return;
        await Promise.all(candidates.map((item) => denyItem(item, decisionDrafts[item.id] || '')));
        clearSelection();
    }, [clearSelection, decisionDrafts, denyItem, filteredInboxItems, selectedInboxIds]);

    const handleAutoApproveLowRisk = useCallback(async () => {
        const candidates = lowRiskPending.filter((item) => item.status === 'pending' && item.isLowRisk);
        if (candidates.length === 0) {
            notify(t('adminDashboard.actionStream.noLowRisk', 'Keine Low-Risk-Korrekturen gefunden.'), 'info');
            return;
        }
        await Promise.all(candidates.map((item) => approveItem(item, decisionDrafts[item.id] || '')));
        clearSelection();
        notify(
            t('adminDashboard.bulkDone', {
                count: candidates.length,
                defaultValue: '{{count}} Anträge verarbeitet',
                returnObjects: false,
            }),
            'success',
        );
    }, [approveItem, clearSelection, decisionDrafts, lowRiskPending, notify, t]);

    const commandList = useMemo(() => {
        const base = [
            {
                id: 'cmd-vacations',
                label: t('adminDashboard.commandPalette.openVacations', 'Urlaubscenter öffnen'),
                action: () => handleNavigateToVacations(),
            },
            {
                id: 'cmd-corrections',
                label: t('adminDashboard.commandPalette.openCorrections', 'Korrekturcenter öffnen'),
                action: () => handleNavigateToCorrections(),
            },
            {
                id: 'cmd-today-week',
                label: t('adminDashboard.commandPalette.jumpToday', 'Zu aktueller Woche springen'),
                action: () => handleCurrentWeek(),
            },
            {
                id: 'cmd-low-risk',
                label: t('adminDashboard.commandPalette.fixLowRisk', 'Offene ≤15 Min fixen'),
                action: () => handleAutoApproveLowRisk(),
            },
        ];
        const userCommands = users
            .filter((user) => user?.username)
            .slice(0, 25)
            .map((user) => ({
                id: `user-${user.username}`,
                label: t('adminDashboard.commandPalette.focusUser', 'User {{name}} fokussieren', {
                    name: `${user.firstName || ''} ${user.lastName || user.username}`.trim(),
                }),
                action: () => handleFocusUserFromTask(user.username),
            }));
        return [...base, ...userCommands];
    }, [handleAutoApproveLowRisk, handleFocusUserFromTask, handleCurrentWeek, handleNavigateToCorrections, handleNavigateToVacations, t, users]);

    const filteredCommands = useMemo(() => {
        if (!paletteQuery) return commandList;
        const query = paletteQuery.toLowerCase();
        return commandList.filter((command) => command.label.toLowerCase().includes(query));
    }, [commandList, paletteQuery]);

    const handleExecuteCommand = useCallback((command) => {
        if (!command) return;
        setPaletteOpen(false);
        command.action?.();
    }, []);

    useEffect(() => {
        if (paletteOpen) {
            setPaletteQuery('');
            setPaletteActiveIndex(0);
            requestAnimationFrame(() => {
                paletteInputRef.current?.focus();
            });
        }
    }, [paletteOpen]);

    useEffect(() => {
        if (paletteActiveIndex >= filteredCommands.length) {
            setPaletteActiveIndex(Math.max(filteredCommands.length - 1, 0));
        }
    }, [filteredCommands, paletteActiveIndex]);

    useEffect(() => () => {
        if (searchDebounceRef.current) {
            clearTimeout(searchDebounceRef.current);
        }
    }, []);

    const handleSearchTermChange = useCallback((value) => {
        if (searchDebounceRef.current) {
            clearTimeout(searchDebounceRef.current);
        }
        searchDebounceRef.current = setTimeout(() => {
            searchDebounceRef.current = null;
            setInboxSearch(value);
            if (value && inboxFilters.savedViewId) {
                clearSavedView();
            }
            if (value) {
                setActiveQuickFilter('custom');
            }
        }, 120);
    }, [clearSavedView, inboxFilters.savedViewId]);

    useEffect(() => {
        const handleKey = (event) => {
            const target = event.target;
            const isTypingTarget = target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable);

            if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
                event.preventDefault();
                setPaletteOpen((prev) => !prev);
                return;
            }

            if (event.key === '?' && !paletteOpen && !isTypingTarget) {
                event.preventDefault();
                notify(t('adminDashboard.shortcutCheatsheet', 'Shortcuts: J/K=Zeile · O=Öffnen · A=Approve · D=Deny · B=Bulk-Select · /=Suche · G G=Heute · H/L=Woche ±'), 'info');
                return;
            }

            if (paletteOpen) {
                if (event.key === 'Escape') {
                    event.preventDefault();
                    setPaletteOpen(false);
                    return;
                }
                if (event.key === 'ArrowDown') {
                    event.preventDefault();
                    setPaletteActiveIndex((prev) => Math.min(prev + 1, Math.max(filteredCommands.length - 1, 0)));
                    return;
                }
                if (event.key === 'ArrowUp') {
                    event.preventDefault();
                    setPaletteActiveIndex((prev) => Math.max(prev - 1, 0));
                    return;
                }
                if (event.key === 'Enter') {
                    event.preventDefault();
                    const command = filteredCommands[paletteActiveIndex];
                    if (command) {
                        handleExecuteCommand(command);
                    }
                    return;
                }
                return;
            }

            if (isTypingTarget) return;

            const key = event.key.toLowerCase();

            if (key === '/') {
                event.preventDefault();
                clearSavedView();
                searchInputRef.current?.focus();
                return;
            }

            if (key === 'h') {
                event.preventDefault();
                weekSectionRef.current?.handlePrevWeek?.();
                return;
            }

            if (key === 'l') {
                event.preventDefault();
                weekSectionRef.current?.handleNextWeek?.();
                return;
            }

            if (key === 'g') {
                const now = Date.now();
                if (now - gSequenceRef.current.last < 600) {
                    gSequenceRef.current.count += 1;
                } else {
                    gSequenceRef.current.count = 1;
                }
                gSequenceRef.current.last = now;
                if (gSequenceRef.current.count >= 2) {
                    event.preventDefault();
                    handleCurrentWeek();
                    gSequenceRef.current.count = 0;
                }
                return;
            }

            if (key === 'j') {
                event.preventDefault();
                if (filteredInboxItems.length === 0) return;
                const nextIndex = Math.min((lastFocusedIndex ?? 0) + 1, filteredInboxItems.length - 1);
                handleRequestFocus(filteredInboxItems[nextIndex], nextIndex);
                return;
            }

            if (key === 'k') {
                event.preventDefault();
                if (filteredInboxItems.length === 0) return;
                const nextIndex = Math.max((lastFocusedIndex ?? 0) - 1, 0);
                handleRequestFocus(filteredInboxItems[nextIndex], nextIndex);
                return;
            }

            if (key === 'o' || key === 'enter') {
                if (focusedInboxItem) {
                    event.preventDefault();
                    handleToggleSelect(focusedInboxItem);
                }
                return;
            }

            if (key === 'a') {
                if (focusedInboxItem && focusedInboxItem.status === 'pending') {
                    event.preventDefault();
                    approveItem(focusedInboxItem, decisionDrafts[focusedInboxItem.id] || '');
                }
                return;
            }

            if (key === 'd') {
                if (focusedInboxItem && focusedInboxItem.status === 'pending') {
                    event.preventDefault();
                    denyItem(focusedInboxItem, decisionDrafts[focusedInboxItem.id] || '');
                }
                return;
            }

            if (key === 'b') {
                if (focusedInboxItem) {
                    event.preventDefault();
                    handleToggleSelect(focusedInboxItem);
                }
            }
        };

        window.addEventListener('keydown', handleKey);
        return () => window.removeEventListener('keydown', handleKey);
    }, [approveItem, clearSavedView, decisionDrafts, denyItem, filteredCommands, filteredInboxItems, focusedInboxItem, handleCurrentWeek, handleExecuteCommand, handleRequestFocus, handleToggleSelect, lastFocusedIndex, notify, paletteActiveIndex, paletteOpen, t]);

    const [issueSummary, setIssueSummary] = useState({
        missing: 0,
        incomplete: 0,
        autoCompleted: 0,
        holidayPending: 0,
        totalWithIssue: 0,
    });
    const [activeIssuePill, setActiveIssuePill] = useState(null);

    const scheduleNextFrame = useCallback((callback) => {
        if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
            window.requestAnimationFrame(callback);
        } else {
            setTimeout(callback, 0);
        }
    }, []);

    const handleIssueSummaryUpdate = useCallback((summary) => {
        if (!summary) {
            return;
        }

        setIssueSummary(prevSummary => {
            if (!prevSummary) {
                return summary;
            }

            const keysToCompare = ['missing', 'incomplete', 'autoCompleted', 'holidayPending', 'totalWithIssue'];
            const hasChanges = keysToCompare.some(key => (prevSummary[key] || 0) !== (summary[key] || 0));

            return hasChanges ? summary : prevSummary;
        });
    }, []);

    const handleFocusIssues = useCallback((filterKey) => {
        setActiveMainTab('team');
        setActiveIssuePill(filterKey);
        if (weekSectionRef.current?.focusIssueType) {
            weekSectionRef.current.focusIssueType(filterKey);
        }
    }, [setActiveMainTab]);

    const handleResetIssueFilters = useCallback(() => {
        setActiveIssuePill(null);
        weekSectionRef.current?.resetIssueFilters?.();
    }, []);

    const handleShowIssueOverview = useCallback(() => {
        handleFocusIssues('all');
    }, [handleFocusIssues]);

    const handleFocusNegativeBalances = useCallback(() => {
        setActiveMainTab('team');
        setActiveIssuePill(null);
        weekSectionRef.current?.focusNegativeBalances?.();
    }, [setActiveMainTab]);

    const handleFocusPositiveBalances = useCallback(() => {
        setActiveMainTab('team');
        setActiveIssuePill(null);
        weekSectionRef.current?.focusPositiveBalances?.();
    }, [setActiveMainTab]);

    function handleFocusUserFromTask(username) {
        if (!username) return;
        setActiveIssuePill(null);
        scheduleNextFrame(() => {
            weekSectionRef.current?.focusUser?.(username);
        });
        setCorrectionOpenSignal((prev) => prev + 1);
        correctionSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    const handleQuickFixSelect = useCallback((item) => {
        if (!item) return;
        setActiveMainTab('team');
        scheduleNextFrame(() => {
            weekSectionRef.current?.focusQuickFixItem?.(item);
        });
    }, [scheduleNextFrame, setActiveMainTab]);


    useEffect(() => {
        persistFilters(inboxFilters, inboxSearch);
    }, [inboxFilters, inboxSearch]);

    useEffect(() => {
        persistViews(customViews);
    }, [customViews]);

    const isSuperAdmin = !!currentUser?.roles?.includes('ROLE_SUPERADMIN');

    const companyFeatureSet = useMemo(() => {
        const keys = currentUser?.companyFeatureKeys;
        if (!keys) return new Set();
        if (Array.isArray(keys)) return new Set(keys);
        return new Set(Object.values(keys));
    }, [currentUser?.companyFeatureKeys]);

    const hasFeature = useCallback((featureKey) => {
        if (!featureKey) return true;
        if (isSuperAdmin) return true;
        return companyFeatureSet.has(featureKey);
    }, [companyFeatureSet, isSuperAdmin]);

    const handleOpenAnalytics = useCallback(() => {
        navigate('/admin/analytics');
    }, [navigate]);

    const issueChipConfig = useMemo(() => ([
        {
            key: 'missing',
            icon: '❗',
            label: t('adminDashboard.issueRibbon.missing', 'Fehlende Zeiten'),
            count: issueSummary.missing,
        },
        {
            key: 'incomplete',
            icon: '⚠️',
            label: t('adminDashboard.issueRibbon.incomplete', 'Unvollständig'),
            count: issueSummary.incomplete,
        },
        {
            key: 'autoCompleted',
            icon: '🤖',
            label: t('adminDashboard.issueRibbon.autoCompleted', 'Auto beendet'),
            count: issueSummary.autoCompleted,
        },
        {
            key: 'holidayPending',
            icon: '🎉',
            label: t('adminDashboard.issueRibbon.holidayPending', 'Feiertag offen'),
            count: issueSummary.holidayPending,
        },
    ]), [issueSummary, t]);

    const hasIssues = issueSummary.totalWithIssue > 0;

    useEffect(() => {
        if (!hasIssues && activeIssuePill !== null) {
            setActiveIssuePill(null);
        }
    }, [hasIssues, activeIssuePill]);

    const fetchUsers = useCallback(async () => {
        try {
            const res = await api.get('/api/admin/users');
            setUsers(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error('Error loading users', err);
            notify(t('errors.fetchUsersError', 'Fehler beim Laden der Benutzer.'), 'error');
        }
    }, [notify, t]);

    const fetchAllDailySummaries = useCallback(async () => {
        try {
            const res = await api.get('/api/admin/timetracking/all-summaries');
            setDailySummaries(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error('Error loading daily summaries', err);
            setDailySummaries([]);
            notify(t('errors.fetchDailySummariesError', 'Fehler beim Laden der Tagesübersichten.'), 'error');
        }
    }, [notify, t]);

    const fetchAllVacations = useCallback(async () => {
        try {
            const res = await api.get('/api/vacation/all');
            setAllVacations(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error('Error loading vacation requests', err);
            setAllVacations([]);
            notify(t('errors.fetchVacationsError', 'Fehler beim Laden der Urlaubsanträge.'), 'error');
        }
    }, [notify, t]);

    const fetchAllCorrections = useCallback(async () => {
        try {
            const res = await api.get('/api/correction/all');
            setAllCorrections(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error('Error loading correction requests', err);
            setAllCorrections([]);
            notify(t('errors.fetchCorrectionsError', 'Fehler beim Laden der Korrekturanträge.'), 'error');
        }
    }, [notify, t]);

    const fetchTrackingBalances = useCallback(async () => {
        try {
            const res = await api.get('/api/admin/timetracking/admin/tracking-balances');
            setWeeklyBalances(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error("Fehler beim Laden der Tracking-Bilanzen:", err);
            setWeeklyBalances([]);
            notify(t('errors.fetchBalancesError', 'Fehler beim Laden der Salden.'), 'error');
        }
    }, [notify, t]);

    const fetchAllSickLeavesForAdmin = useCallback(async () => {
        try {
            const params = {};
            const res = await api.get('/api/sick-leave/company', { params });
            setAllSickLeaves(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error('Error loading sick leaves for admin dashboard', err);
            setAllSickLeaves([]);
            notify(t('errors.fetchSickLeaveErrorAdmin', 'Fehler beim Laden der Krankmeldungen für das Dashboard.'), 'error');
        }
    }, [notify, t]);

    const fetchHolidaysForAllRelevantCantons = useCallback(async () => {
        const currentYear = selectedMonday.getFullYear();
        const yearStartDate = `${currentYear}-01-01`;
        const yearEndDate = `${currentYear}-12-31`;

        const cantonsToFetch = new Set();
        if (currentUser?.companyCantonAbbreviation) {
            cantonsToFetch.add(currentUser.companyCantonAbbreviation);
        }
        users.forEach(user => {
            if (user.companyCantonAbbreviation) {
                cantonsToFetch.add(user.companyCantonAbbreviation);
            }
        });

        if (cantonsToFetch.size === 0) {
            cantonsToFetch.add('');
        }

        const newHolidaysByCantonState = { ...holidaysByCanton };
        let fetchOccurredForNewData = false;

        for (const canton of cantonsToFetch) {
            const cantonKey = canton || 'GENERAL';
            const yearFromState = holidaysByCanton[cantonKey]?.year;
            if (!newHolidaysByCantonState[cantonKey] || yearFromState !== currentYear) {
                try {
                    const params = { year: currentYear, cantonAbbreviation: canton, startDate: yearStartDate, endDate: yearEndDate };
                    const response = await api.get('/api/holidays/details', { params });
                    newHolidaysByCantonState[cantonKey] = { data: response.data || {}, year: currentYear };
                    fetchOccurredForNewData = true;
                } catch (error) {
                    console.error(t('errors.fetchHolidaysErrorForCanton', `Fehler beim Laden der Feiertage für Kanton ${cantonKey}:`), error);
                    if (!newHolidaysByCantonState[cantonKey]) {
                        newHolidaysByCantonState[cantonKey] = { data: {}, year: currentYear };
                    }
                }
            }
        }
        if (fetchOccurredForNewData) {
            setHolidaysByCanton(newHolidaysByCantonState);
        }
    }, [selectedMonday, currentUser, users, t, holidaysByCanton]);

    const handleDataReloadNeeded = useCallback(() => {
        fetchAllDailySummaries();
        fetchAllVacations();
        fetchAllCorrections();
        fetchAllSickLeavesForAdmin();
        fetchTrackingBalances();
        fetchUsers();
    }, [fetchAllDailySummaries, fetchAllVacations, fetchAllCorrections, fetchAllSickLeavesForAdmin, fetchTrackingBalances, fetchUsers]);


    useEffect(() => {
        fetchUsers();
    }, [fetchUsers]);

    useEffect(() => {
        if (currentUser) {
            handleDataReloadNeeded();
        }
    }, [currentUser, handleDataReloadNeeded]);

    useEffect(() => {
        if (currentUser) {
            fetchHolidaysForAllRelevantCantons();
        }
    }, [selectedMonday, users, currentUser, fetchHolidaysForAllRelevantCantons]);


    function handlePrevWeek() {
        setSelectedMonday(prev => addDays(prev, -7));
    }
    function handleNextWeek() {
        setSelectedMonday(prev => addDays(prev, 7));
    }
    function handleWeekJump(e) {
        const picked = new Date(e.target.value);
        if (!isNaN(picked.getTime())) {
            setSelectedMonday(getMondayOfWeek(picked));
        }
    }

    function handleCurrentWeek() {
        setSelectedMonday(getMondayOfWeek(new Date()));
    }

    async function handleApproveVacation(id) {
        try {
            await api.post(`/api/vacation/approve/${id}`);
            notify(t('adminDashboard.vacationApprovedMsg', 'Urlaub genehmigt.'), 'success');
            handleDataReloadNeeded();
        } catch (err) {
            console.error('Error approving vacation', err);
            notify(t('adminDashboard.vacationApproveErrorMsg', 'Fehler beim Genehmigen des Urlaubs: ') + (err.response?.data?.message || err.message), 'error');
        }
    }
    async function handleDenyVacation(id) {
        try {
            await api.post(`/api/vacation/deny/${id}`);
            notify(t('adminDashboard.vacationDeniedMsg', 'Urlaub abgelehnt.'), 'success');
            fetchAllVacations();
        } catch (err) {
            console.error('Error denying vacation', err);
            notify(t('adminDashboard.vacationDenyErrorMsg', 'Fehler beim Ablehnen des Urlaubs: ') + (err.response?.data?.message || err.message), 'error');
        }
    }

    async function handleApproveCorrection(id, comment) {
        try {
            await api.post(`/api/correction/approve/${id}`, null, { params: { comment } });
            notify(`${t('adminDashboard.correctionApprovedMsg')} #${id}`, "success");
            handleDataReloadNeeded();
        } catch (error) {
            console.error(`Fehler beim Genehmigen von Antrag #${id}:`, error);
            notify(`${t('adminDashboard.correctionErrorMsg')} #${id}`, "error");
        }
    }

    async function handleDenyCorrection(id, comment) {
        try {
            await api.post(`/api/correction/deny/${id}`, null, { params: { comment } });
            notify(`${t('adminDashboard.correctionDeniedMsg')} #${id}`, "success");
            handleDataReloadNeeded();
        } catch (error) {
            console.error(`Fehler beim Ablehnen von Antrag #${id}:`, error);
            notify(`${t('adminDashboard.correctionErrorMsg')} #${id}`, "error");
        }
    }

    function openEditModal(targetUsername, dateObj, dailySummaryForDay) {
        setEditTargetUsername(targetUsername);
        setEditDate(dateObj);
        setEditDayEntries(dailySummaryForDay ? dailySummaryForDay.entries || [] : []);
        setEditModalVisible(true);
    }

    function openNewEntryModal(targetUsername, dateObj) {
        setEditTargetUsername(targetUsername);
        setEditDate(dateObj);
        setEditDayEntries([]);
        setEditModalVisible(true);
    }

    async function handleEditSubmit(updatedEntriesForDay) {
        if (!editDate || !editTargetUsername) {
            notify(t('adminDashboard.noValidDateOrUser', "Kein gültiges Datum oder Benutzer ausgewählt."), 'error');
            return;
        }
        const formattedDate = formatLocalDateYMD(editDate);

        try {
            await api.put(`/api/admin/timetracking/editDay/${editTargetUsername}/${formattedDate}`, updatedEntriesForDay);
            setEditModalVisible(false);
            notify(t('adminDashboard.editSuccessfulMsg', 'Zeiten erfolgreich bearbeitet.'), 'success');
            handleDataReloadNeeded();
        } catch (err) {
            console.error('Edit failed', err);
            const errorMsg = err.response?.data?.message || err.response?.data || err.message || t('errors.unknownError');
            notify(t('adminDashboard.editFailed', "Fehler beim Bearbeiten") + ': ' + errorMsg, 'error');
        }
    }

    const focusWeekForProblem = useCallback((dateForWeek) => {
        if (dateForWeek && !isNaN(new Date(dateForWeek).getTime())) {
            setSelectedMonday(getMondayOfWeek(new Date(dateForWeek)));
        }
    }, []);

    function openPrintUserModal(username) {
        setPrintUser(username);
        const now = new Date();
        const firstDayOfMonth = formatLocalDateYMD(new Date(now.getFullYear(), now.getMonth(), 1));
        const lastDayOfMonth = formatLocalDateYMD(new Date(now.getFullYear(), now.getMonth() + 1, 0));
        setPrintUserStartDate(firstDayOfMonth);
        setPrintUserEndDate(lastDayOfMonth);
        setPrintUserModalVisible(true);
    }
    const calculateCardHeight = (doc, dayData, width) => {
        let height = 25; // Header + initial padding
        let leftHeight = 30;
        let rightHeight = 30;

        if(dayData.note) {
            leftHeight += doc.splitTextToSize(dayData.note, (width/2.5) - 10).length * 5 + 10;
        }

        dayData.blocks.work.forEach(block => {
            const text = `${block.description ? `${block.description}:` : ''} ${block.start} - ${block.end} (${block.duration})`;
            rightHeight += doc.splitTextToSize(text, width - (width/2.5) - 5).length * 5 + 2;
        });
        height += Math.max(leftHeight, rightHeight);
        return height;
    };
    async function handlePrintUserTimesPeriodSubmit() {
        if (!printUser || !printUserStartDate || !printUserEndDate) return;

        const userSummariesForPrint = dailySummaries.filter(summary =>
            summary.username === printUser &&
            summary.date >= printUserStartDate &&
            summary.date <= printUserEndDate
        );

        const sortedSummaries = userSummariesForPrint.sort(
            (a, b) => new Date(a.date) - new Date(b.date)
        );

        const userDetails = users.find(u => u.username === printUser);
        const userNameDisplay = userDetails ? `${userDetails.firstName} ${userDetails.lastName} (${printUser})` : printUser;
        const balanceRecord = filteredWeeklyBalances.find(b => b.username === printUser);
        const overtimeStr = minutesToHHMM(balanceRecord?.trackingBalance || 0);

        const doc = new jsPDF("p", "mm", "a4");
        const pageMargin = 15;
        const pageWidth = doc.internal.pageSize.getWidth();
        const pageHeight = doc.internal.pageSize.getHeight();
        const contentWidth = pageWidth - (2 * pageMargin);
        let yPos = 20;

        doc.setFontSize(22);
        doc.setFont("helvetica", "bold");
        doc.text("Zeitenbericht", pageWidth / 2, yPos, { align: "center" });
        yPos += 10;

        doc.setFontSize(12);
        doc.setFont("helvetica", "normal");
        doc.text(`für ${userNameDisplay}`, pageWidth / 2, yPos, { align: "center" });
        yPos += 6;
        doc.text(`Zeitraum: ${formatDate(new Date(printUserStartDate))} - ${formatDate(new Date(printUserEndDate))}`, pageWidth / 2, yPos, { align: "center" });
        yPos += 6;
        doc.text(`${t('overtimeBalance', 'Überstundensaldo')}: ${overtimeStr}`, pageWidth / 2, yPos, { align: "center" });
        yPos += 15;

        const totalWork = sortedSummaries.reduce((sum, day) => sum + day.workedMinutes, 0);
        const totalPause = sortedSummaries.reduce((sum, day) => sum + day.breakMinutes, 0);

        doc.setFillColor(248, 249, 250);
        doc.rect(pageMargin, yPos, contentWidth, 25, 'F');
        const summaryTextY = yPos + 15;
        const summaryCol1 = pageMargin + contentWidth / 4;
        const summaryCol2 = pageMargin + (contentWidth / 4) * 3;

        doc.setFontSize(10);
        doc.setTextColor(108, 117, 125);
        doc.text("Gesamte Arbeitszeit", summaryCol1, summaryTextY - 8, { align: 'center' });
        doc.text("Gesamte Pausenzeit", summaryCol2, summaryTextY - 8, { align: 'center' });

        doc.setFontSize(16);
        doc.setFont("helvetica", "bold");
        doc.setTextColor(41, 128, 185);
        doc.text(minutesToHHMM(totalWork), summaryCol1, summaryTextY, { align: 'center' });
        doc.setTextColor(44, 62, 80);
        doc.text(minutesToHHMM(totalPause), summaryCol2, summaryTextY, { align: 'center' });
        yPos += 35;

        sortedSummaries.forEach(day => {
            const dayData = {
                date: day.date,
                workedMinutes: day.workedMinutes,
                breakMinutes: day.breakMinutes,
                blocks: processEntriesForReport(day.entries),
                note: day.dailyNote || ""
            };

            const cardHeight = calculateCardHeight(doc, dayData, contentWidth);

            if (yPos + cardHeight > pageHeight - pageMargin) {
                doc.addPage();
                yPos = 20;
            }

            const cardStartY = yPos;
            doc.setFillColor(236, 240, 241);
            doc.roundedRect(pageMargin, yPos, contentWidth, 10, 3, 3, 'F');
            doc.setFontSize(14);
            doc.setFont("helvetica", "bold");
            doc.setTextColor(44, 62, 80);
            doc.text(formatDate(new Date(dayData.date)), pageMargin + 5, yPos + 7);
            yPos += 10;

            const bodyYStart = yPos;
            let leftColY = bodyYStart + 10;
            let rightColY = bodyYStart + 10;
            const rightColX = pageMargin + contentWidth / 2.5;

            doc.setFontSize(11);
            doc.setFont("helvetica", "bold");
            doc.text("Übersicht", pageMargin + 5, leftColY);
            leftColY += 7;

            doc.setFontSize(10);
            doc.setFont("helvetica", "normal");
            doc.text(`Gearbeitet: ${minutesToHHMM(dayData.workedMinutes)}`, pageMargin + 5, leftColY);
            leftColY += 6;
            doc.text(`Pause: ${minutesToHHMM(dayData.breakMinutes)}`, pageMargin + 5, leftColY);
            leftColY += 10;

            if (dayData.note) {
                doc.setFont("helvetica", "bold");
                doc.text("Notiz:", pageMargin + 5, leftColY);
                leftColY += 6;
                doc.setFont("helvetica", "italic");
                const noteLines = doc.splitTextToSize(dayData.note, (contentWidth / 2.5) - 10);
                doc.text(noteLines, pageMargin + 5, leftColY);
                leftColY += noteLines.length * 5;
            }

            doc.setFontSize(11);
            doc.setFont("helvetica", "bold");
            doc.text("Arbeitsblöcke", rightColX, rightColY);
            rightColY += 7;

            doc.setFontSize(10);
            doc.setFont("helvetica", "normal");
            dayData.blocks.work.forEach(block => {
                const text = `${block.description ? `${block.description}:` : 'Arbeit:'} ${block.start} - ${block.end} (${block.duration})`;
                const textLines = doc.splitTextToSize(text, contentWidth - rightColX - 5);
                doc.text(textLines, rightColX, rightColY);
                rightColY += textLines.length * 5 + 2;
            });

            const cardEndY = Math.max(leftColY, rightColY) + 5;
            doc.setDrawColor(222, 226, 230);
            doc.roundedRect(pageMargin, cardStartY, contentWidth, cardEndY - cardStartY, 3, 3, 'S');

            yPos = cardEndY + 10;
        });

        doc.save(`Zeitenbericht_${printUser}_${printUserStartDate}_bis_${printUserEndDate}.pdf`);
        setPrintUserModalVisible(false);
    }

    return (
        <div className="admin-dashboard scoped-dashboard">
            <Navbar />
            <header className="dashboard-header">
                <div className="header-info">
                    <h2>{t('adminDashboard.titleWeekly')}</h2>
                    {currentUser && (
                        <p>{t('adminDashboard.loggedInAs')} {currentUser.username}</p>
                    )}
                </div>
                <button
                    type="button"
                    className="command-palette-trigger button-secondary"
                    onClick={() => setPaletteOpen(true)}
                >
                    {t('adminDashboard.commandPalette.buttonLabel', 'Befehle (Strg+K)')}
                </button>
            </header>

            <section
                className="dashboard-overview-grid dashboard-summary-section"
                aria-label={t('adminDashboard.summary.ariaLabel', 'Aktueller Admin-Überblick')}
            >
                <div className="dashboard-overview-main">
                    <AdminDashboardKpis
                        t={t}
                        allVacations={allVacations}
                        allCorrections={allCorrections}
                        weeklyBalances={filteredWeeklyBalances}
                        users={users}
                        onNavigateToVacations={handleNavigateToVacations}
                        onNavigateToCorrections={handleNavigateToCorrections}
                        onShowIssueOverview={handleShowIssueOverview}
                        onFocusNegativeBalances={handleFocusNegativeBalances}
                        onFocusOvertimeLeaders={handleFocusPositiveBalances}
                        onOpenAnalytics={hasFeature('analytics') ? handleOpenAnalytics : null}
                    />
                </div>
                <aside className="dashboard-overview-side">
                    {renderInboxSummary()}
                </aside>
            </section>

            <div className="team-overview-content">
                <section className="team-overview-main">
                    <AdminWeekSection
                        ref={weekSectionRef}
                        t={t}
                        weekDates={Array.from({ length: 7 }, (_, i) => addDays(selectedMonday, i))}
                        selectedMonday={selectedMonday}
                        handlePrevWeek={handlePrevWeek}
                        handleNextWeek={handleNextWeek}
                        handleWeekJump={handleWeekJump}
                        handleCurrentWeek={handleCurrentWeek}
                        onFocusProblemWeek={focusWeekForProblem}
                        dailySummariesForWeekSection={dailySummaries}
                        allVacations={allVacations}
                        allSickLeaves={allSickLeaves}
                        allHolidays={holidaysByCanton}
                        users={users}
                        defaultExpectedHours={defaultExpectedHours}
                        openEditModal={openEditModal}
                        openPrintUserModal={openPrintUserModal}
                        rawUserTrackingBalances={filteredWeeklyBalances}
                        openNewEntryModal={openNewEntryModal}
                        onDataReloadNeeded={handleDataReloadNeeded}
                        onIssueSummaryChange={handleIssueSummaryUpdate}
                        showSmartOverview={false}
                        onQuickFixQueueChange={setQuickFixQueue}
                    />

                    <AdminQuickFixPanel
                        t={t}
                        items={quickFixQueue}
                        onSelect={handleQuickFixSelect}
                    />
                </section>

            </div>

            <section className="dashboard-requests-section">
                <div ref={vacationSectionRef}>
                    <AdminVacationRequests
                        t={t}
                        allVacations={allVacations}
                        handleApproveVacation={handleApproveVacation}
                        handleDenyVacation={handleDenyVacation}
                        onReloadVacations={fetchAllVacations}
                        openSignal={vacationOpenSignal}
                    />
                </div>
                <div ref={correctionSectionRef}>
                    <AdminCorrectionsList
                        t={t}
                        allCorrections={allCorrections}
                        onApprove={handleApproveCorrection}
                        onDeny={handleDenyCorrection}
                        openSignal={correctionOpenSignal}
                    />
                </div>
            </section>

            <section
                className="admin-calendar-section"
                aria-label={t('adminDashboard.vacationCalendarAria', 'Abwesenheitskalender Übersicht')}
            >
                <div className="admin-calendar-card">
                    <h3>{t('adminDashboard.vacationCalendarTitle')}</h3>
                    <VacationCalendarAdmin
                        vacationRequests={allVacations.filter(v => v.approved)}
                        onReloadVacations={handleDataReloadNeeded}
                        users={users}
                    />
                </div>
            </section>
            {paletteOpen && (
                <div className="command-palette-overlay" role="dialog" aria-modal="true">
                    <div className="command-palette">
                        <input
                            type="search"
                            ref={paletteInputRef}
                            value={paletteQuery}
                            onChange={(event) => {
                                setPaletteQuery(event.target.value);
                                setPaletteActiveIndex(0);
                            }}
                            placeholder={t('adminDashboard.commandPalette.placeholder', 'Befehl suchen …')}
                            aria-label={t('adminDashboard.commandPalette.placeholder', 'Befehl suchen …')}
                        />
                        <ul>
                            {filteredCommands.length === 0 && (
                                <li className="empty">{t('adminDashboard.commandPalette.empty', 'Keine Treffer')}</li>
                            )}
                            {filteredCommands.map((command, index) => (
                                <li key={command.id} className={index === paletteActiveIndex ? 'is-active' : ''}>
                                    <button
                                        type="button"
                                        onMouseEnter={() => setPaletteActiveIndex(index)}
                                        onClick={() => handleExecuteCommand(command)}
                                    >
                                        {command.label}
                                    </button>
                                </li>
                            ))}
                        </ul>
                    </div>
                </div>
            )}
            <EditTimeModal
                t={t}
                isVisible={editModalVisible}
                targetDate={editDate}
                dayEntries={editDayEntries}
                targetUsername={editTargetUsername}
                onSubmit={handleEditSubmit}
                onClose={() => setEditModalVisible(false)}
                users={users}
            />
            <PrintUserTimesModal
                printUserModalVisible={printUserModalVisible}
                printUser={printUser}
                printUserStartDate={printUserStartDate}
                printUserEndDate={printUserEndDate}
                setPrintUserStartDate={setPrintUserStartDate}
                setPrintUserEndDate={setPrintUserEndDate}
                handlePrintUserTimesPeriodSubmit={handlePrintUserTimesPeriodSubmit}
                setPrintUserModalVisible={setPrintUserModalVisible}
                t={t}
            />
        </div>
    );
};

export default AdminDashboard;