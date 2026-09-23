import { useEffect, useState } from 'react';
import api from '../../utils/api.js';

export const PMS_PERMISSION_LABELS = {
    FRONT_DESK: 'Rezeption', GUESTS: 'Gäste & Firmen', HOUSEKEEPING: 'Housekeeping',
    FINANCE: 'Kasse & Abrechnung', REFUNDS: 'Rückerstattungen', RATES: 'Preise & Raten',
    REPORTS: 'Berichte', INTEGRATIONS: 'Schnittstellen',
};

export default function PmsAccessWorkspace({ onChanged }) {
    const [data, setData] = useState(null);
    const [selectedId, setSelectedId] = useState('');
    const [grants, setGrants] = useState([]);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [query,setQuery] = useState('');
    const [page,setPage] = useState(0);
    const [loading,setLoading] = useState(true);
    const [dirty,setDirty] = useState(false);
    useEffect(() => {
        const controller = new AbortController();
        setLoading(true);
        const timer=setTimeout(()=>api.get('/api/pms/access/users', { params:{page,size:50,query},signal: controller.signal }).then(({ data: result }) => {
            if (!Array.isArray(result?.users) || !Array.isArray(result?.properties) || !Array.isArray(result?.permissionKeys)) throw new Error('Ungültige Berechtigungsübersicht');
            if (!controller.signal.aborted) setData(result);
        }).catch((err) => {
            if (!controller.signal.aborted) setError(err.response?.data?.detail || 'Die Hotelrechte konnten nicht geladen werden.');
        }).finally(()=>{if(!controller.signal.aborted)setLoading(false);}),200);
        return () => {clearTimeout(timer);controller.abort();};
    }, [page,query]);
    const selected = data?.users?.find((user) => String(user.userId) === selectedId);
    const selectUser = (id) => {
        setSelectedId(id);
        setGrants(structuredClone(data.users.find((user) => String(user.userId) === id)?.grants || []));
        setError(''); setNotice('');setDirty(false);
    };
    const setPermission = (propertyId, key, value) => {setDirty(true);setGrants((current) => {
        const permissions = { ...(current.find((grant) => grant.propertyId === propertyId)?.permissions || {}) };
        if (value) permissions[key] = value; else delete permissions[key];
        return [...current.filter((grant) => grant.propertyId !== propertyId), { propertyId, permissions }];
    });};
    const save = async (event) => {
        event.preventDefault(); setBusy(true); setError(''); setNotice('');
        try {
            const { data: user } = await api.put(`/api/pms/access/users/${selectedId}`, {
                grants: grants.filter((grant) => Object.keys(grant.permissions).length),
            });
            setData((current) => ({ ...current, users: current.users.map((entry) => entry.userId === user.userId ? user : entry) }));
            setGrants(structuredClone(user.grants||[]));setDirty(false);
            setNotice('Hotelrechte gespeichert. Die Zugriffe werden ab der nächsten Anfrage geprüft.');
            onChanged?.();
        } catch (err) { setError(err.response?.data?.detail || 'Die Hotelrechte konnten nicht gespeichert werden.'); }
        finally { setBusy(false); }
    };
    return <section className="pms-work-card pms-enterprise-card">
        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Master-Verwaltung</span><h3>Mitarbeiter & Hotelrechte</h3></div><span className="pms-status-pill">Je Hotel</span></div>
        <p className="pms-muted">Weise jedem Mitarbeiter die Hotels und Tätigkeiten zu, die er für seine Arbeit benötigt.</p>
        {error && <div className="pms-feedback is-error" role="alert">{error}</div>}
        {notice && <div className="pms-feedback is-success" role="status">{notice}</div>}
        <label>Mitarbeiter suchen<input type="search" maxLength="120" value={query} placeholder="Name oder Benutzername" disabled={busy||dirty} onChange={event=>{setQuery(event.target.value);setPage(0);setSelectedId('');setGrants([]);}}/></label>
        {data&&<div className="pms-form-actions"><button type="button" disabled={!page||busy||dirty||loading} onClick={()=>{setPage(current=>current-1);setSelectedId('');setGrants([]);}}>Mitarbeiter zurück</button><span role="status">{loading?'Mitarbeiter werden geladen …':`Seite ${page+1} · ${data.totalElements??data.users.length} Mitarbeiter`}</span><button type="button" disabled={!data.hasNext||busy||dirty||loading} onClick={()=>{setPage(current=>current+1);setSelectedId('');setGrants([]);}}>Mitarbeiter weiter</button></div>}
        {!data ? !error && <p role="status">Hotelrechte werden geladen …</p> : <form onSubmit={save}>
            <label className="pms-enterprise-select">Mitarbeiter<select value={selectedId} onChange={(event) => selectUser(event.target.value)} disabled={busy||dirty||loading}>
                <option value="">Mitarbeiter auswählen</option>{data.users.map((user) => <option key={user.userId} value={user.userId}>{user.displayName || user.username}{user.master ? ' · Master' : ''}</option>)}
            </select></label>
            {selected?.master ? <p className="pms-inline-note">Dieser Master-Account verwaltet alle Hotels. Die Master-Rolle wird in der Benutzerverwaltung geändert.</p> : selected && <>
                <div className="pms-table-scroll"><table className="pms-access-table"><caption className="sr-only">Berechtigungen je Hotel und Tätigkeit</caption><thead><tr><th>Hotel</th>{data.permissionKeys.map((key) => <th key={key}>{PMS_PERMISSION_LABELS[key] || key}</th>)}</tr></thead>
                    <tbody>{data.properties.map((property) => <tr key={property.propertyId}><th scope="row">{property.propertyName}</th>{data.permissionKeys.map((key) => <td key={key}><select aria-label={`${property.propertyName}: ${PMS_PERMISSION_LABELS[key] || key}`} disabled={busy} value={grants.find((g) => g.propertyId === property.propertyId)?.permissions?.[key] || ''} onChange={(event) => setPermission(property.propertyId, key, event.target.value)}><option value="">Kein Zugriff</option><option value="VIEW">Ansehen</option><option value="MANAGE">Bearbeiten</option></select></td>)}</tr>)}</tbody>
                </table></div>
                <div className="pms-form-actions"><button className="is-primary" disabled={busy||!dirty} type="submit">{busy ? 'Wird gespeichert …' : 'Hotelrechte speichern'}</button>{dirty&&<button type="button" disabled={busy} onClick={()=>selectUser(selectedId)}>Änderungen verwerfen</button>}</div>
            </>}
        </form>}
    </section>;
}
