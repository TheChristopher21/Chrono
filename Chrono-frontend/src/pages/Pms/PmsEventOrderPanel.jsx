import { useCallback, useEffect, useRef, useState } from 'react';
import api from '../../utils/api.js';
import { currencyStep, formatPmsMoney } from './pmsMoney.js';
import { formatPmsDateTime } from './pmsFormatting.js';
import { FOLIO_ITEM_TYPE_LABELS } from './pmsTerminology.js';

const emptyLine = () => ({ description: '', quantity: 1, netUnitPrice: '', taxRate: '', type: 'SERVICE' });
export default function PmsEventOrderPanel({ property, bookings = [], folios = [], canManage, canFinance = canManage, onChanged }) {
    const [bookingId, setBookingId] = useState('');
    const [order, setOrder] = useState(null);
    const [draft, setDraft] = useState(null);
    const [folioId, setFolioId] = useState('');
    const [busy, setBusy] = useState(false);
    const [dirty, setDirty] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const dirtyRef = useRef(false);
    const loadVersion = useRef(0);
    const scope = useRef(''); scope.current = `${property.id}:${bookingId}`;
    const endpoint = `/api/pms/properties/${property.id}/resource-bookings/${bookingId}/event-order`;
    const load = useCallback(async (replaceDraft = false) => {
        const current = ++loadVersion.current;
        const expectedScope = `${property.id}:${bookingId}`;
        if (!bookingId) { setOrder(null); setDraft(null); return; }
        const { data } = await api.get(`/api/pms/properties/${property.id}/resource-bookings/${bookingId}/event-order`);
        if (current !== loadVersion.current || scope.current !== expectedScope) return;
        setOrder(data);
        if (replaceDraft || !dirtyRef.current) {
            setDraft({ setupMinutes: data.setupMinutes, teardownMinutes: data.teardownMinutes, agenda: data.agenda || '', setupInstructions: data.setupInstructions || '', cateringNotes: data.cateringNotes || '', lines: data.lines || [] });
            dirtyRef.current = false; setDirty(false);
        }
    }, [property.id, bookingId]);
    useEffect(() => { let active = true; load().catch((err) => { if (active) setError(err.response?.data?.detail || 'Veranstaltungsauftrag konnte nicht geladen werden.'); }); return () => { active = false; loadVersion.current++; }; }, [load]);
    const change = (next) => { dirtyRef.current = true; setDraft(next); setDirty(true); };
    const act = async (operation, message) => { if (busy) return; const started = scope.current; setBusy(true); setError(''); setNotice(''); try { await operation(); if (scope.current !== started) return; await load(true); await onChanged?.(); setNotice(message); } catch (err) { if (scope.current === started) setError(err.response?.data?.detail || 'Veranstaltungsauftrag konnte nicht gespeichert werden.'); } finally { if (scope.current === started) setBusy(false); } };
    const download = async () => { setBusy(true); setError(''); try { const { data } = await api.get(`${endpoint}/beo.pdf`, { responseType: 'blob' }); const url = URL.createObjectURL(data); const link = document.createElement('a'); link.href = url; link.download = `Veranstaltungsauftrag-${bookingId}.pdf`; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000); } catch (err) { setError('Der Veranstaltungsauftrag konnte nicht heruntergeladen werden.'); } finally { setBusy(false); } };
    const readonly = !canManage || order?.status === 'POSTED' || busy;
    return <section className="pms-work-card pms-enterprise-card pms-event-order">
        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Tagung & Bankett</span><h3>Veranstaltungsauftrag</h3><p className="pms-muted">Ablauf, Aufbau, Catering und Leistungen gemeinsam planen.</p></div>{order && <span className="pms-status-pill">{order.status === 'POSTED' ? 'Auf Gastkonto gebucht' : 'Entwurf'}</span>}</div>
        {error && <div className="pms-inline-message is-error" role="alert">{error}</div>}{notice && <div className="pms-inline-message is-success" role="status">{notice}</div>}
        <label>Veranstaltung<select value={bookingId} disabled={busy || dirty} onChange={(event) => { setBookingId(event.target.value); setFolioId(''); setError(''); setNotice(''); }}><option value="">Veranstaltung auswählen</option>{bookings.filter((entry) => entry.status !== 'CANCELLED').map((entry) => <option key={entry.id} value={entry.id}>{entry.title} · {formatPmsDateTime(entry.startAt)}</option>)}</select></label>
        {order && draft && <><div className="pms-day-checks"><div><span>{order.resourceName}</span><strong>{order.attendees} Gäste</strong><small>{order.organizerName}</small></div><div><span>Leistungen netto</span><strong>{formatPmsMoney(order.netAmount, order.currencyCode)}</strong><small>MwSt. {formatPmsMoney(order.taxAmount, order.currencyCode)}</small></div><div><span>Gesamtbetrag brutto</span><strong>{formatPmsMoney(order.grossAmount, order.currencyCode)}</strong><small>{dirty ? 'Gespeicherter Stand · Entwurf geändert' : 'Gespeicherter Stand'}</small></div></div>
            <p className="pms-muted">Belegte Zeit einschließlich Aufbau und Abbau: {formatPmsDateTime(order.occupiedFrom)} – {formatPmsDateTime(order.occupiedUntil)}</p>
            <form onSubmit={(event) => { event.preventDefault(); act(() => api.put(endpoint, { ...draft, setupMinutes: Number(draft.setupMinutes), teardownMinutes: Number(draft.teardownMinutes), lines: draft.lines.map(({ description, quantity, netUnitPrice, taxRate, type }) => ({ description, quantity: Number(quantity), netUnitPrice: Number(netUnitPrice), taxRate: Number(taxRate), type })) }), 'Veranstaltungsauftrag gespeichert und Raumbelegung geprüft.'); }}>
                <fieldset disabled={readonly} className="pms-plain-fieldset"><div className="pms-form-grid"><label>Aufbau vor Beginn (Minuten)<input type="number" min="0" max="1440" value={draft.setupMinutes} onChange={(event) => change({ ...draft, setupMinutes: event.target.value })} required /></label><label>Abbau nach Ende (Minuten)<input type="number" min="0" max="1440" value={draft.teardownMinutes} onChange={(event) => change({ ...draft, teardownMinutes: event.target.value })} required /></label><label className="is-wide">Ablauf / Agenda<textarea rows={4} value={draft.agenda} maxLength={5000} onChange={(event) => change({ ...draft, agenda: event.target.value })} placeholder="09:00 Empfang · 09:30 Tagung · 12:30 Mittagessen" /></label><label>Raumaufbau & Technik<textarea rows={3} value={draft.setupInstructions} maxLength={5000} onChange={(event) => change({ ...draft, setupInstructions: event.target.value })} /></label><label>Catering & Besonderheiten<textarea rows={3} value={draft.cateringNotes} maxLength={5000} onChange={(event) => change({ ...draft, cateringNotes: event.target.value })} /></label></div>
                <h4>Vereinbarte Leistungen</h4><div className="pms-event-lines">{draft.lines.map((line, index) => <div className="pms-event-line" key={line.id || index}><label>Leistung {index + 1}<input value={line.description} maxLength={250} onChange={(event) => change({ ...draft, lines: draft.lines.map((row, i) => i === index ? { ...row, description: event.target.value } : row) })} required /></label><label>Menge<input type="number" min="0.01" step="0.01" value={line.quantity} onChange={(event) => change({ ...draft, lines: draft.lines.map((row, i) => i === index ? { ...row, quantity: event.target.value } : row) })} required /></label><label>Einzelpreis netto<input type="number" min="0" step={currencyStep(property.currencyCode)} value={line.netUnitPrice} onChange={(event) => change({ ...draft, lines: draft.lines.map((row, i) => i === index ? { ...row, netUnitPrice: event.target.value } : row) })} required /></label><label>MwSt. %<input type="number" min="0" max="100" step="0.0001" value={line.taxRate} onChange={(event) => change({ ...draft, lines: draft.lines.map((row, i) => i === index ? { ...row, taxRate: event.target.value } : row) })} required /></label><label>Umsatzart<select value={line.type} onChange={(event) => change({ ...draft, lines: draft.lines.map((row, i) => i === index ? { ...row, type: event.target.value } : row) })}>{Object.entries(FOLIO_ITEM_TYPE_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><button type="button" aria-label={`Leistung ${index + 1} entfernen`} onClick={() => change({ ...draft, lines: draft.lines.filter((_, i) => i !== index) })}>×</button></div>)}</div>
                <div className="pms-form-actions"><button type="button" onClick={() => change({ ...draft, lines: [...draft.lines, emptyLine()] })}>Leistung hinzufügen</button><button type="submit" className="is-primary" disabled={!dirty}>Auftrag speichern</button></div></fieldset>
            </form>
            <div className="pms-form-actions">{dirty && <button type="button" disabled={busy} onClick={() => load(true).catch((err) => setError(err.response?.data?.detail || 'Auftrag konnte nicht neu geladen werden.'))}>Änderungen verwerfen</button>}<button type="button" disabled={busy || dirty} onClick={download}>Veranstaltungsauftrag als PDF</button></div>
            {order.status !== 'POSTED' && <form className="pms-form-grid pms-enterprise-form" onSubmit={(event) => { event.preventDefault(); act(() => api.post(`${endpoint}/post`, { folioId: folioId ? Number(folioId) : null }), 'Veranstaltungsleistungen auf das Gastkonto gebucht.'); }}><label className="is-wide">Abrechnungskonto<select value={folioId} disabled={!canFinance || busy || dirty} onChange={(event) => setFolioId(event.target.value)} required={!order.groupBookingId}><option value="">{order.groupBookingId ? 'Masterkonto der zugeordneten Gruppe' : 'Gastkonto auswählen'}</option>{folios.filter((folio) => folio.status === 'OPEN').map((folio) => <option key={folio.id} value={folio.id}>{folio.guestName} · {folio.label || `Konto ${folio.id}`}</option>)}</select></label><p className="pms-muted is-wide">Die gespeicherten Leistungen werden verbindlich gebucht. Der Auftrag kann danach nicht mehr verändert werden.</p><div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={!canFinance || busy || dirty || !order.lines.length}>Leistungen verbindlich buchen</button></div></form>}
        </>}
    </section>;
}
