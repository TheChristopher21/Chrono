const fields = [
    ['addressLine2', 'Adresszusatz', 180],
    ['region', 'Bundesland / Region', 100],
    ['taxRegistrationLabel', 'Bezeichnung der Steuer-ID (z. B. UID, VAT, GST)', 40],
    ['taxNumber', 'Steuer- / MwSt.-Nummer des Hotels', 80],
    ['registrationNumber', 'Handelsregister- / Unternehmensnummer', 100],
    ['invoicePrefix', 'Präfix der Rechnungsnummer', 24],
];

export const propertyBillingDefaults = {
    addressLine2: '', region: '', taxRegistrationLabel: 'VAT / Tax ID', taxNumber: '',
    registrationNumber: '', invoicePrefix: 'INV', invoiceDueDays: 14, invoiceFooter: '',
};

export const propertyBillingForm = (property) => Object.fromEntries(
    Object.entries(propertyBillingDefaults).map(([key, fallback]) => [key, property?.[key] ?? fallback]),
);

export default function PmsPropertyBillingFields({ value, onChange }) {
    return <>
        <div className="is-wide"><h4>Rechnungen und internationale Betriebsdaten</h4>
            <p>Diese Angaben gehören zum ausgewählten Hotel. Steuersätze werden je Leistung im Ratenplan gepflegt.</p></div>
        {fields.map(([key, label, maxLength]) => <label key={key}>{label}
            <input name={key} value={value[key] ?? ''} maxLength={maxLength} onChange={onChange}
                required={key === 'invoicePrefix'} pattern={key === 'invoicePrefix' ? '[A-Za-z0-9_-]{1,24}' : undefined} />
        </label>)}
        <label>Zahlungsziel (Tage)<input name="invoiceDueDays" type="number" min="0" max="365"
            value={value.invoiceDueDays ?? 14} onChange={onChange} required /></label>
        <label className="is-wide">Rechnungsfußtext / lokale Pflichtangaben
            <textarea name="invoiceFooter" maxLength={2000} rows={4} value={value.invoiceFooter ?? ''} onChange={onChange}
                placeholder="Zahlungsinformationen, Registerangaben und erforderliche lokale Hinweise" />
        </label>
    </>;
}
