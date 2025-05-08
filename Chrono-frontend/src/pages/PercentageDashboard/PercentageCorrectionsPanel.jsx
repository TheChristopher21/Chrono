import React from "react";
import PropTypes from "prop-types";
import { addDays, formatDate } from "./percentageDashUtils";
import "../../styles/PercentageDashboardScoped.css";

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
    const sortedCorrections = (
        showAllCorrections
            ? correctionRequests
            : correctionRequests.filter((req) => {
                if (!req.desiredStart) return false;
                const reqDate = new Date(req.desiredStart);
                return (
                    reqDate >= selectedCorrectionMonday &&
                    reqDate < addDays(selectedCorrectionMonday, 7)
                );
            })
    )
        .slice()
        .sort((a, b) => new Date(b.desiredStart) - new Date(a.desiredStart));

    function handlePrevWeek() {
        setSelectedCorrectionMonday((prev) => addDays(prev, -7));
    }
    function handleNextWeek() {
        setSelectedCorrectionMonday((prev) => addDays(prev, 7));
    }

    return (
        <section className="correction-panel">
            <div
                className="corrections-header"
                onClick={() => setShowCorrectionsPanel((prev) => !prev)}
            >
                <h3>{t("correctionRequests") || "Korrekturanträge"}</h3>
                <span className="toggle-icon">{showCorrectionsPanel ? "▲" : "▼"}</span>
            </div>

            {showCorrectionsPanel && (
                <div className="corrections-content">
                    {!showAllCorrections && (
                        <div className="week-navigation corrections-nav">
                            <button onClick={handlePrevWeek}>
                                ← {t("prevWeek") || "Prev"}
                            </button>
                            <span className="week-label">
                {formatDate(selectedCorrectionMonday)} –{" "}
                                {formatDate(addDays(selectedCorrectionMonday, 6))}
              </span>
                            <button onClick={handleNextWeek}>
                                {t("nextWeek") || "Next"} →
                            </button>
                        </div>
                    )}

                    <div className="toggle-all-button">
                        <button onClick={() => setShowAllCorrections((prev) => !prev)}>
                            {showAllCorrections
                                ? t("showWeeklyOnly") || "Nur aktuelle Woche"
                                : t("showAll") || "Alle anzeigen"}
                        </button>
                    </div>

                    {sortedCorrections.length === 0 ? (
                        <p>{t("noCorrections") || "Keine Korrekturanträge vorhanden"}</p>
                    ) : (
                        sortedCorrections.map((req) => {
                            const d = new Date(req.desiredStart);
                            return (
                                <div key={req.id} className="single-correction">
                                    {formatDate(d)} –{" "}
                                    {req.approved ? (
                                        <span className="approved">
                      {t("approved") || "Bestätigt"}
                    </span>
                                    ) : req.denied ? (
                                        <span className="denied">{t("denied") || "Abgelehnt"}</span>
                                    ) : (
                                        <span className="pending">{t("pending") || "Offen"}</span>
                                    )}
                                    <br />
                                    {/* Hier könntest du optional noch einen Label davor setzen,
                      z.B. <strong>{t("reason")}:</strong> {req.reason} */}
                                    {req.reason}
                                    <br />
                                    <strong>{t("workStart") || "Work Start"}:</strong>{" "}
                                    {req.workStart || "-"}
                                    <br />
                                    <strong>{t("breakStart") || "Break Start"}:</strong>{" "}
                                    {req.breakStart || "-"}
                                    <br />
                                    <strong>{t("breakEnd") || "Break End"}:</strong>{" "}
                                    {req.breakEnd || "-"}
                                    <br />
                                    <strong>{t("workEnd") || "Work End"}:</strong>{" "}
                                    {req.workEnd || "-"}
                                    {req.adminComment && (
                                        <>
                                            <br />
                                            <em className="admin-comment">💬 {req.adminComment}</em>
                                        </>
                                    )}
                                </div>
                            );
                        })
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
