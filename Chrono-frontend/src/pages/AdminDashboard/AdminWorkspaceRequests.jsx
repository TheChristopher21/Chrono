import { useEffect, useId, useMemo, useRef, useState } from 'react';
import ModalOverlay from '../../components/ModalOverlay';
import api from '../../utils/api';
import { ACCESS_MANAGE, hasPageAccess } from '../../utils/pageAccess';
import { getUserDisplayName, getUserSearchText } from '../../utils/userDisplay';
import { formatRequestAdmin } from '../../utils/correctionActor';
import { formatDate, formatTime, minutesToHHMM } from './adminDashboardUtils';
import { workspaceAbsences, workspaceDate } from './adminWorkspaceData';
import AdminWorkspaceVacationBalance from './AdminWorkspaceVacationBalance';
import { buildWorkspaceRequestRows, matchesRequestDate, requestEntryKey, requestStatus, sortWorkspaceRequestRows } from './adminWorkspaceRequestsData';
import './AdminWorkspaceRequests.css';

const EMPTY = [];
const fallbackTranslation = (_key, fallback) => fallback;
const displayDate = value => {
    const date = workspaceDate(value);
    return date ? formatDate(new Date(`${date}T12:00:00`)) : '–';
};
const displayPeriod = (start, end) => start === (end || start) ? displayDate(start) : `${displayDate(start)} – ${displayDate(end)}`;
const errorMessage = (error, fallback) => {
    const message = error?.response?.data?.message || error?.response?.data || error?.message;
    return typeof message === 'string' && message.trim() ? message : fallback;
};

