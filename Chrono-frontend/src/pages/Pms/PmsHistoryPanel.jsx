import { useEffect, useState } from 'react';
import api from '../../utils/api.js';
import { formatPmsMoney } from './pmsMoney.js';
import { getPmsEnumLabel } from './pmsTerminology.js';
import './PmsHistoryPanel.css';

export const PMS_HISTORY_SECTIONS = {
    invoices: 'Rechnungen', 'night-audits': 'Tagesabschlüsse', communications: 'Gästekommunikation',
    outbox: 'Schnittstellenversand', audit: 'Änderungsprotokoll', 'guest-registrations': 'Gästemeldungen',
    'resource-bookings': 'Ressourcenbuchungen', 'pos-tickets': 'Kassenbelege',
    'access-credentials': 'Zutrittsberechtigungen', 'migration-batches': 'Datenimporte',
};
const ALL_SECTIONS = Object.keys(PMS_HISTORY_SECTIONS);
const moneyColumns = new Set(['netAmount', 'vatAmount', 'grossAmount', 'taxAmount', 'openBalance', 'totalAmount', 'totalOpeningBalance']);
const enumColumns = {
    invoices: { type: 'InvoiceType', status: 'InvoiceStatus' },
    communications: { channel: 'CommunicationChannel', direction: 'CommunicationDirection', status: 'CommunicationStatus' },
    outbox: { status: 'OutboxStatus' },
    'guest-registrations': { status: 'GuestRegistrationStatus' },
    'resource-bookings': { status: 'ResourceBookingStatus' },
    'pos-tickets': { paymentMethod: 'PaymentMethod', status: { OPEN: 'Offen', SETTLED: 'Abgerechnet', VOIDED: 'Storniert' } },
    'access-credentials': { status: { ACTIVE: 'Aktiv', REVOKED: 'Widerrufen', EXPIRED: 'Abgelaufen' } },
    'migration-batches': { status: { COMPLETED: 'Abgeschlossen', RECONCILIATION_REQUIRED: 'Abstimmung erforderlich', FAILED: 'Fehlgeschlagen' } },
};
const labels = {
    id: 'ID', invoiceNumber: 'Rechnungsnummer', type: 'Art', issueDate: 'Ausgestellt', dueDate: 'Fällig', recipientName: 'Empfänger', currencyCode: 'Währung', netAmount: 'Netto', vatAmount: 'Steuer', grossAmount: 'Brutto', status: 'Status',
    businessDate: 'Betriebstag', arrivalsCount: 'Anreisen', departuresCount: 'Abreisen', inHouseCount: 'Im Haus', noShowCount: 'Nichtanreisen', openBalance: 'Offener Saldo', closedBy: 'Abgeschlossen von', closedAt: 'Abgeschlossen am',
    subject: 'Betreff', recipient: 'Empfänger', sender: 'Absender', channel: 'Kanal', direction: 'Richtung', createdAt: 'Erstellt', sentAt: 'Gesendet', readAt: 'Gelesen', eventType: 'Ereignis', aggregateType: 'Datensatz', aggregateId: 'Referenz', attemptCount: 'Versuche', deliveredAt: 'Zugestellt', nextAttemptAt: 'Nächster Versuch', lastAttemptAt: 'Letzter Versuch', lastError: 'Letzter Fehler',
    actor: 'Bearbeitet von', integrityHash: 'Prüfsumme', city: 'Ort', countryCode: 'Land', signatureName: 'Unterschrift', completedAt: 'Abgeschlossen', invitedAt: 'Eingeladen', title: 'Bezeichnung', organizerName: 'Veranstalter', startAt: 'Beginn', endAt: 'Ende', attendees: 'Personen', totalAmount: 'Gesamtbetrag', createdBy: 'Erstellt von',
    ticketNumber: 'Belegnummer', outletCode: 'Verkaufsstelle', tableReference: 'Tisch', serviceDate: 'Leistungsdatum', paymentMethod: 'Zahlungsart', taxAmount: 'Steuer', providerCode: 'Anbieter', externalReference: 'Externe Referenz', validFrom: 'Gültig ab', validUntil: 'Gültig bis', issuedBy: 'Ausgestellt von', issuedAt: 'Ausgestellt', revokedAt: 'Widerrufen', idempotencyKey: 'Importreferenz', sourceSystem: 'Quellsystem', importedGuests: 'Gäste', importedReservations: 'Reservierungen', importedPayments: 'Zahlungen', totalOpeningBalance: 'Eröffnungssaldo', reconciliationMessage: 'Abstimmung',
};
export default function PmsHistoryPanel({ propertyId, currencyCode = 'CHF', sections = ALL_SECTIONS }) {
    const allowed = sections.filter((section) => PMS_HISTORY_SECTIONS[section]);
    const [selected, setSelected] = useState(allowed[0] || '');
    const section = allowed.includes(selected) ? selected : allowed[0];
    const [input, setInput] = useState('');
    const [query, setQuery] = useState('');
    const [page, setPage] = useState(0);
    const [size, setSize] = useState(25);
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [revision, setRevision] = useState(0);
    useEffect(() => {
        if (input.trim() === query) return;
        const timer = setTimeout(() => { setQuery(input.trim()); setPage(0); }, 300);
        return () => clearTimeout(timer);
    }, [input, query]);
    const requestKey = `${propertyId}:${section}:${page}:${size}:${query}`;
    useEffect(() => {
        if (!section || !propertyId) return;
        const controller = new AbortController(); setLoading(true); setError('');
        api.get(`/api/pms/properties/${propertyId}/history/${section}`, { params: { page, size, query: query || undefined }, signal: controller.signal }).then(({ data: result }) => {
            if (!controller.signal.aborted) setData({ key: requestKey, ...result });
        }).catch((failure) => { if (!controller.signal.aborted) setError(failure?.response?.data?.detail || failure?.response?.data?.message || failure.message); })
            .finally(() => { if (!controller.signal.aborted) setLoading(false); });
        return () => controller.abort();
    }, [requestKey, revision]);
    if (!allowed.length) return null;
    const current = data?.key === requestKey ? data : null;
    const columns = current?.items?.length ? Object.keys(current.items[0]) : [];
    const display = (key, value, row) => {
        if (value == null || value === '') return '—';
        if (moneyColumns.has(key)) return formatPmsMoney(value, row.currencyCode || currencyCode);
        if (enumColumns[section]?.[key]) return getPmsEnumLabel(enumColumns[section][key], value);
        return typeof value === 'boolean' ? value ? 'Ja' : 'Nein' : String(value).replace(/^(\d{4}-\d\d-\d\d)T/, '$1 ');
    };
    return <section className="pms-work-card pms-history-panel" aria-label="Hotelverlauf">
        <div className="pms-history-heading"><h3>Verlauf und ältere Einträge</h3><button type="button" disabled={loading} onClick={() => setRevision((value) => value + 1)}>Verlauf aktualisieren</button></div>
        <div className="pms-form-grid"><label>Verlaufsbereich<select value={section} onChange={(event) => { setSelected(event.target.value); setPage(0); }}>
            {allowed.map((key) => <option value={key} key={key}>{PMS_HISTORY_SECTIONS[key]}</option>)}</select></label>
            <label>Verlauf durchsuchen<input type="search" maxLength={120} value={input} onChange={(event) => setInput(event.target.value)} placeholder="Name, Nummer oder Referenz" /></label>
            <label>Einträge pro Seite<select value={size} onChange={(event) => { setSize(Number(event.target.value)); setPage(0); }}>{[10,25,50,100].map((value) => <option key={value} value={value}>{value}</option>)}</select></label></div>
        {error && <p role="alert" className="pms-error">{error}</p>}
        <p role="status">{loading ? 'Verlauf wird geladen …' : current ? `${current.totalElements} passende Einträge` : ''}</p>
        {current?.items?.length > 0 && <div className="pms-history-table-scroll"><table><thead><tr>{columns.map((key) => <th key={key} scope="col">{labels[key] || key}</th>)}</tr></thead><tbody>
            {current.items.map((row) => <tr key={row.id}>{columns.map((key) => <td key={key} className={moneyColumns.has(key) ? 'is-money' : ''}>{display(key, row[key], row)}</td>)}</tr>)}
        </tbody></table></div>}
        {!loading && !error && current?.items?.length === 0 && <p>Keine Einträge für diese Suche.</p>}
        <div className="pms-history-pagination"><button type="button" disabled={page === 0 || loading} onClick={() => setPage((value) => value - 1)}>Vorherige Seite</button>
            <span>Seite {page + 1}{current ? ` von ${Math.max(1, Math.ceil(current.totalElements / size))}` : ''}</span>
            <button type="button" disabled={!current?.hasNext || loading} onClick={() => setPage((value) => value + 1)}>Nächste Seite</button></div>
    </section>;
}
