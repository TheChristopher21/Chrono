import { useEffect, useState } from 'react';
import api from '../utils/api';

const endpoint = '/api/admin/company/payroll-configuration/openings';
const blank = (start = '2026-01-01', end = '2026-01-31') => ({ start, end, base: '', premium: '', overtimePayout: '', thirteenth: '', withholding: '', insuranceDays: '',
  thirteenthEligibleBase: '', alvUvgBase: '', ktgBase: '', ktgAnnualCap: '', pensionBase: '', healthBase: '', solidarityTax: '', churchTax: '', swissOtherAperiodicGross: '', ktgPoolKey: '', ktgPoolFrom: '', ktgPoolThrough: '', taxAllocations: [] });
const common = [['base', 'Periodischer Grundlohn inkl. bezahlter Abwesenheiten/Zuschläge'], ['premium', 'Überstundenzuschläge'], ['overtimePayout', 'Überstundenauszahlungen'],
  ['thirteenth', 'Bereits bezahlter 13. Monatslohn'], ['thirteenthEligibleBase', 'Anspruchsberechtigte Basis für den 13. Monatslohn'], ['withholding', 'Bereits abgezogene Quellen-/Lohnsteuer (Erstattung negativ)']];
const swiss = [['alvUvgBase', 'Bereits belastete ALV-/UVG-Basis'], ['ktgBase', 'Bereits belastete KTG-Basis'], ['ktgAnnualCap', 'Vertragliche KTG-Jahreslohngrenze (0 = unbeschränkt)']];
const german = [['pensionBase', 'Bereits belastete RV-/ALV-Basis'], ['healthBase', 'Bereits belastete KV-/PV-Basis'], ['solidarityTax', 'Bereits abgezogener Solidaritätszuschlag'], ['churchTax', 'Bereits abgezogene Kirchensteuer']];

