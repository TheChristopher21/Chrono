import { useCallback, useEffect, useRef, useState } from 'react';
import api from '../../utils/api.js';
import usePmsLiveRefresh from './usePmsLiveRefresh.js';
import './PmsPaymentAutomationPanel.css';

export default function PmsPaymentAutomationPanel({ property, canManageSettings, canManage = false }) {
    const [settings, setSettings] = useState(null);
    const [status, setStatus] = useState(null);
    const [dirty, setDirty] = useState(false);
    const dirtyRef = useRef(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [legacy, setLegacy] = useState('');
    const scope = useRef(property.id); scope.current = property.id;
    const loadStatus = useCallback(async () => {
        const { data } = await api.get(`/api/pms/properties/${property.id}/payment-automation`);
        if (scope.current === property.id && Array.isArray(data?.requests)) setStatus(data);
    }, [property.id]);
    const loadSettings = useCallback(async ({ discardDraft = false } = {}) => {
        const { data } = await api.get(`/api/pms/properties/${property.id}/payment-settings`);
        if (scope.current === property.id && (discardDraft || !dirtyRef.current)) { setSettings(data); dirtyRef.current = false; setDirty(false); }
    }, [property.id]);
    useEffect(() => { let active = true; Promise.all([loadSettings(), loadStatus()]).catch(() => { if (active) setError('Zahlungsautomatik konnte nicht geladen werden.'); }); return () => { active = false; }; }, [loadSettings, loadStatus]);
    usePmsLiveRefresh(loadStatus, { enabled: !busy });
    const change = (next) => { dirtyRef.current = true; setSettings((current) => ({ ...current, ...next })); setDirty(true); setNotice(''); };
    const save = async (event) => {
        event.preventDefault(); if (busy || !canManageSettings) return;
        const started = property.id; setBusy(true); setError('');
        try {
            const { data } = await api.put(`/api/pms/properties/${started}/payment-settings`, settings);
            if (scope.current !== started) return;
            setSettings(data); dirtyRef.current = false; setDirty(false); setNotice('Händlerkonto geprüft und Einstellungen gespeichert.'); await loadStatus();
        } catch (err) { if (scope.current === started) setError(err.response?.data?.detail || 'Einstellungen konnten nicht gespeichert werden.'); }
        finally { if (scope.current === started) setBusy(false); }
    };
    const retry = async (id) => {
        if (busy || !canManage) return; const started = property.id; setBusy(true); setError('');
        try { await api.post(`/api/pms/properties/${started}/payment-requests/${id}/reconcile`); if (scope.current === started) await loadStatus(); }
        catch (err) { if (scope.current === started) setError(err.response?.data?.detail || 'Anbieterabgleich bleibt offen.'); }
        finally { if (scope.current === started) setBusy(false); }
    };
    const mode = settings?.merchantContext?.split(':')[0] || 'CONNECT';
    const account = settings?.merchantContext?.split(':')[1] || '';
    const labels = { REQUESTED: 'Angefordert', OPEN: 'Zahlung ausstehend', AUTHORIZED: 'Karte vorautorisiert', PAID: 'Bezahlt', RELEASED: 'Freigegeben', EXPIRED: 'Abgelaufen', FAILED: 'Fehlgeschlagen', CAPTURE_REQUESTED: 'Einzug angefordert', RELEASE_REQUESTED: 'Freigabe angefordert' };
    const bindLegacy = async (event) => {
        event.preventDefault(); if (!canManageSettings || busy || !legacy) return;
        const started = property.id; const [target, id] = legacy.split(':'); setBusy(true); setError('');
        try { await api.post(`/api/pms/properties/${started}/payment-settings/legacy-merchant`, { target, id: Number(id), merchantContext: settings.merchantContext }); if (scope.current === started) { setLegacy(''); setNotice('Historischen Vorgang beim Händler bestätigt und dauerhaft zugeordnet.'); await loadStatus(); } }
        catch (err) { if (scope.current === started) setError(err.response?.data?.detail || 'Historische Händlerzuordnung konnte nicht bestätigt werden.'); }
        finally { if (scope.current === started) setBusy(false); }
    };
    return <section className="pms-work-card pms-enterprise-card pms-payment-automation">
        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Zahlungsbetrieb</span><h3>Händlerkonto & automatische Anzahlungslinks</h3></div><span className={`pms-status-pill ${status?.workerEnabled ? '' : 'is-warning'}`}>{status?.workerEnabled ? 'Automatik aktiv' : 'Automatik auf Server inaktiv'}</span></div>
        {error && <div className="pms-inline-message is-error" role="alert">{error}</div>}{notice && <div className="pms-inline-message is-success" role="status">{notice}</div>}
        <p className="pms-muted">Jeder Zahlungsvorgang behält sein Händlerkonto. Ein späterer Kontowechsel gilt für neue Vorgänge. Fällige Anzahlungen können einen Zahlungslink per E-Mail erhalten; der Gast bestätigt die Zahlung selbst.</p>
        {settings && <form className="pms-form-grid" onSubmit={save}>
            <label>Händlerkonto<select value={mode} disabled={!canManageSettings || busy} onChange={(event) => change({ merchantContext: `${event.target.value}:${account}` })}><option value="CONNECT">Eigenes Hotelkonto über Stripe Connect</option><option value="PLATFORM">Serverkonto dieser Installation</option></select></label>
            <label>Stripe-Konto-ID<input aria-label="Stripe-Konto-ID" value={account} disabled={!canManageSettings || busy} placeholder="acct_…" pattern="acct_[A-Za-z0-9]+" required onChange={(event) => change({ merchantContext: `${mode}:${event.target.value.trim()}` })} /></label>
            <label className="pms-check-row is-wide"><input type="checkbox" checked={Boolean(settings.automaticDepositLinks)} disabled={!canManageSettings || busy} onChange={(event) => change({ automaticDepositLinks: event.target.checked })} />Fällige Anzahlungslinks automatisch erstellen und per E-Mail bereitstellen</label>
            <p className="pms-muted is-wide">Benötigt einen eingerichteten Hotelabsender. Bestehende Zahlungslinks werden wiederverwendet. Fehlgeschlagene Vorgänge bleiben zur Prüfung sichtbar.</p>
            {settings.verifiedAt && <small className="is-wide">Zuletzt geprüft: {new Date(settings.verifiedAt).toLocaleString('de-DE')}</small>}
            {canManageSettings && <div className="pms-form-actions is-wide"><button type="button" disabled={busy} onClick={() => { setError(''); loadSettings({ discardDraft: true }).catch(() => setError('Einstellungen konnten nicht neu geladen werden.')); }}>Gespeicherte Einstellungen laden</button><button type="submit" className="is-primary" disabled={busy || !dirty}>Prüfen & speichern</button></div>}
        </form>}
        {status?.depositError && <div className="pms-inline-message is-error">{status.depositError}</div>}
        {canManageSettings && <details className="pms-enterprise-details"><summary>Historische Kartenvorgänge zuordnen</summary><p className="pms-muted">Für ältere Vorgänge kann das Händlerkonto fehlen. Wähle das tatsächliche ursprüngliche Konto oben aus. Die Zuordnung wird erst nach Prüfung beim Anbieter gespeichert und löst keine neue Zahlung aus.</p><form className="pms-form-grid" onSubmit={bindLegacy}><label>Historischer Vorgang<select aria-label="Historischer Kartenvorgang" value={legacy} disabled={busy} required onChange={(event) => setLegacy(event.target.value)}><option value="">Vorgang auswählen</option>{status?.requests?.filter((r) => !r.merchantContext).map((r) => <option key={`r${r.id}`} value={`REQUEST:${r.id}`}>Zahlungsauftrag {r.requestId} · Gastkonto {r.folioId}</option>)}{status?.legacyPayments?.map((p) => <option key={`p${p.id}`} value={`PAYMENT:${p.id}`}>Kartenzahlung {p.id} · Gastkonto {p.folioId} · {p.amount} {property.currencyCode}</option>)}</select></label><div className="pms-form-actions"><button type="submit" className="is-primary" disabled={busy || !legacy || !account}>Beim Händler prüfen & zuordnen</button></div></form></details>}
        <div className="pms-payment-automation-status"><h4>Letzte Zahlungsvorgänge</h4><button type="button" disabled={busy} onClick={() => loadStatus().catch(() => setError('Status konnte nicht geladen werden.'))}>Aktualisieren</button></div>
        <div className="pms-table-scroll"><table className="pms-access-table"><thead><tr><th>Vorgang</th><th>Status</th><th>Händlerkonto</th><th>Abgleich</th><th>Aktion</th></tr></thead><tbody>{status?.requests?.map((request) => <tr key={request.id}><td>{request.requestId}</td><td>{labels[request.status] || request.status}{request.error && <small className="pms-table-subline">{request.error}</small>}</td><td>{request.merchantContext || 'Historische Zuordnung erforderlich'}</td><td>{request.checkedAt ? new Date(request.checkedAt).toLocaleString('de-DE') : 'Ausstehend'}</td><td><button type="button" disabled={busy || !canManage} onClick={() => retry(request.id)}>Status prüfen</button></td></tr>)}</tbody></table>{status?.requests?.length === 0 && <p className="pms-muted">Noch keine Zahlungsaufträge vorhanden.</p>}</div>
    </section>;
}
