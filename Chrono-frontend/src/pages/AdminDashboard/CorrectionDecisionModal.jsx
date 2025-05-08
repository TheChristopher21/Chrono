import React from "react";
import PropTypes from "prop-types";
import "../../styles/AdminDashboardScoped.css";

const CorrectionDecisionModal = ({
                                     visible,
                                     mode,
                                     comment,
                                     setComment,
                                     onSubmit,
                                     onClose,
                                 }) => {
    if (!visible) return null;

    const isApprove = mode === "approve";
    // Hier kannst du mit t(...) arbeiten
    // Ggf. Key: "adminDashboard.approveCorrectionTitle" / "adminDashboard.denyCorrectionTitle"
    const header = isApprove ? "Korrektur genehmigen" : "Korrektur ablehnen";
    const btnLabel = isApprove ? "Genehmigen" : "Ablehnen";

    return (
        <div className="modal-overlay">
            <div className="modal-content">
                <h3>{header}</h3>

                <div className="form-group">
                    <label>Kommentar für den Nutzer:</label>
                    <textarea
                        rows={4}
                        value={comment}
                        onChange={(e) => setComment(e.target.value)}
                        placeholder="Warum genehmigst / lehnst du ab?"
                    />
                </div>

                <div className="modal-buttons">
                    <button onClick={onSubmit}>{btnLabel}</button>
                    <button onClick={onClose} className="secondary">
                        Abbrechen
                    </button>
                </div>
            </div>
        </div>
    );
};

CorrectionDecisionModal.propTypes = {
    visible: PropTypes.bool.isRequired,
    mode: PropTypes.oneOf(["approve", "deny"]).isRequired,
    comment: PropTypes.string.isRequired,
    setComment: PropTypes.func.isRequired,
    onSubmit: PropTypes.func.isRequired,
    onClose: PropTypes.func.isRequired,
};

export default CorrectionDecisionModal;
