import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import api from '../utils/api';

export const DASHBOARD_LAYOUT_SCHEMA_VERSION = 1;
export const DASHBOARD_WIDGET_SIZES = ['S', 'M', 'L', 'full'];

const USER_PREFERENCE_ENDPOINT = '/api/ui/preferences/TIME_USER_DASHBOARD';
const ADMIN_PREFERENCE_ENDPOINT = '/api/ui/preferences/TIME_ADMIN_DASHBOARD';
const PMS_PREFERENCE_ENDPOINT = '/api/ui/preferences/PMS_DASHBOARD';
const STORAGE_PREFIX = 'chrono.dashboard-layout';

const normalizeSize = (value, fallback = 'full') => {
    const raw = String(value ?? fallback).trim();
    if (raw.toLowerCase() === 'full') return 'full';
    const upper = raw.toUpperCase();
    return DASHBOARD_WIDGET_SIZES.includes(upper) ? upper : fallback;
};

const normalizeAllowedSizes = (widget) => {
    const configured = Array.isArray(widget?.sizes) && widget.sizes.length > 0
        ? widget.sizes
        : DASHBOARD_WIDGET_SIZES;
    const normalized = configured
        .map((size) => normalizeSize(size, null))
        .filter(Boolean);
    return normalized.length > 0 ? [...new Set(normalized)] : DASHBOARD_WIDGET_SIZES;
};

const unwrapLayout = (value) => {
    if (Array.isArray(value)) return value;
    if (Array.isArray(value?.widgets)) return value.widgets;
    if (Array.isArray(value?.layout)) return value.layout;
    return [];
};

export const createDefaultDashboardLayout = (registry = [], defaultLayout = []) => {
    const overrides = new Map(
        unwrapLayout(defaultLayout)
            .filter((item) => item?.id)
            .map((item) => [String(item.id), item])
    );

    return registry
        .filter((widget) => widget?.id)
        .map((widget, registryIndex) => {
            const id = String(widget.id);
            const override = overrides.get(id) || {};
            const allowedSizes = normalizeAllowedSizes(widget);
            const preferredSize = normalizeSize(
                override.size ?? widget.defaultSize,
                allowedSizes.includes('full') ? 'full' : allowedSizes[0]
            );

            return {
                id,
                visible: override.visible ?? widget.defaultVisible ?? true,
                order: Number.isFinite(Number(override.order)) ? Number(override.order) : registryIndex,
                size: allowedSizes.includes(preferredSize) ? preferredSize : allowedSizes[0],
            };
        })
        .sort((a, b) => a.order - b.order)
        .map((item, order) => ({ ...item, order }));
};

export const normalizeDashboardLayout = (value, registry = [], defaultLayout = []) => {
    const defaults = createDefaultDashboardLayout(registry, defaultLayout);
    const storedById = new Map(
        unwrapLayout(value)
            .filter((item) => item?.id)
            .map((item) => [String(item.id), item])
    );
    const widgetById = new Map(registry.map((widget) => [String(widget.id), widget]));

    return defaults
        .map((fallback) => {
            const stored = storedById.get(fallback.id);
            if (!stored) return fallback;
            const allowedSizes = normalizeAllowedSizes(widgetById.get(fallback.id));
            const storedSize = normalizeSize(stored.size, fallback.size);

            return {
                id: fallback.id,
                visible: typeof stored.visible === 'boolean' ? stored.visible : fallback.visible,
                order: Number.isFinite(Number(stored.order)) ? Number(stored.order) : fallback.order,
                size: allowedSizes.includes(storedSize) ? storedSize : fallback.size,
            };
        })
        .sort((a, b) => a.order - b.order)
        .map((item, order) => ({ ...item, order }));
};

const getPreferenceEndpoint = (context) => {
    if (context === 'ADMIN') return ADMIN_PREFERENCE_ENDPOINT;
    if (context === 'PMS') return PMS_PREFERENCE_ENDPOINT;
    return USER_PREFERENCE_ENDPOINT;
};

const sanitizeStoragePart = (value, fallback) => {
    const normalized = String(value ?? fallback).trim();
    return encodeURIComponent(normalized || fallback);
};

export const getDashboardStorageKey = ({ storageIdentity, context, scope }) => (
    [
        STORAGE_PREFIX,
        sanitizeStoragePart(storageIdentity, 'current-user'),
        sanitizeStoragePart(context, 'UNKNOWN'),
        sanitizeStoragePart(scope, 'default'),
    ].join('.')
);

