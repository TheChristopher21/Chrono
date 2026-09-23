/** @vitest-environment jsdom */
import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, useNavigate } from 'react-router-dom';
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
const TAB_IDLE_SIGN_OUT_KEY = 'chrono:tabIdleSignOut';
const INACTIVITY_DURATION = 10 * 60 * 1000;

const AuthProbe = () => {
    const { authToken, currentUser, isAuthLoading, logout, login, fetchCurrentUser } = useAuth();
    const navigate = useNavigate();

    return (
        <div><div data-testid="auth-state" data-company={currentUser?.companyId}>
            {authToken ?? 'none'}:{currentUser?.username ?? 'no-user'}:{isAuthLoading ? 'loading' : 'idle'}
        </div>
            <button onClick={() => navigate('/pms')}>PMS</button>
            <button onClick={() => navigate('/dashboard')}>Chrono</button>
            <button onClick={logout}>Logout</button>
            <button onClick={() => login('alice', 'test-password')}>Login</button>
            <button onClick={() => fetchCurrentUser()}>Reload user</button>
        </div>
    );
};

const renderAuthProvider = (path = '/dashboard') => render(
    <MemoryRouter initialEntries={[path]}>
        <AuthProvider>
            <AuthProbe />
        </AuthProvider>
    </MemoryRouter>
);

