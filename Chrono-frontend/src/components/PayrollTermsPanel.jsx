import { useEffect, useState } from 'react';
import api from '../utils/api';

const endpoint = '/api/admin/company/payroll-configuration/terms';
const empty = () => ({ userId: '', effectiveFrom: '', source: '', country: 'CH', payType: 'SALARY',
  amount: '', weeklyHours: '', overtimeHourlyRate: '', overtimePremiumRate: '', weeklyLimit: '',
  taxMode: '', canton: '', tariffCode: '', monthlySingleEmployment: false, birthDate: '', entryDate: '',
  taxClass: '', childAllowance: '', churchRate: '', healthAdditionalRate: '', parent: '', childrenUnder25: '', saxony: '',
  taxAllowance: '', taxAddition: '', taxFactor: '', u1Rate: '', u2Rate: '', insolvencyRate: '',
  exitDate: '', salaryProration: '', pensionProration: '', thirteenthFrequency: '', germanPreviousEmploymentGross: '',
  multipleMethod: 'NONE', ownPercent: '', totalPercent: '', otherPeriodicMonthlyGross: '', mainGermanEmployment: '',
  hourlyMode: 'NONE', vacationRate: '', holidayRate: '', allowanceBase: '', cashVacationPermitted: false, allowancesInThirteenth: '', paidAbsencesInThirteenth: '' });
const percents = new Set(['overtimePremiumRate', 'churchRate', 'healthAdditionalRate', 'u1Rate', 'u2Rate', 'insolvencyRate']);
const integers = new Set(['weeklyLimit', 'taxClass', 'childrenUnder25']);
const decimals = new Set(['amount', 'weeklyHours', 'overtimeHourlyRate', 'childAllowance', 'taxAllowance', 'taxAddition', 'taxFactor']);
const taxLabels = { CH_ORDINARY: 'Ordentliche Besteuerung', CH_SOURCE: 'Quellensteuer', DE_STANDARD: 'Deutsche Lohnsteuer' };

