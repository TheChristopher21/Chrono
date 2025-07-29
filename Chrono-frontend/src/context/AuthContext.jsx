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

const INACTIVITY_DURATION = 10 * 60 * 1000; // 10 Minuten Inaktivität
const USER_STATUS_CHECK_INTERVAL = 15 * 60 * 1000; // 15 Minuten für periodische Prüfung

export const AuthProvider = ({ children }) => {
    const { notify } = useNotification();
    const { t } = useTranslation();
    const [authToken, setAuthToken] = useState(() => localStorage.getItem('token'));
    const [currentUser, setCurrentUser] = useState(null);

    // Logout-Funktion, die Zustände zurücksetzt
    const logout = useCallback(() => {
        localStorage.removeItem('token');
        setAuthToken(null);
        setCurrentUser(null);
        delete api.defaults.headers.common.Authorization;
    }, []);

    // Funktion zum Abrufen des aktuellen Benutzers vom Server
    const fetchCurrentUser = useCallback(async (currentToken) => {
        // Falls kein Token übergeben wird, versuche es aus dem localStorage zu holen
        const tokenToUse = currentToken || localStorage.getItem('token');
        if (!tokenToUse) {
            // Stellt sicher, dass ein eingeloggter Benutzer ohne Token ausgeloggt wird
            if (currentUser) {
                logout();
            }
            return;
        }

        api.defaults.headers.common.Authorization = `Bearer ${tokenToUse}`;

        try {
            const res = await api.get('/api/auth/me');
            const newUser = res.data;

            // *** DIE KERNLÖSUNG ***
            // Aktualisiert den Benutzer nur, wenn sich die Daten geändert haben.
            // Dies verhindert das unnötige Neu-Rendern und das Springen der Seite.
            setCurrentUser(prevUser => {
                if (JSON.stringify(prevUser) !== JSON.stringify(newUser)) {
                    return newUser; // Daten haben sich geändert -> aktualisieren
                }
                return prevUser; // Daten sind identisch -> keine Änderung, kein Re-Render
            });

        } catch (err) {
            console.error('⚠️ /api/auth/me fehlgeschlagen. Token könnte ungültig sein.', err);
            logout(); // Bei Fehler ausloggen, um inkonsistente Zustände zu vermeiden
        }
    }, [logout, currentUser]); // Abhängigkeiten korrigiert

    // Effekt für den initialen Ladevorgang und die periodische Statusprüfung
    useEffect(() => {
        const token = localStorage.getItem('token');
        if (token) {
            fetchCurrentUser(token);
        }

        // Dieser periodische Check stellt sicher, dass der Benutzerstatus aktuell bleibt
        // (z.B. wenn ein Admin die Rechte ändert) und ist so optimiert, dass er
        // das "Springen" der Seite verhindert.
        const intervalId = setInterval(() => {
            const currentToken = localStorage.getItem('token');
            if (currentToken) {
                fetchCurrentUser(currentToken);
            }
        }, USER_STATUS_CHECK_INTERVAL);

        // Aufräumen, wenn die Komponente verlassen wird
        return () => clearInterval(intervalId);
    }, [fetchCurrentUser]);


    // Effekt für den Inaktivitäts-Timer
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
        };

        resetInactivityTimer();
        const events = ['click', 'mousemove', 'keydown', 'scroll'];
        events.forEach((e) => window.addEventListener(e, resetInactivityTimer));

        return () => {
            events.forEach((e) => window.removeEventListener(e, resetInactivityTimer));
            clearTimeout(timerRef.current);
        };
    }, [authToken, resetInactivityTimer]);


    // Login-Funktion
    const login = async (username, password) => {
        try {
            const { data } = await api.post('/api/auth/login', { username, password });
            const { token } = data;

            localStorage.setItem('token', token);
            setAuthToken(token); // Löst den Inaktivitäts-Timer aus

            const user = await fetchCurrentUser(token);
            return { success: true, user };
        } catch (err) {
            logout();
            return { success: false, message: err.response?.data?.message || "Login failed" };
        }
    };

    return (
        <AuthContext.Provider value={{ authToken, currentUser, setCurrentUser, login, logout, fetchCurrentUser }}>
            {children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => useContext(AuthContext);