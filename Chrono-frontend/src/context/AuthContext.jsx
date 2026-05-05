// src/context/AuthContext.jsx
import {
    createContext,
    useContext,
    useState,
    useEffect,
    useCallback,
    useRef,
} from 'react';
import api from '../utils/api';
import { useNotification } from './NotificationContext';
import { useTranslation } from './LanguageContext';

export const AuthContext = createContext();

const INACTIVITY_DURATION = 10 * 60 * 1000; // 10 minutes
const ACTIVITY_WRITE_THROTTLE = 1000;
const LAST_ACTIVITY_STORAGE_KEY = 'lastActivityAt';
const areUsersEqual = (prevUser, nextUser) => JSON.stringify(prevUser) === JSON.stringify(nextUser);

const getLastActivityAt = () => {
    const storedValue = Number(localStorage.getItem(LAST_ACTIVITY_STORAGE_KEY));
    return Number.isFinite(storedValue) && storedValue > 0 ? storedValue : null;
};

const markSessionActivity = (timestamp = Date.now()) => {
    const lastActivityAt = getLastActivityAt();
    if (lastActivityAt !== null && timestamp - lastActivityAt < ACTIVITY_WRITE_THROTTLE) {
        return lastActivityAt;
    }

    localStorage.setItem(LAST_ACTIVITY_STORAGE_KEY, String(timestamp));
    return timestamp;
};

const clearStoredSession = () => {
    localStorage.removeItem('token');
    localStorage.removeItem(LAST_ACTIVITY_STORAGE_KEY);
};

const hasSessionTimedOut = (timestamp = Date.now()) => {
    const lastActivityAt = getLastActivityAt();
    return lastActivityAt === null || timestamp - lastActivityAt >= INACTIVITY_DURATION;
};

const getUsableStoredToken = () => {
    const token = localStorage.getItem('token');
    if (!token) {
        return null;
    }

    if (hasSessionTimedOut()) {
        clearStoredSession();
        return null;
    }

    return token;
};

export const AuthProvider = ({ children }) => {
    const { notify } = useNotification();
    const { t } = useTranslation();
    const [authToken, setAuthToken] = useState(() => getUsableStoredToken());
    const [currentUser, setCurrentUser] = useState(null);
    const [isAuthLoading, setIsAuthLoading] = useState(() => Boolean(localStorage.getItem('token')));
    const currentUserRef = useRef(null);

    useEffect(() => {
        currentUserRef.current = currentUser;
    }, [currentUser]);

    // Logout clears all auth-related client state.
    const logout = useCallback(() => {
        clearStoredSession();
        sessionStorage.removeItem('chatMessages');
        setAuthToken(null);
        setCurrentUser(null);
        setIsAuthLoading(false);
        delete api.defaults.headers.common.Authorization;
    }, []);

    // Fetch the current user for the stored token.
    const fetchCurrentUser = useCallback(async (currentToken, options = {}) => {
        const shouldShowLoader = options.showLoader ?? !currentUserRef.current;
        const tokenToUse = currentToken || localStorage.getItem('token');
        if (!tokenToUse) {
            if (currentUserRef.current) {
                logout();
            }
            setIsAuthLoading(false);
            return null;
        }

        if (hasSessionTimedOut()) {
            logout();
            return null;
        }

        if (shouldShowLoader) {
            setIsAuthLoading(true);
        }
        api.defaults.headers.common.Authorization = `Bearer ${tokenToUse}`;

        try {
            const res = await api.get('/api/auth/me');
            const newUser = res.data;

            setCurrentUser((prevUser) => (areUsersEqual(prevUser, newUser) ? prevUser : newUser));

            return newUser;
        } catch (err) {
            console.error('/api/auth/me failed. Token may be invalid.', err);
            logout();
            return null;
        } finally {
            if (shouldShowLoader) {
                setIsAuthLoading(false);
            }
        }
    }, [logout]);

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
        const lastActivityAt = getLastActivityAt() ?? markSessionActivity();
        const remainingMillis = INACTIVITY_DURATION - (Date.now() - lastActivityAt);

        if (remainingMillis <= 0) {
            logout();
            notify(t('sessionExpired'));
            return;
        }

        timerRef.current = setTimeout(() => {
            if (hasSessionTimedOut()) {
                logout();
                notify(t('sessionExpired'));
            } else {
                scheduleInactivityTimer();
            }
        }, remainingMillis);
    }, [logout, notify, t]);

    const resetInactivityTimer = useCallback(() => {
        if (!localStorage.getItem('token')) {
            return;
        }

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
                logout();
                return;
            }

            if (event.key === LAST_ACTIVITY_STORAGE_KEY) {
                scheduleInactivityTimer();
            }
        };
        window.addEventListener('storage', handleStorage);

        return () => {
            events.forEach((eventName) => window.removeEventListener(eventName, resetInactivityTimer));
            window.removeEventListener('storage', handleStorage);
            clearTimeout(timerRef.current);
        };
    }, [authToken, logout, resetInactivityTimer, scheduleInactivityTimer]);

    const login = async (username, password) => {
        try {
            const { data } = await api.post('/api/auth/login', { username, password });
            const { token } = data;

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
