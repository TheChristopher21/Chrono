// PercentageWeekOverview.jsx
import React from 'react';
import PropTypes from 'prop-types';
import { useTranslation } from '../../context/LanguageContext';

import {
    addDays,
    formatISO,
    formatTime,
    computeDayTotalMinutes,
    isLateTime,
    minutesToHours,
} from './percentageDashUtils';

const PercentageWeekOverview = ({
                                    entries,
                                    monday,
                                    setMonday,
                                    weeklyWorked,
                                    weeklyExpected,
                                    weeklyDiff,
                                    handleManualPunch,
                                    punchMessage,
                                    openCorrectionModal,
                                }) => {
    const { t } = useTranslation();

    // Sieben Tage ab "monday"
    const weekDates = Array.from({ length: 5 }, (_, i) => addDays(monday, i));
    const isoStrings = weekDates.map(formatISO);

    // dayMap: isoString -> Array
    const dayMap = {};
    entries.forEach(e => {
        const iso = e.startTime.slice(0, 10);
        if (!dayMap[iso]) dayMap[iso] = [];
        dayMap[iso].push(e);
    });

    function prevWeek() {
        setMonday(prev => addDays(prev, -7));
    }
    function nextWeek() {
        setMonday(prev => addDays(prev, 7));
    }
    function jumpWeek(e) {
        const d = new Date(e.target.value);
        if (!isNaN(d)) setMonday(d);
    }

    return (
        <section className="weekly-overview">
            <h3>{t('weeklyOverview')}</h3>

            {punchMessage && <div className="punch-message">{punchMessage}</div>}

            {/* Manuelles Stempeln */}
            <div className="punch-section">
                <button onClick={handleManualPunch}>
                    {t('manualPunchButton')}
                </button>
            </div>

            {/* Navigation */}
            <div className="week-navigation">
                <button onClick={prevWeek}>
                    ← {t('prevWeek')}
                </button>
                <input
                    type="date"
                    value={formatISO(monday)}
                    onChange={jumpWeek}
                />
                <button onClick={nextWeek}>
                    {t('nextWeek')} →
                </button>
            </div>

            <div className="weekly-summary">
                <p>
                    <strong>{t('weeklyHours')}:</strong> {minutesToHours(weeklyWorked)}
                </p>
                <p>
                    <strong>{t('expected')}:</strong> {minutesToHours(weeklyExpected)}
                </p>
                <p>
                    <strong>{t('weekBalance')}:</strong> {minutesToHours(weeklyDiff)}
                </p>
            </div>

            {/* Darstellung der Einträge */}
            <div className="week-display">
                {weekDates.map((dayObj, idx) => {
                    const iso = isoStrings[idx];
                    const dayEntries = (dayMap[iso] || []).sort((a,b) => a.punchOrder - b.punchOrder);
                    const worked = computeDayTotalMinutes(dayEntries);

                    return (
                        <div key={idx} className="week-day-card">
                            <div className="week-day-header">
                                {dayObj.toLocaleDateString('de-DE', {
                                    weekday: 'long',
                                    day: '2-digit',
                                    month: '2-digit',
                                    year: 'numeric'
                                })}
                            </div>

                            <div className="week-day-content">
                                {dayEntries.length === 0 ? (
                                    <p>{t('noEntries')}</p>
                                ) : (
                                    <ul>
                                        {dayEntries.map(e => {
                                            const labelByOrder = {
                                                1: t('workStart'),
                                                2: t('breakStart'),
                                                3: t('breakEnd'),
                                                4: t('workEnd'),
                                            };
                                            const label = labelByOrder[e.punchOrder] || '-';

                                            let time = '-';
                                            if (e.punchOrder === 4) {
                                                time = e.endTime ? formatTime(e.endTime) : formatTime(e.startTime);
                                            } else if (e.punchOrder === 2 && e.breakStart) {
                                                time = formatTime(e.breakStart);
                                            } else if (e.punchOrder === 3 && e.breakEnd) {
                                                time = formatTime(e.breakEnd);
                                            } else {
                                                time = formatTime(e.startTime);
                                            }

                                            return (
                                                <li key={e.id} className={isLateTime(time) ? 'late-time' : ''}>
                                                    <strong>{label}:</strong> {time}
                                                </li>
                                            );
                                        })}
                                    </ul>
                                )}
                            </div>

                            {dayEntries.length > 0 && (
                                <div className="daily-summary">
                                    ⏱ {minutesToHours(worked)}
                                </div>
                            )}

                            <div className="correction-button-row">
                                <button onClick={() => openCorrectionModal(iso)}>
                                    {t('submitCorrectionRequest')}
                                </button>
                            </div>
                        </div>
                    );
                })}
            </div>
        </section>
    );
};

PercentageWeekOverview.propTypes = {
    entries: PropTypes.array.isRequired,
    monday: PropTypes.instanceOf(Date).isRequired,
    setMonday: PropTypes.func.isRequired,
    weeklyWorked: PropTypes.number,
    weeklyExpected: PropTypes.number,
    weeklyDiff: PropTypes.number,
    handleManualPunch: PropTypes.func.isRequired,
    punchMessage: PropTypes.string,
    openCorrectionModal: PropTypes.func.isRequired,
};

export default PercentageWeekOverview;
