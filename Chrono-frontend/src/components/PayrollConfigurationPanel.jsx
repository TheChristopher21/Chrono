import React, { useEffect, useState } from 'react';
import api from '../utils/api';
import PayrollTermsPanel from './PayrollTermsPanel';
import '../styles/PayrollConfigurationPanel.css';

const endpoint = '/api/admin/company/payroll-configuration';
const rateFields = [
    ['uvgBuRate', 'BU · Arbeitgeber'], ['uvgNbuRate', 'NBU · Arbeitnehmer'],
    ['ktgRateEmployee', 'KTG · Arbeitnehmer'], ['ktgRateEmployer', 'KTG · Arbeitgeber'], ['fakRate', 'FAK · Arbeitgeber'],
    ['fakEmployeeRate', 'FAK · Arbeitnehmer'], ['ahvAdminRate', 'AHV-Verwaltungskosten (auf AHV/IV/EO-Gesamtbeitrag)'],
];
const blankInsurance = () => Object.fromEntries([...rateFields.map(([key]) => [key, '']), ['ktgAnnualSalaryCap', ''], ['effectiveFrom', ''], ['source', '']]);
const blankPension = () => ({ userId: '', effectiveFrom: '', source: '', bvgEmployee: '', bvgEmployer: '' });
const number = (value, percent = false) => value === '' ? null : (percent ? (Number(value) / 100).toFixed(8) : value);
const money = value => value == null ? 'offen' : `${Number(value).toLocaleString('de-CH', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} CHF`;
const percent = value => value == null ? 'offen' : `${Number((Number(value) * 100).toFixed(6)).toLocaleString('de-CH', { maximumFractionDigits: 6 })} %`;

function BasisFields({ prefix, value, onChange }) {
    return <>
        <label htmlFor={`${prefix}-date`}>Gültig ab</label>
        <input id={`${prefix}-date`} type="date" required min="1900-01-01" max="9999-12-31" value={value.effectiveFrom} onChange={e => onChange({ ...value, effectiveFrom: e.target.value })} />
        <label htmlFor={`${prefix}-source`}>Grundlage / Begründung</label>
        <input id={`${prefix}-source`} required maxLength={250} placeholder="Zum Beispiel Police 2026 oder Vorsorgeausweis" value={value.source} onChange={e => onChange({ ...value, source: e.target.value })} />
    </>;
}

