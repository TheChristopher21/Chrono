import { useEffect, useMemo, useRef, useState } from 'react';
import api from '../../utils/api.js';
import usePmsLiveRefresh from './usePmsLiveRefresh.js';
import { offlineScope, readOffline, writeOffline, clearPmsOffline, cacheHousekeeping, cachedHousekeeping, enqueueHousekeeping, updateOfflineCommand } from './pmsOfflineStore.js';
import './PmsHousekeepingWorkspace.css';

export const HK_WORK_TYPES = { CLEAN: 'Reinigung', INSPECTION: 'Kontrolle', TURNDOWN: 'Abendservice' };
export const HK_WORK_STATUSES = { OPEN: 'Offen', IN_PROGRESS: 'In Arbeit', DONE: 'Erledigt', DND: 'Bitte nicht stören', DEFERRED: 'Zurückgestellt' };
const reasons = { MANUAL: 'Manuell geplant', ARRIVAL: 'Anreise', DEPARTURE: 'Abreise', STAYOVER: 'Bleibezimmer', INSPECTION: 'Kontrolle' };
const blank = (date) => ({ roomId: '', serviceDate: date || '', workType: 'CLEAN', workStatus: 'OPEN', type: 'MANUAL', priority: 50, estimatedMinutes: 30, notes: '', assignedTo: '' });
const message = (failure) => failure?.response?.data?.detail || failure?.response?.data?.message || failure.message;

