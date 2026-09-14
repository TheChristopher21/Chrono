export const profileDetails = (value = {}) => ({
    privateEmail: value.privateEmail || '', businessEmail: value.businessEmail || '',
    additionalEmails: value.additionalEmails || [], dietaryNotes: value.dietaryNotes || '',
    vatNumber: value.vatNumber || '', organizationContactId: value.organizationContactId || '',
    billingOverride: Boolean(value.billingOverride), billingProfile: value.billingProfile || {},
});

export const preferredProfileEmail = (value) => [value.email, value.businessEmail, value.privateEmail, ...(value.additionalEmails || [])]
    .map((email) => String(email || '').trim()).find(Boolean) || null;

export const profilePayload = (value) => ({
    ...profileDetails(value), organizationContactId: value.organizationContactId || null,
    email: preferredProfileEmail(value),
});

export function BillingProfileEditor({ value = {}, onChange }) {
    const field = (key, label, maxLength = 180, type = 'text') => <label key={key}>{label}<input type={type} maxLength={maxLength} value={value[key] || ''} onChange={(event) => onChange({ ...value, [key]: event.target.value })} /></label>;
    const lines = value.recipientOrder === 'PERSON_FIRST' ? [value.attention, value.legalName] : [value.legalName, value.attention];
    lines.push(value.addressLine1, value.addressLine2);
    if (value.addressFormat === 'CITY_REGION_POSTAL') lines.push([value.city, value.region, value.postalCode].filter(Boolean).join(' '));
    else lines.push([value.postalCode, value.city].filter(Boolean).join(' '), value.region);
    lines.push(value.countryCode);
    return <fieldset className="is-wide pms-form-grid"><legend>Rechnungs- und Briefempfänger</legend>
        <p className="is-wide">Leere Felder übernehmen die Stammdaten. Land und Reihenfolge bestimmen den Adressblock; Steuern richten sich nach dem Hotel und der Leistung.</p>
        {field('legalName', 'Rechtlicher Rechnungsempfänger')}{field('attention', 'Zu Händen / Mitarbeiter')}
        {field('addressLine1', 'Rechnungsadresse')}{field('addressLine2', 'Adresszusatz / Abteilung')}
        {field('postalCode', 'Rechnungs-PLZ', 20)}{field('city', 'Rechnungsort', 120)}{field('region', 'Bundesstaat / Region', 100)}{field('countryCode', 'Rechnungsland (ISO)', 2)}
        {field('vatNumber', 'Empfänger VAT / Tax ID', 80)}{field('billingEmail', 'Versand an Rechnungs-E-Mail', 190, 'email')}
        {field('reference', 'Bestellnummer / Referenz', 120)}{field('costCenter', 'Kostenstelle', 120)}
        <label>Namensreihenfolge<select value={value.recipientOrder || 'COMPANY_FIRST'} onChange={(e) => onChange({ ...value, recipientOrder: e.target.value })}><option value="COMPANY_FIRST">Firma, danach Ansprechpartner</option><option value="PERSON_FIRST">Ansprechpartner, danach Firma</option></select></label>
        <label>Postalisches Format<select value={value.addressFormat || 'POSTAL_CITY'} onChange={(e) => onChange({ ...value, addressFormat: e.target.value })}><option value="POSTAL_CITY">PLZ Ort / Region</option><option value="CITY_REGION_POSTAL">Ort Region PLZ</option></select></label>
        <label className="is-wide">Zusatz auf der Rechnung<textarea maxLength={2000} value={value.footer || ''} onChange={(e) => onChange({ ...value, footer: e.target.value })} /></label>
        {lines.some(Boolean) && <div className="is-wide"><strong>Vorschau der erfassten Empfängerzeilen</strong><pre style={{ whiteSpace: 'pre-wrap', font: 'inherit' }}>{lines.filter(Boolean).join('\n')}</pre></div>}
    </fieldset>;
}

export function ProfileEmailFields({ value, onChange }) {
    return <>
        <small className="is-wide">Ohne allgemeine E-Mail wird zuerst die geschäftliche, danach die private oder eine zusätzliche Adresse als Hauptkontakt verwendet.</small>
        <label>Private E-Mail<input type="email" maxLength={190} value={value.privateEmail || ''} onChange={(e) => onChange({ ...value, privateEmail: e.target.value })} /></label>
        <label>Geschäftliche E-Mail<input type="email" maxLength={190} value={value.businessEmail || ''} onChange={(e) => onChange({ ...value, businessEmail: e.target.value })} /></label>
        <label className="is-wide">Weitere E-Mails (durch Komma getrennt)<input type="email" multiple value={(value.additionalEmails || []).join(', ')} onChange={(e) => onChange({ ...value, additionalEmails: e.target.value.split(',').map((entry) => entry.trim()) })} /></label>
    </>;
}

export default function PmsGuestProfileDetails({ value, onChange, organizations = [] }) {
    const organization = organizations.find((entry) => String(entry.id) === String(value.organizationId));
    return <>
        <ProfileEmailFields value={value} onChange={onChange} />
        <label className="is-wide">Ernährung, Allergien und Unverträglichkeiten<textarea maxLength={1000} value={value.dietaryNotes || ''} placeholder="Zum Beispiel laktosefreie Kost; mit dem Gast abgestimmte Hinweise" onChange={(e) => onChange({ ...value, dietaryNotes: e.target.value })} /></label>
        <label>VAT / Tax ID (optional bei Geschäftsreisen)<input maxLength={80} value={value.vatNumber || ''} onChange={(e) => onChange({ ...value, vatNumber: e.target.value })} /></label>
        {organization && <label>Firmen-Ansprechpartner<select value={value.organizationContactId || ''} onChange={(e) => onChange({ ...value, organizationContactId: e.target.value })}><option value="">Keiner</option>{(organization.contacts || []).map((contact) => <option key={contact.id} value={contact.id}>{contact.name}{contact.role ? ` · ${contact.role}` : ''}</option>)}</select></label>}
        <label className="pms-checkbox is-wide"><input type="checkbox" checked={Boolean(value.billingOverride)} onChange={(e) => onChange({ ...value, billingOverride: e.target.checked })} /> Individuelle Rechnungsangaben für diesen Gast / Firmenmitarbeiter</label>
        {(value.billingOverride || !organization) && <BillingProfileEditor value={value.billingProfile || {}} onChange={(billingProfile) => onChange({ ...value, billingProfile })} />}
    </>;
}
