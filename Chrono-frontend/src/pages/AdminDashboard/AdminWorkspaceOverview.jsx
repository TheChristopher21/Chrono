import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { ACCESS_MANAGE, hasPageAccess } from '../../utils/pageAccess';
import { getUserDisplayName } from '../../utils/userDisplay';
import { addDays, formatDate, formatLocalDateYMD, formatTime, minutesToHHMM } from './adminDashboardUtils';
import AdminWorkspaceVacationBalance from './AdminWorkspaceVacationBalance';
import './AdminWorkspaceOverview.css';
import {
    groupWorkspaceCorrections, isWorkspacePending, workspaceAbsences, workspaceBalances, workspaceDate,
} from './adminWorkspaceData';

const EMPTY = [];
const COMPACT_DESKTOP_QUERY = '(min-width: 1200px) and (max-height: 940px)';
const SHORT_DESKTOP_QUERY = '(min-width: 1200px) and (max-height: 820px)';
const readDesktopSize = () => {
    if (typeof window === 'undefined' || !window.matchMedia?.(COMPACT_DESKTOP_QUERY).matches) return null;
    return window.matchMedia(SHORT_DESKTOP_QUERY).matches ? 'short' : 'medium';
};
const defaultTranslation = (_key, fallback) => fallback;
const displayDate = value => {
    const date = workspaceDate(value);
    return date ? formatDate(new Date(`${date}T12:00:00`)) : '–';
};
const periodLabel = (start, end) => workspaceDate(start) === workspaceDate(end || start)
    ? displayDate(start) : `${displayDate(start)} – ${displayDate(end)}`;
