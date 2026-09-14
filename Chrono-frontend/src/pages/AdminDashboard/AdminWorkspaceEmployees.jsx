import { useEffect, useMemo, useState } from 'react';
import { getUserDisplayName } from '../../utils/userDisplay';
import { formatLocalDate } from '../../utils/dateUtils';
import { isHourlyEmploymentModel, minutesToHHMM } from './adminDashboardUtils';
import { groupWorkspaceCorrections, isWorkspacePending, workspaceAbsences } from './adminWorkspaceData';
import './AdminWorkspaceEmployees.css';

const EMPTY = [];
const PAGE_SIZE = 6;
const dayLabel = value => value ? value.split('-').reverse().join('.') : '–';

export default function AdminWorkspaceEmployees({
    t, users = EMPTY, balances = EMPTY, vacations = EMPTY, corrections = EMPTY, sickLeaves = EMPTY,
    issueRows = null, loading = false, loadError = '', canManage = false,
    onOpenEmployee, onOpenTime, onOpenRequests, onCreateVacation,
}) {
    const [query, setQuery] = useState('');
    const [filter, setFilter] = useState('all');
    const [sort, setSort] = useState('attention');
    const [page, setPage] = useState(0);
    const today = formatLocalDate(new Date());
    const reliable = !loading && !loadError;
    const rows = useMemo(() => {
        const byBalance = new Map(balances.map(row => [row.username, row.trackingBalance]));
        const byIssue = new Map((issueRows || EMPTY).map(row => [row.username, row]));
        const requests = new Map();
        [...vacations.filter(isWorkspacePending), ...groupWorkspaceCorrections(corrections)].forEach(row => {
            requests.set(row.username, (requests.get(row.username) || 0) + 1);
        });
        const absences = workspaceAbsences(vacations, sickLeaves);
        return users.filter(user => user?.username).map(user => {
            const balance = isHourlyEmploymentModel(user) ? null : byBalance.get(user.username);
            const issue = byIssue.get(user.username);
            const activeAbsences = absences.filter(row => row.username === user.username && row.startIso <= today && row.endIso >= today);
            const nextAbsence = absences.find(row => row.username === user.username && row.startIso > today);
            const requestCount = requests.get(user.username) || 0;
            return { user, name: getUserDisplayName(user), balance, issue, requestCount, activeAbsences, nextAbsence, needsAttention: Boolean(issue || requestCount) };
        });
    }, [users, balances, vacations, corrections, sickLeaves, issueRows, today]);
    const filtered = useMemo(() => rows.filter(row => {
        const text = `${row.name} ${row.user.username} ${row.user.departmentName || ''}`.toLocaleLowerCase();
        return text.includes(query.trim().toLocaleLowerCase()) && (filter === 'all'
            || filter === 'attention' && row.needsAttention
            || filter === 'absent' && row.activeAbsences.length > 0
            || filter === 'negative' && Number.isFinite(row.balance) && row.balance < 0);
    }).sort((a, b) => {
        if (sort === 'attention') {
            const priority = Number(Boolean(b.issue)) - Number(Boolean(a.issue)) || b.requestCount - a.requestCount;
            if (priority) return priority;
        }
        if (sort === 'balance') {
            const difference = (Number.isFinite(a.balance) ? a.balance : Infinity) - (Number.isFinite(b.balance) ? b.balance : Infinity);
            if (difference) return difference;
        }
        return a.name.localeCompare(b.name, 'de');
    }), [rows, query, filter, sort]);
    useEffect(() => { setPage(0); }, [query, filter, sort]);
    const pages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
    const safePage = Math.min(page, pages - 1);
    const shown = filtered.slice(safePage * PAGE_SIZE, (safePage + 1) * PAGE_SIZE);
    const counts = { all: rows.length, attention: issueRows === null ? null : rows.filter(row => row.needsAttention).length, absent: rows.filter(row => row.activeAbsences.length).length, negative: rows.filter(row => Number.isFinite(row.balance) && row.balance < 0).length };
    const filters = [['all', t('all', 'Alle')], ['attention', t('adminWorkspace.employeeAttention', 'Handlungsbedarf')], ['absent', t('adminWorkspace.absentToday', 'Heute abwesend')], ['negative', t('adminWorkspace.employeeNegative', 'Minussaldo')]];

    return <section className="aw-people" aria-busy={loading}>
        <div className="aw-people-heading"><h2>{t('adminWorkspace.employeeDesk', 'Alles zur Person, direkt zur Aktion')}</h2>
            <p>{t('adminWorkspace.employeePurpose', 'Zeitprobleme, Anträge und Abwesenheiten zusammen sehen – ohne zwischen Bereichen zu suchen.')}</p></div>
        <div className="aw-people-toolbar">
            <label><span>{t('adminWorkspace.searchEmployees', 'Mitarbeitende suchen')}</span><input type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder={t('adminWorkspace.searchEmployeeTeam', 'Name, Benutzername oder Team')} /></label>
            <label><span>{t('sort', 'Sortierung')}</span><select value={sort} onChange={event => setSort(event.target.value)}>
                <option value="attention">{t('adminWorkspace.attentionFirst', 'Handlungsbedarf zuerst')}</option><option value="name">{t('adminWorkspace.nameOrder', 'Name A–Z')}</option><option value="balance">{t('adminWorkspace.lowestBalance', 'Niedrigster Saldo zuerst')}</option>
            </select></label>
        </div>
        <div className="aw-people-filters" role="group" aria-label={t('adminWorkspace.filterEmployees', 'Mitarbeitende filtern')}>
            {filters.map(([key, label]) => <button type="button" key={key} aria-pressed={filter === key} onClick={() => { setFilter(key); setPage(0); }}>{label}<span>{reliable && counts[key] !== null ? counts[key] : '–'}</span></button>)}
        </div>
        {loadError && <p className="aw-people-warning" role="status">{t('adminWorkspace.employeeStale', 'Die Daten sind nicht vollständig aktualisiert. Angezeigte Angaben können veraltet sein.')}</p>}
        {shown.length ? <ul className="aw-people-list">{shown.map(row => <li key={row.user.username} data-employee={row.user.username}>
            <div className="aw-people-identity"><span className="aw-avatar" aria-hidden="true">{row.name.split(/\s+/).slice(0, 2).map(word => word[0]).join('')}</span><div>
                <button type="button" className="aw-task-name" onClick={() => onOpenEmployee?.(row.user.username)}>{row.name}</button><small>{row.user.departmentName || row.user.username}</small>
            </div></div>
            <div className="aw-people-state">
                <div className="aw-people-badges">
                    {row.issue ? <button type="button" className="is-problem" onClick={() => onOpenTime?.(row.user.username, row.issue.dateIso)}>{t('adminWorkspace.timeReview', 'Zeitprüfung')} · {row.issue.issues?.length || 1}</button>
                        : <span>{issueRows === null ? t('adminWorkspace.employeeCalculating', 'Zeitprüfung lädt …') : t('adminWorkspace.employeeNoIssue', 'Keine Zeitprobleme')}</span>}
                    {row.requestCount > 0 && <button type="button" className="is-request" onClick={() => onOpenRequests?.(row.user.username)}>{row.requestCount} {row.requestCount === 1 ? t('adminWorkspace.request', 'Antrag') : t('adminWorkspace.requests', 'Anträge')}</button>}
                </div>
                <small>{row.activeAbsences.length ? row.activeAbsences.map(absence => `${absence.kind === 'sick' ? t('sick', 'Krank') : absence.usesOvertime ? t('adminWorkspace.overtimeLeave', 'Überstundenfrei') : t('adminWorkspace.vacation', 'Urlaub')}${absence.halfDay ? ' · ½ Tag' : ''} · ${t('until', 'bis')} ${dayLabel(absence.endIso)}`).join(' / ')
                    : row.nextAbsence ? `${t('adminWorkspace.nextAbsence', 'Nächste Abwesenheit')}: ${dayLabel(row.nextAbsence.startIso)}` : t('adminWorkspace.noEmployeeAbsence', 'Keine Abwesenheit eingetragen')}</small>
            </div>
            <div className={`aw-people-balance${row.balance < 0 ? ' is-negative' : ''}`}><strong>{Number.isFinite(row.balance) ? minutesToHHMM(row.balance) : '–'}</strong><small>{t('adminWorkspace.totalBalance', 'Aktueller Gesamtstand')}</small></div>
            <div className="aw-people-actions"><button type="button" onClick={() => onOpenTime?.(row.user.username, row.issue?.dateIso)}>{t('adminWorkspace.employeeTimes', 'Zeiten')} ↗</button>
                <button type="button" disabled={!canManage || !reliable} onClick={() => onCreateVacation?.(row.user.username)}>+ {t('adminWorkspace.vacation', 'Urlaub')}</button></div>
        </li>)}</ul> : <p className="aw-empty" role="status">{loading ? t('loading', 'Wird geladen …') : t('adminWorkspace.noEmployees', 'Keine passenden Mitarbeitenden gefunden.')}</p>}
        <footer className="aw-people-footer"><span>{filtered.length ? `${safePage * PAGE_SIZE + 1}–${Math.min((safePage + 1) * PAGE_SIZE, filtered.length)}` : '0'} / {filtered.length} {t('adminWorkspace.employees', 'Mitarbeitende')}</span>
            <div><button type="button" disabled={safePage === 0} onClick={() => setPage(safePage - 1)} aria-label={t('previousPage', 'Vorherige Seite')}>←</button><span>{safePage + 1} / {pages}</span><button type="button" disabled={safePage + 1 >= pages} onClick={() => setPage(safePage + 1)} aria-label={t('nextPage', 'Nächste Seite')}>→</button></div>
        </footer>
    </section>;
}
