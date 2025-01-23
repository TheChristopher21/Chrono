import React, { createContext, useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

export const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
    const [isAuthenticated, setIsAuthenticated] = useState(false);
    const [loading, setLoading] = useState(true);
    const navigate = useNavigate();

    useEffect(() => {
        const token = localStorage.getItem('token');
        if (token) {
            // Optional: Token-Validierung
            setIsAuthenticated(true);
        }
        setLoading(false);
    }, []);

    const login = (token) => {
        localStorage.setItem('token', token);
        setIsAuthenticated(true);
        navigate('/dashboard');
    };

    const logout = () => {
        localStorage.removeItem('token');
        setIsAuthenticated(false);
        navigate('/login');
    };

    if (loading) {
        return <div>Loading...</div>; // Ladeindikator
    }

    return (
        <AuthContext.Provider
            value={{
                isAuthenticated,
                login,
                logout,
                loading,
            }}
        >
            {children}
        </AuthContext.Provider>
    );
};
