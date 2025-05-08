import React from 'react';
import PropTypes from 'prop-types';

/**
 * DeleteConfirmModal
 *
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
    if (!visible) return null;

    return (
        <div className="modal-overlay">
            <div className="modal-content delete-confirm-modal">
                {/* Title */}
                <h3>{title}</h3>

                {/* Nachricht inkl. Username */}
                <p>
                    {message}
                    {userName && (
                        <strong style={{ marginLeft: "6px" }}>
                            {`"${userName}"`}
                        </strong>
                    )}
                </p>

                {/* Buttons */}
                <div className="modal-buttons">
                    <button
                        className="delete-confirm-button confirm"
                        onClick={onConfirm}
                    >
                        {/* z.B. "Ja, löschen" */}
                        Ja, löschen
                    </button>
                    <button
                        className="delete-confirm-button cancel"
                        onClick={onCancel}
                    >
                        Abbrechen
                    </button>
                </div>
            </div>
        </div>
    );
}

DeleteConfirmModal.propTypes = {
    visible: PropTypes.bool.isRequired,
    title: PropTypes.string,
    message: PropTypes.string,
    userName: PropTypes.string,       // <-- Neu
    onConfirm: PropTypes.func.isRequired,
    onCancel: PropTypes.func.isRequired
};

export default DeleteConfirmModal;
