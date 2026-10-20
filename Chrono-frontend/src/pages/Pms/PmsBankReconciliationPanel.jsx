import { useCallback, useEffect, useRef, useState } from 'react';
import api from '../../utils/api.js';
import { formatPmsDate, formatPmsDateTime } from './pmsFormatting.js';
import { formatPmsMoney } from './pmsMoney.js';
import './PmsBillingAutomationPanel.css';

const requestId = () => globalThis.crypto?.randomUUID?.() || `export-${Date.now()}-${Math.random().toString(36).slice(2)}`;
const problem = (error) => error.response?.data?.detail || 'Der Bankabgleich konnte nicht aktualisiert werden.';
const today = () => new Date().toLocaleDateString('en-CA');
const nextDay = (date) => { const value = new Date(`${date}T12:00:00Z`); value.setUTCDate(value.getUTCDate() + 1); return value.toISOString().slice(0, 10); };

export default function PmsBankReconciliationPanel({ propertyId, property, canManage = false, businessDate }) {
    const id = propertyId ?? property?.id;
    return id ? <BankPanel key={id} id={id} canManage={canManage} businessDate={businessDate} /> : null;
}

function BankPanel({ id, canManage, businessDate }) {
    const base = `/api/pms/properties/${id}/billing`;
    const [imports, setImports] = useState([]); const [batch, setBatch] = useState(null); const [file, setFile] = useState(null);
    const [matches, setMatches] = useState({}); const [confirm, setConfirm] = useState(false); const [query, setQuery] = useState(''); const [debtors, setDebtors] = useState([]);
    const [exports, setExports] = useState([]); const [from, setFrom] = useState(businessDate || today()); const [to, setTo] = useState(() => nextDay(businessDate || today()));
    const [exportIntent, setExportIntent] = useState(null); const [ackId, setAckId] = useState(null); const [reference, setReference] = useState('');
    const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [notice, setNotice] = useState('');
    const alive = useRef(true); const searchEpoch = useRef(0); const readEpoch = useRef(0); const inFlight = useRef(false);
    const batchRef = useRef(null); batchRef.current = batch?.id;
    useEffect(() => { alive.current = true; setBusy(inFlight.current); return () => { alive.current = false; readEpoch.current++; searchEpoch.current++; }; }, []);
    const refresh = useCallback(async () => {
        const epoch = ++readEpoch.current; const batchId = batchRef.current;
        const requests = [api.get(`${base}/bank-imports`), api.get(`${base}/export-runs`)];
        if (batchId) requests.push(api.get(`${base}/bank-imports/${batchId}`));
        const results = await Promise.allSettled(requests);
        if (!alive.current || epoch !== readEpoch.current) return;
        if (results[0].status === 'fulfilled' && Array.isArray(results[0].value.data)) setImports(results[0].value.data);
        if (results[1].status === 'fulfilled' && Array.isArray(results[1].value.data)) setExports(results[1].value.data);
        if (results[2]?.status === 'fulfilled' && batchRef.current === batchId && Array.isArray(results[2].value.data?.rows)) setBatch(results[2].value.data);
        const failed = results.find((result) => result.status === 'rejected'); if (failed) setError(problem(failed.reason));
    }, [base]);
    useEffect(() => { refresh(); }, [refresh]);
    const act = async (work, message) => {
        if (inFlight.current) return; inFlight.current = true; setBusy(true); setError(''); setNotice('');
        try { await work(); if (alive.current) { setNotice(message || ''); await refresh(); } }
        catch (failure) { if (alive.current) setError(problem(failure)); }
        finally { inFlight.current = false; if (alive.current) setBusy(false); }
    };
    const open = (data) => { if (!alive.current) return; setBatch(data); setMatches({}); setConfirm(false); };
    const search = async (event) => {
        event.preventDefault(); const epoch = ++searchEpoch.current;
        try { const { data } = await api.get(`/api/pms/properties/${id}/receivables`, { params: { openOnly: true, size: 100, query } }); if (alive.current && epoch === searchEpoch.current) setDebtors(data.items || []); }
        catch (failure) { if (alive.current && epoch === searchEpoch.current) setError(problem(failure)); }
    };
    const selected = Object.entries(matches).filter(([, value]) => value).map(([transactionId, receivableId]) => ({ transactionId: Number(transactionId), receivableId: Number(receivableId) }));
    const download = (run) => act(async () => {
        const { data } = await api.get(`${base}/export-runs/${run.id}/download`, { responseType: 'blob' }); if (!alive.current) return;
        const url = URL.createObjectURL(new Blob([data], { type: 'text/csv;charset=utf-8' })); const link = document.createElement('a'); link.href = url; link.download = `accounting-run-${run.id}.csv`; link.click(); URL.revokeObjectURL(url);
    }, 'Der gespeicherte Exportlauf wurde heruntergeladen.');
    return <section className="pms-work-card pms-billing-panel">
        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Bank und Buchhaltung</span><h3>Bankabgleich und Exportjournal</h3></div><button type="button" onClick={refresh} disabled={busy}>Aktualisieren</button></div>
        {error && <p role="alert" className="pms-inline-message is-error">{error}</p>}{notice && <p role="status" className="pms-inline-message is-success">{notice}</p>}
        <p className="pms-muted">Bankdateien erzeugen zunächst eine Vorschau. Nur bestätigte positive Bankgutschriften gleichen eine Forderung aus. Jede Bankreferenz wird je Hotel und Bankkonto einmal übernommen.</p>
        <details className="pms-enterprise-details"><summary>CSV-Format und Buchung</summary><p>UTF-8, Semikolon, ISO-Datum JJJJ-MM-TT, ISO-Währung und Dezimalpunkt. Zeichenketten dürfen mit doppelten Anführungszeichen eingeschlossen werden. Höchstens 2000 Zeilen und 5 MB.</p><code>bankAccount;transactionId;bookingDate;currency;amount;reference;debtorName</code><p>Die Referenz kann exakt die Rechnungsnummer oder QR-Referenz enthalten. Negative Beträge bleiben als „Ignoriert“ im Import; Erstattungen benötigen den bestehenden Erstattungsprozess. Es gibt keine automatische Währungsumrechnung. Bestätigte Ausgleiche buchen auf den offenen Betriebstag; der Bankbuchungstag bleibt im Import erhalten.</p></details>
        {canManage && <form onSubmit={(event) => { event.preventDefault(); if (!file) return; act(async () => { const input = new FormData(); input.append('file', file); const { data } = await api.post(`${base}/bank-imports`, input); open(data); }, 'Bankdatei eingelesen. Es wurde noch keine Forderung ausgeglichen.'); }}><label>Bankdatei<input type="file" accept=".csv,text/csv" required disabled={busy} onChange={(event) => setFile(event.target.files?.[0] || null)} /></label><button type="submit" disabled={busy || !file}>Importvorschau erstellen</button></form>}
        <div className="pms-form-grid"><label>Gespeicherter Bankimport<select value={batch?.id || ''} disabled={busy} onChange={(event) => { const value = event.target.value; if (value) act(async () => { const { data } = await api.get(`${base}/bank-imports/${value}`); open(data); }); }}><option value="">Bankimport auswählen</option>{imports.map((row) => <option key={row.id} value={row.id}>#{row.id} · {row.filename} · {row.rowCount} neue Transaktionen</option>)}</select></label></div>
        {batch && <><h4>{batch.filename} · Import #{batch.id}</h4><small>SHA-256: {batch.sha256}</small>
            {canManage && <form onSubmit={search} className="pms-form-actions"><label>Weitere offene Rechnung suchen<input value={query} onChange={(event) => setQuery(event.target.value)} maxLength={120} /></label><button type="submit" disabled={busy}>Forderungen suchen</button></form>}
            <div className="pms-table-scroll"><table className="pms-data-table"><thead><tr><th>Bankreferenz</th><th>Banktag</th><th>Betrag</th><th>Verwendungszweck</th><th>Zuordnung</th></tr></thead><tbody>{batch.rows.map((row) => {
                const options = new Map(); row.suggestions.forEach((r) => options.set(String(r.receivableId), { id: r.receivableId, invoiceNumber: r.invoiceNumber, organization: r.organization, balance: r.outstandingAmount })); debtors.forEach((r) => options.set(String(r.id), { id: r.id, invoiceNumber: r.invoiceNumber, organization: r.organizationName, balance: r.balance }));
                return <tr key={row.id}><td>{row.externalId}<small>{row.bankAccount}</small></td><td>{formatPmsDate(row.bookingDate)}</td><td>{formatPmsMoney(row.amount, row.currencyCode)}</td><td>{row.reference}<small>{row.debtorName}</small></td><td>{row.status === 'MATCHED' ? `Ausgeglichen · Forderung #${row.receivableId}` : row.status === 'IGNORED' ? 'Ignoriert: kein positiver Zahlungseingang' : canManage ? <><select aria-label={`Forderung für ${row.externalId}`} value={matches[row.id] || ''} disabled={busy} onChange={(event) => { setMatches({ ...matches, [row.id]: event.target.value }); setConfirm(false); }}><option value="">Noch nicht zugeordnet</option>{Array.from(options.values()).map((r) => <option key={r.id} value={r.id}>{r.invoiceNumber} · {r.organization} · #{r.id}</option>)}</select>{row.suggestions.length > 0 && <small>{row.suggestions.length} Vorschlag/Vorschläge aufgrund exakter Referenz</small>}</> : 'Offen'}</td></tr>;
            })}</tbody></table></div>
            {canManage && <div className="pms-billing-confirm"><p>{selected.length} Transaktionen zur Buchung ausgewählt.</p><label className="pms-billing-check"><input type="checkbox" checked={confirm} disabled={busy || !selected.length} onChange={(event) => setConfirm(event.target.checked)} />Bankeingänge und ausgewählte Rechnungen geprüft</label><button type="button" disabled={busy || !confirm || !selected.length} onClick={() => act(async () => { const { data } = await api.post(`${base}/bank-imports/${batch.id}/confirm`, { matches: selected }); open(data); }, 'Die bestätigten Bankeingänge wurden auf den Forderungen verbucht.')}>Ausgewählte Forderungen ausgleichen</button></div>}
        </>}
        <h4>Buchhaltungsexporte</h4><p className="pms-muted">Jeder Exportlauf bewahrt seine Originaldatei und Prüfsumme. Eine Empfangsbestätigung dokumentiert die Übernahme in die Buchhaltung; ein Download allein setzt diesen Status nicht.</p>
        {canManage && <form onSubmit={(event) => { event.preventDefault(); const intent = exportIntent || { requestId: requestId(), fromDate: from, toExclusive: to }; setExportIntent(intent); act(async () => { try { await api.post(`${base}/export-runs`, intent); } catch (failure) { if (failure.response?.status >= 400 && failure.response?.status < 500) setExportIntent(null); throw failure; } if (alive.current) setExportIntent(null); }, 'Exportlauf gespeichert.'); }}>
            <div className="pms-form-grid"><label>Ab Buchungstag<input type="date" required value={from} disabled={busy || Boolean(exportIntent)} onChange={(event) => setFrom(event.target.value)} /></label><label>Bis Datum (ausschließlich)<input type="date" required value={to} disabled={busy || Boolean(exportIntent)} onChange={(event) => setTo(event.target.value)} /></label></div><button type="submit" disabled={busy || !from || !to || to <= from}>{exportIntent ? 'Denselben Exportauftrag wiederholen' : 'Exportlauf erzeugen'}</button>
        </form>}
        <div className="pms-table-scroll"><table className="pms-data-table"><thead><tr><th>Lauf</th><th>Zeitraum</th><th>Status</th><th>Empfangsreferenz</th><th /></tr></thead><tbody>{exports.map((run) => <tr key={run.id}><td>#{run.id}<small>{formatPmsDateTime(run.createdAt)}</small><small title={run.sha256}>{run.sha256.slice(0, 16)}…</small></td><td>{formatPmsDate(run.fromDate)} – {formatPmsDate(run.toExclusive)} (exkl.)</td><td>{run.status === 'ACKNOWLEDGED' ? 'Übernahme bestätigt' : 'Erzeugt'}</td><td>{run.acknowledgementReference || '—'}</td><td><button type="button" disabled={busy} onClick={() => download(run)}>CSV herunterladen</button>{canManage && run.status !== 'ACKNOWLEDGED' && <button type="button" disabled={busy} onClick={() => { setAckId(run.id); setReference(''); }}>Übernahme bestätigen</button>}</td></tr>)}</tbody></table></div>
        {ackId && <form className="pms-billing-confirm" onSubmit={(event) => { event.preventDefault(); act(async () => { await api.post(`${base}/export-runs/${ackId}/ack`, { reference }); if (alive.current) setAckId(null); }, 'Übernahme in die Buchhaltung bestätigt.'); }}><label>Empfangs-/Buchungsreferenz für Lauf #{ackId}<input required maxLength={190} value={reference} disabled={busy} onChange={(event) => setReference(event.target.value)} /></label><button type="submit" disabled={busy || !reference.trim()}>Empfang verbindlich bestätigen</button><button type="button" disabled={busy} onClick={() => setAckId(null)}>Abbrechen</button></form>}
    </section>;
}
