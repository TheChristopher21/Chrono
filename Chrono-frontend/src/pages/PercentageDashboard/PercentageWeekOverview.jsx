// src/pages/PercentageDashboard/PercentageWeekOverview.jsx
import React from 'react';
import PropTypes from 'prop-types';
import { useTranslation } from '../../context/LanguageContext';

import {
    addDays,
    formatISO,
    formatDate,
    formatTime,
    computeDayTotalMinutes,
    expectedDayMinutes,
    isLateTime,
    minutesToHours,
} from './percentageDashUtils';

const PercentageWeekOverview = ({
                                    entries,
                                    monday,
                                    setMonday,
                                    weeklyDiff,          // falls du eine Wochensumme anzeigst
                                    handleManualPunch,
                                    punchMessage,
                                    openCorrectionModal,
                                }) => {
    const { t } = useTranslation();

    // Die 7 Tage der Woche berechnen:
    const weekDates  = Array.from({ length: 7 }, (_, i) => addDays(monday, i));
    // Nur als ISO, damit wir filtern können:
    const isoStrings = weekDates.map(formatISO);

    // dayMap: isoString => Array von Einträgen
    const dayMap = {};
    entries.forEach(e => {
        const iso = e.startTime.slice(0, 10); // "yyyy-MM-dd"
        if (isoStrings.includes(iso)) {
            if (!dayMap[iso]) dayMap[iso] = [];
            dayMap[iso].push(e);
        }
    });

    // Navigation
    const prevWeek = () => setMonday(prev => addDays(prev, -7));
    const nextWeek = () => setMonday(prev => addDays(prev, 7));
    const jumpWeek = (e) => {
        const d = new Date(e.target.value);
        if (!isNaN(d)) setMonday(d);
    };

    return (
        <section className="weekly-overview">
            <h3>{t('weeklyOverview')}</h3>

            {punchMessage && (
                <div className="punch-message">{punchMessage}</div>
            )}

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
                    value={formatISO(monday)}  // "yyyy-MM-dd"
                    onChange={jumpWeek}
                />
                <button onClick={nextWeek}>
                    {t('nextWeek')} →
                </button>
            </div>

            {/* Tages‐Karten */}
            <div className="week-display">
                {weekDates.map((d, i) => {
                    const iso  = formatISO(d); // "yyyy-MM-dd"
                    const list = (dayMap[iso] || []).sort((a,b) => a.punchOrder - b.punchOrder);
                    const worked = computeDayTotalMinutes(list);

                    return (
                        <div key={i} className="week-day-card">
                            <div className="week-day-header">
                                {/* Direkt d.toLocaleDateString(...) verwenden */}
                                {d.toLocaleDateString('de-DE', {
                                    weekday: 'long',
                                    timeZone: 'Europe/Berlin',
                                })},{' '}
                                {d.toLocaleDateString('de-DE', {
                                    day: '2-digit',
                                    month: '2-digit',
                                    year: 'numeric',
                                    timeZone: 'Europe/Berlin',
                                })}
                            </div>

                            <div className="week-day-content">
                                {list.length === 0 ? (
                                    <p>{t('noEntries')}</p>
                                ) : (
                                    <ul>
                                        {list.map(e => {
                                            // PunchOrder -> Label
                                            const label = [
                                                '–',
                                                t('workStart'),
                                                t('breakStart'),
                                                t('breakEnd'),
                                                t('workEnd'),
                                            ][e.punchOrder] || '-';

                                            // Uhrzeit
                                            let time = '-';
                                            if (e.punchOrder === 4) {
                                                time = e.endTime
                                                    ? formatTime(e.endTime)
                                                    : formatTime(e.startTime);
                                            } else if (e.punchOrder === 2 && e.breakStart) {
                                                time = formatTime(e.breakStart);
                                            } else if (e.punchOrder === 3 && e.breakEnd) {
                                                time = formatTime(e.breakEnd);
                                            } else {
                                                time = formatTime(e.startTime);
                                            }

                                            return (
                                                <li
                                                    key={e.id}
                                                    className={isLateTime(time) ? 'late-time' : ''}
                                                >
                                                    <strong>{label}:</strong> {time}
                                                </li>
                                            );
                                        })}
                                    </ul>
                                )}
                            </div>

                            {list.length > 0 && (
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
    weeklyDiff: PropTypes.number,
    handleManualPunch: PropTypes.func.isRequired,
    punchMessage: PropTypes.string,
    openCorrectionModal: PropTypes.func.isRequired,
};

export default PercentageWeekOverview;
