// src/pages/PercentageDashboard/PercentageWeekOverview.jsx
import React, { useState, useEffect } from 'react';
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
                                    assignCustomerForRange,
                                    assignProjectForDay,
                                    vacationRequests,
                                    sickLeaves,
                                    holidaysForUserCanton
                                }) => {

    // Immer 7 Tage für eine volle Wochenansicht (Mo-So)
    const weekDates = Array.from({ length: 7 }, (_, i) => addDays(monday, i)); // Mo-So
    const [selectedCustomers, setSelectedCustomers] = useState({});
    const [selectedProjects, setSelectedProjects] = useState({});
    const [startTimes, setStartTimes] = useState({});
    const [endTimes, setEndTimes] = useState({});
    const [customerRanges, setCustomerRanges] = useState({});
    const [savedRanges, setSavedRanges] = useState({});

    // Derive current customer selections and ranges from loaded entries
    useEffect(() => {
        const initialCustomers = {};
        const existingRanges = {};
        const displayRanges = {};

        dailySummaries.forEach(s => {
            const iso = s.date;
            const entries = Array.isArray(s.entries) ? s.entries : [];
            const customerIds = entries.map(e => e.customerId).filter(id => id != null);
            const unique = Array.from(new Set(customerIds));
            if (unique.length === 1) {
                initialCustomers[iso] = String(unique[0]);
            }
            let currentCustomer = null;
            let start = null;
            entries.forEach(entry => {
                const time = entry.entryTimestamp?.substring(11,16);
                if (entry.punchType === 'START') {
                    currentCustomer = entry.customerId;
                    start = time;
                } else if (entry.punchType === 'ENDE' && start) {
                    (existingRanges[iso] ||= []).push({
                        customerId: currentCustomer || '',
                        start,
                        end: time
                    });
                    const name = entry.customerName || '';
                    (displayRanges[iso] ||= []).push({
                        customerId: currentCustomer || '',
                        customerName: name,
                        start,
                        end: time
                    });

                    currentCustomer = null;
                    start = null;
                }
            });
        });
        setSelectedCustomers(initialCustomers);
        setCustomerRanges(existingRanges);
        setSavedRanges(displayRanges);
    }, [dailySummaries]);


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

    const addCustomerRange = iso => {
        setCustomerRanges(prev => ({
            ...prev,
            [iso]: [...(prev[iso] || []), { customerId: '', start: '', end: '' }]
        }));
    };

    const updateCustomerRange = (iso, idx, field, value) => {
        setCustomerRanges(prev => {
            const ranges = [...(prev[iso] || [])];
            ranges[idx] = { ...ranges[idx], [field]: value };
            return { ...prev, [iso]: ranges };
        });
    };

    const saveCustomerRange = async (iso, idx) => {
        const range = (customerRanges[iso] || [])[idx];
        if (!range || !range.start || !range.end) return;
        await assignCustomerForRange(iso, range.start, range.end, range.customerId);
        setCustomerRanges(prev => {
            const ranges = [...(prev[iso] || [])];
            ranges.splice(idx, 1);
            return { ...prev, [iso]: ranges };
        });
    };


    return (
        <section className="weekly-overview content-section">
            <h3 className="section-title">{t('weeklyOverview', "Wochenübersicht")}</h3>

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
                <p>
                    <strong>{t('weeklyHours', "Ges. Std. (Woche)")}:</strong> {minutesToHHMM(weeklyWorked)}
                </p>
                <p>
                    <strong>{t('expected', "Soll (Woche)")}:</strong> {minutesToHHMM(weeklyExpected)}
                    <span className="info-badge" title={t('expectedWeekInfo')}>ℹ️</span>
                </p>
                <p>
                    <strong className={(weeklyDiff ?? 0) < 0 ? 'balance-negative' : 'balance-positive'}>
                        {t('weekBalance', "Saldo (Woche)")}:
                    </strong>
                    <span className={(weeklyDiff ?? 0) < 0 ? 'balance-negative' : 'balance-positive'}>
                        {minutesToHHMM(weeklyDiff)}
                    </span>
                </p>
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
                            {userProfile?.customerTrackingEnabled && (
                                <div className="day-customer-select">
                                    <label>
                                        <span>{t('customerLabel', 'Kunde')}</span>
                                        <select
                                            value={selectedCustomers[isoDate] || ''}
                                            onChange={e => {
                                                const val = e.target.value;
                                                setSelectedCustomers(prev => ({ ...prev, [isoDate]: val }));
                                                if (startTimes[isoDate] && endTimes[isoDate]) {
                                                    assignCustomerForRange(isoDate, startTimes[isoDate], endTimes[isoDate], val);
                                                } else {
                                                    assignCustomerForDay(isoDate, val);
                                                }
                                            }}

                                        >
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
                                    </label>
                                    <label>
                                        <span>{t('projectLabel', 'Projekt')}</span>
                                        <select
                                            value={selectedProjects[isoDate] || ''}
                                            onChange={e => {
                                                const val = e.target.value;
                                                setSelectedProjects(prev => ({ ...prev, [isoDate]: val }));
                                                assignProjectForDay(isoDate, val);
                                            }}

                                        >
                                            <option value="">{t('noProject','Kein Projekt')}</option>
                                            {projects.map(p => (
                                                <option key={p.id} value={p.id}>{p.name}</option>
                                            ))}
                                        </select>
                                    </label>
                                    <label>
                                        <span>{t('start')}</span>
                                        <input
                                            type="time"
                                            placeholder="HH:MM"
                                            value={startTimes[isoDate] || ''}
                                            onChange={e => setStartTimes(prev => ({ ...prev, [isoDate]: e.target.value }))}
                                        />
                                    </label>
                                    <label>
                                        <span>{t('end')}</span>
                                        <input
                                            type="time"
                                            placeholder="HH:MM"
                                            value={endTimes[isoDate] || ''}
                                            onChange={e => setEndTimes(prev => ({ ...prev, [isoDate]: e.target.value }))}
                                        />
                                    </label>



                                    {(savedRanges[isoDate] || []).length > 0 && (
                                        <ul className="saved-range-list">
                                            {(savedRanges[isoDate] || []).map((r, i) => (
                                                <li key={i}>{r.customerName || t('noCustomer')} {r.start} - {r.end}</li>
                                            ))}
                                        </ul>
                                    )}

                                    {(customerRanges[isoDate] || []).map((r, idx) => (
                                        <div key={idx} className="customer-range-row">
                                            <select
                                                value={r.customerId}
                                                onChange={e => updateCustomerRange(isoDate, idx, 'customerId', e.target.value)}
                                            >
                                                <option value="">{t('noCustomer')}</option>
                                                {customers.map(c => (
                                                    <option key={c.id} value={c.id}>{c.name}</option>
                                                ))}

                                            </select>
                                            <input type="time" value={r.start} onChange={e => updateCustomerRange(isoDate, idx, 'start', e.target.value)} />
                                            <input type="time" value={r.end} onChange={e => updateCustomerRange(isoDate, idx, 'end', e.target.value)} />
                                            <button className="button-secondary" onClick={() => saveCustomerRange(isoDate, idx)}>{t('save','Speichern')}</button>
                                        </div>
                                    ))}
                                    <button className="button-secondary" onClick={() => addCustomerRange(isoDate)}>{t('addRange','Zeitraum hinzufügen')}</button>
                                </div>
                            )}
                        </DayCard>
                    );
                })}
            </div>
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
    assignCustomerForRange: PropTypes.func,
    assignProjectForDay: PropTypes.func,
    vacationRequests: PropTypes.array.isRequired,
    sickLeaves: PropTypes.array.isRequired,
    holidaysForUserCanton: PropTypes.object,
};

export default PercentageWeekOverview;