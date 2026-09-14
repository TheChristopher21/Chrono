import { useMemo, useState } from 'react';
import { getUserDisplayName } from '../../utils/userDisplay';
import { isHourlyEmploymentModel, minutesToHHMM } from './adminDashboardUtils';

export default function AdminWorkspaceEmployees({ t, users, balances, loading, onOpenEmployee }) {
    const [query, setQuery] = useState('');
    const visibleUsers = useMemo(() => users.filter(user => user?.username)
        .filter(user => `${getUserDisplayName(user)} ${user.username}`.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase()))
        .sort((a, b) => getUserDisplayName(a).localeCompare(getUserDisplayName(b), 'de')), [users, query]);
    const byUsername = new Map(balances.map(row => [row.username, row]));
    return <section className="aw-card aw-employees" aria-busy={loading}>
        <div className="aw-card-heading">
            <div><h2>{t('adminWorkspace.employees', 'Mitarbeitende')}</h2><p>{t('adminWorkspace.employeeCount', '{{count}} im ausgewählten Team', { count: visibleUsers.length })}</p></div>
            <label className="aw-employee-search"><span>{t('adminWorkspace.searchEmployees', 'Mitarbeitende suchen')}</span>
                <input type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder={t('adminWorkspace.searchName', 'Name oder Benutzername')} />
            </label>
        </div>
        {visibleUsers.length === 0 ? <p className="aw-empty" role="status">{loading ? t('loading', 'Wird geladen …') : t('adminWorkspace.noEmployees', 'Keine passenden Mitarbeitenden gefunden.')}</p>
            : <ul className="aw-employee-list">{visibleUsers.map(user => {
                const balance = byUsername.get(user.username)?.trackingBalance;
                const name = getUserDisplayName(user);
                return <li key={user.username}><button type="button" onClick={() => onOpenEmployee(user.username)}>
                    <span className="aw-avatar" aria-hidden="true">{name.split(/\s+/).slice(0, 2).map(part => part[0]).join('')}</span>
                    <span className="aw-employee-name"><strong>{name}</strong><small>{user.departmentName || user.username}</small></span>
                    {!isHourlyEmploymentModel(user) && Number.isFinite(balance) && <span className={`aw-employee-balance ${balance < 0 ? 'is-negative' : ''}`}>{minutesToHHMM(balance)} <small>{t('adminWorkspace.balance', 'Saldo')}</small></span>}
                    <span aria-hidden="true">→</span>
                </button></li>;
            })}</ul>}
    </section>;
}
