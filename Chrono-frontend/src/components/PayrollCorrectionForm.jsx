import { useState } from 'react';
import api from '../utils/api';

export default function PayrollCorrectionForm({ original, onClose, onCreated }) {
  const [reason, setReason] = useState('');
  const [payoutDate, setPayoutDate] = useState('');
  const [minutes, setMinutes] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [assignment, setAssignment] = useState(false);
  const [assignmentSource, setAssignmentSource] = useState('');
  const [certificateUnsent, setCertificateUnsent] = useState(false);
  const [annualPermitted, setAnnualPermitted] = useState(false);
  const crossYear = payoutDate && payoutDate.slice(0, 4) !== original.periodStart?.slice(0, 4);
  const german = original.currency === 'EUR';
  async function submit(event) {
    event.preventDefault(); if (busy) return;
    setBusy(true); setError('');
    try {
      const { data } = await api.post(`/api/payslips/${original.id}/corrections`, {
        reason: reason.trim(), payoutDate, replacementOvertimeHours: minutes === '' ? null : Number(minutes) / 60,
        ...(assignment ? { yearAssignment: { source: assignmentSource.trim(), originalGrossAlreadyPaid: true,
          germanCertificateNotTransmitted: certificateUnsent, germanAnnualAdjustmentPermitted: annualPermitted } } : {}),
      });
      await onCreated(data);
    } catch (failure) { setError(failure.response?.data?.message || failure.response?.data?.detail || 'Rückrechnung konnte nicht erstellt werden.'); }
    finally { setBusy(false); }
  }
  return <div className="drawer-layer"><aside className="payslip-drawer" role="dialog" aria-modal="true" aria-labelledby="correction-title">
    <h2 id="correction-title">Rückrechnung zu Original #{original.id}</h2>
    <p>{original.periodStart} – {original.periodEnd}. Es entstehen separate Differenzbelege für diesen und alle bereits freigegebenen Folgemonate desselben Jahres. Originale und frühere Korrekturen bleiben erhalten.</p>
    <p>Die neuen datierten Lohn- und Versicherungsregeln sowie korrigierte Zeitdaten müssen vorab gespeichert sein. Der gesamte Lauf wird anschliessend gemeinsam geprüft und freigegeben.</p>
    <form onSubmit={submit}><fieldset disabled={busy}>
      <label htmlFor="correction-reason">Korrekturgrund</label>
      <textarea id="correction-reason" required maxLength={500} value={reason} onChange={e => setReason(e.target.value)} />
      <label htmlFor="correction-date">Auszahlungs-/Verrechnungsdatum der Differenz</label>
      <input id="correction-date" required type="date" value={payoutDate} onChange={e => setPayoutDate(e.target.value)} />
      <label htmlFor="correction-minutes">Neue gesamte Überstundenauszahlung des Originalmonats in Minuten (leer = unverändert, 0 = aufheben)</label>
      <input id="correction-minutes" type="number" min="0" step="1" value={minutes} onChange={e => setMinutes(e.target.value)} />
      <label><input type="checkbox" required={Boolean(crossYear)} checked={assignment} onChange={e => setAssignment(e.target.checked)} />Berichtigung bereits in den jeweiligen Originalperioden des Originaljahres bezahlter Bruttolöhne für alle Belege dieses Laufs; keine neue rückwirkende Lohngewährung.</label>
      {assignment && <>
        <label htmlFor="correction-assignment-source">Nachweis der ursprünglichen Lohnzahlung / Jahreszuordnung</label>
        <input id="correction-assignment-source" required maxLength={250} value={assignmentSource} onChange={e => setAssignmentSource(e.target.value)} />
        {crossYear && <p>Unterstützt sind Steuer-/SV-Berichtigungen auf unverändertem Brutto und mit vollständigem Jahres-/Austrittsabschluss. Schweizer Quellensteuerberichtigungen sind bis 31. März des Folgejahres zu melden. Neue Lohnnachzahlungen benötigen die Regeln ihres tatsächlichen Zuflussjahres.</p>}
        {crossYear && german && <>
          <label><input type="checkbox" required checked={certificateUnsent} onChange={e => setCertificateUnsent(e.target.checked)} />Die deutsche Lohnsteuerbescheinigung wurde noch nicht übermittelt/ausgeschrieben.</label>
          <label><input type="checkbox" required checked={annualPermitted} onChange={e => setAnnualPermitted(e.target.checked)} />Der Jahresausgleich ist zulässig: ganzjähriges Arbeitsverhältnis ohne Unterbrechung, kein Arbeitnehmerwiderspruch, keine ausschliessenden Lohnersatzleistungen oder steuerfreien Auslandslöhne. Die weiteren Steuermerkmale werden bei der Berechnung geprüft.</label>
          <p>Der gesamte Lauf benötigt die Dezember-Abrechnung und muss spätestens mit der letzten Februar-Abrechnung abgeschlossen werden.</p>
        </>}
      </>}
      {error && <p role="alert">{error}</p>}
      <button type="submit" className="primary-btn">{busy ? 'Wird berechnet …' : 'Korrekturlauf als Entwurf erstellen'}</button>
      <button type="button" onClick={onClose}>Abbrechen</button>
    </fieldset></form>
  </aside></div>;
}
