import { useCallback, useEffect, useMemo, useState } from 'react';
import api from '../../utils/api.js';
import { formatPmsDateTime } from './pmsFormatting.js';
import { getFolioDisplayLabel } from './pmsTerminology.js';

const errorMessage = (error) => error?.response?.data?.detail
    || error?.response?.data?.message || error?.message || 'Die Aktion konnte nicht abgeschlossen werden.';

const money = (value, currency = 'CHF') => new Intl.NumberFormat('de-CH', {
    style: 'currency', currency,
}).format(Number(value ?? 0));

const PmsExtensionsWorkspace = ({ property, operations, businessDate, canManage, onOperationsChange }) => {
    const [data, setData] = useState(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [booking, setBooking] = useState({
        publicSlug: property?.code?.toLowerCase() ?? '', enabled: false, requireGuarantee: false, termsUrl: '', privacyUrl: '', confirmationMessage: '',
    });
    const [tax, setTax] = useState({
        enabled: false, name: 'Kurtaxe', adultRate: 0, childRate: 0, childFreeUnder: 16, maximumNights: '',
    });
    const [taxPosting, setTaxPosting] = useState({ reservationId: '', chargeableChildren: 0 });
    const [pos, setPos] = useState({
        folioId: '', outletCode: 'RESTAURANT', tableReference: '', paymentMethod: 'CASH',
        description: '', quantity: 1, unitPrice: 0, taxRate: 8.1,
    });
    const [access, setAccess] = useState({
        reservationId: '', providerCode: '', externalReference: '',
        validFrom: `${businessDate}T15:00`, validUntil: `${businessDate}T23:00`,
    });
    const [migration, setMigration] = useState({
        idempotencyKey: `migration-${businessDate}`, sourceSystem: '', reservationsJson: '[]',
    });
    const [accounting, setAccounting] = useState({
        from: `${businessDate.slice(0, 7)}-01`, toExclusive: businessDate,
    });

    const activeReservations = useMemo(() => (operations?.reservations ?? [])
        .filter((entry) => !['CANCELLED', 'NO_SHOW', 'CHECKED_OUT'].includes(entry.status)), [operations]);
    const openFolios = useMemo(() => (operations?.folios ?? []).filter((entry) => entry.status === 'OPEN'), [operations]);

    const applyData = useCallback((next) => {
        setData(next);
        if (next?.bookingEngine) setBooking({
            publicSlug: next.bookingEngine.publicSlug ?? property?.code?.toLowerCase() ?? '',
            enabled: next.bookingEngine.enabled,
            requireGuarantee: next.bookingEngine.requireGuarantee,
            termsUrl: next.bookingEngine.termsUrl ?? '',
            privacyUrl: next.bookingEngine.privacyUrl ?? '',
            confirmationMessage: next.bookingEngine.confirmationMessage ?? '',
        });
        if (next?.tourismTax) setTax({
            enabled: next.tourismTax.enabled,
            name: next.tourismTax.name ?? 'Kurtaxe',
            adultRate: next.tourismTax.adultRate ?? 0,
            childRate: next.tourismTax.childRate ?? 0,
            childFreeUnder: next.tourismTax.childFreeUnder ?? 16,
            maximumNights: next.tourismTax.maximumNights ?? '',
        });
    }, [property?.code]);

    const load = useCallback(async () => {
        if (!property?.id) return;
        setBusy(true);
        setError('');
        try {
            const response = await api.get('/api/pms/extensions', { params: { propertyId: property.id } });
            applyData(response.data);
        } catch (loadError) {
            setError(errorMessage(loadError));
        } finally {
            setBusy(false);
        }
    }, [applyData, property?.id]);

    useEffect(() => { load(); }, [load]);

    const mutate = async (method, url, payload, success, refreshOperations = false) => {
        setBusy(true);
        setError('');
        setNotice('');
        try {
            const response = await api[method](url, payload);
            applyData(response.data);
            if (refreshOperations) {
                const operationsResponse = await api.get('/api/pms/operations', {
                    params: { propertyId: property.id, businessDate },
                });
                onOperationsChange(operationsResponse.data);
            }
            setNotice(success);
            return true;
        } catch (mutationError) {
            setError(errorMessage(mutationError));
            return false;
        } finally {
            setBusy(false);
        }
    };

    const submitBooking = (event) => {
        event.preventDefault();
        return mutate('put', `/api/pms/properties/${property.id}/booking-engine`, booking,
            booking.enabled ? `Onlinebuchung ist aktiv: /book/${booking.publicSlug}` : 'Onlinebuchung deaktiviert.');
    };

    const submitTax = (event) => {
        event.preventDefault();
        return mutate('put', `/api/pms/properties/${property.id}/tourism-tax`, {
            ...tax, maximumNights: tax.maximumNights === '' ? null : Number(tax.maximumNights),
        }, 'Kurtaxenregel gespeichert.');
    };

    const postTax = async (event) => {
        event.preventDefault();
        const ok = await mutate('post', `/api/pms/properties/${property.id}/tourism-tax/postings`, {
            reservationId: Number(taxPosting.reservationId),
            chargeableChildren: Number(taxPosting.chargeableChildren),
        }, 'Kurtaxe auf das Gastkonto verbucht.', true);
        if (ok) setTaxPosting({ reservationId: '', chargeableChildren: 0 });
    };

    const submitPos = async (event) => {
        event.preventDefault();
        const roomCharge = Boolean(pos.folioId);
        const ok = await mutate('post', `/api/pms/properties/${property.id}/pos/tickets`, {
            folioId: roomCharge ? Number(pos.folioId) : null,
            outletCode: pos.outletCode,
            tableReference: pos.tableReference || null,
            serviceDate: businessDate,
            paymentMethod: roomCharge ? null : pos.paymentMethod,
            lines: [{
                description: pos.description,
                quantity: Number(pos.quantity),
                unitPrice: Number(pos.unitPrice),
                taxRate: Number(pos.taxRate),
            }],
        }, roomCharge ? 'POS-Beleg auf das Gastkonto gebucht.' : 'POS-Beleg direkt bezahlt.', true);
        if (ok) setPos((current) => ({ ...current, description: '', quantity: 1, unitPrice: 0 }));
    };

    const issueAccess = async (event) => {
        event.preventDefault();
        const ok = await mutate('post', `/api/pms/properties/${property.id}/access-credentials`, {
            ...access, reservationId: Number(access.reservationId),
        }, 'Digitaler Schlüssel beim Anbieter eingeplant.');
        if (ok) setAccess((current) => ({ ...current, externalReference: '' }));
    };

    const importMigration = async (event) => {
        event.preventDefault();
        let reservations;
        try { reservations = JSON.parse(migration.reservationsJson); } catch {
            setError('Die Migrationsdaten sind kein gültiges JSON-Array.');
            return;
        }
        await mutate('post', `/api/pms/properties/${property.id}/migration-batches`, {
            idempotencyKey: migration.idempotencyKey,
            sourceSystem: migration.sourceSystem,
            reservations,
        }, 'Migrationsbatch importiert und abgestimmt.', true);
    };

    const downloadAccounting = async (event) => {
        event.preventDefault();
        setBusy(true);
        setError('');
        try {
            const response = await api.get(`/api/pms/properties/${property.id}/accounting-export.csv`, {
                params: accounting, responseType: 'blob',
            });
            const url = URL.createObjectURL(response.data);
            const link = document.createElement('a');
            link.href = url;
            link.download = `chrono-pms-accounting-${accounting.from}.csv`;
            link.click();
            URL.revokeObjectURL(url);
            setNotice('Buchhaltungsexport erstellt.');
        } catch (downloadError) {
            setError(errorMessage(downloadError));
        } finally { setBusy(false); }
    };

    return (
        <div className="pms-operations-stack">
            {error && <div className="pms-inline-message is-error" role="alert">{error}</div>}
            {notice && <div className="pms-inline-message is-success" role="status">{notice}</div>}
            <div className="pms-operations-layout">
                <section className="pms-work-card">
                    <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Direktvertrieb</span><h3>Booking Engine</h3></div></div>
                    <form className="pms-form-grid" onSubmit={submitBooking}>
                        <label className="pms-checkbox-row"><input type="checkbox" checked={booking.enabled} onChange={(e) => setBooking({ ...booking, enabled: e.target.checked })} /> Onlinebuchung aktiv</label>
                        <label className="pms-checkbox-row"><input type="checkbox" checked={booking.requireGuarantee} onChange={(e) => setBooking({ ...booking, requireGuarantee: e.target.checked })} /> Garantie/Anzahlung verlangen</label>
                        <label className="is-wide">Öffentlicher Buchungsname<input value={booking.publicSlug} pattern="[a-z0-9](?:[a-z0-9-]{1,118}[a-z0-9])?" onChange={(e) => setBooking({ ...booking, publicSlug: e.target.value.toLowerCase() })} required /><small>/book/{booking.publicSlug || 'hotelname'}</small></label>
                        <label className="is-wide">AGB-Adresse<input type="url" value={booking.termsUrl} onChange={(e) => setBooking({ ...booking, termsUrl: e.target.value })} placeholder="https://…" /></label>
                        <label className="is-wide">Datenschutz-Adresse<input type="url" value={booking.privacyUrl} onChange={(e) => setBooking({ ...booking, privacyUrl: e.target.value })} placeholder="https://…" /></label>
                        <label className="is-wide">Bestätigungstext<textarea value={booking.confirmationMessage} onChange={(e) => setBooking({ ...booking, confirmationMessage: e.target.value })} /></label>
                        <div className="pms-form-actions is-wide"><button className="is-primary" disabled={!canManage || busy}>Booking Engine speichern</button></div>
                    </form>
                </section>

                <section className="pms-work-card">
                    <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Gemeindeabgaben</span><h3>Kurtaxe</h3></div></div>
                    <form className="pms-form-grid" onSubmit={submitTax}>
                        <label className="pms-checkbox-row"><input type="checkbox" checked={tax.enabled} onChange={(e) => setTax({ ...tax, enabled: e.target.checked })} /> Regel aktiv</label>
                        <label>Name<input value={tax.name} onChange={(e) => setTax({ ...tax, name: e.target.value })} required /></label>
                        <label>Erwachsene/Nacht<input type="number" min="0" step="0.01" value={tax.adultRate} onChange={(e) => setTax({ ...tax, adultRate: e.target.value })} /></label>
                        <label>Kinder/Nacht<input type="number" min="0" step="0.01" value={tax.childRate} onChange={(e) => setTax({ ...tax, childRate: e.target.value })} /></label>
                        <label>Kinder frei unter<input type="number" min="0" max="21" value={tax.childFreeUnder} onChange={(e) => setTax({ ...tax, childFreeUnder: e.target.value })} /></label>
                        <label>Max. Nächte<input type="number" min="1" value={tax.maximumNights} onChange={(e) => setTax({ ...tax, maximumNights: e.target.value })} /></label>
                        <div className="pms-form-actions is-wide"><button disabled={!canManage || busy}>Regel speichern</button></div>
                    </form>
                    <hr />
                    <form className="pms-form-grid" onSubmit={postTax}>
                        <label className="is-wide">Reservierung<select value={taxPosting.reservationId} onChange={(e) => setTaxPosting({ ...taxPosting, reservationId: e.target.value })} required><option value="">Reservierung wählen</option>{activeReservations.map((entry) => <option key={entry.id} value={entry.id}>{entry.confirmationCode} · {entry.guestName}</option>)}</select></label>
                        <label>Steuerpflichtige Kinder<input type="number" min="0" max="20" value={taxPosting.chargeableChildren} onChange={(e) => setTaxPosting({ ...taxPosting, chargeableChildren: e.target.value })} /></label>
                        <div className="pms-form-actions"><button className="is-primary" disabled={!canManage || busy}>Kurtaxe verbuchen</button></div>
                    </form>
                </section>
            </div>

            <div className="pms-operations-layout">
                <section className="pms-work-card">
                    <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Restaurant, Bar & Spa</span><h3>POS-Beleg</h3></div></div>
                    <form className="pms-form-grid" onSubmit={submitPos}>
                        <label>Outlet<input value={pos.outletCode} onChange={(e) => setPos({ ...pos, outletCode: e.target.value })} required /></label>
                        <label>Tisch/Referenz<input value={pos.tableReference} onChange={(e) => setPos({ ...pos, tableReference: e.target.value })} /></label>
                        <label className="is-wide">Gastkonto (leer = direkt bezahlt)<select value={pos.folioId} onChange={(e) => setPos({ ...pos, folioId: e.target.value })}><option value="">Direktzahlung</option>{openFolios.map((folio) => <option key={folio.id} value={folio.id}>{getFolioDisplayLabel(folio.label)} · {folio.guestName}</option>)}</select></label>
                        {!pos.folioId && <label>Zahlungsart<select value={pos.paymentMethod} onChange={(e) => setPos({ ...pos, paymentMethod: e.target.value })}><option value="CASH">Bar</option><option value="CARD">Karte</option><option value="BANK_TRANSFER">Überweisung</option><option value="VOUCHER">Gutschein</option></select></label>}
                        <label className="is-wide">Leistung<input value={pos.description} onChange={(e) => setPos({ ...pos, description: e.target.value })} required /></label>
                        <label>Menge<input type="number" min="0.01" step="0.01" value={pos.quantity} onChange={(e) => setPos({ ...pos, quantity: e.target.value })} /></label>
                        <label>Einzelpreis<input type="number" min="0" step="0.01" value={pos.unitPrice} onChange={(e) => setPos({ ...pos, unitPrice: e.target.value })} /></label>
                        <label>MWST %<input type="number" min="0" max="100" step="0.01" value={pos.taxRate} onChange={(e) => setPos({ ...pos, taxRate: e.target.value })} /></label>
                        <div className="pms-form-actions"><button className="is-primary" disabled={!canManage || busy}>Beleg abschliessen</button></div>
                    </form>
                    <div className="pms-record-list">{data?.posTickets?.slice(0, 10).map((ticket) => <article className="pms-record" key={ticket.id}><div><span>{ticket.ticketNumber} · {ticket.paymentMethod}</span><strong>{ticket.outletCode} · {money(ticket.grossAmount, ticket.currencyCode)}</strong><small>{ticket.guestName || ticket.tableReference || 'Direktverkauf'} · {formatPmsDateTime(ticket.createdAt)}</small></div></article>)}</div>
                </section>

                <section className="pms-work-card">
                    <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Zutrittssystem</span><h3>Digitale Zimmerschlüssel</h3></div></div>
                    <form className="pms-form-grid" onSubmit={issueAccess}>
                        <label className="is-wide">Reservierung<select value={access.reservationId} onChange={(e) => setAccess({ ...access, reservationId: e.target.value })} required><option value="">Reservierung mit Zimmer wählen</option>{activeReservations.filter((entry) => entry.roomNumber).map((entry) => <option key={entry.id} value={entry.id}>{entry.roomNumber} · {entry.guestName}</option>)}</select></label>
                        <label>Anbieter<input value={access.providerCode} onChange={(e) => setAccess({ ...access, providerCode: e.target.value })} placeholder="SALTO" required /></label>
                        <label>Externe Referenz<input value={access.externalReference} onChange={(e) => setAccess({ ...access, externalReference: e.target.value })} placeholder="secret-manager:key-…" required /></label>
                        <label>Gültig ab<input type="datetime-local" value={access.validFrom} onChange={(e) => setAccess({ ...access, validFrom: e.target.value })} required /></label>
                        <label>Gültig bis<input type="datetime-local" value={access.validUntil} onChange={(e) => setAccess({ ...access, validUntil: e.target.value })} required /></label>
                        <div className="pms-form-actions is-wide"><button className="is-primary" disabled={!canManage || busy}>Schlüssel ausstellen</button></div>
                    </form>
                    <div className="pms-record-list">{data?.accessCredentials?.map((credential) => <article className="pms-record" key={credential.id}><div><span>{credential.status} · Zimmer {credential.roomNumber}</span><strong>{credential.guestName}</strong><small>{credential.providerCode} · bis {formatPmsDateTime(credential.validUntil)}</small></div>{credential.status === 'ACTIVE' && <button type="button" disabled={!canManage || busy} onClick={() => mutate('post', `/api/pms/properties/${property.id}/access-credentials/${credential.id}/revoke`, undefined, 'Schlüsselwiderruf eingeplant.')}>Widerrufen</button>}</article>)}</div>
                </section>
            </div>

            <div className="pms-operations-layout">
                <section className="pms-work-card">
                    <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Finanzübergabe</span><h3>Buchhaltungsexport</h3></div></div>
                    <form className="pms-form-grid" onSubmit={downloadAccounting}>
                        <label>Von<input type="date" value={accounting.from} onChange={(e) => setAccounting({ ...accounting, from: e.target.value })} required /></label>
                        <label>Bis exklusiv<input type="date" value={accounting.toExclusive} onChange={(e) => setAccounting({ ...accounting, toExclusive: e.target.value })} required /></label>
                        <div className="pms-form-actions is-wide"><button disabled={busy}>CSV herunterladen</button></div>
                    </form>
                </section>
                <section className="pms-work-card">
                    <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Systemwechsel</span><h3>Datenmigration</h3></div></div>
                    <form className="pms-form-grid" onSubmit={importMigration}>
                        <label>Quellsystem<input value={migration.sourceSystem} onChange={(e) => setMigration({ ...migration, sourceSystem: e.target.value })} required /></label>
                        <label>Idempotenzschlüssel<input value={migration.idempotencyKey} onChange={(e) => setMigration({ ...migration, idempotencyKey: e.target.value })} required /></label>
                        <label className="is-wide">Reservationen als JSON<textarea rows="8" value={migration.reservationsJson} onChange={(e) => setMigration({ ...migration, reservationsJson: e.target.value })} required /></label>
                        <div className="pms-form-actions is-wide"><button className="is-primary" disabled={!canManage || busy}>Importieren & abstimmen</button></div>
                    </form>
                    <div className="pms-record-list">{data?.migrationBatches?.map((batch) => <article className="pms-record" key={batch.id}><div><span>{batch.status} · {batch.sourceSystem}</span><strong>{batch.importedReservations} Reservationen · {money(batch.totalOpeningBalance, property.currencyCode)} offen</strong><small>{batch.reconciliationMessage}</small></div></article>)}</div>
                </section>
            </div>
        </div>
    );
};

export default PmsExtensionsWorkspace;
