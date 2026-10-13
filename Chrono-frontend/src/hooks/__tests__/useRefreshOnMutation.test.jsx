import { act, cleanup, renderHook } from '@testing-library/react';
import { useRefreshOnMutation } from '../useRefreshOnMutation.js';
import {
    publishDataRefresh,
    resetDataRefreshCoordinator,
} from '../../utils/dataRefresh.js';

const deferred = () => {
    let resolve;
    let reject;
    const promise = new Promise((resolvePromise, rejectPromise) => {
        resolve = resolvePromise;
        reject = rejectPromise;
    });
    return { promise, resolve, reject };
};

const flushCoordinator = async (milliseconds = 80) => {
    await act(async () => {
        vi.advanceTimersByTime(milliseconds);
        await Promise.resolve();
    });
};

describe('useRefreshOnMutation', () => {
    let visibilityState;
    let visibilitySpy;

    beforeEach(() => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date('2026-09-01T10:00:00Z'));
        resetDataRefreshCoordinator();
        visibilityState = 'visible';
        visibilitySpy = vi.spyOn(document, 'visibilityState', 'get')
            .mockImplementation(() => visibilityState);
    });

    afterEach(() => {
        cleanup();
        resetDataRefreshCoordinator();
        visibilitySpy.mockRestore();
        vi.useRealTimers();
    });

    it('runs only for a matching scope and uses global as a wildcard', async () => {
        const loader = vi.fn().mockResolvedValue(undefined);
        renderHook(() => useRefreshOnMutation(['pms'], loader));

        publishDataRefresh(['crm']);
        await flushCoordinator();
        expect(loader).not.toHaveBeenCalled();

        publishDataRefresh(['pms']);
        await flushCoordinator();
        expect(loader).toHaveBeenCalledTimes(1);

        publishDataRefresh(['global']);
        await flushCoordinator();
        expect(loader).toHaveBeenCalledTimes(2);
    });

    it('never runs loaders in parallel and performs exactly one trailing refresh', async () => {
        const firstRun = deferred();
        const loader = vi.fn()
            .mockImplementationOnce(() => firstRun.promise)
            .mockResolvedValue(undefined);
        renderHook(() => useRefreshOnMutation(['time'], loader));

        publishDataRefresh(['time']);
        await flushCoordinator();
        expect(loader).toHaveBeenCalledTimes(1);

        publishDataRefresh(['time']);
        publishDataRefresh(['time']);
        await flushCoordinator();
        expect(loader).toHaveBeenCalledTimes(1);

        await act(async () => {
            firstRun.resolve();
            await firstRun.promise;
            await Promise.resolve();
        });
        expect(loader).toHaveBeenCalledTimes(2);
    });

    it('defers any number of hidden-tab mutation events until visibility returns', async () => {
        const loader = vi.fn().mockResolvedValue(undefined);
        renderHook(() => useRefreshOnMutation(['absence'], loader));
        visibilityState = 'hidden';

        publishDataRefresh(['absence']);
        await flushCoordinator();
        publishDataRefresh(['absence']);
        await flushCoordinator();
        expect(loader).not.toHaveBeenCalled();

        visibilityState = 'visible';
        await act(async () => {
            document.dispatchEvent(new Event('visibilitychange'));
            await Promise.resolve();
        });
        expect(loader).toHaveBeenCalledTimes(1);
    });

    it('optionally refreshes on focus and throttles repeated focus events', async () => {
        const loader = vi.fn().mockResolvedValue(undefined);
        renderHook(() => useRefreshOnMutation(['crm'], loader, {
            refreshOnFocus: true,
            focusThrottleMs: 1_000,
        }));

        vi.advanceTimersByTime(999);
        window.dispatchEvent(new Event('focus'));
        await flushCoordinator();
        expect(loader).not.toHaveBeenCalled();

        vi.advanceTimersByTime(1);
        window.dispatchEvent(new Event('focus'));
        await flushCoordinator();
        expect(loader).toHaveBeenCalledTimes(1);

        window.dispatchEvent(new Event('focus'));
        await flushCoordinator();
        expect(loader).toHaveBeenCalledTimes(1);
    });

    it('can ignore local mutation events while still accepting broadcast events', async () => {
        const originalBroadcastChannel = globalThis.BroadcastChannel;
        let channel;
        class FakeBroadcastChannel {
            constructor() {
                channel = this;
                this.onmessage = null;
            }

            postMessage() {}
            close() {}
        }
        globalThis.BroadcastChannel = FakeBroadcastChannel;
        const loader = vi.fn().mockResolvedValue(undefined);

        try {
            renderHook(() => useRefreshOnMutation(['pms'], loader, {
                refreshOnLocalMutation: false,
            }));

            publishDataRefresh(['pms']);
            await flushCoordinator();
            expect(loader).not.toHaveBeenCalled();

            await act(async () => {
                channel.onmessage({
                    data: {
                        type: 'chrono:data-refresh',
                        sourceId: 'another-window',
                        scopes: ['pms'],
                        firstPublishedAt: Date.now() - 80,
                        changes: [{ method: 'post', url: '/api/pms/reservations' }],
                    },
                });
                vi.advanceTimersByTime(0);
                await Promise.resolve();
            });
            expect(loader).toHaveBeenCalledTimes(1);
        } finally {
            cleanup();
            resetDataRefreshCoordinator();
            globalThis.BroadcastChannel = originalBroadcastChannel;
        }
    });
});
