import PmsDirectoryPicker from './PmsDirectoryPicker.jsx';
import { useCallback, useEffect, useRef, useState } from 'react';
import api from '../../utils/api.js';
import { formatPmsDate } from './pmsFormatting.js';
import usePmsLiveRefresh from './usePmsLiveRefresh.js';
import { currencyStep } from './pmsMoney.js';

export default function PmsReceivablesWorkspace({ property, invoices = [], organizations = [], canManage, canRefund = canManage, canManageSettings, onChanged }) {
    const [receivables, setReceivables] = useState([]);
    const [accounts, setAccounts] = useState([]);
    const [query, setQuery] = useState('');
    const [search, setSearch] = useState('');
    const [page, setPage] = useState(0);
    const [pageInfo, setPageInfo] = useState({ totalElements: 0, hasNext: false, totalOutstanding: 0, totalOverdue: 0 });
    const loadVersion = useRef(0);
    const [overdueOnly, setOverdueOnly] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [transfer, setTransfer] = useState({ invoiceId: '', organizationId: '' });
    const [credit, setCredit] = useState({ organizationId: '', enabled: true, creditLimit: '', paymentTermsDays: 30 });
    const [settlement, setSettlement] = useState({ id: '', amount: '', bankReference: '', requestId: crypto.randomUUID(), refund: false });
    const [reminder, setReminder] = useState({ id: '', note: '' });
    const [settlementIntent, setSettlementIntent] = useState(null);
    const scope = useRef(property.id); scope.current = property.id;
    useEffect(() => { if (query === search) return; const timer = setTimeout(() => { setSearch(query); setPage(0); }, 250); return () => clearTimeout(timer); }, [query, search]);
    const load = useCallback(async () => {
        const version = ++loadVersion.current;
        const [ar, credits] = await Promise.all([
            api.get(`/api/pms/properties/${property.id}/receivables`, { params: { openOnly: false, overdueOnly, query: search, page, size: 50 } }),
            api.get(`/api/pms/properties/${property.id}/credit-accounts`),
        ]);
        if (version !== loadVersion.current || scope.current !== property.id) return;
        if (!Array.isArray(ar.data?.items) || !Array.isArray(credits.data)) throw new Error('Ungültige Forderungsübersicht');
        setReceivables(ar.data.items); setPageInfo(ar.data); setAccounts(credits.data);
    }, [property.id, overdueOnly, search, page]);
    useEffect(() => { let active = true; load().catch((err) => { if (active) setError(err.response?.data?.detail || 'Firmenforderungen konnten nicht geladen werden.'); }); return () => { active = false; loadVersion.current++; }; }, [load]);
    usePmsLiveRefresh(load, { enabled: !busy });
    const money = (value) => new Intl.NumberFormat('de-CH', { style: 'currency', currency: property.currencyCode }).format(Number(value || 0));
    const filtered = receivables;
    const perform = async (action, message) => {
        if (busy) return false;
        const started = property.id;
        setBusy(true); setError(''); setNotice('');
        try { await action(); if (scope.current !== started) return false; await load(); await onChanged?.(); setNotice(message); return true; }
        catch (err) { setError(err.response?.data?.detail || 'Der Vorgang konnte nicht gespeichert werden.'); return false; }
        finally { setBusy(false); }
    };
    const postSettlement = async (event) => {
        event.preventDefault(); if (busy || !((settlementIntent?.refund ?? settlement.refund) ? canRefund : canManage)) return;
        const intent = settlementIntent || { propertyId: property.id, id: settlement.id, refund: settlement.refund,
            payload: { amount: Number(settlement.amount), bankReference: settlement.bankReference, requestId: settlement.requestId } };
        setSettlementIntent(intent);
        await perform(async () => {
            try { await api.post(`/api/pms/properties/${intent.propertyId}/receivables/${intent.id}/${intent.refund ? 'refunds' : 'settlements'}`, intent.payload); }
            catch (failure) { if ([400,401,403,404,422].includes(failure?.response?.status)) setSettlementIntent(null); throw failure; }
            if (scope.current !== intent.propertyId) return;
            setSettlementIntent(null); setSettlement((current) => ({ ...current, id: '' }));
        }, 'Bankbewegung der Firmenforderung zugeordnet.');
    };
    return <section className="pms-work-card pms-enterprise-card">
        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Firmenabrechnung</span><h3>Offene Posten & Zahlungsziele</h3></div><span className="pms-status-pill">{property.currencyCode}</span></div>
        {error && <div className="pms-inline-message is-error" role="alert">{error}</div>}{notice && <div className="pms-inline-message is-success" role="status">{notice}</div>}
        <div className="pms-day-checks"><div><span>Offene Forderungen</span><strong>{money(pageInfo.totalOutstanding)}</strong></div><div className="needs-attention"><span>Davon überfällig</span><strong>{money(pageInfo.totalOverdue)}</strong></div><div><span>Freigegebene Firmen</span><strong>{accounts.filter((account) => account.enabled).length}</strong></div></div>
        <div className="pms-enterprise-toolbar"><label>Firma oder Rechnungsnummer<input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Offene Posten durchsuchen" /></label><label className="pms-check-row"><input type="checkbox" checked={overdueOnly} onChange={(event) => { setOverdueOnly(event.target.checked); setPage(0); }} />Nur überfällige Forderungen</label></div>
        <div className="pms-table-scroll"><table className="pms-access-table"><thead><tr><th>Rechnung / Firma</th><th>Fällig am</th><th>Offener Betrag</th><th>Status</th><th>Aktionen</th></tr></thead><tbody>{filtered.map((entry) => <tr key={entry.id}><td><strong>{entry.invoiceNumber}</strong><small className="pms-table-subline">{entry.organizationName}</small></td><td>{formatPmsDate(entry.dueDate)}{entry.daysOverdue > 0 && Number(entry.balance) > 0 && <small className="pms-table-subline">{entry.daysOverdue} Tage überfällig</small>}</td><td>{money(entry.balance)}</td><td>{Number(entry.balance) > 0 ? `Offen · Mahnstufe ${entry.reminderLevel}` : Number(entry.balance) < 0 ? 'Guthaben' : 'Ausgeglichen'}</td><td><div className="pms-record-actions">{Number(entry.balance) !== 0 && <button type="button" disabled={!(Number(entry.balance) < 0 ? canRefund : canManage) || busy || Boolean(settlementIntent)} onClick={() => { setSettlement({ id: String(entry.id), amount: String(Math.abs(Number(entry.balance))), bankReference: '', requestId: crypto.randomUUID(), refund: Number(entry.balance) < 0 }); setReminder({ id: '', note: '' }); }}>{Number(entry.balance) < 0 ? 'Guthaben auszahlen' : 'Zahlung zuordnen'}</button>}{Number(entry.balance) > 0 && <button type="button" disabled={!canManage || busy || Boolean(settlementIntent)} onClick={() => { setReminder({ id: String(entry.id), note: '' }); setSettlement((current) => ({ ...current, id: '' })); }}>Mahnung dokumentieren</button>}</div></td></tr>)}</tbody></table>{!filtered.length && <p className="pms-muted pms-table-empty">Keine passenden Firmenforderungen.</p>}</div>
        <div className="pms-pagination"><span>{pageInfo.totalElements} Forderungen · Seite {page + 1}</span><button type="button" disabled={busy || page === 0} onClick={() => setPage(page - 1)}>Zurück</button><button type="button" disabled={busy || !pageInfo.hasNext} onClick={() => setPage(page + 1)}>Weiter</button></div>
        {settlement.id && <form className="pms-form-grid pms-enterprise-form" onSubmit={postSettlement}><h4 className="is-wide">{settlement.refund ? 'Ausgeführte Firmenrückzahlung erfassen' : 'Eingegangene Firmenzahlung zuordnen'}</h4><label>Betrag<input type="number" min={currencyStep(property.currencyCode)} step={currencyStep(property.currencyCode)} value={settlement.amount} disabled={busy || Boolean(settlementIntent)} onChange={(event) => setSettlement({ ...settlement, amount: event.target.value })} required /></label><label>Bankreferenz<input value={settlement.bankReference} disabled={busy || Boolean(settlementIntent)} onChange={(event) => setSettlement({ ...settlement, bankReference: event.target.value })} required maxLength={180} /></label><div className="pms-form-actions is-wide"><button type="button" disabled={busy || Boolean(settlementIntent)} onClick={() => setSettlement((current) => ({ ...current, id: '' }))}>Abbrechen</button><button type="submit" className="is-primary" disabled={busy || !((settlementIntent?.refund ?? settlement.refund) ? canRefund : canManage)}>{settlementIntent ? 'Dieselbe Bankbewegung erneut prüfen' : 'Bankbewegung buchen'}</button></div></form>}
        {reminder.id && <form className="pms-form-grid pms-enterprise-form" onSubmit={async (event) => { event.preventDefault(); if (await perform(() => api.post(`/api/pms/properties/${property.id}/receivables/${reminder.id}/reminders`, { note: reminder.note }), 'Mahnung im Forderungsjournal dokumentiert.')) setReminder({ id: '', note: '' }); }}><label className="is-wide">Mahnung / Kontakt dokumentieren<textarea value={reminder.note} onChange={(event) => setReminder({ ...reminder, note: event.target.value })} placeholder="Kontaktweg, Datum und Vereinbarung" maxLength={500} required /></label><p className="pms-muted is-wide">Dieser Eintrag dokumentiert den Vorgang. Er versendet keine Nachricht an die Firma.</p><div className="pms-form-actions is-wide"><button type="button" onClick={() => setReminder({ id: '', note: '' })}>Abbrechen</button><button type="submit" className="is-primary" disabled={!canManage || busy}>Mahnung dokumentieren</button></div></form>}
        {canManage && <details className="pms-enterprise-details"><summary>Rechnung auf Firmenkredit übertragen</summary><p className="pms-muted">Der Gast kann mit ausgeglichenem Gastkonto abreisen. Die Forderung bleibt bis zum Zahlungseingang bei der freigegebenen Firma offen.</p><form className="pms-form-grid" onSubmit={async (event) => { event.preventDefault(); if (await perform(() => api.post(`/api/pms/properties/${property.id}/invoices/${transfer.invoiceId}/direct-bill`, { organizationId: Number(transfer.organizationId) }), 'Rechnung auf Firmenkredit übertragen.')) setTransfer({ invoiceId: '', organizationId: '' }); }}><label>Rechnung<select value={transfer.invoiceId} onChange={(event) => setTransfer({ ...transfer, invoiceId: event.target.value })} required><option value="">Rechnung auswählen</option>{invoices.filter((invoice) => invoice.type === 'INVOICE' && invoice.status === 'ISSUED').map((invoice) => <option value={invoice.id} key={invoice.id}>{invoice.invoiceNumber} · {invoice.recipientName}</option>)}</select></label><label>Freigegebene Firma<select value={transfer.organizationId} onChange={(event) => setTransfer({ ...transfer, organizationId: event.target.value })} required><option value="">Firma auswählen</option>{accounts.filter((account) => account.enabled).map((account) => <option value={account.organizationId} key={account.organizationId}>{account.organizationName} · {account.paymentTermsDays} Tage</option>)}</select></label><div className="pms-form-actions is-wide"><button type="submit" disabled={busy} className="is-primary">Kreditlimit prüfen & übertragen</button></div></form></details>}
        {canManageSettings && <details className="pms-enterprise-details"><summary>Firmenkredit freigeben & Limits verwalten</summary><form className="pms-form-grid" onSubmit={(event) => { event.preventDefault(); perform(() => api.put(`/api/pms/properties/${property.id}/organizations/${credit.organizationId}/credit-account`, { enabled: credit.enabled, creditLimit: Number(credit.creditLimit), paymentTermsDays: Number(credit.paymentTermsDays) }), 'Firmenkredit gespeichert.'); }}><PmsDirectoryPicker propertyId={property.id} label="Firma" value={credit.organizationId} initialOptions={organizations} required placeholder="Firma auswählen" onChange={(value) => { const account = accounts.find((entry) => String(entry.organizationId) === value); setCredit({ organizationId: value, enabled: account?.enabled ?? true, creditLimit: account?.creditLimit ?? '', paymentTermsDays: account?.paymentTermsDays ?? 30 }); }} /><label>Kreditlimit ({property.currencyCode})<input type="number" min="0" step={currencyStep(property.currencyCode)} value={credit.creditLimit} onChange={(event) => setCredit({ ...credit, creditLimit: event.target.value })} required /></label><label>Zahlungsziel (Tage)<input type="number" min="0" max="365" value={credit.paymentTermsDays} onChange={(event) => setCredit({ ...credit, paymentTermsDays: event.target.value })} required /></label><label className="pms-check-row"><input type="checkbox" checked={credit.enabled} onChange={(event) => setCredit({ ...credit, enabled: event.target.checked })} />Firmenkredit freigegeben</label><div className="pms-form-actions is-wide"><button type="submit" disabled={busy} className="is-primary">Freigabe speichern</button></div></form></details>}
    </section>;
}
