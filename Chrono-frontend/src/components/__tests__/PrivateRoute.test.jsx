/** @vitest-environment jsdom */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import PrivateRoute from '../PrivateRoute.jsx';
import { AuthContext } from '../../context/AuthContext.jsx';

const renderProtectedRoute = (authValue) => {
    render(
        <AuthContext.Provider value={authValue}>
            <MemoryRouter initialEntries={['/admin/dashboard']}>
                <Routes>
                    <Route
                        path="/admin/dashboard"
                        element={(
                            <PrivateRoute requiredRole="ROLE_ADMIN">
                                <div>Admin dashboard</div>
                            </PrivateRoute>
                        )}
                    />
                    <Route path="/login" element={<div>Login page</div>} />
                    <Route path="/" element={<div>Landing page</div>} />
                </Routes>
            </MemoryRouter>
        </AuthContext.Provider>
    );
};

describe('PrivateRoute', () => {
    it('offers a retry after a profile connection failure without redirecting or showing protected data', () => {
        const fetchCurrentUser = vi.fn();
        const logout = vi.fn();
        renderProtectedRoute({
            authToken: 'token', currentUser: null, isAuthLoading: false,
            authError: 'Benutzerprofil konnte nicht geladen werden.', fetchCurrentUser, logout,
        });

        expect(screen.getByRole('alert')).toHaveTextContent('Benutzerprofil konnte nicht geladen werden.');
        expect(screen.queryByText('Login page')).not.toBeInTheDocument();
        expect(screen.queryByText('Admin dashboard')).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Erneut versuchen' }));
        expect(fetchCurrentUser).toHaveBeenCalledWith();
        fireEvent.click(screen.getByRole('button', { name: 'Abmelden' }));
        expect(logout).toHaveBeenCalledOnce();
    });

    it('waits for the user profile before checking admin permissions on refresh', () => {
        renderProtectedRoute({
            authToken: 'token',
            currentUser: null,
            isAuthLoading: true,
        });

        expect(screen.getByTestId('route-auth-loading')).toBeInTheDocument();
        expect(screen.queryByText('Landing page')).not.toBeInTheDocument();
        expect(screen.queryByText('Login page')).not.toBeInTheDocument();
    });

    it('renders the protected page once the user profile is loaded', () => {
        renderProtectedRoute({
            authToken: 'token',
            currentUser: { roles: ['ROLE_ADMIN'], companyFeatureKeys: [] },
            isAuthLoading: false,
        });

        expect(screen.getByText('Admin dashboard')).toBeInTheDocument();
    });

    it('keeps the protected page visible during background auth refreshes', () => {
        renderProtectedRoute({
            authToken: 'token',
            currentUser: { roles: ['ROLE_ADMIN'], companyFeatureKeys: [] },
            isAuthLoading: true,
        });

        expect(screen.getByText('Admin dashboard')).toBeInTheDocument();
        expect(screen.queryByTestId('route-auth-loading')).not.toBeInTheDocument();
    });
});
