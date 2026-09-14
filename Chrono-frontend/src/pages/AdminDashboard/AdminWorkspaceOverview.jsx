import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { ACCESS_MANAGE, hasPageAccess } from '../../utils/pageAccess';
import { getUserDisplayName } from '../../utils/userDisplay';
import { addDays, formatDate, formatLocalDateYMD, formatTime, minutesToHHMM } from './adminDashboardUtils';
import AdminWorkspaceVacationBalance from './AdminWorkspaceVacationBalance';
import {
    groupWorkspaceCorrections, isWorkspacePending, workspaceAbsences, workspaceBalances, workspaceDate,
} from './adminWorkspaceData';

const EMPTY = [];
const defaultTranslation = (_key, fallback) => fallback;
const displayDate = value => {
    const date = workspaceDate(value);
    return date ? formatDate(new Date(`${date}T12:00:00`)) : '–';
};
const periodLabel = (start, end) => workspaceDate(start) === workspaceDate(end || start)
    ? displayDate(start) : `${displayDate(start)} – ${displayDate(end)}`;
const teamOf = user => String(user?.departmentName || '').trim();
const errorMessage = (error, fallback) => {
    const message = error?.response?.data?.message || error?.message;
    return typeof message === 'string' && message.trim() ? message : fallback;
};
const readPreferences = key => {
    try {
        const saved = JSON.parse(window.localStorage.getItem(key) || '{}');
        return { team: saved.team !== false, balances: saved.balances !== false };
    } catch { return { team: true, balances: true }; }
};

export function workspaceIssueLabel(type, t = defaultTranslation) {
    const labels = {
        missing: ['missing', 'Fehlende Stempelungen'],
        incomplete: ['incomplete', 'Unvollständige Stempelungen'],
        incomplete_work_end_missing: ['missingEnd', 'Arbeitsende fehlt'],
        incomplete_duplicate_punch_times: ['duplicate', 'Doppelte Stempelzeiten'],
        auto_completed: ['automatic', 'Automatisch beendet, noch ungeprüft'],
        auto_completed_uncorrected: ['automatic', 'Automatisch beendet, noch ungeprüft'],
        auto_completed_incomplete_uncorrected: ['automaticIncomplete', 'Automatisch beendet, Stempelungen unvollständig'],
        holiday_pending_decision: ['holiday', 'Feiertagsentscheidung offen'],
        weekly_delta_unusual: ['weeklyDelta', 'Ungewöhnliche Wochenabweichung'],
    };
    const [key, fallback] = labels[type] || ['unknown', 'Zeiteinträge prüfen'];
    return t(`adminWorkspace.issues.${key}`, fallback);
}

function CorrectionComparison({ entry, t, completed }) {
    return <div className="aw-compare">
        <div className="aw-punch"><span className="aw-punch-label">{t('adminDashboard.originalTimeLabel', 'Gestempelt')}</span>
            <strong className="aw-punch-value">{entry.originalTimestamp ? formatTime(entry.originalTimestamp) : t('adminDashboard.noOriginalTimeLabel', 'Kein ursprünglicher Stempel')}</strong>
            {entry.originalTimestamp && <small>{displayDate(entry.originalTimestamp)}{entry.originalPunchType ? ` · ${t(`punchTypes.${entry.originalPunchType}`, entry.originalPunchType)}` : ''}</small>}
        </div><span className="aw-arrow" aria-hidden="true">→</span>
        <div className="aw-punch"><span className="aw-punch-label">{t('adminDashboard.requestedTimeLabel', 'Beantragt')}</span>
            <strong className="aw-punch-value">{formatTime(entry.desiredTimestamp)}</strong>
            <small>{displayDate(entry.desiredTimestamp)}{entry.desiredPunchType ? ` · ${t(`punchTypes.${entry.desiredPunchType}`, entry.desiredPunchType)}` : ''}</small>
            {(entry.approved || entry.denied || completed) && <small>{entry.approved ? t('approved', 'Genehmigt') : entry.denied ? t('denied', 'Abgelehnt') : t('adminWorkspace.processed', 'Bereits bearbeitet')}</small>}
        </div>
    </div>;
}

