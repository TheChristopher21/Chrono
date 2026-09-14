import { useEffect, useRef } from 'react';
import {
    normalizeRefreshScopes,
    refreshScopesMatch,
    subscribeDataRefresh,
} from '../utils/dataRefresh.js';

const isDocumentHidden = () => (
    typeof document !== 'undefined' && document.visibilityState === 'hidden'
);

export const useRefreshOnMutation = (
    scopes,
    loader,
    {
        enabled = true,
        debounceMs = 80,
        refreshOnFocus = false,
        focusThrottleMs = 15_000,
        refreshOnLocalMutation = true,
    } = {},
) => {
    const loaderRef = useRef(loader);
    const inFlightRef = useRef(false);
    const trailingRefreshRef = useRef(false);
    const hiddenRefreshRef = useRef(false);
    const hiddenEventRef = useRef(null);
    const timerRef = useRef(null);
    const mountedRef = useRef(false);
    const lastRefreshStartedAtRef = useRef(Date.now());

    loaderRef.current = loader;

    const normalizedScopes = normalizeRefreshScopes(scopes);
    const scopeKey = normalizedScopes.join('|');
    const safeDebounceMs = Math.max(0, Number(debounceMs) || 0);
    const safeFocusThrottleMs = Math.max(0, Number(focusThrottleMs) || 0);

    useEffect(() => {
        mountedRef.current = true;
        trailingRefreshRef.current = false;
        hiddenRefreshRef.current = false;
        hiddenEventRef.current = null;
        lastRefreshStartedAtRef.current = Date.now();

        const clearScheduledRefresh = () => {
            if (timerRef.current !== null) {
                globalThis.clearTimeout(timerRef.current);
                timerRef.current = null;
            }
        };

        const runLoader = async () => {
            timerRef.current = null;
            if (!mountedRef.current || !enabled || typeof loaderRef.current !== 'function') return;

            if (isDocumentHidden()) {
                hiddenRefreshRef.current = true;
                return;
            }
            if (inFlightRef.current) {
                trailingRefreshRef.current = true;
                return;
            }

            inFlightRef.current = true;
            lastRefreshStartedAtRef.current = Date.now();
            try {
                await loaderRef.current();
            } catch (error) {
                // Background reconciliation must not turn a successful mutation into an
                // unhandled rejection. Feature loaders remain responsible for user notices.
                console.error('Background data refresh failed', error);
            } finally {
                inFlightRef.current = false;
                if (mountedRef.current && trailingRefreshRef.current) {
                    trailingRefreshRef.current = false;
                    void runLoader();
                }
            }
        };

        const scheduleRefresh = (event = null) => {
            if (!mountedRef.current || !enabled) return;
            if (isDocumentHidden()) {
                hiddenRefreshRef.current = true;
                hiddenEventRef.current = event;
                return;
            }
            if (inFlightRef.current) {
                trailingRefreshRef.current = true;
                return;
            }

            clearScheduledRefresh();
            const elapsedSincePublish = Number.isFinite(Number(event?.firstPublishedAt))
                ? Math.max(0, Date.now() - Number(event.firstPublishedAt))
                : 0;
            const remainingDelay = Math.max(0, safeDebounceMs - elapsedSincePublish);
            if (remainingDelay === 0) {
                void runLoader();
                return;
            }
            timerRef.current = globalThis.setTimeout(runLoader, remainingDelay);
        };

        const unsubscribe = enabled && normalizedScopes.length > 0
            ? subscribeDataRefresh((event) => {
                const isLocalOnlyEvent = Array.isArray(event.changes)
                    && event.changes.length > 0
                    && event.changes.every((change) => change?.origin === 'local');
                if (!refreshOnLocalMutation && isLocalOnlyEvent) return;
                if (refreshScopesMatch(normalizedScopes, event.scopes)) {
                    scheduleRefresh(event);
                }
            })
            : () => undefined;

        const handleVisibilityChange = () => {
            if (isDocumentHidden() || !enabled) return;

            if (hiddenRefreshRef.current) {
                const hiddenEvent = hiddenEventRef.current;
                hiddenRefreshRef.current = false;
                hiddenEventRef.current = null;
                scheduleRefresh(hiddenEvent);
                return;
            }

            if (
                refreshOnFocus
                && Date.now() - lastRefreshStartedAtRef.current >= safeFocusThrottleMs
            ) {
                scheduleRefresh();
            }
        };

        const handleWindowFocus = () => {
            if (
                !enabled
                || !refreshOnFocus
                || isDocumentHidden()
                || hiddenRefreshRef.current
                || Date.now() - lastRefreshStartedAtRef.current < safeFocusThrottleMs
            ) {
                return;
            }
            scheduleRefresh();
        };

        if (typeof document !== 'undefined') {
            document.addEventListener('visibilitychange', handleVisibilityChange);
        }
        if (typeof window !== 'undefined' && refreshOnFocus) {
            window.addEventListener('focus', handleWindowFocus);
        }

        return () => {
            mountedRef.current = false;
            clearScheduledRefresh();
            unsubscribe();
            if (typeof document !== 'undefined') {
                document.removeEventListener('visibilitychange', handleVisibilityChange);
            }
            if (typeof window !== 'undefined') {
                window.removeEventListener('focus', handleWindowFocus);
            }
        };
    }, [
        enabled,
        refreshOnFocus,
        refreshOnLocalMutation,
        safeDebounceMs,
        safeFocusThrottleMs,
        scopeKey,
    ]);
};

export default useRefreshOnMutation;
