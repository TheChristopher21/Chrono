import { useEffect, useState } from 'react';
import api from '../../utils/api.js';
import { formatPmsDateTime } from './pmsFormatting.js';
import PmsHistoryPanel from './PmsHistoryPanel.jsx';

export default function PmsAccessCredentialsPanel({ property, operations, businessDate, canManage = false }) {
    const [credentials, setCredentials] = useState([]);
    const [revision, setRevision] = useState(0);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [draft, setDraft] = useState({ reservationId: '', providerCode: '', externalReference: '', validFrom: `${businessDate}T15:00`, validUntil: `${businessDate}T23:00` });
    const prefix = `/api/pms/properties/${property.id}/access-credentials`;
    const reservations = (operations?.reservations || []).filter((entry) => entry.roomNumber && !['CANCELLED', 'NO_SHOW', 'CHECKED_OUT'].includes(entry.status));
    useEffect(() => {
        const controller = new AbortController();
        api.get('/api/pms/extensions', { params: { propertyId: property.id }, signal: controller.signal })
            .then(({ data }) => { if (!controller.signal.aborted) setCredentials(data?.accessCredentials || []); })
            .catch((failure) => { if (!controller.signal.aborted) setError(failure.response?.data?.detail || 'Zimmerschlüssel konnten nicht geladen werden.'); });
        return () => controller.abort();
    }, [property.id, revision]);
    const mutate = async (id = null) => {
        if (!canManage || busy) return;
        setBusy(true); setError(''); setNotice('');
        try {
            await api.post(id ? `${prefix}/${id}/revoke` : prefix, id ? undefined : { ...draft, reservationId: Number(draft.reservationId) });
            if (!id) setDraft((current) => ({ ...current, externalReference: '' }));
            setRevision((value) => value + 1); setNotice(id ? 'Schlüsselwiderruf eingeplant.' : 'Digitaler Schlüssel beim Anbieter eingeplant.');
        } catch (failure) { setError(failure.response?.data?.detail || 'Der Schlüsselauftrag konnte nicht gespeichert werden.'); }
        finally { setBusy(false); }
    };
    return <section className="pms-work-card pms-enterprise-card">
        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Rezeption · Zutrittssystem</span><h3>Digitale Zimmerschlüssel</h3></div></div>
        {error && <p className="pms-inline-message is-error" role="alert">{error}</p>}{notice && <p role="status">{notice}</p>}
        <form onSubmit={(event) => { event.preventDefault(); mutate(); }}><fieldset className="pms-form-grid pms-plain-fieldset" disabled={!canManage || busy}>
            <label className="is-wide">Reservierung für Zimmerschlüssel<select value={draft.reservationId} onChange={(event) => setDraft({ ...draft, reservationId: event.target.value })} required><option value="">Reservierung mit Zimmer wählen</option>{reservations.map((entry) => <option key={entry.id} value={entry.id}>{entry.roomNumber} · {entry.guestName}</option>)}</select></label>
            <label>Schlüsselanbieter<input value={draft.providerCode} onChange={(event) => setDraft({ ...draft, providerCode: event.target.value })} placeholder="SALTO" required /></label>
            <label>Externe Schlüsselreferenz<input value={draft.externalReference} onChange={(event) => setDraft({ ...draft, externalReference: event.target.value })} placeholder="secret-manager:key-…" required /></label>
            <label>Schlüssel gültig ab<input type="datetime-local" value={draft.validFrom} onChange={(event) => setDraft({ ...draft, validFrom: event.target.value })} required /></label>
            <label>Schlüssel gültig bis<input type="datetime-local" value={draft.validUntil} onChange={(event) => setDraft({ ...draft, validUntil: event.target.value })} required /></label>
            <div className="pms-form-actions is-wide"><button type="submit" className="is-primary">Schlüssel ausstellen</button></div>
        </fieldset></form>
        <div className="pms-record-list">{credentials.map((credential) => <article className="pms-record" key={credential.id}><div><span>{credential.status} · Zimmer {credential.roomNumber}</span><strong>{credential.guestName}</strong><small>{credential.providerCode} · bis {formatPmsDateTime(credential.validUntil)}</small></div>{credential.status === 'ACTIVE' && <button type="button" disabled={!canManage || busy} onClick={() => mutate(credential.id)}>Widerrufen</button>}</article>)}</div>
        <PmsHistoryPanel key={revision} propertyId={property.id} currencyCode={property.currencyCode} sections={['access-credentials']} />
    </section>;
}
