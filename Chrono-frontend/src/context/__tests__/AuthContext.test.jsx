/** @vitest-environment jsdom */
import React from 'react';
import { act, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const apiMock = vi.hoisted(() => ({
    defaults: { headers: { common: {} } },
    get: vi.fn(),
    post: vi.fn(),
}));

const notifyMock = vi.hoisted(() => vi.fn());

vi.mock('../../utils/api', () => ({ default: apiMock }));
vi.mock('../NotificationContext.jsx', () => ({
    useNotification: () => ({ notify: notifyMock }),
}));
vi.mock('../LanguageContext.jsx', () => ({
    useTranslation: () => ({ t: (key) => key }),
}));

import { AuthProvider, useAuth } from '../AuthContext.jsx';

const LAST_ACTIVITY_STORAGE_KEY = 'lastActivityAt';
const INACTIVITY_DURATION = 10 * 60 * 1000;

const AuthProbe = () => {
    const { authToken, currentUser, isAuthLoading } = useAuth();

    return (
        <div data-testid="auth-state">
            {authToken ?? 'none'}:{currentUser?.username ?? 'no-user'}:{isAuthLoading ? 'loading' : 'idle'}
        </div>
    );
};

const renderAuthProvider = () => render(
    <AuthProvider>
        <AuthProbe />
    </AuthProvider>
);

describe('AuthProvider inactivity restore', () => {
    beforeEach(() => {
        localStorage.clear();
        sessionStorage.clear();
        apiMock.defaults.headers.common = {};
        apiMock.get.mockReset();
        apiMock.post.mockReset();
        notifyMock.mockClear();
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it('clears a restored token when the last activity is older than the inactivity duration', () => {
        const now = 1_700_000_000_000;
        vi.useFakeTimers();
        vi.setSystemTime(now);
        localStorage.setItem('token', 'old-token');
        localStorage.setItem(LAST_ACTIVITY_STORAGE_KEY, String(now - INACTIVITY_DURATION - 1));

        renderAuthProvider();

        expect(screen.getByTestId('auth-state')).toHaveTextContent('none:no-user:idle');
        expect(localStorage.getItem('token')).toBeNull();
        expect(localStorage.getItem(LAST_ACTIVITY_STORAGE_KEY)).toBeNull();
        expect(apiMock.get).not.toHaveBeenCalled();
    });

    it('clears legacy restored tokens without an activity timestamp', () => {
        localStorage.setItem('token', 'legacy-token');

        renderAuthProvider();

        expect(screen.getByTestId('auth-state')).toHaveTextContent('none:no-user:idle');
        expect(localStorage.getItem('token')).toBeNull();
        expect(apiMock.get).not.toHaveBeenCalled();
    });

    it('keeps a restored token when the last activity is still current', async () => {
        const now = Date.now();
        localStorage.setItem('token', 'fresh-token');
        localStorage.setItem(LAST_ACTIVITY_STORAGE_KEY, String(now - 1000));
        apiMock.get.mockResolvedValue({ data: { username: 'alice' } });

        renderAuthProvider();

        await waitFor(() => {
            expect(screen.getByTestId('auth-state')).toHaveTextContent('fresh-token:alice:idle');
        });
        expect(apiMock.get).toHaveBeenCalledWith('/api/auth/me');
        expect(apiMock.defaults.headers.common.Authorization).toBe('Bearer fresh-token');
    });

    it('does not poll the current user while an active session stays open', async () => {
        const now = 1_700_000_000_000;
        vi.useFakeTimers();
        vi.setSystemTime(now);
        localStorage.setItem('token', 'fresh-token');
        localStorage.setItem(LAST_ACTIVITY_STORAGE_KEY, String(now));
        apiMock.get.mockResolvedValue({ data: { username: 'alice' } });

        renderAuthProvider();

        await act(async () => {
            await Promise.resolve();
        });
        expect(apiMock.get).toHaveBeenCalledTimes(1);

        for (let i = 0; i < 4; i += 1) {
            await act(async () => {
                await vi.advanceTimersByTimeAsync(5 * 60 * 1000);
                window.dispatchEvent(new Event('click'));
            });
        }

        expect(localStorage.getItem('token')).toBe('fresh-token');
        expect(apiMock.get).toHaveBeenCalledTimes(1);
    });
});
