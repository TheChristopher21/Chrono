import { createContext, useContext, useState, useEffect } from "react";
import api from "../utils/api";  // Dein API-Client mit axios

const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
    const [authToken, setAuthToken] = useState(localStorage.getItem("token"));
    const [currentUser, setCurrentUser] = useState(null);

    useEffect(() => {
        if (authToken) {
            api.defaults.headers.common["Authorization"] = `Bearer ${authToken}`;

            api.get("/api/auth/me")
                .then((res) => setCurrentUser(res.data))
                .catch(() => {
                    console.error("⚠️ Fehler: Benutzer konnte nicht geladen werden.");
                    logout();
                });
        }
    }, [authToken]);

    const login = async (username, password) => {
        try {
            const response = await fetch('/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });

            if (!response.ok) {
                throw new Error('Login fehlgeschlagen');
            }

            const data = await response.json();
            localStorage.setItem('token', data.token); // Token speichern
            return { success: true, token: data.token };
        } catch (error) {
            return { success: false, message: error.message };
        }
    };


    const logout = () => {
        localStorage.removeItem("token");
        setAuthToken(null);
        setCurrentUser(null);
        delete api.defaults.headers.common["Authorization"];
    };

    return (
        <AuthContext.Provider value={{ authToken, currentUser, login, logout }}>
            {children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => useContext(AuthContext);
