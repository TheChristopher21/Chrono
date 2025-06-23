import React from "react";
import { useTranslation } from "../../context/LanguageContext";
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
    const { t } = useTranslation();
    if (!visible) return null;

    const isApprove = mode === "approve";
    // Hier kannst du mit t(...) arbeiten
    // Ggf. Key: "adminDashboard.approveCorrectionTitle" / "adminDashboard.denyCorrectionTitle"

    const header = isApprove ? t('adminDashboard.correctionModal.approveTitle') : t('adminDashboard.correctionModal.denyTitle');
    const btnLabel = isApprove ? t('adminDashboard.acceptButton') : t('adminDashboard.rejectButton');

    return (
        <div className="modal-overlay">
            <div className="modal-content">
                <h3>{header}</h3>

                <div className="form-group">
                    <label>{t('adminDashboard.correctionModal.commentLabel')}</label>
                    <textarea
                        rows={4}
                        value={comment}
                        onChange={(e) => setComment(e.target.value)}
                        placeholder={t('adminDashboard.correctionModal.commentPlaceholder')}
                    />
                </div>

                <div className="modal-buttons">
                    <button onClick={onSubmit}>{btnLabel}</button>
                    <button onClick={onClose} className="secondary">{t('cancel')}</button>
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