export default function PmsHousekeepingWorkspace({ property, rooms = property.rooms || [], businessDate, canManage, onChanged }) {
    const [tasks, setTasks] = useState([]);
    const [selectedId, setSelectedId] = useState(null);
    const [draft, setDraft] = useState(() => blank(businessDate));
    const [search, setSearch] = useState('');
    const [kind, setKind] = useState('');
    const [status, setStatus] = useState('');
    const [loading, setLoading] = useState(true);
    const [pending, setPending] = useState(false);
    const [revision, setRevision] = useState(0);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [conflict, setConflict] = useState(false);
    const [historyPage, setHistoryPage] = useState(0);
    const [history, setHistory] = useState(null);
    const [historyError, setHistoryError] = useState('');
    const [historyLoading, setHistoryLoading] = useState(false);
    const scope = offlineScope(property.id);
    const [device, setDevice] = useState(() => readOffline(scope));
    const [online, setOnline] = useState(navigator.onLine);
    const [snapshotTime, setSnapshotTime] = useState(null);
    const [syncing, setSyncing] = useState(false);
    const syncingRef = useRef(false);
    const scopeRef = useRef(scope); scopeRef.current = scope;
    const latestSync = useRef(null);
    const context = useRef(`${property.id}:${businessDate}`);
    const prefix = `/api/pms/properties/${property.id}/housekeeping/work-orders`;
    usePmsLiveRefresh(() => setRevision((value) => value + 1), { propertyId: property.id, enabled: online && !pending && !syncing });
    useEffect(() => {
        const update = () => setDevice(readOffline(scope));
        const network = () => { setOnline(navigator.onLine); if (navigator.onLine) setRevision((value) => value + 1); };
        update(); window.addEventListener('chrono:pms-offline-changed', update); window.addEventListener('storage', update);
        window.addEventListener('online', network); window.addEventListener('offline', network);
        return () => { window.removeEventListener('chrono:pms-offline-changed', update); window.removeEventListener('storage', update); window.removeEventListener('online', network); window.removeEventListener('offline', network); };
    }, [scope]);
    const syncQueue = async () => {
        if (syncingRef.current || !online || !canManage || !scope || !readOffline(scope).enabled) return;
        syncingRef.current = true; setSyncing(true);
        try {
            for (const entry of readOffline(scope).queue.filter((value) => value.state === 'PENDING')) {
                // Every replay carries the same immutable command ID and original version.
                if (offlineScope(property.id) !== scope || !readOffline(scope).enabled) break;
                try {
                    const { data: saved } = await api.post(`/api/pms/properties/${property.id}/housekeeping/offline-commands`, { commandId: entry.commandId, taskId: entry.taskId, update: entry.update });
                    updateOfflineCommand(scope, entry.commandId, null);
                    if (scopeRef.current === scope) {
                        setDraft((currentDraft) => currentDraft.id === entry.taskId && currentDraft.version === entry.update.version ? { ...currentDraft, version: saved.version } : currentDraft);
                        setNotice(`Vorgemerkte Änderung für Zimmer ${entry.roomNumber} im Hotel gespeichert.`);
                        setRevision((value) => value + 1); onChanged?.();
                    }
                } catch (failure) {
                    if ([401, 403].includes(failure.response?.status)) { clearPmsOffline(scope); setTasks([]); setError('Die Geräteablage wurde wegen fehlender Berechtigung gelöscht.'); break; }
                    if (failure.response && failure.response.status < 500) updateOfflineCommand(scope, entry.commandId, { state: 'CONFLICT', error: message(failure) });
                    else { setNotice('Die Übertragung ist noch offen. Die Aktion bleibt mit derselben Kennung vorgemerkt.'); break; }
                }
            }
        } finally { syncingRef.current = false; setSyncing(false); }
    };
    latestSync.current = syncQueue;
    useEffect(() => { if (device.enabled && device.queue.length && online && canManage) syncQueue(); }, [scope, device.enabled, device.queue.length, online, canManage]);
    useEffect(() => {
        if (!device.enabled || !online || !canManage) return undefined;
        const timer = setInterval(() => latestSync.current?.(), 15_000);
        return () => clearInterval(timer);
    }, [scope, device.enabled, online, canManage]);
    useEffect(() => {
        const key = `${property.id}:${businessDate}`;
        if (context.current !== key) {
            context.current = key; setTasks([]); setSnapshotTime(null); setSelectedId(null); setDraft(blank(businessDate)); setConflict(false); setError(''); setNotice(''); setHistoryPage(0);
        }
        const controller = new AbortController(); setLoading(true);
        api.get(prefix, { params: { businessDate }, signal: controller.signal }).then(({ data }) => {
            if (!controller.signal.aborted) {
                setTasks(Array.isArray(data) ? data : []); setSnapshotTime(null);
                try { cacheHousekeeping(scope, businessDate, Array.isArray(data) ? data : []); } catch (failure) { setError(failure.message); }
            }
        }).catch((failure) => { if (!controller.signal.aborted) {
            if ([401, 403].includes(failure.response?.status)) { clearPmsOffline(scope); setTasks([]); setSnapshotTime(null); setError(message(failure)); return; }
            const cached = !failure.response ? cachedHousekeeping(scope, businessDate) : null;
            if (cached) { setTasks(cached.tasks); setSnapshotTime(cached.capturedAt); setError(''); }
            else { setTasks([]); setError(message(failure)); }
        } })
            .finally(() => { if (!controller.signal.aborted) setLoading(false); });
        return () => controller.abort();
    }, [property.id, businessDate, revision]);
    useEffect(() => {
        if (!selectedId || !online) { setHistory(null); return; }
        const controller = new AbortController(); setHistoryLoading(true); setHistoryError('');
        api.get(`${prefix}/${selectedId}/history`, { params: { page: historyPage, size: 10 }, signal: controller.signal }).then(({ data }) => {
            if (!controller.signal.aborted) setHistory(data);
        }).catch((failure) => { if (!controller.signal.aborted) setHistoryError(message(failure)); })
            .finally(() => { if (!controller.signal.aborted) setHistoryLoading(false); });
        return () => controller.abort();
    }, [property.id, selectedId, historyPage, revision, online]);
    const filtered = useMemo(() => tasks.filter((task) => (!kind || task.workType === kind) && (!status || task.workStatus === status)
        && [task.roomNumber, task.assignedTo, task.notes].some((value) => String(value || '').toLocaleLowerCase().includes(search.toLocaleLowerCase()))), [tasks, search, kind, status]);
    const current = tasks.find((task) => task.id === selectedId);
    const change = (key, value) => setDraft((prior) => ({ ...prior, [key]: value }));
    const select = (task) => {
        setSelectedId(task.id); setDraft({ ...task, notes: task.notes || '', assignedTo: task.assignedTo || '' });
        setError(''); setNotice(''); setConflict(false); setHistoryPage(0); setHistory(null);
    };
    const reset = () => { setSelectedId(null); setDraft(blank(businessDate)); setConflict(false); setError(''); setNotice(''); };
    const save = async (event) => {
        event.preventDefault(); if (!canManage || pending || conflict) return;
        if (['DND', 'DEFERRED'].includes(draft.workStatus) && !draft.notes.trim()) { setError('Bitte den Grund für DND oder Zurückstellung festhalten.'); return; }
        setPending(true); setError(''); setNotice('');
        const fields = { priority: Number(draft.priority), estimatedMinutes: Number(draft.estimatedMinutes), notes: draft.notes.trim() || null, assignedTo: draft.assignedTo.trim() || null };
        try {
            if (selectedId && device.enabled) {
                enqueueHousekeeping(scope, draft, { ...fields, version: draft.version, workStatus: draft.workStatus });
                setNotice(online ? 'Änderung zur Übertragung vorgemerkt.' : 'Änderung auf diesem Gerät vorgemerkt. Sie ist noch nicht im Hotel gespeichert.');
                return;
            }
            if (!online || snapshotTime) throw new Error('Für neue Aufgaben ist eine Verbindung erforderlich. Bestehende Aufgaben können mit aktivierter Geräteablage vorgemerkt werden.');
            const { data } = selectedId ? await api.put(`${prefix}/${selectedId}`, { ...fields, version: draft.version, workStatus: draft.workStatus })
                : await api.post(prefix, { ...fields, roomId: Number(draft.roomId), serviceDate: draft.serviceDate, workType: draft.workType, type: draft.type });
            select(data); setNotice('Housekeeping-Aufgabe gespeichert.'); setRevision((value) => value + 1); onChanged?.();
        } catch (failure) {
            setError(message(failure));
            if (selectedId && failure?.response?.status === 409) { setConflict(true); setRevision((value) => value + 1); }
        } finally { setPending(false); }
    };
    return <section className="pms-housekeeping-workspace" aria-label="Housekeeping-Arbeitsaufträge">
        <div className="pms-hk-heading"><div><span className="pms-eyebrow">{property.name} · {businessDate}</span><h3>Reinigung, Kontrolle und Abendservice</h3></div><button type="button" onClick={() => setRevision((value) => value + 1)} disabled={loading}>Aktualisieren</button></div>
        <p className="pms-rate-help">Jede Arbeitsart hat ihren eigenen Auftrag pro Zimmer und Tag. Der Abendservice verändert die Freigabe des Zimmers nicht.</p>
        <aside className="pms-work-card pms-hk-device" aria-label="Offline-Arbeit"><div className="pms-hk-heading"><strong>{online && !snapshotTime ? 'Mit Hotel verbunden' : 'Offline · letzter geladener Stand'}</strong><label><input type="checkbox" checked={device.enabled} disabled={!scope || Boolean(device.queue.length) || syncing} onChange={(event) => {
            if (event.target.checked) { try { writeOffline(scope, { enabled: true, snapshots: {}, queue: [] }); cacheHousekeeping(scope, businessDate, tasks); } catch (failure) { setError(failure.message); } }
            else clearPmsOffline(scope);
        }} /> Aufgaben auf diesem Gerät für Offline-Arbeit speichern</label></div>
        <p className="pms-rate-help">Arbeitsaufträge und Notizen werden nur nach Aktivierung auf diesem Gerät abgelegt. Abmelden löscht die Geräteablage. Zimmerfreigaben gelten nach erfolgreicher Übertragung.</p>
        {snapshotTime && <p role="status">Gespeicherter Stand: {new Date(snapshotTime).toLocaleString('de-DE')} · höchstens 24 Stunden verfügbar.</p>}
        {device.queue.length > 0 && <><p role="status">{device.queue.length} offene Geräteaktionen {syncing ? '· Übertragung läuft …' : ''}</p><button type="button" disabled={!online || syncing || !canManage} onClick={syncQueue}>Jetzt synchronisieren</button>
            {device.queue.map((entry) => <div className="pms-record" key={entry.commandId}><span>Zimmer {entry.roomNumber} · {HK_WORK_STATUSES[entry.update.workStatus]} · {entry.state === 'CONFLICT' ? entry.error : 'Noch nicht bestätigt'}</span>
                {entry.state === 'CONFLICT' && <button type="button" disabled={loading || !online || snapshotTime || !tasks.some((task) => task.id === entry.taskId)} onClick={() => { const task = tasks.find((value) => value.id === entry.taskId); select({ ...task, ...entry.update }); setConflict(true); updateOfflineCommand(scope, entry.commandId, null); }}>Konflikt im Formular prüfen</button>}</div>)}</>}
        </aside>
        <div className="pms-hk-layout"><section className="pms-work-card">
            <div className="pms-form-grid"><label>Zimmer, Mitarbeiter oder Notiz suchen<input type="search" value={search} onChange={(event) => setSearch(event.target.value)} /></label>
                <label>Arbeitsart filtern<select value={kind} onChange={(event) => setKind(event.target.value)}><option value="">Alle Arbeitsarten</option>{Object.entries(HK_WORK_TYPES).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
                <label>Status filtern<select value={status} onChange={(event) => setStatus(event.target.value)}><option value="">Alle Status</option>{Object.entries(HK_WORK_STATUSES).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label></div>
            <p role="status">{loading ? 'Aufgaben werden geladen …' : `${filtered.length} von ${tasks.length} Aufgaben`}</p>
            <div className="pms-hk-task-list">{filtered.map((task) => <button type="button" key={task.id} className={`pms-hk-task is-${task.workStatus.toLowerCase()}${selectedId === task.id ? ' is-selected' : ''}`} onClick={() => select(task)}>
                <strong>Zimmer {task.roomNumber} · {HK_WORK_TYPES[task.workType]}</strong><span>{HK_WORK_STATUSES[task.workStatus]} · {task.assignedTo || 'Nicht zugeteilt'}</span><small>Priorität {task.priority} · {task.estimatedMinutes} Min.{task.notes ? ` · ${task.notes}` : ''}</small>
            </button>)}</div>
            {!loading && !filtered.length && <p>Keine passenden Aufgaben für diesen Tag.</p>}
        </section><section className="pms-work-card">
            <div className="pms-hk-heading"><h3>{selectedId ? `Zimmer ${draft.roomNumber} · ${HK_WORK_TYPES[draft.workType]}` : 'Aufgabe anlegen'}</h3>{selectedId && <button type="button" onClick={reset}>Neue Aufgabe</button>}</div>
            {error && <p role="alert" className="pms-error">{error}</p>}{notice && <p role="status">{notice}</p>}
            {conflict && <div className="pms-hk-conflict" role="alert"><strong>Ihre Eingaben sind erhalten geblieben.</strong><p>{loading ? 'Der aktuelle Stand wird geladen.' : current ? `Aktueller Stand: ${HK_WORK_STATUSES[current.workStatus]}, ${current.assignedTo || 'nicht zugeteilt'}, Version ${current.version}.` : 'Die Aufgabe ist im aktuellen Tagesplan nicht mehr vorhanden.'}</p>
                <button type="button" disabled={loading || !current || !canManage || snapshotTime || !online} onClick={() => { change('version', current.version); setConflict(false); setError(''); }}>Aktuellen Stand übernehmen, Eingaben behalten</button></div>}
            <form onSubmit={save}><fieldset className="pms-rate-fieldset" disabled={!canManage || pending}>
                <div className="pms-form-grid">{!selectedId && <>
                    <label>Zimmer<select required value={draft.roomId} onChange={(event) => change('roomId', event.target.value)}><option value="">Zimmer wählen</option>{rooms.map((room) => <option key={room.id} value={room.id}>{room.number || room.roomNumber}</option>)}</select></label>
                    <label>Servicetag<input required type="date" value={draft.serviceDate} onChange={(event) => change('serviceDate', event.target.value)} /></label>
                    <label>Arbeitsart<select value={draft.workType} onChange={(event) => change('workType', event.target.value)}>{Object.entries(HK_WORK_TYPES).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
                    <label>Anlass<select value={draft.type} onChange={(event) => change('type', event.target.value)}>{Object.entries(reasons).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label></>}
                    {selectedId && <label>Arbeitsstatus<select value={draft.workStatus} onChange={(event) => change('workStatus', event.target.value)}>{Object.entries(HK_WORK_STATUSES).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>}
                    <label>Zugewiesen an<input maxLength={120} value={draft.assignedTo} onChange={(event) => change('assignedTo', event.target.value)} /></label>
                    <label>Priorität<input required type="number" min="0" max="100" value={draft.priority} onChange={(event) => change('priority', event.target.value)} /></label>
                    <label>Geplante Minuten<input required type="number" min="1" max="1440" value={draft.estimatedMinutes} onChange={(event) => change('estimatedMinutes', event.target.value)} /></label>
                    <label className="is-wide">Arbeitsnotiz<textarea maxLength={1000} required={['DND', 'DEFERRED'].includes(draft.workStatus)} value={draft.notes} onChange={(event) => change('notes', event.target.value)} /></label>
                </div><div className="pms-form-actions"><button className="is-primary" type="submit" disabled={conflict || device.queue.some((entry) => entry.taskId === selectedId) || (!online && (!device.enabled || !selectedId))}>{pending ? 'Wird gespeichert …' : 'Aufgabe speichern'}</button></div>
            </fieldset></form>
            {selectedId && <section className="pms-hk-history" aria-label="Aufgabenverlauf"><h4>Aufgabenverlauf</h4>{historyError && <p role="alert">{historyError}</p>}
                {historyLoading ? <p role="status">Verlauf wird geladen …</p> : <ol>{history?.items?.map((item) => <li key={item.id}><strong>{item.fromStatus ? `${HK_WORK_STATUSES[item.fromStatus]} → ` : ''}{HK_WORK_STATUSES[item.toStatus]}</strong><span>{item.actor} · {String(item.createdAt || '').replace('T', ' ')} · {item.assignedTo || 'Nicht zugeteilt'}</span>{item.notes && <p>{item.notes}</p>}</li>)}</ol>}
                <div className="pms-hk-pagination"><button type="button" disabled={!historyPage || historyLoading} onClick={() => setHistoryPage((page) => page - 1)}>Verlauf zurück</button><span>Seite {historyPage + 1} · {history?.totalElements || 0} Einträge</span><button type="button" disabled={!history?.hasNext || historyLoading} onClick={() => setHistoryPage((page) => page + 1)}>Verlauf weiter</button></div>
            </section>}
        </section></div>
    </section>;
}