/** Data is already scoped to selectedTeam by the parent. Decision callbacks must
 * reject API failures, and resolve after a successful mutation even if reload fails. */
export default function AdminWorkspaceOverview({
    t = defaultTranslation, currentUser, users = EMPTY, allVacations = EMPTY,
    allCorrections = EMPTY, allSickLeaves = EMPTY, weeklyBalances = EMPTY, issueRows = null,
    selectedTeam = '', onTeamChange, teams = EMPTY, loading = false, loadError,
    onRetry, onOpenTime, onOpenRequests, onOpenCalendar, onOpenModules,
    onFocusEmployee, onOpenEmployee, onOpenAbsence, onApproveVacation, onDenyVacation,
    onApproveCorrection, onDenyCorrection, onPrint, onFocusNegativeBalances, onFocusOvertimeLeaders,
}) {
    const idPrefix = useId();
    const [filter, setFilter] = useState('all');
    const [showAll, setShowAll] = useState(false);
    const [expandedKey, setExpandedKey] = useState(null);
    const [notes, setNotes] = useState({});
    const [savingKey, setSavingKey] = useState(null);
    const savingRef = useRef(false);
    const [completedIds, setCompletedIds] = useState(() => new Set());
    const [decisionError, setDecisionError] = useState(null);
    const [feedback, setFeedback] = useState('');
    const preferencesKey = `chrono:admin-workspace-overview:v1:${currentUser?.companyId || currentUser?.company?.id || ''}:${currentUser?.username || 'anonymous'}`;
    const [preferences, setPreferences] = useState(() => readPreferences(preferencesKey));
    const canManage = hasPageAccess(currentUser, 'adminDashboard', ACCESS_MANAGE);
    const decisionsDisabled = !canManage || loading || Boolean(loadError) || Boolean(savingKey);
    const today = new Date();
    const todayIso = formatLocalDateYMD(today);
    const rangeEndIso = formatLocalDateYMD(addDays(today, 30));
    const nameOf = username => getUserDisplayName(username, users, t('adminVacation.unknownUser', 'Unbekannt'));
    const usersByName = useMemo(() => new Map(users.map(user => [user.username, user])), [users]);

    useEffect(() => { setPreferences(readPreferences(preferencesKey)); }, [preferencesKey]);
    useEffect(() => { setCompletedIds(new Set()); setNotes({}); setFeedback(''); setDecisionError(null); }, [preferencesKey]);
    useEffect(() => { setShowAll(false); setExpandedKey(null); setDecisionError(null); }, [selectedTeam]);
    const updatePreference = (key, checked) => {
        const next = { ...preferences, [key]: checked };
        setPreferences(next);
        try { window.localStorage.setItem(preferencesKey, JSON.stringify(next)); } catch { /* View remains usable without storage. */ }
    };

    const requestRows = useMemo(() => [
        ...groupWorkspaceCorrections(allCorrections).map(group => ({
            ...group, pendingEntries: group.pendingEntries.filter(entry => !completedIds.has(`correction:${entry.id}`)),
        })).filter(group => group.pendingEntries.length),
        ...allVacations.filter(item => item?.id != null && isWorkspacePending(item) && !completedIds.has(`vacation:${item.id}`)).map(item => ({
            key: `vacation:${item.id}`, kind: 'vacation', username: item.username,
            dateIso: workspaceDate(item.startDate), sortDate: workspaceDate(item.requestDate || item.createdAt) || workspaceDate(item.startDate) || '',
            vacation: item,
        })),
    ].sort((a, b) => b.sortDate.localeCompare(a.sortDate) || a.key.localeCompare(b.key)), [allCorrections, allVacations, completedIds]);
    const timeRows = useMemo(() => (Array.isArray(issueRows) ? issueRows : EMPTY).filter(row => row?.username).map(row => ({
        ...row, key: `time:${row.id || row.username}`, kind: 'time', sortDate: row.dateIso || '',
    })), [issueRows]);
    const chosenRows = useMemo(() => [
        ...(filter !== 'time' ? requestRows : EMPTY), ...(filter !== 'request' ? timeRows : EMPTY),
    ].sort((a, b) => (b.sortDate || '').localeCompare(a.sortDate || '') || a.key.localeCompare(b.key)), [filter, requestRows, timeRows]);
    const shownRows = showAll ? chosenRows : chosenRows.slice(0, 5);
    const absences = useMemo(() => workspaceAbsences(allVacations, allSickLeaves), [allVacations, allSickLeaves]);
    const activeAbsences = absences.filter(item => item.startIso <= todayIso && item.endIso >= todayIso);
    const upcomingAbsences = absences.filter(item => item.startIso > todayIso && item.startIso <= rangeEndIso);
    const activeCount = new Set(activeAbsences.map(item => item.username)).size;
    const balances = useMemo(() => workspaceBalances(weeklyBalances, users), [weeklyBalances, users]);
    const average = balances.length ? Math.round(balances.reduce((sum, item) => sum + item.trackingBalance, 0) / balances.length) : null;
    const negativeCount = balances.filter(item => item.trackingBalance < 0).length;
    const leader = balances.reduce((best, item) => !best || item.trackingBalance > best.trackingBalance ? item : best, null);
    const selectedTeamLabel = teams.find(team => team.value === selectedTeam)?.label || t('adminWorkspace.allTeams', 'Alle Teams');

    const focus = (username, dateIso) => onFocusEmployee?.(username, workspaceDate(dateIso) || undefined);
    const toggle = key => setExpandedKey(previous => previous === key ? null : key);
    const decide = async (row, approve) => {
        if (decisionsDisabled || savingRef.current) return;
        const callback = row.kind === 'vacation'
            ? approve ? onApproveVacation : onDenyVacation
            : approve ? onApproveCorrection : onDenyCorrection;
        if (!callback) return;
        const ids = row.kind === 'vacation' ? [row.vacation.id] : row.pendingEntries.map(entry => entry.id);
        if (!ids.length) return;
        savingRef.current = true;
        setSavingKey(row.key); setDecisionError(null); setFeedback('');
        try {
            // All members of a correction group are intentional targets. Settling
            // every request prevents a late success from becoming a retry target.
            const results = await Promise.allSettled(ids.map(id => Promise.resolve().then(() => callback(id, notes[row.key] || ''))));
            const successes = ids.filter((_, index) => results[index].status === 'fulfilled');
            setCompletedIds(previous => new Set([...previous, ...successes.map(id => `${row.kind}:${id}`)]));
            const failed = results.filter(result => result.status === 'rejected');
            if (failed.length) {
                const prefix = successes.length ? `${successes.length} ${successes.length === 1 ? t('adminWorkspace.changeSaved', 'Änderung gespeichert') : t('adminWorkspace.changesSaved', 'Änderungen gespeichert')}. ` : '';
                setDecisionError({ key: row.key, message: prefix + errorMessage(failed[0].reason, t('adminWorkspace.decisionFailed', 'Die Entscheidung konnte nicht gespeichert werden. Bitte erneut versuchen.')) });
            } else {
                setExpandedKey(previous => previous === row.key ? null : previous);
                setFeedback(`${nameOf(row.username)}: ${approve ? t('adminWorkspace.approvedFeedback', 'Antrag genehmigt.') : t('adminWorkspace.deniedFeedback', 'Antrag abgelehnt.')}`);
            }
        } finally { savingRef.current = false; setSavingKey(null); }
    };

    const renderVacationDetails = row => {
        const vacation = row.vacation;
        const ownTeam = teamOf(usersByName.get(row.username));
        const start = workspaceDate(vacation.startDate), end = workspaceDate(vacation.endDate);
        const overlaps = ownTeam && start && end && start <= end ? absences.filter(item =>
            !(item.kind === 'vacation' && item.id === vacation.id)
            && teamOf(usersByName.get(item.username)) === ownTeam
            && item.startIso <= end && item.endIso >= start) : [];
        return <>
            <span className="aw-detail-label">{t('adminWorkspace.requestedPeriod', 'Beantragter Zeitraum')}</span>
            <strong>{periodLabel(vacation.startDate, vacation.endDate)}</strong>
            <p>{vacation.halfDay ? t('adminDashboard.halfDayShort', '½ Tag') : t('adminWorkspace.fullDay', 'Ganztags')}{vacation.usesOvertime ? ` · ${t('adminWorkspace.overtimeLeave', 'Überstundenfrei')}` : ''}</p>
            {(vacation.reason || vacation.comment) && <p className="aw-reason">{vacation.reason || vacation.comment}</p>}
            {vacation.adminNote && <p className="aw-reason"><span className="aw-detail-label">{t('userDashboard.adminNote', 'Admin-Notiz')}</span>{vacation.adminNote}</p>}
            <AdminWorkspaceVacationBalance username={row.username} startDate={vacation.startDate} endDate={vacation.endDate} revision={allVacations} t={t} />
            {ownTeam && start && end && start <= end && <div className="aw-overlap-list"><span className="aw-detail-label">{t('adminWorkspace.teamOverlap', 'Überschneidungen im Team')}</span>
                {overlaps.length ? overlaps.map(item => <p key={item.key}>{nameOf(item.username)} · {periodLabel(item.startIso > start ? item.startIso : start, item.endIso < end ? item.endIso : end)} · {item.kind === 'sick' ? t('sick', 'Krank') : t('adminWorkspace.vacation', 'Urlaub')}{item.halfDay ? ` · ${t('adminDashboard.halfDayShort', '½ Tag')}` : ''}</p>) : <p>{t('adminWorkspace.noTeamOverlap', 'Keine Überschneidungen mit eingetragenen Team-Abwesenheiten.')}</p>}
            </div>}
        </>;
    };

    const renderDetail = (row, index) => <div className="aw-task-detail" id={`${idPrefix}-detail-${index}`}>
        {row.kind === 'time' ? <>
            <ul className="aw-issue-list">{(row.issues || EMPTY).map((issue, issueIndex) => <li className="aw-issue" key={`${issue.type}:${issue.dateIso}:${issueIndex}`}><div><strong>{workspaceIssueLabel(issue.type, t)}</strong><p>{issue.dateIso ? displayDate(issue.dateIso) : t('adminWorkspace.issueNoDate', 'Kein einzelner Problemtag hinterlegt.')}</p></div><button type="button" className="aw-text-button" onClick={() => focus(row.username, issue.dateIso)}>{t('adminWorkspace.review', 'Prüfen')} ↗</button></li>)}</ul>
            {!row.issues?.length && <button type="button" className="aw-text-button" onClick={() => focus(row.username, row.dateIso)}>{t('adminWorkspace.openTime', 'In Zeitprüfung öffnen')} ↗</button>}
        </> : <>
            {row.kind === 'correction' ? <><div className="aw-compare-list">{row.entries.map(entry => <CorrectionComparison key={entry.id} entry={entry} t={t} completed={completedIds.has(`correction:${entry.id}`)} />)}</div><div className="aw-reason"><span className="aw-detail-label">{t('reason', 'Grund')}</span><p>{row.reason || t('adminWorkspace.noReason', 'Keine Begründung hinterlegt.')}</p></div></> : renderVacationDetails(row)}
            <label className="aw-note"><span>{t('adminWorkspace.decisionNote', 'Kommentar zur Entscheidung')} <small>· {t('optional', 'optional')}</small></span><textarea value={notes[row.key] || ''} disabled={!canManage || Boolean(savingKey)} rows={2} onChange={event => setNotes(previous => ({ ...previous, [row.key]: event.target.value }))} /></label>
            {row.kind === 'correction' && row.pendingEntries.length > 1 && <p>{row.pendingEntries.length} {t('adminWorkspace.groupDecision', 'zusammengehörige Änderungen werden gemeinsam entschieden.')}</p>}
            {decisionError?.key === row.key && <p className="aw-error" role="alert">{decisionError.message}</p>}
            <div className="aw-detail-actions"><button type="button" className="aw-text-button" onClick={() => focus(row.username, row.dateIso)}>{t('adminWorkspace.openTime', 'In Zeitprüfung öffnen')} ↗</button><div>
                <button type="button" className="aw-button" disabled={decisionsDisabled || !(row.kind === 'vacation' ? onDenyVacation : onDenyCorrection)} onClick={() => decide(row, false)}>{t('deny', 'Ablehnen')}</button>
                <button type="button" className="aw-button aw-button-primary" disabled={decisionsDisabled || !(row.kind === 'vacation' ? onApproveVacation : onApproveCorrection)} onClick={() => decide(row, true)}>{savingKey === row.key ? t('saving', 'Wird gespeichert…') : t('approve', 'Genehmigen')}</button>
            </div></div>
        </>}
    </div>;

    const renderTask = (row, index) => {
        const name = nameOf(row.username);
        const open = expandedKey === row.key;
        const title = row.kind === 'time' ? workspaceIssueLabel(row.issues?.[0]?.type, t) : row.kind === 'vacation' ? t('adminWorkspace.vacation', 'Urlaub') : t('adminWorkspace.correction', 'Zeitkorrektur');
        const date = row.kind === 'vacation' ? periodLabel(row.vacation.startDate, row.vacation.endDate) : row.dateIso ? displayDate(row.dateIso) : '';
        return <article className={`aw-task${open ? ' is-expanded' : ''}`} key={row.key} data-task-key={row.key}>
            <div className="aw-task-row"><span className="aw-avatar" aria-hidden="true">{name.split(/\s+/).slice(0, 2).map(word => word[0]).join('')}</span><div className="aw-task-copy"><button type="button" className="aw-task-name" onClick={() => onOpenEmployee?.(row.username)}>{name}</button><div className="aw-task-meta"><span>{title}</span>{date && <span> · {date}</span>}{row.kind === 'correction' && row.pendingEntries.length > 1 && <span> · {row.pendingEntries.length} {t('changes', 'Änderungen')}</span>}</div></div>
                {row.kind === 'time' && <button type="button" className="aw-text-button" aria-label={`${t('details', 'Details')}: ${name}`} aria-expanded={open} aria-controls={`${idPrefix}-detail-${index}`} onClick={() => toggle(row.key)}>{open ? '−' : '+'}</button>}
                <button type="button" className={`aw-review-button${open ? ' is-expanded' : ''}`} aria-expanded={row.kind === 'time' ? undefined : open} aria-controls={row.kind === 'time' ? undefined : `${idPrefix}-detail-${index}`} aria-label={`${row.kind !== 'time' && open ? t('close', 'Schließen') : t('adminWorkspace.review', 'Prüfen')}: ${name} · ${title}`} onClick={() => row.kind === 'time' ? focus(row.username, row.dateIso) : toggle(row.key)}>{row.kind !== 'time' && open ? t('close', 'Schließen') : t('adminWorkspace.review', 'Prüfen')} <span aria-hidden="true">{row.kind === 'time' ? '↗' : open ? '⌃' : '⌄'}</span></button>
            </div>{open && renderDetail(row, index)}
        </article>;
    };

    const renderAbsence = item => <div className="aw-absence" key={item.key}><div className="aw-absence-copy"><button type="button" className="aw-task-name" onClick={() => onOpenEmployee?.(item.username)}>{nameOf(item.username)}</button><p>{periodLabel(item.startDate, item.endDate)}</p><span className={`aw-absence-kind${item.kind === 'sick' ? ' is-sick' : ''}`}>{item.kind === 'sick' ? t('sick', 'Krank') : item.usesOvertime ? t('adminWorkspace.overtimeLeave', 'Überstundenfrei') : t('adminWorkspace.vacation', 'Urlaub')}{item.halfDay ? ` · ${t('adminDashboard.halfDayShort', '½ Tag')}` : ''}</span></div><button type="button" className="aw-text-button" aria-label={`${t('details', 'Details')}: ${nameOf(item.username)} · ${periodLabel(item.startDate, item.endDate)}`} onClick={() => onOpenAbsence?.({ ...item.source, kind: item.kind, type: item.kind, dateIso: item.startIso, raw: item.source })}>↗</button></div>;
    const noData = !requestRows.length && !timeRows.length;
    return <div className="aw-overview" aria-busy={loading}>
        <div className="aw-toolbar"><span className="aw-date">{formatDate(today)}</span>{teams.length > 0 && <label className="aw-team-select">{t('team', 'Team')}<select value={selectedTeam} onChange={event => onTeamChange?.(event.target.value)}><option value="">{t('adminWorkspace.allTeams', 'Alle Teams')}</option>{teams.filter(team => team.value !== '').map(team => <option value={team.value} key={team.value}>{team.label}</option>)}</select></label>}</div>
        {loadError && <div className="aw-error" role="alert"><span>{t('adminWorkspace.loadFailed', 'Die Daten konnten nicht vollständig aktualisiert werden. Freigaben sind bis zur Aktualisierung gesperrt.')}</span>{typeof loadError === 'string' && <p>{loadError}</p>}<button type="button" className="aw-button" onClick={onRetry} disabled={loading}>{t('retry', 'Erneut versuchen')}</button></div>}
        {feedback && <div className="aw-feedback" role="status"><span>{feedback}</span><button type="button" className="aw-text-button" aria-label={t('close', 'Schließen')} onClick={() => setFeedback('')}>×</button></div>}
        <div className={`aw-grid${!preferences.team ? ' aw-grid-single' : ''}`}><section className="aw-inbox" aria-label={t('adminWorkspace.tasks', 'Zu erledigen')}><div className="aw-section-heading"><div><h2>{t('adminWorkspace.tasks', 'Zu erledigen')}</h2><p>{t('adminWorkspace.taskDescription', 'Anträge und Zeitprüfung')}</p></div>{loading && !noData && <span className="aw-loading" role="status">{t('loading', 'Wird aktualisiert…')}</span>}</div>
            <div className="aw-filters" role="group" aria-label={t('adminWorkspace.filterTasks', 'Vorgänge filtern')}>{[['all', t('all', 'Alle'), null], ['request', t('adminWorkspace.requests', 'Anträge'), requestRows.length], ['time', t('adminWorkspace.timeReview', 'Zeitprüfung'), issueRows === null ? null : timeRows.length]].map(([key, label, count]) => <button type="button" key={key} className={filter === key ? 'is-active' : ''} aria-pressed={filter === key} onClick={() => { setFilter(key); setShowAll(false); }}>{label}{count !== null && <span className="aw-count">{count}</span>}</button>)}</div>
            <div className="aw-task-list">{loading && noData ? <p className="aw-loading" role="status">{t('adminWorkspace.loadingTasks', 'Vorgänge werden geladen…')}</p> : shownRows.length ? shownRows.map(renderTask) : <div className="aw-empty">{loadError ? t('adminWorkspace.noReliableTasks', 'Für diese Ansicht liegen keine vollständig geladenen Vorgänge vor.') : issueRows === null && filter !== 'request' ? t('adminWorkspace.calculatingIssues', 'Zeitprüfung wird berechnet…') : t('adminWorkspace.noOpenTasks', 'Keine offenen Vorgänge in dieser Ansicht.')}</div>}</div>
            {!canManage && <p className="aw-empty">{t('adminDashboard.readOnlyPermissions', 'Nur Ansicht: Freigaben und Änderungen sind nicht erlaubt.')}</p>}
            <div className="aw-inbox-foot"><span>{shownRows.length} {t('of', 'von')} {chosenRows.length} {t('adminWorkspace.items', 'Vorgängen')}</span>{chosenRows.length > 5 ? <button type="button" className="aw-text-button" onClick={() => setShowAll(value => !value)}>{showAll ? t('showLess', 'Weniger anzeigen') : `${t('showMore', 'Weitere anzeigen')} (${chosenRows.length - 5})`}</button> : <button type="button" className="aw-text-button" onClick={filter === 'time' ? onOpenTime : onOpenRequests}>{filter === 'time' ? t('adminWorkspace.openTime', 'Zeitprüfung öffnen') : t('adminWorkspace.openRequests', 'Anträge öffnen')} ↗</button>}</div>
        </section>{preferences.team && <aside className="aw-team-panel"><div className="aw-section-heading"><div><h2>{t('adminWorkspace.teamAtGlance', 'Team im Blick')}</h2><p>{selectedTeamLabel} · {users.length} {t('adminWorkspace.employees', 'Mitarbeitende')}</p></div></div><div className="aw-today"><div className="aw-coming-head"><h3>{t('adminWorkspace.absentToday', 'Heute abwesend')}</h3><strong className="aw-team-count">{loading && !absences.length ? '–' : activeCount}</strong></div><div className="aw-absence-list">{activeAbsences.length ? activeAbsences.slice(0, 3).map(renderAbsence) : <p className="aw-empty">{loading ? t('loading', 'Wird geladen…') : loadError ? t('adminWorkspace.absenceUnavailable', 'Abwesenheiten sind derzeit nicht vollständig verfügbar.') : t('adminWorkspace.noAbsenceToday', 'Keine Abwesenheiten eingetragen.')}</p>}</div>{activeAbsences.length > 3 && <p className="aw-more-absences">3 {t('of', 'von')} {activeAbsences.length} {t('adminWorkspace.absences', 'Abwesenheiten')}</p>}</div><div className="aw-coming"><div className="aw-coming-head"><h3>{t('adminWorkspace.comingNext', 'Als Nächstes')}</h3><span>{t('until', 'Bis')} {displayDate(rangeEndIso)}</span></div><div className="aw-absence-list">{upcomingAbsences.length ? upcomingAbsences.slice(0, 3).map(renderAbsence) : <p className="aw-empty">{loading ? t('loading', 'Wird geladen…') : loadError ? t('adminWorkspace.absenceUnavailable', 'Abwesenheiten sind derzeit nicht vollständig verfügbar.') : t('adminWorkspace.noComingAbsence', 'Keine Abwesenheiten in den nächsten 30 Tagen geplant.')}</p>}</div>{upcomingAbsences.length > 3 && <p className="aw-more-absences">3 {t('of', 'von')} {upcomingAbsences.length} {t('adminWorkspace.absences', 'Abwesenheiten')}</p>}</div><button type="button" className="aw-calendar-button" onClick={onOpenCalendar}>{t('adminWorkspace.openCalendar', 'Kalender öffnen')} ↗</button></aside>}</div>
        {preferences.balances && <section className="aw-balances" aria-label={t('adminWorkspace.balances', 'Zeitkonten')}><div className="aw-balance-title"><h3>{t('adminWorkspace.balances', 'Zeitkonten')}</h3><small>{t('adminWorkspace.totalBalance', 'Aktueller Gesamtstand')}</small></div><button type="button" className="aw-balance-stat" onClick={onFocusNegativeBalances} disabled={!balances.length}><small>{t('adminWorkspace.averageBalance', 'Ø Team-Saldo')}</small><strong className={average < 0 ? 'aw-negative' : average > 0 ? 'aw-positive' : ''}>{average === null ? '–' : minutesToHHMM(average)}</strong><span>{t('adminWorkspace.perEmployee', 'pro Person')}</span></button><button type="button" className="aw-balance-stat" onClick={onFocusNegativeBalances} disabled={!balances.length}><small>{t('adminWorkspace.negativeBalances', 'Negative Zeitkonten')}</small><strong>{balances.length ? negativeCount : '–'}</strong><span>{balances.length ? `${t('of', 'von')} ${balances.length}` : t('adminWorkspace.noBalances', 'Keine Salden vorhanden')}</span></button><button type="button" className="aw-balance-stat" onClick={onFocusOvertimeLeaders} disabled={!leader}><small>{t('adminWorkspace.highestBalance', 'Höchster Saldo')}</small><strong className={leader?.trackingBalance < 0 ? 'aw-negative' : leader?.trackingBalance > 0 ? 'aw-positive' : ''}>{leader ? minutesToHHMM(leader.trackingBalance) : '–'}</strong><span>{leader ? nameOf(leader.username) : t('adminWorkspace.noBalances', 'Keine Salden vorhanden')}</span></button></section>}
        <footer className="aw-footer"><button type="button" className="aw-text-button" onClick={onPrint}>{t('adminWorkspace.print', 'Zeiten drucken')}</button><button type="button" className="aw-text-button" onClick={onOpenModules}>{t('adminWorkspace.modules', 'Alle Module')}</button><details className="aw-settings"><summary>{t('adminWorkspace.customize', 'Ansicht anpassen')}</summary><label><input type="checkbox" checked={preferences.team} onChange={event => updatePreference('team', event.target.checked)} />{t('adminWorkspace.teamAtGlance', 'Team im Blick')}</label><label><input type="checkbox" checked={preferences.balances} onChange={event => updatePreference('balances', event.target.checked)} />{t('adminWorkspace.balances', 'Zeitkonten')}</label></details></footer>
    </div>;
}