export default function PayrollConfigurationPanel() {
    const [data, setData] = useState(null);
    const [loadError, setLoadError] = useState(false);
    const [retry, setRetry] = useState(0);
    const [insurance, setInsurance] = useState(blankInsurance);
    const [pension, setPension] = useState(blankPension);
    const [saving, setSaving] = useState(null);
    const [message, setMessage] = useState('');
    const [error, setError] = useState('');
    useEffect(() => {
        let active = true;
        setLoadError(false);
        api.get(endpoint).then(({ data: response }) => {
            if (!Array.isArray(response?.versions) || !Array.isArray(response?.employees)) throw new Error('Unvollständige Antwort');
            if (active) setData(response);
        }).catch(() => { if (active) setLoadError(true); });
        return () => { active = false; };
    }, [retry]);

    const save = async (event, kind) => {
        event.preventDefault();
        if (saving) return;
        const input = kind === 'insurance' ? insurance : pension;
        const fields = kind === 'insurance' ? rateFields.map(([key]) => key) : ['bvgEmployee', 'bvgEmployer'];
        if (!input.source.trim() || fields.some(key => input[key] !== '' && (!Number.isFinite(Number(input[key])) || Number(input[key]) < 0 || (kind === 'insurance' && Number(input[key]) > 100)))) {
            setError('Bitte gültige Werte und eine Grundlage eingeben.'); return;
        }
        const payload = { ...input, source: input.source.trim() };
        fields.forEach(key => { payload[key] = number(input[key], kind === 'insurance'); });
        if (kind === 'insurance') payload.ktgAnnualSalaryCap = number(input.ktgAnnualSalaryCap);
        if (kind === 'pension') payload.userId = Number(input.userId);
        setSaving(kind); setError(''); setMessage('');
        try {
            const { data: version } = await api.post(`${endpoint}/${kind}`, payload);
            setData(previous => ({ ...previous, versions: [version, ...previous.versions].sort((a, b) => b.effectiveFrom.localeCompare(a.effectiveFrom) || b.id - a.id) }));
            setMessage('Neue Version gespeichert. Bereits erstellte Abrechnungen wurden nicht verändert.');
            if (kind === 'insurance') setInsurance(blankInsurance());
            else setPension(blankPension());
        } catch (failure) {
            setError(failure.response?.data?.message || failure.response?.data?.detail || 'Speichern fehlgeschlagen. Eingaben bleiben erhalten.');
        } finally { setSaving(null); }
    };

    return <section className="payroll-configuration" aria-labelledby="payroll-configuration-title">
        <h3 id="payroll-configuration-title">Versicherungen & persönliche BVG-Beiträge · Schweiz</h3>
        <p>Jede Firma erfasst ihre eigenen Vertragswerte. Leere Felder bedeuten «noch offen»; 0 bedeutet ausdrücklich «kein Beitrag». Sätze hier in Prozent eingeben, zum Beispiel 1.25 für 1,25 %.</p>
        <p>Jede Speicherung legt eine Version an. Ab dem Gültigkeitsdatum zählt die neueste Version für diesen Zeitpunkt. Alte Versionen und bestehende Abrechnungen bleiben erhalten. Diese Angaben allein bestätigen noch keinen fachlich geprüften Nettolohn.</p>
        {loadError ? <div role="alert">Abrechnungsgrundlagen konnten nicht geladen werden. <button type="button" onClick={() => setRetry(value => value + 1)}>Erneut laden</button></div> : !data ? <p role="status">Abrechnungsgrundlagen werden geladen …</p> : <>
            <div className="payroll-configuration-forms">
                <form onSubmit={event => save(event, 'insurance')} aria-label="Firmenversicherungen">
                    <fieldset disabled={Boolean(saving)}>
                        <legend>Firmenversicherungen</legend>
                        <BasisFields prefix="insurance" value={insurance} onChange={setInsurance} />
                        {rateFields.map(([key, label]) => <React.Fragment key={key}>
                            <label htmlFor={`insurance-${key}`}>{label} (%)</label>
                            <input id={`insurance-${key}`} type="number" min="0" max="100" step="0.000001" placeholder="Noch offen" value={insurance[key]} onChange={e => setInsurance({ ...insurance, [key]: e.target.value })} />
                        </React.Fragment>)}
                        <label htmlFor="insurance-ktg-cap">KTG-Jahreslohngrenze (CHF; 0 = vertraglich unbeschränkt)</label>
                        <input id="insurance-ktg-cap" type="number" min="0" step="0.01" value={insurance.ktgAnnualSalaryCap} onChange={e => setInsurance({ ...insurance, ktgAnnualSalaryCap: e.target.value })} />
                        <button type="submit" className="primary-btn">{saving === 'insurance' ? 'Wird gespeichert …' : 'Versicherungsversion anlegen'}</button>
                    </fieldset>
                </form>
                <form onSubmit={event => save(event, 'pension')} aria-label="Persönliche BVG-Beiträge">
                    <fieldset disabled={Boolean(saving)}>
                        <legend>Persönliche BVG-Beiträge</legend>
                        <label htmlFor="pension-user">Mitarbeitende</label>
                        <select id="pension-user" required value={pension.userId} onChange={e => setPension({ ...blankPension(), userId: e.target.value })}>
                            <option value="">Bitte auswählen</option>
                            {data.employees.map(employee => <option key={employee.id} value={employee.id}>{employee.name}</option>)}
                        </select>
                        <BasisFields prefix="pension" value={pension} onChange={setPension} />
                        <p>Monatliche Beiträge gemäss Vorsorgeausweis in CHF. Bei keinem Beitrag beide Felder ausdrücklich mit 0 ausfüllen und die Begründung angeben.</p>
                        {[['bvgEmployee', 'BVG Arbeitnehmer (CHF / Monat)'], ['bvgEmployer', 'BVG Arbeitgeber (CHF / Monat)']].map(([key, label]) => <React.Fragment key={key}>
                            <label htmlFor={key}>{label}</label>
                            <input id={key} type="number" min="0" max="9999999999.99" step="0.01" placeholder="Noch offen" value={pension[key]} onChange={e => setPension({ ...pension, [key]: e.target.value })} />
                        </React.Fragment>)}
                        <button type="submit" className="primary-btn">{saving === 'pension' ? 'Wird gespeichert …' : 'BVG-Version anlegen'}</button>
                    </fieldset>
                </form>
            </div>
            <PayrollTermsPanel employees={data.employees} />
            <h4>Gespeicherte Versicherungsversionen</h4>
            {data.versions.length === 0 ? <p>Noch keine zeitlich gültigen Vertragswerte hinterlegt.</p> : <div className="payroll-configuration-history">
                <table><thead><tr><th>Gültig ab</th><th>Geltungsbereich</th><th>Werte</th><th>Grundlage</th><th>Version</th></tr></thead>
                    <tbody>{data.versions.map(version => <tr key={version.id}>
                        <td>{version.effectiveFrom}</td>
                        <td>{version.userId == null ? 'Firma' : data.employees.find(employee => employee.id === version.userId)?.name || `Mitarbeitende #${version.userId}`}</td>
                        <td>{version.userId == null ? <>{rateFields.map(([key, label]) => <div key={key}>{label}: {percent(version[key])}</div>)}<div>KTG-Jahreslohngrenze: {version.ktgAnnualSalaryCap != null && Number(version.ktgAnnualSalaryCap) === 0 ? 'Unbeschränkt' : money(version.ktgAnnualSalaryCap)}</div></> : <>AN: {money(version.bvgEmployee)}<br />AG: {money(version.bvgEmployer)}</>}</td>
                        <td>{version.source}</td><td>#{version.id}</td>
                    </tr>)}</tbody>
                </table>
            </div>}
        </>}
        {error && <p role="alert">{error}</p>}
        {message && <p role="status">{message}</p>}
    </section>;
}
