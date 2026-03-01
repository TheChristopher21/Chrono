// src/pages/PercentageDashboard/PercentageWeekOverview.jsx
import React, { useState } from 'react';
import PropTypes from 'prop-types';
import { useAuth } from '../../context/AuthContext.jsx';

import {
    addDays,
    formatLocalDate,
    minutesToHHMM,
    formatDate,
    getMondayOfWeek,
    sortEntries
} from './percentageDashUtils';

import '../../styles/HourlyDashboardScoped.css'; // Einheitliches Design verwenden
import CustomerTimeAssignModal from '../../components/CustomerTimeAssignModal';
import '../../styles/PercentageDashboardScoped.css';

const PercentageWeekOverview = ({
                                    t,
                                    dailySummaries,
                                    monday,
                                    setMonday,
                                    weeklyWorked,
                                    weeklyExpected,
                                    weeklyDiff,
                                    handleManualPunch,
                                    openCorrectionModal,
                                    userProfile,
                                    customers,
                                    recentCustomers,
                                    projects,
                                    tasks,
                                    selectedCustomerId,
                                    setSelectedCustomerId,
                                    selectedProjectId,
                                    setSelectedProjectId,
                                    selectedTaskId,
                                    setSelectedTaskId,
                                    vacationRequests,
                                    sickLeaves,
                                    holidaysForUserCanton,
                                    reloadData,
                                    editingNote,
                                    setEditingNote,
                                    noteContent,
                                    setNoteContent,
                                    handleNoteSave
                                }) => {
    const { currentUser } = useAuth();
    const [modalInfo, setModalInfo] = useState({ isVisible: false, day: null, summary: null });
    const isCustomerTrackingEnabled = userProfile?.customerTrackingEnabled || currentUser?.customerTrackingEnabled;
    const weekDates = Array.from({ length: 7 }, (_, i) => addDays(monday, i));

    function handleWeekJump(e) {
        const picked = new Date(e.target.value + "T00:00:00");
       if (!isNaN(picked.getTime())) {
           setMonday(getMondayOfWeek(picked));
       }
    }

    function handleCurrentWeek() {
        setMonday(getMondayOfWeek(new Date()));
    }

    return (
        <section className="weekly-overview content-section">
            <h3 className="section-title">{t('weeklyOverview', "Wochenübersicht")}</h3>

            <div className="punch-section">
                <h4>{t("manualPunchTitle", "Manuelles Stempeln")}</h4>
                {isCustomerTrackingEnabled && (
                    <div className="customer-project-selectors">
                        <select value={selectedCustomerId} onChange={e => setSelectedCustomerId(e.target.value)}>
                            <option value="">{t('noCustomer')}</option>
                            {recentCustomers.length > 0 && (
                                <optgroup label={t('recentCustomers')}>
                                    {recentCustomers.map(c => (<option key={'r'+c.id} value={c.id}>{c.name}</option>))}
                                </optgroup>
                            )}
                            {customers.map(c => (<option key={c.id} value={c.id}>{c.name}</option>))}
                        </select>
                        <select value={selectedProjectId} onChange={e => setSelectedProjectId(e.target.value)}>
                            <option value="">{t('noProject','Kein Projekt')}</option>
                            {projects.map(p => (<option key={p.id} value={p.id}>{p.name}</option>))}
                        </select>
                        <select value={selectedTaskId} onChange={e => setSelectedTaskId(e.target.value)}>
                            <option value="">{t('noTask','Keine Aufgabe')}</option>
                            {tasks.map(task => (<option key={task.id} value={task.id}>{task.name}</option>))}
                        </select>
                    </div>
                )}
                <button onClick={handleManualPunch} className="button-primary">
                    {t("manualPunchButton", "Jetzt stempeln")}
                </button>
            </div>

            <div className="week-navigation">
                <button onClick={() => setMonday(prev => addDays(prev, -7))} className="button-secondary">
                    ← {t('prevWeek', "Vorige Woche")}
                </button>
                <input
                    type="date"
                    value={formatLocalDate(monday)}
                    onChange={handleWeekJump}
                />
                <button onClick={() => setMonday(prev => addDays(prev, 7))} className="button-secondary">
                    {t('nextWeek', "Nächste Woche")} →
                </button>
                <button onClick={handleCurrentWeek} className="button-secondary">{t('currentWeek', 'Aktuelle Woche')}</button>
            </div>

            <div className="weekly-summary">
                <div className="summary-item">
                    <span className="summary-label">{t('actualTime', 'Ist (Woche)')}</span>
                    <span className="summary-value">{minutesToHHMM(weeklyWorked)}</span>
                </div>
                <div className="summary-item">
                    <span className="summary-label">{t('expected', 'Soll (Woche)')}</span>
                    <span className="summary-value">{minutesToHHMM(weeklyExpected)}</span>
                </div>
                <div className="summary-item">
                    <span className="summary-label">{t('weekBalance', 'Saldo (Woche)')}</span>
                    <span className={`summary-value ${(weeklyDiff ?? 0) < 0 ? 'balance-negative' : 'balance-positive'}`}>
                        {minutesToHHMM(weeklyDiff)}
                    </span>
                </div>
            </div>

            <div className="week-display">
                {weekDates.map((dayObj) => {
                    const isoDate = formatLocalDate(dayObj);
                    const summary = dailySummaries.find(s => s.date === isoDate);
                    const dayName = dayObj.toLocaleDateString('de-DE', { weekday: 'long' });

                    const vacationToday = vacationRequests.find(v => v.approved && isoDate >= v.startDate && isoDate <= v.endDate);
                    const sickToday = sickLeaves.find(sl => isoDate >= sl.startDate && isoDate <= sl.endDate);
                    const holidayName = holidaysForUserCanton && holidaysForUserCanton[isoDate];

                    let dayClass = 'day-card';
                    if (vacationToday) dayClass += ' vacation-day';
                    if (sickToday) dayClass += ' sick-day';
                    if (holidayName) dayClass += ' holiday-day';

                    const dailyWorkedMins = summary?.workedMinutes || 0;
                    const projectNames = Array.from(
                        new Set(
                            (summary?.entries || [])
                                .map(entry => entry.projectName || (projects || []).find(p => String(p.id) === String(entry.projectId))?.name || '')
                                .filter(Boolean)
                        )
                    );
                    const hasProjects = projectNames.length > 0;
                    const showProjectBadge = isCustomerTrackingEnabled && hasProjects;
                    const projectBadgeBaseLabel = t('assignCustomer.projectTag', 'Projektzeit');
                    const projectBadgeLabel = projectNames.length === 1
                        ? projectNames[0]
                        : `${projectBadgeBaseLabel}${projectNames.length > 1 ? ` (${projectNames.length})` : ''}`;
                    const projectBadgeTitle = projectNames.join(', ');

                    return (
                        <div key={isoDate} className={dayClass}>
                            <div className="day-card-header">
                                <div className="day-card-header-main">
                                    <h3>{dayName}, {formatDate(dayObj)}</h3>
                                    {showProjectBadge && (
                                        <div className="day-card-badges">
                                            <span
                                                className="day-card-badge project-badge"
                                                title={projectBadgeTitle || undefined}
                                            >
                                                {projectBadgeLabel}
                                            </span>
                                        </div>
                                    )}
                                </div>
                            </div>
                            <div className="day-card-body">
                                {holidayName ? (
                                    <div className="day-card-info holiday-indicator">🎉 {holidayName}</div>
                                ) : vacationToday ? (
                                    vacationToday.companyVacation ? (
                                        <>
                                            <div className="day-card-info vacation-indicator">🏖️ {t('onVacation', 'Im Urlaub')} {vacationToday.halfDay && `(${t('halfDayShort', '½ Tag')})`}</div>
                                            <div className="daily-summary-times">
                                                <p><strong>{t('actualTime', 'Gearbeitet')}:</strong> {minutesToHHMM(dailyWorkedMins)}</p>
                                                <p><strong>{t('breakTime', 'Pause')}:</strong> {minutesToHHMM(summary?.breakMinutes || 0)}</p>
                                            </div>
                                            <ul className="time-entry-list" style={{marginTop: '1rem'}}>
                                                {sortEntries(summary?.entries).map(entry => (
                                                    <li key={entry.id || entry.entryTimestamp} style={{backgroundColor: entry.customerId ? `hsl(${(entry.customerId * 57) % 360}, var(--customer-color-saturation), var(--customer-color-lightness))` : 'transparent'}}>
                                                        <span className="entry-label">{t(`punchTypes.${entry.punchType}`, entry.punchType)}:</span>
                                                        <span className="entry-time">{entry.entryTimestamp ? new Date(entry.entryTimestamp).toLocaleTimeString('de-CH', { hour: '2-digit', minute: '2-digit' }) : ''}</span>
                                                    </li>
                                                ))}
                                            </ul>
                                            {(summary?.entries || []).length === 0 && <p className='no-entries'>{t('noEntries')}</p>}
                                        </>
                                    ) : (
                                        <div className="day-card-info vacation-indicator">🏖️ {t('onVacation', 'Im Urlaub')} {vacationToday.halfDay && `(${t('halfDayShort', '½ Tag')})`}</div>
                                    )
                                ) : sickToday ? (
                                    <div className="day-card-info sick-leave-indicator">⚕️ {t('sickLeave.sick', 'Krank')} {sickToday.halfDay && `(${t('halfDayShort', '½ Tag')})`}</div>
                                ) : (
                                    <>
                                        <div className="daily-summary-times">
                                            <p><strong>{t('actualTime', 'Gearbeitet')}:</strong> {minutesToHHMM(dailyWorkedMins)}</p>
                                            <p><strong>{t('breakTime', 'Pause')}:</strong> {minutesToHHMM(summary?.breakMinutes || 0)}</p>
                                        </div>
                                        <ul className="time-entry-list" style={{marginTop: '1rem'}}>
                                            {sortEntries(summary?.entries).map(entry => (
                                                <li key={entry.id || entry.entryTimestamp} style={{backgroundColor: entry.customerId ? `hsl(${(entry.customerId * 57) % 360}, var(--customer-color-saturation), var(--customer-color-lightness))` : 'transparent'}}>
                                                    <span className="entry-label">{t(`punchTypes.${entry.punchType}`, entry.punchType)}:</span>
                                                    <span className="entry-time">{entry.entryTimestamp ? new Date(entry.entryTimestamp).toLocaleTimeString('de-CH', { hour: '2-digit', minute: '2-digit' }) : ''}</span>
                                                </li>
                                            ))}
                                        </ul>
                                        {(summary?.entries || []).length === 0 && <p className='no-entries'>{t('noEntries')}</p>}
                                        <div className="daily-note-container">
                                            {editingNote === isoDate ? (
                                                <>
                                                    <textarea className="daily-note-editor" value={noteContent} onChange={(e) => setNoteContent(e.target.value)} rows="4" placeholder={t('enterNotePlaceholder', "Notiz eingeben...")} />
                                                    <div className="note-buttons">
                                                        <button className="button-primary" onClick={() => handleNoteSave(isoDate, noteContent)}>{t('save')}</button>
                                                        <button className="button-secondary" onClick={() => setEditingNote(null)}>{t('cancel')}</button>
                                                    </div>
                                                </>
                                            ) : (
                                                <div className="daily-note-display">
                                                    <div className="note-content">
                                                        <strong>{t('dailyNoteTitle', 'Notiz')}:</strong>
                                                        <p>{summary?.dailyNote || t('noNotePlaceholder', 'Keine Notiz.')}</p>
                                                    </div>
                                                    <button className="button-edit-note" title={t('editNote', 'Notiz bearbeiten')} onClick={() => { setEditingNote(isoDate); setNoteContent(summary?.dailyNote || ''); }}>✏️</button>
                                                </div>
                                            )}
                                        </div>
                                    </>
                                )}
                            </div>
                            <div className="correction-button-row">
                                <button onClick={() => openCorrectionModal(dayObj, summary)} className="button-secondary">
                                    {t('submitCorrectionRequest', 'Korrektur anfragen')}
                                </button>
                                {summary && summary.entries.length > 0 && (
                                    <button onClick={() => setModalInfo({ isVisible: true, day: dayObj, summary })} className="button-primary-outline" style={{marginTop: '0.5rem'}}>
                                        {t('assignCustomer.editButton', 'Kunden & Zeiten bearbeiten')}
                                    </button>
                                )}
                            </div>
                        </div>
                    );
                })}
            </div>

            {modalInfo.isVisible && (
                <CustomerTimeAssignModal t={t} day={modalInfo.day} summary={modalInfo.summary} customers={customers} projects={projects}
                                         onClose={() => setModalInfo({ isVisible: false, day: null, summary: null })}
                                         onSave={() => { if (reloadData) reloadData(); setModalInfo({ isVisible: false, day: null, summary: null }); }} />
            )}
        </section>
    );
};

