import { useEffect, useState } from 'react';
import api from '../../utils/api.js';
import { formatPmsDate, formatPmsDateTime } from './pmsFormatting.js';
import { formatPmsMoney } from './pmsMoney.js';

export default function PmsReservationPolicyPanel({ reservationId, currencyCode }) {
    const [policy, setPolicy] = useState(null);
    const [error, setError] = useState('');
    useEffect(() => { let active = true; api.get(`/api/pms/reservations/${reservationId}/policy`).then(({ data }) => { if (active) setPolicy(data); }).catch((err) => { if (active) setError(err.response?.data?.detail || 'Buchungsbedingungen konnten nicht geladen werden.'); }); return () => { active = false; }; }, [reservationId]);
    if (error) return <p className="pms-inline-message is-error" role="alert">{error}</p>;
    if (!policy) return null;
    return <section className="pms-policy-summary"><h4>Vereinbarte Buchungsbedingungen</h4><div className="pms-day-checks"><div className={policy.depositOverdue ? 'needs-attention' : ''}><span>Noch fällige Anzahlung</span><strong>{formatPmsMoney(policy.depositOutstandingAmount, currencyCode)}</strong><small>{policy.depositDueDate ? `Fällig ${formatPmsDate(policy.depositDueDate)}` : 'Keine Anzahlung vereinbart'}</small></div><div><span>Stornogebühr bei Stornierung jetzt</span><strong>{formatPmsMoney(policy.cancellationFeeNow, currencyCode)}</strong></div><div><span>Gebühr bei Nichtanreise</span><strong>{formatPmsMoney(policy.noShowFee, currencyCode)}</strong></div></div>{policy.cancellationDeadline && <p className="pms-muted">Stornofrist: {formatPmsDateTime(policy.cancellationDeadline)} · vereinbarte Bedingungen vom {formatPmsDateTime(policy.snapshotAt)}</p>}{policy.financialCorrectionRequired && <p className="pms-inline-message is-error">Für diesen Aufenthalt sind bereits ausgestellte oder abgeschlossene Leistungen zu prüfen. Eine Belegkorrektur ist erforderlich.</p>}</section>;
}
