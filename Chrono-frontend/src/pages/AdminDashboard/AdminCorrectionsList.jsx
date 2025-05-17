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
            <section className="correction-section w-full">
                <h3>{t("adminDashboard.correctionRequestsTitle")}</h3>

                {allCorrections.length === 0 ? (
                    <p>{t("adminDashboard.noEntriesThisWeek")}</p>
                ) : (
                    <ul className="correction-list space-y-2">
                        {allCorrections.map((corr) => {
                            const dateStrRaw =
                                corr.desiredStartTime || corr.desiredStart || corr.startDate;
                            const correctionDate = dateStrRaw
                                ? formatDate(new Date(dateStrRaw))
                                : "-";
                            const statusConfig = corr.approved
                                ? { color: 'bg-green-100', icon: '✔️', text: t('adminDashboard.approved') }
                                : corr.denied
                                    ? { color: 'bg-red-100', icon: '❌', text: t('adminDashboard.denied') }
                                    : { color: 'bg-yellow-100', icon: '⏳', text: t('adminDashboard.pending') };

                            return (
                                <li key={corr.id} className={`${statusConfig.color} p-3 rounded shadow`}>
                                    <div className="flex justify-between items-center">
                                        <h4 className="font-semibold">
                                            {t("adminDashboard.correctionItemLabel", "Korrektur vom")} {correctionDate}
                                        </h4>
                                        <span className="flex items-center gap-1">
                                            <span>{statusConfig.icon}</span>
                                            <span className="font-semibold">{statusConfig.text}</span>
                                        </span>
                                    </div>

                                    <div className="correction-info text-sm grid grid-cols-2 gap-x-4 mt-2">
                                        <p><strong>User:</strong> {corr.username}</p>
                                        <p><strong>Work Start:</strong> {corr.workStart || "-"}</p>
                                        <p><strong>Break Start:</strong> {corr.breakStart || "-"}</p>
                                        <p><strong>Break End:</strong> {corr.breakEnd || "-"}</p>
                                        <p><strong>Work End:</strong> {corr.workEnd || "-"}</p>
                                        <p><strong>{t("reason")}:</strong> {corr.reason}</p>
                                        {corr.adminComment && (
                                            <p className="col-span-2"><strong>Admin-Kommentar:</strong> <em>{corr.adminComment}</em></p>

                                        )}
                                    </div>

                                    {!corr.approved && !corr.denied && (
                                        <div className="correction-buttons mt-2 flex gap-2">
                                            <button
                                                className="bg-green-500 text-white px-2 py-1 rounded"
                                                onClick={() => openModal(corr.id, "approve")}
                                            >
                                                {t("adminDashboard.acceptButton")}
                                            </button>
                                            <button
                                                className="bg-red-500 text-white px-2 py-1 rounded"
                                                onClick={() => openModal(corr.id, "deny")}
                                            >                                                {t("adminDashboard.rejectButton")}
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
