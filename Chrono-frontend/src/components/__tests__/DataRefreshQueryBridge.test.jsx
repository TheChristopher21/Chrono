import { act, cleanup, render } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import DataRefreshQueryBridge, {
    shouldInvalidateQueryForRefresh,
} from '../DataRefreshQueryBridge.jsx';
import {
    publishDataRefresh,
    resetDataRefreshCoordinator,
} from '../../utils/dataRefresh.js';

describe('DataRefreshQueryBridge', () => {
    beforeEach(() => {
        vi.useFakeTimers();
        resetDataRefreshCoordinator();
    });

    afterEach(() => {
        cleanup();
        resetDataRefreshCoordinator();
        vi.useRealTimers();
    });

    it('matches query metadata by scope and only includes untagged queries globally', () => {
        expect(shouldInvalidateQueryForRefresh(
            { meta: { refreshScopes: ['pms'] } },
            ['pms'],
        )).toBe(true);
        expect(shouldInvalidateQueryForRefresh(
            { meta: { refreshScopes: ['pms'] } },
            ['crm'],
        )).toBe(false);
        expect(shouldInvalidateQueryForRefresh(
            { meta: { refreshScopes: ['global'] } },
            ['crm'],
        )).toBe(true);
        expect(shouldInvalidateQueryForRefresh({ meta: {} }, ['crm'])).toBe(false);
        expect(shouldInvalidateQueryForRefresh({ meta: {} }, ['global'])).toBe(true);
        expect(shouldInvalidateQueryForRefresh({}, ['global'])).toBe(true);
    });

    it('invalidates matching queries with active-only refetch after a coalesced event', async () => {
        const queryClient = new QueryClient({
            defaultOptions: { queries: { retry: false } },
        });
        const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue(undefined);

        render(
            <QueryClientProvider client={queryClient}>
                <DataRefreshQueryBridge />
            </QueryClientProvider>,
        );

        publishDataRefresh(['schedule']);
        await act(async () => {
            vi.advanceTimersByTime(80);
            await Promise.resolve();
        });

        expect(invalidateSpy).toHaveBeenCalledTimes(1);
        const options = invalidateSpy.mock.calls[0][0];
        expect(options.refetchType).toBe('active');
        expect(options.predicate({ meta: { refreshScopes: ['schedule'] } })).toBe(true);
        expect(options.predicate({ meta: { refreshScopes: ['pms'] } })).toBe(false);
        expect(options.predicate({ meta: {} })).toBe(false);
    });

    it('clears cached server data when the auth/company identity changes', () => {
        const queryClient = new QueryClient({
            defaultOptions: { queries: { retry: false } },
        });
        queryClient.setQueryData(['users'], [{ id: 1, username: 'old-company-user' }]);
        const clearSpy = vi.spyOn(queryClient, 'clear');

        const view = render(
            <QueryClientProvider client={queryClient}>
                <DataRefreshQueryBridge identity="company-1:user-1" />
            </QueryClientProvider>,
        );
        expect(clearSpy).not.toHaveBeenCalled();

        view.rerender(
            <QueryClientProvider client={queryClient}>
                <DataRefreshQueryBridge identity="company-2:user-2" />
            </QueryClientProvider>,
        );

        expect(clearSpy).toHaveBeenCalledTimes(1);
        expect(queryClient.getQueryData(['users'])).toBeUndefined();
    });
});
