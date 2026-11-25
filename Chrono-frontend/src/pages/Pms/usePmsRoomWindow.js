import { useEffect, useMemo, useRef, useState } from 'react';
import api from '../../utils/api.js';

export const ROOM_WINDOW_SIZE = 50;

/** Keep six server pages, while the scrollbar represents every matching room. */
export default function usePmsRoomWindow(propertyId, params, firstRow, lastRow, revision) {
    const key = JSON.stringify([propertyId, params]);
    const [state, setState] = useState({ key: '', pages: new Map(), total: 0, error: null });
    const [pending, setPending] = useState(0);
    const generation = useRef(0);
    const requested = useRef(new Set());
    const cache = useRef(new Map());
    const facets = useRef({ propertyId: null, filters: {} });
    const latestKey = useRef(key);
    latestKey.current = key;
    const firstPage = Math.floor(firstRow / ROOM_WINDOW_SIZE);
    const lastPage = Math.floor(lastRow / ROOM_WINDOW_SIZE);

    useEffect(() => {
        generation.current += 1;
        requested.current.clear(); cache.current.clear();
        setPending(0);
        setState((old) => ({ key, pages: old.key === key ? old.pages : new Map(), total: old.key === key ? old.total : 0, error: null }));
        return () => { generation.current += 1; };
    }, [key, revision]);

    useEffect(() => {
        if (!propertyId) return;
        const currentGeneration = generation.current;
        // At most three visible/adjacent pages; never fetch the entire hotel.
        const end = Math.min(firstPage + 2, lastPage + 1);
        for (let page = firstPage; page <= end; page += 1) {
            if (cache.current.has(page) || requested.current.has(page)) continue;
            if (state.key === key && state.total && page * ROOM_WINDOW_SIZE >= state.total) continue;
            // The first response establishes the room count before prefetching.
            if (!cache.current.size && page !== firstPage) continue;
            requested.current.add(page); setPending((value) => value + 1);
            const query = { ...params, page, size: ROOM_WINDOW_SIZE };
            Object.keys(query).forEach((name) => { if (query[name] === '' || (Array.isArray(query[name]) && !query[name].length)) delete query[name]; });
            api.get(`/api/pms/properties/${propertyId}/room-plan`, { params: query, paramsSerializer: { indexes: null } })
                .then(({ data }) => {
                    if (currentGeneration !== generation.current || latestKey.current !== key) return;
                    if (!Array.isArray(data?.rooms)) throw new Error('Ungültige Zimmerplan-Antwort');
                    facets.current = { propertyId, filters: data.filters || {} };
                    cache.current.set(data.page ?? page, data);
                    // Evict the most distant page. Visible rows always stay cached.
                    while (cache.current.size > 6) {
                        const distant = [...cache.current.keys()].sort((a, b) => Math.abs(b - firstPage) - Math.abs(a - firstPage))[0];
                        cache.current.delete(distant);
                    }
                    setState({ key, pages: new Map(cache.current), total: data.totalRooms, error: null });
                })
                .catch((error) => {
                    if (currentGeneration === generation.current && latestKey.current === key)
                        setState((old) => ({ ...old, key, error }));
                })
                .finally(() => {
                    if (currentGeneration === generation.current) {
                        // Failed requests wait for explicit refresh/online recovery.
                        setPending((value) => Math.max(0, value - 1));
                    }
                });
        }
    }, [key, propertyId, firstPage, lastPage, revision, state.pages, state.total]); // params is represented by key

    return useMemo(() => {
        const filters = facets.current.propertyId === propertyId ? facets.current.filters : {};
        if (state.key !== key) return { data: null, loading: true, error: null, filters };
        const entries = [...state.pages.entries()].sort(([a], [b]) => a - b);
        const first = entries[0]?.[1];
        const rooms = entries.flatMap(([page, value]) => value.rooms.map((room, index) => ({ ...room, planIndex: page * ROOM_WINDOW_SIZE + index })));
        const unique = (values, id) => [...new Map(values.map((value) => [id(value), value])).values()];
        return {
            loading: pending > 0,
            filters,
            error: state.error,
            data: first ? { ...first, totalRooms: state.total, rooms,
                reservations: unique(entries.flatMap(([, value]) => value.reservations || []), (value) => `${value.id}:${value.segmentId || ''}`),
                blocks: unique(entries.flatMap(([, value]) => value.blocks || []), (value) => value.id) } : null,
        };
    }, [state, key, pending]);
}
