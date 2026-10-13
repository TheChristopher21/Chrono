import { currencyStep } from './pmsMoney.js';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import api from '../../utils/api.js';
import usePmsLiveRefresh from './usePmsLiveRefresh.js';
import PmsAccountingSettingsPanel from './PmsAccountingSettingsPanel.jsx';
import PmsHistoryPanel from './PmsHistoryPanel.jsx';
import PmsAccessCredentialsPanel from './PmsAccessCredentialsPanel.jsx';
import { PmsTranslationBoundary, usePmsLocale } from './pmsI18n.jsx';
import { formatPmsDateTime } from './pmsFormatting.js';
import { getFolioDisplayLabel } from './pmsTerminology.js';

const errorMessage = (error) => error?.response?.data?.detail
    || error?.response?.data?.message || error?.message || 'Die Aktion konnte nicht abgeschlossen werden.';

const PmsExtensionsWorkspace = ({ property, operations, businessDate, canManage, canFinance = canManage, canViewFinance = true, canViewFrontDesk = true, canManageFrontDesk = canManage, canViewReports = true, canViewIntegrations = true, canManageSettings = false, onOperationsChange }) => {
    const locale = usePmsLocale();
    const money = (value, currency = 'CHF') => new Intl.NumberFormat(locale, {
        style: 'currency', currency,
    }).format(Number(value ?? 0));
    const [data, setData] = useState(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const bookingDirty = useRef(false);
    const taxDirty = useRef(false);
    const editBooking = (value) => { bookingDirty.current = true; setBooking(value); };
    const editTax = (value) => { taxDirty.current = true; setTax(value); };
    const [booking, setBooking] = useState({
        publicSlug: property?.code?.toLowerCase() ?? '', enabled: false, requireGuarantee: false, termsUrl: '', privacyUrl: '', confirmationMessage: '',
    });
    const [tax, setTax] = useState({
        enabled: false, name: 'Kurtaxe', adultRate: 0, childRate: 0, childFreeUnder: 16, maximumNights: '',
    });
    const [taxPosting, setTaxPosting] = useState({ reservationId: '', chargeableChildren: 0 });
    const [pos, setPos] = useState({
        folioId: '', outletCode: 'RESTAURANT', tableReference: '', paymentMethod: 'CASH',
        description: '', quantity: 1, unitPrice: 0, taxRate: '',
    });
    const [cashShifts, setCashShifts] = useState([]);
    useEffect(() => {
        if (!property?.id) return;
        api.get(`/api/pms/properties/${property.id}/cash-shifts`).then(({ data }) => setCashShifts(Array.isArray(data) ? data : [])).catch(() => setCashShifts([]));
    }, [property?.id, operations]);
    const [migration, setMigration] = useState({
        idempotencyKey: `migration-${businessDate}`, sourceSystem: '', reservationsJson: '[]',
    });
    const [accounting, setAccounting] = useState({
        from: `${businessDate.slice(0, 7)}-01`, toExclusive: businessDate,
    });

    const activeReservations = useMemo(() => (operations?.reservations ?? [])
        .filter((entry) => !['CANCELLED', 'NO_SHOW', 'CHECKED_OUT'].includes(entry.status)), [operations]);
    const openFolios = useMemo(() => (operations?.folios ?? []).filter((entry) => entry.status === 'OPEN'), [operations]);

    const applyData = useCallback((next, resetForm = null) => {
        setData(next);
        if (resetForm === 'booking') bookingDirty.current = false;
        if (resetForm === 'tax') taxDirty.current = false;
        if (next?.bookingEngine && !bookingDirty.current) setBooking({
            publicSlug: next.bookingEngine.publicSlug ?? property?.code?.toLowerCase() ?? '',
            enabled: next.bookingEngine.enabled,
            requireGuarantee: next.bookingEngine.requireGuarantee,
            termsUrl: next.bookingEngine.termsUrl ?? '',
            privacyUrl: next.bookingEngine.privacyUrl ?? '',
            confirmationMessage: next.bookingEngine.confirmationMessage ?? '',
        });
        if (next?.tourismTax && !taxDirty.current) setTax({
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
    usePmsLiveRefresh(async () => {
        const response = await api.get('/api/pms/extensions', { params: { propertyId: property.id } });
        setData(response.data);
    }, { enabled: Boolean(property?.id) && !busy });

    const mutate = async (method, url, payload, success, refreshOperations = false, resetForm = null) => {
        setBusy(true);
        setError('');
        setNotice('');
        try {
            const response = await api[method](url, payload);
            applyData(response.data, resetForm);
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
        if (!canManageSettings) return;
        return mutate('put', `/api/pms/properties/${property.id}/booking-engine`, booking,
            booking.enabled ? `Onlinebuchung ist aktiv: /book/${booking.publicSlug}` : 'Onlinebuchung deaktiviert.', false, 'booking');
    };

    const submitTax = (event) => {
        event.preventDefault();
        if (!canManageSettings) return;
        return mutate('put', `/api/pms/properties/${property.id}/tourism-tax`, {
            ...tax, maximumNights: tax.maximumNights === '' ? null : Number(tax.maximumNights),
        }, 'Kurtaxenregel gespeichert.', false, 'tax');
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
            cashShiftId: !roomCharge && pos.paymentMethod === 'CASH' && pos.cashShiftId ? Number(pos.cashShiftId) : null,
            paymentReference: !roomCharge && pos.paymentMethod === 'CARD' ? pos.paymentReference : null,
            lines: [{
                description: pos.description,
                quantity: Number(pos.quantity),
                unitPrice: Number(pos.unitPrice),
                taxRate: Number(pos.taxRate),
            }],
        }, roomCharge ? 'POS-Beleg auf das Gastkonto gebucht.' : 'POS-Beleg direkt bezahlt.', true);
        if (ok) setPos((current) => ({ ...current, description: '', quantity: 1, unitPrice: 0 }));
    };


    const importMigration = async (event) => {
        event.preventDefault();
        if (!canManageSettings) return;
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
        if (!canViewReports || busy) return;
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
        <PmsTranslationBoundary>
        <div className="pms-operations-stack">
            {error && <div className="pms-inline-message is-error" role="alert">{error}</div>}
            {notice && <div className="pms-inline-message is-success" role="status">{notice}</div>}
            <div className="pms-operations-layout">
                <section className="pms-work-card">
                    <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Direktvertrieb</span><h3>Booking Engine</h3></div></div>
                    <form onSubmit={submitBooking}><fieldset className="pms-form-grid pms-plain-fieldset" disabled={!canManageSettings || busy}>
                        <label className="pms-checkbox-row"><input type="checkbox" checked={booking.enabled} onChange={(e) => editBooking({ ...booking, enabled: e.target.checked })} /> Onlinebuchung aktiv</label>
                        <label className="pms-checkbox-row"><input type="checkbox" checked={booking.requireGuarantee} onChange={(e) => editBooking({ ...booking, requireGuarantee: e.target.checked })} /> Garantie/Anzahlung verlangen</label>
                        <label className="is-wide">Öffentlicher Buchungsname<input value={booking.publicSlug} pattern="[a-z0-9](?:[a-z0-9-]{1,118}[a-z0-9])?" onChange={(e) => editBooking({ ...booking, publicSlug: e.target.value.toLowerCase() })} required /><small>/book/{booking.publicSlug || 'hotelname'}</small></label>
                        <label className="is-wide">AGB-Adresse<input type="url" value={booking.termsUrl} onChange={(e) => editBooking({ ...booking, termsUrl: e.target.value })} placeholder="https://…" /></label>
                        <label className="is-wide">Datenschutz-Adresse<input type="url" value={booking.privacyUrl} onChange={(e) => editBooking({ ...booking, privacyUrl: e.target.value })} placeholder="https://…" /></label>
                        <label className="is-wide">Bestätigungstext<textarea value={booking.confirmationMessage} onChange={(e) => editBooking({ ...booking, confirmationMessage: e.target.value })} /></label>
                        <div className="pms-form-actions is-wide"><button className="is-primary" disabled={!canManageSettings || busy}>Booking Engine speichern</button></div>
                    </fieldset></form>
                </section>

                <section className="pms-work-card">
                    <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Gemeindeabgaben</span><h3>Kurtaxe</h3></div></div>
                    <form onSubmit={submitTax}><fieldset className="pms-form-grid pms-plain-fieldset" disabled={!canManageSettings || busy}>
                        <label className="pms-checkbox-row"><input type="checkbox" checked={tax.enabled} onChange={(e) => editTax({ ...tax, enabled: e.target.checked })} /> Regel aktiv</label>
                        <label>Name<input value={tax.name} onChange={(e) => editTax({ ...tax, name: e.target.value })} required /></label>
                        <label>Erwachsene/Nacht<input type="number" min="0" step={currencyStep(property.currencyCode)} value={tax.adultRate} onChange={(e) => editTax({ ...tax, adultRate: e.target.value })} /></label>
                        <label>Kinder/Nacht<input type="number" min="0" step={currencyStep(property.currencyCode)} value={tax.childRate} onChange={(e) => editTax({ ...tax, childRate: e.target.value })} /></label>
                        <label>Kinder frei unter<input type="number" min="0" max="21" value={tax.childFreeUnder} onChange={(e) => editTax({ ...tax, childFreeUnder: e.target.value })} /></label>
                        <label>Max. Nächte<input type="number" min="1" value={tax.maximumNights} onChange={(e) => editTax({ ...tax, maximumNights: e.target.value })} /></label>
                        <div className="pms-form-actions is-wide"><button disabled={!canManageSettings || busy}>Regel speichern</button></div>
                    </fieldset></form>
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
                        {!pos.folioId && pos.paymentMethod === 'CASH' && <label>Kassenschicht<select value={pos.cashShiftId || ''} onChange={(event) => setPos({ ...pos, cashShiftId: event.target.value })} required><option value="">Kasse auswählen</option>{cashShifts.filter((shift) => shift.status === 'OPEN').map((shift) => <option key={shift.id} value={shift.id}>{shift.registerCode} · {shift.outletCode}</option>)}</select></label>}
                        {!pos.folioId && pos.paymentMethod === 'CARD' && <label className="is-wide">Belegreferenz der extern ausgeführten Kartenzahlung<input value={pos.paymentReference || ''} onChange={(event) => setPos({ ...pos, paymentReference: event.target.value })} required maxLength={190} placeholder="Eindeutige Transaktion des Kassenterminals" /></label>}
                        <label className="is-wide">Leistung<input value={pos.description} onChange={(e) => setPos({ ...pos, description: e.target.value })} required /></label>
                        <label>Menge<input type="number" min="0.01" step="0.01" value={pos.quantity} onChange={(e) => setPos({ ...pos, quantity: e.target.value })} /></label>
                        <label>Einzelpreis<input type="number" min="0" step={currencyStep(property.currencyCode)} value={pos.unitPrice} onChange={(e) => setPos({ ...pos, unitPrice: e.target.value })} /></label>
                        <label>MWST %<input type="number" min="0" max="100" step="0.01" required value={pos.taxRate} onChange={(e) => setPos({ ...pos, taxRate: e.target.value })} /></label>
                        <div className="pms-form-actions"><button className="is-primary" disabled={!canManage || busy}>Beleg abschliessen</button></div>
                    </form>
                    <div className="pms-record-list">{data?.posTickets?.slice(0, 10).map((ticket) => <article className="pms-record" key={ticket.id}><div><span>{ticket.ticketNumber} · {ticket.paymentMethod}</span><strong>{ticket.outletCode} · {money(ticket.grossAmount, ticket.currencyCode)}</strong><small>{ticket.guestName || ticket.tableReference || 'Direktverkauf'} · {formatPmsDateTime(ticket.createdAt)}</small></div></article>)}</div>
                </section>

                {canViewFrontDesk && <PmsAccessCredentialsPanel key={property.id} property={property} operations={operations} businessDate={businessDate} canManage={canManageFrontDesk} />}
            </div>

            <div className="pms-operations-layout">
                {canViewReports && <section className="pms-work-card">
                    <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Finanzübergabe</span><h3>Buchhaltungsexport</h3></div></div>
                    <form className="pms-form-grid" onSubmit={downloadAccounting}>
                        <label>Von<input type="date" value={accounting.from} onChange={(e) => setAccounting({ ...accounting, from: e.target.value })} required /></label>
                        <label>Bis exklusiv<input type="date" value={accounting.toExclusive} onChange={(e) => setAccounting({ ...accounting, toExclusive: e.target.value })} required /></label>
                        <div className="pms-form-actions is-wide"><button disabled={busy}>CSV herunterladen</button></div>
                    </form>
                </section>}
                <section className="pms-work-card">
                    <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Systemwechsel</span><h3>Datenmigration</h3></div></div>
                    <form className="pms-form-grid" onSubmit={importMigration}>
                        <label>Quellsystem<input value={migration.sourceSystem} onChange={(e) => setMigration({ ...migration, sourceSystem: e.target.value })} required /></label>
                        <label>Idempotenzschlüssel<input value={migration.idempotencyKey} onChange={(e) => setMigration({ ...migration, idempotencyKey: e.target.value })} required /></label>
                        <label className="is-wide">Reservationen als JSON<textarea rows="8" value={migration.reservationsJson} onChange={(e) => setMigration({ ...migration, reservationsJson: e.target.value })} required /></label>
                        <div className="pms-form-actions is-wide"><button className="is-primary" disabled={!canManageSettings || busy}>Importieren & abstimmen</button></div>
                    </form>
                    <div className="pms-record-list">{data?.migrationBatches?.map((batch) => <article className="pms-record" key={batch.id}><div><span>{batch.status} · {batch.sourceSystem}</span><strong>{batch.importedReservations} Reservationen · {money(batch.totalOpeningBalance, property.currencyCode)} offen</strong><small>{batch.reconciliationMessage}</small></div></article>)}</div>
                </section>
            </div>
            {canViewReports && <PmsAccountingSettingsPanel key={property.id} propertyId={property.id} property={property} canManage={canManageSettings} />}
            <PmsHistoryPanel propertyId={property.id} currencyCode={property.currencyCode} sections={[...(canViewFinance ? ['pos-tickets'] : []), ...(canViewIntegrations ? ['migration-batches'] : [])]} />
        </div>
        </PmsTranslationBoundary>
    );
};

export default PmsExtensionsWorkspace;
