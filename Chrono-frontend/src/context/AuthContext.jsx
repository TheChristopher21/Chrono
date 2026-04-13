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
const USER_STATUS_CHECK_INTERVAL = 15 * 60 * 1000; // 15 minutes
const areUsersEqual = (prevUser, nextUser) => JSON.stringify(prevUser) === JSON.stringify(nextUser);

export const AuthProvider = ({ children }) => {
    const { notify } = useNotification();
    const { t } = useTranslation();
    const [authToken, setAuthToken] = useState(() => localStorage.getItem('token'));
    const [currentUser, setCurrentUser] = useState(null);
    const [isAuthLoading, setIsAuthLoading] = useState(() => Boolean(localStorage.getItem('token')));
    const currentUserRef = useRef(null);

    useEffect(() => {
        currentUserRef.current = currentUser;
    }, [currentUser]);

    // Logout clears all auth-related client state.
    const logout = useCallback(() => {
        localStorage.removeItem('token');
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

    // Initial load and periodic user refresh.
    useEffect(() => {
        const token = localStorage.getItem('token');
        if (token) {
            fetchCurrentUser(token);
        } else {
            setIsAuthLoading(false);
        }

        const intervalId = setInterval(() => {
            const currentToken = localStorage.getItem('token');
            if (currentToken) {
                fetchCurrentUser(currentToken);
            }
        }, USER_STATUS_CHECK_INTERVAL);

        return () => clearInterval(intervalId);
    }, [fetchCurrentUser]);

    // Inactivity timer.
    const timerRef = useRef(null);

    const resetInactivityTimer = useCallback(() => {
        clearTimeout(timerRef.current);
        timerRef.current = setTimeout(() => {
            logout();
            notify(t('sessionExpired'));
        }, INACTIVITY_DURATION);
    }, [logout, notify, t]);

    useEffect(() => {
        if (!authToken) {
            clearTimeout(timerRef.current);
            return;
        }

        resetInactivityTimer();
        const events = ['click', 'mousemove', 'keydown', 'scroll'];
        events.forEach((eventName) => window.addEventListener(eventName, resetInactivityTimer));

        return () => {
            events.forEach((eventName) => window.removeEventListener(eventName, resetInactivityTimer));
            clearTimeout(timerRef.current);
        };
    }, [authToken, resetInactivityTimer]);

    const login = async (username, password) => {
        try {
            const { data } = await api.post('/api/auth/login', { username, password });
            const { token } = data;

            localStorage.setItem('token', token);
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
