import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import api from '../utils/api';
import { hasDashboardRect, normalizeFreeDashboardLayout } from '../components/dashboard/freeDashboardLayout.js';

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

export const createDefaultDashboardLayout = (registry = [], defaultLayout = [], { layoutMode = 'ordered' } = {}) => {
    const overrides = new Map(
        unwrapLayout(defaultLayout)
            .filter((item) => item?.id)
            .map((item) => [String(item.id), item])
    );

    const layout = registry
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
                ...(layoutMode === 'free' ? { ...widget.defaultRect, ...Object.fromEntries(
                    ['x', 'y', 'w', 'h'].filter((key) => override[key] !== undefined).map((key) => [key, override[key]])
                ) } : {}),
            };
        })
        .sort((a, b) => a.order - b.order)
        .map((item, order) => ({ ...item, order }));
    return layoutMode === 'free' ? normalizeFreeDashboardLayout(layout, registry) : layout;
};

export const normalizeDashboardLayout = (value, registry = [], defaultLayout = [], { layoutMode = 'ordered' } = {}) => {
    const defaults = createDefaultDashboardLayout(registry, defaultLayout, { layoutMode });
    const storedById = new Map(
        unwrapLayout(value)
            .filter((item) => item?.id)
            .map((item) => [String(item.id), item])
    );
    const widgetById = new Map(registry.map((widget) => [String(widget.id), widget]));

    const layout = defaults
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
                ...(layoutMode === 'free' && hasDashboardRect(stored)
                    ? { x: stored.x, y: stored.y, w: stored.w, h: stored.h } : {}),
            };
        })
        .sort((a, b) => a.order - b.order)
        .map((item, order) => ({ ...item, order }));
    return layoutMode === 'free' ? normalizeFreeDashboardLayout(layout, registry) : layout;
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

const readLocalPreference = (storageKey) => {
    if (typeof window === 'undefined') return null;
    try {
        const raw = window.localStorage.getItem(storageKey);
        if (!raw) return null;
        const parsed = JSON.parse(raw);
        return { layout: parsed?.layout ?? parsed, dirty: parsed?.dirty === true, editId: parsed?.editId };
    } catch {
        return null;
    }
};

