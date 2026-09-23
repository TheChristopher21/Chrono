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

export function OrganizationDocuments({ organizationId, propertyId, rates = [], canManage }) {
    const [documents, setDocuments] = useState([]);
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);
    const [ratePlanId, setRatePlanId] = useState('');
    const [file, setFile] = useState(null);
    const [previousDocumentId, setPreviousDocumentId] = useState('');
    const [limits, setLimits] = useState({ maxFileBytes: 25 * 1024 * 1024 });
    useEffect(() => {
        let active = true;
        api.get(`/api/pms/organizations/${organizationId}/documents`, { params: { propertyId } }).then((response) => { if (active) setDocuments(response.data); }).catch(() => { if (active) setError('Dokumente konnten nicht geladen werden.'); });
        api.get('/api/pms/document-limits', { params: { propertyId } }).then(({ data }) => { if (active && data?.maxFileBytes) setLimits(data); }).catch(() => {});
        return () => { active = false; };
    }, [organizationId, propertyId]);
    const upload = async () => {
        if (!file) return;
        setBusy(true); setError('');
        try {
            const form = new FormData(); form.append('file', file);
            const response = await api.post(`/api/pms/organizations/${organizationId}/documents`, form, { params: { propertyId, ...(ratePlanId ? { ratePlanId } : {}), ...(previousDocumentId ? { previousDocumentId } : {}) }, headers: { 'Content-Type': 'multipart/form-data' } });
            setDocuments((values) => [response.data, ...values]); setFile(null); setPreviousDocumentId('');
        } catch (err) { setError(err.response?.data?.detail || 'Dokument konnte nicht gespeichert werden.'); }
        finally { setBusy(false); }
    };
    const download = async (document) => {
        try {
            const response = await api.get(`/api/pms/documents/${document.id}/download`, { params: { propertyId }, responseType: 'blob' });
            const url = URL.createObjectURL(response.data); const anchor = window.document.createElement('a');
            anchor.href = url; anchor.download = document.fileName; anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
        } catch { setError('Download fehlgeschlagen.'); }
    };
    const latest = documents.filter((entry) => !entry.versionGroup || !documents.some((other) => other.versionGroup === entry.versionGroup && other.documentVersion > entry.documentVersion));
    return <section className="pms-work-card"><h4>Firmen- und Ratenverträge</h4><p>Private Ablage: PDF, PNG oder JPEG, maximal {Math.floor(limits.maxFileBytes / 1024 / 1024)} MB pro Datei. Neue Versionen bewahren den bisherigen Vertragsstand.</p>
        {error && <p role="alert">{error}</p>}
        {documents.map((document) => <div className="pms-record" key={document.id}><span>{document.fileName} · Version {document.documentVersion || 1} · {Math.ceil(document.sizeBytes / 1024)} KB{document.ratePlanId ? ` · ${rates.find((rate) => rate.id === document.ratePlanId)?.name || 'Vertragsrate'}` : ''}{!latest.includes(document) ? ' · Vorheriger Stand' : ''}</span><button type="button" onClick={() => download(document)}>Herunterladen</button></div>)}
        {canManage && <div className="pms-form-grid"><label>Dokument<input type="file" accept=".pdf,.png,.jpg,.jpeg" onChange={(e) => setFile(e.target.files?.[0] || null)} /></label>
            <label>Vertragsstand<select value={previousDocumentId} onChange={(event) => { const previous = documents.find((entry) => String(entry.id) === event.target.value); setPreviousDocumentId(event.target.value); if (previous) setRatePlanId(previous.ratePlanId || ''); }}><option value="">Neues eigenständiges Dokument</option>{latest.map((document) => <option key={document.id} value={document.id}>Neue Version von {document.fileName} (V{document.documentVersion || 1})</option>)}</select></label>
            <label>Zugehörige Rate<select disabled={Boolean(previousDocumentId)} value={ratePlanId} onChange={(e) => setRatePlanId(e.target.value)}><option value="">Allgemeiner Firmenvertrag</option>{rates.map((rate) => <option key={rate.id} value={rate.id}>{rate.name}</option>)}</select></label>
            {file?.size > limits.maxFileBytes && <p role="alert">Die Datei überschreitet das Uploadlimit.</p>}
            <button type="button" disabled={busy || !file || file.size > limits.maxFileBytes} onClick={upload}>{previousDocumentId ? 'Neue Version hinterlegen' : 'Dokument hinterlegen'}</button>
        </div>}
    </section>;
}
