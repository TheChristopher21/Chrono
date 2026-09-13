import { useCallback, useEffect, useRef, useState } from 'react';
import api from '../../utils/api.js';
import { formatPmsDate, formatPmsDateTime } from './pmsFormatting.js';
import { formatPmsMoney } from './pmsMoney.js';
import './PmsBillingAutomationPanel.css';

const statusText = { QUEUED: 'Eingeplant', SENDING: 'Übertragung läuft', SENT: 'Vom Mailserver angenommen', RETRY: 'Wiederholung geplant', UNKNOWN: 'Annahme unklar', FAILED: 'Fehlgeschlagen', PAUSED: 'Pausiert', CANCELLED: 'Verworfen' };
const requestId = () => globalThis.crypto?.randomUUID?.() || `billing-${Date.now()}-${Math.random().toString(36).slice(2)}`;
const problem = (error) => error.response?.data?.detail || 'Der Vorgang konnte nicht abgeschlossen werden. Bitte denselben Auftrag erneut versuchen.';

export default function PmsBillingAutomationPanel({ propertyId, property, canManage = false, canMaster = false, invoices = [] }) {
    const id = propertyId ?? property?.id;
    return id ? <BillingPanel key={id} propertyId={id} canManage={canManage} canMaster={canMaster} invoices={invoices} /> : null;
}