const readLocalLayout = (storageKey) => readLocalPreference(storageKey)?.layout ?? null;
let editCounter = 0;
const createEditId = () => globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${++editCounter}`;

const writeLocalLayout = (storageKey, layout, { dirty = false, editId } = {}) => {
    if (typeof window === 'undefined') return;
    try {
        window.localStorage.setItem(storageKey, JSON.stringify({
            schemaVersion: DASHBOARD_LAYOUT_SCHEMA_VERSION,
            layout,
            dirty,
            ...(editId ? { editId } : {}),
        }));
    } catch {
        // Private browsing or a full quota must not make the dashboard unusable.
    }
};

const acknowledgeLocalEdit = (storageKey, editId) => {
    const local = readLocalPreference(storageKey);
    // A slow acknowledgement must not clear a later edit, including one made in another tab.
    if (local?.dirty && local.editId === editId) writeLocalLayout(storageKey, local.layout, { editId });
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
 * Loads and stores a personal dashboard layout. Unacknowledged local edits remain
 * durable per user/hotel key and are retried against the latest server revision on return.
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
    layoutMode = 'ordered',
}) => {
    const registryKey = JSON.stringify(registry.map((widget) => ({
        id: String(widget.id),
        defaultVisible: widget.defaultVisible ?? true,
        defaultSize: widget.defaultSize ?? 'full',
        sizes: normalizeAllowedSizes(widget),
        ...(layoutMode === 'free' ? { defaultRect: widget.defaultRect, minW: widget.minW, minH: widget.minH } : {}),
    })));
    const defaultLayoutKey = JSON.stringify(unwrapLayout(defaultLayout));
    const defaults = useMemo(
        () => createDefaultDashboardLayout(registry, defaultLayout, { layoutMode }),
        // registryKey deliberately describes only preference-relevant registry fields.
        // eslint-disable-next-line react-hooks/exhaustive-deps
        [registryKey, defaultLayoutKey, layoutMode]
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
    const preferenceKey = `${endpoint}|${requestParamsKey}|${scope}|${storageIdentity}|${layoutMode}|${remoteEnabled ? 'remote' : 'local'}`;

    const [layout, setLayout] = useState(() => normalizeDashboardLayout(
        readLocalLayout(storageKey) ?? defaults,
        registry,
        defaultLayout,
        { layoutMode }
    ));
    const [persistenceStatus, setPersistenceStatus] = useState(remoteEnabled ? 'loading' : 'local');
    const layoutRef = useRef(layout);
    const payloadRef = useRef({});
    const revisionRef = useRef(0);
    const mutationRef = useRef(0);
    const saveSequenceRef = useRef(0);
    const pendingSaveRef = useRef(null);
    const writeQueueRef = useRef(Promise.resolve());
    const mountedRef = useRef(true);
    const activePreferenceKeyRef = useRef(preferenceKey);

    useEffect(() => () => {
        mountedRef.current = false;
    }, []);

    const applyLayout = useCallback((nextLayout, localState) => {
        const normalized = normalizeDashboardLayout(nextLayout, registry, defaultLayout, { layoutMode });
        layoutRef.current = normalized;
        setLayout(normalized);
        writeLocalLayout(storageKey, normalized, localState);
        return normalized;
        // registryKey/defaultLayoutKey keep this stable when only render closures change.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [storageKey, registryKey, defaultLayoutKey, layoutMode]);

    const readRemotePreference = useCallback(async () => {
        const response = await api.get(endpoint, { params: requestParams });
        const data = response?.data && typeof response.data === 'object' ? response.data : null;
        return {
            payload: data?.payload && typeof data.payload === 'object' ? data.payload : {},
            revision: Number.isFinite(Number(data?.revision)) ? Number(data.revision) : 0,
        };
    }, [endpoint, requestParamsKey]);

    const persistRemote = useCallback((nextLayout, editId) => {
        const saveSequence = ++saveSequenceRef.current;
        if (!remoteEnabled) {
            pendingSaveRef.current = null;
            if (mountedRef.current) setPersistenceStatus('local');
            return;
        }
        const capturedPreferenceKey = preferenceKey;
        pendingSaveRef.current = { preferenceKey: capturedPreferenceKey, sequence: saveSequence };
        if (mountedRef.current) setPersistenceStatus('saving');
        const finish = (status) => {
            if (pendingSaveRef.current?.sequence === saveSequence) pendingSaveRef.current = null;
            if (mountedRef.current && activePreferenceKeyRef.current === capturedPreferenceKey
                && saveSequenceRef.current === saveSequence) setPersistenceStatus(status);
        };
        const save = async () => {
            if (activePreferenceKeyRef.current !== capturedPreferenceKey) { finish('local'); return; }
            const attempt = async (allowConflictRetry) => {
                const nextPayload = mergeScopeLayout(payloadRef.current, scope, nextLayout);
                try {
                    const response = await api.put(endpoint, {
                        schemaVersion: DASHBOARD_LAYOUT_SCHEMA_VERSION,
                        revision: revisionRef.current,
                        payload: nextPayload,
                    }, { params: requestParams });
                    acknowledgeLocalEdit(storageKey, editId);
                    if (activePreferenceKeyRef.current !== capturedPreferenceKey) { finish('server'); return; }
                    const responseData = response?.data || {};
                    payloadRef.current = responseData.payload || nextPayload;
                    revisionRef.current = Number.isFinite(Number(responseData.revision))
                        ? Number(responseData.revision)
                        : revisionRef.current + 1;
                    finish('server');
                } catch (error) {
                    if (allowConflictRetry && isConflict(error)) {
                        try {
                            const latest = await readRemotePreference();
                            if (activePreferenceKeyRef.current !== capturedPreferenceKey) { finish('local'); return; }
                            payloadRef.current = latest.payload;
                            revisionRef.current = latest.revision;
                            await attempt(false);
                            return;
                        } catch {
                            // Fall through to the durable local copy.
                        }
                    }
                    finish('local');
                }
            };

            await attempt(true);
        };

        writeQueueRef.current = writeQueueRef.current.catch(() => undefined).then(save);
    }, [endpoint, preferenceKey, readRemotePreference, remoteEnabled, requestParamsKey, scope, storageKey]);

    useEffect(() => {
        mountedRef.current = true;
        activePreferenceKeyRef.current = preferenceKey;
        mutationRef.current = 0;
        const pendingAtLoad = pendingSaveRef.current?.preferenceKey === preferenceKey;
        const loadSaveSequence = saveSequenceRef.current;
        if (!pendingAtLoad) {
            payloadRef.current = {};
            revisionRef.current = 0;
        }

        const localPreference = readLocalPreference(storageKey);
        const localLayout = localPreference?.layout;
        const startingLayout = normalizeDashboardLayout(
            localLayout ?? defaults,
            registry,
            defaultLayout,
            { layoutMode }
        );
        layoutRef.current = startingLayout;
        setLayout(startingLayout);
        setPersistenceStatus(remoteEnabled ? (pendingAtLoad ? 'saving' : 'loading') : 'local');
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
                // An older GET or earlier queued write must not acknowledge a newer local edit.
                if (pendingAtLoad || mutationRef.current !== loadMutation || saveSequenceRef.current !== loadSaveSequence) return;
                payloadRef.current = payload;
                revisionRef.current = revision;
                const unsaved = readLocalPreference(storageKey);
                if (unsaved?.dirty) {
                    const editId = unsaved.editId || createEditId();
                    const normalized = applyLayout(unsaved.layout, { dirty: true, editId });
                    persistRemote(normalized, editId);
                    return;
                }
                const remoteLayout = getScopeLayout(payload, scope);
                if (remoteLayout && mutationRef.current === loadMutation) {
                    applyLayout(remoteLayout);
                }
                setPersistenceStatus('server');
            })
            .catch(() => {
                if (!cancelled && !pendingAtLoad && mutationRef.current === loadMutation
                    && saveSequenceRef.current === loadSaveSequence) setPersistenceStatus('local');
            });

        return () => {
            cancelled = true;
        };
        // defaultsKey represents the complete default preference shape.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [storageKey, endpoint, preferenceKey, requestParamsKey, scope, defaultsKey, readRemotePreference, remoteEnabled, persistRemote]);

    const updateLayout = useCallback((nextValue) => {
        mutationRef.current += 1;
        const candidate = typeof nextValue === 'function'
            ? nextValue(layoutRef.current)
            : nextValue;
        const editId = createEditId();
        const normalized = applyLayout(candidate, { dirty: true, editId });
        persistRemote(normalized, editId);
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
