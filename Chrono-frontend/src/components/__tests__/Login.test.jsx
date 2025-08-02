/** @vitest-environment jsdom */
import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

vi.mock('../../components/Navbar', () => ({ default: () => <div>Navbar</div> }));
vi.mock('howler', () => ({ Howl: vi.fn().mockImplementation(() => ({ play: vi.fn() })) }));
vi.mock('/sounds/stamp.mp3', () => ({ default: '' }), { virtual: true });
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
    const actual = await vi.importActual('react-router-dom');
    return { ...actual, useNavigate: () => mockNavigate, useLocation: () => ({ search: '' }) };
});

import { MemoryRouter } from 'react-router-dom';
import Login from '../../pages/Login.jsx';
import { AuthContext } from '../../context/AuthContext.jsx';
import { LanguageProvider } from '../../context/LanguageContext.jsx';

beforeEach(() => {
    vi.spyOn(global, 'fetch').mockResolvedValue({ ok: false, json: () => Promise.resolve({}) });
});

afterEach(() => {
    vi.restoreAllMocks();
});

describe('Login', () => {
    it('submits credentials and navigates after successful login', async () => {
        const loginMock = vi.fn().mockResolvedValue({ success: true, user: {} });
        render(
            <MemoryRouter>
                <LanguageProvider>
                    <AuthContext.Provider value={{ login: loginMock }}>
                        <Login />
                    </AuthContext.Provider>
                </LanguageProvider>
            </MemoryRouter>
        );

        await userEvent.type(screen.getByLabelText(/Benutzername/i), ' alice ');
        await userEvent.type(screen.getByLabelText(/Passwort/i), 'secret');
        await userEvent.click(screen.getByRole('button', { name: /Login/i }));

        expect(loginMock).toHaveBeenCalledWith('alice', 'secret');
        expect(mockNavigate).toHaveBeenCalledWith('/user', { replace: true });
    });
});
