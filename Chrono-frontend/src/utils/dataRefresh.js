const MUTATION_METHODS = new Set(['post', 'put', 'patch', 'delete']);
const DATA_REFRESH_CHANNEL = 'chrono:data-refresh:v1';
const DATA_REFRESH_EVENT = 'chrono:data-refresh';

export const REFRESH_SCOPES = Object.freeze({
    TIME: 'time',
    ABSENCE: 'absence',
    REQUESTS: 'requests',
    PEOPLE: 'people',
    HOLIDAYS: 'holidays',
    CUSTOMERS: 'customers',
    PROJECTS: 'projects',
    TASKS: 'tasks',
    SCHEDULE: 'schedule',
    PAYROLL: 'payroll',
    PMS: 'pms',
    CRM: 'crm',
    ACCOUNTING: 'accounting',
    BANKING: 'banking',
    SUPPLY_CHAIN: 'supplyChain',
    CHRONO_TWO: 'chrono2',
    COMPANY: 'company',
    KNOWLEDGE: 'knowledge',
    DASHBOARD: 'dashboard',
    GLOBAL: 'global',
});

export const REFRESH_SCOPE_VALUES = Object.freeze(Object.values(REFRESH_SCOPES));

const VALID_REFRESH_SCOPES = new Set(REFRESH_SCOPE_VALUES);
const REFRESH_SCOPE_ALIASES = Object.freeze({
    users: REFRESH_SCOPES.PEOPLE,
    user: REFRESH_SCOPES.PEOPLE,
    people: REFRESH_SCOPES.PEOPLE,
    timetracking: REFRESH_SCOPES.TIME,
    timeTracking: REFRESH_SCOPES.TIME,
    balances: REFRESH_SCOPES.TIME,
    vacations: REFRESH_SCOPES.ABSENCE,
    vacation: REFRESH_SCOPES.ABSENCE,
    sickLeave: REFRESH_SCOPES.ABSENCE,
    corrections: REFRESH_SCOPES.REQUESTS,
    correction: REFRESH_SCOPES.REQUESTS,
    payslips: REFRESH_SCOPES.PAYROLL,
    preferences: REFRESH_SCOPES.DASHBOARD,
    companies: REFRESH_SCOPES.COMPANY,
});

const TECHNICAL_MUTATION_PATHS = [
    /^\/api\/auth(?:\/|$)/,
    /^\/api\/ui\/preferences(?:\/|$)/,
];

const READ_LIKE_POST_PATHS = [
    /^\/api\/public\/analytics\/events(?:\/|$)/,
    /^\/api\/chat(?:\/|$)/,
    /^\/api\/contact(?:\/|$)/,
    /^\/api\/apply(?:\/|$)/,
    /^\/api\/nfc\/command(?:\/|$)/,
    /^\/api\/public\/pms\/booking\/[^/]+\/verify(?:\/|$)/,
    /^\/api\/supply-chain\/receiving\/(?:preview|document-preview)(?:\/|$)/,
    /^\/chrono2\/(?:slotting|analytics\/nlp|procurement\/sourcing|procurement\/accounting|procurement\/mobile-inbound|outbound\/pick-route)(?:\/|$)/,
];

