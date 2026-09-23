/** @vitest-environment jsdom */
import React, { Activity } from 'react';
import { act, fireEvent, render, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
    auth: {},
    api: { get: vi.fn(), put: vi.fn(), defaults: { baseURL: 'https://chrono.example' } },
    notify: vi.fn(),
}));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => mocks.auth }));
vi.mock('../../context/LanguageContext', () => ({ useTranslation: () => ({ t: (key, fallback) => fallback || key }) }));
vi.mock('../../context/NotificationContext', () => ({ useNotification: () => ({ notify: mocks.notify }) }));
vi.mock('../../utils/api', () => ({ default: mocks.api }));
vi.mock('../../components/Navbar', () => ({ default: () => null }));
vi.mock('../../components/CalendarExportModal', () => ({ default: () => null }));

import PersonalDataPage from '../PersonalDataPage.jsx';

const profile = { id: 1, companyId: 10, username: 'alice', firstName: 'Tab', lastName: 'User', email: 'alice@example.com' };
const view = (mode = 'visible') => <Activity mode={mode}><PersonalDataPage /></Activity>;
const profileCalls = () => mocks.api.get.mock.calls.filter(([url]) => url === '/api/auth/me');
const deferred = () => {
    let resolve;
    const promise = new Promise((done) => { resolve = done; });
    return { promise, resolve };
};

beforeEach(() => {
    mocks.auth = { currentUser: profile, setCurrentUser: vi.fn() };
    mocks.notify.mockReset();
    mocks.api.get.mockReset().mockImplementation((url) => Promise.resolve({ data: url === '/api/auth/me' ? profile : { token: 'feed-token' } }));
    mocks.api.put.mockReset();
});

describe('PersonalDataPage in cached workspace tabs', () => {
    it('keeps unsaved edits on Activity reactivation and same-user profile refresh', async () => {
        const { container, rerender } = render(view());
        const firstName = () => container.querySelector('input[name="firstName"]');
        await waitFor(() => expect(firstName()).toHaveValue('Tab'));
        fireEvent.change(firstName(), { target: { value: 'ErsterEntwurf' } });
        rerender(view('hidden'));
        mocks.auth.currentUser = { ...profile, address: 'Updated elsewhere' };
        rerender(view());
        expect(firstName()).toHaveValue('ErsterEntwurf');
        expect(profileCalls()).toHaveLength(1);
    });

    it('finishes an in-flight hydration while hidden without overwriting already edited fields', async () => {
        const pending = deferred();
        mocks.api.get.mockImplementation((url) => url === '/api/auth/me' ? pending.promise : Promise.resolve({ data: {} }));
        const { container, rerender } = render(view());
        const firstName = () => container.querySelector('input[name="firstName"]');
        fireEvent.change(firstName(), { target: { value: 'Pending draft' } });
        const signal = profileCalls()[0][1].signal;
        rerender(view('hidden'));
        expect(signal.aborted).toBe(false);
        await act(async () => pending.resolve({ data: { ...profile, address: 'Fetched address' } }));
        rerender(view());
        expect(firstName()).toHaveValue('Pending draft');
        expect(container.querySelector('input[name="address"]')).toHaveValue('Fetched address');
        expect(profileCalls()).toHaveLength(1);
    });

    it('aborts the old identity request and rejects a late response after a user change', async () => {
        const oldRequest = deferred();
        const newRequest = deferred();
        const requests = [oldRequest, newRequest];
        mocks.api.get.mockImplementation((url) => url === '/api/auth/me' ? requests.shift().promise : Promise.resolve({ data: {} }));
        const { container, rerender } = render(view());
        const oldSignal = profileCalls()[0][1].signal;
        mocks.auth.currentUser = { ...profile, id: 2, username: 'bob', firstName: 'Bob' };
        rerender(view());
        expect(oldSignal.aborted).toBe(true);
        await act(async () => newRequest.resolve({ data: { ...mocks.auth.currentUser, firstName: 'Bob server' } }));
        await act(async () => oldRequest.resolve({ data: { ...profile, firstName: 'Stale Alice' } }));
        expect(container.querySelector('input[name="firstName"]')).toHaveValue('Bob server');
        expect(profileCalls()).toHaveLength(2);
    });
});
