import api from '../api.js';
import {
    resetDataRefreshCoordinator,
    subscribeDataRefresh,
} from '../dataRefresh.js';

const successfulAdapter = async (config) => ({
    data: {},
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
});

describe('api data refresh interceptor', () => {
    beforeEach(() => {
        vi.useFakeTimers();
        localStorage.clear();
        sessionStorage.clear();
        resetDataRefreshCoordinator();
    });

    afterEach(() => {
        resetDataRefreshCoordinator();
        vi.useRealTimers();
    });

    it('publishes scopes only after a successful mutation response', async () => {
        const listener = vi.fn();
        subscribeDataRefresh(listener);

        await api.request({
            method: 'put',
            url: '/api/vacation/9',
            data: {},
            adapter: successfulAdapter,
        });
        vi.advanceTimersByTime(80);

        expect(listener).toHaveBeenCalledTimes(1);
        expect(listener.mock.calls[0][0].scopes).toEqual(['absence', 'time', 'schedule']);
    });

    it('does not publish for a failed mutation', async () => {
        const listener = vi.fn();
        subscribeDataRefresh(listener);

        await expect(api.request({
            method: 'delete',
            url: '/api/projects/5',
            adapter: async () => Promise.reject(new Error('request failed')),
        })).rejects.toThrow('request failed');
        vi.runAllTimers();

        expect(listener).not.toHaveBeenCalled();
    });

    it('does not publish for GET or technical POST responses', async () => {
        const listener = vi.fn();
        subscribeDataRefresh(listener);

        await api.request({ method: 'get', url: '/api/vacation/all', adapter: successfulAdapter });
        await api.request({ method: 'post', url: '/api/chat', data: {}, adapter: successfulAdapter });
        vi.runAllTimers();

        expect(listener).not.toHaveBeenCalled();
    });

    it('does not send shared PMS credentials from an idle-signed-out Chrono tab', async () => {
        localStorage.setItem('token', 'pms-shared-token');
        sessionStorage.setItem('chrono:tabIdleSignOut', 'true');
        const response = await api.request({
            method: 'get', url: '/api/auth/me',
            headers: { Authorization: 'Bearer stale-default-token' }, adapter: successfulAdapter,
        });
        expect(response.config.headers.get('Authorization')).toBeUndefined();
        expect(localStorage.getItem('token')).toBe('pms-shared-token');
    });

    it('uses the latest shared finite token in a signed-in tab', async () => {
        localStorage.setItem('token', 'renewed-pms-token');
        const response = await api.request({ method: 'get', url: '/api/auth/me', adapter: successfulAdapter });
        expect(response.config.headers.get('Authorization')).toBe('Bearer renewed-pms-token');
    });
});
