/**
 * EditTimeModal.jsx
 * Öffnet ein Modal an der Scrollposition des Users, indem wir
 * im useEffect() den window.scrollY messen und .modal-content setzen.
 */
import React, { useEffect } from "react";
import PropTypes from "prop-types";

const EditTimeModal = ({
                           t,
                           editModalVisible,
                           editDate,
                           editData,
                           handleEditInputChange,
                           handleEditSubmit,
                           setEditModalVisible,
                       }) => {
    // 1) useEffect: Wenn Modal geöffnet wird, berechnen wir die Scrollposition
    useEffect(() => {
        if (editModalVisible) {
            const scrollY = window.scrollY || document.documentElement.scrollTop;
            // Wir suchen nur innerhalb dieses Modal-Overlays nach .modal-content
            // Falls du mehrere Modals hast, kannst du es z.B. an einer ID unterscheiden
            const el = document.querySelector(".modal-content");
            if (el) {
                el.style.top = `${scrollY + 100}px`; // 100px Polster
            }
        }
        // Wenn das Modal schließt, könnte man top zurücksetzen, z.B.:
        // else {
        //   const el = document.querySelector(".modal-content");
        //   if (el) {
        //     el.style.top = "0px";
        //   }
        // }
    }, [editModalVisible]);

    // Falls Modal nicht sichtbar, direkt null zurückgeben
    if (!editModalVisible) return null;

    return (
        <div className="admin-dashboard scoped-dashboard">
            {/**
             * 2) WICHTIG: In deinem CSS muss .modal-overlay und .modal-content
             *    auf position: absolute umgestellt sein, damit .style.top wirkt.
             */}
            <div className="modal-overlay">
                <div className="modal-content">
                    <h3>
                        {t("adminDashboard.editTrackingTitle", "Zeiterfassung bearbeiten")}{" "}
                        {editDate?.toLocaleDateString("de-DE")}
                    </h3>
                    <form onSubmit={handleEditSubmit}>
                        <div className="form-group">
                            <label>{t("workStart", "Work Start")}:</label>
                            <input
                                type="time"
                                name="workStart"
                                value={editData.workStart}
                                onChange={handleEditInputChange}
                                required
                            />
                        </div>
                        <div className="form-group">
                            <label>{t("breakStart", "Break Start")}:</label>
                            <input
                                type="time"
                                name="breakStart"
                                value={editData.breakStart}
                                onChange={handleEditInputChange}
                            />
                        </div>
                        <div className="form-group">
                            <label>{t("breakEnd", "Break End")}:</label>
                            <input
                                type="time"
                                name="breakEnd"
                                value={editData.breakEnd}
                                onChange={handleEditInputChange}
                            />
                        </div>
                        <div className="form-group">
                            <label>{t("workEnd", "Work End")}:</label>
                            <input
                                type="time"
                                name="workEnd"
                                value={editData.workEnd}
                                onChange={handleEditInputChange}
                                required
                            />
                        </div>
                        <div className="form-group">
                            <label>
                                {t("adminDashboard.adminPassword", "Admin Passwort")}:
                            </label>
                            <input
                                type="password"
                                name="adminPassword"
                                value={editData.adminPassword}
                                onChange={handleEditInputChange}
                                required
                            />
                        </div>
                        <div className="form-group">
                            <label>
                                {t("adminDashboard.userPassword", "Benutzerpasswort")}:
                            </label>
                            <input
                                type="password"
                                name="userPassword"
                                value={editData.userPassword}
                                onChange={handleEditInputChange}
                                required
                            />
                        </div>
                        <div className="modal-buttons">
                            <button type="submit">{t("save", "Speichern")}</button>
                            <button type="button" onClick={() => setEditModalVisible(false)}>
                                {t("cancel", "Abbrechen")}
                            </button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    );
};

EditTimeModal.propTypes = {
    t: PropTypes.func.isRequired,
    editModalVisible: PropTypes.bool.isRequired,
    editDate: PropTypes.instanceOf(Date),
    editData: PropTypes.shape({
        workStart: PropTypes.string.isRequired,
        breakStart: PropTypes.string.isRequired,
        breakEnd: PropTypes.string.isRequired,
        workEnd: PropTypes.string.isRequired,
        adminPassword: PropTypes.string.isRequired,
        userPassword: PropTypes.string.isRequired,
    }).isRequired,
    handleEditInputChange: PropTypes.func.isRequired,
    handleEditSubmit: PropTypes.func.isRequired,
    setEditModalVisible: PropTypes.func.isRequired,
};

export default EditTimeModal;
