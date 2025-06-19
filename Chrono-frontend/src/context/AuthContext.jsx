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

export const AuthContext = createContext();
const INACTIVITY_DURATION = 10 * 60 * 1000; // 10 min

export const AuthProvider = ({ children }) => {
    const { notify } = useNotification();
    const [authToken, setAuthToken] = useState(null);
    const [currentUser, setCurrentUser] = useState(null);

    /* ---------- Logout ------------------------------------------- */
    const logout = () => {
        localStorage.removeItem('token');
        setAuthToken(null);
        setCurrentUser(null);
        delete api.defaults.headers.common.Authorization;
    };

    /* ---------- User laden --------------------------------------- */
    // Diese Funktion muss auch über den Context bereitgestellt werden
    const fetchCurrentUser = useCallback(async () => { // useCallback hinzugefügt für Konsistenz und Memoization
        try {
            const res = await api.get('/api/auth/me');
            setCurrentUser(res.data);
            return res.data; // Gibt die Benutzerdaten zurück, damit sie direkt verwendet werden können
        } catch (err) {
            console.error('⚠️ /api/auth/me fehlgeschlagen', err);
            logout(); // Wichtig: Bei Fehler ausloggen
            return null; // Gibt null zurück bei Fehler, um klar zu signalisieren, dass kein User geladen wurde
        }
    }, []); // Abhängigkeiten hier leer, da logout nicht als Abhängigkeit notwendig ist (definiert im selben Scope)

    /* ---------- Init --------------------------------------------- */
    useEffect(() => {
        const storedToken = localStorage.getItem('token');
        if (storedToken) {
            setAuthToken(storedToken);
            api.defaults.headers.common.Authorization = `Bearer ${storedToken}`;
            fetchCurrentUser(); // Ruft die oben definierte Funktion auf
            // resetInactivityTimer(); // Timer wird erst nach erfolgreichem Login/Token-Validierung gestartet
        } else {
            logout(); // Stellt sicher, dass der Zustand sauber ist, wenn kein Token vorhanden ist
        }
    }, [fetchCurrentUser]); // fetchCurrentUser als Abhängigkeit hinzugefügt


    /* ---------- Timer -------------------------------------------- */
    const timerRef = useRef(null);

    const resetInactivityTimer = useCallback(() => {
        clearTimeout(timerRef.current);
        timerRef.current = setTimeout(() => {
            logout();
            notify('Session expired. Please log in again.');
        }, INACTIVITY_DURATION);
    }, [logout, notify]); // notify und logout sind Abhängigkeiten

    useEffect(() => {
        if (!authToken) return; // Timer nur starten, wenn ein Token vorhanden ist

        // Timer beim ersten Laden (nachdem Token aus localStorage gelesen wurde) und nach jedem Login starten
        resetInactivityTimer();

        const events = ['click', 'mousemove', 'keydown', 'scroll'];
        events.forEach((e) => window.addEventListener(e, resetInactivityTimer));

        return () => {
            events.forEach((e) => window.removeEventListener(e, resetInactivityTimer));
            clearTimeout(timerRef.current);
        };
    }, [authToken, resetInactivityTimer]);


    /* ---------- Login -------------------------------------------- */
    const login = async (username, password) => {
        try {
            const { data } = await api.post('/api/auth/login', { username, password });
            const { token } = data;

            localStorage.setItem('token', token);
            api.defaults.headers.common.Authorization = `Bearer ${token}`;
            setAuthToken(token);

            const user = await fetchCurrentUser();   // Ruft die oben definierte Funktion auf
            // resetInactivityTimer(); // Wird jetzt durch das useEffect auf authToken ausgelöst
            return { success: true, user };
        } catch (err) {
            logout(); // Bei Login-Fehler auch ausloggen, um inkonsistenten Zustand zu vermeiden
            return { success: false, message: err.message || "Login failed" };
        }
    };

    return (
        <AuthContext.Provider value={{ authToken, currentUser, login, logout, fetchCurrentUser }}> {/* fetchCurrentUser hier hinzugefügt */}
            {children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => useContext(AuthContext);