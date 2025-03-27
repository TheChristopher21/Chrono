import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { useNotification } from './NotificationContext';

const AuthContext = createContext();

const SESSION_DURATION = 300000; // 5 Minuten

export const AuthProvider = ({ children }) => {
    const [authToken, setAuthToken] = useState(localStorage.getItem('token'));
    const [currentUser, setCurrentUser] = useState(null);
    const { notify } = useNotification(); // Nutzung des Notification-Contexts

    const startSessionTimer = useCallback(() => {
        localStorage.setItem('loginTime', Date.now().toString());
        setTimeout(() => {
            const loginTime = parseInt(localStorage.getItem('loginTime'), 10);
            if (Date.now() - loginTime >= SESSION_DURATION) {
                logout();
                // Statt alert wird notify verwendet, sodass kein blockierender Dialog entsteht.
                notify("Session expired. Please log in again.");
            }
        }, SESSION_DURATION);
    }, [notify]);

    useEffect(() => {
        if (authToken) {
            try {
                const decoded = JSON.parse(atob(authToken.split('.')[1]));
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
        }
    }, [authToken, startSessionTimer]);

    const login = async (username, password) => {
        try {
            const response = await fetch('http://localhost:8080/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });
            if (!response.ok) {
                throw new Error('Login failed');
            }
            const data = await response.json();
            localStorage.setItem('token', data.token);
            setAuthToken(data.token);
            localStorage.setItem('loginTime', Date.now().toString());
            const decoded = JSON.parse(atob(data.token.split('.')[1]));
            setCurrentUser({
                username: decoded.username || decoded.sub,
                roles: decoded.roles || [],
                firstName: decoded.firstName || '',
                lastName: decoded.lastName || '',
                email: decoded.email || ''
            });
            startSessionTimer();
            return { success: true, token: data.token, user: decoded };
        } catch (error) {
            return { success: false, message: error.message };
        }
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
