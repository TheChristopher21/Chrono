// src/pages/UserDashboard/UserCorrectionsPanel.jsx
import React from "react";
import PropTypes from "prop-types";
import { addDays, formatDate, formatTime } from "./userDashUtils"; // formatTime hinzugefügt
import "../../styles/UserDashboardScoped.css"; // Stellt sicher, dass die Styles hier auch referenziert werden

const UserCorrectionsPanel = ({
                                  t,
                                  showCorrectionsPanel,
                                  setShowCorrectionsPanel,
                                  selectedCorrectionMonday,
                                  setSelectedCorrectionMonday,
                                  showAllCorrections,
                                  setShowAllCorrections,
                                  sortedCorrections, // Enthält jetzt die neue Struktur von CorrectionRequest
                              }) => {
    const correctionWeekLabel = `${formatDate(selectedCorrectionMonday)} – ${formatDate(
        addDays(selectedCorrectionMonday, 6)
    )}`;

    return (
        <section className="correction-panel content-section"> {/* content-section für einheitliches Styling */}
            <div
                className="corrections-header section-header" // section-header für einheitliches Styling
                onClick={() => setShowCorrectionsPanel((prev) => !prev)}
                role="button"
                tabIndex={0}
                onKeyPress={(e) => e.key === 'Enter' && setShowCorrectionsPanel((prev) => !prev)}
                aria-expanded={showCorrectionsPanel}
            >
                <h3 className="section-title">{t("correctionRequests") || "Korrekturanträge"}</h3>
                <span className="toggle-icon">{showCorrectionsPanel ? "▲" : "▼"}</span>
            </div>

            {showCorrectionsPanel && (
                <div className="corrections-content section-content"> {/* section-content für einheitliches Styling */}
                    <div className="week-navigation corrections-nav">
                        <button
                            onClick={() =>
                                setSelectedCorrectionMonday((prev) => addDays(prev, -7))
                            }
                            className="button-secondary" // Einheitlicher Button-Stil
                        >
                            ← {t("prevWeek")}
                        </button>
                        <span className="week-label">{correctionWeekLabel}</span>
                        <button
                            onClick={() =>
                                setSelectedCorrectionMonday((prev) => addDays(prev, 7))
                            }
                            className="button-secondary" // Einheitlicher Button-Stil
                        >
                            {t("nextWeek")} →
                        </button>
                    </div>

                    <div className="toggle-all-button">
                        <button onClick={() => setShowAllCorrections((prev) => !prev)}  className="button-secondary">
                            {showAllCorrections
                                ? t("showWeeklyOnly") || "Nur aktuelle Woche"
                                : t("showAll") || "Alle anzeigen"}
                        </button>
                    </div>

                    {sortedCorrections.length === 0 ? (
                        <p className="no-data-message">{t("noCorrections") || "Keine Korrekturanträge vorhanden"}</p>
                    ) : (
                        <ul className="corrections-list user-correction-list"> {/* user-correction-list für spezifisches Styling */}
                            {sortedCorrections.map((req) => {
                                // requestDate ist jetzt ein direktes Feld im DTO (LocalDate)
                                const correctionDisplayDate = req.requestDate
                                    ? formatDate(new Date(req.requestDate + "T00:00:00")) // Als lokales Datum behandeln
                                    : (req.desiredTimestamp ? formatDate(new Date(req.desiredTimestamp)) : "-");

                                let statusClass = "status-is-pending";
                                let statusIcon = '⏳';
                                let statusText = t('adminDashboard.pending', 'Ausstehend'); // Nutze Admin-Keys, wenn passend

                                if (req.approved) {
                                    statusClass = "status-is-approved";
                                    statusIcon = '✔️';
                                    statusText = t('adminDashboard.approved', 'Genehmigt');
                                } else if (req.denied) {
                                    statusClass = "status-is-denied";
                                    statusIcon = '❌';
                                    statusText = t('adminDashboard.denied', 'Abgelehnt');
                                }

                                return (
                                    <li key={req.id} className={statusClass}>
                                        <div className="correction-header-info">
                                            <h4 className="font-semibold">
                                                {/* username ist im Korrekturantrag vorhanden */}
                                                {t("adminDashboard.correctionRequestFor", "Antrag für")}: {req.username}
                                            </h4>
                                            <span className="status-indicator">
                                                <span>{statusIcon}</span>
                                                <span className="font-semibold">{statusText}</span>
                                            </span>
                                        </div>
                                        <p className="text-sm correction-date-indicator">
                                            <strong>{t("date", "Datum des Antrags")}:</strong> {correctionDisplayDate}
                                        </p>

                                        <div className="correction-info text-sm mt-2">
                                            {req.targetEntryId && (
                                                <div className="correction-detail-block">
                                                    <p><strong>{t("correction.originalPunch", "Originale Stempelung")}</strong></p>
                                                    <p><span>{t("correction.type", "Typ")}: {req.originalPunchType || "-"}</span></p>
                                                    <p><span>{t("correction.time", "Zeit")}: {req.originalTimestamp ? formatTime(new Date(req.originalTimestamp)) : "-"}</span></p>
                                                    <p><span className="text-xs text-muted">(ID: {req.targetEntryId})</span></p>
                                                </div>
                                            )}
                                            <div className="correction-detail-block">
                                                <p><strong>{t("correction.desiredChange", "Gewünschte Änderung")}</strong></p>
                                                <p><span>{t("correction.type", "Typ")}: {req.desiredPunchType || "-"}</span></p>
                                                <p><span>{t("correction.time", "Zeit")}: {req.desiredTimestamp ? formatTime(new Date(req.desiredTimestamp)) : "-"}</span></p>
                                            </div>
                                            <p className="reason-field full-width-field"><strong>{t("reason")}:</strong> {req.reason || "-"}</p>
                                            {req.adminComment && (
                                                <p className="admin-comment-field full-width-field">
                                                    <strong>{t("adminDashboard.adminComment", "Admin-Kommentar")}:</strong> <em>{req.adminComment}</em>
                                                </p>
                                            )}
                                        </div>
                                        {/* Keine Aktionsbuttons für User hier, nur Anzeige */}
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
    sortedCorrections: PropTypes.arrayOf(
        PropTypes.shape({
            id: PropTypes.number.isRequired,
            username: PropTypes.string,
            requestDate: PropTypes.string,
            targetEntryId: PropTypes.number,
            originalPunchType: PropTypes.string,
            originalTimestamp: PropTypes.string,
            desiredPunchType: PropTypes.string,
            desiredTimestamp: PropTypes.string,
            reason: PropTypes.string,
            approved: PropTypes.bool,
            denied: PropTypes.bool,
            adminComment: PropTypes.string,
        })
    ).isRequired,
};

export default UserCorrectionsPanel;