const teamOf = user => String(user?.departmentName || '').trim();
// Compare civil dates in UTC so age/order do not change at a daylight-saving boundary.
const daysBetween = (start, end) => Math.round((Date.parse(`${end}T00:00:00Z`) - Date.parse(`${start}T00:00:00Z`)) / 86400000);
const affectedDate = row => workspaceDate(row.dateIso);
const taskOrder = (row, todayIso) => {
    const start = workspaceDate(row.vacation?.startDate);
    const end = workspaceDate(row.vacation?.endDate || start);
    const approaching = start && end >= todayIso && daysBetween(todayIso, start) <= 7;
    const date = approaching ? start : affectedDate(row);
    return { priority: approaching ? 0 : date ? 1 : 2, date: date || '9999-12-31' };
};
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
    initialLoading = loading && !allVacations.length && !allCorrections.length && !allSickLeaves.length && !weeklyBalances.length && !issueRows?.length,
    dataUpdatedAt, timeRangeStart, timeRangeEnd,
    onRetry, onOpenTime, onOpenRequests, onOpenCalendar, onOpenModules,
    onFocusEmployee, onOpenEmployee, onOpenAbsence, onApproveVacation, onDenyVacation,
    onApproveCorrection, onDenyCorrection, onPrint, onFocusNegativeBalances, onFocusOvertimeLeaders,
}) {
    const idPrefix = useId();
    const [filter, setFilter] = useState('all');
    const [showAll, setShowAll] = useState(false);
    const [desktopSize, setDesktopSize] = useState(readDesktopSize);
    const compactDesktop = desktopSize !== null;
    const [expandedKey, setExpandedKey] = useState(null);
    const [notes, setNotes] = useState({});
    const [savingKey, setSavingKey] = useState(null);
    const savingRef = useRef(false);
    const inboxRef = useRef(null);
    const [completedIds, setCompletedIds] = useState(() => new Set());
    const [decisionError, setDecisionError] = useState(null);
    const [feedback, setFeedback] = useState('');
    const preferencesKey = `chrono:admin-workspace-overview:v1:${currentUser?.companyId || currentUser?.company?.id || ''}:${currentUser?.username || 'anonymous'}`;
    const [preferences, setPreferences] = useState(() => readPreferences(preferencesKey));
    const canManage = hasPageAccess(currentUser, 'adminDashboard', ACCESS_MANAGE);
    const decisionsDisabled = !canManage || loading || initialLoading || Boolean(loadError) || Boolean(savingKey);
    const dataUnknown = initialLoading || Boolean(loadError);
    const today = new Date();
    const todayIso = formatLocalDateYMD(today);
    const rangeEndIso = formatLocalDateYMD(addDays(today, 30));
    const nameOf = username => getUserDisplayName(username, users, t('adminVacation.unknownUser', 'Unbekannt'));
    const usersByName = useMemo(() => new Map(users.map(user => [user.username, user])), [users]);

    useEffect(() => {
        const media = [window.matchMedia?.(COMPACT_DESKTOP_QUERY), window.matchMedia?.(SHORT_DESKTOP_QUERY)].filter(Boolean);
        if (!media.length) return undefined;
        const update = () => setDesktopSize(readDesktopSize());
        update();
        media.forEach(query => query.addEventListener?.('change', update));
        return () => media.forEach(query => query.removeEventListener?.('change', update));
    }, []);
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
            dateIso: workspaceDate(item.startDate),
            vacation: item,
        })),
    ], [allCorrections, allVacations, completedIds]);
    const timeRows = useMemo(() => (Array.isArray(issueRows) ? issueRows : EMPTY).filter(row => row?.username).map(row => ({
        ...row, key: `time:${row.id || row.username}`, kind: 'time', sortDate: row.dateIso || '',
    })), [issueRows]);
    const chosenRows = useMemo(() => [
        ...(filter !== 'time' ? requestRows : EMPTY), ...(filter !== 'request' ? timeRows : EMPTY),
    ].sort((a, b) => {
        const first = taskOrder(a, todayIso), second = taskOrder(b, todayIso);
        return first.priority - second.priority || first.date.localeCompare(second.date) || a.key.localeCompare(b.key);
    }), [filter, requestRows, timeRows, todayIso]);
    const previewLimit = desktopSize === 'short' ? 3 : desktopSize === 'medium' ? 4 : 5;
    // A viewport change must never hide the form a person is currently editing.
    const expandedIndex = chosenRows.findIndex(row => row.key === expandedKey);
    const shownRows = showAll ? chosenRows : chosenRows.slice(0, Math.max(previewLimit, expandedIndex + 1));
    const absencePreviewLimit = desktopSize === 'short' ? 1 : 2;
    const absences = useMemo(() => workspaceAbsences(allVacations, allSickLeaves), [allVacations, allSickLeaves]);
    const activeAbsences = absences.filter(item => item.startIso <= todayIso && item.endIso >= todayIso);
    const upcomingAbsences = absences.filter(item => item.startIso > todayIso && item.startIso <= rangeEndIso);
    const activeCount = new Set(activeAbsences.map(item => item.username)).size;
    const balances = useMemo(() => workspaceBalances(weeklyBalances, users), [weeklyBalances, users]);
    const average = balances.length ? Math.round(balances.reduce((sum, item) => sum + item.trackingBalance, 0) / balances.length) : null;
    const negativeCount = balances.filter(item => item.trackingBalance < 0).length;
    const leader = balances.reduce((best, item) => !best || item.trackingBalance > best.trackingBalance ? item : best, null);
    const selectedTeamLabel = teams.find(team => team.value === selectedTeam)?.label || t('adminWorkspace.allTeams', 'Alle Teams');
    const employeeCount = new Set(users.map(user => user.username).filter(Boolean)).size;
    const affectedCount = new Set(timeRows.map(row => row.username)).size;
    const vacationCount = requestRows.filter(row => row.kind === 'vacation').length;
    const correctionCount = requestRows.length - vacationCount;
    const issueDates = timeRows.flatMap(row => [workspaceDate(row.periodStart), workspaceDate(row.periodEnd)]).filter(Boolean).sort();
    const issueStart = issueDates[0] || workspaceDate(timeRangeStart);
    const issueEnd = issueDates[issueDates.length - 1] || workspaceDate(timeRangeEnd);
    const issuePeriod = issueStart && issueEnd ? periodLabel(issueStart, issueEnd) : null;
    const updatedDate = dataUpdatedAt == null ? null : new Date(dataUpdatedAt);
    const updatedLabel = updatedDate && Number.isFinite(updatedDate.getTime())
        ? `${formatDate(updatedDate)} · ${formatTime(updatedDate)}` : null;
    const unknownLabel = loadError ? t('adminWorkspace.notCurrent', 'Nicht aktuell') : t('adminWorkspace.loadingData', 'Daten werden geladen…');
    const dataContextLabel = loading ? t('adminWorkspace.refreshing', 'Wird aktualisiert…')
        : updatedLabel ? `${t('adminWorkspace.updatedAt', 'Datenstand')} ${updatedLabel}`
        : loadError ? t('adminWorkspace.dataUnavailable', 'Datenstand nicht verfügbar')
        : initialLoading ? t('adminWorkspace.loadingData', 'Daten werden geladen…')
        : t('adminWorkspace.loadedData', 'Geladener Datenstand');
    const absenceEmptyLabel = fallback => loadError
        ? t('adminWorkspace.absenceUnavailable', 'Abwesenheiten sind derzeit nicht vollständig verfügbar.')
        : initialLoading ? t('loading', 'Wird geladen…') : fallback;
    const chooseFilter = key => {
        setFilter(key); setShowAll(false);
        inboxRef.current?.focus({ preventScroll: true });
    };
    const timingLabel = row => {
        if (row.kind === 'vacation') {
            const start = workspaceDate(row.vacation.startDate), end = workspaceDate(row.vacation.endDate || start);
            if (start && end && end >= todayIso && daysBetween(todayIso, start) <= 7) {
                const days = daysBetween(todayIso, start);
                if (days < 0) return t('adminWorkspace.periodStarted', 'Zeitraum hat begonnen');
                if (days === 0) return t('adminWorkspace.startsToday', 'Beginn heute');
                if (days === 1) return t('adminWorkspace.startsTomorrow', 'Beginn morgen');
                return `${t('adminWorkspace.startsIn', 'Beginn in')} ${days} ${t('adminWorkspace.days', 'Tagen')}`;
            }
        }
        return row.kind === 'time' ? t('adminWorkspace.timeReview', 'Zeitprüfung') : t('adminWorkspace.openRequest', 'Offener Antrag');
    };

    const focus = (username, dateIso) => onFocusEmployee?.(username, workspaceDate(dateIso) || undefined);
    const toggle = key => setExpandedKey(previous => previous === key ? null : key);
    const decide = async (row, approve) => {
        if (decisionsDisabled || savingRef.current) return;
        if (row.kind === 'correction' && approve && !row.decisionDate) return;
        const callback = row.kind === 'vacation'
            ? approve ? onApproveVacation : onDenyVacation
            : approve ? onApproveCorrection : onDenyCorrection;
        if (!callback) return;
        const ids = row.kind === 'vacation' ? [row.vacation.id] : row.pendingEntries.map(entry => entry.id);
        if (!ids.length) return;
        const approvesWholeDay = row.kind === 'correction' && approve;
        const mutationIds = approvesWholeDay ? [ids[0]] : ids;
        savingRef.current = true;
        setSavingKey(row.key); setDecisionError(null); setFeedback('');
        try {
            // Approval is atomic for the whole desired day on the server. Denial
            // is per record, so settle every denial before offering a safe retry.
            const results = await Promise.allSettled(mutationIds.map(id => Promise.resolve().then(() => callback(id, notes[row.key] || ''))));
            const successes = approvesWholeDay
                ? results[0].status === 'fulfilled' ? ids : []
                : ids.filter((_, index) => results[index].status === 'fulfilled');
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
            {row.kind === 'correction' ? <>{row.decisionDate && <p>{t('adminWorkspace.replaceDayStamps', 'Bei Genehmigung werden die bisherigen Stempel dieses Tages durch die offenen Änderungen ersetzt.')}</p>}<div className="aw-compare-list">{row.entries.map(entry => <CorrectionComparison key={entry.id} entry={entry} t={t} completed={completedIds.has(`correction:${entry.id}`)} />)}</div><div className="aw-reason"><span className="aw-detail-label">{t('adminWorkspace.groupReasons', 'Begründungen der Tageskorrekturen')}</span>{row.reasons.map((reason, reasonIndex) => <p key={reasonIndex}>{reason || t('adminWorkspace.noReason', 'Keine Begründung hinterlegt.')}</p>)}</div>{!row.decisionDate && <p className="aw-error" role="alert">{t('adminWorkspace.unknownCorrectionDay', 'Der gewünschte Kalendertag fehlt. Bitte zuerst in der Zeitprüfung kontrollieren; eine Tagesfreigabe ist hier nicht möglich.')}</p>}</> : renderVacationDetails(row)}
            <label className="aw-note"><span>{t('adminWorkspace.decisionNote', 'Kommentar zur Entscheidung')} <small>· {t('optional', 'optional')}</small></span><textarea value={notes[row.key] || ''} disabled={!canManage || Boolean(savingKey)} rows={2} onChange={event => setNotes(previous => ({ ...previous, [row.key]: event.target.value }))} /></label>
            {row.kind === 'correction' && row.pendingEntries.length > 1 && <p>{row.pendingEntries.length} {t('adminWorkspace.dayGroupDecision', 'offene Änderungen dieses Kalendertags werden gemeinsam entschieden.')}</p>}
            {decisionError?.key === row.key && <p className="aw-error" role="alert">{decisionError.message}</p>}
            <div className="aw-detail-actions"><button type="button" className="aw-text-button" onClick={() => focus(row.username, row.dateIso)}>{t('adminWorkspace.openTime', 'In Zeitprüfung öffnen')} ↗</button><div>
                <button type="button" className="aw-button" disabled={decisionsDisabled || !(row.kind === 'vacation' ? onDenyVacation : onDenyCorrection)} onClick={() => decide(row, false)}>{t('deny', 'Ablehnen')}</button>
                <button type="button" className="aw-button aw-button-primary" disabled={decisionsDisabled || (row.kind === 'correction' && !row.decisionDate) || !(row.kind === 'vacation' ? onApproveVacation : onApproveCorrection)} onClick={() => decide(row, true)}>{savingKey === row.key ? t('saving', 'Wird gespeichert…') : t('approve', 'Genehmigen')}</button>
            </div></div>
        </>}
    </div>;

    const renderTask = (row, index) => {
        const name = nameOf(row.username);
        const open = expandedKey === row.key;
        const title = row.kind === 'time' ? workspaceIssueLabel(row.issues?.[0]?.type, t) : row.kind === 'vacation' ? t('adminWorkspace.vacation', 'Urlaub') : t('adminWorkspace.correction', 'Zeitkorrektur');
        const date = row.kind === 'vacation' ? periodLabel(row.vacation.startDate, row.vacation.endDate) : affectedDate(row) ? displayDate(affectedDate(row)) : '';
        return <article className={`aw-task${open ? ' is-expanded' : ''}`} key={row.key} data-task-key={row.key}>
            <div className="aw-task-row"><span className="aw-avatar" aria-hidden="true">{name.split(/\s+/).slice(0, 2).map(word => word[0]).join('')}</span><div className="aw-task-copy"><div className="aw-task-title"><button type="button" className="aw-task-name" onClick={() => onOpenEmployee?.(row.username)}>{name}</button><span className={`aw-task-timing${taskOrder(row, todayIso).priority === 0 ? ' is-near' : ''}`}>{timingLabel(row)}</span></div><div className="aw-task-meta"><span>{title}</span>{date && <span> · {date}</span>}{row.kind === 'correction' && row.pendingEntries.length > 1 && <span> · {row.pendingEntries.length} {t('changes', 'Änderungen')}</span>}</div></div>
                {row.kind === 'time' && <button type="button" className="aw-text-button" aria-label={`${t('details', 'Details')}: ${name}`} aria-expanded={open} aria-controls={`${idPrefix}-detail-${index}`} onClick={() => toggle(row.key)}>{open ? '−' : '+'}</button>}
                <button type="button" className={`aw-review-button${open ? ' is-expanded' : ''}`} aria-expanded={row.kind === 'time' ? undefined : open} aria-controls={row.kind === 'time' ? undefined : `${idPrefix}-detail-${index}`} aria-label={`${row.kind !== 'time' && open ? t('close', 'Schließen') : t('adminWorkspace.review', 'Prüfen')}: ${name} · ${title}`} onClick={() => row.kind === 'time' ? focus(row.username, row.dateIso) : toggle(row.key)}>{row.kind !== 'time' && open ? t('close', 'Schließen') : t('adminWorkspace.review', 'Prüfen')} <span aria-hidden="true">{row.kind === 'time' ? '↗' : open ? '⌃' : '⌄'}</span></button>
            </div>{open && renderDetail(row, index)}
        </article>;
    };

    const renderAbsence = item => <div className="aw-absence" key={item.key}><div className="aw-absence-copy"><button type="button" className="aw-task-name" onClick={() => onOpenEmployee?.(item.username)}>{nameOf(item.username)}</button><p>{periodLabel(item.startDate, item.endDate)}</p><span className={`aw-absence-kind${item.kind === 'sick' ? ' is-sick' : ''}`}>{item.kind === 'sick' ? t('sick', 'Krank') : item.usesOvertime ? t('adminWorkspace.overtimeLeave', 'Überstundenfrei') : t('adminWorkspace.vacation', 'Urlaub')}{item.halfDay ? ` · ${t('adminDashboard.halfDayShort', '½ Tag')}` : ''}</span></div><button type="button" className="aw-text-button" aria-label={`${t('details', 'Details')}: ${nameOf(item.username)} · ${periodLabel(item.startDate, item.endDate)}`} onClick={() => onOpenAbsence?.({ ...item.source, kind: item.kind, type: item.kind, dateIso: item.startIso, raw: item.source })}>↗</button></div>;
    const noData = !requestRows.length && !timeRows.length;
    return <div className={`aw-overview${compactDesktop ? ' aw-overview-compact' : ''}`} aria-busy={loading || initialLoading}>
        <div className="aw-toolbar"><div className="aw-data-context"><span className="aw-date">{formatDate(today)}</span><span>{dataContextLabel}{loadError ? ` · ${t('adminWorkspace.notCurrent', 'Nicht aktuell')}` : ''}</span></div>{teams.length > 0 && <label className="aw-team-select">{t('team', 'Team')}<select value={selectedTeam} onChange={event => onTeamChange?.(event.target.value)}><option value="">{t('adminWorkspace.allTeams', 'Alle Teams')}</option>{teams.filter(team => team.value !== '').map(team => <option value={team.value} key={team.value}>{team.label}</option>)}</select></label>}</div>
        {loadError && <div className="aw-error" role="alert"><span>{t('adminWorkspace.loadFailed', 'Die Daten konnten nicht vollständig aktualisiert werden. Freigaben sind bis zur Aktualisierung gesperrt.')}</span>{typeof loadError === 'string' && <p>{loadError}</p>}<button type="button" className="aw-button" onClick={onRetry} disabled={loading}>{t('retry', 'Erneut versuchen')}</button></div>}
        {feedback && <div className="aw-feedback" role="status"><span>{feedback}</span><button type="button" className="aw-text-button" aria-label={t('close', 'Schließen')} onClick={() => setFeedback('')}>×</button></div>}
        <div className="aw-metrics" role="group" aria-label={t('adminWorkspace.keyFigures', 'Kennzahlen und Schnellzugriff')}>
            <button type="button" className="aw-metric" data-metric="requests" onClick={() => chooseFilter('request')} aria-label={t('adminWorkspace.showOpenRequests', 'Offene Anträge anzeigen')}><span className="aw-metric-label">{t('adminWorkspace.openRequestsLabel', 'Offene Anträge')}<span aria-hidden="true">↘</span></span><strong>{dataUnknown ? '–' : requestRows.length}</strong><small>{dataUnknown ? unknownLabel : `${vacationCount} ${t('adminWorkspace.vacation', 'Urlaub')} · ${correctionCount} ${t('adminWorkspace.correctionsShort', 'Korrekturen')}`}</small></button>
            <button type="button" className="aw-metric" data-metric="issues" onClick={() => chooseFilter('time')} aria-label={t('adminWorkspace.showPeopleWithIssues', 'Mitarbeitende mit Zeitproblemen anzeigen')}><span className="aw-metric-label">{t('adminWorkspace.peopleWithIssues', 'Mit Zeitproblemen')}<span aria-hidden="true">↘</span></span><strong>{dataUnknown || issueRows === null ? '–' : affectedCount}</strong><small>{dataUnknown ? unknownLabel : issueRows === null ? t('adminWorkspace.calculatingShort', 'Wird berechnet…') : t('adminWorkspace.affectedPeople', 'betroffene Mitarbeitende')}</small></button>
            <button type="button" className="aw-metric" data-metric="absences" onClick={onOpenCalendar} aria-label={t('adminWorkspace.showTodayAbsences', 'Heutige Abwesenheiten im Kalender anzeigen')}><span className="aw-metric-label">{t('adminWorkspace.absentToday', 'Heute abwesend')}<span aria-hidden="true">↗</span></span><strong>{dataUnknown ? '–' : activeCount}<span className="aw-metric-denominator">{!dataUnknown && ` / ${employeeCount}`}</span></strong><small>{dataUnknown ? unknownLabel : t('adminWorkspace.absencesOfTeam', 'Mitarbeitende im gewählten Team')}</small></button>
            <button type="button" className="aw-metric" data-metric="negative" onClick={onFocusNegativeBalances} aria-label={t('adminWorkspace.showNegativeBalances', 'Negative Zeitkonten anzeigen')} disabled={!onFocusNegativeBalances}><span className="aw-metric-label">{t('adminWorkspace.negativeBalances', 'Negative Zeitkonten')}<span aria-hidden="true">↗</span></span><strong className={!dataUnknown && negativeCount ? 'aw-negative' : ''}>{dataUnknown || !balances.length ? '–' : negativeCount}</strong><small>{dataUnknown ? unknownLabel : balances.length ? `${t('adminWorkspace.balanceAvailableFor', 'Saldo für')} ${balances.length} ${t('adminWorkspace.people', 'Personen')}` : t('adminWorkspace.noBalances', 'Keine Salden vorhanden')}</small></button>
        </div>
        <div className={`aw-grid${!preferences.team ? ' aw-grid-single' : ''}`}><section className="aw-inbox" ref={inboxRef} tabIndex={-1} aria-label={t('adminWorkspace.tasks', 'Zu erledigen')}><div className="aw-section-heading"><div><h2>{t('adminWorkspace.tasks', 'Zu erledigen')}</h2><p>{t('adminWorkspace.affectedDateOrder', 'Zeitnaher Urlaub zuerst, sonst nach betroffenem Datum.')}</p></div><span className="aw-preview-label">{t('adminWorkspace.preview', 'Vorschau')}</span></div>
            <div className="aw-filters" role="group" aria-label={t('adminWorkspace.filterTasks', 'Vorgänge filtern')}>{[['all', t('all', 'Alle'), null], ['request', t('adminWorkspace.requests', 'Anträge'), dataUnknown ? '–' : requestRows.length], ['time', t('adminWorkspace.timeReview', 'Zeitprüfung'), dataUnknown || issueRows === null ? '–' : timeRows.length]].map(([key, label, count]) => <button type="button" key={key} className={filter === key ? 'is-active' : ''} aria-pressed={filter === key} onClick={() => { setFilter(key); setShowAll(false); }}>{label}{count !== null && <span className="aw-count">{count}</span>}</button>)}</div>
            <div className="aw-review-context"><span>{t('adminWorkspace.timeReview', 'Zeitprüfung')}: {issuePeriod || t('adminWorkspace.periodNotAvailable', 'Zeitraum noch nicht verfügbar')}{issueRows === null && !initialLoading ? ` · ${t('adminWorkspace.calculatingShort', 'Wird berechnet…')}` : ''}</span><button type="button" className="aw-text-button" onClick={onOpenTime}>{t('adminWorkspace.changePeriod', 'Zeitraum ändern')} ↗</button></div>
            <div className="aw-task-list">{!loadError && (loading || initialLoading) && noData ? <p className="aw-loading" role="status">{t('adminWorkspace.loadingTasks', 'Vorgänge werden geladen…')}</p> : shownRows.length ? shownRows.map(renderTask) : <div className="aw-empty">{loadError ? t('adminWorkspace.noReliableTasks', 'Für diese Ansicht liegen keine vollständig geladenen Vorgänge vor.') : issueRows === null && filter !== 'request' ? t('adminWorkspace.calculatingIssues', 'Zeitprüfung wird berechnet…') : t('adminWorkspace.noOpenTasks', 'Keine offenen Vorgänge in dieser Ansicht.')}</div>}</div>
            {!canManage && <p className="aw-empty">{t('adminDashboard.readOnlyPermissions', 'Nur Ansicht: Freigaben und Änderungen sind nicht erlaubt.')}</p>}
            <div className="aw-inbox-foot"><span>{dataUnknown || (issueRows === null && filter !== 'request') ? t('adminWorkspace.reliableCountUnavailable', 'Anzahl derzeit nicht verfügbar') : `${shownRows.length} ${t('of', 'von')} ${chosenRows.length} ${t('adminWorkspace.items', 'Vorgängen')}`}</span><div>{chosenRows.length > previewLimit && <button type="button" className="aw-text-button" onClick={() => setShowAll(value => !value)}>{showAll ? t('showLess', 'Weniger anzeigen') : `${t('adminWorkspace.showAllTasks', 'Alle anzeigen')} (${chosenRows.length})`}</button>}<button type="button" className="aw-text-button" onClick={filter === 'time' ? onOpenTime : onOpenRequests}>{filter === 'time' ? t('adminWorkspace.openTime', 'Zeitprüfung öffnen') : t('adminWorkspace.openRequests', 'Anträge öffnen')} ↗</button></div></div>
        </section>{preferences.team && <aside className="aw-team-panel"><div className="aw-section-heading"><div><h2>{t('adminWorkspace.teamAtGlance', 'Team im Blick')}</h2><p>{selectedTeamLabel}</p></div><button type="button" className="aw-text-button" onClick={onOpenCalendar}>{t('adminWorkspace.calendarShort', 'Kalender')} ↗</button></div><div className="aw-today"><div className="aw-coming-head"><h3>{t('adminWorkspace.absentToday', 'Heute abwesend')}</h3><strong className="aw-team-count">{dataUnknown ? '–' : activeCount}</strong></div><div className="aw-absence-list">{activeAbsences.length ? activeAbsences.slice(0, absencePreviewLimit).map(renderAbsence) : <p className="aw-empty">{absenceEmptyLabel(t('adminWorkspace.noAbsenceToday', 'Keine Abwesenheiten eingetragen.'))}</p>}</div>{activeAbsences.length > absencePreviewLimit && <button type="button" className="aw-text-button aw-more-absences" onClick={onOpenCalendar}>+{activeAbsences.length - absencePreviewLimit} {t('adminWorkspace.moreInCalendar', 'weitere im Kalender')} ↗</button>}</div><div className="aw-coming"><div className="aw-coming-head"><h3>{t('adminWorkspace.nextThirtyDays', 'Nächste 30 Tage')}</h3><span>{t('until', 'Bis')} {displayDate(rangeEndIso)}</span></div><div className="aw-absence-list">{upcomingAbsences.length ? upcomingAbsences.slice(0, absencePreviewLimit).map(renderAbsence) : <p className="aw-empty">{absenceEmptyLabel(t('adminWorkspace.noComingAbsence', 'Keine Abwesenheiten in den nächsten 30 Tagen geplant.'))}</p>}</div>{upcomingAbsences.length > absencePreviewLimit && <button type="button" className="aw-text-button aw-more-absences" onClick={onOpenCalendar}>+{upcomingAbsences.length - absencePreviewLimit} {t('adminWorkspace.moreInCalendar', 'weitere im Kalender')} ↗</button>}</div></aside>}</div>
        {preferences.balances && <section className="aw-balances" aria-label={t('adminWorkspace.balances', 'Zeitkonten')}><div className="aw-balance-title"><h3>{t('adminWorkspace.balances', 'Zeitkonten')}</h3><small>{dataUnknown ? unknownLabel : balances.length ? `${balances.length} ${t('adminWorkspace.accountsWithData', 'Konten mit Saldo')}` : t('adminWorkspace.noBalances', 'Keine Salden vorhanden')}</small></div><button type="button" className="aw-balance-stat" onClick={onFocusNegativeBalances} disabled={dataUnknown || !balances.length}><small>{t('adminWorkspace.averageBalance', 'Ø Team-Saldo')}</small><strong className={!dataUnknown && average < 0 ? 'aw-negative' : !dataUnknown && average > 0 ? 'aw-positive' : ''}>{dataUnknown || average === null ? '–' : minutesToHHMM(average)}</strong><span>{t('adminWorkspace.perEmployee', 'pro Person')}</span></button><button type="button" className="aw-balance-stat" onClick={onFocusOvertimeLeaders} disabled={dataUnknown || !leader}><small>{t('adminWorkspace.highestBalance', 'Höchster Saldo')}</small><strong className={!dataUnknown && leader?.trackingBalance < 0 ? 'aw-negative' : !dataUnknown && leader?.trackingBalance > 0 ? 'aw-positive' : ''}>{!dataUnknown && leader ? minutesToHHMM(leader.trackingBalance) : '–'}</strong><span>{!dataUnknown && leader ? nameOf(leader.username) : '–'}</span></button></section>}
        <footer className="aw-footer"><button type="button" className="aw-text-button" onClick={onPrint}>{t('adminWorkspace.print', 'Zeiten drucken')}</button><button type="button" className="aw-text-button" onClick={onOpenModules}>{t('adminWorkspace.modules', 'Alle Module')}</button><details className="aw-settings"><summary>{t('adminWorkspace.customize', 'Ansicht anpassen')}</summary><label><input type="checkbox" checked={preferences.team} onChange={event => updatePreference('team', event.target.checked)} />{t('adminWorkspace.teamAtGlance', 'Team im Blick')}</label><label><input type="checkbox" checked={preferences.balances} onChange={event => updatePreference('balances', event.target.checked)} />{t('adminWorkspace.balances', 'Zeitkonten')}</label></details></footer>
    </div>;
}
