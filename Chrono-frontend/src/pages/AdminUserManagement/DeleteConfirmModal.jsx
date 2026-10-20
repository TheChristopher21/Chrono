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
                                title,
                                message,
                                userName = "",
                                onConfirm,
                                onCancel
                            }) {
    const { t } = useTranslation();
    if (!visible) return null;

    const modalTitle = title || t('userManagement.deleteConfirmTitle', 'Benutzer löschen');
    const modalMessage = message || t(
        'userManagement.deleteConfirmMessage',
        'Dieser Benutzer wird deaktiviert. Seine Daten bleiben bis zu einem Jahr gespeichert und werden danach endgültig gelöscht. Fortfahren?'
    );

    return (
        <ModalOverlay visible={visible}>
            <div className="modal-content delete-confirm-modal">
                <h3>{modalTitle}</h3>
                <p>
                    {modalMessage}
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