function BillingPanel({ propertyId, canManage, canMaster, invoices }) {
    const base = `/api/pms/properties/${propertyId}/billing`;
    const [settings, setSettings] = useState(null); const [deliveries, setDeliveries] = useState({ items: [], hasNext: false });
    const [candidates, setCandidates] = useState([]); const [history, setHistory] = useState([]); const [page, setPage] = useState(0);
    const [error, setError] = useState(''); const [notice, setNotice] = useState(''); const [busy, setBusy] = useState(false);
    const [invoiceId, setInvoiceId] = useState(''); const [recipient, setRecipient] = useState('');
    const [compose, setCompose] = useState({ recipient: '', subject: '', body: '' }); const [files, setFiles] = useState([]);
    const [invoiceIntent, setInvoiceIntent] = useState(() => { try { return JSON.parse(sessionStorage.getItem(`pms-invoice-send:${propertyId}`) || 'null'); } catch { return null; } });
    const [composeIntent, setComposeIntent] = useState(null); const [retryId, setRetryId] = useState(null); const [duplicateAck, setDuplicateAck] = useState(false);
    const alive = useRef(true); const dirty = useRef(false); const inFlight = useRef(false); const readEpoch = useRef(0);
    useEffect(() => { alive.current = true; setBusy(inFlight.current); return () => { alive.current = false; readEpoch.current++; }; }, []);
    const refresh = useCallback(async () => {
        const epoch = ++readEpoch.current;
        const results = await Promise.allSettled([api.get(`${base}/settings`), api.get(`${base}/deliveries?page=${page}&size=25`), api.get(`${base}/reminders/preview`), api.get(`${base}/reminders`)]);
        if (!alive.current || epoch !== readEpoch.current) return;
        if (results[0].status === 'fulfilled' && !dirty.current) setSettings(results[0].value.data);
        if (results[1].status === 'fulfilled') {
            if (Array.isArray(results[1].value.data?.items)) setDeliveries(results[1].value.data);
            else setError('Das Versandjournal ist unvollständig. Bitte erneut laden.');
        }
        if (results[2].status === 'fulfilled' && Array.isArray(results[2].value.data)) setCandidates(results[2].value.data);
        if (results[3].status === 'fulfilled' && Array.isArray(results[3].value.data)) setHistory(results[3].value.data);
        const failed = results.find((result) => result.status === 'rejected'); if (failed) setError(problem(failed.reason));
    }, [base, page]);
    useEffect(() => { refresh(); }, [refresh]);
    const act = async (work, message) => {
        if (inFlight.current) return; inFlight.current = true; setBusy(true); setError(''); setNotice('');
        try { await work(); if (alive.current) { setNotice(message); await refresh(); } }
        catch (failure) { if (alive.current) setError(problem(failure)); }
        finally { inFlight.current = false; if (alive.current) setBusy(false); }
    };
    const change = (key, value) => { dirty.current = true; setSettings((current) => ({ ...current, [key]: value })); };
    const input = (key, label, options = {}) => <label key={key}>{label}<input value={settings[key] ?? ''} disabled={!canMaster || busy} onChange={(event) => change(key, options.type === 'number' ? Number(event.target.value) : event.target.value)} {...options} /></label>;
    const toggle = (key, label) => <label className="pms-billing-check" key={key}><input type="checkbox" checked={Boolean(settings[key])} onChange={(event) => change(key, event.target.checked)} disabled={!canMaster || busy} />{label}</label>;
    const sendInvoice = (event) => {
        event.preventDefault(); if (!canManage) return;
        const intent = invoiceIntent || { invoiceId, recipient, requestId: requestId() };
        setInvoiceIntent(intent); try { sessionStorage.setItem(`pms-invoice-send:${propertyId}`, JSON.stringify(intent)); } catch { /* The server still deduplicates this mounted request. */ }
        act(async () => {
            try { await api.post(`${base}/invoices/${intent.invoiceId}/send`, { requestId: intent.requestId, recipient: intent.recipient || null }); }
            catch (failure) { if (failure.response?.status >= 400 && failure.response?.status < 500) { setInvoiceIntent(null); sessionStorage.removeItem(`pms-invoice-send:${propertyId}`); } throw failure; }
            setInvoiceIntent(null); sessionStorage.removeItem(`pms-invoice-send:${propertyId}`);
        }, 'Der unveränderte Rechnungsanhang wurde zum Versand eingeplant.');
    };
    const sendMessage = (event) => {
        event.preventDefault(); if (!canManage) return;
        const intent = composeIntent || { ...compose, files, requestId: requestId() }; setComposeIntent(intent);
        act(async () => {
            const data = new FormData(); ['requestId', 'recipient', 'subject', 'body'].forEach((key) => data.append(key, intent[key])); intent.files.forEach((file) => data.append('attachments', file));
            try { await api.post(`${base}/deliveries`, data); }
            catch (failure) { if (failure.response?.status >= 400 && failure.response?.status < 500) setComposeIntent(null); throw failure; }
            setComposeIntent(null); setCompose({ recipient: '', subject: '', body: '' }); setFiles([]);
        }, 'Die Nachricht samt Anhängen wurde zum Versand eingeplant.');
    };
    return <section className="pms-work-card pms-billing-panel">
        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Kommunikation und Debitoren</span><h3>Versand und Mahnwesen</h3></div><button type="button" disabled={busy} onClick={refresh}>Aktualisieren</button></div>
        {error && <p role="alert" className="pms-inline-message is-error">{error}</p>}{notice && <p role="status" className="pms-inline-message is-success">{notice}</p>}
        {settings && <details className="pms-enterprise-details"><summary>Hotelabsender, Vorlagen und Automatik</summary>
            <form onSubmit={(event) => { event.preventDefault(); if (canMaster) act(async () => { const { data } = await api.put(`${base}/settings`, settings); if (alive.current) { dirty.current = false; setSettings(data); } }, 'Versandregeln gespeichert.'); }}>
                <div className="pms-form-grid">{input('senderEmail', 'Absender-E-Mail', { type: 'email', maxLength: 190 })}{input('senderName', 'Absendername', { maxLength: 120 })}{input('replyTo', 'Antwortadresse', { type: 'email', maxLength: 190 })}
                    <label>Standardsprache der Rechnung<select value={settings.invoiceLanguage} disabled={!canMaster || busy} onChange={(event) => change('invoiceLanguage', event.target.value)}>{['DE', 'EN', 'FR', 'IT', 'ES'].map((language) => <option key={language}>{language}</option>)}</select></label>
                    {input('firstReminderDays', 'Erste Mahnung: Tage nach Fälligkeit', { type: 'number', min: 1, max: 365 })}{input('reminderIntervalDays', 'Abstand zwischen Mahnungen (Tage)', { type: 'number', min: 1, max: 365 })}{input('maxReminders', 'Maximale Mahnstufe', { type: 'number', min: 1, max: 12 })}</div>
                <div className="pms-billing-options">{toggle('mailEnabled', 'E-Mail-Versand für dieses Hotel aktivieren')}{toggle('automaticInvoices', 'Neue Rechnungen und Gutschriften automatisch versenden')}{toggle('automaticReminders', 'Fällige Mahnungen automatisch anlegen')}{toggle('sendReminders', 'Angelegte Mahnungen per E-Mail versenden')}</div>
                <p className="pms-muted">Der SMTP-Zugang wird vom Systembetrieb eingerichtet. Eine Serverannahme bestätigt noch keine Zustellung im Posteingang. Automatik ist freiwillig; neue Hotels beginnen ohne Versand.</p>
                <div className="pms-form-grid">{input('invoiceSubject', 'Rechnungsbetreff', { required: true, maxLength: 240 })}{input('reminderSubject', 'Mahnbetreff', { required: true, maxLength: 240 })}
                    {['invoiceBody', 'reminderBody'].map((key) => <label key={key}>{key === 'invoiceBody' ? 'Rechnungstext' : 'Mahntext'}<textarea value={settings[key] || ''} maxLength={8000} required disabled={!canMaster || busy} onChange={(event) => change(key, event.target.value)} /></label>)}</div>
                <p className="pms-muted">Platzhalter: {'{{hotelName}}, {{invoiceNumber}}, {{recipientName}}, {{amount}}, {{currency}}, {{dueDate}}, {{level}}'}.</p>
                {canMaster && <button type="submit" disabled={busy}>Versandregeln speichern</button>}
            </form>
        </details>}
        {canManage && <details className="pms-enterprise-details" open><summary>Rechnung per E-Mail</summary><form onSubmit={sendInvoice}>
            <div className="pms-form-grid"><label>Rechnung<select required value={invoiceIntent?.invoiceId || invoiceId} disabled={busy || Boolean(invoiceIntent)} onChange={(event) => setInvoiceId(event.target.value)}><option value="">Rechnung auswählen</option>{invoices.map((invoice) => <option key={invoice.id} value={invoice.id}>{invoice.invoiceNumber} · {invoice.recipientName}</option>)}</select></label>
                <label>Empfänger-E-Mail (leer: gespeicherte Rechnungsadresse)<input type="email" maxLength={190} value={invoiceIntent?.recipient ?? recipient} disabled={busy || Boolean(invoiceIntent)} onChange={(event) => setRecipient(event.target.value)} /></label></div>
            {invoiceIntent && <p role="status">Dieser Versandauftrag bleibt bis zur geklärten Antwort unverändert.</p>}<button type="submit" disabled={busy || (!invoiceId && !invoiceIntent)}>{invoiceIntent ? 'Denselben Rechnungsversand wiederholen' : 'Rechnung zum Versand einplanen'}</button>
        </form></details>}
        {canManage && <details className="pms-enterprise-details"><summary>Nachricht mit Anhängen</summary><form onSubmit={sendMessage}>
            <div className="pms-form-grid"><label>Empfänger<input required type="email" maxLength={190} value={compose.recipient} disabled={busy || Boolean(composeIntent)} onChange={(event) => setCompose({ ...compose, recipient: event.target.value })} /></label><label>Betreff<input required maxLength={240} value={compose.subject} disabled={busy || Boolean(composeIntent)} onChange={(event) => setCompose({ ...compose, subject: event.target.value })} /></label></div>
            <label>Nachricht<textarea required maxLength={16000} value={compose.body} disabled={busy || Boolean(composeIntent)} onChange={(event) => setCompose({ ...compose, body: event.target.value })} /></label>
            <label>Anhänge (PDF, Bilder oder Text, zusammen höchstens 8 MB)<input type="file" multiple accept=".pdf,.png,.jpg,.jpeg,.txt,.csv" disabled={busy || Boolean(composeIntent)} onChange={(event) => setFiles(Array.from(event.target.files || []))} /></label>
            <p className="pms-muted">{files.map((file) => file.name).join(', ')}</p><button type="submit" disabled={busy}>{composeIntent ? 'Denselben Nachrichtenauftrag wiederholen' : 'Nachricht zum Versand einplanen'}</button>
        </form></details>}
        <h4>Versandjournal</h4><div className="pms-table-scroll"><table className="pms-data-table"><thead><tr><th>Auftrag</th><th>Empfänger und Betreff</th><th>Status</th><th>Anhänge</th><th>Versuche</th><th /></tr></thead><tbody>{deliveries.items.map((job) => <tr key={job.id}><td>#{job.id}<small>{formatPmsDateTime(job.createdAt)}</small></td><td>{job.recipient}<small>{job.subject}</small></td><td>{statusText[job.status] || job.status}{job.lastError && <small>{job.lastError}</small>}{job.status === 'RETRY' && <small>{formatPmsDateTime(job.nextAttemptAt)}</small>}</td><td>{job.attachments?.map((attachment) => attachment.filename).join(', ') || '—'}</td><td>{job.attempts}</td><td>{canManage && ['FAILED', 'UNKNOWN', 'RETRY', 'PAUSED'].includes(job.status) && <button type="button" disabled={busy} onClick={() => { setRetryId(job.id); setDuplicateAck(job.status !== 'UNKNOWN'); }}>Erneut prüfen</button>}</td></tr>)}</tbody></table></div>
        {!deliveries.items.length && <p className="pms-muted">Noch keine Versandaufträge.</p>}
        <div className="pms-form-actions"><button type="button" disabled={!page || busy} onClick={() => setPage(page - 1)}>Vorherige Versandseite</button><span>Seite {page + 1}</span><button type="button" disabled={!deliveries.hasNext || busy} onClick={() => setPage(page + 1)}>Nächste Versandseite</button></div>
        {retryId && <div className="pms-billing-confirm" role="group" aria-label="Versandwiederholung prüfen"><p>Bei unklarer Serverannahme zuerst die Zustellung prüfen. Ein weiterer Versuch kann eine zweite Nachricht erzeugen.</p><label className="pms-billing-check"><input type="checkbox" checked={duplicateAck} onChange={(event) => setDuplicateAck(event.target.checked)} />Mögliche Doppelzustellung geprüft und akzeptiert</label><button disabled={busy || !duplicateAck} onClick={() => act(async () => { await api.post(`${base}/deliveries/${retryId}/retry`, { acknowledgePossibleDuplicate: duplicateAck }); if (alive.current) setRetryId(null); }, 'Wiederholung eingeplant.')} type="button">Versandwiederholung bestätigen</button><button type="button" onClick={() => setRetryId(null)}>Schließen</button></div>}
        <h4>Mahnvorschau</h4><p className="pms-muted">Es gelten der offene Betriebstag, das tatsächliche Datum und die gespeicherten Fristen. Bereits ausgeglichene oder geänderte Forderungen werden vor dem Versand erneut geprüft.</p>
        <div className="pms-table-scroll"><table className="pms-data-table"><thead><tr><th>Rechnung / Firma</th><th>Offen</th><th>Fällig</th><th>Nächste Stufe</th><th>Prüfung</th></tr></thead><tbody>{candidates.map((row) => <tr key={row.receivableId}><td>{row.invoiceNumber}<small>{row.organization}</small></td><td>{formatPmsMoney(row.outstandingAmount, row.currencyCode)}</td><td>{formatPmsDate(row.dueDate)}</td><td>{row.nextLevel} · ab {formatPmsDate(row.eligibleOn)}</td><td>{row.eligible ? 'Bereit' : row.reason}</td></tr>)}</tbody></table></div>
        {canManage && <button type="button" disabled={busy} onClick={() => act(async () => { const { data } = await api.post(`${base}/reminders/run`); if (data.skipped?.length) throw { response: { data: { detail: `${data.created} Mahnungen angelegt. ${data.skipped.join(' ')}` } } }; }, 'Mahnprüfung abgeschlossen. Das Mahnjournal enthält die neuen Vorgänge.')}>Fällige Mahnungen nach Hotelregel anlegen</button>}
        <details className="pms-enterprise-details"><summary>Letzte Mahnungen ({history.length})</summary>{history.map((row) => <p key={row.id}>{row.invoiceNumber} · Stufe {row.level} · {formatPmsDate(row.businessDate)} · {formatPmsMoney(row.outstandingAmount, row.currencyCode)} · {row.deliveryId ? `Versand #${row.deliveryId}` : 'Nur im Mahnjournal'}</p>)}</details>
    </section>;
}
