import api from '../../utils/api.js';

const channels = new Map();
// Deliberately not persisted: duplicated browser tabs need distinct identities.
const clientId = globalThis.crypto?.randomUUID?.() || `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
const HANDOFF_DELAY = 1000;

function retryAfterMillis(response) {
    const value = response.headers?.get('Retry-After')?.trim();
    if (!value) return 0;
    const delay = /^\d+(\.\d+)?$/.test(value) ? Number(value) * 1000 : Date.parse(value) - Date.now();
    return Number.isFinite(delay) ? Math.max(0, Math.min(delay, 2_147_483_647)) : 0;
}

/** One authenticated stream per hotel and browser window, shared by visible panels. */
export function subscribePmsLive(propertyId, listener) {
    if (!propertyId || typeof fetch !== 'function' || !api.getUri) return () => {};
    const key = String(propertyId);
    let channel = channels.get(key);
    if (!channel) {
        channel = { listeners: new Set(), controller: null, timer: null, closeTimer: null, disposed: false, failures: 0 };
        channels.set(key, channel);
        const connect = async () => {
            const token = localStorage.getItem('token');
            if (channel.disposed || !token || sessionStorage.getItem('chrono:tabIdleSignOut')) return;
            channel.controller = new AbortController();
            let serverDelay = 0;
            try {
                const response = await fetch(api.getUri({ url: `/api/pms/properties/${propertyId}/live?clientId=${clientId}` }), {
                    headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
                    signal: channel.controller.signal, cache: 'no-store', credentials: 'same-origin',
                });
                if (!response.ok || !response.body) {
                    serverDelay = retryAfterMillis(response);
                    await response.body?.cancel();
                    throw new Error('Live-Verbindung nicht verfügbar');
                }
                channel.failures = 0;
                const reader = response.body.getReader(); const decoder = new TextDecoder(); let buffer = '';
                while (!channel.disposed) {
                    const { value, done } = await reader.read(); if (done) break;
                    if (!localStorage.getItem('token')) { channel.controller.abort(); break; }
                    buffer += decoder.decode(value, { stream: true }).replace(/\r/g, '');
                    let boundary;
                    while ((boundary = buffer.indexOf('\n\n')) >= 0) {
                        const frame = buffer.slice(0, boundary); buffer = buffer.slice(boundary + 2);
                        // 'ready' triggers recovery after a disconnect: intermediate changes cannot be lost.
                        if (/^event:\s*(changed|ready)\s*$/m.test(frame)) channel.listeners.forEach((callback) => callback());
                    }
                    if (buffer.length > 16_384) throw new Error('Ungültige Live-Antwort');
                }
            } catch (error) { if (error.name !== 'AbortError') channel.failures += 1; }
            finally {
                if (!channel.disposed) channel.timer = setTimeout(connect,
                    Math.max(serverDelay, Math.min(30_000, 3000 * 2 ** Math.min(channel.failures, 4))));
            }
        };
        // Defer until the first consumer is registered.
        channel.timer = setTimeout(connect, 0);
    }
    clearTimeout(channel.closeTimer);
    channel.listeners.add(listener);
    let detached = false;
    return () => {
        if (detached) return;
        detached = true;
        channel.listeners.delete(listener);
        if (!channel.listeners.size) {
            // Pane changes and short busy states should hand the existing connection
            // to the next consumer without tearing down/reopening the server stream.
            channel.closeTimer = setTimeout(() => {
                channel.disposed = true;
                clearTimeout(channel.timer);
                channel.controller?.abort();
                channels.delete(key);
            }, HANDOFF_DELAY);
        }
    };
}
