import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import api from '../utils/api';

// --------------------------------------------------------------
// AuthContext – stellt login/logout & User‑Daten global bereit
// --------------------------------------------------------------
export const AuthContext = createContext(null);

// Bequemer Hook zum Konsumieren des Contexts
export const useAuth = () => useContext(AuthContext);

export default function AuthProvider({ children }) {
    const [authToken, setAuthToken] = useState(null);
    const [currentUser, setCurrentUser] = useState(null);

    /* ------------------------------------------------------------
       Inactivity‑Logout (30 Minuten ohne Benutzerinteraktion)
    ------------------------------------------------------------ */
    const inactivityTimer = useRef(null);
    const INACTIVITY_LIMIT = 1_000 * 60 * 30; // 30 min

    /* ------------------------------------------------------------
       logout – **oberhalb** der Hooks, damit es bereits
       initialisiert ist, wenn useEffect es das erste Mal aufruft
    ------------------------------------------------------------ */
    function logout() {
        localStorage.removeItem('token');

        // Context‑State leeren
        setAuthToken(null);
        setCurrentUser(null);

        // Axios‑Defaults bereinigen
        delete api.defaults.headers.common.Authorization;

        // laufenden Timer stoppen
        clearTimeout(inactivityTimer.current);
    }

    /* ------------------------------------------------------------
       Hilfsfunktion zum Zurücksetzen/Starten des Inactivity‑Timers
    ------------------------------------------------------------ */
    const resetInactivityTimer = useCallback(() => {
        clearTimeout(inactivityTimer.current);
        inactivityTimer.current = setTimeout(logout, INACTIVITY_LIMIT);
    }, []);

    /* ------------------------------------------------------------
       login – Token sichern & Benutzerdaten laden
    ------------------------------------------------------------ */
    const login = async (token) => {
        localStorage.setItem('token', token);
        api.defaults.headers.common.Authorization = `Bearer ${token}`;
        setAuthToken(token);

        await fetchCurrentUser();
        resetInactivityTimer();
    };

    /* ------------------------------------------------------------
       Aktuellen Benutzer vom Backend holen
    ------------------------------------------------------------ */
    const fetchCurrentUser = async () => {
        try {
            const { data } = await api.get('/users/me');
            setCurrentUser(data);
        } catch (err) {
            console.error('Unable to fetch user – logging out', err);
            logout();
        }
    };

    /* ------------------------------------------------------------
       Beim ersten Mount: gespeichertes Token laden (falls vorhanden)
    ------------------------------------------------------------ */
    useEffect(() => {
        const stored = localStorage.getItem('token');
        if (!stored) return;                    // nichts zu tun → kein Token

        api.defaults.headers.common.Authorization = `Bearer ${stored}`;
        setAuthToken(stored);
        fetchCurrentUser();
        resetInactivityTimer();
    }, [resetInactivityTimer]);

    /* ------------------------------------------------------------
       Globale Event‑Listener für Benutzeraktivität
    ------------------------------------------------------------ */
    useEffect(() => {
        const events = ['mousemove', 'keydown', 'click', 'wheel', 'scroll', 'touchstart'];
        events.forEach((e) => window.addEventListener(e, resetInactivityTimer));
        return () => events.forEach((e) => window.removeEventListener(e, resetInactivityTimer));
    }, [resetInactivityTimer]);

    /* ------------------------------------------------------------
       Context‑Wert exportieren
    ------------------------------------------------------------ */
    const value = {
        authToken,
        currentUser,
        login,
        logout,
    };

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
