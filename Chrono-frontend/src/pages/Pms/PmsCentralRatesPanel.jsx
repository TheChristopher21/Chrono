import { useEffect, useId, useRef, useState } from 'react';
import api from '../../utils/api.js';
import { currencyStep, formatPmsMoney } from './pmsMoney.js';
import './PmsCentralRatesPanel.css';

const errorText = (failure) => failure?.response?.data?.detail || failure?.response?.data?.message || failure.message;
const amounts = [['nightlyRate', 'Nachtpreis'], ['breakfastAmount', 'Frühstücksanteil'], ['extraAdultRate', 'Weiterer Erwachsener'], ['childRate', 'Kind']];
const numberOrNull = (value) => value === '' || value == null ? null : Number(value);

export default function PmsCentralRatesPanel({ property, rates }) {
    const titleId = useId();
    const [hotels, setHotels] = useState([]);
    const [sourceId, setSourceId] = useState('');
    const [hotelId, setHotelId] = useState('');
    const [targets, setTargets] = useState([]);
    const [localRates, setLocalRates] = useState({});
    const [pending, setPending] = useState(false);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const sequence = useRef(0);
    const source = rates.find((rate) => String(rate.id) === sourceId);
    const requiresFeeTax = Number(source?.cancellationFeePercent) > 0 || Number(source?.noShowFeePercent) > 0;
    useEffect(() => {
        const controller = new AbortController();
        setLoading(true);
        api.get('/api/pms/setup', { params: { includeRooms: false }, signal: controller.signal }).then(({ data }) => {
            if (!controller.signal.aborted) setHotels((data.properties || []).filter((hotel) => hotel.active !== false));
        }).catch((failure) => { if (!controller.signal.aborted) setError(errorText(failure)); })
            .finally(() => { if (!controller.signal.aborted) setLoading(false); });
        return () => controller.abort();
    }, []);
    const change = (key, values) => setTargets((current) => current.map((target) => target.key === key ? { ...target, ...values } : target));
    const add = async () => {
        const hotel = hotels.find((value) => String(value.id) === hotelId);
        if (!source || !hotel || pending) return;
        setError(''); setNotice('');
        setTargets((current) => [...current, { key: ++sequence.current, propertyId: hotel.id, roomTypeId: '', targetRatePlanId: '',
            code: source.code, name: source.name, currencyCode: hotel.currencyCode,
            nightlyRate: '', breakfastAmount: source.breakfastIncluded ? '' : 0, extraAdultRate: '', childRate: '',
            vatRate: '', breakfastVatRate: '', policyFeeTaxRate: '' }]);
        if (!localRates[hotel.id]) {
            try {
                const { data } = await api.get('/api/pms/operations', { params: { propertyId: hotel.id } });
                setLocalRates((current) => ({ ...current, [hotel.id]: data.ratePlans || [] }));
            } catch (failure) { setError(`Bestehende Zielraten konnten nicht geladen werden: ${errorText(failure)}`); }
        }
    };
    const chooseExisting = (target, value) => {
        const rate = (localRates[target.propertyId] || []).find((item) => String(item.id) === value);
        change(target.key, rate ? { targetRatePlanId: value, code: rate.code, name: rate.name,
            ...Object.fromEntries([...amounts.map(([key]) => key), 'vatRate', 'breakfastVatRate', 'policyFeeTaxRate'].map((key) => [key, rate[key] ?? ''])) }
            : { targetRatePlanId: '' });
    };
    const publish = async (event) => {
        event.preventDefault();
        if (!source || !targets.length || pending) return;
        setError(''); setNotice('');
        if (targets.some((target) => !target.roomTypeId || !target.code.trim() || !target.name.trim()
            || amounts.some(([key]) => target[key] === '' || Number(target[key]) < 0 || !Number.isFinite(Number(target[key])))
            || target.vatRate === '' || source.breakfastIncluded && target.breakfastVatRate === '' || requiresFeeTax && target.policyFeeTaxRate === '')) {
            setError('Für jedes Ziel einen Zimmertyp, lokale Preise und die erforderlichen Steuersätze eingeben. 0 ist ein ausdrücklicher Wert.'); return;
        }
        if (targets.some((target) => Number(target.breakfastAmount) > Number(target.nightlyRate))) { setError('Der Frühstücksanteil darf den Nachtpreis im Zielhotel nicht übersteigen.'); return; }
        const body = { targets: targets.map(({ key, ...target }) => ({ ...target, roomTypeId: Number(target.roomTypeId),
            targetRatePlanId: numberOrNull(target.targetRatePlanId), ...Object.fromEntries(amounts.map(([name]) => [name, Number(target[name])])),
            vatRate: Number(target.vatRate), breakfastVatRate: numberOrNull(target.breakfastVatRate), policyFeeTaxRate: numberOrNull(target.policyFeeTaxRate) })) };
        setPending(true);
        try {
            const { data } = await api.post(`/api/pms/rate-plans/${source.id}/publish`, body);
            setNotice(`${data.targets.length} Zielrate${data.targets.length === 1 ? '' : 'n'} veröffentlicht. Die lokale Vorlage bleibt erhalten.`);
            setTargets([]); setLocalRates({});
        } catch (failure) { setError(errorText(failure)); }
        finally { setPending(false); }
    };
    return <section className="pms-work-card pms-central-rates" aria-labelledby={titleId}>
        <div className="pms-central-rates-heading"><div><span className="pms-eyebrow">Hotelkette · Master</span><h3 id={titleId}>Raten in weiteren Hotels veröffentlichen</h3></div><span>Vorlage aus {property.name}</span></div>
        <p className="pms-rate-help">Die gespeicherte Vorlage überträgt Verkaufsregeln, Zahlungsfristen und Firmenzuordnung. Preise und Steuern werden je Zielhotel in dessen Währung angegeben. Bestehende Buchungen behalten ihre vereinbarten Bedingungen.</p>
        {error && <p className="pms-error" role="alert">{error}</p>}{notice && <p role="status">{notice}</p>}
        <form onSubmit={publish}><fieldset disabled={pending || loading} className="pms-rate-fieldset">
            <div className="pms-form-grid"><label>Gespeicherte Ratenvorlage<select required value={sourceId} onChange={(event) => { setSourceId(event.target.value); setTargets([]); setNotice(''); }}><option value="">Vorlage wählen</option>{rates.map((rate) => <option key={rate.id} value={rate.id}>{rate.code} · {rate.name}</option>)}</select></label>
                <label>Zielhotel<select value={hotelId} onChange={(event) => setHotelId(event.target.value)}><option value="">Hotel wählen</option>{hotels.filter((hotel) => hotel.id !== property.id).map((hotel) => <option key={hotel.id} value={hotel.id}>{hotel.name} · {hotel.currencyCode}</option>)}</select></label>
                <div className="pms-form-actions"><button type="button" disabled={!source || !hotelId || targets.length >= 100} onClick={add}>Zielhotel hinzufügen</button></div></div>
            {targets.map((target) => {
                const hotel = hotels.find((value) => value.id === target.propertyId);
                const candidates = (localRates[target.propertyId] || []).filter((rate) => String(rate.roomTypeId) === String(target.roomTypeId) && rate.id !== source?.id);
                return <article className="pms-central-rate-target" key={target.key} aria-label={`Zielrate ${hotel?.name}`}>
                    <div className="pms-central-rates-heading"><h4>{hotel?.name} · {target.currencyCode}</h4><button type="button" onClick={() => setTargets((current) => current.filter((value) => value.key !== target.key))}>Ziel entfernen</button></div>
                    <div className="pms-form-grid"><label>Ziel-Zimmertyp<select required value={target.roomTypeId} onChange={(event) => change(target.key, { roomTypeId: event.target.value, targetRatePlanId: '' })}><option value="">Zimmertyp zuordnen</option>{hotel?.roomTypes?.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}</select></label>
                        <label>Zielrate<select value={target.targetRatePlanId} onChange={(event) => chooseExisting(target, event.target.value)}><option value="">Neue Rate anlegen</option>{candidates.map((rate) => <option key={rate.id} value={rate.id}>{rate.code} · bestehende Rate aktualisieren</option>)}</select></label>
                        <label>Ziel-Ratencode<input required maxLength={32} value={target.code} onChange={(event) => change(target.key, { code: event.target.value })} /></label>
                        <label>Ziel-Ratenname<input required maxLength={120} value={target.name} onChange={(event) => change(target.key, { name: event.target.value })} /></label>
                        {amounts.map(([key, label]) => <label key={key}>{label} ({target.currencyCode})<input required type="number" min="0" step={currencyStep(target.currencyCode)} value={target[key]} onChange={(event) => change(target.key, { [key]: event.target.value })} /></label>)}
                        {[['vatRate', 'Steuer Beherbergung', true], ['breakfastVatRate', 'Steuer Frühstück', source?.breakfastIncluded], ['policyFeeTaxRate', 'Steuer Storno/Nichtanreise', requiresFeeTax]].map(([key, label, required]) => <label key={key}>{label} im Zielhotel (%)<input required={Boolean(required)} type="number" min="0" max="100" step="0.0001" value={target[key]} onChange={(event) => change(target.key, { [key]: event.target.value })} /></label>)}
                    </div>
                    <p className="pms-central-rate-summary">{target.targetRatePlanId ? 'Bestehende Rate aktualisieren' : 'Neue Rate'} · {target.code || 'Code fehlt'} · {target.nightlyRate === '' ? 'Lokalen Nachtpreis festlegen' : formatPmsMoney(target.nightlyRate, target.currencyCode)} {source?.taxIncluded === false ? 'netto' : 'brutto'}</p>
                </article>;
            })}
            {targets.length > 0 && <div className="pms-form-actions"><button className="is-primary" type="submit">{pending ? 'Wird veröffentlicht …' : `${targets.length} Zielrate${targets.length === 1 ? '' : 'n'} veröffentlichen`}</button></div>}
        </fieldset></form>
        {loading && <p role="status">Hotels werden geladen …</p>}
    </section>;
}
