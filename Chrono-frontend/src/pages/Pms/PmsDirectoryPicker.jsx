import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import api from '../../utils/api.js';
import './PmsBillingAutomationPanel.css';
const errorText = (error) => error.response?.data?.detail || 'Das Verzeichnis konnte nicht geladen werden.';
const optionLabel = (entry) => [entry.referenceCode || entry.groupCode, entry.name].filter(Boolean).join(' · ');
const EMPTY = [];
export function usePmsOrganizationDirectory(propertyId, initial = EMPTY) {
    const [cache, setCache] = useState({ propertyId, entries: [] });
    const remember = useCallback((entry) => { if (!entry) return; setCache((current) => {
        const previous = current.propertyId === propertyId ? current.entries : [];
        if (previous.find((item) => item.id === entry.id) === entry) return current;
        return { propertyId, entries: [...previous.filter((item) => item.id !== entry.id), entry] };
    }); }, [propertyId]);
    const entries = useMemo(() => Array.from(new Map([...initial, ...(cache.propertyId === propertyId ? cache.entries : [])].map((entry) => [entry.id, entry])).values()), [initial, propertyId, cache]);
    return [entries, remember];
}

export default function PmsDirectoryPicker({ propertyId, kind = 'organizations', label, value, onChange, onResolve, initialOptions = EMPTY, placeholder = 'Bitte auswählen', activeOnly = true, masterOnly = false, excludeIds = EMPTY, required = false, disabled = false }) {
    return <DirectoryPicker key={`${propertyId}:${kind}`} {...{ propertyId, kind, label, value, onChange, onResolve, initialOptions, placeholder, activeOnly, masterOnly, excludeIds, required, disabled }} />;
}
function DirectoryPicker({ propertyId, kind, label, value, onChange, onResolve, initialOptions, placeholder, activeOnly, masterOnly, excludeIds, required, disabled }) {
    const [input, setInput] = useState(''); const [query, setQuery] = useState(''); const [page, setPage] = useState(0); const [opened, setOpened] = useState(false);
    const [result, setResult] = useState(null); const [selected, setSelected] = useState(null); const [error, setError] = useState(''); const [loading, setLoading] = useState(false);
    const resolveRef = useRef(onResolve); resolveRef.current = onResolve;
    const base = `/api/pms/properties/${propertyId}/directory/${kind}`;
    useEffect(() => {
        if (!opened || !propertyId) return; let active = true; setLoading(true); setError('');
        api.get(base, { params: { page, size: 25, query, activeOnly, masterOnly } }).then(({ data }) => {
            if (!active) return; if (!data || !Array.isArray(data.items)) throw new Error('Invalid directory'); setResult(data);
        }).catch((error) => { if (active) setError(errorText(error)); }).finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [base, propertyId, page, query, opened, activeOnly, masterOnly]);
    useEffect(() => {
        if (!value) { setSelected(null); return; }
        const existing = [...initialOptions, ...(result?.items || [])].find((entry) => String(entry.id) === String(value));
        if (existing) { setSelected(existing); return; }
        if (selected && String(selected.id) === String(value)) return;
        let active = true;
        api.get(`${base}/${value}`).then(({ data }) => { if (active && String(data?.id) === String(value)) { setSelected(data); resolveRef.current?.(data); } }).catch((error) => { if (active) setError(errorText(error)); });
        return () => { active = false; };
    }, [base, value, initialOptions, result]);
    const options = new Map();
    (result?.items || initialOptions).filter((entry) => (!activeOnly || entry.active !== false) && (!masterOnly || entry.masterRecord) && !excludeIds.some((id) => String(id) === String(entry.id))).forEach((entry) => options.set(String(entry.id), entry));
    if (selected && String(selected.id) === String(value)) options.set(String(selected.id), selected);
    return <div className="pms-directory-picker">
        <label>{label}<select value={value || ''} required={required} disabled={disabled || loading} onChange={(event) => { const entry = options.get(event.target.value); setSelected(entry || null); if (entry) resolveRef.current?.(entry); onChange(event.target.value, entry || null); }}><option value="">{placeholder}</option>{Array.from(options.values()).map((entry) => <option key={entry.id} value={entry.id}>{optionLabel(entry)}</option>)}</select></label>
        <div className="pms-directory-search"><input aria-label={`${label} suchen`} placeholder="Alle Einträge durchsuchen" maxLength={120} value={input} disabled={disabled} onChange={(event) => setInput(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); setQuery(input); setPage(0); setOpened(true); } }} /><button type="button" disabled={disabled || loading} onClick={() => { setQuery(input); setPage(0); setOpened(true); }}>Suchen</button></div>
        {result && <div className="pms-directory-search"><small>{result.totalElements} Treffer · Seite {page + 1}</small><button type="button" aria-label={`${label}: vorherige Seite`} disabled={disabled || loading || page === 0} onClick={() => setPage(page - 1)}>Zurück</button><button type="button" aria-label={`${label}: nächste Seite`} disabled={disabled || loading || !result.hasNext} onClick={() => setPage(page + 1)}>Weiter</button></div>}
        {error && <small role="alert">{error}</small>}
    </div>;
}

export function PmsDirectoryBrowser({ propertyId, kind, refreshKey, initialItems = EMPTY, renderItems, children }) {
    return <DirectoryBrowser key={`${propertyId}:${kind}`} {...{ propertyId, kind, refreshKey, initialItems }} renderItems={renderItems || children} />;
}
function DirectoryBrowser({ propertyId, kind, refreshKey, initialItems, renderItems }) {
    const [input, setInput] = useState(''); const [query, setQuery] = useState(''); const [page, setPage] = useState(0);
    const [result, setResult] = useState(null); const [error, setError] = useState(''); const [loading, setLoading] = useState(false);
    useEffect(() => {
        let active = true; setLoading(true); setError('');
        api.get(`/api/pms/properties/${propertyId}/directory/${kind}`, { params: { page, size: 25, query } }).then(({ data }) => { if (active) { if (!Array.isArray(data?.items)) throw new Error('Invalid directory'); setResult(data); } }).catch((error) => { if (active) setError(errorText(error)); }).finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [propertyId, kind, page, query, refreshKey]);
    return <><div className="pms-directory-search"><input aria-label={kind === 'organizations' ? 'Firmenverzeichnis durchsuchen' : 'Gruppenverzeichnis durchsuchen'} value={input} maxLength={120} onChange={(event) => setInput(event.target.value)} placeholder="Name oder Referenz suchen" onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); setQuery(input); setPage(0); } }} /><button type="button" disabled={loading} onClick={() => { setQuery(input); setPage(0); }}>Verzeichnis durchsuchen</button></div>
        {error && <p role="alert">{error}</p>}{renderItems(result?.items || initialItems)}
        <div className="pms-directory-search"><span>{result?.totalElements ?? initialItems.length} Einträge · Seite {page + 1}</span><button type="button" disabled={loading || !page} onClick={() => setPage(page - 1)}>Vorherige Verzeichnisseite</button><button type="button" disabled={loading || !result?.hasNext} onClick={() => setPage(page + 1)}>Nächste Verzeichnisseite</button></div></>;
}
