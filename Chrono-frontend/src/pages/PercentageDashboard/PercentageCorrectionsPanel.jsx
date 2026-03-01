// src/pages/PercentageDashboard/PercentageCorrectionsPanel.jsx
import React from "react";
import PropTypes from "prop-types";
import { addDays, formatDate, formatTime } from "./percentageDashUtils";

const PercentageCorrectionsPanel = ({
                                        t,
                                        correctionRequests,
                                        selectedCorrectionMonday,
                                        setSelectedCorrectionMonday,
                                        showCorrectionsPanel,
                                        setShowCorrectionsPanel,
                                        showAllCorrections,
                                        setShowAllCorrections,
                                    }) => {
    const correctionWeekLabel = `${formatDate(selectedCorrectionMonday)} – ${formatDate(addDays(selectedCorrectionMonday, 6))}`;

    const filtered = showAllCorrections
        ? correctionRequests
        : correctionRequests.filter((req) => {
            if (!req.requestDate) return false;
            const reqDate = new Date(req.requestDate + "T00:00:00");
            return reqDate >= selectedCorrectionMonday && reqDate < addDays(selectedCorrectionMonday, 7);
        });

    const sortedCorrections = filtered.slice().sort((a, b) => new Date(b.requestDate) - new Date(a.requestDate));

    return (
        <section className="correction-panel content-section">
            <div
                className="corrections-header section-header"
                onClick={() => setShowCorrectionsPanel((prev) => !prev)}
                role="button" tabIndex={0}
                onKeyPress={(e) => e.key === 'Enter' && setShowCorrectionsPanel((prev) => !prev)}
                aria-expanded={showCorrectionsPanel} >
                <h3 className="section-title">{t("correctionRequests", "Korrekturanträge")}</h3>
                <span className="toggle-icon">{showCorrectionsPanel ? "▲" : "▼"}</span>
            </div>

            {showCorrectionsPanel && (
                <div className="corrections-content">
                    {!showAllCorrections && (
                        <div className="week-navigation corrections-nav">
                            <button onClick={() => setSelectedCorrectionMonday(prev => addDays(prev, -7))} className="button-secondary">
                                ← {t("prevWeek", "Vorige Woche")}
                            </button>
                            <span className="week-label">{correctionWeekLabel}</span>
                            <button onClick={() => setSelectedCorrectionMonday(prev => addDays(prev, 7))} className="button-secondary">
                                {t("nextWeek", "Nächste Woche")} →
                            </button>
                        </div>
                    )}
                    <div className="toggle-all-button" style={{textAlign: 'center', margin: '1rem 0'}}>
                        <button onClick={() => setShowAllCorrections((prev) => !prev)} className="button-secondary">
                            {showAllCorrections ? t("showWeeklyOnly", "Nur aktuelle Woche") : t("showAll", "Alle anzeigen")}
                        </button>
                    </div>

                    {sortedCorrections.length === 0 ? (
                        <p className="no-data-message">{t("noCorrections", "Keine Anträge")}</p>
                    ) : (
                        <ul className="corrections-list user-correction-list">
                            {sortedCorrections.map((req) => {
                                const correctionDisplayDate = req.requestDate ? formatDate(new Date(req.requestDate + "T00:00:00")) : "-";
                                let statusClass = "status-is-pending";
                                let statusIcon = '⏳';
                                let statusText = t('adminDashboard.pending', 'Ausstehend');

                                if (req.approved) {
                                    statusClass = "status-is-approved"; statusIcon = '✔️'; statusText = t('adminDashboard.approved', 'Genehmigt');
                                } else if (req.denied) {
                                    statusClass = "status-is-denied"; statusIcon = '❌'; statusText = t('adminDashboard.denied', 'Abgelehnt');
                                }

                                return (
                                    <li key={req.id} className={statusClass}>
                                        <div className="correction-header-info">
                                            <h4 className="font-semibold">{t("adminDashboard.correctionRequestFor", "Antrag für")}: {req.username}</h4>
                                            <span className="status-indicator"><span>{statusIcon}</span><span className="font-semibold">{statusText}</span></span>
                                        </div>
                                        <p className="text-sm correction-date-indicator"><strong>{t("date")}:</strong> {correctionDisplayDate}</p>
                                        <div className="correction-info text-sm mt-2">
                                            <div className="correction-detail-block">
                                                <p><strong>{t("correction.desiredChange", "Gewünschte Änderung")}</strong></p>
                                                <p><span>{t("correction.type", "Typ")}: {req.desiredPunchType || "-"}</span></p>
                                                <p><span>{t("correction.time", "Zeit")}: {req.desiredTimestamp ? formatTime(new Date(req.desiredTimestamp)) : "-"}</span></p>
                                            </div>
                                            <p className="reason-field full-width-field"><strong>{t("reason")}:</strong> {req.reason || "-"}</p>
                                            {req.adminComment && (
                                                <p className="admin-comment-field full-width-field"><strong>{t("adminDashboard.adminComment", "Admin-Kommentar")}:</strong> <em>{req.adminComment}</em></p>
                                            )}
                                        </div>
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

PercentageCorrectionsPanel.propTypes = {
    t: PropTypes.func.isRequired,
    correctionRequests: PropTypes.array.isRequired,
    selectedCorrectionMonday: PropTypes.instanceOf(Date).isRequired,
    setSelectedCorrectionMonday: PropTypes.func.isRequired,
    showCorrectionsPanel: PropTypes.bool.isRequired,
    setShowCorrectionsPanel: PropTypes.func.isRequired,
    showAllCorrections: PropTypes.bool.isRequired,
    setShowAllCorrections: PropTypes.func.isRequired,
};

export default PercentageCorrectionsPanel;