const MUTATION_SCOPE_RULES = [
    {
        test: /^\/api\/(?:admin\/)?timetracking(?:\/|$)/,
        scopes: [REFRESH_SCOPES.TIME],
    },
    {
        test: /^\/api\/correction(?:\/|$)/,
        scopes: [REFRESH_SCOPES.REQUESTS, REFRESH_SCOPES.TIME],
    },
    {
        test: /^\/api\/(?:vacation|sick-leave)(?:\/|$)/,
        scopes: [REFRESH_SCOPES.ABSENCE, REFRESH_SCOPES.TIME, REFRESH_SCOPES.SCHEDULE],
    },
    {
        test: /^\/api\/(?:holidays|admin\/user-holiday-options)(?:\/|$)/,
        scopes: [REFRESH_SCOPES.HOLIDAYS, REFRESH_SCOPES.TIME, REFRESH_SCOPES.SCHEDULE],
    },
    {
        test: /^\/api\/admin\/(?:schedule|shift-definitions)(?:\/|$)/,
        scopes: [REFRESH_SCOPES.SCHEDULE],
    },
    {
        test: /^\/api\/admin\/users(?:\/|$)/,
        scopes: [REFRESH_SCOPES.PEOPLE, REFRESH_SCOPES.SCHEDULE],
    },
    {
        test: /^\/api\/user(?:\/|$)/,
        scopes: [REFRESH_SCOPES.PEOPLE],
    },
    {
        test: /^\/api\/(?:admin\/company|superadmin\/companies)(?:\/|$)/,
        scopes: [
            REFRESH_SCOPES.COMPANY,
            REFRESH_SCOPES.PEOPLE,
            REFRESH_SCOPES.HOLIDAYS,
            REFRESH_SCOPES.PAYROLL,
        ],
    },
    {
        test: /^\/api\/customers(?:\/|$)/,
        scopes: [REFRESH_SCOPES.CUSTOMERS, REFRESH_SCOPES.PROJECTS, REFRESH_SCOPES.TIME],
    },
    {
        test: /^\/api\/projects(?:\/|$)/,
        scopes: [REFRESH_SCOPES.PROJECTS, REFRESH_SCOPES.TASKS, REFRESH_SCOPES.TIME],
    },
    {
        test: /^\/api\/tasks(?:\/|$)/,
        scopes: [REFRESH_SCOPES.TASKS, REFRESH_SCOPES.TIME],
    },
    {
        test: /^\/api\/payslips(?:\/|$)/,
        scopes: [REFRESH_SCOPES.PAYROLL],
    },
    {
        test: /^\/api\/accounting(?:\/|$)/,
        scopes: [REFRESH_SCOPES.ACCOUNTING, REFRESH_SCOPES.BANKING],
    },
    {
        test: /^\/api\/banking(?:\/|$)/,
        scopes: [REFRESH_SCOPES.BANKING, REFRESH_SCOPES.ACCOUNTING],
    },
    {
        test: /^\/api\/(?:public\/)?pms(?:\/|$)/,
        scopes: [REFRESH_SCOPES.PMS],
    },
    {
        test: /^\/api\/crm(?:\/|$)/,
        scopes: [REFRESH_SCOPES.CRM],
    },
    {
        test: /^\/api\/supply-chain(?:\/|$)/,
        scopes: [REFRESH_SCOPES.SUPPLY_CHAIN],
    },
    {
        test: /^\/chrono2(?:\/|$)/,
        scopes: [REFRESH_SCOPES.CHRONO_TWO],
    },
    {
        test: /^\/api\/admin\/knowledge(?:\/|$)/,
        scopes: [REFRESH_SCOPES.KNOWLEDGE],
    },
    {
        test: /^\/api\/(?:integrations|billing)(?:\/|$)/,
        scopes: [REFRESH_SCOPES.PROJECTS, REFRESH_SCOPES.ACCOUNTING],
    },
    {
        test: /^\/api\/(?:changelog|superadmin\/analytics)(?:\/|$)/,
        scopes: [REFRESH_SCOPES.DASHBOARD],
    },
];

const listeners = new Set();
const instanceId = globalThis.crypto?.randomUUID?.()
    ?? `chrono-refresh-${Date.now()}-${Math.random().toString(36).slice(2)}`;

let broadcastChannel = null;
let flushTimer = null;
let pendingFirstPublishedAt = null;
let pendingScopes = new Set();
let pendingBroadcastScopes = new Set();
let pendingChanges = [];

const toScopeList = (value) => {
    if (Array.isArray(value)) return value;
    if (value instanceof Set) return [...value];
    if (typeof value === 'string') return [value];
    return [];
};

export const normalizeRefreshScopes = (value, { fallbackToGlobal = false } = {}) => {
    const normalized = [];

    toScopeList(value).forEach((scope) => {
        const rawScope = String(scope ?? '').trim();
        const canonicalScope = REFRESH_SCOPE_ALIASES[rawScope] ?? rawScope;
        if (VALID_REFRESH_SCOPES.has(canonicalScope) && !normalized.includes(canonicalScope)) {
            normalized.push(canonicalScope);
        }
    });

    if (normalized.length === 0 && fallbackToGlobal) {
        return [REFRESH_SCOPES.GLOBAL];
    }
    return normalized;
};

