import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import api from '../../utils/api.js';
import { formatPmsDate, formatPmsDateTime } from './pmsFormatting.js';
import { currencyStep, formatPmsMoney } from './pmsMoney.js';
import './PmsRevenuePlanningPanel.css';

const shiftDate = (value, days) => {
    const date = new Date(`${value}T12:00:00Z`); date.setUTCDate(date.getUTCDate() + days); return date.toISOString().slice(0, 10);
};
const INVALID_REPORT = 'Die Antwort zur Umsatzplanung ist unvollständig oder gehört nicht zu diesem Hotel. Bitte erneut laden.';
const problem = (error) => error.response?.data?.detail || (error.code === 'INVALID_REVENUE_REPORT' ? INVALID_REPORT : 'Die Umsatzplanung konnte nicht aktualisiert werden.');
const count = (value) => value == null ? '—' : new Intl.NumberFormat('de-CH', { maximumFractionDigits: 2 }).format(Number(value));
const validDate = (value) => typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(Date.parse(`${value}T12:00:00Z`));
const validSnapshot = (value) => value && value.id != null && validDate(value.asOfDate) && typeof value.capturedAt === 'string';
function validateReport(data, propertyId) {
    const valid = data && String(data.propertyId) === String(propertyId) && /^[A-Z]{3}$/.test(data.currencyCode)
        && validDate(data.businessDate) && validDate(data.from) && validDate(data.toExclusive)
        && data.summary && ['roomNights', 'unknownRevenueRoomNights', 'comparisonCoverageDays', 'requestedDays'].every((key) => Number.isFinite(data.summary[key]))
        && Array.isArray(data.days) && data.days.every((day) => day && validDate(day.date))
        && Array.isArray(data.months) && data.months.every((month) => month && validDate(month.monthStart) && (month.budget == null || typeof month.budget === 'object'))
        && Array.isArray(data.snapshots) && data.snapshots.every(validSnapshot)
        && (data.comparisonSnapshot == null || validSnapshot(data.comparisonSnapshot));
    if (!valid) throw Object.assign(new Error(INVALID_REPORT), { code: 'INVALID_REVENUE_REPORT' });
    return data;
}

function PaceChart({ days, currency }) {
    const known = days.flatMap((day) => [day.netRoomRevenue, day.previousNetRoomRevenue]).filter((value) => value != null).map(Number);
    if (!known.length) return <p className="pms-planning-empty">Für diesen Zeitraum fehlen bestätigbare Netto-Zimmererlöse.</p>;
    const minimum = Math.min(0, ...known); const maximum = Math.max(1, ...known); const width = 960; const height = 170; const margin = 12;
    const y = (value) => height - margin - (Number(value) - minimum) / (maximum - minimum) * (height - margin * 2);
    const points = (key) => {
        const segments = []; let current = [];
        days.forEach((day, index) => {
            if (day[key] == null) { if (current.length) segments.push(current); current = []; return; }
            current.push(`${margin + index * (width - margin * 2) / Math.max(1, days.length - 1)},${y(day[key])}`);
        });
        if (current.length) segments.push(current); return segments;
    };
    return <figure className="pms-planning-chart">
        <div className="pms-planning-chart-labels"><span>{formatPmsMoney(maximum, currency)}</span><div><span className="pms-planning-legend current">Aktueller Buchungsstand</span><span className="pms-planning-legend previous">Gespeicherter Vergleich</span></div></div>
        <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Netto-Zimmererlöse je Aufenthaltstag: aktueller und gespeicherter Buchungsstand">
            {[0, 1, 2, 3].map((line) => <line key={line} x1={margin} x2={width - margin} y1={margin + line * (height - margin * 2) / 3} y2={margin + line * (height - margin * 2) / 3} stroke="currentColor" opacity=".09" />)}
            <line x1={margin} x2={width - margin} y1={y(0)} y2={y(0)} stroke="currentColor" opacity=".3" />
            {['previousNetRoomRevenue', 'netRoomRevenue'].map((key) => points(key).map((segment, index) => segment.length === 1
                ? <circle key={`${key}-${index}`} cx={segment[0].split(',')[0]} cy={segment[0].split(',')[1]} r="3" fill={key === 'netRoomRevenue' ? '#168776' : '#b58335'} />
                : <polyline key={`${key}-${index}`} points={segment.join(' ')} fill="none" stroke={key === 'netRoomRevenue' ? '#168776' : '#b58335'} strokeWidth="3" strokeDasharray={key === 'netRoomRevenue' ? undefined : '7 5'} vectorEffect="non-scaling-stroke" />))}
        </svg>
        <div className="pms-planning-chart-labels"><span>{formatPmsMoney(minimum, currency)}</span></div>
        <figcaption><span>{formatPmsDate(days[0]?.date)}</span><span>Lücken zeigen fehlende Werte.</span><span>{formatPmsDate(days.at(-1)?.date)}</span></figcaption>
    </figure>;
}

