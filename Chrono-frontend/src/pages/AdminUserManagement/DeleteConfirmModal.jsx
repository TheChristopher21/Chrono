import React from 'react';
import ModalOverlay from '../../components/ModalOverlay';
import PropTypes from 'prop-types';
import { useTranslation } from '../../context/LanguageContext';

/**
 * DeleteConfirmModal
 * - Zeigt den `username` in der Nachricht an
 * - Hat ein etwas freundlicheres Button-Design
 */
function DeleteConfirmModal({
                                visible,
                                title = "Benutzer löschen",
                                message = "Möchtest du diesen Benutzer wirklich löschen?",
                                userName = "",
                                onConfirm,
                                onCancel
                            }) {
    const { t } = useTranslation();
    if (!visible) return null;

    return (
        <ModalOverlay visible={visible}>
            <div className="modal-content delete-confirm-modal">
                <h3>{title}</h3>
                <p>
                    {message}
                    {userName && (
                        <strong style={{ marginLeft: "6px" }}>
                            {`"${userName}"`}
                        </strong>
                    )}
                </p>

                <div className="modal-buttons">
                    <button
                        className="delete-confirm-button confirm"
                        onClick={onConfirm}
                    >
                        {/* z.B. "Ja, löschen" */}
                        {t('userManagement.deleteConfirmConfirm')}
                    </button>
                    <button
                        className="delete-confirm-button cancel"
                        onClick={onCancel}
                    >
                        {t('userManagement.deleteConfirmCancel')}
                    </button>
                </div>
            </div>
        </ModalOverlay>
    );
}

DeleteConfirmModal.propTypes = {
    visible: PropTypes.bool.isRequired,
    title: PropTypes.string,
    message: PropTypes.string,
    userName: PropTypes.string,
    onConfirm: PropTypes.func.isRequired,
    onCancel: PropTypes.func.isRequired
};

export default DeleteConfirmModal;
