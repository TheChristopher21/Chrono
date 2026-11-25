import { useEffect, useMemo, useRef, useState } from 'react';
import api from '../../utils/api';
import { workspaceDate } from './adminWorkspaceData';

export function vacationBalanceYears(startDate, endDate) {
    const start = workspaceDate(startDate), end = workspaceDate(endDate || startDate);
    if (!start || !end || start > end) return [];
    const first = Number(start.slice(0, 4)), last = Number(end.slice(0, 4));
    return Array.from({ length: last - first + 1 }, (_, index) => first + index);
}

/** Fetch only for an expanded request. The server accounts for the employee's
 * chargeable days; the browser must not substitute calendar-day arithmetic. */
export default function AdminWorkspaceVacationBalance({ username, startDate, endDate, revision, t }) {
    const years = useMemo(() => vacationBalanceYears(startDate, endDate), [startDate, endDate]);
    const requestKey = JSON.stringify([username, years]);
    const [result, setResult] = useState({ key: '', values: [] });
    const [retry, setRetry] = useState(0);
    const sequence = useRef(0);

    useEffect(() => {
        const requestNumber = ++sequence.current;
        const controller = new AbortController();
        let active = true;
        setResult({ key: requestKey, values: years.map(year => ({ year, status: 'loading' })) });
        if (!username || !years.length) return () => { active = false; controller.abort(); };

        Promise.allSettled(years.map(year => api.get('/api/vacation/remaining', {
            params: { username, year }, signal: controller.signal,
        }))).then(responses => {
            if (!active || controller.signal.aborted || requestNumber !== sequence.current) return;
            setResult({ key: requestKey, values: responses.map((response, index) => {
                if (response.status === 'rejected') return { year: years[index], status: 'error' };
                const value = response.value?.data;
                return typeof value === 'number' && Number.isFinite(value)
                    ? { year: years[index], status: 'ready', days: value }
                    : { year: years[index], status: 'unavailable' };
            }) });
        });
        return () => { active = false; controller.abort(); };
    }, [username, years, requestKey, revision, retry]);

    const values = result.key === requestKey ? result.values : years.map(year => ({ year, status: 'loading' }));
    const retryable = values.some(value => value.status === 'error' || value.status === 'unavailable');
    return <section className="aw-vacation-balance" aria-label={t('adminWorkspace.vacationBalance', 'Urlaubskonto')}>
        <span className="aw-detail-label">{t('adminWorkspace.vacationBalance', 'Urlaubskonto')}</span>
        {!username || !years.length ? <p>{t('adminWorkspace.vacationBalanceMissingContext', 'Ohne Mitarbeiter und gültigen Zeitraum kann kein Urlaubsstand geladen werden.')}</p> : <ul className="aw-overlap-list">
            {values.map(value => <li key={value.year}>
                <strong>{value.year}</strong>{' · '}
                {value.status === 'loading' ? <span role="status">{t('adminWorkspace.loadingVacationBalance', 'Urlaubsstand wird geladen…')}</span>
                    : value.status === 'error' ? <span role="alert">{t('adminWorkspace.vacationBalanceError', 'Urlaubsstand konnte nicht geladen werden.')}</span>
                        : value.status === 'unavailable' ? <span>{t('adminWorkspace.vacationBalanceUnavailable', 'Kein gültiger Urlaubsstand verfügbar.')}</span>
                            : <span>{value.days} {t('adminWorkspace.vacationDaysAvailable', 'Tage verfügbar')}</span>}
            </li>)}
        </ul>}
        {values.some(value => value.status === 'ready') && <p className="aw-balance-caption">{t('adminWorkspace.vacationBalanceBasis', 'Genehmigte Urlaube sind berücksichtigt. Offene Anträge sind noch nicht abgezogen.')}</p>}
        {retryable && <button type="button" className="aw-text-button" onClick={() => setRetry(value => value + 1)}>{t('adminWorkspace.retryVacationBalance', 'Urlaubsstand erneut laden')}</button>}
    </section>;
}
