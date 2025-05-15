// src/context/AuthContext.jsx
import {
    createContext,
    useContext,
    useState,
    useEffect,
    useCallback,
} from 'react';
import api from '../utils/api';
import { useNotification } from './NotificationContext';

const AuthContext       = createContext();
const SESSION_DURATION  = 30 * 60 * 1000;

/* Helper */
const isSessionExpired = () => {
    const t = parseInt(localStorage.getItem('loginTime') ?? '0', 10);
    return Date.now() - t >= SESSION_DURATION;
};

export const AuthProvider = ({ children }) => {
    const { notify }        = useNotification();
    const [authToken, setAuthToken]   = useState(null);
    const [currentUser, setCurrentUser] = useState(null);

    /* ---------- Init --------------------------------------------- */
    useEffect(() => {
        const stored = localStorage.getItem('token');
        if (stored && !isSessionExpired()) {
            setAuthToken(stored);
            api.defaults.headers.common.Authorization = `Bearer ${stored}`;
            fetchCurrentUser();          //  ⬅️ holt User & setzt State
            startSessionTimer();
        } else {
            logout();
        }
    }, []);

    /* ---------- Timer -------------------------------------------- */
    const startSessionTimer = useCallback(() => {
        const remaining =
            SESSION_DURATION -
            (Date.now() - parseInt(localStorage.getItem('loginTime') ?? '0', 10));

        const id = setTimeout(() => {
            logout();
            notify('Session expired. Please log in again.');
        }, Math.max(0, remaining));

        return () => clearTimeout(id);
    }, [notify]);

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
            localStorage.setItem('loginTime', Date.now().toString());
            api.defaults.headers.common.Authorization = `Bearer ${token}`;
            setAuthToken(token);

            const user = await fetchCurrentUser();   // garantiert Objekt
            startSessionTimer();
            return { success: true, user };
        } catch (err) {
            return { success: false, message: err.message };
        }
    };

    /* ---------- Logout ------------------------------------------- */
    const logout = () => {
        localStorage.removeItem('token');
        localStorage.removeItem('loginTime');
        setAuthToken(null);
        setCurrentUser(null);
        delete api.defaults.headers.common.Authorization;
    };

    return (
        <AuthContext.Provider value={{ authToken, currentUser, login, logout }}>
            {children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => useContext(AuthContext);