export default function PmsRevenuePlanningPanel({ property, canManage = false }) {
    const id = property?.id;
    const [report, setReport] = useState(null); const [busy, setBusy] = useState(false);
    const [error, setError] = useState(''); const [notice, setNotice] = useState('');
    const [from, setFrom] = useState(''); const [through, setThrough] = useState(''); const [comparison, setComparison] = useState('');
    const [budgetMonth, setBudgetMonth] = useState(''); const [budgetRevenue, setBudgetRevenue] = useState(''); const [budgetNights, setBudgetNights] = useState('');
    const loadedProperty = useRef(null);
    const activeProperty = useRef(id); activeProperty.current = id;
    const appliedBudget = useRef(null);
    const endpoint = `/api/pms/properties/${id}/reports/revenue-planning`;
    const load = useCallback(async (params = {}) => {
        const { data } = await api.get(`/api/pms/properties/${id}/reports/revenue-planning`, { params });
        validateReport(data, id);
        if (activeProperty.current === id) {
            loadedProperty.current = id;
            setReport(data);
            setBudgetMonth((current) => data.months.some((month) => month.monthStart.slice(0, 7) === current) ? current : data.from.slice(0, 7));
        }
        return data;
    }, [id]);
    useEffect(() => {
        if (!id || loadedProperty.current === id) return undefined;
        let active = true; loadedProperty.current = null; setReport(null); setError(''); setNotice(''); setBusy(true);
        api.get(`/api/pms/properties/${id}/reports/revenue-planning`).then(({ data }) => {
            if (!active) return;
            validateReport(data, id);
            loadedProperty.current = id; setReport(data); setFrom(data.from); setThrough(shiftDate(data.toExclusive, -1)); setComparison(''); setBudgetMonth(data.from.slice(0, 7));
        }).catch((failure) => { if (active) setError(problem(failure)); }).finally(() => { if (active) setBusy(false); });
        return () => { active = false; };
    }, [id]);
    const selectedBudget = useMemo(() => report?.months.find((month) => month.monthStart.slice(0, 7) === budgetMonth)?.budget, [report, budgetMonth]);
    useEffect(() => {
        const identity = `${id}:${budgetMonth}:${selectedBudget?.version ?? 'new'}`;
        if (appliedBudget.current === identity) return;
        appliedBudget.current = identity;
        setBudgetRevenue(selectedBudget?.netRoomRevenue == null ? '' : String(selectedBudget.netRoomRevenue)); setBudgetNights(selectedBudget?.roomNights == null ? '' : String(selectedBudget.roomNights));
    }, [id, selectedBudget, budgetMonth]);
    const parameters = () => ({ from: from || undefined, to: through ? shiftDate(through, 1) : undefined, comparisonSnapshotId: comparison || undefined });
    const refresh = async (event) => {
        event?.preventDefault(); setBusy(true); setError(''); setNotice('');
        try { const data = await load(parameters()); if (activeProperty.current === id) { setFrom(data.from); setThrough(shiftDate(data.toExclusive, -1)); } }
        catch (failure) { if (activeProperty.current === id) setError(problem(failure)); } finally { if (activeProperty.current === id) setBusy(false); }
    };
    const capture = async () => {
        setBusy(true); setError(''); setNotice('');
        try {
            const { data } = await api.post(`${endpoint}/snapshots`);
            if (activeProperty.current !== id) return;
            await load(parameters()); if (activeProperty.current === id) setNotice(`Der Buchungsstand vom Betriebstag ${formatPmsDate(data.asOfDate)} ist unveränderlich gespeichert.`);
        } catch (failure) { if (activeProperty.current === id) setError(problem(failure)); } finally { if (activeProperty.current === id) setBusy(false); }
    };
    const saveBudget = async (event) => {
        event.preventDefault(); setBusy(true); setError(''); setNotice('');
        try {
            await api.put(`${endpoint}/budgets/${budgetMonth}`, { netRoomRevenue: budgetRevenue, roomNights: Number(budgetNights), expectedVersion: selectedBudget?.version ?? null });
            if (activeProperty.current !== id) return;
            await load(parameters()); if (activeProperty.current === id) setNotice('Das Monatsbudget wurde gespeichert.');
        } catch (failure) { if (activeProperty.current === id) setError(problem(failure)); } finally { if (activeProperty.current === id) setBusy(false); }
    };
    const currency = report?.currencyCode || property?.currencyCode || 'CHF';
    const money = (value) => value == null ? 'Nicht verfügbar' : formatPmsMoney(value, currency);
    const signedMoney = (value) => value == null ? 'Kein Vergleich' : `${Number(value) > 0 ? '+' : ''}${formatPmsMoney(value, currency)}`;
    const capturedToday = report?.snapshots.some((snapshot) => snapshot.asOfDate === report.businessDate);
    return <section className="pms-work-card pms-enterprise-card pms-revenue-planning">
        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Revenue Management</span><h3>Buchungsentwicklung und Monatsbudget</h3></div>{report && <span className="pms-status-pill">Betriebstag {formatPmsDate(report.businessDate)}</span>}</div>
        <p className="pms-muted">Vergleichen Sie Zimmernächte und Netto-Zimmererlöse bestätigter und eingecheckter Aufenthalte mit einem tatsächlich gespeicherten Buchungsstand. Frühstück, Restaurant und sonstige Leistungen sind separat.</p>
        {error && <div role="alert" className="pms-inline-message is-error">{error}</div>}{notice && <div role="status" className="pms-inline-message is-success">{notice}</div>}
        {!report && busy && <p role="status">Buchungsstand wird geladen …</p>}
        {!report && error && !busy && <button type="button" onClick={refresh}>Erneut laden</button>}
        {report && <>
            <form className="pms-planning-filters" onSubmit={refresh}>
                <label>Aufenthalt ab<input type="date" required min={report.businessDate} value={from} onChange={(event) => setFrom(event.target.value)} /></label>
                <label>Bis einschließlich<input type="date" required min={from} value={through} onChange={(event) => setThrough(event.target.value)} /></label>
                <label>Vergleichsstand<select value={comparison} onChange={(event) => setComparison(event.target.value)}><option value="">Neuester gespeicherter Stand</option>{report.snapshots.map((snapshot) => <option key={snapshot.id} value={snapshot.id}>{formatPmsDateTime(snapshot.capturedAt)} · Betriebstag {formatPmsDate(snapshot.asOfDate)}</option>)}</select></label>
                <button type="submit" disabled={busy}>Aktualisieren</button>
            </form>
            {!report.comparisonSnapshot ? <div className="pms-planning-empty">Noch kein Vergleichsstand vorhanden. Vergangene Buchungsstände werden nicht nachträglich rekonstruiert.</div>
                : <p className="pms-muted">Vergleich: {formatPmsDateTime(report.comparisonSnapshot.capturedAt)} · {report.summary.comparisonCoverageDays} von {report.summary.requestedDays} Aufenthaltstagen abgedeckt.</p>}
            {report.summary.comparisonCoverageDays < report.summary.requestedDays && report.comparisonSnapshot && <div className="pms-planning-empty">Die Historie deckt den Zeitraum nur teilweise ab. Gesamtveränderungen bleiben deshalb leer; Tageswerte mit belegtem Vergleich werden angezeigt.</div>}
            {report.summary.unknownRevenueRoomNights > 0 && <div role="status" className="pms-inline-message is-error">Für {count(report.summary.unknownRevenueRoomNights)} Zimmernächte fehlen eindeutige Zimmerleistungen oder Steuersätze. Nettoerlöse bleiben bis zur Klärung als nicht verfügbar markiert.</div>}
            <div className="pms-planning-kpis">
                <div><span>Gebuchte Zimmernächte</span><strong>{count(report.summary.roomNights)}</strong><small>{report.summary.pickupRoomNights == null ? 'Kein vollständiger Vergleich' : `${report.summary.pickupRoomNights > 0 ? '+' : ''}${count(report.summary.pickupRoomNights)} seit Vergleichsstand`}</small></div>
                <div><span>Netto-Zimmererlöse</span><strong>{money(report.summary.netRoomRevenue)}</strong><small>Aktueller Buchungsstand</small></div>
                <div><span>Erlösveränderung (Pickup)</span><strong>{signedMoney(report.summary.pickupNetRoomRevenue)}</strong><small>Aktuell minus gespeicherter Vergleich</small></div>
            </div>
            <PaceChart days={report.days} currency={currency} />
            <div className="pms-planning-table"><table><caption>Monatsvergleich · Budgets bei Teilmonaten anteilig nach Kalendertagen</caption><thead><tr><th>Aufenthaltsmonat</th><th>Zimmernächte</th><th>Nettoerlöse</th><th>Erlösveränderung</th><th>Budget Nettoerlöse</th><th>Abweichung vom Budget</th></tr></thead><tbody>{report.months.map((month) => <tr key={month.monthStart}><th>{month.monthStart.slice(0, 7)}<small>{month.coveredDays} von {month.daysInMonth} Tagen</small></th><td>{count(month.roomNights)}{month.proratedBudgetRoomNights != null && <small>Budget {count(month.proratedBudgetRoomNights)}</small>}</td><td>{money(month.netRoomRevenue)}</td><td>{signedMoney(month.pickupNetRoomRevenue)}</td><td>{month.budget ? money(month.proratedBudgetNetRoomRevenue) : 'Nicht geplant'}</td><td>{month.budgetRevenueVariance == null ? '—' : signedMoney(month.budgetRevenueVariance)}</td></tr>)}</tbody></table></div>
            <details className="pms-enterprise-details"><summary>Aufenthaltstage einzeln anzeigen</summary><div className="pms-planning-table"><table><thead><tr><th>Datum</th><th>Zimmernächte</th><th>Nettoerlöse aktuell</th><th>Nettoerlöse Vergleich</th><th>Erlösveränderung</th></tr></thead><tbody>{report.days.map((day) => <tr key={day.date}><th>{formatPmsDate(day.date)}</th><td>{count(day.roomNights)}</td><td>{money(day.netRoomRevenue)}</td><td>{day.previousNetRoomRevenue == null ? 'Nicht verfügbar' : money(day.previousNetRoomRevenue)}</td><td>{signedMoney(day.pickupNetRoomRevenue)}</td></tr>)}</tbody></table></div></details>
            {canManage && <div className="pms-planning-management"><div><h4>Täglichen Vergleich sichern</h4><p className="pms-muted">Pro Hotel und offenem Betriebstag wird einmal ein unveränderlicher Stand der nächsten 366 Tage gespeichert. Ein erneuter Klick überschreibt keine Historie.</p><button type="button" onClick={capture} disabled={busy || capturedToday}>{capturedToday ? 'Heutiger Betriebstag bereits gespeichert' : 'Buchungsstand jetzt sichern'}</button></div>
                <form onSubmit={saveBudget}><h4>Monatsbudget festlegen</h4><p className="pms-muted">Das Budget gilt für den vollständigen Kalendermonat und diese Hotelwährung.</p><div className="pms-form-grid"><label>Monat<select value={budgetMonth} onChange={(event) => setBudgetMonth(event.target.value)}>{report.months.map((month) => <option key={month.monthStart} value={month.monthStart.slice(0, 7)}>{month.monthStart.slice(0, 7)}</option>)}</select></label><label>Netto-Zimmererlöse ({currency})<input type="number" required min="0" step={currencyStep(currency)} value={budgetRevenue} onChange={(event) => setBudgetRevenue(event.target.value)} /></label><label>Zimmernächte<input type="number" required min="0" step="1" value={budgetNights} onChange={(event) => setBudgetNights(event.target.value)} /></label></div>{selectedBudget && <p className="pms-muted">Zuletzt geändert: {formatPmsDateTime(selectedBudget.updatedAt)} · {selectedBudget.updatedBy}</p>}<button type="submit" className="is-primary" disabled={busy}>Monatsbudget speichern</button></form></div>}
        </>}
    </section>;
}
