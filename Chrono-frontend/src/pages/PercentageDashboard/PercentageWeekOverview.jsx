// src/pages/PercentageDashboard/PercentageWeekOverview.jsx
import React, { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext.jsx';
import PropTypes from 'prop-types';
// Removed: import { useTranslation } from '../../context/LanguageContext'; // t wird als Prop übergeben

import {
    addDays,
    formatLocalDate,
    formatTime, // Wird für die Anzeige von Zeitstempeln aus Einträgen benötigt
    minutesToHHMM, // Für die Anzeige von Gesamtminuten
    isLateTime, // Beibehalten für UI-Hervorhebungen
    formatDate,
    formatPunchedTimeFromEntry, // Zum Formatieren der einzelnen Stempelzeiten
    expectedDayMinutesForPercentageUser, // Spezifisch für %-User
    getMondayOfWeek // Wird für die Datums-Input-Navigation benötigt
} from './percentageDashUtils';
import '../../styles/PercentageDashboardScoped.css'; // Stellt sicher, dass die Styles hier auch referenziert werden
import api from "../../utils/api.js";
import DayCard from '../../components/DayCard';
import CustomerTimeAssignModal from '../../components/CustomerTimeAssignModal';

const PercentageWeekOverview = ({
                                    t,
                                    dailySummaries, // Array von DailyTimeSummaryDTOs für die ausgewählte Woche
                                    monday,
                                    setMonday, // Korrigiert von setSelectedMonday zu setMonday, falls es so in der Parent-Komponente heißt
                                    weeklyWorked,
                                    weeklyExpected,
                                    weeklyDiff,
                                    handleManualPunch,
                                    punchMessage,
                                    openCorrectionModal,
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
                                    vacationRequests,
                                    sickLeaves,
                                    holidaysForUserCanton,
                                    reloadData
                                }) => {

    // Immer 7 Tage für eine volle Wochenansicht (Mo-So)
    const weekDates = Array.from({ length: 7 }, (_, i) => addDays(monday, i)); // Mo-So
    const { currentUser } = useAuth();
    const [modalInfo, setModalInfo] = useState({ isVisible: false, day: null, summary: null });

    // Derive current customer selections and ranges from loaded entries


    function handlePrevWeek() {
        setMonday(prev => addDays(prev, -7));
    }
    function handleNextWeek() {
        setMonday(prev => addDays(prev, 7));
    }
    function handleWeekJump(e) {
        // Stellt sicher, dass das Datum korrekt als lokales Datum behandelt wird
        const localDateString = e.target.value; // Kommt als "YYYY-MM-DD"
        const picked = new Date(localDateString + "T00:00:00"); // Als Mitternacht lokal interpretieren
        if (!isNaN(picked.getTime())) {
            setMonday(getMondayOfWeek(picked)); // Stellt sicher, dass immer ein Montag gewählt wird
        }
    }



    return (
        <section className="weekly-overview content-section">
            <h3 className="section-title">{t('weeklyOverview', "Wochenübersicht")}</h3>

            {punchMessage && <div className="punch-message">{punchMessage}</div>}

            <div className="punch-section">
                <h4>{t("manualPunchTitle", "Manuelles Stempeln")}</h4>
                {(userProfile?.customerTrackingEnabled ?? currentUser?.customerTrackingEnabled) && (
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
                <button onClick={handleManualPunch} className="button-primary">
                    {t("manualPunchButton", "Jetzt stempeln")}
                </button>
            </div>

            <div className="week-navigation">
                <button onClick={handlePrevWeek} className="button-secondary">
                    ← {t('prevWeek', "Vorige Woche")}
                </button>
                <input
                    type="date"
                    value={formatLocalDate(monday)}
                    onChange={handleWeekJump}
                />
                <button onClick={handleNextWeek} className="button-secondary">
                    {t('nextWeek', "Nächste Woche")} →
                </button>
            </div>

            <div className="weekly-summary">
                <div className="summary-item">
                    <span className="summary-label">{t('weeklyHours', 'Ges. Std. (Woche)')}</span>
                    <span className="summary-value">{minutesToHHMM(weeklyWorked)}</span>
                </div>
                <div className="summary-item">
                    <span className="summary-label">
                        {t('expected', 'Soll (Woche)')}
                        <span className="info-badge" title={t('expectedWeekInfo')}>ℹ️</span>
                    </span>
                    <span className="summary-value">{minutesToHHMM(weeklyExpected)}</span>
                </div>
                <div className="summary-item">
                    <span className="summary-label">{t('weekBalance', 'Saldo (Woche)')}</span>
                    <span className={`summary-value ${(weeklyDiff ?? 0) < 0 ? 'balance-negative' : 'balance-positive'}`}>{minutesToHHMM(weeklyDiff)}</span>
                </div>
            </div>

            <div className="week-display">
                {weekDates.map((dayObj) => {
                    const isoDate = formatLocalDate(dayObj);
                    const summary = dailySummaries.find(s => s.date === isoDate);
                    const dayName = dayObj.toLocaleDateString('de-DE', { weekday: 'long' });
                    const formattedDisplayDate = formatDate(dayObj);

                    const vacationToday = vacationRequests.find(v => v.approved && isoDate >= v.startDate && isoDate <= v.endDate);
                    const sickToday = sickLeaves.find(sl => isoDate >= sl.startDate && isoDate <= sl.endDate);
                    const isHolidayToday = holidaysForUserCanton && holidaysForUserCanton[isoDate];

                    const baseDailyExpectedMins = expectedDayMinutesForPercentageUser(userProfile);
                    let displayDailyExpectedMins = baseDailyExpectedMins;
                    if (isHolidayToday || (vacationToday && !vacationToday.halfDay) || (sickToday && !sickToday.halfDay)) {
                        displayDailyExpectedMins = 0;
                    } else if (vacationToday?.halfDay || sickToday?.halfDay) {
                        displayDailyExpectedMins /= 2;
                    }

                    const dailyWorkedMins = summary?.workedMinutes || 0;
                    const dailyDiffMinutes = dailyWorkedMins - displayDailyExpectedMins;

                    return (
                        <DayCard
                            key={isoDate}
                            t={t}
                            dayName={dayName}
                            displayDate={formattedDisplayDate}
                            summary={summary}
                            holidayName={holidaysForUserCanton[isoDate]}
                            vacationInfo={vacationToday}
                            sickInfo={sickToday}
                            expectedMinutes={displayDailyExpectedMins}
                            diffMinutes={dailyDiffMinutes}
                            showCorrection
                            onRequestCorrection={() => openCorrectionModal(dayObj, summary)}
                        >
                            {summary && summary.entries.length > 0 && (
                                <div className="day-card-actions">
                                    <button
                                        onClick={() => setModalInfo({ isVisible: true, day: dayObj, summary })}
                                        className="button-primary-outline"
                                    >
                                        {t('assignCustomer.editButton', 'Kunden & Zeiten bearbeiten')}
                                    </button>
                                </div>
                            )}
                        </DayCard>
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

PercentageWeekOverview.propTypes = {
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
    monday: PropTypes.instanceOf(Date).isRequired,
    setMonday: PropTypes.func.isRequired,
    weeklyWorked: PropTypes.number.isRequired,
    weeklyExpected: PropTypes.number.isRequired,
    weeklyDiff: PropTypes.number.isRequired,
    handleManualPunch: PropTypes.func.isRequired,
    punchMessage: PropTypes.string,
    openCorrectionModal: PropTypes.func.isRequired,
    userProfile: PropTypes.object.isRequired,
    customers: PropTypes.array,
    recentCustomers: PropTypes.array,
    projects: PropTypes.array,
    selectedCustomerId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    setSelectedCustomerId: PropTypes.func,
    selectedProjectId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    setSelectedProjectId: PropTypes.func,
    assignCustomerForDay: PropTypes.func,
    assignProjectForDay: PropTypes.func,
    vacationRequests: PropTypes.array.isRequired,
    sickLeaves: PropTypes.array.isRequired,
    holidaysForUserCanton: PropTypes.object,
    reloadData: PropTypes.func,
};

export default PercentageWeekOverview;
