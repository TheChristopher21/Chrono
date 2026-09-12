import { useEffect, useMemo, useState } from 'react';
import api from '../../utils/api.js';
import './PmsRatePlansWorkspace.css';

const blankRate = (property) => ({
    roomTypeId: property.roomTypes?.[0]?.id ?? '', code: '', name: '', nightlyRate: '',
    minStay: 1, maxStay: '', breakfastIncluded: false, refundable: true, active: true,
    vatRate: '', taxIncluded: true, breakfastAmount: 0, breakfastVatRate: '',
    validFrom: '', validTo: '', bookingFrom: '', bookingTo: '', minAdvanceDays: '', maxAdvanceDays: '',
    includedAdults: 1, extraAdultRate: 0, childRate: 0, cancellationDeadlineHours: '',
    cancellationFeePercent: '', depositPercent: '', paymentDueDays: '', cancellationPolicy: '',
    paymentPolicy: '', notes: '', organizationId: '',
});
const optionalNumbers = ['vatRate', 'breakfastVatRate', 'maxStay', 'minAdvanceDays', 'maxAdvanceDays',
    'cancellationDeadlineHours', 'cancellationFeePercent', 'depositPercent', 'paymentDueDays', 'organizationId'];
const optionalStrings = ['validFrom', 'validTo', 'bookingFrom', 'bookingTo', 'cancellationPolicy', 'paymentPolicy', 'notes'];
const errorText = (error) => error?.response?.data?.detail || error?.response?.data?.message || error.message;
const rounded = (value) => Math.round((value + Number.EPSILON) * 100) / 100;

export function ratePricePreview(form) {
    const breakfast = form.breakfastIncluded ? Number(form.breakfastAmount || 0) : 0;
    const accommodation = Number(form.nightlyRate || 0) - breakfast;
    const lodgingTax = Number(form.vatRate || 0) / 100;
    const breakfastTax = Number(form.breakfastVatRate || 0) / 100;
    const lodgingGross = rounded(form.taxIncluded ? accommodation : accommodation * (1 + lodgingTax));
    const breakfastGross = rounded(form.taxIncluded ? breakfast : breakfast * (1 + breakfastTax));
    const gross = rounded(lodgingGross + breakfastGross);
    const net = rounded(rounded(lodgingGross / (1 + lodgingTax)) + rounded(breakfastGross / (1 + breakfastTax)));
    return { gross, net, tax: rounded(gross - net) };
}