export default function PayrollTermsPanel({ employees }) {
  const [form, setForm] = useState(empty);
  const [versions, setVersions] = useState(null);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [saving, setSaving] = useState(false);
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    api.get(endpoint).then(({ data }) => {
      if (!Array.isArray(data)) throw new Error('Ungültige Antwort');
      if (active) { setVersions(data); setError(''); }
    }).catch(() => { if (active) setError('Lohnregeln konnten nicht geladen werden.'); });
    return () => { active = false; };
  }, [retry]);
  const change = (key, value) => setForm(previous => ({ ...previous, [key]: value, ...(key === 'country' ? { multipleMethod: 'NONE', hourlyMode: 'NONE' } : {}) }));
  const input = (key, label, type = 'number', step = '0.01') => <div key={key}>
    <label htmlFor={`terms-${key}`}>{label}</label>
    <input id={`terms-${key}`} type={type} required value={form[key]} min={type === 'number' ? '0' : undefined}
      step={type === 'number' ? step : undefined} onChange={event => change(key, event.target.value)} />
  </div>;
  const select = (key, label, options) => <div key={key}>
    <label htmlFor={`terms-${key}`}>{label}</label>
    <select id={`terms-${key}`} required value={form[key]} onChange={event => change(key, event.target.value)}>
      <option value="">Bitte auswählen</option>{options.map(([value, text]) => <option key={value} value={value}>{text}</option>)}
    </select>
  </div>;
  async function save(event) {
    event.preventDefault(); if (saving || !versions) return;
    const { userId, effectiveFrom, source, exitDate, salaryProration, pensionProration, thirteenthFrequency, germanPreviousEmploymentGross,
      multipleMethod, ownPercent, totalPercent, otherPeriodicMonthlyGross, mainGermanEmployment,
      hourlyMode, vacationRate, holidayRate, allowanceBase, cashVacationPermitted, allowancesInThirteenth, paidAbsencesInThirteenth, ...values } = form;
    const terms = Object.fromEntries(Object.entries(values).map(([key, value]) => [key,
      value === '' ? null : percents.has(key) ? (Number(value) / 100).toFixed(6)
        : integers.has(key) ? Number(value) : decimals.has(key) ? value
          : ['parent', 'saxony'].includes(key) ? value === 'true' : value]));
    terms.contract = { exitDate: exitDate || null, salaryProration, pensionProration, thirteenthFrequency,
      germanPreviousEmploymentGross: germanPreviousEmploymentGross === '' ? null : germanPreviousEmploymentGross };
    if (multipleMethod !== 'NONE') {
      terms.monthlySingleEmployment = false;
      terms.contract.multipleEmployment = { method: form.country === 'DE' ? 'DE_OTHER_EMPLOYER' : multipleMethod,
        ownPercent: ownPercent || null, totalPercent: totalPercent || null, otherPeriodicMonthlyGross: otherPeriodicMonthlyGross || null,
        otherAperiodicGross: null, mainGermanEmployment: mainGermanEmployment === '' ? null : mainGermanEmployment === 'true' };
    }
    if (form.payType === 'HOURLY' && hourlyMode !== 'NONE') terms.contract.hourlyAllowance = {
      vacationMode: hourlyMode, vacationRate: (Number(vacationRate) / 100).toFixed(6), holidayRate: (Number(holidayRate) / 100).toFixed(6), allowanceBase,
      cashVacationPermitted, allowancesInThirteenth: allowancesInThirteenth === 'true', paidAbsencesInThirteenth: paidAbsencesInThirteenth === 'true',
    };
    setSaving(true); setError(''); setMessage('');
    try {
      const { data } = await api.post(endpoint, { userId: Number(userId), effectiveFrom, source: source.trim(), terms });
      setVersions(previous => [data, ...previous]); setForm(empty());
      setMessage('Lohnregeln gespeichert. Bestehende Abrechnungen bleiben unverändert.');
    } catch (failure) { setError(failure.response?.data?.message || failure.response?.data?.detail || 'Lohnregeln konnten nicht gespeichert werden.'); }
    finally { setSaving(false); }
  }
  return <section aria-labelledby="payroll-terms-title">
    <h4 id="payroll-terms-title">Lohn, Steuerpflicht & Überstunden</h4>
    <p>Pro Person mit Gültigkeitsdatum und Vertragsgrundlage erfassen. Änderungen erzeugen eine neue Version. Abrechnung 2026 mit regelmässiger monatlicher Lohnzahlung und regulärer Sozialversicherung vor dem Referenzalter. Weitere Erwerbseinkünfte unten ausdrücklich erfassen. Ein-/Austrittsmonate werden nach den erfassten Vertragsregeln aufgeteilt.</p>
    <p>Stundenlohn: Alle erfassten Arbeitsstunden werden einmal bezahlt; Wochen-Überstunden erhalten den vereinbarten Zuschlag. Monatslohn: Überstunden bleiben auf dem Zeitkonto und werden nur auf ausdrücklichen Antrag ausbezahlt. Der Auszahlungsansatz muss dem Vertrag entsprechen.</p>
    {!versions ? <>{error && <p role="alert">{error}</p>}<button type="button" onClick={() => setRetry(v => v + 1)}>Lohnregeln erneut laden</button></> : <>
      <form onSubmit={save} aria-label="Lohn- und Steuerregeln">
        <fieldset disabled={saving}>
          <legend>Neue persönliche Lohnregel</legend>
          {select('userId', 'Mitarbeitende für Lohnregeln', employees.map(e => [String(e.id), e.name]))}
          {input('effectiveFrom', 'Lohnregeln gültig ab', 'date')}
          <label htmlFor="terms-source">Vertrag / Steuergrundlage</label>
          <input id="terms-source" required maxLength={250} value={form.source} onChange={event => change('source', event.target.value)} />
          {select('country', 'Abrechnungsland', [['CH', 'Schweiz · CHF'], ['DE', 'Deutschland · EUR']])}
          {select('payType', 'Lohnart', [['SALARY', 'Monatslohn'], ['HOURLY', 'Stundenlohn']])}
          {input('amount', form.payType === 'HOURLY' ? 'Stundenansatz' : 'Vertraglicher Monatslohn (für das tatsächliche Pensum)')}
          {input('weeklyHours', 'Vertragliche Wochenstunden', 'number', '0.0001')}
          <p>Im Prozent-Arbeitszeitmodell entsprechen 100 % einer 42,5-Stunden-Woche. Die Vertragsstunden ohne Rundung erfassen, zum Beispiel 34 bei 80 % oder 14,025 bei 33 %.</p>
          {input('overtimeHourlyRate', 'Grundansatz pro ausbezahlter Überstunde')}
          {input('overtimePremiumRate', 'Vereinbarter Überstundenzuschlag (%)')}
          {input('birthDate', 'Geburtsdatum für Beiträge', 'date')}
          {input('entryDate', 'Eintrittsdatum für die Lohnabrechnung', 'date')}
          <label htmlFor="terms-exitDate">Austrittsdatum (falls bekannt)</label>
          <input id="terms-exitDate" type="date" value={form.exitDate} onChange={event => change('exitDate', event.target.value)} />
          {select('salaryProration', 'Vertragliche Lohnkürzung im Teilmonat', [['CALENDAR_DAYS', 'Beschäftigte Kalendertage / Kalendertage des Monats'], ['THIRTY_DAYS', '30-Tage-Methode gemäss Vertrag']])}
          {select('pensionProration', 'BVG im Teilmonat gemäss Pensionskasse', [['FULL', 'Voller erfasster Monatsbeitrag (auch bei 0)'], ['THIRTY_DAYS', 'Anteilig nach 30-Tage-Methode'], ['CALENDAR_DAYS', 'Anteilig nach Kalendertagen']])}
          {select('thirteenthFrequency', '13. Monatslohn: Auszahlung gemäss Vertrag', [['NONE', 'Kein Anspruch'], ['MONTHLY', 'Monatlich'], ['QUARTERLY', 'Quartalsende'], ['SEMIANNUAL', 'Juni / Dezember'], ['ANNUAL', 'Dezember']])}
          <p>Der Anspruch wird als 1/12 des Grundlohns aufgebaut; bereits bezahlte Anteile werden abgezogen. Bei Austritt wird der Rest fällig. Für abweichende Vertragsgrundlagen diese Regel nicht verwenden.</p>
          {form.country === 'CH' ? <>
            {select('weeklyLimit', 'Gesetzliche Wochenhöchstzeit', [['45', '45 Stunden'], ['50', '50 Stunden']])}
            {select('taxMode', 'Schweizer Steuerpflicht', [['CH_ORDINARY', 'Ordentliche Besteuerung · kein Quellensteuerabzug'], ['CH_SOURCE', 'Quellensteuerpflichtig']])}
            {form.taxMode === 'CH_SOURCE' && <>
              {input('canton', 'Quellensteuerkanton (z. B. ZH)', 'text')}
              {input('tariffCode', 'Persönlicher Tarif (z. B. A0N)', 'text')}
              <p>Tarif gemäss persönlichen Verhältnissen oder kantonaler Verfügung. FR, GE, TI, VD und VS werden mit Jahresausgleich berechnet. Dafür müssen alle bisherigen Monate seit Jahresbeginn/Eintritt freigegeben und nachvollziehbar sein.</p>
            </>}
          </> : <>
            {select('taxMode', 'Deutscher Versicherungsstatus', [['DE_STANDARD', 'Regulär gesetzlich versichert · kein Mini-/Midijob']])}
            {select('taxClass', 'ELStAM-Steuerklasse', [1,2,3,4,5,6].map(v => [String(v), String(v)]))}
            {input('childAllowance', 'ELStAM-Kinderfreibeträge', 'number', '0.5')}
            {select('churchRate', 'Kirchensteuer (%)', [['0', 'Keine'], ['8', '8 %'], ['9', '9 %']])}
            {input('healthAdditionalRate', 'Voller Zusatzbeitrag der Krankenkasse (%)')}
            {select('parent', 'Elterneigenschaft in der Pflegeversicherung', [['true', 'Ja'], ['false', 'Nein']])}
            {input('childrenUnder25', 'Berücksichtigungsfähige Kinder unter 25', 'number', '1')}
            {select('saxony', 'Pflegeversicherung nach Sachsen-Regelung', [['true', 'Ja'], ['false', 'Nein']])}
            {input('taxAllowance', 'Monatlicher ELStAM-Freibetrag (0 falls keiner)')}
            {input('taxAddition', 'Monatlicher ELStAM-Hinzurechnungsbetrag (0 falls keiner)')}
            {input('taxFactor', 'ELStAM-Faktor (1 ohne Faktorverfahren)', 'number', '0.001')}
            {input('u1Rate', 'U1-Umlage (%) · 0 bei Befreiung')}
            {input('u2Rate', 'U2-Umlage (%)')}
            {input('insolvencyRate', 'Insolvenzgeldumlage (%) · 0 bei Befreiung')}
            {form.thirteenthFrequency && !['NONE', 'MONTHLY'].includes(form.thirteenthFrequency) && input('germanPreviousEmploymentGross', 'Brutto früherer Arbeitgeber im Abrechnungsjahr (0 nur bei nachgewiesen keiner Vorbeschäftigung)')}
            {['NONE', 'MONTHLY'].includes(form.thirteenthFrequency) && <>
              <label htmlFor="terms-germanPreviousEmploymentGross">Brutto früherer Arbeitgeber im Abrechnungsjahr (0 nur bei nachgewiesen keiner Vorbeschäftigung)</label>
              <input id="terms-germanPreviousEmploymentGross" type="number" min="0" step="0.01" value={form.germanPreviousEmploymentGross} onChange={e => change('germanPreviousEmploymentGross', e.target.value)} />
              <p>Für spätere sonstige Bezüge/Nachzahlungen erforderlich. Leer bedeutet unbekannt; 0 nur ausdrücklich bei belegter fehlender Vorbeschäftigung.</p>
            </>}
          </>}
          {select('multipleMethod', 'Weitere Erwerbs- oder Ersatzeinkünfte', form.country === 'CH' ? [['NONE', 'Keine weiteren Einkünfte'], ['TOTAL_PERCENT', 'Bekanntes Gesamtpensum'], ['UNKNOWN_TOTAL_PERCENT', 'Weiteres Pensum nicht offengelegt · Umrechnung auf 100 %'], ['KNOWN_OTHER_INCOME', 'Bekanntes Gesamteinkommen']] : [['NONE', 'Ein Arbeitgeber'], ['DE_OTHER_EMPLOYER', 'Mehrfachbeschäftigung · reguläre Sozialversicherung']])}
          {form.multipleMethod !== 'NONE' && (form.country === 'DE' ? <>
            {select('mainGermanEmployment', 'Dieses deutsche Arbeitsverhältnis', [['true', 'Hauptarbeitsverhältnis laut ELStAM'], ['false', 'Nebenarbeitsverhältnis · Steuerklasse VI']])}
            <p>Die Entgelte für die anteiligen SV-Grenzen werden für jeden Monat in den zusätzlichen Monatsgrundlagen anhand der Einzugsstellenmeldung erfasst.</p>
          </> : form.multipleMethod === 'KNOWN_OTHER_INCOME' ? <>
            {input('otherPeriodicMonthlyGross', 'Bekanntes weiteres periodisches Einkommen als Monatswert inkl. 13. Monatslohn-Anteil', 'number', '0.0001')}
            <p>Zusätzliche aperiodische Einkünfte monatlich ausdrücklich erfassen, auch wenn sie 0 betragen. Bei Änderungen neue datierte Regeln anlegen.</p>
          </> : <>
            {input('ownPercent', 'Eigenes Pensum für die Satzbestimmung (%)', 'number', '0.0001')}
            {form.multipleMethod === 'TOTAL_PERCENT' && input('totalPercent', 'Offengelegtes Gesamtpensum (%)', 'number', '0.0001')}
          </>)}
          {form.payType === 'HOURLY' && <>
            {select('hourlyMode', 'Ferien-/Abwesenheitsbezahlung im Stundenlohn', form.country === 'CH' ? [['NONE', 'Noch keine zusätzlichen Entschädigungsregeln'], ['PAID_ABSENCE', 'Ferienlohn bei Bezug'], ['CASH_ALLOWANCE', 'Zulässige laufende Ferienabgeltung']] : [['NONE', 'Noch keine zusätzlichen Entschädigungsregeln'], ['PAID_ABSENCE', 'Bezahlung bei Urlaub/Feiertag/Krankheit']])}
            {form.hourlyMode !== 'NONE' && <>
              {input('vacationRate', 'Vertraglicher Ferienzuschlag (%) · 0 bei Bezahlung während Ferien')}
              {input('holidayRate', 'Vertraglicher Feiertagszuschlag (%) · 0 bei konkreter Feiertagsbezahlung')}
              {select('allowanceBase', 'Bemessungsgrundlage der laufenden Zuschläge', [['BASE_ONLY', 'Gearbeiteter Grundlohn'], ['BASE_AND_PREMIUM', 'Gearbeiteter Grundlohn plus Überstundenzuschlag']])}
              {select('allowancesInThirteenth', 'Laufende Zuschläge im Anspruch auf den 13. Monatslohn', [['true', 'Gemäss Vertrag einschliessen'], ['false', 'Gemäss Vertrag ausschliessen']])}
              {select('paidAbsencesInThirteenth', 'Bezahlte Abwesenheiten im Anspruch auf den 13. Monatslohn', [['true', 'Gemäss Vertrag einschliessen'], ['false', 'Gemäss Vertrag ausschliessen']])}
              {form.hourlyMode === 'CASH_ALLOWANCE' && <label><input type="checkbox" required checked={form.cashVacationPermitted} onChange={e => change('cashVacationPermitted', e.target.checked)} />Die laufende Ferienabgeltung ist für dieses unregelmässige Arbeitsverhältnis rechtlich zulässig und im Vertrag separat ausgewiesen.</label>}
              <p>Konkrete Ausfallminuten und belegte Durchschnittsansätze pro Lohnperiode in den zusätzlichen Monatsgrundlagen erfassen.</p>
            </>}
          </>}
          <label className="payroll-terms-confirm"><input type="checkbox" required checked={form.monthlySingleEmployment} onChange={event => change('monthlySingleEmployment', event.target.checked)} />Regelmässige monatliche Zahlung und reguläre Sozialversicherung; die Angaben zu weiteren Einkünften und Leistungen sind vollständig, weitere nicht erfasste Sonderversicherungsfälle liegen nicht vor.</label>
          <p>Rückwirkende Änderungen an neuen Lohnberechnungen werden über einen Korrekturlauf verarbeitet.</p>
          <button type="submit" className="primary-btn">{saving ? 'Wird gespeichert …' : 'Lohnregel als neue Version speichern'}</button>
        </fieldset>
      </form>
      {error && <p role="alert">{error}</p>}{message && <p role="status">{message}</p>}
      <h4>Historie der Lohnregeln</h4>
      {versions.length === 0 ? <p>Noch keine persönlichen Lohnregeln hinterlegt.</p> : <ul>{versions.map(v => {
        let terms; try { terms = JSON.parse(v.parameters); } catch { terms = {}; }
        return <li key={v.id}>#{v.id} · {employees.find(e => e.id === v.userId)?.name || `#${v.userId}`} · ab {v.effectiveFrom} · {terms.country} · {terms.payType === 'HOURLY' ? 'Stundenlohn' : 'Monatslohn'} {terms.amount} · {taxLabels[terms.taxMode] || 'Steuerpflicht offen'} · {v.source}</li>;
      })}</ul>}
    </>}
  </section>;
}
