import { useCallback, useEffect, useState } from 'react';
import api from '../../utils/api.js';
import { formatPmsDate } from './pmsFormatting.js';
import usePmsLiveRefresh from './usePmsLiveRefresh.js';

export default function PmsFinancialDayPanel({ property, canManage, onClosed }) {
    const [day, setDay] = useState(null);
    const [markNoShows, setMarkNoShows] = useState(false);
    const [confirmed, setConfirmed] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const load = useCallback(async () => {
        try { const { data } = await api.get(`/api/pms/properties/${property.id}/financial-day`); setDay(data); }
        catch (err) { setError(err.response?.data?.detail || 'Der Betriebstag konnte nicht geprüft werden.'); }
    }, [property.id]);
    useEffect(() => { load(); }, [load]);
    usePmsLiveRefresh(load, { enabled: !busy });
    const close = async (event) => {
        event.preventDefault(); setBusy(true); setError(''); setNotice('');
        try {
            await api.post(`/api/pms/properties/${property.id}/night-audits`, { businessDate: day.businessDate, markPendingArrivalsAsNoShow: markNoShows });
            setNotice(`Der ${formatPmsDate(day.businessDate)} ist abgeschlossen. Der nächste Betriebstag ist geöffnet.`);
            setConfirmed(false); setMarkNoShows(false); await load(); await onClosed?.();
        } catch (err) { setError(err.response?.data?.detail || 'Der Tagesabschluss konnte nicht ausgeführt werden.'); }
        finally { setBusy(false); }
    };
    return <section className="pms-work-card pms-enterprise-card">
        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Tagesabschluss</span><h3>{day ? `Betriebstag ${formatPmsDate(day.businessDate)}` : 'Betriebstag wird geprüft …'}</h3></div>{day && <span className="pms-status-pill">Offener Betriebstag</span>}</div>
        {error && <div className="pms-inline-message is-error" role="alert">{error}</div>}{notice && <div className="pms-inline-message is-success" role="status">{notice}</div>}
        {day && <form onSubmit={close}>
            <div className="pms-day-checks">{[['Kassen noch offen', day.openCashShifts], ['Anreisen zu klären', day.pendingArrivals], ['Abreisen zu klären', day.pendingDepartures]].map(([label, count]) => <div key={label} className={Number(count) > 0 ? 'needs-attention' : 'is-complete'}><span>{label}</span><strong>{count ?? 0}</strong></div>)}</div>
            {day.blockers?.length > 0 && <ul className="pms-check-list">{day.blockers.map((blocker, index) => <li key={index}>{typeof blocker === 'string' ? blocker : blocker.message}</li>)}</ul>}
            <p className="pms-muted">Abgeschlossene Tage sind für normale Leistungsbuchungen gesperrt. Korrekturen werden nachvollziehbar im offenen Betriebstag gebucht.</p>
            {Number(day.pendingArrivals) > 0 && <label className="pms-check-row"><input type="checkbox" checked={markNoShows} onChange={(event) => setMarkNoShows(event.target.checked)} />Verbleibende bestätigte Anreisen als nicht angereist abschließen</label>}
            <label className="pms-check-row"><input type="checkbox" checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)} />Kassen, Abreisen und offene Anreisen sind geprüft.</label>
            <div className="pms-form-actions"><button className="is-primary" disabled={!canManage || busy || !confirmed || Number(day.openCashShifts) > 0 || Number(day.pendingDepartures) > 0 || (Number(day.pendingArrivals) > 0 && !markNoShows)} type="submit">{busy ? 'Abschluss läuft …' : 'Betriebstag abschließen'}</button></div>
        </form>}
    </section>;
}
