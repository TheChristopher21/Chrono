import { useEffect, useRef } from 'react';
import { useWorkspacePaneActive } from '../../components/workspace/WorkspacePaneContext.jsx';
import { subscribePmsLive } from './pmsLiveConnection.js';

// Only the visible PMS pane refreshes. Forms keep their own local state.
export default function usePmsLiveRefresh(refresh, { enabled = true, interval = 15000, propertyId } = {}) {
    const active = useWorkspacePaneActive();
    const latest = useRef(refresh);
    latest.current = refresh;
    useEffect(() => {
        if (!enabled || !active) return undefined;
        let running = false;
        let disposed = false;
        let debounce;
        let invalidated = false;
        const tick = async () => {
            if (disposed || document.visibilityState === 'hidden') return;
            if (running) { invalidated = true; return; }
            running = true;
            try { await latest.current(); } catch { /* The owning workspace presents request errors. */ }
            finally { running = false; if (invalidated && !disposed) { invalidated = false; void tick(); } }
        };
        const timer = window.setInterval(tick, interval);
        const unsubscribe = subscribePmsLive(propertyId, () => {
            window.clearTimeout(debounce);
            debounce = window.setTimeout(tick, 250);
        });
        document.addEventListener('visibilitychange', tick);
        window.addEventListener('online', tick);
        return () => {
            disposed = true;
            window.clearInterval(timer);
            window.clearTimeout(debounce);
            unsubscribe();
            document.removeEventListener('visibilitychange', tick);
            window.removeEventListener('online', tick);
        };
    }, [active, enabled, interval, propertyId]);
}
