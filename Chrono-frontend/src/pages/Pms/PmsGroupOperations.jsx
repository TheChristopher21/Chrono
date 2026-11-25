import PmsDirectoryPicker from './PmsDirectoryPicker.jsx';
import { useCallback, useEffect, useRef, useState } from 'react';
import api from '../../utils/api.js';
import { formatPmsDate } from './pmsFormatting.js';
import { FOLIO_ITEM_TYPE_LABELS, RESERVATION_STATUS_LABELS } from './pmsTerminology.js';

export default function PmsGroupOperations({ property, groups = [], operations, canManage, canFinance = canManage, businessDate, onChanged }) {
    const [groupId, setGroupId] = useState('');
    const [directoryGroup, setDirectoryGroup] = useState(null);
    const [details, setDetails] = useState(null);
    const [types, setTypes] = useState([]);
    const [routingDirty, setRoutingDirty] = useState(false);
    const routingDirtyRef = useRef(false);
    const loadVersion = useRef(0);
    const scope = useRef(''); scope.current = `${property.id}:${groupId}`;
    const [selected, setSelected] = useState([]);
    const [results, setResults] = useState([]);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [allotment, setAllotment] = useState({ roomTypeId: '', quantity: 10, startDate: '', endDate: '', releaseDate: '' });
    const [guestQuery, setGuestQuery] = useState('');
    const [guests, setGuests] = useState([]);
    const [room, setRoom] = useState({ guestId: '', roomTypeId: '', ratePlanId: '', arrivalDate: '', departureDate: '', adults: 1, children: 0, childAges: [] });
    const group = directoryGroup && String(directoryGroup.id) === groupId ? directoryGroup : groups.find((entry) => String(entry.id) === groupId);
    const base = `/api/pms/properties/${property.id}/groups/${groupId}`;
    const load = useCallback(async (replaceRouting = false) => {
        const current = ++loadVersion.current;
        const expectedScope = `${property.id}:${groupId}`;
        if (!groupId) return;
        const [{ data }, groupResult] = await Promise.all([api.get(`/api/pms/properties/${property.id}/groups/${groupId}/operations`), api.get(`/api/pms/properties/${property.id}/directory/groups/${groupId}`)]);
        if (current !== loadVersion.current || scope.current !== expectedScope) return;
        setDetails(data);
        if (groupResult.data?.id != null) setDirectoryGroup(groupResult.data);
        if (replaceRouting || !routingDirtyRef.current) {
            setTypes(data.routedTypes || []); routingDirtyRef.current = false; setRoutingDirty(false);
        }
    }, [groupId, property.id]);
    useEffect(() => { let active = true; load().catch((err) => { if (active) setError(err.response?.data?.detail || 'Gruppenabläufe konnten nicht geladen werden.'); }); return () => { active = false; loadVersion.current++; }; }, [load]);
    useEffect(() => {
        if (guestQuery.trim().length < 2) { setGuests([]); return; }
        const controller = new AbortController();
        const timer = setTimeout(() => api.get(`/api/pms/properties/${property.id}/guests/search`, { params: { q: guestQuery, limit: 30 }, signal: controller.signal }).then(({ data }) => { if (!controller.signal.aborted) setGuests(data || []); }).catch(() => {}), 250);
        return () => { clearTimeout(timer); controller.abort(); };
    }, [guestQuery, property.id]);
    const perform = async (action, message, replaceRouting = false) => {
        if (busy) return;
        const started = scope.current;
        setBusy(true); setError(''); setNotice('');
        try { await action(); if (scope.current !== started) return; await load(replaceRouting); await onChanged?.(); setNotice(message); }
        catch (err) { if (scope.current === started) setError(err.response?.data?.detail || 'Der Gruppenablauf konnte nicht abgeschlossen werden.'); }
        finally { if (scope.current === started) setBusy(false); }
    };
    return <section className="pms-work-card pms-enterprise-card">
        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Gruppenbetrieb</span><h3>Kontingente, Teilnehmer & Sammelabrechnung</h3></div></div>
        {error && <div className="pms-inline-message is-error" role="alert">{error}</div>}{notice && <div className="pms-inline-message is-success" role="status">{notice}</div>}
        <PmsDirectoryPicker propertyId={property.id} kind="groups" label="Gruppe bearbeiten" value={groupId} initialOptions={groups} disabled={busy || routingDirty} placeholder="Gruppe auswählen" onChange={(id, value) => { setGroupId(id); setDirectoryGroup(value); setDetails(null); setSelected([]); setResults([]); setTypes([]); setGuestQuery(''); setGuests([]); setError(''); setNotice(''); if (value) { setAllotment({ roomTypeId: '', quantity: 10, startDate: value.arrivalDate, endDate: value.departureDate, releaseDate: value.arrivalDate }); setRoom({ guestId: '', roomTypeId: '', ratePlanId: '', arrivalDate: value.arrivalDate, departureDate: value.departureDate, adults: 1, children: 0, childAges: [] }); } }} />
        {group && details && <>
            <div className="pms-day-checks"><div><span>Benannte Zimmer</span><strong>{group.rooms?.length || 0}</strong></div><div><span>Kontingente</span><strong>{details.allotments.length}</strong></div><div><span>Gruppen-Masterkonto</span><strong>{details.masterFolioId ? `#${details.masterFolioId}` : '—'}</strong></div></div>
            <h4>Zimmerkontingente</h4><div className="pms-record-list">{details.allotments.map((entry) => <article className="pms-record" key={entry.id}><div><strong>{entry.quantity} × {entry.roomTypeName}</strong><small>{formatPmsDate(entry.startDate)} – {formatPmsDate(entry.endDate)} · Freigabe {formatPmsDate(entry.releaseDate)}</small><small>{entry.released ? 'Freigegeben' : `Noch gehaltene Zimmernächte: ${entry.nights.reduce((sum, night) => sum + Number(night.held), 0)}`}</small></div>{!entry.released && <button disabled={!canManage || busy} onClick={() => perform(() => api.post(`${base}/allotments/${entry.id}/release`), 'Restkontingent freigegeben.')} type="button">Rest freigeben</button>}</article>)}</div>
            {canManage && <details className="pms-enterprise-details"><summary>Kontingent ohne Gästeliste reservieren</summary><form className="pms-form-grid" onSubmit={(event) => { event.preventDefault(); perform(() => api.post(`${base}/allotments`, { ...allotment, roomTypeId: Number(allotment.roomTypeId), quantity: Number(allotment.quantity) }), 'Kontingent gespeichert und vom freien Bestand abgezogen.'); }}><label>Zimmertyp<select value={allotment.roomTypeId} onChange={(event) => setAllotment({ ...allotment, roomTypeId: event.target.value })} required><option value="">Zimmertyp wählen</option>{property.roomTypes?.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}</select></label><label>Zimmer pro Nacht<input type="number" min="1" max="10000" value={allotment.quantity} onChange={(event) => setAllotment({ ...allotment, quantity: event.target.value })} required /></label>{[['startDate', 'Anreise'], ['endDate', 'Abreise'], ['releaseDate', 'Restkontingent freigeben am']].map(([key, label]) => <label key={key}>{label}<input type="date" value={allotment[key]} onChange={(event) => setAllotment({ ...allotment, [key]: event.target.value })} required /></label>)}<div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={busy}>Bestand prüfen & Kontingent speichern</button></div></form></details>}
            <h4>Teilnehmerliste</h4><div className="pms-table-scroll"><table className="pms-access-table"><thead><tr><th>Auswahl</th><th>Gast</th><th>Zimmer</th><th>Aufenthalt</th><th>Status</th></tr></thead><tbody>{group.rooms?.map((member) => <tr key={member.reservationId}><td><input type="checkbox" aria-label={`${member.guestName} auswählen`} checked={selected.includes(member.reservationId)} onChange={(event) => setSelected(event.target.checked ? [...selected, member.reservationId] : selected.filter((id) => id !== member.reservationId))} /></td><td>{member.guestName}<small className="pms-table-subline">{member.confirmationCode}</small></td><td>{member.roomNumber || 'Noch offen'}</td><td>{formatPmsDate(member.arrivalDate || group.arrivalDate)} – {formatPmsDate(member.departureDate || group.departureDate)}</td><td>{RESERVATION_STATUS_LABELS[member.status] || member.status}</td></tr>)}</tbody></table></div>
            <div className="pms-form-actions"><button type="button" onClick={() => setSelected(group.rooms?.map((member) => member.reservationId) || [])}>Alle auswählen</button>{[['CHECK_IN', 'Auswahl einchecken'], ['CHECK_OUT', 'Auswahl auschecken']].map(([action, label]) => <button type="button" key={action} disabled={!canManage || busy || !selected.length} onClick={() => perform(async () => { const { data } = await api.post(`${base}/bulk-operation`, { action, reservationIds: selected, businessDate }); setResults(data || []); }, 'Gruppenaktion geprüft. Ergebnisse je Reservierung stehen unten.')}>{label}</button>)}</div>
            {results.length > 0 && <ul className="pms-check-list">{results.map((result) => <li key={result.reservationId}>#{result.reservationId}: {result.success ? 'Erledigt' : 'Zu klären'} · {result.message}</li>)}</ul>}
            {canManage && <details className="pms-enterprise-details"><summary>Gast zur Zimmerliste hinzufügen</summary><form className="pms-form-grid" onSubmit={(event) => { event.preventDefault(); perform(() => api.post(`${base}/rooming-list`, { rooms: [{ ...room, guestId: Number(room.guestId), roomTypeId: Number(room.roomTypeId), ratePlanId: Number(room.ratePlanId), adults: Number(room.adults), children: Number(room.children), childAges: room.childAges.map(Number), roomId: null, source: 'DIRECT' }] }), 'Teilnehmer hinzugefügt und Kontingent abgerufen.'); }}><label>Gast suchen<input value={guestQuery} onChange={(event) => setGuestQuery(event.target.value)} placeholder="Name oder E-Mail" /></label><label>Gast auswählen<select value={room.guestId} onChange={(event) => setRoom({ ...room, guestId: event.target.value })} required><option value="">Gast auswählen</option>{guests.map((guest) => <option value={guest.id} key={guest.id}>{guest.firstName} {guest.lastName}</option>)}</select></label><label>Zimmertyp<select value={room.roomTypeId} onChange={(event) => setRoom({ ...room, roomTypeId: event.target.value, ratePlanId: '' })} required><option value="">Zimmertyp</option>{property.roomTypes?.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}</select></label><label>Rate<select value={room.ratePlanId} onChange={(event) => setRoom({ ...room, ratePlanId: event.target.value })} required><option value="">Rate</option>{operations.ratePlans?.filter((rate) => String(rate.roomTypeId) === room.roomTypeId && rate.active).map((rate) => <option key={rate.id} value={rate.id}>{rate.name}</option>)}</select></label>{[['arrivalDate', 'Anreise'], ['departureDate', 'Abreise']].map(([key, label]) => <label key={key}>{label}<input type="date" value={room[key]} onChange={(event) => setRoom({ ...room, [key]: event.target.value })} required /></label>)}{[['adults', 'Erwachsene'], ['children', 'Kinder']].map(([key, label]) => <label key={key}>{label}<input type="number" min={key === 'adults' ? 1 : 0} max="20" value={room[key]} onChange={(event) => setRoom({ ...room, [key]: event.target.value, ...(key === 'children' ? { childAges: Array.from({ length: Math.min(20, Math.max(0, Number(event.target.value) || 0)) }, (_, index) => room.childAges[index] ?? '') } : {}) })} required /></label>)}{room.childAges.map((age, index) => <label key={index}>Alter Kind {index + 1}<input type="number" min="0" max="17" value={age} onChange={(event) => setRoom({ ...room, childAges: room.childAges.map((value, i) => i === index ? event.target.value : value) })} required /></label>)}<div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={busy}>Teilnehmer reservieren</button></div></form></details>}
            <details className="pms-enterprise-details"><summary>Leistungen automatisch auf Gruppen-Masterkonto buchen</summary><p className="pms-muted">Wähle die Leistungsarten, die die Gruppe übernimmt. Andere Leistungen bleiben auf dem jeweiligen Gastkonto. Bereits fakturierte Leistungen bleiben unverändert.</p><div className="pms-routing-options">{Object.entries(FOLIO_ITEM_TYPE_LABELS).map(([key, label]) => <label className="pms-check-row" key={key}><input type="checkbox" disabled={!canFinance || busy} checked={types.includes(key)} onChange={(event) => { routingDirtyRef.current = true; setRoutingDirty(true); setTypes(event.target.checked ? [...types, key] : types.filter((value) => value !== key)); }} />{label}</label>)}</div><div className="pms-form-actions"><button type="button" disabled={!canFinance || busy} className="is-primary" onClick={() => perform(() => api.put(`${base}/routing`, { types }), 'Leistungszuordnung zum Gruppen-Masterkonto gespeichert.', true)}>Zuordnung speichern</button>{routingDirty && <button type="button" disabled={busy} onClick={() => load(true).catch((err) => setError(err.response?.data?.detail || 'Zuordnung konnte nicht neu geladen werden.'))}>Zuordnung verwerfen</button>}</div></details>
        </>}
    </section>;
}
