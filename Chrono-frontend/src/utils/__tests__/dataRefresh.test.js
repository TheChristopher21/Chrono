import {
    classifyMutationRequest,
    normalizeRefreshScopes,
    publishDataRefresh,
    REFRESH_SCOPES,
    refreshScopesMatch,
    resetDataRefreshCoordinator,
    subscribeDataRefresh,
} from '../dataRefresh.js';

describe('dataRefresh mutation classification', () => {
    it.each([
        ['/api/timetracking/punch', 'post', ['time']],
        ['/api/admin/timetracking/editDay/alex/2026-09-01', 'put', ['time']],
        ['/api/correction/approve/3', 'post', ['requests', 'time']],
        ['/api/vacation/7', 'put', ['absence', 'time', 'schedule']],
        ['/api/sick-leave/7', 'delete', ['absence', 'time', 'schedule']],
        ['/api/admin/users', 'post', ['people', 'schedule']],
        ['/api/customers/2', 'patch', ['customers', 'projects', 'time']],
        ['/api/accounting/journal', 'post', ['accounting', 'banking']],
        ['/api/banking/batches', 'post', ['banking', 'accounting']],
        ['/api/pms/reservations', 'post', ['pms']],
        ['/api/supply-chain/products', 'post', ['supplyChain']],
        ['/chrono2/products', 'post', ['chrono2']],
        ['/api/not-yet-mapped/resource', 'patch', ['global']],
    ])('maps %s %s to its refresh scopes', (url, method, expected) => {
        expect(classifyMutationRequest({ url, method })).toEqual(expected);
    });

    it.each([
        ['/api/auth/login', 'post'],
        ['/api/auth/logout', 'delete'],
        ['/api/ui/preferences/APP_TABS', 'put'],
        ['/api/public/analytics/events', 'post'],
        ['/api/chat', 'post'],
        ['/api/contact', 'post'],
        ['/api/apply', 'post'],
        ['/api/nfc/command', 'post'],
        ['/api/public/pms/booking/HOTEL/verify', 'post'],
        ['/api/supply-chain/receiving/preview', 'post'],
        ['/api/supply-chain/receiving/document-preview', 'post'],
        ['/chrono2/analytics/nlp', 'post'],
    ])('does not publish for technical/read-like mutation %s', (url, method) => {
        expect(classifyMutationRequest({ url, method })).toEqual([]);
    });

    it('ignores reads and supports explicit opt-out and explicit scopes', () => {
        expect(classifyMutationRequest({ url: '/api/vacation/all', method: 'get' })).toEqual([]);
        expect(classifyMutationRequest({
            url: '/api/vacation/1',
            method: 'put',
            dataRefresh: false,
        })).toEqual([]);
        expect(classifyMutationRequest({
            url: '/api/not-yet-mapped',
            method: 'post',
            dataRefresh: { scopes: ['pms'] },
        })).toEqual(['pms']);
    });

    it('normalizes legacy aliases while keeping matching exact', () => {
        expect(normalizeRefreshScopes(['timetracking', 'vacations', 'users', 'pms', 'pms']))
            .toEqual(['time', 'absence', 'people', 'pms']);
        expect(refreshScopesMatch(['pms'], ['crm'])).toBe(false);
        expect(refreshScopesMatch(['pms'], ['global'])).toBe(true);
        expect(refreshScopesMatch(['global'], ['crm'])).toBe(true);
    });
});

describe('dataRefresh event coordinator', () => {
    beforeEach(() => {
        vi.useFakeTimers();
        resetDataRefreshCoordinator();
    });

    afterEach(() => {
        resetDataRefreshCoordinator();
        vi.useRealTimers();
    });

    it('coalesces mutation events and merges scopes within about 80 ms', () => {
        const listener = vi.fn();
        const unsubscribe = subscribeDataRefresh(listener);

        publishDataRefresh(['time'], { method: 'post', url: '/api/timetracking/punch' });
        vi.advanceTimersByTime(40);
        publishDataRefresh(['absence'], { method: 'put', url: '/api/vacation/1' });

        vi.advanceTimersByTime(39);
        expect(listener).not.toHaveBeenCalled();
        vi.advanceTimersByTime(1);

        expect(listener).toHaveBeenCalledTimes(1);
        expect(listener.mock.calls[0][0].scopes).toEqual(['time', 'absence']);
        expect(listener.mock.calls[0][0].changes).toHaveLength(2);
        unsubscribe();
    });

    it('does not enqueue invalid scopes', () => {
        const listener = vi.fn();
        subscribeDataRefresh(listener);

        expect(publishDataRefresh(['not-a-scope'])).toBe(false);
        vi.runAllTimers();
        expect(listener).not.toHaveBeenCalled();
    });

    it('broadcasts one coalesced message when BroadcastChannel is available', () => {
        const originalBroadcastChannel = globalThis.BroadcastChannel;
        const postMessage = vi.fn();
        const close = vi.fn();
        class FakeBroadcastChannel {
            constructor() {
                this.onmessage = null;
            }

            postMessage = postMessage;
            close = close;
        }
        globalThis.BroadcastChannel = FakeBroadcastChannel;

        try {
            publishDataRefresh([REFRESH_SCOPES.PMS]);
            publishDataRefresh([REFRESH_SCOPES.CRM]);
            vi.advanceTimersByTime(80);

            expect(postMessage).toHaveBeenCalledTimes(1);
            expect(postMessage.mock.calls[0][0].scopes).toEqual(['pms', 'crm']);
        } finally {
            resetDataRefreshCoordinator();
            globalThis.BroadcastChannel = originalBroadcastChannel;
        }
    });
});

