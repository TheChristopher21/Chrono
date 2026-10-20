import { useState } from 'react';
import api from '../utils/api';

export default function PayrollCorrectionForm({ original, onClose, onCreated }) {
  const [reason, setReason] = useState('');
  const [payoutDate, setPayoutDate] = useState('');
  const [minutes, setMinutes] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function submit(event) {
    event.preventDefault(); if (busy) return;
    setBusy(true); setError('');
    try {
      const { data } = await api.post(`/api/payslips/${original.id}/corrections`, {
        reason: reason.trim(), payoutDate, replacementOvertimeHours: minutes === '' ? null : Number(minutes) / 60,
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
      {error && <p role="alert">{error}</p>}
      <button type="submit" className="primary-btn">{busy ? 'Wird berechnet …' : 'Korrekturlauf als Entwurf erstellen'}</button>
      <button type="button" onClick={onClose}>Abbrechen</button>
    </fieldset></form>
  </aside></div>;
}
