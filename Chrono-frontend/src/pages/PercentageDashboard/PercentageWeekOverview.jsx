// PercentageWeekOverview.jsx
import PropTypes from 'prop-types';
import {
    addDays, formatISO, formatDate, formatTime,
    computeDayTotalMinutes, expectedDayMinutes, isLateTime
} from './percentageDashUtils';
import { minutesToHours } from './percentageDashUtils';
import '../../styles/PercentageDashboardScoped.css';

const PercentageWeekOverview = ({
                                    user, entries, monday, setMonday,
                                    weeklyWorked, weeklyExpected, weeklyDiff,
                                    handleManualPunch, punchMessage,
                                    openCorrectionModal
                                }) => {
    const weekDates = Array.from({ length: 7 }, (_, i) => addDays(monday, i));
    const dayStrings = weekDates.map(formatISO);
    const dayMap = {};
    entries.forEach(e => {
        const iso = e.startTime.slice(0, 10);
        if (dayStrings.includes(iso)) {
            if (!dayMap[iso]) dayMap[iso] = [];
            dayMap[iso].push(e);
        }
    });

    const prevWeek = () => setMonday(p => addDays(p, -7));
    const nextWeek = () => setMonday(p => addDays(p, +7));
    const jumpWeek = (e) => {
        const d = new Date(e.target.value);
        if (!isNaN(d)) setMonday(d);
    };

    return (

        <section className="weekly-overview">
            <h3>Wochenübersicht</h3>

            {punchMessage && <div className="punch-message">{punchMessage}</div>}

            <div className="punch-section">
                <button onClick={handleManualPunch}>Manuell stempeln</button>
            </div>

            <div className="week-navigation">
                <button onClick={prevWeek}>← Vorige Woche</button>
                <input type="date" value={formatISO(monday)} onChange={jumpWeek}/>
                <button onClick={nextWeek}>Nächste Woche →</button>
            </div>

            <div className="week-display">
                {weekDates.map((d, i) => {
                    const iso = formatISO(d);
                    const list = (dayMap[iso] || []).sort((a, b) => a.punchOrder - b.punchOrder);
                    const worked = computeDayTotalMinutes(list);

                    return (
                        <div key={i} className="week-day-card">
                            <div className="week-day-header">
                                {d.toLocaleDateString('de-DE', { weekday: 'long', timeZone: 'Europe/Zurich' })},
                                {new Date(iso).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'Europe/Zurich' })}
                            </div>

                            <div className="week-day-content">
                                {list.length === 0
                                    ? <p>Keine Einträge</p>
                                    : <ul>
                                        {list.map(e => {
                                            const label = ['–','Work Start','Break Start','Break End','Work End'][e.punchOrder];
                                            const time  = e.punchOrder === 4
                                                ? formatTime(e.endTime || e.startTime)
                                                : (e.breakStart && e.punchOrder===2) ? formatTime(e.breakStart)
                                                    : (e.breakEnd && e.punchOrder===3) ? formatTime(e.breakEnd)
                                                        : formatTime(e.startTime);
                                            return       <li key={e.id} className={isLateTime(time) ? 'late-time' : ''}>
                                                <strong>{label}:</strong> {time}
                                            </li>


                                        })}
                                    </ul>}
                            </div>

                            {/* Zeige nur gestempelte Minuten, keine Tagesdiff mehr */}
                            {list.length > 0 && (
                                <div className="daily-summary">
                                    ⏱ {minutesToHours(worked)}

                                </div>
                            )}

                            <div className="correction-button-row">
                                <button onClick={() => openCorrectionModal(iso)}>
                                    Korrektur anfragen
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
    user: PropTypes.object.isRequired,
    entries: PropTypes.array.isRequired,
    monday: PropTypes.instanceOf(Date).isRequired,
    setMonday: PropTypes.func.isRequired,
    weeklyWorked: PropTypes.number.isRequired,
    weeklyExpected: PropTypes.number.isRequired,
    weeklyDiff: PropTypes.number.isRequired,
    handleManualPunch: PropTypes.func.isRequired,
    punchMessage: PropTypes.string,
    openCorrectionModal: PropTypes.func.isRequired
};

export default PercentageWeekOverview;
