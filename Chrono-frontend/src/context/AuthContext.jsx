// src/context/AuthContext.jsx
import {
    createContext,
    useContext,
    useState,
    useEffect,
    useCallback,
    useRef,
} from 'react';
import { useLocation } from 'react-router-dom';
import api from '../utils/api';
import { hasFeatureAccess, hasPageAccess } from '../utils/pageAccess';
import { useNotification } from './NotificationContext';
import { useTranslation } from './LanguageContext';
import { clearPmsOffline } from '../pages/Pms/pmsOfflineStore.js';

export const AuthContext = createContext();

const INACTIVITY_DURATION = 10 * 60 * 1000; // 10 minutes
const ACTIVITY_WRITE_THROTTLE = 1000;
const LAST_ACTIVITY_STORAGE_KEY = 'lastActivityAt';
const TAB_ACTIVITY_STORAGE_KEY = 'chrono:tabLastActivityAt';
const TAB_IDLE_SIGN_OUT_KEY = 'chrono:tabIdleSignOut';
const PMS_REFRESH_CHECK_INTERVAL = 30 * 1000;
const isPmsPath = (pathname) => pathname === '/pms' || pathname.startsWith('/pms/');
const canKeepPmsSession = (user) => hasFeatureAccess(user, 'pms') && hasPageAccess(user, 'pms');
const areUsersEqual = (prevUser, nextUser) => JSON.stringify(prevUser) === JSON.stringify(nextUser);

const readTokenClaims = (token) => {
    try {
        const payload = atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'));
        return JSON.parse(new TextDecoder().decode(Uint8Array.from(payload, (character) => character.charCodeAt(0))));
    } catch {
        return null;
    }
};

const needsTokenRefresh = (token) => {
    try {
        const claims = readTokenClaims(token);
        const expiresAt = Number(claims.exp) * 1000;
        const lifetime = expiresAt - Number(claims.iat) * 1000;
        const refreshWindow = Math.max(60_000, Math.min(5 * 60_000, lifetime * 0.2 || 5 * 60_000));
        return Number.isFinite(expiresAt) && expiresAt - Date.now() <= refreshWindow;
    } catch {
        return false; // The server, rather than this scheduling hint, validates tokens.
    }
};

const getLastActivityAt = () => {
    const tabValue = Number(sessionStorage.getItem(TAB_ACTIVITY_STORAGE_KEY));
    if (Number.isFinite(tabValue) && tabValue > 0) return tabValue;
    const storedValue = Number(localStorage.getItem(LAST_ACTIVITY_STORAGE_KEY));
    if (!Number.isFinite(storedValue) || storedValue <= 0) return null;
    // Seed new/legacy tabs once. Subsequent activity in other browser tabs must
    // not keep this tab's Chrono workspace alive.
    sessionStorage.setItem(TAB_ACTIVITY_STORAGE_KEY, String(storedValue));
    return storedValue;
};

const markSessionActivity = (timestamp = Date.now()) => {
    const lastActivityAt = getLastActivityAt();
    if (lastActivityAt !== null && timestamp - lastActivityAt < ACTIVITY_WRITE_THROTTLE) {
        return lastActivityAt;
    }

    sessionStorage.setItem(TAB_ACTIVITY_STORAGE_KEY, String(timestamp));
    localStorage.setItem(LAST_ACTIVITY_STORAGE_KEY, String(timestamp));
    return timestamp;
};

const clearStoredSession = () => {
    clearPmsOffline();
    localStorage.removeItem('token');
    localStorage.removeItem(LAST_ACTIVITY_STORAGE_KEY);
    sessionStorage.removeItem(TAB_ACTIVITY_STORAGE_KEY);
    sessionStorage.removeItem(TAB_IDLE_SIGN_OUT_KEY);
};

const hasSessionTimedOut = (timestamp = Date.now()) => {
    const lastActivityAt = getLastActivityAt();
    return lastActivityAt === null || timestamp - lastActivityAt >= INACTIVITY_DURATION;
};

const getUsableStoredToken = (pmsRoute) => {
    if (sessionStorage.getItem(TAB_IDLE_SIGN_OUT_KEY)) return null;
    const token = localStorage.getItem('token');
    if (!token) {
        return null;
    }

    if (!pmsRoute && hasSessionTimedOut()) {
        sessionStorage.setItem(TAB_IDLE_SIGN_OUT_KEY, 'true');
        return null;
    }

    return token;
};

