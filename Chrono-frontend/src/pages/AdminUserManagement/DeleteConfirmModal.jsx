// src/pages/AdminUserManagement/DeleteConfirmModal.jsx
import 'react';
import PropTypes from 'prop-types';

const DeleteConfirmModal = ({
                                visible,
                                title,
                                message,
                                onConfirm,
                                onCancel
                            }) => {
    if (!visible) return null;

    return (
        <div className="modal-overlay">
            <div className="modal-content">
                <h3>{title}</h3>
                <p>{message}</p>
                <div className="modal-buttons">
                    <button onClick={onConfirm}>Ja, löschen</button>
                    <button onClick={onCancel}>Abbrechen</button>
                </div>
            </div>
        </div>
    );
};

DeleteConfirmModal.propTypes = {
    visible: PropTypes.bool.isRequired,
    title: PropTypes.string,
    message: PropTypes.string,
    onConfirm: PropTypes.func.isRequired,
    onCancel: PropTypes.func.isRequired
};

DeleteConfirmModal.defaultProps = {
    title: 'Benutzer löschen',
    message: 'Soll der Benutzer wirklich gelöscht werden? (Alle Daten werden gelöscht inkl. Zeiten!)'
};

export default DeleteConfirmModal;
