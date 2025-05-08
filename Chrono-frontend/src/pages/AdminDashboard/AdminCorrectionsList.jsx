import React, { useState } from "react";
import PropTypes from "prop-types";
import { formatDate } from "./adminDashboardUtils";
import CorrectionDecisionModal from "./CorrectionDecisionModal";
import "../../styles/AdminDashboardScoped.css";

const AdminCorrectionsList = ({
                                  t,
                                  allCorrections,
                                  handleApproveCorrection,
                                  handleDenyCorrection,
                              }) => {
    const [modalOpen, setModalOpen] = useState(false);
    const [modalMode, setModalMode] = useState("approve"); // "approve" | "deny"
    const [targetId, setTargetId] = useState(null);
    const [adminComment, setAdminComment] = useState("");

    function openModal(id, mode) {
        setTargetId(id);
        setModalMode(mode);
        setAdminComment("");
        setModalOpen(true);
    }

    async function submitDecision() {
        if (modalMode === "approve") {
            await handleApproveCorrection(targetId, adminComment);
        } else {
            await handleDenyCorrection(targetId, adminComment);
        }
        setModalOpen(false);
        setAdminComment("");
    }

    return (
        <div className="admin-dashboard scoped-dashboard">
            <section className="correction-section">
                <h3>{t("adminDashboard.correctionRequestsTitle")}</h3>

                {allCorrections.length === 0 ? (
                    <p>{t("adminDashboard.noEntriesThisWeek")}</p>
                ) : (
                    <ul className="correction-list">
                        {allCorrections.map((corr) => {
                            const dateStrRaw =
                                corr.desiredStartTime || corr.desiredStart || corr.startDate;
                            const correctionDate = dateStrRaw
                                ? formatDate(new Date(dateStrRaw))
                                : "-";

                            return (
                                <li key={corr.id} className="correction-item">
                                    <h4>
                                        {t("adminDashboard.correctionItemLabel", "Korrektur vom")}{" "}
                                        {correctionDate}
                                    </h4>

                                    <div className="correction-info">
                                        <p>
                                            <strong>User:</strong> {corr.username}
                                        </p>
                                        <p>
                                            <strong>Work Start:</strong> {corr.workStart || "-"}
                                        </p>
                                        <p>
                                            <strong>Break Start:</strong> {corr.breakStart || "-"}
                                        </p>
                                        <p>
                                            <strong>Break End:</strong> {corr.breakEnd || "-"}
                                        </p>
                                        <p>
                                            <strong>Work End:</strong> {corr.workEnd || "-"}
                                        </p>
                                        <p>
                                            <strong>{t("reason")}:</strong> {corr.reason}
                                        </p>
                                        <p>
                                            <strong>{t("adminDashboard.statusLabel", "Status")}:</strong>{" "}
                                            {corr.approved
                                                ? t("adminDashboard.approved")
                                                : corr.denied
                                                    ? t("adminDashboard.denied")
                                                    : t("adminDashboard.pending")}
                                        </p>
                                        {corr.adminComment && (
                                            <p>
                                                <strong>Admin-Kommentar:</strong>{" "}
                                                <em>{corr.adminComment}</em>
                                            </p>
                                        )}
                                    </div>

                                    {!corr.approved && !corr.denied && (
                                        <div className="correction-buttons">
                                            <button onClick={() => openModal(corr.id, "approve")}>
                                                {t("adminDashboard.acceptButton")}
                                            </button>
                                            <button onClick={() => openModal(corr.id, "deny")}>
                                                {t("adminDashboard.rejectButton")}
                                            </button>
                                        </div>
                                    )}
                                </li>
                            );
                        })}
                    </ul>
                )}
            </section>

            <CorrectionDecisionModal
                visible={modalOpen}
                mode={modalMode}
                comment={adminComment}
                setComment={setAdminComment}
                onSubmit={submitDecision}
                onClose={() => setModalOpen(false)}
            />
        </div>
    );
};

AdminCorrectionsList.propTypes = {
    t: PropTypes.func.isRequired,
    allCorrections: PropTypes.array.isRequired,
    handleApproveCorrection: PropTypes.func.isRequired,
    handleDenyCorrection: PropTypes.func.isRequired,
};

export default AdminCorrectionsList;
