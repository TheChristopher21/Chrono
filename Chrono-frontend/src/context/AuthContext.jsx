import { createContext, useState, useEffect } from 'react';
import api from '../utils/api';
import jwt_decode from 'jwt-decode';

export const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
    const [authToken, setAuthToken] = useState(null);
    const [currentUser, setCurrentUser] = useState(null);
    const [error, setError] = useState(null);

    // Prüft, ob ein Token existiert und setzt den User
    useEffect(() => {
        const storedToken = localStorage.getItem('token');
        if (storedToken) {
            setAuthToken(storedToken);
            decodeTokenAndSetUser(storedToken);
        }
    }, []);

    const decodeTokenAndSetUser = (token) => {
        try {
            const decoded = jwt_decode(token);
            console.log("🔍 Decoded Token:", decoded); // Debugging
            setCurrentUser({
                username: decoded.sub,
                roles: decoded.roles || [] // Falls undefined, setze leeres Array
            });
        } catch (err) {
            console.error('❌ Token decoding failed:', err);
            setCurrentUser(null);
        }
    };


    const login = async (username, password) => {
        try {
            setError(null);
            const response = await api.post('/api/auth/login', { username, password });
            const { token } = response.data;
            setAuthToken(token);
            localStorage.setItem('token', token);

            const decoded = jwt_decode(token);
            console.log("Decoded Token:", decoded); // Hier prüfen!

            setCurrentUser({
                username: decoded.sub,
                roles: decoded.roles || [] // Falls undefined, dann als leeres Array setzen
            });

            return { success: true };
        } catch (err) {
            console.error('Login failed:', err);
            return { success: false, message: err };
        }
    };



    const registerUser = async (username, password, firstName, lastName, email) => {
        try {
            setError(null);
            const response = await api.post('/api/auth/register', {
                username,
                password,
                firstName,
                lastName,
                email,
            });

            // Falls erfolgreich, speichere Token & setze User
            const { token } = response.data;
            setAuthToken(token);
            localStorage.setItem('token', token);
            decodeTokenAndSetUser(token);

            return { success: true };
        } catch (err) {
            console.error('Registration failed:', err.response?.data || err.message);
            setError(err.response?.data?.message || 'Registration error');
            return { success: false, message: err.response?.data?.message || 'Error registering' };
        }
    };

    const logout = () => {
        setAuthToken(null);
        setCurrentUser(null);
        localStorage.removeItem('token');
    };

    return (
        <AuthContext.Provider value={{
            authToken,
            currentUser,
            error,
            login,
            registerUser,
            logout
        }}>
            {children}
        </AuthContext.Provider>
    );
};