const readLocalLayout = (storageKey) => {
    if (typeof window === 'undefined') return null;
    try {
        const raw = window.localStorage.getItem(storageKey);
        if (!raw) return null;
        const parsed = JSON.parse(raw);
        return parsed?.layout ?? parsed;
    } catch {
        return null;
    }
};

const writeLocalLayout = (storageKey, layout) => {
    if (typeof window === 'undefined') return;
    try {
        window.localStorage.setItem(storageKey, JSON.stringify({
            schemaVersion: DASHBOARD_LAYOUT_SCHEMA_VERSION,
            layout,
        }));
    } catch {
        // Private browsing or a full quota must not make the dashboard unusable.
    }
};

const getScopeLayout = (payload, scope) => (
    payload?.layouts?.[scope]?.widgets
    ?? payload?.layouts?.[scope]?.layout
    ?? payload?.layouts?.[scope]
    ?? payload?.layout
    ?? payload?.widgets
    ?? null
);

const mergeScopeLayout = (payload, scope, layout) => ({
    ...(payload && typeof payload === 'object' && !Array.isArray(payload) ? payload : {}),
    type: 'chrono-dashboard-layouts',
    schemaVersion: DASHBOARD_LAYOUT_SCHEMA_VERSION,
    layouts: {
        ...(payload?.layouts && typeof payload.layouts === 'object' ? payload.layouts : {}),
        [scope]: {
            widgets: layout,
        },
    },
});

const isConflict = (error) => error?.response?.status === 409;

/**
 * Loads and stores a personal dashboard layout. The server is authoritative when
 * available; LocalStorage keeps the feature usable offline and against older APIs.
 */