const pmsUser = { username: 'alice', companyFeatureKeys: ['pms'], pagePermissions: { pms: 'VIEW' } };
const tokenWithExpiry = (expiresInMillis, issuedAgoMillis = 0, subject = 'alice') => `header.${btoa(JSON.stringify({
    exp: Math.floor((Date.now() + expiresInMillis) / 1000),
    iat: Math.floor((Date.now() - issuedAgoMillis) / 1000),
    sub: subject,
}))}.signature`;

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

    it('rejects an idle restored Chrono session without deleting another browser tab\'s token', () => {
        const now = 1_700_000_000_000;
        vi.useFakeTimers();
        vi.setSystemTime(now);
        localStorage.setItem('token', 'old-token');
        localStorage.setItem(LAST_ACTIVITY_STORAGE_KEY, String(now - INACTIVITY_DURATION - 1));

        renderAuthProvider();

        expect(screen.getByTestId('auth-state')).toHaveTextContent('none:no-user:idle');
        expect(localStorage.getItem('token')).toBe('old-token');
        expect(sessionStorage.getItem(TAB_IDLE_SIGN_OUT_KEY)).toBe('true');
        expect(apiMock.get).not.toHaveBeenCalled();
    });

    it('requires login for legacy Chrono sessions without an activity timestamp', () => {
        localStorage.setItem('token', 'legacy-token');

        renderAuthProvider();

        expect(screen.getByTestId('auth-state')).toHaveTextContent('none:no-user:idle');
        expect(localStorage.getItem('token')).toBe('legacy-token');
        expect(sessionStorage.getItem(TAB_IDLE_SIGN_OUT_KEY)).toBe('true');
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

    it.each(['/pms', '/pms/rooms'])('restores idle PMS sessions at %s and keeps them logged in without input', async (path) => {
        vi.useFakeTimers();
        localStorage.setItem('token', 'pms-token');
        localStorage.setItem(LAST_ACTIVITY_STORAGE_KEY, String(Date.now() - INACTIVITY_DURATION * 3));
        apiMock.get.mockResolvedValue({ data: pmsUser });
        renderAuthProvider(path);

        await act(async () => { await vi.advanceTimersByTimeAsync(INACTIVITY_DURATION * 6); });
        fireEvent.click(screen.getByText('Reload user'));
        await act(async () => { await Promise.resolve(); });

        expect(screen.getByTestId('auth-state')).toHaveTextContent('pms-token:alice:idle');
        expect(localStorage.getItem('token')).toBe('pms-token');
        expect(notifyMock).not.toHaveBeenCalled();
    });

    it.each(['/book/hotel', '/guest-check-in/token', '/pms-public'])('does not exempt public or similarly named route %s', (path) => {
        localStorage.setItem('token', 'old-token');
        localStorage.setItem(LAST_ACTIVITY_STORAGE_KEY, String(Date.now() - INACTIVITY_DURATION - 1));
        renderAuthProvider(path);
        expect(screen.getByTestId('auth-state')).toHaveTextContent('none:no-user:idle');
        expect(sessionStorage.getItem(TAB_IDLE_SIGN_OUT_KEY)).toBe('true');
    });

    it('disables the existing timer on PMS entry and starts ten fresh minutes on return to Chrono', async () => {
        vi.useFakeTimers();
        localStorage.setItem('token', 'pms-token');
        localStorage.setItem(LAST_ACTIVITY_STORAGE_KEY, String(Date.now()));
        apiMock.get.mockResolvedValue({ data: pmsUser });
        renderAuthProvider();
        await act(async () => { await vi.advanceTimersByTimeAsync(9 * 60_000); });
        fireEvent.click(screen.getByText('PMS'));
        await act(async () => { await vi.advanceTimersByTimeAsync(60 * 60_000); });
        expect(localStorage.getItem('token')).toBe('pms-token');

        fireEvent.click(screen.getByText('Chrono'));
        await act(async () => { await vi.advanceTimersByTimeAsync(INACTIVITY_DURATION - 1); });
        expect(localStorage.getItem('token')).toBe('pms-token');
        await act(async () => { await vi.advanceTimersByTimeAsync(1); });
        expect(screen.getByTestId('auth-state')).toHaveTextContent('none:no-user:idle');
        expect(sessionStorage.getItem(TAB_IDLE_SIGN_OUT_KEY)).toBe('true');
        expect(notifyMock).toHaveBeenCalledWith('sessionExpired');
    });

    it('does not exempt a user without PMS access', async () => {
        vi.useFakeTimers();
        localStorage.setItem('token', 'not-pms-token');
        localStorage.setItem(LAST_ACTIVITY_STORAGE_KEY, String(Date.now()));
        apiMock.get.mockResolvedValue({ data: { username: 'alice' } });
        renderAuthProvider('/pms');
        await act(async () => { await vi.advanceTimersByTimeAsync(INACTIVITY_DURATION); });
        expect(screen.getByTestId('auth-state')).toHaveTextContent('none:no-user:idle');
        expect(apiMock.post).not.toHaveBeenCalled();
    });

    it('renews a finite PMS token before server expiry without marking Chrono activity', async () => {
        vi.useFakeTimers();
        const token = tokenWithExpiry(6 * 60_000, 23 * 60 * 60_000);
        const renewed = tokenWithExpiry(24 * 60 * 60_000);
        const oldActivity = String(Date.now() - 2 * INACTIVITY_DURATION);
        localStorage.setItem('token', token);
        localStorage.setItem(LAST_ACTIVITY_STORAGE_KEY, oldActivity);
        apiMock.get.mockResolvedValue({ data: pmsUser });
        apiMock.post.mockResolvedValue({ data: { token: renewed } });
        renderAuthProvider('/pms');
        await act(async () => { await vi.advanceTimersByTimeAsync(60_000); });

        expect(apiMock.post).toHaveBeenCalledWith('/api/pms/session/refresh', {}, { timeout: 10_000, skipDataRefresh: true });
        expect(localStorage.getItem('token')).toBe(renewed);
        expect(localStorage.getItem(LAST_ACTIVITY_STORAGE_KEY)).toBe(oldActivity);
        expect(apiMock.defaults.headers.common.Authorization).toBe(`Bearer ${renewed}`);
    });

    it('never renews tokens in Chrono', async () => {
        vi.useFakeTimers();
        localStorage.setItem('token', tokenWithExpiry(60_000, 23 * 60 * 60_000));
        localStorage.setItem(LAST_ACTIVITY_STORAGE_KEY, String(Date.now()));
        apiMock.get.mockResolvedValue({ data: pmsUser });
        renderAuthProvider();
        await act(async () => { await vi.advanceTimersByTimeAsync(2 * 60_000); });
        expect(apiMock.post).not.toHaveBeenCalled();
    });

    it('retries temporary renewal failures without signing the PMS out', async () => {
        vi.useFakeTimers();
        const token = tokenWithExpiry(4 * 60_000, 23 * 60 * 60_000);
        const renewed = tokenWithExpiry(24 * 60 * 60_000);
        localStorage.setItem('token', token);
        apiMock.get.mockResolvedValue({ data: pmsUser });
        apiMock.post.mockRejectedValueOnce(new Error('Network unavailable'))
            .mockResolvedValue({ data: { token: renewed } });
        renderAuthProvider('/pms');
        await act(async () => { await Promise.resolve(); });
        expect(localStorage.getItem('token')).toBe(token);
        await act(async () => { await vi.advanceTimersByTimeAsync(30_000); });
        expect(localStorage.getItem('token')).toBe(renewed);
        expect(notifyMock).not.toHaveBeenCalled();
    });

    it.each([401, 403])('honors server rejection %s instead of extending a revoked PMS session', async (status) => {
        localStorage.setItem('token', tokenWithExpiry(60_000, 23 * 60 * 60_000));
        apiMock.get.mockResolvedValue({ data: pmsUser });
        apiMock.post.mockRejectedValue({ response: { status } });
        renderAuthProvider('/pms');
        await waitFor(() => expect(localStorage.getItem('token')).toBeNull());
        expect(screen.getByTestId('auth-state')).toHaveTextContent('none:no-user:idle');
    });

    it.each(['Logout', 'Chrono'])('ignores a pending renewal after %s', async (action) => {
        vi.useFakeTimers();
        let resolveRefresh;
        const token = tokenWithExpiry(60_000, 23 * 60 * 60_000);
        localStorage.setItem('token', token);
        localStorage.setItem(LAST_ACTIVITY_STORAGE_KEY, String(Date.now()));
        apiMock.get.mockResolvedValue({ data: pmsUser });
        apiMock.post.mockImplementation(() => new Promise((resolve) => { resolveRefresh = resolve; }));
        renderAuthProvider('/pms');
        await act(async () => { await Promise.resolve(); });
        fireEvent.click(screen.getByText(action));
        await act(async () => { resolveRefresh({ data: { token: 'late-token' } }); });
        expect(localStorage.getItem('token')).toBe(action === 'Logout' ? null : token);
    });

    it('honors logout from another browser tab while PMS is idle', async () => {
        vi.useFakeTimers();
        localStorage.setItem('token', 'pms-token');
        apiMock.get.mockResolvedValue({ data: pmsUser });
        renderAuthProvider('/pms');
        await act(async () => { await Promise.resolve(); });
        act(() => {
            localStorage.removeItem('token');
            window.dispatchEvent(new StorageEvent('storage', { key: 'token', newValue: null }));
        });
        expect(screen.getByTestId('auth-state')).toHaveTextContent('none:no-user:idle');
    });

    it('keeps shared PMS credentials usable while an idle Chrono tab stays signed out after reload', async () => {
        vi.useFakeTimers();
        localStorage.setItem('token', 'shared-token');
        localStorage.setItem(LAST_ACTIVITY_STORAGE_KEY, String(Date.now()));
        apiMock.get.mockResolvedValue({ data: pmsUser });
        const chronoTab = renderAuthProvider();
        await act(async () => { await vi.advanceTimersByTimeAsync(INACTIVITY_DURATION); });
        expect(screen.getByTestId('auth-state')).toHaveTextContent('none:no-user:idle');
        expect(localStorage.getItem('token')).toBe('shared-token');
        chronoTab.unmount();

        const reloadedChrono = renderAuthProvider();
        await act(async () => { await Promise.resolve(); });
        expect(screen.getByTestId('auth-state')).toHaveTextContent('none:no-user:idle');
        expect(apiMock.get).toHaveBeenCalledTimes(1);
        reloadedChrono.unmount();

        // A separate browser tab has independent sessionStorage and shares localStorage.
        sessionStorage.clear();
        renderAuthProvider('/pms');
        await act(async () => { await vi.advanceTimersByTimeAsync(INACTIVITY_DURATION * 2); });
        expect(screen.getByTestId('auth-state')).toHaveTextContent('shared-token:alice:idle');
        expect(localStorage.getItem('token')).toBe('shared-token');
    });

    it('does not extend Chrono inactivity when a different browser tab publishes activity', async () => {
        vi.useFakeTimers();
        localStorage.setItem('token', 'shared-token');
        localStorage.setItem(LAST_ACTIVITY_STORAGE_KEY, String(Date.now()));
        apiMock.get.mockResolvedValue({ data: pmsUser });
        renderAuthProvider();
        await act(async () => { await vi.advanceTimersByTimeAsync(9 * 60_000); });
        act(() => {
            localStorage.setItem(LAST_ACTIVITY_STORAGE_KEY, String(Date.now()));
            window.dispatchEvent(new StorageEvent('storage', { key: LAST_ACTIVITY_STORAGE_KEY, newValue: String(Date.now()) }));
        });
        await act(async () => { await vi.advanceTimersByTimeAsync(60_000); });
        expect(screen.getByTestId('auth-state')).toHaveTextContent('none:no-user:idle');
        expect(localStorage.getItem('token')).toBe('shared-token');
    });

    it('allows a fresh login to clear the tab-specific inactivity sign-out', async () => {
        sessionStorage.setItem(TAB_IDLE_SIGN_OUT_KEY, 'true');
        localStorage.setItem('token', 'pms-shared-token');
        apiMock.post.mockResolvedValue({ data: { token: 'new-login-token' } });
        apiMock.get.mockResolvedValue({ data: pmsUser });
        renderAuthProvider();
        expect(screen.getByTestId('auth-state')).toHaveTextContent('none:no-user:idle');

        fireEvent.click(screen.getByText('Login'));
        await waitFor(() => expect(screen.getByTestId('auth-state')).toHaveTextContent('new-login-token:alice:idle'));
        expect(sessionStorage.getItem(TAB_IDLE_SIGN_OUT_KEY)).toBeNull();
    });

    it('signs out this tab when another browser tab switches account, retaining the new shared login', async () => {
        const aliceToken = tokenWithExpiry(24 * 60 * 60_000);
        const bobToken = tokenWithExpiry(24 * 60 * 60_000, 0, 'bob');
        localStorage.setItem('token', aliceToken);
        apiMock.get.mockResolvedValue({ data: pmsUser });
        renderAuthProvider('/pms');
        await waitFor(() => expect(screen.getByTestId('auth-state')).toHaveTextContent(':alice:idle'));

        act(() => {
            localStorage.setItem('token', bobToken);
            window.dispatchEvent(new StorageEvent('storage', { key: 'token', newValue: bobToken }));
        });
        expect(screen.getByTestId('auth-state')).toHaveTextContent('none:no-user:idle');
        expect(sessionStorage.getItem(TAB_IDLE_SIGN_OUT_KEY)).toBe('true');
        expect(apiMock.defaults.headers.common.Authorization).toBeUndefined();
        expect(localStorage.getItem('token')).toBe(bobToken);
    });

    it('accepts a same-user renewal from another tab and reloads the current profile', async () => {
        const token = tokenWithExpiry(12 * 60 * 60_000);
        const renewed = tokenWithExpiry(24 * 60 * 60_000);
        localStorage.setItem('token', token);
        apiMock.get.mockResolvedValueOnce({ data: pmsUser }).mockResolvedValue({ data: { ...pmsUser, companyId: 42 } });
        renderAuthProvider('/pms');
        await waitFor(() => expect(screen.getByTestId('auth-state')).toHaveTextContent(':alice:idle'));

        act(() => {
            localStorage.setItem('token', renewed);
            window.dispatchEvent(new StorageEvent('storage', { key: 'token', newValue: renewed }));
        });
        await waitFor(() => expect(apiMock.get).toHaveBeenCalledTimes(2));
        expect(screen.getByTestId('auth-state')).toHaveTextContent(`${renewed}:alice:idle`);
        expect(screen.getByTestId('auth-state')).toHaveAttribute('data-company', '42');
        expect(sessionStorage.getItem(TAB_IDLE_SIGN_OUT_KEY)).toBeNull();
        expect(apiMock.defaults.headers.common.Authorization).toBe(`Bearer ${renewed}`);
    });
});
