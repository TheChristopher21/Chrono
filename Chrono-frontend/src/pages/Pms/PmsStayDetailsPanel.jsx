import { useCallback, useEffect, useRef, useState } from 'react';
import api from '../../utils/api.js';
import { formatPmsDate } from './pmsFormatting.js';
import { hotelToday } from './pmsRoomPlan.js';
import PmsReservationPolicyPanel from './PmsReservationPolicyPanel.jsx';

export default function PmsStayDetailsPanel({ reservation, property, operations, canManage, businessDate, onClose, onOperationsChange }) {
    const panel = useRef(null);
    const [details, setDetails] = useState(null);
    const [coGuests, setCoGuests] = useState([]);
    const coGuestsDirty = useRef(false);
    const loadVersion = useRef(0);
    const scope = useRef(reservation.id); scope.current = reservation.id;
    const editCoGuests = (value) => { coGuestsDirty.current = true; setCoGuests(value); };
    const [query, setQuery] = useState('');
    const [matches, setMatches] = useState([]);
    const [move, setMove] = useState({ roomId: '', ratePlanId: '', effectiveDate: [reservation.arrivalDate, hotelToday(property.timezone)].sort().at(-1), reason: '' });
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [registration, setRegistration] = useState({ guestId: '', addressLine: '', postalCode: '', city: '', countryCode: '', nationalityCode: '', documentNumber: '', signatureName: '', privacyConsent: false });
    const load = useCallback(async () => {
        const current = ++loadVersion.current;
        const { data } = await api.get(`/api/pms/reservations/${reservation.id}/stay-details`);
        if (current !== loadVersion.current || scope.current !== reservation.id) return;
        setDetails(data); if (!coGuestsDirty.current) setCoGuests(data.coGuests || []);
    }, [reservation.id]);
    useEffect(() => {
        const previous = document.activeElement;
        panel.current?.focus();
        load().catch((err) => setError(err.response?.data?.detail || 'Aufenthaltsdetails konnten nicht geladen werden.'));
        return () => { loadVersion.current++; if (previous?.isConnected) previous.focus(); };
    }, [load]);
    useEffect(() => {
        if (query.trim().length < 2) { setMatches([]); return; }
        const controller = new AbortController();
        const timer = setTimeout(() => api.get(`/api/pms/properties/${property.id}/guests/search`, { params: { q: query, limit: 30 }, signal: controller.signal })
            .then(({ data }) => { if (!controller.signal.aborted) setMatches(Array.isArray(data) ? data : data?.content || []); })
            .catch(() => { if (!controller.signal.aborted) setMatches([]); }), 250);
        return () => { clearTimeout(timer); controller.abort(); };
    }, [property.id, query]);
    const selectedRoom = operations.rooms?.find((room) => String(room.id) === move.roomId);
    const act = async (action, message) => {
        setBusy(true); setError(''); setNotice('');
        try { await action(); await load(); setNotice(message); }
        catch (err) { setError(err.response?.data?.detail || 'Die Änderung konnte nicht gespeichert werden.'); }
        finally { setBusy(false); }
    };
    const keyboard = (event) => {
        if (event.key === 'Escape' && !busy) onClose();
        if (event.key !== 'Tab') return;
        const nodes = [...panel.current.querySelectorAll('button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled])')];
        if (event.shiftKey && (document.activeElement === nodes[0] || document.activeElement === panel.current)) { event.preventDefault(); nodes.at(-1)?.focus(); }
        else if (!event.shiftKey && document.activeElement === nodes.at(-1)) { event.preventDefault(); nodes[0]?.focus(); }
    };
    return <div className="pms-financial-dialog-backdrop"><section ref={panel} tabIndex={-1} className="pms-financial-dialog pms-stay-dialog" role="dialog" aria-modal="true" aria-labelledby="pms-stay-title" onKeyDown={keyboard}>
        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">{reservation.confirmationCode} · {reservation.guestName}</span><h3 id="pms-stay-title">Aufenthalt & Mitreisende</h3><p className="pms-muted">{formatPmsDate(reservation.arrivalDate)} – {formatPmsDate(reservation.departureDate)}</p></div><button type="button" aria-label="Aufenthaltsdetails schließen" onClick={onClose} disabled={busy}>×</button></div>
        {error && <div className="pms-inline-message is-error" role="alert">{error}</div>}{notice && <div className="pms-inline-message is-success" role="status">{notice}</div>}
        {!details ? <p role="status">Aufenthaltsdetails werden geladen …</p> : <>
            <PmsReservationPolicyPanel reservationId={reservation.id} currencyCode={property.currencyCode} />
            <h4>Zimmer im Verlauf des Aufenthalts</h4><div className="pms-stay-segments">{details.roomSegments.map((segment) => <article key={segment.id || `${segment.roomId}-${segment.startDate}`}><span className="pms-status-pill">Zimmer {segment.roomNumber}</span><strong>{formatPmsDate(segment.startDate)} – {formatPmsDate(segment.endDate)}</strong><small>{segment.roomTypeName}{segment.reason ? ` · ${segment.reason}` : ''}</small></article>)}</div>
            {canManage && !['CHECKED_OUT', 'CANCELLED', 'NO_SHOW'].includes(reservation.status) && <details className="pms-enterprise-details"><summary>Zimmerwechsel oder Upgrade planen</summary><form className="pms-form-grid" onSubmit={(event) => { event.preventDefault(); act(async () => {
                const { data } = await api.post(`/api/pms/reservations/${reservation.id}/move-room`, { ...move, roomId: Number(move.roomId), ratePlanId: move.ratePlanId ? Number(move.ratePlanId) : null }, { params: { businessDate } });
                onOperationsChange?.(data);
            }, 'Zimmerwechsel gespeichert. Frühere Zimmerabschnitte bleiben erhalten.'); }}>
                <label>Wechsel ab<input type="date" value={move.effectiveDate} min={reservation.arrivalDate} max={reservation.departureDate} onChange={(event) => setMove({ ...move, effectiveDate: event.target.value })} required /></label>
                <label>Zielzimmer<select value={move.roomId} onChange={(event) => setMove({ ...move, roomId: event.target.value, ratePlanId: '' })} required><option value="">Zimmer auswählen</option>{operations.rooms?.filter((room) => room.operationalStatus === 'IN_SERVICE').map((room) => <option key={room.id} value={room.id}>{room.number} · {room.roomTypeName}</option>)}</select></label>
                <label className="is-wide">Rate für den neuen Abschnitt<select value={move.ratePlanId} onChange={(event) => setMove({ ...move, ratePlanId: event.target.value })} required={Boolean(selectedRoom && selectedRoom.roomTypeId !== reservation.roomTypeId)}><option value="">Bestehende Rate beibehalten (gleicher Zimmertyp)</option>{operations.ratePlans?.filter((rate) => rate.active && (!selectedRoom || rate.roomTypeId === selectedRoom.roomTypeId)).map((rate) => <option key={rate.id} value={rate.id}>{rate.name}</option>)}</select></label>
                <label className="is-wide">Grund<input value={move.reason} onChange={(event) => setMove({ ...move, reason: event.target.value })} required maxLength={500} placeholder="Zum Beispiel Gästewunsch oder Upgrade" /></label><div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={busy}>Verfügbarkeit prüfen & Wechsel speichern</button></div>
            </form></details>}
            <h4>Mitreisende</h4><p className="pms-muted">Der Hauptgast bleibt der Reservierung zugeordnet. Weitere Gäste erhalten eigene Aufenthaltsdaten und Anmeldungen.</p>
            {canManage && <div className="pms-guest-picker"><label>Weiteren Gast suchen<input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Name oder E-Mail, mindestens 2 Zeichen" /></label>{matches.filter((guest) => guest.id !== reservation.guestId && !coGuests.some((entry) => entry.guestId === guest.id)).map((guest) => <button type="button" key={guest.id} onClick={() => { editCoGuests([...coGuests, { guestId: guest.id, guestName: `${guest.firstName} ${guest.lastName}`, arrivalDate: reservation.arrivalDate, departureDate: reservation.departureDate, child: false }]); setQuery(''); }}>{guest.firstName} {guest.lastName} <span>Hinzufügen</span></button>)}</div>}
            <form onSubmit={(event) => { event.preventDefault(); act(async () => { await api.put(`/api/pms/reservations/${reservation.id}/co-guests`, { guests: coGuests.map(({ guestId, arrivalDate, departureDate, child }) => ({ guestId, arrivalDate, departureDate, child })) }); coGuestsDirty.current = false; }, 'Mitreisende gespeichert.'); }}>
                {!coGuests.length && <p className="pms-inline-note">Bisher sind keine weiteren Gäste hinterlegt.</p>}
                <div className="pms-coguest-list">{coGuests.map((guest, index) => <div className="pms-coguest-row" key={guest.guestId}><div><strong>{guest.guestName}</strong><small>{guest.registrationCompletedAt ? 'Anmeldung vollständig' : 'Anmeldung noch offen'}</small></div><label>Anreise<input aria-label={`Anreise ${guest.guestName}`} type="date" value={guest.arrivalDate} disabled={!canManage || busy} onChange={(event) => editCoGuests(coGuests.map((entry, i) => i === index ? { ...entry, arrivalDate: event.target.value } : entry))} required /></label><label>Abreise<input aria-label={`Abreise ${guest.guestName}`} type="date" value={guest.departureDate} disabled={!canManage || busy} onChange={(event) => editCoGuests(coGuests.map((entry, i) => i === index ? { ...entry, departureDate: event.target.value } : entry))} required /></label><label className="pms-check-row"><input type="checkbox" checked={guest.child} disabled={!canManage || busy} onChange={(event) => editCoGuests(coGuests.map((entry, i) => i === index ? { ...entry, child: event.target.checked } : entry))} />Kind</label>{canManage && <button type="button" aria-label={`${guest.guestName} entfernen`} disabled={busy} onClick={() => editCoGuests(coGuests.filter((_, i) => i !== index))}>×</button>}</div>)}</div>
                {canManage && <div className="pms-form-actions"><button type="submit" disabled={busy} className="is-primary">Mitreisende speichern</button></div>}
            </form>
            {canManage && details.coGuests?.length > 0 && <details className="pms-enterprise-details"><summary>Mitreisenden persönlich anmelden</summary><form className="pms-form-grid" onSubmit={(event) => { event.preventDefault(); act(async () => { await api.post(`/api/pms/reservations/${reservation.id}/co-guests/${registration.guestId}/registration`, registration); setRegistration((current) => ({ ...current, guestId: '', documentNumber: '', privacyConsent: false })); }, 'Anmeldung des Mitreisenden gespeichert.'); }}>
                <label className="is-wide">Mitreisender<select value={registration.guestId} onChange={(event) => setRegistration({ ...registration, guestId: event.target.value, privacyConsent: false })} required><option value="">Gespeicherten Mitreisenden auswählen</option>{details.coGuests.map((guest) => <option key={guest.guestId} value={guest.guestId}>{guest.guestName}</option>)}</select></label>
                {[['addressLine', 'Straße und Hausnummer', 180], ['postalCode', 'Postleitzahl', 20], ['city', 'Ort', 120], ['countryCode', 'Wohnsitzland (ISO, z. B. DE)', 2], ['nationalityCode', 'Staatsangehörigkeit (ISO)', 2], ['documentNumber', 'Ausweisnummer', 120], ['signatureName', 'Bestätigender Name des Gastes', 180]].map(([key, label, max]) => <label key={key}>{label}<input value={registration[key]} maxLength={max} minLength={key === 'documentNumber' ? 4 : max === 2 ? 2 : 1} onChange={(event) => setRegistration({ ...registration, [key]: max === 2 ? event.target.value.toUpperCase() : event.target.value })} required /></label>)}
                <label className="pms-check-row is-wide"><input type="checkbox" checked={registration.privacyConsent} onChange={(event) => setRegistration({ ...registration, privacyConsent: event.target.checked })} required />Der Gast hat die Angaben und Datenschutzhinweise bestätigt.</label><div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={busy}>Anmeldung speichern</button></div>
            </form></details>}
        </>}
    </section></div>;
}
