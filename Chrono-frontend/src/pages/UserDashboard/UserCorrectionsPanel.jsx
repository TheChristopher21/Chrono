// UserCorrectionsPanel.jsx
import React from 'react';
import PropTypes from 'prop-types';
import '../../styles/UserDashboardScoped.css';
import { addDays, formatDate } from './userDashUtils';

const UserCorrectionsPanel = ({
                                  t,
                                  showCorrectionsPanel,
                                  setShowCorrectionsPanel,
                                  selectedCorrectionMonday,
                                  setSelectedCorrectionMonday,
                                  showAllCorrections,
                                  setShowAllCorrections,
                                  sortedCorrections
                              }) => {
    const correctionWeekLabel = `${formatDate(selectedCorrectionMonday)} – ${formatDate(addDays(selectedCorrectionMonday, 6))}`;

    return (
        <section className="correction-panel">
            <div
                className="corrections-header"
                onClick={() => setShowCorrectionsPanel(prev => !prev)}
            >
                <h3>{t('correctionRequests')}</h3>
                <span className="toggle-icon">{showCorrectionsPanel ? '▲' : '▼'}</span>
            </div>

            {showCorrectionsPanel && (
                <div className="corrections-content">
                    <div className="week-navigation corrections-nav">
                        <button onClick={() => setSelectedCorrectionMonday(prev => addDays(prev, -7))}>
                            ← {t('prevWeek')}
                        </button>
                        <span className="week-label">{correctionWeekLabel}</span>
                        <button onClick={() => setSelectedCorrectionMonday(prev => addDays(prev, 7))}>
                            {t('nextWeek')} →
                        </button>
                    </div>

                    <div className="toggle-all-button">
                        <button onClick={() => setShowAllCorrections(prev => !prev)}>
                            {showAllCorrections ? t('showWeeklyOnly') || 'Nur aktuelle Woche' : t('showAll') || 'Alle anzeigen'}
                        </button>
                    </div>

                    {sortedCorrections.length === 0 ? (
                        <p>{t('noCorrections') || 'Keine Korrekturanträge vorhanden'}</p>
                    ) : (
                        <ul className="corrections-list">
                            {sortedCorrections.map(req => {
                                const dateStr = formatDate(new Date(req.desiredStart));
                                return (
                                    <li key={req.id}>
                                        {dateStr}{' '}
                                        {req.approved ? (
                                            <span className="approved">Bestätigt</span>
                                        ) : req.denied ? (
                                            <span className="denied">Abgelehnt</span>
                                        ) : (
                                            <span className="pending">Offen</span>
                                        )}
                                        <br />
                                        {req.reason}
                                        <br />
                                        <strong>Work Start:</strong> {req.workStart?.slice(0, 5) || '-'}
                                        <br />
                                        <strong>Break Start:</strong> {req.breakStart?.slice(0, 5) || '-'}
                                        <br />
                                        <strong>Break End:</strong> {req.breakEnd?.slice(0, 5) || '-'}
                                        <br />
                                        <strong>Work End:</strong> {req.workEnd?.slice(0, 5) || '-'}
                                    </li>
                                );
                            })}
                        </ul>
                    )}
                </div>
            )}
        </section>
    );
};

UserCorrectionsPanel.propTypes = {
    t: PropTypes.func.isRequired,
    showCorrectionsPanel: PropTypes.bool.isRequired,
    setShowCorrectionsPanel: PropTypes.func.isRequired,
    selectedCorrectionMonday: PropTypes.instanceOf(Date).isRequired,
    setSelectedCorrectionMonday: PropTypes.func.isRequired,
    showAllCorrections: PropTypes.bool.isRequired,
    setShowAllCorrections: PropTypes.func.isRequired,
    sortedCorrections: PropTypes.array.isRequired
};

export default UserCorrectionsPanel;
