import React, { useEffect, useMemo, useRef } from "react";
import ModalOverlay from '../../components/ModalOverlay';
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
    inline,
    title,
    actions,
}) => {
    const { t } = useTranslation();
    const textareaRef = useRef(null);

    useEffect(() => {
        if (visible && textareaRef.current) {
            textareaRef.current.focus();
            textareaRef.current.select();
        }
    }, [visible, inline]);

    const isApprove = mode === "approve";
    const header = title
        || (isApprove
            ? t('adminDashboard.correctionModal.approveTitle')
            : t('adminDashboard.correctionModal.denyTitle'));
    const singleActionLabel = isApprove
        ? t('adminDashboard.acceptButton')
        : t('adminDashboard.rejectButton');

    const resolvedActions = useMemo(() => {
        if (Array.isArray(actions) && actions.length > 0) {
            return actions.map((action) => ({
                ...action,
                mode: action.mode || mode,
                label: action.label
                    || (action.mode === 'deny'
                        ? t('adminDashboard.rejectButton')
                        : t('adminDashboard.acceptButton')),
            }));
        }
        return [{
            mode,
            label: singleActionLabel,
            primary: true,
        }];
    }, [actions, mode, singleActionLabel, t]);

    if (!visible) return null;

    const handleSubmit = (actionMode) => {
        if (typeof onSubmit === 'function') {
            onSubmit(actionMode || mode, comment);
        }
    };

    const handleClose = () => {
        if (typeof onClose === 'function') {
            onClose();
        }
    };

    const handleKeyDown = (event) => {
        if (event.key === 'Escape') {
            event.preventDefault();
            handleClose();
            return;
        }
        if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            handleSubmit(mode);
        }
    };

    const content = (
        <div className={`modal-content${inline ? ' inline-decision-panel' : ''}`} role="dialog" aria-modal={!inline}>
            <div className="modal-header">
                <h3>{header}</h3>
            </div>
            <div className="form-group">
                <label htmlFor="correction-decision-comment">{t('adminDashboard.correctionModal.commentLabel')}</label>
                <textarea
                    id="correction-decision-comment"
                    ref={textareaRef}
                    rows={4}
                    value={comment}
                    onChange={(e) => setComment(e.target.value)}
                    onKeyDown={handleKeyDown}
                    placeholder={t('adminDashboard.correctionModal.commentPlaceholder')}
                />
                <small className="modal-hint">
                    {t('adminDashboard.correctionModal.hint', 'Enter bestätigt · Shift+Enter für neue Zeile · Esc schließt')}
                </small>
            </div>
            <div className="modal-buttons">
                {resolvedActions.map((action) => (
                    <button
                        key={action.mode}
                        type="button"
                        className={`button-${action.primary ? 'primary' : 'secondary'}`}
                        onClick={() => {
                            if (typeof action.onSubmit === 'function') {
                                action.onSubmit(comment);
                            } else {
                                handleSubmit(action.mode);
                            }
                        }}
                    >
                        {action.label}
                    </button>
                ))}
                <button
                    type="button"
                    className="button-ghost"
                    onClick={handleClose}
                >
                    {t('cancel')}
                </button>
            </div>
        </div>
    );

    if (inline) {
        return (
            <div className="inline-decision-wrapper" data-mode={mode}>
                {content}
            </div>
        );
    }

    return (
        <ModalOverlay visible={visible}>
            {content}
        </ModalOverlay>
    );
};

CorrectionDecisionModal.propTypes = {
    visible: PropTypes.bool.isRequired,
    mode: PropTypes.oneOf(["approve", "deny"]).isRequired,
    comment: PropTypes.string.isRequired,
    setComment: PropTypes.func.isRequired,
    onSubmit: PropTypes.func.isRequired,
    onClose: PropTypes.func,
    inline: PropTypes.bool,
    title: PropTypes.string,
    actions: PropTypes.arrayOf(PropTypes.shape({
        mode: PropTypes.oneOf(['approve', 'deny']),
        label: PropTypes.string,
        primary: PropTypes.bool,
        onSubmit: PropTypes.func,
    })),
};

CorrectionDecisionModal.defaultProps = {
    onClose: undefined,
    inline: false,
    title: undefined,
    actions: undefined,
};

export default CorrectionDecisionModal;