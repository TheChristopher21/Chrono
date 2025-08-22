/** @vitest-environment jsdom */
import 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';

vi.mock('../../utils/api', () => ({ default: { get: vi.fn().mockResolvedValue({ data: null }) } }));
vi.mock('../ChangelogModal.jsx', () => ({ default: () => null }));

import Navbar from '../Navbar.jsx';
import { AuthContext } from '../../context/AuthContext.jsx';
import { LanguageProvider } from '../../context/LanguageContext.jsx';
import { MemoryRouter } from 'react-router-dom';

const renderNavbar = (authValue, initialRoute = '/') => {
    return render(
        <MemoryRouter initialEntries={[initialRoute]}>
            <LanguageProvider>
                <AuthContext.Provider value={authValue}>
                    <Navbar />
                </AuthContext.Provider>
            </LanguageProvider>
        </MemoryRouter>
    );
};

describe('Navbar', () => {
    it('shows login and register links when unauthenticated', () => {
        renderNavbar({ authToken: null, currentUser: null, logout: vi.fn() }, '/login');
        expect(screen.getByText(/Login/i)).toBeInTheDocument();
        expect(screen.getByText(/Registrieren/i)).toBeInTheDocument();
        expect(screen.queryByText(/Abmelden/i)).toBeNull();
    });

    it('displays username and triggers logout when authenticated', async () => {
        const logoutMock = vi.fn();
        renderNavbar(
            { authToken: 'token', currentUser: { username: 'Alice', roles: [] }, logout: logoutMock },
            '/dashboard'
        );

        // Username-Button sichtbar
        const userMenuTrigger = screen.getByRole('button', { name: /Alice/i });
        expect(userMenuTrigger).toBeInTheDocument();

        // Menü öffnen
        await userEvent.click(userMenuTrigger);

        // Jetzt Logout sichtbar
        const logoutButton = await screen.findByRole('button', { name: /Abmelden/i });
        await userEvent.click(logoutButton);

        expect(logoutMock).toHaveBeenCalled();
    });

});