export const refreshScopesMatch = (subscribedScopes, changedScopes) => {
    const subscriptions = normalizeRefreshScopes(subscribedScopes);
    const changes = normalizeRefreshScopes(changedScopes);

    if (subscriptions.length === 0 || changes.length === 0) return false;
    if (
        subscriptions.includes(REFRESH_SCOPES.GLOBAL)
        || changes.includes(REFRESH_SCOPES.GLOBAL)
    ) {
        return true;
    }

    const changedSet = new Set(changes);
    return subscriptions.some((scope) => changedSet.has(scope));
};

const normalizePath = (url) => {
    if (!url) return '/';
    try {
        return new URL(String(url), 'http://chrono.local').pathname.replace(/\/+$/, '') || '/';
    } catch {
        return String(url).split('?')[0].split('#')[0].replace(/\/+$/, '') || '/';
    }
};

const readExplicitRefreshSetting = (config) => {
    if (
        config?.dataRefresh === false
        || config?.chronoRefresh === false
        || config?.skipDataRefresh === true
    ) {
        return { configured: true, scopes: [] };
    }

    const configuredValue = Array.isArray(config?.dataRefresh)
        || typeof config?.dataRefresh === 'string'
        ? config.dataRefresh
        : config?.dataRefresh?.scopes
            ?? config?.refreshScopes
            ?? config?.chronoRefreshScopes;

    if (configuredValue !== undefined) {
        return {
            configured: true,
            scopes: normalizeRefreshScopes(configuredValue, { fallbackToGlobal: true }),
        };
    }
    return { configured: false, scopes: [] };
};

export const classifyMutationRequest = (config = {}) => {
    const method = String(config.method ?? '').toLowerCase();
    if (!MUTATION_METHODS.has(method)) return [];

    const path = normalizePath(config.url);
    if (TECHNICAL_MUTATION_PATHS.some((pattern) => pattern.test(path))) {
        return [];
    }
    if (method === 'post' && READ_LIKE_POST_PATHS.some((pattern) => pattern.test(path))) {
        return [];
    }

    const explicit = readExplicitRefreshSetting(config);
    if (explicit.configured) return explicit.scopes;

    const matchingRule = MUTATION_SCOPE_RULES.find((rule) => rule.test.test(path));
    return matchingRule
        ? [...matchingRule.scopes]
        : [REFRESH_SCOPES.GLOBAL];
};

const notifyListeners = (event) => {
    listeners.forEach((listener) => {
        try {
            listener(event);
        } catch (error) {
            // A broken consumer must not prevent other mounted screens from refreshing.
            console.error('Data refresh listener failed', error);
        }
    });
};

const ensureBroadcastChannel = () => {
    if (broadcastChannel || typeof globalThis.BroadcastChannel !== 'function') {
        return broadcastChannel;
    }

    try {
        broadcastChannel = new globalThis.BroadcastChannel(DATA_REFRESH_CHANNEL);
    } catch {
        // BroadcastChannel can be blocked in private/locked-down browser contexts.
        broadcastChannel = null;
        return null;
    }
    broadcastChannel.onmessage = (messageEvent) => {
        const message = messageEvent?.data;
        if (
            message?.type !== DATA_REFRESH_EVENT
            || message.sourceId === instanceId
        ) {
            return;
        }

        queueDataRefresh(message.scopes, {
            broadcast: false,
            changes: Array.isArray(message.changes) ? message.changes : [],
            firstPublishedAt: Number(message.firstPublishedAt) || Date.now(),
            delayMs: 0,
            origin: 'broadcast',
        });
    };
    return broadcastChannel;
};

