/** @vitest-environment jsdom */
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
const api = vi.hoisted(() => ({ getUri: vi.fn(({ url }) => `https://pms.example${url}`) }));
vi.mock('../../utils/api.js', () => ({ default: api }));
import { subscribePmsLive } from './pmsLiveConnection.js';
let detach;
beforeEach(() => { vi.useFakeTimers(); localStorage.clear(); sessionStorage.clear(); localStorage.setItem('token', 'local-test-token'); detach = []; });
afterEach(async () => { detach.forEach((stop) => stop()); await vi.advanceTimersByTimeAsync(1000); vi.unstubAllGlobals(); vi.useRealTimers(); });
it('shares an authenticated stream, decodes split events and aborts only after the last subscriber leaves', async () => {
    let feed;
    const body = new ReadableStream({ start(controller) { feed = controller; } });
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, body }); vi.stubGlobal('fetch', fetchMock);
    const first = vi.fn(), second = vi.fn();
    const stopFirst = subscribePmsLive(9, first), stopSecond = subscribePmsLive(9, second); detach.push(stopFirst, stopSecond);
    await vi.advanceTimersByTimeAsync(1);
    expect(fetchMock).toHaveBeenCalledOnce();
    expect(fetchMock.mock.calls[0][0]).toMatch(/^https:\/\/pms.example\/api\/pms\/properties\/9\/live\?clientId=[A-Za-z0-9-]+$/);
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer local-test-token');
    const encoder = new TextEncoder();
    feed.enqueue(encoder.encode('event: rea')); await vi.advanceTimersByTimeAsync(1); expect(first).not.toHaveBeenCalled();
    feed.enqueue(encoder.encode('dy\r\ndata: {}\r\n\r\n: heartbeat\n\n')); await vi.advanceTimersByTimeAsync(1);
    expect(first).toHaveBeenCalledOnce(); expect(second).toHaveBeenCalledOnce();
    stopFirst(); expect(fetchMock.mock.calls[0][1].signal.aborted).toBe(false);
    feed.enqueue(encoder.encode('event: changed\ndata: {}\n\n')); await vi.advanceTimersByTimeAsync(1);
    expect(first).toHaveBeenCalledOnce(); expect(second).toHaveBeenCalledTimes(2);
    stopSecond(); await vi.advanceTimersByTimeAsync(1000);
    expect(fetchMock.mock.calls[0][1].signal.aborted).toBe(true); feed.close();
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
it('hands the same stream across rapid pane changes and stops after the workspace stays closed', async () => {
    let feed;
    const body = new ReadableStream({ start(controller) { feed = controller; } });
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, body }); vi.stubGlobal('fetch', fetchMock);
    let stop = subscribePmsLive(9, vi.fn()); detach.push(stop);
    await vi.advanceTimersByTimeAsync(1);
    for (let pane = 0; pane < 30; pane += 1) {
        stop(); await vi.advanceTimersByTimeAsync(100);
        stop = subscribePmsLive(9, vi.fn()); detach.push(stop);
    }
    expect(fetchMock).toHaveBeenCalledOnce();
    expect(fetchMock.mock.calls[0][1].signal.aborted).toBe(false);
    stop(); await vi.advanceTimersByTimeAsync(1000);
    expect(fetchMock.mock.calls[0][1].signal.aborted).toBe(true);
    feed.close(); await vi.advanceTimersByTimeAsync(60_000);
    expect(fetchMock).toHaveBeenCalledOnce();
});
it.each(['45', () => new Date(Date.now() + 45_000).toUTCString()])('honors server Retry-After before retrying a limited stream (%s)', async (retryAfter) => {
    vi.setSystemTime(new Date('2026-09-14T10:00:00Z'));
    const cancel = vi.fn().mockResolvedValue(undefined);
    const fetchMock = vi.fn().mockImplementation(async () => ({
        ok: false, status: 429, body: { cancel },
        headers: new Headers({ 'Retry-After': typeof retryAfter === 'function' ? retryAfter() : retryAfter }),
    }));
    vi.stubGlobal('fetch', fetchMock);
    detach.push(subscribePmsLive(9, vi.fn()));
    await vi.advanceTimersByTimeAsync(1);
    expect(fetchMock).toHaveBeenCalledOnce(); expect(cancel).toHaveBeenCalledOnce();
    await vi.advanceTimersByTimeAsync(44_000); expect(fetchMock).toHaveBeenCalledOnce();
    await vi.advanceTimersByTimeAsync(1000); expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0][0]).toBe(fetchMock.mock.calls[1][0]);
});
it('retains the browser client identity when a closed hotel stream is reopened', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new TypeError('Network failed')); vi.stubGlobal('fetch', fetchMock);
    const stop = subscribePmsLive(9, vi.fn()); detach.push(stop);
    await vi.advanceTimersByTimeAsync(1);
    stop(); await vi.advanceTimersByTimeAsync(1000);
    detach.push(subscribePmsLive(9, vi.fn())); await vi.advanceTimersByTimeAsync(1);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0][0]).toBe(fetchMock.mock.calls[1][0]);
});