PercentageWeekOverview.propTypes = {
    t: PropTypes.func.isRequired,
    dailySummaries: PropTypes.array.isRequired,
    monday: PropTypes.instanceOf(Date).isRequired,
    setMonday: PropTypes.func.isRequired,
    weeklyWorked: PropTypes.number.isRequired,
    weeklyExpected: PropTypes.number.isRequired,
    weeklyDiff: PropTypes.number.isRequired,
    handleManualPunch: PropTypes.func.isRequired,
    openCorrectionModal: PropTypes.func.isRequired,
    userProfile: PropTypes.object.isRequired,
    customers: PropTypes.array,
    recentCustomers: PropTypes.array,
    projects: PropTypes.array,
    tasks: PropTypes.array,
    selectedCustomerId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    setSelectedCustomerId: PropTypes.func,
    selectedProjectId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    setSelectedProjectId: PropTypes.func,
    selectedTaskId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    setSelectedTaskId: PropTypes.func,
    vacationRequests: PropTypes.array.isRequired,
    sickLeaves: PropTypes.array.isRequired,
    holidaysForUserCanton: PropTypes.object,
    reloadData: PropTypes.func,
    editingNote: PropTypes.string,
    setEditingNote: PropTypes.func.isRequired,
    noteContent: PropTypes.string.isRequired,
    setNoteContent: PropTypes.func.isRequired,
    handleNoteSave: PropTypes.func.isRequired,
};

export default PercentageWeekOverview;