export const useDashboardPreferences = ({
    context,
    scope = 'default',
    registry = [],
    defaultLayout = [],
    storageIdentity = 'current-user',
    preferenceEndpoint,
    preferenceParams,
    remoteEnabled = true,
}) => {
    const registryKey = JSON.stringify(registry.map((widget) => ({
        id: String(widget.id),
        defaultVisible: widget.defaultVisible ?? true,
        defaultSize: widget.defaultSize ?? 'full',
        sizes: normalizeAllowedSizes(widget),
    })));
    const defaultLayoutKey = JSON.stringify(unwrapLayout(defaultLayout));
    const defaults = useMemo(
        () => createDefaultDashboardLayout(registry, defaultLayout),
        // registryKey deliberately describes only preference-relevant registry fields.
        // eslint-disable-next-line react-hooks/exhaustive-deps
        [registryKey, defaultLayoutKey]
    );
    const defaultsKey = JSON.stringify(defaults);
    const storageKey = useMemo(
        () => getDashboardStorageKey({ storageIdentity, context, scope }),
        [context, scope, storageIdentity]
    );
    const endpoint = preferenceEndpoint || getPreferenceEndpoint(context);
    const requestParamsKey = JSON.stringify(preferenceParams || { context });
    const requestParams = useMemo(
        () => JSON.parse(requestParamsKey),
        [requestParamsKey]
    );
    const preferenceKey = `${endpoint}|${requestParamsKey}|${scope}|${remoteEnabled ? 'remote' : 'local'}`;

    const [layout, setLayout] = useState(() => normalizeDashboardLayout(
        readLocalLayout(storageKey) ?? defaults,
        registry,
        defaultLayout
    ));
    const [persistenceStatus, setPersistenceStatus] = useState(remoteEnabled ? 'loading' : 'local');
    const layoutRef = useRef(layout);
    const payloadRef = useRef({});
    const revisionRef = useRef(0);
    const mutationRef = useRef(0);
    const writeQueueRef = useRef(Promise.resolve());
    const mountedRef = useRef(true);
    const activePreferenceKeyRef = useRef(preferenceKey);

    useEffect(() => () => {
        mountedRef.current = false;
    }, []);

    const applyLayout = useCallback((nextLayout) => {
        const normalized = normalizeDashboardLayout(nextLayout, registry, defaultLayout);
        layoutRef.current = normalized;
        setLayout(normalized);
        writeLocalLayout(storageKey, normalized);
        return normalized;
        // registryKey/defaultLayoutKey keep this stable when only render closures change.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [storageKey, registryKey, defaultLayoutKey]);

    const readRemotePreference = useCallback(async () => {
        const response = await api.get(endpoint, { params: requestParams });
        const data = response?.data && typeof response.data === 'object' ? response.data : null;
        return {
            payload: data?.payload && typeof data.payload === 'object' ? data.payload : {},
            revision: Number.isFinite(Number(data?.revision)) ? Number(data.revision) : 0,
        };
    }, [endpoint, requestParamsKey]);

    const persistRemote = useCallback((nextLayout) => {
        if (!remoteEnabled) {
            if (mountedRef.current) setPersistenceStatus('local');
            return;
        }
        const capturedPreferenceKey = preferenceKey;
        const save = async () => {
            if (activePreferenceKeyRef.current !== capturedPreferenceKey) return;
            const attempt = async (allowConflictRetry) => {
                const nextPayload = mergeScopeLayout(payloadRef.current, scope, nextLayout);
                try {
                    const response = await api.put(endpoint, {
                        schemaVersion: DASHBOARD_LAYOUT_SCHEMA_VERSION,
                        revision: revisionRef.current,
                        payload: nextPayload,
                    }, { params: requestParams });
                    if (activePreferenceKeyRef.current !== capturedPreferenceKey) return;
                    const responseData = response?.data || {};
                    payloadRef.current = responseData.payload || nextPayload;
                    revisionRef.current = Number.isFinite(Number(responseData.revision))
                        ? Number(responseData.revision)
                        : revisionRef.current + 1;
                    if (mountedRef.current) setPersistenceStatus('server');
                } catch (error) {
                    if (allowConflictRetry && isConflict(error)) {
                        try {
                            const latest = await readRemotePreference();
                            if (activePreferenceKeyRef.current !== capturedPreferenceKey) return;
                            payloadRef.current = latest.payload;
                            revisionRef.current = latest.revision;
                            await attempt(false);
                            return;
                        } catch {
                            // Fall through to the durable local copy.
                        }
                    }
                    if (mountedRef.current && activePreferenceKeyRef.current === capturedPreferenceKey) {
                        setPersistenceStatus('local');
                    }
                }
            };

            await attempt(true);
        };

        writeQueueRef.current = writeQueueRef.current.catch(() => undefined).then(save);
    }, [endpoint, preferenceKey, readRemotePreference, remoteEnabled, requestParamsKey, scope]);

    useEffect(() => {
        mountedRef.current = true;
        activePreferenceKeyRef.current = preferenceKey;
        mutationRef.current = 0;
        payloadRef.current = {};
        revisionRef.current = 0;

        const localLayout = readLocalLayout(storageKey);
        const startingLayout = normalizeDashboardLayout(
            localLayout ?? defaults,
            registry,
            defaultLayout
        );
        layoutRef.current = startingLayout;
        setLayout(startingLayout);
        setPersistenceStatus(remoteEnabled ? 'loading' : 'local');
        const loadMutation = mutationRef.current;
        let cancelled = false;

        if (!remoteEnabled) {
            return () => {
                cancelled = true;
            };
        }

        readRemotePreference()
            .then(({ payload, revision }) => {
                if (cancelled) return;
                payloadRef.current = payload;
                revisionRef.current = revision;
                const remoteLayout = getScopeLayout(payload, scope);
                if (remoteLayout && mutationRef.current === loadMutation) {
                    applyLayout(remoteLayout);
                }
                setPersistenceStatus('server');
            })
            .catch(() => {
                if (!cancelled) setPersistenceStatus('local');
            });

        return () => {
            cancelled = true;
        };
        // defaultsKey represents the complete default preference shape.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [storageKey, endpoint, preferenceKey, requestParamsKey, scope, defaultsKey, readRemotePreference, remoteEnabled]);

    const updateLayout = useCallback((nextValue) => {
        mutationRef.current += 1;
        const candidate = typeof nextValue === 'function'
            ? nextValue(layoutRef.current)
            : nextValue;
        const normalized = applyLayout(candidate);
        persistRemote(normalized);
        return normalized;
    }, [applyLayout, persistRemote]);

    const resetLayout = useCallback(() => updateLayout(defaults), [defaults, updateLayout]);

    return {
        layout,
        updateLayout,
        resetLayout,
        persistenceStatus,
        storageKey,
    };
};

export default useDashboardPreferences;