export default function PmsRatePlansWorkspace({ property, operations, canManage, businessDate, onOperationsChange }) {
    const [form, setForm] = useState(() => blankRate(property));
    const [editingId, setEditingId] = useState(null);
    const [search, setSearch] = useState('');
    const [status, setStatus] = useState('all');
    const [pending, setPending] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [override, setOverride] = useState({ ratePlanId: '', stayDate: businessDate ?? '', price: '', minStay: 1, closed: false, closedArrival: false, closedDeparture: false });
    useEffect(() => {
        setEditingId(null); setForm(blankRate(property)); setError(''); setNotice('');
        setOverride({ ratePlanId: '', stayDate: businessDate ?? '', price: '', minStay: 1, closed: false, closedArrival: false, closedDeparture: false });
    }, [property.id]);
    const rates = operations?.ratePlans ?? [];
    const organizations = operations?.organizations ?? [];
    const visibleRates = useMemo(() => rates.filter((rate) =>
        (status === 'all' || rate.active === (status === 'active'))
        && [rate.name, rate.code, rate.roomTypeName, rate.organizationName].some((value) => String(value ?? '').toLocaleLowerCase().includes(search.toLocaleLowerCase()))
    ), [rates, status, search]);
    const preview = ratePricePreview(form);
    const money = (value) => new Intl.NumberFormat('de-CH', { style: 'currency', currency: property.currencyCode || 'CHF' }).format(value);
    const change = (key, value) => setForm((current) => ({ ...current, [key]: value }));
    const input = (key, label, options = {}) => <label key={key}>{label}<input value={form[key] ?? ''} onChange={(event) => change(key, event.target.value)} {...options} /></label>;
    const number = (key, label, options = {}) => input(key, label, { type: 'number', min: 0, step: 1, ...options });
    const percent = (key, label, options = {}) => number(key, label, { max: 100, step: '0.0001', ...options });
    const checkbox = (key, label) => <label className="pms-checkbox" key={key}><input type="checkbox" checked={Boolean(form[key])} onChange={(event) => change(key, event.target.checked)} />{label}</label>;
    const reset = () => { setEditingId(null); setForm(blankRate(property)); };

    const saveRate = async (event) => {
        event.preventDefault();
        if (!canManage || pending) return;
        setError(''); setNotice('');
        const breakfast = form.breakfastIncluded ? Number(form.breakfastAmount || 0) : 0;
        if (breakfast > Number(form.nightlyRate)) { setError('Der Frühstücksanteil darf den Nachtpreis nicht übersteigen.'); return; }
        if (form.maxStay !== '' && Number(form.maxStay) < Number(form.minStay)) { setError('Der Höchstaufenthalt muss mindestens dem Mindestaufenthalt entsprechen.'); return; }
        if ((form.validFrom && form.validTo && form.validFrom > form.validTo) || (form.bookingFrom && form.bookingTo && form.bookingFrom > form.bookingTo)) { setError('Bitte die Reihenfolge der Gültigkeitsdaten prüfen.'); return; }
        const payload = { ...form, roomTypeId: Number(form.roomTypeId), nightlyRate: Number(form.nightlyRate), minStay: Number(form.minStay),
            includedAdults: Number(form.includedAdults), breakfastAmount: breakfast, extraAdultRate: Number(form.extraAdultRate || 0), childRate: Number(form.childRate || 0) };
        optionalNumbers.forEach((key) => { payload[key] = form[key] === '' || form[key] == null ? null : Number(form[key]); });
        optionalStrings.forEach((key) => { payload[key] = form[key] || null; });
        if (!form.breakfastIncluded) payload.breakfastVatRate = null;
        setPending(true);
        try {
            const path = `/api/pms/properties/${property.id}/rate-plans${editingId ? `/${editingId}` : ''}`;
            const response = await api[editingId ? 'put' : 'post'](path, payload, { params: { businessDate } });
            onOperationsChange?.(response.data); reset(); setNotice('Ratenplan gespeichert.');
        } catch (failure) { setError(errorText(failure)); } finally { setPending(false); }
    };
    const saveOverride = async (event) => {
        event.preventDefault();
        if (!canManage || pending) return;
        setPending(true); setError(''); setNotice('');
        try {
            const { ratePlanId, ...values } = override;
            const response = await api.put(`/api/pms/properties/${property.id}/rate-plans/${ratePlanId}/override`, {
                ...values, price: Number(values.price), minStay: Number(values.minStay),
            }, { params: { businessDate } });
            onOperationsChange?.(response.data); setNotice('Tagesrate und Restriktionen gespeichert.');
        } catch (failure) { setError(errorText(failure)); } finally { setPending(false); }
    };
    const edit = (rate) => {
        setEditingId(rate.id); setError(''); setNotice('');
        const next = blankRate(property);
        Object.keys(next).forEach((key) => { if (rate[key] != null) next[key] = rate[key]; });
        setForm(next);
    };
    return <div className="pms-rate-workspace">
        <div className="pms-rate-intro"><div><span className="pms-eyebrow">Revenue Management · {property.name}</span><h3>Raten, Steuern und Verkaufsregeln</h3>
            <p>Preise gelten pro Zimmer und Nacht in {property.currencyCode}. Steuerregeln richten sich nach dem Hotel und der Leistung.</p></div>
            {!canManage && <span>Änderungen nur mit Master-Berechtigung</span>}
        </div>
        {error && <div className="pms-error" role="alert">{error}</div>}
        {notice && <p role="status">{notice}</p>}
        <div className="pms-rate-layout">
            <section className="pms-work-card">
                <h3>{editingId ? 'Ratenplan bearbeiten' : 'Ratenplan anlegen'}</h3>
                <form onSubmit={saveRate}>
                    <fieldset disabled={!canManage || pending} className="pms-rate-fieldset">
                        <div className="pms-form-grid">
                            <label>Zimmertyp<select required value={form.roomTypeId} onChange={(event) => change('roomTypeId', event.target.value)}>{property.roomTypes?.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}</select></label>
                            {input('code', 'Ratencode', { required: true, maxLength: 32 })}
                            {input('name', 'Name', { required: true, maxLength: 120 })}
                            <label>Firmenrate für<select value={form.organizationId} onChange={(event) => change('organizationId', event.target.value)}><option value="">Alle Gäste / öffentliche Rate</option>{organizations.filter((company) => company.active !== false).map((company) => <option key={company.id} value={company.id}>{company.name}</option>)}</select></label>
                            {checkbox('active', 'Aktiv und verkaufbar')}
                            {checkbox('refundable', 'Stornierbar')}
                        </div>
                        <h4>Preis und Steueraufteilung</h4>
                        <div className="pms-form-grid">
                            {number('nightlyRate', 'Standardpreis pro Zimmer/Nacht', { required: true, step: '0.01' })}
                            <label>Preiseingabe<select value={form.taxIncluded ? 'gross' : 'net'} onChange={(event) => change('taxIncluded', event.target.value === 'gross')}><option value="gross">Brutto · inklusive Steuer</option><option value="net">Netto · zuzüglich Steuer</option></select></label>
                            {percent('vatRate', 'Steuer Beherbergung (%)', { required: true })}
                            {checkbox('breakfastIncluded', 'Frühstück inklusive')}
                            {form.breakfastIncluded && number('breakfastAmount', 'Frühstücksanteil im Nachtpreis', { step: '0.01', required: true, max: form.nightlyRate || undefined })}
                            {form.breakfastIncluded && percent('breakfastVatRate', 'Steuer Frühstück (%)', { required: Number(form.breakfastAmount) > 0 })}
                        </div>
                        <p className="pms-rate-help">Frühstück ist ein zugeordneter Anteil des Preises pro Zimmer/Nacht. Für eine getrennte Besteuerung den Anteil und dessen Steuersatz erfassen. Weitere Leistungen und lokale Abgaben werden separat gebucht.</p>
                        <div className="pms-rate-preview" aria-label="Preisvorschau">
                            <span>Netto<strong>{money(preview.net)}</strong></span><span>Steuer<strong>{money(preview.tax)}</strong></span><span>Gastpreis brutto<strong>{money(preview.gross)}</strong></span>
                        </div>
                        {form.vatRate === '' && <p className="pms-rate-help">Steuersatz noch nicht konfiguriert. Die Vorschau enthält bis zur Eingabe keine Steuer.</p>}
                        <h4>Belegung und Zuschläge</h4>
                        <div className="pms-form-grid">
                            {number('includedAdults', 'Enthaltene Erwachsene', { min: 1, max: 100, required: true })}
                            {number('extraAdultRate', 'Je weiterer Erwachsener/Nacht', { step: '0.01' })}
                            {number('childRate', 'Je Kind/Nacht', { step: '0.01' })}
                        </div>
                        <p className="pms-rate-help">Zuschläge folgen der gewählten Brutto-/Nettobasis und dem Beherbergungssteuersatz. Der Frühstücksanteil bleibt pro Zimmer gleich.</p>
                        <h4>Gültigkeit und Buchungsregeln</h4>
                        <div className="pms-form-grid">
                            {input('validFrom', 'Erste Übernachtung', { type: 'date' })}
                            {input('validTo', 'Letzte Übernachtung', { type: 'date' })}
                            {input('bookingFrom', 'Verkauf ab', { type: 'date' })}
                            {input('bookingTo', 'Verkauf bis', { type: 'date' })}
                            {number('minStay', 'Mindestaufenthalt (Nächte)', { min: 1, max: 365, required: true })}
                            {number('maxStay', 'Höchstaufenthalt (Nächte)', { min: 1, max: 365 })}
                            {number('minAdvanceDays', 'Mindestens Tage im Voraus', { max: 3650 })}
                            {number('maxAdvanceDays', 'Höchstens Tage im Voraus', { max: 3650 })}
                        </div>
                        <h4>Storno- und Zahlungsvereinbarung</h4>
                        <div className="pms-form-grid">
                            {number('cancellationDeadlineHours', 'Kostenfrei bis Stunden vor Anreise', { max: 8760 })}
                            {percent('cancellationFeePercent', 'Vereinbarte Stornogebühr (%)')}
                            {percent('depositPercent', 'Vereinbarte Anzahlung (%)')}
                            {number('paymentDueDays', 'Vereinbartes Zahlungsziel (Tage)', { max: 365 })}
                            <label className="is-wide">Stornobedingungen<textarea maxLength={2000} value={form.cancellationPolicy} onChange={(event) => change('cancellationPolicy', event.target.value)} /></label>
                            <label className="is-wide">Zahlungsbedingungen<textarea maxLength={2000} value={form.paymentPolicy} onChange={(event) => change('paymentPolicy', event.target.value)} /></label>
                            <label className="is-wide">Interne Ratennotizen<textarea maxLength={4000} value={form.notes} onChange={(event) => change('notes', event.target.value)} /></label>
                        </div>
                        <p className="pms-rate-help">Bedingungen dokumentieren die Vereinbarung; Stornogebühren und Anzahlungen werden im Gastkonto erfasst.</p>
                        <div className="pms-form-actions">{editingId && <button type="button" onClick={reset}>Abbrechen</button>}<button className="is-primary" type="submit">Ratenplan speichern</button></div>
                    </fieldset>
                </form>
            </section>
            <div className="pms-rate-sidebar">
                <section className="pms-work-card"><h3>{rates.length} Ratenpläne</h3>
                    <div className="pms-form-grid"><label>Raten suchen<input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Name, Code, Zimmertyp, Firma" /></label>
                        <label>Status<select value={status} onChange={(event) => setStatus(event.target.value)}><option value="all">Alle</option><option value="active">Aktiv</option><option value="inactive">Inaktiv</option></select></label></div>
                    <div className="pms-record-list">{visibleRates.map((rate) => <article className="pms-record" key={rate.id}>
                        <div><span>{rate.code} · {rate.roomTypeName} · {rate.active ? 'Aktiv' : 'Inaktiv'}</span><strong>{rate.name}</strong>
                            <small>{money(rate.nightlyRate)} {rate.taxIncluded === false ? 'netto' : 'brutto'} · Steuer {rate.vatRate == null ? 'offen' : `${rate.vatRate} %`}</small>
                            <small>{rate.organizationName || 'Öffentliche Rate'} · {rate.minStay}–{rate.maxStay || '∞'} Nächte</small>
                            {rate.validFrom || rate.validTo ? <small>Übernachtungen {rate.validFrom || 'unbegrenzt'} bis {rate.validTo || 'unbegrenzt'}</small> : null}
                        </div><button type="button" onClick={() => edit(rate)}>{canManage ? 'Bearbeiten' : 'Ansehen'}</button>
                    </article>)}{!visibleRates.length && <p>Keine passenden Ratenpläne.</p>}</div>
                </section>
                <section className="pms-work-card"><h3>Tagespreis und Restriktionen</h3>
                    <p className="pms-rate-help">Der Tagespreis ersetzt den Standardpreis in derselben Brutto-/Nettobasis. Steueraufteilung und Belegungszuschläge gelten weiterhin.</p>
                    <form onSubmit={saveOverride}><fieldset className="pms-rate-fieldset" disabled={!canManage || pending}><div className="pms-form-grid">
                        <label>Ratenplan<select required value={override.ratePlanId} onChange={(event) => setOverride({ ...override, ratePlanId: event.target.value })}><option value="">Ratenplan wählen</option>{rates.map((rate) => <option key={rate.id} value={rate.id}>{rate.name}</option>)}</select></label>
                        <label>Datum<input type="date" required value={override.stayDate} onChange={(event) => setOverride({ ...override, stayDate: event.target.value })} /></label>
                        <label>Tagespreis<input type="number" min="0" step="0.01" required value={override.price} onChange={(event) => setOverride({ ...override, price: event.target.value })} /></label>
                        <label>Tages-Mindestaufenthalt<input type="number" min="1" max="365" required value={override.minStay} onChange={(event) => setOverride({ ...override, minStay: event.target.value })} /></label>
                        {[['closed', 'Verkauf geschlossen (Stop Sell)'], ['closedArrival', 'Anreise gesperrt (CTA)'], ['closedDeparture', 'Abreise gesperrt (CTD)']].map(([key, label]) => <label key={key} className="pms-checkbox"><input type="checkbox" checked={override[key]} onChange={(event) => setOverride({ ...override, [key]: event.target.checked })} />{label}</label>)}
                        <div className="pms-form-actions is-wide"><button className="is-primary" type="submit">Tagesrate speichern</button></div>
                    </div></fieldset></form>
                    <div className="pms-record-list">{(operations?.rateOverrides ?? []).filter((entry) => !override.ratePlanId || String(entry.ratePlanId) === String(override.ratePlanId)).map((entry) => <article className="pms-record" key={entry.id}><div><strong>{entry.stayDate} · {rates.find((rate) => rate.id === entry.ratePlanId)?.code}</strong><small>{money(entry.price)} · {entry.closed ? 'Verkauf gesperrt' : `Min. ${entry.minStay} Nächte`}{entry.closedArrival ? ' · CTA' : ''}{entry.closedDeparture ? ' · CTD' : ''}</small></div><button type="button" onClick={() => setOverride(entry)}>Anpassen</button></article>)}</div>
                </section>
            </div>
        </div>
    </div>;
}
