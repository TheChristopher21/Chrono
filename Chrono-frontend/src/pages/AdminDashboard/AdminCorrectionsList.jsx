// AdminCorrectionsList.jsx
import React, { useState, useMemo } from "react";
import PropTypes from "prop-types";
import { formatDate, formatTime } from "./adminDashboardUtils"; // Korrekter Import
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

    const [isExpanded, setIsExpanded] = useState(true);
    const [searchTerm, setSearchTerm] = useState('');

    function toggleExpansion() {
        setIsExpanded(!isExpanded);
    }

    function handleSearch(e) {
        setSearchTerm(e.target.value.toLowerCase());
    }

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

    const filteredCorrections = useMemo(() => {
        if (!searchTerm) {
            return allCorrections;
        }
        return allCorrections.filter(corr => {
            const requestDateStr = corr.requestDate ? formatDate(new Date(corr.requestDate)) : "";
            const desiredTimeStr = corr.desiredTimestamp ? formatTime(new Date(corr.desiredTimestamp)) : "";
            const originalTimeStr = corr.originalTimestamp ? formatTime(new Date(corr.originalTimestamp)) : "";

            return (
                (corr.username || '').toLowerCase().includes(searchTerm) ||
                requestDateStr.toLowerCase().includes(searchTerm) ||
                desiredTimeStr.toLowerCase().includes(searchTerm) ||
                originalTimeStr.toLowerCase().includes(searchTerm) ||
                (corr.desiredPunchType || '').toLowerCase().includes(searchTerm) ||
                (corr.originalPunchType || '').toLowerCase().includes(searchTerm) ||
                (corr.reason || '').toLowerCase().includes(searchTerm) ||
                (corr.adminComment || '').toLowerCase().includes(searchTerm)
            );
        });
    }, [allCorrections, searchTerm]);


    return (
        <div className="admin-dashboard scoped-dashboard">
            <section className="correction-section content-section">
                <div
                    className="section-header"
                    onClick={toggleExpansion}
                    role="button"
                    tabIndex={0}
                    onKeyPress={(e) => e.key === 'Enter' && toggleExpansion()}
                >
                    <h3 className="section-title">
                        {t("adminDashboard.correctionRequestsTitle")}
                    </h3>
                    <span className="toggle-icon">
                        {isExpanded ? '▲' : '▼'}
                    </span>
                </div>

                {isExpanded && (
                    <div className="section-content">
                        <input
                            type="text"
                            placeholder={t('adminDashboard.searchCorrectionsPlaceholder', 'Suche User, Datum, Typ, Grund...')}
                            value={searchTerm}
                            onChange={handleSearch}
                            className="search-input"
                        />
                        {filteredCorrections.length === 0 ? (
                            <p className="no-data-message">{t("adminDashboard.noCorrectionRequestsFound", "Keine Korrekturanträge gefunden.")}</p>
                        ) : (
                            <div className="correction-list-scrollable-container">
                                <ul className="correction-list">
                                    {filteredCorrections.map((corr) => {
                                        const correctionDisplayDate = corr.requestDate
                                            ? formatDate(new Date(corr.requestDate))
                                            : (corr.desiredTimestamp ? formatDate(new Date(corr.desiredTimestamp)) : "-");

                                        let statusClass = "status-is-pending";
                                        let statusIcon = '⏳';
                                        let statusText = t('adminDashboard.pending', 'Ausstehend');

                                        if (corr.approved) {
                                            statusClass = "status-is-approved";
                                            statusIcon = '✔️';
                                            statusText = t('adminDashboard.approved', 'Genehmigt');
                                        } else if (corr.denied) {
                                            statusClass = "status-is-denied";
                                            statusIcon = '❌';
                                            statusText = t('adminDashboard.denied', 'Abgelehnt');
                                        }

                                        return (
                                            <li key={corr.id} className={statusClass}>
                                                <div className="correction-header-info">
                                                    <h4 className="font-semibold">
                                                        {t("adminDashboard.correctionRequestFor", "Korrekturantrag für")}: {corr.username}
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
                                                    {corr.targetEntryId && (
                                                        <div className="correction-detail-block">
                                                            <p><strong>{t("correction.originalPunch", "Originale Stempelung")}</strong></p>
                                                            <p><span>{t("correction.type", "Typ")}: {corr.originalPunchType || "-"}</span></p>
                                                            <p><span>{t("correction.time", "Zeit")}: {corr.originalTimestamp ? formatTime(new Date(corr.originalTimestamp)) : "-"}</span></p>
                                                            <p><span className="text-xs text-muted">(ID: {corr.targetEntryId})</span></p>
                                                        </div>
                                                    )}
                                                    <div className="correction-detail-block">
                                                        <p><strong>{t("correction.desiredChange", "Gewünschte Änderung")}</strong></p>
                                                        <p><span>{t("correction.type", "Typ")}: {corr.desiredPunchType || "-"}</span></p>
                                                        <p><span>{t("correction.time", "Zeit")}: {corr.desiredTimestamp ? formatTime(new Date(corr.desiredTimestamp)) : "-"}</span></p>
                                                    </div>
                                                    <p className="reason-field full-width-field"><strong>{t("reason", "Grund")}:</strong> {corr.reason || "-"}</p>
                                                    {corr.adminComment && (
                                                        <p className="admin-comment-field full-width-field"><strong>{t("adminDashboard.adminComment", "Admin-Kommentar")}:</strong> <em>{corr.adminComment}</em></p>
                                                    )}
                                                </div>

                                                {!corr.approved && !corr.denied && (
                                                    <div className="correction-buttons mt-2 flex gap-2">
                                                        <button
                                                            className="button-approve"
                                                            onClick={() => openModal(corr.id, "approve")}
                                                        >
                                                            {t("adminDashboard.acceptButton", "Genehmigen")}
                                                        </button>
                                                        <button
                                                            className="button-reject"
                                                            onClick={() => openModal(corr.id, "deny")}
                                                        >
                                                            {t("adminDashboard.rejectButton", "Ablehnen")}
                                                        </button>
                                                    </div>
                                                )}
                                            </li>
                                        );
                                    })}
                                </ul>
                            </div>
                        )}
                    </div>
                )}
            </section>

            <CorrectionDecisionModal
                visible={modalOpen}
                mode={modalMode}
                comment={adminComment}
                setComment={setAdminComment}
                onSubmit={submitDecision}
                onClose={() => setModalOpen(false)}
                // t={t} // CorrectionDecisionModal verwendet useTranslation intern
            />
        </div>
    );
};

AdminCorrectionsList.propTypes = {
    t: PropTypes.func.isRequired,
    allCorrections: PropTypes.arrayOf(
        PropTypes.shape({
            id: PropTypes.number.isRequired,
            username: PropTypes.string,
            requestDate: PropTypes.string,      // ISO String "YYYY-MM-DD"
            targetEntryId: PropTypes.number,    // ID des zu korrigierenden Eintrags (optional)
            originalPunchType: PropTypes.string,// Ursprünglicher Typ (START/ENDE), falls targetEntryId vorhanden
            originalTimestamp: PropTypes.string,// Ursprünglicher Zeitstempel (ISO DateTime), falls targetEntryId vorhanden
            desiredPunchType: PropTypes.string.isRequired, // Gewünschter Typ (START/ENDE)
            desiredTimestamp: PropTypes.string.isRequired, // Gewünschter Zeitstempel (ISO DateTime)
            reason: PropTypes.string,
            approved: PropTypes.bool,
            denied: PropTypes.bool,
            adminComment: PropTypes.string,
        })
    ).isRequired,
    handleApproveCorrection: PropTypes.func.isRequired,
    handleDenyCorrection: PropTypes.func.isRequired,
};

export default AdminCorrectionsList;