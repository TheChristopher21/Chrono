// src/pages/PercentageDashboard/PercentageWeekOverview.jsx
import React, { useState, useMemo } from 'react';
import { useAuth } from '../../context/AuthContext.jsx';
import PropTypes from 'prop-types';

import {
    addDays,
    formatLocalDate,
    formatTime,
    minutesToHHMM,
    isLateTime,
    formatDate,
    formatPunchedTimeFromEntry,
    expectedDayMinutesForPercentageUser,
    getMondayOfWeek
} from './percentageDashUtils';
import '../../styles/PercentageDashboardScoped.css';
import CustomerTimeAssignModal from '../../components/CustomerTimeAssignModal';

const PercentageWeekOverview = ({
                                    t,
                                    dailySummaries,
                                    monday,
                                    setMonday,
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
                                    vacationRequests,
                                    sickLeaves,
                                    holidaysForUserCanton,
                                    reloadData
                                }) => {

    const weekDates = Array.from({ length: 7 }, (_, i) => addDays(monday, i));
    const { currentUser } = useAuth();
    const [modalInfo, setModalInfo] = useState({ isVisible: false, day: null, summary: null });

    function handlePrevWeek() {
        setMonday(prev => addDays(prev, -7));
    }

    function handleNextWeek() {
        setMonday(prev => addDays(prev, 7));
    }

    function handleWeekJump(e) {
        const localDateString = e.target.value;
        const picked = new Date(localDateString + "T00:00:00");
        if (!isNaN(picked.getTime())) {
            setMonday(getMondayOfWeek(picked));
        }
    }

    return (
        <section className="weekly-overview content-section">
            <h3 className="section-title">{t('weeklyOverview', "Wochenübersicht")}</h3>

            {punchMessage && <div className="punch-message">{punchMessage}</div>}

            <div className="punch-section">
                <h4>{t("manualPunchTitle", "Manuelles Stempeln")}</h4>
                {(userProfile?.customerTrackingEnabled ?? currentUser?.customerTrackingEnabled) && (
                    <div className="customer-project-selectors">
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
                    </div>
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
                    const dailyBreakMinutes = summary?.breakMinutes || 0;

                    const customerRanges = [];
                    if (summary?.entries) {
                        let currentCustomer = null;
                        let start = null;
                        summary.entries.forEach(entry => {
                            const time = formatTime(entry.entryTimestamp);
                            if (entry.punchType === 'START') {
                                currentCustomer = entry.customerName || t('noCustomer', 'Allgemein');
                                start = time;
                            } else if (entry.punchType === 'ENDE' && start) {
                                customerRanges.push({ customerName: currentCustomer, start, end: time });
                                start = null;
                            }
                        });
                    }

                    const correctionDisabled = isHolidayToday || vacationToday || sickToday;

                    return (
                        <div key={isoDate} className="day-card">
                            <div className="day-card-header">
                                <h3>{dayObj.toLocaleDateString('de-DE', { weekday: 'long' })}, {formatDate(dayObj)}</h3>
                            </div>
                            <div className="day-card-body">
                                {isHolidayToday ? (
                                    <div className="day-card-info holiday-indicator">🎉 {holidaysForUserCanton[isoDate]}</div>
                                ) : vacationToday ? (
                                    <div className="day-card-info vacation-indicator">🏖️ {t('onVacation', 'Im Urlaub')} {vacationToday.halfDay && `(${t('halfDayShort', '½ Tag')})`}</div>
                                ) : sickToday ? (
                                    <div className="day-card-info sick-leave-indicator">⚕️ {t('sickLeave.sick', 'Krank')} {sickToday.halfDay && `(${t('halfDayShort', '½ Tag')})`}</div>
                                ) : (
                                    <>
                                        <div className="time-summary">
                                            <div className="time-block">
                                                <span className="label">{t('actualTime', 'Ist')}</span>
                                                <span className="value">{minutesToHHMM(dailyWorkedMins)}</span>
                                            </div>
                                            <div className="time-block">
                                                <span className="label">{t('expected', 'Soll')}</span>
                                                <span className="value">{minutesToHHMM(displayDailyExpectedMins)}</span>
                                            </div>
                                            <div className="time-block">
                                                <span className="label">{t('diffToday', 'Differenz')}</span>
                                                <span className={`value ${dailyDiffMinutes < 0 ? 'negative' : 'positive'}`}>{minutesToHHMM(dailyDiffMinutes)}</span>
                                            </div>
                                            <div className="time-block">
                                                <span className="label">{t('breakTime', 'Pause')}</span>
                                                <span className="value">{minutesToHHMM(dailyBreakMinutes)}</span>
                                            </div>
                                        </div>
                                        <div className="work-details">
                                            <h4>{t('punchTypes.title', 'Stempelungen')}:</h4>
                                            {customerRanges.length > 0 ? (
                                                <ul>
                                                    {customerRanges.map((r, i) => (
                                                        <li key={i}>
                                                            <span className="work-description">{r.customerName}</span>
                                                            <span className="work-time">{r.start} - {r.end}</span>
                                                        </li>
                                                    ))}
                                                </ul>
                                            ) : (
                                                <p className="no-entries">{t('noEntries', 'Keine Einträge')}</p>
                                            )}
                                        </div>
                                    </>
                                )}
                            </div>
                            <div className="day-card-footer">
                                {!correctionDisabled && (
                                    <button onClick={() => openCorrectionModal(dayObj, summary)} className="button-secondary correction-button">
                                        {t('submitCorrectionRequest', 'Korrektur anfragen')}
                                    </button>
                                )}
                                {summary && summary.entries.length > 0 && (
                                    <button onClick={() => setModalInfo({ isVisible: true, day: dayObj, summary })} className="button-primary-outline">
                                        {t('assignCustomer.editButton', 'Kunden & Zeiten bearbeiten')}
                                    </button>
                                )}
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

PercentageWeekOverview.propTypes = {
    t: PropTypes.func.isRequired,
    dailySummaries: PropTypes.array.isRequired,
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
    vacationRequests: PropTypes.array.isRequired,
    sickLeaves: PropTypes.array.isRequired,
    holidaysForUserCanton: PropTypes.object,
    reloadData: PropTypes.func,
};

export default PercentageWeekOverview;