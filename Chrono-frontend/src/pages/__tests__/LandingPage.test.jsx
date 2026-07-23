/** @vitest-environment jsdom */
import React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';

const mocks = vi.hoisted(() => ({
    loginDemo: vi.fn(),
    navigate: vi.fn(),
    notify: vi.fn(),
}));

vi.mock('../../components/Navbar.jsx', () => ({ default: () => <nav aria-label="Test navigation" /> }));
vi.mock('../../context/AuthContext.jsx', () => ({
    useAuth: () => ({ loginDemo: mocks.loginDemo }),
}));
vi.mock('../../context/NotificationContext.jsx', () => ({
    useNotification: () => ({ notify: mocks.notify }),
}));
vi.mock('../../utils/api', () => ({
    default: { post: vi.fn().mockResolvedValue({}) },
}));
vi.mock('react-router-dom', async () => {
    const actual = await vi.importActual('react-router-dom');
    return { ...actual, useNavigate: () => mocks.navigate };
});

import LandingPage from '../LandingPage.jsx';
import { LanguageProvider } from '../../context/LanguageContext.jsx';

const renderLandingPage = () => render(
    <MemoryRouter initialEntries={['/']}>
        <LanguageProvider>
            <LandingPage />
        </LanguageProvider>
    </MemoryRouter>
);

const getHero = (container) => {
    const hero = container.querySelector('.lp-hero');
    expect(hero).not.toBeNull();
    return within(hero);
};

beforeEach(() => {
    mocks.loginDemo.mockReset();
    mocks.navigate.mockReset();
    mocks.notify.mockReset();
});

describe('LandingPage conversion entry points', () => {
    it('presents the problem-led hero with honest CTA labels', () => {
        const { container } = renderLandingPage();
        const hero = getHero(container);

        expect(hero.getByRole('heading', {
            level: 1,
            name: 'Arbeitszeit, Abwesenheiten und Lohnprozesse – ohne Excel-Chaos.',
        })).toBeInTheDocument();
        expect(hero.getByRole('link', { name: 'Kostenlose Demo anfragen' }))
            .toHaveAttribute('href', '/register');
        expect(hero.getByRole('button', { name: 'Interaktive Produktdemo starten' }))
            .toBeEnabled();
        expect(screen.queryByText('Kostenlos testen', { exact: true })).not.toBeInTheDocument();
    });

    it('shows a pending demo state and navigates after a successful demo login', async () => {
        const user = userEvent.setup();
        let resolveDemo;
        mocks.loginDemo.mockReturnValue(new Promise((resolve) => {
            resolveDemo = resolve;
        }));
        const { container } = renderLandingPage();
        const demoButton = getHero(container).getByRole('button', {
            name: 'Interaktive Produktdemo starten',
        });

        await user.click(demoButton);

        expect(mocks.loginDemo).toHaveBeenCalledTimes(1);
        expect(demoButton).toBeDisabled();

        resolveDemo({ success: true });

        await waitFor(() => {
            expect(mocks.navigate).toHaveBeenCalledWith('/demo-tour', { replace: true });
        });
    });

    it('notifies the visitor when the interactive demo cannot be started', async () => {
        const user = userEvent.setup();
        mocks.loginDemo.mockResolvedValue({
            success: false,
            message: 'Die Produktdemo ist momentan nicht verfügbar.',
        });
        const { container } = renderLandingPage();

        await user.click(getHero(container).getByRole('button', {
            name: 'Interaktive Produktdemo starten',
        }));

        await waitFor(() => {
            expect(mocks.notify).toHaveBeenCalledWith(
                'Die Produktdemo ist momentan nicht verfügbar.',
                'error'
            );
        });
        expect(mocks.navigate).not.toHaveBeenCalled();
    });
});
