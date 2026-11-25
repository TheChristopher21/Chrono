import { currencyStep } from './pmsMoney.js';
import { useEffect, useRef, useState } from 'react';
import api from '../../utils/api.js';

export default function PmsFinancialActionDialog({ payment, invoice, propertyId, cashShifts = [], currency, busy, canSubmit = true, onClose, onSubmit }) {
    const dialog = useRef(null);
    const [reason, setReason] = useState('');
    const [amount, setAmount] = useState(String(Math.abs(Number(payment?.amount || 0))));
    const [mode, setMode] = useState('REISSUE');
    const [cashShiftId, setCashShiftId] = useState('');
    const [requestId] = useState(() => crypto.randomUUID());
    const [refundIntent, setRefundIntent] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');
    const [approvalPending, setApprovalPending] = useState(false);
    const submittingRef = useRef(false);
    const locked = busy || submitting || Boolean(refundIntent);
    const closeLocked = busy || submitting || Boolean(refundIntent) && !approvalPending;
    useEffect(() => {
        const previous = document.activeElement;
        dialog.current?.querySelector('input,select,button')?.focus();
        return () => previous?.isConnected && previous.focus();
    }, []);
    const keys = (event) => {
        if (event.key === 'Escape' && !closeLocked) onClose();
        if (event.key !== 'Tab') return;
        const focusable = [...dialog.current.querySelectorAll('button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled])')];
        const first = focusable[0]; const last = focusable.at(-1);
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
        if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    };
    return <div className="pms-financial-dialog-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget && !closeLocked) onClose(); }}>
        <section ref={dialog} className="pms-financial-dialog" role="dialog" aria-modal="true" aria-labelledby="pms-financial-action-title" onKeyDown={keys}>
            <div className="pms-work-card-heading"><div><span className="pms-eyebrow">{invoice ? invoice.invoiceNumber : 'Zahlungskorrektur'}</span><h3 id="pms-financial-action-title">{invoice ? 'Rechnung korrigieren' : 'Betrag zurückerstatten'}</h3></div><button type="button" aria-label="Schliessen" disabled={closeLocked} onClick={onClose}>×</button></div>
            {error && <p role="alert" className="pms-inline-message is-error">{error}</p>}
            {approvalPending ? <p role="status" className="pms-inline-note">Die Erstattung benötigt eine zweite Person. Der Freigabeantrag ist im Rechnungsbereich unter „Freigaben für Erstattungen“ zu prüfen. Der Dialog kann geschlossen werden.</p> : refundIntent && <p role="status" className="pms-inline-note">Der Ausgang ist noch nicht bestätigt. Betrag, Begründung, Kasse und Vorgangs-ID bleiben gesperrt. Prüfe dieselbe Erstattung erneut; der Dialog schließt, sobald das Ergebnis oder der vorgemerkte Auftrag geladen ist.</p>}
            <form className="pms-form-grid" onSubmit={async (event) => {
                event.preventDefault();
                if (!canSubmit || busy || submittingRef.current) return;
                const priorIntent = refundIntent;
                const payload = invoice ? { reason, mode } : refundIntent || { amount: Number(amount), reason, requestId, cashShiftId: cashShiftId ? Number(cashShiftId) : null };
                if (!invoice) setRefundIntent(payload);
                submittingRef.current = true; setSubmitting(true); setError('');
                try { if (await onSubmit(payload)) onClose(); }
                catch (failure) {
                    if (!invoice && propertyId && failure?.response?.status === 428) {
                        setApprovalPending(true);
                        try { await api.post(`/api/pms/properties/${propertyId}/approvals/refunds`, { paymentId: payment.id, refund: payload }); }
                        catch (approvalFailure) { setError(approvalFailure.response?.data?.detail || 'Freigabeantrag noch nicht bestätigt. Dieselbe Anfrage erneut prüfen.'); }
                        return;
                    }
                    const firstAttemptRejected = [400,401,403,404,422].includes(failure?.response?.status)
                        || failure?.response?.status === 409 && failure.pmsRefundIntentAbsent === true;
                    if (!priorIntent && firstAttemptRejected) setRefundIntent(null);
                    setError(failure?.response?.data?.detail || 'Erstattungsstatus noch unklar. Dieselbe Anfrage erneut prüfen.');
                } finally { submittingRef.current = false; setSubmitting(false); }
            }}>
                {invoice ? <label className="is-wide">Art der Korrektur<select disabled={busy || submitting || !canSubmit} value={mode} onChange={(event) => setMode(event.target.value)}><option value="REISSUE">Beleg korrigieren und neu ausstellen</option><option value="CANCEL_SERVICES">Leistungen gutschreiben</option></select></label> : <label className="is-wide">Erstattungsbetrag ({currency})<input type="number" min={currencyStep(currency)} step={currencyStep(currency)} max={Math.abs(Number(payment.amount))} value={amount} disabled={locked || !canSubmit} onChange={(event) => setAmount(event.target.value)} required /></label>}
                <p className="pms-inline-note is-wide">{invoice ? mode === 'REISSUE' ? 'Der bisherige Beleg wird gutgeschrieben. Die Leistungen bleiben bestehen und können mit den korrigierten Rechnungsdaten neu ausgestellt werden.' : 'Die Leistungen werden am offenen Betriebstag gegengebucht. Eine erforderliche Rückzahlung wird anschließend separat im Gastkonto erfasst.' : 'Eine Teilrückerstattung ist möglich. Der gespeicherte Vorgang wird bei einer Wiederholung derselben Anfrage erneut verwendet.'}</p>
                {payment?.method === 'CASH' && <label className="is-wide">Auszahlende Kasse<select disabled={locked || !canSubmit} value={cashShiftId} onChange={(event) => setCashShiftId(event.target.value)} required><option value="">Kasse auswählen</option>{cashShifts.filter((shift) => shift.status === 'OPEN').map((shift) => <option key={shift.id} value={shift.id}>{shift.registerCode} · {shift.openedBy}</option>)}</select></label>}
                <label className="is-wide">Begründung<textarea disabled={locked || !canSubmit} value={reason} onChange={(event) => setReason(event.target.value)} minLength={3} maxLength={500} required placeholder="Anlass der Korrektur nachvollziehbar beschreiben" /></label>
                <div className="pms-form-actions is-wide"><button type="button" disabled={closeLocked} onClick={onClose}>{approvalPending ? 'Schließen' : 'Abbrechen'}</button><button type="submit" className="is-primary" disabled={busy || submitting || !canSubmit}>{busy || submitting ? 'Wird verarbeitet …' : invoice ? 'Korrektur buchen' : approvalPending ? 'Freigabe erneut prüfen' : refundIntent ? 'Bestehende Erstattung erneut prüfen' : 'Erstattung ausführen'}</button></div>
            </form>
        </section>
    </div>;
}
