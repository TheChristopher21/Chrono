/** @vitest-environment jsdom */
import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
const stream = vi.hoisted(() => ({ listener: null, detach: vi.fn() }));
vi.mock('./pmsLiveConnection.js', () => ({ subscribePmsLive: (_id, callback) => { stream.listener = callback; return stream.detach; } }));
import usePmsLiveRefresh from './usePmsLiveRefresh.js';
beforeEach(() => { vi.useFakeTimers(); vi.clearAllMocks(); });
afterEach(() => { cleanup(); vi.useRealTimers(); });
it('performs a follow-up read when an update arrives during an in-flight refresh without parallel reads', async () => {
    let finish;
    const refresh = vi.fn().mockImplementationOnce(() => new Promise((resolve) => { finish = resolve; })).mockResolvedValue(undefined);
    const view = renderHook(() => usePmsLiveRefresh(refresh, { propertyId: 9 }));
    await act(async () => { stream.listener(); await vi.advanceTimersByTimeAsync(250); });
    expect(refresh).toHaveBeenCalledOnce();
    await act(async () => { stream.listener(); await vi.advanceTimersByTimeAsync(250); });
    expect(refresh).toHaveBeenCalledOnce();
    await act(async () => { finish(); });
    expect(refresh).toHaveBeenCalledTimes(2);
    view.unmount(); expect(stream.detach).toHaveBeenCalledOnce();
});
