/** @vitest-environment jsdom */
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
const api = vi.hoisted(() => ({ getUri: vi.fn(({ url }) => `https://pms.example${url}`) }));
vi.mock('../../utils/api.js', () => ({ default: api }));
import { subscribePmsLive } from './pmsLiveConnection.js';
let detach;
beforeEach(() => { vi.useFakeTimers(); localStorage.clear(); sessionStorage.clear(); localStorage.setItem('token', 'local-test-token'); detach = []; });
afterEach(() => { detach.forEach((stop) => stop()); vi.unstubAllGlobals(); vi.useRealTimers(); });
it('shares an authenticated stream, decodes split events and aborts only after the last subscriber leaves', async () => {
    let feed;
    const body = new ReadableStream({ start(controller) { feed = controller; } });
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, body }); vi.stubGlobal('fetch', fetchMock);
    const first = vi.fn(), second = vi.fn();
    const stopFirst = subscribePmsLive(9, first), stopSecond = subscribePmsLive(9, second); detach.push(stopFirst, stopSecond);
    await vi.advanceTimersByTimeAsync(1);
    expect(fetchMock).toHaveBeenCalledOnce();
    expect(fetchMock.mock.calls[0][0]).toBe('https://pms.example/api/pms/properties/9/live');
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer local-test-token');
    const encoder = new TextEncoder();
    feed.enqueue(encoder.encode('event: rea')); await vi.advanceTimersByTimeAsync(1); expect(first).not.toHaveBeenCalled();
    feed.enqueue(encoder.encode('dy\r\ndata: {}\r\n\r\n: heartbeat\n\n')); await vi.advanceTimersByTimeAsync(1);
    expect(first).toHaveBeenCalledOnce(); expect(second).toHaveBeenCalledOnce();
    stopFirst(); expect(fetchMock.mock.calls[0][1].signal.aborted).toBe(false);
    feed.enqueue(encoder.encode('event: changed\ndata: {}\n\n')); await vi.advanceTimersByTimeAsync(1);
    expect(first).toHaveBeenCalledOnce(); expect(second).toHaveBeenCalledTimes(2);
    stopSecond(); expect(fetchMock.mock.calls[0][1].signal.aborted).toBe(true); feed.close();
});
it('recovers after a dropped connection and stops reconnecting when the workspace closes', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new TypeError('Network failed')); vi.stubGlobal('fetch', fetchMock);
    const stop = subscribePmsLive(11, vi.fn()); detach.push(stop);
    await vi.advanceTimersByTimeAsync(1); expect(fetchMock).toHaveBeenCalledOnce();
    await vi.advanceTimersByTimeAsync(6000); expect(fetchMock).toHaveBeenCalledTimes(2);
    stop(); await vi.advanceTimersByTimeAsync(60_000); expect(fetchMock).toHaveBeenCalledTimes(2);
});
it('does not open a stream after this tab has signed out', async () => {
    sessionStorage.setItem('chrono:tabIdleSignOut', '1'); const fetchMock = vi.fn(); vi.stubGlobal('fetch', fetchMock);
    detach.push(subscribePmsLive(12, vi.fn())); await vi.advanceTimersByTimeAsync(1); expect(fetchMock).not.toHaveBeenCalled();
});
