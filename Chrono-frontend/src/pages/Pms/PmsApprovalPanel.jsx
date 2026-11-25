import { useContext, useEffect, useRef, useState } from 'react';
import api from '../../utils/api.js';
import { AuthContext } from '../../context/AuthContext.jsx';
import { currencyStep, formatPmsMoney } from './pmsMoney.js';
import usePmsLiveRefresh from './usePmsLiveRefresh.js';
const labels = { PENDING: 'Freigabe ausstehend', APPROVED: 'Freigegeben', REJECTED: 'Abgelehnt', CONSUMED: 'Erstattungsauftrag angelegt', EXPIRED: 'Abgelaufen' };
const message = (error) => error.response?.data?.detail || error.message || 'Freigabe konnte nicht verarbeitet werden.';
export default function PmsApprovalPanel({ property, canManage, isMaster, onChanged }) {
    const username = useContext(AuthContext)?.currentUser?.username;
    const [policy, setPolicy] = useState(null); const [draft, setDraft] = useState(null); const [entries, setEntries] = useState([]);
    const [decision, setDecision] = useState(null); const [error, setError] = useState(''); const [notice, setNotice] = useState(''); const [busy, setBusy] = useState(false);
    const [listFilter, setListFilter] = useState({ propertyId: property.id, page: 0, status: 'OPEN' });
    const [pagination, setPagination] = useState({ totalElements: 0, hasNext: false }); const [loading, setLoading] = useState(true);
    const dirty = useRef(false); const guard = useRef(false); const generation = useRef(0); const loadedProperty = useRef(property.id);
    const requestSequence = useRef(0);
    const page = listFilter.propertyId === property.id ? listFilter.page : 0;
    const status = listFilter.propertyId === property.id ? listFilter.status : 'OPEN';
    const base = `/api/pms/properties/${property.id}`;
    const load = async () => {
        const current = generation.current; const sequence = ++requestSequence.current;
        setLoading(true);
        try { const [p, a] = await Promise.all([api.get(`${base}/approval-policy`), api.get(`${base}/approvals/refunds`, { params: { page, size: 50, status } })]);
            if (current !== generation.current || sequence !== requestSequence.current) return;
            if (!Array.isArray(a.data?.items) || !Number.isFinite(a.data.totalElements)) throw new Error('Ungültige Freigabenübersicht.');
            if (p.data && typeof p.data.enabled === 'boolean') { setPolicy(p.data); if (!dirty.current) setDraft(p.data); }
            setEntries(a.data.items); setPagination({ totalElements: a.data.totalElements, hasNext: a.data.hasNext === true });
            if (page > 0 && !a.data.items.length) setListFilter({ propertyId: property.id, status, page: Math.max(0, Math.ceil(a.data.totalElements / 50) - 1) });
        } catch (failure) { if (current === generation.current && sequence === requestSequence.current) setError(message(failure)); }
        finally { if (current === generation.current && sequence === requestSequence.current) setLoading(false); }
    };
    useEffect(() => {
        generation.current++;
        if (loadedProperty.current !== property.id) {
            loadedProperty.current = property.id; dirty.current = false;
            setPolicy(null); setDraft(null); setEntries([]); setDecision(null); setError(''); setNotice('');
            setListFilter({ propertyId: property.id, page: 0, status: 'OPEN' }); setPagination({ totalElements: 0, hasNext: false });
        }
        load(); return () => { generation.current++; };
    }, [property.id, page, status]);
    usePmsLiveRefresh(load, { propertyId: property.id, enabled: !busy });
    const act = async (work, text) => {
        if (guard.current) return; guard.current = true; setBusy(true); setError(''); setNotice('');
        try { await work(); setNotice(text); await load(); }
        catch (failure) { setError(message(failure)); }
        finally { guard.current = false; setBusy(false); }
    };
    return <section className="pms-work-card" aria-label="Freigaben für Erstattungen" aria-busy={loading}><div className="pms-work-card-heading"><div><span className="pms-eyebrow">Vier-Augen-Prinzip</span><h3>Freigaben für Erstattungen</h3></div><button type="button" disabled={busy || loading} onClick={load}>Freigaben aktualisieren</button></div>
        {error && <p role="alert" className="pms-error">{error}</p>}{notice && <p role="status">{notice}</p>}
        <p className="pms-muted">{policy?.enabled ? `Ab ${formatPmsMoney(policy.refundThreshold, property.currencyCode)} summierten Erstattungen einer Originalzahlung muss eine zweite berechtigte Person zustimmen.` : 'Die zusätzliche Freigabe ist für dieses Hotel derzeit deaktiviert.'} Freigaben gelten 48 Stunden und für den angezeigten Betrag, Grund und Kassenschicht.</p>
        {isMaster && draft && <details><summary>Freigaberegel des Hotels</summary><form className="pms-form-grid" onSubmit={(event) => { event.preventDefault(); act(async () => { await api.put(`${base}/approval-policy`, { enabled: draft.enabled, refundThreshold: Number(draft.refundThreshold), expectedVersion: draft.version }); dirty.current = false; }, 'Freigaberegel gespeichert.'); }}>
            <label className="pms-checkbox"><input type="checkbox" disabled={busy} checked={draft.enabled} onChange={(event) => { dirty.current = true; setDraft({ ...draft, enabled: event.target.checked }); }} /> Zweite Person erforderlich</label>
            <label>Summe je Originalzahlung ab ({property.currencyCode})<input required type="number" min={currencyStep(property.currencyCode)} step={currencyStep(property.currencyCode)} disabled={busy} value={draft.refundThreshold} onChange={(event) => { dirty.current = true; setDraft({ ...draft, refundThreshold: event.target.value }); }} /></label>
            <button type="submit" disabled={busy}>Freigaberegel speichern</button></form></details>}
        <div className="pms-form-grid"><label>Freigaben anzeigen<select value={status} disabled={busy} onChange={(event) => setListFilter({ propertyId: property.id, page: 0, status: event.target.value })}><option value="OPEN">Offene Anträge</option><option value="HISTORY">Historie</option><option value="ALL">Alle Anträge</option></select></label>
            <div className="pms-form-actions"><button type="button" disabled={busy || loading || page === 0} onClick={() => setListFilter({ propertyId: property.id, page: page - 1, status })}>Vorherige Freigabeseite</button><span role="status">{loading ? 'Freigaben werden geladen …' : `Seite ${page + 1} · ${pagination.totalElements} Anträge`}</span><button type="button" disabled={busy || loading || !pagination.hasNext} onClick={() => setListFilter({ propertyId: property.id, page: page + 1, status })}>Nächste Freigabeseite</button></div>
        </div>
        <div className="pms-record-list">{entries.map((entry) => <article className="pms-record" key={entry.id}><div><strong>{formatPmsMoney(entry.amount, entry.currency)} · Zahlung {entry.paymentId}</strong><p>{entry.reason}</p><small>{labels[entry.status] || entry.status} · Antrag: {entry.requestedBy}{entry.decidedBy ? ` · Entscheidung: ${entry.decidedBy}` : ''}</small></div>
            {entry.status === 'PENDING' && canManage && <button type="button" disabled={busy || loading || Boolean(decision) || entry.requestedBy === username} onClick={() => setDecision({ entry, approve: true, reason: '' })}>Antrag prüfen</button>}
            {entry.status === 'APPROVED' && canManage && <button type="button" disabled={busy || loading} onClick={() => act(async () => { await api.post(`${base}/payments/${entry.paymentId}/refund`, { requestId: entry.requestId, amount: Number(entry.amount), reason: entry.reason, cashShiftId: entry.cashShiftId }); onChanged?.(); }, 'Der freigegebene Erstattungsauftrag wurde verarbeitet. Den Zahlungsstatus im Gastkonto prüfen.')}>Freigegebene Erstattung ausführen</button>}
        </article>)}</div>{!entries.length && !loading && <p className="pms-muted">Keine Freigaben in dieser Ansicht.</p>}
        {decision && <form className="pms-form-grid" onSubmit={(event) => { event.preventDefault(); act(async () => { await api.post(`${base}/approvals/refunds/${decision.entry.id}/decision`, { approve: decision.approve, reason: decision.reason, expectedVersion: decision.entry.version }); setDecision(null); }, 'Entscheidung protokolliert.'); }}>
            <p className="pms-inline-note is-wide">Entscheidung zu Antrag {decision.entry.id} · Zahlung {decision.entry.paymentId}</p>
            <strong className="is-wide">{formatPmsMoney(decision.entry.amount, decision.entry.currency)} · {decision.entry.reason}</strong><label>Entscheidung<select value={String(decision.approve)} disabled={busy} onChange={(event) => setDecision({ ...decision, approve: event.target.value === 'true' })}><option value="true">Freigeben</option><option value="false">Ablehnen</option></select></label>
            <label>Begründung<textarea required maxLength={500} disabled={busy} value={decision.reason} onChange={(event) => setDecision({ ...decision, reason: event.target.value })} /></label><button type="submit" disabled={busy}>Entscheidung speichern</button><button type="button" disabled={busy} onClick={() => setDecision(null)}>Abbrechen</button></form>}
    </section>;
}
