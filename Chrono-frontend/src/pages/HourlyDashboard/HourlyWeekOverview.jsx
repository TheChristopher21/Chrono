// src/pages/HourlyDashboard/HourlyWeekOverview.jsx
import React, { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import {
    addDays,
    formatLocalDate,
    formatDate,
    formatTime,
    getMondayOfWeek,
    minutesToHHMM,
    isLateTime,
    formatPunchedTimeFromEntry
} from './hourDashUtils';
import '../../styles/HourlyDashboardScoped.css';
import api from "../../utils/api.js";
import TrendChart from '../../components/TrendChart';
import CustomerTimeAssignModal from '../../components/CustomerTimeAssignModal';

const HourlyWeekOverview = ({
                                t,
                                dailySummaries,
                                openCorrectionModal,
                                selectedMonday,
                                setSelectedMonday,
                                weeklyTotalMins,
                                monthlyTotalMins,
                                handleManualPunch,
                                punchMessage,
                                userProfile,
                                customers,
                                recentCustomers,
                                projects,
                                selectedCustomerId,
                                setSelectedCustomerId,
                                selectedProjectId,
                                setSelectedProjectId,
                                assignCustomerForDay,
                                assignProjectForDay,
                                // Diese Props müssten vom HourlyDashboard kommen, um Speichern & Benachrichtigungen zu ermöglichen:
                                // notify,
                                // fetchDataForUser,
                                reloadData,
                            }) => {

    const [editingNote, setEditingNote] = useState(null); // Speichert das Datum des Tages, dessen Notiz bearbeitet wird
    const [noteContent, setNoteContent] = useState('');   // Speichert den Inhalt der Notiz während der Bearbeitung
    const [modalInfo, setModalInfo] = useState({ isVisible: false, day: null, summary: null });


    // Initialize customer selections and ranges from existing entries


    const weekDates = selectedMonday
        ? Array.from({ length: 7 }, (_, i) => addDays(selectedMonday, i))
        : [];

    const chartData = weekDates.map(d => {
        const iso = formatLocalDate(d);
        const summary = dailySummaries.find(s => s.date === iso);
        return { date: iso, workedMinutes: summary ? summary.workedMinutes : 0 };
    });

    const weeklyEarnings = userProfile?.hourlyRate
        ? (weeklyTotalMins / 60) * userProfile.hourlyRate
        : null;

    function handlePrevWeek() {
        setSelectedMonday(prev => addDays(prev, -7));
    }
    function handleNextWeek() {
        setSelectedMonday(prev => addDays(prev, 7));
    }
    function handleWeekJump(e) {
        const picked = new Date(e.target.value + "T00:00:00");
        if (!isNaN(picked.getTime())) {
            setSelectedMonday(getMondayOfWeek(picked));
        }
    }

    const handleNoteSave = async (isoDate, content) => {
        if (!userProfile?.username) return;
        try {
            // HINWEIS: Dieser API-Endpunkt muss im Backend existieren
            await api.post('/api/timetracking/daily-note', { note: content }, {
                params: {
                    username: userProfile.username,
                    date: isoDate
                }
            });
            // Benachrichtigung anzeigen (müsste als Prop kommen)
            // notify(t("dailyNoteSaved"));
            // Daten neu laden, um die Ansicht zu aktualisieren (müsste als Prop kommen)
            // fetchDataForUser();
        } catch (err) {
            console.error('Fehler beim Speichern der Tagesnotiz:', err);
            // Fehler-Benachrichtigung (müsste als Prop kommen)
            // notify(t("dailyNoteError"));
        } finally {
            // Bearbeitungsmodus beenden, egal ob erfolgreich oder nicht
            setEditingNote(null);
        }
    };



    return (
        <section className="weekly-overview content-section">
            {/* Dein bestehender Header bleibt unverändert */}
            <h3 className="section-title">{t("weeklyOverview", "Wochenübersicht")}</h3>
            {punchMessage && <div className="punch-message">{punchMessage}</div>}
            <div className="punch-section">
                <h4>{t("manualPunchTitle", "Manuelles Stempeln")}</h4>
                {userProfile?.customerTrackingEnabled && (
                    <>
                        <select value={selectedCustomerId} onChange={e => setSelectedCustomerId(e.target.value)}>
                            <option value="">{t('noCustomer')}</option>
                            {recentCustomers.length > 0 && (
                                <optgroup label={t('recentCustomers')}>
                                    {recentCustomers.map(c => (
                                        <option key={'r'+c.id} value={c.id}>{c.name}</option>
                                    ))}
                                </optgroup>
                            )}
                            {customers.map(c => (
                                <option key={c.id} value={c.id}>{c.name}</option>
                            ))}
                        </select>
                        <select value={selectedProjectId} onChange={e => setSelectedProjectId(e.target.value)}>
                            <option value="">{t('noProject','Kein Projekt')}</option>
                            {projects.map(p => (
                                <option key={p.id} value={p.id}>{p.name}</option>
                            ))}
                        </select>
                    </>
                )}
                <button id="punch-button" onClick={handleManualPunch} className="button-primary">
                    {t("manualPunchButton", "Jetzt stempeln")}
                </button>
            </div>
            <div className="week-navigation">
                <button onClick={handlePrevWeek} className="button-secondary">← {t("prevWeek", "Vorige Woche")}</button>
                <input
                    type="date"
                    onChange={handleWeekJump}
                    value={formatLocalDate(selectedMonday)}
                />
                <button onClick={handleNextWeek} className="button-secondary">{t("nextWeek", "Nächste Woche")} →</button>
            </div>
            <div className="weekly-monthly-totals">
                <div className="summary-item">
                    <span className="summary-label">{t('weeklyHours', 'Ges. Std. (Woche)')}</span>
                    <span className="summary-value">{minutesToHHMM(weeklyTotalMins)}</span>
                </div>
                <div className="summary-item">
                    <span className="summary-label">{t('monthlyHours', 'Ges. Std. (Monat)')}</span>
                    <span className="summary-value">{minutesToHHMM(monthlyTotalMins)}</span>
                </div>
                {weeklyEarnings !== null && (
                    <div className="summary-item">
                        <span className="summary-label">{t('estimatedEarnings', 'gesch\u00e4tzterVerdienst')}</span>
                        <span className="summary-value">{weeklyEarnings.toFixed(2)} CHF</span>
                    </div>
                )}
            </div>
            <TrendChart data={chartData} />

            {/* Deine bestehende Wochenanzeige bleibt unverändert */}
            <div className="week-display">
                {weekDates.map((dayObj) => {
                    const isoDate = formatLocalDate(dayObj);
                    const summary = dailySummaries.find(s => s.date === isoDate);
                    const dayName = dayObj.toLocaleDateString('de-DE', { weekday: 'long' });
                    const formattedDisplayDate = formatDate(dayObj);

                    return (
                        <div key={isoDate} className={`week-day-card day-card ${summary?.needsCorrection ? 'needs-correction-highlight' : ''}`}>
                        <div className="week-day-header day-card-header">
                                <h4>{dayName}, {formattedDisplayDate}</h4>

                                {summary && summary.entries.length > 0 && (
                                    <div className="day-card-actions">
                                        <button onClick={() => setModalInfo({ isVisible: true, day: dayObj, summary })} className="button-primary-outline">
                                            {t('assignCustomer.editButton', 'Kunden & Zeiten bearbeiten')}
                                        </button>
                                    </div>
                                )}
                            </div>

                            <div className="week-day-content day-card-content">
                                {(!summary || summary.entries.length === 0) ? (
                                    <p className="no-entries">{t("noEntries", "Keine Einträge")}</p>
                                ) : (
                                    <>
                                        <ul className="time-entry-list">
                                            {/* ... (deine Listeneinträge bleiben unverändert) ... */}
                                            {summary.entries.map(entry => (
                                                <li key={entry.id || entry.entryTimestamp} style={{backgroundColor: entry.customerId ? `hsl(${(entry.customerId * 57) % 360}, var(--customer-color-saturation), var(--customer-color-lightness))` : 'transparent'}}>                                                    <span className="entry-label">{t(`punchTypes.${entry.punchType}`, entry.punchType)}:</span>
                                                    <span className={`entry-time ${isLateTime(formatTime(new Date(entry.entryTimestamp))) ? 'late-time' : ''}`}>
                                                        {formatPunchedTimeFromEntry(entry)}
                                                        {entry.source === 'SYSTEM_AUTO_END' && !entry.correctedByUser &&
                                                            <span className="auto-end-indicator" title={t('messages.autoEndedTooltip', 'Automatisch beendet')}> (A)</span>
                                                        }
                                                    </span>
                                                    {(entry.customerName || entry.projectName) && (
                                                        <span className="entry-meta">
                                                            {entry.customerName || ''}{entry.projectName ? ` / ${entry.projectName}` : ''}
                                                        </span>
                                                    )}
                                                </li>
                                            ))}
                                        </ul>
                                        <div className="daily-summary-times">
                                            <p><strong>{t('actualTime', 'Ist')}:</strong> {minutesToHHMM(summary.workedMinutes)}</p>
                                            <p><strong>{t('breakTime', 'Pause')}:</strong> {minutesToHHMM(summary.breakMinutes)}</p>
                                        </div>

                                        {/* === HIER BEGINNT DIE NEUE NOTIZ-LOGIK === */}
                                        <div className="daily-note-container">
                                            {editingNote === isoDate ? (
                                                // Ansicht im Bearbeitungsmodus
                                                <>
                                                    <textarea
                                                        className="daily-note-editor"
                                                        value={noteContent}
                                                        onChange={(e) => setNoteContent(e.target.value)}
                                                        rows="4"
                                                        placeholder="Notiz eingeben..."
                                                    />
                                                    <div className="note-buttons">
                                                        <button className="button-primary" onClick={() => handleNoteSave(isoDate, noteContent)}>{t('save', 'Speichern')}</button>
                                                        <button className="button-secondary" onClick={() => setEditingNote(null)}>{t('cancel', 'Abbrechen')}</button>
                                                    </div>
                                                </>
                                            ) : (
                                                // Normale Anzeige
                                                <div className="daily-note-display">
                                                    <div className="note-content">
                                                        <strong>{t('dailyNoteTitle', 'Notiz')}:</strong>
                                                        <p>{summary?.dailyNote || t('noNotePlaceholder', 'Keine Notiz.')}</p>
                                                    </div>
                                                    <button
                                                        className="button-edit-note"
                                                        title={t('editNote', 'Notiz bearbeiten')}
                                                        aria-label={t('editNote', 'Notiz bearbeiten')}
                                                        onClick={() => {
                                                            setEditingNote(isoDate);
                                                            setNoteContent(summary?.dailyNote || '');
                                                        }}
                                                    >
                                                        ✏️
                                                    </button>
                                                </div>
                                            )}
                                        </div>
                                        {/* === ENDE DER NOTIZ-LOGIK === */}
                                    </>
                                )}
                            </div>
                            {summary?.needsCorrection && (
                                <p className="needs-correction-text">{t('messages.correctionNeeded', 'Bitte korrigieren!')}</p>
                            )}
                            <div className="correction-button-row">
                                <button onClick={() => openCorrectionModal(dayObj, summary)} className="button-secondary">
                                    {t("submitCorrectionRequest", "Korrektur anfragen")}
                                </button>
                            </div>
                            </div>

                    );
                })}
            </div>
            {modalInfo.isVisible && (
                <CustomerTimeAssignModal
                    t={t}
                    day={modalInfo.day}
                    summary={modalInfo.summary}
                    customers={customers}
                    projects={projects}
                    onClose={() => setModalInfo({ isVisible: false, day: null, summary: null })}
                    onSave={() => {
                        if (reloadData) reloadData();
                        setModalInfo({ isVisible: false, day: null, summary: null });
                    }}
                />
            )}
        </section>
    );
};

// Deine PropTypes bleiben unverändert
HourlyWeekOverview.propTypes = {
    t: PropTypes.func.isRequired,
    dailySummaries: PropTypes.arrayOf(PropTypes.shape({
        date: PropTypes.string.isRequired,
        workedMinutes: PropTypes.number.isRequired,
        breakMinutes: PropTypes.number.isRequired,
        entries: PropTypes.arrayOf(PropTypes.shape({
            id: PropTypes.number,
            entryTimestamp: PropTypes.string.isRequired,
            punchType: PropTypes.string.isRequired,
            source: PropTypes.string,
            correctedByUser: PropTypes.bool,
        })).isRequired,
        dailyNote: PropTypes.string,
        needsCorrection: PropTypes.bool,
        primaryTimes: PropTypes.object
    })).isRequired,
    selectedMonday: PropTypes.instanceOf(Date).isRequired,
    setSelectedMonday: PropTypes.func.isRequired,
    weeklyTotalMins: PropTypes.number.isRequired,
    monthlyTotalMins: PropTypes.number.isRequired,
    handleManualPunch: PropTypes.func.isRequired,
    punchMessage: PropTypes.string,
    openCorrectionModal: PropTypes.func.isRequired,
    userProfile: PropTypes.object,
    customers: PropTypes.array,
    recentCustomers: PropTypes.array,
    projects: PropTypes.array,
    selectedCustomerId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    setSelectedCustomerId: PropTypes.func,
    selectedProjectId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    setSelectedProjectId: PropTypes.func,
    assignCustomerForDay: PropTypes.func,
    assignProjectForDay: PropTypes.func,
    reloadData: PropTypes.func
};

export default HourlyWeekOverview;
