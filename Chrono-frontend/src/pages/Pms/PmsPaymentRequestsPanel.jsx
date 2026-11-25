import { useCallback, useEffect, useRef, useState } from 'react';
import api from '../../utils/api.js';
import usePmsLiveRefresh from './usePmsLiveRefresh.js';
import { currencyStep, formatPmsMoney } from './pmsMoney.js';

const statuses = { REQUESTED: 'Anfrage wird geprüft', OPEN: 'Checkout offen', AUTHORIZED: 'Betrag reserviert', PAID: 'Zahlung bestätigt', RELEASED: 'Reservierung freigegeben', EXPIRED: 'Checkout abgelaufen', FAILED: 'Anfrage abgelehnt', CAPTURE_REQUESTED: 'Einzug wird geprüft', RELEASE_REQUESTED: 'Freigabe wird geprüft' };
const safeCheckoutUrl = (value) => { try { const url = new URL(value); return url.protocol === 'https:' && url.hostname === 'checkout.stripe.com' ? url.href : null; } catch { return null; } };

export default function PmsPaymentRequestsPanel({ property, folios = [], canManage, onChanged }) {
    const [folioId, setFolioId] = useState('');
    const [rows, setRows] = useState([]);
    const [draft, setDraft] = useState({ requestId: crypto.randomUUID(), kind: 'PAYMENT', amount: '' });
    const [capture, setCapture] = useState({ id: '', amount: '' });
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [intent, setIntent] = useState(null);
    const scope = useRef(property.id); scope.current = property.id;
    const version = useRef(0);
    const load = useCallback(async () => {
        const current = ++version.current;
        if (!folioId) { setRows([]); return; }
        const { data } = await api.get(`/api/pms/properties/${property.id}/folios/${folioId}/payment-requests`);
        if (current === version.current && scope.current === property.id) { if (!Array.isArray(data)) throw new Error('Ungültige Zahlungsübersicht'); setRows(data); }
    }, [property.id, folioId]);
    useEffect(() => { let active = true; load().catch(() => { if (active) setError('Zahlungsaufträge konnten nicht geladen werden.'); }); return () => { active = false; version.current++; }; }, [load]);
    usePmsLiveRefresh(load, { enabled: Boolean(folioId) && !busy });
    const act = async (action, message) => {
        if (!canManage || busy) return false;
        const started = property.id;
        setBusy(true); setError(''); setNotice('');
        try { await action(); if (scope.current !== started) return false; await load(); await onChanged?.(); setNotice(message); return true; }
        catch (err) { setError(err.response?.data?.detail || 'Status noch nicht bestätigt. Den bestehenden Auftrag erneut prüfen.'); return false; }
        finally { setBusy(false); }
    };
    const create = async (event) => {
        event.preventDefault(); if (!canManage || busy || !folioId) return;
        const request = intent || { propertyId: property.id, folioId, payload: { ...draft, amount: Number(draft.amount) } };
        setIntent(request);
        await act(async () => {
            try { await api.post(`/api/pms/properties/${request.propertyId}/folios/${request.folioId}/payment-requests`, request.payload); }
            catch (failure) { if ([400,401,403,404,422].includes(failure?.response?.status)) setIntent(null); throw failure; }
            if (scope.current !== request.propertyId) return;
            setIntent(null); setDraft({ requestId: crypto.randomUUID(), kind: request.payload.kind, amount: '' });
        }, 'Auftrag erstellt. Der Zahlungslink steht beim Auftrag bereit.');
    };
    const endpoint = (id, action) => `/api/pms/properties/${property.id}/payment-requests/${id}/${action}`;
    return <section className="pms-work-card pms-enterprise-card">
        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Kartenzahlungen</span><h3>Checkout & Vorautorisierung</h3><p className="pms-muted">Zahlungslink erstellen, Kartenbetrag reservieren und nach Bestätigung einziehen.</p></div><span className="pms-status-pill">{property.currencyCode}</span></div>
        {error && <div className="pms-inline-message is-error" role="alert">{error}</div>}{notice && <div className="pms-inline-message is-success" role="status">{notice}</div>}
        {intent && <p className="pms-inline-message" role="status">Der angefragte Betrag und die Vorgangs-ID bleiben für die Prüfung gesperrt. Eine Wiederholung prüft denselben Auftrag.</p>}
        <form className="pms-form-grid" onSubmit={create}>
            <label className="is-wide">Gastkonto für Kartenzahlung<select value={folioId} disabled={busy || Boolean(intent)} onChange={(event) => { const folio = folios.find((entry) => String(entry.id) === event.target.value); setFolioId(event.target.value); setDraft({ requestId: crypto.randomUUID(), kind: 'PAYMENT', amount: folio?.balance > 0 ? String(folio.balance) : '' }); setCapture({ id: '', amount: '' }); setError(''); }} required><option value="">Gastkonto auswählen</option>{folios.filter((folio) => folio.status === 'OPEN').map((folio) => <option key={folio.id} value={folio.id}>{folio.guestName} · {folio.label || folio.folioNumber || `Konto ${folio.id}`} · {formatPmsMoney(folio.balance, folio.currencyCode || property.currencyCode)}</option>)}</select></label>
            <label>Vorgang<select value={draft.kind} disabled={busy || !canManage || Boolean(intent)} onChange={(event) => setDraft({ ...draft, kind: event.target.value, requestId: crypto.randomUUID() })}><option value="PAYMENT">Zahlung einziehen</option><option value="AUTHORIZATION">Betrag auf Karte reservieren</option></select></label>
            <label>Betrag ({property.currencyCode})<input type="number" min={currencyStep(property.currencyCode)} step={currencyStep(property.currencyCode)} value={draft.amount} disabled={busy || !canManage || Boolean(intent)} onChange={(event) => setDraft({ ...draft, amount: event.target.value, requestId: crypto.randomUUID() })} required /></label>
            <div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={busy || !canManage || !folioId}>{intent ? 'Bestehenden Auftrag erneut prüfen' : 'Sicheren Zahlungslink erstellen'}</button></div>
        </form>
        {folioId && <div className="pms-record-list">{rows.map((row) => <article className="pms-record" key={row.id}><div><span>{row.kind === 'AUTHORIZATION' ? 'Vorautorisierung' : 'Kartenzahlung'} · Auftrag {row.id}</span><strong>{formatPmsMoney(row.amount, row.currencyCode)}</strong><small>{statuses[row.status] || row.status}{row.captureAmount != null ? ` · Eingezogen: ${formatPmsMoney(row.captureAmount, row.currencyCode)}` : ''}</small></div><div className="pms-record-actions">
            {row.status === 'OPEN' && safeCheckoutUrl(row.checkoutUrl) && <a className="pms-button" href={safeCheckoutUrl(row.checkoutUrl)} target="_blank" rel="noopener noreferrer">Zahlungsseite öffnen</a>}
            {!['PAID', 'RELEASED', 'EXPIRED', 'FAILED'].includes(row.status) && <button type="button" disabled={busy || !canManage} onClick={() => act(() => api.post(endpoint(row.id, 'reconcile')), 'Anbieterstatus abgeglichen.')}>Status prüfen</button>}
            {row.status === 'AUTHORIZED' && <><button type="button" disabled={busy || !canManage} onClick={() => setCapture({ id: String(row.id), amount: String(row.amount) })}>Betrag einziehen</button><button type="button" disabled={busy || !canManage} onClick={() => setCapture({ id: String(row.id), amount: '', release: true })}>Reservierung freigeben</button></>}
        </div></article>)}{!rows.length && <p className="pms-muted">Für dieses Gastkonto gibt es noch keinen Kartenauftrag.</p>}</div>}
        {capture.id && <form className="pms-form-grid pms-enterprise-form" onSubmit={async (event) => { event.preventDefault(); if (await act(() => api.post(endpoint(capture.id, capture.release ? 'release' : 'capture'), capture.release ? undefined : { amount: Number(capture.amount) }), capture.release ? 'Freigabe angefragt. Der Anbieterstatus zeigt das Ergebnis.' : 'Einzug angefragt. Erst der bestätigte Einzug gleicht das Gastkonto aus.')) setCapture({ id: '', amount: '' }); }}><h4 className="is-wide">{capture.release ? 'Reservierten Kartenbetrag freigeben' : 'Reservierten Kartenbetrag einziehen'}</h4>{capture.release ? <p className="pms-muted is-wide">Die Vorautorisierung dieses Auftrags wird vollständig aufgehoben.</p> : <label>Betrag des Einzugs<input type="number" min={currencyStep(property.currencyCode)} max={rows.find((row) => String(row.id) === capture.id)?.amount} step={currencyStep(property.currencyCode)} value={capture.amount} onChange={(event) => setCapture({ ...capture, amount: event.target.value })} required /></label>}<div className="pms-form-actions is-wide"><button type="button" disabled={busy} onClick={() => setCapture({ id: '', amount: '' })}>Abbrechen</button><button type="submit" className="is-primary" disabled={busy || !canManage}>{capture.release ? 'Freigabe bestätigen' : 'Einzug bestätigen'}</button></div></form>}
    </section>;
}