/** Decision callbacks reject failed writes. A refresh failure must not turn a successful write into a retry. */
export default function AdminWorkspaceRequests({
    t = fallbackTranslation, currentUser, users = EMPTY, allVacations = EMPTY, allCorrections = EMPTY,
    allSickLeaves = EMPTY, loading = false, loadError = '', onRetry, onReload,
    onApproveVacation, onDenyVacation, onApproveCorrection, onDenyCorrection,
    onOpenEmployee, onOpenInTimeReview, vacationOpenSignal = 0, correctionOpenSignal = 0,
    focusedRequest, employeeFilter = '', onEmployeeFilterChange,
}) {
    const id = useId();
    const [type, setType] = useState('all');
    const [status, setStatus] = useState('pending');
    const [search, setSearch] = useState('');
    const [dateBasis, setDateBasis] = useState('period');
    const [dateFilter, setDateFilter] = useState('');
    const [direction, setDirection] = useState('asc');
    const [sortBy, setSortBy] = useState('date');
    const [advancedOpen, setAdvancedOpen] = useState(false);
    const [selectedKey, setSelectedKey] = useState(null);
    const [notes, setNotes] = useState({});
    const [decisions, setDecisions] = useState({});
    const [deletedIds, setDeletedIds] = useState(() => new Set());
    const [savingKey, setSavingKey] = useState(null);
    const [decisionError, setDecisionError] = useState(null);
    const [feedback, setFeedback] = useState('');
    const [refreshError, setRefreshError] = useState('');
    const [deleteTarget, setDeleteTarget] = useState(null);
    const [deleteError, setDeleteError] = useState('');
    const busy = useRef(false);
    const appliedFocus = useRef(null);
    const appliedSignals = useRef({ vacation: 0, correction: 0 });
    const rowButtons = useRef(new Map());
    const detailRef = useRef(null);
    const cancelDeleteRef = useRef(null);
    const deleteOpener = useRef(null);
    const canManage = hasPageAccess(currentUser, 'adminDashboard', ACCESS_MANAGE);
    const writesDisabled = !canManage || loading || Boolean(loadError) || Boolean(savingKey);
    const rows = useMemo(() => buildWorkspaceRequestRows(allVacations, allCorrections, decisions, deletedIds), [allVacations, allCorrections, decisions, deletedIds]);
    const nameOf = username => getUserDisplayName(username, users, username || t('unknown', 'Unbekannt'));
    const typeLabel = requestType => requestType === 'vacation' ? t('adminWorkspace.vacation', 'Urlaub') : t('adminWorkspace.correction', 'Zeitkorrektur');
    const statusLabel = requestState => ({ pending: t('adminWorkspaceRequests.open', 'Offen'), approved: t('approved', 'Genehmigt'),
        denied: t('denied', 'Abgelehnt'), mixed: t('adminWorkspaceRequests.mixed', 'Gemischt erledigt') })[requestState];
    const people = useMemo(() => [...new Set([...users.map(user => user.username), ...rows.map(row => row.username), employeeFilter].filter(Boolean))]
        .sort((a, b) => getUserDisplayName(a, users).localeCompare(getUserDisplayName(b, users), 'de')), [users, rows, employeeFilter]);
    const visibleRows = useMemo(() => sortWorkspaceRequestRows(rows.filter(row =>
        (type === 'all' || row.type === type)
        && (status === 'all' || (status === 'processed' ? !row.pendingEntries.length : row.entries.some(entry => requestStatus(entry) === status)))
        && (!employeeFilter || row.username === employeeFilter)
        && (!search || getUserSearchText(row.username, users).includes(search.trim().toLowerCase()))
        && matchesRequestDate(row, dateFilter, dateBasis)), dateBasis, direction, sortBy), [rows, type, status, employeeFilter, search, users, dateFilter, dateBasis, direction, sortBy]);
    const selected = visibleRows.find(row => row.key === selectedKey) || visibleRows[0] || null;
    const approvalScopeUnknown = selected?.type === 'correction' && !selected.approvalDay;

    useEffect(() => {
        if (selected?.key !== selectedKey) setSelectedKey(selected?.key || null);
    }, [selected?.key, selectedKey]);
    const identity = `${currentUser?.companyId || currentUser?.company?.id || ''}:${currentUser?.id || currentUser?.username || ''}`;
    const previousIdentity = useRef(identity);
    useEffect(() => {
        if (previousIdentity.current === identity) return;
        previousIdentity.current = identity;
        setDecisions({}); setDeletedIds(new Set()); setNotes({}); setDecisionError(null); setFeedback(''); setRefreshError('');
    }, [identity]);
    useEffect(() => {
        const settledKeys = [...allVacations.map(entry => ['vacation', entry]), ...allCorrections.map(entry => ['correction', entry])]
            .filter(([, entry]) => entry.approved || entry.denied).map(([kind, entry]) => requestEntryKey(kind, entry.id));
        setDecisions(previous => {
            if (!settledKeys.some(key => previous[key])) return previous;
            const next = { ...previous }; settledKeys.forEach(key => { delete next[key]; }); return next;
        });
    }, [allVacations, allCorrections]);
    useEffect(() => {
        if (!vacationOpenSignal || appliedSignals.current.vacation === vacationOpenSignal) return;
        appliedSignals.current.vacation = vacationOpenSignal;
        setType('vacation'); setStatus('pending'); setSearch(''); setDateFilter(''); onEmployeeFilterChange?.('');
    }, [vacationOpenSignal, onEmployeeFilterChange]);
    useEffect(() => {
        if (!correctionOpenSignal || appliedSignals.current.correction === correctionOpenSignal) return;
        appliedSignals.current.correction = correctionOpenSignal;
        setType('correction'); setStatus('pending'); setSearch(''); setDateFilter(''); onEmployeeFilterChange?.('');
    }, [correctionOpenSignal, onEmployeeFilterChange]);
    const focusedKey = focusedRequest?.id != null && ['vacation', 'correction'].includes(focusedRequest?.type)
        ? requestEntryKey(focusedRequest.type, focusedRequest.id) : null;
    useEffect(() => {
        if (!focusedKey) { appliedFocus.current = null; return; }
        if (appliedFocus.current === focusedKey) return;
        const target = rows.find(row => row.entries.some(entry => requestEntryKey(row.type, entry.id) === focusedKey));
        if (!target) return;
        appliedFocus.current = focusedKey;
        setType(target.type); setStatus('all'); setSearch(''); setDateFilter(''); setSelectedKey(target.key);
        if (employeeFilter !== target.username) onEmployeeFilterChange?.(target.username);
    }, [focusedKey, rows, employeeFilter, onEmployeeFilterChange]);
    useEffect(() => {
        if (!focusedKey || !selected) return;
        if (selected.entries.some(entry => requestEntryKey(selected.type, entry.id) === focusedKey)) {
            rowButtons.current.get(selected.key)?.scrollIntoView?.({ block: 'nearest' });
        }
    }, [focusedKey, selected?.key]);
    useEffect(() => { if (deleteTarget) cancelDeleteRef.current?.focus(); }, [deleteTarget]);

    const reloadAfterWrite = async () => {
        if (!onReload) return;
        try { await onReload(); }
        catch { setRefreshError(t('adminWorkspaceRequests.refreshFailed', 'Gespeichert. Die Liste konnte noch nicht aktualisiert werden.')); }
    };
    const decide = async approve => {
        if (!selected || writesDisabled || busy.current) return;
        const row = selected;
        if (approve && row.type === 'correction' && !row.approvalDay) return;
        const callback = row.type === 'vacation' ? approve ? onApproveVacation : onDenyVacation : approve ? onApproveCorrection : onDenyCorrection;
        if (!callback || !row.pendingEntries.length) return;
        const comment = (notes[row.key] || '').trim();
        busy.current = true; setSavingKey(row.key); setDecisionError(null); setFeedback(''); setRefreshError('');
        try {
            const approvesWholeDay = approve && row.type === 'correction';
            const writes = approvesWholeDay ? row.pendingEntries.slice(0, 1) : row.pendingEntries;
            const results = await Promise.allSettled(writes.map(entry => Promise.resolve().then(() => callback(entry.id, comment))));
            const successes = approvesWholeDay ? results[0].status === 'fulfilled' ? row.pendingEntries : []
                : writes.filter((_, index) => results[index].status === 'fulfilled');
            setDecisions(previous => ({ ...previous, ...Object.fromEntries(successes.map(entry => [requestEntryKey(row.type, entry.id), { approved: approve, comment }])) }));
            const failures = results.filter(result => result.status === 'rejected');
            if (failures.length) {
                const prefix = successes.length ? `${successes.length} ${successes.length === 1
                    ? t('adminWorkspaceRequests.savedChange', 'Änderung gespeichert') : t('adminWorkspaceRequests.savedChanges', 'Änderungen gespeichert')}. ` : '';
                setDecisionError({ key: row.key, message: prefix + errorMessage(failures[0].reason, t('adminWorkspaceRequests.saveFailed', 'Die Entscheidung konnte nicht gespeichert werden.')) });
            } else {
                setFeedback(`${nameOf(row.username)}: ${approve ? t('adminWorkspaceRequests.approvedFeedback', 'Antrag genehmigt.') : t('adminWorkspaceRequests.deniedFeedback', 'Antrag abgelehnt.')}`);
                setNotes(previous => { const next = { ...previous }; delete next[row.key]; return next; });
            }
            if (successes.length) await reloadAfterWrite();
        } finally { busy.current = false; setSavingKey(null); }
    };
    const closeDelete = () => {
        if (busy.current) return;
        setDeleteTarget(null); setDeleteError(''); deleteOpener.current?.focus();
    };
    const deleteVacation = async () => {
        if (!deleteTarget || writesDisabled || busy.current) return;
        if (!currentUser?.username) { setDeleteError(t('errors.notLoggedIn', 'Admin nicht eingeloggt oder Benutzername fehlt.')); return; }
        busy.current = true; setSavingKey(requestEntryKey('vacation', deleteTarget.id)); setDeleteError(''); setFeedback(''); setRefreshError('');
        try {
            await api.delete(`/api/vacation/${deleteTarget.id}`, { params: { adminUsername: currentUser.username } });
            setDeletedIds(previous => new Set([...previous, String(deleteTarget.id)]));
            setFeedback(t('adminVacation.delete.success', 'Urlaubsantrag erfolgreich gelöscht.')); setDeleteTarget(null);
            await reloadAfterWrite();
        } catch (error) { setDeleteError(errorMessage(error, t('adminVacation.delete.error', 'Fehler beim Löschen des Urlaubsantrags.'))); }
        finally { busy.current = false; setSavingKey(null); }
    };
    const openTime = row => onOpenInTimeReview?.({ type: row.type, id: row.id, username: row.username,
        dateIso: row.startDate || row.requestDate, startDate: row.startDate, endDate: row.endDate,
        requestDate: row.requestDate, ...(row.type === 'correction' ? { entries: row.entries } : {}) });
    const overlaps = useMemo(() => {
        if (selected?.type !== 'vacation' || !selected.startDate || !selected.endDate) return [];
        const team = users.find(user => user.username === selected.username)?.departmentName?.trim();
        if (!team) return [];
        const teamNames = new Set(users.filter(user => user.departmentName?.trim() === team).map(user => user.username));
        return workspaceAbsences(allVacations, allSickLeaves).filter(absence => teamNames.has(absence.username)
            && !(absence.kind === 'vacation' && String(absence.id) === String(selected.id))
            && absence.startIso <= selected.endDate && absence.endIso >= selected.startDate);
    }, [selected, users, allVacations, allSickLeaves]);
    const rowDateLabel = row => dateBasis === 'request'
        ? row.requestDate ? displayDate(row.requestDate) : t('adminWorkspaceRequests.dateMissing', 'Nicht erfasst')
        : displayPeriod(row.startDate, row.endDate);
    const resetFilters = () => { setType('all'); setStatus('pending'); setSearch(''); setDateFilter(''); onEmployeeFilterChange?.(''); };
    const renderPunch = (entry, original) => {
        const timestamp = original ? entry.originalTimestamp : entry.desiredTimestamp;
        const punchType = original ? entry.originalPunchType : entry.desiredPunchType;
        return <div className="awr-punch"><span>{original ? t('adminDashboard.originalTimeLabel', 'Gestempelt') : t('adminDashboard.requestedTimeLabel', 'Beantragt')}</span>
            <strong>{timestamp ? `${displayDate(timestamp)} · ${formatTime(timestamp)}` : original ? t('adminDashboard.noOriginalTimeLabel', 'Kein ursprünglicher Stempel') : t('adminWorkspaceRequests.noTime', 'Keine Zeit hinterlegt')}</strong>
            {punchType && <small>{t(`punchTypes.${punchType}`, punchType)}</small>}</div>;
    };

    return <section className="aw-requests" aria-label={t('adminWorkspaceRequests.title', 'Antragscenter')} aria-busy={loading}>
        <div className="awr-heading"><div><h2>{t('adminWorkspaceRequests.title', 'Antragscenter')}</h2>{employeeFilter && <p>{nameOf(employeeFilter)}</p>}</div>
            <span className="awr-total">{(loading || loadError) && !rows.length ? '–' : rows.filter(row => row.pendingEntries.length).length} {t('adminWorkspaceRequests.open', 'Offen')}</span></div>
        <div className="awr-filters">
            <label>{t('adminWorkspaceRequests.type', 'Antragsart')}<select value={type} onChange={event => setType(event.target.value)}><option value="all">{t('all', 'Alle')}</option><option value="vacation">{typeLabel('vacation')}</option><option value="correction">{typeLabel('correction')}</option></select></label>
            <label>{t('status', 'Status')}<select value={status} onChange={event => setStatus(event.target.value)}><option value="pending">{statusLabel('pending')}</option><option value="approved">{statusLabel('approved')}</option><option value="denied">{statusLabel('denied')}</option><option value="processed">{t('adminWorkspaceRequests.processed', 'Erledigt')}</option><option value="all">{t('all', 'Alle')}</option></select></label>
            <label className="awr-person">{t('adminWorkspaceRequests.person', 'Person')}<select value={employeeFilter} disabled={!onEmployeeFilterChange} onChange={event => onEmployeeFilterChange?.(event.target.value)}><option value="">{t('adminWorkspaceRequests.allPeople', 'Alle Personen')}</option>{people.map(username => <option value={username} key={username}>{nameOf(username)}</option>)}</select></label>
            <label className="awr-search">{t('adminWorkspaceRequests.search', 'Namenssuche')}<input type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder={t('adminWorkspaceRequests.searchPlaceholder', 'Name oder Benutzername')} /></label>
        </div>
        <div className="awr-toolbar">
            <label>{t('adminWorkspaceRequests.order', 'Reihenfolge')}<select value={direction} onChange={event => setDirection(event.target.value)}><option value="asc">{sortBy === 'date' ? t('adminWorkspaceRequests.oldest', 'Ältestes Datum zuerst') : t('adminWorkspaceRequests.ascending', 'Aufsteigend')}</option><option value="desc">{sortBy === 'date' ? t('adminWorkspaceRequests.newest', 'Neuestes Datum zuerst') : t('adminWorkspaceRequests.descending', 'Absteigend')}</option></select></label>
            <button type="button" className="awr-more-filters" aria-expanded={advancedOpen} aria-controls={`${id}-advanced-filters`} onClick={() => setAdvancedOpen(value => !value)}>{t('adminWorkspaceRequests.moreFilters', 'Weitere Filter')}{dateFilter || dateBasis !== 'period' || sortBy !== 'date' ? ` · ${t('adminWorkspaceRequests.filterActive', 'aktiv')}` : ''} <span aria-hidden="true">{advancedOpen ? '−' : '+'}</span></button>
        </div>
        {advancedOpen && <div className="awr-advanced" id={`${id}-advanced-filters`}>
            <div className="awr-date-controls">
                <label>{t('adminWorkspaceRequests.sortBy', 'Sortieren nach')}<select value={sortBy} onChange={event => setSortBy(event.target.value)}><option value="date">{t('date', 'Datum')}</option><option value="username">{t('username', 'Benutzername')}</option><option value="status">{t('status', 'Status')}</option></select></label>
                <label>{t('adminWorkspaceRequests.dateBasis', 'Datumsbasis')}<select value={dateBasis} onChange={event => setDateBasis(event.target.value)}><option value="period">{t('adminWorkspaceRequests.periodBasis', 'Zeitraum / Korrekturtag')}</option><option value="request">{t('adminWorkspaceRequests.correctionDate', 'Erfasster Korrekturtag')}</option></select></label>
                <label>{t('adminWorkspaceRequests.onDate', 'Datum eingrenzen')}<input type="date" value={dateFilter} onChange={event => setDateFilter(event.target.value)} /></label>
                <button type="button" className="awr-link" onClick={() => { resetFilters(); setDateBasis('period'); setSortBy('date'); }}>{t('adminDashboard.resetFilters', 'Filter zurücksetzen')}</button>
            </div>
            <p className="awr-hint">{dateBasis === 'request' ? t('adminWorkspaceRequests.correctionDateHint', 'Im Korrekturantrag gewählter Arbeitstag, kein Zeitstempel des Eingangs. Bei Urlauben nicht erfasst; fehlende Daten stehen am Ende.') : t('adminWorkspaceRequests.periodHint', 'Urlaubsbeginn oder betroffener Korrekturtag. Datumfilter prüfen den ganzen Urlaubszeitraum.')} {t('adminWorkspaceRequests.groupHint', 'Zusammengehörige Korrekturen bleiben als vollständige Gruppe sichtbar.')}</p>
        </div>}
        {employeeFilter && onEmployeeFilterChange && <button type="button" className="awr-person-chip" onClick={() => onEmployeeFilterChange('')}>{nameOf(employeeFilter)} · {t('adminWorkspaceRequests.clearPerson', 'Personenfilter entfernen')} ×</button>}
        {(loadError || refreshError) && <div className="awr-error" role="alert"><p>{loadError || refreshError}</p>{(onRetry || onReload) && <button type="button" disabled={loading || Boolean(savingKey)} onClick={async () => { try { await (onRetry || onReload)(); setRefreshError(''); } catch { setRefreshError(t('adminWorkspaceRequests.reloadFailed', 'Die Liste konnte nicht aktualisiert werden.')); } }}>{t('retry', 'Erneut versuchen')}</button>}</div>}
        {feedback && <p className="awr-feedback" role="status">{feedback}</p>}
        <div className="awr-split">
            <div className="awr-list-panel"><div className="awr-list-heading"><strong>{visibleRows.length} {t('adminWorkspaceRequests.requests', 'Anträge')}</strong><span>{dateBasis === 'request' ? t('adminWorkspaceRequests.correctionDate', 'Erfasster Korrekturtag') : t('adminWorkspaceRequests.period', 'Zeitraum')}</span></div>
                {visibleRows.length ? <ul className="awr-list" aria-label={t('adminWorkspaceRequests.list', 'Antragsliste')}>{visibleRows.map(row => <li key={row.key} data-request-key={row.key}>
                    <button type="button" ref={element => { if (element) rowButtons.current.set(row.key, element); else rowButtons.current.delete(row.key); }} className={`awr-row${selected?.key === row.key ? ' is-selected' : ''}`} aria-pressed={selected?.key === row.key}
                        aria-controls={`${id}-details`} onClick={() => { setSelectedKey(row.key); if (window.matchMedia?.('(max-width: 760px)').matches) detailRef.current?.scrollIntoView?.({ block: 'start', behavior: 'smooth' }); }}>
                        <span className={`awr-type awr-type-${row.type}`} aria-hidden="true">{row.type === 'vacation' ? 'U' : 'K'}</span><span className="awr-row-copy"><strong>{nameOf(row.username)}</strong><span>{typeLabel(row.type)}{row.type === 'correction' ? ` · ${row.entries.length} ${row.entries.length === 1 ? t('adminWorkspaceRequests.oneChange', 'Änderung') : t('changes', 'Änderungen')}` : ''}</span><time dateTime={(dateBasis === 'request' ? row.requestDate : row.startDate) || undefined}>{rowDateLabel(row)}</time></span><span className={`awr-status is-${row.status}`}>{statusLabel(row.status)}</span>
                    </button></li>)}</ul> : <p className="awr-empty">{loading ? t('loading', 'Wird geladen…') : t('adminWorkspaceRequests.empty', 'Keine Anträge für diese Filter.')}</p>}
            </div>
            <div className="awr-details" ref={detailRef} id={`${id}-details`}>
                {!selected ? <p className="awr-empty">{t('adminWorkspaceRequests.choose', 'Wähle einen Antrag aus der Liste.')}</p> : <>
                    <div className="awr-detail-scroll" tabIndex={0} role="region" aria-label={t('adminWorkspaceRequests.details', 'Antragsdetails')}>
                    <header className="awr-detail-heading"><div><span className="awr-eyebrow">{typeLabel(selected.type)}</span><h3>{nameOf(selected.username)}</h3><p>{displayPeriod(selected.startDate, selected.endDate)}</p></div><span className={`awr-status is-${selected.status}`}>{statusLabel(selected.status)}</span></header>
                    <div className="awr-detail-links">{onOpenEmployee && <button type="button" className="awr-link" onClick={() => onOpenEmployee(selected.username)}>{t('adminWorkspaceRequests.profile', 'Mitarbeiterprofil')}</button>}{onOpenInTimeReview && <button type="button" className="awr-link" onClick={() => openTime(selected)}>{t('adminWorkspace.openTime', 'In Zeitprüfung öffnen')} ↗</button>}</div>
                    {selected.type === 'correction' && selected.requestDates.some(date => date !== selected.startDate) && <dl className="awr-facts"><div><dt>{t('adminWorkspaceRequests.correctionDate', 'Erfasster Korrekturtag')}</dt><dd>{selected.requestDates.map(displayDate).join(', ')}</dd></div></dl>}
                    {selected.type === 'correction' ? <div className="awr-changes">{selected.entries.map(entry => <article className="awr-change" key={entry.id}><div className="awr-compare">{renderPunch(entry, true)}<span aria-hidden="true">→</span>{renderPunch(entry, false)}</div>{entry.reason && <p className="awr-existing-note"><strong>{t('reason', 'Grund')}: </strong>{entry.reason}</p>}<div className="awr-change-status"><span className={`awr-status is-${requestStatus(entry)}`}>{statusLabel(requestStatus(entry))}</span>{formatRequestAdmin(entry, t) && <small>{formatRequestAdmin(entry, t)}</small>}</div>{entry.adminComment && <p className="awr-existing-note"><strong>{t('adminWorkspaceRequests.savedComment', 'Gespeicherter Kommentar')}: </strong>{entry.adminComment}</p>}</article>)}</div> : <>
                        {selected.reason && <p className="awr-existing-note"><strong>{t('reason', 'Grund')}: </strong>{selected.reason}</p>}
                        <p className="awr-vacation-options">{selected.vacation.halfDay ? t('adminDashboard.halfDayShort', '½ Tag') : t('adminWorkspace.fullDay', 'Ganztags')}{selected.vacation.companyVacation && ` · ${t('adminWorkspaceRequests.companyVacation', 'Betriebsferien')}`}{selected.vacation.usesOvertime && ` · ${t('adminWorkspace.overtimeLeave', 'Überstundenfrei')}`}{selected.vacation.usesOvertime && Number.isFinite(selected.vacation.overtimeDeductionMinutes) && ` · ${minutesToHHMM(selected.vacation.overtimeDeductionMinutes)} ${t('hours', 'Stunden')}`}</p>
                        {selected.vacation.adminNote && <p className="awr-existing-note"><strong>{t('userDashboard.adminNote', 'Admin-Notiz')}: </strong>{selected.vacation.adminNote}</p>}
                        <AdminWorkspaceVacationBalance username={selected.username} startDate={selected.startDate} endDate={selected.endDate} revision={allVacations} t={t} />
                        {users.find(user => user.username === selected.username)?.departmentName?.trim() && <section className="awr-overlaps"><h4>{t('adminWorkspace.teamOverlap', 'Überschneidungen im Team')}</h4>{overlaps.length ? <ul>{overlaps.map(absence => <li key={absence.key}>{nameOf(absence.username)} · {displayPeriod(absence.startIso > selected.startDate ? absence.startIso : selected.startDate, absence.endIso < selected.endDate ? absence.endIso : selected.endDate)} · {absence.kind === 'sick' ? t('sick', 'Krank') : t('adminWorkspace.vacation', 'Urlaub')}{absence.halfDay ? ' · ½ Tag' : ''}</li>)}</ul> : <p>{t('adminWorkspace.noTeamOverlap', 'Keine Überschneidungen mit eingetragenen Team-Abwesenheiten.')}</p>}</section>}
                    </>}
                    </div>
                    <div className="awr-detail-footer">
                    {!!selected.pendingEntries.length && <div className="awr-decision"><label>{t('adminWorkspace.decisionNote', 'Kommentar zur Entscheidung')}<textarea rows={2} maxLength={1000} value={notes[selected.key] || ''} disabled={!canManage || savingKey === selected.key} onChange={event => { const value = event.target.value; setNotes(previous => ({ ...previous, [selected.key]: value })); }} /></label>
                        {selected.type === 'correction' && selected.pendingEntries.length > 1 && <p className="awr-hint">{selected.pendingEntries.length} {t('adminWorkspaceRequests.remainingChanges', 'offene Änderungen dieses Tages werden gemeinsam entschieden.')}</p>}
                        {approvalScopeUnknown && <p className="awr-hint">{t('adminWorkspaceRequests.unknownApprovalDay', 'Genehmigen ist gesperrt, weil der gewünschte Korrekturzeitpunkt fehlt oder ungültig ist. Ablehnen bleibt möglich.')}</p>}
                        {decisionError?.key === selected.key && <p role="alert" className="awr-error">{decisionError.message}</p>}
                        <div className="awr-actions"><button type="button" className="awr-button" disabled={writesDisabled || !(selected.type === 'vacation' ? onDenyVacation : onDenyCorrection)} onClick={() => decide(false)}>{t('deny', 'Ablehnen')}</button><button type="button" className="awr-button awr-primary" disabled={writesDisabled || approvalScopeUnknown || !(selected.type === 'vacation' ? onApproveVacation : onApproveCorrection)} onClick={() => decide(true)}>{savingKey === selected.key ? t('saving', 'Wird gespeichert…') : t('approve', 'Genehmigen')}</button></div>
                    </div>}
                    {!canManage && <p className="awr-hint">{t('adminWorkspaceRequests.readOnly', 'Nur Ansicht: Entscheidungen und Löschen sind nicht freigegeben.')}</p>}
                    {selected.type === 'vacation' && <button type="button" className="awr-delete" disabled={writesDisabled} onClick={event => { deleteOpener.current = event.currentTarget; setDeleteTarget(selected.vacation); setDeleteError(''); }}>{t('adminWorkspaceRequests.deleteVacation', 'Urlaub löschen')}</button>}
                    </div>
                </>}
            </div>
        </div>
        {deleteTarget && <ModalOverlay visible className="awr-delete-overlay" onClose={closeDelete}><div className="awr-delete-dialog" role="dialog" aria-modal="true" aria-labelledby={`${id}-delete-title`} onKeyDown={event => {
            if (event.key !== 'Tab') return;
            const buttons = [...event.currentTarget.querySelectorAll('button:not(:disabled)')];
            if (!buttons.length) { event.preventDefault(); return; }
            if (event.shiftKey && document.activeElement === buttons[0]) { event.preventDefault(); buttons.at(-1).focus(); }
            else if (!event.shiftKey && document.activeElement === buttons.at(-1)) { event.preventDefault(); buttons[0].focus(); }
        }}><h3 id={`${id}-delete-title`}>{t('adminVacation.delete.confirmTitle', 'Urlaub löschen bestätigen')}</h3><p><strong>{nameOf(deleteTarget.username)}</strong> · {displayPeriod(deleteTarget.startDate, deleteTarget.endDate)}</p><p>{t('adminWorkspaceRequests.deleteConfirm', 'Diesen Urlaub unwiderruflich löschen?')}</p>{deleteTarget.approved && <p>{deleteTarget.usesOvertime ? t('adminVacation.delete.overtimeReversalInfo', 'Bei genehmigten Überstundenurlauben werden die abgezogenen Stunden dem Benutzerkonto wieder gutgeschrieben.') : t('adminWorkspaceRequests.deleteCredit', 'Die Urlaubstage werden bei der Neuberechnung des Resturlaubs wieder gutgeschrieben.')}</p>}{deleteError && <p className="awr-error" role="alert">{deleteError}</p>}<div className="awr-actions"><button type="button" ref={cancelDeleteRef} className="awr-button" onClick={closeDelete} disabled={Boolean(savingKey)}>{t('cancel', 'Abbrechen')}</button><button type="button" className="awr-button awr-danger" onClick={deleteVacation} disabled={writesDisabled}>{savingKey ? t('saving', 'Wird gespeichert…') : t('adminVacation.delete.confirmDeleteButton', 'Ja, löschen')}</button></div></div></ModalOverlay>}
    </section>;
}
