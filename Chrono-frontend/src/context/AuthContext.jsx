// src/context/AuthContext.jsx
import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';

const AuthContext = createContext();

const SESSION_DURATION = 300000; // 5 Minuten in Millisekunden

export const AuthProvider = ({ children }) => {
    const [authToken, setAuthToken] = useState(localStorage.getItem('token'));
    const [currentUser, setCurrentUser] = useState(() => {
        if (authToken) {
            try {
                const decoded = JSON.parse(atob(authToken.split('.')[1]));
                return {
                    username: decoded.username || decoded.sub,
                    roles: decoded.roles || [],
                    firstName: decoded.firstName || '',
                    lastName: decoded.lastName || '',
                    email: decoded.email || ''
                };
            } catch (error) {
                console.error("Error decoding token", error);
            }
        }
        return null;
    });

    const startSessionTimer = useCallback(() => {
        localStorage.setItem('loginTime', Date.now().toString());
        setTimeout(() => {
            const loginTime = parseInt(localStorage.getItem('loginTime'), 10);
            if (Date.now() - loginTime >= SESSION_DURATION) {
                logout();
                alert("Session expired. Please log in again.");
            }
        }, SESSION_DURATION);
    }, []);

    useEffect(() => {
        if (authToken) {
            startSessionTimer();
        }
    }, [authToken, startSessionTimer]);

    const login = (token) => {
        localStorage.setItem('token', token);
        setAuthToken(token);
        localStorage.setItem('loginTime', Date.now().toString());
        try {
            const decoded = JSON.parse(atob(token.split('.')[1]));
            setCurrentUser({
                username: decoded.username || decoded.sub,
                roles: decoded.roles || [],
                firstName: decoded.firstName || '',
                lastName: decoded.lastName || '',
                email: decoded.email || ''
            });
        } catch (error) {
            console.error("Error decoding token", error);
        }
        startSessionTimer();
    };

    const logout = () => {
        localStorage.removeItem('token');
        localStorage.removeItem('loginTime');
        setAuthToken(null);
        setCurrentUser(null);
    };

    return (
        <AuthContext.Provider value={{ authToken, currentUser, login, logout, setCurrentUser }}>
            {children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => useContext(AuthContext);
