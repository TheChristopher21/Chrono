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

const AuthContext        = createContext();
const INACTIVITY_DURATION = 10 * 60 * 1000;

export const AuthProvider = ({ children }) => {
    const { notify }        = useNotification();
    const [authToken, setAuthToken]   = useState(null);
    const [currentUser, setCurrentUser] = useState(null);

    /* ---------- Logout ------------------------------------------- */
    const logout = () => {
        localStorage.removeItem('token');
        setAuthToken(null);
        setCurrentUser(null);
        delete api.defaults.headers.common.Authorization;
    };

    /* ---------- Init --------------------------------------------- */
    useEffect(() => {
        const stored = localStorage.getItem('token');
        if (stored) {
            setAuthToken(stored);
            api.defaults.headers.common.Authorization = `Bearer ${stored}`;
            fetchCurrentUser();          //  ⬅️ holt User & setzt State
            resetInactivityTimer();
        } else {
            logout();
        }
    }, []);

    /* ---------- Timer -------------------------------------------- */
    const timerRef = useRef(null);
    const resetInactivityTimer = useCallback(() => {
        clearTimeout(timerRef.current);
        timerRef.current = setTimeout(() => {
            logout();
            notify('Session expired. Please log in again.');
        }, INACTIVITY_DURATION);
    }, [logout, notify]);

    useEffect(() => {
        if (!authToken) {
            return;
        }
        const events = ['click', 'mousemove', 'keydown', 'scroll'];
        events.forEach((e) => window.addEventListener(e, resetInactivityTimer));
        return () => {
            events.forEach((e) => window.removeEventListener(e, resetInactivityTimer));
            clearTimeout(timerRef.current);
        };
    }, [authToken, resetInactivityTimer]);

    /* ---------- User laden --------------------------------------- */
    const fetchCurrentUser = async () => {
        try {
            const res = await api.get('/api/auth/me');
            setCurrentUser(res.data);
            return res.data;
        } catch (err) {
            console.error('⚠️ /api/auth/me fehlgeschlagen', err);
            logout();
            return {};                       // niemals null/undefined zurückgeben
        }
    };

    /* ---------- Login -------------------------------------------- */
    const login = async (username, password) => {
        try {
            const { data } = await api.post('/api/auth/login', { username, password });
            const { token } = data;

            localStorage.setItem('token', token);
            api.defaults.headers.common.Authorization = `Bearer ${token}`;
            setAuthToken(token);

            const user = await fetchCurrentUser();   // garantiert Objekt
            resetInactivityTimer();
            return { success: true, user };
        } catch (err) {
            return { success: false, message: err.message };
        }
    };

    return (
        <AuthContext.Provider value={{ authToken, currentUser, login, logout }}>
            {children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => useContext(AuthContext);
