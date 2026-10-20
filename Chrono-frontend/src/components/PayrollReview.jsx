import { useEffect, useState } from 'react';
import api from '../utils/api';

const minutes = value => Number.isFinite(value)
  ? `${value < 0 ? '−' : ''}${Math.floor(Math.abs(value) / 60)}:${String(Math.abs(value) % 60).padStart(2, '0')}` : '–';

export default function PayrollReview({ userId, start, end, t }) {
  const key = `${userId}:${start}:${end}`;
  const valid = Boolean(userId && start && end && start <= end);
  const [result, setResult] = useState(null);
  useEffect(() => {
    if (!valid) return undefined;
    let active = true;
    api.get('/api/payslips/admin/review', { params: { userId, start, end } })
      .then(({ data }) => {
        if (!Array.isArray(data?.issues) || !Array.isArray(data?.days)) throw new Error('Invalid review');
        if (active) setResult({ key, data });
      })
      .catch(() => { if (active) setResult({ key, error: true }); });
    return () => { active = false; };
  }, [key, valid, userId, start, end]);

  if (!valid) return <p role="alert">{t('payrollReview.choosePeriod', 'Bitte Mitarbeitende und einen gültigen Zeitraum auswählen.')}</p>;
  if (result?.key !== key) return <p role="status">{t('payrollReview.loading', 'Zeitdaten und Abrechnungsgrundlagen werden geprüft …')}</p>;
  if (result.error) return <p role="alert">{t('payrollReview.failed', 'Die Prüfung konnte nicht geladen werden. Es wird kein geschätzter Nettolohn angezeigt.')}</p>;
  const { data } = result;
  return <section className="run-preview payroll-review" aria-label={t('payrollReview.title', 'Abrechnungsprüfung')}>
    <h3>{t('payrollReview.title', 'Abrechnungsprüfung')}</h3>
    <p>{t('payrollReview.readOnly', 'Diese Prüfung verändert keine Zeiten, Salden oder Abrechnungen.')}</p>
    <dl>
      <div><dt>{t('payrollReview.worked', 'Erfasste Arbeitszeit')}</dt><dd>{minutes(data.workedMinutes)}</dd></div>
      <div><dt>{t('payrollReview.expected', 'Sollzeit laut Zeitübersicht')}</dt><dd>{minutes(data.expectedMinutes)}</dd></div>
      <div><dt>{t('payrollReview.difference', 'Differenz im Zeitraum')}</dt><dd>{minutes(data.differenceMinutes)}</dd></div>
      <div><dt>{t('payrollReview.net', 'Verifizierter Nettolohn')}</dt><dd>{t('payrollReview.notVerified', 'Noch nicht verfügbar')}</dd></div>
    </dl>
    <p className="payroll-review-note">{t('payrollReview.netHint', 'Zeitprüfung und vollständige Stammdaten ersetzen keine validierte Steuer- und Beitragsberechnung. Ein pauschaler Netto-Schätzwert wird deshalb nicht angezeigt.')}</p>
    <ul className="payroll-review-issues">
      {data.issues.map((issue, index) => <li key={`${issue.code}:${issue.date}:${issue.payslipId}:${index}`} className={issue.severity === 'BLOCKER' ? 'review-blocker' : 'review-warning'}>
        <strong>{issue.severity === 'BLOCKER' ? t('payrollReview.resolve', 'Zu klären') : t('payrollReview.check', 'Prüfhinweis')}: </strong>
        {issue.date && <time dateTime={issue.date}>{issue.date} · </time>}{issue.message}
        {issue.payslipId && <span> ({t('payrollReview.payslip', 'Abrechnung')} #{issue.payslipId})</span>}
      </li>)}
    </ul>
    <details>
      <summary>{t('payrollReview.explain', 'Tageswerte nachvollziehen')}</summary>
      <div className="payroll-review-table"><table><thead><tr>
        <th>{t('date', 'Datum')}</th><th>{t('payrollReview.worked', 'Erfasste Arbeitszeit')}</th><th>{t('payrollReview.expected', 'Sollzeit laut Zeitübersicht')}</th><th>{t('payrollReview.difference', 'Differenz im Zeitraum')}</th>
      </tr></thead><tbody>{data.days.map(day => <tr key={day.date}>
        <td>{day.date}</td><td>{minutes(day.workedMinutes)}</td><td>{minutes(day.expectedMinutes)}</td><td>{minutes(day.differenceMinutes)}</td>
      </tr>)}</tbody></table></div>
    </details>
  </section>;
}
