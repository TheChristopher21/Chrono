import { currencyStep } from './pmsMoney.js';
import { useState } from 'react';
import api from '../../utils/api.js';

export default function PmsCashierPanel({ property, shifts = [], canManage, onOperationsChange }) {
    const [opening, setOpening] = useState({ registerCode: 'RECEPTION-1', outletCode: 'FRONTDESK', openingFloat: '200.00', notes: '' });
    const [closing, setClosing] = useState({ cashShiftId: '', actualCash: '', notes: '' });
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const open = shifts.filter((shift) => shift.status === 'OPEN');
    const selected = open.find((shift) => String(shift.id) === closing.cashShiftId);
    const money = (amount) => new Intl.NumberFormat('de-CH', { style: 'currency', currency: property.currencyCode }).format(Number(amount || 0));
    const submit = async (event, action) => {
        event.preventDefault(); setBusy(true); setError(''); setNotice('');
        try {
            const payload = action === 'open' ? { ...opening, openingFloat: Number(opening.openingFloat) } : { ...closing, cashShiftId: Number(closing.cashShiftId), actualCash: Number(closing.actualCash) };
            const { data } = await api.post(`/api/pms/properties/${property.id}/cash-shifts/${action}`, payload);
            onOperationsChange?.(data); setNotice(action === 'open' ? 'Kassenschicht geöffnet.' : 'Kassenschicht abgestimmt und geschlossen.');
            if (action === 'close') setClosing({ cashShiftId: '', actualCash: '', notes: '' });
        } catch (err) { setError(err.response?.data?.detail || 'Die Kassenschicht konnte nicht gespeichert werden.'); }
        finally { setBusy(false); }
    };
    return <section className="pms-work-card pms-enterprise-card">
        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Kassen & Schichten</span><h3>{open.length} Kassen geöffnet</h3></div><span className="pms-status-pill">Rezeption & Outlets</span></div>
        {error && <div className="pms-inline-message is-error" role="alert">{error}</div>}{notice && <div className="pms-inline-message is-success" role="status">{notice}</div>}
        <div className="pms-cash-registers">{open.map((shift) => <button type="button" key={shift.id} className={closing.cashShiftId === String(shift.id) ? 'is-selected' : ''} onClick={() => setClosing({ cashShiftId: String(shift.id), actualCash: '', notes: '' })}><span>{shift.outletCode}</span><strong>{shift.registerCode}</strong><small>{shift.openedBy}</small><b>{money(shift.expectedCash)}</b><small>Sollbestand</small></button>)}</div>
        {selected && <form className="pms-form-grid" onSubmit={(event) => submit(event, 'close')}><p className="pms-inline-note is-wide">{selected.registerCode}: Anfang {money(selected.openingFloat)} · Barbewegungen {money(selected.cashMovements)} · Soll {money(selected.expectedCash)}</p><label>Gezähltes Bargeld<input type="number" min="0" step={currencyStep(property.currencyCode)} value={closing.actualCash} onChange={(event) => setClosing({ ...closing, actualCash: event.target.value })} required /></label><label>Abschlussnotiz<input value={closing.notes} onChange={(event) => setClosing({ ...closing, notes: event.target.value })} maxLength={500} /></label>{closing.actualCash !== '' && <p className="is-wide">Differenz: <strong>{money(Number(closing.actualCash) - Number(selected.expectedCash || 0))}</strong></p>}<div className="pms-form-actions is-wide"><button type="submit" disabled={!canManage || busy} className="is-primary">Kasse zählen & schließen</button></div></form>}
        <details className="pms-enterprise-details" open={!open.length}><summary>Weitere Kassenschicht öffnen</summary><form className="pms-form-grid" onSubmit={(event) => submit(event, 'open')}><label>Kassenkennung<input value={opening.registerCode} onChange={(event) => setOpening({ ...opening, registerCode: event.target.value })} maxLength={40} required /></label><label>Abteilung / Outlet<input value={opening.outletCode} onChange={(event) => setOpening({ ...opening, outletCode: event.target.value })} maxLength={40} required /></label><label>Anfangsbestand<input type="number" min="0" step={currencyStep(property.currencyCode)} value={opening.openingFloat} onChange={(event) => setOpening({ ...opening, openingFloat: event.target.value })} required /></label><label>Notiz<input value={opening.notes} onChange={(event) => setOpening({ ...opening, notes: event.target.value })} maxLength={500} /></label><div className="pms-form-actions is-wide"><button type="submit" disabled={!canManage || busy} className="is-primary">Kassenschicht öffnen</button></div></form></details>
    </section>;
}
