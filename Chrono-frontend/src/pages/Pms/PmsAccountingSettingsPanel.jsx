import { useEffect, useRef, useState } from 'react';
import api from '../../utils/api.js';

const REVENUE = { ROOM: 'Übernachtungen', BREAKFAST: 'Frühstück', SERVICE: 'Zusatzleistungen', TAX: 'Tourismus- und Ortstaxen', DISCOUNT: 'Rabatte', OTHER: 'Weitere Erlöse' };
const PAYMENTS = { CASH: 'Bargeld', CARD: 'Kartenzahlungen', BANK_TRANSFER: 'Überweisungen auf Gastkonten', VOUCHER: 'Gutscheine', OTHER: 'Weitere Zahlungen' };

export default function PmsAccountingSettingsPanel({ propertyId, property, canManage = false }) {
    const id = propertyId ?? property?.id;
    const [settings, setSettings] = useState(null);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const dirty = useRef(false);
    const loadedProperty = useRef(id);
    const scope = useRef(id); scope.current = id;
    useEffect(() => {
        let active = true;
        if (loadedProperty.current !== id) { loadedProperty.current = id; dirty.current = false; setSettings(null); setError(''); setNotice(''); }
        if (dirty.current) return () => { active = false; };
        setLoading(true);
        if (!id) { setLoading(false); return () => { active = false; }; }
        api.get(`/api/pms/properties/${id}/accounting-settings`)
            .then(({ data }) => { if (active && !dirty.current) setSettings(data); })
            .catch((err) => { if (active) setError(err.response?.data?.detail || 'Der Kontenplan konnte nicht geladen werden.'); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [id]);
    const change = (key, value, section) => { dirty.current = true; setSettings((current) => section
        ? { ...current, [section]: { ...current[section], [key]: value } }
        : { ...current, [key]: value }); };
    const field = (key, label, section) => <label key={`${section || 'ledger'}-${key}`}>{label}<input
        value={(section ? settings[section]?.[key] : settings[key]) ?? ''}
        onChange={(event) => change(key, event.target.value, section)} disabled={!canManage || busy}
        required maxLength={32} pattern="[A-Za-z0-9][A-Za-z0-9._\/\-]{0,31}" autoComplete="off" /></label>;
    const save = async (event) => {
        event.preventDefault(); if (!canManage || busy) return; const started = id; setBusy(true); setError(''); setNotice('');
        try {
            const { data } = await api.put(`/api/pms/properties/${id}/accounting-settings`, settings);
            if (scope.current !== started) return;
            dirty.current = false; setSettings(data); setNotice('Der Kontenplan wurde gespeichert. Neue Exporte verwenden diese Zuordnung.');
        } catch (err) { if (scope.current === started) setError(err.response?.data?.detail || 'Der Kontenplan konnte nicht gespeichert werden.'); }
        finally { if (scope.current === started) setBusy(false); }
    };
    return <section className="pms-work-card pms-enterprise-card">
        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Buchhaltung</span><h3>Kontenplan für den Export</h3></div><span className="pms-status-pill">Pro Hotel</span></div>
        {loading && <p className="pms-muted" role="status">Kontenplan wird geladen …</p>}
        {error && <div className="pms-inline-message is-error" role="alert">{error}</div>}
        {notice && <div className="pms-inline-message is-success" role="status">{notice}</div>}
        {settings && <form onSubmit={save}>
            <p className="pms-muted">Ordnen Sie die Buchungen den Konten Ihrer Buchhaltung zu. Die gespeicherte Zuordnung gilt für jeden neu erstellten Export, auch für frühere Zeiträume.</p>
            <div className="pms-form-grid">{field('guestReceivableAccount', 'Forderungen aus Gastkonten')}{field('corporateReceivableAccount', 'Forderungen aus Firmenkredit')}{field('bankAccount', 'Bankeingang für Firmenforderungen')}{field('posRevenueAccount', 'Direkt bezahlte Restaurant- und Barerlöse')}</div>
            <details className="pms-enterprise-details" open><summary>Erlöskonten</summary><div className="pms-form-grid">{Object.entries(REVENUE).map(([key, label]) => field(key, label, 'revenueAccounts'))}</div></details>
            <details className="pms-enterprise-details" open><summary>Zahlungskonten</summary><div className="pms-form-grid">{Object.entries(PAYMENTS).map(([key, label]) => field(key, label, 'paymentAccounts'))}</div></details>
            <p className="pms-muted">Firmenübernahmen buchen automatisch auf das Konto für Firmenforderungen. Erstattungen verwenden dieselben Konten mit umgekehrter Buchungsrichtung.</p>
            {canManage ? <div className="pms-form-actions"><button type="submit" className="is-primary" disabled={busy}>{busy ? 'Wird gespeichert …' : 'Kontenplan speichern'}</button></div> : <p className="pms-muted">Ein PMS-Master kann diese Zuordnung bearbeiten.</p>}
        </form>}
    </section>;
}