export default function PayrollOpeningPanel({ employees }) {
  const [versions, setVersions] = useState(null), [retry, setRetry] = useState(0);
  const [form, setForm] = useState({ userId: '', country: 'CH', payrollStart: '', source: '', confirmed: false, mode: 'NEW', correctionReason: '', correctionDate: '', originalPaid: false, certificateUnsent: false, annualPermitted: false });
  const [periods, setPeriods] = useState([blank()]);
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [message, setMessage] = useState('');
  useEffect(() => { let active = true; api.get(endpoint).then(({ data }) => {
    if (!Array.isArray(data)) throw new Error(); if (active) { setVersions(data); setError(''); }
  }).catch(() => { if (active) setError('Eröffnungswerte konnten nicht geladen werden.'); }); return () => { active = false; }; }, [retry]);
  const change = (key, value) => setForm(previous => ({ ...previous, [key]: value }));
  const periodChange = (index, key, value) => setPeriods(previous => previous.map((p, i) => i === index ? { ...p, [key]: value } : p));
  function addMonth() {
    const last = periods.at(-1), date = new Date(`${last.end}T12:00:00Z`);
    if (!Number.isFinite(date.getTime())) return;
    date.setUTCDate(date.getUTCDate() + 1); const start = date.toISOString().slice(0, 10);
    date.setUTCMonth(date.getUTCMonth() + 1, 0); const end = date.toISOString().slice(0, 10);
    setPeriods(previous => [...previous, blank(start, end)]);
  }
  async function save(event) {
    event.preventDefault(); if (busy || !versions) return;
    const fields = [...common, ...(form.country === 'CH' ? swiss : german)].map(([key]) => key);
    const rows = periods.map(p => ({ start: p.start, end: p.end, insuranceDays: Number(p.insuranceDays),
      ...Object.fromEntries(fields.map(key => [key, p[key] === '' ? null : p[key]])),
      swissOtherAperiodicGross: form.country === 'CH' && p.swissOtherAperiodicGross !== '' ? p.swissOtherAperiodicGross : null,
      ktgPool: form.country === 'CH' && p.ktgPoolKey.trim() ? { key: p.ktgPoolKey.trim(), from: p.ktgPoolFrom, through: p.ktgPoolThrough } : null,
      taxAllocations: form.country === 'CH' && p.taxAllocations.length ? p.taxAllocations : null }));
    setBusy(true); setError(''); setMessage('');
    try {
      const opening = { userId: Number(form.userId), payrollStart: form.payrollStart, source: form.source.trim(), confirmed: form.confirmed, periods: rows };
      const crossYear = form.correctionDate && form.correctionDate.slice(0, 4) !== '2026';
      const payload = form.mode === 'NEW' ? opening : { opening, correction: { reason: form.correctionReason.trim(), payoutDate: form.correctionDate, replacementOvertimeHours: null,
        ...(crossYear ? { yearAssignment: { source: form.source.trim(), originalGrossAlreadyPaid: form.originalPaid, germanCertificateNotTransmitted: form.certificateUnsent, germanAnnualAdjustmentPermitted: form.annualPermitted } } : {}) } };
      const { data } = await api.post(form.mode === 'NEW' ? endpoint : `${endpoint}/corrections`, payload);
      setVersions(previous => [form.mode === 'NEW' ? data : data.opening, ...previous]);
      setMessage(form.mode === 'NEW' ? 'Eröffnungswerte gespeichert. Alte Abrechnungen und Zeitkonten bleiben erhalten.' : 'Eröffnungsberichtigung und Korrekturlauf erstellt. Die neue Grundlage wird erst mit der vollständigen Freigabe des Korrekturlaufs verwendet.');
    } catch (failure) { setError(failure.response?.data?.message || failure.response?.data?.detail || 'Eröffnungswerte konnten nicht gespeichert werden.'); }
    finally { setBusy(false); }
  }
  return <section aria-labelledby="opening-title">
    <h4 id="opening-title">Eröffnungswerte für den Lohnstart 2026</h4>
    <p>Die tatsächlichen Vorperioden seit Jahresbeginn oder Eintritt lückenlos erfassen. Grundlage sind Lohnunterlagen und tatsächlich verbuchte Beträge. Alte Belege zum Stundenabbau werden nicht automatisch als Lohnhistorie übernommen. Historische persönliche Lohnregeln müssen die jeweiligen Monate abdecken.</p>
    <p>Leere Beträge sind unbekannt. Auch Nullwerte ausdrücklich eintragen. Nach einer Lohnfreigabe werden Berichtigungen als neue Version mit einem vollständigen Korrekturlauf erstellt. Frühere Werte bleiben erhalten.</p>
    {versions === null ? <>{error && <p role="alert">{error}</p>}<button type="button" onClick={() => setRetry(v => v + 1)}>Eröffnungswerte erneut laden</button></> : <>
      <form onSubmit={save} aria-label="Eröffnungswerte"><fieldset disabled={busy}>
        <label htmlFor="opening-user">Mitarbeitende für Eröffnungswerte</label>
        <select id="opening-user" required value={form.userId} onChange={e => change('userId', e.target.value)}><option value="">Bitte auswählen</option>{employees.map(e => <option key={e.id} value={e.id}>{e.name}</option>)}</select>
        <label htmlFor="opening-country">Land der Vorperioden</label><select id="opening-country" value={form.country} onChange={e => change('country', e.target.value)}><option value="CH">Schweiz · CHF</option><option value="DE">Deutschland · EUR</option></select>
        <label htmlFor="opening-start">Erste neue Lohnperiode ab</label><input id="opening-start" type="date" required min="2026-02-01" max="2026-12-01" value={form.payrollStart} onChange={e => change('payrollStart', e.target.value)} />
        <label htmlFor="opening-source">Grundlage der Eröffnungswerte</label><input id="opening-source" required maxLength={250} value={form.source} onChange={e => change('source', e.target.value)} />
        <label htmlFor="opening-mode">Eröffnungsvorgang</label><select id="opening-mode" value={form.mode} onChange={e => change('mode', e.target.value)}><option value="NEW">Erfassung vor der ersten Lohnfreigabe</option><option value="CORRECTION">Berichtigung einer bereits verwendeten Eröffnung</option></select>
        {form.mode === 'CORRECTION' && <>
          <p>Den ursprünglichen Lohnstart beibehalten und die vollständigen korrigierten Vorperioden erfassen. Der Lauf umfasst alle freigegebenen neuen Lohnmonate des Jahres.</p>
          <label htmlFor="opening-correction-reason">Grund der Eröffnungsberichtigung</label><input id="opening-correction-reason" required maxLength={500} value={form.correctionReason} onChange={e => change('correctionReason', e.target.value)} />
          <label htmlFor="opening-correction-date">Verrechnungsdatum der Eröffnungsberichtigung</label><input id="opening-correction-date" type="date" required value={form.correctionDate} onChange={e => change('correctionDate', e.target.value)} />
          {form.correctionDate && form.correctionDate.slice(0, 4) !== '2026' && <>
            <p>Im Folgejahr sind nur Steuer-/SV-Berichtigungen auf unverändertem bereits bezahltem Brutto mit vollständigem Jahres-/Austrittsabschluss zulässig. CH-Frist: 31. März; deutscher Jahresausgleich: letzte Februar-Abrechnung.</p>
            <label><input type="checkbox" required checked={form.originalPaid} onChange={e => change('originalPaid', e.target.checked)} />Die Bruttolöhne waren bereits in den jeweiligen Originalperioden bezahlt; die Grundlage belegt die Jahreszuordnung.</label>
            {form.country === 'DE' && <>
              <label><input type="checkbox" required checked={form.certificateUnsent} onChange={e => change('certificateUnsent', e.target.checked)} />Die deutsche Lohnsteuerbescheinigung wurde noch nicht übermittelt/ausgeschrieben.</label>
              <label><input type="checkbox" required checked={form.annualPermitted} onChange={e => change('annualPermitted', e.target.checked)} />Der deutsche Jahresausgleich ist zulässig: keine Unterbrechung, kein Widerspruch, keine ausschliessenden Lohnersatzleistungen oder steuerfreien Auslandslöhne.</label>
            </>}
          </>}
        </>}
        {periods.map((p, index) => <fieldset key={index}><legend>Vorperiode {index + 1}</legend>
          {['start', 'end'].map(key => <div key={key}><label htmlFor={`opening-${index}-${key}`}>{key === 'start' ? 'Beginn' : 'Ende'} Vorperiode {index + 1}</label><input id={`opening-${index}-${key}`} type="date" required min="2026-01-01" max="2026-12-31" value={p[key]} onChange={e => periodChange(index, key, e.target.value)} /></div>)}
          {[...common, ...(form.country === 'CH' ? swiss : german)].map(([key, label]) => <div key={key}><label htmlFor={`opening-${index}-${key}`}>{label} · Vorperiode {index + 1}</label><input id={`opening-${index}-${key}`} type="number" required min={['withholding', 'solidarityTax', 'churchTax'].includes(key) ? undefined : '0'} step="0.01" value={p[key]} onChange={e => periodChange(index, key, e.target.value)} /></div>)}
          <label htmlFor={`opening-${index}-days`}>Versicherungstage · Vorperiode {index + 1}</label><input id={`opening-${index}-days`} type="number" required min="1" max="30" step="1" value={p.insuranceDays} onChange={e => periodChange(index, 'insuranceDays', e.target.value)} />
          {form.country === 'CH' && <>
            <label htmlFor={`opening-${index}-other`}>Weitere aperiodische Einkünfte bei bekanntem Gesamteinkommen · Vorperiode {index + 1}</label><input id={`opening-${index}-other`} type="number" min="0" step="0.01" value={p.swissOtherAperiodicGross} onChange={e => periodChange(index, 'swissOtherAperiodicGross', e.target.value)} />
            <label htmlFor={`opening-${index}-pool`}>KTG-Abrechnungskreis, falls getrennte Police · Vorperiode {index + 1}</label><input id={`opening-${index}-pool`} maxLength={100} value={p.ktgPoolKey} onChange={e => periodChange(index, 'ktgPoolKey', e.target.value)} />
            {p.ktgPoolKey && ['ktgPoolFrom', 'ktgPoolThrough'].map(key => <div key={key}><label htmlFor={`opening-${index}-${key}`}>{key === 'ktgPoolFrom' ? 'Vertragsbeginn' : 'Vertragsende'} KTG · Vorperiode {index + 1}</label><input id={`opening-${index}-${key}`} type="date" required value={p[key]} onChange={e => periodChange(index, key, e.target.value)} /></div>)}
            {p.taxAllocations.length > 0 && <p>Den gesamten übernommenen Steuerabzug aufteilen, wenn er auch Korrekturen früherer Kantonsabschnitte enthält. Abschnittsbeginn ist der erste steuerpflichtige Monatstag im betreffenden Kanton.</p>}
            {p.taxAllocations.map((allocation, a) => <fieldset key={a}><legend>Steuerzuordnung {a + 1} · Vorperiode {index + 1}</legend>
              {['canton', 'from', 'amount'].map(key => <div key={key}><label htmlFor={`opening-${index}-allocation-${a}-${key}`}>{key === 'canton' ? 'Kanton' : key === 'from' ? 'Beginn des Kantonsabschnitts' : 'Zugeordneter Steuerabzug'} · Zuordnung {a + 1} / Vorperiode {index + 1}</label><input id={`opening-${index}-allocation-${a}-${key}`} type={key === 'from' ? 'date' : key === 'amount' ? 'number' : 'text'} step={key === 'amount' ? '0.01' : undefined} required value={allocation[key]} onChange={e => periodChange(index, 'taxAllocations', p.taxAllocations.map((item, position) => position === a ? { ...item, [key]: key === 'canton' ? e.target.value.toUpperCase() : e.target.value } : item))} /></div>)}
              <button type="button" onClick={() => periodChange(index, 'taxAllocations', p.taxAllocations.filter((_, position) => position !== a))}>Steuerzuordnung {a + 1} entfernen · Vorperiode {index + 1}</button>
            </fieldset>)}
            <button type="button" onClick={() => periodChange(index, 'taxAllocations', [...p.taxAllocations, { canton: '', from: '', amount: '' }])}>Steuerabzug auf Kantonsabschnitte aufteilen · Vorperiode {index + 1}</button>
          </>}
        </fieldset>)}
        <button type="button" disabled={periods.length >= 12} onClick={addMonth}>Weitere Vorperiode hinzufügen</button>
        {periods.length > 1 && <button type="button" onClick={() => setPeriods(previous => previous.slice(0, -1))}>Letzte Vorperiode entfernen</button>}
        <label><input type="checkbox" required checked={form.confirmed} onChange={e => change('confirmed', e.target.checked)} />Die Vorperioden sind vollständig und anhand der angegebenen Unterlagen bestätigt; Nullwerte bedeuten tatsächlich keine Zahlung/Basis.</label>
        {error && <p role="alert">{error}</p>}{message && <p role="status">{message}</p>}
        <button type="submit" className="primary-btn">{busy ? 'Wird gespeichert …' : form.mode === 'NEW' ? 'Eröffnungswerte als neue Version speichern' : 'Eröffnungsberichtigung mit Korrekturlauf erstellen'}</button>
      </fieldset></form>
      {message && form.mode === 'CORRECTION' && <p><a href="/admin/payslips">Korrekturlauf bei den Lohnabrechnungen prüfen</a></p>}
      <h5>Gespeicherte Eröffnungen</h5>{versions.length === 0 ? <p>Noch keine Eröffnungswerte hinterlegt.</p> : <ul>{versions.map(v => <li key={v.id}>#{v.id} · {employees.find(e => e.id === v.userId)?.name || `Person #${v.userId}`} · neuer Lohnstart {v.payrollStart} · {v.source} · {v.discarded ? 'Korrekturlauf verworfen' : v.active === false ? 'Wartet auf vollständige Korrekturfreigabe' : 'Bestätigte Eröffnungsversion'}</li>)}</ul>}
    </>}
  </section>;
}