const flushPendingDataRefresh = () => {
    if (flushTimer !== null) {
        globalThis.clearTimeout(flushTimer);
        flushTimer = null;
    }

    const scopes = normalizeRefreshScopes([...pendingScopes]);
    if (scopes.length === 0) return;

    const firstPublishedAt = pendingFirstPublishedAt ?? Date.now();
    const changes = pendingChanges.slice(0, 50);
    const scopesToBroadcast = normalizeRefreshScopes([...pendingBroadcastScopes]);

    pendingScopes = new Set();
    pendingBroadcastScopes = new Set();
    pendingChanges = [];
    pendingFirstPublishedAt = null;

    const event = Object.freeze({
        type: DATA_REFRESH_EVENT,
        scopes: Object.freeze(scopes),
        changes: Object.freeze(changes),
        firstPublishedAt,
        publishedAt: Date.now(),
    });

    notifyListeners(event);

    if (scopesToBroadcast.length > 0) {
        try {
            ensureBroadcastChannel()?.postMessage({
                ...event,
                scopes: scopesToBroadcast,
                sourceId: instanceId,
            });
        } catch {
            // Local listeners still received the event; cross-tab sync is best effort.
        }
    }
};

function queueDataRefresh(scopes, {
    broadcast = true,
    changes = [],
    firstPublishedAt = Date.now(),
    delayMs = 80,
    origin = 'local',
} = {}) {
    const normalizedScopes = normalizeRefreshScopes(scopes);
    if (normalizedScopes.length === 0) return false;

    normalizedScopes.forEach((scope) => pendingScopes.add(scope));
    if (broadcast) {
        normalizedScopes.forEach((scope) => pendingBroadcastScopes.add(scope));
    }
    pendingFirstPublishedAt = pendingFirstPublishedAt === null
        ? firstPublishedAt
        : Math.min(pendingFirstPublishedAt, firstPublishedAt);
    if (pendingChanges.length < 50) {
        pendingChanges.push(
            ...changes
                .slice(0, 50 - pendingChanges.length)
                .map((change) => ({ ...change, origin })),
        );
    }

    if (flushTimer === null || delayMs === 0) {
        if (flushTimer !== null) globalThis.clearTimeout(flushTimer);
        flushTimer = globalThis.setTimeout(flushPendingDataRefresh, Math.max(0, delayMs));
    }
    return true;
}

export const publishDataRefresh = (scopesOrEvent, details = {}) => {
    const eventInput = scopesOrEvent && typeof scopesOrEvent === 'object' && !Array.isArray(scopesOrEvent)
        && !(scopesOrEvent instanceof Set)
        ? scopesOrEvent
        : { scopes: scopesOrEvent };
    const scopes = normalizeRefreshScopes(eventInput.scopes, { fallbackToGlobal: false });
    if (scopes.length === 0) return false;

    ensureBroadcastChannel();
    const change = {
        method: eventInput.method ?? details.method,
        url: eventInput.url ?? details.url,
        status: eventInput.status ?? details.status,
    };

    return queueDataRefresh(scopes, {
        broadcast: eventInput.broadcast ?? details.broadcast ?? true,
        firstPublishedAt: eventInput.firstPublishedAt ?? Date.now(),
        changes: [change],
    });
};

export const publishMutationRefresh = (response) => {
    const config = response?.config ?? {};
    const scopes = classifyMutationRequest(config);
    if (scopes.length === 0) return false;

    return publishDataRefresh(scopes, {
        method: String(config.method ?? '').toLowerCase(),
        url: normalizePath(config.url),
        status: response?.status,
    });
};

export const subscribeDataRefresh = (listener) => {
    if (typeof listener !== 'function') return () => undefined;
    listeners.add(listener);
    ensureBroadcastChannel();
    return () => listeners.delete(listener);
};

// Kept public for deterministic unit tests and controlled teardown in host shells.
export const resetDataRefreshCoordinator = () => {
    if (flushTimer !== null) globalThis.clearTimeout(flushTimer);
    flushTimer = null;
    pendingFirstPublishedAt = null;
    pendingScopes = new Set();
    pendingBroadcastScopes = new Set();
    pendingChanges = [];
    listeners.clear();
    try {
        broadcastChannel?.close?.();
    } catch {
        // Nothing else to tear down.
    }
    broadcastChannel = null;
};
