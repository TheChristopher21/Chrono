// src/pages/HourlyDashboard/HourlyWeekOverview.jsx
import React from 'react';
import PropTypes from 'prop-types';
import {
    addDays,
    formatLocalDate,
    formatDate,
    formatTime, // Wird für die Anzeige von Zeitstempeln aus Einträgen benötigt
    getMondayOfWeek,
    minutesToHHMM, // Für die Anzeige von Gesamtminuten
    isLateTime, // Beibehalten für UI-Hervorhebungen
    formatPunchedTimeFromEntry // Zum Formatieren der einzelnen Stempelzeiten
} from './hourDashUtils';
import '../../styles/HourlyDashboardScoped.css';
import api from "../../utils/api.js"; // Stellt sicher, dass die Styles hier auch referenziert werden


const HourlyWeekOverview = ({
                                t,
                                dailySummaries, // Ersetzt allEntries, erwartet Array von DailyTimeSummaryDTO
                                // dailyNotes, // Annahme: dailyNote ist jetzt Teil von DailyTimeSummaryDTO
                                // noteEditVisibility, // Logik für Notizen muss ggf. angepasst werden
                                // setDailyNotes,
                                // setNoteEditVisibility,
                                // handleSaveNote, // Logik für Notizen muss ggf. angepasst werden
                                openCorrectionModal, // Funktion zum Öffnen des Korrekturmodals
                                selectedMonday,
                                setSelectedMonday,
                                weeklyTotalMins, // Wird weiterhin für die Wochenübersicht verwendet
                                monthlyTotalMins, // Wird weiterhin für die Monatsübersicht verwendet
                                handleManualPunch,
                                punchMessage,
                                userProfile, // Wird für Notiz-Speicherung und Korrekturen benötigt
                            }) => {

    const weekDates = selectedMonday
        ? Array.from({ length: 7 }, (_, i) => addDays(selectedMonday, i)) // Mo-So anzeigen
        : [];

    function handlePrevWeek() {
        setSelectedMonday(prev => addDays(prev, -7));
    }
    function handleNextWeek() {
        setSelectedMonday(prev => addDays(prev, 7));
    }
    function handleWeekJump(e) {
        const picked = new Date(e.target.value + "T00:00:00"); // Sicherstellen, dass es als lokales Datum geparst wird
        if (!isNaN(picked.getTime())) {
            setSelectedMonday(getMondayOfWeek(picked));
        }
    }

    // Beispielhafte Funktion für Notizen, falls noch benötigt und nicht im AdminDashboard gehandhabt
    const handleNoteToggle = (isoDate) => {
        // Hier müsste die Logik für das Anzeigen/Verstecken des Notiz-Textareas implementiert werden,
        // z.B. über einen lokalen State in dieser Komponente oder über Props.
        console.log("Toggle note for", isoDate);
    };
    const handleNoteSave = async (isoDate, noteContent) => {
        if (!userProfile?.username) return;
        try {
            await api.post('/api/timetracking/daily-note', null, { // Dieser Endpunkt ist veraltet, Anpassung nötig
                params: {
                    username: userProfile.username,
                    date: isoDate,
                    note: noteContent || ''
                }
            });
            // notify(t("dailyNoteSaved")); // Notify-Funktion müsste als Prop übergeben werden
            // fetchDataForUser(); // Funktion zum Neuladen der Daten müsste als Prop übergeben werden
        } catch (err) {
            console.error('Fehler beim Speichern der Tagesnotiz:', err);
            // notify(t("dailyNoteError"));
        }
    };


    return (
        <section className="weekly-overview content-section">
            <h3 className="section-title">{t("weeklyOverview", "Wochenübersicht")}</h3>

            {punchMessage && <div className="punch-message">{punchMessage}</div>}

            <div className="punch-section"> {/* Kann auch außerhalb der weekly-overview section sein, je nach Layout */}
                <h4>{t("manualPunchTitle", "Manuelles Stempeln")}</h4>
                <button onClick={handleManualPunch} className="button-primary">
                    {t("manualPunchButton", "Jetzt stempeln")}
                </button>
            </div>

            <div className="week-navigation">
                <button onClick={handlePrevWeek} className="button-secondary">← {t("prevWeek", "Vorige Woche")}</button>
                <input
                    type="date"
                    onChange={handleWeekJump}
                    value={formatLocalDate(selectedMonday)} // Benötigt formatLocalDate
                />
                <button onClick={handleNextWeek} className="button-secondary">{t("nextWeek", "Nächste Woche")} →</button>
            </div>

            <div className="weekly-monthly-totals">
                <p><strong>{t("weeklyHours", "Ges. Std. (Woche)")}:</strong> {minutesToHHMM(weeklyTotalMins)}</p>
                <p><strong>{t("monthlyHours", "Ges. Std. (Monat)")}:</strong> {minutesToHHMM(monthlyTotalMins)}</p>
            </div>

            <div className="week-display">
                {weekDates.map((dayObj) => {
                    const isoDate = formatLocalDate(dayObj);
                    const summary = dailySummaries.find(s => s.date === isoDate);
                    const dayName = dayObj.toLocaleDateString('de-DE', { weekday: 'long' });
                    const formattedDisplayDate = formatDate(dayObj); // Für benutzerfreundliche Anzeige

                    return (
                        <div key={isoDate} className={`week-day-card day-card ${summary?.needsCorrection ? 'needs-correction-highlight' : ''}`}>
                            <div className="week-day-header day-card-header">
                                <h4>{dayName}, {formattedDisplayDate}</h4>
                            </div>

                            <div className="week-day-content day-card-content">
                                {(!summary || summary.entries.length === 0) ? (
                                    <p className="no-entries">{t("noEntries", "Keine Einträge")}</p>
                                ) : (
                                    <>
                                        <ul className="time-entry-list">
                                            {summary.entries.map(entry => (
                                                <li key={entry.id || entry.entryTimestamp}>
                                                    <span className="entry-label">{t(`punchTypes.${entry.punchType}`, entry.punchType)}:</span>
                                                    <span className={`entry-time ${isLateTime(formatTime(entry.entryTimestamp)) ? 'late-time' : ''}`}>
                                                        {formatPunchedTimeFromEntry(entry)}
                                                        {entry.source === 'SYSTEM_AUTO_END' && !entry.correctedByUser &&
                                                            <span className="auto-end-indicator" title={t('messages.autoEndedTooltip', 'Automatisch beendet')}> (A)</span>
                                                        }
                                                    </span>
                                                </li>
                                            ))}
                                        </ul>
                                        <div className="daily-summary-times">
                                            <p><strong>{t('actualTime', 'Ist')}:</strong> {minutesToHHMM(summary.workedMinutes)}</p>
                                            <p><strong>{t('breakTime', 'Pause')}:</strong> {minutesToHHMM(summary.breakMinutes)}</p>
                                        </div>
                                        {summary.dailyNote && (
                                            <div className="daily-note-display">
                                                <strong>{t('dailyNoteTitle', 'Notiz')}:</strong>
                                                <p>{summary.dailyNote}</p>
                                            </div>
                                        )}
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
        </section>
    );
};

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
        primaryTimes: PropTypes.object // Struktur von primaryTimes genauer definieren, falls verwendet
    })).isRequired,
    selectedMonday: PropTypes.instanceOf(Date).isRequired,
    setSelectedMonday: PropTypes.func.isRequired,
    weeklyTotalMins: PropTypes.number.isRequired,
    monthlyTotalMins: PropTypes.number.isRequired,
    handleManualPunch: PropTypes.func.isRequired,
    punchMessage: PropTypes.string,
    openCorrectionModal: PropTypes.func.isRequired,
    userProfile: PropTypes.object // Für Notiz-Speicher-Funktion, falls noch verwendet
    // Veraltete Props für Notizen entfernt, da Notiz jetzt in dailySummary ist
};

export default HourlyWeekOverview;