import PropTypes from 'prop-types';
import {
  minutesToHHMM,
  formatTime,
  formatPunchedTimeFromEntry,
  isLateTime,
} from '../utils/timeUtils';

const DayCard = ({
  t,
  dayName,
  displayDate,
  summary,
  holidayName,
  vacationInfo,
  sickInfo,
  expectedMinutes,
  diffMinutes,
  showCorrection,
  onRequestCorrection,
  children,
}) => {
  const correctionDisabled = holidayName || vacationInfo || sickInfo;
  return (
    <div
      className={`day-card ${summary?.needsCorrection ? 'needs-correction-highlight' : ''} ${
        vacationInfo ? 'vacation-day' : ''
      } ${sickInfo ? 'sick-day' : ''} ${holidayName ? 'holiday-day' : ''}`}
    >
      <div className="day-card-header">
        <h4>
          {dayName}, {displayDate}
        </h4>
        <div className="day-card-meta">
          {typeof expectedMinutes === 'number' && (
            <span className="expected-hours">
              ({t('expectedWorkHours', 'Soll')}: {minutesToHHMM(expectedMinutes)})
            </span>
          )}
          {summary && typeof diffMinutes === 'number' && (
            <span className={`daily-diff ${diffMinutes < 0 ? 'balance-negative' : 'balance-positive'}`}>({
              t('diffToday')
            }: {minutesToHHMM(diffMinutes)})</span>
          )}
        </div>
      </div>
      <div className="day-card-content">
        {holidayName ? (
          <div className="holiday-indicator day-card-info">
            <span role="img" aria-label="Feiertag">
              🎉
            </span>{' '}
            {holidayName}
          </div>
        ) : vacationInfo ? (
          <div className="vacation-indicator day-card-info">
            <span role="img" aria-label="Urlaub">
              🏖️
            </span>{' '}
            {t('onVacation', 'Im Urlaub')}
            {vacationInfo.halfDay && ` (${t('halfDayShort', '½ Tag')})`}
            {vacationInfo.usesOvertime && ` (${t('overtimeVacationShort', 'ÜS')})`}
          </div>
        ) : sickInfo ? (
          <div className="sick-leave-indicator day-card-info">
            <span role="img" aria-label="Krank">
              ⚕️
            </span>{' '}
            {t('sickLeave.sick', 'Krank')}
            {sickInfo.halfDay && ` (${t('halfDayShort', '½ Tag')})`}
            {sickInfo.comment && (
              <span className="info-badge" title={sickInfo.comment}>
                📝
              </span>
            )}
          </div>
        ) : !summary || summary.entries.length === 0 ? (
          <p className="no-entries">{t('noEntries', 'Keine Einträge')}</p>
        ) : (
          <>
            <ul className="time-entry-list">
              {summary.entries.map((entry) => (
                <li
                  key={entry.id || entry.entryTimestamp}
                  style={{
                    backgroundColor: entry.customerId ? `hsl(${(entry.customerId * 57) % 360},70%,90%)` : 'transparent',
                  }}
                >
                  <span className="entry-label">{t(`punchTypes.${entry.punchType}`, entry.punchType)}:</span>
                  <span className={`entry-time ${isLateTime(formatTime(entry.entryTimestamp)) ? 'late-time' : ''}`}>
                    {formatPunchedTimeFromEntry(entry)}
                    {entry.source === 'SYSTEM_AUTO_END' && !entry.correctedByUser && (
                      <span className="auto-end-indicator" title={t('messages.autoEndedTooltip', 'Automatisch beendet')}>
                        {' '}
                        (A)
                      </span>
                    )}
                  </span>
                  {(entry.customerName || entry.projectName) && (
                    <span className="entry-meta">
                      {entry.customerName || ''}
                      {entry.projectName ? ` / ${entry.projectName}` : ''}
                    </span>
                  )}
                </li>
              ))}
            </ul>
            <div className="daily-summary-times">
              <p>
                <strong>{t('actualTime', 'Ist')}:</strong> {minutesToHHMM(summary.workedMinutes)}
              </p>
              <p>
                <strong>{t('breakTime', 'Pause')}:</strong> {minutesToHHMM(summary.breakMinutes)}
              </p>
            </div>
          </>
        )}
        {children}
      </div>
      {summary?.needsCorrection && !correctionDisabled && (
        <p className="needs-correction-text">{t('messages.correctionNeeded', 'Bitte korrigieren!')}</p>
      )}
      {showCorrection && !correctionDisabled && (
        <div className="correction-button-row">
          <button onClick={onRequestCorrection} className="button-secondary">
            {t('submitCorrectionRequest', 'Korrektur anfragen')}
          </button>
        </div>
      )}
    </div>
  );
};

DayCard.propTypes = {
  t: PropTypes.func.isRequired,
  dayName: PropTypes.string.isRequired,
  displayDate: PropTypes.string.isRequired,
  summary: PropTypes.object,
  holidayName: PropTypes.string,
  vacationInfo: PropTypes.object,
  sickInfo: PropTypes.object,
  expectedMinutes: PropTypes.number,
  diffMinutes: PropTypes.number,
  showCorrection: PropTypes.bool,
  onRequestCorrection: PropTypes.func,
  children: PropTypes.node,
};

export default DayCard;
