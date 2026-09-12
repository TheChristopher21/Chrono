import { useEffect, useState } from 'react';
import api from '../../utils/api.js';
import { BillingProfileEditor, ProfileEmailFields } from './PmsGuestProfileDetails.jsx';

export function OrganizationProfileDetails({ value, onChange, guests }) {
    const contacts = value.contacts || [];
    const update = (index, patch) => onChange({ ...value, contacts: contacts.map((contact, i) => i === index ? { ...contact, ...patch } : contact) });
    return <><ProfileEmailFields value={value} onChange={onChange} />
        <fieldset className="is-wide"><legend>Ansprechpartner</legend>
            {contacts.map((contact, index) => <div className="pms-form-grid" key={contact.id || index}>
                <label>Name<input required maxLength={180} value={contact.name || ''} onChange={(e) => update(index, { name: e.target.value })} /></label>
                <label>Funktion / Abteilung<input maxLength={100} value={contact.role || ''} onChange={(e) => update(index, { role: e.target.value })} /></label>
                <label>Kontakt-E-Mail<input type="email" maxLength={190} value={contact.email || ''} onChange={(e) => update(index, { email: e.target.value })} /></label>
                <label>Kontakt-Telefon<input maxLength={60} value={contact.phone || ''} onChange={(e) => update(index, { phone: e.target.value })} /></label>
                <label>Mit Gästekartei verknüpfen<select value={contact.linkedGuestId || ''} onChange={(e) => {
                    const guest = guests.find((entry) => String(entry.id) === e.target.value);
                    update(index, { linkedGuestId: guest?.id || null, ...(guest ? { name: `${guest.firstName} ${guest.lastName}`, email: guest.businessEmail || guest.email || '' } : {}) });
                }}><option value="">Eigenständiger Kontakt</option>{guests.filter((guest) => guest.active !== false && (!guest.organizationId || String(guest.organizationId) === String(value.id))).map((guest) => <option key={guest.id} value={guest.id}>{guest.firstName} {guest.lastName}</option>)}</select></label>
                <label className="pms-checkbox"><input type="checkbox" checked={Boolean(contact.primaryContact)} onChange={(e) => onChange({ ...value, contacts: contacts.map((item, i) => ({ ...item, primaryContact: i === index && e.target.checked })) })} /> Hauptkontakt</label>
                <button type="button" onClick={() => onChange({ ...value, contacts: contacts.filter((_, i) => i !== index) })}>Kontakt entfernen</button>
            </div>)}
            <button type="button" disabled={contacts.length >= 100} onClick={() => onChange({ ...value, contacts: [...contacts, { name: '', role: '', email: '', phone: '', linkedGuestId: null, primaryContact: !contacts.length }] })}>Ansprechpartner hinzufügen</button>
        </fieldset>
        <BillingProfileEditor value={value.billingProfile || {}} onChange={(billingProfile) => onChange({ ...value, billingProfile })} />
    </>;
}

export function OrganizationDocuments({ organizationId, rates = [], canManage }) {
    const [documents, setDocuments] = useState([]);
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);
    const [ratePlanId, setRatePlanId] = useState('');
    const [file, setFile] = useState(null);
    useEffect(() => {
        let active = true;
        api.get(`/api/pms/organizations/${organizationId}/documents`).then((response) => { if (active) setDocuments(response.data); }).catch(() => { if (active) setError('Dokumente konnten nicht geladen werden.'); });
        return () => { active = false; };
    }, [organizationId]);
    const upload = async () => {
        if (!file) return;
        setBusy(true); setError('');
        try {
            const form = new FormData(); form.append('file', file);
            const response = await api.post(`/api/pms/organizations/${organizationId}/documents`, form, { params: ratePlanId ? { ratePlanId } : {}, headers: { 'Content-Type': 'multipart/form-data' } });
            setDocuments((values) => [response.data, ...values]); setFile(null);
        } catch (err) { setError(err.response?.data?.detail || 'Dokument konnte nicht gespeichert werden.'); }
        finally { setBusy(false); }
    };
    const download = async (document) => {
        try {
            const response = await api.get(`/api/pms/documents/${document.id}/download`, { responseType: 'blob' });
            const url = URL.createObjectURL(response.data); const anchor = window.document.createElement('a');
            anchor.href = url; anchor.download = document.fileName; anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
        } catch { setError('Download fehlgeschlagen.'); }
    };
    return <section className="pms-work-card"><h4>Firmen- und Ratenverträge</h4><p>Private Ablage: PDF, PNG oder JPEG, maximal 4 MB pro Datei.</p>
        {error && <p role="alert">{error}</p>}
        {documents.map((document) => <div className="pms-record" key={document.id}><span>{document.fileName} · {Math.ceil(document.sizeBytes / 1024)} KB{document.ratePlanId ? ` · ${rates.find((rate) => rate.id === document.ratePlanId)?.name || 'Vertragsrate'}` : ''}</span><button type="button" onClick={() => download(document)}>Herunterladen</button></div>)}
        {canManage && <div className="pms-form-grid"><label>Dokument<input type="file" accept=".pdf,.png,.jpg,.jpeg" onChange={(e) => setFile(e.target.files?.[0] || null)} /></label>
            <label>Zugehörige Rate<select value={ratePlanId} onChange={(e) => setRatePlanId(e.target.value)}><option value="">Allgemeiner Firmenvertrag</option>{rates.map((rate) => <option key={rate.id} value={rate.id}>{rate.name}</option>)}</select></label>
            <button type="button" disabled={busy || !file || file.size > 4 * 1024 * 1024} onClick={upload}>Dokument hinterlegen</button>
        </div>}
    </section>;
}