export const AuthProvider = ({ children }) => {
    const { pathname } = useLocation();
    const pmsRoute = isPmsPath(pathname);
    const { notify } = useNotification();
    const { t } = useTranslation();
    const [authToken, setAuthToken] = useState(() => getUsableStoredToken(pmsRoute));
    const [currentUser, setCurrentUser] = useState(null);
    const [isAuthLoading, setIsAuthLoading] = useState(() => Boolean(authToken));
    const currentUserRef = useRef(null);
    const pmsRouteRef = useRef(pmsRoute);
    const sessionGenerationRef = useRef(0);
    pmsRouteRef.current = pmsRoute;

    useEffect(() => {
        currentUserRef.current = currentUser;
    }, [currentUser]);

    const clearClientAuth = useCallback(() => {
        sessionGenerationRef.current += 1;
        sessionStorage.removeItem('chatMessages');
        setAuthToken(null);
        setCurrentUser(null);
        setIsAuthLoading(false);
        delete api.defaults.headers.common.Authorization;
    }, []);

    // An explicit logout invalidates the shared browser login in every tab.
    const logout = useCallback(() => {
        clearStoredSession();
        clearClientAuth();
    }, [clearClientAuth]);

    // Inactivity belongs to this browser tab. Deleting the shared token here
    // would also sign out an unattended PMS open in a different browser tab.
    const expireIdleSession = useCallback(() => {
        sessionStorage.setItem(TAB_IDLE_SIGN_OUT_KEY, 'true');
        clearClientAuth();
    }, [clearClientAuth]);

    // Fetch the current user for the stored token.
    const fetchCurrentUser = useCallback(async (currentToken, options = {}) => {
        if (sessionStorage.getItem(TAB_IDLE_SIGN_OUT_KEY)) {
            setIsAuthLoading(false);
            return null;
        }
        const shouldShowLoader = options.showLoader ?? !currentUserRef.current;
        const tokenToUse = currentToken || localStorage.getItem('token');
        if (!tokenToUse) {
            if (currentUserRef.current) {
                logout();
            }
            setIsAuthLoading(false);
            return null;
        }

        if (!pmsRouteRef.current && hasSessionTimedOut()) {
            expireIdleSession();
            return null;
        }

        if (shouldShowLoader) {
            setIsAuthLoading(true);
        }
        api.defaults.headers.common.Authorization = `Bearer ${tokenToUse}`;
        const generation = sessionGenerationRef.current;

        try {
            const res = await api.get('/api/auth/me');
            if (generation !== sessionGenerationRef.current || localStorage.getItem('token') !== tokenToUse) {
                return null;
            }
            const newUser = res.data;

            setCurrentUser((prevUser) => (areUsersEqual(prevUser, newUser) ? prevUser : newUser));

            return newUser;
        } catch (err) {
            if (generation !== sessionGenerationRef.current) return null;
            console.error('/api/auth/me failed. Token may be invalid.', err);
            logout();
            return null;
        } finally {
            if (shouldShowLoader) {
                setIsAuthLoading(false);
            }
        }
    }, [logout, expireIdleSession]);

    // Initial load for a restored token.
    useEffect(() => {
        const token = localStorage.getItem('token');
        if (token) {
            fetchCurrentUser(token);
        } else {
            setIsAuthLoading(false);
        }
    }, [fetchCurrentUser]);

    // Inactivity timer.
    const timerRef = useRef(null);

    const scheduleInactivityTimer = useCallback(() => {
        clearTimeout(timerRef.current);
        if (pmsRouteRef.current && (!currentUserRef.current || canKeepPmsSession(currentUserRef.current))) {
            return;
        }
        const lastActivityAt = getLastActivityAt() ?? markSessionActivity();
        const remainingMillis = INACTIVITY_DURATION - (Date.now() - lastActivityAt);

        if (remainingMillis <= 0) {
            expireIdleSession();
            notify(t('sessionExpired'));
            return;
        }

        timerRef.current = setTimeout(() => {
            if (pmsRouteRef.current && (!currentUserRef.current || canKeepPmsSession(currentUserRef.current))) return;
            if (hasSessionTimedOut()) {
                expireIdleSession();
                notify(t('sessionExpired'));
            } else {
                scheduleInactivityTimer();
            }
        }, remainingMillis);
    }, [expireIdleSession, notify, t]);

    const resetInactivityTimer = useCallback(() => {
        if (!localStorage.getItem('token') || sessionStorage.getItem(TAB_IDLE_SIGN_OUT_KEY)) {
            return;
        }
        if (pmsRouteRef.current && (!currentUserRef.current || canKeepPmsSession(currentUserRef.current))) return;

        markSessionActivity();
        scheduleInactivityTimer();
    }, [scheduleInactivityTimer]);

    useEffect(() => {
        if (!authToken) {
            clearTimeout(timerRef.current);
            return;
        }

        scheduleInactivityTimer();
        const events = ['click', 'mousemove', 'keydown', 'scroll'];
        events.forEach((eventName) => window.addEventListener(eventName, resetInactivityTimer));
        const handleStorage = (event) => {
            if (event.key === 'token' && !event.newValue) {
                // The originating tab already removed the shared credentials.
                // Do not delete a newer login if storage events arrive later.
                expireIdleSession();
                return;
            }
            if (event.key === 'token' && event.newValue && localStorage.getItem('token') === event.newValue) {
                const previousSubject = currentUserRef.current?.username ?? readTokenClaims(authToken)?.sub;
                const nextSubject = readTokenClaims(event.newValue)?.sub;
                if (!previousSubject || !nextSubject || previousSubject !== nextSubject) {
                    // Another native tab switched account. Never combine its
                    // credentials with this tab's existing company/profile state.
                    expireIdleSession();
                    return;
                }
                setAuthToken(event.newValue);
                api.defaults.headers.common.Authorization = `Bearer ${event.newValue}`;
                fetchCurrentUser(event.newValue, { showLoader: false });
            }
        };
        window.addEventListener('storage', handleStorage);

        return () => {
            events.forEach((eventName) => window.removeEventListener(eventName, resetInactivityTimer));
            window.removeEventListener('storage', handleStorage);
            clearTimeout(timerRef.current);
        };
    }, [authToken, logout, expireIdleSession, fetchCurrentUser, resetInactivityTimer, scheduleInactivityTimer]);

    // Returning to Chrono starts its normal ten-minute inactivity period again.
    const previousPmsRouteRef = useRef(pmsRoute);
    useEffect(() => {
        if (authToken && previousPmsRouteRef.current && !pmsRoute) markSessionActivity();
        previousPmsRouteRef.current = pmsRoute;
        if (authToken) scheduleInactivityTimer();
    }, [pmsRoute, authToken, currentUser, scheduleInactivityTimer]);

    // Only an open, authorized PMS renews finite server tokens. Activity timestamps
    // are deliberately untouched, so Chrono keeps its usual inactivity policy.
    useEffect(() => {
        if (!authToken || !pmsRoute || !canKeepPmsSession(currentUser)) return;
        let cancelled = false;
        let pending = false;
        const renewIfNeeded = async () => {
            const token = localStorage.getItem('token');
            if (cancelled || pending || !token || !needsTokenRefresh(token)) return;
            pending = true;
            const generation = sessionGenerationRef.current;
            try {
                const { data } = await api.post('/api/pms/session/refresh', {}, { timeout: 10_000, skipDataRefresh: true });
                if (cancelled || generation !== sessionGenerationRef.current
                    || localStorage.getItem('token') !== token || !pmsRouteRef.current) return;
                if (typeof data?.token !== 'string' || !data.token) return;
                localStorage.setItem('token', data.token);
                api.defaults.headers.common.Authorization = `Bearer ${data.token}`;
                setAuthToken(data.token);
            } catch (error) {
                if (cancelled || generation !== sessionGenerationRef.current
                    || localStorage.getItem('token') !== token) return;
                if ([401, 403].includes(error.response?.status)) {
                    logout();
                    notify(t('sessionExpired'));
                }
                // Transient connection failures retry while the PMS remains open.
            } finally {
                pending = false;
            }
        };
        renewIfNeeded();
        const interval = setInterval(renewIfNeeded, PMS_REFRESH_CHECK_INTERVAL);
        window.addEventListener('focus', renewIfNeeded);
        document.addEventListener('visibilitychange', renewIfNeeded);
        return () => {
            cancelled = true;
            clearInterval(interval);
            window.removeEventListener('focus', renewIfNeeded);
            document.removeEventListener('visibilitychange', renewIfNeeded);
        };
    }, [authToken, pmsRoute, currentUser, logout, notify, t]);

    const login = async (username, password) => {
        try {
            const { data } = await api.post('/api/auth/login', { username, password });
            const { token } = data;

            sessionStorage.removeItem(TAB_IDLE_SIGN_OUT_KEY);
            sessionStorage.removeItem(TAB_ACTIVITY_STORAGE_KEY);
            localStorage.setItem('token', token);
            markSessionActivity();
            setAuthToken(token);

            const user = await fetchCurrentUser(token);
            return { success: true, user };
        } catch (err) {
            logout();
            return { success: false, message: err.response?.data?.message || 'Login failed' };
        }
    };

    const loginDemo = async () => {
        try {
            const { data } = await api.post('/api/auth/demo');
            const { token } = data;

            sessionStorage.removeItem(TAB_IDLE_SIGN_OUT_KEY);
            sessionStorage.removeItem(TAB_ACTIVITY_STORAGE_KEY);
            localStorage.setItem('token', token);
            markSessionActivity();
            setAuthToken(token);

            const user = await fetchCurrentUser(token);
            return { success: true, user };
        } catch (err) {
            logout();
            return { success: false, message: err.response?.data?.message || 'Demo login failed' };
        }
    };

    return (
        <AuthContext.Provider
            value={{
                authToken,
                currentUser,
                isAuthLoading,
                setCurrentUser,
                login,
                loginDemo,
                logout,
                fetchCurrentUser,
            }}
        >
            {children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => useContext(AuthContext);
