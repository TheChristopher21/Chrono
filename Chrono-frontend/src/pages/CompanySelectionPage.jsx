import { useEffect, useMemo, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import { useTranslation } from '../context/LanguageContext.jsx';
import Navbar from '../components/Navbar.jsx';
import api from '../utils/api.js';
import { leaveCompanyScope, openCompanyScope } from '../utils/companyScope.js';
import '../styles/CompanySelectionPage.css';

export function CompanyScopeGate({ children }) {
    const { currentUser } = useAuth();
    return currentUser?.selectedCompanyId ? children : <Navigate to="/admin/firmenauswahl" replace />;
}

export default function CompanySelectionPage() {
    const { currentUser } = useAuth();
    const { t } = useTranslation();
    const [companies, setCompanies] = useState([]);
    const [search, setSearch] = useState('');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);
    const [revision, setRevision] = useState(0);
    useEffect(() => {
        const controller = new AbortController();
        setLoading(true); setError(false);
        api.get('/api/superadmin/companies', { signal: controller.signal })
            .then(({ data }) => { if (!controller.signal.aborted) setCompanies(Array.isArray(data) ? data : []); })
            .catch(() => { if (!controller.signal.aborted) setError(true); })
            .finally(() => { if (!controller.signal.aborted) setLoading(false); });
        return () => controller.abort();
    }, [revision]);
    const visible = useMemo(() => companies.filter(company => `${company.name} ${company.city || ''}`.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase()))
        .sort((a, b) => a.name.localeCompare(b.name)), [companies, search]);
    return <div className="company-selection-page">
        <Navbar />
        <main className="company-selection">
            <header><div><p>{t('companyScope.superadmin', 'Superadmin · Firmenansicht')}</p>
                <h1>{t('companyScope.choose', 'Firma auswählen')}</h1>
                <p>{t('companyScope.description', 'Öffne eine Firma, um ihre Mitarbeitenden, Anträge, Zeiten und Module zu sehen.')}</p></div>
                <button type="button" onClick={leaveCompanyScope}>{t('companyScope.management', 'Zur Firmenverwaltung')}</button></header>
            <label className="company-selection-search">{t('companyScope.search', 'Firma suchen')}
                <input type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder={t('companyScope.searchHint', 'Name oder Ort')} /></label>
            {loading ? <p role="status">{t('loading', 'Wird geladen…')}</p> : error ? <div role="alert"><p>{t('companyScope.loadError', 'Die Firmen konnten nicht geladen werden.')}</p>
                <button type="button" onClick={() => setRevision(value => value + 1)}>{t('retry', 'Erneut versuchen')}</button></div>
                : <><p className="company-selection-count">{visible.length} {t('companyScope.companies', 'Firmen')}</p>
                    <div className="company-selection-grid">{visible.map(company => <button type="button" key={company.id} className="company-selection-card"
                        onClick={() => openCompanyScope(company, currentUser)}>
                        <span className="company-selection-initial" aria-hidden="true">{company.name?.slice(0, 1)}</span>
                        <strong>{company.name}</strong><span>{company.city || '–'}</span>
                        <span>{company.userCount ?? 0} {t('companyScope.employees', 'Mitarbeitende')}
                            {company.active === false && ` · ${t('companyScope.inactive', 'Inaktiv')}`}</span>
                        <span className="company-selection-open">{t('companyScope.open', 'Firma ansehen')} →</span>
                    </button>)}</div>
                    {!visible.length && <p>{t('companyScope.noResults', 'Keine Firmen gefunden.')}</p>}</>}
        </main>
    </div>;
}
